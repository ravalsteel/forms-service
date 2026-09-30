package com.ravalgroups.forms.run.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormRunAudienceRuleJpaRepository extends JpaRepository<FormRunAudienceRuleEntity, UUID> {

    List<FormRunAudienceRuleEntity> findByFormRunIdOrderByCreatedAtAsc(UUID formRunId);

    List<FormRunAudienceRuleEntity> findByFormRunIdIn(Collection<UUID> formRunIds);

    void deleteByFormRunId(UUID formRunId);

    long countByFormRunId(UUID formRunId);
}
