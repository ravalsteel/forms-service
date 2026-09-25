# Architecture

```
                    ┌────────────────────┐
                    │      IAM Service   │
                    │ Identity + Access  │
                    └─────────┬──────────┘
                              │ service JWT (aud=forms-api)
                              ▼
┌──────────────┐      ┌───────────────────────┐
│ Web / Mobile │─────▶│   Forms Service       │
└──────────────┘      │ forms / versions /    │
                      │ templates / runs /    │
                      │ responses / reporting │
                      └──────┬───────┬────────┘
                             │       │
                   ┌─────────┘       └─────────────┐
                   ▼                               ▼
          ┌─────────────────┐             ┌─────────────────┐
          │ Embedded file   │             │ RabbitMQ/Outbox │
          │ storage (local) │             │ domain.events   │
          └─────────────────┘             └────────┬────────┘
                                                   │
                              ┌────────────────────┼──────────────┐
                              ▼                    ▼              ▼
                       Notifications            Local audit   Future consumers
                       (log/SMTP)               forms_audit
```

## Ownership

| Concern | Owner |
|---------|-------|
| Identity / login / JWT issuance | IAM |
| Form schema, runs, responses | Forms |
| Blob bytes | Forms file port (local now; R2-ready) |
| Email delivery | Forms notification adapters |
| Platform event transport | broker-service |

IAM answers “can this user enter Forms?”. Forms answers “what may they do inside Forms?” via local roles.
