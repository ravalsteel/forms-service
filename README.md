# Forms Service

Central Forms / Questionnaire Service for the Raval Group platform.

## Identity

| Item | Value |
|------|-------|
| Service directory | `forms-service` |
| Spring app | `ravalgroups-forms` |
| Package | `com.ravalgroups.forms` |
| IAM application | `forms` |
| JWT audience | `forms-api` |
| API port | `8094` |
| Management port | `8095` |
| Postgres (host) | `5435` |

## Prerequisites

Shared platform services (start first):

- `cache-service` (Redis `:6379`)
- `broker-service` (RabbitMQ `:5672`)
- `iam-service` (JWKS `:8080`)
- Optional: `observability-service` (OTLP `:4318`)

Register in IAM catalog: application `forms` + technical service `forms-api`, then company/user grants.

## Local run

```bash
cp .env.example .env
docker compose -f docker-compose.dev.yml up --build
```

API: `http://localhost:8094`  
Swagger (dev): `http://localhost:8094/swagger-ui.html`  
Health: `http://localhost:8095/actuator/health`

## Build / test

```bash
cd app
# with local JDK 21 + Maven, or:
docker run --rm -v "$PWD":/src -w /src maven:3.9.11-eclipse-temurin-21 mvn -B test
```

## Architecture note

Object Storage, Notifications, and Audit are **not** separate platform services.
Forms embeds file storage (local/R2), notification adapters (log/SMTP), local domain audit, and transactional outbox — same pattern as Performance and Reminders.

See `docs/` for architecture, API, security, privacy, events, IAM integration, and deployment.
