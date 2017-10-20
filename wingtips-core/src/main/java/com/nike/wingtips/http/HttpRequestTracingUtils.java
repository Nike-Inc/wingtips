package com.nike.wingtips.http;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.nike.wingtips.TraceHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.nike.wingtips.TraceHeaders.PARENT_SPAN_ID;
import static com.nike.wingtips.TraceHeaders.SPAN_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_SAMPLED;

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

    /**
     * Sets the tracing headers on the given {@link HttpObjectForPropagation} with values from the given {@link Span}.
     * Does nothing if any of the given arguments are null (i.e. it is safe to pass null, but nothing will happen).
     *
     * <p>This method conforms to the <a href="https://github.com/openzipkin/b3-propagation">B3 propagation spec</a>.
     *
     * @param httpObjectForPropagation The {@link HttpObjectForPropagation} to set tracing headers on. Can be null -
     * if this is null then this method will do nothing.
     * @param span The {@link Span} to get the tracing info from to set on the headers. Can be null - if this is null
     * then this method will do nothing.
     */
    public static void propagateTracingHeaders(HttpObjectForPropagation httpObjectForPropagation, Span span) {
        if (span == null || httpObjectForPropagation == null)
            return;

        httpObjectForPropagation.setHeader(TRACE_ID, span.getTraceId());
        httpObjectForPropagation.setHeader(SPAN_ID, span.getSpanId());
        httpObjectForPropagation.setHeader(TRACE_SAMPLED, (span.isSampleable()) ? "1" : "0");
        if (span.getParentSpanId() != null)
            httpObjectForPropagation.setHeader(PARENT_SPAN_ID, span.getParentSpanId());
    }

    /**
     * A helper method for returning a reasonable {@link Span#getSpanName()} for a subspan surrounding a downstream
     * HTTP request. Returns {@code [PREFIX]-[HTTP_METHOD]_[REQUEST_URI]} if prefix is non-null, or {@code
     * [HTTP_METHOD]_[REQUEST_URI]} if prefix is null. Note that the uri will be stripped of any query string in
     * the result.
     *
     * <p>For example, if you pass "downstream_call", "GET", and "https://foo.bar/baz?stuff=things" to this method,
     * then it would return {@code "downstream_call-GET_https://foo.bar/baz"}.
     *
     * <p>NOTE: This span name format is not required for anything - you can name subspans anything you want. This
     * method is just here as a convenience.
     *
     * @param prefix The prefix that should be added first. This can be null - if this is null then the result will
     * not contain any prefix and will be based solely on httpMethod and uri.
     * @param httpMethod The HTTP method for the downstream call. Should not be null.
     * @param uri The URI for the downstream call. This method will strip any query string for the returned result.
     * Should not be null.
     * @return The name that should be used for the subspan surrounding the call.
     */
    public static String getSubspanSpanNameForHttpRequest(String prefix, String httpMethod, String uri) {
        uri = maskQueryString(uri);

        StringBuilder sb = new StringBuilder();

        if (prefix != null) {
            sb.append(prefix).append("-");
        }

        sb.append(httpMethod).append("_").append(uri);

        return sb.toString();
    }

    protected static String maskQueryString(String original) {
        if (original == null) {
            return null;
        }

        int indexOfQueryString = original.indexOf('?');

        if (indexOfQueryString == -1) {
            // No query string, so return the original.
            return original;
        }

        return original.substring(0, indexOfQueryString);
    }

    /**
     * Converts the given boolean to the B3-specification's value for the {@link TraceHeaders#TRACE_SAMPLED} header.
     * See https://github.com/openzipkin/b3-propagation - we should pass "1" if it's sampleable, "0" if it's not.
     *
     * @param sampleable Whether or not the span is sampleable.
     * @return "1" if passed true, "0" if passed false.
     */
    public static String convertSampleableBooleanToExpectedB3Value(boolean sampleable) {
        return (sampleable) ? "1" : "0";
    }
}
