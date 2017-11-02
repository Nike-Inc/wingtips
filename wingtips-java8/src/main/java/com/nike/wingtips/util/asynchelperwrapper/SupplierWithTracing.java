package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.function.Supplier;

import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

/**
 * A {@link Supplier} that wraps the given original so that the given distributed tracing and MDC information is
 * registered with the thread and therefore available during execution and unregistered after execution.
 */
@SuppressWarnings("WeakerAccess")
public class SupplierWithTracing<U> implements Supplier<U> {

    protected final Supplier<U> origSupplier;
    protected final Deque<Span> spanStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link SupplierWithTracing#SupplierWithTracing(Supplier, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public SupplierWithTracing(Supplier<U> origSupplier) {
        this(origSupplier, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public SupplierWithTracing(Supplier<U> origSupplier,
                               Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origSupplier,
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
    public SupplierWithTracing(Supplier<U> origSupplier,
                               Deque<Span> spanStackForExecution,
                               Map<String, String> mdcContextMapForExecution) {
        if (origSupplier == null)
            throw new IllegalArgumentException("origSupplier cannot be null");

        this.origSupplier = origSupplier;
        this.spanStackForExecution = spanStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new SupplierWithTracing(origSupplier)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new SupplierWithTracing(origSupplier)}.
     * @see SupplierWithTracing#SupplierWithTracing(Supplier)
     * @see SupplierWithTracing
     */
    public static <U> SupplierWithTracing<U> withTracing(Supplier<U> origSupplier) {
        return new SupplierWithTracing<>(origSupplier);
    }

    /**
     * Equivalent to calling {@code new SupplierWithTracing(origSupplier, originalThreadInfo)} - this allows you
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
     * @return {@code new SupplierWithTracing(origSupplier, originalThreadInfo)}.
     * @see SupplierWithTracing#SupplierWithTracing(Supplier, Pair)
     * @see SupplierWithTracing
     */
    public static <U> SupplierWithTracing<U> withTracing(Supplier<U> origSupplier,
                                                         Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        return new SupplierWithTracing<>(origSupplier, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new SupplierWithTracing(origSupplier, spanStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new SupplierWithTracing(origSupplier, spanStackForExecution, mdcContextMapForExecution)}.
     * @see SupplierWithTracing#SupplierWithTracing(Supplier, Deque, Map)
     * @see SupplierWithTracing
     */
    public static <U> SupplierWithTracing<U> withTracing(Supplier<U> origSupplier,
                                                         Deque<Span> spanStackForExecution,
                                                         Map<String, String> mdcContextMapForExecution) {
        return new SupplierWithTracing<>(origSupplier, spanStackForExecution, mdcContextMapForExecution);
    }

    @Override
    public U get() {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(spanStackForExecution, mdcContextMapForExecution);

            return origSupplier.get();
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
