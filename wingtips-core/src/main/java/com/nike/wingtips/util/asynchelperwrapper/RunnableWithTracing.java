package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.unlinkTracingFromCurrentThread;

/**
 * A {@link Runnable} that wraps the given original so that the given distributed tracing and MDC information is
 * registered with the thread and therefore available during execution and unregistered after execution.
 */
@SuppressWarnings("WeakerAccess")
public class RunnableWithTracing implements Runnable {

    protected final Runnable origRunnable;
    protected final Deque<Span> spanStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Constructor that extracts the current tracing and MDC information from the current thread using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}, and forwards the information to
     * the {@link RunnableWithTracing#RunnableWithTracing(Runnable, Deque, Map)}
     * constructor. That tracing and MDC information will be associated with the thread when the given operation is
     * executed.
     * 
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     */
    public RunnableWithTracing(Runnable origRunnable) {
        this(origRunnable, Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
    public RunnableWithTracing(Runnable origRunnable,
                               Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origRunnable,
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
    public RunnableWithTracing(Runnable origRunnable,
                               Deque<Span> spanStackForExecution,
                               Map<String, String> mdcContextMapForExecution) {
        if (origRunnable == null)
            throw new IllegalArgumentException("origRunnable cannot be null");

        this.origRunnable = origRunnable;
        this.spanStackForExecution = spanStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    /**
     * Equivalent to calling {@code new RunnableWithTracing(origRunnable)} - this allows you to do a static method
     * import for cleaner looking code in some cases. This method ultimately extracts the current tracing and MDC
     * information from the current thread using {@link Tracer#getCurrentSpanStackCopy()} and {@link
     * MDC#getCopyOfContextMap()}. That tracing and MDC information will be associated with the thread when the given
     * operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * @return {@code new RunnableWithTracing(origRunnable)}.
     * @see RunnableWithTracing#RunnableWithTracing(Runnable)
     * @see RunnableWithTracing
     */
    public static  RunnableWithTracing withTracing(Runnable origRunnable) {
        return new RunnableWithTracing(origRunnable);
    }

    /**
     * Equivalent to calling {@code new RunnableWithTracing(origRunnable, originalThreadInfo)} - this allows you
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
     * @return {@code new RunnableWithTracing(origRunnable, originalThreadInfo)}.
     * @see RunnableWithTracing#RunnableWithTracing(Runnable, Pair)
     * @see RunnableWithTracing
     */
    public static  RunnableWithTracing withTracing(Runnable origRunnable,
                                                   Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        return new RunnableWithTracing(origRunnable, originalThreadInfo);
    }

    /**
     * Equivalent to calling {@code
     * new RunnableWithTracing(origRunnable, spanStackForExecution, mdcContextMapForExecution)} -
     * this allows you to do a static method import for cleaner looking code in some cases. This method uses the given
     * trace and MDC information, which will be associated with the thread when the given operation is executed.
     *
     * <p>The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in
     * null for the operation).
     *
     * <p>The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     *
     * @return {@code new RunnableWithTracing(origRunnable, spanStackForExecution, mdcContextMapForExecution)}.
     * @see RunnableWithTracing#RunnableWithTracing(Runnable, Deque, Map)
     * @see RunnableWithTracing
     */
    public static  RunnableWithTracing withTracing(Runnable origRunnable,
                                                   Deque<Span> spanStackForExecution,
                                                   Map<String, String> mdcContextMapForExecution) {
        return new RunnableWithTracing(origRunnable, spanStackForExecution, mdcContextMapForExecution);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void run() {
        TracingState originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingToCurrentThread(spanStackForExecution, mdcContextMapForExecution);

            origRunnable.run();
        }
        finally {
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }
}
