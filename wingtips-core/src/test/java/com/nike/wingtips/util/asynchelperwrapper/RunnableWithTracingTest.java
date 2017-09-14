package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nike.wingtips.util.asynchelperwrapper.RunnableWithTracing.withTracing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link RunnableWithTracing}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class RunnableWithTracingTest {

    private Runnable runnableMock;
    List<Deque<Span>> currentSpanStackWhenRunnableWasCalled;
    List<Map<String, String>> currentMdcInfoWhenRunnableWasCalled;
    boolean throwExceptionDuringCall;

    @Before
    public void beforeMethod() {
        runnableMock = mock(Runnable.class);

        throwExceptionDuringCall = false;
        currentSpanStackWhenRunnableWasCalled = new ArrayList<>();
        currentMdcInfoWhenRunnableWasCalled = new ArrayList<>();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                currentSpanStackWhenRunnableWasCalled.add(Tracer.getInstance().getCurrentSpanStackCopy());
                currentMdcInfoWhenRunnableWasCalled.add(MDC.getCopyOfContextMap());
                if (throwExceptionDuringCall)
                    throw new RuntimeException("kaboom");
                return null;
            }
        }).when(runnableMock).run();

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
        RunnableWithTracing instance = (useStaticFactory)
                                       ? withTracing(runnableMock)
                                       : new RunnableWithTracing(runnableMock);

        // then
        assertThat(instance.origRunnable).isSameAs(runnableMock);
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
        RunnableWithTracing instance = (useStaticFactory)
                                       ? withTracing(runnableMock, Pair.of(spanStackMock, mdcInfoMock))
                                       : new RunnableWithTracing(runnableMock, Pair.of(spanStackMock, mdcInfoMock)
                                       );

        // then
        assertThat(instance.origRunnable).isSameAs(runnableMock);
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
        RunnableWithTracing instance = (useStaticFactory)
                                       ? withTracing(runnableMock, (Pair)null)
                                       : new RunnableWithTracing(runnableMock, (Pair)null);

        // then
        assertThat(instance.origRunnable).isSameAs(runnableMock);
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
        RunnableWithTracing instance = (useStaticFactory)
                                       ? withTracing(runnableMock, spanStackMock, mdcInfoMock)
                                       : new RunnableWithTracing(runnableMock, spanStackMock, mdcInfoMock);

        // then
        assertThat(instance.origRunnable).isSameAs(runnableMock);
        assertThat(instance.distributedTraceStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @Test
    public void constructors_throw_exception_if_passed_null_operator() {
        // given
        final Deque<Span> spanStackMock = mock(Deque.class);
        final Map<String, String> mdcInfoMock = mock(Map.class);

        // expect
        assertThat(catchThrowable(new ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                new RunnableWithTracing(null);
            }
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(new ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                withTracing(null);
            }
        })).isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(new ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                new RunnableWithTracing(null, Pair.of(spanStackMock, mdcInfoMock));
            }
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(new ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                withTracing(null, Pair.of(spanStackMock, mdcInfoMock));
            }
        })).isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(new ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                new RunnableWithTracing(null, spanStackMock, mdcInfoMock);
            }
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(new ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                withTracing(null, spanStackMock, mdcInfoMock);
            }
        })).isInstanceOf(IllegalArgumentException.class);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void run_handles_tracing_and_mdc_info_as_expected(boolean throwException) {
        // given
        throwExceptionDuringCall = throwException;
        Tracer.getInstance().startRequestWithRootSpan("foo");
        Deque<Span> spanStack = Tracer.getInstance().getCurrentSpanStackCopy();
        Map<String, String> mdcInfo = MDC.getCopyOfContextMap();
        final RunnableWithTracing instance = new RunnableWithTracing(
            runnableMock, spanStack, mdcInfo
        );
        resetTracing();
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        // when
        Throwable ex = catchThrowable(new ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                instance.run();
            }
        });

        // then
        verify(runnableMock).run();
        if (throwException)
            assertThat(ex).isNotNull();
        else
            assertThat(ex).isNull();

        assertThat(currentSpanStackWhenRunnableWasCalled.get(0)).isEqualTo(spanStack);
        assertThat(currentMdcInfoWhenRunnableWasCalled.get(0)).isEqualTo(mdcInfo);

        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

}