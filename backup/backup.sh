#!/bin/sh
set -eu

: "${PGHOST:?}"
: "${PGUSER:?}"
: "${PGPASSWORD:?}"
: "${PGDATABASE:?}"
: "${AGE_RECIPIENT:?Set AGE_RECIPIENT (age1... public key)}"

BACKUP_PREFIX="${BACKUP_PREFIX:-iam}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
BACKUP_LOCAL_KEEP="${BACKUP_LOCAL_KEEP:-3}"
BACKUP_RECENT_KEEP_DAYS="${BACKUP_RECENT_KEEP_DAYS:-7}"
BACKUP_WEEKLY_KEEP_DAYS="${BACKUP_WEEKLY_KEEP_DAYS:-28}"
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
NAME="${BACKUP_PREFIX}-${STAMP}.dump.age"
WORKDIR="${BACKUP_DIR}/.work"
mkdir -p "$BACKUP_DIR" "$WORKDIR"

exec 9>"${BACKUP_DIR}/.lock"
if ! flock -n 9; then
    echo "backup already running, skipping"
    exit 0
fi

dump="${WORKDIR}/${BACKUP_PREFIX}-${STAMP}.dump"
encrypted="${BACKUP_DIR}/${NAME}"

echo "dumping ${PGDATABASE} from ${PGHOST}"
pg_dump -Fc --no-owner --no-acl -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -f "$dump"
age -r "$AGE_RECIPIENT" -o "$encrypted" "$dump"
rm -f "$dump"
echo "encrypted local copy ${encrypted}"

# Keep the newest N local dumps.
# shellcheck disable=SC2086
ls -1t "${BACKUP_DIR}/${BACKUP_PREFIX}-"*.dump.age 2>/dev/null | tail -n +$((BACKUP_LOCAL_KEEP + 1)) | while read -r old; do
    rm -f "$old"
done

if [ -z "${BACKUP_R2_BUCKET:-}" ] || [ -z "${BACKUP_R2_ACCESS_KEY_ID:-}" ] || [ -z "${BACKUP_R2_SECRET_ACCESS_KEY:-}" ]; then
    echo "R2 backup credentials not set; local dump only (not 3-2-1)"
    exit 0
fi

account="${BACKUP_R2_ACCOUNT_ID:-}"
endpoint="${BACKUP_R2_ENDPOINT:-}"
if [ -z "$endpoint" ]; then
    if [ -z "$account" ]; then
        echo "Set BACKUP_R2_ACCOUNT_ID or BACKUP_R2_ENDPOINT" >&2
        exit 1
    fi
    endpoint="https://${account}.r2.cloudflarestorage.com"
fi

export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="${BACKUP_R2_ACCESS_KEY_ID}"
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="${BACKUP_R2_SECRET_ACCESS_KEY}"
export RCLONE_CONFIG_R2_ENDPOINT="$endpoint"
export RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true
export RCLONE_CONFIG_R2_ACL=private

remote_recent="R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/recent/${NAME}"
echo "uploading ${remote_recent}"
rclone copyto "$encrypted" "$remote_recent"

if [ "$(date -u +%u)" = "7" ]; then
    week=$(date -u +%G-W%V)
    remote_weekly="R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/weekly/${BACKUP_PREFIX}-${week}.dump.age"
    echo "weekly copy ${remote_weekly}"
    rclone copyto "$encrypted" "$remote_weekly"
fi

rclone delete "R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/recent/" --min-age "${BACKUP_RECENT_KEEP_DAYS}d" --include "*.dump.age" || true
rclone delete "R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/weekly/" --min-age "${BACKUP_WEEKLY_KEEP_DAYS}d" --include "*.dump.age" || true
echo "backup complete ${NAME}"
