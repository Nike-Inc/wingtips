package com.nike.wingtips;

/**
 * Headers that are used to pass distributed tracing information across network boundaries.
 *
 * @author Robert Roeser
 */
public interface TraceHeaders {

    /**
     * The root id of the distributed trace. For Zipkin/B3 compatibility this header value should be an unsigned 64 bit long-integer encoded in lowercase hex
     * (see {@link TraceAndSpanIdGenerator#generateId()} for details).
     */
    String TRACE_ID = "X-B3-TraceId";

    /**
     * The span id of the current span. For Zipkin/B3 compatibility this header value should be an unsigned 64 bit long-integer encoded in lowercase hex
     * (see {@link TraceAndSpanIdGenerator#generateId()} for details).
     */
    String SPAN_ID = "X-B3-SpanId";

    /**
     * The span id of the parent span. For Zipkin/B3 compatibility this header value should be an unsigned 64 bit long-integer encoded in lowercase hex
     * (see {@link TraceAndSpanIdGenerator#generateId()} for details).
     */
    String PARENT_SPAN_ID = "X-B3-ParentSpanId";

    /**
     * The human-readable name of the current span.
     */
    String SPAN_NAME = "X-B3-SpanName";

    /**
     * Indicates if the trace's spans should be sampled or not. For Zipkin/B3 compatibility this header value should be set to "0" for false and "1"
     * for true when making calls, although it's advised that servers receiving calls should be more lenient and check if the caller sent "true" or
     * "false" values and honor those as well.
     */
    String TRACE_SAMPLED = "X-B3-Sampled";

}
