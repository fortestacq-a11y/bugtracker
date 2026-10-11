# Engineering Bug & Issue Tracker — Build Plan

Stack: **React (Vite)** → **Spring Boot (Maven)** → **MySQL 8 (Podman container)**

---

## 1. Architecture at a glance

```
┌─────────────────────┐      HTTP/JSON       ┌──────────────────────┐      JDBC       ┌─────────────────────┐
│  React SPA          │  ──────────────────► │  Spring Boot REST    │  ────────────►  │  MySQL 8            │
│  (Vite, :5173)      │   /api/issues ...    │  API  (:8080)        │   :3306        │  (podman container) │
│  Tailwind CSS       │  ◄────────────────── │  JPA / Hibernate     │  ◄──────────── │  volume: mysql-data │
└─────────────────────┘   JSON responses     └──────────────────────┘                └─────────────────────┘
        │                                          │
        └── no CORS problem, Vite proxy ──┐       └── JPA auto-creates tables
                                            └── /api → localhost:8080
```

**Key point:** React never talks to MySQL. It only talks to Spring Boot. Spring Boot is the *only* thing holding a DB connection.

---

## 2. Repo layout

```
bug-tracker/
├── backend/                      # Spring Boot (Maven)
│   ├── pom.xml                   # dependencies live here (that's what "Maven" means)
│   ├── Dockerfile                # optional, only if you also containerise the app
│   └── src/main/
│       ├── java/com/example/bugtracker/
│       │   ├── BugTrackerApplication.java
│       │   ├── domain/           # JPA entities (the table shapes)
│       │   │   ├── Issue.java
│       │   │   ├── Project.java
│       │   │   ├── User.java
│       │   │   ├── Comment.java
│       │   │   ├── IssueHistory.java
│       │   │   └── enums/  (IssueType, Priority, IssueStatus)
│       │   ├── repository/       # Spring Data JPA interfaces
│       │   ├── service/          # business logic + @Transactional
│       │   ├── web/              # @RestController + DTOs
│       │   └── config/           # CORS, seed data
│       └── resources/
│           ├── application.yml   # DB URL, user, password
│           └── data.sql          # sample rows so UI isn't empty
│
├── frontend/                     # React + Vite
│   ├── package.json
│   ├── vite.config.js            # contains the /api proxy
│   ├── tailwind.config.js
│   └── src/
│       ├── api/client.js         # fetch wrappers
│       ├── components/
│       │   ├── IssueBoard.jsx    # Kanban
│       │   ├── IssueCard.jsx
│       │   ├── IssueForm.jsx     # create/edit modal
│       │   ├── IssueDetail.jsx
│       │   ├── FilterBar.jsx
│       │   ├── CommentList.jsx
│       │   └── HistoryTimeline.jsx
│       ├── pages/  Dashboard.jsx, ProjectList.jsx
│       └── App.jsx
│
├── db/
│   └── mysql-init/
│       └── 01-schema.sql         # optional: DDL if you'd rather not use ddl-auto
│
├── scripts/
│   ├── db-up.sh                  # podman run for MySQL
│   ├── db-down.sh
│   ├── db-reset.sh               # drop volume & recreate
│   └── seed.sh                   # curl calls to create sample data
│
└── README.md
```

---

## 3. Database schema (MySQL)

```
projects
  id            PK  AUTO_INCREMENT
  key           VARCHAR(10)  UNIQUE     -- e.g. "BUGTRK"
  name          VARCHAR(120) NOT NULL
  description   TEXT
  created_at    DATETIME

users
  id            PK AUTO_INCREMENT
  name          VARCHAR(80)  NOT NULL
  email         VARCHAR(160) UNIQUE
  role          ENUM('REPORTER','DEVELOPER','REVIEWER','ADMIN')
  avatar_color  VARCHAR(7)              -- for the UI circle, avoids file uploads

issues
  id            PK AUTO_INCREMENT
  key           VARCHAR(20) UNIQUE     -- human id like "BUGTRK-14"
  title         VARCHAR(200) NOT NULL
  description   TEXT
  type          ENUM('BUG','FEATURE','REFACTOR','TASK','TECH_DEBT')
  priority      ENUM('P0','P1','P2','P3')
  status        ENUM('BACKLOG','IN_PROGRESS','IN_REVIEW','DONE','CLOSED')
  environment   ENUM('PROD','STAGING','DEV','LOCAL')
  project_id    FK -> projects.id
  reporter_id   FK -> users.id
  assignee_id   FK -> users.id   NULL
  created_at / updated_at / resolved_at

comments
  id, body TEXT, author_id FK, issue_id FK, created_at

issue_history
  id, issue_id FK, field VARCHAR(40), old_value, new_value,
  changed_by FK, created_at
```

**Relationships:** Project 1—N Issues · User 1—N Issues (as reporter/assignee) · Issue 1—N Comments · Issue 1—N History.
The one worth showing off: an issue is both *reported by* and *assigned to* a user — two FKs into the same table, called a self-referencing relationship on a second entity.

---

## 4. REST API contract

| Method | Endpoint | Purpose |
|---|---|---|
| GET    | `/api/issues` | list + filter (`?status=IN_PROGRESS&priority=P1&projectId=1&q=text`) |
| GET    | `/api/issues/{id}` | single issue w/ comments + history |
| POST   | `/api/issues` | create |
| PUT    | `/api/issues/{id}` | update (auto-writes history rows) |
| PATCH  | `/api/issues/{id}/status` | Kanban drag-drop — only status changes |
| DELETE | `/api/issues/{id}` | delete |
| GET/POST/PUT/DELETE | `/api/projects` | project CRUD |
| GET/POST/PUT/DELETE | `/api/users` | user CRUD |
| GET/POST/DELETE | `/api/issues/{id}/comments` | comments |
| GET    | `/api/stats` | dashboard counters |

**Error shape (all errors look the same):**
```json
{ "status": 404, "error": "Issue not found", "path": "/api/issues/99" }
```
Handled centrally by one `@RestControllerAdvice`.

---

## 5. Backend layer breakdown (what each Spring piece does)

| Layer | Annotation | Your job |
|---|---|---|
| Entity | `@Entity` | Maps to a table. `Issue.java` ↔ `issues` |
| Repository | `extends JpaRepository<Issue, Long>` | Zero code for basic CRUD; add `@Query` for filters |
| Service | `@Service` + `@Transactional` | Business rules, history writes, validation |
| Controller | `@RestController` + `@RequestMapping("/api/issues")` | Maps HTTP verbs, returns DTOs |
| DTO | plain Java `record` | What the JSON actually looks like |
| Config | `@Configuration` | CORS for `:5173`, seed data |

**Business rules worth writing (this is what separates a real project from a tutorial):**
1. `key` auto-generates from project key + counter → `BUGTRK-1`, `BUGTRK-2`
2. Status → `DONE` sets `resolved_at`; moving off `DONE` clears it
3. Every update to `status` / `assignee_id` / `priority` writes an `issue_history` row
4. Deleting a project with issues → reject with 409, don't cascade silently
5. Can't set status to `IN_REVIEW` with no assignee → 422

---

## 6. Frontend plan

**Views:**
- **Board** (default) — 4 columns: Backlog · In Progress · In Review · Done. Cards drag between columns → `PATCH /api/issues/{id}/status`.
- **List** — dense table with sorting, showing issue key, title, badges.
- **Project switcher** — top bar.
- **Detail drawer** — slides in from the right, shows description, comments, history timeline.

**State handling:** React Query (`@tanstack/react-query`) rather than raw `useState` — handles caching, refetch after mutation, and loading states without hand-written logic. Worth mentioning in an interview.

**Styling:** Tailwind CSS. Dark theme, Linear/Jira-like density.

**Vite dev proxy** (`vite.config.js`):
```js
server: { proxy: { '/api': 'http://localhost:8080' } }
```
Frontend calls `/api/issues`; Vite forwards to Spring Boot. No CORS errors in dev, no hardcoded `localhost:8080` scattered through components.

---

## 7. Podman + MySQL setup

```bash
podman run -d --name bugtracker-mysql \
  -e MYSQL_DATABASE=bugtracker \
  -e MYSQL_USER=buguser \
  -e MYSQL_PASSWORD=bugpass \
  -e MYSQL_ROOT_PASSWORD=rootpass \
  -p 3306:3306 \
  -v bugtracker-mysql-data:/var/lib/mysql \
  -v "$PWD/db/mysql-init:/docker-entrypoint-initdb.d:Z" \
  docker.io/library/mysql:8.4
```

- **`:Z`** — SELinux relabel so Podman can read your init folder (easy one to miss; you *will* hit it)
- Named volume `bugtracker-mysql-data` → data survives `podman rm`
- Verify: `podman exec -it bugtracker-mysql mysql -ubuguser -pbugpass -e "SHOW TABLES;" bugtracker`

Then Spring Boot connects with:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bugtracker
    username: buguser
    password: bugpass
  jpa:
    hibernate:
      ddl-auto: update
```

---

## 8. Build order

| # | Step | Produces |
|---|---|---|
| 1 | Podman MySQL up + verify | DB reachable on 3306 |
| 2 | `mvn spring-boot:run` on a fresh project | "Hello" endpoint responds |
| 3 | Entities + repositories | Tables auto-created, test via `GET /api/issues` |
| 4 | Services + controllers + DTOs | Full REST CRUD working (test in Postman) |
| 5 | Seed data | Non-empty DB |
| 6 | React scaffold + proxy + Tailwind | Shell page talking to API |
| 7 | Board, list, form, detail | Full working app |
| 8 | History timeline + rules | The "real project" layer |
| 9 | README | Someone else can run it |

**Steps 1–5 are testable on their own with Postman** — you don't need React before the API works.

---

## 9. Two open questions before I write code

1. **Auth** — skip login entirely (open API, easiest to demo) or add a hardcoded "acting user" picker so issues have reporters/assignees?
2. **Frontend depth** — Kanban board with drag-and-drop (impressive, more work) or start with list + form and add the board after the API is solid?

I'd default to: no auth, list-first, board as phase 2. Say the word and I'll start on step 1.

*(Q1 above is now answered: auth is required. See sections 10 and 11.)*

---

## 10. Authentication & Authorization

Requirement: users sign in, and **no user can see another user's data** outside what their role permits.

### 10.1 The access model

The question "whose data is whose?" has no single answer in a real issue tracker. Jira and Linear solve it with **project membership**, and that's what this plan assumes:

```
project_members
  project_id   FK
  user_id      FK          composite PK (project_id, user_id)
  project_role ENUM('OWNER','MAINTAINER','DEVELOPER','REPORTER','VIEWER')
  added_at     DATETIME
```

**The read rule — one line, enforced in SQL:**

> You can see an issue if you are an `ADMIN`, **or** you are a member of the project that issue belongs to.

Everything else narrows from there.

**The action rules, per project role:**

| Project role | May do |
|---|---|
| `VIEWER` | read issues, comments, history |
| `REPORTER` | + create issues, comment, edit **own** issues only |
| `DEVELOPER` | + edit **any** issue, change status / priority / assignee |
| `MAINTAINER` | + delete issues, add & remove project members |
| `OWNER` | + edit/delete the project itself |

`users.role` stays at just `ADMIN` / `MEMBER` — system-level only, so an admin can see everything without polluting the per-project model.

### 10.2 The `/api/users` problem

This is the subtle one. You cannot simply delete the user list, because the **assignee dropdown needs names to show**. The fix is to scope it:

```
GET /api/users/me                     → your own profile only
GET /api/projects/{id}/members        → only people in that project (requires membership)
DELETE /api/users/{id}                → 404 for anyone else, even if the id exists
```

The assignee dropdown calls `/api/projects/{id}/members`. So the UI still gets its data, and it only ever gets data scoped to a project the caller already belongs to.

**Critical:** the scoping happens in the **query**, not in Java after fetching. This is easy to get wrong:

```java
// WRONG — pulls every issue into memory, filters in Java.
// Leaks data if you ever forget a branch, and breaks pagination.
List<Issue> all = repo.findAll();
return all.stream().filter(i -> isMember(user, i)).toList();

// RIGHT — the database only ever returns rows you're allowed to see.
@Query("""
    select i from Issue i
    where i.project.id in :visibleProjectIds
      and (:status is null or i.status = :status)
      and (:priority is null or i.priority = :priority)
    """)
List<Issue> findVisible(Set<Long> visibleProjectIds, IssueStatus status, Priority priority);
```

Every repository query that returns issues takes the caller's visible project IDs as a parameter. No exceptions, or you will forget one.

### 10.3 Authentication mechanics

**Stack:** Spring Security 6 + `spring-boot-starter-oauth2-resource-server`. You do **not** need a third-party JWT library — Spring Security bundles Nimbus JOSE, which issues and verifies the tokens itself.

**Password storage:** `BCryptPasswordEncoder`. Never plaintext, never MD5 or plain SHA — SHA is too fast to be safe against brute force.

**Flow:**
```
POST /api/auth/register  {name,email,password}  → 201 {accessToken, user}
POST /api/auth/login     {email,password}       → 200 {accessToken, user}
GET  /api/auth/me                              → 200 current user
```
React stores the token, sends `Authorization: Bearer <token>` on every request.

**Security config essentials:**
```java
@Bean SecurityFilterChain filterChain(HttpSecurity http) {
  http.csrf(csrf -> csrf.disable())          // safe *only* because tokens are stateless
      .cors(Customizer.withDefaults())
      .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
      .authorizeHttpRequests(a -> a
          .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
          .anyRequest().authenticated());
  return http.build();
}
```
`csrf.disable()` deserves a sentence in your README: CSRF protection exists because browsers attach cookies automatically; a Bearer token in a header doesn't get attached by the browser, so there's nothing for an attacker to ride on.

**Method-level checks** with `@EnableMethodSecurity`:
```java
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
ResponseEntity<Void> delete(@PathVariable Long id) { ... }
```

### 10.4 401 vs 403 — and the check that must not live in the controller

These are different failures and the frontend must handle them differently:

| Code | Meaning | Frontend reaction |
|---|---|---|
| **401** | No token, or token invalid/expired | Clear token, redirect to `/login` |
| **403** | Valid token, but role too low | Toast "you don't have permission" |

**Where the check belongs: the service layer, not the controller.** A controller check is only as good as the paths that reach it — the moment a second controller, a scheduled job, or a direct service call comes along, the check is bypassed. Put ownership and membership checks in `IssueService.requireVisible(issue, currentUser)`, and have every public service method call it first. Controllers stay thin and just translate HTTP to method calls.

### 10.5 Role-gating the UI (cosmetic only)

Hiding the Delete button when the role is too low is good UX. **It is not security.** The endpoint still returns 403 if called directly. Worth saying out loud in your README so nobody (including a reviewer) mistakes the button hiding for the actual enforcement.

### 10.6 New dependencies

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```
Two additions. No JWT library, no jjwt, no passport.

### 10.7 New files

**Backend**
```
security/SecurityConfig.java          filter chain, CORS, password encoder
security/JwtService.java              issue + parse tokens
security/CurrentUser.java             resolves the token → User, used by controllers
service/AuthService.java              register / login / me
web/AuthController.java               /api/auth/*
service/AccessControlService.java     the membership + role checks
```

**Frontend**
```
pages/Login.jsx
pages/Register.jsx
context/AuthContext.jsx               user + token, login/logout
api/fetch.js                          attaches header, global 401 → logout
components/RequireAuth.jsx            route guard
components/MemberManager.jsx          manage who is in a project
```

### 10.8 Frontend 401 handling

Centralised in the fetch wrapper so you write it once:

```js
export async function apiFetch(path, options = {}) {
  const token = localStorage.getItem('token');
  const res = await fetch(`/api${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options.headers,
    },
  });
  if (res.status === 401) { localStorage.removeItem('token'); window.location = '/login'; }
  if (res.status === 403) throw new Error('You do not have permission to do that.');
  return res.json();
}
```

---

## 11. Amendments to earlier sections

**§3 schema — `users` gains three columns:**
```
  password_hash  VARCHAR(100)  NOT NULL      -- bcrypt, never a plaintext column
  enabled        BOOLEAN       DEFAULT TRUE  -- lets you disable an account without deleting it
  created_at     DATETIME
```
Plus the new `project_members` table from §10.1.

**§4 API — three endpoints added, all others now require `Authorization: Bearer`:**
```
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```
And `GET /api/users` is replaced by the project-scoped pair in §10.2. Everything else keeps its path but is now project-scoped in the query.

**§8 build order — replaced.** Auth can't be bolted on at the end, because every query after it needs the "visible project IDs" parameter. New order:

| # | Step | Testable with |
|---|---|---|
| 1 | Podman MySQL up | `SHOW TABLES;` |
| 2 | Spring Boot skeleton + security starter | app boots, all routes 401 |
| 3 | `User`, `Project`, `ProjectMember` entities | tables auto-create |
| 4 | Auth endpoints — register & login | curl, get a token |
| 5 | SecurityConfig — token actually authenticates | curl with `Bearer`, gets 200 |
| 6 | AccessControlService + project CRUD | curl as user A vs user B |
| 7 | Issue CRUD, project-scoped queries | A cannot fetch B's project issues |
| 8 | Comments + history, role gates on write | A can read, cannot delete |
| 9 | Seed via the API (see below) | populated board |
| 10 | React scaffold + auth context + login page | login redirects |
| 11 | Board, list, form, detail | full app |
| 12 | Member management UI | add/remove members |

**⚠️ Seed data gotcha:** `data.sql` can't hold plaintext passwords — BCrypt hashes must be generated first. Simplest fix: seed projects and issues **through the authenticated API** with `curl` (`scripts/seed.sh` logs in as an admin, grabs the token, then POSTs everything). You get the membership rows right for free, and it doubles as a working demo of the API.

**A good manual test to prove the isolation works:**
```bash
# register two users in two different projects, then:
curl -H "Authorization: Bearer $ALICE_TOKEN" http://localhost:8080/api/projects/2/issues
# → 403 Forbidden (Alice is not a member of project 2)
```
Put this in the README. It's the single clearest demonstration that the authorization works.