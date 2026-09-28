# Security

- Validate IAM JWT locally: issuer, JWKS, `aud=forms-api`, `token_use=access`, `application=forms`, `sub`.
- Company scope comes from token `company_id` — never trust client-supplied company.
- Cross-company access → `CROSS_COMPANY_ACCESS_DENIED`.
- Identity tokens (`aud=iam-auth`) are rejected.
- Business authorization is local Forms roles, not IAM permission claims.
- Public share endpoints under `/api/v1/public/**` are unauthenticated; capability is the raw share token (hashed at rest). Soft Redis rate limits apply.

See also `IAM_INTEGRATION.md`.
