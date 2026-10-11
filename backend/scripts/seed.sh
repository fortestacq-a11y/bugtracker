#!/usr/bin/env bash
# Seeds the database through the authenticated API with curl.
# Decision: we seed through HTTP rather than data.sql because BCrypt hashes
# cannot be hardcoded in SQL cleanly. Every created row goes through the real
# service validators and access-control guards.
#
# Creates:
#   - 3 users: Alice (lead), Bob (dev), Charlie (reporter)
#   - 2 projects: "Core Platform" (CORE) and "Mobile App" (MOB)
#   - Memberships:
#       Alice   -> OWNER of CORE, OWNER of MOB
#       Bob     -> DEVELOPER in CORE, VIEWER in MOB
#       Charlie -> REPORTER in CORE (not in MOB at all)
#   - 6 issues across BACKLOG, IN_PROGRESS, IN_REVIEW, DONE
#   - Comments and history entries
set -euo pipefail

BASE_URL="${API_URL:-http://localhost:8080}"
echo "==> Seeding database via API at $BASE_URL..."

call_json() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local body="${4:-}"
  local auth_header=()
  if [ -n "$token" ]; then
    auth_header=(-H "Authorization: Bearer $token")
  fi

  if [ -n "$body" ]; then
    curl -sS -f -X "$method" "$BASE_URL$path" \
      -H "Content-Type: application/json" \
      "${auth_header[@]}" \
      -d "$body"
  else
    curl -sS -f -X "$method" "$BASE_URL$path" \
      "${auth_header[@]}"
  fi
}

post_auth() {
  local path="$1"
  local name="$2"
  local email="$3"
  local pass="$4"
  local login_json
  if login_json=$(curl -sS -f -X POST "$BASE_URL/api/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"$email\",\"password\":\"$pass\"}" 2>/dev/null); then
    echo "$login_json"
  else
    curl -sS -f -X POST "$BASE_URL/api/auth/register" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"$name\",\"email\":\"$email\",\"password\":\"$pass\"}"
  fi
}

# 1. Users
echo "--> Creating/logging in users..."
ADMIN_AUTH=$(post_auth "/api/auth/register" "System Admin" "admin@example.com" "password123")
ADMIN_TOKEN=$(echo "$ADMIN_AUTH" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
ADMIN_ID=$(echo "$ADMIN_AUTH" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
podman exec bugtracker-mysql mysql -ubuguser -pbugpass -e "UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';" bugtracker 2>/dev/null || true

ALICE_AUTH=$(post_auth "/api/auth/register" "Alice Chen" "alice@example.com" "password123")
ALICE_TOKEN=$(echo "$ALICE_AUTH" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
ALICE_ID=$(echo "$ALICE_AUTH" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

BOB_AUTH=$(post_auth "/api/auth/register" "Bob Smith" "bob@example.com" "password123")
BOB_TOKEN=$(echo "$BOB_AUTH" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
BOB_ID=$(echo "$BOB_AUTH" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

CHARLIE_AUTH=$(post_auth "/api/auth/register" "Charlie Davis" "charlie@example.com" "password123")
CHARLIE_TOKEN=$(echo "$CHARLIE_AUTH" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
CHARLIE_ID=$(echo "$CHARLIE_AUTH" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

echo "    Admin:   id=$ADMIN_ID (role: ADMIN)"
echo "    Alice:   id=$ALICE_ID"
echo "    Bob:     id=$BOB_ID"
echo "    Charlie: id=$CHARLIE_ID"

# 2. Projects (Alice creates both -> she is OWNER of both)
echo "--> Creating projects..."
CORE_P=$(call_json POST "/api/projects" "$ALICE_TOKEN" \
  '{"key":"CORE","name":"Core Platform","description":"Backend APIs, database migrations, and auth services."}' 2>/dev/null || \
  call_json GET "/api/projects" "$ALICE_TOKEN" | grep -o '{"id":[0-9]*,"key":"CORE"[^}]*' | head -1)
CORE_ID=$(echo "$CORE_P" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

MOB_P=$(call_json POST "/api/projects" "$ALICE_TOKEN" \
  '{"key":"MOB","name":"Mobile App","description":"iOS and Android client application."}' 2>/dev/null || \
  call_json GET "/api/projects" "$ALICE_TOKEN" | grep -o '{"id":[0-9]*,"key":"MOB"[^}]*' | head -1)
MOB_ID=$(echo "$MOB_P" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

echo "    Core Platform: id=$CORE_ID"
echo "    Mobile App:    id=$MOB_ID"

# 3. Memberships
echo "--> Setting up project memberships..."
call_json POST "/api/projects/$CORE_ID/members" "$ALICE_TOKEN" \
  "{\"userId\":$BOB_ID,\"role\":\"DEVELOPER\"}" >/dev/null || true
call_json POST "/api/projects/$MOB_ID/members" "$ALICE_TOKEN" \
  "{\"userId\":$BOB_ID,\"role\":\"VIEWER\"}" >/dev/null || true
call_json POST "/api/projects/$CORE_ID/members" "$ALICE_TOKEN" \
  "{\"userId\":$CHARLIE_ID,\"role\":\"REPORTER\"}" >/dev/null || true

# 4. Issues in CORE
echo "--> Creating issues in CORE..."
I1=$(call_json POST "/api/issues" "$ALICE_TOKEN" \
  "{\"projectId\":$CORE_ID,\"title\":\"JWT tokens do not expire on logout\",\"description\":\"Need a token denylist in Redis or short TTL.\",\"type\":\"BUG\",\"priority\":\"P0\",\"environment\":\"PROD\",\"assigneeId\":$BOB_ID}")
I1_ID=$(echo "$I1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

call_json PATCH "/api/issues/$I1_ID/status" "$BOB_TOKEN" '{"status":"IN_PROGRESS"}' >/dev/null
call_json PATCH "/api/issues/$I1_ID/status" "$BOB_TOKEN" '{"status":"IN_REVIEW"}' >/dev/null

I2=$(call_json POST "/api/issues" "$CHARLIE_TOKEN" \
  "{\"projectId\":$CORE_ID,\"title\":\"Add bulk CSV export for issues\",\"description\":\"Users need a way to export filtered results.\",\"type\":\"FEATURE\",\"priority\":\"P2\",\"environment\":\"DEV\"}")

I3=$(call_json POST "/api/issues" "$BOB_TOKEN" \
  "{\"projectId\":$CORE_ID,\"title\":\"Slow queries on /api/issues with large datasets\",\"description\":\"Missing composite index on (project_id, status, updated_at).\",\"type\":\"TECH_DEBT\",\"priority\":\"P1\",\"environment\":\"PROD\",\"assigneeId\":$BOB_ID}")
I3_ID=$(echo "$I3" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
call_json PATCH "/api/issues/$I3_ID/status" "$BOB_TOKEN" '{"status":"DONE"}' >/dev/null

I4=$(call_json POST "/api/issues" "$ALICE_TOKEN" \
  "{\"projectId\":$CORE_ID,\"title\":\"Upgrade to Spring Boot 4.1\",\"description\":\"Track dependencies and test migrations.\",\"type\":\"TASK\",\"priority\":\"P3\",\"environment\":\"LOCAL\"}")

# 5. Issues in MOB
echo "--> Creating issues in MOB..."
I5=$(call_json POST "/api/issues" "$ALICE_TOKEN" \
  "{\"projectId\":$MOB_ID,\"title\":\"Crash on launch with iOS 18\",\"description\":\"Fatal exception in Keychain wrapper.\",\"type\":\"BUG\",\"priority\":\"P0\",\"environment\":\"PROD\"}")

I6=$(call_json POST "/api/issues" "$ALICE_TOKEN" \
  "{\"projectId\":$MOB_ID,\"title\":\"Dark mode theme glitch on Android\",\"description\":\"Navigation bar stays light in dark theme.\",\"type\":\"BUG\",\"priority\":\"P2\",\"environment\":\"DEV\"}")

# 6. Comments
echo "--> Adding comments..."
call_json POST "/api/issues/$I1_ID/comments" "$ALICE_TOKEN" \
  '{"body":"Reproduction confirmed on staging. Moving to P0."}' >/dev/null
call_json POST "/api/issues/$I1_ID/comments" "$BOB_TOKEN" \
  '{"body":"Draft PR up — reduced TTL to 15m and added rotation."}' >/dev/null

echo "==> Seeding complete."
echo ""
echo "Credentials created:"
echo "  alice@example.com   / password123  (OWNER of CORE, OWNER of MOB)"
echo "  bob@example.com     / password123  (DEVELOPER in CORE, VIEWER in MOB)"
echo "  charlie@example.com / password123  (REPORTER in CORE, no access to MOB)"
