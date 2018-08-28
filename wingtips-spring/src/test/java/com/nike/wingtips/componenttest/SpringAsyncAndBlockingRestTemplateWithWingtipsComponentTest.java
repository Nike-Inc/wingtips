package com.nike.wingtips.componenttest;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.servlet.RequestTracingFilter;
import com.nike.wingtips.spring.interceptor.WingtipsAsyncClientHttpRequestInterceptor;
import com.nike.wingtips.spring.interceptor.WingtipsClientHttpRequestInterceptor;
import com.nike.wingtips.spring.interceptor.tag.SpringHttpClientTagAdapter;
import com.nike.wingtips.spring.util.WingtipsSpringUtil;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.tags.WingtipsTags;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import static com.nike.wingtips.componenttest.SpringAsyncAndBlockingRestTemplateWithWingtipsComponentTest.TestBackendServer.ENDPOINT_PATH;
import static com.nike.wingtips.componenttest.SpringAsyncAndBlockingRestTemplateWithWingtipsComponentTest.TestBackendServer.ENDPOINT_PAYLOAD;
import static com.nike.wingtips.componenttest.SpringAsyncAndBlockingRestTemplateWithWingtipsComponentTest.TestBackendServer.SLEEP_TIME_MILLIS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component test validating Wingtips' integration with Spring {@link RestTemplate} and {@link AsyncRestTemplate}.
 * This launches a real running server on a random port and sets up Wingtips-instrumented Spring {@link RestTemplate}s
 * and {@link AsyncRestTemplate}s and fires requests through them at the server to verify the integration.
 *
 * <p>These tests cover {@link WingtipsClientHttpRequestInterceptor} and
 * {@link WingtipsAsyncClientHttpRequestInterceptor}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class SpringAsyncAndBlockingRestTemplateWithWingtipsComponentTest {

    private static final int SERVER_PORT = findFreePort();
    private static ConfigurableApplicationContext serverAppContext;

    private SpanRecorder spanRecorder;

    @BeforeClass
    public static void beforeClass() {
        serverAppContext = SpringApplication.run(TestBackendServer.class, "--server.port=" + SERVER_PORT);
    }

    @AfterClass
    public static void afterClass() {
        SpringApplication.exit(serverAppContext);
    }

    private static int findFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void beforeMethod() {
        resetTracing();

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
        removeSpanRecorderLifecycleListener();
    }

    private void removeSpanRecorderLifecycleListener() {
        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            if (listener instanceof SpanRecorder) {
                Tracer.getInstance().removeSpanLifecycleListener(listener);
            }
        }
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void verify_blocking_RestTemplate_with_Wingtips_interceptor_traced_correctly(
        boolean spanAlreadyExistsBeforeCall, boolean subspanOptionOn
    ) {
        // given
        Span parent = null;
        if (spanAlreadyExistsBeforeCall) {
            parent = Tracer.getInstance().startRequestWithRootSpan("somePreexistingParentSpan");
        }

        String pathTemplateForTags = "/some/path/template/" + UUID.randomUUID().toString();

        RestTemplate restTemplateWithWingtips = WingtipsSpringUtil.createTracingEnabledRestTemplate(
            subspanOptionOn,
            ZipkinHttpTagStrategy.getDefaultInstance(),
            new SpringHttpClientTagAdapterWithHttpRouteKnowledge(pathTemplateForTags)
        );

        // We always expect at least one span to be completed as part of the call: the server span.
        //      We may or may not have a second span completed depending on the value of subspanOptionOn.
        int expectedNumSpansCompleted = (subspanOptionOn) ? 2 : 1;

        String fullRequestUrl = "http://localhost:" + SERVER_PORT + ENDPOINT_PATH + "?foo=bar";

        // when
        ResponseEntity<String> response = restTemplateWithWingtips.exchange(
            fullRequestUrl, HttpMethod.GET, null, String.class
        );

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(ENDPOINT_PAYLOAD);
        verifySpansCompletedAndReturnedInResponse(
            response, SLEEP_TIME_MILLIS, expectedNumSpansCompleted, parent, subspanOptionOn
        );

        if (subspanOptionOn) {
            verifySpanNameAndTags(
                findSpringRestTemplateSpanFromCompletedSpans(false),
                "GET " + pathTemplateForTags,
                "GET",
                ENDPOINT_PATH,
                fullRequestUrl,
                pathTemplateForTags,
                response.getStatusCode().value(),
                "spring.resttemplate"
            );
        }

        if (parent != null) {
            parent.close();
        }
    }

    private Span findSpringRestTemplateSpanFromCompletedSpans(boolean expectAsync) {
        String expectedSpanHandler = (expectAsync) ? "spring.asyncresttemplate" : "spring.resttemplate";

        List<Span> restTemplateSpans = spanRecorder.completedSpans
            .stream()
            .filter(s -> expectedSpanHandler.equals(s.getTags().get(WingtipsTags.SPAN_HANDLER)))
            .collect(Collectors.toList());

        assertThat(restTemplateSpans)
            .withFailMessage(
                "Expected to find exactly one Span that came from Spring RestTemplate - instead found: "
                + restTemplateSpans.size()
            )
            .hasSize(1);

        return restTemplateSpans.get(0);
    }

    private void verifySpanNameAndTags(
        Span span,
        String expectedSpanName,
        String expectedHttpMethodTagValue,
        String expectedPathTagValue,
        String expectedUrlTagValue,
        String expectedHttpRouteTagValue,
        int expectedStatusCodeTagValue,
        String expectedSpanHandlerTagValue
    ) {
        assertThat(span.getSpanName()).isEqualTo(expectedSpanName);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_METHOD)).isEqualTo(expectedHttpMethodTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_PATH)).isEqualTo(expectedPathTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_URL)).isEqualTo(expectedUrlTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_ROUTE)).isEqualTo(expectedHttpRouteTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_STATUS_CODE))
            .isEqualTo(String.valueOf(expectedStatusCodeTagValue));
        assertThat(span.getTags().get(WingtipsTags.SPAN_HANDLER)).isEqualTo(expectedSpanHandlerTagValue);
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void verify_AsyncRestTemplate_with_Wingtips_interceptor_traced_correctly(
        boolean spanAlreadyExistsBeforeCall, boolean subspanOptionOn
    ) throws ExecutionException, InterruptedException {
        // given
        Span parent = null;
        if (spanAlreadyExistsBeforeCall) {
            parent = Tracer.getInstance().startRequestWithRootSpan("somePreexistingParentSpan");
        }

        String pathTemplateForTags = "/some/path/template/" + UUID.randomUUID().toString();

        AsyncRestTemplate asyncRestTemplateWithWingtips = WingtipsSpringUtil.createTracingEnabledAsyncRestTemplate(
            subspanOptionOn,
            ZipkinHttpTagStrategy.getDefaultInstance(),
            new SpringHttpClientTagAdapterWithHttpRouteKnowledge(pathTemplateForTags)
        );

        // We always expect at least one span to be completed as part of the call: the server span.
        //      We may or may not have a second span completed depending on the value of subspanOptionOn.
        int expectedNumSpansCompleted = (subspanOptionOn) ? 2 : 1;

        String fullRequestUrl = "http://localhost:" + SERVER_PORT + ENDPOINT_PATH + "?foo=bar";

        // when
        ListenableFuture<ResponseEntity<String>> responseFuture = asyncRestTemplateWithWingtips.exchange(
            fullRequestUrl, HttpMethod.GET, null, String.class
        );
        ResponseEntity<String> response = responseFuture.get();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(ENDPOINT_PAYLOAD);
        verifySpansCompletedAndReturnedInResponse(
            response, SLEEP_TIME_MILLIS, expectedNumSpansCompleted, parent, subspanOptionOn
        );

        if (subspanOptionOn) {
            verifySpanNameAndTags(
                findSpringRestTemplateSpanFromCompletedSpans(true),
                "GET " + pathTemplateForTags,
                "GET",
                ENDPOINT_PATH,
                fullRequestUrl,
                pathTemplateForTags,
                response.getStatusCode().value(),
                "spring.asyncresttemplate"
            );
        }

        if (parent != null) {
            parent.close();
        }
    }

    private void verifySpansCompletedAndReturnedInResponse(
        ResponseEntity<String> response,
        long expectedMinSpanDurationMillis,
        int expectedNumSpansCompleted,
        Span expectedUpstreamSpan,
        boolean expectSubspanFromHttpClient
    ) {
        // We can have a race condition where the response is sent and we try to verify here before the servlet filter
        //      has had a chance to complete the span. Wait a few milliseconds to give the servlet filter time to
        //      finish.
        waitUntilSpanRecorderHasExpectedNumSpans(expectedNumSpansCompleted);

        assertThat(spanRecorder.completedSpans).hasSize(expectedNumSpansCompleted);
        String traceIdFromResponse = response.getHeaders().getFirst(TraceHeaders.TRACE_ID);
        assertThat(traceIdFromResponse).isNotNull();

        spanRecorder.completedSpans.forEach(
            completedSpan -> assertThat(completedSpan.getTraceId()).isEqualTo(traceIdFromResponse)
        );

        // Find the span with the longest duration - this is the outermost span (either from the server or from
        //      the Spring [Async/]RestTemplate client depending on whether the subspan option was on).
        Span outermostSpan = spanRecorder.completedSpans.stream()
                                                        .max(Comparator.comparing(Span::getDurationNanos))
                                                        .get();
        assertThat(TimeUnit.NANOSECONDS.toMillis(outermostSpan.getDurationNanos()))
            .isGreaterThanOrEqualTo(expectedMinSpanDurationMillis);

        SpanPurpose expectedOutermostSpanPurpose = (expectSubspanFromHttpClient)
                                                   ? SpanPurpose.CLIENT
                                                   : SpanPurpose.SERVER;
        assertThat(outermostSpan.getSpanPurpose()).isEqualTo(expectedOutermostSpanPurpose);

        if (expectedUpstreamSpan == null) {
            assertThat(outermostSpan.getParentSpanId()).isNull();
        }
        else {
            assertThat(outermostSpan.getTraceId()).isEqualTo(expectedUpstreamSpan.getTraceId());
            assertThat(outermostSpan.getParentSpanId()).isEqualTo(expectedUpstreamSpan.getSpanId());
        }
    }

    private void waitUntilSpanRecorderHasExpectedNumSpans(int expectedNumSpans) {
        long timeoutMillis = 5000;
        long startTimeMillis = System.currentTimeMillis();
        while (spanRecorder.completedSpans.size() < expectedNumSpans) {
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            long timeSinceStart = System.currentTimeMillis() - startTimeMillis;
            if (timeSinceStart > timeoutMillis) {
                throw new RuntimeException(
                    "spanRecorder did not have the expected number of spans after waiting "
                    + timeoutMillis + " milliseconds"
                );
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    private static class SpanRecorder implements SpanLifecycleListener {

        public final List<Span> completedSpans = new ArrayList<>();

        @Override
        public void spanStarted(Span span) {
        }

        @Override
        public void spanSampled(Span span) {
        }

        @Override
        public void spanCompleted(Span span) {
            completedSpans.add(span);
        }
    }

    @SpringBootApplication
    public static class TestBackendServer {

        public static final String ENDPOINT_PATH = "/foo";
        public static final String ENDPOINT_PAYLOAD = "endpoint-payload-" + UUID.randomUUID().toString();
        public static final long SLEEP_TIME_MILLIS = 100;

        @Bean
        public RequestTracingFilter requestTracingFilter() {
            return new RequestTracingFilter();
        }

        @RestController
        @RequestMapping("/")
        public static class Controller {

            @GetMapping(path = ENDPOINT_PATH)
            @SuppressWarnings("unused")
            public String basicEndpoint(HttpServletRequest request) throws InterruptedException {
                String queryString = request.getQueryString();
                Thread.sleep(SLEEP_TIME_MILLIS);
                return ENDPOINT_PAYLOAD;
            }

        }

    }

    static class SpringHttpClientTagAdapterWithHttpRouteKnowledge extends SpringHttpClientTagAdapter {

        private final String httpRouteForAllRequests;

        SpringHttpClientTagAdapterWithHttpRouteKnowledge(String httpRouteForAllRequests) {
            this.httpRouteForAllRequests = httpRouteForAllRequests;
        }

        @Override
        public @Nullable String getRequestUriPathTemplate(
            @Nullable HttpRequest request, @Nullable ClientHttpResponse response
        ) {
            return httpRouteForAllRequests;
        }
    }
}
