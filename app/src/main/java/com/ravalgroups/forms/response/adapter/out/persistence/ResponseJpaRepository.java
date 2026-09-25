package com.ravalgroups.forms.response.adapter.out.persistence;

import com.ravalgroups.forms.response.domain.ResponseStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResponseJpaRepository extends JpaRepository<ResponseEntity, UUID> {

    Optional<ResponseEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<ResponseEntity> findByFormRunIdAndIdempotencyKey(UUID formRunId, String idempotencyKey);

    long countByFormRunIdAndStatus(UUID formRunId, ResponseStatus status);

    long countByFormRunId(UUID formRunId);

    List<ResponseEntity> findByFormRunIdAndStatus(UUID formRunId, ResponseStatus status);

    @Query("""
            select r from ResponseEntity r
            where r.status = com.ravalgroups.forms.response.domain.ResponseStatus.SUBMITTED
              and r.anonymizedAt is null
            order by r.submittedAt asc
            """)
    List<ResponseEntity> findSubmittedNotAnonymized(org.springframework.data.domain.Pageable pageable);

    @Query("""
            select a from ResponseAnswerEntity a
            join a.response r
            where r.formRunId = :runId
              and r.status = com.ravalgroups.forms.response.domain.ResponseStatus.SUBMITTED
              and a.questionId = :questionId
            """)
    List<ResponseAnswerEntity> findSubmittedAnswers(
            @Param("runId") UUID runId, @Param("questionId") UUID questionId);
}
