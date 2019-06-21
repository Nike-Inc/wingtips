package com.nike.wingtips.lightstep;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.TimestampedAnnotation;
import com.nike.wingtips.TraceAndSpanIdGenerator;

import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.SpanBuilder;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.opentracing.SpanContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(DataProviderRunner.class)
public class WingtipsToLightStepLifecycleListenerTest {

    private WingtipsToLightStepLifecycleListener listener;
    private JRETracer jreTracerMock;
    private Span spanMock;

    private SpanBuilder lsSpanBuilderMock;
    private io.opentracing.Span otSpanMock;

    @Before
    public void beforeMethod() {
        jreTracerMock = mock(JRETracer.class);
        listener = new WingtipsToLightStepLifecycleListener(jreTracerMock);
        spanMock = mock(Span.class);

        lsSpanBuilderMock = mock(SpanBuilder.class);
        otSpanMock = mock(io.opentracing.Span.class);

        doReturn(lsSpanBuilderMock).when(jreTracerMock).buildSpan(anyString());
        doReturn(lsSpanBuilderMock).when(lsSpanBuilderMock).withStartTimestamp(anyLong());
        doReturn(lsSpanBuilderMock).when(lsSpanBuilderMock).ignoreActiveSpan();
        doReturn(lsSpanBuilderMock).when(lsSpanBuilderMock).withTag(anyString(), anyString());
        doReturn(lsSpanBuilderMock).when(lsSpanBuilderMock).withTag(anyString(), anyBoolean());
        doReturn(lsSpanBuilderMock).when(lsSpanBuilderMock).withTag(anyString(), any(Number.class));
        doReturn(lsSpanBuilderMock).when(lsSpanBuilderMock).asChildOf(any(SpanContext.class));

        doReturn(otSpanMock).when(lsSpanBuilderMock).start();
    }

    @Test
    public void constructor_with_tracer_arg_sets_fields_as_expected() {
        // when
        listener = new WingtipsToLightStepLifecycleListener(jreTracerMock);

        // then
        assertThat(listener.tracer).isSameAs(jreTracerMock);
    }

    @Test
    public void constructor_with_tracer_arg_throws_NPE_if_passed_null_tracer() {
        // when
        @SuppressWarnings("ConstantConditions")
        Throwable ex = catchThrowable(() -> new WingtipsToLightStepLifecycleListener(null));

        // then
        assertThat(ex)
            .isInstanceOf(NullPointerException.class)
            .hasMessage("tracer cannot be null.");
    }
    
    @Test
    public void constructor_with_option_args_works_as_expected() {
        // given
        String serviceName = "someServiceName";
        String accessToken = UUID.randomUUID().toString();
        String satelliteUrl = "someSatelliteUrl";
        int satellitePort = 8080;

        // when
        listener = new WingtipsToLightStepLifecycleListener(serviceName, accessToken, satelliteUrl, satellitePort);

        // then
        // TODO: Currently there's no good way to verify that the JRETracer was instantiated with the given options.
        //       The best we can do is verify something was generated.
        assertThat(listener.tracer).isNotNull();
    }

    private enum NullOptionScenario {
        NULL_SERVICE_NAME(null, UUID.randomUUID().toString(), "someSatelliteUrl", "serviceName cannot be null."),
        NULL_ACCESS_TOKEN(null, UUID.randomUUID().toString(), "someSatelliteUrl", "serviceName cannot be null."),
        NULL_SATELLITE_URL(null, UUID.randomUUID().toString(), "someSatelliteUrl", "serviceName cannot be null.");

        public final String serviceName;
        public final String accessToken;
        public final String satelliteUrl;
        public final String expectedNpeMessage;

        NullOptionScenario(
            String serviceName, String accessToken, String satelliteUrl, String expectedNpeMessage
        ) {
            this.serviceName = serviceName;
            this.accessToken = accessToken;
            this.satelliteUrl = satelliteUrl;
            this.expectedNpeMessage = expectedNpeMessage;
        }
    }

    @DataProvider(value = {
        "NULL_SERVICE_NAME",
        "NULL_ACCESS_TOKEN",
        "NULL_SATELLITE_URL"
    })
    @Test
    public void constructor_with_option_args_throws_NPE_if_passed_null_options(NullOptionScenario scenario) {
        // when
        Throwable ex = catchThrowable(() -> new WingtipsToLightStepLifecycleListener(
            scenario.serviceName, scenario.accessToken, scenario.satelliteUrl, 8080
        ));

        // then
        assertThat(ex)
            .isInstanceOf(NullPointerException.class)
            .hasMessage(scenario.expectedNpeMessage);
    }

    @Test
    public void spanStarted_should_do_nothing() {
        // when
        listener.spanStarted(spanMock);

        // then
        verifyZeroInteractions(jreTracerMock, spanMock);
    }

    @Test
    public void spanSampled_should_do_nothing() {
        // when
        listener.spanSampled(spanMock);

        // then
        verifyZeroInteractions(jreTracerMock, spanMock);
    }

    @DataProvider(value = {
        "true   |   true",
        "false  |   true",
        "true   |   false",
        "false  |   false",
    }, splitBy = "\\|")
    @Test
    public void spanCompleted_should_create_and_complete_matching_opentracing_span(
        boolean spanHasParent,
        boolean useUnsanitizedIds
    ) {
        // given
        String traceId = (useUnsanitizedIds) ? "some_unsanitized_trace_id" : TraceAndSpanIdGenerator.generateId();
        String spanId = (useUnsanitizedIds) ? "some_unsanitized_span_id" : TraceAndSpanIdGenerator.generateId();
        String parentId = null;
        if (spanHasParent) {
            parentId = (useUnsanitizedIds) ? "some_unsanitized_trace_id" : TraceAndSpanIdGenerator.generateId();
        }

        String spanName = "someSpanName-" + UUID.randomUUID().toString();

        Span wtSpan = Span
            .newBuilder(spanName, Span.SpanPurpose.CLIENT)
            .withTraceId(traceId)
            .withSpanId(spanId)
            .withParentSpanId(parentId)
            .withTag("fooTag", "foo-value-" + UUID.randomUUID().toString())
            .withTag("barTag", "bar-value-" + UUID.randomUUID().toString())
            .withTimestampedAnnotation(TimestampedAnnotation.forCurrentTime("fooAnnotation"))
            .withTimestampedAnnotation(TimestampedAnnotation.forEpochMicros(1234, "barAnnotation"))
            .build();

        wtSpan.close(); // spanCompleted is only ever called on completed spans.

        String expectedSanitizedTraceId = listener.sanitizeIdIfNecessary(traceId, true);
        String expectedSanitizedSpanId = listener.sanitizeIdIfNecessary(spanId, false);
        String expectedSanitizedParentId = (spanHasParent) ? listener.sanitizeIdIfNecessary(parentId, false) : null;

        long expectedLsTraceId = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(
            expectedSanitizedTraceId
        );
        long expectedLsSpanId = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(
            expectedSanitizedSpanId
        );
        long expectedLsParentId = 0;
        if (spanHasParent) {
            expectedLsParentId = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(
                expectedSanitizedParentId
            );
        }

        long expectedStopTimeMicros =
            wtSpan.getSpanStartTimeEpochMicros() + TimeUnit.NANOSECONDS.toMicros(wtSpan.getDurationNanos());

        Map<String, String> origWtTags = new LinkedHashMap<>(wtSpan.getTags());

        // when
        listener.spanCompleted(wtSpan);

        // then
        verify(jreTracerMock).buildSpan(spanName);
        verify(lsSpanBuilderMock).withStartTimestamp(wtSpan.getSpanStartTimeEpochMicros());
        verify(lsSpanBuilderMock).ignoreActiveSpan();
        verify(lsSpanBuilderMock).withTag("wingtips.span_id", wtSpan.getSpanId());
        verify(lsSpanBuilderMock).withTag("wingtips.trace_id", wtSpan.getTraceId());
        verify(lsSpanBuilderMock).withTag("wingtips.parent_id", String.valueOf(wtSpan.getParentSpanId()));
        verify(lsSpanBuilderMock).withTag("span.type", wtSpan.getSpanPurpose().name());
        verify(lsSpanBuilderMock).withTraceIdAndSpanId(expectedLsTraceId, expectedLsSpanId);

        if (spanHasParent) {
            assertThat(expectedLsParentId).isNotEqualTo(0L);

            ArgumentCaptor<SpanContext> parentSpanContextArgumentCaptor = ArgumentCaptor.forClass(SpanContext.class);

            verify(lsSpanBuilderMock).asChildOf(parentSpanContextArgumentCaptor.capture());

            SpanContext parentSpanContext = parentSpanContextArgumentCaptor.getValue();
            assertThat(parentSpanContext).isInstanceOf(com.lightstep.tracer.shared.SpanContext.class);
            com.lightstep.tracer.shared.SpanContext lsParentSpanContext =
                (com.lightstep.tracer.shared.SpanContext)parentSpanContext;
            assertThat(lsParentSpanContext.getTraceId()).isEqualTo(expectedLsTraceId);
            assertThat(lsParentSpanContext.getSpanId()).isEqualTo(expectedLsParentId);
        }

        verify(lsSpanBuilderMock).start();

        int expectedNumWingtipsTags = 2;
        if (!wtSpan.getSpanId().equals(expectedSanitizedSpanId)) {
            expectedNumWingtipsTags++;
        }
        if (!wtSpan.getTraceId().equals(expectedSanitizedTraceId)) {
            expectedNumWingtipsTags++;
        }
        if (wtSpan.getParentSpanId() != null && !wtSpan.getParentSpanId().equals(expectedSanitizedParentId)) {
            expectedNumWingtipsTags++;
        }

        assertThat(wtSpan.getTags()).hasSize(expectedNumWingtipsTags);
        assertThat(wtSpan.getTimestampedAnnotations()).hasSize(2);

        wtSpan.getTimestampedAnnotations().forEach(
            annot -> verify(otSpanMock).log(annot.getTimestampEpochMicros(), annot.getValue())
        );

        origWtTags.forEach(
            (expectedTagKey, expectedTagValue) -> verify(otSpanMock).setTag(expectedTagKey, expectedTagValue)
        );

        if (!wtSpan.getSpanId().equals(expectedSanitizedSpanId)) {
            verify(otSpanMock).setTag("wingtips.span_id.invalid", true);
            assertThat(wtSpan.getTags().get("sanitized_span_id")).isEqualTo(expectedSanitizedSpanId);
        }
        else {
            verify(otSpanMock, never()).setTag("wingtips.span_id.invalid", true);
            assertThat(wtSpan.getTags().get("sanitized_span_id")).isNull();
        }

        if (!wtSpan.getTraceId().equals(expectedSanitizedTraceId)) {
            verify(otSpanMock).setTag("wingtips.trace_id.invalid", true);
            assertThat(wtSpan.getTags().get("sanitized_trace_id")).isEqualTo(expectedSanitizedTraceId);
        }
        else {
            verify(otSpanMock, never()).setTag("wingtips.trace_id.invalid", true);
            assertThat(wtSpan.getTags().get("sanitized_trace_id")).isNull();
        }

        if (wtSpan.getParentSpanId() != null && !wtSpan.getParentSpanId().equals(expectedSanitizedParentId)) {
            verify(otSpanMock).setTag("wingtips.parent_id.invalid", true);
            assertThat(wtSpan.getTags().get("sanitized_parent_id")).isEqualTo(expectedSanitizedParentId);
        }
        else {
            verify(otSpanMock, never()).setTag("wingtips.parent_id.invalid", true);
            assertThat(wtSpan.getTags().get("sanitized_parent_id")).isNull();
        }

        verify(otSpanMock).finish(expectedStopTimeMicros);
    }

    @Test
    public void spanCompleted_does_not_propagate_unexpected_exception() {
        // given
        doThrow(new RuntimeException("intentional test exception")).when(jreTracerMock).buildSpan(anyString());
        Span completedSpan = Span.newBuilder("fooSpan", Span.SpanPurpose.CLIENT).build();
        completedSpan.close();

        // when
        Throwable ex = catchThrowable(() -> listener.spanCompleted(completedSpan));

        // then
        verify(jreTracerMock).buildSpan(anyString());
        assertThat(ex).isNull();
    }

    private enum IdSanitizationScenario {
        NOT_HEX_STRING(
            "notahexstring",
            "15afe1fe8fe52ed3cd3efd908c45653b", // SHA 256 hash of original ID, take 32 chars
            "15afe1fe8fe52ed3"                  // SHA 256 hash of original ID, take 16 chars
        ),
        UPPERCASE_HEX_STRING(
            "DAA63E253DAB8990",
            "daa63e253dab8990", // Original ID lowercased
            "daa63e253dab8990"
        ),
        MIXED_CASE_HEX_STRING(
            "dAA63e253DaB8990",
            "daa63e253dab8990",  // Original ID lowercased
            "daa63e253dab8990"
        ),
        LONGER_THAN_16_CHARS_BUT_SHORTER_THAN_32_RAW_LONG(
            "1234567890123456789",
            "112210f47de98115", // Original ID converted to a Long, then converted to lowerhex.
            "112210f47de98115"
        ),
        LONGER_THAN_16_CHARS_BUT_SHORTER_THAN_32_NOT_A_RAW_LONG(
            "daa63e253dab8990123",
            "a3cd6fe61020c277ac30d83d18e670e5", // SHA 256 hash of original ID, take 32 chars
            "a3cd6fe61020c277"                  // SHA 256 hash of original ID, take 16 chars
        ),
        ID_IS_A_UUID(
            "98943667-2429-4019-910e-0f219a43949b",
            "9894366724294019910e0f219a43949b", // Original ID with dashes removed
            "9f26a747d46a3dd5"                  // SHA 256 hash of original ID, take 16 chars
        ),
        ID_IS_A_UPPERCASE_UUID(
            "98943667-2429-4019-910E-0F219A43949B",
            "9894366724294019910e0f219a43949b", // Original ID with dashes removed, and lowercase
            "0bc091a479bad367"                  // SHA 256 hash of original ID, take 16 chars
        ),
        LONGER_THAN_32_CHARS_NOT_UUID(
            "daa63e253dab8990daa63e253dab89901234",
            "47035ab8524e9e68d14de5bf4117e555", // SHA 256 hash of original ID, take 32 chars
            "47035ab8524e9e68"                  // SHA 256 hash of original ID, take 16 chars
        ),
        EXACTLY_32_CHARS_LOWERHEX(
            "1a2b3c4d5e6f1a2b3c4d5e6f1a2b3c4d",
            "1a2b3c4d5e6f1a2b3c4d5e6f1a2b3c4d", // No need to sanitize - this is valid for trace ID
            "fe31730df9f857ee"                  // SHA 256 hash of original ID, take 16 chars
        ),
        GREATER_THAN_JAVA_MAX_LONG(
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE).toString(),
            "c5c29af0c2b1ba23907ca40686689919", // SHA 256 hash of original ID, take 32 chars
            "c5c29af0c2b1ba23"                  // SHA 256 hash of original ID, take 16 chars
        ),
        LESS_THAN_JAVA_MIN_LONG(
            BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE).toString(),
            "a11b7b6918ba4e84f8e3ab75638e7325", // SHA 256 hash of original ID, take 32 chars
            "a11b7b6918ba4e84"                  // SHA 256 hash of original ID, take 16 chars
        );

        public final String originalId;
        public final String expectedSanitizedResultForTraceId; // Trace ID -> 128 bit allowed
        public final String expectedSanitizedResultForSpanIdOrParentSpanId; // Non-Trace ID -> 128 bit not allowed

        IdSanitizationScenario(
            String originalId,
            String expectedSanitizedResultForTraceId,
            String expectedSanitizedResultForSpanIdOrParentSpanId
        ) {
            this.originalId = originalId;
            this.expectedSanitizedResultForTraceId = expectedSanitizedResultForTraceId;
            this.expectedSanitizedResultForSpanIdOrParentSpanId = expectedSanitizedResultForSpanIdOrParentSpanId;
        }
    }

    @DataProvider
    @SuppressWarnings("unused")
    public static List<List<IdSanitizationScenario>> idSanitizationScenarios() {
        return Arrays.stream(IdSanitizationScenario.values())
                     .map(Collections::singletonList)
                     .collect(Collectors.toList());
    }

    @UseDataProvider("idSanitizationScenarios")
    @Test
    public void sanitizeIdIfNecessary_sanitizes_ids_as_expected(
        IdSanitizationScenario scenario
    ) {
        // when
        String result128bit = listener.sanitizeIdIfNecessary(scenario.originalId, true);
        String result64bit = listener.sanitizeIdIfNecessary(scenario.originalId, false);

        // then
        assertThat(result128bit).isEqualTo(scenario.expectedSanitizedResultForTraceId);
        assertThat(result64bit).isEqualTo(scenario.expectedSanitizedResultForSpanIdOrParentSpanId);
    }

    // Verify the method at a per-character level to catch all the branching logic.
    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void isHex_works_as_expected(boolean allowUppercase) {
        for (char c = Character.MIN_VALUE; c < Character.MAX_VALUE; c++) {
            // given
            boolean isHexDigit = (c >= '0') && (c <= '9');
            boolean isHexLowercase = (c >= 'a') && (c <= 'f');
            boolean isHexUppercase = (c >= 'A') && (c <= 'F');

            boolean expectedResult = isHexDigit || isHexLowercase || (allowUppercase && isHexUppercase);

            // when
            boolean result = listener.isHex(String.valueOf(c), allowUppercase);

            // then
            assertThat(result)
                .withFailMessage("Did not get expected result for char with int value " + (int)c +
                                 ". Expected result: " + expectedResult)
                .isEqualTo(expectedResult);
        }
    }

    // Verify the attemptToConvertToLong method with various scenarios to catch branching logic and corner cases.
    @DataProvider(value = {
        "0                      |   true",
        "1                      |   true",
        "-1                     |   true",
        "42                     |   true",
        "-42                    |   true",
        "2147483648             |   true",  // Greater than max int (but still in long range).
        "-2147483649            |   true",  // Less than min int (but still in long range).
        "9199999999999999999    |   true",  // Same num digits as max long, but digit before the end is less than same digit in max long.
        "-9199999999999999999   |   true",  // Same num digits as min long, but digit before the end is less than same digit in min long.
        "9223372036854775807    |   true",  // Exactly max long.
        "-9223372036854775808   |   true",  // Exactly min long.
        "9223372036854775808    |   false", // 1 bigger than max long.
        "-9223372036854775809   |   false", // 1 less than min long.
        "9300000000000000000    |   false", // Same num digits as max long, but digit before the end is greater than than same digit in max long.
        "-9300000000000000000   |   false", // Same num digits as min long, but digit before the end is greater than than same digit in min long.
        "10000000000000000000   |   false", // Too many digits (positive).
        "-10000000000000000000  |   false", // Too many digits (negative).
        "42blue42               |   false", // Contains non-digits.
        "42f                    |   false", // Contains non-digits.
        "4-2                    |   false", // Contains dash in a spot other than the beginning.
        "42-                    |   false", // Contains dash in a spot other than the beginning.
        "null                   |   false"  // Null can't be converted to a long.
    }, splitBy = "\\|")
    @Test
    public void attemptToConvertToLong_works_as_expected(String longAsString, boolean expectValidLongResult) {
        // given
        Long expectedResult = (expectValidLongResult) ? Long.parseLong(longAsString) : null;

        // when
        Long result = listener.attemptToConvertToLong(longAsString);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void attemptToSanitizeAsUuid_returns_null_if_passed_nul() {
        // expect
        assertThat(listener.attemptToSanitizeAsUuid(null)).isNull();
    }

    @Test
    public void stripDashesAndConvertToLowercase_returns_null_if_passed_nul() {
        // expect
        assertThat(listener.stripDashesAndConvertToLowercase(null)).isNull();
    }
}