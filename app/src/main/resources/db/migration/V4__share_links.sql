-- Public multi-use share links for anonymous form collection.

CREATE TABLE form_share_link (
    id              UUID PRIMARY KEY,
    company_id      UUID         NOT NULL,
    form_run_id     UUID         NOT NULL REFERENCES form_run (id),
    token_hash      VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    label           VARCHAR(255),
    expires_at      TIMESTAMPTZ,
    max_responses   INTEGER,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ,
    CONSTRAINT ck_form_share_link_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_form_share_link_max_responses CHECK (max_responses IS NULL OR max_responses > 0)
);

CREATE UNIQUE INDEX uq_form_share_link_token_hash ON form_share_link (token_hash);
CREATE INDEX idx_form_share_link_run ON form_share_link (form_run_id);
CREATE INDEX idx_form_share_link_company_run ON form_share_link (company_id, form_run_id);
