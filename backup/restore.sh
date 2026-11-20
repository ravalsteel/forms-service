#!/bin/sh
set -eu

# Decrypt a dump and pg_restore into PGHOST.
# Refuses every production Postgres service unless you point PGHOST at a
# throwaway database and set BACKUP_ALLOW_RESTORE=yes.

if [ "${BACKUP_ALLOW_RESTORE:-}" != "yes" ]; then
    echo "Refusing restore. Set BACKUP_ALLOW_RESTORE=yes and PGHOST to a throwaway Postgres." >&2
    exit 1
fi

host="${PGHOST:-}"
if [ -z "$host" ]; then
    echo "PGHOST is unset. Point it at a disposable database." >&2
    exit 1
fi

refuse="${BACKUP_REFUSE_HOSTS:-iam-postgres,performance-postgres,reminders-postgres,forms-postgres,messaging-postgres,object-storage-postgres,inference-postgres}"
old_ifs=$IFS
IFS=,
for blocked in $refuse; do
    if [ "$host" = "$blocked" ]; then
        echo "PGHOST=${host} looks like production. Point it at a disposable database." >&2
        exit 1
    fi
done
IFS=$old_ifs

: "${PGUSER:?}"
: "${PGPASSWORD:?}"
: "${PGDATABASE:?}"
: "${BACKUP_PREFIX:?Set BACKUP_PREFIX}"
: "${AGE_IDENTITY_FILE:?Mount secrets/backup/age.key as AGE_IDENTITY_FILE}"

. /usr/local/bin/r2.sh

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

configure_r2 || true
age_file=$(resolve_source "$SOURCE")
plain="${age_file%.age}"
if [ "$plain" = "$age_file" ]; then
    plain="${age_file}.dump"
fi
age -d -i "$AGE_IDENTITY_FILE" -o "$plain" "$age_file"
echo "restoring ${plain} into ${PGHOST}/${PGDATABASE}"
pg_restore --clean --if-exists --no-owner --no-acl -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" "$plain"
echo "restore complete"
