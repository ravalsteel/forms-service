#!/bin/sh
# Sourced by backup.sh and restore.sh. Configures an rclone remote named R2.

configure_r2() {
    account="${BACKUP_R2_ACCOUNT_ID:-}"
    endpoint="${BACKUP_R2_ENDPOINT:-}"
    if [ -z "$endpoint" ] && [ -n "$account" ]; then
        endpoint="https://${account}.r2.cloudflarestorage.com"
    fi
    if [ -z "$endpoint" ]; then
        return 1
    fi
    export RCLONE_CONFIG_R2_TYPE=s3
    export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
    export RCLONE_CONFIG_R2_ACCESS_KEY_ID="${BACKUP_R2_ACCESS_KEY_ID:-}"
    export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="${BACKUP_R2_SECRET_ACCESS_KEY:-}"
    export RCLONE_CONFIG_R2_ENDPOINT="$endpoint"
    export RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true
    export RCLONE_CONFIG_R2_ACL=private
    return 0
}
