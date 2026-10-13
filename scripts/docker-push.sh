#!/usr/bin/env bash
# Pushes the BugTracker images to a registry (Docker Hub by default).
#
#   REGISTRY=docker.io/manjula ./scripts/docker-build.sh
#   ./scripts/docker-push.sh
#
# Requires a prior login — this script will not read credentials for you:
#   podman login docker.io        # or: docker login
#
# Env vars:
#   REGISTRY    registry prefix (default: docker.io, i.e. Docker Hub)
#   VERSION     version tag to push (default: git describe, else 0.0.1)
#   INCLUDE_LATEST  also push :latest (default: true)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT=bugtracker
REGISTRY="${REGISTRY:-docker.io}"
VERSION="${VERSION:-$(git -C "$ROOT" describe --tags --always --dirty 2>/dev/null || echo 0.0.1)}"
INCLUDE_LATEST="${INCLUDE_LATEST:-true}"

if command -v podman >/dev/null 2>&1; then
  ENGINE=podman
elif command -v docker >/dev/null 2>&1; then
  ENGINE=docker
else
  echo "Neither podman nor docker found on PATH." >&2
  exit 1
fi

API_IMAGE="$REGISTRY/$PROJECT-api"
WEB_IMAGE="$REGISTRY/$PROJECT-web"

for image in "$API_IMAGE" "$WEB_IMAGE"; do
  if ! $ENGINE image inspect "$image:$VERSION" >/dev/null 2>&1; then
    echo "Image $image:$VERSION not found locally. Run scripts/docker-build.sh first." >&2
    exit 1
  fi
done

echo "==> Pushing $API_IMAGE:$VERSION"
$ENGINE push "$API_IMAGE:$VERSION"

echo "==> Pushing $WEB_IMAGE:$VERSION"
$ENGINE push "$WEB_IMAGE:$VERSION"

if [ "$INCLUDE_LATEST" = "true" ]; then
  # :latest is what `docker run repo/api` resolves to, so both need pushing.
  $ENGINE tag "$API_IMAGE:$VERSION" "$API_IMAGE:latest"
  $ENGINE tag "$WEB_IMAGE:$VERSION" "$WEB_IMAGE:latest"
  echo "==> Pushing :latest"
  $ENGINE push "$API_IMAGE:latest"
  $ENGINE push "$WEB_IMAGE:latest"
fi

cat <<EOF

==> Done. Published:
     $API_IMAGE:$VERSION
     $WEB_IMAGE:$VERSION

   Run the API alone (needs a reachable MySQL):
     $ENGINE run -d --name bugtracker-api -p 8080:8080 \\
       -e DB_URL='jdbc:mysql://<mysql-host>:3306/bugtracker?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \\
       -e DB_USER=buguser -e DB_PASSWORD=bugpass \\
       -e JWT_SECRET="\$(openssl rand -base64 48)" \\
       $API_IMAGE:$VERSION

   Run the web tier alone (point it at the API):
     $ENGINE run -d --name bugtracker-web -p 5173:80 \\
       -e API_UPSTREAM=http://<api-host>:8080 \\
       $WEB_IMAGE:$VERSION

   Or bring up the whole stack (db + api + web):
     $ENGINE compose up -d
EOF