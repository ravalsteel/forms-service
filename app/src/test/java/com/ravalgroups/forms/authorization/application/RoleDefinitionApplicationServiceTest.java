package com.ravalgroups.forms.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.authorization.FormsPermissionCode;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsPermissionEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsPermissionJpaRepository;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleJpaRepository;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleJpaRepository;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
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
class RoleDefinitionApplicationServiceTest {

    @Mock
    private FormsRoleJpaRepository roles;

    @Mock
    private FormsPermissionJpaRepository permissions;

    @Mock
    private FormsUserRoleJpaRepository userRoles;

    @Mock
    private FormsAuthorizationService authz;

    private RoleDefinitionApplicationService service;

    private final UUID companyId = UUID.fromString("5aa7230d-fe4d-4f5f-a70a-1e4b19af5a65");
    private final UUID userId = UUID.fromString("a2000000-0000-4000-8000-000000000010");

    @BeforeEach
    void setUp() {
        service = new RoleDefinitionApplicationService(roles, permissions, userRoles, authz);
    }

    @Test
    void createPersistsCustomRoleWithValidatedPermissions() {
        CurrentUser actor = actor(userId, companyId);
        stubKnownPermissions();
        when(roles.existsByCompanyIdAndCode(companyId, "CUSTOM")).thenReturn(false);
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var view = service.create(
                actor,
                "custom",
                "Custom",
                "desc",
                List.of(FormsPermissionCode.FORMS_READ, FormsPermissionCode.VERSIONS_WRITE));

        assertThat(view.code()).isEqualTo("CUSTOM");
        assertThat(view.systemDefined()).isFalse();
        assertThat(view.permissionCodes())
                .containsExactly(FormsPermissionCode.FORMS_READ, FormsPermissionCode.VERSIONS_WRITE);
        ArgumentCaptor<FormsRoleEntity> captor = ArgumentCaptor.forClass(FormsRoleEntity.class);
        verify(roles).save(captor.capture());
        assertThat(captor.getValue().isSystemDefined()).isFalse();
    }

    @Test
    void createRejectsUnknownPermission() {
        stubKnownPermissions();
        when(roles.existsByCompanyIdAndCode(companyId, "CUSTOM")).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                        actor(userId, companyId), "custom", "Custom", null, List.of("forms.nope")))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("VALIDATION_ERROR");
        verify(roles, never()).save(any());
    }

    @Test
    void updateAllowsSystemRolePermissionChangeButKeepsCode() {
        UUID roleId = UUID.randomUUID();
        FormsRoleEntity admin = FormsRoleEntity.create(
                roleId,
                companyId,
                "ADMIN",
                "Administrator",
                "Full",
                true,
                new LinkedHashSet<>(FormsPermissionCode.ADMIN_BUNDLE),
                Instant.now());
        when(roles.findByIdAndCompanyId(roleId, companyId)).thenReturn(Optional.of(admin));
        stubKnownPermissions();
        when(roles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var view = service.update(
                actor(userId, companyId),
                roleId,
                "Company Admin",
                "Updated",
                List.of(FormsPermissionCode.ROLES_MANAGE, FormsPermissionCode.FORMS_READ));

        assertThat(view.code()).isEqualTo("ADMIN");
        assertThat(view.name()).isEqualTo("Company Admin");
        assertThat(view.systemDefined()).isTrue();
        assertThat(view.permissionCodes())
                .containsExactly(FormsPermissionCode.FORMS_READ, FormsPermissionCode.ROLES_MANAGE);
    }

    @Test
    void deleteRejectsSystemDefined() {
        UUID roleId = UUID.randomUUID();
        FormsRoleEntity admin = FormsRoleEntity.create(
                roleId,
                companyId,
                "ADMIN",
                "Administrator",
                null,
                true,
                Set.copyOf(FormsPermissionCode.ADMIN_BUNDLE),
                Instant.now());
        when(roles.findByIdAndCompanyId(roleId, companyId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.delete(actor(userId, companyId), roleId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("SYSTEM_ROLE");
        verify(roles, never()).delete(any());
    }

    @Test
    void deleteRejectsWhenAssigned() {
        UUID roleId = UUID.randomUUID();
        FormsRoleEntity custom = FormsRoleEntity.create(
                roleId,
                companyId,
                "CUSTOM",
                "Custom",
                null,
                false,
                Set.of(FormsPermissionCode.FORMS_READ),
                Instant.now());
        when(roles.findByIdAndCompanyId(roleId, companyId)).thenReturn(Optional.of(custom));
        when(userRoles.existsByRoleId(roleId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(actor(userId, companyId), roleId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("ROLE_IN_USE");
    }

    private void stubKnownPermissions() {
        when(permissions.findAll())
                .thenAnswer(inv -> FormsPermissionCode.ALL.stream()
                        .map(code -> {
                            FormsPermissionEntity e =
                                    org.mockito.Mockito.mock(FormsPermissionEntity.class);
                            org.mockito.Mockito.lenient().when(e.getCode()).thenReturn(code);
                            return e;
                        })
                        .toList());
    }

    private static CurrentUser actor(UUID userId, UUID companyId) {
        return new CurrentUser(userId, companyId, "EMP-1", "forms", "sid", 0L, "web");
    }
}
