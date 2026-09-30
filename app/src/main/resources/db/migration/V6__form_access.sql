-- Per-questionnaire ACL: creators grant VIEW / EDIT / MANAGE to other company users.
CREATE TABLE form_access (
    id              UUID PRIMARY KEY,
    company_id      UUID         NOT NULL,
    form_id         UUID         NOT NULL REFERENCES form (id) ON DELETE CASCADE,
    iam_user_id     UUID         NOT NULL,
    access_level    VARCHAR(16)  NOT NULL,
    granted_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_form_access_user UNIQUE (form_id, iam_user_id),
    CONSTRAINT ck_form_access_level CHECK (access_level IN ('VIEW', 'EDIT', 'MANAGE'))
);

CREATE INDEX idx_form_access_company_user ON form_access (company_id, iam_user_id);
CREATE INDEX idx_form_access_form ON form_access (form_id);
