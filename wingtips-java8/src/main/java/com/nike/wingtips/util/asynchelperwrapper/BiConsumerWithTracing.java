package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

/**
 * A {@link BiConsumer} that wraps the given original so that the given distributed tracing and MDC information is
 * registered with the thread and therefore available during execution and unregistered after execution.
 */
@SuppressWarnings("WeakerAccess")
public class BiConsumerWithTracing<T, U> implements BiConsumer<T, U> {

    protected final BiConsumer<T, U> origBiConsumer;
    protected final Deque<Span> distributedTraceStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link BiConsumerWithTracing#BiConsumerWithTracing(BiConsumer, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public BiConsumerWithTracing(BiConsumer<T, U> origBiConsumer) {
        this(origBiConsumer, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public BiConsumerWithTracing(BiConsumer<T, U> origBiConsumer,
                                 Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origBiConsumer,
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
    public BiConsumerWithTracing(BiConsumer<T, U> origBiConsumer,
                                 Deque<Span> distributedTraceStackForExecution,
                                 Map<String, String> mdcContextMapForExecution) {
        if (origBiConsumer == null)
            throw new IllegalArgumentException("origBiConsumer cannot be null");

        this.origBiConsumer = origBiConsumer;
        this.distributedTraceStackForExecution = distributedTraceStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new BiConsumerWithTracing(origBiConsumer)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new BiConsumerWithTracing(origBiConsumer)}.
     * @see BiConsumerWithTracing#BiConsumerWithTracing(BiConsumer)
     * @see BiConsumerWithTracing
     */
    public static <T, U> BiConsumerWithTracing<T, U> withTracing(BiConsumer<T, U> origBiConsumer) {
        return new BiConsumerWithTracing<>(origBiConsumer);
    }

    /**
     * Equivalent to calling {@code new BiConsumerWithTracing(origBiConsumer, originalThreadInfo)} - this allows you
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
     * @return {@code new BiConsumerWithTracing(origBiConsumer, originalThreadInfo)}.
     * @see BiConsumerWithTracing#BiConsumerWithTracing(BiConsumer, Pair)
     * @see BiConsumerWithTracing
     */
    public static <T, U> BiConsumerWithTracing<T, U> withTracing(
        BiConsumer<T, U> origBiConsumer,
        Pair<Deque<Span>, Map<String, String>> originalThreadInfo
    ) {
        return new BiConsumerWithTracing<>(origBiConsumer, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new BiConsumerWithTracing(origBiConsumer, distributedTraceStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new BiConsumerWithTracing(origBiConsumer, distributedTraceStackForExecution, mdcContextMapForExecution)}.
     * @see BiConsumerWithTracing#BiConsumerWithTracing(BiConsumer, Deque, Map)
     * @see BiConsumerWithTracing
     */
    public static <T, U> BiConsumerWithTracing<T, U> withTracing(BiConsumer<T, U> origBiConsumer,
                                                                 Deque<Span> distributedTraceStackForExecution,
                                                                 Map<String, String> mdcContextMapForExecution) {
        return new BiConsumerWithTracing<>(origBiConsumer, distributedTraceStackForExecution, mdcContextMapForExecution);
    }

    @Override
    public void accept(T t, U u) {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(distributedTraceStackForExecution, mdcContextMapForExecution);

            origBiConsumer.accept(t, u);
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
