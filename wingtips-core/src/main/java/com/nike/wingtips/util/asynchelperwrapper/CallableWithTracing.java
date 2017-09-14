package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.unlinkTracingFromCurrentThread;

/**
 * A {@link Callable} that wraps the given original so that the given distributed tracing and MDC information is
 * registered with the thread and therefore available during execution and unregistered after execution.
 */
@SuppressWarnings("WeakerAccess")
public class CallableWithTracing<U> implements Callable<U> {

    protected final Callable<U> origCallable;
    protected final Deque<Span> distributedTraceStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link CallableWithTracing#CallableWithTracing(Callable, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public CallableWithTracing(Callable<U> origCallable) {
        this(origCallable, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
    }

    /**
     * Constructor that uses the given trace and MDC information, which will be associated with the thread when the
     * given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The {@link Pair} can be null, or you can pass null for the left and/or right side of the pair, and no error
     * will be thrown. Any trace or MDC info that is null means the corresponding info will not be available to the
     * thread when the operation is executed however.
     *
     * <p>You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    public CallableWithTracing(Callable<U> origCallable,
                               Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origCallable,
            (originalThreadInfo == null) ? null : originalThreadInfo.getLeft(),
            (originalThreadInfo == null) ? null : originalThreadInfo.getRight()
        );
    }

    /**
     * Constructor that uses the given trace and MDC information, which will be associated with the thread when the
     * given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     */
    public CallableWithTracing(Callable<U> origCallable,
                               Deque<Span> distributedTraceStackForExecution,
                               Map<String, String> mdcContextMapForExecution) {
        if (origCallable == null)
            throw new IllegalArgumentException("origCallable cannot be null");

        this.origCallable = origCallable;
        this.distributedTraceStackForExecution = distributedTraceStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new CallableWithTracing(origCallable)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new CallableWithTracing(origCallable)}.
     * @see CallableWithTracing#CallableWithTracing(Callable)
     * @see CallableWithTracing
     */
    public static <U> CallableWithTracing<U> withTracing(Callable<U> origCallable) {
        return new CallableWithTracing<>(origCallable);
    }

    /**
     * Equivalent to calling {@code new CallableWithTracing(origCallable, originalThreadInfo)} - this allows you
     * to do a static method import for cleaner looking code in some cases. This method uses the given trace and MDC
     * information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The {@link Pair} can be null, or you can pass null for the left and/or right side of the pair, and no error
     * will be thrown. Any trace or MDC info that is null means the corresponding info will not be available to the
     * thread when the operation is executed however.
     *
     * <p>You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     *
     * @return {@code new CallableWithTracing(origCallable, originalThreadInfo)}.
     * @see CallableWithTracing#CallableWithTracing(Callable, Pair) 
     * @see CallableWithTracing
     */
    public static <U> CallableWithTracing<U> withTracing(Callable<U> origCallable,
                                                         Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        return new CallableWithTracing<>(origCallable, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new CallableWithTracing(origCallable, distributedTraceStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new CallableWithTracing(origCallable, distributedTraceStackForExecution, mdcContextMapForExecution)}.
     * @see CallableWithTracing#CallableWithTracing(Callable, Deque, Map)
     * @see CallableWithTracing
     */
    public static <U> CallableWithTracing<U> withTracing(Callable<U> origCallable,
                                                         Deque<Span> distributedTraceStackForExecution,
                                                         Map<String, String> mdcContextMapForExecution) {
        return new CallableWithTracing<>(origCallable, distributedTraceStackForExecution, mdcContextMapForExecution);
    }

    @Override
    @SuppressWarnings("deprecation")
    public U call() throws Exception {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(distributedTraceStackForExecution, mdcContextMapForExecution);

            return origCallable.call();
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
