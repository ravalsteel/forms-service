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

    @Test
    void acceptsMatrixDefinitionAndMeta() {
        assertDoesNotThrow(() -> validator.parseAndValidate("""
                {
                  "meta":{"issuingBodyTitle":"HR Department","introductionHtml":"<p>Hello</p>"},
                  "pages":[{"id":"p1","title":"P","components":[{"id":"c1","type":"QUESTION","questionId":"q1"}]}],
                  "questions":[{
                    "id":"q1","key":"wage","type":"MATRIX","label":"Wage policies","required":true,
                    "configuration":{
                      "rows":[{"key":"salary","label":"I am satisfied with my current salary."}],
                      "columns":[
                        {"value":1,"label":"Very dissatisfied"},
                        {"value":5,"label":"Very Satisfied"}
                      ],
                      "allowComment":true
                    }
                  }],
                  "rules":[]
                }
                """));
    }

    @Test
    void acceptsContentLocalesAndI18nOverlays() {
        assertDoesNotThrow(() -> validator.parseAndValidate("""
                {
                  "meta":{
                    "defaultLocale":"en",
                    "contentLocales":["en","pt"],
                    "issuingBodyTitle":"HR",
                    "introductionHtml":"<p>Hi</p>",
                    "i18n":{"pt":{"issuingBodyTitle":"RH","introductionHtml":"<p>Olá</p>"}}
                  },
                  "pages":[{"id":"p1","title":"P","components":[{"id":"c1","type":"QUESTION","questionId":"q1"}]}],
                  "questions":[{
                    "id":"q1","key":"wage","type":"MATRIX","label":"Wage",
                    "i18n":{"pt":{"label":"Salário"}},
                    "required":true,
                    "configuration":{
                      "rows":[{
                        "key":"salary","label":"I am satisfied with my current salary.",
                        "i18n":{"pt":{"label":"Estou satisfeito com o meu salário atual."}}
                      }],
                      "columns":[
                        {"value":1,"label":"Very dissatisfied","i18n":{"pt":{"label":"Muito insatisfeito"}}},
                        {"value":5,"label":"Very Satisfied","i18n":{"pt":{"label":"Muito satisfeito"}}}
                      ],
                      "allowComment":true,
                      "commentLabel":"Comment",
                      "commentLabelI18n":{"pt":"Comentário"}
                    }
                  }],
                  "rules":[]
                }
                """));
    }

    @Test
    void rejectsUnknownContentLocale() {
        DomainException ex = assertThrows(DomainException.class, () -> validator.parseAndValidate("""
                {
                  "meta":{"defaultLocale":"en","contentLocales":["en","fr"]},
                  "pages":[{"id":"p1","title":"P","components":[]}],
                  "questions":[{"id":"q1","key":"a","type":"SHORT_TEXT","label":"A"}],
                  "rules":[]
                }
                """));
        assertEquals("INVALID_FORM_DEFINITION", ex.code());
    }

    @Test
    void rejectsI18nLocaleOutsideContentLocales() {
        // Overlays for known locales are allowed even if temporarily not in contentLocales.
        assertDoesNotThrow(() -> validator.parseAndValidate("""
                {
                  "meta":{"defaultLocale":"en","contentLocales":["en"]},
                  "pages":[{"id":"p1","title":"P","components":[]}],
                  "questions":[{
                    "id":"q1","key":"a","type":"SHORT_TEXT","label":"A",
                    "i18n":{"pt":{"label":"A em português"}}
                  }],
                  "rules":[]
                }
                """));
    }

    @Test
    void rejectsUnsupportedI18nLocaleCode() {
        DomainException ex = assertThrows(DomainException.class, () -> validator.parseAndValidate("""
                {
                  "meta":{"defaultLocale":"en","contentLocales":["en"]},
                  "pages":[{"id":"p1","title":"P","components":[]}],
                  "questions":[{
                    "id":"q1","key":"a","type":"SHORT_TEXT","label":"A",
                    "i18n":{"fr":{"label":"A en français"}}
                  }],
                  "rules":[]
                }
                """));
        assertEquals("INVALID_FORM_DEFINITION", ex.code());
    }
}
