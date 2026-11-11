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

    long countByFormVersionId(UUID formVersionId);

    List<ResponseEntity> findByFormRunIdAndStatus(UUID formRunId, ResponseStatus status);

    List<ResponseEntity> findByFormRunId(UUID formRunId);

    Optional<ResponseEntity> findFirstByFormRunIdAndRespondentIdOrderByStartedAtDesc(
            UUID formRunId, UUID respondentId);

    Optional<ResponseEntity> findFirstByFormRunIdAndRespondentIdAndStatusOrderByUpdatedAtDesc(
            UUID formRunId, UUID respondentId, ResponseStatus status);

    List<ResponseEntity> findByFormRunIdInAndRespondentId(List<UUID> formRunIds, UUID respondentId);

    @Query("""
            select count(distinct r.respondentId) from ResponseEntity r
            where r.formRunId = :runId and r.respondentId is not null
            """)
    long countDistinctRespondentsByFormRunId(@Param("runId") UUID runId);

    @Query("""
            select count(distinct r.respondentId) from ResponseEntity r
            where r.formRunId = :runId
              and r.respondentId is not null
              and r.status = :status
            """)
    long countDistinctRespondentsByFormRunIdAndStatus(
            @Param("runId") UUID runId, @Param("status") ResponseStatus status);

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
