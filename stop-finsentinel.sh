#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_DIR="$ROOT/.local-runtime"

for pf in "$RUNTIME_DIR/frontend.pid" "$RUNTIME_DIR/backend.pid"; do
  if [ -f "$pf" ]; then
    PID=$(cat "$pf")
    kill "$PID" >/dev/null 2>&1 || true
    rm -f "$pf"
  fi
done

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  docker compose -f "$ROOT/infra/docker-compose.yml" down >/dev/null 2>&1 || true
fi

echo "[FinSentinel] Stopped."
