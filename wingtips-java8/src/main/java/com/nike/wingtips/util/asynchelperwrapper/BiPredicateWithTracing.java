package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.function.BiPredicate;

import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

/**
 * A {@link BiPredicate} that wraps the given original so that the given distributed tracing and MDC information is
 * registered with the thread and therefore available during execution and unregistered after execution.
 */
@SuppressWarnings("WeakerAccess")
public class BiPredicateWithTracing<T, U> implements BiPredicate<T, U> {

    protected final BiPredicate<T, U> origBiPredicate;
    protected final Deque<Span> distributedTraceStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link BiPredicateWithTracing#BiPredicateWithTracing(BiPredicate, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public BiPredicateWithTracing(BiPredicate<T, U> origBiPredicate) {
        this(origBiPredicate, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public BiPredicateWithTracing(BiPredicate<T, U> origBiPredicate,
                                  Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origBiPredicate,
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
    public BiPredicateWithTracing(BiPredicate<T, U> origBiPredicate,
                                  Deque<Span> distributedTraceStackForExecution,
                                  Map<String, String> mdcContextMapForExecution) {
        if (origBiPredicate == null)
            throw new IllegalArgumentException("origBiPredicate cannot be null");

        this.origBiPredicate = origBiPredicate;
        this.distributedTraceStackForExecution = distributedTraceStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new BiPredicateWithTracing(origBiPredicate)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new BiPredicateWithTracing(origBiPredicate)}.
     * @see BiPredicateWithTracing#BiPredicateWithTracing(BiPredicate)
     * @see BiPredicateWithTracing
     */
    public static <T, U> BiPredicateWithTracing<T, U> withTracing(BiPredicate<T, U> origBiPredicate) {
        return new BiPredicateWithTracing<>(origBiPredicate);
    }

    /**
     * Equivalent to calling {@code new BiPredicateWithTracing(origBiPredicate, originalThreadInfo)} - this allows you
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
     * @return {@code new BiPredicateWithTracing(origBiPredicate, originalThreadInfo)}.
     * @see BiPredicateWithTracing#BiPredicateWithTracing(BiPredicate, Pair)
     * @see BiPredicateWithTracing
     */
    public static <T, U> BiPredicateWithTracing<T, U> withTracing(
        BiPredicate<T, U> origBiPredicate,
        Pair<Deque<Span>, Map<String, String>> originalThreadInfo
    ) {
        return new BiPredicateWithTracing<>(origBiPredicate, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new BiPredicateWithTracing(origBiPredicate, distributedTraceStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new BiPredicateWithTracing(origBiPredicate, distributedTraceStackForExecution, mdcContextMapForExecution)}.
     * @see BiPredicateWithTracing#BiPredicateWithTracing(BiPredicate, Deque, Map)
     * @see BiPredicateWithTracing
     */
    public static <T, U> BiPredicateWithTracing<T, U> withTracing(BiPredicate<T, U> origBiPredicate,
                                                                  Deque<Span> distributedTraceStackForExecution,
                                                                  Map<String, String> mdcContextMapForExecution) {
        return new BiPredicateWithTracing<>(origBiPredicate, distributedTraceStackForExecution, mdcContextMapForExecution);
    }

    @Override
    public boolean test(T t, U u) {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(distributedTraceStackForExecution, mdcContextMapForExecution);

            return origBiPredicate.test(t, u);
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
