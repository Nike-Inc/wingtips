package com.nike.wingtips.servlet;

import com.nike.wingtips.http.RequestWithHeaders;

import javax.servlet.http.HttpServletRequest;

/**
 * Adapter for {@link HttpServletRequest} so that it can be used as a {@link RequestWithHeaders}.
 *
 * @author Nic Munroe
 */
public class RequestWithHeadersServletAdapter implements RequestWithHeaders {

    private final HttpServletRequest httpServletRequest;

    public RequestWithHeadersServletAdapter(HttpServletRequest httpServletRequest) {
        if (httpServletRequest == null)
            throw new IllegalArgumentException("httpServletRequest cannot be null");

        this.httpServletRequest = httpServletRequest;
    }

    @Override
    public String getHeader(String headerName) {
        return httpServletRequest.getHeader(headerName);
    }

    @Override
    public Object getAttribute(String name) {
        return httpServletRequest.getAttribute(name);
    }
}
