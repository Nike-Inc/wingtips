package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.function.BiFunction;

import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

/**
 * A {@link BiFunction} that wraps the given original so that the given distributed tracing and MDC information is
 * registered with the thread and therefore available during execution and unregistered after execution.
 */
@SuppressWarnings("WeakerAccess")
public class BiFunctionWithTracing<T, U, R> implements BiFunction<T, U, R> {

    protected final BiFunction<T, U, R> origBiFunction;
    protected final Deque<Span> distributedTraceStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link BiFunctionWithTracing#BiFunctionWithTracing(BiFunction, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public BiFunctionWithTracing(BiFunction<T, U, R> origBiFunction) {
        this(origBiFunction, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public BiFunctionWithTracing(BiFunction<T, U, R> origBiFunction,
                                 Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origBiFunction,
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
    public BiFunctionWithTracing(BiFunction<T, U, R> origBiFunction,
                                 Deque<Span> distributedTraceStackForExecution,
                                 Map<String, String> mdcContextMapForExecution) {
        if (origBiFunction == null)
            throw new IllegalArgumentException("origBiFunction cannot be null");

        this.origBiFunction = origBiFunction;
        this.distributedTraceStackForExecution = distributedTraceStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new BiFunctionWithTracing(origBiFunction)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new BiFunctionWithTracing(origBiFunction)}.
     * @see BiFunctionWithTracing#BiFunctionWithTracing(BiFunction)
     * @see BiFunctionWithTracing
     */
    public static <T, U, R> BiFunctionWithTracing<T, U, R> withTracing(BiFunction<T, U, R> origBiFunction) {
        return new BiFunctionWithTracing<>(origBiFunction);
    }

    /**
     * Equivalent to calling {@code new BiFunctionWithTracing(origBiFunction, originalThreadInfo)} - this allows you
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
     * @return {@code new BiFunctionWithTracing(origBiFunction, originalThreadInfo)}.
     * @see BiFunctionWithTracing#BiFunctionWithTracing(BiFunction, Pair)
     * @see BiFunctionWithTracing
     */
    public static <T, U, R> BiFunctionWithTracing<T, U, R> withTracing(
        BiFunction<T, U, R> origBiFunction,
        Pair<Deque<Span>, Map<String, String>> originalThreadInfo
    ) {
        return new BiFunctionWithTracing<>(origBiFunction, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new BiFunctionWithTracing(origBiFunction, distributedTraceStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new BiFunctionWithTracing(origBiFunction, distributedTraceStackForExecution, mdcContextMapForExecution)}.
     * @see BiFunctionWithTracing#BiFunctionWithTracing(BiFunction, Deque, Map)
     * @see BiFunctionWithTracing
     */
    public static <T, U, R> BiFunctionWithTracing<T, U, R> withTracing(BiFunction<T, U, R> origBiFunction,
                                                                       Deque<Span> distributedTraceStackForExecution,
                                                                       Map<String, String> mdcContextMapForExecution) {
        return new BiFunctionWithTracing<>(origBiFunction, distributedTraceStackForExecution, mdcContextMapForExecution);
    }

    @Override
    public R apply(T t, U u) {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(distributedTraceStackForExecution, mdcContextMapForExecution);

            return origBiFunction.apply(t, u);
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
