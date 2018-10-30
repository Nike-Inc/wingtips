package com.nike.wingtips.apache.httpclient.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor;
import com.nike.wingtips.apache.httpclient.WingtipsHttpClientBuilder;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;

import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Contains utility helper methods for common code related to integrating Wingtips with Apache HTTP Client.
 *
 * <p>Wingtips users shouldn't need to use this class most of the time. Instead please refer to {@link
 * WingtipsHttpClientBuilder} for creating a {@link HttpClientBuilder} with tracing built-in, or {@link
 * WingtipsApacheHttpClientInterceptor} for request/response interceptors to integrate tracing into other {@link
 * HttpClientBuilder}s.
 *
 * @author Nic Munroe
 */
public class WingtipsApacheHttpClientUtil {

    /**
     * Intentionally protected - use the static methods.
     */
    @SuppressWarnings("WeakerAccess")
    protected WingtipsApacheHttpClientUtil() {
        // Do nothing
    }

    /**
     * Sets the tracing headers on the given {@link HttpRequest} with values from the given {@link Span}.
     * Does nothing if any of the given arguments are null (i.e. it is safe to pass null, but nothing will happen).
     * Usually you'd want to use {@link WingtipsHttpClientBuilder} or {@link WingtipsApacheHttpClientInterceptor}
     * to handle tracing propagation for you, however you can call this method to do manual propagation if needed.
     *
     * <p>This method conforms to the <a href="https://github.com/openzipkin/b3-propagation">B3 propagation spec</a>.
     *
     * @param request The {@link HttpRequest} to set tracing headers on. Can be null - if this is null then this
     * method will do nothing.
     * @param span The {@link Span} to get the tracing info from to set on the headers. Can be null - if this is null
     * then this method will do nothing.
     */
    public static void propagateTracingHeaders(HttpRequest request, Span span) {
        HttpRequestForPropagation requestForPropagation = (request == null)
                                                          ? null
                                                          : new HttpRequestForPropagation(request);
        HttpRequestTracingUtils.propagateTracingHeaders(requestForPropagation, span);
    }

    /**
     * Returns span name that should be used for the subspan surrounding the given request when {@link
     * com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy#getInitialSpanName(Object, HttpTagAndSpanNamingAdapter)}
     * returns null and you need a fallback.
     *
     * <p>This method returns {@code apachehttpclient_downstream_call-[HTTP_METHOD]}, e.g. for a GET
     * call, this would return {@code "apachehttpclient_downstream_call-GET"}.
     *
     * @param request The request that is about to be executed.
     * @return The fallback span name that should be used for the subspan surrounding the request.
     */
    public static String getFallbackSubspanSpanName(HttpRequest request) {
        RequestLine requestLine = request.getRequestLine();

        return HttpRequestTracingUtils.getFallbackSpanNameForHttpRequest(
            "apachehttpclient_downstream_call", requestLine.getMethod()
        );
    }

}
