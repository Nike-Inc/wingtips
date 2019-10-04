package com.nike.wingtips.springboot2webfluxsample.controller;

import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.spring.webflux.client.WingtipsSpringWebfluxExchangeFilterFunction;
import com.nike.wingtips.spring.webflux.server.RequestWithHeadersServerWebExchangeAdapter;
import com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter;
import com.nike.wingtips.util.TracingState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils.tracingStateFromContext;
import static com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils.tracingStateFromExchange;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.functionWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.supplierWithTracing;

/**
 * A set of example endpoints showing tracing working in a Spring Boot 2 WebFlux app. In particular it shows:
 *
 * <ul>
 *     <li>
 *         {@link WingtipsSpringWebfluxWebFilter} starting and completing request spans in a variety of situations.
 *     </li>
 *     <li>
 *         Automatic subspan-generation and propagation of tracing info on downstream requests when using Spring's
 *         reactive {@link WebClient} to make HTTP calls.
 *     </li>
 *     <li>
 *         Examples for how you can make tracing hop threads during async processing - see
 *         {@link #getAsyncCompletableFutureEndpoint()}, {@link #getNestedWebClientCallEndpoint()},
 *         and {@link #getSpanInfoCallEndpoint(ServerWebExchange)}.
 *     </li>
 * </ul>
 */
@RestController
@RequestMapping("/")
@SuppressWarnings({"WeakerAccess"})
public class SampleController {

    public static final String SAMPLE_PATH_BASE = "/sample";

    public static final String SIMPLE_PATH = SAMPLE_PATH_BASE + "/simple";
    public static final String SIMPLE_RESULT = "simple endpoint hit - check logs for distributed tracing info";

    public static final String MONO_PATH = SAMPLE_PATH_BASE + "/mono";
    public static final String MONO_RESULT = "mono endpoint hit - check logs for distributed tracing info";

    public static final String FLUX_PATH = SAMPLE_PATH_BASE + "/flux";
    public static final List<String> FLUX_RESULT = Arrays.asList(
        "flux endpoint hit - ",
        "check logs for distributed tracing info"
    );

    public static final String ROUTER_FUNCTION_PATH = SAMPLE_PATH_BASE + "/router-function";
    public static final String ROUTER_FUNCTION_RESULT =
        "router function endpoint hit - check logs for distributed tracing info";

    public static final String ASYNC_FUTURE_PATH = SAMPLE_PATH_BASE + "/async-future";
    public static final String ASYNC_FUTURE_RESULT =
        "async endpoint hit (CompletableFuture) - check logs for distributed tracing info";

    public static final String ASYNC_TIMEOUT_PATH = SAMPLE_PATH_BASE + "/async-timeout";
    public static final String ASYNC_ERROR_PATH = SAMPLE_PATH_BASE + "/async-error";

    public static final String SPAN_INFO_CALL_PATH = SAMPLE_PATH_BASE + "/span-info";
    public static final String NESTED_WEB_CLIENT_CALL_PATH = SAMPLE_PATH_BASE + "/nested-webclient-call";

    public static final String PATH_PARAM_ENDPOINT_PATH_PREFIX = SAMPLE_PATH_BASE + "/path-param";
    public static final String PATH_PARAM_ENDPOINT_RESULT =
        "path param endpoint hit - check logs for distributed tracing info";

    public static final String WILDCARD_PATH_PREFIX = SAMPLE_PATH_BASE + "/wildcard";
    public static final String WILDCARD_RESULT = "wildcard endpoint hit - check logs for distributed tracing info";

    public static final long SLEEP_TIME_MILLIS = 100;

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private static final Logger logger = LoggerFactory.getLogger(SampleController.class);

    private final int serverPort;

    private final WebClient wingtipsEnabledWebClient = WebClient
        .builder()
        .filter(new WingtipsSpringWebfluxExchangeFilterFunction())
        .build();

    private final List<String> userIdHeaderKeys;

    @Autowired
    public SampleController(ServerProperties serverProps, Environment environment) {
        this.serverPort = serverProps.getPort();
        String userIdHeaderKeysFromEnv = environment.getProperty("wingtips.user-id-header-keys");
        this.userIdHeaderKeys = (userIdHeaderKeysFromEnv == null)
                                ? null
                                : Arrays.asList(userIdHeaderKeysFromEnv.split(","));
    }

    @GetMapping(path = SIMPLE_PATH)
    @SuppressWarnings("unused")
    public String getSimpleEndpoint() {
        logger.info("Simple endpoint hit");

        return SIMPLE_RESULT;
    }

    @GetMapping(path = MONO_PATH)
    @SuppressWarnings("unused")
    public Mono<String> getMonoEndpoint() {
        logger.info("Mono endpoint hit");

        return Mono
            .delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
            .map(d -> MONO_RESULT);
    }

    @GetMapping(FLUX_PATH)
    @SuppressWarnings("unused")
    public Flux<String> getFluxEndpoint() {
        logger.info("Flux endpoint hit");

        long delayPerElementMillis = SLEEP_TIME_MILLIS / FLUX_RESULT.size();
        return Flux.fromIterable(FLUX_RESULT).delayElements(Duration.ofMillis(delayPerElementMillis));
    }

    @SuppressWarnings("unused")
    public Mono<ServerResponse> routerFunctionEndpoint(ServerRequest request) {
        logger.info("RouterFunction endpoint hit");

        return ServerResponse
            .ok()
            .syncBody(ROUTER_FUNCTION_RESULT)
            .delayElement(Duration.ofMillis(SLEEP_TIME_MILLIS));
    }

    @GetMapping(path = PATH_PARAM_ENDPOINT_PATH_PREFIX + "/{somePathParam}")
    @SuppressWarnings("unused")
    public Mono<String> getPathParamEndpoint(@PathVariable String somePathParam) {
        logger.info("Path param endpoint hit - somePathParam: {}", somePathParam);

        return Mono
            .delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
            .map(d -> PATH_PARAM_ENDPOINT_RESULT);
    }

    @GetMapping(path = WILDCARD_PATH_PREFIX + "/**")
    @SuppressWarnings("unused")
    public Mono<String> getWildcardEndpoint() {
        logger.info("Wildcard endpoint hit");

        return Mono
            .delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
            .map(d -> WILDCARD_RESULT);
    }

    @GetMapping(path = ASYNC_FUTURE_PATH)
    @SuppressWarnings("unused")
    public CompletableFuture<String> getAsyncCompletableFutureEndpoint() {
        logger.info("Async endpoint hit (CompletableFuture)");

        return CompletableFuture.supplyAsync(
            supplierWithTracing(
                () -> {
                    logger.info("Async endpoint (CompletableFuture) async logic");
                    sleepThread(SLEEP_TIME_MILLIS);
                    return ASYNC_FUTURE_RESULT;
                }
            ),
            executor
        );
    }

    @GetMapping(path = ASYNC_TIMEOUT_PATH)
    @SuppressWarnings("unused")
    public Mono<Void> getAsyncTimeoutEndpoint() {
        logger.info("Async timeout endpoint hit");

        return Mono
            .delay(Duration.ofMillis(SLEEP_TIME_MILLIS * 2))
            .timeout(Duration.ofMillis(SLEEP_TIME_MILLIS))
            .then();
    }

    @GetMapping(path = ASYNC_ERROR_PATH)
    @SuppressWarnings("unused")
    public Mono<String> getAsyncErrorEndpoint() {
        logger.info("Async error endpoint hit");

        return Mono
            .delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
            .map(d -> {
                throw new RuntimeException("Intentional exception by " + ASYNC_ERROR_PATH + " endpoint");
            });
    }

    @GetMapping(path = SPAN_INFO_CALL_PATH)
    @SuppressWarnings("unused")
    public Mono<EndpointSpanInfoDto> getSpanInfoCallEndpoint(ServerWebExchange exchange) {
        logger.info("Span info endpoint hit.");

        Span currentSpan = Tracer.getInstance().getCurrentSpan();
        TracingState tracingStateOnEndpointThread = TracingState.getCurrentThreadTracingState();

        logger.info("Current span on endpoint thread: {}", currentSpan.toJSON());
        assert (tracingStateOnEndpointThread != null);
        assert (currentSpan == tracingStateOnEndpointThread.spanStack.peek());
        logger.info("The current span is the top of the stack for the endpoint thread's TracingState");

        TracingState tracingStateFromExchange = tracingStateFromExchange(exchange);
        assert (tracingStateFromExchange != null);
        assert tracingStateFromExchange.equals(tracingStateOnEndpointThread);
        logger.info(
            "The TracingState extracted from ServerWebExchange attributes matches the endpoint thread TracingState"
        );

        return Mono
            .subscriberContext()
            .map(context -> {
                TracingState tracingStateFromContext = tracingStateFromContext(context);
                assert (tracingStateFromContext != null);
                // The tracing state from the Context should be a logical-equals match for the endpoint thread
                //      TracingState (since TracingState.getCurrentThreadTracingState() creates a new object)...
                assert tracingStateFromContext.equals(tracingStateOnEndpointThread);
                // ... but it should be an identical object match for the TracingState from the ServerWebExchange.
                assert tracingStateFromContext == tracingStateFromExchange;
                runnableWithTracing(
                    // Surround the log message with runnableWithTracing() so it's tagged with the trace ID.
                    () -> logger.info(
                        "The TracingState extracted from Mono Context matches the endpoint thread "
                        + "and ServerWebExchange TracingStates"
                    ),
                    tracingStateFromContext
                ).run();
                return context;
            })
            .delayElement(Duration.ofMillis(SLEEP_TIME_MILLIS))
            .map(d -> new EndpointSpanInfoDto(exchange, currentSpan, userIdHeaderKeys));
    }

    @GetMapping(path = NESTED_WEB_CLIENT_CALL_PATH)
    @SuppressWarnings("unused")
    public Mono<EndpointSpanInfoDto> getNestedWebClientCallEndpoint() {
        logger.info("Nested blocking call endpoint hit.");

        TracingState overallRequestTracingState = TracingState.getCurrentThreadTracingState();
        Span overallRequestSpan = Tracer.getInstance().getCurrentSpan();

        return Mono
            .delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
            .flatMap(
                functionWithTracing(
                    d -> {
                        URI nestedCallUri = URI.create("http://localhost:" + serverPort + SPAN_INFO_CALL_PATH + "?someQuery=foobar");
                        logger.info("...Calling: " + nestedCallUri.toString());

                        return wingtipsEnabledWebClient
                            .get()
                            .uri(nestedCallUri)
                            .headers(headers -> addUserIdHeader(headers, overallRequestSpan))
                            .exchange()
                            .flatMap(
                                response -> response
                                    .bodyToMono(new ParameterizedTypeReference<EndpointSpanInfoDto>() {})
                                    .doOnTerminate(
                                        runnableWithTracing(
                                            () -> logger.info("Nested WebClient call complete"),
                                            overallRequestTracingState
                                        )
                                    )
                            );
                    },
                    overallRequestTracingState
                )
            );
    }

    private void addUserIdHeader(HttpHeaders headers, Span overallRequestSpan) {
        String userId = overallRequestSpan.getUserId();

        if (userIdHeaderKeys == null || userIdHeaderKeys.isEmpty() || userId == null) {
            return;
        }

        headers.set(userIdHeaderKeys.get(0), userId);
    }

    @SuppressWarnings("SameParameterValue")
    private static void sleepThread(long sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class EndpointSpanInfoDto {
        public final SpanInfoDto parent_span_info;
        public final SpanInfoDto endpoint_execution_span_info;

        // Here for deserialization support
        @SuppressWarnings("unused")
        private EndpointSpanInfoDto() {
            this(null, null);
        }

        public EndpointSpanInfoDto(ServerWebExchange exchange, Span endpoint_execution_span, List<String> userIdHeaderKeys) {
            HttpHeaders headers = exchange.getRequest().getHeaders();
            this.parent_span_info = new SpanInfoDto(
                headers.getFirst(TraceHeaders.TRACE_ID),
                headers.getFirst(TraceHeaders.SPAN_ID),
                headers.getFirst(TraceHeaders.PARENT_SPAN_ID),
                headers.getFirst(TraceHeaders.TRACE_SAMPLED),
                HttpRequestTracingUtils.getUserIdFromRequestWithHeaders(
                    new RequestWithHeadersServerWebExchangeAdapter(exchange),
                    userIdHeaderKeys
                )
            );
            this.endpoint_execution_span_info = new SpanInfoDto(
                endpoint_execution_span.getTraceId(),
                endpoint_execution_span.getSpanId(),
                endpoint_execution_span.getParentSpanId(),
                String.valueOf(endpoint_execution_span.isSampleable()),
                endpoint_execution_span.getUserId()
            );
        }

        public EndpointSpanInfoDto(SpanInfoDto parent_span_info, SpanInfoDto endpoint_execution_span_info) {
            this.parent_span_info = parent_span_info;
            this.endpoint_execution_span_info = endpoint_execution_span_info;
        }
    }

    public static final class SpanInfoDto {
        public final String trace_id;
        public final String span_id;
        public final String parent_span_id;
        public final String sampleable;
        public final String user_id;

        // Here for deserialization support
        @SuppressWarnings("unused")
        private SpanInfoDto() {
            this(null, null, null, null, null);
        }

        public SpanInfoDto(String trace_id, String span_id, String parent_span_id, String sampleable, String user_id) {
            this.trace_id = trace_id;
            this.span_id = span_id;
            this.parent_span_id = parent_span_id;
            this.sampleable = sampleable;
            this.user_id = user_id;
        }
    }
}
