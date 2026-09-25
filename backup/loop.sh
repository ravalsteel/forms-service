#!/bin/sh
set -eu

INTERVAL="${BACKUP_INTERVAL_SECONDS:-21600}"

echo "backup loop every ${INTERVAL}s"
while true; do
    if /usr/local/bin/backup.sh; then
        echo "backup ok $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    else
        echo "backup failed $(date -u +%Y-%m-%dT%H:%M:%SZ)" >&2
    fi
    sleep "$INTERVAL"
done
