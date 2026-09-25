package com.ravalgroups.forms.audit.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormsAuditEventJpaRepository extends JpaRepository<FormsAuditEventEntity, UUID> {}
