#!/usr/bin/env bash
# Stops the MySQL 8 container
set -euo pipefail

NAME=bugtracker-mysql
if podman container exists "$NAME"; then
  podman stop "$NAME"
  echo "Stopped container $NAME."
else
  echo "Container $NAME is not running."
fi
