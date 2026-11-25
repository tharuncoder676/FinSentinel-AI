#!/usr/bin/env bash
set -euo pipefail
DB_NAME="${1:-finsentinel}"
DB_USER="${2:-finsentinel}"
BACKUP_FILE="${3:-/tmp/finsentinel_backup.sql}"

pg_dump -h localhost -p 5432 -U "$DB_USER" -d "$DB_NAME" -F p -f "$BACKUP_FILE"
RESTORE_DB="${DB_NAME}_restore_check"
psql -h localhost -p 5432 -U postgres -c "DROP DATABASE IF EXISTS $RESTORE_DB;"
psql -h localhost -p 5432 -U postgres -c "CREATE DATABASE $RESTORE_DB OWNER $DB_USER;"
psql -h localhost -p 5432 -U "$DB_USER" -d "$RESTORE_DB" -v ON_ERROR_STOP=1 -f "$BACKUP_FILE" >/dev/null
psql -h localhost -p 5432 -U "$DB_USER" -d "$RESTORE_DB" -t -A -c "SELECT count(*) FROM users;"
psql -h localhost -p 5432 -U postgres -c "DROP DATABASE IF EXISTS $RESTORE_DB;"
