package com.ravalgroups.forms.authorization.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormsRoleAssignmentJpaRepository extends JpaRepository<FormsRoleAssignmentEntity, UUID> {

    List<FormsRoleAssignmentEntity> findByCompanyIdAndIamUserId(UUID companyId, UUID iamUserId);

    List<FormsRoleAssignmentEntity> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<FormsRoleAssignmentEntity> findByCompanyIdAndRoleCodeOrderByCreatedAtDesc(UUID companyId, String roleCode);

    boolean existsByCompanyIdAndIamUserIdAndRoleCodeIn(
            UUID companyId, UUID iamUserId, Collection<String> roleCodes);

    boolean existsByCompanyIdAndRoleCode(UUID companyId, String roleCode);

    boolean existsByCompanyIdAndIamUserIdAndRoleCode(UUID companyId, UUID iamUserId, String roleCode);

    long countByCompanyIdAndRoleCode(UUID companyId, String roleCode);

    void deleteByCompanyIdAndIamUserIdAndRoleCode(UUID companyId, UUID iamUserId, String roleCode);
}
