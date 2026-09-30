package com.ravalgroups.forms.iam.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamDepartmentProjectionJpaRepository extends JpaRepository<IamDepartmentProjectionEntity, UUID> {

    List<IamDepartmentProjectionEntity> findByCompanyIdOrderByNameAsc(UUID companyId);
}
