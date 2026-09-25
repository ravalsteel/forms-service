# IAM integration (Forms)

Mirrors Reminders/Performance. Values follow live IAM contracts.

## Tokens

| Kind | `aud` | `token_use` | Accepted by Forms? |
|------|-------|-------------|--------------------|
| Identity | `iam-auth` | `identity` | **No** |
| Service access | `forms-api` | `access` | **Yes** |

Service claims used: `sub`, `application=forms`, `company_id`, `employee_id` (optional), `sid`, `auth_version`, `client_id` (machine).

JWKS: `GET /.well-known/jwks.json` on IAM. Forms caches JWKS and validates locally — no IAM call per business request.

## Catalog registration

1. Application code: `forms` (typically COMPANY scope)
2. Technical service code / audience: `forms-api`
3. Company + user application access grants
4. Portal launch URL for future forms portal
5. CORS for Forms FE origin

## Context selection

`POST /api/v1/auth/context` with `{ applicationCode: "forms", companyId, serviceCode: "forms-api" }`

## Projections

Queue `forms.domain-events` bound to `iam.user.*`, `iam.membership.*`, `iam.company.*`, `iam.department.*`, `iam.application_access.*` on exchange `domain.events`.

Soft UUID references only — never FK to IAM DB.

## Env

```
IAM_ISSUER=
IAM_JWKS_URI=
IAM_BASE_URL=
FORMS_AUDIENCE=forms-api
FORMS_APPLICATION_CODE=forms
IAM_SERVICE_BEARER_TOKEN=   # optional reconcile
```
