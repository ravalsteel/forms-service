package com.ravalgroups.forms.share.adapter.out.persistence;

import com.ravalgroups.forms.share.domain.ShareLinkStatus;
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
@Table(name = "form_share_link")
public class ShareLinkEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "form_run_id", nullable = false)
    private UUID formRunId;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShareLinkStatus status;

    @Column(length = 255)
    private String label;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_responses")
    private Integer maxResponses;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ShareLinkEntity() {}

    public static ShareLinkEntity create(
            UUID id,
            UUID companyId,
            UUID formRunId,
            String tokenHash,
            String label,
            Instant expiresAt,
            Integer maxResponses,
            UUID createdBy,
            Instant now) {
        ShareLinkEntity e = new ShareLinkEntity();
        e.id = id;
        e.companyId = companyId;
        e.formRunId = formRunId;
        e.tokenHash = tokenHash;
        e.status = ShareLinkStatus.ACTIVE;
        e.label = label;
        e.expiresAt = expiresAt;
        e.maxResponses = maxResponses;
        e.createdBy = createdBy;
        e.createdAt = now;
        return e;
    }

    public void revoke(Instant now) {
        if (status == ShareLinkStatus.REVOKED) {
            return;
        }
        this.status = ShareLinkStatus.REVOKED;
        this.revokedAt = now;
    }

    public void expireIfNeeded(Instant now) {
        if (status == ShareLinkStatus.ACTIVE && expiresAt != null && !expiresAt.isAfter(now)) {
            this.status = ShareLinkStatus.EXPIRED;
        }
    }

    public void requireUsable(Instant now) {
        expireIfNeeded(now);
        if (status == ShareLinkStatus.REVOKED) {
            throw new DomainException("SHARE_LINK_REVOKED", "This share link has been revoked");
        }
        if (status == ShareLinkStatus.EXPIRED
                || (expiresAt != null && !expiresAt.isAfter(now))) {
            this.status = ShareLinkStatus.EXPIRED;
            throw new DomainException("SHARE_LINK_EXPIRED", "This share link has expired");
        }
        if (status != ShareLinkStatus.ACTIVE) {
            throw new DomainException("SHARE_LINK_UNAVAILABLE", "This share link is not available");
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

    public String getTokenHash() {
        return tokenHash;
    }

    public ShareLinkStatus getStatus() {
        return status;
    }

    public String getLabel() {
        return label;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Integer getMaxResponses() {
        return maxResponses;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
