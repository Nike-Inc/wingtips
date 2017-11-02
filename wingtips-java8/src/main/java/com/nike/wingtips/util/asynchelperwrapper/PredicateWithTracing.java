package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.function.Predicate;

import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

/**
 * A {@link Predicate} that wraps the given original so that the given distributed tracing and MDC information is
 * registered with the thread and therefore available during execution and unregistered after execution.
 */
@SuppressWarnings("WeakerAccess")
public class PredicateWithTracing<T> implements Predicate<T> {

    protected final Predicate<T> origPredicate;
    protected final Deque<Span> spanStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link PredicateWithTracing#PredicateWithTracing(Predicate, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public PredicateWithTracing(Predicate<T> origPredicate) {
        this(origPredicate, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public PredicateWithTracing(Predicate<T> origPredicate,
                                Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origPredicate,
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
    public PredicateWithTracing(Predicate<T> origPredicate,
                                Deque<Span> spanStackForExecution,
                                Map<String, String> mdcContextMapForExecution) {
        if (origPredicate == null)
            throw new IllegalArgumentException("origPredicate cannot be null");

        this.origPredicate = origPredicate;
        this.spanStackForExecution = spanStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new PredicateWithTracing(origPredicate)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new PredicateWithTracing(origPredicate)}.
     * @see PredicateWithTracing#PredicateWithTracing(Predicate)
     * @see PredicateWithTracing
     */
    public static <T> PredicateWithTracing<T> withTracing(Predicate<T> origPredicate) {
        return new PredicateWithTracing<>(origPredicate);
    }

    /**
     * Equivalent to calling {@code new PredicateWithTracing(origPredicate, originalThreadInfo)} - this allows you
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
     * @return {@code new PredicateWithTracing(origPredicate, originalThreadInfo)}.
     * @see PredicateWithTracing#PredicateWithTracing(Predicate, Pair)
     * @see PredicateWithTracing
     */
    public static <T> PredicateWithTracing<T> withTracing(Predicate<T> origPredicate,
                                                          Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        return new PredicateWithTracing<>(origPredicate, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new PredicateWithTracing(origPredicate, spanStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new PredicateWithTracing(origPredicate, spanStackForExecution, mdcContextMapForExecution)}.
     * @see PredicateWithTracing#PredicateWithTracing(Predicate, Deque, Map)
     * @see PredicateWithTracing
     */
    public static <T> PredicateWithTracing<T> withTracing(Predicate<T> origPredicate,
                                                          Deque<Span> spanStackForExecution,
                                                          Map<String, String> mdcContextMapForExecution) {
        return new PredicateWithTracing<>(origPredicate, spanStackForExecution, mdcContextMapForExecution);
    }

    @Override
    public boolean test(T o) {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(spanStackForExecution, mdcContextMapForExecution);

            return origPredicate.test(o);
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
