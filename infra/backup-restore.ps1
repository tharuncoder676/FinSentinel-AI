param(
  [string]$PgBin = "C:\Users\sthar\.gemini\antigravity\brain\76c1900b-8e25-40a1-863d-796e3fdb9b4b\scratch\local-services\postgres\pgsql\bin",
  [string]$DbName = "finsentinel",
  [string]$DbUser = "finsentinel",
  [string]$BackupFile = "C:\Users\sthar\.gemini\antigravity\brain\76c1900b-8e25-40a1-863d-796e3fdb9b4b\scratch\finsentinel_backup.sql"
)
$ErrorActionPreference = "Stop"
& "$PgBin\pg_dump.exe" -h 127.0.0.1 -p 5432 -U $DbUser -d $DbName -F p -f $BackupFile
Write-Host "Backup written to $BackupFile ($( (Get-Item $BackupFile).Length ) bytes)"

$restoreDb = "finsentinel_restore_check"
& "$PgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -c "DROP DATABASE IF EXISTS $restoreDb;"
& "$PgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -c "CREATE DATABASE $restoreDb OWNER $DbUser;"
& "$PgBin\psql.exe" -h 127.0.0.1 -p 5432 -U $DbUser -d $restoreDb -v ON_ERROR_STOP=1 -f $BackupFile | Out-Null
$cnt = & "$PgBin\psql.exe" -h 127.0.0.1 -p 5432 -U $DbUser -d $restoreDb -t -A -c "SELECT count(*) FROM users;"
Write-Host "Restore verified into $restoreDb; users row count = $cnt"
& "$PgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -c "DROP DATABASE IF EXISTS $restoreDb;"
