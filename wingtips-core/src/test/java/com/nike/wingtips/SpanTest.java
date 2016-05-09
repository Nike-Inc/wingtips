package com.nike.wingtips;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the functionality of {@link Span}
 */
public class SpanTest {

    String traceId = TraceAndSpanIdGenerator.generateId();
    String spanId = TraceAndSpanIdGenerator.generateId();
    String parentSpanId = TraceAndSpanIdGenerator.generateId();
    String spanName = "spanName-" + UUID.randomUUID().toString();
    String userId = "userId-" + UUID.randomUUID().toString();
    boolean sampleableForFullyCompleteSpan = false;
    long startTimeNanosForFullyCompleteSpan = 42;
    long endTimeNanosForFullyCompleteSpan = 4242;

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @return The string "null" if obj is null (in order to match how Span.toJson() functions), otherwise
     * String.valueOf(obj).
     */
    private String nullSafeStringValueOf(Object obj) {
        if (obj == null) {
            return "null";
        }

        return String.valueOf(obj);
    }

    private Span createFilledOutSpan(boolean completed) {
        Long endTime = (completed) ? endTimeNanosForFullyCompleteSpan : null;
        return new Span(traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId, startTimeNanosForFullyCompleteSpan, endTime);
    }

    private void verifySpanEqualsDeserializedValues(Span span, Map<String, String> deserializedValues) {
        assertThat(nullSafeStringValueOf(span.getSpanStartTimeNanos())).isEqualTo(deserializedValues.get(Span.START_TIME_NANOS_FIELD));
        assertThat(span.isCompleted()).isEqualTo(deserializedValues.containsKey(Span.END_TIME_NANOS_FIELD));
        assertThat(nullSafeStringValueOf(span.getSpanEndTimeNanos())).isEqualTo(nullSafeStringValueOf(deserializedValues.get(Span.END_TIME_NANOS_FIELD)));
        assertThat(nullSafeStringValueOf(span.getTraceId())).isEqualTo(deserializedValues.get(Span.TRACE_ID_FIELD));
        assertThat(nullSafeStringValueOf(span.getSpanId())).isEqualTo(deserializedValues.get(Span.SPAN_ID_FIELD));
        assertThat(nullSafeStringValueOf(span.getParentSpanId())).isEqualTo(deserializedValues.get(Span.PARENT_SPAN_ID_FIELD));
        assertThat(nullSafeStringValueOf(span.getSpanName())).isEqualTo(deserializedValues.get(Span.SPAN_NAME_FIELD));
        assertThat(nullSafeStringValueOf(span.isSampleable())).isEqualTo(deserializedValues.get(Span.SAMPLEABLE_FIELD));
        assertThat(nullSafeStringValueOf(span.getUserId())).isEqualTo(deserializedValues.get(Span.USER_ID_FIELD));
        assertThat(nullSafeStringValueOf(span.getTimeSpentNanos())).isEqualTo(nullSafeStringValueOf(deserializedValues.get(Span.TIME_SPENT_NANOS_FIELD)));
    }

    private void verifySpanDeepEquals(Span span1, Span span2) {
        assertThat(span1.getSpanStartTimeNanos()).isEqualTo(span2.getSpanStartTimeNanos());
        assertThat(span1.isCompleted()).isEqualTo(span2.isCompleted());
        assertThat(span1.getSpanEndTimeNanos()).isEqualTo(span2.getSpanEndTimeNanos());
        assertThat(span1.getTraceId()).isEqualTo(span2.getTraceId());
        assertThat(span1.getSpanId()).isEqualTo(span2.getSpanId());
        assertThat(span1.getParentSpanId()).isEqualTo(span2.getParentSpanId());
        assertThat(span1.getSpanName()).isEqualTo(span2.getSpanName());
        assertThat(span1.isSampleable()).isEqualTo(span2.isSampleable());
        assertThat(span1.getUserId()).isEqualTo(span2.getUserId());
        assertThat(span1.getTimeSpentNanos()).isEqualTo(span2.getTimeSpentNanos());
    }

    @Test
    public void public_constructor_works_as_expected_for_completed_span() {
        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId, startTimeNanosForFullyCompleteSpan, endTimeNanosForFullyCompleteSpan);

        // then
        assertThat(span.getTraceId()).isEqualTo(traceId);
        assertThat(span.getParentSpanId()).isEqualTo(parentSpanId);
        assertThat(span.getSpanId()).isEqualTo(spanId);
        assertThat(span.getSpanName()).isEqualTo(spanName);
        assertThat(span.isSampleable()).isEqualTo(sampleableForFullyCompleteSpan);
        assertThat(span.getUserId()).isEqualTo(userId);
        assertThat(span.getSpanStartTimeNanos()).isEqualTo(startTimeNanosForFullyCompleteSpan);
        assertThat(span.getSpanEndTimeNanos()).isEqualTo(endTimeNanosForFullyCompleteSpan);

        assertThat(span.isCompleted()).isTrue();
        assertThat(span.getTimeSpentNanos()).isEqualTo(endTimeNanosForFullyCompleteSpan - startTimeNanosForFullyCompleteSpan);
    }

    @Test
    public void public_constructor_works_as_expected_for_incomplete_span() {
        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId, startTimeNanosForFullyCompleteSpan, null);

        // then
        assertThat(span.getTraceId()).isEqualTo(traceId);
        assertThat(span.getParentSpanId()).isEqualTo(parentSpanId);
        assertThat(span.getSpanId()).isEqualTo(spanId);
        assertThat(span.getSpanName()).isEqualTo(spanName);
        assertThat(span.isSampleable()).isEqualTo(sampleableForFullyCompleteSpan);
        assertThat(span.getUserId()).isEqualTo(userId);
        assertThat(span.getSpanStartTimeNanos()).isEqualTo(startTimeNanosForFullyCompleteSpan);
        assertThat(span.getSpanEndTimeNanos()).isNull();

        assertThat(span.isCompleted()).isFalse();
        assertThat(span.getTimeSpentNanos()).isNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_passed_null_trace_id() {
        // expect
        new Span(null, parentSpanId, spanId, spanName, true, userId, 42, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_passed_null_span_id() {
        // expect
        new Span(traceId, parentSpanId, null, spanName, true, userId, 42, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_passed_null_span_name() {
        // expect
        new Span(traceId, parentSpanId, spanId, null, true, userId, 42, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_end_time_is_before_start_time() {
        // expect
        new Span(traceId, parentSpanId, spanId, spanName, true, userId, 42, 41L);
    }

    @Test
    public void protected_constructor_generates_instance_with_placeholder_values() {
        // given
        String placeholderValue = "PLACEHOLDER";

        // when
        Span result = new Span();

        // then
        verifySpanDeepEquals(result, new Span(placeholderValue, null, placeholderValue, placeholderValue, false, null, -1, null));
    }

    @Test
    public void generateRootSpanForNewTrace_generates_root_span_as_expected() {
        // given
        String spanName = UUID.randomUUID().toString();

        // when
        long beforeCallNanos = System.nanoTime();
        Span result = Span.generateRootSpanForNewTrace(spanName).build();
        long afterCallNanos = System.nanoTime();

        // then
        assertThat(result.getTraceId()).isNotEmpty();
        assertThat(result.getParentSpanId()).isNull();
        assertThat(result.getSpanId()).isNotEmpty();
        assertThat(result.getSpanName()).isEqualTo(spanName);
        assertThat(result.isSampleable()).isTrue();
        assertThat(result.getUserId()).isNull();
        assertThat(result.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(result.getSpanEndTimeNanos()).isNull();
        assertThat(result.isCompleted()).isFalse();
        assertThat(result.getTimeSpentNanos()).isNull();
    }

    @Test
    public void generateChildSpan_works_as_expected_for_incomplete_parent_span() {
        // given: span object with known values that is not completed
        Span parentSpan = createFilledOutSpan(false);
        String childSpanName = UUID.randomUUID().toString();
        assertThat(parentSpan.isCompleted()).isFalse();

        // when: generateChildSpan is used to create a child span with a new span name
        long beforeCallNanos = System.nanoTime();
        Span childSpan = parentSpan.generateChildSpan(childSpanName);
        long afterCallNanos = System.nanoTime();

        // then: returned object contains the expected values
        //       (new span ID, expected span name, parent span ID equal to parent's span ID, start time generated during call, not completed, everything else the same as parent).
        assertThat(childSpan.getSpanId()).isNotEmpty();
        assertThat(childSpan.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(childSpan.getSpanName()).isEqualTo(childSpanName);
        assertThat(childSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());

        assertThat(childSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(childSpan.getUserId()).isEqualTo(parentSpan.getUserId());
        assertThat(childSpan.isSampleable()).isEqualTo(parentSpan.isSampleable());

        assertThat(childSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(childSpan.isCompleted()).isFalse();
        assertThat(childSpan.getSpanEndTimeNanos()).isNull();
        assertThat(childSpan.getTimeSpentNanos()).isNull();
    }

    @Test
    public void generateChildSpan_works_as_expected_for_completed_parent_span() {
        // given: span with known values that is completed
        Span parentSpan = createFilledOutSpan(true);
        parentSpan.complete();
        String childSpanName = UUID.randomUUID().toString();
        assertThat(parentSpan.isCompleted()).isTrue();

        // when: generateChildSpan is used to create a child span with a new span name
        long beforeCallNanos = System.nanoTime();
        Span childSpan = parentSpan.generateChildSpan(childSpanName);
        long afterCallNanos = System.nanoTime();

        // then: returned object contains the expected values
        //       (new span ID, expected span name, parent span ID equal to parent's span ID, start time generated during call, not completed, everything else the same as parent).
        assertThat(childSpan.getSpanId()).isNotEmpty();
        assertThat(childSpan.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(childSpan.getSpanName()).isEqualTo(childSpanName);
        assertThat(childSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());

        assertThat(childSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(childSpan.getUserId()).isEqualTo(parentSpan.getUserId());
        assertThat(childSpan.isSampleable()).isEqualTo(parentSpan.isSampleable());

        assertThat(childSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(childSpan.isCompleted()).isFalse();
        assertThat(childSpan.getSpanEndTimeNanos()).isNull();
        assertThat(childSpan.getTimeSpentNanos()).isNull();
    }

    @Test
    public void complete_method_should_complete_the_span() {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        assertThat(validSpan.isCompleted()).isFalse();

        // when
        long beforeCompleteTime = System.nanoTime();
        validSpan.complete();
        long afterCompleteTime = System.nanoTime();

        // then
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getSpanEndTimeNanos()).isBetween(beforeCompleteTime, afterCompleteTime);
    }

    @Test
    public void toString_delegates_to_toJSON() {
        // given: span with all values filled in
        Span span = createFilledOutSpan(true);

        // when: toString is called on that span
        String toStringVal = span.toString();

        // then: it has the same value as toJSON()
        assertThat(toStringVal).isEqualTo(span.toJSON());
    }

    @Test
    public void toJson_should_function_properly_when_there_are_no_null_values() throws IOException {
        // given: valid span without any null values, span completed (so that end time is not null) and JSON string from Span.toJson()
        Span validSpan = createFilledOutSpan(true);
        assertThat(validSpan.getTraceId()).isNotEmpty();
        assertThat(validSpan.getUserId()).isNotEmpty();
        assertThat(validSpan.getParentSpanId()).isNotEmpty();
        assertThat(validSpan.getSpanName()).isNotEmpty();
        assertThat(validSpan.getSpanId()).isNotEmpty();
        assertThat(validSpan.getSpanEndTimeNanos()).isNotNull();
        assertThat(validSpan.getTimeSpentNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        String json = validSpan.toJSON();

        // when: jackson is used to deserialize that JSON
        Map<String, String> tcValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });

        // then: the original span and jackson's span values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, tcValuesFromJackson);
    }

    @Test
    public void toJson_should_function_properly_when_there_are_null_values() throws IOException {
        // given: valid span with null values and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        assertThat(validSpan.getSpanEndTimeNanos()).isNull();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        String json = validSpan.toJSON();

        // when: jackson is used to deserialize that JSON
        Map<String, String> tcValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});

        // then: the original span context and jackson's span context values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, tcValuesFromJackson);
    }

    @Test
    public void toJson_should_function_properly_for_non_completed_spans() throws IOException {
        // given: valid span and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        String json = validSpan.toJSON();
        assertThat(validSpan.isCompleted()).isFalse();

        // when: jackson is used to deserialize that JSON
        Map<String, String> tcValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});

        // then: the original span and jackson's span values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, tcValuesFromJackson);
    }

    @Test
    public void toJson_should_function_properly_for_completed_spans() throws IOException {
        // given: valid span and completed, and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        validSpan.complete();
        assertThat(validSpan.isCompleted()).isTrue();
        String json = validSpan.toJSON();

        // when: jackson is used to deserialize that JSON
        Map<String, String> tcValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});

        // then: the original span and jackson's span values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, tcValuesFromJackson);
    }

    @Test
    public void toJson_should_use_cached_json() {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        String uuidString = UUID.randomUUID().toString();
        Whitebox.setInternalState(validSpan, "cachedJsonRepresentation", uuidString);

        // when
        String toJsonResult = validSpan.toJSON();

        // then
        assertThat(toJsonResult).isEqualTo(uuidString);
    }

    @Test
    public void complete_should_reset_cached_json() throws IOException {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        String uuidString = UUID.randomUUID().toString();
        Whitebox.setInternalState(validSpan, "cachedJsonRepresentation", uuidString);

        // when
        String beforeCompleteJson = validSpan.toJSON();
        validSpan.complete();

        // then
        String afterCompleteJson = validSpan.toJSON();
        assertThat(afterCompleteJson).isNotEqualTo(beforeCompleteJson);
        assertThat(afterCompleteJson).isNotEqualTo(uuidString);
        Map<String, String> tcValuesFromJackson = objectMapper.readValue(afterCompleteJson, new TypeReference<Map<String, String>>() { });
        verifySpanEqualsDeserializedValues(validSpan, tcValuesFromJackson);
    }

    @Test
    public void fromJson_should_function_properly_when_there_are_no_null_values() {
        // given: valid span without any null values, completed (so that end time is not null) and JSON string from Span.toJson()
        Span validSpan = createFilledOutSpan(true);
        assertThat(validSpan).isNotNull();
        assertThat(validSpan.getTraceId()).isNotNull();
        assertThat(validSpan.getUserId()).isNotNull();
        assertThat(validSpan.getParentSpanId()).isNotNull();
        assertThat(validSpan.getSpanName()).isNotNull();
        assertThat(validSpan.getSpanId()).isNotNull();
        assertThat(validSpan.getSpanEndTimeNanos()).isNotNull();
        assertThat(validSpan.getTimeSpentNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        String json = validSpan.toJSON();

        // when: fromJson is called
        Span spanFromJson = Span.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson);
    }

    @Test
    public void fromJson_should_function_properly_when_there_are_null_values() throws IOException {
        // given: valid span with null values and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        assertThat(validSpan.getSpanEndTimeNanos()).isNull();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        String json = validSpan.toJSON();

        // when: fromJson is called
        Span spanFromJson = Span.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson);
    }

    @Test
    public void fromJson_should_function_properly_for_non_completed_spans() throws IOException {
        // given: valid, non-completed span and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        String json = validSpan.toJSON();
        assertThat(validSpan.isCompleted()).isFalse();

        // when: fromJson is called
        Span spanFromJson = Span.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson);
    }

        @Test
    public void fromJson_should_function_properly_for_completed_spans() throws IOException {
        // given: valid span that has been completed, and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        validSpan.complete();
        assertThat(validSpan.isCompleted()).isTrue();
        String json = validSpan.toJSON();

        // when: fromJson is called
        Span tcFromJson = Span.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, tcFromJson);
    }

    @Test
    public void fromJson_should_return_null_for_garbage_input() throws IOException {
        // given: garbage input
        String garbageInput = "garbagio";

        // when: fromJson is called
        Span tcFromJson = Span.fromJSON(garbageInput);

        // then: the return value should be null
        assertThat(tcFromJson).isNull();
    }

    @Test
    public void fromJson_returns_null_if_sampleable_field_is_missing() throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validJson = validSpan.toJSON();
        String invalidJson = validJson.replace(String.format(",\"%s\":\"%s\"", Span.SAMPLEABLE_FIELD, String.valueOf(validSpan.isSampleable())), "");

        // when
        Span result = Span.fromJSON(invalidJson);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void fromJson_returns_null_if_startTimeNanos_field_is_missing() throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validJson = validSpan.toJSON();
        String invalidJson = validJson.replace(String.format(",\"%s\":\"%s\"", Span.START_TIME_NANOS_FIELD, String.valueOf(validSpan.getSpanStartTimeNanos())), "");

        // when
        Span result = Span.fromJSON(invalidJson);

        // then
        assertThat(result).isNull();
    }

    private Map<String, String> deserializeKeyValueSpanString(String keyValStr) {
        Map<String, String> map = new HashMap<>();
        String[] fields = keyValStr.split(",");
        for (String field : fields) {
            String[] info = field.split("=");
            map.put(info[0], info[1]);
        }
        return map;
    }

    @Test
    public void toKeyValueString_should_function_properly_when_there_are_no_null_values() throws IOException {
        // given: valid known span without any null values, span completed (so that end time is not null) and key/value string from Span.toKeyValueString()
        Span validSpan = createFilledOutSpan(true);
        assertThat(validSpan.getTraceId()).isNotEmpty();
        assertThat(validSpan.getUserId()).isNotEmpty();
        assertThat(validSpan.getParentSpanId()).isNotEmpty();
        assertThat(validSpan.getSpanName()).isNotEmpty();
        assertThat(validSpan.getSpanId()).isNotEmpty();
        assertThat(validSpan.getSpanEndTimeNanos()).isNotNull();
        assertThat(validSpan.getTimeSpentNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        String keyValueStr = validSpan.toKeyValueString();

        // when: the string is deserialized into a map
        Map<String, String> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void toKeyValueString_should_function_properly_when_there_are_null_values() throws IOException {
        // given: valid span with null values and key/value string from Span.toKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        assertThat(validSpan.getSpanEndTimeNanos()).isNull();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        String keyValueStr = validSpan.toKeyValueString();

        // when: the string is deserialized into a map
        Map<String, String> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void toKeyValueString_should_function_properly_for_non_completed_spans() throws IOException {
        // given: valid span and key/value string from Span.toKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        String keyValueStr = validSpan.toKeyValueString();
        assertThat(validSpan.isCompleted()).isFalse();

        // when: the string is deserialized into a map
        Map<String, String> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void toKeyValueString_should_function_properly_for_completed_spans() throws IOException {
        // given: valid span and completed, and key/value string from Span.toKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        validSpan.complete();
        assertThat(validSpan.isCompleted()).isTrue();
        String keyValueStr = validSpan.toKeyValueString();

        // when: the string is deserialized into a map
        Map<String, String> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void toKeyValueString_should_use_cached_key_value_string() {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        String uuidString = UUID.randomUUID().toString();
        Whitebox.setInternalState(validSpan, "cachedKeyValueRepresentation", uuidString);

        // when
        String toKeyValueStringResult = validSpan.toKeyValueString();

        // then
        assertThat(toKeyValueStringResult).isEqualTo(uuidString);
    }

    @Test
    public void complete_should_reset_cached_key_value_string() throws IOException {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        String uuidString = UUID.randomUUID().toString();
        Whitebox.setInternalState(validSpan, "cachedKeyValueRepresentation", uuidString);

        // when
        String beforeCompleteKeyValueString = validSpan.toKeyValueString();
        validSpan.complete();

        // then
        String afterCompleteKeyValueString = validSpan.toKeyValueString();
        assertThat(afterCompleteKeyValueString).isNotEqualTo(beforeCompleteKeyValueString);
        assertThat(afterCompleteKeyValueString).isNotEqualTo(uuidString);
        Map<String, String> deserializedValues = deserializeKeyValueSpanString(afterCompleteKeyValueString);
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void fromKeyValueString_should_function_properly_when_there_are_no_null_values() {
        // given: valid span without any null values, completed (so that end time is not null) and key/value string from Span.fromKeyValueString()
        Span validSpan = createFilledOutSpan(true);
        assertThat(validSpan).isNotNull();
        assertThat(validSpan.getTraceId()).isNotNull();
        assertThat(validSpan.getUserId()).isNotNull();
        assertThat(validSpan.getParentSpanId()).isNotNull();
        assertThat(validSpan.getSpanName()).isNotNull();
        assertThat(validSpan.getSpanId()).isNotNull();
        assertThat(validSpan.getSpanEndTimeNanos()).isNotNull();
        assertThat(validSpan.getTimeSpentNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        String keyValStr = validSpan.toKeyValueString();

        // when: toKeyValueString is called
        Span spanFromKeyValStr = Span.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr);
    }

    @Test
    public void fromKeyValueString_should_function_properly_when_there_are_null_values() throws IOException {
        // given: valid span with null values and key/value string from Span.fromKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        assertThat(validSpan.getSpanEndTimeNanos()).isNull();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        String keyValStr = validSpan.toKeyValueString();

        // when: toKeyValueString is called
        Span spanFromKeyValStr = Span.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr);
    }

    @Test
    public void fromKeyValueString_should_function_properly_for_non_completed_spans() throws IOException {
        // given: valid, non-completed span and key/value string from Span.fromKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        String keyValStr = validSpan.toKeyValueString();
        assertThat(validSpan.isCompleted()).isFalse();

        // when: toKeyValueString is called
        Span spanFromKeyValStr = Span.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr);
    }

    @Test
    public void fromKeyValueString_should_function_properly_for_completed_spans() throws IOException {
        // given: valid span that has been completed, and key/value string from Span.fromKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        validSpan.complete();
        assertThat(validSpan.isCompleted()).isTrue();
        String keyValStr = validSpan.toKeyValueString();

        // when: toKeyValueString is called
        Span spanFromKeyValStr = Span.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr);
    }

    @Test
    public void fromKeyValueString_should_return_null_for_garbage_input() throws IOException {
        // given: garbage input
        String garbageInput = "garbagio";

        // when: fromKeyValueString is called
        Span tcFromKeyValStr = Span.fromKeyValueString(garbageInput);

        // then: the return value should be null
        assertThat(tcFromKeyValStr).isNull();
    }

    @Test
    public void fromKeyValueString_returns_null_if_sampleable_field_is_missing() throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validKeyValStr = validSpan.toKeyValueString();
        String invalidKeyValStr = validKeyValStr.replace(String.format(",%s=%s", Span.SAMPLEABLE_FIELD, String.valueOf(validSpan.isSampleable())), "");

        // when
        Span result = Span.fromKeyValueString(invalidKeyValStr);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void fromKeyValueString_returns_null_if_startTimeNanos_field_is_missing() throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validKeyValStr = validSpan.toKeyValueString();
        String invalidKeyValStr = validKeyValStr.replace(String.format(",%s=%s", Span.START_TIME_NANOS_FIELD, String.valueOf(validSpan.getSpanStartTimeNanos())), "");

        // when
        Span result = Span.fromKeyValueString(invalidKeyValStr);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getTimeSpentNanos_should_be_null_until_span_is_completed() {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName).build();
        assertThat(validSpan.getTimeSpentNanos()).isNull();

        // when
        validSpan.complete();

        // then
        assertThat(validSpan.getTimeSpentNanos()).isNotNull();
        Long startTimeNanos = (Long) Whitebox.getInternalState(validSpan, "spanStartTimeNanos");
        assertThat(validSpan.getTimeSpentNanos()).isEqualTo(validSpan.getSpanEndTimeNanos() - startTimeNanos);
    }

    @Test
    public void equals_returns_true_and_hashCode_same_if_same_instance() {
        // given
        Span fullSpan = createFilledOutSpan(true);

        // expect
        //noinspection EqualsWithItself
        assertThat(fullSpan.equals(fullSpan)).isTrue();
        assertThat(fullSpan.hashCode()).isEqualTo(fullSpan.hashCode());
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_other_is_not_a_Span() {
        // given
        Span span = createFilledOutSpan(true);
        String notASpan = "notASpan";

        // expect
        //noinspection EqualsBetweenInconvertibleTypes
        assertThat(span.equals(notASpan)).isFalse();
    }

    @Test
    public void equals_returns_true_and_hashCode_same_if_all_fields_are_equal() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);

        // expect
        assertThat(fullSpan1.equals(fullSpan2)).isTrue();
        assertThat(fullSpan1.hashCode()).isEqualTo(fullSpan2.hashCode());
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_spanId_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        Whitebox.setInternalState(fullSpan2, "spanId", fullSpan1.getSpanId() + "_nope");

        // expect
        assertThat(fullSpan1.equals(fullSpan2)).isFalse();
        assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_sampleable_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        Whitebox.setInternalState(fullSpan2, "sampleable", !fullSpan1.isSampleable());

        // expect
        assertThat(fullSpan1.equals(fullSpan2)).isFalse();
        assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_spanStartTimeNanos_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        Whitebox.setInternalState(fullSpan2, "spanStartTimeNanos", fullSpan1.getSpanStartTimeNanos() + 1);

        // expect
        assertThat(fullSpan1.equals(fullSpan2)).isFalse();
        assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_traceId_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        Whitebox.setInternalState(fullSpan2, "traceId", fullSpan1.getTraceId() + "_nope");

        // expect
        assertThat(fullSpan1.equals(fullSpan2)).isFalse();
        assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_parentSpanId_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        List<String> badDataList = Arrays.asList(fullSpan1.getParentSpanId() + "_nope", null);

        for (String badData : badDataList) {
            Whitebox.setInternalState(fullSpan2, "parentSpanId", badData);

            // expect
            assertThat(fullSpan1.equals(fullSpan2)).isFalse();
            assertThat(fullSpan2.equals(fullSpan1)).isFalse();
            assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
        }
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_spanName_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        Whitebox.setInternalState(fullSpan2, "spanName", fullSpan1.getSpanName() + "_nope");

        // expect
        assertThat(fullSpan1.equals(fullSpan2)).isFalse();
        assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_userId_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        List<String> badDataList = Arrays.asList(fullSpan1.getUserId() + "_nope", null);

        for (String badData : badDataList) {
            Whitebox.setInternalState(fullSpan2, "userId", badData);

            // expect
            assertThat(fullSpan1.equals(fullSpan2)).isFalse();
            assertThat(fullSpan2.equals(fullSpan1)).isFalse();
            assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
        }
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_spanEndTimeNanos_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        List<Long> badDataList = Arrays.asList(fullSpan1.getSpanEndTimeNanos() + 1, null);

        for (Long badData : badDataList) {
            Whitebox.setInternalState(fullSpan2, "spanEndTimeNanos", badData);

            // expect
            assertThat(fullSpan1.equals(fullSpan2)).isFalse();
            assertThat(fullSpan2.equals(fullSpan1)).isFalse();
            assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
        }
    }

    @Test
    public void newBuilder_with_spanName_arg_returns_root_span_builder_by_default() {
        // given
        String spanName = UUID.randomUUID().toString();

        // when
        long beforeCallNanos = System.nanoTime();
        Span result = Span.newBuilder(spanName).build();
        long afterCallNanos = System.nanoTime();

        // then
        assertThat(result.getTraceId()).isNotEmpty();
        assertThat(result.getParentSpanId()).isNull();
        assertThat(result.getSpanId()).isNotEmpty();
        assertThat(result.getSpanName()).isEqualTo(spanName);
        assertThat(result.isSampleable()).isTrue();
        assertThat(result.getUserId()).isNull();
        assertThat(result.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(result.getSpanEndTimeNanos()).isNull();
        assertThat(result.isCompleted()).isFalse();
    }

    @Test
    public void newBuilder_with_copy_arg_returns_exact_copy() {
        // given
        Span origSpan = createFilledOutSpan(true);

        // when
        Span copySpan = Span.newBuilder(origSpan).build();

        // then
        verifySpanDeepEquals(origSpan, copySpan);
    }

    @Test
    public void newBuilder_honors_values_for_all_fields_if_set() {
        // given
        Span.Builder builder = Span
                .newBuilder("override_me")
                .withTraceId(traceId)
                .withSpanId(spanId)
                .withParentSpanId(parentSpanId)
                .withSpanName(spanName)
                .withSampleable(sampleableForFullyCompleteSpan)
                .withUserId(userId)
                .withSpanStartTimeNanos(startTimeNanosForFullyCompleteSpan)
                .withSpanEndTimeNanos(endTimeNanosForFullyCompleteSpan);

        // when
        Span span = builder.build();

        // then
        assertThat(span.getTraceId()).isEqualTo(traceId);
        assertThat(span.getSpanId()).isEqualTo(spanId);
        assertThat(span.getParentSpanId()).isEqualTo(parentSpanId);
        assertThat(span.getSpanName()).isEqualTo(spanName);
        assertThat(span.isSampleable()).isEqualTo(sampleableForFullyCompleteSpan);
        assertThat(span.getUserId()).isEqualTo(userId);
        assertThat(span.getSpanStartTimeNanos()).isEqualTo(startTimeNanosForFullyCompleteSpan);
        assertThat(span.getSpanEndTimeNanos()).isEqualTo(endTimeNanosForFullyCompleteSpan);
    }
}

