package com.ravalgroups.forms.form.domain;

import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.Locale;

public enum FormAccessLevel {
    VIEW(1),
    EDIT(2),
    MANAGE(3);

    private final int rank;

    FormAccessLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(FormAccessLevel required) {
        return required != null && this.rank >= required.rank;
    }

    public static FormAccessLevel parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "accessLevel is required");
        }
        try {
            return FormAccessLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainException("VALIDATION_ERROR", "accessLevel must be VIEW, EDIT, or MANAGE");
        }
    }

    public static FormAccessLevel max(FormAccessLevel a, FormAccessLevel b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.rank >= b.rank ? a : b;
    }
}
