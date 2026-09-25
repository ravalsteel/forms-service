# API

Base path: `/api/v1`  
Auth: `Authorization: Bearer <IAM service JWT aud=forms-api>`

## Forms / versions

- `POST/GET /forms`, `GET/PATCH/DELETE /forms/{id}`
- `POST/GET /forms/{id}/versions`
- `GET/PATCH /forms/{id}/versions/{versionId}`
- `POST .../publish`, `POST .../archive`

Draft PATCH requires `expectedRevision`.

## Templates

- `POST/GET/PATCH/DELETE /templates`
- `POST /templates/{id}/instantiate`

## Runs

- `POST/GET /forms/{formId}/runs`
- `GET /runs/{runId}`
- `POST /runs/{runId}/open|close|cancel`

## Responses

- `POST /runs/{runId}/responses` (+ optional `Idempotency-Key`, client `responseId`)
- `GET/PATCH /responses/{responseId}`
- `POST /responses/{responseId}/submit` (body may include `formVersionId`)
- `POST /responses/{responseId}/anonymize`

## Reporting / exports / files / invitations

- `GET /runs/{runId}/summary`
- `GET /runs/{runId}/questions/{questionId}/results`
- `POST /runs/{runId}/exports`, `GET /exports/{id}`, `GET /exports/{id}/download` (CSV | XLSX | JSON)
- `POST /files`, `GET /files/{id}/download`, `DELETE /files/{id}`
- `GET/POST /runs/{runId}/invitations`
- `POST /invitations/{id}/revoke`
- `POST /invitations/consume` (token; requires Forms JWT for eligibility)

## Roles

`ADMIN`, `DESIGNER`, `PUBLISHER`, `ANALYST`, `RESPONDENT` via `/api/v1/roles` (+ `/bootstrap`).
