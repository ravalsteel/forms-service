package com.ravalgroups.forms.iam.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Synchronous IAM access for bootstrap / exceptional refresh.
 * Normal reads use the local IAM projection.
 */
public interface IamClientPort {

    Optional<IamUserView> getUser(UUID userId);

    Optional<IamMembershipView> getMembership(UUID companyId, UUID userId);

    record IamUserView(
            UUID id,
            String email,
            String username,
            String firstName,
            String lastName,
            String displayName,
            String designation,
            String phoneNumber,
            UUID reportingManagerId,
            String status) {}

    record IamMembershipView(
            UUID id,
            UUID userId,
            UUID companyId,
            String companyCode,
            String companyName,
            String employeeId,
            String status,
            UUID departmentId,
            String departmentCode,
            String departmentName,
            String username,
            String displayName,
            String email) {}
}
