#!/bin/sh
set -eu

# Sleep until the next UTC wall-clock slot, then run.
# INTERVAL 86400 → 00:00. INTERVAL 21600 → 00:00 / 06:00 / 12:00 / 18:00.
# BACKUP_SCHEDULE_OFFSET_SECONDS (or legacy BACKUP_INITIAL_DELAY_SECONDS) is
# added after that slot so siblings do not dump in the same second.

INTERVAL="${BACKUP_INTERVAL_SECONDS:-21600}"
OFFSET="${BACKUP_SCHEDULE_OFFSET_SECONDS:-${BACKUP_INITIAL_DELAY_SECONDS:-0}}"

case "$INTERVAL" in
    ''|*[!0-9]*)
        echo "BACKUP_INTERVAL_SECONDS must be a positive integer" >&2
        exit 1
        ;;
esac
if [ "$INTERVAL" -le 0 ]; then
    echo "BACKUP_INTERVAL_SECONDS must be > 0" >&2
    exit 1
fi

case "$OFFSET" in
    ''|*[!0-9]*)
        echo "BACKUP_SCHEDULE_OFFSET_SECONDS must be a non-negative integer" >&2
        exit 1
        ;;
esac

echo "backup loop every ${INTERVAL}s UTC (offset ${OFFSET}s after slot)"

seconds_until_next_slot() {
    now=$(date -u +%s)
    # Unix epoch is midnight UTC, so multiples of INTERVAL land on the slots above.
    next=$(( (now / INTERVAL + 1) * INTERVAL ))
    wait=$(( next - now + OFFSET ))
    if [ "$wait" -lt 1 ]; then
        wait=1
    fi
    echo "$wait"
}

while true; do
    wait=$(seconds_until_next_slot)
    echo "next backup in ${wait}s ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
    sleep "$wait"

    if /usr/local/bin/backup.sh; then
        echo "backup ok $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    else
        echo "backup failed $(date -u +%Y-%m-%dT%H:%M:%SZ)" >&2
    fi
done
