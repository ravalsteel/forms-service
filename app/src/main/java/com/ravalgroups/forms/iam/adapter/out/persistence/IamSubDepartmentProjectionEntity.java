package com.ravalgroups.forms.iam.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_sub_department_projection")
public class IamSubDepartmentProjectionEntity {

    @Id
    @Column(name = "sub_department_id")
    private UUID subDepartmentId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(length = 64)
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

    protected IamSubDepartmentProjectionEntity() {}

    public static IamSubDepartmentProjectionEntity createNew(UUID subDepartmentId, Instant now) {
        IamSubDepartmentProjectionEntity entity = new IamSubDepartmentProjectionEntity();
        entity.subDepartmentId = subDepartmentId;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.syncedAt = now;
        return entity;
    }

    public void apply(
            UUID companyId,
            UUID departmentId,
            String code,
            String name,
            String status,
            Long sourceVersion,
            Instant now) {
        this.companyId = companyId;
        this.departmentId = departmentId;
        this.code = code;
        this.name = name;
        this.status = status;
        this.sourceVersion = sourceVersion;
        this.syncedAt = now;
        this.updatedAt = now;
    }

    public UUID getSubDepartmentId() {
        return subDepartmentId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getDepartmentId() {
        return departmentId;
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
