package com.nike.wingtips.springbootsample.componenttest;

import com.nike.internal.util.MapBuilder;
import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.springbootsample.Main;
import com.nike.wingtips.springbootsample.controller.SampleController.EndpointSpanInfoDto;
import com.nike.wingtips.springbootsample.controller.SampleController.SpanInfoDto;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.tags.WingtipsTags;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
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

import static com.nike.wingtips.springbootsample.controller.SampleController.ASYNC_CALLABLE_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.ASYNC_CALLABLE_RESULT;
import static com.nike.wingtips.springbootsample.controller.SampleController.ASYNC_DEFERRED_RESULT_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.ASYNC_DEFERRED_RESULT_PAYLOAD;
import static com.nike.wingtips.springbootsample.controller.SampleController.ASYNC_ERROR_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.ASYNC_FUTURE_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.ASYNC_FUTURE_RESULT;
import static com.nike.wingtips.springbootsample.controller.SampleController.ASYNC_TIMEOUT_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.BLOCKING_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.BLOCKING_RESULT;
import static com.nike.wingtips.springbootsample.controller.SampleController.NESTED_ASYNC_CALL_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.NESTED_BLOCKING_CALL_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.PATH_PARAM_ENDPOINT_PATH_PREFIX;
import static com.nike.wingtips.springbootsample.controller.SampleController.PATH_PARAM_ENDPOINT_RESULT;
import static com.nike.wingtips.springbootsample.controller.SampleController.SIMPLE_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.SIMPLE_RESULT;
import static com.nike.wingtips.springbootsample.controller.SampleController.SLEEP_TIME_MILLIS;
import static com.nike.wingtips.springbootsample.controller.SampleController.SPAN_INFO_CALL_PATH;
import static com.nike.wingtips.springbootsample.controller.SampleController.WILDCARD_PATH_PREFIX;
import static com.nike.wingtips.springbootsample.controller.SampleController.WILDCARD_RESULT;
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
        clearAllSpanLifecycleListeners();

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);
    }

    @After
    public void afterMethod() {
        clearAllSpanLifecycleListeners();
    }

    private void clearAllSpanLifecycleListeners() {
        Tracer.getInstance().removeAllSpanLifecycleListeners();
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
            "servlet"
        );
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
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + BLOCKING_PATH,
            "GET",
            BLOCKING_PATH,
            "http://localhost:" + SERVER_PORT + BLOCKING_PATH + "?foo=bar",
            BLOCKING_PATH,
            response.statusCode(),
            null,
            "servlet"
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
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

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
            "servlet"
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
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

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
            "servlet"
        );
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
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + ASYNC_DEFERRED_RESULT_PATH,
            "GET",
            ASYNC_DEFERRED_RESULT_PATH,
            "http://localhost:" + SERVER_PORT + ASYNC_DEFERRED_RESULT_PATH + "?foo=bar",
            ASYNC_DEFERRED_RESULT_PATH,
            response.statusCode(),
            null,
            "servlet"
        );
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
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        verifySpanNameAndTags(
            completedSpan,
            "GET " + ASYNC_CALLABLE_PATH,
            "GET",
            ASYNC_CALLABLE_PATH,
            "http://localhost:" + SERVER_PORT + ASYNC_CALLABLE_PATH + "?foo=bar",
            ASYNC_CALLABLE_PATH,
            response.statusCode(),
            null,
            "servlet"
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
            "servlet"
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
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

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

        assertThat(response.statusCode()).isEqualTo(503);
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
            String.valueOf(response.statusCode()),
            "servlet"
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

        assertThat(response.statusCode()).isEqualTo(500);
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        // Springboot sets the org.springframework.web.servlet.HandlerMapping.bestMatchingPattern request attribute
        //      (which is used to pull the HTTP route) to "/error" when async endpoints complete exceptionally
        //      ... because ... reasons? So unfortunately the span name and http.route tag aren't as useful as they
        //      could/should be, but it's the best we can do.
        verifySpanNameAndTags(
            completedSpan,
            "GET /error",
            "GET",
            ASYNC_ERROR_PATH,
            "http://localhost:" + SERVER_PORT + ASYNC_ERROR_PATH + "?foo=bar",
            "/error",
            response.statusCode(),
            String.valueOf(response.statusCode()),
            "servlet"
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
            "servlet"
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
        verifySpanTaggingForNestedCallEndpoint(endpointPath);
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
            "servlet"
        );

        // The next span is the nested client call span. It calls SPAN_INFO_CALL_PATH.
        //      It might use the regular RestTemplate or AsyncRestTemplate, depending on which initial endpoint was hit.
        String expectedRestTemplateSpanHandlerTagValue =
            (initialEndpointPath.contains("async"))
            ? "spring.asyncresttemplate"
            : "spring.resttemplate";
        Span nestedClientCallSpan = findCompletedSpanByCriteria(
            s -> expectedRestTemplateSpanHandlerTagValue.equals(s.getTags().get(WingtipsTags.SPAN_HANDLER))
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
            expectedRestTemplateSpanHandlerTagValue
        );

        // The final span is the nested server call span for the SPAN_INFO_CALL_PATH endpoint.
        Span nestedServerCallSpan = findCompletedSpanByCriteria(
            s -> (SPAN_INFO_CALL_PATH.equals(s.getTags().get(KnownZipkinTags.HTTP_PATH))
                 && "servlet".equals(s.getTags().get(WingtipsTags.SPAN_HANDLER)))
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
            "servlet"
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

        return completedSpan;
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

    @SuppressWarnings("WeakerAccess")
    public static class SpanRecorder implements SpanLifecycleListener {

        public final List<Span> completedSpans = Collections.synchronizedList(new ArrayList<>());

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
