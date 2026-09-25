package com.ravalgroups.forms.authorization.application;

import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.authorization.FormsPermissionCode;
import com.ravalgroups.forms.authorization.FormsRoleCode;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleJpaRepository;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionJpaRepository;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAssignmentApplicationService {

    private final FormsUserRoleJpaRepository userRoles;
    private final FormsRoleJpaRepository roles;
    private final IamMembershipProjectionJpaRepository memberships;
    private final FormsAuthorizationService authz;

    public RoleAssignmentApplicationService(
            FormsUserRoleJpaRepository userRoles,
            FormsRoleJpaRepository roles,
            IamMembershipProjectionJpaRepository memberships,
            FormsAuthorizationService authz) {
        this.userRoles = userRoles;
        this.roles = roles;
        this.memberships = memberships;
        this.authz = authz;
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentView> list(CurrentUser actor, String roleCode) {
        authz.requireAdmin(actor);
        UUID companyId = actor.companyId();
        List<FormsUserRoleEntity> rows;
        if (roleCode == null || roleCode.isBlank()) {
            rows = userRoles.findByCompanyIdOrderByCreatedAtDesc(companyId);
        } else {
            FormsRoleEntity role = resolveByCode(companyId, FormsRoleCode.normalize(roleCode));
            rows = userRoles.findByCompanyIdAndRoleIdOrderByCreatedAtDesc(companyId, role.getId());
        }
        return rows.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentView> listForUser(CurrentUser actor, UUID iamUserId) {
        if (actor.userId().equals(iamUserId)) {
            if (!actor.hasCompanyContext()) {
                throw new DomainException("FORBIDDEN", "Company context required");
            }
        } else {
            authz.requireAdmin(actor);
        }
        return userRoles.findByCompanyIdAndIamUserId(actor.companyId(), iamUserId).stream()
                .map(this::toView)
                .toList();
    }

    /** IAM bridge: list assignments for a user in a company. */
    @Transactional(readOnly = true)
    public List<RoleAssignmentView> listForUserInCompany(UUID companyId, UUID iamUserId) {
        requireCompanyAndUser(companyId, iamUserId);
        return userRoles.findByCompanyIdAndIamUserId(companyId, iamUserId).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * IAM bridge: replace the user's role set for a company. Same last-admin guards as revoke.
     */
    @Transactional
    public List<RoleAssignmentView> replaceForCompany(UUID companyId, UUID iamUserId, List<UUID> roleIds) {
        requireCompanyAndUser(companyId, iamUserId);
        authz.ensureSystemRoles(companyId);
        memberships
                .findByCompanyIdAndIamUserId(companyId, iamUserId)
                .orElseThrow(() -> new DomainException(
                        "NOT_FOUND", "User is not projected as a member of this company"));

        List<UUID> desired = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        for (UUID roleId : desired) {
            roles.findByIdAndCompanyId(roleId, companyId)
                    .orElseThrow(() -> new DomainException("NOT_FOUND", "Role definition not found: " + roleId));
        }

        List<FormsUserRoleEntity> current = userRoles.findByCompanyIdAndIamUserId(companyId, iamUserId);
        java.util.Set<UUID> currentIds = current.stream()
                .map(FormsUserRoleEntity::getRoleId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<UUID> desiredSet = new java.util.LinkedHashSet<>(desired);

        for (FormsUserRoleEntity row : current) {
            if (!desiredSet.contains(row.getRoleId())) {
                FormsRoleEntity role = roles.findByIdAndCompanyId(row.getRoleId(), companyId)
                        .orElse(null);
                if (role != null
                        && role.getPermissionCodes().contains(FormsPermissionCode.ROLES_MANAGE)
                        && roles.countUsersWithPermissionExcludingAssignment(
                                        companyId,
                                        FormsPermissionCode.ROLES_MANAGE,
                                        iamUserId,
                                        role.getId())
                                < 1) {
                    throw new DomainException(
                            "LAST_ADMIN",
                            "Cannot remove the last Forms administrator for this company");
                }
                userRoles.deleteByCompanyIdAndIamUserIdAndRoleId(companyId, iamUserId, row.getRoleId());
            }
        }

        for (UUID roleId : desired) {
            if (!currentIds.contains(roleId)) {
                FormsRoleEntity role = roles.findByIdAndCompanyId(roleId, companyId).orElseThrow();
                userRoles.save(FormsUserRoleEntity.create(
                        UuidV7.create(), companyId, iamUserId, role.getId(), Instant.now()));
            }
        }

        return userRoles.findByCompanyIdAndIamUserId(companyId, iamUserId).stream()
                .map(this::toView)
                .toList();
    }

    private static void requireCompanyAndUser(UUID companyId, UUID iamUserId) {
        if (companyId == null) {
            throw new DomainException("VALIDATION_ERROR", "companyId is required");
        }
        if (iamUserId == null) {
            throw new DomainException("VALIDATION_ERROR", "iamUserId is required");
        }
    }

    @Transactional
    public RoleAssignmentView assign(CurrentUser actor, UUID iamUserId, UUID roleId, String roleCode) {
        authz.requireAdmin(actor);
        authz.ensureSystemRoles(actor.companyId());
        FormsRoleEntity role = resolveRole(actor.companyId(), roleId, roleCode);
        return persist(actor.companyId(), iamUserId, role);
    }

    /**
     * First-admin bootstrap: when no one holds roles.manage, a projected member
     * with a company-scoped Forms token may claim ADMIN for themselves.
     */
    @Transactional
    public RoleAssignmentView claimFirstAdmin(CurrentUser actor) {
        if (!actor.hasCompanyContext()) {
            throw new DomainException("FORBIDDEN", "Company context required");
        }
        UUID companyId = actor.companyId();
        if (!authz.isBootstrapOpen(companyId)) {
            throw new DomainException(
                    "BOOTSTRAP_CLOSED", "A Forms administrator already exists for this company");
        }
        authz.ensureSystemRoles(companyId);
        FormsRoleEntity admin = resolveByCode(companyId, FormsRoleCode.ADMIN);
        return persist(companyId, actor.userId(), admin);
    }

    @Transactional
    public void revoke(CurrentUser actor, UUID iamUserId, UUID roleId, String roleCode) {
        authz.requireAdmin(actor);
        FormsRoleEntity role = resolveRole(actor.companyId(), roleId, roleCode);
        if (!userRoles.existsByCompanyIdAndIamUserIdAndRoleId(
                actor.companyId(), iamUserId, role.getId())) {
            throw new DomainException("NOT_FOUND", "Role assignment not found");
        }
        if (role.getPermissionCodes().contains(FormsPermissionCode.ROLES_MANAGE)
                && roles.countUsersWithPermissionExcludingAssignment(
                                actor.companyId(),
                                FormsPermissionCode.ROLES_MANAGE,
                                iamUserId,
                                role.getId())
                        < 1) {
            throw new DomainException(
                    "LAST_ADMIN", "Cannot remove the last Forms administrator for this company");
        }
        userRoles.deleteByCompanyIdAndIamUserIdAndRoleId(actor.companyId(), iamUserId, role.getId());
    }

    private FormsRoleEntity resolveRole(UUID companyId, UUID roleId, String roleCode) {
        if (roleId != null) {
            return roles.findByIdAndCompanyId(roleId, companyId)
                    .orElseThrow(() -> new DomainException("NOT_FOUND", "Role definition not found"));
        }
        if (roleCode != null && !roleCode.isBlank()) {
            return resolveByCode(companyId, FormsRoleCode.normalize(roleCode));
        }
        throw new DomainException("VALIDATION_ERROR", "roleId or roleCode is required");
    }

    private FormsRoleEntity resolveByCode(UUID companyId, String code) {
        return roles.findByCompanyIdAndCode(companyId, code)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Role not found: " + code));
    }

    private RoleAssignmentView persist(UUID companyId, UUID iamUserId, FormsRoleEntity role) {
        memberships
                .findByCompanyIdAndIamUserId(companyId, iamUserId)
                .orElseThrow(() -> new DomainException(
                        "NOT_FOUND", "User is not projected as a member of this company"));
        if (userRoles.existsByCompanyIdAndIamUserIdAndRoleId(companyId, iamUserId, role.getId())) {
            throw new DomainException("CONFLICT", "Role already assigned");
        }
        FormsUserRoleEntity saved = userRoles.save(FormsUserRoleEntity.create(
                UuidV7.create(), companyId, iamUserId, role.getId(), Instant.now()));
        return toView(saved, role);
    }

    private RoleAssignmentView toView(FormsUserRoleEntity e) {
        FormsRoleEntity role = roles.findById(e.getRoleId()).orElse(null);
        return toView(e, role);
    }

    private RoleAssignmentView toView(FormsUserRoleEntity e, FormsRoleEntity role) {
        return new RoleAssignmentView(
                e.getId(),
                e.getCompanyId(),
                e.getIamUserId(),
                role != null ? role.getCode() : null,
                e.getRoleId(),
                e.getCreatedAt());
    }

    public record RoleAssignmentView(
            UUID id,
            UUID companyId,
            UUID iamUserId,
            String roleCode,
            UUID roleId,
            Instant createdAt) {}
}
