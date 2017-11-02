package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.function.Consumer;

import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

/**
 * A {@link Consumer} that wraps the given original so that the given distributed tracing and MDC information is 
 * registered with the thread and therefore available during execution and unregistered after execution. 
 */
@SuppressWarnings("WeakerAccess")
public class ConsumerWithTracing<T> implements Consumer<T> {

    protected final Consumer<T> origConsumer;
    protected final Deque<Span> spanStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link ConsumerWithTracing#ConsumerWithTracing(Consumer, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public ConsumerWithTracing(Consumer<T> origConsumer) {
        this(origConsumer, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public ConsumerWithTracing(Consumer<T> origConsumer,
                               Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origConsumer,
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
    public ConsumerWithTracing(Consumer<T> origConsumer,
                               Deque<Span> spanStackForExecution,
                               Map<String, String> mdcContextMapForExecution) {
        if (origConsumer == null)
            throw new IllegalArgumentException("origConsumer cannot be null");

        this.origConsumer = origConsumer;
        this.spanStackForExecution = spanStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new ConsumerWithTracing(origConsumer)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new ConsumerWithTracing(origConsumer)}.
     * @see ConsumerWithTracing#ConsumerWithTracing(Consumer)
     * @see ConsumerWithTracing
     */
    public static <T> ConsumerWithTracing<T> withTracing(Consumer<T> origConsumer) {
        return new ConsumerWithTracing<>(origConsumer);
    }

    /**
     * Equivalent to calling {@code new ConsumerWithTracing(origConsumer, originalThreadInfo)} - this allows you
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
     * @return {@code new ConsumerWithTracing(origConsumer, originalThreadInfo)}.
     * @see ConsumerWithTracing#ConsumerWithTracing(Consumer, Pair)
     * @see ConsumerWithTracing
     */
    public static <T> ConsumerWithTracing<T> withTracing(Consumer<T> origConsumer,
                                                         Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        return new ConsumerWithTracing<>(origConsumer, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new ConsumerWithTracing(origConsumer, spanStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new ConsumerWithTracing(origConsumer, spanStackForExecution, mdcContextMapForExecution)}.
     * @see ConsumerWithTracing#ConsumerWithTracing(Consumer, Deque, Map)
     * @see ConsumerWithTracing
     */
    public static <T> ConsumerWithTracing<T> withTracing(Consumer<T> origConsumer,
                                                         Deque<Span> spanStackForExecution,
                                                         Map<String, String> mdcContextMapForExecution) {
        return new ConsumerWithTracing<>(origConsumer, spanStackForExecution, mdcContextMapForExecution);
    }
    
    @Override
    public void accept(T t) {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(spanStackForExecution, mdcContextMapForExecution);

            origConsumer.accept(t);
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
