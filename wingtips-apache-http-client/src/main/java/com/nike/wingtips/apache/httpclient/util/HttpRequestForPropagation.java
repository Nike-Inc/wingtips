package com.nike.wingtips.apache.httpclient.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor;
import com.nike.wingtips.apache.httpclient.WingtipsHttpClientBuilder;
import com.nike.wingtips.http.HttpObjectForPropagation;

import org.apache.http.HttpRequest;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * An implementation of {@link HttpObjectForPropagation} that knows how to set headers on Apache {@link HttpRequest}
 * objects. This allows you to use the {@link
 * com.nike.wingtips.http.HttpRequestTracingUtils#propagateTracingHeaders(HttpObjectForPropagation, Span)} helper
 * method with Apache {@link HttpRequest}s.
 *
 * <p>Wingtips users shouldn't need to use this class most of the time. Instead please refer to {@link
 * WingtipsHttpClientBuilder} for creating a {@link HttpClientBuilder} with tracing built-in, or {@link
 * WingtipsApacheHttpClientInterceptor} for request/response interceptors to integrate tracing into other {@link
 * HttpClientBuilder}s.
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
public class HttpRequestForPropagation implements HttpObjectForPropagation {

    protected final HttpRequest httpRequest;

    public HttpRequestForPropagation(HttpRequest httpRequest) {
        if (httpRequest == null) {
            throw new IllegalArgumentException("httpRequest cannot be null");
        }
        this.httpRequest = httpRequest;
    }

    @Override
    public void setHeader(String headerKey, String headerValue) {
        httpRequest.setHeader(headerKey, headerValue);
    }
}
