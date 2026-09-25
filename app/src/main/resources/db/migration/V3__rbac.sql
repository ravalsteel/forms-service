-- Custom company roles + resource permission catalog.
-- Migrates fixed-string forms_role_assignment into forms_user_role.

CREATE TABLE forms_permission (
    code            VARCHAR(128) PRIMARY KEY,
    resource        VARCHAR(64)  NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    description     VARCHAR(512)
);

CREATE TABLE forms_role (
    id              UUID PRIMARY KEY,
    company_id      UUID         NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(512),
    system_defined  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    UNIQUE (company_id, code)
);

CREATE INDEX idx_forms_role_company ON forms_role (company_id);

CREATE TABLE forms_role_permission (
    role_id             UUID         NOT NULL REFERENCES forms_role(id) ON DELETE CASCADE,
    permission_code     VARCHAR(128) NOT NULL REFERENCES forms_permission(code),
    PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE forms_user_role (
    id              UUID PRIMARY KEY,
    company_id      UUID         NOT NULL,
    iam_user_id     UUID         NOT NULL,
    role_id         UUID         NOT NULL REFERENCES forms_role(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL,
    UNIQUE (company_id, iam_user_id, role_id)
);

CREATE INDEX idx_forms_user_role_user ON forms_user_role (company_id, iam_user_id);
CREATE INDEX idx_forms_user_role_role ON forms_user_role (role_id);

INSERT INTO forms_permission (code, resource, action, description) VALUES
    ('forms.forms.read',            'forms',       'read',          'View forms'),
    ('forms.forms.write',           'forms',       'write',         'Create and update forms'),
    ('forms.forms.delete',          'forms',       'delete',        'Delete forms'),
    ('forms.versions.read',         'versions',    'read',          'View form versions'),
    ('forms.versions.write',        'versions',    'write',         'Create and edit form versions'),
    ('forms.versions.publish',      'versions',    'publish',       'Publish form versions'),
    ('forms.templates.read',        'templates',   'read',          'View templates'),
    ('forms.templates.write',       'templates',   'write',         'Create and update templates'),
    ('forms.templates.manage_system','templates',  'manage_system', 'Manage system templates'),
    ('forms.runs.read',             'runs',        'read',          'View form runs'),
    ('forms.runs.manage',           'runs',        'manage',        'Create and manage form runs'),
    ('forms.invitations.manage',    'invitations', 'manage',        'Manage invitations'),
    ('forms.responses.read',        'responses',   'read',          'View responses'),
    ('forms.responses.write',       'responses',   'write',         'Submit and update responses'),
    ('forms.responses.anonymize',   'responses',   'anonymize',     'Anonymize responses'),
    ('forms.reports.read',          'reports',     'read',          'View reports and analytics'),
    ('forms.exports.create',        'exports',     'create',        'Create exports'),
    ('forms.files.upload',          'files',       'upload',        'Upload files'),
    ('forms.files.manage',          'files',       'manage',        'Manage files owned by others'),
    ('forms.roles.read',            'roles',       'read',          'View role definitions and assignments'),
    ('forms.roles.manage',          'roles',       'manage',        'Create, update, delete roles and assign them');

-- Seed system roles for every company that already has assignments, then map users.
DO $$
DECLARE
    cid UUID;
    admin_id UUID;
    designer_id UUID;
    publisher_id UUID;
    analyst_id UUID;
    respondent_id UUID;
    now_ts TIMESTAMPTZ := NOW();
BEGIN
    FOR cid IN SELECT DISTINCT company_id FROM forms_role_assignment
    LOOP
        admin_id := gen_random_uuid();
        designer_id := gen_random_uuid();
        publisher_id := gen_random_uuid();
        analyst_id := gen_random_uuid();
        respondent_id := gen_random_uuid();

        INSERT INTO forms_role (id, company_id, code, name, description, system_defined, created_at, updated_at)
        VALUES
            (admin_id,      cid, 'ADMIN',      'Administrator', 'Full access including role management', TRUE, now_ts, now_ts),
            (designer_id,   cid, 'DESIGNER',   'Designer',      'Design forms and versions',              TRUE, now_ts, now_ts),
            (publisher_id,  cid, 'PUBLISHER',  'Publisher',     'Publish versions and manage runs',       TRUE, now_ts, now_ts),
            (analyst_id,    cid, 'ANALYST',    'Analyst',       'Read responses, reports, and exports',   TRUE, now_ts, now_ts),
            (respondent_id, cid, 'RESPONDENT', 'Respondent',    'Respond to assigned form runs',          TRUE, now_ts, now_ts);

        INSERT INTO forms_role_permission (role_id, permission_code)
        SELECT admin_id, code FROM forms_permission;

        INSERT INTO forms_role_permission (role_id, permission_code) VALUES
            (designer_id, 'forms.forms.read'),
            (designer_id, 'forms.forms.write'),
            (designer_id, 'forms.forms.delete'),
            (designer_id, 'forms.versions.read'),
            (designer_id, 'forms.versions.write'),
            (designer_id, 'forms.templates.read'),
            (designer_id, 'forms.templates.write'),
            (designer_id, 'forms.runs.read'),
            (designer_id, 'forms.responses.read'),
            (designer_id, 'forms.files.upload'),
            (designer_id, 'forms.roles.read');

        INSERT INTO forms_role_permission (role_id, permission_code) VALUES
            (publisher_id, 'forms.forms.read'),
            (publisher_id, 'forms.versions.read'),
            (publisher_id, 'forms.versions.publish'),
            (publisher_id, 'forms.runs.read'),
            (publisher_id, 'forms.runs.manage'),
            (publisher_id, 'forms.invitations.manage'),
            (publisher_id, 'forms.responses.read'),
            (publisher_id, 'forms.files.upload'),
            (publisher_id, 'forms.roles.read');

        INSERT INTO forms_role_permission (role_id, permission_code) VALUES
            (analyst_id, 'forms.forms.read'),
            (analyst_id, 'forms.versions.read'),
            (analyst_id, 'forms.runs.read'),
            (analyst_id, 'forms.responses.read'),
            (analyst_id, 'forms.reports.read'),
            (analyst_id, 'forms.exports.create'),
            (analyst_id, 'forms.files.upload'),
            (analyst_id, 'forms.roles.read');

        INSERT INTO forms_role_permission (role_id, permission_code) VALUES
            (respondent_id, 'forms.forms.read'),
            (respondent_id, 'forms.versions.read'),
            (respondent_id, 'forms.runs.read'),
            (respondent_id, 'forms.responses.read'),
            (respondent_id, 'forms.responses.write'),
            (respondent_id, 'forms.files.upload'),
            (respondent_id, 'forms.roles.read');

        INSERT INTO forms_user_role (id, company_id, iam_user_id, role_id, created_at)
        SELECT gen_random_uuid(), a.company_id, a.iam_user_id,
               CASE a.role_code
                   WHEN 'ADMIN'      THEN admin_id
                   WHEN 'DESIGNER'   THEN designer_id
                   WHEN 'PUBLISHER'  THEN publisher_id
                   WHEN 'ANALYST'    THEN analyst_id
                   WHEN 'RESPONDENT' THEN respondent_id
               END,
               a.created_at
        FROM forms_role_assignment a
        WHERE a.company_id = cid
          AND a.role_code IN ('ADMIN', 'DESIGNER', 'PUBLISHER', 'ANALYST', 'RESPONDENT');
    END LOOP;
END $$;

DROP TABLE forms_role_assignment;
