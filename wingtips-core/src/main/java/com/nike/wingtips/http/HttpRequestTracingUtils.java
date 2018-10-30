package com.nike.wingtips.http;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
     * Converts the given boolean to the B3-specification's value for the {@link TraceHeaders#TRACE_SAMPLED} header.
     * See https://github.com/openzipkin/b3-propagation - we should pass "1" if it's sampleable, "0" if it's not.
     *
     * @param sampleable Whether or not the span is sampleable.
     * @return "1" if passed true, "0" if passed false.
     */
    public static String convertSampleableBooleanToExpectedB3Value(boolean sampleable) {
        return (sampleable) ? "1" : "0";
    }

    /**
     * This method does a best-effort to extract the necessary information from the given adapter, and then returns
     * the result of calling {@link #generateSafeSpanName(String, String, Integer)}. See that method for full details,
     * but essentially the returned span name should be "safe" for visualization/analytics systems that expect low
     * cardinality span names, and this logic mimics what Zipkin does for its span names.
     *
     * <p>The returned span name format will be the HTTP method followed by a space, and then the path template
     * (if one exists). If the HTTP response status code is 3xx, then the path template is replaced with "redirected",
     * and if the status code is 404 then the path template is replaced with "not_found". If the HTTP method is null
     * or blank, then "UNKNOWN_HTTP_METHOD" will be used for the HTTP method in the returned span name.
     *
     * <p>Examples that show these rules:
     * <ul>
     *     <li>
     *         HTTP method "GET", path template "/some/path/tmplt", and response status code not 3xx and not 404:
     *         {@code "GET /some/path/tmplt"}
     *     </li>
     *     <li>
     *         HTTP method "GET", and response status code 3xx:
     *         {@code "GET redirected"}
     *     </li>
     *     <li>
     *         HTTP method "GET", and response status code 404:
     *         {@code "GET not_found"}
     *     </li>
     *     <li>
     *         HTTP method "GET", null or blank path template, and response status code not 3xx and not 404:
     *         {@code "GET"}
     *     </li>
     *     <li>
     *         Null or blank HTTP method, path template "/some/path/tmplt", and response status code not 3xx and not
     *         404: {@code "UNKNOWN_HTTP_METHOD /some/path/tmplt"}
     *     </li>
     *     <li>
     *         Null or blank HTTP method, and response status code 3xx:
     *         {@code "UNKNOWN_HTTP_METHOD redirected"}
     *     </li>
     *     <li>
     *         Null or blank HTTP method, and response status code 404:
     *         {@code "UNKNOWN_HTTP_METHOD not_found"}
     *     </li>
     *     <li>
     *         Null or blank HTTP method, null or blank path template, and response status code not 3xx and not 404:
     *         {@code "UNKNOWN_HTTP_METHOD"}
     *     </li>
     * </ul>
     *
     * @param request The request object - can be null although that probably means HTTP method and path template
     * won't be retrievable.
     * @param response The response object - can be null although that probably means HTTP response status code won't
     * be retrievable.
     * @param adapter The adapter that knows how to extract the required data from the request and response. Should
     * never be null.
     * @return The result of calling {@link #generateSafeSpanName(String, String, Integer)} with the HTTP method,
     * path template, and HTTP response status code extracted from the given adapter and using the given request and
     * response objects.
     */
    @SuppressWarnings("ConstantConditions")
    public static @NotNull <REQ, RES> String generateSafeSpanName(
        @Nullable REQ request,
        @Nullable RES response,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    ) {
        String httpMethod = (adapter == null) ? null : adapter.getRequestHttpMethod(request);
        String pathTemplate = (adapter == null) ? null : adapter.getRequestUriPathTemplate(request, response);
        Integer responseStatusCode = (adapter == null) ? null : adapter.getResponseHttpStatus(response);

        return generateSafeSpanName(httpMethod, pathTemplate, responseStatusCode);
    }

    /**
     * This method generates a span name from the given arguments that is "safe" for visualization/analytics systems
     * that expect low cardinality span names. The logic in this method mimics what Zipkin does for its span names.
     *
     * <p>The returned span name format will be the HTTP method followed by a space, and then the path template
     * (if one exists). If the HTTP response status code is 3xx, then the path template is replaced with "redirected",
     * and if the status code is 404 then the path template is replaced with "not_found". If the HTTP method is null
     * or blank, then "UNKNOWN_HTTP_METHOD" will be used for the HTTP method in the returned span name.
     *
     * <p>Examples that show these rules:
     * <ul>
     *     <li>
     *         HTTP method "GET", path template "/some/path/tmplt", and response status code not 3xx and not 404:
     *         {@code "GET /some/path/tmplt"}
     *     </li>
     *     <li>
     *         HTTP method "GET", and response status code 3xx:
     *         {@code "GET redirected"}
     *     </li>
     *     <li>
     *         HTTP method "GET", and response status code 404:
     *         {@code "GET not_found"}
     *     </li>
     *     <li>
     *         HTTP method "GET", null or blank path template, and response status code not 3xx and not 404:
     *         {@code "GET"}
     *     </li>
     *     <li>
     *         Null or blank HTTP method, path template "/some/path/tmplt", and response status code not 3xx and not
     *         404: {@code "UNKNOWN_HTTP_METHOD /some/path/tmplt"}
     *     </li>
     *     <li>
     *         Null or blank HTTP method, and response status code 3xx:
     *         {@code "UNKNOWN_HTTP_METHOD redirected"}
     *     </li>
     *     <li>
     *         Null or blank HTTP method, and response status code 404:
     *         {@code "UNKNOWN_HTTP_METHOD not_found"}
     *     </li>
     *     <li>
     *         Null or blank HTTP method, null or blank path template, and response status code not 3xx and not 404:
     *         {@code "UNKNOWN_HTTP_METHOD"}
     *     </li>
     * </ul>
     *
     * @param requestHttpMethod The request HTTP method - can be null. If you pass null, then "UNKNOWN_HTTP_METHOD"
     * will be used for the HTTP method.
     * @param pathTemplate The *low-cardinality* URI path template for the request (e.g. {@code /foo/:id} rather than
     * {@code /foo/12345}) - can be null. If you pass null, then path template will be omitted.
     * @param responseStatusCode The HTTP response status code associated with the request - can be null. If this
     * is not null and represents a 3xx response, then "redirected" will be used as the path template to avoid high
     * cardinality issues. Similarly, a 404 status code will result in "not_found" being used as the path template.
     * @return The concatenation of HTTP method, followed by a space, followed by the path template. See the rest of
     * this method's javadocs for details on how null arguments and/or the HTTP response status code can adjust the
     * returned value.
     */
    public static @NotNull String generateSafeSpanName(
        @Nullable String requestHttpMethod,
        @Nullable String pathTemplate,
        @Nullable Integer responseStatusCode
    ) {
        if (StringUtils.isBlank(requestHttpMethod)) {
            requestHttpMethod = "UNKNOWN_HTTP_METHOD";
        }

        if (responseStatusCode != null) {
            if (responseStatusCode / 100 == 3) {
                return requestHttpMethod + " redirected";
            }
            else if (responseStatusCode == 404) {
                return requestHttpMethod + " not_found";
            }
        }

        return (StringUtils.isBlank(pathTemplate))
               ? requestHttpMethod
               : requestHttpMethod + " " + pathTemplate;
    }

    /**
     * A helper method for returning a reasonable fallback {@link Span#getSpanName()} for a span around an HTTP
     * request - good for when you need a span name but {@link
     * com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy#getInitialSpanName(Object, HttpTagAndSpanNamingAdapter)}
     * returns null.
     *
     * <p>This method returns {@code [PREFIX]-[HTTP_METHOD]}, or simply {@code [HTTP_METHOD]} if prefix is null or
     * blank. If the given HTTP method is null or blank, then "UNKNOWN_HTTP_METHOD" will be used. This method will
     * therefore never return null.
     *
     * <p>For example, if you pass "downstream_call", and "GET" to this method, then it would return
     * {@code "downstream_call-GET"}.
     *
     * <p>NOTE: This span name format is not required for anything - you can name spans anything you want. This
     * method is just here as a convenience. You should be aware, though, that some distributed tracing visualization
     * and analysis systems expect span names to be low cardinality, so adding the raw URL to the span name is
     * discouraged (and enforced by this method only taking a presumably-low-cardinality prefix and
     * definitely-low-cardinality HTTP method). Adding the low-cardinality path template is a good idea
     * (e.g. {@code /foo/:id} instead of {@code /foo/12345}), which is what {@link
     * HttpTagAndSpanNamingAdapter#getRequestUriPathTemplate(Object, Object)} is for, and which itself is used by
     * the various naming methods of {@link com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy}. So this method
     * is really only meant to be used as a fallback in case the naming strategy/adapter returns null.
     *
     * <p>ALSO NOTE: The {@link #generateSafeSpanName(Object, Object, HttpTagAndSpanNamingAdapter)} and
     * {@link #generateSafeSpanName(String, String, Integer)} methods are similar to this one, but meant for a slightly
     * different use case where you have access to things like the request, response, URI/path template, and/or
     * HTTP response status code. Use those methods where possible - this method is meant as a last resort.
     *
     * @param prefix The prefix that should be added first. This can be null - if this is null then the result will
     * not contain any prefix and will be based solely on httpMethod.
     * @param httpMethod The HTTP method for the downstream call. This can be null (although it's not recommended) -
     * if this is null or blank then "UNKNOWN_HTTP_METHOD" will be used instead.
     * @return The fallback span name for the given prefix and HTTP method - never returns null.
     */
    public static @NotNull String getFallbackSpanNameForHttpRequest(
        @Nullable String prefix,
        @Nullable String httpMethod
    ) {
        if (StringUtils.isBlank(prefix)) {
            prefix = null;
        }

        if (StringUtils.isBlank(httpMethod)) {
            httpMethod = "UNKNOWN_HTTP_METHOD";
        }

        return (prefix == null)
               ? httpMethod
               : prefix + "-" + httpMethod;
    }
}
