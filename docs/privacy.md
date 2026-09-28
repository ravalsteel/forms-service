# Privacy

- `IDENTIFIED` — stores IAM user id on response; fill via authenticated app.
- `ANONYMOUS` — `respondent_id` is null; audit actor is null on submit. May be filled by authenticated users **or** via a public share link (`/api/v1/public/share/{token}`).
- `PSEUDONYMOUS` — currently stores user id for eligibility linkage; treat reporting carefully (tighten in later phase).
- Anonymous question results respect `min_aggregation_threshold`.
- Anonymize endpoint clears respondent id and scrubs TEXT/JSON/FILE/DATE answers while retaining numeric/boolean aggregates where present.
- Do not log answer payloads or anonymous respondent identity.
- Public share links store only a SHA-256 token hash; revoke/expiry/`maxResponses` gate further submissions.
