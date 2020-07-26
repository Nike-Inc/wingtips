package com.nike.wingtips.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link TracingState}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class TracingStateTest {

    private Deque<Span> spanStackMock;
    private Map<String, String> mdcInfoMock;
    private TracingState tracingState;

    @Before
    public void beforeMethod() {
        spanStackMock = mock(Deque.class);
        mdcInfoMock = mock(Map.class);
        tracingState = new TracingState(spanStackMock, mdcInfoMock);

        resetTracing();
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
    }

    @Test
    public void constructor_sets_fields_as_expected() {
        // when
        TracingState tracingState = new TracingState(spanStackMock, mdcInfoMock);

        // then
        assertThat(tracingState.spanStack).isSameAs(spanStackMock);
        assertThat(tracingState.mdcInfo).isSameAs(mdcInfoMock);
    }

    @Test
    public void pair_methods_work_as_expected() {
        // expect
        assertThat(tracingState.getLeft()).isSameAs(spanStackMock);
        assertThat(tracingState.getKey()).isSameAs(spanStackMock);
        assertThat(tracingState.getRight()).isSameAs(mdcInfoMock);
        assertThat(tracingState.getValue()).isSameAs(mdcInfoMock);
    }

    @Test
    public void setValue_throws_UnsupportedOperationException() {
        // when
        Throwable ex = catchThrowable(new ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                tracingState.setValue(mock(Map.class));
            }
        });

        // then
        assertThat(ex).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void getCurrentThreadTracingState_works_as_expected() {
        // given
        Tracer tracer = Tracer.getInstance();
        tracer.startRequestWithRootSpan("request-" + UUID.randomUUID().toString());
        Deque<Span> currentSpanStack = tracer.getCurrentSpanStackCopy();
        Map<String, String> currentMdcInfo = MDC.getCopyOfContextMap();

        assertThat(currentSpanStack.size()).isGreaterThanOrEqualTo(1);
        assertThat(currentMdcInfo).isNotEmpty();

        // when
        TracingState currentTracingState = TracingState.getCurrentThreadTracingState();

        // then
        assertThat(currentTracingState.spanStack).isEqualTo(currentSpanStack);
        assertThat(currentTracingState.mdcInfo).isEqualTo(currentMdcInfo);
    }

    @Test
    public void getActiveSpan_works_as_expected() {
        // given
        Deque<Span> stackMock = mock(Deque.class);
        Span spanMock = mock(Span.class);
        doReturn(spanMock).when(stackMock).peek();

        TracingState tc = new TracingState(stackMock, Collections.emptyMap());

        // when
        Span result = tc.getActiveSpan();

        // then
        assertThat(result).isSameAs(spanMock);
        verify(stackMock).peek();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void getActiveSpan_returns_null_if_stack_is_null_or_empty(boolean stackIsNull) {
        // given
        Deque<Span> stack = (stackIsNull) ? null : new ArrayDeque<>();

        TracingState tc = new TracingState(stack, Collections.emptyMap());

        // when
        Span result = tc.getActiveSpan();

        // then
        assertThat(result).isNull();
    }
}
