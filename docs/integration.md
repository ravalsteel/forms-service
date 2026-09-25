# Integration

| Dependency | How Forms uses it |
|------------|-------------------|
| IAM | JWKS validation; optional HTTP reconcile; Rabbit projections on `iam.*` |
| broker-service | Consume IAM events; publish outbox to `domain.events` |
| cache-service | Redis readiness (+ future idempotency/cache) |
| observability-service | OTLP traces; Prometheus on `:8095` |

## Outbound routing keys

`forms.form.created`, `forms.form.updated`, `forms.version.published`, `forms.version.archived`, `forms.run.created`, `forms.run.opened`, `forms.run.closed`, `forms.response.started`, `forms.response.submitted`, `forms.response.anonymized`, `forms.export.requested`, `forms.template.created`

Queue: `forms.domain-events`
