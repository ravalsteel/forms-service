# Domain model

```
FORM TEMPLATE ──instantiate──▶ FORM ──▶ FORM VERSION (immutable when PUBLISHED)
                                              │
                                              ▼
                                          FORM RUN
                                              │
                        ┌─────────────────────┼─────────────────────┬──────────────────┐
                        ▼                     ▼                     ▼                  ▼
                   INVITATION           SHARE LINK              RESPONSE       AUDIENCE RULE
                 (auth invite)        (public multi-use)                         (in-app fill)
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
- Identified/pseudonymous runs use **audience rules** (`ALL_COMPANY`, `DEPARTMENT`, `SUB_DEPARTMENT`, `USER`) for who can fill; `form_access` is design/admin only.
- Opening an identified run requires at least one audience rule when `audience_required` is true.
- Question identity is UUID + stable `key`, never array index.
- Domain IDs use UUIDv7 (client-provided v7 accepted).
