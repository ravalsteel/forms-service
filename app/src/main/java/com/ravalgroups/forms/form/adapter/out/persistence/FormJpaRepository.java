package com.ravalgroups.forms.form.adapter.out.persistence;

import com.ravalgroups.forms.form.domain.FormStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormJpaRepository extends JpaRepository<FormEntity, UUID> {

    List<FormEntity> findByCompanyIdOrderByUpdatedAtDesc(UUID companyId);

    List<FormEntity> findByCompanyIdAndStatusOrderByUpdatedAtDesc(UUID companyId, FormStatus status);

    Optional<FormEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
