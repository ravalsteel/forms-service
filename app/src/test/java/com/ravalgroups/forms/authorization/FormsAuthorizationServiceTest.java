package com.ravalgroups.forms.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleJpaRepository;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleJpaRepository;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FormsAuthorizationServiceTest {

    @Mock
    private FormsRoleJpaRepository roles;

    @Mock
    private FormsUserRoleJpaRepository userRoles;

    private FormsAuthorizationService authz;

    private final UUID companyId = UUID.fromString("5aa7230d-fe4d-4f5f-a70a-1e4b19af5a65");
    private final UUID userId = UUID.fromString("a2000000-0000-4000-8000-000000000010");

    @BeforeEach
    void setUp() {
        authz = new FormsAuthorizationService(roles, userRoles);
    }

    @Test
    void requireAdminChecksRolesManagePermission() {
        CurrentUser user = actor(userId, companyId);
        when(roles.userHasPermission(companyId, userId, FormsPermissionCode.ROLES_MANAGE))
                .thenReturn(false);

        assertThatThrownBy(() -> authz.requireAdmin(user))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void requireDesignerOrAdminChecksVersionsWrite() {
        CurrentUser user = actor(userId, companyId);
        when(roles.userHasPermission(companyId, userId, FormsPermissionCode.VERSIONS_WRITE))
                .thenReturn(true);

        authz.requireDesignerOrAdmin(user);
        verify(roles).userHasPermission(companyId, userId, FormsPermissionCode.VERSIONS_WRITE);
    }

    @Test
    void requirePublisherOrAdminAcceptsVersionsPublishOrRunsManage() {
        CurrentUser user = actor(userId, companyId);
        when(roles.userHasPermission(companyId, userId, FormsPermissionCode.VERSIONS_PUBLISH))
                .thenReturn(false);
        when(roles.userHasPermission(companyId, userId, FormsPermissionCode.RUNS_MANAGE))
                .thenReturn(true);

        authz.requirePublisherOrAdmin(user);
        verify(roles).userHasPermission(companyId, userId, FormsPermissionCode.RUNS_MANAGE);
    }

    @Test
    void requireAnalystOrAdminChecksReportsRead() {
        CurrentUser user = actor(userId, companyId);
        when(roles.userHasPermission(companyId, userId, FormsPermissionCode.REPORTS_READ))
                .thenReturn(false);

        assertThatThrownBy(() -> authz.requireAnalystOrAdmin(user))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void bootstrapOpenWhenNoRolesManageHolder() {
        when(roles.existsUserWithPermission(companyId, FormsPermissionCode.ROLES_MANAGE))
                .thenReturn(false);
        assertThat(authz.isBootstrapOpen(companyId)).isTrue();
    }

    @Test
    void ensureSystemRolesCreatesMissingBundles() {
        when(roles.existsByCompanyIdAndCode(companyId, "ADMIN")).thenReturn(false);
        when(roles.existsByCompanyIdAndCode(companyId, "DESIGNER")).thenReturn(false);
        when(roles.existsByCompanyIdAndCode(companyId, "PUBLISHER")).thenReturn(false);
        when(roles.existsByCompanyIdAndCode(companyId, "ANALYST")).thenReturn(false);
        when(roles.existsByCompanyIdAndCode(companyId, "RESPONDENT")).thenReturn(false);
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authz.ensureSystemRoles(companyId);

        ArgumentCaptor<FormsRoleEntity> captor = ArgumentCaptor.forClass(FormsRoleEntity.class);
        verify(roles, org.mockito.Mockito.times(5)).save(captor.capture());
        List<FormsRoleEntity> saved = captor.getAllValues();
        assertThat(saved)
                .extracting(FormsRoleEntity::getCode)
                .containsExactly("ADMIN", "DESIGNER", "PUBLISHER", "ANALYST", "RESPONDENT");
        assertThat(saved.get(0).getPermissionCodes()).containsAll(FormsPermissionCode.ADMIN_BUNDLE);
        assertThat(saved.get(1).getPermissionCodes()).containsAll(FormsPermissionCode.DESIGNER_BUNDLE);
        assertThat(saved.get(1).getPermissionCodes()).doesNotContain(FormsPermissionCode.VERSIONS_PUBLISH);
        assertThat(saved.get(2).getPermissionCodes()).containsAll(FormsPermissionCode.PUBLISHER_BUNDLE);
        assertThat(saved.get(3).getPermissionCodes()).containsAll(FormsPermissionCode.ANALYST_BUNDLE);
        assertThat(saved.get(4).getPermissionCodes()).containsAll(FormsPermissionCode.RESPONDENT_BUNDLE);
        assertThat(saved).allMatch(FormsRoleEntity::isSystemDefined);
    }

    @Test
    void ensureSystemRolesSkipsExisting() {
        when(roles.existsByCompanyIdAndCode(eq(companyId), any())).thenReturn(true);

        authz.ensureSystemRoles(companyId);

        verify(roles, never()).save(any());
    }

    @Test
    void assignedPermissionsReturnsSortedUnion() {
        CurrentUser user = actor(userId, companyId);
        when(roles.findPermissionCodesByCompanyAndUser(companyId, userId))
                .thenReturn(List.of(
                        FormsPermissionCode.VERSIONS_WRITE, FormsPermissionCode.FORMS_READ));

        assertThat(authz.assignedPermissions(user))
                .containsExactly(FormsPermissionCode.FORMS_READ, FormsPermissionCode.VERSIONS_WRITE);
    }

    private static CurrentUser actor(UUID userId, UUID companyId) {
        return new CurrentUser(userId, companyId, "EMP-1", "forms", "sid", 0L, "web");
    }
}
