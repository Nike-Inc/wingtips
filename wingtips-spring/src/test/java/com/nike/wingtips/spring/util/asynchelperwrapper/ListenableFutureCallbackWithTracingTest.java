package com.nike.wingtips.spring.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nike.wingtips.spring.util.asynchelperwrapper.ListenableFutureCallbackWithTracing.withTracing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link ListenableFutureCallbackWithTracing}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class ListenableFutureCallbackWithTracingTest {

    private ListenableFutureCallback listenableFutureCallbackMock;
    List<Deque<Span>> currentSpanStackWhenListenableFutureCallbackWasCalled;
    List<Map<String, String>> currentMdcInfoWhenListenableFutureCallbackWasCalled;
    boolean throwExceptionDuringCall;
    Object successInObj;
    Throwable failureInObj;

    @Before
    public void beforeMethod() {
        listenableFutureCallbackMock = mock(ListenableFutureCallback.class);

        successInObj = new Object();
        failureInObj = new Exception("kaboom");
        throwExceptionDuringCall = false;
        currentSpanStackWhenListenableFutureCallbackWasCalled = new ArrayList<>();
        currentMdcInfoWhenListenableFutureCallbackWasCalled = new ArrayList<>();
        doAnswer(invocation -> {
            currentSpanStackWhenListenableFutureCallbackWasCalled.add(Tracer.getInstance().getCurrentSpanStackCopy());
            currentMdcInfoWhenListenableFutureCallbackWasCalled.add(MDC.getCopyOfContextMap());
            if (throwExceptionDuringCall)
                throw new RuntimeException("kaboom");
            return null;
        }).when(listenableFutureCallbackMock).onSuccess(successInObj);
        doAnswer(invocation -> {
            currentSpanStackWhenListenableFutureCallbackWasCalled.add(Tracer.getInstance().getCurrentSpanStackCopy());
            currentMdcInfoWhenListenableFutureCallbackWasCalled.add(MDC.getCopyOfContextMap());
            if (throwExceptionDuringCall)
                throw new RuntimeException("kaboom");
            return null;
        }).when(listenableFutureCallbackMock).onFailure(failureInObj);

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

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void current_thread_info_constructor_sets_fields_as_expected(boolean useStaticFactory) {
        // given
        Tracer.getInstance().startRequestWithRootSpan("request-" + UUID.randomUUID().toString());
        Deque<Span> spanStackMock = Tracer.getInstance().getCurrentSpanStackCopy();
        Map<String, String> mdcInfoMock = MDC.getCopyOfContextMap();

        // when
        ListenableFutureCallbackWithTracing instance = (useStaticFactory)
                                       ? withTracing(listenableFutureCallbackMock)
                                       : new ListenableFutureCallbackWithTracing(listenableFutureCallbackMock);

        // then
        assertThat(instance.origListenableFutureCallback).isSameAs(listenableFutureCallbackMock);
        assertThat(instance.distributedTraceStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @DataProvider(value = {
        "true   |   true    |   true",
        "true   |   false   |   true",
        "false  |   true    |   true",
        "false  |   false   |   true",
        "true   |   true    |   false",
        "true   |   false   |   false",
        "false  |   true    |   false",
        "false  |   false   |   false",
    }, splitBy = "\\|")
    @Test
    public void pair_constructor_sets_fields_as_expected(
        boolean nullSpanStack, boolean nullMdcInfo, boolean useStaticFactory
    ) {
        // given
        Deque<Span> spanStackMock = (nullSpanStack) ? null : mock(Deque.class);
        Map<String, String> mdcInfoMock = (nullMdcInfo) ? null : mock(Map.class);

        // when
        ListenableFutureCallbackWithTracing instance = (useStaticFactory)
                                       ? withTracing(listenableFutureCallbackMock, Pair.of(spanStackMock, mdcInfoMock))
                                       : new ListenableFutureCallbackWithTracing(listenableFutureCallbackMock, Pair.of(spanStackMock, mdcInfoMock)
                                       );

        // then
        assertThat(instance.origListenableFutureCallback).isSameAs(listenableFutureCallbackMock);
        assertThat(instance.distributedTraceStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void pair_constructor_sets_fields_as_expected_when_pair_is_null(boolean useStaticFactory) {
        // when
        ListenableFutureCallbackWithTracing instance = (useStaticFactory)
                                       ? withTracing(listenableFutureCallbackMock, (Pair)null)
                                       : new ListenableFutureCallbackWithTracing(listenableFutureCallbackMock, (Pair)null);

        // then
        assertThat(instance.origListenableFutureCallback).isSameAs(listenableFutureCallbackMock);
        assertThat(instance.distributedTraceStackForExecution).isNull();
        assertThat(instance.mdcContextMapForExecution).isNull();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void kitchen_sink_constructor_sets_fields_as_expected(boolean useStaticFactory) {
        // given
        Deque<Span> spanStackMock = mock(Deque.class);
        Map<String, String> mdcInfoMock = mock(Map.class);

        // when
        ListenableFutureCallbackWithTracing instance = (useStaticFactory)
                                       ? withTracing(listenableFutureCallbackMock, spanStackMock, mdcInfoMock)
                                       : new ListenableFutureCallbackWithTracing(listenableFutureCallbackMock, spanStackMock, mdcInfoMock);

        // then
        assertThat(instance.origListenableFutureCallback).isSameAs(listenableFutureCallbackMock);
        assertThat(instance.distributedTraceStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @Test
    public void constructors_throw_exception_if_passed_null_operator() {
        // given
        final Deque<Span> spanStackMock = mock(Deque.class);
        final Map<String, String> mdcInfoMock = mock(Map.class);

        // expect
        assertThat(catchThrowable(() -> new ListenableFutureCallbackWithTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new ListenableFutureCallbackWithTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new ListenableFutureCallbackWithTracing(null, spanStackMock, mdcInfoMock)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null, spanStackMock, mdcInfoMock)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void onSuccess_handles_tracing_and_mdc_info_as_expected(boolean throwException) {
        // given
        throwExceptionDuringCall = throwException;
        Tracer.getInstance().startRequestWithRootSpan("foo");
        Deque<Span> spanStack = Tracer.getInstance().getCurrentSpanStackCopy();
        Map<String, String> mdcInfo = MDC.getCopyOfContextMap();
        ListenableFutureCallbackWithTracing instance = new ListenableFutureCallbackWithTracing(
            listenableFutureCallbackMock, spanStack, mdcInfo
        );
        resetTracing();
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        // when
        Throwable ex = catchThrowable(() -> instance.onSuccess(successInObj));

        // then
        verify(listenableFutureCallbackMock).onSuccess(successInObj);
        if (throwException) {
            assertThat(ex).isNotNull();
        }
        else {
            assertThat(ex).isNull();
        }

        assertThat(currentSpanStackWhenListenableFutureCallbackWasCalled.get(0)).isEqualTo(spanStack);
        assertThat(currentMdcInfoWhenListenableFutureCallbackWasCalled.get(0)).isEqualTo(mdcInfo);

        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void onFailure_handles_tracing_and_mdc_info_as_expected(boolean throwException) {
        // given
        throwExceptionDuringCall = throwException;
        Tracer.getInstance().startRequestWithRootSpan("foo");
        Deque<Span> spanStack = Tracer.getInstance().getCurrentSpanStackCopy();
        Map<String, String> mdcInfo = MDC.getCopyOfContextMap();
        ListenableFutureCallbackWithTracing instance = new ListenableFutureCallbackWithTracing(
            listenableFutureCallbackMock, spanStack, mdcInfo
        );
        resetTracing();
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        // when
        Throwable ex = catchThrowable(() -> instance.onFailure(failureInObj));

        // then
        verify(listenableFutureCallbackMock).onFailure(failureInObj);
        if (throwException) {
            assertThat(ex).isNotNull();
        }
        else {
            assertThat(ex).isNull();
        }

        assertThat(currentSpanStackWhenListenableFutureCallbackWasCalled.get(0)).isEqualTo(spanStack);
        assertThat(currentMdcInfoWhenListenableFutureCallbackWasCalled.get(0)).isEqualTo(mdcInfo);

        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}