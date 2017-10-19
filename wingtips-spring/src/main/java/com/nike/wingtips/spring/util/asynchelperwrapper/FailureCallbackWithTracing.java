package com.nike.wingtips.spring.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;
import org.springframework.util.concurrent.FailureCallback;

import java.util.Deque;
import java.util.Map;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.unlinkTracingFromCurrentThread;

/**
 * A {@link FailureCallback} that wraps the given original so that the given distributed tracing and MDC information is
 * registered with the thread and therefore available during execution and unregistered after execution.
 */
@SuppressWarnings("WeakerAccess")
public class FailureCallbackWithTracing implements FailureCallback {

    protected final FailureCallback origFailureCallback;
    protected final Deque<Span> distributedTraceStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link FailureCallbackWithTracing#FailureCallbackWithTracing(FailureCallback, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     * 
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public FailureCallbackWithTracing(FailureCallback origFailureCallback) {
        this(origFailureCallback, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public FailureCallbackWithTracing(FailureCallback origFailureCallback,
                                      Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origFailureCallback,
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
    public FailureCallbackWithTracing(FailureCallback origFailureCallback,
                                      Deque<Span> distributedTraceStackForExecution,
                                      Map<String, String> mdcContextMapForExecution) {
        if (origFailureCallback == null)
            throw new IllegalArgumentException("origFailureCallback cannot be null");

        this.origFailureCallback = origFailureCallback;
        this.distributedTraceStackForExecution = distributedTraceStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new FailureCallbackWithTracing(origFailureCallback)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new FailureCallbackWithTracing(origFailureCallback)}.
     * @see FailureCallbackWithTracing#FailureCallbackWithTracing(FailureCallback)
     * @see FailureCallbackWithTracing
     */
    public static FailureCallbackWithTracing withTracing(FailureCallback origFailureCallback) {
        return new FailureCallbackWithTracing(origFailureCallback);
    }

    /**
     * Equivalent to calling {@code new FailureCallbackWithTracing(origFailureCallback, originalThreadInfo)} - this allows you
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
     * @return {@code new FailureCallbackWithTracing(origFailureCallback, originalThreadInfo)}.
     * @see FailureCallbackWithTracing#FailureCallbackWithTracing(FailureCallback, Pair)
     * @see FailureCallbackWithTracing
     */
    public static FailureCallbackWithTracing withTracing(FailureCallback origFailureCallback,
                                                         Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        return new FailureCallbackWithTracing(origFailureCallback, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new FailureCallbackWithTracing(origFailureCallback, distributedTraceStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new FailureCallbackWithTracing(origFailureCallback, distributedTraceStackForExecution, mdcContextMapForExecution)}.
     * @see FailureCallbackWithTracing#FailureCallbackWithTracing(FailureCallback, Deque, Map)
     * @see FailureCallbackWithTracing
     */
    public static FailureCallbackWithTracing withTracing(FailureCallback origFailureCallback,
                                                         Deque<Span> distributedTraceStackForExecution,
                                                         Map<String, String> mdcContextMapForExecution) {
        return new FailureCallbackWithTracing(origFailureCallback, distributedTraceStackForExecution, mdcContextMapForExecution);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onFailure(Throwable ex) {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(distributedTraceStackForExecution, mdcContextMapForExecution);

            origFailureCallback.onFailure(ex);
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
