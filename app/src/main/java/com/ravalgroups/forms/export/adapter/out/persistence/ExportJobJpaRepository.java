package com.ravalgroups.forms.export.adapter.out.persistence;

import com.ravalgroups.forms.export.domain.ExportJobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExportJobJpaRepository extends JpaRepository<ExportJobEntity, UUID> {

    Optional<ExportJobEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("select e from ExportJobEntity e where e.status = com.ravalgroups.forms.export.domain.ExportJobStatus.PENDING order by e.createdAt asc")
    List<ExportJobEntity> findPending(Pageable pageable);
}
