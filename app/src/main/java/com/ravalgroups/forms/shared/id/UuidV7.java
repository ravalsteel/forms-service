package com.ravalgroups.forms.shared.id;

import com.github.f4b6a3.uuid.UuidCreator;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.UUID;

/**
 * UUIDv7 factory for Forms domain identifiers (time-ordered, offline-friendly).
 */
public final class UuidV7 {

    private UuidV7() {}

    public static UUID create() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * Use a client-provided id when present; otherwise generate UUIDv7.
     * Client-provided ids must be version 7.
     */
    public static UUID createOrUse(UUID clientProvided) {
        if (clientProvided == null) {
            return create();
        }
        if (clientProvided.version() != 7) {
            throw new DomainException(
                    "VALIDATION_ERROR", "Client-provided id must be a UUIDv7 (got version " + clientProvided.version() + ")");
        }
        return clientProvided;
    }
}
