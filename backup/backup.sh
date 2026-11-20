#!/bin/sh
set -eu

# Same script in every service. Database, prefix, and schedule come from compose.
# Uploads go straight to the BACKUP_R2_* bucket, not through object-storage.

: "${PGHOST:?}"
: "${PGUSER:?}"
: "${PGPASSWORD:?}"
: "${PGDATABASE:?}"
: "${BACKUP_PREFIX:?Set BACKUP_PREFIX}"
: "${AGE_RECIPIENT:?Set AGE_RECIPIENT (age1... public key)}"

. /usr/local/bin/r2.sh

BACKUP_DIR="${BACKUP_DIR:-/backups}"
BACKUP_STATUS_DIR="${BACKUP_STATUS_DIR:-/status}"
BACKUP_LOCAL_KEEP="${BACKUP_LOCAL_KEEP:-3}"
BACKUP_RECENT_KEEP_DAYS="${BACKUP_RECENT_KEEP_DAYS:-7}"
BACKUP_WEEKLY_KEEP_DAYS="${BACKUP_WEEKLY_KEEP_DAYS:-28}"
BACKUP_INTERVAL_SECONDS="${BACKUP_INTERVAL_SECONDS:-86400}"
BACKUP_SCHEDULE_OFFSET_SECONDS="${BACKUP_SCHEDULE_OFFSET_SECONDS:-${BACKUP_INITIAL_DELAY_SECONDS:-0}}"
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
NAME="${BACKUP_PREFIX}-${STAMP}.dump.age"
WORKDIR="${BACKUP_DIR}/.work"
mkdir -p "$BACKUP_DIR" "$WORKDIR"

# JSON status for console-service (shared host dir).
write_status() {
    ok="$1"
    error="$2"
    object_key="$3"
    finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    finished_unix=$(date -u +%s)
    # Escape for JSON string values.
    esc() {
        printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
    }
    err_json=null
    if [ -n "$error" ]; then
        err_json="\"$(esc "$error")\""
    fi
    obj_json=null
    if [ -n "$object_key" ]; then
        obj_json="\"$(esc "$object_key")\""
    fi
    body=$(printf '{"prefix":"%s","ok":%s,"finishedAt":"%s","finishedUnix":%s,"objectKey":%s,"error":%s,"intervalSeconds":%s,"scheduleOffsetSeconds":%s}\n' \
        "$(esc "$BACKUP_PREFIX")" \
        "$ok" \
        "$finished_at" \
        "$finished_unix" \
        "$obj_json" \
        "$err_json" \
        "$BACKUP_INTERVAL_SECONDS" \
        "$BACKUP_SCHEDULE_OFFSET_SECONDS")
    printf '%s' "$body" >"${BACKUP_DIR}/status.json"
    if [ -d "$BACKUP_STATUS_DIR" ] && [ -w "$BACKUP_STATUS_DIR" ]; then
        printf '%s' "$body" >"${BACKUP_STATUS_DIR}/${BACKUP_PREFIX}.json"
    fi
}

exec 9>"${BACKUP_DIR}/.lock"
if ! flock -n 9; then
    echo "backup already running, skipping"
    exit 0
fi

dump="${WORKDIR}/${BACKUP_PREFIX}-${STAMP}.dump"
encrypted="${BACKUP_DIR}/${NAME}"

echo "dumping ${PGDATABASE} from ${PGHOST}"
if ! pg_dump -Fc --no-owner --no-acl -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -f "$dump"; then
    write_status false "pg_dump failed" ""
    exit 1
fi
if ! age -r "$AGE_RECIPIENT" -o "$encrypted" "$dump"; then
    rm -f "$dump"
    write_status false "age encrypt failed" ""
    exit 1
fi
rm -f "$dump"
echo "encrypted local copy ${encrypted}"

# Keep the newest N local dumps.
# shellcheck disable=SC2086
ls -1t "${BACKUP_DIR}/${BACKUP_PREFIX}-"*.dump.age 2>/dev/null | tail -n +$((BACKUP_LOCAL_KEEP + 1)) | while read -r old; do
    rm -f "$old"
done

if [ -z "${BACKUP_R2_BUCKET:-}" ] || [ -z "${BACKUP_R2_ACCESS_KEY_ID:-}" ] || [ -z "${BACKUP_R2_SECRET_ACCESS_KEY:-}" ]; then
    echo "R2 backup credentials not set; local dump only (not 3-2-1)"
    write_status true "" ""
    exit 0
fi

if ! configure_r2; then
    write_status false "Set BACKUP_R2_ACCOUNT_ID or BACKUP_R2_ENDPOINT" ""
    echo "Set BACKUP_R2_ACCOUNT_ID or BACKUP_R2_ENDPOINT" >&2
    exit 1
fi

remote_recent="R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/recent/${NAME}"
object_key="${BACKUP_PREFIX}/recent/${NAME}"
echo "uploading ${remote_recent}"
if ! rclone copyto "$encrypted" "$remote_recent"; then
    write_status false "rclone upload failed" ""
    exit 1
fi

if [ "$(date -u +%u)" = "7" ]; then
    week=$(date -u +%G-W%V)
    remote_weekly="R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/weekly/${BACKUP_PREFIX}-${week}.dump.age"
    echo "weekly copy ${remote_weekly}"
    rclone copyto "$encrypted" "$remote_weekly" || true
fi

rclone delete "R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/recent/" --min-age "${BACKUP_RECENT_KEEP_DAYS}d" --include "*.dump.age" || true
rclone delete "R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/weekly/" --min-age "${BACKUP_WEEKLY_KEEP_DAYS}d" --include "*.dump.age" || true
echo "backup complete ${NAME}"
write_status true "" "$object_key"
