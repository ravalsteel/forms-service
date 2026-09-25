package com.ravalgroups.forms.template.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormTemplateJpaRepository extends JpaRepository<FormTemplateEntity, UUID> {

    @Query("""
            select t from FormTemplateEntity t
            where t.status = com.ravalgroups.forms.template.domain.TemplateStatus.ACTIVE
              and (t.scope = com.ravalgroups.forms.template.domain.TemplateScope.GLOBAL
                   or t.companyId = :companyId)
            order by t.updatedAt desc
            """)
    List<FormTemplateEntity> findVisible(@Param("companyId") UUID companyId);

    Optional<FormTemplateEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<FormTemplateEntity> findById(UUID id);
}
