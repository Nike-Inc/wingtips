package com.nike.wingtips;

import com.nike.internal.util.MapBuilder;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.util.TracerManagedSpanStatus;
import com.nike.wingtips.util.parser.SpanParser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.nike.wingtips.TestSpanCompleter.completeSpan;
import static com.nike.wingtips.util.parser.SpanParserTest.deserializeKeyValueSpanString;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

/**
 * Tests the functionality of {@link Span}
 */
@RunWith(DataProviderRunner.class)
public class SpanTest {

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
    private Map<String,String> tags = Collections.unmodifiableMap(
        MapBuilder.builder("fooTagKey", UUID.randomUUID().toString())
                  .put("barTagKey", UUID.randomUUID().toString())
                  .build()
    );

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

    private Span createFilledOutSpan(boolean completed) {
        Long durationNanos = (completed) ? durationNanosForFullyCompletedSpan : null;
        return new Span(traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId,
                        spanPurposeForFullyCompletedSpan, startTimeEpochMicrosForFullyCompleteSpan,
                        startTimeNanosForFullyCompleteSpan, durationNanos, tags
        );
    }

    public static void verifySpanDeepEquals(Span span1, Span span2, boolean allowStartTimeNanosFudgeFactor) {
        assertThat(span1.getSpanStartTimeEpochMicros()).isEqualTo(span2.getSpanStartTimeEpochMicros());
        if (allowStartTimeNanosFudgeFactor) {
            assertThat(span1.getSpanStartTimeNanos())
                .isCloseTo(span2.getSpanStartTimeNanos(), Offset.offset(TimeUnit.MILLISECONDS.toNanos(1)));
        }
        else {
            assertThat(span1.getSpanStartTimeNanos()).isEqualTo(span2.getSpanStartTimeNanos());
        }
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

    @Test
    public void public_constructor_works_as_expected_for_completed_span() {
        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId, spanPurposeForFullyCompletedSpan,
                             startTimeEpochMicrosForFullyCompleteSpan, startTimeNanosForFullyCompleteSpan, durationNanosForFullyCompletedSpan, tags);

        // then
        assertThat(span.getTraceId()).isEqualTo(traceId);
        assertThat(span.getParentSpanId()).isEqualTo(parentSpanId);
        assertThat(span.getSpanId()).isEqualTo(spanId);
        assertThat(span.getSpanName()).isEqualTo(spanName);
        assertThat(span.isSampleable()).isEqualTo(sampleableForFullyCompleteSpan);
        assertThat(span.getUserId()).isEqualTo(userId);
        assertThat(span.getSpanStartTimeEpochMicros()).isEqualTo(startTimeEpochMicrosForFullyCompleteSpan);
        assertThat(span.getSpanStartTimeNanos()).isEqualTo(startTimeNanosForFullyCompleteSpan);
        assertThat(span.getSpanPurpose()).isEqualTo(spanPurposeForFullyCompletedSpan);

        assertThat(span.isCompleted()).isTrue();
        assertThat(span.getDurationNanos()).isEqualTo(durationNanosForFullyCompletedSpan);
        assertThat(span.getTags()).isEqualTo(tags);
    }

    @Test
    public void public_constructor_works_as_expected_for_incomplete_span() {
        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId, spanPurposeForFullyCompletedSpan,
                             startTimeEpochMicrosForFullyCompleteSpan, startTimeNanosForFullyCompleteSpan, null, tags);

        // then
        assertThat(span.getTraceId()).isEqualTo(traceId);
        assertThat(span.getParentSpanId()).isEqualTo(parentSpanId);
        assertThat(span.getSpanId()).isEqualTo(spanId);
        assertThat(span.getSpanName()).isEqualTo(spanName);
        assertThat(span.isSampleable()).isEqualTo(sampleableForFullyCompleteSpan);
        assertThat(span.getUserId()).isEqualTo(userId);
        assertThat(span.getSpanStartTimeEpochMicros()).isEqualTo(startTimeEpochMicrosForFullyCompleteSpan);
        assertThat(span.getSpanStartTimeNanos()).isEqualTo(startTimeNanosForFullyCompleteSpan);
        assertThat(span.getSpanPurpose()).isEqualTo(spanPurposeForFullyCompletedSpan);

        assertThat(span.isCompleted()).isFalse();
        assertThat(span.getDurationNanos()).isNull();
        assertThat(span.getTags()).isEqualTo(tags);
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_passed_null_trace_id() {
        // expect
        new Span(null, parentSpanId, spanId, spanName, true, userId, spanPurpose, 42, null, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_passed_null_span_id() {
        // expect
        new Span(traceId, parentSpanId, null, spanName, true, userId, spanPurpose, 42, null, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_passed_null_span_name() {
        // expect
        new Span(traceId, parentSpanId, spanId, null, true, userId, spanPurpose, 42, null, null, null);
    }

    @Test
    public void public_constructor_defaults_to_UNKNOWN_span_purpose_if_passed_null() {
        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, true, userId, null, 42, null, null, null);

        // then
        assertThat(span.getSpanPurpose()).isEqualTo(SpanPurpose.UNKNOWN);
    }

    @Test
    public void public_constructor_keeps_empty_set_when_tags_are_set_null() {
        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, true, userId, null, 42, null, null, null);

        // then
        assertThat(span.getTags()).isNotNull();
    }
    
    @Test
    public void public_constructor_calculates_start_time_nanos_if_passed_null() {
        // given
        long startTimeEpochMicrosUsed = 42;
        long nanosBeforeCall = System.nanoTime();
        long epochMicrosBeforeCall = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, true, userId, spanPurpose, startTimeEpochMicrosUsed, null, 41L, null);
        long epochMicrosAfterCall = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        long nanosAfterCall = System.nanoTime();

        // then
        long lowerBound = calculateNanoStartTimeFromSpecifiedEpochMicrosStartTime(startTimeEpochMicrosUsed, epochMicrosBeforeCall, nanosBeforeCall);
        long upperBound = calculateNanoStartTimeFromSpecifiedEpochMicrosStartTime(startTimeEpochMicrosUsed, epochMicrosAfterCall, nanosAfterCall);
        assertThat(span.getSpanStartTimeNanos()).isBetween(lowerBound, upperBound);
    }

    private long calculateNanoStartTimeFromSpecifiedEpochMicrosStartTime(long epochMicrosStartTime, long currentEpochMicros, long currentNanoTime) {
        long currentDurationMicros = currentEpochMicros - epochMicrosStartTime;
        long nanoStartTimeOffset = TimeUnit.MICROSECONDS.toNanos(currentDurationMicros);
        return currentNanoTime - nanoStartTimeOffset;
    }

    @Test
    public void protected_constructor_generates_instance_with_placeholder_values() {
        // given
        String placeholderValue = "PLACEHOLDER";

        // when
        Span result = new Span();

        // then
        verifySpanDeepEquals(result,
                             new Span(placeholderValue, null, placeholderValue, placeholderValue, false, null, SpanPurpose.UNKNOWN, -1, -1L, -1L, null),
                             false);
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void generateRootSpanForNewTrace_generates_root_span_as_expected(SpanPurpose spanPurpose) {
        // given
        String spanName = UUID.randomUUID().toString();

        // when
        long beforeCallNanos = System.nanoTime();
        long beforeCallEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Span result = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        long afterCallNanos = System.nanoTime();
        long afterCallEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then
        assertThat(result.getTraceId()).isNotEmpty();
        assertThat(result.getParentSpanId()).isNull();
        assertThat(result.getSpanId()).isNotEmpty();
        assertThat(result.getSpanName()).isEqualTo(spanName);
        assertThat(result.isSampleable()).isTrue();
        assertThat(result.getUserId()).isNull();
        assertThat(result.getSpanPurpose()).isEqualTo(spanPurpose);
        assertThat(result.getSpanStartTimeEpochMicros()).isBetween(beforeCallEpochMicros, afterCallEpochMicros);
        assertThat(result.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(result.isCompleted()).isFalse();
        assertThat(result.getDurationNanos()).isNull();
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void generateChildSpan_works_as_expected_for_incomplete_parent_span(SpanPurpose childSpanPurpose) {
        // given: span object with known values that is not completed
        Span parentSpan = createFilledOutSpan(false);
        String childSpanName = UUID.randomUUID().toString();
        assertThat(parentSpan.isCompleted()).isFalse();

        // when: generateChildSpan is used to create a child span with a new span name
        long beforeCallNanos = System.nanoTime();
        long beforeCallEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Span childSpan = parentSpan.generateChildSpan(childSpanName, childSpanPurpose);
        long afterCallNanos = System.nanoTime();
        long afterCallEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then: returned object contains the expected values
        //       (new span ID, expected span name, parent span ID equal to parent's span ID, start time generated during call, not completed, everything else the same as parent).
        assertThat(childSpan.getSpanId()).isNotEmpty();
        assertThat(childSpan.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(childSpan.getSpanName()).isEqualTo(childSpanName);
        assertThat(childSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());

        assertThat(childSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(childSpan.getUserId()).isEqualTo(parentSpan.getUserId());
        assertThat(childSpan.getSpanPurpose()).isEqualTo(childSpanPurpose);
        assertThat(childSpan.isSampleable()).isEqualTo(parentSpan.isSampleable());

        assertThat(childSpan.getSpanStartTimeEpochMicros()).isBetween(beforeCallEpochMicros, afterCallEpochMicros);
        assertThat(childSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(childSpan.isCompleted()).isFalse();
        assertThat(childSpan.getDurationNanos()).isNull();
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void generateChildSpan_works_as_expected_for_completed_parent_span(SpanPurpose childSpanPurpose) {
        // given: span with known values that is completed
        Span parentSpan = createFilledOutSpan(true);
        String childSpanName = UUID.randomUUID().toString();
        assertThat(parentSpan.isCompleted()).isTrue();

        // when: generateChildSpan is used to create a child span with a new span name
        long beforeCallNanos = System.nanoTime();
        long beforeCallEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Span childSpan = parentSpan.generateChildSpan(childSpanName, childSpanPurpose);
        long afterCallNanos = System.nanoTime();
        long afterCallEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then: returned object contains the expected values
        //       (new span ID, expected span name, parent span ID equal to parent's span ID, start time generated during call, not completed, everything else the same as parent).
        assertThat(childSpan.getSpanId()).isNotEmpty();
        assertThat(childSpan.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(childSpan.getSpanName()).isEqualTo(childSpanName);
        assertThat(childSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());

        assertThat(childSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(childSpan.getUserId()).isEqualTo(parentSpan.getUserId());
        assertThat(childSpan.getSpanPurpose()).isEqualTo(childSpanPurpose);
        assertThat(childSpan.isSampleable()).isEqualTo(parentSpan.isSampleable());

        assertThat(childSpan.getSpanStartTimeEpochMicros()).isBetween(beforeCallEpochMicros, afterCallEpochMicros);
        assertThat(childSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(childSpan.isCompleted()).isFalse();
        assertThat(childSpan.getDurationNanos()).isNull();
    }

    @Test
    public void complete_method_should_complete_the_span_with_correct_duration() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            // given
            Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
            assertThat(validSpan.isCompleted()).isFalse();

            // when
            Thread.sleep((long) (Math.random() * 10));
            long beforeCompleteNanoTime = System.nanoTime();
            validSpan.complete();
            long afterCompleteNanoTime = System.nanoTime();

            // then
            assertThat(validSpan.isCompleted()).isTrue();
            long lowerBoundDuration = beforeCompleteNanoTime - validSpan.getSpanStartTimeNanos();
            long upperBoundDuration = afterCompleteNanoTime - validSpan.getSpanStartTimeNanos();
            assertThat(validSpan.getDurationNanos()).isBetween(lowerBoundDuration, upperBoundDuration);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void complete_should_throw_IllegalStateException_if_span_is_already_completed() {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        validSpan.complete();
        assertThat(validSpan.isCompleted()).isTrue();

        // expect
        validSpan.complete();
        fail("Expected IllegalStateException but no exception was thrown");
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
    public void getDuration_should_be_null_until_span_is_completed() {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        assertThat(validSpan.getDurationNanos()).isNull();

        // when
        validSpan.complete();

        // then
        assertThat(validSpan.getDurationNanos()).isNotNull();
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
    public void equals_returns_false_and_hashCode_different_if_spanStartTimeEpochMicros_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        Whitebox.setInternalState(fullSpan2, "spanStartTimeEpochMicros", fullSpan1.getSpanStartTimeEpochMicros() + 1);

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
    public void equals_returns_false_and_hashCode_different_if_spanPurpose_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        List<SpanPurpose> badDataList = Arrays.asList(SpanPurpose.CLIENT, SpanPurpose.UNKNOWN, null);

        for (SpanPurpose badData : badDataList) {
            assertThat(fullSpan1.getSpanPurpose()).isNotEqualTo(badData);
            Whitebox.setInternalState(fullSpan2, "spanPurpose", badData);

            // expect
            assertThat(fullSpan1.equals(fullSpan2)).isFalse();
            assertThat(fullSpan2.equals(fullSpan1)).isFalse();
            assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
        }
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_durationNanos_is_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        List<Long> badDataList = Arrays.asList(fullSpan1.getDurationNanos() + 1, null);

        for (Long badData : badDataList) {
            Whitebox.setInternalState(fullSpan2, "durationNanos", badData);

            // expect
            assertThat(fullSpan1.equals(fullSpan2)).isFalse();
            assertThat(fullSpan2.equals(fullSpan1)).isFalse();
            assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
        }
    }

    @Test
    public void equals_returns_false_and_hashCode_different_if_tags_are_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        fullSpan1.putTag("key-" + UUID.randomUUID().toString(), UUID.randomUUID().toString());

        // expect
        assertThat(fullSpan1.equals(fullSpan2)).isFalse();
        assertThat(fullSpan1.hashCode()).isNotEqualTo(fullSpan2.hashCode());
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void newBuilder_with_spanName_and_spanPurpose_args_returns_root_span_builder_by_default(SpanPurpose spanPurpose) {
        // given
        String spanName = UUID.randomUUID().toString();

        // when
        long beforeCallNanos = System.nanoTime();
        long beforeCallEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Span result = Span.newBuilder(spanName, spanPurpose).build();
        long afterCallNanos = System.nanoTime();
        long afterCallEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then
        assertThat(result.getTraceId()).isNotEmpty();
        assertThat(result.getParentSpanId()).isNull();
        assertThat(result.getSpanId()).isNotEmpty();
        assertThat(result.getSpanName()).isEqualTo(spanName);
        assertThat(result.isSampleable()).isTrue();
        assertThat(result.getUserId()).isNull();
        assertThat(result.getSpanPurpose()).isEqualTo(spanPurpose);
        assertThat(result.getSpanStartTimeEpochMicros()).isBetween(beforeCallEpochMicros, afterCallEpochMicros);
        assertThat(result.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(result.getDurationNanos()).isNull();
        assertThat(result.isCompleted()).isFalse();
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void newBuilder_with_copy_arg_returns_exact_copy(SpanPurpose spanPurpose) {
        // given
        Span origSpan = createFilledOutSpan(true);
        Whitebox.setInternalState(origSpan, "spanPurpose", spanPurpose);
        assertThat(origSpan.getSpanPurpose()).isEqualTo(spanPurpose);

        // when
        Span copySpan = Span.newBuilder(origSpan).build();

        // then
        verifySpanDeepEquals(origSpan, copySpan, false);
    }

    @Test
    public void newBuilder_honors_values_for_all_fields_if_set() {

        // given
        Span.Builder builder = Span
                .newBuilder("override_me", SpanPurpose.UNKNOWN)
                .withTraceId(traceId)
                .withSpanId(spanId)
                .withParentSpanId(parentSpanId)
                .withSpanName(spanName)
                .withSampleable(sampleableForFullyCompleteSpan)
                .withUserId(userId)
                .withSpanPurpose(spanPurpose)
                .withSpanStartTimeEpochMicros(startTimeEpochMicrosForFullyCompleteSpan)
                .withSpanStartTimeNanos(startTimeNanosForFullyCompleteSpan)
                .withDurationNanos(durationNanosForFullyCompletedSpan)
                .withTags(tags);
        
        assertThat(spanPurpose).isNotEqualTo(SpanPurpose.UNKNOWN);

        // when
        Span span = builder.build();

        // then
        assertThat(span.getTraceId()).isEqualTo(traceId);
        assertThat(span.getSpanId()).isEqualTo(spanId);
        assertThat(span.getParentSpanId()).isEqualTo(parentSpanId);
        assertThat(span.getSpanName()).isEqualTo(spanName);
        assertThat(span.isSampleable()).isEqualTo(sampleableForFullyCompleteSpan);
        assertThat(span.getUserId()).isEqualTo(userId);
        assertThat(span.getSpanPurpose()).isEqualTo(spanPurpose);
        assertThat(span.getSpanStartTimeEpochMicros()).isEqualTo(startTimeEpochMicrosForFullyCompleteSpan);
        assertThat(span.getSpanStartTimeNanos()).isEqualTo(startTimeNanosForFullyCompleteSpan);
        assertThat(span.getDurationNanos()).isEqualTo(durationNanosForFullyCompletedSpan);
        assertThat(span.getTags()).isEqualTo(tags);
    }

    @Test
    public void builder_build_ignores_passed_in_spanStartTimeNanos_if_spanStartTimeEpochMicros_is_null() {
        // given
        Span.Builder builder = Span
            .newBuilder("stuff", SpanPurpose.LOCAL_ONLY)
            .withSpanStartTimeNanos(42L)
            .withSpanStartTimeEpochMicros(null);

        // when
        long beforeNanos = System.nanoTime();
        long beforeEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Span span = builder.build();
        long afterNanos = System.nanoTime();
        long afterEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then
        assertThat(span.getSpanStartTimeNanos()).isBetween(beforeNanos, afterNanos);
        assertThat(span.getSpanStartTimeEpochMicros()).isBetween(beforeEpochMicros, afterEpochMicros);
    }

    @Test
    public void close_completes_the_span_as_expected_overall_request_span() {
        // given
        Span overallSpan = Tracer.getInstance().startRequestWithRootSpan("root");

        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(overallSpan);
        assertThat(overallSpan.isCompleted()).isFalse();

        // when
        overallSpan.close();

        // then
        assertThat(overallSpan.isCompleted()).isTrue();
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
    }

    @Test
    public void close_completes_the_span_as_expected_subspan() {
        // given
        Span parentSpan = Tracer.getInstance().startRequestWithRootSpan("root");
        Span subspan = Tracer.getInstance().startSubSpan("subspan", SpanPurpose.LOCAL_ONLY);

        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(subspan);
        assertThat(subspan.isCompleted()).isFalse();

        // when
        subspan.close();

        // then
        assertThat(subspan.isCompleted()).isTrue();
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(parentSpan);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void span_is_autocloseable_using_try_with_resources_block_overall_request_span(
        boolean throwExceptionInTryBlock
    ) {
        // given
        Span span = null;
        Throwable caughtEx = null;

        // when
        try(Span autocloseableSpan = Tracer.getInstance().startRequestWithRootSpan("root")) {
            assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(autocloseableSpan);
            span = autocloseableSpan;
            if (throwExceptionInTryBlock) {
                throw new RuntimeException("kaboom");
            }
        }
        catch(Throwable t) {
            caughtEx = t;
        }

        // then
        assertThat(span.isCompleted()).isTrue();
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        if (throwExceptionInTryBlock) {
            assertThat(caughtEx).isNotNull();
        }
        else {
            assertThat(caughtEx).isNull();
        }
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void span_is_autocloseable_using_try_with_resources_block_subspan(
        boolean throwExceptionInTryBlock
    ) {
        // given
        Span parentSpan = Tracer.getInstance().startRequestWithRootSpan("root");
        Span subspan = null;
        Throwable caughtEx = null;

        // when
        try(Span autocloseableSubspan = Tracer.getInstance().startSubSpan("subspan", SpanPurpose.LOCAL_ONLY)) {
            assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(autocloseableSubspan);
            subspan = autocloseableSubspan;
            if (throwExceptionInTryBlock) {
                throw new RuntimeException("kaboom");
            }
        }
        catch(Throwable t) {
            caughtEx = t;
        }

        // then
        assertThat(subspan.isCompleted()).isTrue();
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(parentSpan);
        if (throwExceptionInTryBlock) {
            assertThat(caughtEx).isNotNull();
        }
        else {
            assertThat(caughtEx).isNull();
        }
    }

    @Test
    public void close_does_nothing_if_span_is_already_completed() {
        // given
        Span rootSpan = Tracer.getInstance().startRequestWithRootSpan("root");
        Span subspan = Tracer.getInstance().startSubSpan("subspan", SpanPurpose.LOCAL_ONLY);
        Tracer.getInstance().completeSubSpan();

        assertThat(subspan.isCompleted()).isTrue();
        assertThat(rootSpan.isCompleted()).isFalse();
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(rootSpan);
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isEqualTo(singletonList(rootSpan));

        // when
        subspan.close();

        // then
        assertThat(rootSpan.isCompleted()).isFalse();
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(rootSpan);
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isEqualTo(singletonList(rootSpan));
    }

    @DataProvider(value = {
        "0",
        "1",
        "2"
    })
    @Test
    public void close_handles_non_Tracer_managed_spans_gracefully_without_affecting_existing_stack(
        int numValidSpansOnStack
    ) {
        // given
        for (int i = 0; i < numValidSpansOnStack; i++) {
            if (i == 0) {
                Tracer.getInstance().startRequestWithRootSpan("root");
            }
            else {
                Tracer.getInstance().startSubSpan("subspan" + i, SpanPurpose.LOCAL_ONLY);
            }
        }
        assertThat(Tracer.getInstance().getCurrentSpanStackSize()).isEqualTo(numValidSpansOnStack);
        Deque<Span> originalValidSpanStack = Tracer.getInstance().getCurrentSpanStackCopy();

        Span invalidSpan = Span.generateRootSpanForNewTrace("invalidSpan", SpanPurpose.LOCAL_ONLY).build();
        assertThat(invalidSpan.isCompleted()).isFalse();

        // when
        invalidSpan.close();

        // then
        assertThat(invalidSpan.isCompleted()).isTrue();
        assertThat(Tracer.getInstance().getCurrentSpanStackSize()).isEqualTo(numValidSpansOnStack);
        if (numValidSpansOnStack == 0) {
            assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        }
        else {
            assertThat(Tracer.getInstance().getCurrentSpan().isCompleted()).isFalse();
        }
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isEqualTo(originalValidSpanStack);
    }

    @Test
    public void close_handles_non_current_but_Tracer_managed_spans_gracefully() {
        // given
        Span parentSpan = Tracer.getInstance().startRequestWithRootSpan("root");
        Span subspan1 = Tracer.getInstance().startSubSpan("subspan1", SpanPurpose.LOCAL_ONLY);
        Span subspan2 = Tracer.getInstance().startSubSpan("subspan2", SpanPurpose.LOCAL_ONLY);

        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(subspan2);
        assertThat(subspan2.isCompleted()).isFalse();
        assertThat(subspan1.isCompleted()).isFalse();
        assertThat(parentSpan.isCompleted()).isFalse();

        Deque<Span> originalSpanStack = Tracer.getInstance().getCurrentSpanStackCopy();

        // when
        parentSpan.close();

        // then
        // Current span (subspan2) should be unmodified.
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(subspan2);
        assertThat(subspan2.isCompleted()).isFalse();
        // The stack as a whole should still be unchanged.
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isEqualTo(originalSpanStack);
        // But the out-of-order closed span should now be completed.
        assertThat(parentSpan.isCompleted()).isTrue();

        // and when - we do the same thing for the middle subspan1
        subspan1.close();

        // then - subspan2 should still be unmodified and the stack as a whole unchanged, but subspan1 completed
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(subspan2);
        assertThat(subspan2.isCompleted()).isFalse();
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isEqualTo(originalSpanStack);
        assertThat(subspan1.isCompleted()).isTrue();

        // and when - we complete everything using tracer
        Tracer.getInstance().completeSubSpan();
        Tracer.getInstance().completeSubSpan();
        Tracer.getInstance().completeRequestSpan();

        // then - we should not have received any errors and everything should be completed
        assertThat(subspan2.isCompleted()).isTrue();
        assertThat(subspan1.isCompleted()).isTrue();
        assertThat(parentSpan.isCompleted()).isTrue();
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(Tracer.getInstance().getCurrentSpanStackSize()).isEqualTo(0);
    }

    @Test
    public void getCurrentTracerManagedSpanStatus_works_as_expected_for_managed_current() {
        {
            // given
            Span currentRootSpan = Tracer.getInstance().startRequestWithRootSpan("root");

            // when
            TracerManagedSpanStatus tmss = currentRootSpan.getCurrentTracerManagedSpanStatus();

            // then
            assertThat(tmss).isEqualTo(TracerManagedSpanStatus.MANAGED_CURRENT_ROOT_SPAN);
        }

        {
            // and given
            Span currentSubspan = Tracer.getInstance().startSubSpan("subspan", SpanPurpose.LOCAL_ONLY);

            // when
            TracerManagedSpanStatus tmss = currentSubspan.getCurrentTracerManagedSpanStatus();

            // then
            assertThat(tmss).isEqualTo(TracerManagedSpanStatus.MANAGED_CURRENT_SUB_SPAN);

        }
    }

    @Test
    public void getCurrentTracerManagedSpanStatus_works_as_expected_for_managed_noncurrent() {
        // given
        Span nonCurrentRootSpan = Tracer.getInstance().startRequestWithRootSpan("root");
        Span nonCurrentSubspan = Tracer.getInstance().startSubSpan("subspan1", SpanPurpose.LOCAL_ONLY);
        Span currentSubspan = Tracer.getInstance().startSubSpan("subspan2", SpanPurpose.LOCAL_ONLY);

        // expect
        assertThat(nonCurrentRootSpan.getCurrentTracerManagedSpanStatus())
            .isEqualTo(TracerManagedSpanStatus.MANAGED_NON_CURRENT_ROOT_SPAN);
        assertThat(nonCurrentSubspan.getCurrentTracerManagedSpanStatus())
            .isEqualTo(TracerManagedSpanStatus.MANAGED_NON_CURRENT_SUB_SPAN);
    }

    @Test
    public void getCurrentTracerManagedSpanStatus_works_as_expected_for_unmanaged() {
        // given
        Span manuallyCreatedSpan = Span.newBuilder("manuallyCreatedSpan", SpanPurpose.LOCAL_ONLY).build();
        Span completedSpan = Tracer.getInstance().startRequestWithRootSpan("completedSpan");
        Tracer.getInstance().completeRequestSpan();

        // when
        TracerManagedSpanStatus tmssManual = manuallyCreatedSpan.getCurrentTracerManagedSpanStatus();
        TracerManagedSpanStatus tmssCompleted = completedSpan.getCurrentTracerManagedSpanStatus();

        // then
        assertThat(tmssManual).isEqualTo(TracerManagedSpanStatus.UNMANAGED_SPAN);
        assertThat(tmssCompleted).isEqualTo(TracerManagedSpanStatus.UNMANAGED_SPAN);
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
        Map<String, Object> spanValuesFromJackson = objectMapper.readValue(afterCompleteJson, new TypeReference<Map<String, Object>>() { });
        verifySpanEqualsDeserializedValues(validSpan, spanValuesFromJackson);
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
    public void complete_should_reset_cached_key_value_string() {
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
        Map<String, Object> deserializedValues = deserializeKeyValueSpanString(afterCompleteKeyValueString);
        verifySpanEqualsDeserializedValues(validSpan, deserializedValues);
    }

    @Test
    public void putTag_works_as_expected() {
        // given
        Span span = Span.newBuilder("foo", SpanPurpose.CLIENT).build();
        assertThat(span.getTags()).isEmpty();
        String tagKey = "key-" + UUID.randomUUID().toString();
        String tagValue = "value-" + UUID.randomUUID().toString();
        String otherValue = "othervalue-" + UUID.randomUUID().toString();

        // when
        span.putTag(tagKey, tagValue);

        // then
        assertThat(span.getTags()).hasSize(1);
        assertThat(span.getTags().get(tagKey)).isEqualTo(tagValue);

        // and when
        span.putTag(tagKey, otherValue);

        // then
        assertThat(span.getTags()).hasSize(1);
        assertThat(span.getTags().get(tagKey)).isEqualTo(otherValue);
    }

    @Test
    public void fromKeyValueString_delegates_to_span_parser() {
        // given
        Span span = Span.newBuilder("foo", SpanPurpose.CLIENT)
                        .withTag("blahtag", UUID.randomUUID().toString())
                        .build();
        String keyValueStr = span.toKeyValueString();

        // when
        Span result = span.fromKeyValueString(keyValueStr);

        // then
        verifySpanDeepEquals(result, span, true);
    }

    @Test
    public void fromJSON_delegates_to_span_parser() {
        // given
        Span span = Span.newBuilder("foo", SpanPurpose.CLIENT)
                        .withTag("blahtag", UUID.randomUUID().toString())
                        .build();
        String json = span.toJSON();

        // when
        Span result = span.fromJSON(json);

        // then
        verifySpanDeepEquals(result, span, true);
    }

    /**
     * @return The string "null" if obj is null (in order to match how Span.toJson() functions), otherwise
     * String.valueOf(obj).
     */
    public static String nullSafeStringValueOf(Object obj) {
        if (obj == null) {
            return "null";
        }

        return String.valueOf(obj);
    }

    public static void verifySpanEqualsDeserializedValues(Span span, Map<String, ?> deserializedValues) {
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

        Map<String, String> tags = span.getTags();
        Map<String, Object> deserializedTags = (Map<String, Object>) deserializedValues.get(SpanParser.TAGS_FIELD);

        if (tags.isEmpty()) {
            assertThat(deserializedTags).isNullOrEmpty();
        }
        else {
            assertThat(deserializedTags).isEqualTo(tags);
        }
    }
}

