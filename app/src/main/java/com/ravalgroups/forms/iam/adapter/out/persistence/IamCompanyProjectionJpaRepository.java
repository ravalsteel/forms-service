package com.ravalgroups.forms.iam.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamCompanyProjectionJpaRepository extends JpaRepository<IamCompanyProjectionEntity, UUID> {

    Optional<IamCompanyProjectionEntity> findByCode(String code);
}
