package com.ravalgroups.forms.iam.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamSubDepartmentProjectionJpaRepository
        extends JpaRepository<IamSubDepartmentProjectionEntity, UUID> {

    List<IamSubDepartmentProjectionEntity> findByCompanyIdOrderByNameAsc(UUID companyId);

    List<IamSubDepartmentProjectionEntity> findByCompanyIdAndDepartmentIdOrderByNameAsc(
            UUID companyId, UUID departmentId);
}
