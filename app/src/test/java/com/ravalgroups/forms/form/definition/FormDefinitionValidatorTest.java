package com.ravalgroups.forms.form.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FormDefinitionValidatorTest {

    private FormDefinitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FormDefinitionValidator(new ObjectMapper());
    }

    @Test
    void acceptsValidDefinition() {
        assertDoesNotThrow(() -> validator.parseAndValidate(validDefinition()));
    }

    @Test
    void rejectsDuplicateQuestionKeys() {
        String json = """
                {
                  "pages":[{"id":"p1","title":"P","components":[]}],
                  "questions":[
                    {"id":"q1","key":"score","type":"RATING","label":"A","required":true},
                    {"id":"q2","key":"score","type":"RATING","label":"B","required":false}
                  ],
                  "rules":[]
                }
                """;
        DomainException ex = assertThrows(DomainException.class, () -> validator.parseAndValidate(json));
        assertEquals("INVALID_FORM_DEFINITION", ex.code());
    }

    @Test
    void rejectsSkipToCycle() {
        DomainException ex = assertThrows(
                DomainException.class, () -> validator.detectSkipCycles(Map.of("a", Set.of("b"), "b", Set.of("a"))));
        assertEquals("INVALID_FORM_DEFINITION", ex.code());
    }

    @Test
    void rejectsUnknownRuleReference() {
        String json = """
                {
                  "pages":[{"id":"p1","title":"P","components":[]}],
                  "questions":[
                    {"id":"q1","key":"has_manager","type":"BOOLEAN","label":"Has manager","required":true}
                  ],
                  "rules":[
                    {"id":"r1","when":{"questionKey":"has_manager","operator":"EQUALS","value":true},
                     "actions":[{"type":"SHOW","questionKey":"missing_key"}]}
                  ]
                }
                """;
        DomainException ex = assertThrows(DomainException.class, () -> validator.parseAndValidate(json));
        assertEquals("INVALID_FORM_DEFINITION", ex.code());
    }

    private static String validDefinition() {
        return """
                {
                  "pages":[{"id":"p1","title":"Page 1","components":[
                    {"id":"c1","type":"QUESTION","questionId":"q1"}
                  ]}],
                  "questions":[
                    {"id":"q1","key":"has_manager","type":"BOOLEAN","label":"Has manager?","required":true},
                    {"id":"q2","key":"manager_satisfaction","type":"RATING","label":"Satisfaction","required":false}
                  ],
                  "rules":[
                    {"id":"r1","when":{"questionKey":"has_manager","operator":"EQUALS","value":true},
                     "actions":[{"type":"SHOW","questionKey":"manager_satisfaction"}]}
                  ]
                }
                """;
    }
}
