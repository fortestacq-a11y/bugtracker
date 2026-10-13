# Engineering Bug & Issue Tracker

Full-stack issue tracking system built with **Spring Boot 4 / Java 21**, **MySQL 8.4**, and **React 18 (Vite, Tailwind CSS)** styled according to the **Claude Design System** (warm parchment canvas `#f5f4ed`, ivory surfaces `#faf9f5`, terracotta brand accent `#c96442`, Anthropic Serif typography, and ring-based elevation).

---

## 1. Architecture

```
┌────────────────────────────────┐         HTTP / JSON         ┌────────────────────────────────┐       JDBC       ┌────────────────────────────────┐
│  React 18 SPA (Vite + Tailwind)│  ─────────────────────────► │  Spring Boot 4 REST API        │  ────────────►  │  MySQL 8.4                     │
│  Port: 5173                    │    /api/issues, /api/auth   │  Port: 8080                    │    :3306        │  (Podman container)            │
│  Claude Design System Theme    │  ◄───────────────────────── │  Stateless JWT / Nimbus OAuth2 │  ◄──────────── │  Volume: bugtracker-mysql-data │
└────────────────────────────────┘      JSON Responses         └────────────────────────────────┘                 └────────────────────────────────┘
        │                                                              │
        └── Vite Dev Proxy: /api ──► localhost:8080                    └── Hibernate (ddl-auto: update)
```

- **Frontend never talks to MySQL directly.** All queries and mutations pass through the Spring Boot service layer.
- **Vite dev server proxy** forwards `/api` requests to `localhost:8080` to prevent CORS issues in development.

---

## 2. Security & Authorization Model

### Authentication
- **Stateless Bearer JWT** issued and verified using Nimbus JOSE JWT bundled within `spring-boot-starter-oauth2-resource-server` (zero third-party JWT dependencies).
- **BCrypt password hashing** (cost factor 12) for all stored credentials.
- **CSRF is disabled** because tokens are passed via HTTP `Authorization: Bearer <token>` headers instead of browser cookies.
- **The JWT signing secret is mandatory and has no default.** `app.jwt.secret` resolves
  from the `JWT_SECRET` environment variable only. If it is missing, shorter than the
  32-byte HS256 minimum, or still set to the old development placeholder, startup fails
  with an explicit message instead of running with a guessable key.

  > Why this is not a convenience default: the secret lives in `application.yml`, which
  > ships inside the published Docker image. A committed default would therefore be
  > public knowledge, and anyone could sign a token for any user id — which bypasses
  > the entire membership and role model below. Refusing to start is the only safe
  > behaviour, because a service running with a known key looks completely healthy.

  ```bash
  # local dev
  export JWT_SECRET="$(openssl rand -base64 48)"
  # container / CI / Railway: set JWT_SECRET as an environment variable or secret
  ```
- **Error contract**:
  - `401 Unauthorized`: Missing, expired, or invalid token (frontend clears token and redirects to `/login`).
  - `403 Forbidden`: Authenticated user lacks required project role (frontend shows toast and retains user position).

### Project-Membership Authorization (SQL-Level Scoping)
You can see an issue if you are a system `ADMIN` **or** a member of the project that issue belongs to.

**Crucial Security Guard**: Data scoping is enforced directly in repository queries via `where i.project.id in :visibleProjectIds`, **never filtered in Java after `findAll()`**.

```java
@Query("""
    select i from Issue i
    where i.project.id in :visibleProjectIds
      and (:projectId is null or i.project.id = :projectId)
      and (:status is null or i.status = :status)
      and (:priority is null or i.priority = :priority)
    order by i.updatedAt desc
""")
List<Issue> findVisible(Set<Long> visibleProjectIds, Long projectId, IssueStatus status, Priority priority, ...);
```

### Role Capabilities
| Project Role | Capabilities |
|---|---|
| `VIEWER` | Read issues, comments, audit history |
| `REPORTER` | + Create issues, add comments, edit **own** reported issues |
| `DEVELOPER` | + Edit **any** issue, triage (change status, priority, assignee) |
| `MAINTAINER` | + Delete issues, add & remove project members |
| `OWNER` | + Edit project details, delete empty project |

---

## 3. Business Rules Implemented
1. **Auto-Generated Issue Keys**: Format `PREFIX-N` (e.g. `CORE-1`, `MOB-2`), computed from max project counter + 1.
2. **Resolution Timestamp**: Transitioning to `DONE` or `CLOSED` sets `resolvedAt = Instant.now()`; moving back out clears it.
3. **Audit History Trail**: Every change to `status`, `priority`, `assignee`, or `title` appends an immutable `issue_history` record.
4. **IN_REVIEW Requirement**: Moving an issue to `IN_REVIEW` without an assignee is rejected with `422 Unprocessable Entity`.
5. **Project Deletion Safety**: Deleting a project with remaining issues is rejected with `409 Conflict`.
6. **Last Owner Protection**: Demoting or removing the sole `OWNER` of a project is rejected with `409 Conflict`.
7. **No Global User Leakage**: There is no `GET /api/users`. Assignee dropdowns fetch `GET /api/projects/{id}/members`, ensuring users only discover collaborators within shared projects.

---

## 4. Quick Start

### Prerequisites
- Java 21+ (`java -version`)
- Node.js 18+ (`node -v`)
- Podman (or Docker)

### Step 1: Start MySQL 8.4 Container
```bash
./scripts/db-up.sh
```
Verify the container:
```bash
podman exec -it bugtracker-mysql mysql -ubuguser -pbugpass -e "SHOW TABLES;" bugtracker
```

### Step 2: Set the JWT signing secret

The app **refuses to start without this** — there is deliberately no built-in
default, because a default baked into a published image means anyone can mint a
valid token for any user. Generate one:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"

# no openssl installed? (common on minimal Fedora):
export JWT_SECRET="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
```

### Step 3: Start Spring Boot Backend
```bash
cd backend
./mvnw spring-boot:run
```
Backend will start on `http://localhost:8080`.

### Step 4: Seed Sample Data
```bash
./scripts/seed.sh
```
Creates:
- `alice@example.com` / `password123` (`OWNER` in CORE & MOB)
- `bob@example.com` / `password123` (`DEVELOPER` in CORE, `VIEWER` in MOB)
- `charlie@example.com` / `password123` (`REPORTER` in CORE, **No access** to MOB)

### Step 5: Start React Frontend
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

> **Prefer containers?** See [§5 Container Images](#5-container-images) — it builds both
> tiers as images and brings up the same three services (db + api + web) in one command.

---

## 5. Container Images

Two images, both multi-stage builds:

| Image | Built from | Runtime | Contents |
|---|---|---|---|
| `bugtracker-api` | `backend/Dockerfile` | `eclipse-temurin:21-jre-alpine` | Spring Boot API only — no Maven, no source, no shell tools |
| `bugtracker-web` | `frontend/Dockerfile` | `nginx:1.27-alpine` | Compiled Vite bundle + nginx that serves it and proxies `/api` to the API |

`docker-compose.yml` adds the third piece, MySQL 8.4, reusing the same image, credentials
and named volume (`bugtracker-mysql-data`) as `scripts/db-up.sh`, so data is shared
between host-run and compose-run setups.

### Build

```bash
./scripts/docker-build.sh
```

Or invoke the builder directly (context is the component directory, not the repo root):

```bash
podman build -f backend/Dockerfile  -t localhost/bugtracker-api:dev backend
podman build -f frontend/Dockerfile -t localhost/bugtracker-web:dev frontend
```

To run the backend test suite inside the image build (skipped by default for speed):

```bash
RUN_TESTS=true ./scripts/docker-build.sh
```

Or run them directly on the host — the suite is self-contained and uses an in-memory
H2 database, so it never touches the MySQL container:

```bash
cd backend && ./mvnw test
```

### Run the whole stack

```bash
podman compose up -d --build      # or: docker compose up -d --build
```

| Service | URL | Notes |
|---|---|---|
| Web (nginx) | http://localhost:5173 | Serves the SPA, reverse-proxies `/api` internally |
| API | http://localhost:8080/api/health | `{"status":"UP","database":"UP"}` when ready |
| MySQL | `localhost:3306` | `podman exec bugtracker-mysql mysql -ubuguser -pbugpass bugtracker` |

Seed data works unchanged against the containerised API:

```bash
API_URL=http://localhost:8080 ./scripts/seed.sh
```

Tear down with `podman compose down` (keeps data) or `podman compose down -v`
(**deletes** the MySQL volume).

> **Note:** `podman compose` / `docker compose` requires a compose provider
> (`podman-compose` or the `docker-compose` plugin). The images themselves build and run
> fine without one — see the manual `run` commands printed by `scripts/docker-push.sh`.

### Push to Docker Hub

```bash
cp .env.example .env
# edit .env:  REGISTRY=docker.io/<your-username>

./scripts/docker-build.sh         # tags docker.io/<you>/bugtracker-api and -web
podman login docker.io            # or: docker login
./scripts/docker-push.sh          # pushes the version tag and :latest
```

Both scripts accept overrides instead of editing `.env`:

```bash
REGISTRY=docker.io/manjula VERSION=0.1.0 ./scripts/docker-build.sh
REGISTRY=docker.io/manjula VERSION=0.1.0 ./scripts/docker-push.sh
```

`VERSION` defaults to `git describe --tags --always --dirty`, so image tags stay
traceable to a commit.

### Configuration

Every deployment-specific value is an environment variable. Defaults are the local-dev
values, which is why `mvn spring-boot:run` still works with no `.env` at all.

| Variable | Default | Used by |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/bugtracker?...` | API datasource |
| `DB_USER` / `DB_PASSWORD` | `buguser` / `bugpass` | API datasource |
| `DB_POOL_SIZE` | `10` | HikariCP max pool size |
| `SERVER_PORT` | `8080` | API listen port |
| `JWT_SECRET` | *(none — required)* | **must be set or startup fails** |
| `API_UPSTREAM` | `http://backend:8080` | nginx `/api` proxy target |

> ⚠️ **`JWT_SECRET` has no default.** If you skip it the API will not start at all —
> that is deliberate, not a misconfiguration. `openssl rand -base64 48`.

### Design notes

- **Layered API image.** The boot jar is exploded with `jarmode=tools` and its
  `dependencies/`, `spring-boot-loader/`, `snapshot-dependencies/` and `application/`
  directories are copied as separate layers. Editing one Java file then rebuilds a layer
  of a few hundred KB rather than the whole ~60MB dependency set.
- **Non-root.** The API runs as UID `10001`; nginx runs as its default non-root user.
- **JVM as PID 1.** `ENTRYPOINT` is exec-form, so the JVM receives `SIGTERM` directly
  and Spring's graceful shutdown runs. Shutdown theme matters most here: an in-flight
  `PUT /api/issues/{id}` writes its `issue_history` rows in a transaction, and a `SIGKILL`
  mid-transaction would lose that audit trail.
- **Startup ordering is explicit.** Compose gates the API on MySQL's *healthcheck*, not
  on "container started" — `mysqladmin ping` succeeds while first-boot initialisation is
  still running, which is a classic source of "API crashed on boot" false alarms.
- **One client code path.** The browser always calls relative `/api/...`; nginx proxies it
  to the API. This is the same shape as the Vite dev proxy, so production never relies
  on CORS, and the built bundle needs no per-environment rebuild.
- **`/api/health` is public and deliberately vague.** It returns only `UP`/`DOWN` (and a
  503 when the database is unreachable). No version, no datasource URL, no stack traces —
  those still come from the `@RestControllerAdvice` on the authenticated endpoints.

### What is *not* in these images

- No `target/`, `node_modules/`, `.git` or IDE folders are sent as build context — see the
  `.dockerignore` in each component directory.
- No Maven, JDK, Node or npm. The API image is a JRE plus the app's own jars.
- No `application-test.yml`. That file lives in `src/test/resources`, not
  `src/main/resources`, so its fixed JWT secret and H2 datasource never reach a
  published image.
- No database dump or volume is baked in. Schema comes from Hibernate `ddl-auto: update`
  at startup; sample data is seeded through the authenticated API (`scripts/seed.sh`),
  which is also what creates the `project_members` rows correctly.

---

## 6. Verification Test: Multi-Tenant Project Isolation

Run these curl commands to prove that User Charlie cannot read or access User Alice's Mobile App project:

```bash
# 1. Login as Charlie (Member of Project 1 / CORE only)
CHARLIE_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"charlie@example.com","password":"password123"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

# 2. Try fetching Project 2 (Mobile App) -> 403 Forbidden
curl -i -H "Authorization: Bearer $CHARLIE_TOKEN" http://localhost:8080/api/projects/2

# 3. List issues -> Only returns CORE-* issues (0 MOB-* issues returned)
curl -s -H "Authorization: Bearer $CHARLIE_TOKEN" http://localhost:8080/api/issues | grep -o '"key":"[^"]*'
```

---

## 7. REST API Reference

| Verb | Endpoint | Purpose | Required Role |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register new user | Public |
| `POST` | `/api/auth/login` | Obtain Bearer JWT | Public |
| `GET` | `/api/auth/me` | Current user profile | Authenticated |
| `GET` | `/api/projects` | List visible projects | Member / Admin |
| `POST` | `/api/projects` | Create project | Authenticated |
| `GET` | `/api/projects/{id}` | Project details | Member / Admin |
| `PUT` | `/api/projects/{id}` | Update project | `OWNER` |
| `DELETE` | `/api/projects/{id}` | Delete empty project | `OWNER` |
| `GET` | `/api/projects/{id}/members` | Project member list | Member / Admin |
| `POST` | `/api/projects/{id}/members` | Add member | `MAINTAINER`+ |
| `PATCH` | `/api/projects/{id}/members/{uid}` | Update member role | `MAINTAINER`+ |
| `DELETE` | `/api/projects/{id}/members/{uid}` | Remove member | `MAINTAINER`+ |
| `GET` | `/api/issues` | Filtered issue list | Member / Admin |
| `POST` | `/api/issues` | Create issue | `REPORTER`+ |
| `GET` | `/api/issues/{id}` | Issue detail + history | Member / Admin |
| `PUT` | `/api/issues/{id}` | Partial update | `REPORTER` (own) / `DEVELOPER`+ |
| `PATCH` | `/api/issues/{id}/status` | Kanban status advance | `DEVELOPER`+ |
| `DELETE` | `/api/issues/{id}` | Delete issue | `MAINTAINER`+ |
| `GET` | `/api/issues/{id}/comments` | List issue comments | Member / Admin |
| `POST` | `/api/issues/{id}/comments` | Post comment | `REPORTER`+ |
| `GET` | `/api/stats` | Dashboard counters | Member / Admin |
| `GET` | `/api/users/me` | Own profile | Authenticated |
| `PUT` | `/api/users/{id}` | Update profile | Self / Admin |
| `DELETE` | `/api/users/{id}` | Delete account | Self / Admin |
