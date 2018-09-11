package com.nike.wingtips.servlet;

import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.tag.ServletRequestTagAdapter;
import com.nike.wingtips.tags.HttpTagStrategy;
import com.nike.wingtips.tags.NoOpTagStrategy;
import com.nike.wingtips.tags.OpenTracingTagStrategy;
import com.nike.wingtips.tags.ZipkinTagStrategy;
import com.nike.wingtips.util.TracingState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.naming.NameNotFoundException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.nike.wingtips.servlet.ServletRuntime.ASYNC_LISTENER_CLASSNAME;
import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.unlinkTracingFromCurrentThread;

/**
 * Makes sure distributed tracing is handled for each request. Sets up the span for incoming requests (either an
 * entirely new root span or one with a parent, depending on what is in the incoming request's headers), and also sets
 * the {@link TraceHeaders#TRACE_ID} on the response. This is designed to only run once per request.
 *
 * <p>NOTE: You can override {@link #getUserIdHeaderKeys()} if your service is expecting user ID header(s) and you can't
 * (or don't want to) set up those headers via the {@link #USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME} init parameter.
 *
 * <p>This class supports Servlet 3 async requests when running in a Servlet 3+ environment. It also supports running
 * in a Servlet 2.x environment.
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
public class RequestTracingFilter implements Filter {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * This attribute key will be set to a value of true via {@link ServletRequest#setAttribute(String, Object)} the
     * first time this filter's distributed tracing logic is run for any given request. This filter will then see this
     * attribute on any subsequent executions for the same request and continue the filter chain without executing the
     * distributed tracing logic again to make sure this filter's logic is only executed once per request.
     *
     * <p>If you want to prevent this filter from executing on specific requests then you can override {@link
     * #skipDispatch(HttpServletRequest)} to return true for any requests where you don't want distributed tracing to
     * occur.
     */
    public static final String FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE = "RequestTracingFilterAlreadyFiltered";

    /**
     * Corresponds to {@link javax.servlet.RequestDispatcher#ERROR_REQUEST_URI}. This will be populated in the request
     * attributes during an error dispatch.
     *
     * @deprecated This is no longer being used and will be removed in a future update.
     */
    @Deprecated
    public static final String ERROR_REQUEST_URI_ATTRIBUTE = "javax.servlet.error.request_uri";

    /**
     * The param name for the "list of user ID header keys" init param for this filter. The value of this init param
     * will be parsed for the list of user ID header keys to use when calling {@link
     * HttpSpanFactory#fromHttpServletRequest(HttpServletRequest, List)} or {@link
     * HttpSpanFactory#getUserIdFromHttpServletRequest(HttpServletRequest, List)}. The value for this init param is
     * expected to be a comma-delimited list.
     */
    public static final String USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME = "user-id-header-keys-list";
    
    public static final String TAG_STRATEGY_INIT_PARAM_NAME = "server-side-span-tag-strategy";

    protected ServletRuntime servletRuntime;
    protected List<String> userIdHeaderKeysFromInitParam;

    /**
     * This {@code HttpTagStrategy} is responsible for tagging a span with metadata from the request and responses handled
     * by this {@code java.servlet.Filter}. 
     */
    protected HttpTagStrategy<HttpServletRequest, HttpServletResponse> tagStrategy;
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String userIdHeaderKeysListString = filterConfig.getInitParameter(USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME);
        if (userIdHeaderKeysListString != null) {
            List<String> parsedList = new ArrayList<>();
            for (String headerKey : userIdHeaderKeysListString.split(",")) {
                String trimmedHeaderKey = headerKey.trim();
                if (trimmedHeaderKey.length() > 0)
                    parsedList.add(trimmedHeaderKey);
            }
            userIdHeaderKeysFromInitParam = Collections.unmodifiableList(parsedList);
        }
        
        this.tagStrategy = initializeTagStrategy(filterConfig);
    }

    protected HttpTagStrategy<HttpServletRequest, HttpServletResponse> initializeTagStrategy(FilterConfig filterConfig)  {
        String tagStrategyString = filterConfig.getInitParameter(TAG_STRATEGY_INIT_PARAM_NAME);
        try {
            return getTagStrategyFromName(tagStrategyString);
        } catch(NameNotFoundException nnfe) {
            logger.warn("Unable to match tagging strategy " + tagStrategyString + ", using default OpenTracing strategy");
            return getOpenTracingTagStrategy();
        }
    }

    protected HttpTagStrategy<HttpServletRequest, HttpServletResponse> getTagStrategyFromName(String strategyName) throws NameNotFoundException{
        // Default is the opentracing strategy
        if (strategyName == null || "opentracing".equalsIgnoreCase(strategyName)) {
            return getOpenTracingTagStrategy(); 
        } 
        if("zipkin".equalsIgnoreCase(strategyName)) {
            return getZipkinTagStrategy();
        }
        if("none".equalsIgnoreCase(strategyName) || "noop".equalsIgnoreCase(strategyName)) {
            return getNoOpStrategy();
        }
        throw new NameNotFoundException("Unable to find tag strategy for " + strategyName);
    }

    @Override
    public void destroy() {
        // Nothing to do
    }

    /**
     * Wrapper around {@link #doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)} to make sure this
     * filter's logic is only executed once per request.
     */
    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain filterChain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            throw new ServletException(this.getClass().getName() + " only supports HTTP requests");
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        boolean filterHasAlreadyExecuted = request.getAttribute(FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE) != null;
        if (filterHasAlreadyExecuted || skipDispatch(httpRequest)) {

            // Already executed or we're supposed to skip, so continue the filter chain without doing the
            //      distributed tracing work.
            filterChain.doFilter(request, response);
        }
        else {
            // Time to execute the distributed tracing logic.
            request.setAttribute(FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE, Boolean.TRUE);
            doFilterInternal(httpRequest, httpResponse, filterChain);
        }
    }

    /**
     * Performs the distributed tracing work for each request's overall span. Guaranteed to only be called once per
     * request.
     */
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Surround the tracing filter logic with a try/finally that guarantees the original tracing and MDC info found
        //      on the current thread at the beginning of this method is restored to this thread before this method
        //      returns, even if the request ends up being an async request. Otherwise there's the possibility of
        //      incorrect tracing information sticking around on this thread and potentially polluting other requests.

        TracingState originalThreadInfo = TracingState.getCurrentThreadTracingState();
        try {
            Span newSpan = createNewSpanForRequest(request);

            addTracingInfoToRequestAttributes(newSpan, request);

            // Make sure we set the trace ID on the response header now before the response is committed (if we wait
            //      until after the filter chain then the response might already be committed, silently preventing us
            //      from setting the response header)
            response.setHeader(TraceHeaders.TRACE_ID, newSpan.getTraceId());

            TracingState originalRequestTracingState = TracingState.getCurrentThreadTracingState();
            try {
                tagSpanWithRequestAttributes(newSpan, request);
                filterChain.doFilter(request, response);
            } catch(Throwable t) {
                handleErroredRequestTags(newSpan, t);
                throw t;
            } finally {
                if (isAsyncRequest(request)) {
                    // Async, so we need to attach a listener to complete the original tracing state when the async
                    //      servlet request finishes.
                    // The listener will also add tags once the request is complete
                    setupTracingCompletionWhenAsyncRequestCompletes(request, originalRequestTracingState, tagStrategy);
                }
                else {
                    tagSpanWithResponseAttributes(newSpan, response);
                    // Not async, so we need to complete the request span now.
                    Tracer.getInstance().completeRequestSpan();
                }
            }
        }
        finally {
            //noinspection deprecation
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }

    protected Span createNewSpanForRequest(HttpServletRequest request) {
        // See if there's trace info in the incoming request's headers. If so it becomes the parent trace.
        Tracer tracer = Tracer.getInstance();
        final Span parentSpan = HttpSpanFactory.fromHttpServletRequest(request, getUserIdHeaderKeys());
        Span newSpan;

        if (parentSpan != null) {
            logger.debug("Found parent Span {}", parentSpan);
            newSpan = tracer.startRequestWithChildSpan(parentSpan, HttpSpanFactory.getSpanName(request));
        } else {
            newSpan = tracer.startRequestWithRootSpan(
                    getSpanNameFromHttpServletRequest(request),
                    HttpSpanFactory.getUserIdFromHttpServletRequest(request, getUserIdHeaderKeys())
                    );
            logger.debug("Parent span not found, starting a new span {}", newSpan);
        }
        return newSpan;
    }

    protected void addTracingInfoToRequestAttributes(Span span, HttpServletRequest request) {
        // Put the new span's trace info into the request attributes.
        request.setAttribute(TraceHeaders.TRACE_SAMPLED, span.isSampleable());
        request.setAttribute(TraceHeaders.TRACE_ID, span.getTraceId());
        request.setAttribute(TraceHeaders.SPAN_ID, span.getSpanId());
        request.setAttribute(TraceHeaders.PARENT_SPAN_ID, span.getParentSpanId());
        request.setAttribute(TraceHeaders.SPAN_NAME, span.getSpanName());
        request.setAttribute(Span.class.getName(), span);
    }


    /**
     * @return The human-readable name to be given to a {@code Span} representing this request. The default is to use
     *  {@code HttpSpanFactory.getSpanName(HttpServletRequest)}
     */
    protected String getSpanNameFromHttpServletRequest(HttpServletRequest request) {
        return HttpSpanFactory.getSpanName(request);
    }

    /**
     * @return true if {@link #doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)} should be
     * skipped (and therefore prevent distributed tracing logic from starting), false otherwise. This defaults to
     * returning false so the first execution of this filter will always trigger distributed tracing, so if you have a
     * need to skip distributed tracing for a request you can override this method and have whatever logic you need.
     */
    protected boolean skipDispatch(HttpServletRequest request) {
        return false;
    }

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
        return getServletRuntime(request).isAsyncDispatch(request);
    }
    
    /**
     * The list of header keys that will be used to search the request headers for a user ID to set on the {@link Span}
     * for the request. The user ID header keys will be searched in list order, and the first non-empty user ID header
     * value found will be used as the {@link Span#getUserId()}. You can safely return null or an empty list for this
     * method if there is no user ID to extract; if you return null/empty then the request span's {@link
     * Span#getUserId()} will be null.
     *
     * <p>By default this method will return the list specified via the {@link
     * #USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME} init param, or null if that init param does not exist.
     *
     * @return The list of header keys that will be used to search the request headers for a user ID to set on the
     * {@link Span} for the request. This method may return null or an empty list if there are no user IDs to search
     * for.
     */
    protected List<String> getUserIdHeaderKeys() {
        return userIdHeaderKeysFromInitParam;
    }

    /**
     * Helper method for determining (and then caching) the {@link ServletRuntime} implementation appropriate for
     * the current Servlet runtime environment. If the current Servlet runtime environment supports the Servlet 3 API
     * (i.e. async requests) then a Servlet-3-async-request-capable implementation will be returned, otherwise a
     * Servlet-2-blocking-requests-only implementation will be returned. The first time this method is called the
     * result will be cached, and the cached value returned for subsequent calls.
     *
     * @param request The concrete {@link ServletRequest} implementation use to determine the Servlet runtime
     * environment.
     * @return The {@link ServletRuntime} implementation appropriate for the current Servlet runtime environment.
     */
    protected ServletRuntime getServletRuntime(ServletRequest request) {
        // It's ok that this isn't synchronized - this logic is idempotent and if it's executed a few extra times
        //      due to concurrent requests when the service first starts up it won't hurt anything.
        if (servletRuntime == null) {
            servletRuntime = ServletRuntime.determineServletRuntime(request.getClass(), ASYNC_LISTENER_CLASSNAME);
        }

        return servletRuntime;
    }
    
    /**
     * Returns the value of calling {@link ServletRuntime#isAsyncRequest(HttpServletRequest)} on the {@link
     * ServletRuntime} returned by {@link #getServletRuntime(ServletRequest)}. This method is here to allow
     * easy overriding by subclasses if needed, where {@link ServletRuntime} is not in scope.
     *
     * @param request The request to inspect to see if it's part of an async servlet request or not.
     * @return the value of calling {@link ServletRuntime#isAsyncRequest(HttpServletRequest)} on the {@link
     * ServletRuntime} returned by {@link #getServletRuntime(ServletRequest)}.
     */
    protected boolean isAsyncRequest(HttpServletRequest request) {
        return getServletRuntime(request).isAsyncRequest(request);
    }

    /**
     * Delegates to {@link
     * ServletRuntime#setupTracingCompletionWhenAsyncRequestCompletes(HttpServletRequest, TracingState)}, with the
     * {@link ServletRuntime} retrieved via {@link #getServletRuntime(ServletRequest)}. This method is here to
     * allow easy overriding by subclasses if needed, where {@link ServletRuntime} is not in scope.
     *
     * @param asyncRequest The async servlet request (guaranteed to be async since this method will only be called when
     * {@link #isAsyncRequest(HttpServletRequest)} returns true).
     * @param originalRequestTracingState The {@link TracingState} that was generated when this request started, and
     * which should be completed when the given async servlet request finishes.
     * @param tagStrategy Once the async servlet request completes we want to tag the span with attributes from the 
     * {@code HttpServletResponse}
     * 
     */
    protected void setupTracingCompletionWhenAsyncRequestCompletes(HttpServletRequest asyncRequest,
                                                                   TracingState originalRequestTracingState,
                                                                   HttpTagStrategy<HttpServletRequest, HttpServletResponse> tagStrategy) {
        getServletRuntime(asyncRequest).setupTracingCompletionWhenAsyncRequestCompletes(
            asyncRequest, originalRequestTracingState, tagStrategy
        );
    }
    
    /**
     * Broken out as a separate method so we can surround it in a try{} to ensure we don't break the overall
     * span handling with exceptions from the {@code tagStrategy}.
     * @param span The span to be tagged
     * @param requestObj The request context to use for tag values
     */
    private void tagSpanWithRequestAttributes(Span span, HttpServletRequest requestObj) {
        try {
            tagStrategy.tagSpanWithRequestAttributes(span, requestObj);
        } catch(Throwable taggingException) {
            logger.warn("Unable to tag span with request attributes", taggingException);
        }
    }

    /**
     * Broken out as a separate method so we can surround it in a try{} to ensure we don't break the overall
     * span handling with exceptions from the {@code tagStrategy}.
     * @param span The span to be tagged
     * @param responseObj The response context to be used for tag values
     */
    private void tagSpanWithResponseAttributes(Span span, HttpServletResponse responseObj) {
        try {
            tagStrategy.tagSpanWithResponseAttributes(span, responseObj);
        } catch(Throwable taggingException) {
            logger.warn("Unable to tag span with response attributes", taggingException);
        }
    }

    /**
     * Broken out as a separate method so we can surround it in a try{} to ensure we don't break the overall
     * span handling with exceptions from the {@code tagStrategy}.
     * @param span The span to be tagged
     * @param throwable The exception context to use for tag values
     */
    private void handleErroredRequestTags(Span span, Throwable throwable) {
        try {
            tagStrategy.handleErroredRequest(span, throwable);
        } catch(Throwable taggingException) {
            logger.warn("Unable to tag errored span with exception", taggingException);
        }
    }
    
    protected HttpTagStrategy<HttpServletRequest, HttpServletResponse> getOpenTracingTagStrategy() {
        return new OpenTracingTagStrategy<HttpServletRequest, HttpServletResponse>(new ServletRequestTagAdapter());
    }
    
    protected HttpTagStrategy<HttpServletRequest, HttpServletResponse> getZipkinTagStrategy() {
        return new ZipkinTagStrategy<HttpServletRequest, HttpServletResponse>(new ServletRequestTagAdapter());
    }
    
    protected HttpTagStrategy<HttpServletRequest, HttpServletResponse> getNoOpStrategy() {
        return new NoOpTagStrategy<HttpServletRequest, HttpServletResponse>();
    }    

}
