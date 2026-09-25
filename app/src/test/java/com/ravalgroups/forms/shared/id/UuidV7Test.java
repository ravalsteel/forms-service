package com.ravalgroups.forms.shared.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void createReturnsVersion7() {
        assertThat(UuidV7.create().version()).isEqualTo(7);
    }

    @Test
    void createOrUseAcceptsV7() {
        UUID id = UuidV7.create();
        assertThat(UuidV7.createOrUse(id)).isEqualTo(id);
    }

    @Test
    void createOrUseRejectsV4() {
        UUID v4 = UUID.randomUUID();
        assertThatThrownBy(() -> UuidV7.createOrUse(v4))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createOrUseNullGenerates() {
        assertThat(UuidV7.createOrUse(null).version()).isEqualTo(7);
    }
}
