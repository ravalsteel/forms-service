# Deployment

| Port | Use |
|------|-----|
| 8094 | API |
| 8095 | Actuator / Prometheus |
| 5435 | Postgres (host) |

```bash
cp .env.example .env
# start cache-service, broker-service, iam-service first
docker compose -f docker-compose.dev.yml up --build
```

Register IAM application `forms` + service `forms-api`, grant company/users, set CORS.

Production compose follows Performance/Reminders pattern (`docker-compose.yml`).
