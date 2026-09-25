package com.ravalgroups.forms.invitation.adapter.out.persistence;

import com.ravalgroups.forms.invitation.domain.InvitationStatus;
import com.ravalgroups.forms.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitation")
public class InvitationEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "form_run_id", nullable = false)
    private UUID formRunId;

    @Column(name = "respondent_reference", nullable = false, length = 320)
    private String respondentReference;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvitationStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvitationEntity() {}

    public static InvitationEntity create(
            UUID id,
            UUID companyId,
            UUID formRunId,
            String respondentReference,
            String tokenHash,
            Instant expiresAt,
            Instant now) {
        InvitationEntity e = new InvitationEntity();
        e.id = id;
        e.companyId = companyId;
        e.formRunId = formRunId;
        e.respondentReference = respondentReference;
        e.tokenHash = tokenHash;
        e.status = InvitationStatus.PENDING;
        e.expiresAt = expiresAt;
        e.createdAt = now;
        return e;
    }

    public void markUsed(Instant now) {
        if (status == InvitationStatus.REVOKED) {
            throw new DomainException("INVALID_STATE", "Invitation is revoked");
        }
        if (status == InvitationStatus.USED) {
            throw new DomainException("INVALID_STATE", "Invitation already used");
        }
        if (status == InvitationStatus.EXPIRED
                || (expiresAt != null && expiresAt.isBefore(now))) {
            this.status = InvitationStatus.EXPIRED;
            throw new DomainException("INVALID_STATE", "Invitation expired");
        }
        this.status = InvitationStatus.USED;
        this.usedAt = now;
    }

    public void revoke(Instant now) {
        if (status == InvitationStatus.USED) {
            throw new DomainException("INVALID_STATE", "Cannot revoke a used invitation");
        }
        this.status = InvitationStatus.REVOKED;
        if (this.expiresAt == null || this.expiresAt.isAfter(now)) {
            this.expiresAt = now;
        }
    }

    public void expireIfNeeded(Instant now) {
        if (status == InvitationStatus.PENDING && expiresAt != null && expiresAt.isBefore(now)) {
            this.status = InvitationStatus.EXPIRED;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getFormRunId() {
        return formRunId;
    }

    public String getRespondentReference() {
        return respondentReference;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
