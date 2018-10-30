package com.nike.wingtips.servlet;

import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.util.TracingState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A class for abstracting out bits of the Servlet API that are version-dependent, e.g. async request support
 * doesn't show up until Servlet 3.0 API. This abstraction allows us to have one servlet filter that works in
 * pre-Servlet-3 or post-Servlet-3 environments without receiving class-not-found or method-not-found exceptions.
 *
 * <p>This class was derived from
 * <a href="https://github.com/openzipkin/brave/blob/master/instrumentation/servlet/src/main/java/brave/servlet/ServletRuntime.java">
 * Brave's ServletRuntime</a>, which was itself derived from OkHttp's {@code okhttp3.internal.platform.Platform}.
 *
 * <p>You should not need to worry about this class - it is an internal implementation detail for
 * {@link RequestTracingFilter}.
 *
 * @author Nic Munroe
 */
abstract class ServletRuntime {

    private static final Logger logger = LoggerFactory.getLogger(ServletRuntime.class);

    /**
     * The classname for {@code AsyncListener}. Pass this in to {@link #determineServletRuntime(Class, String)} along
     * with the servlet request class to determine which servlet runtime environment you're running in.
     */
    static final String ASYNC_LISTENER_CLASSNAME = "javax.servlet.AsyncListener";

    /**
     * @param request The request to inspect to see if it's async or not.
     * @return true if the given request represents an async request, false otherwise.
     */
    abstract boolean isAsyncRequest(HttpServletRequest request);

    /**
     * This method should be overridden to do the right thing depending on the Servlet runtime environment - Servlet
     * 2.x environments should do nothing or throw an exception since they do not support async requests (and this
     * method should never be called since {@link #isAsyncRequest(HttpServletRequest)} should return false), but
     * Servlet 3+ environments should setup a listener that will complete the given {@link TracingState} when the given
     * async request finishes. The listener code would look something like:
     *
     * <pre>
     *      AsyncListener spanCompletingAsyncListener = ...;
     *      asyncRequest.getAsyncContext().addListener(spanCompletingAsyncListener);
     * </pre>
     *
     * @param asyncRequest The async servlet request (guaranteed to be async since this method will only be called when
     * {@link #isAsyncRequest(HttpServletRequest)} returns true).
     * @param asyncResponse The servlet response object - needed for span tagging.
     * @param originalRequestTracingState The {@link TracingState} that was generated when this request started, and
     * which should be completed when the given async servlet request finishes.
     * @param tagAndNamingStrategy The {@link HttpTagAndSpanNamingStrategy} that should be used for final span name
     * and tagging.
     * @param tagAndNamingAdapter The {@link HttpTagAndSpanNamingAdapter} that should be used by
     * {@code tagAndNamingStrategy} for final span name and tagging.
     */
    abstract void setupTracingCompletionWhenAsyncRequestCompletes(
        HttpServletRequest asyncRequest,
        HttpServletResponse asyncResponse,
        TracingState originalRequestTracingState,
        HttpTagAndSpanNamingStrategy<HttpServletRequest,HttpServletResponse> tagAndNamingStrategy,
        HttpTagAndSpanNamingAdapter<HttpServletRequest,HttpServletResponse> tagAndNamingAdapter
    );

    /**
     * The dispatcher type {@code javax.servlet.DispatcherType.ASYNC} introduced in Servlet 3.0 means a filter can be
     * invoked in more than one thread over the course of a single request. This method should return {@code true} if
     * the filter is currently executing within an asynchronous dispatch.
     *
     * @param request the current request
     *
     * @deprecated This method is no longer used to determine whether the servlet filter should execute, and will be
     * removed in a future update. It is here to support {@link
     * RequestTracingFilter#isAsyncDispatch(HttpServletRequest)}, which only remains to prevent breaking impls that
     * overrode the method.
     */
    @Deprecated
    abstract boolean isAsyncDispatch(HttpServletRequest request);

    /**
     * Determines whether the current servlet container supports Servlet 3 async requests by using reflection to
     * inspect the given {@link ServletRequest} implementation class to see if it contains an async-related method
     * introduced with the Servlet 3 API, and attempts to use the given string to load the class for
     * {@code javax.servlet.AsyncListener}. If both of those checks pass without error then {@link Servlet3Runtime}
     * will be returned, otherwise a Servlet 2.x environment is assumed and {@link Servlet2Runtime} will be returned.
     *
     * @param servletRequestClass The {@link ServletRequest} implementation class to check.
     * @param asyncListenerClassname This should be "javax.servlet.AsyncListener" at runtime (use the {@link
     * #ASYNC_LISTENER_CLASSNAME} constant). It is passed in as an argument to facilitate testing scenarios.
     * @return true if the given {@link ServletRequest} implementation class supports the getAsyncContext() method
     * and the given {@code javax.servlet.AsyncListener} classname could be loaded, otherwise false.
     */
    static ServletRuntime determineServletRuntime(Class<?> servletRequestClass, String asyncListenerClassname) {
        try {
            servletRequestClass.getMethod("getAsyncContext");
            Class.forName(asyncListenerClassname);
            // No exceptions were thrown, so we're running in a Servlet 3+ environment.
            return new Servlet3Runtime();
        } catch (Exception ex) {
            logger.warn(
                "Servlet 3 async requests are not supported on the current container. "
                + "RequestTracingFilter will default to blocking request behavior (Servlet 2.x). "
                + "Exception message indicating a Servlet 2.x environment: {}", ex.toString()
            );
            return new Servlet2Runtime();
        }
    }

    /**
     * Implementation of {@link ServletRuntime} for Servlet 2.x environments.
     */
    static class Servlet2Runtime extends ServletRuntime {

        @Override
        public boolean isAsyncRequest(HttpServletRequest request) {
            return false;
        }

        @Override
        public void setupTracingCompletionWhenAsyncRequestCompletes(
            HttpServletRequest asyncRequest,
            HttpServletResponse asyncResponse,
            TracingState originalRequestTracingState,
            HttpTagAndSpanNamingStrategy<HttpServletRequest,HttpServletResponse> tagAndNamingStrategy,
            HttpTagAndSpanNamingAdapter<HttpServletRequest,HttpServletResponse> tagAndNamingAdapter
        ) {
            throw new IllegalStateException("This method should never be called in a pre-Servlet-3.0 environment.");
        }

        @Override
        boolean isAsyncDispatch(HttpServletRequest request) {
            return false;
        }
    }

    /**
     * Implementation of {@link ServletRuntime} for Servlet 3+ environments that supports async requests.
     */
    static class Servlet3Runtime extends ServletRuntime {

        @Override
        public boolean isAsyncRequest(HttpServletRequest request) {
            return request.isAsyncStarted();
        }

        @Override
        public void setupTracingCompletionWhenAsyncRequestCompletes(
            HttpServletRequest asyncRequest,
            HttpServletResponse asyncResponse,
            TracingState originalRequestTracingState,
            HttpTagAndSpanNamingStrategy<HttpServletRequest,HttpServletResponse> tagAndNamingStrategy,
            HttpTagAndSpanNamingAdapter<HttpServletRequest,HttpServletResponse> tagAndNamingAdapter
        ) {
            // Async processing was started, so we have to complete it with a listener.
            asyncRequest.getAsyncContext().addListener(
                new WingtipsRequestSpanCompletionAsyncListener(
                    originalRequestTracingState, tagAndNamingStrategy, tagAndNamingAdapter
                ),
                asyncRequest,
                asyncResponse
            );
        }

        @Override
        boolean isAsyncDispatch(HttpServletRequest request) {
            // Do a string comparison to avoid pulling in the DispatcherType import.
            return "ASYNC".equals(request.getDispatcherType().name());
        }
    }

}
