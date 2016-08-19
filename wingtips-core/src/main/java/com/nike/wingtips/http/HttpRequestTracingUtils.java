package com.nike.wingtips.http;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.nike.wingtips.TraceHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Utility class for dealing with HTTP requests in relation to distributed tracing. Since different frameworks represent HTTP requests in different ways (e.g. Servlet API vs. Netty)
 * the methods in this class take in the {@link RequestWithHeaders} interface which lets us represent requests in a generic way. Different frameworks just need a trivial adapter to
 * expose their request class through the interface and all the methods in this class can be used.
 * <p/>
 * The main method you're likely to need in this class is {@link #fromRequestWithHeaders(RequestWithHeaders, List)}, which extracts a {@link Span} from the header information
 * in the request.
 * <p/>
 * NOTE: If span information exists in the request but is not explicitly set with {@link TraceHeaders#TRACE_SAMPLED} false, then
 * {@link #fromRequestWithHeaders(RequestWithHeaders, List)} will assume it should be sampleable.
 *
 * @author Nic Munroe
 */
public class HttpRequestTracingUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestTracingUtils.class);

    /**
     * The span name to use when a request contains the trace ID header but no span name.
     */
    public static final String UNSPECIFIED_SPAN_NAME = "UNSPECIFIED";

    /**
     * Intentionally private to force all access through static methods.
     */
    private HttpRequestTracingUtils() {
        // Nothing to do
    }

    /**
     * @param request The incoming request that may have {@link Span} information embedded in the headers. If this argument is null then this method will return null.
     * @param userIdHeaderKeys This list of header keys will be used to search the request headers for a user ID to set on the returned span. The user ID header keys
     *                         will be searched in list order, and the first non-empty user ID header value found will be used as the {@link Span#getUserId()}.
     *                         You can safely pass in null or an empty list for this argument if there is no user ID to extract; if you pass in null then the returned
     *                         span's {@link Span#getUserId()} will be null.
     *
     * @return The {@link Span} stored in the given request's trace headers (e.g. {@link TraceHeaders#TRACE_ID}, {@link TraceHeaders#TRACE_SAMPLED},
     *         {@link TraceHeaders#PARENT_SPAN_ID}, etc), or null if the request is null or doesn't contain the necessary headers. Since this method is for
     *         a server receiving a request, if this method returns a non-null span then its {@link Span#getSpanPurpose()} will be {@link SpanPurpose#SERVER}.
     *         <p>
     *         NOTE: {@link TraceHeaders#TRACE_ID} is the minimum header needed to return a non-null {@link Span}. If {@link TraceHeaders#SPAN_ID} is missing then
     *         a new span ID will be generated using {@link TraceAndSpanIdGenerator#generateId()}. If {@link TraceHeaders#TRACE_SAMPLED} is missing then the returned
     *         span will be sampleable. If {@link TraceHeaders#SPAN_NAME} is missing then {@link #UNSPECIFIED_SPAN_NAME} will be used as the span name.
     *         </p>
     */
    public static Span fromRequestWithHeaders(RequestWithHeaders request, List<String> userIdHeaderKeys) {
        if (request == null)
            return null;

        String traceId = getTraceId(request);
        if (traceId == null)
            return null;

        String spanName = getHeaderWithAttributeAsBackup(request, TraceHeaders.SPAN_NAME);
        if (spanName == null || spanName.length() == 0)
            spanName = UNSPECIFIED_SPAN_NAME;

        return Span.newBuilder(spanName, SpanPurpose.SERVER)
                   .withTraceId(traceId)
                   .withParentSpanId(getSpanIdFromRequest(request, TraceHeaders.PARENT_SPAN_ID, false))
                   .withSpanId(getSpanIdFromRequest(request, TraceHeaders.SPAN_ID, true))
                   .withSampleable(getSpanSampleableFlag(request))
                   .withUserId(getUserIdFromRequestWithHeaders(request, userIdHeaderKeys))
                   .build();
    }

    /**
     * Attempts to pull a valid ID for the user making the request.
     *
     * @return The HTTP Header value of the user ID if it exists, null otherwise. The request's headers will be inspected for the user ID using the given list of userIdHeaderKeys
     *          in list order - the first one found that is not null/empty will be returned.
     */
    public static String getUserIdFromRequestWithHeaders(RequestWithHeaders request, List<String> userIdHeaderKeys) {
        if (request == null || userIdHeaderKeys == null || userIdHeaderKeys.isEmpty()) {
            return null;
        }

        for (String userIdHeaderKey : userIdHeaderKeys) {
            String userId = getHeaderWithAttributeAsBackup(request, userIdHeaderKey);
            if (userId != null && !userId.isEmpty()) {
                return userId;
            }
        }

        return null;
    }

    /**
     * Extracts the {@link TraceHeaders#TRACE_SAMPLED} boolean value from the given request's headers or attributes if available, and defaults to true if the request doesn't contain
     * that header/attribute or if it's an invalid value. In other words, request values of "0" or "false" (ignoring case) will return false from this method,
     * everything else will return true.
     */
    protected static boolean getSpanSampleableFlag(RequestWithHeaders request) {
        String spanSampleableHeaderStr = getHeaderWithAttributeAsBackup(request, TraceHeaders.TRACE_SAMPLED);
        // Default to true (enabling trace sampling for requests that don't explicitly exclude it)
        boolean result = true;

        if ("0".equals(spanSampleableHeaderStr) || "false".equalsIgnoreCase(spanSampleableHeaderStr))
            result = false;

        return result;
    }

    /**
     * The given header from the given request as a span ID (or parent span ID), with the generateNewSpanIdIfNotFoundInRequest argument determining whether this method returns null
     * or a new random span ID from {@link TraceAndSpanIdGenerator#generateId()} when the request doesn't contain that header.
     */
    protected static String getSpanIdFromRequest(RequestWithHeaders request, String headerName, boolean generateNewSpanIdIfNotFoundInRequest) {
        String spanIdString = getHeaderWithAttributeAsBackup(request, headerName);
        if (spanIdString == null)
            return generateNewSpanIdIfNotFoundInRequest ? TraceAndSpanIdGenerator.generateId() : null;

        return spanIdString;
    }

    /**
     * Extracts the {@link TraceHeaders#TRACE_ID} from the given request's headers, or returns null if the request doesn't contain that header.
     */
    protected static String getTraceId(RequestWithHeaders request) {
        String requestTraceId = getHeaderWithAttributeAsBackup(request, TraceHeaders.TRACE_ID);

        logger.debug(String.format("TraceId from client is TraceId=%s", requestTraceId));

        return requestTraceId;
    }

    /**
     * Extracts the given {@code headerName} from the given request using {@link RequestWithHeaders#getHeader(String)} first and {@link RequestWithHeaders#getAttribute(String)} as
     * a backup in case the desired value was not found in the headers. If the desired value is missing from both then null will be returned. The result will be passed through
     * {@link String#trim()} before being returned if it is non-null.
     */
    protected static String getHeaderWithAttributeAsBackup(RequestWithHeaders request, String headerName) {
        Object result = request.getHeader(headerName);

        if (result == null || result.toString().trim().length() == 0)
            result = request.getAttribute(headerName);

        return (result == null) ? null : result.toString().trim();
    }
}
