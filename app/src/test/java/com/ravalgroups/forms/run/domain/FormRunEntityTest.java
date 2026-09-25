package com.ravalgroups.forms.run.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FormRunEntityTest {

    @Test
    void closedRunRejectsSubmissionGate() {
        FormRunEntity run = FormRunEntity.create(
                UuidV7.create(),
                UuidV7.create(),
                UuidV7.create(),
                UuidV7.create(),
                "Q1",
                FormRunStatus.OPEN,
                RespondentMode.IDENTIFIED,
                null,
                null,
                5,
                UuidV7.create(),
                Instant.now());
        run.close(Instant.now());
        DomainException ex = assertThrows(DomainException.class, run::requireOpen);
        assertEquals("FORM_RUN_NOT_OPEN", ex.code());
    }
}
