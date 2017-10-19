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
import org.springframework.util.concurrent.SuccessCallback;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nike.wingtips.spring.util.asynchelperwrapper.SuccessCallbackWithTracing.withTracing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link SuccessCallbackWithTracing}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class SuccessCallbackWithTracingTest {

    private SuccessCallback successCallbackMock;
    List<Deque<Span>> currentSpanStackWhenSuccessCallbackWasCalled;
    List<Map<String, String>> currentMdcInfoWhenSuccessCallbackWasCalled;
    boolean throwExceptionDuringCall;
    Object inObj;

    @Before
    public void beforeMethod() {
        successCallbackMock = mock(SuccessCallback.class);

        inObj = new Object();
        throwExceptionDuringCall = false;
        currentSpanStackWhenSuccessCallbackWasCalled = new ArrayList<>();
        currentMdcInfoWhenSuccessCallbackWasCalled = new ArrayList<>();
        doAnswer(invocation -> {
            currentSpanStackWhenSuccessCallbackWasCalled.add(Tracer.getInstance().getCurrentSpanStackCopy());
            currentMdcInfoWhenSuccessCallbackWasCalled.add(MDC.getCopyOfContextMap());
            if (throwExceptionDuringCall)
                throw new RuntimeException("kaboom");
            return null;
        }).when(successCallbackMock).onSuccess(inObj);

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
        SuccessCallbackWithTracing instance = (useStaticFactory)
                                       ? withTracing(successCallbackMock)
                                       : new SuccessCallbackWithTracing(successCallbackMock);

        // then
        assertThat(instance.origSuccessCallback).isSameAs(successCallbackMock);
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
        SuccessCallbackWithTracing instance = (useStaticFactory)
                                       ? withTracing(successCallbackMock, Pair.of(spanStackMock, mdcInfoMock))
                                       : new SuccessCallbackWithTracing(successCallbackMock, Pair.of(spanStackMock, mdcInfoMock)
                                       );

        // then
        assertThat(instance.origSuccessCallback).isSameAs(successCallbackMock);
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
        SuccessCallbackWithTracing instance = (useStaticFactory)
                                       ? withTracing(successCallbackMock, (Pair)null)
                                       : new SuccessCallbackWithTracing(successCallbackMock, (Pair)null);

        // then
        assertThat(instance.origSuccessCallback).isSameAs(successCallbackMock);
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
        SuccessCallbackWithTracing instance = (useStaticFactory)
                                       ? withTracing(successCallbackMock, spanStackMock, mdcInfoMock)
                                       : new SuccessCallbackWithTracing(successCallbackMock, spanStackMock, mdcInfoMock);

        // then
        assertThat(instance.origSuccessCallback).isSameAs(successCallbackMock);
        assertThat(instance.distributedTraceStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @Test
    public void constructors_throw_exception_if_passed_null_operator() {
        // given
        final Deque<Span> spanStackMock = mock(Deque.class);
        final Map<String, String> mdcInfoMock = mock(Map.class);

        // expect
        assertThat(catchThrowable(() -> new SuccessCallbackWithTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new SuccessCallbackWithTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new SuccessCallbackWithTracing(null, spanStackMock, mdcInfoMock)))
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
        SuccessCallbackWithTracing instance = new SuccessCallbackWithTracing(
            successCallbackMock, spanStack, mdcInfo
        );
        resetTracing();
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        // when
        Throwable ex = catchThrowable(() -> instance.onSuccess(inObj));

        // then
        verify(successCallbackMock).onSuccess(inObj);
        if (throwException) {
            assertThat(ex).isNotNull();
        }
        else {
            assertThat(ex).isNull();
        }

        assertThat(currentSpanStackWhenSuccessCallbackWasCalled.get(0)).isEqualTo(spanStack);
        assertThat(currentMdcInfoWhenSuccessCallbackWasCalled.get(0)).isEqualTo(mdcInfo);

        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}