package com.ravalgroups.forms.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.authorization.FormsPermissionCode;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleJpaRepository;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionJpaRepository;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentApplicationServiceTest {

    @Mock
    private FormsUserRoleJpaRepository userRoles;

    @Mock
    private FormsRoleJpaRepository roles;

    @Mock
    private IamMembershipProjectionJpaRepository memberships;

    @Mock
    private FormsAuthorizationService authz;

    private RoleAssignmentApplicationService service;

    private final UUID companyId = UUID.fromString("5aa7230d-fe4d-4f5f-a70a-1e4b19af5a65");
    private final UUID userId = UUID.fromString("a2000000-0000-4000-8000-000000000010");
    private final UUID adminRoleId = UUID.fromString("b1000000-0000-4000-8000-000000000001");

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentApplicationService(userRoles, roles, memberships, authz);
    }

    @Test
    void claimFirstAdminPersistsAdminWhenBootstrapIsOpen() {
        CurrentUser actor = actor(userId, companyId);
        when(authz.isBootstrapOpen(companyId)).thenReturn(true);
        FormsRoleEntity admin = systemAdmin();
        when(roles.findByCompanyIdAndCode(companyId, "ADMIN")).thenReturn(Optional.of(admin));
        when(memberships.findByCompanyIdAndIamUserId(companyId, userId))
                .thenReturn(Optional.of(
                        IamMembershipProjectionEntity.createNew(UUID.randomUUID(), Instant.now())));
        when(userRoles.existsByCompanyIdAndIamUserIdAndRoleId(companyId, userId, adminRoleId))
                .thenReturn(false);
        when(userRoles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var view = service.claimFirstAdmin(actor);

        assertThat(view.roleCode()).isEqualTo("ADMIN");
        assertThat(view.roleId()).isEqualTo(adminRoleId);
        assertThat(view.companyId()).isEqualTo(companyId);
        assertThat(view.iamUserId()).isEqualTo(userId);
        verify(authz).ensureSystemRoles(companyId);
        ArgumentCaptor<FormsUserRoleEntity> captor =
                ArgumentCaptor.forClass(FormsUserRoleEntity.class);
        verify(userRoles).save(captor.capture());
        assertThat(captor.getValue().getRoleId()).isEqualTo(adminRoleId);
    }

    @Test
    void claimFirstAdminRejectedWhenAdminAlreadyExists() {
        when(authz.isBootstrapOpen(companyId)).thenReturn(false);

        assertThatThrownBy(() -> service.claimFirstAdmin(actor(userId, companyId)))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("BOOTSTRAP_CLOSED");
        verify(userRoles, never()).save(any());
    }

    @Test
    void claimFirstAdminRequiresProjectedMembership() {
        when(authz.isBootstrapOpen(companyId)).thenReturn(true);
        when(roles.findByCompanyIdAndCode(companyId, "ADMIN")).thenReturn(Optional.of(systemAdmin()));
        when(memberships.findByCompanyIdAndIamUserId(companyId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claimFirstAdmin(actor(userId, companyId)))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void assignAcceptsRoleId() {
        CurrentUser actor = actor(userId, companyId);
        FormsRoleEntity designer = FormsRoleEntity.create(
                adminRoleId,
                companyId,
                "DESIGNER",
                "Designer",
                null,
                true,
                new LinkedHashSet<>(FormsPermissionCode.DESIGNER_BUNDLE),
                Instant.now());
        when(roles.findByIdAndCompanyId(adminRoleId, companyId)).thenReturn(Optional.of(designer));
        when(memberships.findByCompanyIdAndIamUserId(companyId, userId))
                .thenReturn(Optional.of(
                        IamMembershipProjectionEntity.createNew(UUID.randomUUID(), Instant.now())));
        when(userRoles.existsByCompanyIdAndIamUserIdAndRoleId(companyId, userId, adminRoleId))
                .thenReturn(false);
        when(userRoles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var view = service.assign(actor, userId, adminRoleId, null);

        assertThat(view.roleCode()).isEqualTo("DESIGNER");
        verify(authz).ensureSystemRoles(companyId);
    }

    @Test
    void revokeRejectsLastAdmin() {
        CurrentUser actor = actor(userId, companyId);
        FormsRoleEntity admin = systemAdmin();
        when(roles.findByCompanyIdAndCode(companyId, "ADMIN")).thenReturn(Optional.of(admin));
        when(userRoles.existsByCompanyIdAndIamUserIdAndRoleId(companyId, userId, adminRoleId))
                .thenReturn(true);
        when(roles.countUsersWithPermissionExcludingAssignment(
                        companyId, FormsPermissionCode.ROLES_MANAGE, userId, adminRoleId))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.revoke(actor, userId, null, "ADMIN"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("LAST_ADMIN");
        verify(userRoles, never())
                .deleteByCompanyIdAndIamUserIdAndRoleId(eq(companyId), eq(userId), eq(adminRoleId));
    }

    private FormsRoleEntity systemAdmin() {
        return FormsRoleEntity.create(
                adminRoleId,
                companyId,
                "ADMIN",
                "Administrator",
                "Full access",
                true,
                Set.copyOf(FormsPermissionCode.ADMIN_BUNDLE),
                Instant.now());
    }

    private static CurrentUser actor(UUID userId, UUID companyId) {
        return new CurrentUser(userId, companyId, "EMP-1", "forms", "sid", 0L, "web");
    }
}
