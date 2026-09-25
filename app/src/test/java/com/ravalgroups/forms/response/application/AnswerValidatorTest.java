package com.ravalgroups.forms.response.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnswerValidatorTest {

    private AnswerValidator validator;
    private String definition;
    private UUID questionId;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        validator = new AnswerValidator(new FormDefinitionValidator(mapper), mapper);
        questionId = UuidV7.create();
        definition = """
                {
                  "pages":[{"id":"p1","title":"P","components":[]}],
                  "questions":[
                    {"id":"%s","key":"score","type":"RATING","label":"Score","required":true}
                  ],
                  "rules":[]
                }
                """.formatted(questionId);
    }

    @Test
    void mapsNumericAnswer() {
        var answers = validator.mapAnswers(
                definition,
                List.of(new AnswerValidator.AnswerInput(
                        questionId, null, null, 4.0, null, null, null, null, null)),
                true,
                Instant.now());
        assertEquals(1, answers.size());
        assertEquals("NUMBER", answers.getFirst().getValueType());
        assertEquals(4.0, answers.getFirst().getNumberValue());
    }

    @Test
    void rejectsMissingRequiredOnSubmit() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> validator.mapAnswers(definition, List.of(), true, Instant.now()));
        assertEquals("INVALID_ANSWER", ex.code());
    }

    @Test
    void rejectsUnknownQuestion() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> validator.mapAnswers(
                        definition,
                        List.of(new AnswerValidator.AnswerInput(
                                UuidV7.create(), null, null, 1.0, null, null, null, null, null)),
                        false,
                        Instant.now()));
        assertEquals("INVALID_QUESTION_REFERENCE", ex.code());
    }
}
