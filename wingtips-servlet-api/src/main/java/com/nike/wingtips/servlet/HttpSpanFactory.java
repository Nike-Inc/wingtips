package com.nike.wingtips.servlet;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.http.HttpRequestTracingUtils;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * Creates {@link Span} objects extracted from a {@link HttpServletRequest}. NOTE: If a span exists in the request but is not explicitly defined with
 * {@link com.nike.wingtips.TraceHeaders#TRACE_SAMPLED} set to false, then {@link #fromHttpServletRequest(HttpServletRequest, List)} will default it to be sampleable.
 */
public class HttpSpanFactory {

    /**
     * Intentionally private to force all access through static methods.
     */
    private HttpSpanFactory() {
        // Nothing to do
    }

    /**
     * @param servletRequest The incoming request that may have {@link Span} information embedded in the headers. If this argument is null then this method will return null.
     * @param userIdHeaderKeys This list of header keys will be used to search the request headers for a user ID to set on the returned span. The user ID header keys
     *                         will be searched in list order, and the first non-empty user ID header value found will be used as the {@link Span#getUserId()}.
     *                         You can safely pass in null or an empty list for this argument if there is no user ID to extract; if you pass in null then the returned
     *                         span's {@link Span#getUserId()} will be null.
     *
     * @return The {@link Span} stored in the given request's trace headers (e.g. {@link com.nike.wingtips.TraceHeaders#TRACE_ID}, {@link com.nike.wingtips.TraceHeaders#TRACE_SAMPLED},
     *         {@link com.nike.wingtips.TraceHeaders#PARENT_SPAN_ID}, etc), or null if the request is null or doesn't contain the necessary headers.
     *         <br/>
     *         NOTE: {@link com.nike.wingtips.TraceHeaders#TRACE_ID} is the minimum header needed to return a non-null {@link Span}. If {@link com.nike.wingtips.TraceHeaders#SPAN_ID}
     *         is missing then a new span ID will be generated using {@link com.nike.wingtips.TraceAndSpanIdGenerator#generateId()}.
     *         If {@link com.nike.wingtips.TraceHeaders#TRACE_SAMPLED} is missing then the returned span will be sampleable. If {@link com.nike.wingtips.TraceHeaders#SPAN_NAME}
     *         is missing then {@link HttpRequestTracingUtils#UNSPECIFIED_SPAN_NAME} will be used as the span name.
     */
    public static Span fromHttpServletRequest(HttpServletRequest servletRequest, List<String> userIdHeaderKeys) {
        if (servletRequest == null)
            return null;

        return HttpRequestTracingUtils.fromRequestWithHeaders(new RequestWithHeadersServletAdapter(servletRequest), userIdHeaderKeys);
    }

    /**
     * @return A {@link Span} object created from the headers if they exist (see {@link #fromHttpServletRequest(HttpServletRequest, List)} for details), or if the headers don't
     *         have enough information then this will return a new root span with a span name based on the results of {@link #getSpanName(HttpServletRequest)} and user ID based
     *         on the result of {@link #getUserIdFromHttpServletRequest(HttpServletRequest, List)}. Since this method is for a server receiving a request
     *         the returned span's {@link Span#getSpanPurpose()} will be {@link SpanPurpose#SERVER}.
     */
    public static Span fromHttpServletRequestOrCreateRootSpan(HttpServletRequest servletRequest, List<String> userIdHeaderKeys) {
        Span span = fromHttpServletRequest(servletRequest, userIdHeaderKeys);

        if (span == null) {
            span = Span
                .generateRootSpanForNewTrace(getSpanName(servletRequest), SpanPurpose.SERVER)
                .withUserId(getUserIdFromHttpServletRequest(servletRequest, userIdHeaderKeys))
                .build();
        }

        return span;
    }

    /**
     * Attempts to pull a valid ID for the user making the request.
     *
     * @return The HTTP Header value of the user ID if it exists, null otherwise. The request's headers will be inspected for the user ID using the given list of userIdHeaderKeys
     *          in list order - the first one found that is not null/empty will be returned.
     */
    public static String getUserIdFromHttpServletRequest(HttpServletRequest servletRequest, List<String> userIdHeaderKeys) {
        if (servletRequest == null)
            return null;

        return HttpRequestTracingUtils.getUserIdFromRequestWithHeaders(new RequestWithHeadersServletAdapter(servletRequest), userIdHeaderKeys);
    }

    /**
     * @return Span name appropriate for a new root span for this request
     */
    public static String getSpanName(HttpServletRequest request) {
        // Try the servlet path first, and fall back to the raw request URI.
        String path = request.getServletPath();
        if (path == null || path.trim().length() == 0)
            path = request.getRequestURI();

        // Include the HTTP method in the returned value to help delineate which endpoint this request represents.
        return request.getMethod() + '_' + path;
    }

}
