package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.function.Function;

import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

/**
 * A {@link Function} that wraps the given original so that the given distributed tracing and MDC information is
 * registered with the thread and therefore available during execution and unregistered after execution.
 */
@SuppressWarnings("WeakerAccess")
public class FunctionWithTracing<T, U> implements Function<T, U> {

    protected final Function<T, U> origFunction;
    protected final Deque<Span> distributedTraceStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link FunctionWithTracing#FunctionWithTracing(Function, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public FunctionWithTracing(Function<T, U> origFunction) {
        this(origFunction, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public FunctionWithTracing(Function<T, U> origFunction,
                               Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origFunction,
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
    public FunctionWithTracing(Function<T, U> origFunction,
                               Deque<Span> distributedTraceStackForExecution,
                               Map<String, String> mdcContextMapForExecution) {
        if (origFunction == null)
            throw new IllegalArgumentException("origFunction cannot be null");

        this.origFunction = origFunction;
        this.distributedTraceStackForExecution = distributedTraceStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new FunctionWithTracing(origFunction)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new FunctionWithTracing(origFunction)}.
     * @see FunctionWithTracing#FunctionWithTracing(Function)
     * @see FunctionWithTracing
     */
    public static <T, U> FunctionWithTracing<T, U> withTracing(Function<T, U> origFunction) {
        return new FunctionWithTracing<>(origFunction);
    }

    /**
     * Equivalent to calling {@code new FunctionWithTracing(origFunction, originalThreadInfo)} - this allows you
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
     * @return {@code new FunctionWithTracing(origFunction, originalThreadInfo)}.
     * @see FunctionWithTracing#FunctionWithTracing(Function, Pair)
     * @see FunctionWithTracing
     */
    public static <T, U> FunctionWithTracing<T, U> withTracing(
        Function<T, U> origFunction,
        Pair<Deque<Span>, Map<String, String>> originalThreadInfo
    ) {
        return new FunctionWithTracing<>(origFunction, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new FunctionWithTracing(origFunction, distributedTraceStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new FunctionWithTracing(origFunction, distributedTraceStackForExecution, mdcContextMapForExecution)}.
     * @see FunctionWithTracing#FunctionWithTracing(Function, Deque, Map)
     * @see FunctionWithTracing
     */
    public static <T, U> FunctionWithTracing<T, U> withTracing(Function<T, U> origFunction,
                                                               Deque<Span> distributedTraceStackForExecution,
                                                               Map<String, String> mdcContextMapForExecution) {
        return new FunctionWithTracing<>(origFunction, distributedTraceStackForExecution, mdcContextMapForExecution);
    }

    @Override
    public U apply(T t) {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(distributedTraceStackForExecution, mdcContextMapForExecution);

            return origFunction.apply(t);
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
