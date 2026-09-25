FROM postgres:16-alpine
RUN apk add --no-cache age rclone tzdata findutils
COPY backup.sh loop.sh restore.sh /usr/local/bin/
RUN chmod 755 /usr/local/bin/backup.sh /usr/local/bin/loop.sh /usr/local/bin/restore.sh \
    && mkdir -p /backups \
    && chown postgres:postgres /backups
USER postgres
ENV TZ=UTC
WORKDIR /backups
ENTRYPOINT ["/usr/local/bin/loop.sh"]
