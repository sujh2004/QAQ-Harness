#!/usr/bin/env bash
# Brings DevPilot up on a machine that has only Docker.
#
#   ./scripts/bootstrap.sh            # start
#   ./scripts/bootstrap.sh --reset    # start from the seeded state again
#
# Everything here is idempotent, so re-running after a failure is safe. Without a DASHSCOPE_API_KEY
# the stack still starts and every read-only page works; only agent runs and knowledge search report
# that no model is configured. A missing key must look like a missing key, not like a broken build.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

step() { printf '\033[36m==> %s\033[0m\n' "$1"; }
warn() { printf '\033[33m    %s\033[0m\n' "$1"; }

step 'Checking Docker'
if ! docker info >/dev/null 2>&1; then
    echo 'Docker is not reachable. Start Docker and run this script again.' >&2
    exit 1
fi

if [ ! -f .env ]; then
    step 'Creating .env from .env.example'
    cp .env.example .env
    warn 'Put your DASHSCOPE_API_KEY in .env to enable agents and knowledge search.'
fi

has_key=no
if grep -Eq '^DASHSCOPE_API_KEY=.+' .env; then
    has_key=yes
fi

if [ "${1:-}" = "--reset" ]; then
    step 'Removing existing containers and volumes'
    docker compose --profile full down -v
fi

step 'Building and starting the stack (first run downloads images and dependencies)'
docker compose --profile full up -d --build

step 'Waiting for the backend health check'
healthy=no
for _ in $(seq 1 60); do
    if curl -fsS http://localhost:8080/api/v1/health >/dev/null 2>&1; then
        healthy=yes
        break
    fi
    sleep 5
done

if [ "$healthy" != "yes" ]; then
    docker compose --profile full logs --tail 50 backend
    echo 'The backend did not become healthy in time. The last 50 log lines are above.' >&2
    exit 1
fi

echo
step 'DevPilot is up'
echo '    Frontend : http://localhost:5173'
echo '    Backend  : http://localhost:8080/api/v1/health'
echo '    MySQL    : localhost:3307 (devpilot / devpilot)'
if [ "$has_key" = "yes" ]; then
    printf '\033[32m    Model    : DASHSCOPE_API_KEY found — agents and knowledge search are enabled.\033[0m\n'
else
    warn 'Model    : no DASHSCOPE_API_KEY — read-only pages work, agent runs will refuse.'
fi
echo
echo '    Stop with:  docker compose --profile full down'
