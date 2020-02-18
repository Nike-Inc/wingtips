package com.nike.wingtips.util.asynchelperwrapper;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A wrapper around any {@link ScheduledExecutorService} instance that causes the current (method caller's) tracing
 * state to hop
 * threads when {@link Runnable}s or {@link Callable}s are supplied for execution. Simply supply the constructor with
 * the delegate {@link ScheduledExecutorService} you want to wrap (often one from {@link java.util.concurrent.Executors}) and
 * then treat it like any other {@link ScheduledExecutorService}. Shutdown and termination methods pass through to the
 * delegate - {@link ScheduledExecutorServiceWithTracing} does not contain any state of its own.
 *
 *
 * <p>Usage:
 * <pre>
 * // In some setup location, create the ScheduledExecutorWithTracing.
 * ScheduledExecutorService tracingAwareScheduledExecutorService =
 *                          new ScheduledExecutorWithTracing(Executors.newSingleThreadScheduledExecutor());
 *
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
 * @author Biju Kunjummen
 * @author Rafaela Breed
 */
public class ScheduledExecutorServiceWithTracing extends ExecutorServiceWithTracing implements ScheduledExecutorService {

    protected final ScheduledExecutorService delegate;

    /**
     * Creates a new instance that wraps the given delegate {@link ScheduledExecutorService} so that when
     * {@link Runnable}s
     * or {@link Callable}s are executed they will automatically inherit the tracing state of the thread that called
     * the {@link ScheduledExecutorService} method.
     *
     * @param delegate The {@link ScheduledExecutorService} to delegate all calls to.
     */
    public ScheduledExecutorServiceWithTracing(ScheduledExecutorService delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    /**
     * Factory method that creates a new instance that wraps the given delegate {@link ScheduledExecutorService} so
     * that when
     * {@link Runnable}s or {@link Callable}s are executed they will automatically inherit the tracing state of the
     * thread that called the {@link ScheduledExecutorService} method. Equivalent to calling:
     * {@code new ScheduledExecutorWithTracing(delegate)}.
     *
     * @param delegate The {@link ScheduledExecutorService} to delegate all calls to.
     * @return {@code new ScheduledExecutorWithTracing(delegate)}
     */
    public static ScheduledExecutorServiceWithTracing withTracing(ScheduledExecutorService delegate) {
        return new ScheduledExecutorServiceWithTracing(delegate);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return delegate.schedule(new RunnableWithTracing(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return this.delegate.schedule(new CallableWithTracing<>(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return this.delegate.scheduleAtFixedRate(new RunnableWithTracing(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return this.delegate.scheduleWithFixedDelay(new RunnableWithTracing(command), initialDelay, delay, unit);
    }
}
