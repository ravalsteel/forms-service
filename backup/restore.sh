#!/bin/sh
set -eu

# Decrypt a dump and pg_restore into PGHOST.
# Refuses production unless BACKUP_ALLOW_RESTORE=yes and you point PGHOST
# at a throwaway Postgres (not iam-postgres / reminders-postgres / performance-postgres).

if [ "${BACKUP_ALLOW_RESTORE:-}" != "yes" ]; then
    echo "Refusing restore. Set BACKUP_ALLOW_RESTORE=yes and PGHOST to a throwaway Postgres." >&2
    exit 1
fi

case "${PGHOST:-}" in
    iam-postgres|reminders-postgres|performance-postgres|"")
        echo "PGHOST=${PGHOST:-unset} looks like production. Point it at a disposable database." >&2
        exit 1
        ;;
esac

: "${PGUSER:?}"
: "${PGPASSWORD:?}"
: "${PGDATABASE:?}"
: "${AGE_IDENTITY_FILE:?Mount secrets/backup/age.key as AGE_IDENTITY_FILE}"

BACKUP_PREFIX="${BACKUP_PREFIX:-iam}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
SOURCE="${1:-latest}"

pick_local_latest() {
    ls -1t "${BACKUP_DIR}/${BACKUP_PREFIX}-"*.dump.age 2>/dev/null | head -n 1
}

resolve_source() {
    src="$1"
    if [ "$src" = "latest" ]; then
        local_latest=$(pick_local_latest || true)
        if [ -n "$local_latest" ]; then
            echo "$local_latest"
            return
        fi
        if [ -n "${BACKUP_R2_BUCKET:-}" ]; then
            newest=$(rclone lsf "R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/recent/" --files-only | sort | tail -n 1)
            if [ -z "$newest" ]; then
                echo "No local or R2 dumps found" >&2
                exit 1
            fi
            dest="${BACKUP_DIR}/.work/${newest}"
            mkdir -p "${BACKUP_DIR}/.work"
            rclone copyto "R2:${BACKUP_R2_BUCKET}/${BACKUP_PREFIX}/recent/${newest}" "$dest"
            echo "$dest"
            return
        fi
        echo "No dumps found" >&2
        exit 1
    fi
    if [ -f "$src" ]; then
        echo "$src"
        return
    fi
    dest="${BACKUP_DIR}/.work/$(basename "$src")"
    mkdir -p "${BACKUP_DIR}/.work"
    rclone copyto "R2:${BACKUP_R2_BUCKET}/${src}" "$dest"
    echo "$dest"
}

configure_rclone() {
    account="${BACKUP_R2_ACCOUNT_ID:-}"
    endpoint="${BACKUP_R2_ENDPOINT:-}"
    if [ -z "$endpoint" ] && [ -n "$account" ]; then
        endpoint="https://${account}.r2.cloudflarestorage.com"
    fi
    [ -z "$endpoint" ] && return 0
    export RCLONE_CONFIG_R2_TYPE=s3
    export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
    export RCLONE_CONFIG_R2_ACCESS_KEY_ID="${BACKUP_R2_ACCESS_KEY_ID:-}"
    export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="${BACKUP_R2_SECRET_ACCESS_KEY:-}"
    export RCLONE_CONFIG_R2_ENDPOINT="$endpoint"
    export RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true
}

configure_rclone
age_file=$(resolve_source "$SOURCE")
plain="${age_file%.age}"
if [ "$plain" = "$age_file" ]; then
    plain="${age_file}.dump"
fi
age -d -i "$AGE_IDENTITY_FILE" -o "$plain" "$age_file"
echo "restoring ${plain} into ${PGHOST}/${PGDATABASE}"
pg_restore --clean --if-exists --no-owner --no-acl -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" "$plain"
echo "restore complete"
