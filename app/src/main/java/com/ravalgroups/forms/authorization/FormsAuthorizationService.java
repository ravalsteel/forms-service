package com.ravalgroups.forms.authorization;

import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleAssignmentEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleAssignmentJpaRepository;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FormsAuthorizationService {

    private final FormsRoleAssignmentJpaRepository roles;

    public FormsAuthorizationService(FormsRoleAssignmentJpaRepository roles) {
        this.roles = roles;
    }

    public void requireCompany(CurrentUser user, UUID companyId) {
        if (!user.hasCompanyContext() || !user.companyId().equals(companyId)) {
            throw new DomainException("CROSS_COMPANY_ACCESS_DENIED", "Company context does not match resource");
        }
    }

    public boolean hasAnyRole(CurrentUser user, Set<String> roleCodes) {
        if (!user.hasCompanyContext()) {
            return false;
        }
        return roles.existsByCompanyIdAndIamUserIdAndRoleCodeIn(
                user.companyId(), user.userId(), List.copyOf(roleCodes));
    }

    public void requireAdmin(CurrentUser user) {
        requireCompanyContext(user);
        if (!hasAnyRole(user, Set.of(FormsRoleCode.ADMIN))) {
            throw new DomainException("FORBIDDEN", "ADMIN role required");
        }
    }

    public void requireDesignerOrAdmin(CurrentUser user) {
        requireCompanyContext(user);
        if (!hasAnyRole(user, FormsRoleCode.DESIGNER_OR_ADMIN)) {
            throw new DomainException("FORBIDDEN", "DESIGNER or ADMIN role required");
        }
    }

    public void requirePublisherOrAdmin(CurrentUser user) {
        requireCompanyContext(user);
        if (!hasAnyRole(user, FormsRoleCode.PUBLISHER_OR_ADMIN)) {
            throw new DomainException("FORBIDDEN", "PUBLISHER or ADMIN role required");
        }
    }

    public void requireAnalystOrAdmin(CurrentUser user) {
        requireCompanyContext(user);
        if (!hasAnyRole(user, FormsRoleCode.ANALYST_OR_ADMIN)) {
            throw new DomainException("FORBIDDEN", "ANALYST or ADMIN role required");
        }
    }

    /** Any assigned Forms role may access read-safe company surfaces. */
    public void requireAssigned(CurrentUser user) {
        requireCompanyContext(user);
        if (!hasAnyRole(user, FormsRoleCode.ANY_ASSIGNED)) {
            throw new DomainException("FORBIDDEN", "Forms role required");
        }
    }

    public boolean isBootstrapOpen(UUID companyId) {
        return companyId != null && !roles.existsByCompanyIdAndRoleCode(companyId, FormsRoleCode.ADMIN);
    }

    public List<String> assignedRoleCodes(CurrentUser user) {
        if (!user.hasCompanyContext()) {
            return List.of();
        }
        return roles.findByCompanyIdAndIamUserId(user.companyId(), user.userId()).stream()
                .map(FormsRoleAssignmentEntity::getRoleCode)
                .toList();
    }

    private void requireCompanyContext(CurrentUser user) {
        if (!user.hasCompanyContext()) {
            throw new DomainException("COMPANY_CONTEXT_REQUIRED", "Company context required");
        }
    }
}
