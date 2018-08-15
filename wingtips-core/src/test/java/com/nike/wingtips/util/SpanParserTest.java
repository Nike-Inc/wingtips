package com.nike.wingtips.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.assertj.core.data.Offset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TestSpanCompleter;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import com.nike.wingtips.Tracer;

@RunWith(DataProviderRunner.class)
public class SpanParserTest {

	private String traceId = TraceAndSpanIdGenerator.generateId();
    private String spanId = TraceAndSpanIdGenerator.generateId();
    private String parentSpanId = TraceAndSpanIdGenerator.generateId();
    private String spanName = "spanName-" + UUID.randomUUID().toString();
    private String userId = "userId-" + UUID.randomUUID().toString();
    private SpanPurpose spanPurpose = SpanPurpose.SERVER;
    private boolean sampleableForFullyCompleteSpan = false;
    private long startTimeEpochMicrosForFullyCompleteSpan = 42;
    private long startTimeNanosForFullyCompleteSpan = calculateNanoStartTimeFromSpecifiedEpochMicrosStartTime(
        startTimeEpochMicrosForFullyCompleteSpan,
        TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()),
        System.nanoTime());
    private long durationNanosForFullyCompletedSpan = 424242;
    private SpanPurpose spanPurposeForFullyCompletedSpan = SpanPurpose.SERVER;

    private ObjectMapper objectMapper = new ObjectMapper();

    private enum TAG_SET {
    		NULL {
    			public Map<String,String> getTags() {
    				return null;
    			}
    		},
    		SINGLE_VALUE {
    			public Map<String,String> getTags() {
    				return Collections.singletonMap("Key", "Value");
    			}
    		},
    		MULTIPLE_VALUES {
    			public Map<String,String> getTags() {
    				Map<String, String> tags = new HashMap<String,String>();
    				tags.put("key", "value");
    				tags.put("color", "blue");
    				tags.put("day", "today");
    				return tags;
    			}
    		},
    		SPECIAL_CHARS {
    			public Map<String,String> getTags() {
    				Map<String, String> tags = new HashMap<String,String>();
    				tags.put("key+s", "value");
    				tags.put("c0!0$%^&", "$%^&(*&^&*<>?");
    				return tags;
    			}
    		};
    		
    		public abstract Map<String,String> getTags();
    }
    
    private Span createFilledOutSpan(boolean completed) {
    		return createFilledOutSpan(completed, TAG_SET.SINGLE_VALUE.getTags());
    }
    
    private Span createFilledOutSpan(boolean completed, Map<String, String> tags) {
        Long durationNanos = (completed) ? durationNanosForFullyCompletedSpan : null;
        return new Span(traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId,
                        spanPurposeForFullyCompletedSpan, startTimeEpochMicrosForFullyCompleteSpan, startTimeNanosForFullyCompleteSpan, durationNanos, tags);
    }
    
    private void verifySpanDeepEquals(Span span1, Span span2, boolean allowStartTimeNanosFudgeFactor) {
        assertThat(span1.getSpanStartTimeEpochMicros()).isEqualTo(span2.getSpanStartTimeEpochMicros());
        if (allowStartTimeNanosFudgeFactor)
            assertThat(span1.getSpanStartTimeNanos()).isCloseTo(span2.getSpanStartTimeNanos(), Offset.offset(TimeUnit.MILLISECONDS.toNanos(1)));
        else
            assertThat(span1.getSpanStartTimeNanos()).isEqualTo(span2.getSpanStartTimeNanos());
        assertThat(span1.isCompleted()).isEqualTo(span2.isCompleted());
        assertThat(span1.getTraceId()).isEqualTo(span2.getTraceId());
        assertThat(span1.getSpanId()).isEqualTo(span2.getSpanId());
        assertThat(span1.getParentSpanId()).isEqualTo(span2.getParentSpanId());
        assertThat(span1.getSpanName()).isEqualTo(span2.getSpanName());
        assertThat(span1.isSampleable()).isEqualTo(span2.isSampleable());
        assertThat(span1.getUserId()).isEqualTo(span2.getUserId());
        assertThat(span1.getDurationNanos()).isEqualTo(span2.getDurationNanos());
        assertThat(span1.getSpanPurpose()).isEqualTo(span2.getSpanPurpose());
        assertThat(span1.getTags()).isEqualTo(span2.getTags());
    }
    
    private long calculateNanoStartTimeFromSpecifiedEpochMicrosStartTime(long epochMicrosStartTime, long currentEpochMicros, long currentNanoTime) {
        long currentDurationMicros = currentEpochMicros - epochMicrosStartTime;
        long nanoStartTimeOffset = TimeUnit.MICROSECONDS.toNanos(currentDurationMicros);
        return currentNanoTime - nanoStartTimeOffset;
    }
    
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
    
	private void verifySpanEqualsDeserializedValues(Span span, Map<String, String> deserializedValues) {
        assertThat(nullSafeStringValueOf(span.getSpanStartTimeEpochMicros())).isEqualTo(deserializedValues.get(SpanParser.START_TIME_EPOCH_MICROS_FIELD));
        assertThat(span.isCompleted()).isEqualTo(deserializedValues.containsKey(SpanParser.DURATION_NANOS_FIELD));
        assertThat(nullSafeStringValueOf(span.getTraceId())).isEqualTo(deserializedValues.get(SpanParser.TRACE_ID_FIELD));
        assertThat(nullSafeStringValueOf(span.getSpanId())).isEqualTo(deserializedValues.get(SpanParser.SPAN_ID_FIELD));
        assertThat(nullSafeStringValueOf(span.getParentSpanId())).isEqualTo(deserializedValues.get(SpanParser.PARENT_SPAN_ID_FIELD));
        assertThat(nullSafeStringValueOf(span.getSpanName())).isEqualTo(deserializedValues.get(SpanParser.SPAN_NAME_FIELD));
        assertThat(nullSafeStringValueOf(span.isSampleable())).isEqualTo(deserializedValues.get(SpanParser.SAMPLEABLE_FIELD));
        assertThat(nullSafeStringValueOf(span.getUserId())).isEqualTo(deserializedValues.get(SpanParser.USER_ID_FIELD));
        assertThat(nullSafeStringValueOf(span.getDurationNanos())).isEqualTo(nullSafeStringValueOf(deserializedValues.get(SpanParser.DURATION_NANOS_FIELD)));
        assertThat(nullSafeStringValueOf(span.getSpanPurpose())).isEqualTo(nullSafeStringValueOf(deserializedValues.get(SpanParser.SPAN_PURPOSE_FIELD)));
    }
	
	private void verifySpanTagsEqualDeserializedValues(Map<String,String> tags, Map<String,String> deserializedTags) {
		assertThat(twoMapsAreEqual(tags, deserializedTags));
	}
	
	protected boolean twoMapsAreEqual(Map<String, String> one, Map<String,String> two) {
		if (one.size() != two.size()) 
			return false;
		return verifyMapContainsIdenticalEntries(one,two) &&
			   verifyMapContainsIdenticalEntries(two,one);
	}
	
	protected boolean verifyMapContainsIdenticalEntries(Map<String,String> mapOne, Map<String,String> mapTwo) {
		for(Map.Entry<String, String> entry : mapTwo.entrySet()) {
			if (!mapOne.containsKey(entry.getKey())) 
				return false;
			String mapOneValue = mapOne.get(entry.getKey());
			String mapTwoValue = entry.getValue();
			if (! mapOneValue.equals(mapTwoValue))
				return false;
		}
		return true;
	}
	
	protected void completeSpan(Span span) {
		TestSpanCompleter.completeSpan(span);
	}
	
	/** @todo Create a TypeReference that knows how to parse this JSON **/
	@DataProvider( value = {
        "NULL",
        "SINGLE_VALUE",
        "MULTIPLE_VALUES",
        "SPECIAL_CHARS"
    })
	@Test
    public void toJson_should_function_properly_when_there_are_no_null_values(TAG_SET tags) throws IOException {
        // given: valid span without any null values, span completed (so that end time is not null) and JSON string from Span.toJson()
        Span validSpan = createFilledOutSpan(true, tags.getTags());
        assertThat(validSpan.getTraceId()).isNotEmpty();
        assertThat(validSpan.getUserId()).isNotEmpty();
        assertThat(validSpan.getParentSpanId()).isNotEmpty();
        assertThat(validSpan.getSpanName()).isNotEmpty();
        assertThat(validSpan.getSpanId()).isNotEmpty();
        assertThat(validSpan.getDurationNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getSpanPurpose()).isNotNull();
        String json = validSpan.toJSON();
        
        if(tags.getTags() == null || tags.getTags().isEmpty()) {
        		Map<String, String> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        		verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
        } else {
        		//Parse the tags separately from the rest of the json
        		String jsonWithoutNestedTags = SpanParser.removeTagsFromJson(json);
        		String tagJson = SpanParser.parseNestedTagJson(json);
      
        		// when: jackson is used to deserialize that JSON
        		Map<String, String> spanValuesFromJackson = objectMapper.readValue(jsonWithoutNestedTags, new TypeReference<Map<String, String>>() {});
        		Map<String, String> tagsFromJackson = objectMapper.readValue(tagJson, new TypeReference<Map<String, String>>() {});
      
        		// then: the original span context and jackson's span context values should be exactly the same
        		verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
        		verifySpanTagsEqualDeserializedValues(validSpan.getTags(), tagsFromJackson);
        }
    }

    @Test
    public void toJson_should_function_properly_when_there_are_null_values() throws IOException {
        // given: valid span with null values and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        String json = validSpan.toJSON();

        // when: jackson is used to deserialize that JSON
        Map<String, String> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});

        // then: the original span context and jackson's span context values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
    }

    @Test
    public void toJson_should_function_properly_for_non_completed_spans() throws IOException {
        // given: valid span and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String json = validSpan.toJSON();
        assertThat(validSpan.isCompleted()).isFalse();
        assertThat(validSpan.getDurationNanos()).isNull();

        // when: jackson is used to deserialize that JSON
        Map<String, String> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});

        // then: the original span and jackson's span values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
    }

    @Test
    public void toJson_should_function_properly_for_completed_spans() throws IOException {
        // given: valid span and completed, and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        completeSpan(validSpan);
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getDurationNanos()).isNotNull();
        String json = validSpan.toJSON();

        // when: jackson is used to deserialize that JSON
        Map<String, String> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});

        // then: the original span and jackson's span values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
    }

    @Test
    public void toJson_should_use_cached_json() {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String uuidString = UUID.randomUUID().toString();
        Whitebox.setInternalState(validSpan, "cachedJsonRepresentation", uuidString);

        // when
        String toJsonResult = validSpan.toJSON();

        // then
        assertThat(toJsonResult).isEqualTo(uuidString);
    }
    
//    @Test
//    public void toJson_should_function_properly_with_no_tags() throws IOException {
//        // given: valid span with null values and JSON string from Span.toJson()
//        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
//        assertThat(validSpan.getTags()).isEmpty();
//        
//        String json = validSpan.toJSON();
//
//        // when: jackson is used to deserialize that JSON
//        Map<String, String> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
//
//        // then: the original span context and jackson's span context values should be exactly the same
//        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
//    }

//    @Test
//    public void toJson_should_function_properly_with_one_tag() throws IOException {
//        // given: valid span with null values and JSON string from Span.toJson()
//        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
//        validSpan.addTag("Key", "value");
//        assertThat(validSpan.getTags()).isNotEmpty();
//        
//        String json = validSpan.toJSON();
//
//        // when: jackson is used to deserialize that JSON
//        Map<String, String> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
//
//        // then: the original span context and jackson's span context values should be exactly the same
//        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
//    }
//    
//    @Test
//    public void toJson_should_function_properly_with_multiple_tags() throws IOException {
//        // given: valid span with null values and JSON string from Span.toJson()
//        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
//        validSpan.addTag("Key", "value");
//        validSpan.addTag("Key2", "value2");
//        assertThat(validSpan.getTags()).isNotEmpty();
//        
//        String json = validSpan.toJSON();
//
//        // when: jackson is used to deserialize that JSON
//        Map<String, String> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
//
//        // then: the original span context and jackson's span context values should be exactly the same
//        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
//    }
//    
//    @Test
//    public void toJson_should_function_properly_with_special_chars_in_tag() throws IOException {
//        // given: valid span with null values and JSON string from Span.toJson()
//        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
//        validSpan.addTag("Key-a-1!|/!@>#$%%^&*()", "v@l|>u$|");
//        
//        assertThat(validSpan.getTags()).isNotEmpty();
//        
//        String json = validSpan.toJSON();
//
//        String jsonWithoutNestedTags = SpanParser.removeNestedTagsJson(json);
//        String tagJson = SpanParser.parseNestedTagJson(json);
//        
//        // when: jackson is used to deserialize that JSON
//        Map<String, String> spanValuesFromJackson = objectMapper.readValue(jsonWithoutNestedTags, new TypeReference<Map<String, String>>() {});
//        Map<String, String> tagsFromJackson = objectMapper.readValue(tagJson, new TypeReference<Map<String, String>>() {});
//        
//        // then: the original span context and jackson's span context values should be exactly the same
//        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
//        verifySpanTagsEqualDeserializedValues(validSpan.getTags(), tagsFromJackson);
//    }
    
    @Test
    public void complete_should_reset_cached_json() throws IOException {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String uuidString = UUID.randomUUID().toString();
        Whitebox.setInternalState(validSpan, "cachedJsonRepresentation", uuidString);

        // when
        String beforeCompleteJson = validSpan.toJSON();
        completeSpan(validSpan);

        // then
        String afterCompleteJson = validSpan.toJSON();
        assertThat(afterCompleteJson).isNotEqualTo(beforeCompleteJson);
        assertThat(afterCompleteJson).isNotEqualTo(uuidString);
        Map<String, String> spanValuesFromJackson = objectMapper.readValue(afterCompleteJson, new TypeReference<Map<String, String>>() { });
        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
    }

    @DataProvider( value = {
            "NULL",
            "SINGLE_VALUE",
            "MULTIPLE_VALUES",
            "SPECIAL_CHARS"
        })
    @Test
    public void fromJson_should_function_properly_when_there_are_no_null_values(TAG_SET tags) {
        // given: valid span without any null values, completed (so that end time is not null) and JSON string from Span.toJson()
        Span validSpan = createFilledOutSpan(true, tags.getTags());
        assertThat(validSpan).isNotNull();
        assertThat(validSpan.getTraceId()).isNotNull();
        assertThat(validSpan.getUserId()).isNotNull();
        assertThat(validSpan.getParentSpanId()).isNotNull();
        assertThat(validSpan.getSpanName()).isNotNull();
        assertThat(validSpan.getSpanId()).isNotNull();
        assertThat(validSpan.getDurationNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getSpanPurpose()).isNotNull();
        String json = validSpan.toJSON();

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson, true);
    }

    @Test
    public void fromJson_should_function_properly_when_there_are_null_values() throws IOException {
        // given: valid span with null values and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        assertThat(validSpan.getDurationNanos()).isNull();
        String json = validSpan.toJSON();

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson, true);
    }

    @Test
    public void fromJson_should_function_properly_for_non_completed_spans() throws IOException {
        // given: valid, non-completed span and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String json = validSpan.toJSON();
        assertThat(validSpan.isCompleted()).isFalse();

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson, true);
    }

        @Test
    public void fromJson_should_function_properly_for_completed_spans() throws IOException {
        // given: valid span that has been completed, and JSON string from Span.toJson()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        completeSpan(validSpan);
        assertThat(validSpan.isCompleted()).isTrue();
        String json = validSpan.toJSON();

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson, true);
    }

    @Test
    public void fromJson_should_return_null_for_garbage_input() throws IOException {
        // given: garbage input
        String garbageInput = "garbagio";

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(garbageInput);

        // then: the return value should be null
        assertThat(spanFromJson).isNull();
    }

    @Test
    public void fromJson_returns_null_if_sampleable_field_is_missing() throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validJson = validSpan.toJSON();
        String invalidJson = validJson.replace(String.format(",\"%s\":\"%s\"", SpanParser.SAMPLEABLE_FIELD, String.valueOf(validSpan.isSampleable())), "");

        // when
        Span result = SpanParser.fromJSON(invalidJson);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void fromJson_returns_null_if_startTimeEpochMicros_field_is_missing() throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validJson = validSpan.toJSON();
        String invalidJson = validJson.replace(
            String.format(",\"%s\":\"%s\"", SpanParser.START_TIME_EPOCH_MICROS_FIELD, String.valueOf(validSpan.getSpanStartTimeEpochMicros())),
            ""
        );

        // when
        Span result = SpanParser.fromJSON(invalidJson);

        // then
        assertThat(result).isNull();
    }

    @DataProvider(value = {
        "",
        "foobar-not-a-real-enum-value"
    }, splitBy = "\\|")
    @Test
    public void fromJson_returns_span_with_UNKNOWN_span_purpose_if_spanPurpose_field_is_missing_or_garbage(String badValue) throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validJson = validSpan.toJSON();
        if (badValue.trim().length() > 0) {
            badValue = ",\"spanPurpose\":\"" + badValue + "\"";
        }
        String invalidJson = validJson.replace(
            String.format(",\"%s\":\"%s\"", SpanParser.SPAN_PURPOSE_FIELD, validSpan.getSpanPurpose().name()),
            badValue
        );
        assertThat(validSpan.getSpanPurpose()).isNotEqualTo(SpanPurpose.UNKNOWN);

        // when
        Span result = SpanParser.fromJSON(invalidJson);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSpanPurpose()).isEqualTo(SpanPurpose.UNKNOWN);
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

    @DataProvider( value = {
            "NULL",
            "SINGLE_VALUE",
            "MULTIPLE_VALUES",
            "SPECIAL_CHARS"
        })
    @Test
    public void toKeyValueString_should_function_properly_when_there_are_no_null_values(TAG_SET tags) throws IOException {
        // given: valid known span without any null values, span completed (so that end time is not null) and key/value string from Span.toKeyValueString()
        Span validSpan = createFilledOutSpan(true,tags.getTags());
        assertThat(validSpan.getTraceId()).isNotEmpty();
        assertThat(validSpan.getUserId()).isNotEmpty();
        assertThat(validSpan.getParentSpanId()).isNotEmpty();
        assertThat(validSpan.getSpanName()).isNotEmpty();
        assertThat(validSpan.getSpanId()).isNotEmpty();
        assertThat(validSpan.getDurationNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getSpanPurpose()).isNotNull();
        String keyValueStr = validSpan.toKeyValueString();

        // when: the string is deserialized into a map
        Map<String, String> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void toKeyValueString_should_function_properly_when_there_are_null_values() throws IOException {
        // given: valid span with null values and key/value string from Span.toKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        assertThat(validSpan.getDurationNanos()).isNull();
        String keyValueStr = validSpan.toKeyValueString();

        // when: the string is deserialized into a map
        Map<String, String> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void toKeyValueString_should_function_properly_for_non_completed_spans() throws IOException {
        // given: valid span and key/value string from Span.toKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
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
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        completeSpan(validSpan);
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
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
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
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String uuidString = UUID.randomUUID().toString();
        Whitebox.setInternalState(validSpan, "cachedKeyValueRepresentation", uuidString);

        // when
        String beforeCompleteKeyValueString = validSpan.toKeyValueString();
        completeSpan(validSpan);

        // then
        String afterCompleteKeyValueString = validSpan.toKeyValueString();
        assertThat(afterCompleteKeyValueString).isNotEqualTo(beforeCompleteKeyValueString);
        assertThat(afterCompleteKeyValueString).isNotEqualTo(uuidString);
        Map<String, String> deserializedValues = deserializeKeyValueSpanString(afterCompleteKeyValueString);
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @DataProvider( value = {
            "NULL",
            "SINGLE_VALUE",
            "MULTIPLE_VALUES",
            "SPECIAL_CHARS"
        })
    @Test
    public void fromKeyValueString_should_function_properly_when_there_are_no_null_values(TAG_SET tags) {
        // given: valid span without any null values, completed (so that end time is not null) and key/value string from Span.fromKeyValueString()
        Span validSpan = createFilledOutSpan(true, tags.getTags());
        assertThat(validSpan).isNotNull();
        assertThat(validSpan.getTraceId()).isNotNull();
        assertThat(validSpan.getUserId()).isNotNull();
        assertThat(validSpan.getParentSpanId()).isNotNull();
        assertThat(validSpan.getSpanName()).isNotNull();
        assertThat(validSpan.getSpanId()).isNotNull();
        assertThat(validSpan.getDurationNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getSpanPurpose()).isNotNull();
        assertThat(validSpan.getTags()).isNotNull();
        String keyValStr = validSpan.toKeyValueString();

        // when: toKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr, true);
    }

    @Test
    public void fromKeyValueString_should_function_properly_when_there_are_null_values() throws IOException {
        // given: valid span with null values and key/value string from Span.fromKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        assertThat(validSpan.getDurationNanos()).isNull();
        String keyValStr = validSpan.toKeyValueString();

        // when: toKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr, true);
    }

    @Test
    public void fromKeyValueString_should_function_properly_for_non_completed_spans() throws IOException {
        // given: valid, non-completed span and key/value string from Span.fromKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String keyValStr = validSpan.toKeyValueString();
        assertThat(validSpan.isCompleted()).isFalse();

        // when: toKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr, true);
    }

    @Test
    public void fromKeyValueString_should_function_properly_for_completed_spans() throws IOException {
        // given: valid span that has been completed, and key/value string from Span.fromKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        completeSpan(validSpan);
        assertThat(validSpan.isCompleted()).isTrue();
        String keyValStr = validSpan.toKeyValueString();

        // when: toKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr, true);
    }

    @Test
    public void fromKeyValueString_should_return_null_for_garbage_input() throws IOException {
        // given: garbage input
        String garbageInput = "garbagio";

        // when: fromKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(garbageInput);

        // then: the return value should be null
        assertThat(spanFromKeyValStr).isNull();
    }

    @Test
    public void fromKeyValueString_returns_null_if_sampleable_field_is_missing() throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validKeyValStr = validSpan.toKeyValueString();
        String invalidKeyValStr = validKeyValStr.replace(String.format(",%s=%s", SpanParser.SAMPLEABLE_FIELD, String.valueOf(validSpan.isSampleable())), "");

        // when
        Span result = SpanParser.fromKeyValueString(invalidKeyValStr);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void fromKeyValueString_returns_null_if_startTimeEpochMicros_field_is_missing() throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validKeyValStr = validSpan.toKeyValueString();
        String invalidKeyValStr = validKeyValStr.replace(
            String.format(",%s=%s", SpanParser.START_TIME_EPOCH_MICROS_FIELD, String.valueOf(validSpan.getSpanStartTimeEpochMicros())),
            ""
        );

        // when
        Span result = SpanParser.fromKeyValueString(invalidKeyValStr);

        // then
        assertThat(result).isNull();
    }

    @DataProvider(value = {
        "",
        "foobar-not-a-real-enum-value"
    }, splitBy = "\\|")
    @Test
    public void fromKeyValueString_returns_span_with_UNKNOWN_span_purpose_if_spanPurpose_field_is_missing_or_garbage(String badValue) throws IOException {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validKeyValStr = validSpan.toKeyValueString();
        if (badValue.trim().length() > 0) {
            badValue = ",spanPurpose=" + badValue;
        }
        String invalidKeyValStr = validKeyValStr.replace(
            String.format(",%s=%s", SpanParser.SPAN_PURPOSE_FIELD, String.valueOf(validSpan.getSpanPurpose())),
            badValue
        );
        assertThat(validSpan.getSpanPurpose()).isNotEqualTo(SpanPurpose.UNKNOWN);

        // when
        Span result = SpanParser.fromKeyValueString(invalidKeyValStr);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSpanPurpose()).isEqualTo(SpanPurpose.UNKNOWN);
    }
    
    
}
