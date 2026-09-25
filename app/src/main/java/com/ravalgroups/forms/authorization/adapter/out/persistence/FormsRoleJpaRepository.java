package com.ravalgroups.forms.authorization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormsRoleJpaRepository extends JpaRepository<FormsRoleEntity, UUID> {

    List<FormsRoleEntity> findByCompanyIdOrderBySystemDefinedDescCodeAsc(UUID companyId);

    Optional<FormsRoleEntity> findByCompanyIdAndCode(UUID companyId, String code);

    Optional<FormsRoleEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndCode(UUID companyId, String code);

    @Query(
            """
            select distinct p from FormsUserRoleEntity ur
            join FormsRoleEntity r on r.id = ur.roleId
            join r.permissionCodes p
            where ur.companyId = :companyId and ur.iamUserId = :iamUserId
            """)
    List<String> findPermissionCodesByCompanyAndUser(
            @Param("companyId") UUID companyId, @Param("iamUserId") UUID iamUserId);

    @Query(
            """
            select case when count(ur) > 0 then true else false end
            from FormsUserRoleEntity ur
            join FormsRoleEntity r on r.id = ur.roleId
            where ur.companyId = :companyId
              and :permission member of r.permissionCodes
            """)
    boolean existsUserWithPermission(
            @Param("companyId") UUID companyId, @Param("permission") String permission);

    @Query(
            """
            select case when count(ur) > 0 then true else false end
            from FormsUserRoleEntity ur
            join FormsRoleEntity r on r.id = ur.roleId
            where ur.companyId = :companyId and ur.iamUserId = :iamUserId
              and :permission member of r.permissionCodes
            """)
    boolean userHasPermission(
            @Param("companyId") UUID companyId,
            @Param("iamUserId") UUID iamUserId,
            @Param("permission") String permission);

    @Query(
            """
            select count(distinct ur.iamUserId)
            from FormsUserRoleEntity ur
            join FormsRoleEntity r on r.id = ur.roleId
            where ur.companyId = :companyId
              and :permission member of r.permissionCodes
              and not (ur.iamUserId = :excludeUserId and ur.roleId = :excludeRoleId)
            """)
    long countUsersWithPermissionExcludingAssignment(
            @Param("companyId") UUID companyId,
            @Param("permission") String permission,
            @Param("excludeUserId") UUID excludeUserId,
            @Param("excludeRoleId") UUID excludeRoleId);
}
