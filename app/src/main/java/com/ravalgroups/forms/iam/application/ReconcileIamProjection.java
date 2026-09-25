package com.ravalgroups.forms.iam.application;

import com.ravalgroups.forms.iam.adapter.out.persistence.IamCompanyProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamCompanyProjectionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamUserProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamUserProjectionJpaRepository;
import com.ravalgroups.forms.iam.application.port.IamClientPort;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconcileIamProjection {

    private final IamClientPort iamClient;
    private final IamUserProjectionJpaRepository users;
    private final IamMembershipProjectionJpaRepository memberships;
    private final IamCompanyProjectionJpaRepository companies;

    public ReconcileIamProjection(
            IamClientPort iamClient,
            IamUserProjectionJpaRepository users,
            IamMembershipProjectionJpaRepository memberships,
            IamCompanyProjectionJpaRepository companies) {
        this.iamClient = iamClient;
        this.users = users;
        this.memberships = memberships;
        this.companies = companies;
    }

    @Transactional
    public void reconcileUserAndMembership(UUID companyId, UUID iamUserId) {
        Instant now = Instant.now();
        IamClientPort.IamUserView userView = iamClient
                .getUser(iamUserId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "IAM user not found"));
        IamClientPort.IamMembershipView membershipView = iamClient
                .getMembership(companyId, iamUserId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "IAM membership not found for company"));

        IamUserProjectionEntity user = users.findById(iamUserId).orElseGet(() -> IamUserProjectionEntity.createNew(iamUserId, now));
        user.apply(
                userView.email(),
                userView.username(),
                userView.firstName(),
                userView.lastName(),
                userView.displayName(),
                userView.designation(),
                userView.phoneNumber(),
                userView.reportingManagerId(),
                userView.status(),
                null,
                now);
        users.save(user);

        IamCompanyProjectionEntity company = companies
                .findById(companyId)
                .orElseGet(() -> IamCompanyProjectionEntity.createNew(companyId, now));
        company.apply(membershipView.companyCode(), membershipView.companyName(), null, null, now);
        companies.save(company);

        IamMembershipProjectionEntity membership = memberships
                .findById(membershipView.id())
                .orElseGet(() -> IamMembershipProjectionEntity.createNew(membershipView.id(), now));
        membership.apply(
                membershipView.userId(),
                membershipView.companyId(),
                membershipView.employeeId(),
                membershipView.status(),
                membershipView.departmentId(),
                membershipView.departmentCode(),
                membershipView.departmentName(),
                membershipView.companyCode(),
                membershipView.companyName(),
                membershipView.username(),
                membershipView.displayName(),
                membershipView.email(),
                null,
                null,
                now);
        memberships.save(membership);
    }
}
