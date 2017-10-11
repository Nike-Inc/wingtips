package com.nike.wingtips.servlet;

import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.util.TracingState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

/**
 * Makes sure distributed tracing is handled for each request. Sets up the span for incoming requests (either an
 * entirely new root span or one with a parent, depending on what is in the incoming request's headers), and also sets
 * the {@link TraceHeaders#TRACE_ID} on the response. This is designed to only run once per request.
 *
 * <p>This class supports Servlet 3 async requests. For Servlet 2.x environments where async requests are not supported
 * please use {@link RequestTracingFilterOldServlet} instead.
 *
 * <p>NOTE: You can override {@link #getUserIdHeaderKeys()} if your service is expecting user ID header(s) and you can't
 * (or don't want to) set up those headers via the {@link #USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME} init parameter.
 *
 * Extension of {@link RequestTracingFilterOldServlet} that adds Servlet 3 support, specifically around async requests.
 *
 * @author Nic Munroe
 */
public class RequestTracingFilter extends RequestTracingFilterOldServlet {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected Boolean containerSupportsAsyncRequests = null;

    /**
     * Determines whether the current servlet container supports Servlet 3 async requests by using reflection to
     * inspect the given {@link ServletRequest} implementation class to see if it contains an async-related method
     * introduced with the Servlet 3 API.
     *
     * @param servletRequestClass ServletRequest implementation class
     * @return true if the given servletRequest implementation class supports the getAsyncContext() method,
     * otherwise false
     */
    protected boolean supportsAsyncRequests(Class<?> servletRequestClass) {
        Method asyncContextMethod = null;
        try {
            asyncContextMethod = servletRequestClass.getMethod("getAsyncContext");
        } catch (Exception ex) {
            logger.warn(
                "Servlet 3 async requests are not supported on the current container. "
                + "This filter will default to blocking request behavior", ex
            );
        }

        return asyncContextMethod != null;
    }

    /**
     * Helper method for determining (and then caching) whether the given {@link ServletRequest} concrete
     * implementation supports Servlet 3 async requests. This could be necessary if a WAR-based project is built
     * using Servlet 3 APIs and then deployed to a Servlet 2.x-only environment.
     *
     * @param servletRequest The concrete {@link ServletRequest} implementation to check.
     * @return true if the given concrete {@link ServletRequest} implementation supports Servlet 3 async requests,
     * false otherwise.
     */
    protected boolean containerSupportsAsyncRequests(ServletRequest servletRequest) {
        // It's ok that this isn't synchronized - this logic is idempotent and if it's executed a few extra times
        //      due to concurrent requests when the service first starts up it won't hurt anything.
        if (containerSupportsAsyncRequests == null) {
            containerSupportsAsyncRequests = supportsAsyncRequests(servletRequest.getClass());
        }

        return containerSupportsAsyncRequests;
    }

    /**
     * The result of calling {@link HttpServletRequest#isAsyncStarted()} on the given request, assuming the runtime
     * environment's {@link ServletRequest} implementation supports Servlet 3 async requests (necessary for the case
     * where a WAR built with Servlet 3 support is deployed to a Servlet 2.x-only container).
     *
     * @param request The request to inspect to see if it's part of an async servlet request or not.
     * @return The result of calling {@link HttpServletRequest#isAsyncStarted()} on the given request.
     */
    @Override
    protected boolean isAsyncRequest(HttpServletRequest request) {
        return containerSupportsAsyncRequests(request) && request.isAsyncStarted();

    }

    /**
     * Adds a {@link AsyncListener} to the given request's {@link HttpServletRequest#getAsyncContext()} so that the
     * given {@link TracingState} will be completed appropriately when this async servlet request completes.
     *
     * @param asyncRequest The async servlet request (guaranteed to be async since this method will only be called when
     * {@link #isAsyncRequest(HttpServletRequest)} returns true).
     * @param originalRequestTracingState The {@link TracingState} that was generated when this request started, and
     * which should be completed when the given async servlet request finishes.
     */
    @Override
    protected void setupTracingCompletionWhenAsyncRequestCompletes(HttpServletRequest asyncRequest,
                                                                   TracingState originalRequestTracingState) {
        // Async processing was started, so we have to complete it with a listener.
        asyncRequest.getAsyncContext().addListener(
            new WingtipsRequestSpanCompletionAsyncListener(originalRequestTracingState)
        );
    }

    /**
     * Corresponds to {@link javax.servlet.RequestDispatcher#ERROR_REQUEST_URI}. This will be populated in the request
     * attributes during an error dispatch.
     *
     * @deprecated This is no longer being used and will be removed in a future update.
     */
    @Deprecated
    public static final String ERROR_REQUEST_URI_ATTRIBUTE = "javax.servlet.error.request_uri";

    /**
     * The dispatcher type {@code javax.servlet.DispatcherType.ASYNC} introduced in Servlet 3.0 means a filter can be
     * invoked in more than one thread over the course of a single request. This method should return {@code true} if
     * the filter is currently executing within an asynchronous dispatch.
     *
     * @param request the current request
     *
     * @deprecated This method is no longer used to determine whether this filter should execute, and will be removed
     * in a future update. It remains here only to prevent breaking impls that overrode the method.
     */
    @Deprecated
    protected boolean isAsyncDispatch(HttpServletRequest request) {
        return DispatcherType.ASYNC.equals(request.getDispatcherType());
    }

}
