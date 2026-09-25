package com.ravalgroups.forms.form.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class QuestionTypes {

    public static final String SHORT_TEXT = "SHORT_TEXT";
    public static final String LONG_TEXT = "LONG_TEXT";
    public static final String TEXT = "TEXT";
    public static final String NUMBER = "NUMBER";
    public static final String DECIMAL = "DECIMAL";
    public static final String EMAIL = "EMAIL";
    public static final String PHONE = "PHONE";
    public static final String URL = "URL";
    public static final String SINGLE_CHOICE = "SINGLE_CHOICE";
    public static final String MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    public static final String MULTI_CHOICE = "MULTI_CHOICE";
    public static final String DROPDOWN = "DROPDOWN";
    public static final String RATING = "RATING";
    public static final String LIKERT = "LIKERT";
    public static final String NPS = "NPS";
    public static final String DATE = "DATE";
    public static final String TIME = "TIME";
    public static final String DATETIME = "DATETIME";
    public static final String YES_NO = "YES_NO";
    public static final String BOOLEAN = "BOOLEAN";
    public static final String FILE_UPLOAD = "FILE_UPLOAD";

    public static final Set<String> ALL = Set.of(
            SHORT_TEXT,
            LONG_TEXT,
            TEXT,
            NUMBER,
            DECIMAL,
            EMAIL,
            PHONE,
            URL,
            SINGLE_CHOICE,
            MULTIPLE_CHOICE,
            MULTI_CHOICE,
            DROPDOWN,
            RATING,
            LIKERT,
            NPS,
            DATE,
            TIME,
            DATETIME,
            YES_NO,
            BOOLEAN,
            FILE_UPLOAD);

    private static final Map<String, String> ALIASES = Map.of(
            "MULTI_CHOICE", MULTIPLE_CHOICE,
            "TEXT", SHORT_TEXT);

    private QuestionTypes() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return ALIASES.getOrDefault(normalized, normalized);
    }

    public static boolean isSupported(String type) {
        return type != null && ALL.contains(type);
    }

    public static boolean isTextLike(String type) {
        return Set.of(SHORT_TEXT, LONG_TEXT, TEXT, EMAIL, PHONE, URL, SINGLE_CHOICE, DROPDOWN, TIME)
                .contains(type);
    }

    public static boolean isChoiceMulti(String type) {
        return MULTIPLE_CHOICE.equals(type) || MULTI_CHOICE.equals(type);
    }

    public static boolean isNumeric(String type) {
        return Set.of(NUMBER, DECIMAL, RATING, LIKERT, NPS).contains(type);
    }

    public static boolean isBooleanLike(String type) {
        return BOOLEAN.equals(type) || YES_NO.equals(type);
    }
}
