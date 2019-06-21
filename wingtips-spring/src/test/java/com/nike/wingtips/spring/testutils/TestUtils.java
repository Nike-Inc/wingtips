package com.nike.wingtips.spring.testutils;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static com.nike.wingtips.TraceHeaders.PARENT_SPAN_ID;
import static com.nike.wingtips.TraceHeaders.SPAN_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_SAMPLED;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Some util methods to ease testing.
 *
 * @author Nic Munroe
 */
public class TestUtils {

    public static void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();

        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            Tracer.getInstance().removeSpanLifecycleListener(listener);
        }
    }

    public static class SpanRecorder implements SpanLifecycleListener {

        public final List<Span> completedSpans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void spanStarted(Span span) { }

        @Override
        public void spanSampled(Span span) { }

        @Override
        public void spanCompleted(Span span) {
            completedSpans.add(span);
        }
    }

    public static Span getExpectedSpanForHeaders(boolean expectTracingInfoPropagation,
                                                 TracingState tracingStateAtTimeOfExecution) {
        Span expectedSpanForHeaders;
        if (tracingStateAtTimeOfExecution.spanStack == null || tracingStateAtTimeOfExecution.spanStack.isEmpty()) {
            expectedSpanForHeaders = null;
        }
        else {
            expectedSpanForHeaders = tracingStateAtTimeOfExecution.spanStack.peek();
        }

        // Sanity check - if we expect propagation then there should have been a span available at the time of
        //      execution.
        if (expectTracingInfoPropagation) {
            assertThat(expectedSpanForHeaders).isNotNull();
        }
        else {
            assertThat(expectedSpanForHeaders).isNull();
        }

        return expectedSpanForHeaders;
    }

    public static void verifyExpectedTracingHeaders(HttpRequest executedRequest, Span expectedSpanForHeaders) {
        HttpHeaders headers = executedRequest.getHeaders();

        List<String> actualTraceIdHeaderVal = headers.get(TRACE_ID);
        List<String> actualSpanIdHeaderVal = headers.get(SPAN_ID);
        List<String> actualSampledHeaderVal = headers.get(TRACE_SAMPLED);
        List<String> actualParentSpanIdHeaderVal = headers.get(PARENT_SPAN_ID);

        if (expectedSpanForHeaders == null) {
            verifyExpectedTracingHeaderValue(actualTraceIdHeaderVal, null);
            verifyExpectedTracingHeaderValue(actualSpanIdHeaderVal, null);
            verifyExpectedTracingHeaderValue(actualSampledHeaderVal, null);
            verifyExpectedTracingHeaderValue(actualParentSpanIdHeaderVal, null);

        }
        else {
            verifyExpectedTracingHeaderValue(actualTraceIdHeaderVal, expectedSpanForHeaders.getTraceId());
            verifyExpectedTracingHeaderValue(actualSpanIdHeaderVal, expectedSpanForHeaders.getSpanId());
            verifyExpectedTracingHeaderValue(
                actualSampledHeaderVal,
                convertSampleableBooleanToExpectedB3Value(expectedSpanForHeaders.isSampleable())
            );
            verifyExpectedTracingHeaderValue(actualParentSpanIdHeaderVal, expectedSpanForHeaders.getParentSpanId());
        }
    }

    public static void verifyExpectedTracingHeaderValue(List<String> actualHeaderValueList, String expectedValue) {
        if (expectedValue == null) {
            assertThat(actualHeaderValueList).isNull();
        }
        else {
            assertThat(actualHeaderValueList).hasSize(1);
            assertThat(actualHeaderValueList).isEqualTo(singletonList(expectedValue));
        }
    }

    // See https://github.com/openzipkin/b3-propagation - we should pass "1" if it's sampleable, "0" if it's not.
    public static String convertSampleableBooleanToExpectedB3Value(boolean sampleable) {
        return (sampleable) ? "1" : "0";
    }

    public static TracingState normalizeTracingState(TracingState orig) {
        Deque<Span> spanStack = orig.spanStack;
        if (spanStack != null && spanStack.isEmpty())
            spanStack = null;

        Map<String, String> mdcInfo = orig.mdcInfo;
        if (mdcInfo != null && mdcInfo.isEmpty())
            mdcInfo = null;

        return new TracingState(spanStack, mdcInfo);
    }

}
