package com.ravalgroups.forms.authorization;

import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleJpaRepository;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleJpaRepository;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormsAuthorizationService {

    private final FormsRoleJpaRepository roles;
    private final FormsUserRoleJpaRepository userRoles;

    public FormsAuthorizationService(FormsRoleJpaRepository roles, FormsUserRoleJpaRepository userRoles) {
        this.roles = roles;
        this.userRoles = userRoles;
    }

    public void requireCompany(CurrentUser user, UUID companyId) {
        if (!user.hasCompanyContext() || !user.companyId().equals(companyId)) {
            throw new DomainException("CROSS_COMPANY_ACCESS_DENIED", "Company context does not match resource");
        }
    }

    public boolean hasPermission(CurrentUser user, String permissionCode) {
        if (!user.hasCompanyContext() || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return roles.userHasPermission(user.companyId(), user.userId(), permissionCode);
    }

    public boolean hasAnyPermission(CurrentUser user, String... permissionCodes) {
        if (permissionCodes == null) {
            return false;
        }
        for (String code : permissionCodes) {
            if (hasPermission(user, code)) {
                return true;
            }
        }
        return false;
    }

    public void requirePermission(CurrentUser user, String permissionCode) {
        requireCompanyContext(user);
        if (!hasPermission(user, permissionCode)) {
            throw new DomainException("FORBIDDEN", "Permission required: " + permissionCode);
        }
    }

    public void requireAnyPermission(CurrentUser user, String... permissionCodes) {
        requireCompanyContext(user);
        if (!hasAnyPermission(user, permissionCodes)) {
            throw new DomainException(
                    "FORBIDDEN", "One of these permissions required: " + String.join(", ", permissionCodes));
        }
    }

    /** Convenience: roles.manage (or equivalent admin power). */
    public void requireAdmin(CurrentUser user) {
        requirePermission(user, FormsPermissionCode.ROLES_MANAGE);
    }

    /** Convenience: versions.write. */
    public void requireDesignerOrAdmin(CurrentUser user) {
        requirePermission(user, FormsPermissionCode.VERSIONS_WRITE);
    }

    /** Convenience: versions.publish or runs.manage. */
    public void requirePublisherOrAdmin(CurrentUser user) {
        requireAnyPermission(user, FormsPermissionCode.VERSIONS_PUBLISH, FormsPermissionCode.RUNS_MANAGE);
    }

    /** Convenience: reports.read. */
    public void requireAnalystOrAdmin(CurrentUser user) {
        requirePermission(user, FormsPermissionCode.REPORTS_READ);
    }

    /** Any role assignment (and thus any permission) grants access to read-safe company surfaces. */
    public void requireAssigned(CurrentUser user) {
        requireCompanyContext(user);
        if (!userRoles.existsByCompanyIdAndIamUserId(user.companyId(), user.userId())
                && roles.findPermissionCodesByCompanyAndUser(user.companyId(), user.userId()).isEmpty()) {
            throw new DomainException("FORBIDDEN", "Forms role required");
        }
    }

    /**
     * True when no user in this company holds {@code forms.roles.manage}.
     * A projected member may then claim the first ADMIN via bootstrap.
     */
    public boolean isBootstrapOpen(UUID companyId) {
        return companyId != null
                && !roles.existsUserWithPermission(companyId, FormsPermissionCode.ROLES_MANAGE);
    }

    public List<String> assignedRoleCodes(CurrentUser user) {
        if (!user.hasCompanyContext()) {
            return List.of();
        }
        List<FormsUserRoleEntity> assignments =
                userRoles.findByCompanyIdAndIamUserId(user.companyId(), user.userId());
        List<String> codes = new ArrayList<>();
        for (FormsUserRoleEntity ur : assignments) {
            roles.findById(ur.getRoleId()).ifPresent(r -> codes.add(r.getCode()));
        }
        return codes;
    }

    public List<String> assignedPermissions(CurrentUser user) {
        if (!user.hasCompanyContext()) {
            return List.of();
        }
        return roles.findPermissionCodesByCompanyAndUser(user.companyId(), user.userId()).stream()
                .sorted()
                .toList();
    }

    /**
     * Ensures ADMIN / DESIGNER / PUBLISHER / ANALYST / RESPONDENT system roles exist for the
     * company with default permission bundles. Safe to call repeatedly.
     */
    @Transactional
    public void ensureSystemRoles(UUID companyId) {
        if (companyId == null) {
            return;
        }
        Instant now = Instant.now();
        ensureSystemRole(
                companyId,
                FormsRoleCode.ADMIN,
                "Administrator",
                "Full access including role management",
                FormsPermissionCode.ADMIN_BUNDLE,
                now);
        ensureSystemRole(
                companyId,
                FormsRoleCode.DESIGNER,
                "Designer",
                "Design forms and versions",
                FormsPermissionCode.DESIGNER_BUNDLE,
                now);
        ensureSystemRole(
                companyId,
                FormsRoleCode.PUBLISHER,
                "Publisher",
                "Publish versions and manage runs",
                FormsPermissionCode.PUBLISHER_BUNDLE,
                now);
        ensureSystemRole(
                companyId,
                FormsRoleCode.ANALYST,
                "Analyst",
                "Read responses, reports, and exports",
                FormsPermissionCode.ANALYST_BUNDLE,
                now);
        ensureSystemRole(
                companyId,
                FormsRoleCode.RESPONDENT,
                "Respondent",
                "Respond to assigned form runs",
                FormsPermissionCode.RESPONDENT_BUNDLE,
                now);
    }

    private void ensureSystemRole(
            UUID companyId,
            String code,
            String name,
            String description,
            Set<String> permissions,
            Instant now) {
        if (roles.existsByCompanyIdAndCode(companyId, code)) {
            return;
        }
        roles.save(FormsRoleEntity.create(
                UuidV7.create(),
                companyId,
                code,
                name,
                description,
                true,
                new LinkedHashSet<>(permissions),
                now));
    }

    private void requireCompanyContext(CurrentUser user) {
        if (!user.hasCompanyContext()) {
            throw new DomainException("FORBIDDEN", "Company context required");
        }
    }
}
