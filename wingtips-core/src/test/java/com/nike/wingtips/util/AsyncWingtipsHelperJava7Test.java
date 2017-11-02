package com.nike.wingtips.util;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.asynchelperwrapper.CallableWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.RunnableWithTracing;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.callableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.runnableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.unlinkTracingFromCurrentThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests the functionality of {@link AsyncWingtipsHelperJava7}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
@SuppressWarnings("deprecation")
public class AsyncWingtipsHelperJava7Test {
    private Runnable runnableMock;
    private Callable callableMock;

    @Before
    public void beforeMethod() {
        runnableMock = mock(Runnable.class);
        callableMock = mock(Callable.class);

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

    private TracingState generateTracingStateInfo() {
        Pair<Deque<Span>, Map<String, String>> tracingInfo = generateTracingInfo();
        return new TracingState(tracingInfo.getLeft(), tracingInfo.getRight());
    }

    private Pair<Deque<Span>, Map<String, String>> generateTracingInfo() {
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("someSpan");
        Pair<Deque<Span>, Map<String, String>> result = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            (Map<String, String>)new HashMap<>(MDC.getCopyOfContextMap())
        );
        resetTracing();
        return result;
    }

    private Pair<Deque<Span>, Map<String, String>> setupCurrentThreadWithTracingInfo() {
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("request-" + UUID.randomUUID().toString());
        return Pair.of(Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
    }

    @Test
    public void code_coverage_hoops() {
        // jump!
        new AsyncWingtipsHelperJava7();
    }

    private void verifyRunnableWithTracing(Runnable result, Runnable expectedCoreRunnable,
                                                        Deque<Span> expectedSpanStack,
                                                        Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(RunnableWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origRunnable")).isSameAs(expectedCoreRunnable);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @Test
    public void runnableWithTracing_using_current_thread_info_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        Runnable result = runnableWithTracing(runnableMock);

        // then
        verifyRunnableWithTracing(result, runnableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void runnableWithTracing_pair_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Runnable result = runnableWithTracing(runnableMock, setupInfo);

        // then
        verifyRunnableWithTracing(result, runnableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void runnableWithTracing_TracingState_works_as_expected() {
        // given
        TracingState setupInfo = generateTracingStateInfo();

        // when
        Runnable result = runnableWithTracing(runnableMock, setupInfo);

        // then
        verifyRunnableWithTracing(result, runnableMock, setupInfo.spanStack, setupInfo.mdcInfo);
    }

    @Test
    public void runnableWithTracing_separate_args_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Runnable result = runnableWithTracing(runnableMock, setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyRunnableWithTracing(result, runnableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyCallableWithTracing(Callable result, Callable expectedCoreInstance,
                                                        Deque<Span> expectedSpanStack,
                                                        Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(CallableWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origCallable")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @Test
    public void callableWithTracing_using_current_thread_info_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        Callable result = callableWithTracing(callableMock);

        // then
        verifyCallableWithTracing(result, callableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void callableWithTracing_pair_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Callable result = callableWithTracing(callableMock, setupInfo);

        // then
        verifyCallableWithTracing(result, callableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void callableWithTracing_TracingState_works_as_expected() {
        // given
        TracingState setupInfo = generateTracingStateInfo();

        // when
        Callable result = callableWithTracing(callableMock, setupInfo);

        // then
        verifyCallableWithTracing(result, callableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void callableWithTracing_separate_args_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Callable result = callableWithTracing(callableMock, setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyCallableWithTracing(result, callableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void linkTracingToCurrentThread_pair_works_as_expected(boolean useNullPair) {
        // given
        Pair<Deque<Span>, Map<String, String>> infoForLinking = (useNullPair) ? null
                                                                              : generateTracingInfo();
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());
        Pair<Deque<Span>, Map<String, String>> expectedPreCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // when
        Pair<Deque<Span>, Map<String, String>> preCallInfo = linkTracingToCurrentThread(infoForLinking);

        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(preCallInfo).isEqualTo(expectedPreCallInfo);
        if (useNullPair) {
            assertThat(postCallInfo.getLeft()).isNull();
            assertThat(postCallInfo.getRight()).isNullOrEmpty();
        }
        else
            assertThat(postCallInfo).isEqualTo(infoForLinking);
    }

    @Test
    public void linkTracingToCurrentThread_pair_works_as_expected_with_non_null_pair_and_null_innards() {
        // given
        Pair<Deque<Span>, Map<String, String>> infoForLinking = Pair.of(null, null);
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());
        Pair<Deque<Span>, Map<String, String>> expectedPreCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // when
        Pair<Deque<Span>, Map<String, String>> preCallInfo = linkTracingToCurrentThread(infoForLinking);

        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(preCallInfo).isEqualTo(expectedPreCallInfo);
        assertThat(postCallInfo.getLeft()).isNull();
        assertThat(postCallInfo.getRight()).isNullOrEmpty();
    }

    @DataProvider(value = {
        "true   |   true",
        "false  |   true",
        "true   |   false",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void linkTracingToCurrentThread_separate_args_works_as_expected(boolean useNullSpanStack,
                                                                                 boolean useNullMdcInfo) {
        // given
        Pair<Deque<Span>, Map<String, String>> info = generateTracingInfo();
        info.getRight().put("fooMdcKey", UUID.randomUUID().toString());
        Deque<Span> spanStackForLinking = (useNullSpanStack) ? null : info.getLeft();
        Map<String, String> mdcInfoForLinking = (useNullMdcInfo) ? null : info.getRight();
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());
        Pair<Deque<Span>, Map<String, String>> expectedPreCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        Map<String, String> expectedMdcInfo;
        // The expected MDC info will vary depending on combinations.
        if (useNullMdcInfo) {
            // MDC may still be populated after the call if the span stack is not empty
            if (useNullSpanStack)
                expectedMdcInfo = Collections.emptyMap();
            else {
                // MDC will have been populated with tracing info.
                expectedMdcInfo = new HashMap<>();
                Span expectedSpan = spanStackForLinking.peek();
                expectedMdcInfo.put(Tracer.TRACE_ID_MDC_KEY, expectedSpan.getTraceId());
                expectedMdcInfo.put(Tracer.SPAN_JSON_MDC_KEY, expectedSpan.toJSON());
            }
        }
        else {
            // Not null MDC. Start with the MDC info for linking.
            expectedMdcInfo = new HashMap<>(mdcInfoForLinking);
            if (useNullSpanStack) {
                // In the case of a null span stack, the trace info would be removed from the MDC.
                expectedMdcInfo.remove(Tracer.TRACE_ID_MDC_KEY);
                expectedMdcInfo.remove(Tracer.SPAN_JSON_MDC_KEY);
            }
        }

        // when
        Pair<Deque<Span>, Map<String, String>> preCallInfo =
            linkTracingToCurrentThread(spanStackForLinking, mdcInfoForLinking);

        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(preCallInfo).isEqualTo(expectedPreCallInfo);
        assertThat(postCallInfo.getLeft()).isEqualTo(spanStackForLinking);
        if (expectedMdcInfo.isEmpty()) {
            assertThat(postCallInfo.getRight()).isNullOrEmpty();
        }
        else {
            assertThat(postCallInfo.getRight()).isEqualTo(expectedMdcInfo);
        }
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void unlinkTracingFromCurrentThread_pair_works_as_expected(boolean useNullPair) {
        // given
        Pair<Deque<Span>, Map<String, String>> infoForLinking = (useNullPair) ? null
                                                                              : generateTracingInfo();
        // Setup the current thread with something that is not ultimately what we expect so that our assertions are
        //      verifying that the unlinkTracingFromCurrentThread method actually did something.
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());

        // when
        unlinkTracingFromCurrentThread(infoForLinking);

        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        if (useNullPair) {
            assertThat(postCallInfo.getLeft()).isNull();
            assertThat(postCallInfo.getRight()).isNullOrEmpty();
        }
        else
            assertThat(postCallInfo).isEqualTo(infoForLinking);
    }

    @Test
    public void unlinkTracingFromCurrentThread_pair_works_as_expected_with_non_null_pair_and_null_innards() {
        // given
        Pair<Deque<Span>, Map<String, String>> infoForLinking = Pair.of(null, null);
        // Setup the current thread with something that is not ultimately what we expect so that our assertions are
        //      verifying that the unlinkTracingFromCurrentThread method actually did something.
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());

        // when
        unlinkTracingFromCurrentThread(infoForLinking);

        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(postCallInfo.getLeft()).isNull();
        assertThat(postCallInfo.getRight()).isNullOrEmpty();
    }

    @DataProvider(value = {
        "true   |   true",
        "false  |   true",
        "true   |   false",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void unlinkTracingFromCurrentThread_separate_args_works_as_expected(boolean useNullSpanStack,
                                                                                     boolean useNullMdcInfo) {
        // given
        Pair<Deque<Span>, Map<String, String>> info = generateTracingInfo();
        info.getRight().put("fooMdcKey", UUID.randomUUID().toString());
        Deque<Span> spanStackForLinking = (useNullSpanStack) ? null : info.getLeft();
        Map<String, String> mdcInfoForLinking = (useNullMdcInfo) ? null : info.getRight();
        // Setup the current thread with something that is not ultimately what we expect so that our assertions are
        //      verifying that the unlinkTracingFromCurrentThread method actually did something.
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());

        Map<String, String> expectedMdcInfo;
        // The expected MDC info will vary depending on combinations.
        if (useNullMdcInfo) {
            // MDC may still be populated after the call if the span stack is not empty
            if (useNullSpanStack)
                expectedMdcInfo = Collections.emptyMap();
            else {
                // MDC will have been populated with tracing info.
                expectedMdcInfo = new HashMap<>();
                Span expectedSpan = spanStackForLinking.peek();
                expectedMdcInfo.put(Tracer.TRACE_ID_MDC_KEY, expectedSpan.getTraceId());
                expectedMdcInfo.put(Tracer.SPAN_JSON_MDC_KEY, expectedSpan.toJSON());
            }
        }
        else {
            // Not null MDC. Since unlinkTracingFromCurrentThread doesn't call registerWithThread when
            //      the span stack is null we don't need to worry about trace ID and span JSON being removed from MDC.
            //      Therefore it should match mdcInfoForLinking exactly.
            expectedMdcInfo = new HashMap<>(mdcInfoForLinking);
        }

        // when
        unlinkTracingFromCurrentThread(spanStackForLinking, mdcInfoForLinking);
        
        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(postCallInfo.getLeft()).isEqualTo(spanStackForLinking);
        if (expectedMdcInfo.isEmpty()) {
            assertThat(postCallInfo.getRight()).isNullOrEmpty();
        }
        else {
            assertThat(postCallInfo.getRight()).isEqualTo(expectedMdcInfo);
        }
    }
}
