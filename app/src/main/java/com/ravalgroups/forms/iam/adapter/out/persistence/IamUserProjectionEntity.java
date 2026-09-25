package com.ravalgroups.forms.iam.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_user_projection")
public class IamUserProjectionEntity {

    @Id
    @Column(name = "iam_user_id")
    private UUID iamUserId;

    @Column(length = 320)
    private String email;

    @Column(length = 128)
    private String username;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(length = 255)
    private String designation;

    @Column(name = "phone_number", length = 64)
    private String phoneNumber;

    @Column(name = "reporting_manager_id")
    private UUID reportingManagerId;

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

    protected IamUserProjectionEntity() {}

    public static IamUserProjectionEntity createNew(UUID iamUserId, Instant now) {
        IamUserProjectionEntity entity = new IamUserProjectionEntity();
        entity.iamUserId = iamUserId;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.syncedAt = now;
        return entity;
    }

    public void apply(
            String email,
            String username,
            String firstName,
            String lastName,
            String displayName,
            String designation,
            String phoneNumber,
            UUID reportingManagerId,
            String status,
            Long sourceVersion,
            Instant now) {
        this.email = email;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.designation = designation;
        this.phoneNumber = phoneNumber;
        this.reportingManagerId = reportingManagerId;
        this.status = status;
        this.sourceVersion = sourceVersion;
        this.syncedAt = now;
        this.updatedAt = now;
    }

    public UUID getIamUserId() {
        return iamUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDesignation() {
        return designation;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public UUID getReportingManagerId() {
        return reportingManagerId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
