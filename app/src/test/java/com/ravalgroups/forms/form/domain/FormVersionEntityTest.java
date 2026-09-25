package com.ravalgroups.forms.form.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionEntity;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FormVersionEntityTest {

    @Test
    void optimisticLockRejectsStaleRevision() {
        FormVersionEntity draft = FormVersionEntity.createDraft(
                UuidV7.create(),
                UuidV7.create(),
                1,
                "{\"pages\":[{\"id\":\"p1\",\"title\":\"t\",\"components\":[]}],\"questions\":[],\"rules\":[]}",
                UuidV7.create(),
                Instant.now());
        DomainException ex = assertThrows(
                DomainException.class,
                () -> draft.updateDraft(draft.getDefinitionJson(), 99L, Instant.now()));
        assertEquals("FORM_VERSION_CONCURRENT_MODIFICATION", ex.code());
    }

    @Test
    void publishedVersionCannotBeEdited() {
        FormVersionEntity draft = FormVersionEntity.createDraft(
                UuidV7.create(),
                UuidV7.create(),
                1,
                "{\"pages\":[{\"id\":\"p1\",\"title\":\"t\",\"components\":[]}],\"questions\":[],\"rules\":[]}",
                UuidV7.create(),
                Instant.now());
        draft.publish(UuidV7.create(), Instant.now());
        DomainException ex = assertThrows(
                DomainException.class,
                () -> draft.updateDraft(draft.getDefinitionJson(), 0L, Instant.now()));
        assertEquals("FORM_VERSION_NOT_EDITABLE", ex.code());
    }

    @Test
    void uuidV7CreateOrUseRejectsNonV7() {
        UUID v4 = UUID.randomUUID();
        DomainException ex = assertThrows(DomainException.class, () -> UuidV7.createOrUse(v4));
        assertEquals("VALIDATION_ERROR", ex.code());
        assertTrue(UuidV7.create().version() == 7);
    }
}
