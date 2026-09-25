package com.ravalgroups.forms.iam.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamUserProjectionJpaRepository extends JpaRepository<IamUserProjectionEntity, UUID> {}
