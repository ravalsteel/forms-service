# Forms Service — Implementation Assessment

Phase 1 inspection of `/home/dev/ravalgroup` before coding.

## Platform inventory (what actually exists)

| Concern | Spec assumption | Reality |
|---------|-----------------|---------|
| IAM | Separate service | **Exists** — `iam-service` |
| Object Storage | Separate service | **Does not exist** — embedded `FileStoragePort` (local/R2) in Performance |
| Notifications | Separate service | **Does not exist** — embedded `NotificationPort` (log/SMTP) in Performance/Reminders |
| Audit | Separate service | **Does not exist** — IAM has IAM audit; business apps use local audit tables + outbox |
| RabbitMQ | Shared broker | **Exists** — `broker-service` (`domain.events`) |
| Redis | Shared cache | **Exists** — `cache-service` |
| Edge / Tunnel / Observability | Shared | **Exist** |

Sibling business apps: `performance-service` (8090/8091), `reminders-service` (8092/8093).  
**Forms will use 8094/8095 + Postgres host port 5435.**

## Reusable infrastructure (copy patterns, do not invent)

Primary reference: **reminders-service** (cleaner scaffold) + **performance-service** (file storage).

Copy/adapt:

- JWT resource-server validation (`iss`, JWKS, `aud`, `token_use=access`, `application`)
- `CurrentUser` from token claims; company from JWT only
- Local role assignments + imperative authorization service
- IAM projections via RabbitMQ `domain.events` + `processed_event`
- Transactional `outbox_event` + scheduled publisher
- Flyway + `hibernate.ddl-auto=validate`
- `ApiError` / `DomainException` / `PageResponse` / `X-Request-Id`
- Actuator split management port, OTEL, Prometheus
- Docker Compose pattern (app + postgres; Redis/Rabbit/IAM external)
- Embedded file storage (Performance `file` package) behind a port
- Embedded notification adapters (Reminders/Performance pattern)

## IAM integration (mandatory)

| Item | Value |
|------|-------|
| Application code | `forms` |
| Technical service / JWT `aud` | `forms-api` |
| Issuer | `IAM_ISSUER` (must match IAM `JWT_ISSUER`) |
| JWKS | `IAM_JWKS_URI` → `/.well-known/jwks.json` |
| Projection queue | `forms.domain-events` |
| Redis key prefix | `forms:` |

Flow: identity JWT → IAM context/launch → service access token → Forms validates locally (no IAM call per business request).

Register in IAM catalog (operator step, not code): application `forms` + service `forms-api` + company/user grants + CORS.

## Conflicts with master prompt — adapted decisions

| Spec says | Platform reality | Decision |
|-----------|------------------|----------|
| Call Object Storage Service | No such service | Embed `FileStoragePort` (local → R2) like Performance; store `object_id` in Forms DB |
| Call Notifications Service | No such service | Embed `NotificationPort` (log/SMTP); emit domain events for future consumers |
| Call Audit Service | No such service | Local `forms_audit_event` + outbox events; never duplicate IAM audit store |
| Do not put R2/S3 SDK in Forms | Performance already uses AWS S3 SDK for R2 | Same adapters; Forms never owns blob bytes in Postgres |
| UUIDv7 for all IDs | Siblings use `UUID.randomUUID()` (v4) | **Adopt UUIDv7** via `uuid-creator` — intentional Forms improvement for offline/index locality |
| Controller/service/repository packages | Modular hexagonal packages | Follow Performance/Reminders: `adapter.in/out`, `application`, `domain` per module |

## Naming / ports

| Item | Value |
|------|-------|
| Repo / service dir | `forms-service` |
| Maven artifact | `com.ravalgroups:forms` |
| Spring app name | `ravalgroups-forms` |
| Java package | `com.ravalgroups.forms` |
| API port | `8094` |
| Management port | `8095` |
| Postgres (host) | `5435` |
| Public hostname (future edge) | `service-forms.${ROOT_DOMAIN}` |

## Modules to create

```
forms/          templates/       versions/      questions+rules (in definition)
runs/           responses/       invitations/  respondents/
reporting/      exports/         file/         notification/
audit/ (outbox + domain audit)   iam/          authorization/
security/       shared/          observability/
```

## Files / areas to create

- New sibling repo: `/home/dev/ravalgroup/forms-service/` (same layout as reminders)
- Edge: later add `forms-api.conf.template` (not blocking V1 API)
- Observability: later `prometheus/targets/forms.yaml` → `:8095`
- IAM catalog registration (ops, not code)

## What not to build in V1

Per platform + prompt: no Kafka, no Qualtrics clone, no realtime collab, no Microsoft Forms importer core, no second IAM, no blob storage in Postgres, no distributed transactions.

## Implementation order (aligned with prompt phases)

1. Scaffold (Boot, Flyway foundation, Docker, observability, OpenAPI)
2. IAM JWT + company context + local roles
3. Form + FormVersion + optimistic locking + publish immutability
4. Schema definition (JSONB) + rule validation on publish
5. Templates
6. Runs
7. Responses (identified/anonymous, idempotency, autosave, submit)
8. File port + answer object_id
9. Reporting + exports (async via outbox/jobs)
10. Events, privacy/anonymization, Testcontainers suite, docs hardening
