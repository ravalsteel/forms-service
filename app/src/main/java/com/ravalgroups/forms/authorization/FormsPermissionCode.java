package com.ravalgroups.forms.authorization;

import java.util.Set;

/** Global permission catalog codes (seeded in Flyway V3). */
public final class FormsPermissionCode {

    public static final String FORMS_READ = "forms.forms.read";
    public static final String FORMS_WRITE = "forms.forms.write";
    public static final String FORMS_DELETE = "forms.forms.delete";
    public static final String VERSIONS_READ = "forms.versions.read";
    public static final String VERSIONS_WRITE = "forms.versions.write";
    public static final String VERSIONS_PUBLISH = "forms.versions.publish";
    public static final String TEMPLATES_READ = "forms.templates.read";
    public static final String TEMPLATES_WRITE = "forms.templates.write";
    public static final String TEMPLATES_MANAGE_SYSTEM = "forms.templates.manage_system";
    public static final String RUNS_READ = "forms.runs.read";
    public static final String RUNS_MANAGE = "forms.runs.manage";
    public static final String INVITATIONS_MANAGE = "forms.invitations.manage";
    public static final String RESPONSES_READ = "forms.responses.read";
    public static final String RESPONSES_WRITE = "forms.responses.write";
    public static final String RESPONSES_ANONYMIZE = "forms.responses.anonymize";
    public static final String REPORTS_READ = "forms.reports.read";
    public static final String EXPORTS_CREATE = "forms.exports.create";
    public static final String FILES_UPLOAD = "forms.files.upload";
    public static final String FILES_MANAGE = "forms.files.manage";
    public static final String ROLES_READ = "forms.roles.read";
    public static final String ROLES_MANAGE = "forms.roles.manage";

    public static final Set<String> ALL = Set.of(
            FORMS_READ,
            FORMS_WRITE,
            FORMS_DELETE,
            VERSIONS_READ,
            VERSIONS_WRITE,
            VERSIONS_PUBLISH,
            TEMPLATES_READ,
            TEMPLATES_WRITE,
            TEMPLATES_MANAGE_SYSTEM,
            RUNS_READ,
            RUNS_MANAGE,
            INVITATIONS_MANAGE,
            RESPONSES_READ,
            RESPONSES_WRITE,
            RESPONSES_ANONYMIZE,
            REPORTS_READ,
            EXPORTS_CREATE,
            FILES_UPLOAD,
            FILES_MANAGE,
            ROLES_READ,
            ROLES_MANAGE);

    public static final Set<String> ADMIN_BUNDLE = ALL;

    public static final Set<String> DESIGNER_BUNDLE = Set.of(
            FORMS_READ,
            FORMS_WRITE,
            FORMS_DELETE,
            VERSIONS_READ,
            VERSIONS_WRITE,
            TEMPLATES_READ,
            TEMPLATES_WRITE,
            RUNS_READ,
            RESPONSES_READ,
            FILES_UPLOAD,
            ROLES_READ);

    public static final Set<String> PUBLISHER_BUNDLE = Set.of(
            FORMS_READ,
            VERSIONS_READ,
            VERSIONS_PUBLISH,
            RUNS_READ,
            RUNS_MANAGE,
            INVITATIONS_MANAGE,
            RESPONSES_READ,
            FILES_UPLOAD,
            ROLES_READ);

    public static final Set<String> ANALYST_BUNDLE = Set.of(
            FORMS_READ,
            VERSIONS_READ,
            RUNS_READ,
            RESPONSES_READ,
            REPORTS_READ,
            EXPORTS_CREATE,
            FILES_UPLOAD,
            ROLES_READ);

    public static final Set<String> RESPONDENT_BUNDLE = Set.of(
            FORMS_READ,
            VERSIONS_READ,
            RUNS_READ,
            RESPONSES_READ,
            RESPONSES_WRITE,
            FILES_UPLOAD,
            ROLES_READ);

    private FormsPermissionCode() {}
}
