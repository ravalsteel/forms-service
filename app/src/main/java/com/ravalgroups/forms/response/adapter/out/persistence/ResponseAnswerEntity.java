package com.ravalgroups.forms.response.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "response_answer")
public class ResponseAnswerEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private ResponseEntity response;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "question_key", nullable = false, length = 128)
    private String questionKey;

    @Column(name = "value_type", nullable = false, length = 32)
    private String valueType;

    @Column(name = "text_value")
    private String textValue;

    @Column(name = "number_value")
    private Double numberValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "date_value")
    private LocalDate dateValue;

    @Column(name = "datetime_value")
    private Instant datetimeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "json_value", columnDefinition = "jsonb")
    private String jsonValue;

    @Column(name = "object_id")
    private UUID objectId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ResponseAnswerEntity() {}

    public static ResponseAnswerEntity create(
            UUID id,
            UUID questionId,
            String questionKey,
            String valueType,
            String textValue,
            Double numberValue,
            Boolean booleanValue,
            LocalDate dateValue,
            Instant datetimeValue,
            String jsonValue,
            UUID objectId,
            Instant now) {
        ResponseAnswerEntity e = new ResponseAnswerEntity();
        e.id = id;
        e.questionId = questionId;
        e.questionKey = questionKey;
        e.valueType = valueType;
        e.textValue = textValue;
        e.numberValue = numberValue;
        e.booleanValue = booleanValue;
        e.dateValue = dateValue;
        e.datetimeValue = datetimeValue;
        e.jsonValue = jsonValue;
        e.objectId = objectId;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    void setResponse(ResponseEntity response) {
        this.response = response;
    }

    /** Scrubs text/file values that may contain PII; keeps numeric/boolean aggregates. */
    public void scrubPii(Instant now) {
        if ("TEXT".equals(valueType) || "JSON".equals(valueType) || "FILE".equals(valueType)
                || "DATE".equals(valueType) || "DATETIME".equals(valueType)) {
            this.textValue = null;
            this.jsonValue = null;
            this.dateValue = null;
            this.datetimeValue = null;
            this.objectId = null;
        }
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public ResponseEntity getResponse() { return response; }
    public UUID getQuestionId() { return questionId; }
    public String getQuestionKey() { return questionKey; }
    public String getValueType() { return valueType; }
    public String getTextValue() { return textValue; }
    public Double getNumberValue() { return numberValue; }
    public Boolean getBooleanValue() { return booleanValue; }
    public LocalDate getDateValue() { return dateValue; }
    public Instant getDatetimeValue() { return datetimeValue; }
    public String getJsonValue() { return jsonValue; }
    public UUID getObjectId() { return objectId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
