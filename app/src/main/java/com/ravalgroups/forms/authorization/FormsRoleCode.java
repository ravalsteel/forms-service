package com.ravalgroups.forms.authorization;

import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.Locale;
import java.util.Set;

/** Well-known system role codes. Custom company roles may use any other code. */
public final class FormsRoleCode {

    public static final String ADMIN = "ADMIN";
    public static final String DESIGNER = "DESIGNER";
    public static final String PUBLISHER = "PUBLISHER";
    public static final String ANALYST = "ANALYST";
    public static final String RESPONDENT = "RESPONDENT";

    public static final Set<String> SYSTEM = Set.of(ADMIN, DESIGNER, PUBLISHER, ANALYST, RESPONDENT);

    private FormsRoleCode() {}

    /** Uppercases and trims; does not restrict to system codes (custom roles allowed). */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "roleCode is required");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isSystem(String code) {
        return code != null && SYSTEM.contains(code.trim().toUpperCase(Locale.ROOT));
    }
}
