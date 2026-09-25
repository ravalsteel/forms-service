# Events

Transactional outbox (`outbox_event`) + scheduled publisher.

Payload shape (no answer content):

```json
{
  "eventId": "uuid",
  "eventType": "forms.response.submitted",
  "companyId": "uuid",
  "aggregateType": "Response",
  "aggregateId": "uuid",
  "occurredAt": "...",
  "responseId": "uuid",
  "formRunId": "uuid",
  "formVersionId": "uuid"
}
```

Inbound IAM events are idempotent via `processed_event`.
