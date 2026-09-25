# Privacy

- `IDENTIFIED` — stores IAM user id on response.
- `ANONYMOUS` — eligibility may be authenticated; `respondent_id` is null; audit actor is null on submit.
- `PSEUDONYMOUS` — currently stores user id for eligibility linkage; treat reporting carefully (tighten in later phase).
- Anonymous question results respect `min_aggregation_threshold`.
- Anonymize endpoint clears respondent id and scrubs TEXT/JSON/FILE/DATE answers while retaining numeric/boolean aggregates where present.
- Do not log answer payloads or anonymous respondent identity.
