package com.ravalgroups.forms.authorization.application;

import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.authorization.FormsRoleCode;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleAssignmentEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleAssignmentJpaRepository;
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

    private final FormsRoleAssignmentJpaRepository roles;
    private final IamMembershipProjectionJpaRepository memberships;
    private final FormsAuthorizationService authz;

    public RoleAssignmentApplicationService(
            FormsRoleAssignmentJpaRepository roles,
            IamMembershipProjectionJpaRepository memberships,
            FormsAuthorizationService authz) {
        this.roles = roles;
        this.memberships = memberships;
        this.authz = authz;
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentView> list(CurrentUser actor, String roleCode) {
        authz.requireAdmin(actor);
        UUID companyId = actor.companyId();
        List<FormsRoleAssignmentEntity> rows = roleCode == null || roleCode.isBlank()
                ? roles.findByCompanyIdOrderByCreatedAtDesc(companyId)
                : roles.findByCompanyIdAndRoleCodeOrderByCreatedAtDesc(
                        companyId, FormsRoleCode.normalize(roleCode));
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
        return roles.findByCompanyIdAndIamUserId(actor.companyId(), iamUserId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public RoleAssignmentView assign(CurrentUser actor, UUID iamUserId, String roleCode) {
        authz.requireAdmin(actor);
        return persist(actor.companyId(), iamUserId, FormsRoleCode.normalize(roleCode));
    }

    /**
     * First-admin bootstrap: when the company has no ADMIN, a projected member
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
        return persist(companyId, actor.userId(), FormsRoleCode.ADMIN);
    }

    @Transactional
    public void revoke(CurrentUser actor, UUID iamUserId, String roleCode) {
        authz.requireAdmin(actor);
        String code = FormsRoleCode.normalize(roleCode);
        if (!roles.existsByCompanyIdAndIamUserIdAndRoleCode(actor.companyId(), iamUserId, code)) {
            throw new DomainException("NOT_FOUND", "Role assignment not found");
        }
        if (FormsRoleCode.ADMIN.equals(code)
                && roles.countByCompanyIdAndRoleCode(actor.companyId(), FormsRoleCode.ADMIN) <= 1) {
            throw new DomainException(
                    "LAST_ADMIN", "Cannot remove the last Forms administrator for this company");
        }
        roles.deleteByCompanyIdAndIamUserIdAndRoleCode(actor.companyId(), iamUserId, code);
    }

    private RoleAssignmentView persist(UUID companyId, UUID iamUserId, String code) {
        memberships
                .findByCompanyIdAndIamUserId(companyId, iamUserId)
                .orElseThrow(() -> new DomainException(
                        "NOT_FOUND", "User is not projected as a member of this company"));
        if (roles.existsByCompanyIdAndIamUserIdAndRoleCode(companyId, iamUserId, code)) {
            throw new DomainException("CONFLICT", "Role already assigned");
        }
        FormsRoleAssignmentEntity saved = roles.save(FormsRoleAssignmentEntity.create(
                UuidV7.create(), companyId, iamUserId, code, Instant.now()));
        return toView(saved);
    }

    private RoleAssignmentView toView(FormsRoleAssignmentEntity e) {
        return new RoleAssignmentView(e.getId(), e.getCompanyId(), e.getIamUserId(), e.getRoleCode(), e.getCreatedAt());
    }

    public record RoleAssignmentView(
            UUID id, UUID companyId, UUID iamUserId, String roleCode, Instant createdAt) {}
}
