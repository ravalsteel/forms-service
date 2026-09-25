package com.ravalgroups.forms.run.adapter.out.persistence;

import com.ravalgroups.forms.run.domain.FormRunStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormRunJpaRepository extends JpaRepository<FormRunEntity, UUID> {

    List<FormRunEntity> findByCompanyIdAndFormIdOrderByCreatedAtDesc(UUID companyId, UUID formId);

    List<FormRunEntity> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<FormRunEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByFormVersionId(UUID formVersionId);
}
