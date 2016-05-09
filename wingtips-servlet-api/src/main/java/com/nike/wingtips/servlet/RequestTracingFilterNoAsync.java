package com.nike.wingtips.servlet;

import javax.servlet.http.HttpServletRequest;

/**
 * Simple extension of {@link RequestTracingFilter} for use in environments where async request processing is not used.
 *
 * @author Nic Munroe
 */
public class RequestTracingFilterNoAsync extends RequestTracingFilter {

    @Override
    protected boolean isAsyncDispatch(HttpServletRequest request) {
        return false;
    }

}
