# Data retention

- Question metadata may include `dataClassification` and `retention` (`RETAIN|ANONYMIZE|DELETE`).
- Manual anonymize: `POST /api/v1/responses/{id}/anonymize` (ADMIN).
- Scheduled job (`forms.retention.poll-interval-ms`) applies `ANONYMIZE`/`DELETE` retention on submitted answers, deletes associated files, and clears respondent id.
- Export files expire after 7 days metadata-wise.
- DB backups remain a platform/ops concern (not through the Forms object API).
