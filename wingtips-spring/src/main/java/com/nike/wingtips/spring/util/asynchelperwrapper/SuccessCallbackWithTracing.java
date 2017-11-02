package com.nike.wingtips.spring.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;
import org.springframework.util.concurrent.SuccessCallback;

import java.util.Deque;
import java.util.Map;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.unlinkTracingFromCurrentThread;

/**
 * A {@link SuccessCallback} that wraps the given original so that the given distributed tracing and MDC information is 
 * registered with the thread and therefore available during execution and unregistered after execution. 
 */
@SuppressWarnings("WeakerAccess")
public class SuccessCallbackWithTracing<T> implements SuccessCallback<T> {

    protected final SuccessCallback<T> origSuccessCallback;
    protected final Deque<Span> spanStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link SuccessCallbackWithTracing#SuccessCallbackWithTracing(SuccessCallback, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public SuccessCallbackWithTracing(SuccessCallback<T> origSuccessCallback) {
        this(origSuccessCallback, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public SuccessCallbackWithTracing(SuccessCallback<T> origSuccessCallback,
                                      Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origSuccessCallback,
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
    public SuccessCallbackWithTracing(SuccessCallback<T> origSuccessCallback,
                                      Deque<Span> spanStackForExecution,
                                      Map<String, String> mdcContextMapForExecution) {
        if (origSuccessCallback == null)
            throw new IllegalArgumentException("origSuccessCallback cannot be null");

        this.origSuccessCallback = origSuccessCallback;
        this.spanStackForExecution = spanStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new SuccessCallbackWithTracing(origSuccessCallback)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new SuccessCallbackWithTracing(origSuccessCallback)}.
     * @see SuccessCallbackWithTracing#SuccessCallbackWithTracing(SuccessCallback)
     * @see SuccessCallbackWithTracing
     */
    public static <T> SuccessCallbackWithTracing<T> withTracing(SuccessCallback<T> origSuccessCallback) {
        return new SuccessCallbackWithTracing<>(origSuccessCallback);
    }

    /**
     * Equivalent to calling {@code new SuccessCallbackWithTracing(origSuccessCallback, originalThreadInfo)} - this allows you
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
     * @return {@code new SuccessCallbackWithTracing(origSuccessCallback, originalThreadInfo)}.
     * @see SuccessCallbackWithTracing#SuccessCallbackWithTracing(SuccessCallback, Pair)
     * @see SuccessCallbackWithTracing
     */
    public static <T> SuccessCallbackWithTracing<T> withTracing(SuccessCallback<T> origSuccessCallback,
                                                         Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        return new SuccessCallbackWithTracing<>(origSuccessCallback, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new SuccessCallbackWithTracing(origSuccessCallback, spanStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new SuccessCallbackWithTracing(origSuccessCallback, spanStackForExecution, mdcContextMapForExecution)}.
     * @see SuccessCallbackWithTracing#SuccessCallbackWithTracing(SuccessCallback, Deque, Map)
     * @see SuccessCallbackWithTracing
     */
    public static <T> SuccessCallbackWithTracing<T> withTracing(SuccessCallback<T> origSuccessCallback,
                                                         Deque<Span> spanStackForExecution,
                                                         Map<String, String> mdcContextMapForExecution) {
        return new SuccessCallbackWithTracing<>(origSuccessCallback, spanStackForExecution, mdcContextMapForExecution);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onSuccess(T result) {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(spanStackForExecution, mdcContextMapForExecution);

            origSuccessCallback.onSuccess(result);
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
