package com.ravalgroups.forms.form.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormAccessJpaRepository extends JpaRepository<FormAccessEntity, UUID> {

    List<FormAccessEntity> findByFormIdOrderByCreatedAtAsc(UUID formId);

    Optional<FormAccessEntity> findByFormIdAndIamUserId(UUID formId, UUID iamUserId);

    void deleteByFormIdAndIamUserId(UUID formId, UUID iamUserId);

    @Query("""
            select distinct f from FormEntity f
            where f.companyId = :companyId
              and (
                f.createdBy = :userId
                or exists (
                  select 1 from FormAccessEntity a
                  where a.formId = f.id and a.iamUserId = :userId
                )
              )
            order by f.updatedAt desc
            """)
    List<FormEntity> findAccessibleByCompanyAndUser(
            @Param("companyId") UUID companyId, @Param("userId") UUID userId);

    @Query("""
            select distinct f from FormEntity f
            where f.companyId = :companyId
              and f.status = :status
              and (
                f.createdBy = :userId
                or exists (
                  select 1 from FormAccessEntity a
                  where a.formId = f.id and a.iamUserId = :userId
                )
              )
            order by f.updatedAt desc
            """)
    List<FormEntity> findAccessibleByCompanyUserAndStatus(
            @Param("companyId") UUID companyId,
            @Param("userId") UUID userId,
            @Param("status") com.ravalgroups.forms.form.domain.FormStatus status);
}
