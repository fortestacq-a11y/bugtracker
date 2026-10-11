#!/usr/bin/env bash
# Starts the MySQL 8 container for BugTracker
set -euo pipefail

NAME=bugtracker-mysql
IMAGE=docker.io/library/mysql:8.4
VOLUME=bugtracker-mysql-data

if podman container exists "$NAME"; then
  if [ "$(podman inspect -f '{{.State.Running}}' "$NAME")" = "true" ]; then
    echo "Container $NAME is already running."
    exit 0
  fi
  podman start "$NAME"
  echo "Started existing container $NAME."
  exit 0
fi

podman run -d --name "$NAME" \
  -e MYSQL_DATABASE=bugtracker \
  -e MYSQL_USER=buguser \
  -e MYSQL_PASSWORD=bugpass \
  -e MYSQL_ROOT_PASSWORD=rootpass \
  -p 3306:3306 \
  -v "$VOLUME":/var/lib/mysql \
  "$IMAGE"

echo "Waiting for MySQL to accept connections..."
for _ in $(seq 1 60); do
  if podman exec "$NAME" mysqladmin ping -ubuguser -pbugpass --silent >/dev/null 2>&1; then
    echo "MySQL is up and ready on localhost:3306"
    exit 0
  fi
  sleep 2
done

echo "MySQL did not become ready in time." >&2
exit 1
