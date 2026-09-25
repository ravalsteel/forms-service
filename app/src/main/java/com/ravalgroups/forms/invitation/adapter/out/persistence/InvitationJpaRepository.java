package com.ravalgroups.forms.invitation.adapter.out.persistence;

import com.ravalgroups.forms.invitation.domain.InvitationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationJpaRepository extends JpaRepository<InvitationEntity, UUID> {

    long countByFormRunId(UUID formRunId);

    Optional<InvitationEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    List<InvitationEntity> findByCompanyIdAndFormRunIdOrderByCreatedAtDesc(UUID companyId, UUID formRunId);

    @Query("""
            select i from InvitationEntity i
            where i.status = com.ravalgroups.forms.invitation.domain.InvitationStatus.PENDING
              and i.expiresAt is not null
              and i.expiresAt < :now
            """)
    List<InvitationEntity> findExpiredPending(@Param("now") Instant now, Pageable pageable);
}
