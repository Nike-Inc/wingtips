package com.nike.wingtips.servlet;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.tags.KnownZipkinTags;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * Creates {@link Span} objects extracted from a {@link HttpServletRequest}. NOTE: If a span exists in the request but is not explicitly defined with
 * {@link com.nike.wingtips.TraceHeaders#TRACE_SAMPLED} set to false, then {@link #fromHttpServletRequest(HttpServletRequest, List)} will default it to be sampleable.
 */
public class HttpSpanFactory {

    @SuppressWarnings("WeakerAccess")
    protected static final String SPRING_BEST_MATCHING_PATTERN_REQUEST_ATTRIBUTE_KEY =
        "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";

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
     * Tries to determine the URI path template for this request, and returns null if no such template could be found.
     * i.e. We try to return {@code /foo/:id} (or however your framework shows path template) rather than
     * {@code /foo/12345}.
     *
     * <p>The rules this method follows for trying to find the path template:
     *
     * <ol>
     *     <li>
     *         We look in {@link HttpServletRequest#getAttribute(String)} for a request attribute matching
     *         {@link KnownZipkinTags#HTTP_ROUTE} ("http.route"). If we find a non-empty http.route attribute, then
     *         we'll use it as the path template.
     *     </li>
     *     <li>
     *         If there was no http.route request attribute, then we'll look for Spring's
     *         "best matching pattern" attribute ({@value #SPRING_BEST_MATCHING_PATTERN_REQUEST_ATTRIBUTE_KEY}).
     *     </li>
     * </ol>
     *
     * In particular, note that we're not falling back to {@link HttpServletRequest#getServletPath()}, because while
     * that works in some cases for raw Servlets, many popular Servlet frameworks don't honor the spirit of that
     * method's javadocs and return full URIs. If you're working in a system where you know {@link
     * HttpServletRequest#getServletPath()} is safe to use, then you can call {@code
     * request.setAttribute(KnownZipkinTags.HTTP_ROUTE, request.getServletPath())} and this method will use it.
     * A similar trick of setting http.route as a request attribute can be used anytime by any code to set the path
     * template that will be used.
     *
     * @return The URI path template for this request, or null if no such path template could be determined.
     */
    public static @Nullable String determineUriPathTemplate(@Nullable HttpServletRequest request) {
        // Null might be passed in. If so, there's nothing we can do except return null ourselves.
        if (request == null) {
            return null;
        }

        // Try the Zipkin http.route attribute first. If this exists, then it is definitively what we want.
        String path = getRequestAttributeAsString(request, KnownZipkinTags.HTTP_ROUTE);

        if (StringUtils.isNotBlank(path)) {
            // Found http.route. Use it.
            return path;
        }

        // The Zipkin http.route attribute was null or blank, so try the Spring "best matching pattern" attribute.
        path = getRequestAttributeAsString(request, SPRING_BEST_MATCHING_PATTERN_REQUEST_ATTRIBUTE_KEY);

        if (StringUtils.isNotBlank(path)) {
            // Found Spring best matching pattern. Use it.
            return path;
        }

        // At this point we've struck out on finding a path template.
        return null;
    }

    /**
     * Generates a span name appropriate for a new root span for this request.
     *
     * <p>NOTE: Due to problems that can crop up in visualization and analysis systems when there is high cardinality on
     * span names, this method does its best to discover the "path template" and use that in the span name rather than
     * raw full URI path. i.e. We try to return {@code /foo/:id} (or however your framework shows path template) rather
     * than {@code /foo/12345}. We use {@link #determineUriPathTemplate(HttpServletRequest)} for this purpose - see
     * the javadocs for that method for the rules that are used.
     *
     * <p>All returned span names will start with the {@link HttpServletRequest#getMethod()} for the request, but if
     * we can't discover the path template, then the HTTP method is all that the span name will contain.
     *
     * @return A Span name appropriate for a new root span for this request.
     */
    public static @NotNull String getSpanName(@Nullable HttpServletRequest request) {
        /*
            NOTE: We used to use request.getServletPath() and request.getRequestURI() as last resorts, but that leads
            to high cardinality span names (even with request.getServletPath(), thanks to different frameworks not
            really honoring the contract for that method), which is really bad for visualization systems that try to
            do any kind of analytics on your spans. Now that we have span tags and are auto-tagging spans with the
            full path, we won't be missing data if we drop the full path from the span name. So if we don't have
            a path at this point then we'll do what Zipkin does and just default to the HTTP method.
        */

        String pathTemplate = determineUriPathTemplate(request);
        String method = (request == null) ? null : request.getMethod();

        // HttpRequestTracingUtils.generateSafeSpanName() gives us what we want, and properly handles the case
        //      where everything passed into it is null.
        return HttpRequestTracingUtils.generateSafeSpanName(method, pathTemplate, (Integer)null);
    }

    private static String getRequestAttributeAsString(HttpServletRequest request, String attrName) {
        Object attrValue = request.getAttribute(attrName);
        return (attrValue == null) ? null : attrValue.toString();
    }

}
