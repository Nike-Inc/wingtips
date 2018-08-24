package com.nike.wingtips.util.parser;

import com.nike.internal.util.MapBuilder;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.nike.wingtips.Tracer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nike.wingtips.SpanTest.verifySpanDeepEquals;
import static com.nike.wingtips.SpanTest.verifySpanEqualsDeserializedValues;
import static com.nike.wingtips.TestSpanCompleter.completeSpan;
import static org.assertj.core.api.Assertions.assertThat;

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

    @Before
    public void beforeMethod() {
        resetTracing();
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
    }

    private long calculateNanoStartTimeFromSpecifiedEpochMicrosStartTime(
        long epochMicrosStartTime, long currentEpochMicros, long currentNanoTime
    ) {
        long currentDurationMicros = currentEpochMicros - epochMicrosStartTime;
        long nanoStartTimeOffset = TimeUnit.MICROSECONDS.toNanos(currentDurationMicros);
        return currentNanoTime - nanoStartTimeOffset;
    }

    @Test
    public void code_coverage_hoops() {
        // jump!
        new SpanParser();
    }

    private static final String ALL_JSON_CHARS_THAT_NEED_ESCAPING;
    private static final String ESCAPED_JSON_CHARS =
        "\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007\\b\\t\\n\\u000B\\f\\r\\u000E\\u000F\\u0010\\u0011"
        + "\\u0012\\u0013\\u0014\\u0015\\u0016\\u0017\\u0018\\u0019\\u001A\\u001B\\u001C\\u001D\\u001E\\u001F\\\"\\\\";
    static {
        char firstControlChar = '\u0000';
        char lastControlChar = '\u001F';

        StringBuilder needEscaping = new StringBuilder();
        for (int i = (int)firstControlChar; i <= (int)lastControlChar; i++) {
            needEscaping.append((char)i);
        }

        needEscaping
            .append('\"')
            .append('\\');

        ALL_JSON_CHARS_THAT_NEED_ESCAPING = needEscaping.toString();
    }

    private enum EscapeJsonScenario {
        ONLY_CHARS_NEEDING_ESCAPE(
            ALL_JSON_CHARS_THAT_NEED_ESCAPING,
            ESCAPED_JSON_CHARS
        ),
        CHARS_NEEDING_ESCAPE_AT_END(
            "SomeNormalPrefix" + ALL_JSON_CHARS_THAT_NEED_ESCAPING,
            "SomeNormalPrefix" + ESCAPED_JSON_CHARS
        ),
        CHARS_NEEDING_ESCAPE_AT_START(
            ALL_JSON_CHARS_THAT_NEED_ESCAPING + "SomeNormalSuffix",
            ESCAPED_JSON_CHARS + "SomeNormalSuffix"
        ),
        CHARS_NEEDING_ESCAPE_IN_MIDDLE(
            "SomeNormalPrefix" + ALL_JSON_CHARS_THAT_NEED_ESCAPING + "SomeNormalSuffix",
            "SomeNormalPrefix" + ESCAPED_JSON_CHARS + "SomeNormalSuffix"
        );

        public final String strToEscape;
        public final String expectedEscaped;

        EscapeJsonScenario(String strToEscape, String expectedEscaped) {
            this.strToEscape = strToEscape;
            this.expectedEscaped = expectedEscaped;
        }
    }

    @DataProvider
    public static List<List<EscapeJsonScenario>> escapeJsonScenarioDataProvider() {
        return Arrays.stream(EscapeJsonScenario.values()).map(Collections::singletonList).collect(Collectors.toList());
    }

    @UseDataProvider("escapeJsonScenarioDataProvider")
    @Test
    public void escapeJson_escapes_characters_as_expected(EscapeJsonScenario scenario) throws JsonProcessingException {
        // given
        // Have jackson serialize something with the scenario's string-to-escape, and compare our escapeJson result to it.
        String jacksonSerialized = objectMapper.writeValueAsString(
            Collections.singletonMap("foo", scenario.strToEscape)
        );

        // when
        String escaped = SpanParser.escapeJson(scenario.strToEscape);

        // then
        String escapedWithJacksonObjBoilerplate = "{\"foo\":\"" + escaped + "\"}";
        assertThat(escapedWithJacksonObjBoilerplate).isEqualTo(jacksonSerialized);
        assertThat(escaped).isEqualTo(scenario.expectedEscaped);
    }

    @Test
    public void escapeJson_returns_null_if_passed_null() {
        // expect
        assertThat(SpanParser.escapeJson(null)).isNull();
    }

    @Test
    public void escapeJson_returns_same_instance_if_no_chars_need_escaping() {
        // given
        String orig = UUID.randomUUID().toString();

        // expect
        assertThat(SpanParser.escapeJson(orig)).isSameAs(orig);
    }

    @UseDataProvider("escapeJsonScenarioDataProvider")
    @Test
    public void unescapeJson_unescapes_characters_as_expected(EscapeJsonScenario scenario) throws IOException {
        // given
        // Have jackson deserialize something with the scenario's escaped-value, and compare our unescapeJson result to it.
        Map<String, String> jacksonDeserialized = objectMapper.readValue(
            "{\"foo\":\"" + scenario.expectedEscaped + "\"}",
            new TypeReference<Map<String, String>>(){}
        );
        String jacksonUnescaped = jacksonDeserialized.get("foo");

        // when
        String unescaped = SpanParser.unescapeJson(scenario.expectedEscaped);

        // then
        assertThat(unescaped).isEqualTo(jacksonUnescaped);
        assertThat(unescaped).isEqualTo(scenario.strToEscape);
    }

    @Test
    public void unescapeJson_returns_null_if_passed_null() {
        // expect
        assertThat(SpanParser.unescapeJson(null)).isNull();
    }

    @Test
    public void unescapeJson_returns_same_instance_if_no_backspace_chars_exist() {
        // given
        String orig = UUID.randomUUID().toString();

        // expect
        assertThat(SpanParser.unescapeJson(orig)).isSameAs(orig);
    }

    @Test
    public void escape_and_unescape_json_kitchen_sink() throws IOException {
        // given
        StringBuilder allCharsSb = new StringBuilder();
        for (int i = Character.MIN_VALUE; i <= Character.MAX_VALUE; i++) {
            allCharsSb.append((char)i);
        }
        assertThat(allCharsSb.length()).isEqualTo(Character.MAX_VALUE + 1);
        String mess = allCharsSb.toString();

        Map<String, String> mapWithMess = Collections.singletonMap("mess", mess);

        // when
        String escapedMess = SpanParser.escapeJson(mess);
        String jacksonSerialized = objectMapper.writeValueAsString(mapWithMess);

        // then
        assertThat("{\"mess\":\"" + escapedMess + "\"}").isEqualTo(jacksonSerialized);

        // and when
        String unescapedMess = SpanParser.unescapeJson(escapedMess);
        Map<String, String> jacksonDeserialized = objectMapper.readValue(
            jacksonSerialized,
            new TypeReference<Map<String, String>>(){}
        );

        // then
        assertThat(unescapedMess).isEqualTo(mess);
        assertThat(unescapedMess).isEqualTo(jacksonDeserialized.get("mess"));
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void getJsonEscapedValueForChar_returns_null_if_passed_int_value_outside_range_of_JSON_ESCAPE_CHAR_MAPPINGS_array(
        boolean exactArrayLength
    ) {
        // given
        int argValue = (exactArrayLength)
                       ? SpanParser.JSON_ESCAPE_CHAR_MAPPINGS.length
                       : SpanParser.JSON_ESCAPE_CHAR_MAPPINGS.length + 1;

        // when
        String result = SpanParser.getJsonEscapedValueForChar((char)argValue);

        // then
        assertThat(result).isNull();
    }

    private enum TagScenario {
        EMPTY_TAGS_MAP(Collections.emptyMap()),
        SINGLE_TAG(Collections.singletonMap("fookey", "foovalue")),
        TAG_WITH_EMPTY_KEY_AND_VALUE(Collections.singletonMap("", "")),
        MULTIPLE_TAGS(
            MapBuilder.builder("fookey", "foovalue")
                      .put("color", "blue")
                      .put("day", "today")
                      .build()
        ),
        TAGS_WITH_SPECIAL_CHARS(
            MapBuilder.builder("key+s", "value")
                      .put("crazy \" \n\t\r\b\f \\ key", "crazy \" \n\t\r\b\f \\ value")
                      .put("\"keywithquotes\"", "\"valuewithquotes\"")
                      .put("unicode\u0000\u0001key", "unicode\u0000\u0001value")
                      .build()
        );

        public final Map<String,String> tags;

        TagScenario(Map<String, String> tags) {
            this.tags = tags;
        }
    }

    @DataProvider
    public static List<List<TagScenario>> tagScenarioDataProvider() {
        return Arrays.stream(TagScenario.values()).map(Collections::singletonList).collect(Collectors.toList());
    }

    @UseDataProvider("tagScenarioDataProvider")
    @Test
    public void convertSpanToJSON_should_function_properly_when_there_are_no_null_values(
        TagScenario tagsScenario
    ) throws IOException {
        // given: valid span without any null values, span completed (so that end time is not null), and JSON string
        //      from SpanParser.convertSpanToJSON()
        Span validSpan = createFilledOutSpan(true, tagsScenario.tags);
        assertThat(validSpan.getTraceId()).isNotEmpty();
        assertThat(validSpan.getUserId()).isNotEmpty();
        assertThat(validSpan.getParentSpanId()).isNotEmpty();
        assertThat(validSpan.getSpanName()).isNotEmpty();
        assertThat(validSpan.getSpanId()).isNotEmpty();
        assertThat(validSpan.getDurationNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getSpanPurpose()).isNotNull();
        String json = SpanParser.convertSpanToJSON(validSpan);
        
        // when: jackson is used to deserialize that JSON
        Map<String, Object> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        // then: the original span context and jackson's span context values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
    }

    private Span createFilledOutSpan(boolean completed) {
        return createFilledOutSpan(completed, TagScenario.SINGLE_TAG.tags);
    }

    private Span createFilledOutSpan(boolean completed, Map<String, String> tags) {
        Long durationNanos = (completed) ? durationNanosForFullyCompletedSpan : null;
        return new Span(
            traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId,
            spanPurposeForFullyCompletedSpan, startTimeEpochMicrosForFullyCompleteSpan,
            startTimeNanosForFullyCompleteSpan, durationNanos, tags
        );
    }

    @Test
    public void convertSpanToJSON_should_function_properly_when_there_are_null_values() throws IOException {
        // given: valid span with null values and JSON string from SpanParser.convertSpanToJSON()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        assertThat(validSpan.getDurationNanos()).isNull();
        String json = SpanParser.convertSpanToJSON(validSpan);

        // when: jackson is used to deserialize that JSON
        Map<String, Object> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        // then: the original span context and jackson's span context values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
    }

    @Test
    public void convertSpanToJSON_should_function_properly_for_non_completed_spans() throws IOException {
        // given: valid span and JSON string from SpanParser.convertSpanToJSON()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String json = SpanParser.convertSpanToJSON(validSpan);
        assertThat(validSpan.isCompleted()).isFalse();
        assertThat(validSpan.getDurationNanos()).isNull();

        // when: jackson is used to deserialize that JSON
        Map<String, Object> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        // then: the original span and jackson's span values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
    }

    @Test
    public void convertSpanToJSON_should_function_properly_for_completed_spans() throws IOException {
        // given: valid span and completed, and JSON string from SpanParser.convertSpanToJSON()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        completeSpan(validSpan);
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getDurationNanos()).isNotNull();
        String json = SpanParser.convertSpanToJSON(validSpan);

        // when: jackson is used to deserialize that JSON
        Map<String, Object> spanValuesFromJackson = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        // then: the original span and jackson's span values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
    }

    @UseDataProvider("tagScenarioDataProvider")
    @Test
    public void fromJson_should_function_properly_when_there_are_no_null_values(TagScenario tagsScenario) {
        // given: valid span without any null values, completed (so that end time is not null) and JSON string
        //      from SpanParser.convertSpanToJSON()
        Span validSpan = createFilledOutSpan(true, tagsScenario.tags);
        assertThat(validSpan).isNotNull();
        assertThat(validSpan.getTraceId()).isNotNull();
        assertThat(validSpan.getUserId()).isNotNull();
        assertThat(validSpan.getParentSpanId()).isNotNull();
        assertThat(validSpan.getSpanName()).isNotNull();
        assertThat(validSpan.getSpanId()).isNotNull();
        assertThat(validSpan.getDurationNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getSpanPurpose()).isNotNull();
        String json = SpanParser.convertSpanToJSON(validSpan);

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson, true);
    }

    @Test
    public void fromJson_should_function_properly_when_there_are_null_values() {
        // given: valid span with null values and JSON string from SpanParser.convertSpanToJSON()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        assertThat(validSpan.getDurationNanos()).isNull();
        String json = SpanParser.convertSpanToJSON(validSpan);

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson, true);
    }

    @Test
    public void fromJson_should_function_properly_for_non_completed_spans() {
        // given: valid, non-completed span and JSON string from SpanParser.convertSpanToJSON()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String json = SpanParser.convertSpanToJSON(validSpan);
        assertThat(validSpan.isCompleted()).isFalse();

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson, true);
    }

    @Test
    public void fromJson_should_function_properly_for_completed_spans() {
        // given: valid span that has been completed, and JSON string from SpanParser.convertSpanToJSON()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        completeSpan(validSpan);
        assertThat(validSpan.isCompleted()).isTrue();
        String json = SpanParser.convertSpanToJSON(validSpan);

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(json);

        // then: the original span and the fromJson() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromJson, true);
    }

    private enum BadJsonScenario {
        MISSING_SPAN_OBJECT_START_CURLY_BRACE(
            s -> SpanParser.convertSpanToJSON(s).replace("{\"traceId\":", "\"traceId\":")
        ),
        MISSING_KEY_START_QUOTES(
            s -> SpanParser.convertSpanToJSON(s).replace("\"traceId\":", "traceId\":")
        ),
        MISSING_COLON_DELIMETER(
            s -> SpanParser.convertSpanToJSON(s).replace("\"traceId\":", "\"traceId\"")
        ),
        BAD_VALUE_START_CHAR(
            s -> SpanParser.convertSpanToJSON(s).replace("\"traceId\":\"", "\"traceId\":x")
        ),
        MISSING_COMMA_BETWEEN_VALUE_AND_NEXT_KEY(
            s -> SpanParser.convertSpanToJSON(s).replace("\",\"spanId\"", "\"\"spanId\"")
        ),
        UNEXPECTED_SPACE_BETWEEN_VALUE_AND_COMMA(
            s -> SpanParser.convertSpanToJSON(s).replace("\",\"spanId\"", "\" ,\"spanId\"")
        ),
        UNEXPECTED_SPACE_BETWEEN_COMMA_AND_NEXT_KEY(
            s -> SpanParser.convertSpanToJSON(s).replace("\",\"spanId\"", "\", \"spanId\"")
        ),
        MISSING_FINAL_CLOSE_OBJECT_CURLY_BRACE(
            s -> {
                String goodJson = SpanParser.convertSpanToJSON(s);
                return goodJson.substring(0, goodJson.length() - 1);
            }
        ),
        TAGS_MISSING_OPEN_OBJECT_CURLY_BRACE(
            s -> SpanParser.convertSpanToJSON(s).replace("\"tags\":{\"", "\"tags\":\"")
        ),
        TAGS_MISSING_CLOSE_OBJECT_CURLY_BRACE(
            s -> SpanParser.convertSpanToJSON(s).replaceFirst("}", "")
        );

        private final Function<Span, String> badJsonGenerator;

        BadJsonScenario(Function<Span, String> badJsonGenerator) {
            this.badJsonGenerator = badJsonGenerator;
        }

        public String generateBadJson(Span span) {
            String regularJson = SpanParser.convertSpanToJSON(span);
            String badJson = badJsonGenerator.apply(span);
            assertThat(badJson).isNotEqualTo(regularJson);
            return badJson;
        }
    }

    @DataProvider
    public static List<List<BadJsonScenario>> badJsonScenarioDataProvider() {
        return Arrays.stream(BadJsonScenario.values()).map(Collections::singletonList).collect(Collectors.toList());
    }

    @UseDataProvider("badJsonScenarioDataProvider")
    @Test
    public void fromJson_should_return_null_for_garbage_input(BadJsonScenario scenario) {
        // given: garbage input
        String garbageInput = scenario.generateBadJson(
            Span.newBuilder("foo", SpanPurpose.CLIENT)
                .withTag("footag", "bar")
                .build()
        );

        // when: fromJson is called
        Span spanFromJson = SpanParser.fromJSON(garbageInput);

        // then: the return value should be null
        assertThat(spanFromJson).isNull();
    }

    @Test
    public void fromJson_returns_null_if_sampleable_field_is_missing() {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validJson = SpanParser.convertSpanToJSON(validSpan);
        String invalidJson = validJson.replace(
            String.format(",\"%s\":\"%s\"",
                          SpanParser.SAMPLEABLE_FIELD,
                          String.valueOf(validSpan.isSampleable())
            ),
            ""
        );

        // when
        Span result = SpanParser.fromJSON(invalidJson);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void fromJson_returns_null_if_startTimeEpochMicros_field_is_missing() {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validJson = SpanParser.convertSpanToJSON(validSpan);
        String invalidJson = validJson.replace(
            String.format(",\"%s\":\"%s\"",
                          SpanParser.START_TIME_EPOCH_MICROS_FIELD,
                          String.valueOf(validSpan.getSpanStartTimeEpochMicros())
            ),
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
    public void fromJson_returns_span_with_UNKNOWN_span_purpose_if_spanPurpose_field_is_missing_or_garbage(
        String badValue
    ) {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validJson = SpanParser.convertSpanToJSON(validSpan);
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

    private enum EscapedAndUnescapedQuotesBeforeKeyOrValueEndScenario {
        BACKSLASH_BEFORE_KEY_OR_VALUE_END("foo\\", "bar\\"),
        TWO_BACKSLASHES_BEFORE_KEY_OR_VALUE_END("foo\\\\", "bar\\\\"),
        THREE_BACKSLASHES_BEFORE_KEY_OR_VALUE_END("foo\\\\\\", "bar\\\\\\"),
        QUOTE_BEFORE_KEY_OR_VALUE_END("foo\"", "bar\""),
        BACKSLASH_THEN_QUOTE_BEFORE_KEY_OR_VALUE_END("foo\\\"", "bar\\\""),
        TWO_BACKSLASHES_THEN_QUOTE_BEFORE_KEY_OR_VALUE_END("foo\\\\\"", "bar\\\\\""),
        THREE_BACKSLASHES_THEN_QUOTE_BEFORE_KEY_OR_VALUE_END("foo\\\\\\\"", "bar\\\\\\\"");

        public final String unescapedTagKey;
        public final String unescapedTagValue;
        public final String escapedTagKey;
        public final String escapedTagValue;

        EscapedAndUnescapedQuotesBeforeKeyOrValueEndScenario(String unescapedTagKey, String unescapedTagValue) {
            this.unescapedTagKey = unescapedTagKey;
            this.unescapedTagValue = unescapedTagValue;

            this.escapedTagKey = SpanParser.escapeJson(unescapedTagKey);
            this.escapedTagValue = SpanParser.escapeJson(unescapedTagValue);
        }
    }

    @DataProvider
    public static List<List<EscapedAndUnescapedQuotesBeforeKeyOrValueEndScenario>>
                                escapedAndUnescapedQuotesBeforeKeyOrValueEndScenarioDataProvider() {
        return Arrays.stream(EscapedAndUnescapedQuotesBeforeKeyOrValueEndScenario.values())
                     .map(Collections::singletonList).collect(Collectors.toList());
    }

    @UseDataProvider("escapedAndUnescapedQuotesBeforeKeyOrValueEndScenarioDataProvider")
    @Test
    public void fromJSON_properly_handles_escaped_quotes_and_unescaped_quotes_preceded_by_backslashes(
        EscapedAndUnescapedQuotesBeforeKeyOrValueEndScenario scenario
    ) {
        // given
        Span span = Span.newBuilder("someSpan", SpanPurpose.CLIENT)
                        .withTag(scenario.unescapedTagKey, scenario.unescapedTagValue)
                        .build();
        String json = SpanParser.convertSpanToJSON(span);

        // when
        Span result = SpanParser.fromJSON(json);

        // then
        assertThat(result.getTags().get(scenario.unescapedTagKey)).isEqualTo(scenario.unescapedTagValue);
    }

    @Test
    public void convertSpanToJSON_and_fromJSON_should_escape_and_unescape_expected_non_tag_values() {
        // The TAGS_WITH_SPECIAL_CHARS case already verified tags. Now we need to verify that non-tag values are
        //      escaped. Also note that other tests have verified that escapeJson() and unescapeJson() work properly.

        // given
        String complexSpanName = "span-name-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        String complexTraceId = "trace-id-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        String complexParentId = "parent-id-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        String complexSpanId = "span-id-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        String complexUserId = "user-id-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        Span span = Span.newBuilder(complexSpanName, SpanPurpose.CLIENT)
                        .withTraceId(complexTraceId)
                        .withParentSpanId(complexParentId)
                        .withSpanId(complexSpanId)
                        .withUserId(complexUserId)
                        .build();

        // when
        String json = SpanParser.convertSpanToJSON(span);

        // then
        assertThat(json).contains("\"traceId\":\"trace-id-" + ESCAPED_JSON_CHARS + "\"");
        assertThat(json).contains("\"parentSpanId\":\"parent-id-" + ESCAPED_JSON_CHARS + "\"");
        assertThat(json).contains("\"spanId\":\"span-id-" + ESCAPED_JSON_CHARS + "\"");
        assertThat(json).contains("\"userId\":\"user-id-" + ESCAPED_JSON_CHARS + "\"");
        assertThat(json).contains("\"spanName\":\"span-name-" + ESCAPED_JSON_CHARS + "\"");

        // and when
        Span deserialized = SpanParser.fromJSON(json);

        // then
        verifySpanDeepEquals(span, deserialized, true);
    }

    @UseDataProvider("tagScenarioDataProvider")
    @Test
    public void convertSpanToKeyValueFormat_should_function_properly_when_there_are_no_null_values(
        TagScenario tagsScenario
    ) {
        // given: valid known span without any null values, span completed (so that end time is not null)
        //      and key/value string from SpanParser.convertSpanToKeyValueFormat()
        Span validSpan = createFilledOutSpan(true,tagsScenario.tags);
        assertThat(validSpan.getTraceId()).isNotEmpty();
        assertThat(validSpan.getUserId()).isNotEmpty();
        assertThat(validSpan.getParentSpanId()).isNotEmpty();
        assertThat(validSpan.getSpanName()).isNotEmpty();
        assertThat(validSpan.getSpanId()).isNotEmpty();
        assertThat(validSpan.getDurationNanos()).isNotNull();
        assertThat(validSpan.isCompleted()).isTrue();
        assertThat(validSpan.getSpanPurpose()).isNotNull();
        String keyValueStr = SpanParser.convertSpanToKeyValueFormat(validSpan);

        // when: the string is deserialized into a map
        Map<String, Object> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    /**
     * NOTE: This method does not work if any of the keys or values contains equals '=' or commas ','. We're relying
     * on this assumption to do quick and easily-verifiable parsing for the purpose of these tests.
     */
    public static Map<String, Object> deserializeKeyValueSpanString(String keyValStr) {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, String> tags = new LinkedHashMap<>();
        map.put(SpanParser.TAGS_FIELD, tags);
        String[] fields = keyValStr.split(",");
        for (String field : fields) {
            String[] info = field.split("=");
            String key = SpanParser.unescapeTagKeyForKeyValueFormatDeserialization(info[0]);
            String value = SpanParser.unescapeJson(info[1]);
            assertThat(value)
                .withFailMessage("Expected values in the key/value string to be surrounded with unescaped quotes.")
                .startsWith("\"")
                .endsWith("\"")
                .doesNotEndWith("\\\"");
            value = value.substring(1, value.length() - 1);
            if (key.startsWith(SpanParser.KEY_VALUE_TAG_PREFIX)) {
                key = key.substring(SpanParser.KEY_VALUE_TAG_PREFIX.length());
                tags.put(key, value);
            }
            else {
                map.put(key, value);
            }
        }
        return map;
    }

    @Test
    public void convertSpanToKeyValueFormat_should_function_properly_when_there_are_null_values() {
        // given: valid span with null values and key/value string from SpanParser.convertSpanToKeyValueFormat()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        assertThat(validSpan.getDurationNanos()).isNull();
        String keyValueStr = SpanParser.convertSpanToKeyValueFormat(validSpan);

        // when: the string is deserialized into a map
        Map<String, Object> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void convertSpanToKeyValueFormat_should_function_properly_for_non_completed_spans() {
        // given: valid span and key/value string from SpanParser.convertSpanToKeyValueFormat()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        assertThat(validSpan.isCompleted()).isFalse();
        String keyValueStr = SpanParser.convertSpanToKeyValueFormat(validSpan);

        // when: the string is deserialized into a map
        Map<String, Object> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void convertSpanToKeyValueFormat_should_function_properly_for_completed_spans() {
        // given: valid span and completed, and key/value string from SpanParser.convertSpanToKeyValueFormat()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        completeSpan(validSpan);
        assertThat(validSpan.isCompleted()).isTrue();
        String keyValueStr = SpanParser.convertSpanToKeyValueFormat(validSpan);

        // when: the string is deserialized into a map
        Map<String, Object> deserializedValues = deserializeKeyValueSpanString(keyValueStr);

        // then: the original span and deserialized map values should be exactly the same
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @UseDataProvider("tagScenarioDataProvider")
    @Test
    public void fromKeyValueString_should_function_properly_when_there_are_no_null_values(TagScenario tagsScenario) {
        // given: valid span without any null values, completed (so that end time is not null) and key/value string
        //      from Span.fromKeyValueString()
        Span validSpan = createFilledOutSpan(true, tagsScenario.tags);
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
        String keyValStr = SpanParser.convertSpanToKeyValueFormat(validSpan);

        // when: fromKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr, true);
    }

    @Test
    public void fromKeyValueString_should_function_properly_when_there_are_null_values() {
        // given: valid span with null values and key/value string from Span.fromKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, null).build();
        assertThat(validSpan.getParentSpanId()).isNull();
        assertThat(validSpan.getUserId()).isNull();
        assertThat(validSpan.getDurationNanos()).isNull();
        String keyValStr = SpanParser.convertSpanToKeyValueFormat(validSpan);

        // when: fromKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr, true);
    }

    @Test
    public void fromKeyValueString_should_function_properly_for_non_completed_spans() {
        // given: valid, non-completed span and key/value string from Span.fromKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String keyValStr = SpanParser.convertSpanToKeyValueFormat(validSpan);
        assertThat(validSpan.isCompleted()).isFalse();

        // when: fromKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr, true);
    }

    @Test
    public void fromKeyValueString_should_function_properly_for_completed_spans() {
        // given: valid span that has been completed, and key/value string from Span.fromKeyValueString()
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        completeSpan(validSpan);
        assertThat(validSpan.isCompleted()).isTrue();
        String keyValStr = SpanParser.convertSpanToKeyValueFormat(validSpan);

        // when: fromKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(keyValStr);

        // then: the original span and the fromKeyValueString() span values should be exactly the same
        verifySpanDeepEquals(validSpan, spanFromKeyValStr, true);
    }

    private enum BadKeyValueScenario {
        MISSING_VALUE_START_QUOTES(
            s -> SpanParser.convertSpanToKeyValueFormat(s).replace("traceId=\"", "traceId=")
        ),
        MISSING_COMMA_BETWEEN_VALUE_AND_NEXT_KEY(
            s -> SpanParser.convertSpanToKeyValueFormat(s).replace(",spanId=\"", "spanId=\"")
        ),
        UNEXPECTED_SPACE_BETWEEN_VALUE_AND_COMMA(
            s -> SpanParser.convertSpanToKeyValueFormat(s).replace(",spanId=\"", " ,spanId=\"")
        ),
        UNEXPECTED_SPACE_BETWEEN_COMMA_AND_NEXT_KEY(
            s -> SpanParser.convertSpanToKeyValueFormat(s).replace(",spanId=\"", ", spanId=\"")
        ),
        ENDED_IN_WRONG_STATE(
            s -> "traceId=\"foo\",spanId="
        );

        private final Function<Span, String> badKeyValueStrGenerator;

        BadKeyValueScenario(Function<Span, String> badKeyValueStrGenerator) {
            this.badKeyValueStrGenerator = badKeyValueStrGenerator;
        }

        public String generateBadKeyValueStr(Span span) {
            String regularKeyValueStr = SpanParser.convertSpanToKeyValueFormat(span);
            String badKeyValueStr = badKeyValueStrGenerator.apply(span);
            assertThat(badKeyValueStr).isNotEqualTo(regularKeyValueStr);
            return badKeyValueStr;
        }
    }

    @DataProvider
    public static List<List<BadKeyValueScenario>> badKeyValueScenarioDataProvider() {
        return Arrays.stream(BadKeyValueScenario.values()).map(Collections::singletonList).collect(Collectors.toList());
    }

    @UseDataProvider("badKeyValueScenarioDataProvider")
    @Test
    public void fromKeyValueString_should_return_null_for_garbage_input(BadKeyValueScenario scenario) {
        // given: garbage input
        String garbageInput = scenario.generateBadKeyValueStr(
            Span.newBuilder("foo", SpanPurpose.CLIENT)
                .withTag("footag", "bar")
                .build()
        );

        // when: fromKeyValueString is called
        Span spanFromKeyValStr = SpanParser.fromKeyValueString(garbageInput);

        // then: the return value should be null
        assertThat(spanFromKeyValStr).isNull();
    }

    @Test
    public void fromKeyValueString_returns_null_if_sampleable_field_is_missing() {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validKeyValStr = SpanParser.convertSpanToKeyValueFormat(validSpan);
        String invalidKeyValStr = validKeyValStr.replace(
            String.format(",%s=\"%s\"",
                          SpanParser.SAMPLEABLE_FIELD,
                          String.valueOf(validSpan.isSampleable())
            ),
            ""
        );

        // when
        Span result = SpanParser.fromKeyValueString(invalidKeyValStr);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void fromKeyValueString_returns_null_if_startTimeEpochMicros_field_is_missing() {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validKeyValStr = SpanParser.convertSpanToKeyValueFormat(validSpan);
        String invalidKeyValStr = validKeyValStr.replace(
            String.format(",%s=\"%s\"",
                          SpanParser.START_TIME_EPOCH_MICROS_FIELD,
                          String.valueOf(validSpan.getSpanStartTimeEpochMicros())
            ),
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
    public void fromKeyValueString_returns_span_with_UNKNOWN_span_purpose_if_spanPurpose_field_is_missing_or_garbage(
        String badValue
    ) {
        // given
        Span validSpan = createFilledOutSpan(true);
        String validKeyValStr = SpanParser.convertSpanToKeyValueFormat(validSpan);
        if (badValue.trim().length() > 0) {
            badValue = ",spanPurpose=\"" + badValue + "\"";
        }
        String invalidKeyValStr = validKeyValStr.replace(
            String.format(",%s=\"%s\"", SpanParser.SPAN_PURPOSE_FIELD, String.valueOf(validSpan.getSpanPurpose())),
            badValue
        );
        assertThat(validSpan.getSpanPurpose()).isNotEqualTo(SpanPurpose.UNKNOWN);

        // when
        Span result = SpanParser.fromKeyValueString(invalidKeyValStr);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSpanPurpose()).isEqualTo(SpanPurpose.UNKNOWN);
    }

    @UseDataProvider("escapedAndUnescapedQuotesBeforeKeyOrValueEndScenarioDataProvider")
    @Test
    public void fromKeyValueString_properly_handles_escaped_quotes_and_unescaped_quotes_preceded_by_backslashes(
        EscapedAndUnescapedQuotesBeforeKeyOrValueEndScenario scenario
    ) {
        // given
        Span span = Span.newBuilder("someSpan", SpanPurpose.CLIENT)
                        .withTag(scenario.unescapedTagKey, scenario.unescapedTagValue)
                        .build();
        String keyValueStr = SpanParser.convertSpanToKeyValueFormat(span);

        // when
        Span result = SpanParser.fromKeyValueString(keyValueStr);

        // then
        assertThat(result.getTags().get(scenario.unescapedTagKey)).isEqualTo(scenario.unescapedTagValue);
    }

    @Test
    public void convertSpanToKeyValueFormat_and_fromKeyValueString_should_escape_and_unescape_expected_non_tag_values() {
        // The TAGS_WITH_SPECIAL_CHARS case already verified tags. Now we need to verify that non-tag values are
        //      escaped. Also note that other tests have verified that escapeJson() and unescapeJson() work properly.

        // given
        String complexSpanName = "span-name-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        String complexTraceId = "trace-id-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        String complexParentId = "parent-id-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        String complexSpanId = "span-id-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        String complexUserId = "user-id-" + ALL_JSON_CHARS_THAT_NEED_ESCAPING;
        Span span = Span.newBuilder(complexSpanName, SpanPurpose.CLIENT)
                        .withTraceId(complexTraceId)
                        .withParentSpanId(complexParentId)
                        .withSpanId(complexSpanId)
                        .withUserId(complexUserId)
                        .build();

        // when
        String keyValueStr = SpanParser.convertSpanToKeyValueFormat(span);

        // then
        assertThat(keyValueStr).contains("traceId=\"trace-id-" + ESCAPED_JSON_CHARS + "\"");
        assertThat(keyValueStr).contains("parentSpanId=\"parent-id-" + ESCAPED_JSON_CHARS + "\"");
        assertThat(keyValueStr).contains("spanId=\"span-id-" + ESCAPED_JSON_CHARS + "\"");
        assertThat(keyValueStr).contains("userId=\"user-id-" + ESCAPED_JSON_CHARS + "\"");
        assertThat(keyValueStr).contains("spanName=\"span-name-" + ESCAPED_JSON_CHARS + "\"");

        // and when
        Span deserialized = SpanParser.fromKeyValueString(keyValueStr);

        // then
        verifySpanDeepEquals(span, deserialized, true);
    }

    @Test
    public void convertSpanToKeyValueFormat_and_fromKeyValueString_escapes_and_unescapes_tag_keys_as_expected() {
        // given
        String unescapedTagKey = "fookey=blah withspace";
        String tagValue = UUID.randomUUID().toString();
        Span span = Span.newBuilder("someSpan", SpanPurpose.CLIENT)
                        .withTag(unescapedTagKey, tagValue)
                        .build();
        String expectedEscapedTagKey = "fookey\\u003Dblah\\u0020withspace";

        // when
        String keyValueStr = SpanParser.convertSpanToKeyValueFormat(span);

        // then
        assertThat(keyValueStr).contains("," + SpanParser.KEY_VALUE_TAG_PREFIX + expectedEscapedTagKey + "=\"" + tagValue + "\"");

        // and when
        Span deserialized = SpanParser.fromKeyValueString(keyValueStr);

        // then
        verifySpanDeepEquals(span, deserialized, true);
    }
}
