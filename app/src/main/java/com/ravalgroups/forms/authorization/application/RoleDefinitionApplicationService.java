package com.ravalgroups.forms.authorization.application;

import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.authorization.FormsPermissionCode;
import com.ravalgroups.forms.authorization.FormsRoleCode;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsPermissionEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsPermissionJpaRepository;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleJpaRepository;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleJpaRepository;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleDefinitionApplicationService {

    private final FormsRoleJpaRepository roles;
    private final FormsPermissionJpaRepository permissions;
    private final FormsUserRoleJpaRepository userRoles;
    private final FormsAuthorizationService authz;

    public RoleDefinitionApplicationService(
            FormsRoleJpaRepository roles,
            FormsPermissionJpaRepository permissions,
            FormsUserRoleJpaRepository userRoles,
            FormsAuthorizationService authz) {
        this.roles = roles;
        this.permissions = permissions;
        this.userRoles = userRoles;
        this.authz = authz;
    }

    @Transactional(readOnly = true)
    public List<PermissionView> listPermissions(CurrentUser actor) {
        authz.requireAssigned(actor);
        return permissions.findAllByOrderByCodeAsc().stream().map(this::toPermissionView).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDefinitionView> listDefinitions(CurrentUser actor) {
        authz.requirePermission(actor, FormsPermissionCode.ROLES_READ);
        return roles.findByCompanyIdOrderBySystemDefinedDescCodeAsc(actor.companyId()).stream()
                .map(this::toView)
                .toList();
    }

    /** IAM bridge: no human actor; company scoped. */
    @Transactional
    public List<RoleDefinitionView> listDefinitionsForCompany(UUID companyId) {
        if (companyId == null) {
            throw new DomainException("VALIDATION_ERROR", "companyId is required");
        }
        authz.ensureSystemRoles(companyId);
        return roles.findByCompanyIdOrderBySystemDefinedDescCodeAsc(companyId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public RoleDefinitionView create(
            CurrentUser actor, String code, String name, String description, List<String> permissionCodes) {
        authz.requirePermission(actor, FormsPermissionCode.ROLES_MANAGE);
        UUID companyId = actor.companyId();
        authz.ensureSystemRoles(companyId);

        String normalized = FormsRoleCode.normalize(code);
        if (roles.existsByCompanyIdAndCode(companyId, normalized)) {
            throw new DomainException("CONFLICT", "Role code already exists for this company");
        }
        Set<String> perms = validatePermissionCodes(permissionCodes);
        Instant now = Instant.now();
        FormsRoleEntity saved = roles.save(FormsRoleEntity.create(
                UuidV7.create(),
                companyId,
                normalized,
                requireName(name),
                blankToNull(description),
                false,
                perms,
                now));
        return toView(saved);
    }

    @Transactional
    public RoleDefinitionView update(
            CurrentUser actor, UUID roleId, String name, String description, List<String> permissionCodes) {
        authz.requirePermission(actor, FormsPermissionCode.ROLES_MANAGE);
        FormsRoleEntity role = roles.findByIdAndCompanyId(roleId, actor.companyId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Role definition not found"));

        Instant now = Instant.now();
        role.updateMetadata(requireName(name), blankToNull(description), now);
        if (permissionCodes != null) {
            role.replacePermissions(validatePermissionCodes(permissionCodes), now);
        }
        return toView(roles.save(role));
    }

    @Transactional
    public void delete(CurrentUser actor, UUID roleId) {
        authz.requirePermission(actor, FormsPermissionCode.ROLES_MANAGE);
        FormsRoleEntity role = roles.findByIdAndCompanyId(roleId, actor.companyId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Role definition not found"));
        if (role.isSystemDefined()) {
            throw new DomainException("SYSTEM_ROLE", "Cannot delete a system-defined role");
        }
        if (userRoles.existsByRoleId(roleId)) {
            throw new DomainException("ROLE_IN_USE", "Cannot delete a role that still has assignments");
        }
        roles.delete(role);
    }

    private Set<String> validatePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "At least one permission is required");
        }
        Set<String> requested = permissionCodes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "At least one permission is required");
        }
        Set<String> known = permissions.findAll().stream()
                .map(FormsPermissionEntity::getCode)
                .collect(Collectors.toSet());
        for (String code : requested) {
            if (!known.contains(code)) {
                throw new DomainException("VALIDATION_ERROR", "Unknown permission: " + code);
            }
        }
        return requested;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "name is required");
        }
        return name.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RoleDefinitionView toView(FormsRoleEntity e) {
        return new RoleDefinitionView(
                e.getId(),
                e.getCompanyId(),
                e.getCode(),
                e.getName(),
                e.getDescription(),
                e.isSystemDefined(),
                e.getPermissionCodes().stream().sorted().toList(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private PermissionView toPermissionView(FormsPermissionEntity e) {
        return new PermissionView(e.getCode(), e.getResource(), e.getAction(), e.getDescription());
    }

    public record PermissionView(String code, String resource, String action, String description) {}

    public record RoleDefinitionView(
            UUID id,
            UUID companyId,
            String code,
            String name,
            String description,
            boolean systemDefined,
            List<String> permissionCodes,
            Instant createdAt,
            Instant updatedAt) {}
}
