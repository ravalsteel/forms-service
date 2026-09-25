-- Foundation: IAM projection read models, outbox, notifications, forms roles.
-- No FK to IAM database. UUIDs are soft references.

CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(128) NOT NULL,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    routing_key     VARCHAR(128) NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT
);

CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_event (
    event_id        VARCHAR(128) PRIMARY KEY,
    event_type      VARCHAR(128) NOT NULL,
    source          VARCHAR(64)  NOT NULL,
    processed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE iam_company_projection (
    company_id      UUID PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(64),
    source_version  BIGINT,
    synced_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_iam_company_code ON iam_company_projection (code);

CREATE TABLE iam_department_projection (
    department_id   UUID PRIMARY KEY,
    company_id      UUID         NOT NULL,
    code            VARCHAR(64),
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(64),
    source_version  BIGINT,
    synced_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_iam_department_company ON iam_department_projection (company_id);

CREATE TABLE iam_user_projection (
    iam_user_id             UUID PRIMARY KEY,
    email                   VARCHAR(320),
    username                VARCHAR(128),
    first_name              VARCHAR(128),
    last_name               VARCHAR(128),
    display_name            VARCHAR(255),
    designation             VARCHAR(255),
    phone_number            VARCHAR(64),
    reporting_manager_id    UUID,
    status                  VARCHAR(64),
    source_version          BIGINT,
    synced_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_iam_user_email ON iam_user_projection (email);
CREATE INDEX idx_iam_user_display_name ON iam_user_projection (display_name);

CREATE TABLE iam_membership_projection (
    membership_id           UUID PRIMARY KEY,
    iam_user_id             UUID         NOT NULL,
    company_id              UUID         NOT NULL,
    employee_id             VARCHAR(64)  NOT NULL,
    status                  VARCHAR(64),
    department_id           UUID,
    department_code         VARCHAR(64),
    department_name         VARCHAR(255),
    company_code            VARCHAR(64),
    company_name            VARCHAR(255),
    username                VARCHAR(128),
    display_name            VARCHAR(255),
    email                   VARCHAR(320),
    expires_at              TIMESTAMPTZ,
    source_version          BIGINT,
    synced_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_iam_membership_company_employee UNIQUE (company_id, employee_id)
);

CREATE INDEX idx_iam_membership_user ON iam_membership_projection (iam_user_id);
CREATE INDEX idx_iam_membership_company_status ON iam_membership_projection (company_id, status);
CREATE INDEX idx_iam_membership_department ON iam_membership_projection (company_id, department_id);

CREATE TABLE notification_log (
    id                  UUID PRIMARY KEY,
    company_id          UUID,
    channel             VARCHAR(32)  NOT NULL,
    recipient           VARCHAR(320) NOT NULL,
    subject             VARCHAR(512),
    event_type          VARCHAR(128) NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    error_message       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL', 'LOG')),
    CONSTRAINT ck_notification_status CHECK (status IN ('SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_notification_log_created ON notification_log (created_at DESC);

CREATE TABLE forms_role_assignment (
    id                      UUID PRIMARY KEY,
    company_id              UUID         NOT NULL,
    iam_user_id             UUID         NOT NULL,
    role_code               VARCHAR(64)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_forms_role UNIQUE (company_id, iam_user_id, role_code),
    CONSTRAINT ck_forms_role CHECK (role_code IN ('ADMIN', 'DESIGNER', 'PUBLISHER', 'ANALYST', 'RESPONDENT'))
);

CREATE INDEX idx_forms_role_user ON forms_role_assignment (company_id, iam_user_id);
CREATE INDEX idx_forms_role_company_code ON forms_role_assignment (company_id, role_code);
