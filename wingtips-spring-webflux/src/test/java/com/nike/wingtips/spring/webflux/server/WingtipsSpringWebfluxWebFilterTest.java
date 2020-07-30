package com.nike.wingtips.spring.webflux.server;

import com.nike.internal.util.MapBuilder;
import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter.WingtipsWebFilterTracingMonoWrapper;
import com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter.WingtipsWebFilterTracingSubscriber;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.InitialSpanNameArgs;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.RequestTaggingArgs;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.ResponseTaggingArgs;
import com.nike.wingtips.testutils.TestUtils.SpanRecorder;
import com.nike.wingtips.testutils.Whitebox;
import com.nike.wingtips.util.TracingState;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Subscription;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.support.DefaultServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.i18n.FixedLocaleContextResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import static com.nike.wingtips.http.HttpRequestTracingUtils.CHILD_OF_SPAN_FROM_HEADERS_WHERE_CALLER_DID_NOT_SEND_SPAN_ID_TAG_KEY;
import static com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils.tracingStateFromContext;
import static com.nike.wingtips.testutils.TestUtils.resetTracing;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link WingtipsSpringWebfluxWebFilter}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsSpringWebfluxWebFilterTest {

    private WingtipsSpringWebfluxWebFilter filterSpy;
    private ServerWebExchange exchange;
    private ServerHttpRequest requestMock;
    private HttpHeaders requestHeadersMock;
    private ServerHttpResponse responseMock;
    private HttpHeaders responseHeadersMock;
    private WebFilterChain chainMock;
    private Mono<Void> webFilterChainResult;
    private Duration expectedResultMonoDuration;

    private HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> tagAndNamingStrategy;
    private HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> tagAndNamingAdapterMock;
    private AtomicReference<String> initialSpanNameFromStrategy;
    private AtomicBoolean strategyInitialSpanNameMethodCalled;
    private AtomicBoolean strategyRequestTaggingMethodCalled;
    private AtomicBoolean strategyResponseTaggingAndFinalSpanNameMethodCalled;
    private AtomicReference<InitialSpanNameArgs<ServerWebExchange>> strategyInitialSpanNameArgs;
    private AtomicReference<RequestTaggingArgs<ServerWebExchange>> strategyRequestTaggingArgs;
    private AtomicReference<ResponseTaggingArgs<ServerWebExchange, ServerHttpResponse>> strategyResponseTaggingArgs;

    @SuppressWarnings("FieldCanBeLocal")
    private List<String> userIdHeaderKeys;

    private SpanRecorder spanRecorder;
    
    @Before
    public void beforeMethod() {
        resetTracing();

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);

        initialSpanNameFromStrategy = new AtomicReference<>("span-name-from-strategy-" + UUID.randomUUID().toString());
        strategyInitialSpanNameMethodCalled = new AtomicBoolean(false);
        strategyRequestTaggingMethodCalled = new AtomicBoolean(false);
        strategyResponseTaggingAndFinalSpanNameMethodCalled = new AtomicBoolean(false);
        strategyInitialSpanNameArgs = new AtomicReference<>(null);
        strategyRequestTaggingArgs = new AtomicReference<>(null);
        strategyResponseTaggingArgs = new AtomicReference<>(null);
        tagAndNamingStrategy = new ArgCapturingHttpTagAndSpanNamingStrategy<>(
            initialSpanNameFromStrategy, strategyInitialSpanNameMethodCalled, strategyRequestTaggingMethodCalled,
            strategyResponseTaggingAndFinalSpanNameMethodCalled, strategyInitialSpanNameArgs,
            strategyRequestTaggingArgs, strategyResponseTaggingArgs
        );
        //noinspection unchecked
        tagAndNamingAdapterMock = mock(HttpTagAndSpanNamingAdapter.class);

        userIdHeaderKeys = Arrays.asList("user-id", "alt-user-id");

        filterSpy = spy(
            WingtipsSpringWebfluxWebFilter
                .newBuilder()
                .withTagAndNamingStrategy(tagAndNamingStrategy)
                .withTagAndNamingAdapter(tagAndNamingAdapterMock)
                .withUserIdHeaderKeys(userIdHeaderKeys)
                .build()
        );

        requestMock = mock(ServerHttpRequest.class);
        requestHeadersMock = mock(HttpHeaders.class);
        responseMock = mock(ServerHttpResponse.class);
        responseHeadersMock = mock(HttpHeaders.class);

        doReturn("someRequestId").when(requestMock).getId();

        doReturn(requestHeadersMock).when(requestMock).getHeaders();
        doReturn(responseHeadersMock).when(responseMock).getHeaders();

        exchange = new DefaultServerWebExchange(
            requestMock, responseMock, new DefaultWebSessionManager(), new DefaultServerCodecConfigurer(),
            new FixedLocaleContextResolver(Locale.US)
        );
        
        chainMock = mock(WebFilterChain.class);
        expectedResultMonoDuration = Duration.ofMillis(50);
        webFilterChainResult = Mono.delay(expectedResultMonoDuration).flatMap(l -> Mono.empty());

        doReturn(webFilterChainResult).when(chainMock).filter(any(ServerWebExchange.class));
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    // ========== Tests for WingtipsSpringWebfluxWebFilter main class (not inner classes) ============

    @Test
    public void default_constructor_sets_up_default_config_options() {
        // when
        WingtipsSpringWebfluxWebFilter impl = new WingtipsSpringWebfluxWebFilter();

        // then
        assertThat(impl.order).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(impl.tagAndNamingStrategy).isSameAs(ZipkinHttpTagStrategy.getDefaultInstance());
        assertThat(impl.tagAndNamingAdapter).isSameAs(SpringWebfluxServerRequestTagAdapter.getDefaultInstance());
        assertThat(impl.userIdHeaderKeys).isEmpty();
        verifyUserIdHeaderKeysIsUnmodifiable(impl);
    }

    private void verifyUserIdHeaderKeysIsUnmodifiable(WingtipsSpringWebfluxWebFilter filter) {
        @SuppressWarnings("ConstantConditions")
        Throwable ex = catchThrowable(() -> filter.userIdHeaderKeys.add(UUID.randomUUID().toString()));

        assertThat(ex).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void builder_constructor_sets_up_options_as_specified_in_the_builder() {
        // given
        int expectedOrder = 42;
        HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> expectedTagStrategyMock =
            mock(HttpTagAndSpanNamingStrategy.class);
        HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> expectedTagAdapterMock =
            mock(HttpTagAndSpanNamingAdapter.class);
        List<String> expectedUserIdHeaderKeys = Arrays.asList("foo", "bar");

        WingtipsSpringWebfluxWebFilter.Builder builder = WingtipsSpringWebfluxWebFilter
            .newBuilder()
            .withOrder(expectedOrder)
            .withTagAndNamingStrategy(expectedTagStrategyMock)
            .withTagAndNamingAdapter(expectedTagAdapterMock)
            .withUserIdHeaderKeys(expectedUserIdHeaderKeys);

        // when
        WingtipsSpringWebfluxWebFilter impl = new WingtipsSpringWebfluxWebFilter(builder);

        // then
        assertThat(impl.order).isEqualTo(expectedOrder);
        assertThat(impl.tagAndNamingStrategy).isSameAs(expectedTagStrategyMock);
        assertThat(impl.tagAndNamingAdapter).isSameAs(expectedTagAdapterMock);
        assertThat(impl.userIdHeaderKeys).isEqualTo(expectedUserIdHeaderKeys);
        verifyUserIdHeaderKeysIsUnmodifiable(impl);
    }

    @Test
    public void builder_constructor_sets_up_default_config_options_when_passed_empty_builder() {
        // when
        WingtipsSpringWebfluxWebFilter impl = new WingtipsSpringWebfluxWebFilter(
            WingtipsSpringWebfluxWebFilter.newBuilder()
        );

        // then
        assertThat(impl.order).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(impl.tagAndNamingStrategy).isSameAs(ZipkinHttpTagStrategy.getDefaultInstance());
        assertThat(impl.tagAndNamingAdapter).isSameAs(SpringWebfluxServerRequestTagAdapter.getDefaultInstance());
        assertThat(impl.userIdHeaderKeys).isEmpty();
        verifyUserIdHeaderKeysIsUnmodifiable(impl);
    }

    @Test
    public void filter_works_as_expected() {
        // given
        AtomicReference<Span> expectedSpanRef = new AtomicReference<>();
        doAnswer(invocation -> {
            Span overallRequestSpan = Tracer.getInstance().startRequestWithRootSpan("fooOverallRequestSpan");
            expectedSpanRef.set(overallRequestSpan);
            return overallRequestSpan;
        }).when(filterSpy).createNewSpanForRequest(exchange);

        // when
        Mono<Void> result = filterSpy.filter(exchange, chainMock);

        // then
        // Make sure createNewSpanForRequest() was called.
        verify(filterSpy).createNewSpanForRequest(exchange);
        Span expectedSpan = expectedSpanRef.get();

        // Make sure the request attributes were set correctly.
        verify(filterSpy).addTracingInfoToRequestAttributes(any(TracingState.class), eq(expectedSpan), eq(exchange));
        assertThat(exchange.<String>getAttribute(TraceHeaders.TRACE_ID)).isEqualTo(expectedSpan.getTraceId());
        assertThat(exchange.<String>getAttribute(TraceHeaders.SPAN_ID)).isEqualTo(expectedSpan.getSpanId());
        TracingState overallReqTracingState = exchange.getAttribute(TracingState.class.getName());

        // Verify that the tracing state from the request attributes is what we expect.
        assertThat(overallReqTracingState).isNotNull();
        assertThat(overallReqTracingState.spanStack.peek()).isSameAs(expectedSpan);

        // The trace ID header should have been set on the response.
        verify(responseHeadersMock).set(TraceHeaders.TRACE_ID, expectedSpan.getTraceId());

        // Span tagging for the request should have been done using the strategy.
        assertThat(strategyRequestTaggingMethodCalled.get()).isTrue();
        strategyRequestTaggingArgs.get().verifyArgs(expectedSpan, exchange, tagAndNamingAdapterMock);

        // Verify we get a result mono that has a source that is a WingtipsWebFilterTracingMonoWrapper with everything
        //      setup correctly.
        Object resultSource = Whitebox.getInternalState(result, "source");
        assertThat(resultSource).isInstanceOf(WingtipsWebFilterTracingMonoWrapper.class);

        WingtipsWebFilterTracingMonoWrapper resultWrapper = (WingtipsWebFilterTracingMonoWrapper)resultSource;
        assertThat(Whitebox.getInternalState(resultWrapper, "source")).isSameAs(webFilterChainResult);
        assertThat(resultWrapper.exchange).isSameAs(exchange);
        assertThat(resultWrapper.overallRequestTracingState).isSameAs(overallReqTracingState);
        assertThat(resultWrapper.tagAndNamingStrategy).isSameAs(filterSpy.tagAndNamingStrategy);
        assertThat(resultWrapper.tagAndNamingAdapter).isSameAs(filterSpy.tagAndNamingAdapter);

        // No spans should have been completed before we subscribe to the result Mono.
        assertThat(expectedSpan.isCompleted()).isFalse();
        assertThat(spanRecorder.completedSpans).isEmpty();

        // Verify the mono execution, including that the Context has been populated with the expected TracingState.
        Duration resultExecutionDuration = StepVerifier
            .create(result)
            .expectAccessibleContext()
            .assertThat(
                c -> {
                    TracingState tc = tracingStateFromContext(c);
                    assertThat(tc).isSameAs(overallReqTracingState);
                }
            )
            .then()
            .expectComplete()
            .verify();

        assertThat(resultExecutionDuration).isGreaterThanOrEqualTo(expectedResultMonoDuration);

        // The overall request span should be completed, and it should have been tagged appropriately.
        waitUntilSpanRecorderHasExpectedNumSpans(1);
        assertThat(expectedSpan.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(expectedSpan));
        assertThat(expectedSpan.getDurationNanos()).isGreaterThanOrEqualTo(expectedResultMonoDuration.toNanos());

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            expectedSpan, exchange, responseMock, null, tagAndNamingAdapterMock
        );

        // The tracing state on this thread should have been cleaned up before the filter() method returned.
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
    }

    @SuppressWarnings("SameParameterValue")
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

    @Test
    public void filter_completes_the_overall_request_span_if_unexpected_exception_occurs() {
        // given
        Throwable exFromFilterChain = new RuntimeException("Intentional test exception.");
        doThrow(exFromFilterChain).when(chainMock).filter(exchange);

        AtomicReference<Span> expectedSpanRef = new AtomicReference<>();
        doAnswer(invocation -> {
            Span overallRequestSpan = Tracer.getInstance().startRequestWithRootSpan("fooOverallRequestSpan");
            expectedSpanRef.set(overallRequestSpan);
            return overallRequestSpan;
        }).when(filterSpy).createNewSpanForRequest(exchange);

        // when
        Throwable ex = catchThrowable(() -> filterSpy.filter(exchange, chainMock));

        // then
        verify(chainMock).filter(exchange);
        assertThat(ex).isSameAs(exFromFilterChain);

        // Make sure createNewSpanForRequest() was called and get the expected overall request span.
        verify(filterSpy).createNewSpanForRequest(exchange);
        Span expectedSpan = expectedSpanRef.get();

        // The overall request span should be completed at this point due to the catch block in the filter,
        //      and it should have been tagged appropriately.
        assertThat(expectedSpan.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(expectedSpan));

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            expectedSpan, exchange, responseMock, exFromFilterChain, tagAndNamingAdapterMock
        );

        // The tracing state on this thread should have been cleaned up before the filter() method returned.
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
    }

    @Test
    public void calling_filter_twice_for_a_single_request_causes_the_first_tracing_state_to_be_used() {
        // given
        AtomicReference<Span> expectedSpanRef = new AtomicReference<>();
        doAnswer(invocation -> {
            Span overallRequestSpan = Tracer.getInstance().startRequestWithRootSpan("fooOverallRequestSpan");
            expectedSpanRef.set(overallRequestSpan);
            return overallRequestSpan;
        }).when(filterSpy).createNewSpanForRequest(exchange);

        // Call filter the first time.
        Mono<Void> firstFilterCallResult = filterSpy.filter(exchange, chainMock);
        verify(filterSpy).createNewSpanForRequest(exchange);
        Span expectedSpan = expectedSpanRef.get();

        String traceIdFromReqAttr = exchange.getAttribute(TraceHeaders.TRACE_ID);
        String spanIdFromReqAttr = exchange.getAttribute(TraceHeaders.SPAN_ID);
        TracingState tracingStateFromReqAttr = exchange.getAttribute(TracingState.class.getName());

        assertThat(traceIdFromReqAttr).isEqualTo(expectedSpan.getTraceId());
        assertThat(spanIdFromReqAttr).isEqualTo(expectedSpan.getSpanId());
        assertThat(tracingStateFromReqAttr).isNotNull();
        assertThat(tracingStateFromReqAttr.spanStack.peek()).isSameAs(expectedSpan);

        AtomicBoolean secondFilterStrategyRequestTaggingMethodCalled = new AtomicBoolean(false);
        HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> secondFilterTagAndNamingStrategy =
            new ArgCapturingHttpTagAndSpanNamingStrategy<>(
                new AtomicReference<>(), new AtomicBoolean(),
                secondFilterStrategyRequestTaggingMethodCalled,
                new AtomicBoolean(), new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>()
            );
        WingtipsSpringWebfluxWebFilter secondFilterSpy = spy(
            WingtipsSpringWebfluxWebFilter
                .newBuilder()
                .withTagAndNamingStrategy(secondFilterTagAndNamingStrategy)
                .withTagAndNamingAdapter(tagAndNamingAdapterMock)
                .withUserIdHeaderKeys(userIdHeaderKeys)
                .build()
        );

        assertThat(secondFilterSpy.warnedAboutMultipleFilters.get()).isFalse();

        // when - we call a second filter on the same request.
        Mono<Void> secondFilterCallResult = secondFilterSpy.filter(exchange, chainMock);

        // then
        assertThat(secondFilterSpy.warnedAboutMultipleFilters.get()).isTrue();

        // None of the normal tracing state setup and request stuff should have been done on the second filter.
        verify(secondFilterSpy, never()).createNewSpanForRequest(any(ServerWebExchange.class));
        verify(secondFilterSpy, never())
            .addTracingInfoToRequestAttributes(any(TracingState.class), any(Span.class), any(ServerWebExchange.class));
        assertThat(secondFilterStrategyRequestTaggingMethodCalled.get()).isFalse();

        // The request attributes should be the same as before.
        assertThat(exchange.<String>getAttribute(TraceHeaders.TRACE_ID)).isEqualTo(traceIdFromReqAttr);
        assertThat(exchange.<String>getAttribute(TraceHeaders.SPAN_ID)).isEqualTo(spanIdFromReqAttr);
        assertThat(exchange.<TracingState>getAttribute(TracingState.class.getName())).isEqualTo(tracingStateFromReqAttr);

        // No spans should have been completed before we subscribe to the either Mono.
        assertThat(expectedSpan.isCompleted()).isFalse();
        assertThat(spanRecorder.completedSpans).isEmpty();

        // Verify that executing both mono results from both filters results in the same original TracingState being
        //      used for both filters.
        StepVerifier
            .create(firstFilterCallResult)
            .expectAccessibleContext()
            .assertThat(
                c -> {
                    TracingState tc = tracingStateFromContext(c);
                    assertThat(tc).isSameAs(tracingStateFromReqAttr);
                }
            )
            .then()
            .expectComplete()
            .verify();

        StepVerifier
            .create(secondFilterCallResult)
            .expectAccessibleContext()
            .assertThat(
                c -> {
                    TracingState tc = tracingStateFromContext(c);
                    assertThat(tc).isSameAs(tracingStateFromReqAttr);
                }
            )
            .then()
            .expectComplete()
            .verify();

        // The overall request span should be completed, but only that one expected span! We should only have the one
        //      expected span in the span recorder.
        waitUntilSpanRecorderHasExpectedNumSpans(1);
        assertThat(expectedSpan.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(expectedSpan));

        assertThat(spanRecorder.completedSpans).hasSize(1);
    }

    @SuppressWarnings("unused")
    private enum CreateNewSpanForRequestScenario {
        WITH_PARENT_SPAN_ON_INCOMING_HEADERS(
            "fooTraceId", "fooParentId", "1",
            null, null,
            "fooParentId", true, null
        ),
        WITH_PARENT_AND_USER_ID_HEADER(
            "fooTraceId", "fooParentId", "1",
            "fooUserId", null,
            "fooParentId", true, "fooUserId"
        ),
        WITH_PARENT_AND_ALT_USER_ID_HEADER(
            "fooTraceId", "fooParentId", "1",
            null, "fooAltUserId",
            "fooParentId", true, "fooAltUserId"
        ),
        WITH_PARENT_AND_BOTH_USER_ID_HEADERS(
            "fooTraceId", "fooParentId", "1",
            "fooUserId", "fooAltUserId",
            "fooParentId", true, "fooUserId"
        ),
        NO_PARENT_SPAN_ON_INCOMING_HEADERS(
            null, null, null,
            null, null,
            null, true, null
        ),
        NO_PARENT_BUT_WITH_USER_ID_HEADER(
            null, null, null,
            "fooUserId", null,
            null, true, "fooUserId"
        ),
        NO_PARENT_BUT_WITH_ALT_USER_ID_HEADER(
            null, null, null,
            null, "fooAltUserId",
            null, true, "fooAltUserId"
        ),
        NO_PARENT_BUT_WITH_BOTH_USER_ID_HEADERS(
            null, null, null,
            "fooUserId", "fooAltUserId",
            null, true, "fooUserId"
        ),
        WITH_INCOMING_TRACE_ID_HEADER_BUT_NOTHING_ELSE(
            "fooTraceId", null, null,
            null, null,
            null, true, null
        ),
        MISSING_INCOMING_TRACE_ID_HEADER_BUT_WITH_EVERYTHING_ELSE(
            null, "fooParentId", "0",
            "fooUserId", "fooAltUserId",
            null, true, "fooUserId"
        ),
        WITH_INCOMING_SAMPLEABLE_FLAG_SET_TO_1(
            "fooTraceId", "fooParentId", "1",
            null, null,
            "fooParentId", true, null
        ),
        WITH_INCOMING_SAMPLEABLE_FLAG_SET_TO_TRUE(
            "fooTraceId", "fooParentId", "true",
            null, null,
            "fooParentId", true, null
        ),
        WITH_INCOMING_SAMPLEABLE_FLAG_SET_TO_0(
            "fooTraceId", "fooParentId", "0",
            null, null,
            "fooParentId", false, null
        ),
        WITH_INCOMING_SAMPLEABLE_FLAG_SET_TO_FALSE(
            "fooTraceId", "fooParentId", "false",
            null, null,
            "fooParentId", false, null
        );

        public final @Nullable String incomingTraceIdHeader;
        public final @Nullable String incomingSpanIdHeader;
        public final @Nullable String incomingSampledHeader;
        public final @Nullable String incomingUserIdHeader;
        public final @Nullable String incomingAltUserIdHeader;

        public final @Nullable String expectedParentSpanId;
        public final boolean expectedSampleableValue;
        public final @Nullable String expectedUserId;

        CreateNewSpanForRequestScenario(
            @Nullable String incomingTraceIdHeader,
            @Nullable String incomingSpanIdHeader,
            @Nullable String incomingSampledHeader,
            @Nullable String incomingUserIdHeader,
            @Nullable String incomingAltUserIdHeader,
            @Nullable String expectedParentSpanId,
            boolean expectedSampleableValue,
            @Nullable String expectedUserId
        ) {
            this.incomingTraceIdHeader = incomingTraceIdHeader;
            this.incomingSpanIdHeader = incomingSpanIdHeader;
            this.incomingSampledHeader = incomingSampledHeader;
            this.incomingUserIdHeader = incomingUserIdHeader;
            this.incomingAltUserIdHeader = incomingAltUserIdHeader;
            this.expectedParentSpanId = expectedParentSpanId;
            this.expectedSampleableValue = expectedSampleableValue;
            this.expectedUserId = expectedUserId;
        }
    }

    @DataProvider
    public static List<List<CreateNewSpanForRequestScenario>> createNewSpanForRequestScenarioDataProvider() {
        return Stream.of(CreateNewSpanForRequestScenario.values())
                     .map(Collections::singletonList)
                     .collect(Collectors.toList());
    }

    @UseDataProvider("createNewSpanForRequestScenarioDataProvider")
    @Test
    public void createNewSpanForRequest_works_as_expected(CreateNewSpanForRequestScenario scenario) {
        // given
        doReturn(scenario.incomingTraceIdHeader).when(requestHeadersMock).getFirst(TraceHeaders.TRACE_ID);
        doReturn(scenario.incomingSpanIdHeader).when(requestHeadersMock).getFirst(TraceHeaders.SPAN_ID);
        doReturn(scenario.incomingSampledHeader).when(requestHeadersMock).getFirst(TraceHeaders.TRACE_SAMPLED);
        doReturn(scenario.incomingUserIdHeader).when(requestHeadersMock).getFirst("user-id");
        doReturn(scenario.incomingAltUserIdHeader).when(requestHeadersMock).getFirst("alt-user-id");

        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();

        // when
        Span result = filterSpy.createNewSpanForRequest(exchange);

        // then
        if (scenario.incomingTraceIdHeader != null) {
            assertThat(result.getTraceId()).isEqualTo(scenario.incomingTraceIdHeader);
        }
        assertThat(result.getParentSpanId()).isEqualTo(scenario.expectedParentSpanId);
        assertThat(result.isSampleable()).isEqualTo(scenario.expectedSampleableValue);
        assertThat(result.getUserId()).isEqualTo(scenario.expectedUserId);

        if (scenario.incomingTraceIdHeader != null && scenario.incomingSpanIdHeader == null) {
            assertThat(HttpRequestTracingUtils.hasInvalidParentIdBecauseCallerDidNotSendSpanId(result)).isTrue();
            assertThat(result.getTags().get(CHILD_OF_SPAN_FROM_HEADERS_WHERE_CALLER_DID_NOT_SEND_SPAN_ID_TAG_KEY))
                .isEqualTo("true");
        }

        // The current thread tracing state should be setup appropriately.
        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(result);
    }

    @DataProvider(value = {
        "spanNameFromStrategy   |   someHttpRoute   |   someSpringPattern   |   PATCH   |   spanNameFromStrategy",
        "null                   |   someHttpRoute   |   someSpringPattern   |   PATCH   |   PATCH someHttpRoute",
        "                       |   someHttpRoute   |   someSpringPattern   |   PATCH   |   PATCH someHttpRoute",
        "[whitespace]           |   someHttpRoute   |   someSpringPattern   |   PATCH   |   PATCH someHttpRoute",
        "null                   |   null            |   someSpringPattern   |   PATCH   |   PATCH someSpringPattern",
        "null                   |                   |   someSpringPattern   |   PATCH   |   PATCH someSpringPattern",
        "null                   |   [whitespace]    |   someSpringPattern   |   PATCH   |   PATCH someSpringPattern",
        "null                   |   null            |   null                |   PATCH   |   PATCH",
        "null                   |   null            |                       |   PATCH   |   PATCH",
        "null                   |   null            |   [whitespace]        |   PATCH   |   PATCH",
    }, splitBy = "\\|")
    @Test
    public void getInitialSpanName_works_as_expected(
        String strategyResult,
        String httpRouteAttr,
        String bestMatchingPatternAttr,
        String httpMethod,
        String expectedResult
    ) {
        // given
        if ("[whitespace]".equals(strategyResult)) {
            strategyResult = "  \n\r\t  ";
        }

        if ("[whitespace]".equals(httpRouteAttr)) {
            httpRouteAttr = "  \n\r\t  ";
        }

        if ("[whitespace]".equals(bestMatchingPatternAttr)) {
            bestMatchingPatternAttr = "  \n\r\t  ";
        }

        if ("[whitespace]".equals(httpMethod)) {
            httpMethod = "  \n\r\t  ";
        }

        initialSpanNameFromStrategy.set(strategyResult);
        if (httpRouteAttr != null) {
            exchange.getAttributes().put(KnownZipkinTags.HTTP_ROUTE, httpRouteAttr);
        }
        if (bestMatchingPatternAttr != null) {
            exchange.getAttributes().put(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, bestMatchingPatternAttr);
        }
        doReturn(httpMethod).when(requestMock).getMethodValue();

        // when
        String result = filterSpy.getInitialSpanName(exchange, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(strategyInitialSpanNameMethodCalled.get()).isTrue();
        strategyInitialSpanNameArgs.get().verifyArgs(exchange, tagAndNamingAdapterMock);
    }

    @DataProvider(value = {
        // http.route takes precedence
        "/some/http/route   |   /some/spring/pattern    |   /some/http/route",

        "/some/http/route   |   null                    |   /some/http/route",
        "/some/http/route   |                           |   /some/http/route",
        "/some/http/route   |   [whitespace]            |   /some/http/route",

        // Spring matching pattern request attr is used if http.route is null/blank
        "null               |   /some/spring/pattern    |   /some/spring/pattern",
        "                   |   /some/spring/pattern    |   /some/spring/pattern",
        "[whitespace]       |   /some/spring/pattern    |   /some/spring/pattern",

        // null returned if both request attrs are null/blank
        "null               |   null                    |   null",
        "                   |                           |   null",
        "[whitespace]       |   [whitespace]            |   null",
    }, splitBy = "\\|")
    @Test
    public void determineUriPathTemplate_works_as_expected(
        String httpRouteRequestAttr,
        String springMatchingPatternRequestAttr,
        String expectedResult
    ) {
        // given
        if ("[whitespace]".equals(httpRouteRequestAttr)) {
            httpRouteRequestAttr = "  \t\r\n  ";
        }

        if ("[whitespace]".equals(springMatchingPatternRequestAttr)) {
            springMatchingPatternRequestAttr = "  \t\r\n  ";
        }

        if (httpRouteRequestAttr != null) {
            exchange.getAttributes().put(KnownZipkinTags.HTTP_ROUTE, httpRouteRequestAttr);
        }

        if (springMatchingPatternRequestAttr != null) {
            exchange.getAttributes().put(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                                         springMatchingPatternRequestAttr);
        }

        // when
        String result = WingtipsSpringWebfluxWebFilter.determineUriPathTemplate(exchange);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void getRequestAttributeAsString_works_as_expected(boolean attrValueIsNull) {
        // given
        String attrName = UUID.randomUUID().toString();

        Object attrValueMock = mock(Object.class);
        String attrValueToString = UUID.randomUUID().toString();
        //noinspection ResultOfMethodCallIgnored
        doReturn(attrValueToString).when(attrValueMock).toString();

        if (!attrValueIsNull) {
            exchange.getAttributes().put(attrName, attrValueMock);
        }

        String expectedResult = (attrValueIsNull) ? null : attrValueToString;

        // when
        String result = WingtipsSpringWebfluxWebFilter.getRequestAttributeAsString(exchange, attrName);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void addTracingInfoToRequestAttributes_works_as_expected() {
        // given
        Span span = Span.newBuilder("foo", Span.SpanPurpose.SERVER).build();
        TracingState tracingStateMock = mock(TracingState.class);

        exchange.getAttributes().clear();
        assertThat(exchange.getAttributes()).isEmpty();

        // when
        filterSpy.addTracingInfoToRequestAttributes(tracingStateMock, span, exchange);

        // then
        assertThat(exchange.getAttributes()).hasSize(3);
        assertThat(exchange.getAttributes().get(TraceHeaders.TRACE_ID)).isEqualTo(span.getTraceId());
        assertThat(exchange.getAttributes().get(TraceHeaders.SPAN_ID)).isEqualTo(span.getSpanId());
        assertThat(exchange.getAttributes().get(TracingState.class.getName())).isEqualTo(tracingStateMock);
    }

    @Test
    public void subscriberContextWithTracingInfo_works_as_expected() {
        // given
        Map<String, String> origContextPairs = MapBuilder
            .builder("foo", UUID.randomUUID().toString())
            .put("bar", UUID.randomUUID().toString())
            .build();
        Context origContext = Context.of(origContextPairs);
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        Context result = filterSpy.subscriberContextWithTracingInfo(origContext, tracingStateMock);

        // then
        assertThat(result.size()).isEqualTo(origContextPairs.size() + 1);
        origContextPairs.forEach(
            (k, v) -> assertThat(result.<String>get(k)).isEqualTo(v)
        );
        assertThat(result.get(TracingState.class)).isSameAs(tracingStateMock);
    }

    @SuppressWarnings("unused")
    private enum ExtraCustomTagsScenario {
        NULL_EXTRA_TAGS(null),
        EMPTY_EXTRA_TAGS(new Pair[0]),
        SINGLE_EXTRA_TAG(new Pair[]{
            Pair.of("foo", UUID.randomUUID().toString())
        }),
        MULTIPLE_EXTRA_TAGS(new Pair[]{
            Pair.of("foo", UUID.randomUUID().toString()),
            Pair.of("bar", UUID.randomUUID().toString()),
            });

        public final Pair<String, String>[] extraCustomTags;

        ExtraCustomTagsScenario(Pair<String, String>[] extraCustomTags) {
            this.extraCustomTags = extraCustomTags;
        }
    }

    @DataProvider
    public static List<List<ExtraCustomTagsScenario>> extraCustomTagsScenarioDataProvider() {
        return Stream.of(ExtraCustomTagsScenario.values()).map(Collections::singletonList).collect(Collectors.toList());
    }

    @UseDataProvider("extraCustomTagsScenarioDataProvider")
    @Test
    public void finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread_works_as_expected_happy_path(
        ExtraCustomTagsScenario scenario
    ) {
        // given
        Span rootSpan = Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        Throwable error = mock(Throwable.class);

        // when
        WingtipsSpringWebfluxWebFilter.finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread(
            exchange, error, tagAndNamingStrategy, tagAndNamingAdapterMock, scenario.extraCustomTags
        );

        // then
        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            rootSpan, exchange, responseMock, error, tagAndNamingAdapterMock
        );

        if (scenario.extraCustomTags != null) {
            for (Pair<String, String> expectedTag : scenario.extraCustomTags) {
                assertThat(rootSpan.getTags().get(expectedTag.getKey())).isEqualTo(expectedTag.getValue());
            }
        }

        assertThat(rootSpan.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(rootSpan));
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
    }

    @Test
    public void finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread_does_nothing_if_called_when_current_thread_span_is_null() {
        // given
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();

        // when
        WingtipsSpringWebfluxWebFilter.finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread(
            exchange, mock(Throwable.class), tagAndNamingStrategy, tagAndNamingAdapterMock,
            Pair.of("foo", "bar")
        );

        // then
        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isFalse();
        assertThat(spanRecorder.completedSpans).isEmpty();
    }

    @Test
    public void finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread_does_nothing_if_called_when_current_thread_span_is_already_completed() {
        // given
        Span spanMock = mock(Span.class);
        Tracer.getInstance().registerWithThread(new ArrayDeque<>(singleton(spanMock)));
        reset(spanMock);
        doReturn(true).when(spanMock).isCompleted();

        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(spanMock);

        // when
        WingtipsSpringWebfluxWebFilter.finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread(
            exchange, mock(Throwable.class), tagAndNamingStrategy, tagAndNamingAdapterMock,
            Pair.of("foo", "bar")
        );

        // then
        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isFalse();
        assertThat(spanRecorder.completedSpans).isEmpty();
        verify(spanMock).isCompleted();
        verifyNoMoreInteractions(spanMock);
    }

    @Test
    public void finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread_completes_span_even_if_highly_improbable_exception_occurs() {
        // given
        Span rootSpan = Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        RuntimeException improbableException = new RuntimeException("Intentional test exception");

        @SuppressWarnings("unchecked")
        Pair<String, String> explodingTag = mock(Pair.class);
        //noinspection ResultOfMethodCallIgnored
        doThrow(improbableException).when(explodingTag).getKey();

        // when
        Throwable ex = catchThrowable(
            () -> WingtipsSpringWebfluxWebFilter.finalizeAndCompleteOverallRequestSpanAttachedToCurrentThread(
                exchange, mock(Throwable.class), tagAndNamingStrategy, tagAndNamingAdapterMock, explodingTag
            )
        );

        // then
        assertThat(ex).isSameAs(improbableException);

        assertThat(rootSpan.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(rootSpan));
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
    }

    @DataProvider(value = {
        "-2147483648",
        "-42",
        "-1",
        "0",
        "1",
        "42",
        "2147483647"
    })
    @Test
    public void getOrder_and_setOrder_work_as_expected(int order) {
        // when
        filterSpy.setOrder(order);

        // then
        assertThat(filterSpy.getOrder()).isEqualTo(order);
    }

    @Test
    public void newBuilder_creates_new_default_builder() {
        // when
        WingtipsSpringWebfluxWebFilter.Builder builder = WingtipsSpringWebfluxWebFilter.newBuilder();

        // then
        assertThat(builder.order).isNull();
        assertThat(builder.tagAndNamingStrategy).isNull();
        assertThat(builder.tagAndNamingAdapter).isNull();
        assertThat(builder.userIdHeaderKeys).isNull();
    }

    // ========== Tests for Builder inner class ============
    
    @Test
    public void Builder_defaults_fields_to_null() {
        // when
        WingtipsSpringWebfluxWebFilter.Builder builder = new WingtipsSpringWebfluxWebFilter.Builder();

        // then
        assertThat(builder.order).isNull();
        assertThat(builder.tagAndNamingStrategy).isNull();
        assertThat(builder.tagAndNamingAdapter).isNull();
        assertThat(builder.userIdHeaderKeys).isNull();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void Builder_withOrder_works_as_expected(boolean valueIsNull) {
        // given
        WingtipsSpringWebfluxWebFilter.Builder origBuilder = new WingtipsSpringWebfluxWebFilter.Builder();
        Integer expectedValue = (valueIsNull) ? null : 42;

        // when
        WingtipsSpringWebfluxWebFilter.Builder result = origBuilder.withOrder(expectedValue);

        // then
        assertThat(result).isSameAs(origBuilder);
        assertThat(origBuilder.order).isEqualTo(expectedValue);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void Builder_withTagAndNamingStrategy_works_as_expected(boolean valueIsNull) {
        // given
        WingtipsSpringWebfluxWebFilter.Builder origBuilder = new WingtipsSpringWebfluxWebFilter.Builder();
        HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> expectedValue =
            (valueIsNull) ? null : tagAndNamingStrategy;

        // when
        WingtipsSpringWebfluxWebFilter.Builder result = origBuilder.withTagAndNamingStrategy(expectedValue);

        // then
        assertThat(result).isSameAs(origBuilder);
        assertThat(origBuilder.tagAndNamingStrategy).isEqualTo(expectedValue);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void Builder_withTagAndNamingAdapter_works_as_expected(boolean valueIsNull) {
        // given
        WingtipsSpringWebfluxWebFilter.Builder origBuilder = new WingtipsSpringWebfluxWebFilter.Builder();
        HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> expectedValue =
            (valueIsNull) ? null : tagAndNamingAdapterMock;

        // when
        WingtipsSpringWebfluxWebFilter.Builder result = origBuilder.withTagAndNamingAdapter(expectedValue);

        // then
        assertThat(result).isSameAs(origBuilder);
        assertThat(origBuilder.tagAndNamingAdapter).isEqualTo(expectedValue);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void Builder_withUserIdHeaderKeys_works_as_expected(boolean valueIsNull) {
        // given
        WingtipsSpringWebfluxWebFilter.Builder origBuilder = new WingtipsSpringWebfluxWebFilter.Builder();
        List<String> expectedValue = (valueIsNull) ? null : userIdHeaderKeys;

        // when
        WingtipsSpringWebfluxWebFilter.Builder result = origBuilder.withUserIdHeaderKeys(expectedValue);

        // then
        assertThat(result).isSameAs(origBuilder);
        assertThat(origBuilder.userIdHeaderKeys).isEqualTo(expectedValue);
    }

    @Test
    public void Builder_build_works_as_expected() {
        // given
        WingtipsSpringWebfluxWebFilter.Builder builder = new WingtipsSpringWebfluxWebFilter.Builder()
            .withOrder(42)
            .withTagAndNamingStrategy(tagAndNamingStrategy)
            .withTagAndNamingAdapter(tagAndNamingAdapterMock)
            .withUserIdHeaderKeys(userIdHeaderKeys);

        // when
        WingtipsSpringWebfluxWebFilter result = builder.build();

        // then
        assertThat(result.order).isEqualTo(builder.order);
        assertThat(result.tagAndNamingStrategy).isSameAs(builder.tagAndNamingStrategy);
        assertThat(result.tagAndNamingAdapter).isSameAs(builder.tagAndNamingAdapter);
        assertThat(result.userIdHeaderKeys).isEqualTo(builder.userIdHeaderKeys);
    }

    // ========== Tests for WingtipsWebFilterTracingMonoWrapper inner class ============
    @Test
    public void WingtipsWebFilterTracingMonoWrapper_constructor_sets_fields_as_expected() {
        // given
        @SuppressWarnings("unchecked")
        Mono<Void> sourceMock = mock(Mono.class);
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        WingtipsWebFilterTracingMonoWrapper impl = new WingtipsWebFilterTracingMonoWrapper(
            sourceMock, exchange, tracingStateMock, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        // then
        assertThat(impl.exchange).isSameAs(exchange);
        assertThat(impl.overallRequestTracingState).isSameAs(tracingStateMock);
        assertThat(impl.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(impl.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void WingtipsWebFilterTracingMonoWrapper_subscribe_works_as_expected() {
        // given
        // Setup the tracing state for the subscribe call.
        Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        TracingState expectedTracingStateInSourceSubscribe = TracingState.getCurrentThreadTracingState();

        // Now that the tracing-state-for-subscribe has been setup, setup the current thread tracing state to
        //      something completely different.
        Tracer.getInstance().unregisterFromThread();
        Tracer.getInstance().startRequestWithRootSpan("someCompletelyDifferentRootSpan");
        TracingState baseTracingState = TracingState.getCurrentThreadTracingState();

        Mono<Void> sourceMock = mock(Mono.class);
        AtomicReference<TracingState> actualSourceSubscribeTracingState = new AtomicReference<>();
        doAnswer(invocation -> {
            actualSourceSubscribeTracingState.set(TracingState.getCurrentThreadTracingState());
            return null;
        }).when(sourceMock).subscribe(any(CoreSubscriber.class));

        WingtipsWebFilterTracingMonoWrapper impl = new WingtipsWebFilterTracingMonoWrapper(
            sourceMock, exchange, expectedTracingStateInSourceSubscribe, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        CoreSubscriber<? super Void> actualSubscriberMock = mock(CoreSubscriber.class);
        Context actualSubscriberContextMock = mock(Context.class);
        doReturn(actualSubscriberContextMock).when(actualSubscriberMock).currentContext();

        // when
        impl.subscribe(actualSubscriberMock);

        // then
        // The source should have been subscribed to with a WingtipsWebFilterTracingSubscriber
        //      that contains expected values.
        ArgumentCaptor<CoreSubscriber> subscriberCaptor = ArgumentCaptor.forClass(CoreSubscriber.class);
        verify(sourceMock).subscribe(subscriberCaptor.capture());
        assertThat(subscriberCaptor.getValue()).isInstanceOf(WingtipsWebFilterTracingSubscriber.class);
        WingtipsWebFilterTracingSubscriber subscriberWrapper =
            (WingtipsWebFilterTracingSubscriber) subscriberCaptor.getValue();

        assertThat(subscriberWrapper.actual).isSameAs(actualSubscriberMock);
        assertThat(subscriberWrapper.exchange).isSameAs(exchange);
        assertThat(subscriberWrapper.subscriberContext).isSameAs(actualSubscriberContextMock);
        assertThat(subscriberWrapper.overallRequestTracingState).isSameAs(expectedTracingStateInSourceSubscribe);
        assertThat(subscriberWrapper.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(subscriberWrapper.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);

        // The tracing state at the time of subscription should be what we expect.
        assertThat(actualSourceSubscribeTracingState.get()).isEqualTo(expectedTracingStateInSourceSubscribe);

        // The base tracing state should have been restored.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);
    }

    // ========== Tests for WingtipsWebFilterTracingSubscriber inner class ============
    @Test
    public void WingtipsWebFilterTracingSubscriber_constructor_sets_fields_as_expected() {
        // given
        @SuppressWarnings("unchecked")
        CoreSubscriber<Void> actualSubscriberMock = mock(CoreSubscriber.class);
        Context subscriberContextMock = mock(Context.class);
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        WingtipsWebFilterTracingSubscriber impl =
            new WingtipsWebFilterTracingSubscriber(
                actualSubscriberMock, exchange, subscriberContextMock, tracingStateMock,
                tagAndNamingStrategy, tagAndNamingAdapterMock
            );

        // then
        assertThat(impl.actual).isSameAs(actualSubscriberMock);
        assertThat(impl.exchange).isSameAs(exchange);
        assertThat(impl.subscriberContext).isSameAs(subscriberContextMock);
        assertThat(impl.overallRequestTracingState).isSameAs(tracingStateMock);
        assertThat(impl.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(impl.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
    }

    private WingtipsWebFilterTracingSubscriber setupSubscriberWrapper() {
        Tracer.getInstance().startRequestWithRootSpan("TracingState for Subscriber wrapper root span");

        @SuppressWarnings("unchecked")
        WingtipsWebFilterTracingSubscriber result =
            new WingtipsWebFilterTracingSubscriber(
                mock(CoreSubscriber.class), exchange, mock(Context.class), TracingState.getCurrentThreadTracingState(),
                tagAndNamingStrategy, tagAndNamingAdapterMock
            );

        Tracer.getInstance().unregisterFromThread();

        return result;
    }

    @Test
    public void WingtipsWebFilterTracingSubscriber_currentContext_returns_expected_context() {
        // given
        WingtipsWebFilterTracingSubscriber impl = setupSubscriberWrapper();

        // when
        Context result = impl.currentContext();

        // then
        assertThat(result).isSameAs(impl.subscriberContext);
    }

    @Test
    public void WingtipsWebFilterTracingSubscriber_onSubscribe_wraps_Subscription_with_one_that_completes_overall_request_span_on_cancel() {
        // given
        WingtipsWebFilterTracingSubscriber impl = setupSubscriberWrapper();
        Span spanForCompletion = impl.overallRequestTracingState.spanStack.peek();
        assertThat(spanForCompletion).isNotNull();

        Subscription origSubscriptionMock = mock(Subscription.class);
        AtomicReference<TracingState> tracingStateOnOrigSubscriptionCancel = new AtomicReference<>();
        doAnswer(invocation -> {
            tracingStateOnOrigSubscriptionCancel.set(TracingState.getCurrentThreadTracingState());
            return null;
        }).when(origSubscriptionMock).cancel();

        Tracer.getInstance().startRequestWithRootSpan("someRandomThreadRootSpan");
        TracingState baseTracingState = TracingState.getCurrentThreadTracingState();

        // when
        impl.onSubscribe(origSubscriptionMock);

        // then - the actual CoreSubscriber should be onSubscribe()'d with a wrapper subscription which we'll capture
        //      for later testing.
        ArgumentCaptor<Subscription> wrappedSubscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(impl.actual).onSubscribe(wrappedSubscriptionCaptor.capture());
        Subscription wrappedSubscription = wrappedSubscriptionCaptor.getValue();
        assertThat(wrappedSubscription).isNotEqualTo(origSubscriptionMock);

        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);

        // and when
        wrappedSubscription.request(42);

        // then - the wrapper subscription passes request() calls through to the original subscription.
        verify(origSubscriptionMock).request(42);

        // and when
        assertThat(spanRecorder.completedSpans).isEmpty();
        assertThat(spanForCompletion.isCompleted()).isFalse();
        wrappedSubscription.cancel();

        // then - the wrapper subscription passes cancel() calls through to the original subscription (with the
        //      correct subspan tracing state attached to the thread), and it completes the expected subspan correctly.
        verify(origSubscriptionMock).cancel();
        assertThat(tracingStateOnOrigSubscriptionCancel.get()).isEqualTo(impl.overallRequestTracingState);

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            spanForCompletion, exchange, responseMock, null, tagAndNamingAdapterMock
        );

        assertThat(spanForCompletion.getTags().get("cancelled")).isEqualTo("true");

        assertThat(spanForCompletion.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(spanForCompletion));

        // The current thread tracing state should be unchanged after the cancel() call.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);
    }


    @Test
    public void WingtipsWebFilterTracingSubscriber_onNext_calls_actual_subscriber_onNext() {
        // given
        WingtipsWebFilterTracingSubscriber impl = setupSubscriberWrapper();

        // when
        impl.onNext(null);

        // then
        verify(impl.actual).onNext(null);
    }

    @Test
    public void WingtipsWebFilterTracingSubscriber_onError_calls_actual_subscriber_onError_and_completes_overall_request_span() {
        // given
        WingtipsWebFilterTracingSubscriber impl = setupSubscriberWrapper();
        Span spanForCompletion = impl.overallRequestTracingState.spanStack.peek();
        assertThat(spanForCompletion).isNotNull();

        Tracer.getInstance().startRequestWithRootSpan("someRandomThreadRootSpan");
        TracingState baseTracingState = TracingState.getCurrentThreadTracingState();

        AtomicReference<TracingState> tracingStateOnActualOnError = new AtomicReference<>();
        doAnswer(invocation -> {
            tracingStateOnActualOnError.set(TracingState.getCurrentThreadTracingState());
            return null;
        }).when(impl.actual).onError(any());

        Throwable errorMock = mock(Throwable.class);

        assertThat(spanRecorder.completedSpans).isEmpty();
        assertThat(spanForCompletion.isCompleted()).isFalse();

        // when
        impl.onError(errorMock);

        // then
        verify(impl.actual).onError(errorMock);
        assertThat(tracingStateOnActualOnError.get()).isEqualTo(impl.overallRequestTracingState);

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            spanForCompletion, exchange, responseMock, errorMock, tagAndNamingAdapterMock
        );

        assertThat(spanForCompletion.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(spanForCompletion));

        // The current thread tracing state should be unchanged after the onError() call.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);
    }

    @Test
    public void WingtipsWebFilterTracingSubscriber_onComplete_calls_actual_subscriber_onComplete_and_completes_overall_request_span() {
        // given
        WingtipsWebFilterTracingSubscriber impl = setupSubscriberWrapper();
        Span spanForCompletion = impl.overallRequestTracingState.spanStack.peek();
        assertThat(spanForCompletion).isNotNull();

        Tracer.getInstance().startRequestWithRootSpan("someRandomThreadRootSpan");
        TracingState baseTracingState = TracingState.getCurrentThreadTracingState();

        AtomicReference<TracingState> tracingStateOnActualOnComplete = new AtomicReference<>();
        doAnswer(invocation -> {
            tracingStateOnActualOnComplete.set(TracingState.getCurrentThreadTracingState());
            return null;
        }).when(impl.actual).onComplete();

        assertThat(spanRecorder.completedSpans).isEmpty();
        assertThat(spanForCompletion.isCompleted()).isFalse();

        // when
        impl.onComplete();

        // then
        verify(impl.actual).onComplete();
        assertThat(tracingStateOnActualOnComplete.get()).isEqualTo(impl.overallRequestTracingState);

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            spanForCompletion, exchange, responseMock, null, tagAndNamingAdapterMock
        );

        assertThat(spanForCompletion.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(spanForCompletion));

        // The current thread tracing state should be unchanged after the onComplete() call.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);
    }
}