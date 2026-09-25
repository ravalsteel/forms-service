package com.ravalgroups.forms.security;

/**
 * Claim names issued by IAM service tokens ({@code IssueServiceAccessCredential}).
 * Mirrored here — Forms does not depend on IAM JARs.
 */
public final class ServiceTokenClaims {

    public static final String TOKEN_USE = "token_use";
    public static final String TOKEN_USE_ACCESS = "access";
    public static final String APPLICATION = "application";
    public static final String COMPANY_ID = "company_id";
    public static final String EMPLOYEE_ID = "employee_id";
    public static final String SESSION_ID = "sid";
    public static final String AUTH_VERSION = "auth_version";
    public static final String CLIENT_ID = "client_id";

    private ServiceTokenClaims() {}
}
