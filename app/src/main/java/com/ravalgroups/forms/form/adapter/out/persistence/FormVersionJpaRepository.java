package com.ravalgroups.forms.form.adapter.out.persistence;

import com.ravalgroups.forms.form.domain.FormVersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormVersionJpaRepository extends JpaRepository<FormVersionEntity, UUID> {

    List<FormVersionEntity> findByFormIdOrderByVersionNumberDesc(UUID formId);

    Optional<FormVersionEntity> findByIdAndFormId(UUID id, UUID formId);

    Optional<FormVersionEntity> findByFormIdAndStatus(UUID formId, FormVersionStatus status);

    @Query("select coalesce(max(v.versionNumber), 0) from FormVersionEntity v where v.formId = :formId")
    int findMaxVersionNumber(@Param("formId") UUID formId);
}
