package com.nike.wingtips.util;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.asynchelperwrapper.RunnableWithTracing;

import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.Map;

/**
 * DTO for holding/passing tracing information around. Since this extends {@code Pair<Deque<Span>, Map<String, String>>}
 * you can use this with the async helper class constructors (like {@link
 * RunnableWithTracing#RunnableWithTracing(Runnable, Pair)}) and the {@code AsyncWingtipsHelper*.*withTracing(*, Pair)}
 * helper methods.
 *
 * <p>NOTE: This is usually not needed unless you're doing asynchronous processing and need to pass tracing state across
 * thread boundaries.
 */
@SuppressWarnings("WeakerAccess")
public class TracingState extends Pair<Deque<Span>, Map<String, String>> {

    /**
     * The span stack associated with this tracing state.
     */
    public final Deque<Span> spanStack;
    /**
     * The MDC context associated with this tracing state.
     */
    public final Map<String, String> mdcInfo;

    /**
     * Creates a new instance with the given tracing information. NOTE: This is usually not needed unless you're doing
     * asynchronous processing and need to pass tracing state across thread boundaries.
     *
     * @param spanStack The span stack for this tracing state.
     * @param mdcInfo The logger MDC context info for this tracing state.
     */
    public TracingState(Deque<Span> spanStack, Map<String, String> mdcInfo) {
        this.spanStack = spanStack;
        this.mdcInfo = mdcInfo;
    }

    /**
     * @return A *copy* of the current thread's tracing information - retrieved by calling {@link
     * Tracer#getCurrentTracingStateCopy()}. Since this creates copies of the span stack and MDC info it can be
     * have a noticeable performance impact if used too many times (i.e. tens or hundreds of times per request for high
     * throughput services). NOTE: This is usually not needed unless you're doing asynchronous processing and need to
     * pass tracing state across thread boundaries.
     */
    public static TracingState getCurrentThreadTracingState() {
        return Tracer.getInstance().getCurrentTracingStateCopy();
    }

    /**
     * @return The span stack {@link #spanStack} associated with this instance.
     */
    @Override
    public Deque<Span> getLeft() {
        return spanStack;
    }

    /**
     * @return The MDC context information {@link #mdcInfo} associated with this instance.
     */
    @Override
    public Map<String, String> getRight() {
        return mdcInfo;
    }

    /**
     * This method is not supported - create a new {@link TracingState} rather than modifying this one.
     */
    @Override
    public Map<String, String> setValue(Map<String, String> value) {
        throw new UnsupportedOperationException("TracingState is immutable - please create a new TracingState instead");
    }

    /**
     * @return The "active" (a.k.a. "current") span from {@link #spanStack}, or null if {@link #spanStack} is null
     * or empty.
     */
    public @Nullable Span getActiveSpan() {
        if (spanStack == null) {
            return null;
        }

        return spanStack.peek();
    }
}
