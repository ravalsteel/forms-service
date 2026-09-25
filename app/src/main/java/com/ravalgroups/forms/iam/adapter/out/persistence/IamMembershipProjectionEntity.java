package com.ravalgroups.forms.iam.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_membership_projection")
public class IamMembershipProjectionEntity {

    @Id
    @Column(name = "membership_id")
    private UUID membershipId;

    @Column(name = "iam_user_id", nullable = false)
    private UUID iamUserId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false, length = 64)
    private String employeeId;

    @Column(length = 64)
    private String status;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "department_code", length = 64)
    private String departmentCode;

    @Column(name = "department_name", length = 255)
    private String departmentName;

    @Column(name = "company_code", length = 64)
    private String companyCode;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(length = 128)
    private String username;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(length = 320)
    private String email;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "source_version")
    private Long sourceVersion;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IamMembershipProjectionEntity() {}

    public static IamMembershipProjectionEntity createNew(UUID membershipId, Instant now) {
        IamMembershipProjectionEntity entity = new IamMembershipProjectionEntity();
        entity.membershipId = membershipId;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.syncedAt = now;
        return entity;
    }

    public void apply(
            UUID iamUserId,
            UUID companyId,
            String employeeId,
            String status,
            UUID departmentId,
            String departmentCode,
            String departmentName,
            String companyCode,
            String companyName,
            String username,
            String displayName,
            String email,
            Instant expiresAt,
            Long sourceVersion,
            Instant now) {
        this.iamUserId = iamUserId;
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.status = status;
        this.departmentId = departmentId;
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.companyCode = companyCode;
        this.companyName = companyName;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.expiresAt = expiresAt;
        this.sourceVersion = sourceVersion;
        this.syncedAt = now;
        this.updatedAt = now;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public UUID getIamUserId() {
        return iamUserId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getStatus() {
        return status;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getCompanyCode() {
        return companyCode;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void renameDepartment(String departmentCode, String departmentName, Instant now) {
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.syncedAt = now;
        this.updatedAt = now;
    }
}
