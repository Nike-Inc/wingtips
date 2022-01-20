package com.nike.wingtips.componenttest;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.spring.webflux.client.SpringWebfluxClientRequestTagAdapter;
import com.nike.wingtips.spring.webflux.client.SpringWebfluxClientRequestZipkinTagStrategy;
import com.nike.wingtips.spring.webflux.client.WingtipsSpringWebfluxExchangeFilterFunction;
import com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.tags.WingtipsTags;
import com.nike.wingtips.testutils.TestUtils.SpanRecorder;
import com.nike.wingtips.util.TracingState;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.restassured.response.ExtractableResponse;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.BASIC_ENDPOINT_PATH;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.BASIC_ENDPOINT_PAYLOAD;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.FLUX_ENDPOINT_PATH;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.FLUX_ENDPOINT_PAYLOAD;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.MONO_ENDPOINT_PATH;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.MONO_ENDPOINT_PAYLOAD;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.PATH_PARAM_ENDPOINT_PATH_TMPLT;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.PATH_PARAM_ENDPOINT_PAYLOAD_SUFFIX;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.ROUTER_FUNCTION_ENDPOINT_PATH;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestController.ROUTER_FUNCTION_ENDPOINT_RESPONSE_PAYLOAD;
import static com.nike.wingtips.componenttest.WingtipsSpringWebfluxComponentTest.ComponentTestWebFluxApp.USER_ID_HEADER_KEY;
import static com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils.tracingStateFromContext;
import static com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils.tracingStateFromExchange;
import static com.nike.wingtips.testutils.TestUtils.resetTracing;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

/**
 * Component test validating Wingtips' integration with Spring WebFlux. This launches a real running server on a
 * random port and sets up {@link WingtipsSpringWebfluxWebFilter} to handle server-side requests, along with a
 * Wingtips-instrumented Spring {@link WebClient} utilizing {@link WingtipsSpringWebfluxExchangeFilterFunction} to
 * handle client-side requests. Requests are fired against the server and client to verify the integrations.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsSpringWebfluxComponentTest {

    private static final int SERVER_PORT = findFreePort();
    private static ConfigurableApplicationContext serverAppContext;

    private SpanRecorder spanRecorder;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static @Nullable Optional<TracingState> monoEndpointTracingStateFromServerWebExchange;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static @Nullable Optional<TracingState> monoEndpointTracingStateFromMonoContext;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static @Nullable Optional<Span> monoEndpointCurrentSpanOnEndpointExecute;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static @Nullable Optional<TracingState> subWebFilterTracingStateFromServerWebExchange;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static @Nullable Optional<TracingState> subWebFilterTracingStateFromMonoContext;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static @Nullable Optional<Span> subWebFilterCurrentSpanOnFilterExecute;

    @BeforeClass
    public static void beforeClass() {
        serverAppContext = SpringApplication.run(ComponentTestWebFluxApp.class, "--server.port=" + SERVER_PORT);
    }

    @AfterClass
    public static void afterClass() {
        if (serverAppContext != null) {
            SpringApplication.exit(serverAppContext);
        }
    }

    @Before
    public void beforeMethod() {
        resetTracing();

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);

        //noinspection OptionalAssignedToNull
        monoEndpointTracingStateFromServerWebExchange = null;
        //noinspection OptionalAssignedToNull
        monoEndpointTracingStateFromMonoContext = null;
        //noinspection OptionalAssignedToNull
        monoEndpointCurrentSpanOnEndpointExecute = null;

        //noinspection OptionalAssignedToNull
        subWebFilterTracingStateFromServerWebExchange = null;
        //noinspection OptionalAssignedToNull
        subWebFilterTracingStateFromMonoContext = null;
        //noinspection OptionalAssignedToNull
        subWebFilterCurrentSpanOnFilterExecute = null;
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private static int findFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== VERIFY THE WebFilter (SERVERSIDE FILTER) - WingtipsSpringWebfluxWebFilter =======================

    @DataProvider(value = {
        "true   |   /basicEndpoint",
        "false  |   /basicEndpoint",
        "true   |   /monoEndpoint",
        "false  |   /monoEndpoint",
        "true   |   /fluxEndpoint",
        "false  |   /fluxEndpoint",
        "true   |   /routerFunctionEndpoint",
        "false  |   /routerFunctionEndpoint",
    }, splitBy = "\\|")
    @Test
    public void verify_single_endpoint_traced_correctly(
        boolean upstreamSendsSpan, String endpointPath
    ) {
        Pair<Span, Map<String, String>> upstreamSpanInfo =
            (upstreamSendsSpan)
            ? generateUpstreamSpanHeaders()
            : Pair.of((Span) null, Collections.emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("someQueryParam", "someValue")
                .queryParam("otherQueryParam", "otherValue")
                .log().all()
                .when()
                .get(endpointPath)
                .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(getExpectedPayloadForEndpoint(endpointPath));
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        String expectedQueryString = "?someQueryParam=someValue&otherQueryParam=otherValue";
        verifySpanNameAndTags(
            completedSpan,
            "GET " + endpointPath,
            upstreamSpanInfo.getRight().get(USER_ID_HEADER_KEY),
            "GET",
            endpointPath,
            "http://localhost:" + SERVER_PORT + endpointPath + expectedQueryString,
            endpointPath,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
    }

    // Verify that span name and http-route-tag come from low-cardinality path template, *not* the high-cardinality
    //      full path.
    @DataProvider(value = {
        "true",
        "false",
    })
    @Test
    public void verify_path_param_endpoint_traced_correctly(
        boolean upstreamSendsSpan
    ) {
        Pair<Span, Map<String, String>> upstreamSpanInfo =
            (upstreamSendsSpan)
            ? generateUpstreamSpanHeaders()
            : Pair.of((Span) null, Collections.emptyMap());

        String pathParamValue = UUID.randomUUID().toString();
        String expectedFinalPath = PATH_PARAM_ENDPOINT_PATH_TMPLT.replace("{foo}", pathParamValue);

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("someQueryParam", "someValue")
                .queryParam("otherQueryParam", "otherValue")
                .log().all()
                .when()
                .get(expectedFinalPath)
                .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(pathParamValue + PATH_PARAM_ENDPOINT_PAYLOAD_SUFFIX);
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        String expectedQueryString = "?someQueryParam=someValue&otherQueryParam=otherValue";
        verifySpanNameAndTags(
            completedSpan,
            "GET " + PATH_PARAM_ENDPOINT_PATH_TMPLT,
            upstreamSpanInfo.getRight().get(USER_ID_HEADER_KEY),
            "GET",
            expectedFinalPath,
            "http://localhost:" + SERVER_PORT + expectedFinalPath + expectedQueryString,
            PATH_PARAM_ENDPOINT_PATH_TMPLT,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );
    }

    // Verify that an error produced by the framework is traced correctly (i.e. an error that occurs
    //      after the WebFilter but before the controller method is executed).
    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false",
    }, splitBy = "\\|")
    @Test
    public void verify_framework_error_traced_correctly(
        boolean upstreamSendsSpan, boolean triggerError
    ) {
        Pair<Span, Map<String, String>> upstreamSpanInfo =
            (upstreamSendsSpan)
            ? generateUpstreamSpanHeaders()
            : Pair.of((Span) null, Collections.emptyMap());

        Map<String, String> queryParams = (triggerError)
                                          ? Collections.emptyMap()
                                          : Collections.singletonMap("requiredQueryParamValue", "42");

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParams(queryParams)
                .log().all()
                .when()
                .get(INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH)
                .then()
                .log().all()
                .extract();

        if (triggerError) {
            // Framework error scenario.
            assertThat(response.statusCode()).isEqualTo(400);
            Span completedSpan =
                verifySingleSpanCompletedAndReturnedInResponse(response, 0, upstreamSpanInfo.getLeft());
            verifySpanNameAndTags(
                completedSpan,
                "GET " + INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH,
                upstreamSpanInfo.getRight().get(USER_ID_HEADER_KEY),
                "GET",
                INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH,
                "http://localhost:" + SERVER_PORT + INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH,
                INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH,
                response.statusCode(),
                "400 BAD_REQUEST \"Required int parameter 'requiredQueryParamValue' is not present\"",
                "spring.webflux.server"
            );
        }
        else {
            // Normal non-error scenario for this endpoint.
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("You passed in 42 for the required query param value");
            Span completedSpan =
                verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
            String expectedQueryString = "?requiredQueryParamValue=42";
            verifySpanNameAndTags(
                completedSpan,
                "GET " + INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH,
                upstreamSpanInfo.getRight().get(USER_ID_HEADER_KEY),
                "GET",
                INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH,
                "http://localhost:" + SERVER_PORT + INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH + expectedQueryString,
                INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH,
                response.statusCode(),
                null,
                "spring.webflux.server"
            );
        }
    }

    private enum EndpointAndFilterErrorScenario {
        BASIC_ENDPOINT_THROWS_EX(
            BASIC_ENDPOINT_PATH, "throw-exception",
            "Error occurred in basicEndpoint()", true
        ),
        MONO_ENDPOINT_THROWS_EX(
            MONO_ENDPOINT_PATH, "throw-exception",
            "Error thrown in monoEndpoint(), outside Mono", true
        ),
        MONO_ENDPOINT_RETURNS_EX_IN_MONO(
            MONO_ENDPOINT_PATH, "return-exception-in-mono",
            "Error thrown in monoEndpoint(), inside Mono", true
        ),
        FLUX_ENDPOINT_THROWS_EX(
            FLUX_ENDPOINT_PATH, "throw-exception",
            "Error thrown in fluxEndpoint(), outside Flux", true
        ),
        FLUX_ENDPOINT_RETURNS_EX_IN_MONO(
            FLUX_ENDPOINT_PATH, "return-exception-in-flux",
            "Error thrown in fluxEndpoint(), inside Flux", true
        ),
        ROUTER_RUNCTION_ENDPOINT_THROWS_EX(
            ROUTER_FUNCTION_ENDPOINT_PATH, "throw-exception",
            "Error thrown in routerFunctionEndpoint(), outside Mono", true
        ),
        ROUTER_FUNCTION_ENDPOINT_RETURNS_EX_IN_MONO(
            ROUTER_FUNCTION_ENDPOINT_PATH, "return-exception-in-mono",
            "Error thrown in routerFunctionEndpoint(), inside Mono", true
        ),
        WEB_FILTER_THROWS_EX(
            MONO_ENDPOINT_PATH, "throw-web-filter-exception",
            "Exception thrown from WebFilter", false
        ),
        WEB_FILTER_RETURNS_EX_IN_MONO(
            MONO_ENDPOINT_PATH, "return-exception-in-web-filter-mono",
            "Exception returned from WebFilter Mono", false
        ),
        HANDLER_FILTER_FUNCTION_THROWS_EX(
            ROUTER_FUNCTION_ENDPOINT_PATH, "throw-handler-filter-function-exception",
            "Exception thrown from HandlerFilterFunction", true
        ),
        HANDLER_FILTER_FUNCTION_RETURNS_EX_IN_MONO(
            ROUTER_FUNCTION_ENDPOINT_PATH, "return-exception-in-handler-filter-function-mono",
            "Exception returned from HandlerFilterFunction Mono", true
        );

        public final String endpointPath;
        public final String specialHeader;
        public final String expectedErrorTag;
        public final boolean expectEndpointPathInSpanName;

        EndpointAndFilterErrorScenario(
            String endpointPath, String specialHeader, String expectedErrorTag, boolean expectEndpointPathInSpanName
        ) {
            this.endpointPath = endpointPath;
            this.specialHeader = specialHeader;
            this.expectedErrorTag = expectedErrorTag;
            this.expectEndpointPathInSpanName = expectEndpointPathInSpanName;
        }
    }

    @DataProvider
    public static List<List<Object>> endpointAndFilterErrorScenarioDataProvider() {
        List<List<Object>> result = new ArrayList<>();
        for (EndpointAndFilterErrorScenario ees : EndpointAndFilterErrorScenario.values()) {
            result.add(Arrays.asList(true, ees));
            result.add(Arrays.asList(false, ees));
        }
        return result;
    }

    @UseDataProvider("endpointAndFilterErrorScenarioDataProvider")
    @Test
    public void verify_endpoint_and_filter_error_traced_correctly(
        boolean upstreamSendsSpan, EndpointAndFilterErrorScenario scenario
    ) {
        Pair<Span, Map<String, String>> upstreamSpanInfo =
            (upstreamSendsSpan)
            ? generateUpstreamSpanHeaders()
            : Pair.of((Span) null, Collections.emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("someQueryParam", "someValue")
                .queryParam("otherQueryParam", "otherValue")
                .log().all()
                .when()
                .header(scenario.specialHeader, "true")
                .get(scenario.endpointPath)
                .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(500);
        Span completedSpan = verifySingleSpanCompletedAndReturnedInResponse(
            response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft()
        );
        String expectedQueryString = "?someQueryParam=someValue&otherQueryParam=otherValue";
        String expectedSpanName = (scenario.expectEndpointPathInSpanName) ? "GET " + scenario.endpointPath : "GET";
        String expectedHttpRoute = (scenario.expectEndpointPathInSpanName) ? scenario.endpointPath : null;
        verifySpanNameAndTags(
            completedSpan,
            expectedSpanName,
            upstreamSpanInfo.getRight().get(USER_ID_HEADER_KEY),
            "GET",
            scenario.endpointPath,
            "http://localhost:" + SERVER_PORT + scenario.endpointPath + expectedQueryString,
            expectedHttpRoute,
            response.statusCode(),
            scenario.expectedErrorTag,
            "spring.webflux.server"
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void verify_tracing_state_set_correctly_on_ServerWebExchange_and_webflux_Context_for_endpoint_and_WebFilter(
        boolean upstreamSendsSpan
    ) {
        Pair<Span, Map<String, String>> upstreamSpanInfo =
            (upstreamSendsSpan)
            ? generateUpstreamSpanHeaders()
            : Pair.of((Span) null, Collections.emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .headers(upstreamSpanInfo.getRight())
                .queryParam("someQueryParam", "someValue")
                .queryParam("otherQueryParam", "otherValue")
                .log().all()
                .when()
                .get(MONO_ENDPOINT_PATH)
                .then()
                .log().all()
                .extract();

        // Sanity check standard tracing stuff.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(MONO_ENDPOINT_PAYLOAD);
        Span completedSpan =
            verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
        String expectedQueryString = "?someQueryParam=someValue&otherQueryParam=otherValue";
        verifySpanNameAndTags(
            completedSpan,
            "GET " + MONO_ENDPOINT_PATH,
            upstreamSpanInfo.getRight().get(USER_ID_HEADER_KEY),
            "GET",
            MONO_ENDPOINT_PATH,
            "http://localhost:" + SERVER_PORT + MONO_ENDPOINT_PATH + expectedQueryString,
            MONO_ENDPOINT_PATH,
            response.statusCode(),
            null,
            "spring.webflux.server"
        );

        // Verify the things we're actually interested in for this test: the current span on the thread when the
        //      endpoint/filter is executed, and the TracingState stored in the ServerWebExchange and Webflux Context.
        assertThat(monoEndpointCurrentSpanOnEndpointExecute).isNotNull();
        assertThat(monoEndpointCurrentSpanOnEndpointExecute.get()).isEqualTo(completedSpan);

        assertThat(subWebFilterCurrentSpanOnFilterExecute).isNotNull();
        assertThat(subWebFilterCurrentSpanOnFilterExecute.get()).isEqualTo(completedSpan);

        assertThat(monoEndpointTracingStateFromServerWebExchange).isNotNull();
        assertThat(monoEndpointTracingStateFromServerWebExchange.get().getLeft())
            .hasSize(1)
            .containsExactly(completedSpan);
        TracingState expectedTracingState = monoEndpointTracingStateFromServerWebExchange.get();

        assertThat(monoEndpointTracingStateFromMonoContext).isEqualTo(Optional.of(expectedTracingState));
        assertThat(subWebFilterTracingStateFromServerWebExchange).isEqualTo(Optional.of(expectedTracingState));
        assertThat(subWebFilterTracingStateFromMonoContext).isEqualTo(Optional.of(expectedTracingState));
    }


    // ========== VERIFY THE WEBCLIENT ExchangeFilterFunction (CLIENTSIDE FILTER) - WingtipsSpringWebfluxExchangeFilterFunction ============

    private enum BaseTracingStateScenario {
        NO_BASE_TRACING_STATE(
            () -> null,
            tc -> attrs -> {}
        ),
        BASE_TRACING_STATE_ON_CURRENT_THREAD(
            () -> {
                Tracer.getInstance().startRequestWithRootSpan("somePreexistingParentSpan");
                return TracingState.getCurrentThreadTracingState();
            },
            tc -> attrs -> {}
        ),
        BASE_TRACING_STATE_IN_REQUEST_ATTRS(
            () -> {
                Tracer.getInstance().startRequestWithRootSpan("somePreexistingParentSpan");
                TracingState tc = TracingState.getCurrentThreadTracingState();
                Tracer.getInstance().unregisterFromThread();
                return tc;
            },
            tc -> attrs -> { attrs.put(TracingState.class.getName(), tc); }
        ),
        DIFFERENT_BASE_TRACING_STATES_ON_CURRENT_THREAD_VS_IN_REQUEST_ATTRS(
            () -> {
                // Setup the custom TracingState for the request attrs.
                Tracer.getInstance().startRequestWithRootSpan("somePreexistingParentSpan");
                TracingState requestAttrTc = TracingState.getCurrentThreadTracingState();

                // Setup a *different* TracingState for the current thread.
                Tracer.getInstance().unregisterFromThread();
                Tracer.getInstance().startRequestWithRootSpan("shouldNotBeUsed");
                assertThat(Tracer.getInstance().getCurrentSpan().getTraceId())
                    .isNotEqualTo(requestAttrTc.spanStack.getFirst().getTraceId());

                // Return the TracingState for the request attrs, as it should take precedence.
                return requestAttrTc;
            },
            tc -> attrs -> { attrs.put(TracingState.class.getName(), tc); }
        );

        private final Supplier<TracingState> baseTracingStateSetupSupplier;
        private final Function<TracingState, Consumer<Map<String, Object>>> webClientAttributesConsumer;

        BaseTracingStateScenario(
            Supplier<TracingState> baseTracingStateSetupSupplier,
            Function<TracingState, Consumer<Map<String, Object>>> webClientAttributesConsumer
        ) {
            this.baseTracingStateSetupSupplier = baseTracingStateSetupSupplier;
            this.webClientAttributesConsumer = webClientAttributesConsumer;
        }

        public TracingState setupBaseTracingState() {
            return baseTracingStateSetupSupplier.get();
        }

        public Consumer<Map<String, Object>> tracingStateAttrSetup(TracingState tc) {
            return webClientAttributesConsumer.apply(tc);
        }
    }

    @DataProvider
    public static List<List<Object>> baseTracingStateWithSubspanOptionScenarioDataProvider() {
        List<List<Object>> result = new ArrayList<>();
        for (BaseTracingStateScenario scenario : BaseTracingStateScenario.values()) {
            result.add(Arrays.asList(scenario, true));
            result.add(Arrays.asList(scenario, false));
        }
        return result;
    }

    @UseDataProvider("baseTracingStateWithSubspanOptionScenarioDataProvider")
    @Test
    public void verify_webflux_WebClient_with_WingtipsSpringWebfluxExchangeFilterFunction_traced_correctly(
        BaseTracingStateScenario baseTracingStateScenario, boolean subspanOptionOn
    ) {
        // given
        TracingState baseTracingState = baseTracingStateScenario.setupBaseTracingState();
        Span parent = (baseTracingState == null) ? null : baseTracingState.spanStack.getFirst();

        String pathTemplateForTags = "/some/path/template/" + UUID.randomUUID().toString();

        WebClient webClientWithWingtips = WebClient
            .builder()
            .filter(
                new WingtipsSpringWebfluxExchangeFilterFunction(
                    subspanOptionOn,
                    SpringWebfluxClientRequestZipkinTagStrategy.getDefaultInstance(),
                    new SpringHttpClientTagAdapterWithHttpRouteKnowledge(pathTemplateForTags)
                )
            )
            .build();

        // We always expect at least one span to be completed as part of the call: the server span.
        //      We may or may not have a second span completed depending on the value of subspanOptionOn.
        int expectedNumSpansCompleted = (subspanOptionOn) ? 2 : 1;

        String fullRequestUrl = "http://localhost:" + SERVER_PORT + BASIC_ENDPOINT_PATH + "?foo=bar";

        // when
        ClientResponse response = webClientWithWingtips
            .get()
            .uri(fullRequestUrl)
            .attributes(baseTracingStateScenario.tracingStateAttrSetup(baseTracingState))
            .exchange()
            .block();

        // then
        assertThat(response.statusCode().value()).isEqualTo(200);
        assertThat(response.bodyToMono(String.class).block()).isEqualTo(BASIC_ENDPOINT_PAYLOAD);
        verifySpansCompletedAndReturnedInResponse(
            response, SLEEP_TIME_MILLIS, expectedNumSpansCompleted, parent, subspanOptionOn
        );

        if (subspanOptionOn) {
            Span clientSpan = findWebfluxWebClientSpanFromCompletedSpans();
            verifySpanNameAndTags(
                clientSpan,
                "GET " + pathTemplateForTags,
                null,
                "GET",
                BASIC_ENDPOINT_PATH,
                fullRequestUrl,
                pathTemplateForTags,
                response.statusCode().value(),
                null,
                "spring.webflux.client"
            );
            assertThat(clientSpan.getTags().get("webflux_log_id")).isNotEmpty();
        }

        if (parent != null) {
            parent.close();
        }
    }

    // Verify that an error that occurs in the ExchangeFilterFunction's returned Mono<ClientResponse> doesn't
    //      prevent tracing from working.
    @UseDataProvider("baseTracingStateWithSubspanOptionScenarioDataProvider")
    @Test
    public void verify_webflux_WebClient_call_with_error_in_Mono_of_ClientResponse_traced_correctly(
            BaseTracingStateScenario baseTracingStateScenario, boolean subspanOptionOn
    ) {
        // given
        TracingState baseTracingState = baseTracingStateScenario.setupBaseTracingState();
        Span parent = (baseTracingState == null) ? null : baseTracingState.spanStack.getFirst();

        String pathTemplateForTags = "/some/path/template/" + UUID.randomUUID().toString();

        WebClient webClientWithWingtips = WebClient
            .builder()
            .filter(
                new WingtipsSpringWebfluxExchangeFilterFunction(
                    subspanOptionOn,
                    SpringWebfluxClientRequestZipkinTagStrategy.getDefaultInstance(),
                    new SpringHttpClientTagAdapterWithHttpRouteKnowledge(pathTemplateForTags)
                )
            )
            .build();

        // The call will never make it to the server, so we only expect one or zero spans completed,
        //      depending on the value of subspanOptionOn.
        int expectedNumSpansCompleted = (subspanOptionOn) ? 1 : 0;

        String fullRequestUrl = "http://localhost:1234567890" + BASIC_ENDPOINT_PATH + "?foo=bar";

        // when
        Throwable ex = catchThrowable(
            () -> webClientWithWingtips
                .get()
                .uri(fullRequestUrl)
                .attributes(baseTracingStateScenario.tracingStateAttrSetup(baseTracingState))
                .exchangeToMono(Mono::just)
                .block()
        );

        // then
        Throwable unwrappedEx = Exceptions.unwrap(ex);
        assertThat(unwrappedEx)
            .isInstanceOfAny(WebClientRequestException.class)
            .hasCauseInstanceOf(UnknownHostException.class);
        waitUntilSpanRecorderHasExpectedNumSpans(expectedNumSpansCompleted);
        assertThat(spanRecorder.completedSpans).hasSize(expectedNumSpansCompleted);
        if (expectedNumSpansCompleted > 0) {
            Span errorSpan = spanRecorder.completedSpans.get(0);

            if (parent == null) {
                assertThat(errorSpan.getParentSpanId()).isNull();
            }
            else {
                assertThat(errorSpan.getTraceId()).isEqualTo(parent.getTraceId());
                assertThat(errorSpan.getParentSpanId()).isEqualTo(parent.getSpanId());
            }

            verifySpanNameAndTags(
                errorSpan,
                "GET " + pathTemplateForTags,
                null,
                "GET",
                BASIC_ENDPOINT_PATH,
                fullRequestUrl,
                pathTemplateForTags,
                null,
                unwrappedEx.getMessage(),
                "spring.webflux.client"
            );
            assertThat(errorSpan.getTags().get("webflux_log_id")).isNotEmpty();
        }

        if (parent != null) {
            parent.close();
        }
    }

    // Verify that an error thrown from the ExchangeFilterFunction.filter(...) chain (NOT the returned
    //      Mono<ClientResponse>) doesn't prevent tracing from working.
    @UseDataProvider("baseTracingStateWithSubspanOptionScenarioDataProvider")
    @Test
    public void verify_webflux_WebClient_call_with_error_thrown_in_sub_filter_traced_correctly(
        BaseTracingStateScenario baseTracingStateScenario, boolean subspanOptionOn
    ) {
        // given
        TracingState baseTracingState = baseTracingStateScenario.setupBaseTracingState();
        Span parent = (baseTracingState == null) ? null : baseTracingState.spanStack.getFirst();

        String pathTemplateForTags = "/some/path/template/" + UUID.randomUUID().toString();

        RuntimeException subFilterEx = new RuntimeException(
            "Intentional test exception in secondary WebClient filter."
        );
        WebClient webClientWithWingtips = WebClient
            .builder()
            .filter(
                new WingtipsSpringWebfluxExchangeFilterFunction(
                    subspanOptionOn,
                    SpringWebfluxClientRequestZipkinTagStrategy.getDefaultInstance(),
                    new SpringHttpClientTagAdapterWithHttpRouteKnowledge(pathTemplateForTags)
                )
            )
            .filter((request, next) -> {
                throw subFilterEx;
            })
            .build();

        // The call will never make it to the server, so we only expect one or zero spans completed,
        //      depending on the value of subspanOptionOn.
        int expectedNumSpansCompleted = (subspanOptionOn) ? 1 : 0;

        String fullRequestUrl = "http://localhost:1234567890" + BASIC_ENDPOINT_PATH + "?foo=bar";

        // when
        Throwable ex = catchThrowable(
            () -> webClientWithWingtips
                .get()
                .uri(fullRequestUrl)
                .attributes(baseTracingStateScenario.tracingStateAttrSetup(baseTracingState))
                .exchange()
                .block()
        );

        // then
        Throwable unwrappedEx = Exceptions.unwrap(ex);
        assertThat(unwrappedEx).isSameAs(subFilterEx);
        waitUntilSpanRecorderHasExpectedNumSpans(expectedNumSpansCompleted);
        assertThat(spanRecorder.completedSpans).hasSize(expectedNumSpansCompleted);
        if (expectedNumSpansCompleted > 0) {
            Span errorSpan = spanRecorder.completedSpans.get(0);

            if (parent == null) {
                assertThat(errorSpan.getParentSpanId()).isNull();
            }
            else {
                assertThat(errorSpan.getTraceId()).isEqualTo(parent.getTraceId());
                assertThat(errorSpan.getParentSpanId()).isEqualTo(parent.getSpanId());
            }

            verifySpanNameAndTags(
                errorSpan,
                "GET " + pathTemplateForTags,
                null,
                "GET",
                BASIC_ENDPOINT_PATH,
                fullRequestUrl,
                pathTemplateForTags,
                null,
                unwrappedEx.getMessage(),
                "spring.webflux.client"
            );
            assertThat(errorSpan.getTags().get("webflux_log_id")).isNotEmpty();
        }

        if (parent != null) {
            parent.close();
        }
    }

    @UseDataProvider("baseTracingStateWithSubspanOptionScenarioDataProvider")
    @Test
    public void verify_tracing_state_set_correctly_on_ClientRequest_and_webflux_Context_for_sub_ExchangeFilterFunction(
        BaseTracingStateScenario baseTracingStateScenario, boolean subspanOptionOn
    ) {
        // given
        TracingState baseTracingState = baseTracingStateScenario.setupBaseTracingState();
        Span parent = (baseTracingState == null) ? null : baseTracingState.spanStack.getFirst();

        String pathTemplateForTags = "/some/path/template/" + UUID.randomUUID().toString();

        AtomicReference<Span> currentSpanInSubFilter = new AtomicReference<>(null);
        AtomicReference<TracingState> tracingStateFromRequestAttrs = new AtomicReference<>(null);
        AtomicReference<TracingState> tracingStateFromWebfluxContext = new AtomicReference<>(null);

        WebClient webClientWithWingtips = WebClient
            .builder()
            .filter(
                new WingtipsSpringWebfluxExchangeFilterFunction(
                    subspanOptionOn,
                    SpringWebfluxClientRequestZipkinTagStrategy.getDefaultInstance(),
                    new SpringHttpClientTagAdapterWithHttpRouteKnowledge(pathTemplateForTags)
                )
            )
            .filter((request, next) -> {
                currentSpanInSubFilter.set(Tracer.getInstance().getCurrentSpan());
                tracingStateFromRequestAttrs.set(
                    (TracingState)request.attribute(TracingState.class.getName()).orElse(null)
                );
                return next.exchange(request).subscriberContext(c -> {
                    tracingStateFromWebfluxContext.set(tracingStateFromContext(c));
                    return c;
                });
            })
            .build();

        // We always expect at least one span to be completed as part of the call: the server span.
        //      We may or may not have a second span completed depending on the value of subspanOptionOn.
        int expectedNumSpansCompleted = (subspanOptionOn) ? 2 : 1;

        String fullRequestUrl = "http://localhost:" + SERVER_PORT + BASIC_ENDPOINT_PATH + "?foo=bar";

        // when
        ClientResponse response = webClientWithWingtips
            .get()
            .uri(fullRequestUrl)
            .attributes(baseTracingStateScenario.tracingStateAttrSetup(baseTracingState))
            .exchange()
            .block();

        // then
        // Sanity check standard tracing stuff.
        assertThat(response.statusCode().value()).isEqualTo(200);
        assertThat(response.bodyToMono(String.class).block()).isEqualTo(BASIC_ENDPOINT_PAYLOAD);
        verifySpansCompletedAndReturnedInResponse(
            response, SLEEP_TIME_MILLIS, expectedNumSpansCompleted, parent, subspanOptionOn
        );

        if (subspanOptionOn) {
            Span clientSpan = findWebfluxWebClientSpanFromCompletedSpans();
            verifySpanNameAndTags(
                clientSpan,
                "GET " + pathTemplateForTags,
                null,
                "GET",
                BASIC_ENDPOINT_PATH,
                fullRequestUrl,
                pathTemplateForTags,
                response.statusCode().value(),
                null,
                "spring.webflux.client"
            );
            assertThat(clientSpan.getTags().get("webflux_log_id")).isNotEmpty();
        }

        if (parent != null) {
            parent.close();
        }

        // Verify the things we're actually interested in for this test: the current span on the thread when the
        //      sub-ExchangeFilterFunction is executed, and the TracingState stored in the ClientRequest attrs and
        //      Webflux Context.
        if (subspanOptionOn) {
            Span clientSpan = findWebfluxWebClientSpanFromCompletedSpans();

            assertThat(currentSpanInSubFilter.get()).isNotNull();
            assertThat(currentSpanInSubFilter.get()).isEqualTo(clientSpan);

            assertThat(tracingStateFromRequestAttrs.get()).isNotNull();
            // At the time the WebClient filter sees things, there might be 1 or 2 spans in the TracingState,
            //      depending on whether the parent exists.
            if (parent == null) {
                assertThat(tracingStateFromRequestAttrs.get().getLeft())
                    .hasSize(1)
                    .containsExactly(clientSpan);
            }
            else {
                assertThat(tracingStateFromRequestAttrs.get().getLeft())
                    .hasSize(2)
                    .containsExactly(clientSpan, parent);
            }

            assertThat(tracingStateFromWebfluxContext.get()).isEqualTo(tracingStateFromRequestAttrs.get());
        }
        else {
            if (parent == null) {
                TracingState emptyTracingState = new TracingState(null, null);
                assertThat(currentSpanInSubFilter.get()).isNull();
                assertThat(tracingStateFromRequestAttrs.get()).isEqualTo(emptyTracingState);
                assertThat(tracingStateFromWebfluxContext.get()).isEqualTo(emptyTracingState);
            }
            else {
                assertThat(currentSpanInSubFilter.get()).isNotNull();
                assertThat(currentSpanInSubFilter.get()).isEqualTo(parent);
                assertThat(tracingStateFromRequestAttrs.get().getLeft())
                    .hasSize(1)
                    .containsExactly(parent);
                assertThat(tracingStateFromWebfluxContext.get()).isEqualTo(tracingStateFromRequestAttrs.get());
            }
        }
    }

    private Pair<Span, Map<String, String>> generateUpstreamSpanHeaders() {
        Span span = Span.newBuilder("upstreamSpan", SpanPurpose.CLIENT).build();
        Map<String, String> headers = new HashMap<>();
        HttpRequestTracingUtils.propagateTracingHeaders(headers::put, span);
        headers.put(USER_ID_HEADER_KEY, UUID.randomUUID().toString());
        
        return Pair.of(span, headers);
    }

    private String getExpectedPayloadForEndpoint(String endpoint) {
        switch (endpoint) {
            case BASIC_ENDPOINT_PATH:
                return BASIC_ENDPOINT_PAYLOAD;
            case MONO_ENDPOINT_PATH:
                return MONO_ENDPOINT_PAYLOAD;
            case FLUX_ENDPOINT_PATH:
                return String.join("", FLUX_ENDPOINT_PAYLOAD);
            case ROUTER_FUNCTION_ENDPOINT_PATH:
                return ROUTER_FUNCTION_ENDPOINT_RESPONSE_PAYLOAD;
            default:
                throw new RuntimeException("Unhandled endpoint case: " + endpoint);
        }
    }

    private Span verifySingleSpanCompletedAndReturnedInResponse(
        ExtractableResponse response,
        long expectedMinSpanDurationMillis,
        Span expectedUpstreamSpan
    ) {
        // We can have a race condition where the response is sent and we try to verify here before the server
        //      has had a chance to complete the span. Wait a few milliseconds to give the server time to finish.
        waitUntilSpanRecorderHasExpectedNumSpans(1);

        assertThat(spanRecorder.completedSpans).hasSize(1);
        Span completedSpan = spanRecorder.completedSpans.get(0);
        String traceIdFromResponse = response.header(TraceHeaders.TRACE_ID);
        assertThat(traceIdFromResponse).isNotNull();
        assertThat(completedSpan.getTraceId()).isEqualTo(traceIdFromResponse);

        assertThat(TimeUnit.NANOSECONDS.toMillis(completedSpan.getDurationNanos()))
            .isGreaterThanOrEqualTo(expectedMinSpanDurationMillis);

        if (expectedUpstreamSpan != null) {
            assertThat(completedSpan.getTraceId()).isEqualTo(expectedUpstreamSpan.getTraceId());
            assertThat(completedSpan.getParentSpanId()).isEqualTo(expectedUpstreamSpan.getSpanId());
        }

        return completedSpan;
    }

    @SuppressWarnings({"SameParameterValue", "OptionalGetWithoutIsPresent"})
    private void verifySpansCompletedAndReturnedInResponse(
        ClientResponse response,
        long expectedMinSpanDurationMillis,
        int expectedNumSpansCompleted,
        Span expectedUpstreamSpan,
        boolean expectSubspanFromHttpClient
    ) {
        // We can have a race condition where the response is sent and we try to verify here before the server
        //      has had a chance to complete the span. Wait a few milliseconds to give the server time to finish.
        waitUntilSpanRecorderHasExpectedNumSpans(expectedNumSpansCompleted);

        assertThat(spanRecorder.completedSpans).hasSize(expectedNumSpansCompleted);
        String traceIdFromResponse = response.headers().asHttpHeaders().getFirst(TraceHeaders.TRACE_ID);
        assertThat(traceIdFromResponse).isNotNull();

        spanRecorder.completedSpans.forEach(
            completedSpan -> assertThat(completedSpan.getTraceId()).isEqualTo(traceIdFromResponse)
        );

        // We also have a race condition where the inner (child) span might claim a longer duration, simply because the
        //      client and server spans are async and we don't control when the spans are completed. So we can't
        //      rely on duration to find outer vs. inner span. Instead we'll look for the expected CLIENT or SERVER
        //      span purpose, depending on what's expected.
        SpanPurpose expectedOutermostSpanPurpose = (expectSubspanFromHttpClient)
                                                        ? SpanPurpose.CLIENT
                                                        : SpanPurpose.SERVER;

        Span outermostSpan = spanRecorder.completedSpans
            .stream()
            .filter(s -> s.getSpanPurpose() == expectedOutermostSpanPurpose)
            .findFirst().get();
        assertThat(TimeUnit.NANOSECONDS.toMillis(outermostSpan.getDurationNanos()))
            .isGreaterThanOrEqualTo(expectedMinSpanDurationMillis);

        if (expectedUpstreamSpan == null) {
            assertThat(outermostSpan.getParentSpanId()).isNull();
        }
        else {
            assertThat(outermostSpan.getTraceId()).isEqualTo(expectedUpstreamSpan.getTraceId());
            assertThat(outermostSpan.getParentSpanId()).isEqualTo(expectedUpstreamSpan.getSpanId());
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void verifySpanNameAndTags(
        Span span,
        String expectedSpanName,
        String expectedUserId,
        String expectedHttpMethodTagValue,
        String expectedPathTagValue,
        String expectedUrlTagValue,
        String expectedHttpRouteTagValue,
        Integer expectedStatusCodeTagValue,
        String expectedErrorTagValue,
        String expectedSpanHandlerTagValue
    ) {
        String expectedStatusCodeStr = (expectedStatusCodeTagValue == null)
                                       ? null
                                       : String.valueOf(expectedStatusCodeTagValue);

        assertThat(span.getSpanName()).isEqualTo(expectedSpanName);
        assertThat(span.getUserId()).isEqualTo(expectedUserId);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_METHOD)).isEqualTo(expectedHttpMethodTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_PATH)).isEqualTo(expectedPathTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_URL)).isEqualTo(expectedUrlTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_ROUTE)).isEqualTo(expectedHttpRouteTagValue);
        assertThat(span.getTags().get(KnownZipkinTags.HTTP_STATUS_CODE)).isEqualTo(expectedStatusCodeStr);
        assertThat(span.getTags().get(KnownZipkinTags.ERROR)).isEqualTo(expectedErrorTagValue);
        assertThat(span.getTags().get(WingtipsTags.SPAN_HANDLER)).isEqualTo(expectedSpanHandlerTagValue);
    }

    private Span findWebfluxWebClientSpanFromCompletedSpans() {
        List<Span> webfluxWebClientSpans = spanRecorder.completedSpans
            .stream()
            .filter(s -> "spring.webflux.client".equals(s.getTags().get(WingtipsTags.SPAN_HANDLER)))
            .collect(Collectors.toList());

        assertThat(webfluxWebClientSpans)
            .withFailMessage(
                "Expected to find exactly one Span that came from Spring Webflux WebClient - instead found: "
                + webfluxWebClientSpans.size()
            )
            .hasSize(1);

        return webfluxWebClientSpans.get(0);
    }

    @SpringBootApplication
    @Configuration
    @SuppressWarnings("unused")
    static class ComponentTestWebFluxApp {

        static final String USER_ID_HEADER_KEY = "foo-user-id";

        @Bean
        public WebFilter wingtipsWebFilter() {
            return WingtipsSpringWebfluxWebFilter
                .newBuilder()
                .withUserIdHeaderKeys(singletonList(USER_ID_HEADER_KEY))
                .build();
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE + 1)
        public WebFilter explodingWebFilter() {
            return new ExplodingWebFilter();
        }

        @Bean
        public RouterFunction<ServerResponse> routerFunctionEndpoint(ComponentTestController controller) {
            return RouterFunctions
                .route(GET(ROUTER_FUNCTION_ENDPOINT_PATH), controller::routerFunctionEndpoint)
                .filter(new ExplodingHandlerFilterFunction());
        }
    }

    private static final int SLEEP_TIME_MILLIS = 50;

    @Controller
    @SuppressWarnings("unused")
    static class ComponentTestController {
        static final String BASIC_ENDPOINT_PATH = "/basicEndpoint";
        static final String MONO_ENDPOINT_PATH = "/monoEndpoint";
        static final String FLUX_ENDPOINT_PATH = "/fluxEndpoint";
        static final String WEB_CLIENT_ENDPOINT_PATH = "/webClientEndpoint";
        static final String ROUTER_FUNCTION_ENDPOINT_PATH = "/routerFunctionEndpoint";
        static final String INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH = "/intQueryParamRequiredEndpoint";
        static final String PATH_PARAM_ENDPOINT_PATH_TMPLT = "/somePathParam/{foo}/required";

        static final String BASIC_ENDPOINT_PAYLOAD = "basic_endpoint_" + UUID.randomUUID().toString();
        static final String MONO_ENDPOINT_PAYLOAD = "mono_endpoint_" + UUID.randomUUID().toString();
        static final List<String> FLUX_ENDPOINT_PAYLOAD = Arrays.asList(
            "flux_endpoint_1_" + UUID.randomUUID().toString(),
            "flux_endpoint_2_" + UUID.randomUUID().toString()
        );
        static final String WEB_CLIENT_PAYLOAD_SUFFIX = "-web_client_endpoint_suffix_" + UUID.randomUUID().toString();
        static final String PATH_PARAM_ENDPOINT_PAYLOAD_SUFFIX = "-path_param_endpoint_" + UUID.randomUUID().toString();
        static final String ROUTER_FUNCTION_ENDPOINT_RESPONSE_PAYLOAD =
            "router_function_endpoint_" + UUID.randomUUID().toString();

        static final String THROW_SECONDARY_WEBCLIENT_FILTER_EXCEPTION_HEADER_KEY =
            "throw-secondary-webclient-filter-exception";

        private final WebClient webClient = WebClient
            .builder()
            .baseUrl("http://localhost:" + SERVER_PORT)
            .filter(new WingtipsSpringWebfluxExchangeFilterFunction())
            .filter((request, next) -> {
                if ("true".equals(request.headers().getFirst(THROW_SECONDARY_WEBCLIENT_FILTER_EXCEPTION_HEADER_KEY))) {
                    throw new RuntimeException("Intentional test exception in secondary WebClient filter.");
                }

                return next.exchange(request);
            })
            .build();

        @GetMapping(BASIC_ENDPOINT_PATH)
        @ResponseBody
        String basicEndpoint(ServerHttpRequest request) {
            sleepThread(SLEEP_TIME_MILLIS);

            if ("true".equals(request.getHeaders().getFirst("throw-exception"))) {
                throw new RuntimeException("Error occurred in basicEndpoint()");
            }

            return BASIC_ENDPOINT_PAYLOAD;
        }

        @GetMapping(MONO_ENDPOINT_PATH)
        @ResponseBody
        Mono<String> monoEndpoint(ServerHttpRequest request, ServerWebExchange exchange) {
            monoEndpointTracingStateFromServerWebExchange = Optional.ofNullable(tracingStateFromExchange(exchange));
            monoEndpointCurrentSpanOnEndpointExecute = Optional.ofNullable(Tracer.getInstance().getCurrentSpan());

            HttpHeaders headers = request.getHeaders();

            if ("true".equals(headers.getFirst("throw-exception"))) {
                sleepThread(SLEEP_TIME_MILLIS);
                throw new RuntimeException("Error thrown in monoEndpoint(), outside Mono");
            }

            if ("true".equals(headers.getFirst("return-exception-in-mono"))) {
                return Mono
                    .delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
                    .map(d -> {
                        throw new RuntimeException("Error thrown in monoEndpoint(), inside Mono");
                    });
            }

            return Mono
                .subscriberContext()
                .delayElement(Duration.ofMillis(SLEEP_TIME_MILLIS))
                .map(c ->{
                    monoEndpointTracingStateFromMonoContext = Optional.ofNullable(tracingStateFromContext(c));
                    return MONO_ENDPOINT_PAYLOAD;
                });
        }

        @GetMapping(FLUX_ENDPOINT_PATH)
        @ResponseBody
        Flux<String> fluxEndpoint(ServerHttpRequest request) {
            HttpHeaders headers = request.getHeaders();

            if ("true".equals(headers.getFirst("throw-exception"))) {
                sleepThread(SLEEP_TIME_MILLIS);
                throw new RuntimeException("Error thrown in fluxEndpoint(), outside Flux");
            }

            if ("true".equals(headers.getFirst("return-exception-in-flux"))) {
                return Flux.just("foo")
                           .delayElements(Duration.ofMillis(SLEEP_TIME_MILLIS))
                           .map(d -> {
                               throw new RuntimeException("Error thrown in fluxEndpoint(), inside Flux");
                           });
            }

            long delayPerElementMillis = SLEEP_TIME_MILLIS / FLUX_ENDPOINT_PAYLOAD.size();
            return Flux.fromIterable(FLUX_ENDPOINT_PAYLOAD).delayElements(Duration.ofMillis(delayPerElementMillis));
        }

        @GetMapping(WEB_CLIENT_ENDPOINT_PATH)
        @ResponseBody
        Mono<String> webClientEndpoint(ServerHttpRequest request) {
            boolean throwWebclientFilterEx = "true".equals(
                request.getHeaders().getFirst(THROW_SECONDARY_WEBCLIENT_FILTER_EXCEPTION_HEADER_KEY)
            );

            String webClientUri =
                "true".equals(request.getHeaders().getFirst("web-client-connection-error"))
                ? "http://localhost:1234567890"
                : MONO_ENDPOINT_PATH;

            return webClient
                .get()
                .uri(webClientUri)
                .header(THROW_SECONDARY_WEBCLIENT_FILTER_EXCEPTION_HEADER_KEY, String.valueOf(throwWebclientFilterEx))
                .retrieve()
                .bodyToMono(String.class)
                .delayElement(Duration.ofMillis(SLEEP_TIME_MILLIS))
                .map(s -> s + WEB_CLIENT_PAYLOAD_SUFFIX);
        }

        Mono<ServerResponse> routerFunctionEndpoint(ServerRequest request) {
            HttpHeaders headers = request.headers().asHttpHeaders();

            if ("true".equals(headers.getFirst("throw-exception"))) {
                sleepThread(SLEEP_TIME_MILLIS);
                throw new RuntimeException("Error thrown in routerFunctionEndpoint(), outside Mono");
            }

            if ("true".equals(headers.getFirst("return-exception-in-mono"))) {
                return Mono
                    .delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
                    .map(d -> {
                        throw new RuntimeException("Error thrown in routerFunctionEndpoint(), inside Mono");
                    });
            }

            return ServerResponse
                .ok()
                .syncBody(ROUTER_FUNCTION_ENDPOINT_RESPONSE_PAYLOAD)
                .delayElement(Duration.ofMillis(SLEEP_TIME_MILLIS));
        }

        @GetMapping(path = INT_QUERY_PARAM_REQUIRED_ENDPOINT_PATH)
        @ResponseBody
        public String intQueryParamRequiredEndpoint(
            @RequestParam(name = "requiredQueryParamValue") int someRequiredQueryParam
        ) {
            sleepThread(SLEEP_TIME_MILLIS);
            return "You passed in " + someRequiredQueryParam + " for the required query param value";
        }

        @GetMapping(path = PATH_PARAM_ENDPOINT_PATH_TMPLT)
        @ResponseBody
        public Mono<String> pathParamEndpoint(@PathVariable(name = "foo") String fooPathParam) {
            return Mono.delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
                       .map(d -> fooPathParam + PATH_PARAM_ENDPOINT_PAYLOAD_SUFFIX);
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
    }

    public static class ExplodingWebFilter implements WebFilter {

        @Override
        public Mono<Void> filter(
            ServerWebExchange exchange, WebFilterChain chain
        ) {
            subWebFilterTracingStateFromServerWebExchange = Optional.ofNullable(tracingStateFromExchange(exchange));
            subWebFilterCurrentSpanOnFilterExecute = Optional.ofNullable(Tracer.getInstance().getCurrentSpan());

            HttpHeaders httpHeaders = exchange.getRequest().getHeaders();

            if ("true".equals(httpHeaders.getFirst("throw-web-filter-exception"))) {
                ComponentTestController.sleepThread(SLEEP_TIME_MILLIS);
                throw new RuntimeException("Exception thrown from WebFilter");
            }

            if ("true".equals(httpHeaders.getFirst("return-exception-in-web-filter-mono"))) {
                return Mono
                    .delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
                    .map(d -> {
                        throw new RuntimeException("Exception returned from WebFilter Mono");
                    });
            }

            return chain
                .filter(exchange)
                .subscriberContext(c -> {
                    subWebFilterTracingStateFromMonoContext = Optional.ofNullable(tracingStateFromContext(c));
                    return c;
                });
        }
    }

    public static class ExplodingHandlerFilterFunction
        implements HandlerFilterFunction<ServerResponse, ServerResponse> {

        @Override
        public Mono<ServerResponse> filter(
            ServerRequest serverRequest,
            HandlerFunction<ServerResponse> handlerFunction
        ) {
            HttpHeaders httpHeaders = serverRequest.headers().asHttpHeaders();

            if ("true".equals(httpHeaders.getFirst("throw-handler-filter-function-exception"))) {
                ComponentTestController.sleepThread(SLEEP_TIME_MILLIS);
                throw new RuntimeException("Exception thrown from HandlerFilterFunction");
            }

            if ("true".equals(httpHeaders.getFirst("return-exception-in-handler-filter-function-mono"))) {
                return Mono
                    .delay(Duration.ofMillis(SLEEP_TIME_MILLIS))
                    .map(d -> {
                        throw new RuntimeException("Exception returned from HandlerFilterFunction Mono");
                    });
            }

            return handlerFunction.handle(serverRequest);
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

    static class SpringHttpClientTagAdapterWithHttpRouteKnowledge extends SpringWebfluxClientRequestTagAdapter {

        private final String httpRouteForAllRequests;

        SpringHttpClientTagAdapterWithHttpRouteKnowledge(String httpRouteForAllRequests) {
            this.httpRouteForAllRequests = httpRouteForAllRequests;
        }

        @Override
        public @Nullable String getRequestUriPathTemplate(
            @Nullable ClientRequest request,
            @Nullable ClientResponse response
        ) {
            return httpRouteForAllRequests;
        }
    }
}
