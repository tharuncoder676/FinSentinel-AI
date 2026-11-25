#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

RUNTIME_DIR="$ROOT/.local-runtime"
mkdir -p "$RUNTIME_DIR"

if [ ! -f .env ] && [ -f .env.example ]; then
  cp .env.example .env
  echo "[FinSentinel] Created .env from .env.example"
fi

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

# 1. Prefer Docker Compose if Docker daemon is running
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "[FinSentinel] Docker daemon detected. Building & starting full stack..."
  docker compose --env-file .env -f infra/docker-compose.yml up --build -d
  echo "[FinSentinel] Waiting for http://127.0.0.1:8080/actuator/health ..."
  for _ in $(seq 1 60); do
    if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  echo "[FinSentinel] Live at http://localhost:5173"
  command -v open >/dev/null 2>&1 && open "http://localhost:5173" || true
  command -v xdg-open >/dev/null 2>&1 && xdg-open "http://localhost:5173" || true
  exit 0
fi

# 2. Native check (Java 21+, Node 18+, PostgreSQL)
command -v java >/dev/null 2>&1 || { echo "Error: Java 21+ is required (or start Docker Desktop)."; exit 1; }
command -v node >/dev/null 2>&1 || { echo "Error: Node.js 18+ is required (or start Docker Desktop)."; exit 1; }

if [ ! -d frontend/node_modules ]; then
  echo "[FinSentinel] Installing frontend npm dependencies..."
  (cd frontend && npm install)
fi

JAR_PATH="backend/target/finsentinel-backend-1.0.0.jar"
if [ ! -f "$JAR_PATH" ]; then
  echo "[FinSentinel] Downloading Maven dependencies & building backend JAR..."
  (cd backend && chmod +x mvnw && ./mvnw -DskipTests package)
fi

echo "[FinSentinel] Starting Spring Boot backend on :8080..."
nohup java -jar "$JAR_PATH" > "$RUNTIME_DIR/backend.log" 2>&1 &
echo $! > "$RUNTIME_DIR/backend.pid"

echo "[FinSentinel] Starting Vite frontend on :5173..."
(cd frontend && nohup npm run dev -- --host 127.0.0.1 --port 5173 > "$RUNTIME_DIR/frontend.log" 2>&1 & echo $! > "$RUNTIME_DIR/frontend.pid")

for _ in $(seq 1 60); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[FinSentinel] Live at http://127.0.0.1:5173 (run ./stop-finsentinel.sh to stop)"
command -v open >/dev/null 2>&1 && open "http://127.0.0.1:5173" || true
command -v xdg-open >/dev/null 2>&1 && xdg-open "http://127.0.0.1:5173" || true
