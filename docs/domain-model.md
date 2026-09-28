# Domain model

```
FORM TEMPLATE ──instantiate──▶ FORM ──▶ FORM VERSION (immutable when PUBLISHED)
                                              │
                                              ▼
                                          FORM RUN
                                              │
                        ┌─────────────────────┼─────────────────────┐
                        ▼                     ▼                     ▼
                   INVITATION           SHARE LINK              RESPONSE ──▶ RESPONSE ANSWER
                 (auth invite)        (public multi-use)
```

## Lifecycles

| Aggregate | States |
|-----------|--------|
| Form | ACTIVE → ARCHIVED |
| FormVersion | DRAFT → PUBLISHED → ARCHIVED |
| FormRun | SCHEDULED → OPEN → CLOSED / CANCELLED |
| Response | IN_PROGRESS → SUBMITTED (+ anonymized_at) |

## Invariants

- Published versions are immutable; drafts use optimistic `revision`.
- Every response binds to exactly one run and one form version.
- Submit payloads that disagree with the run version fail with `FORM_VERSION_MISMATCH`.
- Anonymous runs store `respondent_id = null`.
- Public share links bind to ANONYMOUS runs only; token is hashed at rest and may expire or cap submissions.
- Question identity is UUID + stable `key`, never array index.
- Domain IDs use UUIDv7 (client-provided v7 accepted).
