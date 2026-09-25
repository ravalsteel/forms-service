package com.ravalgroups.forms.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Authenticated Forms request context derived from a validated IAM service JWT.
 */
public record CurrentUser(
        UUID userId,
        UUID companyId,
        String employeeId,
        String application,
        String sessionId,
        Long authVersion,
        String clientId) {

    public boolean hasCompanyContext() {
        return companyId != null;
    }

    public void requireCompany(UUID resourceCompanyId) {
        if (companyId == null || resourceCompanyId == null || !companyId.equals(resourceCompanyId)) {
            throw new com.ravalgroups.forms.shared.exception.DomainException(
                    "CROSS_COMPANY_ACCESS_DENIED", "Company context does not match resource");
        }
    }

    public static CurrentUser fromJwt(org.springframework.security.oauth2.jwt.Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID companyId = parseUuid(jwt.getClaimAsString(ServiceTokenClaims.COMPANY_ID));
        String employeeId = jwt.getClaimAsString(ServiceTokenClaims.EMPLOYEE_ID);
        String application = jwt.getClaimAsString(ServiceTokenClaims.APPLICATION);
        String sessionId = jwt.getClaimAsString(ServiceTokenClaims.SESSION_ID);
        Long authVersion = claimAsLong(jwt.getClaim(ServiceTokenClaims.AUTH_VERSION));
        String clientId = jwt.getClaimAsString(ServiceTokenClaims.CLIENT_ID);
        return new CurrentUser(userId, companyId, employeeId, application, sessionId, authVersion, clientId);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private static Long claimAsLong(Object value) {
        return switch (value) {
            case null -> null;
            case Number number -> number.longValue();
            case String text when !text.isBlank() -> Long.parseLong(text);
            default -> null;
        };
    }

    public static Optional<CurrentUser> fromSecurityContext() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public static CurrentUser require() {
        return fromSecurityContext()
                .orElseThrow(() -> new com.ravalgroups.forms.shared.exception.DomainException(
                        "UNAUTHENTICATED", "Authentication required"));
    }
}
