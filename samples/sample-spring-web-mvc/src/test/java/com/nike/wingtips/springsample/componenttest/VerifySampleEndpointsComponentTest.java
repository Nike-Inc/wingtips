package com.nike.wingtips.springsample.componenttest;

import com.nike.internal.util.MapBuilder;
import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.springsample.Main;
import com.nike.wingtips.springsample.controller.SampleController.EndpointSpanInfoDto;
import com.nike.wingtips.springsample.controller.SampleController.SpanInfoDto;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.restassured.response.ExtractableResponse;

import static com.nike.wingtips.springsample.controller.SampleController.ASYNC_CALLABLE_PATH;
import static com.nike.wingtips.springsample.controller.SampleController.ASYNC_CALLABLE_RESULT;
import static com.nike.wingtips.springsample.controller.SampleController.ASYNC_DEFERRED_RESULT_PATH;
import static com.nike.wingtips.springsample.controller.SampleController.ASYNC_DEFERRED_RESULT_PAYLOAD;
import static com.nike.wingtips.springsample.controller.SampleController.ASYNC_ERROR_PATH;
import static com.nike.wingtips.springsample.controller.SampleController.ASYNC_FUTURE_PATH;
import static com.nike.wingtips.springsample.controller.SampleController.ASYNC_FUTURE_RESULT;
import static com.nike.wingtips.springsample.controller.SampleController.BLOCKING_PATH;
import static com.nike.wingtips.springsample.controller.SampleController.BLOCKING_RESULT;
import static com.nike.wingtips.springsample.controller.SampleController.NESTED_ASYNC_CALL_PATH;
import static com.nike.wingtips.springsample.controller.SampleController.NESTED_BLOCKING_CALL_PATH;
import static com.nike.wingtips.springsample.controller.SampleController.SIMPLE_PATH;
import static com.nike.wingtips.springsample.controller.SampleController.SIMPLE_RESULT;
import static com.nike.wingtips.springsample.controller.SampleController.SLEEP_TIME_MILLIS;
import static com.nike.wingtips.springsample.controller.SampleController.SPAN_INFO_CALL_PATH;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component test that starts up the sample server and hits it with various requests and verifies that the expected
 * request spans are created/completed appropriately.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class VerifySampleEndpointsComponentTest {

    private static final int SERVER_PORT = findFreePort();
    private static Server server;

    private SpanRecorder spanRecorder;

    @BeforeClass
    public static void beforeClass() throws Exception {
        server = Main.createServer(SERVER_PORT);
        server.start();
        for (int i = 0; i < 100; i++) {
            if (server.isStarted())
                return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("Server is not up after waiting 10 seconds. Aborting tests.");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null) {
            server.stop();
            server.destroy();
        }
    }

    private static int findFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void beforeMethod() {
        clearTracerSpanLifecycleListeners();

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);
    }

    @After
    public void afterMethod() {
        clearTracerSpanLifecycleListeners();
    }

    private void clearTracerSpanLifecycleListeners() {
        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            Tracer.getInstance().removeSpanLifecycleListener(listener);
        }
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_simple_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(SIMPLE_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(SIMPLE_RESULT);
        verifySingleSpanCompletedAndReturnedInResponse(response, 0, upstreamSpanInfo.getLeft());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_blocking_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(BLOCKING_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(BLOCKING_RESULT);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_async_DeferredResult_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(ASYNC_DEFERRED_RESULT_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(ASYNC_DEFERRED_RESULT_PAYLOAD);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_async_Callable_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(ASYNC_CALLABLE_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(ASYNC_CALLABLE_RESULT);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_async_CompletableFuture_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(ASYNC_FUTURE_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(ASYNC_FUTURE_RESULT);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_async_error_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(ASYNC_ERROR_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(503);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void verify_span_info_endpoint_traced_correctly(boolean upstreamSendsSpan, boolean upstreamSendsUserId) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders(upstreamSendsUserId)
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(SPAN_INFO_CALL_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        EndpointSpanInfoDto resultDto = response.as(EndpointSpanInfoDto.class);
        if (upstreamSendsSpan) {
            verifySpanInfoEqual(resultDto.parent_span_info, spanInfoDtoFromSpan(upstreamSpanInfo.getLeft()));
            verifyParentChildRelationship(resultDto);
        }
        else {
            verifySpanInfoEqual(resultDto.parent_span_info, emptySpanInfoDto());
        }
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void verify_nested_blocking_call_traced_correctly(boolean upstreamSendsSpan, boolean upstreamSendsUserId) {
        verifyNestedCallEndpoint(upstreamSendsSpan, upstreamSendsUserId, NESTED_BLOCKING_CALL_PATH);
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void verify_nested_async_call_traced_correctly(boolean upstreamSendsSpan, boolean upstreamSendsUserId) {
        verifyNestedCallEndpoint(upstreamSendsSpan, upstreamSendsUserId, NESTED_ASYNC_CALL_PATH);
    }

    private void verifyNestedCallEndpoint(boolean upstreamSendsSpan, boolean upstreamSendsUserId, String endpointPath) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders(upstreamSendsUserId)
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(endpointPath)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        // The nested-*-call endpoints sleep once for the outer endpoint, and again for the span-info endpoint sub-call.
        //      We expect 3 spans to have been completed: (1) the server span around the span-info endpoint,
        //      (2) the client subspan around the RestTemplate/AsyncRestTemplate call, and (3) the server span around
        //      the nested-*-call endpoint.
        verifyMultipleSpansCompletedAndReturnedInResponse(
            response, SLEEP_TIME_MILLIS * 2, 3, upstreamSpanInfo.getLeft()
        );
        EndpointSpanInfoDto resultDto = response.as(EndpointSpanInfoDto.class);
        if (upstreamSendsSpan) {
            // The span-info endpoint would have received span info generated by the nested-blocking-call endpoint,
            //      *not* what we sent in our original call. We can still verify trace ID and user ID though, and
            //      verify that the span-info endpoint had the correct parent/child relationship between spans.
            Span spanSent = upstreamSpanInfo.getLeft();
            assertThat(resultDto.parent_span_info.trace_id).isEqualTo(spanSent.getTraceId());
            assertThat(resultDto.parent_span_info.user_id).isEqualTo(spanSent.getUserId());
            verifyParentChildRelationship(resultDto);
        }
        else {
            verifyParentChildRelationship(resultDto);
        }
    }

    private SpanInfoDto spanInfoDtoFromSpan(Span span) {
        return new SpanInfoDto(
            span.getTraceId(), span.getSpanId(), span.getParentSpanId(), String.valueOf(span.isSampleable()),
            span.getUserId()
        );
    }

    private SpanInfoDto emptySpanInfoDto() {
        return new SpanInfoDto(null, null, null, null, null);
    }

    private void verifySpanInfoEqual(SpanInfoDto s1, SpanInfoDto s2) {
        assertThat(s1.trace_id).isEqualTo(s2.trace_id);
        assertThat(s1.span_id).isEqualTo(s2.span_id);
        assertThat(s1.parent_span_id).isEqualTo(s2.parent_span_id);
        assertThat(normalizeSampleableValue(s1.sampleable)).isEqualTo(normalizeSampleableValue(s2.sampleable));
        assertThat(s1.user_id).isEqualTo(s2.user_id);
    }

    private String normalizeSampleableValue(String sampleableString) {
        if (sampleableString == null)
            return null;

        if ("0".equals(sampleableString))
            return "false";
        else if ("1".equals(sampleableString))
            return "true";

        return sampleableString;
    }

    private void verifyParentChildRelationship(EndpointSpanInfoDto spanInfo) {
        assertThat(spanInfo.parent_span_info.trace_id).isEqualTo(spanInfo.endpoint_execution_span_info.trace_id);
        assertThat(spanInfo.endpoint_execution_span_info.parent_span_id).isEqualTo(spanInfo.parent_span_info.span_id);
    }

    private static final String USER_ID_HEADER_KEY = "userid";

    private Pair<Span, Map<String, String>> generateUpstreamSpanHeaders() {
        return generateUpstreamSpanHeaders(false);
    }

    private Pair<Span, Map<String, String>> generateUpstreamSpanHeaders(boolean includeUserId) {
        Span.Builder spanBuilder = Span.newBuilder("upstreamSpan", Span.SpanPurpose.CLIENT);
        if (includeUserId) {
            spanBuilder.withUserId("user-" + UUID.randomUUID().toString());
        }

        Span span = spanBuilder.build();

        MapBuilder<String, String> headersBuilder = MapBuilder
            .builder(TraceHeaders.TRACE_ID, span.getTraceId())
            .put(TraceHeaders.SPAN_ID, span.getSpanId())
            .put(TraceHeaders.SPAN_NAME, span.getSpanName())
            .put(TraceHeaders.TRACE_SAMPLED, String.valueOf(span.isSampleable()));

        if (span.getUserId() != null) {
            headersBuilder.put(USER_ID_HEADER_KEY, span.getUserId());
        }

        return Pair.of(span, headersBuilder.build());
    }

    private void verifySingleSpanCompletedAndReturnedInResponse(ExtractableResponse response,
                                                                long expectedMinSpanDurationMillis,
                                                                Span expectedUpstreamSpan) {
        // We can have a race condition where the response is sent and we try to verify here before the servlet filter
        //      has had a chance to complete the span. Wait a few milliseconds to give the servlet filter time to
        //      finish.
        waitUntilSpanRecorderHasExpectedNumSpans(1);

        assertThat(spanRecorder.completedSpans).hasSize(1);
        Span completedSpan = spanRecorder.completedSpans.get(0);
        String traceIdFromResponse = response.header(TraceHeaders.TRACE_ID);
        assertThat(traceIdFromResponse).isNotNull();
        assertThat(completedSpan.getTraceId()).isEqualTo(traceIdFromResponse);

        assertThat(completedSpan.getSpanName()).doesNotContain("?");

        assertThat(TimeUnit.NANOSECONDS.toMillis(completedSpan.getDurationNanos()))
            .isGreaterThanOrEqualTo(expectedMinSpanDurationMillis);

        if (expectedUpstreamSpan != null) {
            assertThat(completedSpan.getTraceId()).isEqualTo(expectedUpstreamSpan.getTraceId());
            assertThat(completedSpan.getParentSpanId()).isEqualTo(expectedUpstreamSpan.getSpanId());
        }
    }

    private void verifyMultipleSpansCompletedAndReturnedInResponse(ExtractableResponse response,
                                                                   long expectedMinSpanDurationMillis,
                                                                   int expectedNumSpansCompleted,
                                                                   Span expectedUpstreamSpan) {
        // We can have a race condition where the response is sent and we try to verify here before the servlet filter
        //      has had a chance to complete the span. Wait a few milliseconds to give the servlet filter time to
        //      finish.
        waitUntilSpanRecorderHasExpectedNumSpans(expectedNumSpansCompleted);

        assertThat(spanRecorder.completedSpans).hasSize(expectedNumSpansCompleted);
        String traceIdFromResponse = response.header(TraceHeaders.TRACE_ID);
        assertThat(traceIdFromResponse).isNotNull();

        spanRecorder.completedSpans.forEach(
            completedSpan -> {
                assertThat(completedSpan.getTraceId()).isEqualTo(traceIdFromResponse);
                assertThat(completedSpan.getSpanName()).doesNotContain("?");
            }
        );

        // Find the span with the longest duration - this is the outermost request span.
        Span outerRequestSpan = spanRecorder.completedSpans.stream()
                                                           .max(Comparator.comparing(Span::getDurationNanos))
                                                           .get();
        assertThat(TimeUnit.NANOSECONDS.toMillis(outerRequestSpan.getDurationNanos()))
            .isGreaterThanOrEqualTo(expectedMinSpanDurationMillis);

        if (expectedUpstreamSpan != null) {
            assertThat(outerRequestSpan.getTraceId()).isEqualTo(expectedUpstreamSpan.getTraceId());
            assertThat(outerRequestSpan.getParentSpanId()).isEqualTo(expectedUpstreamSpan.getSpanId());
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
    public static class SpanRecorder implements SpanLifecycleListener {

        public final List<Span> completedSpans = new ArrayList<>();

        @Override
        public void spanStarted(Span span) { }

        @Override
        public void spanSampled(Span span) { }

        @Override
        public void spanCompleted(Span span) {
            completedSpans.add(span);
        }
    }

}
