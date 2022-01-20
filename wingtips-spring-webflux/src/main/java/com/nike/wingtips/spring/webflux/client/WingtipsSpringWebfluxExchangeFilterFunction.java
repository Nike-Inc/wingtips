package com.nike.wingtips.spring.webflux.client;

import com.nike.internal.util.Pair;
import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.http.HttpObjectForPropagation;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.tags.NoOpHttpTagAdapter;
import com.nike.wingtips.tags.NoOpHttpTagStrategy;
import com.nike.wingtips.util.TracingState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import java.util.Optional;
import java.util.function.Supplier;

import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.util.context.Context;

import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.supplierWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

/**
 * A {@link ExchangeFilterFunction} which propagates Wingtips tracing information on a downstream Spring Webflux {@link
 * org.springframework.web.reactive.function.client.WebClient WebClient} call's request headers, with an option to
 * surround downstream calls in a subspan / child-span. The subspan option defaults to on and is highly recommended
 * since the subspans will provide you with timing info for your downstream calls separate from any parent span that
 * may be active at the time this interceptor executes.
 *
 * <p>This interceptor's behavior is dependent on the "base tracing state" associated with the WebClient call. There
 * are two ways to set the base tracing state before making a call:
 * <ol>
 *     <li>
 *         PREFERRED - You can explicitly define the {@link TracingState} you want this interceptor to use by
 *         setting an attribute on the {@link ClientRequest} when configuring your WebClient call, with a key
 *         of {@code TracingState.class.getName()} and a value of the {@link TracingState} you want used. e.g.:
 *         <pre>
 *     webClientWithWingtips
 *         .get()
 *         .uri(someUri)
 *         .attribute(TracingState.class.getName(), tracingState)
 *         .exchange()
 *         </pre>
 *     </li>
 *     <li>
 *         UNRELIABLE - Ensure your current thread's tracing state is setup when you execute the WebClient call
 *         (i.e. when you call the WebClient's {@link
 *         org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec#exchange() exchange()} or
 *         {@link org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec#retrieve() retrieve()}
 *         method). This is done via the usual Wingtips {@code Tracer.getInstance().start*(...)} methods. If you're
 *         using the {@link com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter
 *         WingtipsSpringWebfluxWebFilter} for handling things on the server side, and you've made sure to propagate
 *         your tracing state during any thread hopping, then this should already be setup for you.
 *
 *         <p><b>WARNING: This mechanism is unreliable</b>, because different versions of the Spring WebFlux WebClient
 *         have different threading behaviors when filters are executed. Sometimes filters are executed on the same
 *         thread (in which case this mechanism would work), but other times filters are executed on different threads
 *         (in which case this mechanism would fail). Since you cannot predict the threading behavior that gets used
 *         as you change Spring versions (they do not consider threading behavior changes like this to be breaking
 *         changes), you should use the request attribute mechanism described above instead.
 *
 *         <p>NOTE: If the current thread's tracing state is setup when you execute the WebClient call (and you're
 *         lucky enough to have the filter execute on the same thread) *and* you've explicitly specified
 *         {@link TracingState} in the request attributes, and those two tracing states are different, then the
 *         request attribute's tracing state will take precedence for the purpose of the WebClient call.
 *     </li>
 * </ol>
 *
 * <p>If the subspan option is enabled but there's no base tracing state when this interceptor executes
 * (as described above), then a new root span (new trace)
 * will be created rather than a subspan. In either case the newly created span will have a {@link
 * Span#getSpanPurpose()} of {@link Span.SpanPurpose#CLIENT} since this {@link ExchangeFilterFunction} is for a
 * client call. The {@link Span#getSpanName()} for the newly created span will be generated by {@link
 * #getSubspanSpanName(ClientRequest, HttpTagAndSpanNamingStrategy, HttpTagAndSpanNamingAdapter)}. If you want a
 * different span naming format then instantiate this class with a custom {@link HttpTagAndSpanNamingStrategy} and/or
 * {@link HttpTagAndSpanNamingAdapter} (preferred), or override that method (last resort).
 *
 * <p>Note that if you have the subspan option turned off then this interceptor will propagate the base tracing state's
 * tracing info downstream if it's available, but will do nothing if the base tracing state is not setup when this
 * interceptor executes as there's no tracing info to propagate. Turning on the subspan option mitigates this as it
 * guarantees there will be a span to propagate.
 *
 * @author Nic Munroe
 */
public class WingtipsSpringWebfluxExchangeFilterFunction implements ExchangeFilterFunction {

    /**
     * The default implementation of this class. Since this class is thread-safe you can reuse this rather than creating
     * a new object.
     */
    public static final @NotNull WingtipsSpringWebfluxExchangeFilterFunction DEFAULT_IMPL =
        new WingtipsSpringWebfluxExchangeFilterFunction();

    /**
     * If this is true then all downstream calls that this instance intercepts will be surrounded by a
     * subspan which will be started immediately before the call and completed as soon as the call completes.
     */
    protected final boolean surroundCallsWithSubspan;
    /**
     * Controls span naming and tagging when {@link #surroundCallsWithSubspan} is true.
     */
    protected final @NotNull HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy;
    /**
     * Used by {@link #tagAndNamingStrategy} for span naming and tagging when {@link #surroundCallsWithSubspan} is true.
     */
    protected final @NotNull HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapter;

    /**
     * Default constructor - sets {@link #surroundCallsWithSubspan} to true, and uses the default
     * {@link HttpTagAndSpanNamingStrategy} and {@link HttpTagAndSpanNamingAdapter} ({@link
     * SpringWebfluxClientRequestZipkinTagStrategy} and {@link SpringWebfluxClientRequestTagAdapter}).
     */
    public WingtipsSpringWebfluxExchangeFilterFunction() {
        this(true);
    }

    /**
     * Constructor that lets you choose whether downstream calls will be surrounded with a subspan. The default
     * {@link HttpTagAndSpanNamingStrategy} and {@link HttpTagAndSpanNamingAdapter} will be used
     * ({@link SpringWebfluxClientRequestZipkinTagStrategy} and {@link SpringWebfluxClientRequestTagAdapter}).
     *
     * @param surroundCallsWithSubspan pass in true to have downstream calls surrounded with a new span, false to only
     * propagate the current span's info downstream (no subspan).
     */
    public WingtipsSpringWebfluxExchangeFilterFunction(boolean surroundCallsWithSubspan) {
        this(
            surroundCallsWithSubspan,
            SpringWebfluxClientRequestZipkinTagStrategy.getDefaultInstance(),
            SpringWebfluxClientRequestTagAdapter.getDefaultInstance()
        );
    }

    /**
     * Constructor that lets you define whether downstream calls will be surrounded with a subspan and provide
     * a different span tag strategy.
     *
     * @param surroundCallsWithSubspan pass in true to have downstream calls surrounded with a new span, false to only
     * propagate the current span's info downstream (no subspan).
     * @param tagAndNamingStrategy The span tag and naming strategy to use - cannot be null. If you really want no
     * tag and naming strategy, then pass in {@link NoOpHttpTagStrategy#getDefaultInstance()}.
     * @param tagAndNamingAdapter The tag and naming adapter to use - cannot be null. If you really want no tag and
     * naming adapter, then pass in {@link NoOpHttpTagAdapter#getDefaultInstance()}.
     */
    @SuppressWarnings("ConstantConditions")
    public WingtipsSpringWebfluxExchangeFilterFunction(
        boolean surroundCallsWithSubspan,
        @NotNull HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy,
        @NotNull HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapter
    ) {
        if (tagAndNamingStrategy == null) {
            throw new NullPointerException(
                "tagAndNamingStrategy cannot be null - if you really want no strategy, use NoOpHttpTagStrategy"
            );
        }

        if (tagAndNamingAdapter == null) {
            throw new NullPointerException(
                "tagAndNamingAdapter cannot be null - if you really want no adapter, use NoOpHttpTagAdapter"
            );
        }

        this.surroundCallsWithSubspan = surroundCallsWithSubspan;
        this.tagAndNamingStrategy = tagAndNamingStrategy;
        this.tagAndNamingAdapter = tagAndNamingAdapter;
    }

    @Override
    public @NotNull Mono<ClientResponse> filter(
        @NotNull ClientRequest request, @NotNull ExchangeFunction next
    ) {
        // Try to get the base tracing state from the request attributes first.
        Optional<TracingState> tcFromRequestAttributesOpt = request
            .attribute(TracingState.class.getName())
            .map(o -> (TracingState)o);

        // If the request attributes contains a TracingState, then that's what we should use for the request.
        //      Otherwise, we'll just go with whatever's on the current thread.
        Supplier<Mono<ClientResponse>> resultSupplier = () -> doFilterForCurrentThreadTracingState(request, next);
        return tcFromRequestAttributesOpt.map(
            tcFromRequestAttributes -> supplierWithTracing(
                resultSupplier,
                tcFromRequestAttributes
            ).get()
        ).orElseGet(
            resultSupplier
        );
    }

    /**
     * Calls {@link #createAsyncSubSpanAndExecute(ClientRequest, ExchangeFunction)} or {@link
     * #propagateTracingHeadersAndExecute(ClientRequest, ExchangeFunction, TracingState)}, depending on whether
     * {@link #surroundCallsWithSubspan} is true or false.
     *
     * <p>NOTE: This method expects the "base tracing state" to be attached to the current thread at the time
     * this method is called. In other words, whatever tracing state is attached to the current thread will be used
     * for generating a subspan (if {@link #surroundCallsWithSubspan} is true) and for propagating the tracing info on
     * the outbound call.
     *
     * @param request The request to use.
     * @param next The next {@link ExchangeFunction} in the chain.
     * @return The result of calling {@link #createAsyncSubSpanAndExecute(ClientRequest, ExchangeFunction)} or
     * {@link #propagateTracingHeadersAndExecute(ClientRequest, ExchangeFunction, TracingState)}, depending on
     * whether {@link #surroundCallsWithSubspan} is true or false.
     */
    protected @NotNull Mono<ClientResponse> doFilterForCurrentThreadTracingState(
        @NotNull ClientRequest request, @NotNull ExchangeFunction next
    ) {
        if (surroundCallsWithSubspan) {
             return createAsyncSubSpanAndExecute(request, next);
        }

        return propagateTracingHeadersAndExecute(request, next, TracingState.getCurrentThreadTracingState());
    }

    /**
     * Calls {@link HttpRequestTracingUtils#propagateTracingHeaders(HttpObjectForPropagation, Span)} to propagate the
     * current span's tracing state on a the given request's headers, sets a {@link TracingState} request
     * attribute to match the given argument, then returns {@link ExchangeFunction#exchange(ClientRequest)} to
     * continue the chain. The resulting {@link Mono} will also have it's subscriber {@link Context} adjusted
     * to also include the {@link TracingState} via {@link
     * WingtipsSpringWebfluxUtils#subscriberContextWithTracingInfo(Context, TracingState)}.
     *
     * <p>The given {@link TracingState} argument is strictly for adding to the the request attributes and Mono
     * {@link Context}. The headers that are passed on the outbound call will come from {@link
     * Tracer#getCurrentSpan()}, which means the correct tracing state must also be attached to the current thread.
     *
     * @return The result of calling {@link ExchangeFunction#exchange(ClientRequest)} on the request, adjusted
     * to propagate the tracing state on outbound headers.
     */
    protected @NotNull Mono<ClientResponse> propagateTracingHeadersAndExecute(
        @NotNull ClientRequest request,
        @NotNull ExchangeFunction next,
        @Nullable TracingState tracingStateForReqAttrsAndMonoContext
    ) {
        // Create a mutable builder from the request.
        ClientRequest.Builder requestWithTracingHeadersBuilder = ClientRequest.from(request);

        // Propagate tracing headers
        HttpRequestTracingUtils.propagateTracingHeaders(
            requestWithTracingHeadersBuilder::header,
            Tracer.getInstance().getCurrentSpan()
        );

        // Add the tracing state to the request attributes if the tracing state is non-null.
        if (tracingStateForReqAttrsAndMonoContext != null) {
            requestWithTracingHeadersBuilder.attribute(TracingState.class.getName(),
                                                       tracingStateForReqAttrsAndMonoContext);
        }

        // Execute the request/interceptor chain, using the request with tracing headers.
        return next.exchange(requestWithTracingHeadersBuilder.build())
                   .subscriberContext(
                       // Add the tracing state to the Mono Context if it's non-null.
                       c -> (tracingStateForReqAttrsAndMonoContext == null)
                            ? c
                            : subscriberContextWithTracingInfo(c, tracingStateForReqAttrsAndMonoContext)
                   );
    }

    /**
     * Creates a subspan (or new trace if no current span exists) to surround the HTTP request, then returns the
     * result of calling {@link #propagateTracingHeadersAndExecute(ClientRequest, ExchangeFunction, TracingState)}
     * to actually execute the request. The whole thing will be wrapped in a {@link
     * WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper} to finish the subspan when the call completes.
     *
     * <p>Span naming and tagging is done here using {@link #tagAndNamingStrategy} and {@link #tagAndNamingAdapter}.
     *
     * @return The result of calling {@link
     * #propagateTracingHeadersAndExecute(ClientRequest, ExchangeFunction, TracingState)} after surrounding the
     * request with a subspan (or new trace if no current span exists).
     */
    protected @NotNull Mono<ClientResponse> createAsyncSubSpanAndExecute(
        @NotNull ClientRequest request, @NotNull ExchangeFunction next
    ) {
        // Handle subspan stuff. Start by getting the current thread's tracing state (so we can restore it before
        //      this method returns).
        TracingState originalThreadInfo = TracingState.getCurrentThreadTracingState();
        TracingState spanAroundCallTracingState = null;

        try {
            // This will start a new trace if necessary, or a subspan if a trace is already in progress.
            Span subspan = Tracer.getInstance().startSpanInCurrentContext(
                getSubspanSpanName(request, tagAndNamingStrategy, tagAndNamingAdapter),
                Span.SpanPurpose.CLIENT
            );

            spanAroundCallTracingState = TracingState.getCurrentThreadTracingState();
            final TracingState spanAroundCallTracingStateFinal = spanAroundCallTracingState;

            // Add request tags to the subspan.
            tagAndNamingStrategy.handleRequestTagging(subspan, request, tagAndNamingAdapter);

            // Execute the request/interceptor chain, wrapped in a WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper
            //      to finish the subspan when the call is done. Also add the tracing state to the Mono Context.
            return new WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper(
                propagateTracingHeadersAndExecute(request, next, spanAroundCallTracingState),
                request,
                spanAroundCallTracingState,
                tagAndNamingStrategy,
                tagAndNamingAdapter
            ).subscriberContext(c -> subscriberContextWithTracingInfo(c, spanAroundCallTracingStateFinal));
        }
        catch(Throwable t) {
            // Something went wrong, probably in the next.exchange(...) chain. Complete the subspan now
            //      (if one exists).
            completeSubspan(spanAroundCallTracingState, request, null, t);

            throw t;
        }
        finally {
            // Reset back to the original tracing state that was on this thread when this method began.
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }

    /**
     * Helper method that calls {@link #completeSubspanAttachedToCurrentThread(ClientRequest, ClientResponse,
     * Throwable, HttpTagAndSpanNamingStrategy, HttpTagAndSpanNamingAdapter, Pair[])} after attaching the given
     * {@link TracingState} for the subspan to the current thread. If that tracing state is null, then this
     * method does nothing.
     *
     * @param spanAroundCallTracingState The tracing state for the subspan.
     * @param request The request.
     * @param response The response (may be null).
     * @param error The error that occurred (may be null).
     */
    @SuppressWarnings("SameParameterValue")
    protected void completeSubspan(
        @Nullable TracingState spanAroundCallTracingState,
        @NotNull ClientRequest request,
        @Nullable ClientResponse response,
        @Nullable Throwable error
    ) {
        if (spanAroundCallTracingState != null) {
            runnableWithTracing(
                () -> completeSubspanAttachedToCurrentThread(
                    request, response, error, tagAndNamingStrategy, tagAndNamingAdapter
                ),
                spanAroundCallTracingState
            ).run();
        }
    }

    /**
     * Helper method that completes whatever span is currently attached to the caller's thread ({@link
     * Tracer#getCurrentSpan()}) at the time this method is called. This is expected to be the subspan around the
     * WebClient call.
     *
     * <p>If the current span is null, or if it's already been completed, then this method will do nothing.
     *
     * @param request The request.
     * @param response The response (if any) - may be null.
     * @param error The error associated with the subspan (if any) - may be null.
     * @param tagAndNamingStrategy The tag strategy to use to handle the response tagging and final span name on
     * the subspan.
     * @param tagAndNamingAdapter The tag adapter to use to handle the response tagging and final span name on
     * the subspan.
     * @param extraCustomTags Any extra custom tags you want added to the subspan beyond what the tag strategy
     * will do - may be null or empty.
     */
    @SafeVarargs
    protected static void completeSubspanAttachedToCurrentThread(
        @NotNull ClientRequest request,
        @Nullable ClientResponse response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy,
        @NotNull HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapter,
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
                span, request, response, error, tagAndNamingAdapter
            );
        }
        finally {
            // Span.close() contains the logic we want - if the span around the WebClient call was an overall span
            //      (new trace) then tracer.completeRequestSpan() will be called, otherwise it's
            //      a subspan and tracer.completeSubSpan() will be called.
            span.close();
        }
    }

    /**
     * Returns the name that should be used for the subspan surrounding the call. Defaults to whatever {@link
     * HttpTagAndSpanNamingStrategy#getInitialSpanName(Object, HttpTagAndSpanNamingAdapter)} returns, with a fallback
     * of {@link HttpRequestTracingUtils#getFallbackSpanNameForHttpRequest(String, String)} if the naming strategy
     * returned null or blank string. You can override this method to return something else if you want different
     * behavior and you don't want to adjust the naming strategy or adapter.
     *
     * @param request The request that is about to be executed.
     * @param namingStrategy The {@link HttpTagAndSpanNamingStrategy} being used.
     * @param adapter The {@link HttpTagAndSpanNamingAdapter} being used.
     * @return The name that should be used for the subspan surrounding the call.
     */
    protected @NotNull String getSubspanSpanName(
        @NotNull ClientRequest request,
        @NotNull HttpTagAndSpanNamingStrategy<ClientRequest, ?> namingStrategy,
        @NotNull HttpTagAndSpanNamingAdapter<ClientRequest, ?> adapter
    ) {
        // Try the naming strategy first.
        String subspanNameFromStrategy = namingStrategy.getInitialSpanName(request, adapter);

        if (StringUtils.isNotBlank(subspanNameFromStrategy)) {
            return subspanNameFromStrategy;
        }

        // The naming strategy didn't have anything for us. Fall back to something reasonable.
        return HttpRequestTracingUtils.getFallbackSpanNameForHttpRequest(
            "webflux_downstream_call", getRequestMethodAsString(request.method())
        );
    }

    /**
     * @return The value of {@link HttpMethod#name()}, or "UNKNOWN_HTTP_METHOD" if the given method is null.
     */
    protected @NotNull String getRequestMethodAsString(@Nullable HttpMethod method) {
        if (method == null) {
            return "UNKNOWN_HTTP_METHOD";
        }

        return method.name();
    }

    /**
     * A wrapper around {@link WingtipsSpringWebfluxUtils#subscriberContextWithTracingInfo(Context, TracingState)}.
     * This is here to allow you to override the behavior in a subclass if necessary.
     *
     * @param origContext The original {@link Context}.
     * @param tracingState The {@link TracingState} to add to the given context.
     * @return The result of calling {@link
     * WingtipsSpringWebfluxUtils#subscriberContextWithTracingInfo(Context, TracingState)} with the given arguments.
     */
    protected @NotNull Context subscriberContextWithTracingInfo(
        @NotNull Context origContext,
        @NotNull TracingState tracingState
    ) {
        return WingtipsSpringWebfluxUtils.subscriberContextWithTracingInfo(origContext, tracingState);
    }

    /**
     * A {@link MonoOperator} that wraps subscribers in a {@link
     * WingtipsExchangeFilterFunctionTracingCompletionSubscriber} so that the subspan around the WebClient call can be
     * completed when the WebClient call finishes for any reason.
     */
    protected static class WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper
        extends MonoOperator<ClientResponse, ClientResponse> {

        protected final @NotNull ClientRequest request;
        protected final @NotNull TracingState spanAroundCallTracingState;
        protected final @NotNull HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy;
        protected final @NotNull HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapter;

        public WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper(
            @NotNull Mono<ClientResponse> source,
            @NotNull ClientRequest request,
            @NotNull TracingState spanAroundCallTracingState,
            @NotNull HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy,
            @NotNull HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapter
        ) {
            super(source);
            this.request = request;
            this.spanAroundCallTracingState = spanAroundCallTracingState;
            this.tagAndNamingStrategy = tagAndNamingStrategy;
            this.tagAndNamingAdapter = tagAndNamingAdapter;
        }

        @Override
        public void subscribe(
            @NotNull CoreSubscriber<? super ClientResponse> actual
        ) {
            // The request will be kicked off as part of the source.subscribe(...) call, so wrap it in
            //      a runnableWithTracing(...) so it has the correct tracing state.
            runnableWithTracing(
                () -> {
                    // Wrap the actual subscriber with a WingtipsExchangeFilterFunctionTracingCompletionSubscriber
                    //      in order to complete the subspan when the source Mono finishes.
                    source.subscribe(
                        new WingtipsExchangeFilterFunctionTracingCompletionSubscriber(
                            actual,
                            request,
                            actual.currentContext(),
                            spanAroundCallTracingState,
                            tagAndNamingStrategy,
                            tagAndNamingAdapter
                        )
                    );
                },
                spanAroundCallTracingState
            ).run();
        }
    }

    /**
     * A {@link CoreSubscriber} intended to be used with {@link
     * WingtipsExchangeFilterFunctionTracingCompletionSubscriber} that listens for any terminal events (the
     * {@link ClientResponse} coming in, an error, or cancelled Mono) and completes the subspan around the WebClient
     * call appropriately.
     */
    protected static class WingtipsExchangeFilterFunctionTracingCompletionSubscriber
        implements CoreSubscriber<ClientResponse> {

        protected final @NotNull CoreSubscriber<? super ClientResponse> actual;
        protected final @NotNull ClientRequest request;
        protected final @NotNull Context subscriberContext;
        protected final @NotNull TracingState spanAroundCallTracingState;
        protected final @NotNull HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy;
        protected final @NotNull HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapter;

        WingtipsExchangeFilterFunctionTracingCompletionSubscriber(
            @NotNull CoreSubscriber<? super ClientResponse> actual,
            @NotNull ClientRequest request,
            @NotNull Context subscriberContext,
            @NotNull TracingState spanAroundCallTracingState,
            @NotNull HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy,
            @NotNull HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapter
        ) {
            this.actual = actual;
            this.request = request;
            this.subscriberContext = subscriberContext;
            this.spanAroundCallTracingState = spanAroundCallTracingState;
            this.tagAndNamingStrategy = tagAndNamingStrategy;
            this.tagAndNamingAdapter = tagAndNamingAdapter;
        }

        @Override
        public Context currentContext() {
            return subscriberContext;
        }

        @Override
        public void onSubscribe(@NotNull Subscription subscription) {
            // Wrap the subscription so that we can complete the subspan if the subscription is cancelled.
            Subscription subscriptionWrapper = new Subscription() {
                @Override
                public void request(long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    // It doesn't appear that Webflux WebClient calls can be cancelled under normal circumstances,
                    //      but if it does happen then we should finish the span like any other terminal event.
                    runnableWithTracing(
                        () -> {
                            subscription.cancel();
                            completeSubspanAttachedToCurrentThread(
                                request, null, null, tagAndNamingStrategy, tagAndNamingAdapter,
                                Pair.of("cancelled", "true"),
                                Pair.of(KnownZipkinTags.ERROR, "CANCELLED")
                            );
                        },
                        spanAroundCallTracingState
                    ).run();
                }
            };
            this.actual.onSubscribe(subscriptionWrapper);
        }

        @Override
        public void onNext(ClientResponse response) {
            runnableWithTracing(
                () -> {
                    actual.onNext(response);
                    completeSubspanAttachedToCurrentThread(
                        request, response, null, tagAndNamingStrategy, tagAndNamingAdapter
                    );
                },
                spanAroundCallTracingState
            ).run();
        }

        @Override
        public void onError(Throwable t) {
            runnableWithTracing(
                () -> {
                    this.actual.onError(t);
                    completeSubspanAttachedToCurrentThread(
                        request, null, t, tagAndNamingStrategy, tagAndNamingAdapter
                    );
                },
                spanAroundCallTracingState
            ).run();
        }

        @Override
        public void onComplete() {
            // No need to complete the subspan - that would have already been done in onNext(...) or onError(...).
            actual.onComplete();
        }
    }

}
