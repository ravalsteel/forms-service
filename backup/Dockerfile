# Identical in every Postgres service. Copy this directory across services when it changes.
FROM postgres:16-alpine
RUN apk add --no-cache age rclone tzdata findutils
COPY r2.sh backup.sh loop.sh restore.sh /usr/local/bin/
RUN chmod 755 /usr/local/bin/r2.sh /usr/local/bin/backup.sh /usr/local/bin/loop.sh /usr/local/bin/restore.sh \
    && mkdir -p /backups \
    && chown postgres:postgres /backups
USER postgres
ENV TZ=UTC
WORKDIR /backups
ENTRYPOINT ["/usr/local/bin/loop.sh"]
