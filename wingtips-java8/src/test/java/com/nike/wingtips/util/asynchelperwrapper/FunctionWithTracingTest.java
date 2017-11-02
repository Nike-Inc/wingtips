package com.nike.wingtips.util.asynchelperwrapper;

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

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static com.nike.wingtips.util.asynchelperwrapper.FunctionWithTracing.withTracing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link FunctionWithTracing}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class FunctionWithTracingTest {

    private Function functionMock;
    List<Deque<Span>> currentSpanStackWhenFunctionWasCalled;
    List<Map<String, String>> currentMdcInfoWhenFunctionWasCalled;
    boolean throwExceptionDuringCall;
    Object inObj;
    Object outObj;

    @Before
    public void beforeMethod() {
        functionMock = mock(Function.class);

        inObj = new Object();
        outObj = new Object();
        throwExceptionDuringCall = false;
        currentSpanStackWhenFunctionWasCalled = new ArrayList<>();
        currentMdcInfoWhenFunctionWasCalled = new ArrayList<>();
        doAnswer(invocation -> {
            currentSpanStackWhenFunctionWasCalled.add(Tracer.getInstance().getCurrentSpanStackCopy());
            currentMdcInfoWhenFunctionWasCalled.add(MDC.getCopyOfContextMap());
            if (throwExceptionDuringCall)
                throw new RuntimeException("kaboom");
            return outObj;
        }).when(functionMock).apply(inObj);

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
        FunctionWithTracing instance = (useStaticFactory)
                                         ? withTracing(functionMock)
                                         : new FunctionWithTracing(functionMock);

        // then
        assertThat(instance.origFunction).isSameAs(functionMock);
        assertThat(instance.spanStackForExecution).isEqualTo(spanStackMock);
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
        FunctionWithTracing instance = (useStaticFactory)
                                         ? withTracing(functionMock, Pair.of(spanStackMock, mdcInfoMock))
                                         : new FunctionWithTracing(functionMock, Pair.of(spanStackMock, mdcInfoMock)
                                         );

        // then
        assertThat(instance.origFunction).isSameAs(functionMock);
        assertThat(instance.spanStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void pair_constructor_sets_fields_as_expected_when_pair_is_null(boolean useStaticFactory) {
        // when
        FunctionWithTracing instance = (useStaticFactory)
                                         ? withTracing(functionMock, (Pair)null)
                                         : new FunctionWithTracing(functionMock, (Pair)null);

        // then
        assertThat(instance.origFunction).isSameAs(functionMock);
        assertThat(instance.spanStackForExecution).isNull();
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
        FunctionWithTracing instance = (useStaticFactory)
                                         ? withTracing(functionMock, spanStackMock, mdcInfoMock)
                                         : new FunctionWithTracing(functionMock, spanStackMock, mdcInfoMock);

        // then
        assertThat(instance.origFunction).isSameAs(functionMock);
        assertThat(instance.spanStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @Test
    public void constructors_throw_exception_if_passed_null_operator() {
        // given
        final Deque<Span> spanStackMock = mock(Deque.class);
        final Map<String, String> mdcInfoMock = mock(Map.class);

        // expect
        assertThat(catchThrowable(() -> new FunctionWithTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new FunctionWithTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new FunctionWithTracing(null, spanStackMock, mdcInfoMock)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null, spanStackMock, mdcInfoMock)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void apply_handles_tracing_and_mdc_info_as_expected(boolean throwException) {
        // given
        throwExceptionDuringCall = throwException;
        Tracer.getInstance().startRequestWithRootSpan("foo");
        Deque<Span> spanStack = Tracer.getInstance().getCurrentSpanStackCopy();
        Map<String, String> mdcInfo = MDC.getCopyOfContextMap();
        FunctionWithTracing instance = new FunctionWithTracing(
            functionMock, spanStack, mdcInfo
        );
        resetTracing();
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        // when
        Throwable ex = null;
        Object result = null;
        try {
            result = instance.apply(inObj);
        }
        catch(Throwable t) {
            ex = t;
        }

        // then
        verify(functionMock).apply(inObj);
        if (throwException) {
            assertThat(ex).isNotNull();
            assertThat(result).isNull();
        }
        else {
            assertThat(ex).isNull();
            assertThat(result).isSameAs(outObj);
        }

        assertThat(currentSpanStackWhenFunctionWasCalled.get(0)).isEqualTo(spanStack);
        assertThat(currentMdcInfoWhenFunctionWasCalled.get(0)).isEqualTo(mdcInfo);

        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

}