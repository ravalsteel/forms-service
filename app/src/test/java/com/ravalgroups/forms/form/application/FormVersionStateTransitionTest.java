package com.ravalgroups.forms.form.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionEntity;
import com.ravalgroups.forms.form.domain.FormVersionStatus;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FormVersionStateTransitionTest {

    private static final String DEFINITION =
            "{\"pages\":[{\"id\":\"p1\",\"title\":\"P\",\"components\":[]}],\"questions\":[],\"rules\":[]}";

    @Test
    void publishMovesDraftToPublished() {
        FormVersionEntity version = draft();
        version.publish(UuidV7.create(), Instant.now());
        assertThat(version.getStatus()).isEqualTo(FormVersionStatus.PUBLISHED);
        assertThat(version.getPublishedAt()).isNotNull();
        assertThat(version.getPublishedBy()).isNotNull();
    }

    @Test
    void cannotUpdatePublishedVersion() {
        FormVersionEntity version = draft();
        version.publish(UuidV7.create(), Instant.now());
        assertThatThrownBy(() -> version.updateDraft(DEFINITION, 0L, Instant.now()))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("FORM_VERSION_NOT_EDITABLE");
    }

    @Test
    void wrongExpectedRevisionThrowsConcurrentModification() {
        FormVersionEntity version = draft();
        assertThatThrownBy(() -> version.updateDraft(DEFINITION, 99L, Instant.now()))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("FORM_VERSION_CONCURRENT_MODIFICATION");
    }

    @Test
    void cannotPublishTwice() {
        FormVersionEntity version = draft();
        version.publish(UuidV7.create(), Instant.now());
        assertThatThrownBy(() -> version.publish(UuidV7.create(), Instant.now()))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("FORM_VERSION_NOT_EDITABLE");
    }

    private static FormVersionEntity draft() {
        return FormVersionEntity.createDraft(
                UuidV7.create(), UuidV7.create(), 1, DEFINITION, UuidV7.create(), Instant.now());
    }
}
