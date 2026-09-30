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

    @Test
    void mapsMatrixAnswer() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID matrixId = UuidV7.create();
        String matrixDefinition = """
                {
                  "pages":[{"id":"p1","title":"P","components":[]}],
                  "questions":[{
                    "id":"%s","key":"wage","type":"MATRIX","label":"Wage","required":true,
                    "configuration":{
                      "rows":[{"key":"salary","label":"Salary"}],
                      "columns":[{"value":1,"label":"Low"},{"value":5,"label":"High"}],
                      "allowComment":true
                    }
                  }],
                  "rules":[]
                }
                """.formatted(matrixId);
        String json = mapper.writeValueAsString(
                java.util.Map.of("cells", java.util.Map.of("salary", 5), "comment", "ok"));
        var answers = validator.mapAnswers(
                matrixDefinition,
                List.of(new AnswerValidator.AnswerInput(
                        matrixId, null, null, null, null, null, null, json, null)),
                true,
                Instant.now());
        assertEquals(1, answers.size());
        assertEquals("JSON", answers.getFirst().getValueType());
    }
}
