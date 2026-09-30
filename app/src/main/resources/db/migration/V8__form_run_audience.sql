-- Run-level audience for identified / pseudonymous collections.
-- form_access remains design/admin only; respondents use these rules + /me/collections.

ALTER TABLE form_run
    ADD COLUMN audience_required BOOLEAN NOT NULL DEFAULT TRUE;

-- Anonymous public collections do not use audience rules.
UPDATE form_run
SET audience_required = FALSE
WHERE respondent_mode = 'ANONYMOUS';

CREATE TABLE form_run_audience_rule (
    id           UUID PRIMARY KEY,
    form_run_id  UUID         NOT NULL REFERENCES form_run (id) ON DELETE CASCADE,
    company_id   UUID         NOT NULL,
    rule_type    VARCHAR(32)  NOT NULL,
    target_id    UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_form_run_audience_rule_type CHECK (
        rule_type IN ('ALL_COMPANY', 'DEPARTMENT', 'SUB_DEPARTMENT', 'USER')
    ),
    CONSTRAINT ck_form_run_audience_target CHECK (
        (rule_type = 'ALL_COMPANY' AND target_id IS NULL)
            OR (rule_type <> 'ALL_COMPANY' AND target_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_form_run_audience_rule
    ON form_run_audience_rule (
        form_run_id,
        rule_type,
        (COALESCE(target_id, '00000000-0000-0000-0000-000000000000'))
    );

CREATE INDEX idx_form_run_audience_run ON form_run_audience_rule (form_run_id);
CREATE INDEX idx_form_run_audience_company ON form_run_audience_rule (company_id);
