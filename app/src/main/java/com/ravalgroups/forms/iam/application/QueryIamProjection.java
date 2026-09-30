package com.ravalgroups.forms.iam.application;

import com.ravalgroups.forms.iam.adapter.out.persistence.IamDepartmentProjectionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamSubDepartmentProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamSubDepartmentProjectionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamUserProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamUserProjectionJpaRepository;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.pagination.PageQuery;
import com.ravalgroups.forms.shared.pagination.PageResult;
import java.time.Instant;
import java.util.List;
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
    private final IamDepartmentProjectionJpaRepository departments;
    private final IamSubDepartmentProjectionJpaRepository subDepartments;

    public QueryIamProjection(
            IamUserProjectionJpaRepository users,
            IamMembershipProjectionJpaRepository memberships,
            IamDepartmentProjectionJpaRepository departments,
            IamSubDepartmentProjectionJpaRepository subDepartments) {
        this.users = users;
        this.memberships = memberships;
        this.departments = departments;
        this.subDepartments = subDepartments;
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
        Pageable pageable =
                PageQuery.toPageable(page, size, sort, Set.of("employeeId", "displayName", "syncedAt"), "displayName");
        Page<IamMembershipProjectionEntity> result;
        if (q == null || q.isBlank()) {
            result = memberships.findByCompanyId(companyId, pageable);
        } else {
            result = memberships.searchByCompany(companyId, q.trim(), pageable);
        }
        return PageResult.from(result.map(this::toView));
    }

    @Transactional(readOnly = true)
    public List<DepartmentProjectionView> listDepartments(UUID companyId) {
        return departments.findByCompanyIdOrderByNameAsc(companyId).stream()
                .map(d -> new DepartmentProjectionView(
                        d.getDepartmentId(), d.getCompanyId(), d.getCode(), d.getName(), d.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubDepartmentProjectionView> listSubDepartments(UUID companyId, UUID departmentId) {
        List<IamSubDepartmentProjectionEntity> rows = departmentId == null
                ? subDepartments.findByCompanyIdOrderByNameAsc(companyId)
                : subDepartments.findByCompanyIdAndDepartmentIdOrderByNameAsc(companyId, departmentId);
        return rows.stream()
                .map(s -> new SubDepartmentProjectionView(
                        s.getSubDepartmentId(),
                        s.getCompanyId(),
                        s.getDepartmentId(),
                        s.getCode(),
                        s.getName(),
                        s.getStatus()))
                .toList();
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
                membership.getSubDepartmentId(),
                membership.getSubDepartmentCode(),
                membership.getSubDepartmentName(),
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
            UUID subDepartmentId,
            String subDepartmentCode,
            String subDepartmentName,
            String companyCode,
            String companyName,
            String membershipStatus,
            String userStatus,
            Instant syncedAt) {}

    public record DepartmentProjectionView(UUID id, UUID companyId, String code, String name, String status) {}

    public record SubDepartmentProjectionView(
            UUID id, UUID companyId, UUID departmentId, String code, String name, String status) {}
}
