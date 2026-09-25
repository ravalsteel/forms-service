-- Forms domain: forms, versions, templates, runs, responses, invitations, exports, audit, files.

CREATE TABLE form (
    id              UUID PRIMARY KEY,
    company_id      UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    status          VARCHAR(32)  NOT NULL,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_form_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_form_company ON form (company_id);
CREATE INDEX idx_form_company_status ON form (company_id, status);
CREATE INDEX idx_form_company_updated ON form (company_id, updated_at DESC);

CREATE TABLE form_version (
    id              UUID PRIMARY KEY,
    form_id         UUID         NOT NULL REFERENCES form (id),
    version_number  INTEGER      NOT NULL,
    revision        BIGINT       NOT NULL DEFAULT 0,
    definition_json JSONB        NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    created_by      UUID         NOT NULL,
    published_by    UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_form_version_number UNIQUE (form_id, version_number),
    CONSTRAINT ck_form_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_form_version_number_positive CHECK (version_number >= 1)
);

CREATE INDEX idx_form_version_form ON form_version (form_id);
CREATE INDEX idx_form_version_form_status ON form_version (form_id, status);

CREATE TABLE form_template (
    id              UUID PRIMARY KEY,
    company_id      UUID,
    scope           VARCHAR(32)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    definition_json JSONB        NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_form_template_scope CHECK (scope IN ('COMPANY', 'GLOBAL')),
    CONSTRAINT ck_form_template_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_form_template_global_company CHECK (
        (scope = 'GLOBAL' AND company_id IS NULL) OR (scope = 'COMPANY' AND company_id IS NOT NULL)
    )
);

CREATE INDEX idx_form_template_company ON form_template (company_id);
CREATE INDEX idx_form_template_scope_status ON form_template (scope, status);

CREATE TABLE form_run (
    id                          UUID PRIMARY KEY,
    company_id                  UUID         NOT NULL,
    form_id                     UUID         NOT NULL REFERENCES form (id),
    form_version_id             UUID         NOT NULL REFERENCES form_version (id),
    name                        VARCHAR(255) NOT NULL,
    status                      VARCHAR(32)  NOT NULL,
    respondent_mode             VARCHAR(32)  NOT NULL,
    opens_at                    TIMESTAMPTZ,
    closes_at                   TIMESTAMPTZ,
    min_aggregation_threshold   INTEGER      NOT NULL DEFAULT 5,
    created_by                  UUID         NOT NULL,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_form_run_status CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_form_run_respondent_mode CHECK (respondent_mode IN ('IDENTIFIED', 'ANONYMOUS', 'PSEUDONYMOUS')),
    CONSTRAINT ck_form_run_threshold CHECK (min_aggregation_threshold >= 0)
);

CREATE INDEX idx_form_run_company ON form_run (company_id);
CREATE INDEX idx_form_run_form ON form_run (form_id);
CREATE INDEX idx_form_run_company_status ON form_run (company_id, status);
CREATE INDEX idx_form_run_version ON form_run (form_version_id);

CREATE TABLE response (
    id                  UUID PRIMARY KEY,
    company_id          UUID         NOT NULL,
    form_run_id         UUID         NOT NULL REFERENCES form_run (id),
    form_version_id     UUID         NOT NULL REFERENCES form_version (id),
    respondent_id       UUID,
    respondent_mode     VARCHAR(32)  NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    revision            BIGINT       NOT NULL DEFAULT 0,
    idempotency_key     VARCHAR(128),
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    submitted_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    anonymized_at       TIMESTAMPTZ,
    CONSTRAINT ck_response_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED')),
    CONSTRAINT ck_response_respondent_mode CHECK (respondent_mode IN ('IDENTIFIED', 'ANONYMOUS', 'PSEUDONYMOUS'))
);

CREATE UNIQUE INDEX uq_response_run_idempotency
    ON response (form_run_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_response_run ON response (form_run_id);
CREATE INDEX idx_response_run_status ON response (form_run_id, status);
CREATE INDEX idx_response_company ON response (company_id);
CREATE INDEX idx_response_respondent ON response (company_id, respondent_id);

CREATE TABLE response_answer (
    id              UUID PRIMARY KEY,
    response_id     UUID         NOT NULL REFERENCES response (id) ON DELETE CASCADE,
    question_id     UUID         NOT NULL,
    question_key    VARCHAR(128) NOT NULL,
    value_type      VARCHAR(32)  NOT NULL,
    text_value      TEXT,
    number_value    DOUBLE PRECISION,
    boolean_value   BOOLEAN,
    date_value      DATE,
    datetime_value  TIMESTAMPTZ,
    json_value      JSONB,
    object_id       UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_response_answer_question UNIQUE (response_id, question_id),
    CONSTRAINT ck_response_answer_value_type CHECK (value_type IN (
        'TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATETIME', 'JSON', 'FILE'
    ))
);

CREATE INDEX idx_response_answer_response ON response_answer (response_id);
CREATE INDEX idx_response_answer_question ON response_answer (question_id);
CREATE INDEX idx_response_answer_object ON response_answer (object_id) WHERE object_id IS NOT NULL;

CREATE TABLE invitation (
    id                      UUID PRIMARY KEY,
    company_id              UUID         NOT NULL,
    form_run_id             UUID         NOT NULL REFERENCES form_run (id),
    respondent_reference    VARCHAR(320) NOT NULL,
    token_hash              VARCHAR(128) NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    expires_at              TIMESTAMPTZ,
    used_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING', 'USED', 'EXPIRED', 'REVOKED'))
);

CREATE INDEX idx_invitation_run ON invitation (form_run_id);
CREATE INDEX idx_invitation_company_run ON invitation (company_id, form_run_id);
CREATE UNIQUE INDEX uq_invitation_token_hash ON invitation (token_hash);

CREATE TABLE export_job (
    id              UUID PRIMARY KEY,
    company_id      UUID         NOT NULL,
    form_run_id     UUID         NOT NULL REFERENCES form_run (id),
    status          VARCHAR(32)  NOT NULL,
    format          VARCHAR(32)  NOT NULL,
    created_by      UUID         NOT NULL,
    object_id       UUID,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    CONSTRAINT ck_export_job_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_export_job_format CHECK (format IN ('CSV', 'XLSX', 'JSON'))
);

CREATE INDEX idx_export_job_company ON export_job (company_id);
CREATE INDEX idx_export_job_run ON export_job (form_run_id);
CREATE INDEX idx_export_job_status ON export_job (status) WHERE status IN ('PENDING', 'PROCESSING');

CREATE TABLE forms_audit_event (
    id              UUID PRIMARY KEY,
    company_id      UUID         NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    actor_user_id   UUID,
    payload         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_forms_audit_company_created ON forms_audit_event (company_id, created_at DESC);
CREATE INDEX idx_forms_audit_aggregate ON forms_audit_event (aggregate_type, aggregate_id);

CREATE TABLE stored_file (
    id                  UUID PRIMARY KEY,
    company_id          UUID         NOT NULL,
    storage_key         VARCHAR(256) NOT NULL UNIQUE,
    content_type        VARCHAR(128) NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    original_filename   VARCHAR(512),
    created_by          UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stored_file_company ON stored_file (company_id, created_at DESC);
