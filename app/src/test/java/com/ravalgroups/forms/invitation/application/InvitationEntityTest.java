package com.ravalgroups.forms.invitation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ravalgroups.forms.invitation.adapter.out.persistence.InvitationEntity;
import com.ravalgroups.forms.invitation.domain.InvitationStatus;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class InvitationEntityTest {

    @Test
    void hashTokenIsStableAndNotPlaintext() {
        String hash = InvitationApplicationService.hashToken("secret-token");
        assertEquals(64, hash.length());
        assertEquals(hash, InvitationApplicationService.hashToken("secret-token"));
        assertNotEquals("secret-token", hash);
    }

    @Test
    void markUsedExpiresStaleInvitation() {
        Instant now = Instant.now();
        InvitationEntity invitation = InvitationEntity.create(
                UuidV7.create(),
                UuidV7.create(),
                UuidV7.create(),
                "user@example.com",
                InvitationApplicationService.hashToken("abc"),
                now.minus(1, ChronoUnit.HOURS),
                now.minus(2, ChronoUnit.DAYS));
        DomainException ex = assertThrows(DomainException.class, () -> invitation.markUsed(now));
        assertEquals("INVALID_STATE", ex.code());
        assertEquals(InvitationStatus.EXPIRED, invitation.getStatus());
    }
}
