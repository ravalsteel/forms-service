package com.ravalgroups.forms.authorization.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface FormsUserRoleJpaRepository extends JpaRepository<FormsUserRoleEntity, UUID> {

    List<FormsUserRoleEntity> findByCompanyIdAndIamUserId(UUID companyId, UUID iamUserId);

    List<FormsUserRoleEntity> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<FormsUserRoleEntity> findByCompanyIdAndRoleIdOrderByCreatedAtDesc(UUID companyId, UUID roleId);

    boolean existsByCompanyIdAndIamUserId(UUID companyId, UUID iamUserId);

    boolean existsByCompanyIdAndIamUserIdAndRoleId(UUID companyId, UUID iamUserId, UUID roleId);

    boolean existsByRoleId(UUID roleId);

    long countByCompanyIdAndRoleId(UUID companyId, UUID roleId);

    @Modifying
    @Transactional
    void deleteByCompanyIdAndIamUserIdAndRoleId(UUID companyId, UUID iamUserId, UUID roleId);
}
