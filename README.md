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

### Step 2: Start Spring Boot Backend
```bash
cd backend
./mvnw spring-boot:run
```
Backend will start on `http://localhost:8080`.

### Step 3: Seed Sample Data
```bash
./scripts/seed.sh
```
Creates:
- `alice@example.com` / `password123` (`OWNER` in CORE & MOB)
- `bob@example.com` / `password123` (`DEVELOPER` in CORE, `VIEWER` in MOB)
- `charlie@example.com` / `password123` (`REPORTER` in CORE, **No access** to MOB)

### Step 4: Start React Frontend
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## 5. Verification Test: Multi-Tenant Project Isolation

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

## 6. REST API Reference

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
