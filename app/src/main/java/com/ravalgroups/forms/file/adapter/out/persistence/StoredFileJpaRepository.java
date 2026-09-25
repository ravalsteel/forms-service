package com.ravalgroups.forms.file.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileJpaRepository extends JpaRepository<StoredFileEntity, UUID> {

    Optional<StoredFileEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
