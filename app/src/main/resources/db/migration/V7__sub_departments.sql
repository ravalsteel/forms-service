CREATE TABLE iam_sub_department_projection (
    sub_department_id   UUID PRIMARY KEY,
    company_id          UUID         NOT NULL,
    department_id       UUID         NOT NULL,
    code                VARCHAR(64),
    name                VARCHAR(255) NOT NULL,
    status              VARCHAR(64),
    source_version      BIGINT,
    synced_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_iam_sub_department_company ON iam_sub_department_projection (company_id);
CREATE INDEX idx_iam_sub_department_department ON iam_sub_department_projection (department_id);

ALTER TABLE iam_membership_projection
    ADD COLUMN sub_department_id UUID,
    ADD COLUMN sub_department_code VARCHAR(64),
    ADD COLUMN sub_department_name VARCHAR(255);

CREATE INDEX idx_iam_membership_sub_department ON iam_membership_projection (company_id, sub_department_id);
