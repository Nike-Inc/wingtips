package com.nike.wingtips.servlet;

import javax.servlet.http.HttpServletRequest;

/**
 * Simple extension of {@link RequestTracingFilter} for use in environments where async request processing is not used.
 *
 * @deprecated This class is no longer needed - the super {@link RequestTracingFilter} class is no longer abstract
 * and does not need subclasses to tell it whether the request is async. You should move to using {@link
 * RequestTracingFilter} directly. This class will be deleted in a future update.
 *
 * @author Nic Munroe
 */
@Deprecated
public class RequestTracingFilterNoAsync extends RequestTracingFilter {

    @Override
    protected boolean isAsyncDispatch(HttpServletRequest request) {
        return false;
    }

}
