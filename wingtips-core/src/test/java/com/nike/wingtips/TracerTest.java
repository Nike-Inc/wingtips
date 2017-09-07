package com.nike.wingtips;

import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.sampling.RootSpanSamplingStrategy;
import com.nike.wingtips.sampling.SampleAllTheThingsStrategy;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link Tracer}
 */
@RunWith(DataProviderRunner.class)
public class TracerTest {

    private void resetTracer() {
        Tracer.getInstance().completeRequestSpan();
        Tracer.getInstance().setRootSpanSamplingStrategy(new SampleAllTheThingsStrategy());
        for (SpanLifecycleListener listener : new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners())) {
            Tracer.getInstance().removeSpanLifecycleListener(listener);
        }
        Tracer.getInstance().setSpanLoggingRepresentation(Tracer.SpanLoggingRepresentation.JSON);
    }

    @Before
    public void beforeMethod() {
        resetTracer();
    }

    @After
    public void afterMethod() {
        resetTracer();
    }

    private ThreadLocal<Deque<Span>> getSpanStackThreadLocal() {
        try {
            Field stackThreadLocalField = Tracer.class.getDeclaredField("currentSpanStackThreadLocal");
            stackThreadLocalField.setAccessible(true);
            //noinspection unchecked
            return ((ThreadLocal<Deque<Span>>) stackThreadLocalField.get(Tracer.getInstance()));
        }
        catch (Exception ex) {
            throw new IllegalStateException("Couldn't do necessary reflection on Tracer", ex);
        }
    }

    private Deque<Span> getSpanStackFromTracer() {
        return getSpanStackThreadLocal().get();
    }

    private int getSpanStackSize() {
        Deque<Span> stack = getSpanStackFromTracer();
        if (stack == null)
            return 0;

        return stack.size();
    }

    @Test
    public void startRequestWithRootSpan_should_start_valid_root_span_without_parent() {
        // given: no span started
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);

        // when: Tracer.startRequestWithRootSpan(String) is called to start a span without a parent
        long beforeNanoTime = System.nanoTime();
        long beforeEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Tracer.getInstance().startRequestWithRootSpan("noparent");
        long afterNanoTime = System.nanoTime();
        long afterEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then: a new span is started that has no parent but is otherwise valid, and the MDC is updated
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span span = Tracer.getInstance().getCurrentSpan();
        assertThat(span).isNotNull();
        assertThat(span.getSpanName()).isEqualTo("noparent");
        assertThat(span.getParentSpanId()).isNull();
        assertThat(span.getSpanStartTimeEpochMicros()).isBetween(beforeEpochMicros, afterEpochMicros);
        assertThat(span.getSpanStartTimeNanos()).isBetween(beforeNanoTime, afterNanoTime);
        assertThat(span.isCompleted()).isFalse();
        assertThat(span.getDurationNanos()).isNull();
        assertThat(span.getTraceId()).isNotNull();
        assertThat(span.getSpanId()).isNotNull();
        assertThat(span.isSampleable()).isTrue();
        assertThat(span.getUserId()).isNull();
        assertThat(span.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(span.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(span.toJSON());
    }

    @Test
    public void startRequestWithRootSpan_should_start_valid_root_span_without_parent_with_userid() {
        // given: no span started
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);

        // when: Tracer.startRequestWithRootSpan(String) is called to start a span without a parent
        long beforeNanoTime = System.nanoTime();
        long beforeEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Tracer.getInstance().startRequestWithRootSpan("noparent", "testUserId");
        long afterNanoTime = System.nanoTime();
        long afterEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then: a new span is started that has no parent but is otherwise valid, it has the expected user ID, and the MDC is updated
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span span = Tracer.getInstance().getCurrentSpan();
        assertThat(span).isNotNull();
        assertThat(span.getSpanName()).isEqualTo("noparent");
        assertThat(span.getParentSpanId()).isNull();
        assertThat(span.getSpanStartTimeEpochMicros()).isBetween(beforeEpochMicros, afterEpochMicros);
        assertThat(span.getSpanStartTimeNanos()).isBetween(beforeNanoTime, afterNanoTime);
        assertThat(span.isCompleted()).isFalse();
        assertThat(span.getDurationNanos()).isNull();
        assertThat(span.getTraceId()).isNotNull();
        assertThat(span.getSpanId()).isNotNull();
        assertThat(span.isSampleable()).isTrue();
        assertThat(span.getUserId()).isEqualTo("testUserId");
        assertThat(span.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(span.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(span.toJSON());
    }

    @Test
    public void startRequestWithRootSpan_wipes_out_any_existing_spans_on_the_stack() {
        // given: Tracer already has some Spans on the stack
        Tracer.getInstance().startRequestWithRootSpan("span1");
        Tracer.getInstance().startSubSpan("span2", SpanPurpose.LOCAL_ONLY);
        assertThat(getSpanStackSize()).isEqualTo(2);

        // when: Tracer.startRequestWithRootSpan(String) is called to start a span without a parent
        Tracer.getInstance().startRequestWithRootSpan("noparent");

        // then: a new span is started for it, and the other spans on the stack are removed
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span span = Tracer.getInstance().getCurrentSpan();
        assertThat(span).isNotNull();
        assertThat(span.getSpanName()).isEqualTo("noparent");
    }

    @Test
    public void startRequestWithChildSpan_should_start_valid_child_span_with_parent() {
        // given: no span started and a parent span exists
        Span parentSpan = Span.generateRootSpanForNewTrace("parentspan", SpanPurpose.LOCAL_ONLY).build();
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);

        // when: Tracer.startRequestWithChildSpan(Span, String) is called to start a span with a parent
        long beforeNanoTime = System.nanoTime();
        long beforeEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Tracer.getInstance().startRequestWithChildSpan(parentSpan, "childspan");
        long afterNanoTime = System.nanoTime();
        long afterEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then: a new span is started that has the given parent and is otherwise valid, and the MDC is updated
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span span = Tracer.getInstance().getCurrentSpan();
        assertThat(span).isNotNull();
        assertThat(span.getSpanName()).isEqualTo("childspan");
        assertThat(span.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
        assertThat(span.getSpanStartTimeEpochMicros()).isBetween(beforeEpochMicros, afterEpochMicros);
        assertThat(span.getSpanStartTimeNanos()).isBetween(beforeNanoTime, afterNanoTime);
        assertThat(span.isCompleted()).isFalse();
        assertThat(span.getDurationNanos()).isNull();
        assertThat(span.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(span.getSpanId()).isNotNull();
        assertThat(span.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(span.isSampleable()).isEqualTo(parentSpan.isSampleable());
        assertThat(span.getUserId()).isNull();
        assertThat(span.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(span.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(span.toJSON());
    }

    @Test
    public void startRequestWithChildSpan_should_start_valid_child_span_with_parent_and_user_id() {
        // given: no span started and a parent span exists
        Span parentSpan = Span.generateRootSpanForNewTrace("parentspan", SpanPurpose.LOCAL_ONLY)
                              .withUserId("testUserId")
                              .build();
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);

        // when: Tracer.startRequestWithChildSpan(Span, String) is called to start a span with a parent
        long beforeNanoTime = System.nanoTime();
        long beforeEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Tracer.getInstance().startRequestWithChildSpan(parentSpan, "childspan");
        long afterNanoTime = System.nanoTime();
        long afterEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then: a new span is started that has the given parent and is otherwise valid, has the expected user ID, and the MDC is updated
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span span = Tracer.getInstance().getCurrentSpan();
        assertThat(span).isNotNull();
        assertThat(span.getSpanName()).isEqualTo("childspan");
        assertThat(span.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
        assertThat(span.getSpanStartTimeEpochMicros()).isBetween(beforeEpochMicros, afterEpochMicros);
        assertThat(span.getSpanStartTimeNanos()).isBetween(beforeNanoTime, afterNanoTime);
        assertThat(span.isCompleted()).isFalse();
        assertThat(span.getDurationNanos()).isNull();
        assertThat(span.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(span.getSpanId()).isNotNull();
        assertThat(span.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(span.isSampleable()).isEqualTo(parentSpan.isSampleable());
        assertThat(span.getUserId()).isEqualTo("testUserId");
        assertThat(span.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(span.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(span.toJSON());
    }

    @Test
    public void startRequestWithChildSpan_wipes_out_any_existing_spans_on_the_stack() {
        // given: Tracer already has some Spans on the stack, and we have a parent span we're going to use
        Tracer.getInstance().startRequestWithRootSpan("span1");
        Tracer.getInstance().startSubSpan("span2", SpanPurpose.LOCAL_ONLY);
        assertThat(getSpanStackSize()).isEqualTo(2);

        Span newSpanParent = Span.generateRootSpanForNewTrace("parentspan", SpanPurpose.CLIENT).build();

        // when: Tracer.startRequestWithChildSpan(Span, String) is called to start a span with a parent
        Tracer.getInstance().startRequestWithChildSpan(newSpanParent, "childspan");

        // then: a new span is started that has the given parent, and the other spans on the stack are removed
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span span = Tracer.getInstance().getCurrentSpan();
        assertThat(span).isNotNull();
        assertThat(span.getSpanName()).isEqualTo("childspan");
    }

    @Test(expected = IllegalArgumentException.class)
    public void startRequestWithChildSpan_throws_IllegalArgumentException_if_passed_null_parent() {
        // expect
        Tracer.getInstance().startRequestWithChildSpan(null, "somechildspan");
        fail("Expected IllegalArgumentException but no exception was thrown");
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void startRequestWithSpanInfo_should_start_valid_span_with_given_data(SpanPurpose spanPurpose) {
        // given
        String traceId = UUID.randomUUID().toString();
        String parentSpanId = UUID.randomUUID().toString();
        String spanName = UUID.randomUUID().toString();
        boolean sampleable = false;
        String userId = UUID.randomUUID().toString();
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();

        // when
        long beforeNanoTime = System.nanoTime();
        long beforeEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        @SuppressWarnings("ConstantConditions")
        Span span = Tracer.getInstance().startRequestWithSpanInfo(traceId, parentSpanId, spanName, sampleable, userId, spanPurpose);
        long afterNanoTime = System.nanoTime();
        long afterEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then
        assertThat(Tracer.getInstance().getCurrentSpan()).isEqualTo(span);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(span.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(span.toJSON());
        assertThat(span.getTraceId()).isEqualTo(traceId);
        assertThat(span.getParentSpanId()).isEqualTo(parentSpanId);
        assertThat(span.getSpanId()).isNotEmpty();
        assertThat(span.getSpanName()).isEqualTo(spanName);
        //noinspection ConstantConditions
        assertThat(span.isSampleable()).isEqualTo(sampleable);
        assertThat(span.getUserId()).isEqualTo(userId);
        assertThat(span.getSpanPurpose()).isEqualTo(spanPurpose);
        assertThat(span.getSpanStartTimeEpochMicros()).isBetween(beforeEpochMicros, afterEpochMicros);
        assertThat(span.getSpanStartTimeNanos()).isBetween(beforeNanoTime, afterNanoTime);
        assertThat(span.isCompleted()).isFalse();
        assertThat(span.getDurationNanos()).isNull();
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void startSubSpan_should_start_valid_sub_span(SpanPurpose spanPurpose) {
        // given: an already-started span
        assertThat(getSpanStackSize()).isEqualTo(0);
        Tracer.getInstance().startRequestWithRootSpan("firstspan");
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span firstSpan = Tracer.getInstance().getCurrentSpan();

        // when: Tracer.startSubSpan(String) is called to start a subspan
        long beforeNanoTime = System.nanoTime();
        long beforeEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Tracer.getInstance().startSubSpan("subspan", spanPurpose);
        long afterNanoTime = System.nanoTime();
        long afterEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then: a new subspan is started that uses the first span as its parent, and the MDC is updated
        assertThat(getSpanStackSize()).isEqualTo(2);
        Span subspan = Tracer.getInstance().getCurrentSpan();
        assertThat(subspan).isNotNull();
        assertThat(subspan.getSpanName()).isEqualTo("subspan");
        assertThat(subspan.getParentSpanId()).isEqualTo(firstSpan.getSpanId());
        assertThat(subspan.getSpanStartTimeEpochMicros()).isBetween(beforeEpochMicros, afterEpochMicros);
        assertThat(subspan.getSpanStartTimeNanos()).isBetween(beforeNanoTime, afterNanoTime);
        assertThat(subspan.isCompleted()).isFalse();
        assertThat(subspan.getDurationNanos()).isNull();
        assertThat(subspan.getTraceId()).isNotNull();
        assertThat(subspan.getSpanId()).isNotNull();
        assertThat(subspan.isSampleable()).isEqualTo(firstSpan.isSampleable());
        assertThat(subspan.getSpanPurpose()).isEqualTo(spanPurpose);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void startSubSpan_should_function_like_startRequestWithRootSpan_when_there_is_no_parent_span(SpanPurpose spanPurpose) {
        // given: no span started
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);

        // when: Tracer.startSubSpan(String) is called to start a subspan
        long beforeNanoTime = System.nanoTime();
        long beforeEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        Tracer.getInstance().startSubSpan("subspan", spanPurpose);
        long afterNanoTime = System.nanoTime();
        long afterEpochMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

        // then: a new span is started even though there was no parent, and the MDC is updated.
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span subspan = Tracer.getInstance().getCurrentSpan();
        assertThat(subspan).isNotNull();
        assertThat(subspan.getSpanName()).isEqualTo("subspan");
        assertThat(subspan.getParentSpanId()).isNull();
        assertThat(subspan.getSpanStartTimeEpochMicros()).isBetween(beforeEpochMicros, afterEpochMicros);
        assertThat(subspan.getSpanStartTimeNanos()).isBetween(beforeNanoTime, afterNanoTime);
        assertThat(subspan.isCompleted()).isFalse();
        assertThat(subspan.getDurationNanos()).isNull();
        assertThat(subspan.getTraceId()).isNotNull();
        assertThat(subspan.getSpanId()).isNotNull();
        assertThat(subspan.isSampleable()).isTrue();
        assertThat(subspan.getSpanPurpose()).isEqualTo(spanPurpose);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());
    }

    @Test(expected = IllegalArgumentException.class)
    public void starting_a_request_with_null_span_name_should_throw_IllegalArgumentException() {
        // expect
        Tracer.getInstance().startRequestWithRootSpan(null);
        fail("Expected IllegalArgumentException but no exception was thrown");
    }

    @DataProvider
    public static Object[][] spanStackDataProvider() {
        Span rootSpan = Span.generateRootSpanForNewTrace("rootspan", SpanPurpose.SERVER).build();
        Span childSpan = rootSpan.generateChildSpan("childSpan", SpanPurpose.CLIENT);

        return new Object[][] {
                { null },
                { new LinkedList<>() },
                { new LinkedList<>(Collections.singleton(rootSpan)) },
                { new LinkedList<>(Arrays.asList(rootSpan, childSpan)) }
        };
    }

    @Test
    @UseDataProvider("spanStackDataProvider")
    public void starting_a_request_should_reset_span_stack_no_matter_what_the_span_stack_already_looked_like(Deque<Span> stackToUse) {
        // given
        getSpanStackThreadLocal().set(stackToUse);
        assertThat(getSpanStackFromTracer()).isSameAs(stackToUse);
        String newRequestSpanName = UUID.randomUUID().toString();

        // when
        Span newRequestSpan = Tracer.getInstance().startRequestWithRootSpan(newRequestSpanName);

        // then
        assertThat(getSpanStackFromTracer()).isNotSameAs(stackToUse);
        assertThat(getSpanStackSize()).isEqualTo(1);
        assertThat(Tracer.getInstance().getCurrentSpan()).isEqualTo(newRequestSpan);
        assertThat(Tracer.getInstance().getCurrentSpan().getSpanName()).isEqualTo(newRequestSpanName);
    }

    @Test
    public void configureMDC_should_set_span_values_on_MDC() throws Exception {
        // given
        Span span = Span.newBuilder("test-span", SpanPurpose.LOCAL_ONLY).withParentSpanId("3").build();
        String expected = span.toJSON();

        // when
        Tracer.configureMDC(span);

        // then
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(expected);
    }

    @Test
    public void unconfigureMDC_should_unset_span_values_on_MDC() throws Exception {
        // given
        Span span = Span.newBuilder("test-span", SpanPurpose.LOCAL_ONLY).withParentSpanId("3").build();
        Tracer.configureMDC(span);

        // when
        Tracer.unconfigureMDC();

        // then
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
    }

    @Test
    public void getCurrentSpan_should_return_current_span() throws Exception {
        // given
        Tracer tracer = Tracer.getInstance();
        tracer.startRequestWithRootSpan("test-span");

        // when
        Span span = tracer.getCurrentSpan();

        // then
        assertThat(span).isNotNull();
        assertThat(span.getSpanName()).isEqualTo("test-span");

    }

    @Test
    public void getCurrentSpan_should_retrieve_from_MDC_if_stack_is_null_and_MDC_is_populated() {
        // given: no span started (stack is null) and MDC is populated
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);
        assertThat(getSpanStackFromTracer()).isNull();

        Span mdcSpan = Span.generateRootSpanForNewTrace("mdcSpan", SpanPurpose.LOCAL_ONLY).build();
        MDC.put(Tracer.TRACE_ID_MDC_KEY, mdcSpan.getTraceId());
        MDC.put(Tracer.SPAN_JSON_MDC_KEY, mdcSpan.toJSON());
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(mdcSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(mdcSpan.toJSON());

        // when: Tracer.getCurrentSpan() is called
        Span spanFromTracer = Tracer.getInstance().getCurrentSpan();

        // then: the returned span matches the one we stuffed into the MDC
        assertThat(spanFromTracer).isNotNull();
        assertThat(spanFromTracer.getTraceId()).isEqualTo(mdcSpan.getTraceId());
        assertThat(spanFromTracer.getSpanId()).isEqualTo(mdcSpan.getSpanId());
        assertThat(spanFromTracer.getSpanName()).isEqualTo("mdcSpan");
        assertThat(spanFromTracer.toJSON()).isEqualTo(mdcSpan.toJSON());
    }

    @Test
    public void getCurrentSpan_should_retrieve_from_MDC_if_stack_is_empty_and_MDC_is_populated() {
        // given: no span started (stack is not null but empty) and MDC is populated
        getSpanStackThreadLocal().set(new LinkedList<Span>());
        assertThat(getSpanStackFromTracer()).isNotNull();
        assertThat(getSpanStackSize()).isEqualTo(0);
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();

        Span span = Span.generateRootSpanForNewTrace("mdcSpan", SpanPurpose.LOCAL_ONLY).build();
        MDC.put(Tracer.TRACE_ID_MDC_KEY, span.getTraceId());
        MDC.put(Tracer.SPAN_JSON_MDC_KEY, span.toJSON());
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(span.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(span.toJSON());

        // when: Tracer.getCurrentSpan() is called
        Span spanFromTracer = Tracer.getInstance().getCurrentSpan();

        // then: the returned span matches the one we stuffed into the MDC
        assertThat(spanFromTracer).isNotNull();
        assertThat(spanFromTracer.getTraceId()).isEqualTo(span.getTraceId());
        assertThat(spanFromTracer.getSpanId()).isEqualTo(span.getSpanId());
        assertThat(spanFromTracer.getSpanName()).isEqualTo("mdcSpan");
        assertThat(spanFromTracer.toJSON()).isEqualTo(span.toJSON());
    }

    @Test
    public void setCurrentSpanFromMdcIfNotAlreadySet_should_do_nothing_if_stack_is_not_empty() {
        // given: span stack has something on it and the MDC is setup with a different span for some reason
        Tracer.getInstance().startSubSpan("somespan", SpanPurpose.LOCAL_ONLY);
        assertThat(Tracer.getInstance().getCurrentSpan()).isNotNull();
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span spanFromTracer = Tracer.getInstance().getCurrentSpan();

        Span span = Span.generateRootSpanForNewTrace("mdcSpan", SpanPurpose.LOCAL_ONLY).build();
        MDC.put(Tracer.TRACE_ID_MDC_KEY, span.getTraceId());
        MDC.put(Tracer.SPAN_JSON_MDC_KEY, span.toJSON());
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(span.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(span.toJSON());

        // when: Tracer.setCurrentSpanFromMdcIfNotAlreadySet() is called
        Tracer.getInstance().setCurrentSpanFromMdcIfNotAlreadySet();

        // then: it does nothing because the stack was not null
        assertThat(getSpanStackSize()).isEqualTo(1);
        assertThat(Tracer.getInstance().getCurrentSpan().getSpanName()).isEqualTo("somespan");
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(spanFromTracer);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(span.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(span.toJSON());
        assertThat(span.getTraceId()).isNotEqualTo(spanFromTracer.getTraceId());
    }

    private void verifyDurationBetweenLowerAndUpperBounds(Span span, long beforeCompletionCallNanoTime, long afterCompletionCallNanoTime) {
        long durationLowerBound = beforeCompletionCallNanoTime - span.getSpanStartTimeNanos();
        long durationUpperBound = afterCompletionCallNanoTime - span.getSpanStartTimeNanos();
        assertThat(span.getDurationNanos()).isNotNull();
        assertThat(span.getDurationNanos()).isBetween(durationLowerBound, durationUpperBound);
    }

    @Test
    public void completeRequestSpan_should_complete_the_span() {
        // given: an already-started span
        Tracer.getInstance().startRequestWithRootSpan("somespan");
        Span span = Tracer.getInstance().getCurrentSpan();
        assertThat(span.getSpanName()).isEqualTo("somespan");
        assertThat(getSpanStackSize()).isEqualTo(1);
        assertThat(span.isCompleted()).isFalse();

        // when: completeRequestSpan() is called
        long beforeNanoTime = System.nanoTime();
        Tracer.getInstance().completeRequestSpan();
        long afterNanoTime = System.nanoTime();

        // then: the span should be completed, the stack emptied, and the MDC unconfigured
        assertThat(span.isCompleted()).isTrue();
        verifyDurationBetweenLowerAndUpperBounds(span, beforeNanoTime, afterNanoTime);
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
    }

    @Test
    public void completeRequestSpan_should_complete_all_spans_and_empty_stack_when_there_is_more_than_one_span_on_the_stack() {
        // given: an already-started span AND some subspans
        Tracer.getInstance().startRequestWithRootSpan("parentspan");
        Span parentSpan = Tracer.getInstance().getCurrentSpan();
        assertThat(parentSpan.getSpanName()).isEqualTo("parentspan");
        Tracer.getInstance().startSubSpan("subspan1", SpanPurpose.LOCAL_ONLY);
        Span subspan1 = Tracer.getInstance().getCurrentSpan();
        assertThat(subspan1.getSpanName()).isEqualTo("subspan1");
        Tracer.getInstance().startSubSpan("subspan2", SpanPurpose.LOCAL_ONLY);
        Span subspan2 = Tracer.getInstance().getCurrentSpan();
        assertThat(subspan2.getSpanName()).isEqualTo("subspan2");
        assertThat(getSpanStackSize()).isEqualTo(3);
        assertThat(parentSpan.isCompleted()).isFalse();
        assertThat(subspan1.isCompleted()).isFalse();
        assertThat(subspan2.isCompleted()).isFalse();

        // when: completeRequestSpan() is called
        long beforeNanoTime = System.nanoTime();
        Tracer.getInstance().completeRequestSpan();
        long afterNanoTime = System.nanoTime();

        // then: all spans should be completed, the stack emptied, and the MDC unconfigured
        assertThat(parentSpan.isCompleted()).isTrue();
        assertThat(subspan1.isCompleted()).isTrue();
        assertThat(subspan2.isCompleted()).isTrue();
        verifyDurationBetweenLowerAndUpperBounds(parentSpan, beforeNanoTime, afterNanoTime);
        verifyDurationBetweenLowerAndUpperBounds(subspan1, beforeNanoTime, afterNanoTime);
        verifyDurationBetweenLowerAndUpperBounds(subspan2, beforeNanoTime, afterNanoTime);
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
    }

    @Test
    public void completeRequestSpan_should_do_nothing_if_there_is_no_span_to_complete() {
        // given: no span started
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);

        // when: completeRequestSpan() is called
        Tracer.getInstance().completeRequestSpan();

        // then: nothing should be done
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);
    }

    @Test
    public void completeSubSpan_should_complete_the_sub_span() {
        // given: an already-started span AND a subspan
        Tracer.getInstance().startRequestWithRootSpan("parentspan");
        Span parentSpan = Tracer.getInstance().getCurrentSpan();
        assertThat(parentSpan.getSpanName()).isEqualTo("parentspan");
        Tracer.getInstance().startSubSpan("subspan", SpanPurpose.LOCAL_ONLY);
        Span subspan = Tracer.getInstance().getCurrentSpan();
        assertThat(subspan.getSpanName()).isEqualTo("subspan");
        assertThat(getSpanStackSize()).isEqualTo(2);
        assertThat(parentSpan.isCompleted()).isFalse();
        assertThat(subspan.isCompleted()).isFalse();

        // when: completeSubSpan() is called
        long beforeNanoTime = System.nanoTime();
        Tracer.getInstance().completeSubSpan();
        long afterNanoTime = System.nanoTime();

        // then: only the subspan should be completed, the stack decremented by 1, the current span set to the parent, and the MDC configured to point to the parent
        assertThat(parentSpan.isCompleted()).isFalse();
        assertThat(subspan.isCompleted()).isTrue();
        verifyDurationBetweenLowerAndUpperBounds(subspan, beforeNanoTime, afterNanoTime);
        assertThat(Tracer.getInstance().getCurrentSpan()).isNotNull();
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(parentSpan);
        assertThat(getSpanStackSize()).isEqualTo(1);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(parentSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(parentSpan.toJSON());
    }

    @Test
    public void completeSubSpan_should_do_nothing_if_the_span_stack_is_null() {
        // given: a null span stack
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);
        assertThat(getSpanStackFromTracer()).isNull();

        // when: completeSubSpan() is called
        Tracer.getInstance().completeSubSpan();

        // then: nothing should be done because the stack is null
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);
        assertThat(getSpanStackFromTracer()).isNull();
    }

    @Test
    public void completeSubSpan_should_do_nothing_if_there_is_only_one_span_on_the_stack() {
        // given: a single span on the stack
        Tracer.getInstance().startRequestWithRootSpan("somespan");
        assertThat(Tracer.getInstance().getCurrentSpan()).isNotNull();
        assertThat(getSpanStackSize()).isEqualTo(1);
        Span span = Tracer.getInstance().getCurrentSpan();
        assertThat(span).isNotNull();
        assertThat(span.getSpanName()).isEqualTo("somespan");

        // when: completeSubSpan() is called
        Tracer.getInstance().completeSubSpan();

        // then: nothing should be done because the stack only has one thing on it and completeSubSpan() requires at least two spans
        assertThat(span.isCompleted()).isFalse();
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(span);
        assertThat(getSpanStackSize()).isEqualTo(1);
    }

    @Test
    public void starting_request_span_should_configure_MDC_and_completing_it_should_unset_MDC() throws Exception {
        // given
        Tracer tracer = Tracer.getInstance();

        // when
        tracer.startRequestWithRootSpan("test-span");

        // then
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNotNull();

        // and when
        tracer.completeRequestSpan();

        // then
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
    }

    @Test
    public void setRootSpanSamplingStrategy_should_set_the_strategy() {
        // given: known unique sampling strategy
        RootSpanSamplingStrategy strategy = new RootSpanSamplingStrategy() {
            @Override
            public boolean isNextRootSpanSampleable() {
                return false;
            }
        };

        // when: setRootSpanSamplingStrategy() is called
        Tracer.getInstance().setRootSpanSamplingStrategy(strategy);

        // then: that exact strategy instance is used
        assertThat(Whitebox.getInternalState(Tracer.getInstance(), "rootSpanSamplingStrategy")).isSameAs(strategy);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setRootSpanSamplingStrategy_should_explode_if_passed_null() {
        // expect
        Tracer.getInstance().setRootSpanSamplingStrategy(null);
    }

    @Test
    public void isNextRootSpanSampleable_should_use_strategy() {
        // given: known sampling strategy that is set on the Tracer
        RootSpanSamplingStrategy mockStrategy = mock(RootSpanSamplingStrategy.class);
        Tracer.getInstance().setRootSpanSamplingStrategy(mockStrategy);

        // when: isNextRootSpanSampleable() is called
        Tracer.getInstance().isNextRootSpanSampleable();

        // then: the strategy's isNextRootSpanSampleable() method should have been called
        verify(mockStrategy).isNextRootSpanSampleable();
    }

    @Test
    public void addSpanLifecycleListener_should_work_as_advertised() {
        // given
        SpanLifecycleListener listener = mock(SpanLifecycleListener.class);
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).isEmpty();

        // when
        Tracer.getInstance().addSpanLifecycleListener(listener);

        // then
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).hasSize(1);
        assertThat(Tracer.getInstance().getSpanLifecycleListeners().get(0)).isEqualTo(listener);
    }

    @Test
    public void addSpanLifecycleListener_should_do_nothing_if_passed_null() {
        // given
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).isEmpty();

        // when
        Tracer.getInstance().addSpanLifecycleListener(null);

        // then
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).isEmpty();
    }

    @Test
    public void removeSpanLifecycleListener_should_work_as_advertised() {
        // given
        SpanLifecycleListener listener = mock(SpanLifecycleListener.class);
        Tracer.getInstance().addSpanLifecycleListener(listener);
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).hasSize(1);
        assertThat(Tracer.getInstance().getSpanLifecycleListeners().get(0)).isEqualTo(listener);

        // when
        boolean result = Tracer.getInstance().removeSpanLifecycleListener(listener);

        // then
        assertThat(result).isTrue();
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).isEmpty();
    }

    @Test
    public void removeSpanLifecycleListener_should_return_false_and_do_nothing_if_passed_null() {
        // given
        SpanLifecycleListener listener = mock(SpanLifecycleListener.class);
        Tracer.getInstance().addSpanLifecycleListener(listener);
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).hasSize(1);
        assertThat(Tracer.getInstance().getSpanLifecycleListeners().get(0)).isEqualTo(listener);

        // when
        boolean result = Tracer.getInstance().removeSpanLifecycleListener(null);

        // then
        assertThat(result).isFalse();
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).hasSize(1);
        assertThat(Tracer.getInstance().getSpanLifecycleListeners().get(0)).isEqualTo(listener);
    }

    @Test
    public void getSpanLifecycleListeners_returns_unmodifiable_list() {
        // given
        SpanLifecycleListener listener = mock(SpanLifecycleListener.class);
        Tracer.getInstance().addSpanLifecycleListener(listener);

        // when
        List<SpanLifecycleListener> returnedList = Tracer.getInstance().getSpanLifecycleListeners();

        // then
        Exception caughtEx = null;
        try {
            returnedList.add(mock(SpanLifecycleListener.class));
        }
        catch(Exception ex) {
            caughtEx = ex;
        }

        assertThat(caughtEx).isNotNull();
        assertThat(caughtEx).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void spanLifecycleListener_spanStarted_is_called_when_new_request_span_is_started() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);

        // when
        Span span = tracer.startRequestWithRootSpan("newspan");

        // then
        verify(listener1).spanStarted(span);
        verify(listener1, times(0)).spanCompleted(span);
        verify(listener2).spanStarted(span);
        verify(listener2, times(0)).spanCompleted(span);
    }

    @Test
    public void spanLifecycleListener_spanSampled_is_called_when_new_request_span_is_started_if_span_is_sampleable() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);

        // when
        Span span = tracer.startRequestWithSpanInfo("t", "p", "n", true, "u", SpanPurpose.LOCAL_ONLY);

        // then
        verify(listener1).spanStarted(span);
        verify(listener1).spanSampled(span);
        verify(listener1, times(0)).spanCompleted(span);
        verify(listener2).spanStarted(span);
        verify(listener2).spanSampled(span);
        verify(listener2, times(0)).spanCompleted(span);

    }

    @Test
    public void spanLifecycleListener_spanSampled_is_not_called_when_new_request_span_is_started_if_span_is_not_sampleable() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);

        // when
        Span span = tracer.startRequestWithSpanInfo("t", "p", "n", false, "u", SpanPurpose.LOCAL_ONLY);

        // then
        verify(listener1).spanStarted(span);
        verify(listener1, times(0)).spanSampled(span);
        verify(listener1, times(0)).spanCompleted(span);
        verify(listener2).spanStarted(span);
        verify(listener2, times(0)).spanSampled(span);
        verify(listener2, times(0)).spanCompleted(span);
    }

    @Test
    public void spanLifecycleListener_spanCompleted_is_called_when_request_span_is_completed() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);
        Span span = tracer.startRequestWithRootSpan("newspan");
        verify(listener1).spanStarted(span);
        verify(listener1, times(0)).spanCompleted(span);
        verify(listener2).spanStarted(span);
        verify(listener2, times(0)).spanCompleted(span);

        // when
        tracer.completeRequestSpan();

        // then
        verify(listener1).spanCompleted(span);
        verify(listener2).spanCompleted(span);
    }

    @Test
    public void spanLifecycleListener_spanCompleted_is_not_called_when_request_span_was_completed_already() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);
        Span span = tracer.startRequestWithRootSpan("newspan");
        span.complete();
        verify(listener1).spanStarted(span);
        verify(listener1, times(0)).spanCompleted(span);
        verify(listener2).spanStarted(span);
        verify(listener2, times(0)).spanCompleted(span);

        // when
        tracer.completeRequestSpan();

        // then
        verify(listener1, never()).spanCompleted(span);
        verify(listener2, never()).spanCompleted(span);
    }

    @Test
    public void spanLifecycleListener_spanStarted_is_called_when_subspan_is_started() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);
        tracer.startRequestWithRootSpan("newspan");

        // when
        Span subspan = tracer.startSubSpan("subspan", SpanPurpose.LOCAL_ONLY);

        // then
        verify(listener1).spanStarted(subspan);
        verify(listener1, times(0)).spanCompleted(subspan);
        verify(listener2).spanStarted(subspan);
        verify(listener2, times(0)).spanCompleted(subspan);
    }

    @Test
    public void spanLifecycleListener_spanSampled_is_called_when_subspan_is_started_if_subspan_is_sampleable() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);
        tracer.startRequestWithSpanInfo("t", "p", "n", true, "u", SpanPurpose.LOCAL_ONLY);

        // when
        Span subspan = tracer.startSubSpan("subspan", SpanPurpose.LOCAL_ONLY);

        // then
        verify(listener1).spanStarted(subspan);
        verify(listener1).spanSampled(subspan);
        verify(listener1, times(0)).spanCompleted(subspan);
        verify(listener2).spanStarted(subspan);
        verify(listener2).spanSampled(subspan);
        verify(listener2, times(0)).spanCompleted(subspan);

    }

    @Test
    public void spanLifecycleListener_spanSampled_is_not_called_when_subspan_is_started_if_subspan_is_not_sampleable() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);
        tracer.startRequestWithSpanInfo("t", "p", "n", false, "u", SpanPurpose.LOCAL_ONLY);

        // when
        Span subspan = tracer.startSubSpan("subspan", SpanPurpose.LOCAL_ONLY);

        // then
        verify(listener1).spanStarted(subspan);
        verify(listener1, times(0)).spanSampled(subspan);
        verify(listener1, times(0)).spanCompleted(subspan);
        verify(listener2).spanStarted(subspan);
        verify(listener2, times(0)).spanSampled(subspan);
        verify(listener2, times(0)).spanCompleted(subspan);
    }

    @Test
    public void spanLifecycleListener_spanCompleted_is_called_when_subspan_is_completed() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);
        tracer.startRequestWithRootSpan("newspan");
        Span subspan = tracer.startSubSpan("subspan", SpanPurpose.LOCAL_ONLY);
        verify(listener1).spanStarted(subspan);
        verify(listener1, times(0)).spanCompleted(subspan);
        verify(listener2).spanStarted(subspan);
        verify(listener2, times(0)).spanCompleted(subspan);

        // when
        tracer.completeSubSpan();

        // then
        verify(listener1).spanCompleted(subspan);
        verify(listener2).spanCompleted(subspan);
    }

    @Test
    public void spanLifecycleListener_spanCompleted_is_not_called_if_subspan_was_already_completed() {
        // given
        SpanLifecycleListener listener1 = mock(SpanLifecycleListener.class);
        SpanLifecycleListener listener2 = mock(SpanLifecycleListener.class);
        Tracer tracer = Tracer.getInstance();
        tracer.addSpanLifecycleListener(listener1);
        tracer.addSpanLifecycleListener(listener2);
        tracer.startRequestWithRootSpan("newspan");
        Span subspan = tracer.startSubSpan("subspan", SpanPurpose.LOCAL_ONLY);
        subspan.complete();
        verify(listener1).spanStarted(subspan);
        verify(listener1, times(0)).spanCompleted(subspan);
        verify(listener2).spanStarted(subspan);
        verify(listener2, times(0)).spanCompleted(subspan);

        // when
        tracer.completeSubSpan();

        // then
        verify(listener1, never()).spanCompleted(subspan);
        verify(listener2, never()).spanCompleted(subspan);
    }

    @Test
    public void unregisterFromThread_should_work_as_advertised() {
        // given
        Tracer tracer = Tracer.getInstance();
        Span parentSpan = tracer.startRequestWithRootSpan("foo");
        Span subspan = tracer.startSubSpan("bar", SpanPurpose.LOCAL_ONLY);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());
        assertThat(getSpanStackSize()).isEqualTo(2);

        // when
        Deque<Span> unregisteredStack = tracer.unregisterFromThread();

        // then
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(getSpanStackSize()).isEqualTo(0);

        assertThat(unregisteredStack).hasSize(2);
        assertThat(unregisteredStack.pop()).isEqualTo(subspan);
        assertThat(unregisteredStack.pop()).isEqualTo(parentSpan);
    }

    @Test
    public void registerWithThread_should_work_as_advertised() {
        // given
        Tracer tracer = Tracer.getInstance();

        Deque<Span> newSpanStack = new LinkedList<>();
        Span parentSpan = Span.newBuilder("foo", SpanPurpose.LOCAL_ONLY).build();
        Span subspan = Span.newBuilder("bar", SpanPurpose.LOCAL_ONLY).build();
        newSpanStack.push(parentSpan);
        newSpanStack.push(subspan);

        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();

        // when
        tracer.registerWithThread(newSpanStack);

        // then
        // our stack was registered, so subspan should be current
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());
        assertThat(tracer.getCurrentSpan()).isEqualTo(subspan);

        // a *copy* of the stack we passed in should have been registered, and modifying the original stack should not affect Tracer's stack
        Deque<Span> spanStack = getSpanStackThreadLocal().get();
        assertThat(Tracer.getInstance().containsSameSpansInSameOrder(spanStack, newSpanStack)).isTrue();
        assertThat(spanStack).isNotSameAs(newSpanStack);

        newSpanStack.push(subspan.generateChildSpan("subsub", SpanPurpose.LOCAL_ONLY));
        assertThat(newSpanStack).hasSize(3);
        assertThat(spanStack).hasSize(2);
    }

    @Test
    public void registerWithThread_should_work_as_advertised_if_existing_stack_is_empty() {
        // given
        getSpanStackThreadLocal().set(new LinkedList<Span>());
        Tracer tracer = Tracer.getInstance();

        Deque<Span> newSpanStack = new LinkedList<>();
        Span parentSpan = Span.newBuilder("foo", SpanPurpose.LOCAL_ONLY).build();
        Span subspan = Span.newBuilder("bar", SpanPurpose.LOCAL_ONLY).build();
        newSpanStack.push(parentSpan);
        newSpanStack.push(subspan);

        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();

        // when
        tracer.registerWithThread(newSpanStack);

        // then
        // our stack was registered, so subspan should be current
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());
        assertThat(tracer.getCurrentSpan()).isEqualTo(subspan);

        // a *copy* of the stack we passed in should have been registered, and modifying the original stack should not affect Tracer's stack
        Deque<Span> spanStack = getSpanStackThreadLocal().get();
        assertThat(Tracer.getInstance().containsSameSpansInSameOrder(spanStack, newSpanStack)).isTrue();
        assertThat(spanStack).isNotSameAs(newSpanStack);

        newSpanStack.push(subspan.generateChildSpan("subsub", SpanPurpose.LOCAL_ONLY));
        assertThat(newSpanStack).hasSize(3);
        assertThat(spanStack).hasSize(2);
    }

    @Test
    public void registerWithThread_should_override_existing_stuff() {
        // given
        Tracer tracer = Tracer.getInstance();
        Span existingSpan = tracer.startRequestWithRootSpan("old");

        Deque<Span> newSpanStack = new LinkedList<>();
        Span parentSpan = Span.newBuilder("foo", SpanPurpose.LOCAL_ONLY).build();
        Span subspan = Span.newBuilder("bar", SpanPurpose.LOCAL_ONLY).build();
        newSpanStack.push(parentSpan);
        newSpanStack.push(subspan);

        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(existingSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(existingSpan.toJSON());

        // when
        tracer.registerWithThread(newSpanStack);

        // then
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());

        Deque<Span> spanStack = getSpanStackThreadLocal().get();
        assertThat(spanStack).isEqualTo(newSpanStack);
    }

    @Test
    public void registerWithThread_should_do_nothing_if_same_stack_is_passed_in() {
        // given
        Tracer tracer = Tracer.getInstance();
        tracer.startRequestWithRootSpan("foo");
        Span subspan = tracer.startSubSpan("bar", SpanPurpose.LOCAL_ONLY);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());

        // when
        Deque<Span> spanStack = getSpanStackThreadLocal().get();
        tracer.registerWithThread(spanStack);

        // then
        assertThat(getSpanStackThreadLocal().get()).isEqualTo(spanStack);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());
    }

    @Test
    public void registerWithThread_should_do_nothing_if_copy_of_same_stack_is_passed_in() {
        // given
        Tracer tracer = Tracer.getInstance();
        tracer.startRequestWithRootSpan("foo");
        Span subspan = tracer.startSubSpan("bar", SpanPurpose.LOCAL_ONLY);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());

        // when
        Deque<Span> spanStack = getSpanStackThreadLocal().get();
        tracer.registerWithThread(new LinkedList<>(spanStack));

        // then
        assertThat(getSpanStackThreadLocal().get()).isEqualTo(spanStack);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());
    }

    @Test
    public void registerWithThread_should_reset_everything_if_passed_null() {
        // given
        Tracer tracer = Tracer.getInstance();
        tracer.startRequestWithRootSpan("foo");
        Span subspan = tracer.startSubSpan("bar", SpanPurpose.LOCAL_ONLY);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());

        // when
        tracer.registerWithThread(null);

        // then
        assertThat(getSpanStackThreadLocal().get()).isNull();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
    }

    @Test
    public void registerWithThread_should_reset_everything_if_passed_empty_instance() {
        // given
        Tracer tracer = Tracer.getInstance();
        tracer.startRequestWithRootSpan("foo");
        Span subspan = tracer.startSubSpan("bar", SpanPurpose.LOCAL_ONLY);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());

        // when
        Deque<Span> emptyStack = new LinkedList<>();
        tracer.registerWithThread(emptyStack);

        // then
        assertThat(getSpanStackThreadLocal().get()).isEqualTo(emptyStack);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
    }

    @DataProvider
    public static Object[][] dataProviderForContainsSameSpansInSameOrder() {
        Span spanA = Span.newBuilder("span-A", SpanPurpose.LOCAL_ONLY).withTraceId("A").build();
        Span spanB = Span.newBuilder("span-B", SpanPurpose.SERVER).withTraceId("B").build();
        Span spanC = Span.newBuilder("span-C", SpanPurpose.CLIENT).withTraceId("C").build();

        Span otherSpanA = Span.newBuilder(spanA).build();
        Span otherSpanB = Span.newBuilder(spanB).build();
        Span otherSpanC = Span.newBuilder(spanC).build();

        Deque<Span> abStack = new LinkedList<>(Arrays.asList(spanA, spanB));
        Deque<Span> abcStack = new LinkedList<>(Arrays.asList(spanA, spanB, spanC));
        Deque<Span> acbStack = new LinkedList<>(Arrays.asList(spanA, spanC, spanB));
        Deque<Span> a_null_c_Stack = new LinkedList<>(Arrays.asList(spanA, null, spanC));
        Deque<Span> abcStackCopy = new LinkedList<>(abcStack);
        Deque<Span> abcStackWithDuplicateSpans = new LinkedList<>(Arrays.asList(otherSpanA, otherSpanB, otherSpanC));

        return new Object[][] {
                { abcStack, abcStackCopy, true, "stack copy test" },
                { abStack, abStack, true, "same stack instance test" },
                { null, null, true, "null stacks test" },
                { abcStack, abcStackWithDuplicateSpans, true, "duplicate spans but not same instance test" },
                { abStack, null, false, "other stack null test" },
                { null, abStack, false, "first stack null test" },
                { abStack, abcStack, false, "not same stack size test" },
                { abcStack, acbStack, false, "not same order test" },
                { abcStack, a_null_c_Stack, false, "null span in other stack test" },
                { a_null_c_Stack, abcStack, false, "null span in first stack test" }
        };
    }

    @Test
    @UseDataProvider("dataProviderForContainsSameSpansInSameOrder")
    public void containsSameSpansInSameOrder_should_work_as_expected_for_known_data(Deque<Span> stack, Deque<Span> otherStack, boolean expected, String testId) {
        assertThat(Tracer.getInstance().containsSameSpansInSameOrder(stack, otherStack)).isEqualTo(expected).withFailMessage("Test failed: " + testId);
    }

    @Test
    public void should_support_async_workflow_with_unregister_and_register() {
        Tracer tracer = Tracer.getInstance();

        // Start some spans for request A
        Span reqAParentSpan = tracer.startRequestWithRootSpan("req_A_foo");
        Span reqASubSpan = tracer.startSubSpan("req_A_bar", SpanPurpose.LOCAL_ONLY);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(reqASubSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(reqASubSpan.toJSON());
        assertThat(tracer.getCurrentSpan()).isEqualTo(reqASubSpan);

        // Unregister in preparation for request B
        Deque<Span> reqAStack = tracer.unregisterFromThread();

        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(tracer.getCurrentSpan()).isNull();

        // Start some spans for request B
        Span reqBParentSpan = tracer.startRequestWithRootSpan("req_B_foo");
        Span reqBSubSpan = tracer.startSubSpan("req_B_bar", SpanPurpose.LOCAL_ONLY);

        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(reqBSubSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(reqBSubSpan.toJSON());
        assertThat(tracer.getCurrentSpan()).isEqualTo(reqBSubSpan);

        // Unregister in preparation for going back to request A
        Deque<Span> reqBStack = tracer.unregisterFromThread();

        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(tracer.getCurrentSpan()).isNull();

        // Re-register request A's stack
        tracer.registerWithThread(reqAStack);

        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(reqASubSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(reqASubSpan.toJSON());
        assertThat(tracer.getCurrentSpan()).isEqualTo(reqASubSpan);

        // Complete request A
        tracer.completeSubSpan();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(reqAParentSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(reqAParentSpan.toJSON());
        assertThat(tracer.getCurrentSpan()).isEqualTo(reqAParentSpan);

        tracer.completeRequestSpan();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(tracer.getCurrentSpan()).isNull();

        // Re-register request B's stack
        tracer.registerWithThread(reqBStack);

        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(reqBSubSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(reqBSubSpan.toJSON());
        assertThat(tracer.getCurrentSpan()).isEqualTo(reqBSubSpan);

        // Complete request B
        tracer.completeSubSpan();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(reqBParentSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(reqBParentSpan.toJSON());
        assertThat(tracer.getCurrentSpan()).isEqualTo(reqBParentSpan);

        tracer.completeRequestSpan();
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isNull();
        assertThat(tracer.getCurrentSpan()).isNull();
    }

    @Test
    public void getCurrentSpanStackCopy_returns_null_if_original_is_null() {
        // given
        Tracer tracer = Tracer.getInstance();
        assertThat(getSpanStackSize()).isEqualTo(0);

        // expect
        assertThat(tracer.getCurrentSpanStackCopy()).isNull();
    }

    @Test
    public void getCurrentSpanStackCopy_returns_copy_of_stack_not_original() {
        // given
        Tracer tracer = Tracer.getInstance();
        Span parentSpan = tracer.startRequestWithRootSpan("foo");
        Span subspan = tracer.startSubSpan("bar", SpanPurpose.LOCAL_ONLY);

        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());

        // when
        Deque<Span> stack = tracer.getCurrentSpanStackCopy();

        // Verify that the returned stack contains both spans
        assertThat(stack).hasSize(2);
        assertThat(stack.peek()).isEqualTo(subspan);
        assertThat(stack.peekLast()).isEqualTo(parentSpan);

        // Clear the returned stack
        stack.clear();
        assertThat(stack.isEmpty()).isTrue();

        // then
        // Now verify that tracer still points to a stack that contains both spans. This proves that getCurrentSpanStackCopy() returned a copy, not the original
        assertThat(tracer.getCurrentSpan()).isEqualTo(subspan);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(subspan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(subspan.toJSON());
        tracer.completeSubSpan();
        assertThat(tracer.getCurrentSpan()).isEqualTo(parentSpan);
        assertThat(MDC.get(Tracer.TRACE_ID_MDC_KEY)).isEqualTo(parentSpan.getTraceId());
        assertThat(MDC.get(Tracer.SPAN_JSON_MDC_KEY)).isEqualTo(parentSpan.toJSON());
    }

    @Test
    public void getCurrentSpanStackSize_works_as_expected() {
        // given
        Tracer tracer = Tracer.getInstance();

        {
            // when - null stack
            tracer.unregisterFromThread();

            // then
            assertThat(tracer.getCurrentSpanStackSize()).isEqualTo(0);
        }

        {
            // and when - empty stack
            tracer.registerWithThread(new LinkedList<Span>());

            // then
            assertThat(tracer.getCurrentSpanStackSize()).isEqualTo(0);
        }

        {
            // and when - force-register non-empty stack
            Deque<Span> nonEmptyStack = new LinkedList<>(Arrays.asList(
                mock(Span.class), mock(Span.class), mock(Span.class)
            ));
            tracer.registerWithThread(nonEmptyStack);

            // then
            assertThat(tracer.getCurrentSpanStackSize()).isEqualTo(nonEmptyStack.size());
        }

        {
            // and when - start with single root span
            tracer.unregisterFromThread();
            tracer.startRequestWithRootSpan("foo");

            // then
            assertThat(tracer.getCurrentSpanStackSize()).isEqualTo(1);
        }

        {
            // and when - add a subspan
            tracer.startSubSpan("bar", SpanPurpose.LOCAL_ONLY);

            // then
            assertThat(tracer.getCurrentSpanStackSize()).isEqualTo(2);
        }

        {
            // and when - complete the subspan
            tracer.completeSubSpan();

            // then
            assertThat(tracer.getCurrentSpanStackSize()).isEqualTo(1);
        }

        {
            // and when - complete the overall request span
            tracer.completeRequestSpan();

            // then
            assertThat(tracer.getCurrentSpanStackSize()).isEqualTo(0);
        }
    }

    @DataProvider(value = {
        "JSON",
        "KEY_VALUE"
    }, splitBy = "\\|")
    @Test
    public void verify_span_serialization_methods(Tracer.SpanLoggingRepresentation serializationOption) {
        // given
        Span span = Span.generateRootSpanForNewTrace(UUID.randomUUID().toString(), SpanPurpose.LOCAL_ONLY).build();
        String expectedOutput;
        switch(serializationOption) {
            case JSON:
                expectedOutput = span.toJSON();
                break;
            case KEY_VALUE:
                expectedOutput = span.toKeyValueString();
                break;
            default:
                throw new IllegalArgumentException("Unhandled option: " + serializationOption);
        }
        Tracer.getInstance().setSpanLoggingRepresentation(serializationOption);

        // then
        assertThat(Tracer.getInstance().getSpanLoggingRepresentation()).isEqualTo(serializationOption);

        // and when
        String serializedString = Tracer.getInstance().serializeSpanToDesiredStringRepresentation(span);

        // then
        assertThat(serializedString).isEqualTo(expectedOutput);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setSpanLoggingRepresentation_blows_up_if_spanLoggingRepresentation_is_null() {
        // expect
        Tracer.getInstance().setSpanLoggingRepresentation(null);
    }

    @Test
    public void make_code_coverage_happy() {
        // Some code coverage tools force you to exercise valueOf() (for example) or you get uncovered lines.
        for (Tracer.SpanLoggingRepresentation option : Tracer.SpanLoggingRepresentation.values()) {
            assertThat(Tracer.SpanLoggingRepresentation.valueOf(option.name())).isEqualTo(option);
        }
    }

}
