package com.ravalgroups.forms.iam.application;

import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamUserProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamUserProjectionJpaRepository;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.pagination.PageQuery;
import com.ravalgroups.forms.shared.pagination.PageResult;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryIamProjection {

    private final IamUserProjectionJpaRepository users;
    private final IamMembershipProjectionJpaRepository memberships;

    public QueryIamProjection(IamUserProjectionJpaRepository users, IamMembershipProjectionJpaRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public EmployeeProjectionView getByEmployeeId(UUID companyId, String employeeId) {
        IamMembershipProjectionEntity membership = memberships
                .findByCompanyIdAndEmployeeId(companyId, employeeId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Employee projection not found"));
        return toView(membership);
    }

    @Transactional(readOnly = true)
    public EmployeeProjectionView getByUserId(UUID companyId, UUID iamUserId) {
        IamMembershipProjectionEntity membership = memberships
                .findByCompanyIdAndIamUserId(companyId, iamUserId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Employee projection not found"));
        return toView(membership);
    }

    @Transactional(readOnly = true)
    public PageResult<EmployeeProjectionView> listCompanyEmployees(
            UUID companyId, String q, Integer page, Integer size, String sort) {
        Pageable pageable = PageQuery.toPageable(page, size, sort, Set.of("employeeId", "displayName", "syncedAt"), "displayName");
        Page<IamMembershipProjectionEntity> result;
        if (q == null || q.isBlank()) {
            result = memberships.findByCompanyId(companyId, pageable);
        } else {
            result = memberships.searchByCompany(companyId, q.trim(), pageable);
        }
        return PageResult.from(result.map(this::toView));
    }

    private EmployeeProjectionView toView(IamMembershipProjectionEntity membership) {
        IamUserProjectionEntity user = users.findById(membership.getIamUserId()).orElse(null);
        return new EmployeeProjectionView(
                membership.getIamUserId(),
                membership.getCompanyId(),
                membership.getEmployeeId(),
                user == null ? null : user.getFirstName(),
                user == null ? null : user.getLastName(),
                membership.getDisplayName() != null
                        ? membership.getDisplayName()
                        : (user == null ? null : user.getDisplayName()),
                membership.getEmail() != null ? membership.getEmail() : (user == null ? null : user.getEmail()),
                user == null ? null : user.getPhoneNumber(),
                user == null ? null : user.getDesignation(),
                membership.getDepartmentId(),
                membership.getDepartmentCode(),
                membership.getDepartmentName(),
                membership.getCompanyCode(),
                membership.getCompanyName(),
                membership.getStatus(),
                user == null ? null : user.getStatus(),
                membership.getSyncedAt());
    }

    public record EmployeeProjectionView(
            UUID iamUserId,
            UUID companyId,
            String employeeId,
            String firstName,
            String lastName,
            String displayName,
            String email,
            String phoneNumber,
            String designation,
            UUID departmentId,
            String departmentCode,
            String departmentName,
            String companyCode,
            String companyName,
            String membershipStatus,
            String userStatus,
            Instant syncedAt) {}
}
