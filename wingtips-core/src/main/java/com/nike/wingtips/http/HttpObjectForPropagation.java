package com.nike.wingtips.http;

import com.nike.wingtips.Span;

/**
 * Represents an HTTP object that can have its headers set in order to propagate tracing info downstream. This can
 * wrap a headers object, or a request object, or anything else as long as you implement {@link
 * #setHeader(String, String)} so that the request's headers are updated appropriately when called.
 *
 * <p>Once you implement this interface you can call the {@link
 * HttpRequestTracingUtils#propagateTracingHeaders(HttpObjectForPropagation, Span)} helper method to propagate the
 * given {@link Span}'s tracing info to the given HTTP request headers.
 *
 * @author Nic Munroe
 */
public interface HttpObjectForPropagation {

    /**
     * Sets the given header key/value pair on the request's headers.
     *
     * @param headerKey The header key to set.
     * @param headerValue The header value to set.
     */
    void setHeader(String headerKey, String headerValue);

}
