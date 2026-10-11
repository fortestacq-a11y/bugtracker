#!/usr/bin/env bash
# Resets the MySQL container and drops all database volumes
set -euo pipefail

NAME=bugtracker-mysql
VOLUME=bugtracker-mysql-data

echo "Stopping and removing container $NAME..."
podman rm -f "$NAME" 2>/dev/null || true

echo "Removing volume $VOLUME..."
podman volume rm -f "$VOLUME" 2>/dev/null || true

echo "Recreating MySQL container..."
"$(dirname "$0")/db-up.sh"

echo "Re-seeding database via API..."
"$(dirname "$0")/seed.sh"
