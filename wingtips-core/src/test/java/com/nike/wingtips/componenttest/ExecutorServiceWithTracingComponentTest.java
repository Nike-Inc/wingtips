package com.nike.wingtips.componenttest;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;
import com.nike.wingtips.util.asynchelperwrapper.ExecutorServiceWithTracing;

import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Component test that verifies {@link ExecutorServiceWithTracing} causes tracing state to hop threads when
 * {@link Callable}s or {@link Runnable}s are supplied for async execution.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class ExecutorServiceWithTracingComponentTest {

    private ExecutorServiceWithTracing instance;

    private List<TracingState> capturedTracingStates;
    private List<Long> capturedThreadIds;

    @Before
    public void beforeMethod() {
        instance = new ExecutorServiceWithTracing(Executors.newCachedThreadPool());

        capturedTracingStates = new CopyOnWriteArrayList<>();
        capturedThreadIds = new CopyOnWriteArrayList<>();

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

    @Test
    public void submit_callable_hops_the_tracing_state_to_the_callable_during_execution()
        throws InterruptedException, ExecutionException, TimeoutException {
        // given
        String origTaskResult = UUID.randomUUID().toString();
        Callable<String> origTask = generateTracingCapturingCallable(origTaskResult);

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        String result = instance.submit(origTask)
                                .get(10, TimeUnit.SECONDS);

        // then
        assertThat(result).isEqualTo(origTaskResult);
        verifyExpectedCapturedTracingStates(expectedTracingState);
    }

    private Callable<String> generateTracingCapturingCallable(String result) {
        return () -> {
            capturedTracingStates.add(TracingState.getCurrentThreadTracingState());
            capturedThreadIds.add(Thread.currentThread().getId());
            return result;
        };
    }

    private TracingState generateTracingStateOnCurrentThread() {
        Tracer.getInstance().startRequestWithRootSpan(UUID.randomUUID().toString());
        Tracer.getInstance().startSubSpan(UUID.randomUUID().toString(), Span.SpanPurpose.LOCAL_ONLY);
        return TracingState.getCurrentThreadTracingState();
    }

    private void verifyExpectedCapturedTracingStates(TracingState... expectedTracingStates) {
        assertThat(capturedTracingStates).hasSize(expectedTracingStates.length);
        assertThat(capturedTracingStates).containsExactly(expectedTracingStates);

        assertThat(capturedThreadIds).hasSameSizeAs(capturedTracingStates);
        long testThreadId = Thread.currentThread().getId();
        assertThat(capturedThreadIds).doesNotContain(testThreadId);
    }

    @Test
    public void submit_runnable_with_result_hops_the_tracing_state_to_the_runnable_during_execution()
        throws InterruptedException, ExecutionException, TimeoutException {
        // given
        Runnable origTask = generateTracingCapturingRunnable();
        String resultArg = UUID.randomUUID().toString();

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        String result = instance.submit(origTask, resultArg)
                                .get(10, TimeUnit.SECONDS);

        // then
        assertThat(result).isEqualTo(resultArg);
        verifyExpectedCapturedTracingStates(expectedTracingState);
    }

    private Runnable generateTracingCapturingRunnable() {
        return () -> {
            capturedTracingStates.add(TracingState.getCurrentThreadTracingState());
            capturedThreadIds.add(Thread.currentThread().getId());
        };
    }

    @Test
    public void submit_runnable_hops_the_tracing_state_to_the_runnable_during_execution()
        throws InterruptedException, ExecutionException, TimeoutException {
        // given
        Runnable origTask = generateTracingCapturingRunnable();

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        instance.submit(origTask).get(10, TimeUnit.SECONDS);

        // then
        verifyExpectedCapturedTracingStates(expectedTracingState);
    }

    @Test
    public void invokeAll_no_timeout_hops_the_tracing_state_to_the_callables_during_execution()
        throws InterruptedException {
        // given
        String callableResult1 = UUID.randomUUID().toString();
        String callableResult2 = UUID.randomUUID().toString();
        List<Callable<String>> origTasks = Arrays.asList(generateTracingCapturingCallable(callableResult1),
                                                         generateTracingCapturingCallable(callableResult2));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        List<String> results = instance.invokeAll(origTasks)
                                       .stream()
                                       .map(
                                           f -> {
                                               try {
                                                   return f.get(10, TimeUnit.SECONDS);
                                               }
                                               catch (Throwable e) {
                                                   throw new RuntimeException(e);
                                               }
                                           }
                                       )
                                       .collect(Collectors.toList());

        Thread.sleep(100);
        
        // then
        assertThat(results).isEqualTo(Arrays.asList(callableResult1, callableResult2));
        verifyExpectedCapturedTracingStates(expectedTracingState, expectedTracingState);
    }

    @Test
    public void invokeAll_with_timeout_hops_the_tracing_state_to_the_callables_during_execution()
        throws InterruptedException {
        // given
        String callableResult1 = UUID.randomUUID().toString();
        String callableResult2 = UUID.randomUUID().toString();
        List<Callable<String>> origTasks = Arrays.asList(generateTracingCapturingCallable(callableResult1),
                                                         generateTracingCapturingCallable(callableResult2));

        long timeoutValue = 42;
        TimeUnit timeoutTimeUnit = TimeUnit.MINUTES;

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        List<String> results = instance.invokeAll(origTasks, timeoutValue, timeoutTimeUnit)
                                       .stream()
                                       .map(
                                           f -> {
                                               try {
                                                   return f.get(10, TimeUnit.SECONDS);
                                               }
                                               catch (Throwable e) {
                                                   throw new RuntimeException(e);
                                               }
                                           }
                                       )
                                       .collect(Collectors.toList());

        Thread.sleep(100);

        // then
        assertThat(results).isEqualTo(Arrays.asList(callableResult1, callableResult2));
        verifyExpectedCapturedTracingStates(expectedTracingState, expectedTracingState);
    }

    @Test
    public void invokeAny_no_timeout_hops_the_tracing_state_to_the_callables_during_execution()
        throws InterruptedException, ExecutionException {
        // given
        String callableResult1 = UUID.randomUUID().toString();
        String callableResult2 = UUID.randomUUID().toString();
        List<Callable<String>> origTasks = Arrays.asList(generateTracingCapturingCallable(callableResult1),
                                                         generateTracingCapturingCallable(callableResult2));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        String result = instance.invokeAny(origTasks);

        Thread.sleep(500);
        
        // then
        assertThat(Arrays.asList(callableResult1, callableResult2)).contains(result);
        if (capturedTracingStates.size() == 1) {
            verifyExpectedCapturedTracingStates(expectedTracingState);
        }
        else {
            verifyExpectedCapturedTracingStates(expectedTracingState, expectedTracingState);
        }
    }

    @Test
    public void invokeAny_with_timeout_hops_the_tracing_state_to_the_callables_during_execution()
        throws InterruptedException, TimeoutException, ExecutionException {
        // given
        String callableResult1 = UUID.randomUUID().toString();
        String callableResult2 = UUID.randomUUID().toString();
        List<Callable<String>> origTasks = Arrays.asList(generateTracingCapturingCallable(callableResult1),
                                                         generateTracingCapturingCallable(callableResult2));

        long timeoutValue = 42;
        TimeUnit timeoutTimeUnit = TimeUnit.MINUTES;

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        String result = instance.invokeAny(origTasks, timeoutValue, timeoutTimeUnit);

        Thread.sleep(500);

        // then
        assertThat(Arrays.asList(callableResult1, callableResult2)).contains(result);
        if (capturedTracingStates.size() == 1) {
            verifyExpectedCapturedTracingStates(expectedTracingState);
        }
        else {
            verifyExpectedCapturedTracingStates(expectedTracingState, expectedTracingState);
        }
    }

    @Test
    public void execute_hops_the_tracing_state_to_the_runnable_during_execution() throws InterruptedException {
        // given
        Runnable origRunnable = generateTracingCapturingRunnable();

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        instance.execute(origRunnable);

        for (int i = 0; (i < 1000 && capturedTracingStates.size() == 0); i++) {
            Thread.sleep(10);
        }

        if (capturedTracingStates.size() == 0) {
            fail("The runnable did not populate capturedTracingStates after waiting for 10 seconds");
        }

        // then
        verifyExpectedCapturedTracingStates(expectedTracingState);
    }

}
