#!/usr/bin/env bash
# Builds the BugTracker container images (API + web).
#
#   ./scripts/docker-build.sh                 # local build, tags :latest and version
#   REGISTRY=docker.io/manjula ./scripts/docker-build.sh   # Docker Hub names
#   RUN_TESTS=true ./scripts/docker-build.sh  # run the backend test suite in the build
#
# Env vars:
#   REGISTRY     registry prefix for image names (default: none = local-only tags)
#   VERSION      version tag (default: git describe, else 0.0.1)
#   PUSH_LATEST  also tag as :latest (default: true)
#   RUN_TESTS    run `mvn verify` in the API build instead of skipping tests
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT=bugtracker
REGISTRY="${REGISTRY:-}"
PUSH_LATEST="${PUSH_LATEST:-true}"
RUN_TESTS="${RUN_TESTS:-false}"

# Podman first: the project already uses podman in scripts/db-up.sh, and a
# Docker-installed podman would silently produce Docker-format images.
if command -v podman >/dev/null 2>&1; then
  ENGINE=podman
elif command -v docker >/dev/null 2>&1; then
  ENGINE=docker
else
  echo "Neither podman nor docker found on PATH." >&2
  exit 1
fi

if [ -n "$REGISTRY" ]; then
  API_IMAGE="$REGISTRY/$PROJECT-api"
  WEB_IMAGE="$REGISTRY/$PROJECT-web"
else
  API_IMAGE="localhost/$PROJECT-api"
  WEB_IMAGE="localhost/$PROJECT-web"
fi

VERSION="${VERSION:-$(git -C "$ROOT" describe --tags --always --dirty 2>/dev/null || echo 0.0.1)}"

echo "==> Engine:  $ENGINE"
echo "==> Images:  $API_IMAGE / $WEB_IMAGE"
echo "==> Version: $VERSION"

build_one() {
  local name="$1" context="$2" image="$3"
  echo ""
  echo "==> Building $image:$VERSION"
  # Podman defaults to the OCI image format, which silently DROPS HEALTHCHECK and
  # cannot be loaded by `docker load`. Emit Docker format instead: Docker Hub takes
  # it, and both engines read it. Docker's own builder needs no such flag.
  local format_arg=()
  if [ "$ENGINE" = "podman" ]; then
    format_arg=(--format docker)
  fi
  # RUN_TESTS only applies to the API build. Passing it to the web build too would
  # earn a "build arg not consumed" warning on every build.
  local test_arg=()
  if [ "$name" = "api" ]; then
    test_arg=(--build-arg "RUN_TESTS=$RUN_TESTS")
  fi
  $ENGINE build \
    "${format_arg[@]}" \
    "${test_arg[@]}" \
    --file "$ROOT/$context/Dockerfile" \
    --tag "$image:$VERSION" \
    "$ROOT/$context"
}

# Context is the component dir, not the repo root: it is smaller, and each
# .dockerignore is written for exactly that context.
build_one api  backend  "$API_IMAGE"
build_one web  frontend "$WEB_IMAGE"

if [ "$PUSH_LATEST" = "true" ]; then
  $ENGINE tag "$API_IMAGE:$VERSION" "$API_IMAGE:latest"
  $ENGINE tag "$WEB_IMAGE:$VERSION" "$WEB_IMAGE:latest"
fi

echo ""
echo "==> Built:"
for img in "$API_IMAGE:$VERSION" "$WEB_IMAGE:$VERSION"; do
  $ENGINE image inspect --format \
    "  {{.RepoTags}}  {{.Size}} bytes" "$img" 2>/dev/null || echo "  $img"
done

if [ -z "$REGISTRY" ]; then
  echo ""
  echo "Local-only tags (localhost/ prefix keeps them out of docker.io searches)."
  echo "To push to Docker Hub:  REGISTRY=docker.io/<your-username> ./scripts/docker-build.sh"
  echo "then:                  ./scripts/docker-push.sh"
fi

echo ""
echo "Run the whole stack:  $ENGINE compose up -d"