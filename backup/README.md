# Postgres backups (3-2-1)

Encrypted `pg_dump -Fc` nightly. Local copies on the host (last 2). Offsite copies in a dedicated Cloudflare R2 bucket (7 daily + 4 weekly). Use a **backups** bucket (not an uploads bucket).

```bash
mkdir -p secrets/backup
age-keygen -o secrets/backup/age.key
# put the printed age1… public key in AGE_RECIPIENT
```

```bash
docker compose --profile backup up -d --build forms-backup
```

## Restore drill (throwaway Postgres, never production)

```bash
docker run --rm --name forms-restore-pg -e POSTGRES_PASSWORD=restore -e POSTGRES_DB=forms \
  -p 127.0.0.1:55435:5432 postgres:16-alpine

docker compose --profile backup run --rm \
  -e BACKUP_ALLOW_RESTORE=yes \
  -e PGHOST=host.docker.internal \
  -e PGPORT=55435 \
  -e PGDATABASE=forms \
  --entrypoint /usr/local/bin/restore.sh \
  forms-backup latest
```

Monthly: restore from R2, confirm schema, discard the container.
