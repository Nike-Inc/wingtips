package com.nike.wingtips.spring.webflux.server;

import com.nike.internal.util.Pair;
import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.http.RequestWithHeaders;
import com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;
import com.nike.wingtips.util.TracingState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.util.context.Context;

import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;

/**
 * A Spring WebFlux {@link WebFilter} that makes sure distributed tracing is handled for each request. Sets up the
 * span for incoming requests (either an entirely new root span or one with a parent, depending on what is in the
 * incoming request's headers), and also sets the {@link TraceHeaders#TRACE_ID} on the response. 
 *
 * <p>Span naming and automatic tagging is controlled via the {@link HttpTagAndSpanNamingStrategy} and
 * {@link HttpTagAndSpanNamingAdapter} that this class is initialized with. All config options, including the tag
 * strategy and adapter, are specified via the {@link Builder}. Use {@link #newBuilder()} to create a new
 * {@link Builder}.
 *
 * @author Nic Munroe
 */
public class WingtipsSpringWebfluxWebFilter implements WebFilter, Ordered {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * The sort order for where this handler goes in the Spring {@link WebFilter} series. We default to {@link
     * Ordered#HIGHEST_PRECEDENCE}, so that tracing is started as early as possible.
     */
    protected int order = Ordered.HIGHEST_PRECEDENCE;

    /**
     * This {@link HttpTagAndSpanNamingStrategy} is responsible for naming spans and tagging them with metadata from
     * the request and responses handled by this Servlet filter.
     */
    protected final @NotNull HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> tagAndNamingStrategy;

    /**
     * This {@link HttpTagAndSpanNamingAdapter} is used by {@link #tagAndNamingStrategy}, for the purpose of naming
     * spans and tagging them with request/response metadata.
     */
    protected final @NotNull HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> tagAndNamingAdapter;

    /**
     * The list of user ID header keys to use when inspecting incoming request headers for tracing info.
     */
    protected final @NotNull List<String> userIdHeaderKeys;

    /**
     * There should only ever be one {@link WingtipsSpringWebfluxWebFilter} registered for an application, so if
     * we detect that tracing logic is happening multiple times for a request we want to log a warning. But we
     * don't want to spam that warning for every request. This atomic boolean lets us guarantee that the warning
     * will only be logged once.
     */
    protected final AtomicBoolean warnedAboutMultipleFilters = new AtomicBoolean(false);

    /**
     * Creates a new instance with default config options. If you want to customize things, use {@link #newBuilder()}
     * to create a new builder, set the options you want, and call {@link Builder#build()} to generate a new instance
     * with those options.
     */
    public WingtipsSpringWebfluxWebFilter() {
        this(newBuilder());
    }

    /**
     * Creates a new instance with the options specified in the given {@link Builder}. If any of those options are
     * null then they will be defaulted to a sane default value instead.
     *
     * @param builder The {@link Builder} containing the config options.
     */
    public WingtipsSpringWebfluxWebFilter(Builder builder) {
        if (builder.order != null) {
            this.order = builder.order;
        }

        this.tagAndNamingStrategy =
            (builder.tagAndNamingStrategy == null)
            ? ZipkinHttpTagStrategy.getDefaultInstance()
            : builder.tagAndNamingStrategy;

        this.tagAndNamingAdapter =
            (builder.tagAndNamingAdapter == null)
            ? SpringWebfluxServerRequestTagAdapter.getDefaultInstance()
            : builder.tagAndNamingAdapter;

        this.userIdHeaderKeys =
            (builder.userIdHeaderKeys == null)
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(builder.userIdHeaderKeys));
    }

    /**
     * @return A new {@link Builder} to create a {@link WingtipsSpringWebfluxWebFilter} with custom config options.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public @NotNull Mono<Void> filter(@NotNull ServerWebExchange exchange, @NotNull WebFilterChain chain) {
        try {
            TracingState overallRequestTracingState;

            TracingState existingTracingStateFromExchangeAttrs =
                WingtipsSpringWebfluxUtils.tracingStateFromExchange(exchange);

            if (existingTracingStateFromExchangeAttrs == null) {
                // Start the span for the overall request.
                Span overallRequestSpan = createNewSpanForRequest(exchange);

                // Capture the current tracing state - this is the tracing state for the overall request.
                overallRequestTracingState = TracingState.getCurrentThreadTracingState();

                // Set the trace ID header on the response.
                exchange.getResponse().getHeaders().set(TraceHeaders.TRACE_ID, overallRequestSpan.getTraceId());

                // Add the overall request tracing info to the request attributes.
                addTracingInfoToRequestAttributes(overallRequestTracingState, overallRequestSpan, exchange);

                // The handleRequestTagging(...) method is final and wrapped in a try/catch,
                //      so it will never throw an exception.
                tagAndNamingStrategy.handleRequestTagging(overallRequestSpan, exchange, tagAndNamingAdapter);
            }
            else {
                // This filter is somehow being executed multiple times for a single request (maybe two of these
                //      filters have been registered on accident?). Use the tracing state already attached to the
                //      exchange, and don't do any of the tag strategy stuff since it has already been done.
                overallRequestTracingState = existingTracingStateFromExchangeAttrs;

                // Only warn about this situation once.
                if (warnedAboutMultipleFilters.compareAndSet(false, true)) {
                    logger.error(
                        "WingtipsSpringWebfluxWebFilter executed multiple times for a single request! "
                        + "Is WingtipsSpringWebfluxWebFilter registered twice? This error message will not appear "
                        + "again, however it's likely that this double-filtering will occur on every request."
                    );
                }
            }

            // Execute the filter chain, wrapped in a WingtipsWebFilterTracingMonoWrapper to finish the overall
            //      request span when the server request is done. Also add the tracing state to the Mono Context.
            return new WingtipsWebFilterTracingMonoWrapper(
                chain.filter(exchange),
                exchange,
                overallRequestTracingState,
                tagAndNamingStrategy,
                tagAndNamingAdapter
            ).subscriberContext(c -> subscriberContextWithTracingInfo(c, overallRequestTracingState));
        }
        catch(Throwable t) {
            // Something went wrong, probably in the chain.filter(...) chain. Complete the overall request span now.
            //      It's unlikely this can ever be triggered, but if it is then we can still do the right thing.
            finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread(
                exchange, t, tagAndNamingStrategy, tagAndNamingAdapter
            );

            throw t;
        }
        finally {
            // Remove tracing info from this thread, as we're in a reactive situation and this thread will be used
            //      to handle other concurrent requests.
            Tracer.getInstance().unregisterFromThread();
        }
    }

    /**
     * Creates a new {@link Span} for the overall request. This inspects the incoming request's headers to determine
     * if it should continue an existing trace with a child span, or whether a brand new trace needs to be started.
     * {@link #getInitialSpanName(ServerWebExchange, HttpTagAndSpanNamingStrategy, HttpTagAndSpanNamingAdapter)}
     * is used to generate the initial span name.
     *
     * @param exchange The incoming request.
     * @return A new {@link Span} for the overall request.
     */
    protected @NotNull Span createNewSpanForRequest(@NotNull ServerWebExchange exchange) {
        // See if there's trace info in the incoming request's headers. If so it becomes the parent trace.
        Tracer tracer = Tracer.getInstance();
        RequestWithHeadersServerWebExchangeAdapter requestWithHeadersAdapter =
            new RequestWithHeadersServerWebExchangeAdapter(exchange);

        final Span parentSpan = HttpRequestTracingUtils.fromRequestWithHeaders(
            requestWithHeadersAdapter, userIdHeaderKeys
        );

        Span newSpan;

        if (parentSpan == null) {
            newSpan = tracer.startRequestWithRootSpan(
                getInitialSpanName(exchange, tagAndNamingStrategy, tagAndNamingAdapter),
                HttpRequestTracingUtils.getUserIdFromRequestWithHeaders(requestWithHeadersAdapter, userIdHeaderKeys)
            );
            logger.debug("Parent span not found, starting a new span {}", newSpan);
        }
        else {
            logger.debug("Found parent Span {}", parentSpan);
            newSpan = tracer.startRequestWithChildSpan(
                parentSpan,
                getInitialSpanName(exchange, tagAndNamingStrategy, tagAndNamingAdapter)
            );
        }
        
        return newSpan;
    }

    /**
     * @param exchange The incoming request.
     * @param namingStrategy The {@link HttpTagAndSpanNamingStrategy} that should be used to try and generate the
     * initial span name - cannot be null.
     * @param adapter The {@link HttpTagAndSpanNamingAdapter} that should be passed to the given {@code namingStrategy}
     * to try and generate the initial span name - cannot be null.
     * @return The human-readable name to be given to a {@link Span} representing this request. By default this method
     * attempts to use {@link HttpTagAndSpanNamingStrategy#getInitialSpanName(Object, HttpTagAndSpanNamingAdapter)}
     * with the given {@code namingStrategy} and {@code adapter} for generating the name, and falls back to
     * {@link HttpRequestTracingUtils#generateSafeSpanName(String, String, Integer)} if the
     * {@link HttpTagAndSpanNamingStrategy} returns null or blank.
     */
    protected @NotNull String getInitialSpanName(
        @NotNull ServerWebExchange exchange,
        @NotNull HttpTagAndSpanNamingStrategy<ServerWebExchange, ?> namingStrategy,
        @NotNull HttpTagAndSpanNamingAdapter<ServerWebExchange, ?> adapter
    ) {
        // Try the naming strategy first.
        String spanNameFromStrategy = namingStrategy.getInitialSpanName(exchange, adapter);

        if (StringUtils.isNotBlank(spanNameFromStrategy)) {
            return spanNameFromStrategy;
        }

        // The naming strategy didn't have anything for us. Fall back to something reasonable.
        String pathTemplate = determineUriPathTemplate(exchange);
        String method = exchange.getRequest().getMethodValue();

        // HttpRequestTracingUtils.generateSafeSpanName() gives us what we want for a fallback, and properly handles
        //      the case where everything passed into it is null.
        return HttpRequestTracingUtils.generateSafeSpanName(method, pathTemplate, (Integer)null);
    }

    /**
     * Tries to determine the low-cardinality path template for the given request. First, it looks for a
     * {@link KnownZipkinTags#HTTP_ROUTE} request attribute - if it finds one, then it uses that (this is not expected
     * under normal circumstances, but could be used to override normal behavior). If that attribute is not set, then
     * it falls back to looking for {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}, which is the usual way
     * Spring tells you what the path template was. If both of those fail, then null will be returned.
     *
     * @param exchange The {@link ServerWebExchange} for the request.
     * @return The low-cardinality path template for the given request, or null if no such path template could be
     * found.
     */
    protected static @Nullable String determineUriPathTemplate(@NotNull ServerWebExchange exchange) {
        // Try the Zipkin http.route attribute first. If this exists, then it is definitively what we want.
        String path = getRequestAttributeAsString(exchange, KnownZipkinTags.HTTP_ROUTE);

        if (StringUtils.isNotBlank(path)) {
            // Found http.route. Use it.
            return path;
        }

        // The Zipkin http.route attribute was null or blank, so try the Spring "best matching pattern" attribute.
        path = getRequestAttributeAsString(exchange, HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        if (StringUtils.isNotBlank(path)) {
            // Found Spring best matching pattern. Use it.
            return path;
        }

        // At this point we've struck out on finding a path template.
        return null;
    }

    /**
     * Helper method for null-safe extraction of a request attribute as a String.
     *
     * @param exchange The {@link ServerWebExchange} to inspect for request attributes.
     * @param attrName The desired request attribute name.
     * @return The desired request attribute value as a string, or null if no such request attribute could be found.
     */
    protected static @Nullable String getRequestAttributeAsString(
        @NotNull ServerWebExchange exchange,
        @NotNull String attrName
    ) {
        Object attrValue = exchange.getAttribute(attrName);
        return (attrValue == null) ? null : attrValue.toString();
    }

    /**
     * Helper method for adding tracing-related request attributes to the given request based on the given span.
     * This is simply a wrapper around {@link
     * WingtipsSpringWebfluxUtils#addTracingInfoToRequestAttributes(TracingState, Span, ServerWebExchange)}.
     * This is here to allow you to override the behavior in a subclass if necessary.
     *
     * @param tracingState The {@link TracingState} for the overall request.
     * @param overallRequestSpan The span for the overall request.
     * @param exchange The {@link ServerWebExchange} object to add tracing-related request attributes to.
     */
    protected void addTracingInfoToRequestAttributes(
        @NotNull TracingState tracingState,
        @NotNull Span overallRequestSpan,
        @NotNull ServerWebExchange exchange
    ) {
        WingtipsSpringWebfluxUtils.addTracingInfoToRequestAttributes(tracingState, overallRequestSpan, exchange);
    }

    /**
     * Helper method for creating a new {@link Context} that matches the given context, but with the given
     * {@link TracingState} added as a key/value pair. This is simply a wrapper around {@link
     * WingtipsSpringWebfluxUtils#subscriberContextWithTracingInfo(Context, TracingState)}.
     * This is here to allow you to override the behavior in a subclass if necessary.
     *
     * @param origContext The original {@link Context} that you want tracing state added to.
     * @param tracingState The {@link TracingState} to add to the given {@link Context}.
     * @return A new {@link Context} that matches the original, but also contains a new key/value pair of
     * {@code TracingState.class} -> {@link TracingState}.
     */
    protected @NotNull Context subscriberContextWithTracingInfo(
        @NotNull Context origContext,
        @NotNull TracingState tracingState
    ) {
        return WingtipsSpringWebfluxUtils.subscriberContextWithTracingInfo(origContext, tracingState);
    }

    /**
     * Helper method that handles final response tagging and span name for the {@link Tracer#getCurrentSpan()},
     * then calls {@link Tracer#completeRequestSpan()} to complete the overall request span. It is therefore
     * expected/required that the overall request tracing state is currently attached to the caller's thread at the
     * time this method is called.
     *
     * <p>If the current span is null, or if it's already been completed, then this method will do nothing.
     *
     * @param exchange The {@link ServerWebExchange} containing the request and response.
     * @param error The error associated with the overall request span (if any) - may be null.
     * @param tagAndNamingStrategy The tag strategy to use to handle the response tagging and final span name on
     * the overall request span.
     * @param tagAndNamingAdapter The tag adapter to use to handle the response tagging and final span name on
     * the overall request span.
     * @param extraCustomTags Any extra custom tags you want added to the overall request span beyond what the tag
     * strategy will do - may be null or empty.
     */
    @SafeVarargs
    protected static void finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread(
        @NotNull ServerWebExchange exchange,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> tagAndNamingStrategy,
        @NotNull HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> tagAndNamingAdapter,
        Pair<String, String>... extraCustomTags
    ) {
        Span span = Tracer.getInstance().getCurrentSpan();

        // This method should never be called with a null current span, but if it does happen then there's nothing
        //      for us to do.
        if (span == null) {
            return;
        }

        // Do nothing if the span is already completed - should never happen in reality, but if it does we'll avoid
        //      headaches by short circuiting.
        if (span.isCompleted()) {
            return;
        }

        try {
            // Add any extra custom tags.
            if (extraCustomTags != null) {
                for (Pair<String, String> tag : extraCustomTags) {
                    span.putTag(tag.getKey(), tag.getValue());
                }
            }

            // Handle response/error tagging and final span name.
            tagAndNamingStrategy.handleResponseTaggingAndFinalSpanName(
                span, exchange, exchange.getResponse(), error, tagAndNamingAdapter
            );
        }
        finally {
            // Complete the overall request span.
            Tracer.getInstance().completeRequestSpan();
        }
    }

    /**
     * The sort order for where this handler goes in the Spring {@link WebFilter} series. This defaults to {@link
     * Ordered#HIGHEST_PRECEDENCE}, so that tracing is started as early as possible. You can override this by
     * calling {@link #setOrder(int)} or setting {@link Builder#withOrder(Integer)} when constructing this class.
     */
    @Override
    public int getOrder() {
        return order;
    }

    /**
     * Sets the desired order for this {@link WebFilter}. NOTE: Calling this after Spring has been initialized will
     * have no effect. If you want your setting to be honored it must happen as part of Spring application context
     * startup.
     *
     * @param order The desired order for this {@link WebFilter}.
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * A builder for {@link WingtipsSpringWebfluxWebFilter}. Call any of the {@code with*(...)} methods to set config
     * options, then {@link #build()} to generate the {@link WingtipsSpringWebfluxWebFilter}.
     */
    public static class Builder {
        protected @Nullable Integer order = null;
        protected @Nullable HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> tagAndNamingStrategy = null;
        protected @Nullable HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> tagAndNamingAdapter = null;
        protected @Nullable List<String> userIdHeaderKeys = null;

        /**
         * Specifies the {@link #getOrder()} for the {@link WingtipsSpringWebfluxWebFilter} - see that method for
         * more details. Defaults to {@link Ordered#HIGHEST_PRECEDENCE} if never called or if you pass null.
         *
         * @param order The desired order - may be null if you want to use the default.
         * @return This builder for fluent chaining.
         */
        public @NotNull Builder withOrder(@Nullable Integer order) {
            this.order = order;
            return this;
        }

        /**
         * Specifies the {@link HttpTagAndSpanNamingStrategy} that should be used by the {@link
         * WingtipsSpringWebfluxWebFilter}. Defaults to {@link ZipkinHttpTagStrategy#getDefaultInstance()} if never
         * called or if you pass null.
         *
         * @param tagAndNamingStrategy The desired {@link HttpTagAndSpanNamingStrategy} - may be null if you want
         * to use the default.
         * @return This builder for fluent chaining.
         */
        public @NotNull Builder withTagAndNamingStrategy(
            @Nullable HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> tagAndNamingStrategy
        ) {
            this.tagAndNamingStrategy = tagAndNamingStrategy;
            return this;
        }

        /**
         * Specifies the {@link HttpTagAndSpanNamingAdapter} that should be used by the {@link
         * WingtipsSpringWebfluxWebFilter}. Defaults to {@link
         * SpringWebfluxServerRequestTagAdapter#getDefaultInstance()} if never called or if you pass null.
         *
         * @param tagAndNamingAdapter The desired {@link HttpTagAndSpanNamingAdapter} - may be null if you want
         * to use the default.
         * @return This builder for fluent chaining.
         */
        public @NotNull Builder withTagAndNamingAdapter(
            @Nullable HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> tagAndNamingAdapter
        ) {
            this.tagAndNamingAdapter = tagAndNamingAdapter;
            return this;
        }

        /**
         * Specifies the list of user ID headers that should be used by the {@link WingtipsSpringWebfluxWebFilter}
         * when inspecting the incoming request headers for tracing info (see the javadocs for the {@code
         * userIdHeaderKeys} argument of {@link
         * HttpRequestTracingUtils#fromRequestWithHeaders(RequestWithHeaders, List)} for details). Defaults to null
         * if never called.
         *
         * @param userIdHeaderKeys The desired list of user ID headers - may be null.
         * @return This builder for fluent chaining.
         */
        public @NotNull Builder withUserIdHeaderKeys(@Nullable List<String> userIdHeaderKeys) {
            this.userIdHeaderKeys = userIdHeaderKeys;
            return this;
        }

        /**
         * @return The {@link WingtipsSpringWebfluxWebFilter} generated with the config options from this builder.
         */
        public WingtipsSpringWebfluxWebFilter build() {
            return new WingtipsSpringWebfluxWebFilter(this);
        }
    }

    /**
     * A {@link MonoOperator} that wraps subscribers in a {@link WingtipsWebFilterTracingSubscriber} so that the
     * overall request span for the server request can be completed when the request finishes for any reason.
     */
    protected static class WingtipsWebFilterTracingMonoWrapper extends MonoOperator<Void, Void> {

        protected final @NotNull ServerWebExchange exchange;
        protected final @NotNull TracingState overallRequestTracingState;
        protected final @NotNull HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> tagAndNamingStrategy;
        protected final @NotNull HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> tagAndNamingAdapter;

        public WingtipsWebFilterTracingMonoWrapper(
            @NotNull Mono<Void> source,
            @NotNull ServerWebExchange exchange,
            @NotNull TracingState overallRequestTracingState,
            @NotNull HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> tagAndNamingStrategy,
            @NotNull HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> tagAndNamingAdapter
        ) {
            super(source);
            this.exchange = exchange;
            this.overallRequestTracingState = overallRequestTracingState;
            this.tagAndNamingStrategy = tagAndNamingStrategy;
            this.tagAndNamingAdapter = tagAndNamingAdapter;
        }

        @Override
        public void subscribe(
            @NotNull CoreSubscriber<? super Void> actual
        ) {
            // The endpoint will be executed as part of the source.subscribe(...) call, so wrap it in
            //      a runnableWithTracing(...) so the endpoint has the correct tracing state attached to the
            //      thread when it executes.
            runnableWithTracing(
                () -> source.subscribe(
                    new WingtipsWebFilterTracingSubscriber(
                        actual,
                        exchange,
                        actual.currentContext(),
                        overallRequestTracingState,
                        tagAndNamingStrategy,
                        tagAndNamingAdapter
                    )
                ),
                overallRequestTracingState
            ).run();
        }
    }

    /**
     * A {@link CoreSubscriber} intended to be used with {@link WingtipsWebFilterTracingMonoWrapper} that listens for
     * any terminal events (the response being sent, an error, or cancelled Mono) and completes the overall request
     * span appropriately.
     */
    protected static final class WingtipsWebFilterTracingSubscriber implements CoreSubscriber<Void> {

        protected final @NotNull CoreSubscriber<? super Void> actual;
        protected final @NotNull ServerWebExchange exchange;
        protected final @NotNull Context subscriberContext;
        protected final @NotNull TracingState overallRequestTracingState;
        protected final @NotNull HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> tagAndNamingStrategy;
        protected final @NotNull HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> tagAndNamingAdapter;

        WingtipsWebFilterTracingSubscriber(
            @NotNull CoreSubscriber<? super Void> actual,
            @NotNull ServerWebExchange exchange,
            @NotNull Context subscriberContext,
            @NotNull TracingState overallRequestTracingState,
            @NotNull HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> tagAndNamingStrategy,
            @NotNull HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> tagAndNamingAdapter
        ) {
            this.actual = actual;
            this.exchange = exchange;
            this.subscriberContext = subscriberContext;
            this.overallRequestTracingState = overallRequestTracingState;
            this.tagAndNamingStrategy = tagAndNamingStrategy;
            this.tagAndNamingAdapter = tagAndNamingAdapter;
        }

        @Override
        public Context currentContext() {
            return subscriberContext;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            // Wrap the subscription so that we can complete the overall request span if the subscription is cancelled.
            Subscription subscriptionWrapper = new Subscription() {
                @Override
                public void request(long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    // Webflux server calls can be cancelled under normal circumstances on some platforms in some cases.
                    //      e.g. on linux (epoll), due to `reactor.netty.channel.ChannelOperations.terminate(...)`
                    //      triggering `reactor.core.publisher.Operators.terminate(...)` which eventually bubbles up
                    //      to cancel this *before* onError(...) or onComplete(...) are called, and preventing
                    //      onError(...) or onComplete(...) from being called at all. So on some platforms in some
                    //      scenarios, this cancel() call is the only way for us to hook into request completion.
                    //      Therefore we need to finish the span like any other terminal event.
                    //  TODO: This seems really ... odd. Shouldn't onError(...) or onComplete(...) be called anyway?
                    //      Maybe this is a Project Reactor or Reactor Netty bug that will be fixed someday. But for
                    //      now it empirically happens, so we don't really have a choice.
                    runnableWithTracing(
                        () -> {
                            subscription.cancel();
                            finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread(
                                exchange, null, tagAndNamingStrategy, tagAndNamingAdapter,
                                // We'll put a tag on here to show that completion came through the cancellation flow,
                                //      but this should not be considered an error since it happens under non-error
                                //      conditions (whether it should or not doesn't matter - it provably does - at
                                //      least on the version of Reactor Netty we're testing against).
                                Pair.of("cancelled", "true")
                            );
                        },
                        overallRequestTracingState
                    ).run();
                }
            };
            this.actual.onSubscribe(subscriptionWrapper);
        }

        @Override
        public void onNext(Void aVoid) {
            // This should never be called in reality, but if it is then pass the call through to the actual subscriber.
            actual.onNext(aVoid);
        }

        @Override
        public void onError(Throwable t) {
            runnableWithTracing(
                () -> {
                    this.actual.onError(t);
                    finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread(
                        exchange, t, tagAndNamingStrategy, tagAndNamingAdapter
                    );
                },
                overallRequestTracingState
            ).run();
        }

        @Override
        public void onComplete() {
            runnableWithTracing(
                () -> {
                    this.actual.onComplete();
                    finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread(
                        exchange, null, tagAndNamingStrategy, tagAndNamingAdapter
                    );
                },
                overallRequestTracingState
            ).run();
        }

    }
}
