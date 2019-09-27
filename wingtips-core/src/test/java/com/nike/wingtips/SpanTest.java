package com.nike.wingtips;

import com.nike.internal.util.MapBuilder;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Span.TimestampedAnnotation;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.util.TracerManagedSpanStatus;
import com.nike.wingtips.util.parser.SpanParser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.apache.commons.lang.SerializationUtils;
import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.nike.wingtips.http.HttpRequestTracingUtils.CHILD_OF_SPAN_FROM_HEADERS_WHERE_CALLER_DID_NOT_SEND_SPAN_ID_TAG_KEY;
import static com.nike.wingtips.util.parser.SpanParserTest.deserializeKeyValueSpanString;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyZeroInteractions;

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
    private List<TimestampedAnnotation> annotations = Collections.unmodifiableList(
        Arrays.asList(
            TimestampedAnnotation.forEpochMicros(12345, UUID.randomUUID().toString()),
            TimestampedAnnotation.forEpochMicros(12345, UUID.randomUUID().toString()),
            TimestampedAnnotation.forEpochMicros(67890, UUID.randomUUID().toString())
        )
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
                        startTimeNanosForFullyCompleteSpan, durationNanos, tags, annotations
        );
    }

    public static void verifySpanDeepEquals(
        Span spanToVerify, Span expectedSpan, boolean allowStartTimeNanosFudgeFactor
    ) {
        assertThat(spanToVerify.getSpanStartTimeEpochMicros()).isEqualTo(expectedSpan.getSpanStartTimeEpochMicros());
        if (allowStartTimeNanosFudgeFactor) {
            assertThat(spanToVerify.getSpanStartTimeNanos())
                .isCloseTo(expectedSpan.getSpanStartTimeNanos(), Offset.offset(TimeUnit.MILLISECONDS.toNanos(1)));
        }
        else {
            assertThat(spanToVerify.getSpanStartTimeNanos()).isEqualTo(expectedSpan.getSpanStartTimeNanos());
        }
        assertThat(spanToVerify.isCompleted()).isEqualTo(expectedSpan.isCompleted());
        assertThat(spanToVerify.getTraceId()).isEqualTo(expectedSpan.getTraceId());
        assertThat(spanToVerify.getSpanId()).isEqualTo(expectedSpan.getSpanId());
        assertThat(spanToVerify.getParentSpanId()).isEqualTo(expectedSpan.getParentSpanId());
        assertThat(spanToVerify.getSpanName()).isEqualTo(expectedSpan.getSpanName());
        assertThat(spanToVerify.isSampleable()).isEqualTo(expectedSpan.isSampleable());
        assertThat(spanToVerify.getUserId()).isEqualTo(expectedSpan.getUserId());
        assertThat(spanToVerify.getDurationNanos()).isEqualTo(expectedSpan.getDurationNanos());
        assertThat(spanToVerify.getSpanPurpose()).isEqualTo(expectedSpan.getSpanPurpose());
        assertThat(spanToVerify.getTags()).isEqualTo(expectedSpan.getTags());
        assertThat(spanToVerify.getTimestampedAnnotations()).isEqualTo(expectedSpan.getTimestampedAnnotations());
    }

    @Test
    public void public_constructor_works_as_expected_for_completed_span() {
        // when
        Span span = new Span(
            traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId,
            spanPurposeForFullyCompletedSpan, startTimeEpochMicrosForFullyCompleteSpan,
            startTimeNanosForFullyCompleteSpan, durationNanosForFullyCompletedSpan, tags, annotations
        );

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
        assertThat(span.getTimestampedAnnotations()).isEqualTo(annotations);
    }

    @Test
    public void public_constructor_works_as_expected_for_incomplete_span() {
        // when
        Span span = new Span(
            traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId,
            spanPurposeForFullyCompletedSpan, startTimeEpochMicrosForFullyCompleteSpan,
            startTimeNanosForFullyCompleteSpan, null, tags, annotations
        );

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
        assertThat(span.getTimestampedAnnotations()).isEqualTo(annotations);
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_passed_null_trace_id() {
        // expect
        new Span(null, parentSpanId, spanId, spanName, true, userId, spanPurpose, 42, null, null, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_passed_null_span_id() {
        // expect
        new Span(traceId, parentSpanId, null, spanName, true, userId, spanPurpose, 42, null, null, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void public_constructor_throws_IllegalArgumentException_if_passed_null_span_name() {
        // expect
        new Span(traceId, parentSpanId, spanId, null, true, userId, spanPurpose, 42, null, null, null, null);
    }

    @Test
    public void public_constructor_defaults_to_UNKNOWN_span_purpose_if_passed_null() {
        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, true, userId, null, 42, null, null, null, null);

        // then
        assertThat(span.getSpanPurpose()).isEqualTo(SpanPurpose.UNKNOWN);
    }

    @Test
    public void public_constructor_uses_empty_tags_map_when_tags_argument_is_null() {
        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, true, userId, null, 42, null, null, null, null);

        // then
        assertThat(span.getTags())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void public_constructor_uses_empty_annotations_list_when_annotations_argument_is_null() {
        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, true, userId, null, 42, null, null, null, null);

        // then
        assertThat(span.getTimestampedAnnotations())
            .isNotNull()
            .isEmpty();
    }
    
    @Test
    public void public_constructor_calculates_start_time_nanos_if_passed_null() {
        // given
        long startTimeEpochMicrosUsed = 42;
        long nanosBeforeCall = System.nanoTime();
        long epochMicrosBeforeCall = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // when
        Span span = new Span(traceId, parentSpanId, spanId, spanName, true, userId, spanPurpose, startTimeEpochMicrosUsed, null, 41L, null, null);
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
        verifySpanDeepEquals(
            result,
            new Span(
                placeholderValue, null, placeholderValue, placeholderValue, false, null, SpanPurpose.UNKNOWN, -1, -1L,
                -1L, null, null
            ),
            false
        );
    }

    @Test
    public void setSpanName_works_as_expected() {
        // given
        Span span = Span.newBuilder("origSpanName", SpanPurpose.SERVER).build();
        String newSpanName = UUID.randomUUID().toString();

        assertThat(span.getSpanName()).isNotEqualTo(newSpanName);

        // when
        span.setSpanName(newSpanName);

        // then
        assertThat(span.getSpanName()).isEqualTo(newSpanName);
    }

    @Test
    public void setSpanName_throws_IllegalArgumentException_if_passed_null() {
        // given
        Span span = Span.newBuilder("origSpanName", SpanPurpose.SERVER).build();

        // when
        Throwable ex = catchThrowable(() -> span.setSpanName(null));

        // then
        assertThat(ex).isInstanceOf(IllegalArgumentException.class);
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
        assertThat(result.getTags()).isEmpty();
        assertThat(result.getTimestampedAnnotations()).isEmpty();
    }

    @DataProvider(value = {
        "SERVER     |   true",
        "SERVER     |   false",
        "CLIENT     |   true",
        "CLIENT     |   false",
        "LOCAL_ONLY |   true",
        "LOCAL_ONLY |   false",
        "UNKNOWN    |   true",
        "UNKNOWN    |   false",
    }, splitBy = "\\|")
    @Test
    public void generateChildSpan_works_as_expected_for_incomplete_parent_span(
        SpanPurpose childSpanPurpose, boolean parentHasInvalidSpanIdDueToCallerNotSendingOne
    ) {
        // given: span object with known values that is not completed
        Span parentSpan = createFilledOutSpan(false);
        if (parentHasInvalidSpanIdDueToCallerNotSendingOne) {
            parentSpan.putTag(
                HttpRequestTracingUtils.SPAN_FROM_HEADERS_WHERE_CALLER_DID_NOT_SEND_SPAN_ID_TAG_KEY,
                "true"
            );
        }
        String childSpanName = UUID.randomUUID().toString();
        assertThat(parentSpan.isCompleted()).isFalse();

        String expectedParentSpanIdForChild = (parentHasInvalidSpanIdDueToCallerNotSendingOne)
                                              ? null
                                              : parentSpan.getSpanId();

        int expectedNumChildTags = (parentHasInvalidSpanIdDueToCallerNotSendingOne) ? 1 : 0;

        // when: generateChildSpan is used to create a child span with a new span name
        long beforeCallNanos = System.nanoTime();
        Span childSpan = parentSpan.generateChildSpan(childSpanName, childSpanPurpose);
        long afterCallNanos = System.nanoTime();

        // then: returned object contains the expected values (new span ID, expected span name, parent span ID equal
        //      to parent's span ID, start time generated during call, not completed, no tags, no annotations, and
        //      everything else the same as parent).
        assertThat(childSpan.getSpanId()).isNotEmpty();
        assertThat(childSpan.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(childSpan.getSpanName()).isEqualTo(childSpanName);
        assertThat(childSpan.getParentSpanId()).isEqualTo(expectedParentSpanIdForChild);

        assertThat(childSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(childSpan.getUserId()).isEqualTo(parentSpan.getUserId());
        assertThat(childSpan.getSpanPurpose()).isEqualTo(childSpanPurpose);
        assertThat(childSpan.isSampleable()).isEqualTo(parentSpan.isSampleable());

        long expectedMinChildStartEpochMicros =
            parentSpan.getSpanStartTimeEpochMicros() + TimeUnit.NANOSECONDS.toMicros(beforeCallNanos - parentSpan.getSpanStartTimeNanos());
        long expectedMaxChildStartEpochMicros =
            parentSpan.getSpanStartTimeEpochMicros() + TimeUnit.NANOSECONDS.toMicros(afterCallNanos - parentSpan.getSpanStartTimeNanos());
        assertThat(childSpan.getSpanStartTimeEpochMicros()).isBetween(expectedMinChildStartEpochMicros, expectedMaxChildStartEpochMicros);

        assertThat(childSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(childSpan.isCompleted()).isFalse();
        assertThat(childSpan.getDurationNanos()).isNull();

        assertThat(childSpan.getTags()).hasSize(expectedNumChildTags);
        assertThat(childSpan.getTimestampedAnnotations()).isEmpty();

        verifyInvalidParentIdBecauseCallerDidNotSendSpanId(childSpan, parentHasInvalidSpanIdDueToCallerNotSendingOne);
    }

    private void verifyInvalidParentIdBecauseCallerDidNotSendSpanId(Span childSpan, boolean expectInvalid) {
        String expectedIndicatorTagValue = (expectInvalid) ? "true" : null;

        boolean isInvalidParent = HttpRequestTracingUtils.hasInvalidParentIdBecauseCallerDidNotSendSpanId(childSpan);
        String indicatorTagValue = childSpan.getTags().get(
            CHILD_OF_SPAN_FROM_HEADERS_WHERE_CALLER_DID_NOT_SEND_SPAN_ID_TAG_KEY
        );

        assertThat(isInvalidParent).isEqualTo(expectInvalid);
        assertThat(indicatorTagValue).isEqualTo(expectedIndicatorTagValue);
    }

    @DataProvider(value = {
        "SERVER     |   true",
        "SERVER     |   false",
        "CLIENT     |   true",
        "CLIENT     |   false",
        "LOCAL_ONLY |   true",
        "LOCAL_ONLY |   false",
        "UNKNOWN    |   true",
        "UNKNOWN    |   false",
    }, splitBy = "\\|")
    @Test
    public void generateChildSpan_works_as_expected_for_completed_parent_span(
        SpanPurpose childSpanPurpose, boolean parentHasInvalidSpanIdDueToCallerNotSendingOne
    ) {
        // given: span with known values that is completed
        Span parentSpan = createFilledOutSpan(true);
        if (parentHasInvalidSpanIdDueToCallerNotSendingOne) {
            parentSpan.putTag(
                HttpRequestTracingUtils.SPAN_FROM_HEADERS_WHERE_CALLER_DID_NOT_SEND_SPAN_ID_TAG_KEY,
                "true"
            );
        }
        String childSpanName = UUID.randomUUID().toString();
        assertThat(parentSpan.isCompleted()).isTrue();

        String expectedParentSpanIdForChild = (parentHasInvalidSpanIdDueToCallerNotSendingOne)
                                              ? null
                                              : parentSpan.getSpanId();

        int expectedNumChildTags = (parentHasInvalidSpanIdDueToCallerNotSendingOne) ? 1 : 0;

        // when: generateChildSpan is used to create a child span with a new span name
        long beforeCallNanos = System.nanoTime();
        Span childSpan = parentSpan.generateChildSpan(childSpanName, childSpanPurpose);
        long afterCallNanos = System.nanoTime();

        // then: returned object contains the expected values (new span ID, expected span name, parent span ID equal
        //      to parent's span ID, start time generated during call, not completed, no tags, no annotations, and
        //      everything else the same as parent).
        assertThat(childSpan.getSpanId()).isNotEmpty();
        assertThat(childSpan.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(childSpan.getSpanName()).isEqualTo(childSpanName);
        assertThat(childSpan.getParentSpanId()).isEqualTo(expectedParentSpanIdForChild);

        assertThat(childSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(childSpan.getUserId()).isEqualTo(parentSpan.getUserId());
        assertThat(childSpan.getSpanPurpose()).isEqualTo(childSpanPurpose);
        assertThat(childSpan.isSampleable()).isEqualTo(parentSpan.isSampleable());

        long expectedMinChildStartEpochMicros =
            parentSpan.getSpanStartTimeEpochMicros() + TimeUnit.NANOSECONDS.toMicros(beforeCallNanos - parentSpan.getSpanStartTimeNanos());
        long expectedMaxChildStartEpochMicros =
            parentSpan.getSpanStartTimeEpochMicros() + TimeUnit.NANOSECONDS.toMicros(afterCallNanos - parentSpan.getSpanStartTimeNanos());
        assertThat(childSpan.getSpanStartTimeEpochMicros()).isBetween(expectedMinChildStartEpochMicros, expectedMaxChildStartEpochMicros);
        
        assertThat(childSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(childSpan.isCompleted()).isFalse();
        assertThat(childSpan.getDurationNanos()).isNull();

        assertThat(childSpan.getTags()).hasSize(expectedNumChildTags);
        assertThat(childSpan.getTimestampedAnnotations()).isEmpty();

        verifyInvalidParentIdBecauseCallerDidNotSendSpanId(childSpan, parentHasInvalidSpanIdDueToCallerNotSendingOne);
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
            boolean result = validSpan.complete();
            long afterCompleteNanoTime = System.nanoTime();

            // then
            assertThat(result).isTrue();
            assertThat(validSpan.isCompleted()).isTrue();
            long lowerBoundDuration = beforeCompleteNanoTime - validSpan.getSpanStartTimeNanos();
            long upperBoundDuration = afterCompleteNanoTime - validSpan.getSpanStartTimeNanos();
            assertThat(validSpan.getDurationNanos()).isBetween(lowerBoundDuration, upperBoundDuration);
        }
    }

    @Test
    public void complete_should_throw_IllegalStateException_if_span_is_already_completed() {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        validSpan.complete();
        assertThat(validSpan.isCompleted()).isTrue();

        long durationAfterInitialCompletion = validSpan.getDurationNanos();

        // when
        boolean result = validSpan.complete();

        // expect
        assertThat(result).isFalse();
        assertThat(validSpan.getDurationNanos()).isEqualTo(durationAfterInitialCompletion);
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
        assertThat(span.hashCode()).isNotEqualTo(notASpan.hashCode());
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

    @Test
    public void equals_returns_false_and_hashCode_different_if_annotations_are_different() {
        // given
        Span fullSpan1 = createFilledOutSpan(true);
        Span fullSpan2 = createFilledOutSpan(true);
        fullSpan1.addTimestampedAnnotation(TimestampedAnnotation.forCurrentTime("foo"));

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
        assertThat(result.getTags()).isEmpty();
        assertThat(result.getTimestampedAnnotations()).isEmpty();
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
        verifySpanDeepEquals(copySpan, origSpan, false);
    }

    @Test
    public void newBuilder_honors_values_for_all_fields_if_set() {

        // given
        TimestampedAnnotation extraAnnotation = TimestampedAnnotation.forCurrentTime(UUID.randomUUID().toString());
        List<TimestampedAnnotation> evenMoreAnnotations = Arrays.asList(
            TimestampedAnnotation.forCurrentTime(UUID.randomUUID().toString()),
            TimestampedAnnotation.forCurrentTime(UUID.randomUUID().toString())
        );

        String extraTagKey = "extraTagKey-" + UUID.randomUUID().toString();
        String extraTagValue = "extraTagValue-" + UUID.randomUUID().toString();
        Map<String, String> evenMoreTags = MapBuilder
            .builder("foo-" + UUID.randomUUID().toString(), "bar-" + UUID.randomUUID().toString())
            .put("foo2-" + UUID.randomUUID().toString(), "bar2-" + UUID.randomUUID().toString())
            .build();

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
                .withTags(tags)
                .withTag(extraTagKey, extraTagValue)
                .withTags(evenMoreTags)
                .withTimestampedAnnotations(annotations)
                .withTimestampedAnnotation(extraAnnotation)
                .withTimestampedAnnotations(evenMoreAnnotations);
        
        assertThat(spanPurpose).isNotEqualTo(SpanPurpose.UNKNOWN);

        Map<String, String> expectedTags = MapBuilder.<String, String>builder()
            .putAll(tags)
            .put(extraTagKey, extraTagValue)
            .putAll(evenMoreTags)
            .build();

        List<TimestampedAnnotation> expectedAnnotations = new ArrayList<>(annotations);
        expectedAnnotations.add(extraAnnotation);
        expectedAnnotations.addAll(evenMoreAnnotations);

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
        assertThat(span.getTags()).isEqualTo(expectedTags);
        assertThat(span.getTimestampedAnnotations()).isEqualTo(expectedAnnotations);
    }

    @Test
    public void builder_withTags_does_nothing_if_passed_null() {
        // given
        Span.Builder builder = Span.newBuilder("foo", SpanPurpose.UNKNOWN);
        Map<String, String> tagsMapSpy = spy(new LinkedHashMap<>());
        Whitebox.setInternalState(builder, "tags", tagsMapSpy);
        
        // when
        Span.Builder resultingBuilder = builder.withTags(null);

        // then
        assertThat(resultingBuilder).isSameAs(builder);
        verifyZeroInteractions(tagsMapSpy);

        // and when
        Span resultingSpan = resultingBuilder.build();

        // then
        assertThat(resultingSpan.getTags()).isEmpty();
    }

    @Test
    public void builder_withTimestampedAnnotations_does_nothing_if_passed_null() {
        // given
        Span.Builder builder = Span.newBuilder("foo", SpanPurpose.UNKNOWN);
        List<TimestampedAnnotation> annotationsListSpy = spy(new ArrayList<>());
        Whitebox.setInternalState(builder, "annotations", annotationsListSpy);

        // when
        Span.Builder resultingBuilder = builder.withTimestampedAnnotations(null);

        // then
        assertThat(resultingBuilder).isSameAs(builder);
        verifyZeroInteractions(annotationsListSpy);

        // and when
        Span resultingSpan = resultingBuilder.build();

        // then
        assertThat(resultingSpan.getTimestampedAnnotations()).isEmpty();
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

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void toJson_matches_SpanParser_convertSpanToJSON(boolean completed) {
        // given
        Span span = createFilledOutSpan(completed);
        String jsonFromSpanParser = SpanParser.convertSpanToJSON(span);

        // when
        String jsonFromSpan = span.toJSON();

        // then
        assertThat(jsonFromSpan).isEqualTo(jsonFromSpanParser);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void toKeyValueString_matches_SpanParser_convertSpanToKeyValueFormat(boolean completed) {
        // given
        Span span = createFilledOutSpan(completed);
        String keyValueStrFromSpanParser = SpanParser.convertSpanToKeyValueFormat(span);

        // when
        String keyValueStrFromSpan = span.toKeyValueString();

        // then
        assertThat(keyValueStrFromSpan).isEqualTo(keyValueStrFromSpanParser);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void toString_matches_SpanParser_convertSpanToJSON(boolean completed) {
        // given
        Span span = createFilledOutSpan(completed);
        String jsonFromSpanParser = SpanParser.convertSpanToJSON(span);

        // when
        String toStringFromSpan = span.toString();

        // then
        assertThat(toStringFromSpan).isEqualTo(jsonFromSpanParser);
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
    public void toString_should_use_cached_json() {
        // given
        Span validSpan = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        String uuidString = UUID.randomUUID().toString();
        Whitebox.setInternalState(validSpan, "cachedJsonRepresentation", uuidString);

        // when
        String toStringResult = validSpan.toString();

        // then
        assertThat(toStringResult).isEqualTo(uuidString);
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
    public void getTags_returns_unmodifiable_map() {
        // given
        Span span = createFilledOutSpan(false);

        // when
        Throwable ex1 = catchThrowable(() -> span.getTags().put("foo", "bar"));
        Throwable ex2 = catchThrowable(() -> span.getTags().remove("foo"));
        Throwable ex3 = catchThrowable(() -> span.getTags().clear());

        // then
        assertThat(ex1).isInstanceOf(UnsupportedOperationException.class);
        assertThat(ex2).isInstanceOf(UnsupportedOperationException.class);
        assertThat(ex3).isInstanceOf(UnsupportedOperationException.class);
    }

    @DataProvider(value = {
        // It's unlikely the test will actually execute fast enough to measure anything under 1 microsecond delay,
        //      but it can't hurt to try.
        "0",            // No delay
        "420",          // 420 nanos
        "1000",         // 1 micros
        "42000",        // 42 micros
        "420000",       // 420 micros
        "600000",       // 600 micros
        "1000000",      // 1 millis
        "4200000"       // 4.2 millis
    })
    @Test
    public void addTimestampedAnnotationForCurrentTime_works_as_expected(long delayNanos) {
        // given
        String annotationValue = UUID.randomUUID().toString();
        
        long nanoTimeBeforeSpanCreation = System.nanoTime();
        Span span = Span.newBuilder("foo", SpanPurpose.CLIENT).build();
        long nanoTimeAfterSpanCreation = System.nanoTime();

        assertThat(span.getTimestampedAnnotations()).isEmpty();

        busyWaitForNanos(delayNanos);

        // when
        long nanoTimeBeforeMethodCall = System.nanoTime();
        span.addTimestampedAnnotationForCurrentTime(annotationValue);
        long nanoTimeAfterMethodCall = System.nanoTime();

        // then
        long minPossibleOffsetMicros =
            TimeUnit.NANOSECONDS.toMicros(nanoTimeBeforeMethodCall - nanoTimeAfterSpanCreation);
        long maxPossibleOffsetMicros =
            1 + TimeUnit.NANOSECONDS.toMicros(nanoTimeAfterMethodCall - nanoTimeBeforeSpanCreation);

        long expectedMinTimestamp = span.getSpanStartTimeEpochMicros() + minPossibleOffsetMicros;
        long expectedMaxTimestamp = span.getSpanStartTimeEpochMicros() + maxPossibleOffsetMicros;

        assertThat(span.getTimestampedAnnotations().get(0).getTimestampEpochMicros())
            .isBetween(expectedMinTimestamp, expectedMaxTimestamp);
    }

    private void busyWaitForNanos(long delayNanos) {
        if (delayNanos == 0) {
            return;
        }

        long startTimeNanos = System.nanoTime();
        while ((System.nanoTime() - startTimeNanos) < delayNanos) {
            // Do nothing. Busy/wait.
        }

        return;
    }

    @Test
    public void addTimestampedAnnotation_works_as_expected() {
        // given
        Span span = Span.newBuilder("foo", SpanPurpose.CLIENT).build();
        TimestampedAnnotation annotationMock = mock(TimestampedAnnotation.class);
        
        // when
        span.addTimestampedAnnotation(annotationMock);

        // then
        assertThat(span.getTimestampedAnnotations())
            .hasSize(1)
            .containsExactly(annotationMock);
    }

    @Test
    public void getTimestampedAnnotations_returns_unmodifiable_list() {
        // given
        Span span = createFilledOutSpan(false);

        // when
        Throwable ex1 = catchThrowable(() -> span.getTimestampedAnnotations().add(mock(TimestampedAnnotation.class)));
        Throwable ex2 = catchThrowable(() -> span.getTimestampedAnnotations().remove(0));
        Throwable ex3 = catchThrowable(() -> span.getTimestampedAnnotations().clear());

        // then
        assertThat(ex1).isInstanceOf(UnsupportedOperationException.class);
        assertThat(ex2).isInstanceOf(UnsupportedOperationException.class);
        assertThat(ex3).isInstanceOf(UnsupportedOperationException.class);
    }

    private void setCachedSerializedSpanStrings(Span span, String cachedJson, String cachedKeyValueStr) {
        Whitebox.setInternalState(span, "cachedJsonRepresentation", cachedJson);
        Whitebox.setInternalState(span, "cachedKeyValueRepresentation", cachedKeyValueStr);
    }

    private void verifyCachedSerializedSpanRepresentationStrings(
        Span span,
        String expectedCachedJson,
        String expectedCachedKeyValueStr
    ) {
        assertThat(Whitebox.getInternalState(span, "cachedJsonRepresentation")).isEqualTo(expectedCachedJson);
        assertThat(Whitebox.getInternalState(span, "cachedKeyValueRepresentation")).isEqualTo(expectedCachedKeyValueStr);
    }

    private enum SpanStateChangeScenario {
        COMPLETE_SPAN(
            span -> {},
            TestSpanCompleter::completeSpan
        ),
        SET_SPAN_NAME(
            span -> {},
            span -> span.setSpanName("someNewSpanName_" + UUID.randomUUID().toString())
        ),
        PUT_TAG(
            span -> {},
            span -> span.putTag("fooTag", UUID.randomUUID().toString())
        ),
        REMOVE_TAG(
            span -> span.putTag("fooTag", "fooTagValue"),
            span -> span.removeTag("fooTag")
        ),
        ADD_TIMESTAMPED_ANNOTATION(
            span -> {},
            span -> span.addTimestampedAnnotationForCurrentTime("fooEvent")
        );

        public final Consumer<Span> spanSetupConsumer;
        public final Consumer<Span> stateChanger;

        SpanStateChangeScenario(
            Consumer<Span> spanSetupConsumer, Consumer<Span> stateChanger
        ) {
            this.spanSetupConsumer = spanSetupConsumer;
            this.stateChanger = stateChanger;
        }
    }

    @DataProvider(value = {
        "COMPLETE_SPAN",
        "SET_SPAN_NAME",
        "PUT_TAG",
        "REMOVE_TAG",
        "ADD_TIMESTAMPED_ANNOTATION"
    })
    @Test
    public void span_state_change_should_reset_cached_serialized_span_representation_strings(
        SpanStateChangeScenario scenario
    ) throws IOException {
        // given
        Span span = Span.generateRootSpanForNewTrace(spanName, spanPurpose).build();
        scenario.spanSetupConsumer.accept(span);

        String origCachedJson = UUID.randomUUID().toString();
        String origCachedKeyValueStr = UUID.randomUUID().toString();
        setCachedSerializedSpanStrings(span, origCachedJson, origCachedKeyValueStr);

        String beforeStateChangeJson = span.toJSON();
        String beforeStateChangeKeyValueStr = span.toKeyValueString();

        assertThat(beforeStateChangeJson).isEqualTo(origCachedJson);
        assertThat(beforeStateChangeKeyValueStr).isEqualTo(origCachedKeyValueStr);

        // when
        scenario.stateChanger.accept(span);

        // then
        String afterStateChangeJson = span.toJSON();
        String afterStateChangeKeyValueStr = span.toKeyValueString();

        {
            // Verify cached JSON was reset.
            assertThat(afterStateChangeJson).isNotEqualTo(beforeStateChangeJson);
            assertThat(afterStateChangeJson).isNotEqualTo(origCachedJson);

            assertThat(SpanParser.convertSpanToJSON(span)).isEqualTo(afterStateChangeJson);

            Map<String, Object> spanValuesFromJacksonJson =
                objectMapper.readValue(afterStateChangeJson, new TypeReference<Map<String, Object>>() {});
            verifySpanEqualsDeserializedValues(span, spanValuesFromJacksonJson);
        }

        {
            // Verify cached key/value string was reset.
            assertThat(afterStateChangeKeyValueStr).isNotEqualTo(beforeStateChangeKeyValueStr);
            assertThat(afterStateChangeKeyValueStr).isNotEqualTo(origCachedKeyValueStr);

            assertThat(SpanParser.convertSpanToKeyValueFormat(span)).isEqualTo(afterStateChangeKeyValueStr);

            Map<String, Object> deserializedValuesFromKeyValueStr =
                deserializeKeyValueSpanString(afterStateChangeKeyValueStr);
            verifySpanEqualsDeserializedValues(span, deserializedValuesFromKeyValueStr);
        }

        verifyCachedSerializedSpanRepresentationStrings(span, afterStateChangeJson, afterStateChangeKeyValueStr);
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
        // Verify basic fields.
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

        // Verify tags.
        Map<String, String> tags = span.getTags();
        Map<String, Object> deserializedTags = (Map<String, Object>) deserializedValues.get(SpanParser.TAGS_FIELD);

        if (tags.isEmpty()) {
            assertThat(deserializedTags).isNullOrEmpty();
        }
        else {
            assertThat(deserializedTags).isEqualTo(tags);
        }

        // Verify annotations.
        List<Map<String, String>> annotationsAsListOfMaps = span
            .getTimestampedAnnotations()
            .stream()
            .map(
                a -> MapBuilder.builder(
                    SpanParser.ANNOTATION_SUBOBJECT_TIMESTAMP_FIELD, String.valueOf(a.getTimestampEpochMicros())
                ).put(
                    SpanParser.ANNOTATION_SUBOBJECT_VALUE_FIELD, a.getValue()
                ).build()
            )
            .collect(Collectors.toList());

        List<Map<String, String>> deserializedAnnotations =
            (List<Map<String, String>>) deserializedValues.get(SpanParser.ANNOTATIONS_LIST_FIELD);

        if (annotationsAsListOfMaps.isEmpty()) {
            assertThat(deserializedAnnotations).isNullOrEmpty();
        }
        else {
            assertThat(deserializedAnnotations).isEqualTo(annotationsAsListOfMaps);
        }
    }

    @Test
    public void TimestampedAnnotation_constructor_works_as_expected() {
        // given
        long timestamp = 42;
        String value = UUID.randomUUID().toString();

        // when
        TimestampedAnnotation result = new TimestampedAnnotation(timestamp, value);

        // then
        assertThat(result.getTimestampEpochMicros()).isEqualTo(timestamp);
        assertThat(result.getValue()).isEqualTo(value);
    }

    @Test
    public void TimestampedAnnotation_equals_returns_true_and_hashCode_same_if_same_instance() {
        // given
        TimestampedAnnotation instance = new TimestampedAnnotation(42, "foo");

        // expect
        //noinspection EqualsWithItself
        assertThat(instance.equals(instance)).isTrue();
        assertThat(instance.hashCode()).isEqualTo(instance.hashCode());
    }

    @Test
    public void TimestampedAnnotation_equals_returns_false_and_hashCode_different_if_other_is_not_a_TimestampedAnnotation() {
        // given
        TimestampedAnnotation annotation = new TimestampedAnnotation(42, "foo");
        String notAnAnnotation = "notAnAnnotation";

        // expect
        //noinspection EqualsBetweenInconvertibleTypes
        assertThat(annotation.equals(notAnAnnotation)).isFalse();
        assertThat(annotation.hashCode()).isNotEqualTo(notAnAnnotation.hashCode());
    }

    @Test
    public void TimestampedAnnotation_equals_returns_true_and_hashCode_same_if_all_fields_are_equal() {
        // given
        TimestampedAnnotation annotation1 = new TimestampedAnnotation(42, "foo");
        TimestampedAnnotation annotation2 = new TimestampedAnnotation(
            annotation1.getTimestampEpochMicros(),
            annotation1.getValue()
        );

        // expect
        assertThat(annotation1.equals(annotation2)).isTrue();
        assertThat(annotation1.hashCode()).isEqualTo(annotation2.hashCode());
    }

    @Test
    public void TimestampedAnnotation_equals_returns_false_and_hashCode_different_if_timestamp_is_different() {
        // given
        TimestampedAnnotation annotation1 = new TimestampedAnnotation(42, "foo");
        TimestampedAnnotation annotation2 = new TimestampedAnnotation(
            annotation1.getTimestampEpochMicros() + 1234,
            annotation1.getValue()
        );

        // expect
        assertThat(annotation1.equals(annotation2)).isFalse();
        assertThat(annotation1.hashCode()).isNotEqualTo(annotation2.hashCode());
    }

    @Test
    public void TimestampedAnnotation_equals_returns_false_and_hashCode_different_if_value_is_different() {
        // given
        TimestampedAnnotation annotation1 = new TimestampedAnnotation(42, "foo");
        TimestampedAnnotation annotation2 = new TimestampedAnnotation(
            annotation1.getTimestampEpochMicros(),
            annotation1.getValue() + "_nope"
        );

        // expect
        assertThat(annotation1.equals(annotation2)).isFalse();
        assertThat(annotation1.hashCode()).isNotEqualTo(annotation2.hashCode());
    }

    @Test
    public void TimestampedAnnotation_forEpochMicros_works_as_expected() {
        // given
        long timestampMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        String value = UUID.randomUUID().toString();

        // when
        TimestampedAnnotation result = TimestampedAnnotation.forEpochMicros(timestampMicros, value);

        // then
        assertThat(result.getTimestampEpochMicros()).isEqualTo(timestampMicros);
        assertThat(result.getValue()).isEqualTo(value);
    }

    @Test
    public void TimestampedAnnotation_forEpochMillis_works_as_expected() {
        // given
        long timestampMillis = System.currentTimeMillis();
        String value = UUID.randomUUID().toString();

        // when
        TimestampedAnnotation result = TimestampedAnnotation.forEpochMillis(timestampMillis, value);

        // then
        assertThat(result.getTimestampEpochMicros()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(timestampMillis));
        assertThat(result.getValue()).isEqualTo(value);
    }

    @Test
    public void TimestampedAnnotation_forCurrentTime_works_as_expected() {
        // given
        String value = UUID.randomUUID().toString();

        // when
        long beforeMillis = System.currentTimeMillis();
        TimestampedAnnotation result = TimestampedAnnotation.forCurrentTime(value);
        long afterMillis = System.currentTimeMillis();

        // then
        long beforeMicros = TimeUnit.MILLISECONDS.toMicros(beforeMillis);
        long afterMicros = TimeUnit.MILLISECONDS.toMicros(afterMillis);
        assertThat(result.getTimestampEpochMicros()).isBetween(beforeMicros, afterMicros);
        assertThat(result.getValue()).isEqualTo(value);
    }

    @Test
    public void TimestampedAnnotation_forEpochMicrosWithNanoOffset_works_as_expected() {
        // given
        long timestampMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        long nanoOffset = 4242;
        String value = UUID.randomUUID().toString();

        long expectedMicrosOffset = TimeUnit.NANOSECONDS.toMicros(nanoOffset);
        assertThat(expectedMicrosOffset).isGreaterThan(0);

        long expectedTimestamp = timestampMicros + expectedMicrosOffset;
        assertThat(expectedTimestamp).isNotEqualTo(timestampMicros);

        // when
        TimestampedAnnotation result = TimestampedAnnotation.forEpochMicrosWithNanoOffset(
            timestampMicros, nanoOffset, value
        );

        // then
        assertThat(result.getTimestampEpochMicros()).isEqualTo(expectedTimestamp);
        assertThat(result.getValue()).isEqualTo(value);
    }

    @Test
    public void span_serializes_and_deserializes_with_no_data_loss() {
        Span span = new Span(
            traceId, parentSpanId, spanId, spanName, sampleableForFullyCompleteSpan, userId,
            spanPurposeForFullyCompletedSpan, startTimeEpochMicrosForFullyCompleteSpan,
            startTimeNanosForFullyCompleteSpan, durationNanosForFullyCompletedSpan, tags, annotations
        );

        byte[] bytes = SerializationUtils.serialize(span);
        Span deserializedSpan = (Span) SerializationUtils.deserialize(bytes);

        verifySpanDeepEquals(span, deserializedSpan, false);
    }
}
