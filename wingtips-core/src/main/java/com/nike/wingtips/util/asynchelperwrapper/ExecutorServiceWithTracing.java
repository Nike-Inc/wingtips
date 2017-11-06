package com.nike.wingtips.util.asynchelperwrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A wrapper around any {@link ExecutorService} instance that causes the current (method caller's) tracing state to hop
 * threads when {@link Runnable}s or {@link Callable}s are supplied for execution. Simply supply the constructor with
 * the delegate {@link ExecutorService} you want to wrap (often one from {@link java.util.concurrent.Executors}) and
 * then treat it like any other {@link ExecutorService}. Shutdown and termination methods pass through to the
 * delegate - {@link ExecutorServiceWithTracing} does not contain any state of its own.
 *
 * <p>WARNING: Keep in mind that you should avoid using a {@link ExecutorServiceWithTracing} when spinning off
 * background threads that aren't tied to a specific trace, or in any other situation where an executed
 * {@link Runnable}/{@link Callable} should *not* automatically inherit the calling thread's tracing state!
 *
 * <p>Usage example (written with Java 8 lambda syntax for the {@link Runnable} for brevity):
 * <pre>
 * // In some setup location, create the ExecutorServiceWithTracing.
 * ExecutorService tracingAwareExecutorService = new ExecutorServiceWithTracing(Executors.newCachedThreadPool());
 *
 * // ...
 *
 * // Elsewhere, setup the tracing state for the caller's thread.
 * Tracer.getInstance().startRequestWithRootSpan("someRootSpan");
 * TracingState callerThreadTracingState = TracingState.getCurrentThreadTracingState();
 *
 * // Tell the tracing-wrapped executor to execute a Runnable.
 * tracingAwareExecutorService.execute(() -> {
 *     // Runnable's execution code goes here.
 *     // The important bit is that although we are no longer on the caller's thread, this Runnable's thread
 *     //     will have the same tracing state as the caller. The following line will not throw an exception.
 *     assert callerThreadTracingState.equals(TracingState.getCurrentThreadTracingState());
 * });
 * </pre>
 *
 * @author Nic Munroe
 */
public class ExecutorServiceWithTracing implements ExecutorService {

    protected final ExecutorService delegate;

    /**
     * Creates a new instance that wraps the given delegate {@link ExecutorService} so that when {@link Runnable}s
     * or {@link Callable}s are executed they will automatically inherit the tracing state of the thread that called
     * the {@link ExecutorService} method.
     *
     * <p>WARNING: Keep in mind that you should avoid using a {@link ExecutorServiceWithTracing} when spinning off
     * background threads that aren't tied to a specific trace, or in any other situation where an executed
     * {@link Runnable}/{@link Callable} should *not* automatically inherit the calling thread's tracing state!
     *
     * @param delegate The {@link ExecutorService} to delegate all calls to.
     */
    public ExecutorServiceWithTracing(ExecutorService delegate) {
        this.delegate = delegate;
    }

    /**
     * Factory method that creates a new instance that wraps the given delegate {@link ExecutorService} so that when
     * {@link Runnable}s or {@link Callable}s are executed they will automatically inherit the tracing state of the
     * thread that called the {@link ExecutorService} method. Equivalent to calling:
     * {@code new ExecutorServiceWithTracing(delegate)}.
     *
     * <p>WARNING: Keep in mind that you should avoid using a {@link ExecutorServiceWithTracing} when spinning off
     * background threads that aren't tied to a specific trace, or in any other situation where an executed
     * {@link Runnable}/{@link Callable} should *not* automatically inherit the calling thread's tracing state!
     *
     * @param delegate The {@link ExecutorService} to delegate all calls to.
     * @return {@code new ExecutorServiceWithTracing(delegate)}
     */
    public static ExecutorServiceWithTracing withTracing(ExecutorService delegate) {
        return new ExecutorServiceWithTracing(delegate);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(new CallableWithTracing<>(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(new RunnableWithTracing(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(new RunnableWithTracing(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(convertToCallableWithTracingList(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit
    ) throws InterruptedException {
        return delegate.invokeAll(convertToCallableWithTracingList(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(convertToCallableWithTracingList(tasks));
    }

    @Override
    public <T> T invokeAny(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit
    ) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(convertToCallableWithTracingList(tasks), timeout, unit);
    }

    protected <T> List<Callable<T>> convertToCallableWithTracingList(Collection<? extends Callable<T>> tasks) {
        if (tasks == null) {
            return null;
        }

        List<Callable<T>> tasksWithTracing = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            Callable<T> taskWithTracing = (task == null) ? null : new CallableWithTracing<>(task);
            tasksWithTracing.add(taskWithTracing);
        }

        return tasksWithTracing;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(new RunnableWithTracing(command));
    }
}
