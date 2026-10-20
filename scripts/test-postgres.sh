#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
name="weread-flomo-pg-test-$$"
docker run --rm -d --name "$name" -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test-only -e POSTGRES_DB=weread_test -p 127.0.0.1::5432 postgres:17-alpine >/dev/null
trap 'docker stop "$name" >/dev/null' EXIT
for n in $(seq 1 30); do
  if docker exec "$name" pg_isready -U test -d weread_test >/dev/null 2>&1; then break; fi
  sleep 1
done
port=$(docker port "$name" 5432/tcp | awk -F: '{print $NF}')
cd backend
TEST_DATABASE_URL="jdbc:postgresql://127.0.0.1:$port/weread_test" ./mvnw -q test
