package com.nike.wingtips.spring.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.http.HttpObjectForPropagation;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMessage;

/**
 * An implementation of {@link HttpObjectForPropagation} that knows how to set headers on Spring {@link HttpHeaders}
 * objects. This allows you to use the {@link
 * com.nike.wingtips.http.HttpRequestTracingUtils#propagateTracingHeaders(HttpObjectForPropagation, Span)} helper
 * method with Spring {@link HttpHeaders}s. 
 *
 * @author Nic Munroe
 */
public class HttpHeadersForPropagation implements HttpObjectForPropagation {

    protected final HttpHeaders httpHeaders;

    public HttpHeadersForPropagation(HttpHeaders httpHeaders) {
        if (httpHeaders == null) {
            throw new IllegalArgumentException("httpHeaders cannot be null");
        }
        this.httpHeaders = httpHeaders;
    }

    public HttpHeadersForPropagation(HttpMessage httpMessage) {
        this(extractHttpHeaders(httpMessage));
    }

    protected static HttpHeaders extractHttpHeaders(HttpMessage httpMessage) {
        if (httpMessage == null) {
            throw new IllegalArgumentException("httpMessage cannot be null");
        }
        return httpMessage.getHeaders();
    }

    @Override
    public void setHeader(String headerKey, String headerValue) {
        httpHeaders.set(headerKey, headerValue);
    }
}
