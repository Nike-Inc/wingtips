package com.nike.wingtips.lightstep;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.TimestampedAnnotation;
import com.nike.wingtips.TraceAndSpanIdGenerator;

import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.SpanBuilder;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

        // when
        listener.spanCompleted(wtSpan);

        // then
        verify(jreTracerMock).buildSpan(spanName);
        verify(lsSpanBuilderMock).withStartTimestamp(wtSpan.getSpanStartTimeEpochMicros());
        verify(lsSpanBuilderMock).ignoreActiveSpan();
        verify(lsSpanBuilderMock).withTag("lightstep.trace_id", expectedLsTraceId);
        verify(lsSpanBuilderMock).withTag("lightstep.span_id", expectedLsSpanId);
        verify(lsSpanBuilderMock).withTag("wingtips.span_id", wtSpan.getSpanId());
        verify(lsSpanBuilderMock).withTag("wingtips.trace_id", wtSpan.getTraceId());

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

            verify(lsSpanBuilderMock).withTag("lightstep.parent_id", expectedLsParentId);
            verify(lsSpanBuilderMock).withTag("wingtips.parent_id", wtSpan.getParentSpanId());
        }
        else {
            verify(lsSpanBuilderMock).withTag("lightstep.parent_id", "null");
            verify(lsSpanBuilderMock).withTag("wingtips.parent_id", "null");
        }

        verify(lsSpanBuilderMock).start();

        assertThat(wtSpan.getTags()).hasSize(2);
        assertThat(wtSpan.getTimestampedAnnotations()).hasSize(2);

        wtSpan.getTimestampedAnnotations().forEach(
            annot -> verify(otSpanMock).log(annot.getTimestampEpochMicros(), annot.getValue())
        );

        verify(otSpanMock).setTag("span.type", wtSpan.getSpanPurpose().name());

        wtSpan.getTags().forEach(
            (expectedTagKey, expectedTagValue) -> verify(otSpanMock).setTag(expectedTagKey, expectedTagValue)
        );

        if (!wtSpan.getSpanId().equals(expectedSanitizedSpanId)) {
            verify(otSpanMock).setTag("wingtips.span_id.invalid", true);
        }
        else {
            verify(otSpanMock, never()).setTag("wingtips.span_id.invalid", true);
        }

        if (!wtSpan.getTraceId().equals(expectedSanitizedTraceId)) {
            verify(otSpanMock).setTag("wingtips.trace_id.invalid", true);
        }
        else {
            verify(otSpanMock, never()).setTag("wingtips.trace_id.invalid", true);
        }

        if (wtSpan.getParentSpanId() != null && !wtSpan.getParentSpanId().equals(expectedSanitizedParentId)) {
            verify(otSpanMock).setTag("wingtips.parent_id.invalid", true);
        }
        else {
            verify(otSpanMock, never()).setTag("wingtips.parent_id.invalid", true);
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
}