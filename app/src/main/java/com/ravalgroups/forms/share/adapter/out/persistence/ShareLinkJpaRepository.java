package com.ravalgroups.forms.share.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkJpaRepository extends JpaRepository<ShareLinkEntity, UUID> {

    Optional<ShareLinkEntity> findByTokenHash(String tokenHash);

    Optional<ShareLinkEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    List<ShareLinkEntity> findByCompanyIdAndFormRunIdOrderByCreatedAtDesc(UUID companyId, UUID formRunId);
}
