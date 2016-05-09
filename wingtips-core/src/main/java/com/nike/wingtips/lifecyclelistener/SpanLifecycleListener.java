package com.nike.wingtips.lifecyclelistener;

import com.nike.wingtips.Span;

/**
 * Listener interface for {@link com.nike.wingtips.Tracer} that allows you to be notified during various span lifecycle events (span created/started, span sampled, span completed).
 * Call {@link com.nike.wingtips.Tracer#addSpanLifecycleListener(SpanLifecycleListener)} to add a specific listener.
 * <p/>
 * IMPORTANT NOTE: Tracing can become a severe bottleneck for high throughput services if the implementation of any of these methods are expensive. If any of the work you
 *                 need to do in these methods takes more than a few nanoseconds and you have a high throughput service you may want to consider doing the work asynchronously.
 *                 If you do anything here make sure you profile your application with and without the {@link SpanLifecycleListener} enabled to see how it impacts performance.
 *
 * @author Nic Munroe
 */
public interface SpanLifecycleListener {

    /**
     * This will be called when the given {@link Span} is created/started, and will be called whether or not the span is sampled.
     */
    void spanStarted(Span span);

    /**
     * This will be called when the given {@link Span} is determined to be sampleable - if this is called for a given span it will be called
     * immediately after {@link #spanStarted(Span)} is called.
     */
    void spanSampled(Span span);

    /**
     * This will be called when the given {@link Span} is completed, and will be called whether or not the span is sampled.
     */
    void spanCompleted(Span span);

}
