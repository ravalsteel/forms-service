package com.ravalgroups.forms.iam.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_company_projection")
public class IamCompanyProjectionEntity {

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 64)
    private String status;

    @Column(name = "source_version")
    private Long sourceVersion;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IamCompanyProjectionEntity() {}

    public static IamCompanyProjectionEntity createNew(UUID companyId, Instant now) {
        IamCompanyProjectionEntity entity = new IamCompanyProjectionEntity();
        entity.companyId = companyId;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.syncedAt = now;
        return entity;
    }

    public void apply(String code, String name, String status, Long sourceVersion, Instant now) {
        this.code = code;
        this.name = name;
        this.status = status;
        this.sourceVersion = sourceVersion;
        this.syncedAt = now;
        this.updatedAt = now;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }
}
