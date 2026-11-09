package com.ravalgroups.forms.export.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseAnswerEntity;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadableResponseExportTest {

    private static final String DEFINITION = """
            {
              "questions": [
                {"key":"shift","label":"Shift","type":"SINGLE_CHOICE","configuration":{"options":[{"value":"a","label":"Morning"},{"value":"b","label":"Night"}]}},
                {"key":"sites","label":"Sites","type":"MULTI_CHOICE","configuration":{"options":[{"value":"dk","label":"Dukem"},{"value":"aa","label":"Addis Ababa"}]}},
                {"key":"safe","label":"Felt safe","type":"BOOLEAN"},
                {"key":"score","label":"Score","type":"NUMBER"},
                {"key":"when","label":"Date","type":"DATE"},
                {"key":"grid","label":"Ratings","type":"MATRIX","configuration":{
                  "rows":[{"key":"speed","label":"Speed"}],
                  "columns":[{"value":"good","label":"Good"}]
                }},
                {"key":"note","label":"Name","type":"TEXT"}
              ]
            }
            """;

    @Test
    void onePersonPerRowWithQuestionLabels() {
        List<ReadableResponseExport.QuestionColumn> questions =
                ReadableResponseExport.questionsFromDefinition(new ObjectMapper(), DEFINITION, new LinkedHashSet<>());
        Instant now = Instant.parse("2026-11-09T06:51:22Z");
        Map<String, ResponseAnswerEntity> answers = new LinkedHashMap<>();
        answers.put("shift", answer("shift", "TEXT", "a", null, null, null, null, null));
        answers.put("sites", answer("sites", "JSON", null, null, null, null, "[\"dk\",\"aa\"]", null));
        answers.put("safe", answer("safe", "BOOLEAN", null, null, true, null, null, null));
        answers.put("score", answer("score", "NUMBER", null, 4.0, null, null, null, null));
        answers.put("when", answer("when", "DATE", null, null, null, LocalDate.parse("2026-11-01"), null, null));
        answers.put("grid", answer("grid", "JSON", null, null, null, null, "{\"cells\":{\"speed\":\"good\"},\"comment\":\"ok\"}", null));
        answers.put("note", answer("note", "TEXT", "Hello, \"world\"", null, null, null, null, null));

        ReadableResponseExport.Person person = new ReadableResponseExport.Person(
                "XP100011", "Yerram Reddy", "SMS Production", "Melting", "2026-11-09 06:51", answers);
        ReadableResponseExport.Grid grid = ReadableResponseExport.grid(questions, List.of(person));
        String csv = ReadableResponseExport.toCsv(grid);

        assertEquals(
                List.of(
                        "Employee ID",
                        "Name",
                        "Department",
                        "Sub-department",
                        "Submitted",
                        "Shift",
                        "Sites",
                        "Felt safe",
                        "Score",
                        "Date",
                        "Ratings",
                        "Name (2)"),
                grid.headers());
        assertEquals(
                List.of(
                        "XP100011",
                        "Yerram Reddy",
                        "SMS Production",
                        "Melting",
                        "2026-11-09 06:51",
                        "Morning",
                        "Dukem; Addis Ababa",
                        "Yes",
                        "4",
                        "2026-11-01",
                        "Speed: Good; ok",
                        "Hello, \"world\""),
                grid.rows().get(0));
        assertTrue(csv.startsWith("\uFEFFEmployee ID,Name,Department"));
        assertTrue(csv.contains("\"Hello, \"\"world\"\"\""));
        assertEquals("2026-11-09 06:51", ReadableResponseExport.formatSubmitted(now));
    }

    private static ResponseAnswerEntity answer(
            String key,
            String type,
            String text,
            Double number,
            Boolean bool,
            LocalDate date,
            String json,
            java.util.UUID objectId) {
        return ResponseAnswerEntity.create(
                UuidV7.create(), UuidV7.create(), key, type, text, number, bool, date, null, json, objectId, Instant.now());
    }
}
