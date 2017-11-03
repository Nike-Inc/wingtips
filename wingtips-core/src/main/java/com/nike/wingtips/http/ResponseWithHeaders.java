package com.nike.wingtips.http;

/**
 * Facade similar to {@link RequestWithHeaders}, but for handling response.
 *
 * @author Ales Justin
 */
public interface ResponseWithHeaders {

    /**
     * Set the header value.
     */
    void setHeader(String headerName, String headerValue);

    /**
     * Set attribute.
     */
    void setAttribute(String name, Object attribute);
}
