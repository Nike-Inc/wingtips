package com.nike.wingtips.zipkin.util;

import java.io.Flushable;

/**
 * <p>
 *     A interface for sending Zipkin spans to a Zipkin server. This is similar to a Zipkin {@code SpanCollector} and you can easily create
 *     an adapter that wraps a native Zipkin <a href="https://github.com/openzipkin/brave">Brave</a> {@code SpanCollector} with this interface
 *     if you prefer the features, flexibility, and numerous transport options provided by the native Zipkin {@code SpanCollector}s over the
 *     no-dependencies HTTP-only default implementation provided by {@link ZipkinSpanSenderDefaultHttpImpl}.
 * </p>
 *
 * @author Nic Munroe
 */
public interface ZipkinSpanSender extends Flushable {

    /**
     * <p>
     *     "Handles" the given Zipkin span. In a typical implementation spans are stored in a threadsafe collection for batching until some trigger
     *     is hit that causes the span batch to be sent to the Zipkin server (e.g. scheduled job that sends batches every x period of time, or
     *     after a batch size threshold is hit, or both).
     * </p>
     * <p>
     *     <b>IMPORTANT NOTE:</b> DO NOT BLOCK IN THIS METHOD'S IMPLEMENTATION. If you send data to the Zipkin server directly based on this method
     *     call then it should be split out into a separate thread to do the work. The only thing that should happen on the calling thread is to put
     *     the span into a queue for later processing, or spinning off a job on a separate thread.
     * </p>
     *
     * @param span The Zipkin span to handle.
     */
    void handleSpan(zipkin.Span span);

    /**
     * <p>
     *     Forces any queued/batched spans to be sent to the Zipkin server as quickly as possible.
     * </p>
     * <p>
     *     <b>IMPORTANT NOTE:</b> DO NOT BLOCK IN THIS METHOD'S IMPLEMENTATION. This method should spin off a task on a separate thread for
     *     executing the push-to-Zipkin-server logic.
     * </p>
     */
    @Override
    void flush();
}
