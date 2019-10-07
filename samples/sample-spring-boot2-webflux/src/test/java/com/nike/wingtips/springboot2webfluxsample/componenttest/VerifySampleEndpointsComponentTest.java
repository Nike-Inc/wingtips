package com.nike.wingtips.springboot2webfluxsample.componenttest;

import com.nike.internal.util.MapBuilder;
import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.springboot2webfluxsample.Main;
import com.nike.wingtips.springboot2webfluxsample.controller.SampleController.EndpointSpanInfoDto;
import com.nike.wingtips.springboot2webfluxsample.controller.SampleController.SpanInfoDto;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.tags.WingtipsTags;
import com.nike.wingtips.zipkin2.WingtipsToZipkinLifecycleListener;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.restassured.response.ExtractableResponse;

import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.ASYNC_ERROR_PATH;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.ASYNC_FUTURE_PATH;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.ASYNC_FUTURE_RESULT;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.ASYNC_TIMEOUT_PATH;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.FLUX_PATH;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.FLUX_RESULT;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.MONO_PATH;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.MONO_RESULT;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.NESTED_WEB_CLIENT_CALL_PATH;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.PATH_PARAM_ENDPOINT_PATH_PREFIX;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.PATH_PARAM_ENDPOINT_RESULT;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.ROUTER_FUNCTION_PATH;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.ROUTER_FUNCTION_RESULT;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.SIMPLE_PATH;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.SIMPLE_RESULT;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.SLEEP_TIME_MILLIS;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.SPAN_INFO_CALL_PATH;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.WILDCARD_PATH_PREFIX;
import static com.nike.wingtips.springboot2webfluxsample.controller.SampleController.WILDCARD_RESULT;
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
    private static ConfigurableApplicationContext serverAppContext;

    private SpanRecorder spanRecorder;

    @BeforeClass
    public static void beforeClass() {
        serverAppContext = SpringApplication.run(Main.class, "--server.port=" + SERVER_PORT);
    }

    @AfterClass
    public static void afterClass() {
        SpringApplication.exit(serverAppContext);
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
        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            if (!(listener instanceof WingtipsToZipkinLifecycleListener)) {
                Tracer.getInstance().removeSpanLifecycleListener(listener);
            }
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
                                                           : Pair.of((Span)null, Collections.emptyMap());

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
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, 0, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + SIMPLE_PATH,
            "GET",
            SIMPLE_PATH,
            "http://localhost:" + SERVER_PORT + SIMPLE_PATH + "?foo=bar",
            SIMPLE_PATH,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_mono_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(MONO_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(MONO_RESULT);
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + MONO_PATH,
            "GET",
            MONO_PATH,
            "http://localhost:" + SERVER_PORT + MONO_PATH + "?foo=bar",
            MONO_PATH,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_flux_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
                .when()
                .get(FLUX_PATH)
                .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(String.join("", FLUX_RESULT));
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + FLUX_PATH,
            "GET",
            FLUX_PATH,
            "http://localhost:" + SERVER_PORT + FLUX_PATH + "?foo=bar",
            FLUX_PATH,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_router_function_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
                .when()
                .get(ROUTER_FUNCTION_PATH)
                .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(ROUTER_FUNCTION_RESULT);
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + ROUTER_FUNCTION_PATH,
            "GET",
            ROUTER_FUNCTION_PATH,
            "http://localhost:" + SERVER_PORT + ROUTER_FUNCTION_PATH + "?foo=bar",
            ROUTER_FUNCTION_PATH,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_path_param_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.emptyMap());

        String fullPathWithPathParam = PATH_PARAM_ENDPOINT_PATH_PREFIX + "/" + UUID.randomUUID().toString();
        String expectedPathTemplate = PATH_PARAM_ENDPOINT_PATH_PREFIX + "/{somePathParam}";

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(fullPathWithPathParam)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(PATH_PARAM_ENDPOINT_RESULT);
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + expectedPathTemplate,
            "GET",
            fullPathWithPathParam,
            "http://localhost:" + SERVER_PORT + fullPathWithPathParam + "?foo=bar",
            expectedPathTemplate,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_wildcard_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.emptyMap());

        String fullPathWithPathParam = WILDCARD_PATH_PREFIX + "/" + UUID.randomUUID().toString();
        String expectedPathTemplate = WILDCARD_PATH_PREFIX + "/**";

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(fullPathWithPathParam)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(WILDCARD_RESULT);
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + expectedPathTemplate,
            "GET",
            fullPathWithPathParam,
            "http://localhost:" + SERVER_PORT + fullPathWithPathParam + "?foo=bar",
            expectedPathTemplate,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_async_CompletableFuture_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.emptyMap());

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
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + ASYNC_FUTURE_PATH,
            "GET",
            ASYNC_FUTURE_PATH,
            "http://localhost:" + SERVER_PORT + ASYNC_FUTURE_PATH + "?foo=bar",
            ASYNC_FUTURE_PATH,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_async_timeout_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(ASYNC_TIMEOUT_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(500);
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + ASYNC_TIMEOUT_PATH,
            "GET",
            ASYNC_TIMEOUT_PATH,
            "http://localhost:" + SERVER_PORT + ASYNC_TIMEOUT_PATH + "?foo=bar",
            ASYNC_TIMEOUT_PATH,
            response.statusCode(),
            "Did not observe any item or terminal signal within 100ms in 'source(MonoDelay)' (and no fallback has been configured)",
            "spring.webflux.server"
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_async_error_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.emptyMap());

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

        assertThat(response.statusCode()).isEqualTo(500);
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + ASYNC_ERROR_PATH,
            "GET",
            ASYNC_ERROR_PATH,
            "http://localhost:" + SERVER_PORT + ASYNC_ERROR_PATH + "?foo=bar",
            ASYNC_ERROR_PATH,
            response.statusCode(),
            "Intentional exception by " + ASYNC_ERROR_PATH + " endpoint",
            "spring.webflux.server"
        );
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
                                                           : Pair.of((Span)null, Collections.emptyMap());

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
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + SPAN_INFO_CALL_PATH,
            "GET",
            SPAN_INFO_CALL_PATH,
            "http://localhost:" + SERVER_PORT + SPAN_INFO_CALL_PATH + "?foo=bar",
            SPAN_INFO_CALL_PATH,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
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
    public void verify_nested_webclient_call_endpoint(boolean upstreamSendsSpan, boolean upstreamSendsUserId) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders(upstreamSendsUserId)
                                                           : Pair.of((Span)null, Collections.emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("foo", "bar")
                .log().all()
            .when()
                .get(NESTED_WEB_CLIENT_CALL_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        // The nested-webclient-call endpoint sleeps once for the outer endpoint, and again for the span-info endpoint
        //      sub-call.
        //      We expect 3 spans to have been completed: (1) the server span around the span-info endpoint,
        //      (2) the client subspan around the WebClient call, and (3) the server span around the
        //      nested-webclient-call endpoint.
        verifyMultipleSpansCompletedAndReturnedInResponse(
            response, SLEEP_TIME_MILLIS * 2, 3, upstreamSpanInfo.getLeft()
        );
        verifySpanTaggingForNestedCallEndpoint(NESTED_WEB_CLIENT_CALL_PATH);
        EndpointSpanInfoDto resultDto = response.as(EndpointSpanInfoDto.class);
        if (upstreamSendsSpan) {
            // The span-info endpoint would have received span info generated by the nested-webclient-call endpoint,
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

    @SuppressWarnings("SameParameterValue")
    private void verifySpanTaggingForNestedCallEndpoint(String initialEndpointPath) {
        // The initial span is always the one where the http.path tag matches the given initialEndpointPath.
        Span initialSpan = findCompletedSpanByCriteria(
            s -> initialEndpointPath.equals(s.getTags().get(KnownZipkinTags.HTTP_PATH))
        );
        verifySpanNameAndTags(
            initialSpan,
            "GET " + initialEndpointPath,
            "GET",
            initialEndpointPath,
            "http://localhost:" + SERVER_PORT + initialEndpointPath + "?foo=bar",
            initialEndpointPath,
            200,
            null,
            "spring.webflux.server"
        );

        // The next span is the nested client call span. It calls SPAN_INFO_CALL_PATH.
        String expectedWebClientSpanHandlerTagValue = "spring.webflux.client";
        Span nestedClientCallSpan = findCompletedSpanByCriteria(
            s -> expectedWebClientSpanHandlerTagValue.equals(s.getTags().get(WingtipsTags.SPAN_HANDLER))
        );
        verifySpanNameAndTags(
            nestedClientCallSpan,
            "GET",
            "GET",
            SPAN_INFO_CALL_PATH,
            "http://localhost:" + SERVER_PORT + SPAN_INFO_CALL_PATH + "?someQuery=foobar",
            null,
            200,
            null,
            expectedWebClientSpanHandlerTagValue
        );

        // The final span is the nested server call span for the SPAN_INFO_CALL_PATH endpoint.
        Span nestedServerCallSpan = findCompletedSpanByCriteria(
            s -> (SPAN_INFO_CALL_PATH.equals(s.getTags().get(KnownZipkinTags.HTTP_PATH))
                 && "spring.webflux.server".equals(s.getTags().get(WingtipsTags.SPAN_HANDLER)))
        );
        verifySpanNameAndTags(
            nestedServerCallSpan,
            "GET " + SPAN_INFO_CALL_PATH,
            "GET",
            SPAN_INFO_CALL_PATH,
            "http://localhost:" + SERVER_PORT + SPAN_INFO_CALL_PATH + "?someQuery=foobar",
            SPAN_INFO_CALL_PATH,
            200,
            null,
            "spring.webflux.server"
        );
    }

    private Span findCompletedSpanByCriteria(Predicate<Span> criteria) {
        List<Span> matchingSpans = spanRecorder.completedSpans.stream().filter(criteria).collect(Collectors.toList());
        assertThat(matchingSpans)
            .withFailMessage(
                "Expected to find exactly 1 span matching the specified criteria - instead found: "
                + matchingSpans.size()
            )
            .hasSize(1);

        return matchingSpans.get(0);
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

    private Span verifySingleSpanCompletedAndReturnedInResponse(ExtractableResponse response,
                                                                long expectedMinSpanDurationMillis,
                                                                Span expectedUpstreamSpan) {
        // We can have a race condition where the response is sent and we try to verify here before the WebFilter
        //      has had a chance to complete the span. Wait a few milliseconds to give the WebFilter time to
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

        return completedSpan;
    }

    @SuppressWarnings("SameParameterValue")
    private void verifyMultipleSpansCompletedAndReturnedInResponse(ExtractableResponse response,
                                                                   long expectedMinSpanDurationMillis,
                                                                   int expectedNumSpansCompleted,
                                                                   Span expectedUpstreamSpan) {
        // We can have a race condition where the response is sent and we try to verify here before the WebFilter
        //      has had a chance to complete the span. Wait a few milliseconds to give the WebFilter time to
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
        @SuppressWarnings("OptionalGetWithoutIsPresent")
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

    @SuppressWarnings("SameParameterValue")
    private void verifySpanNameAndTags(
        Span span,
        String expectedSpanName,
        String expectedHttpMethodTagValue,
        String expectedPathTagValue,
        String expectedUrlTagValue,
        String expectedHttpRouteTagValue,
        int expectedStatusCodeTagValue,
        String expectedErrorTagValue,
        String expectedSpanHandlerTagValue
    ) {
        assertThat(span.getSpanName()).isEqualTo(expectedSpanName);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_METHOD)).isEqualTo(expectedHttpMethodTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_PATH)).isEqualTo(expectedPathTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_URL)).isEqualTo(expectedUrlTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_ROUTE)).isEqualTo(expectedHttpRouteTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_STATUS_CODE))
            .isEqualTo(String.valueOf(expectedStatusCodeTagValue));
        assertThat(span.getTags().get(KnownZipkinTags.ERROR)).isEqualTo(expectedErrorTagValue);
        assertThat(span.getTags().get(WingtipsTags.SPAN_HANDLER)).isEqualTo(expectedSpanHandlerTagValue);
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

    public static class SpanRecorder implements SpanLifecycleListener {

        final List<Span> completedSpans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void spanStarted(Span span) { }

        @Override
        public void spanSampled(Span span) { }

        @Override
        public void spanCompleted(Span span) {
            // Create a copy so we know what the span looked like exactly when it was completed (in case other tags
            //      are added after completion, for example).
            completedSpans.add(Span.newBuilder(span).build());
        }
    }

}
