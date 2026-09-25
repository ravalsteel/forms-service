package com.ravalgroups.forms.authorization;

import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.Locale;
import java.util.Set;

/**
 * Forms-local business roles. IAM grants application access; these roles govern
 * what the user may do inside Forms.
 */
public final class FormsRoleCode {

    public static final String ADMIN = "ADMIN";
    public static final String DESIGNER = "DESIGNER";
    public static final String PUBLISHER = "PUBLISHER";
    public static final String ANALYST = "ANALYST";
    public static final String RESPONDENT = "RESPONDENT";

    public static final Set<String> ALL = Set.of(ADMIN, DESIGNER, PUBLISHER, ANALYST, RESPONDENT);
    public static final Set<String> DESIGNER_OR_ADMIN = Set.of(ADMIN, DESIGNER);
    public static final Set<String> PUBLISHER_OR_ADMIN = Set.of(ADMIN, PUBLISHER);
    public static final Set<String> ANALYST_OR_ADMIN = Set.of(ADMIN, ANALYST);
    public static final Set<String> ANY_ASSIGNED = Set.of(ADMIN, DESIGNER, PUBLISHER, ANALYST, RESPONDENT);

    private FormsRoleCode() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "roleCode is required");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!ALL.contains(code)) {
            throw new DomainException("VALIDATION_ERROR", "Invalid roleCode. Allowed: " + ALL);
        }
        return code;
    }
}
