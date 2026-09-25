package com.ravalgroups.forms.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravalgroups.forms.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

class FormsRoleCodeTest {

    @Test
    void normalizesKnownRoles() {
        assertEquals("ADMIN", FormsRoleCode.normalize("admin"));
        assertEquals("DESIGNER", FormsRoleCode.normalize("Designer"));
        assertEquals("RESPONDENT", FormsRoleCode.normalize("respondent"));
    }

    @Test
    void normalizesCustomRoleCodes() {
        assertEquals("CUSTOM", FormsRoleCode.normalize("custom"));
        assertEquals("HR_LEAD", FormsRoleCode.normalize("hr_lead"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(DomainException.class, () -> FormsRoleCode.normalize(""));
        assertThrows(DomainException.class, () -> FormsRoleCode.normalize(null));
    }

    @Test
    void identifiesSystemRoles() {
        assertTrue(FormsRoleCode.isSystem("ADMIN"));
        assertFalse(FormsRoleCode.isSystem("CUSTOM"));
    }
}
