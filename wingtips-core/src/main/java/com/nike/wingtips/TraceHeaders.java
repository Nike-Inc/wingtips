package com.nike.wingtips;

/**
 * Headers that are used to pass distributed tracing information across network boundaries.
 *
 * @author Robert Roeser
 */
public interface TraceHeaders {

    /**
     * The root id of the distributed trace.
     */
    String TRACE_ID = "X-B3-TraceId";

    /**
     * The span id of the current span.
     */
    String SPAN_ID = "X-B3-SpanId";

    /**
     * The span id of the parent span.
     */
    String PARENT_SPAN_ID = "X-B3-ParentSpanId";

    /**
     * The human-readable name of the current span.
     */
    String SPAN_NAME = "X-B3-SpanName";

    /**
     * Indicates if the trace's spans should be sampled or not.
     */
    String TRACE_SAMPLED = "X-B3-Sampled";

}
