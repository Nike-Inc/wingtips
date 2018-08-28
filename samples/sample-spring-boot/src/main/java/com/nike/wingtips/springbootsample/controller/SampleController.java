package com.nike.wingtips.springbootsample.controller;

import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.HttpSpanFactory;
import com.nike.wingtips.servlet.RequestTracingFilter;
import com.nike.wingtips.spring.util.WingtipsSpringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.http.HttpServletRequest;

import static com.nike.wingtips.spring.util.WingtipsSpringUtil.failureCallbackWithTracing;
import static com.nike.wingtips.spring.util.WingtipsSpringUtil.successCallbackWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.callableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.supplierWithTracing;

/**
 * A set of example endpoints showing tracing working in a Spring Boot app. In particular it shows:
 *
 * <ul>
 *     <li>
 *         {@link RequestTracingFilter} starting and completing request spans in a variety of situations.
 *     </li>
 *     <li>
 *         Automatic subspan-generation and propagation of tracing info on downstream requests when using Spring's
 *         {@link RestTemplate} or {@link AsyncRestTemplate} to make HTTP calls.
 *     </li>
 *     <li>
 *         Examples for how you can make tracing hop threads during async processing - see
 *         {@link #getAsyncDeferredResult()}, {@link #getAsyncCallable()}, {@link #getAsyncCompletableFuture()}, and
 *         {@link #getNestedAsyncCall()}.
 *     </li>
 * </ul>
 */
@RestController
@RequestMapping("/")
@SuppressWarnings({"WeakerAccess", "deprecation"})
public class SampleController {

    public static final String SAMPLE_PATH_BASE = "/sample";

    public static final String SIMPLE_PATH = SAMPLE_PATH_BASE + "/simple";
    public static final String SIMPLE_RESULT = "simple endpoint hit - check logs for distributed tracing info";

    public static final String BLOCKING_PATH = SAMPLE_PATH_BASE + "/blocking";
    public static final String BLOCKING_RESULT = "blocking endpoint hit - check logs for distributed tracing info";

    public static final String ASYNC_DEFERRED_RESULT_PATH = SAMPLE_PATH_BASE + "/async";
    public static final String ASYNC_DEFERRED_RESULT_PAYLOAD =
        "async endpoint hit (DeferredResult) - check logs for distributed tracing info";

    public static final String ASYNC_CALLABLE_PATH = SAMPLE_PATH_BASE + "/async-callable";
    public static final String ASYNC_CALLABLE_RESULT =
        "async endpoint hit (Callable) - check logs for distributed tracing info";

    public static final String ASYNC_FUTURE_PATH = SAMPLE_PATH_BASE + "/async-future";
    public static final String ASYNC_FUTURE_RESULT =
        "async endpoint hit (CompletableFuture) - check logs for distributed tracing info";

    public static final String ASYNC_TIMEOUT_PATH = SAMPLE_PATH_BASE + "/async-timeout";
    public static final String ASYNC_ERROR_PATH = SAMPLE_PATH_BASE + "/async-error";

    public static final String SPAN_INFO_CALL_PATH = SAMPLE_PATH_BASE + "/span-info";
    public static final String NESTED_BLOCKING_CALL_PATH = SAMPLE_PATH_BASE + "/nested-blocking-call";
    public static final String NESTED_ASYNC_CALL_PATH = SAMPLE_PATH_BASE + "/nested-async-call";

    public static final String PATH_PARAM_ENDPOINT_PATH_PREFIX = SAMPLE_PATH_BASE + "/path-param";
    public static final String PATH_PARAM_ENDPOINT_RESULT =
        "path param endpoint hit - check logs for distributed tracing info";

    public static final String WILDCARD_PATH_PREFIX = SAMPLE_PATH_BASE + "/wildcard";
    public static final String WILDCARD_RESULT = "wildcard endpoint hit - check logs for distributed tracing info";

    public static final long SLEEP_TIME_MILLIS = 100;

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private static final Logger logger = LoggerFactory.getLogger(SampleController.class);

    private final int serverPort;
    private final RestTemplate wingtipsEnabledRestTemplate = WingtipsSpringUtil.createTracingEnabledRestTemplate();
    private final AsyncRestTemplate wingtipsEnabledAsyncRestTemplate =
        WingtipsSpringUtil.createTracingEnabledAsyncRestTemplate();

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
    public String getSimple() {
        return SIMPLE_RESULT;
    }

    @GetMapping(path = BLOCKING_PATH)
    @SuppressWarnings("unused")
    public String getBlocking() {
        logger.info("Blocking endpoint hit");
        sleepThread(SLEEP_TIME_MILLIS);

        return BLOCKING_RESULT;
    }

    @GetMapping(path = PATH_PARAM_ENDPOINT_PATH_PREFIX + "/{somePathParam}")
    @SuppressWarnings("unused")
    public String getPathParam(@PathVariable String somePathParam) {
        logger.info("Path param endpoint hit - somePathParam: {}", somePathParam);
        sleepThread(SLEEP_TIME_MILLIS);

        return PATH_PARAM_ENDPOINT_RESULT;
    }

    @GetMapping(path = WILDCARD_PATH_PREFIX + "/**")
    @SuppressWarnings("unused")
    public String getWildcard() {
        logger.info("Wildcard endpoint hit");
        sleepThread(SLEEP_TIME_MILLIS);

        return WILDCARD_RESULT;
    }

    @GetMapping(path = ASYNC_DEFERRED_RESULT_PATH)
    @SuppressWarnings("unused")
    public DeferredResult<String> getAsyncDeferredResult() {
        logger.info("Async endpoint hit (DeferredResult)");

        DeferredResult<String> asyncResponse = new DeferredResult<>();

        executor.execute(runnableWithTracing(() -> {
            logger.info("Async endpoint (DeferredResult) async logic");
            sleepThread(SLEEP_TIME_MILLIS);
            asyncResponse.setResult(ASYNC_DEFERRED_RESULT_PAYLOAD);
        }));

        return asyncResponse;
    }

    @GetMapping(path = ASYNC_CALLABLE_PATH)
    @SuppressWarnings("unused")
    public Callable<String> getAsyncCallable() {
        logger.info("Async endpoint hit (Callable)");

        return callableWithTracing(() -> {
            logger.info("Async endpoint (Callable) async logic");
            sleepThread(SLEEP_TIME_MILLIS);
            return ASYNC_CALLABLE_RESULT;
        });
    }

    @GetMapping(path = ASYNC_FUTURE_PATH)
    @SuppressWarnings("unused")
    public CompletableFuture<String> getAsyncCompletableFuture() {
        logger.info("Async endpoint hit (CompletableFuture)");

        return CompletableFuture.supplyAsync(supplierWithTracing(
            () -> {
                logger.info("Async endpoint (CompletableFuture) async logic");
                sleepThread(SLEEP_TIME_MILLIS);
                return ASYNC_FUTURE_RESULT;
            }),
            executor
        );
    }

    @GetMapping(path = ASYNC_TIMEOUT_PATH)
    @SuppressWarnings("unused")
    public DeferredResult<String> getAsyncTimeout() {
        logger.info("Async timeout endpoint hit");

        return new DeferredResult<>(SLEEP_TIME_MILLIS);
    }

    @GetMapping(path = ASYNC_ERROR_PATH)
    @SuppressWarnings("unused")
    public DeferredResult<String> getAsyncError() {
        logger.info("Async error endpoint hit");

        sleepThread(SLEEP_TIME_MILLIS);

        DeferredResult<String> deferredResult = new DeferredResult<>();
        deferredResult.setErrorResult(new RuntimeException("Intentional exception by asyncError endpoint"));

        return deferredResult;
    }

    @GetMapping(path = SPAN_INFO_CALL_PATH)
    @SuppressWarnings("unused")
    public EndpointSpanInfoDto getSpanInfoCall(HttpServletRequest request) {
        logger.info("Span info endpoint hit. Sleeping...");
        sleepThread(SLEEP_TIME_MILLIS);

        return new EndpointSpanInfoDto(request, Tracer.getInstance().getCurrentSpan(), userIdHeaderKeys);
    }

    @GetMapping(path = NESTED_BLOCKING_CALL_PATH)
    @SuppressWarnings("unused")
    public EndpointSpanInfoDto getNestedBlockingCall() {
        logger.info("Nested blocking call endpoint hit. Sleeping...");
        sleepThread(SLEEP_TIME_MILLIS);

        URI nestedCallUri = URI.create( "http://localhost:" + serverPort + SPAN_INFO_CALL_PATH + "?someQuery=foobar");
        logger.info("...Calling: " + nestedCallUri.toString());

        EndpointSpanInfoDto returnVal = wingtipsEnabledRestTemplate
            .exchange(nestedCallUri, HttpMethod.GET, getHttpEntityWithUserIdHeader(), EndpointSpanInfoDto.class)
            .getBody();

        logger.info("Blocking RestTemplate call complete");
        return returnVal;
    }

    @GetMapping(path = NESTED_ASYNC_CALL_PATH)
    @SuppressWarnings("unused")
    public DeferredResult<EndpointSpanInfoDto> getNestedAsyncCall() {
        DeferredResult<EndpointSpanInfoDto> asyncResponse = new DeferredResult<>();

        executor.execute(runnableWithTracing(() -> {
            try {
                logger.info("Nested async call endpoint hit. Sleeping...");
                sleepThread(SLEEP_TIME_MILLIS);

                URI nestedCallUri = URI.create(
                    "http://localhost:" + serverPort + SPAN_INFO_CALL_PATH + "?someQuery=foobar"
                );
                logger.info("...Calling: " + nestedCallUri.toString());

                ListenableFuture<ResponseEntity<EndpointSpanInfoDto>> asyncRestTemplateResultFuture =
                    wingtipsEnabledAsyncRestTemplate.exchange(
                        nestedCallUri, HttpMethod.GET, getHttpEntityWithUserIdHeader(), EndpointSpanInfoDto.class
                    );

                asyncRestTemplateResultFuture.addCallback(
                    successCallbackWithTracing(result -> {
                        logger.info("AsyncRestTemplate call complete");
                        asyncResponse.setResult(result.getBody());
                    }),
                    failureCallbackWithTracing(asyncResponse::setErrorResult)
                );
            }
            catch(Throwable t) {
                asyncResponse.setErrorResult(t);
            }
        }));

        return asyncResponse;
    }

    private HttpEntity getHttpEntityWithUserIdHeader() {
        HttpHeaders headers = new HttpHeaders();
        String userId = Tracer.getInstance().getCurrentSpan().getUserId();

        if (userIdHeaderKeys == null || userIdHeaderKeys.isEmpty() || userId == null) {
            return new HttpEntity(headers);
        }

        headers.set(userIdHeaderKeys.get(0), userId);
        return new HttpEntity(headers);
    }

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

        public EndpointSpanInfoDto(HttpServletRequest request, Span endpoint_execution_span, List<String> userIdHeaderKeys) {
            this.parent_span_info = new SpanInfoDto(
                request.getHeader(TraceHeaders.TRACE_ID),
                request.getHeader(TraceHeaders.SPAN_ID),
                request.getHeader(TraceHeaders.PARENT_SPAN_ID),
                request.getHeader(TraceHeaders.TRACE_SAMPLED),
                HttpSpanFactory.getUserIdFromHttpServletRequest(request, userIdHeaderKeys)
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
