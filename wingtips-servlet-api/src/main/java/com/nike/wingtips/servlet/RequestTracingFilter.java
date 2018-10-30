package com.nike.wingtips.servlet;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.tag.ServletRequestTagAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.NoOpHttpTagStrategy;
import com.nike.wingtips.tags.OpenTracingHttpTagStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;
import com.nike.wingtips.util.TracingState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * <p>Span naming and automatic tagging is controlled via the {@link HttpTagAndSpanNamingStrategy} and
 * {@link HttpTagAndSpanNamingAdapter} that this class is initialized with. You specify which implementations you want
 * via the {@link #TAG_AND_SPAN_NAMING_STRATEGY_INIT_PARAM_NAME} and {@link
 * #TAG_AND_SPAN_NAMING_ADAPTER_INIT_PARAM_NAME} init params.
 *
 * <p>NOTE: You can override {@link #getUserIdHeaderKeys()} if your service is expecting user ID header(s) and you can't
 * (or don't want to) set up those headers via the {@link #USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME} init parameter.
 * Similarly, you can override {@link #initializeTagAndNamingStrategy(FilterConfig)} and/or {@link
 * #initializeTagAndNamingAdapter(FilterConfig)} if you can't (or don't want to) configure them via the
 * {@link #TAG_AND_SPAN_NAMING_STRATEGY_INIT_PARAM_NAME} and {@link #TAG_AND_SPAN_NAMING_ADAPTER_INIT_PARAM_NAME}
 * init params.
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

    /**
     * The param name for the {@link HttpTagAndSpanNamingStrategy} that should be used by this filter for span naming
     * and tagging. {@link #initializeTagAndNamingStrategy(FilterConfig)} is used to interpret the value of this init
     * param. You can pass a fully qualified class name to specify a custom impl, or you can pass one of the following
     * short names:
     * <ul>
     *     <li>{@code ZIPKIN} - short for {@link ZipkinHttpTagStrategy}</li>
     *     <li>{@code OPENTRACING} - short for {@link OpenTracingHttpTagStrategy}</li>
     *     <li>{@code NONE} - short for {@link com.nike.wingtips.tags.NoOpHttpTagStrategy}</li>
     * </ul>
     * If left unspecified, then {@link #getDefaultTagStrategy()} is used (defaults to
     * {@link ZipkinHttpTagStrategy}).
     */
    public static final String TAG_AND_SPAN_NAMING_STRATEGY_INIT_PARAM_NAME =
        "server-side-span-tag-and-naming-strategy";

    /**
     * The param name for the {@link HttpTagAndSpanNamingAdapter} that should be used by this filter for span naming
     * and tagging. {@link #initializeTagAndNamingAdapter(FilterConfig)} is used to interpret the value of this init
     * param. You can pass a fully qualified class name to specify a custom impl.
     *
     * <p>If left unspecified, then {@link #getDefaultTagAdapter()} is used (defaults to
     * {@link ServletRequestTagAdapter}).
     */
    public static final String TAG_AND_SPAN_NAMING_ADAPTER_INIT_PARAM_NAME =
        "server-side-span-tag-and-naming-adapter";

    protected ServletRuntime servletRuntime;
    protected List<String> userIdHeaderKeysFromInitParam;

    /**
     * This {@link HttpTagAndSpanNamingStrategy} is responsible for naming spans and tagging them with metadata from
     * the request and responses handled by this Servlet filter.
     */
    protected HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> tagAndNamingStrategy;

    /**
     * This {@link HttpTagAndSpanNamingAdapter} is used by {@link #tagAndNamingStrategy}, for the purpose of naming
     * spans and tagging them with request/response metadata.
     */
    protected HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> tagAndNamingAdapter;

    @Override
    @SuppressWarnings("RedundantThrows")
    public void init(FilterConfig filterConfig) throws ServletException {
        this.userIdHeaderKeysFromInitParam = initializeUserIdHeaderKeys(filterConfig);
        this.tagAndNamingStrategy = initializeTagAndNamingStrategy(filterConfig);
        this.tagAndNamingAdapter = initializeTagAndNamingAdapter(filterConfig);
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
            Span overallRequestSpan = createNewSpanForRequest(request);

            // Put the new span's trace info into the request attributes.
            addTracingInfoToRequestAttributes(overallRequestSpan, request);

            // Make sure we set the trace ID on the response header now before the response is committed (if we wait
            //      until after the filter chain then the response might already be committed, silently preventing us
            //      from setting the response header)
            response.setHeader(TraceHeaders.TRACE_ID, overallRequestSpan.getTraceId());

            TracingState originalRequestTracingState = TracingState.getCurrentThreadTracingState();
            Throwable errorForTagging = null;
            try {
                tagAndNamingStrategy.handleRequestTagging(overallRequestSpan, request, tagAndNamingAdapter);
                filterChain.doFilter(request, response);
            } catch(Throwable t) {
                errorForTagging = t;
                throw t;
            } finally {
                if (isAsyncRequest(request)) {
                    // Async, so we need to attach a listener to complete the original tracing state when the async
                    //      servlet request finishes.
                    // The listener will also add tags and set a final span name once the request is complete.
                    setupTracingCompletionWhenAsyncRequestCompletes(
                        request, response, originalRequestTracingState, tagAndNamingStrategy, tagAndNamingAdapter
                    );
                }
                else {
                    // Not async, so we need to finalize and complete the request span now.
                    try {
                        // Handle response/error tagging and final span name.
                        tagAndNamingStrategy.handleResponseTaggingAndFinalSpanName(
                            overallRequestSpan, request, response, errorForTagging, tagAndNamingAdapter
                        );
                    }
                    finally {
                        // Complete the overall request span.
                        Tracer.getInstance().completeRequestSpan();
                    }
                }
            }
        }
        finally {
            //noinspection deprecation
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }

    /**
     * @param request The incoming request.
     * @return A new {@link Span} for the overall request. This inspects the incoming request's headers to determine
     * if it should continue an existing trace with a child span, or whether a brand new trace needs to be started.
     * {@link #getInitialSpanName(HttpServletRequest, HttpTagAndSpanNamingStrategy, HttpTagAndSpanNamingAdapter)}
     * is used to generate the initial span name.
     */
    protected Span createNewSpanForRequest(HttpServletRequest request) {
        // See if there's trace info in the incoming request's headers. If so it becomes the parent trace.
        Tracer tracer = Tracer.getInstance();
        final Span parentSpan = HttpSpanFactory.fromHttpServletRequest(request, getUserIdHeaderKeys());
        Span newSpan;

        if (parentSpan != null) {
            logger.debug("Found parent Span {}", parentSpan);
            newSpan = tracer.startRequestWithChildSpan(
                parentSpan,
                getInitialSpanName(request, tagAndNamingStrategy, tagAndNamingAdapter)
            );
        }
        else {
            newSpan = tracer.startRequestWithRootSpan(
                getInitialSpanName(request, tagAndNamingStrategy, tagAndNamingAdapter),
                HttpSpanFactory.getUserIdFromHttpServletRequest(request, getUserIdHeaderKeys())
            );
            logger.debug("Parent span not found, starting a new span {}", newSpan);
        }
        return newSpan;
    }

    /**
     * Helper method for adding tracing-related request attributes to the given request based on the given span.
     *
     * @param span The span for the overall request.
     * @param request The request object to add tracing-related request attributes to.
     */
    protected void addTracingInfoToRequestAttributes(Span span, HttpServletRequest request) {
        request.setAttribute(TraceHeaders.TRACE_SAMPLED, span.isSampleable());
        request.setAttribute(TraceHeaders.TRACE_ID, span.getTraceId());
        request.setAttribute(TraceHeaders.SPAN_ID, span.getSpanId());
        request.setAttribute(TraceHeaders.PARENT_SPAN_ID, span.getParentSpanId());
        request.setAttribute(TraceHeaders.SPAN_NAME, span.getSpanName());
        request.setAttribute(Span.class.getName(), span);
    }


    /**
     * @param request The incoming request.
     * @param namingStrategy The {@link HttpTagAndSpanNamingStrategy} that should be used to try and generate the
     * initial span name - cannot be null.
     * @param adapter The {@link HttpTagAndSpanNamingAdapter} that should be passed to the given {@code namingStrategy}
     * to try and generate the initial span name - cannot be null.
     * @return The human-readable name to be given to a {@link Span} representing this request. By default this method
     * attempts to use {@link HttpTagAndSpanNamingStrategy#getInitialSpanName(Object, HttpTagAndSpanNamingAdapter)}
     * with the given {@code namingStrategy} and {@code adapter} for generating the name, and falls back to
     * {@link HttpSpanFactory#getSpanName(HttpServletRequest)} if the {@link HttpTagAndSpanNamingStrategy} returns
     * null or blank.
     */
    protected String getInitialSpanName(
        HttpServletRequest request,
        HttpTagAndSpanNamingStrategy<HttpServletRequest, ?> namingStrategy,
        HttpTagAndSpanNamingAdapter<HttpServletRequest, ?> adapter
    ) {
        // Try the naming strategy first.
        String spanNameFromStrategy = namingStrategy.getInitialSpanName(request, adapter);

        if (StringUtils.isNotBlank(spanNameFromStrategy)) {
            return spanNameFromStrategy;
        }

        // The naming strategy didn't have anything for us. Fall back to something reasonable.
        return HttpSpanFactory.getSpanName(request);
    }

    /**
     * @return true if {@link #doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)} should be
     * skipped (and therefore prevent distributed tracing logic from starting), false otherwise. This defaults to
     * returning false so the first execution of this filter will always trigger distributed tracing, so if you have a
     * need to skip distributed tracing for a request you can override this method and have whatever logic you need.
     */
    @SuppressWarnings("unused")
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
     *
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
     *
     * @return the value of calling {@link ServletRuntime#isAsyncRequest(HttpServletRequest)} on the {@link
     * ServletRuntime} returned by {@link #getServletRuntime(ServletRequest)}.
     */
    protected boolean isAsyncRequest(HttpServletRequest request) {
        return getServletRuntime(request).isAsyncRequest(request);
    }

    /**
     * Delegates to {@link
     * ServletRuntime#setupTracingCompletionWhenAsyncRequestCompletes(HttpServletRequest, HttpServletResponse,
     * TracingState, HttpTagAndSpanNamingStrategy, HttpTagAndSpanNamingAdapter)}, with the {@link ServletRuntime}
     * retrieved via {@link #getServletRuntime(ServletRequest)}. This method is here to allow easy overriding by
     * subclasses if needed, where {@link ServletRuntime} is not in scope.
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
    protected void setupTracingCompletionWhenAsyncRequestCompletes(
        HttpServletRequest asyncRequest,
        HttpServletResponse asyncResponse,
        TracingState originalRequestTracingState,
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> tagAndNamingStrategy,
        HttpTagAndSpanNamingAdapter<HttpServletRequest,HttpServletResponse> tagAndNamingAdapter
    ) {
        getServletRuntime(asyncRequest).setupTracingCompletionWhenAsyncRequestCompletes(
            asyncRequest, asyncResponse, originalRequestTracingState, tagAndNamingStrategy, tagAndNamingAdapter
        );
    }

    /**
     * @param filterConfig The {@link FilterConfig} for initializing this Servlet filter.
     * @return The list of user ID header keys that should be used by this instance, based on the given {@link
     * FilterConfig#getInitParameter(String)} for the {@link #USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME} init param.
     * May return null or empty list if there are no specified user ID header keys.
     */
    protected List<String> initializeUserIdHeaderKeys(FilterConfig filterConfig) {
        String userIdHeaderKeysListString = filterConfig.getInitParameter(USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME);
        if (userIdHeaderKeysListString != null) {
            List<String> parsedList = new ArrayList<>();
            for (String headerKey : userIdHeaderKeysListString.split(",")) {
                String trimmedHeaderKey = headerKey.trim();
                if (trimmedHeaderKey.length() > 0)
                    parsedList.add(trimmedHeaderKey);
            }
            return Collections.unmodifiableList(parsedList);
        }

        return null;
    }

    /**
     * @param filterConfig The {@link FilterConfig} for initializing this Servlet filter.
     * @return The {@link HttpTagAndSpanNamingStrategy} that should be used by this instance. Delegates to
     * {@link #getTagStrategyFromName(String)}, and uses {@link #getDefaultTagStrategy()} as a last resort if
     * {@link #getTagStrategyFromName(String)} throws an exception.
     */
    protected HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> initializeTagAndNamingStrategy(
        FilterConfig filterConfig
    ) {
        String tagStrategyString = filterConfig.getInitParameter(TAG_AND_SPAN_NAMING_STRATEGY_INIT_PARAM_NAME);
        try {
            return getTagStrategyFromName(tagStrategyString);
        } catch(Throwable t) {
            logger.warn("Unable to match tagging strategy " + tagStrategyString + ". Using default Zipkin strategy", t);
            return getDefaultTagStrategy();
        }
    }

    /**
     * @param filterConfig The {@link FilterConfig} for initializing this Servlet filter.
     * @return The {@link HttpTagAndSpanNamingAdapter} that should be used by this instance. Delegates to
     * {@link #getTagAdapterFromName(String)}, and uses {@link #getDefaultTagAdapter()} as a last resort if
     * {@link #getTagAdapterFromName(String)} throws an exception.
     */
    protected HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> initializeTagAndNamingAdapter(
        FilterConfig filterConfig
    ) {
        String tagAdapterString = filterConfig.getInitParameter(TAG_AND_SPAN_NAMING_ADAPTER_INIT_PARAM_NAME);
        try {
            return getTagAdapterFromName(tagAdapterString);
        } catch(Throwable t) {
            logger.warn(
                "Unable to match tagging adapter " + tagAdapterString + ". Using default ServletRequestTagAdapter",
                t
            );
            return getDefaultTagAdapter();
        }
    }

    /**
     * Uses the given {@code strategyName} to determine and generate the {@link HttpTagAndSpanNamingStrategy} that
     * should be used by this instance. This method looks for the following short names first:
     * <ul>
     *     <li>
     *         {@code ZIPKIN} (or a null/blank {@code strategyName}) - causes {@link #getZipkinHttpTagStrategy()} to be
     *         returned
     *     </li>
     *     <li>{@code OPENTRACING} - causes {@link #getOpenTracingHttpTagStrategy()} to be returned</li>
     *     <li>{@code NONE} - causes {@link #getNoOpTagStrategy()} to be returned</li>
     * </ul>
     *
     * If {@code strategyName} does not match any of those short names, then it is assumed to be a fully qualified
     * class name. {@link Class#forName(String)} will be used to get the class, and then it will be instantiated
     * via {@link Class#newInstance()} and cast to the necessary {@link HttpTagAndSpanNamingStrategy}. This means a
     * class instantiated this way must have a default no-arg constructor and must extend {@link
     * HttpTagAndSpanNamingStrategy}.
     *
     * <p>NOTE: This method may throw a variety of exceptions if {@code strategyName} does not match a short name,
     * and {@link Class#forName(String)} or {@link Class#newInstance()} fails to instantiate it as a fully qualified
     * class name (or if it was instantiated but couldn't be cast to the necessary {@link
     * HttpTagAndSpanNamingStrategy}). Callers should account for this possibility and have a reasonable default
     * fallback if an exception is thrown.
     *
     * @param strategyName The short name or fully qualified class name of the {@link HttpTagAndSpanNamingStrategy}
     * that should be used by this instance. If this is null or blank, then {@link #getZipkinHttpTagStrategy()} will
     * be returned.
     * @return The {@link HttpTagAndSpanNamingStrategy} that should be used by this instance.
     */
    @SuppressWarnings("unchecked")
    protected HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> getTagStrategyFromName(
        String strategyName
    ) throws ClassNotFoundException, IllegalAccessException, InstantiationException, ClassCastException {
        // Default is the Zipkin strategy
        if (StringUtils.isBlank(strategyName) || "zipkin".equalsIgnoreCase(strategyName)) {
            return getZipkinHttpTagStrategy();
        }

        if("opentracing".equalsIgnoreCase(strategyName)) {
            return getOpenTracingHttpTagStrategy();
        }

        if("none".equalsIgnoreCase(strategyName) || "noop".equalsIgnoreCase(strategyName)) {
            return getNoOpTagStrategy();
        }

        // At this point there was no short-name match. Try instantiating it by classname.
        return (HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse>)
            Class.forName(strategyName).newInstance();
    }

    /**
     * Uses the given {@code adapterName} to determine and generate the {@link HttpTagAndSpanNamingAdapter} that
     * should be used by this instance.
     *
     * <p>If {@code adapterName} is null or blank, then {@link #getDefaultTagAdapter()} will be returned. Otherwise,
     * it is assumed to be a fully qualified class name. {@link Class#forName(String)} will be used to get the class,
     * and then it will be instantiated via {@link Class#newInstance()} and cast to the necessary
     * {@link HttpTagAndSpanNamingAdapter}. This means a class instantiated this way must have a default no-arg
     * constructor and must extend {@link HttpTagAndSpanNamingAdapter}.
     *
     * <p>NOTE: This method may throw a variety of exceptions if {@link Class#forName(String)} or
     * {@link Class#newInstance()} fails to instantiate it as a fully qualified class name (or if it was instantiated
     * but couldn't be cast to the necessary {@link HttpTagAndSpanNamingAdapter}). Callers should account for this
     * possibility and have a reasonable default fallback if an exception is thrown.
     *
     * @param adapterName The fully qualified class name of the {@link HttpTagAndSpanNamingAdapter} that should be
     * used by this instance, or pass null/blank if you want {@link #getDefaultTagAdapter()} to be returned.
     * @return The {@link HttpTagAndSpanNamingAdapter} that should be used by this instance.
     */
    @SuppressWarnings("unchecked")
    protected HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> getTagAdapterFromName(
        String adapterName
    ) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        // Default is the ServletRequestTagAdapter
        if (StringUtils.isBlank(adapterName)) {
            return getDefaultTagAdapter();
        }

        // There are no shortnames for the adapter like there are for strategy. Try instantiating by classname
        return (HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse>)
            Class.forName(adapterName).newInstance();
    }

    /**
     * @return {@link ZipkinHttpTagStrategy#getDefaultInstance()}.
     */
    protected HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> getZipkinHttpTagStrategy() {
        return ZipkinHttpTagStrategy.getDefaultInstance();
    }

    /**
     * @return {@link OpenTracingHttpTagStrategy#getDefaultInstance()}.
     */
    protected HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> getOpenTracingHttpTagStrategy() {
        return OpenTracingHttpTagStrategy.getDefaultInstance();
    }

    /**
     * @return {@link NoOpHttpTagStrategy#getDefaultInstance()}.
     */
    protected HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> getNoOpTagStrategy() {
        return NoOpHttpTagStrategy.getDefaultInstance();
    }

    /**
     * @return {@link #getZipkinHttpTagStrategy()} (i.e. the default tag and naming strategy is the Zipkin tag strategy).
     */
    protected HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> getDefaultTagStrategy() {
        return getZipkinHttpTagStrategy();
    }

    /**
     * @return {@link ServletRequestTagAdapter#getDefaultInstance()} (i.e. the default tag and naming adapter is
     * {@link ServletRequestTagAdapter}).
     */
    protected HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> getDefaultTagAdapter() {
        return ServletRequestTagAdapter.getDefaultInstance();
    }

}
