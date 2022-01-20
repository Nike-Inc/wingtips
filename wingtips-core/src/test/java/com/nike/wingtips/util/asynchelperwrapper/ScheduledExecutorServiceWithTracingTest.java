package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.nike.wingtips.util.asynchelperwrapper.ScheduledExecutorServiceWithTracing.withTracing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link ScheduledExecutorServiceWithTracing}.
 *
 * @author Biju Kunjummen
 * @author Rafaela Breed
 */
@RunWith(DataProviderRunner.class)
public class ScheduledExecutorServiceWithTracingTest {

    private ScheduledExecutorService executorServiceMock;
    private ScheduledExecutorServiceWithTracing instance;

    private ArgumentCaptor<Callable> callableCaptor;
    private ArgumentCaptor<Runnable> runnableCaptor;
    private ArgumentCaptor<Collection> collectionCaptor;

    @Before
    public void beforeMethod() {
        executorServiceMock = mock(ScheduledExecutorService.class);
        instance = new ScheduledExecutorServiceWithTracing(executorServiceMock);

        callableCaptor = ArgumentCaptor.forClass(Callable.class);
        runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        collectionCaptor = ArgumentCaptor.forClass(Collection.class);

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
    public void constructor_sets_fields_as_expected(boolean useStaticFactoryMethod) {
        // given
        executorServiceMock = mock(ScheduledExecutorService.class);

        // when
        instance = (useStaticFactoryMethod)
                   ? withTracing(executorServiceMock)
                   : new ScheduledExecutorServiceWithTracing(executorServiceMock);

        // then
        assertThat(instance.delegate).isSameAs(executorServiceMock);
    }

    @Test
    public void shutdown_passes_through_to_delegate() {
        // when
        instance.shutdown();

        // then
        verify(executorServiceMock).shutdown();
        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void shutdownNow_passes_through_to_delegate() {
        // when
        instance.shutdownNow();

        // then
        verify(executorServiceMock).shutdownNow();
        verifyNoMoreInteractions(executorServiceMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void isShutdown_passes_through_to_delegate(boolean delegateValue) {
        // given
        doReturn(delegateValue).when(executorServiceMock).isShutdown();

        // when
        boolean result = instance.isShutdown();

        // then
        assertThat(result).isEqualTo(delegateValue);
        verify(executorServiceMock).isShutdown();
        verifyNoMoreInteractions(executorServiceMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void isTerminated_passes_through_to_delegate(boolean delegateValue) {
        // given
        doReturn(delegateValue).when(executorServiceMock).isTerminated();

        // when
        boolean result = instance.isTerminated();

        // then
        assertThat(result).isEqualTo(delegateValue);
        verify(executorServiceMock).isTerminated();
        verifyNoMoreInteractions(executorServiceMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void awaitTermination_passes_through_to_delegate(boolean delegateValue) throws InterruptedException {
        // given
        long timeoutValue = 42;
        TimeUnit timeoutTimeUnit = TimeUnit.MINUTES;
        doReturn(delegateValue).when(executorServiceMock).awaitTermination(anyLong(), any(TimeUnit.class));

        // when
        boolean result = instance.awaitTermination(timeoutValue, timeoutTimeUnit);

        // then
        assertThat(result).isEqualTo(delegateValue);
        verify(executorServiceMock).awaitTermination(timeoutValue, timeoutTimeUnit);
        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void submit_callable_passes_through_to_delegate_with_tracing_wrapper() {
        // given
        Callable<?> origTaskMock = mock(Callable.class);
        Future<?> expectedResultMock = mock(Future.class);
        doReturn(expectedResultMock).when(executorServiceMock).submit(any(Callable.class));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        Future<?> result = instance.submit(origTaskMock);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(executorServiceMock).submit(callableCaptor.capture());
        Callable<?> actualTask = callableCaptor.getValue();
        verifyCallableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void schedule_callable_passes_through_to_delegate_with_tracing_wrapper() {
        // given
        Callable<?> origTaskMock = mock(Callable.class);
        ScheduledFuture<?> expectedResultMock = mock(ScheduledFuture.class);
        doReturn(expectedResultMock).when(executorServiceMock).schedule(any(Callable.class), anyLong(), any(TimeUnit.class));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        Future<?> result = instance.schedule(origTaskMock, 10L, TimeUnit.SECONDS);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(executorServiceMock).schedule(callableCaptor.capture(), eq(10L), eq(TimeUnit.SECONDS));
        Callable<?> actualTask = callableCaptor.getValue();
        verifyCallableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);

        verifyNoMoreInteractions(executorServiceMock);
    }

    private TracingState generateTracingStateOnCurrentThread() {
        Tracer.getInstance().startRequestWithRootSpan(UUID.randomUUID().toString());
        Tracer.getInstance().startSubSpan(UUID.randomUUID().toString(), SpanPurpose.LOCAL_ONLY);
        return TracingState.getCurrentThreadTracingState();
    }

    private void verifyCallableWithTracingWrapper(
        Callable<?> actual, Callable<?> expectedOrigCallable, TracingState expectedTracingState
    ) {
        assertThat(actual).isInstanceOf(CallableWithTracing.class);
        CallableWithTracing<?> actualWithTracing = (CallableWithTracing<?>) actual;
        assertThat(actualWithTracing.origCallable).isSameAs(expectedOrigCallable);
        verifyExpectedTracingState(
            actualWithTracing.spanStackForExecution, actualWithTracing.mdcContextMapForExecution, expectedTracingState
        );
    }

    private void verifyExpectedTracingState(
        Deque<Span> actualSpanStack, Map<String, String> actualMdc, TracingState expectedTracingState
    ) {
        assertThat(actualSpanStack).isEqualTo(expectedTracingState.spanStack);
        assertThat(actualMdc).isEqualTo(expectedTracingState.mdcInfo);
    }

    @Test
    public void submit_runnable_with_result_passes_through_to_delegate_with_tracing_wrapper() {
        // given
        Runnable origTaskMock = mock(Runnable.class);
        String resultArg = UUID.randomUUID().toString();
        Future<String> expectedResultMock = mock(Future.class);
        doReturn(expectedResultMock).when(executorServiceMock).submit(any(Runnable.class), anyString());

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        Future<String> result = instance.submit(origTaskMock, resultArg);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(executorServiceMock).submit(runnableCaptor.capture(), eq(resultArg));
        Runnable actualTask = runnableCaptor.getValue();
        verifyRunnableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void schedule_runnable_with_delay_passes_through_to_delegate_with_tracing_wrapper() {
        // given
        Runnable origTaskMock = mock(Runnable.class);
        ScheduledFuture<?> expectedResultMock = mock(ScheduledFuture.class);
        doReturn(expectedResultMock).when(executorServiceMock).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        ScheduledFuture<?> result = instance.schedule(origTaskMock, 10L, TimeUnit.SECONDS);

        // then
        assertThat((Future<?>)result).isSameAs(expectedResultMock);
        verify(executorServiceMock).schedule(runnableCaptor.capture(), eq(10L), eq(TimeUnit.SECONDS));
        Runnable actualTask = runnableCaptor.getValue();
        verifyRunnableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void schedule_runnable_at_fixed_rate_passes_through_to_delegate_with_tracing_wrapper() {
        // given
        Runnable origTaskMock = mock(Runnable.class);
        ScheduledFuture<?> expectedResultMock = mock(ScheduledFuture.class);
        doReturn(expectedResultMock).when(executorServiceMock)
                .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        ScheduledFuture<?> result = instance.scheduleAtFixedRate(origTaskMock, 10L, 10L, TimeUnit.SECONDS);

        // then
        assertThat((Future<?>)result).isSameAs(expectedResultMock);
        verify(executorServiceMock).scheduleAtFixedRate(runnableCaptor.capture(), eq(10L), eq(10L), eq(TimeUnit.SECONDS));
        Runnable actualTask = runnableCaptor.getValue();
        verifyRunnableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void schedule_runnable_with_fixed_delay_passes_through_to_delegate_with_tracing_wrapper() {
        // given
        Runnable origTaskMock = mock(Runnable.class);
        ScheduledFuture<?> expectedResultMock = mock(ScheduledFuture.class);
        doReturn(expectedResultMock).when(executorServiceMock)
                .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        ScheduledFuture<?> result = instance.scheduleWithFixedDelay(origTaskMock, 10L, 10L, TimeUnit.SECONDS);

        // then
        assertThat((Future<?>)result).isSameAs(expectedResultMock);
        verify(executorServiceMock).scheduleWithFixedDelay(runnableCaptor.capture(), eq(10L), eq(10L), eq(TimeUnit.SECONDS));
        Runnable actualTask = runnableCaptor.getValue();
        verifyRunnableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);

        verifyNoMoreInteractions(executorServiceMock);
    }


    private void verifyRunnableWithTracingWrapper(
        Runnable actual, Runnable expectedOrigRunnable, TracingState expectedTracingState
    ) {
        assertThat(actual).isInstanceOf(RunnableWithTracing.class);
        RunnableWithTracing actualWithTracing = (RunnableWithTracing) actual;
        assertThat(actualWithTracing.origRunnable).isSameAs(expectedOrigRunnable);
        verifyExpectedTracingState(
            actualWithTracing.spanStackForExecution, actualWithTracing.mdcContextMapForExecution, expectedTracingState
        );
    }

    @Test
    public void submit_runnable_passes_through_to_delegate_with_tracing_wrapper() {
        // given
        Runnable origTaskMock = mock(Runnable.class);
        Future<?> expectedResultMock = mock(Future.class);
        doReturn(expectedResultMock).when(executorServiceMock).submit(any(Runnable.class));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        Future<?> result = instance.submit(origTaskMock);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(executorServiceMock).submit(runnableCaptor.capture());
        Runnable actualTask = runnableCaptor.getValue();
        verifyRunnableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void invokeAll_no_timeout_passes_through_to_delegate_with_tracing_wrapped_tasks()
        throws InterruptedException {
        // given
        List<Callable<Object>> origTasks = Arrays.asList(mock(Callable.class), mock(Callable.class));
        List<Future<Object>> expectedResultMock = mock(List.class);
        doReturn(expectedResultMock).when(executorServiceMock).invokeAll(any(Collection.class));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        List<Future<Object>> result = instance.invokeAll(origTasks);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(executorServiceMock).invokeAll(collectionCaptor.capture());
        List<Callable<Object>> actualTasks = (List<Callable<Object>>) collectionCaptor.getValue();
        assertThat(actualTasks).hasSameSizeAs(origTasks);
        for (int i = 0; i < actualTasks.size(); i++) {
            Callable<Object> actualTask = actualTasks.get(i);
            Callable<Object> origTaskMock = origTasks.get(i);
            verifyCallableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);
        }

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void invokeAll_no_timeout_uses_convertToCallableWithTracingList_to_convert_tasks()
        throws InterruptedException {
        // given
        ExecutorServiceWithTracing instanceSpy = spy(instance);

        List<Callable<Object>> origTasks = Arrays.asList(mock(Callable.class), mock(Callable.class));

        List<Callable<Object>> convertMethodResult = mock(List.class);
        doReturn(convertMethodResult).when(instanceSpy).convertToCallableWithTracingList(any(Collection.class));

        List<Future<Object>> expectedResultMock = mock(List.class);
        doReturn(expectedResultMock).when(executorServiceMock).invokeAll(any(Collection.class));

        // when
        List<Future<Object>> result = instanceSpy.invokeAll(origTasks);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(instanceSpy).convertToCallableWithTracingList(origTasks);
        verify(executorServiceMock).invokeAll(convertMethodResult);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void invokeAll_with_timeout_passes_through_to_delegate_with_tracing_wrapped_tasks()
        throws InterruptedException {
        // given
        List<Callable<Object>> origTasks = Arrays.asList(mock(Callable.class), mock(Callable.class));
        List<Future<Object>> expectedResultMock = mock(List.class);
        doReturn(expectedResultMock).when(executorServiceMock)
                                    .invokeAll(any(Collection.class), anyLong(), any(TimeUnit.class));

        long timeoutValue = 42;
        TimeUnit timeoutTimeUnit = TimeUnit.MINUTES;

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        List<Future<Object>> result = instance.invokeAll(origTasks, timeoutValue, timeoutTimeUnit);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(executorServiceMock).invokeAll(collectionCaptor.capture(), eq(timeoutValue), eq(timeoutTimeUnit));
        List<Callable<Object>> actualTasks = (List<Callable<Object>>) collectionCaptor.getValue();
        assertThat(actualTasks).hasSameSizeAs(origTasks);
        for (int i = 0; i < actualTasks.size(); i++) {
            Callable<Object> actualTask = actualTasks.get(i);
            Callable<Object> origTaskMock = origTasks.get(i);
            verifyCallableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);
        }

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void invokeAll_with_timeout_uses_convertToCallableWithTracingList_to_convert_tasks()
        throws InterruptedException {
        // given
        ExecutorServiceWithTracing instanceSpy = spy(instance);

        List<Callable<Object>> origTasks = Arrays.asList(mock(Callable.class), mock(Callable.class));

        List<Callable<Object>> convertMethodResult = mock(List.class);
        doReturn(convertMethodResult).when(instanceSpy).convertToCallableWithTracingList(any(Collection.class));

        List<Future<Object>> expectedResultMock = mock(List.class);
        doReturn(expectedResultMock).when(executorServiceMock)
                                    .invokeAll(any(Collection.class), anyLong(), any(TimeUnit.class));

        long timeoutValue = 42;
        TimeUnit timeoutTimeUnit = TimeUnit.MINUTES;

        // when
        List<Future<Object>> result = instanceSpy.invokeAll(origTasks, timeoutValue, timeoutTimeUnit);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(instanceSpy).convertToCallableWithTracingList(origTasks);
        verify(executorServiceMock).invokeAll(convertMethodResult, timeoutValue, timeoutTimeUnit);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void invokeAny_no_timeout_passes_through_to_delegate_with_tracing_wrapped_tasks()
        throws InterruptedException, ExecutionException {
        // given
        List<Callable<Object>> origTasks = Arrays.asList(mock(Callable.class), mock(Callable.class));
        Object expectedResultMock = mock(Object.class);
        doReturn(expectedResultMock).when(executorServiceMock).invokeAny(any(Collection.class));

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        Object result = instance.invokeAny(origTasks);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(executorServiceMock).invokeAny(collectionCaptor.capture());
        List<Callable<Object>> actualTasks = (List<Callable<Object>>) collectionCaptor.getValue();
        assertThat(actualTasks).hasSameSizeAs(origTasks);
        for (int i = 0; i < actualTasks.size(); i++) {
            Callable<Object> actualTask = actualTasks.get(i);
            Callable<Object> origTaskMock = origTasks.get(i);
            verifyCallableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);
        }

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void invokeAny_no_timeout_uses_convertToCallableWithTracingList_to_convert_tasks()
        throws InterruptedException, ExecutionException {
        // given
        ExecutorServiceWithTracing instanceSpy = spy(instance);

        List<Callable<Object>> origTasks = Arrays.asList(mock(Callable.class), mock(Callable.class));

        List<Callable<Object>> convertMethodResult = mock(List.class);
        doReturn(convertMethodResult).when(instanceSpy).convertToCallableWithTracingList(any(Collection.class));

        Object expectedResultMock = mock(Object.class);
        doReturn(expectedResultMock).when(executorServiceMock).invokeAny(any(Collection.class));

        // when
        Object result = instanceSpy.invokeAny(origTasks);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(instanceSpy).convertToCallableWithTracingList(origTasks);
        verify(executorServiceMock).invokeAny(convertMethodResult);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void invokeAny_with_timeout_passes_through_to_delegate_with_tracing_wrapped_tasks()
        throws InterruptedException, TimeoutException, ExecutionException {
        // given
        List<Callable<Object>> origTasks = Arrays.asList(mock(Callable.class), mock(Callable.class));
        Object expectedResultMock = mock(Object.class);
        doReturn(expectedResultMock).when(executorServiceMock)
                                    .invokeAny(any(Collection.class), anyLong(), any(TimeUnit.class));

        long timeoutValue = 42;
        TimeUnit timeoutTimeUnit = TimeUnit.MINUTES;

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        Object result = instance.invokeAny(origTasks, timeoutValue, timeoutTimeUnit);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(executorServiceMock).invokeAny(collectionCaptor.capture(), eq(timeoutValue), eq(timeoutTimeUnit));
        List<Callable<Object>> actualTasks = (List<Callable<Object>>) collectionCaptor.getValue();
        assertThat(actualTasks).hasSameSizeAs(origTasks);
        for (int i = 0; i < actualTasks.size(); i++) {
            Callable<Object> actualTask = actualTasks.get(i);
            Callable<Object> origTaskMock = origTasks.get(i);
            verifyCallableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);
        }

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void invokeAny_with_timeout_uses_convertToCallableWithTracingList_to_convert_tasks()
        throws InterruptedException, TimeoutException, ExecutionException {
        // given
        ExecutorServiceWithTracing instanceSpy = spy(instance);
        List<Callable<Object>> origTasks = Arrays.asList(mock(Callable.class), mock(Callable.class));
        List<Callable<Object>> convertMethodResult = mock(List.class);
        doReturn(convertMethodResult).when(instanceSpy).convertToCallableWithTracingList(any(Collection.class));

        Object expectedResultMock = mock(Object.class);
        doReturn(expectedResultMock).when(executorServiceMock)
                                    .invokeAny(any(Collection.class), anyLong(), any(TimeUnit.class));

        long timeoutValue = 42;
        TimeUnit timeoutTimeUnit = TimeUnit.MINUTES;

        // when
        Object result = instanceSpy.invokeAny(origTasks, timeoutValue, timeoutTimeUnit);

        // then
        assertThat(result).isSameAs(expectedResultMock);
        verify(instanceSpy).convertToCallableWithTracingList(origTasks);
        verify(executorServiceMock).invokeAny(convertMethodResult, timeoutValue, timeoutTimeUnit);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void execute_passes_through_to_delegate_with_tracing_wrapped_runnable() {
        // given
        Runnable origRunnableMock = mock(Runnable.class);

        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        instance.execute(origRunnableMock);

        // then
        verify(executorServiceMock).execute(runnableCaptor.capture());
        Runnable actualRunnable = runnableCaptor.getValue();
        verifyRunnableWithTracingWrapper(actualRunnable, origRunnableMock, expectedTracingState);

        verifyNoMoreInteractions(executorServiceMock);
    }

    @Test
    public void convertToCallableWithTracingList_works_as_expected() {
        // given
        List<Callable<Object>> origCallables = Arrays.asList(
            mock(Callable.class),
            null,
            mock(Callable.class)
        );
        TracingState expectedTracingState = generateTracingStateOnCurrentThread();

        // when
        List<Callable<Object>> results = instance.convertToCallableWithTracingList(origCallables);

        // then
        assertThat(results).hasSameSizeAs(origCallables);
        for (int i = 0; i < results.size(); i++) {
            Callable<Object> actualTask = results.get(i);
            Callable<Object> origTaskMock = origCallables.get(i);
            if (origTaskMock == null) {
                assertThat(actualTask).isNull();
            }
            else {
                verifyCallableWithTracingWrapper(actualTask, origTaskMock, expectedTracingState);
            }
        }
    }

    @Test
    public void convertToCallableWithTracingList_returns_null_if_passed_null() {
        // expect
        assertThat(instance.convertToCallableWithTracingList(null)).isNull();
    }

}