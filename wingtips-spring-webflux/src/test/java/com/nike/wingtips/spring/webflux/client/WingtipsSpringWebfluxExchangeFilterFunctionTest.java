package com.nike.wingtips.spring.webflux.client;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils;
import com.nike.wingtips.spring.webflux.client.WingtipsSpringWebfluxExchangeFilterFunction.WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper;
import com.nike.wingtips.spring.webflux.client.WingtipsSpringWebfluxExchangeFilterFunction.WingtipsExchangeFilterFunctionTracingCompletionSubscriber;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.InitialSpanNameArgs;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.RequestTaggingArgs;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.ResponseTaggingArgs;
import com.nike.wingtips.testutils.TestUtils.SpanRecorder;
import com.nike.wingtips.util.TracingState;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.util.reflection.Whitebox;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import static com.nike.wingtips.TraceHeaders.PARENT_SPAN_ID;
import static com.nike.wingtips.TraceHeaders.SPAN_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_SAMPLED;
import static com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils.tracingStateFromContext;
import static com.nike.wingtips.testutils.TestUtils.resetTracing;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
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
 * Tests the functionality of {@link WingtipsSpringWebfluxExchangeFilterFunction}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsSpringWebfluxExchangeFilterFunctionTest {

    private WingtipsSpringWebfluxExchangeFilterFunction filterSpy;

    private HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy;
    private HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapterMock;
    private AtomicReference<String> initialSpanNameFromStrategy;
    private AtomicBoolean strategyInitialSpanNameMethodCalled;
    private AtomicBoolean strategyRequestTaggingMethodCalled;
    private AtomicBoolean strategyResponseTaggingAndFinalSpanNameMethodCalled;
    private AtomicReference<InitialSpanNameArgs<ClientRequest>> strategyInitialSpanNameArgs;
    private AtomicReference<RequestTaggingArgs<ClientRequest>> strategyRequestTaggingArgs;
    private AtomicReference<ResponseTaggingArgs<ClientRequest, ClientResponse>> strategyResponseTaggingArgs;

    private ClientRequest request;
    private ExchangeFunction nextExchangeFunctionMock;
    @SuppressWarnings("FieldCanBeLocal")
    private Mono<ClientResponse> nextExchangeFunctionResult;
    private ClientResponse responseMock;

    private SpanRecorder spanRecorder;

    @Before
    @SuppressWarnings("unchecked")
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
        tagAndNamingAdapterMock = mock(HttpTagAndSpanNamingAdapter.class);

        filterSpy = spy(
            new WingtipsSpringWebfluxExchangeFilterFunction(true, tagAndNamingStrategy, tagAndNamingAdapterMock)
        );

        request = ClientRequest
            .create(HttpMethod.PATCH, URI.create("http://localhost:1234/foo/bar?stuff=things"))
            .header("fooHeader", UUID.randomUUID().toString())
            .build();
        responseMock = mock(ClientResponse.class);
        nextExchangeFunctionMock = mock(ExchangeFunction.class);
        nextExchangeFunctionResult = Mono.just(responseMock);

        doReturn(nextExchangeFunctionResult).when(nextExchangeFunctionMock).exchange(any(ClientRequest.class));
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    // ========== Tests for WingtipsSpringWebfluxExchangeFilterFunction main class (not inner classes) ============

    @Test
    public void DEFAULT_IMPL_has_default_config_options() {
        // given
        WingtipsSpringWebfluxExchangeFilterFunction defaultImpl =
            WingtipsSpringWebfluxExchangeFilterFunction.DEFAULT_IMPL;

        // then
        assertThat(defaultImpl.surroundCallsWithSubspan).isTrue();
        assertThat(defaultImpl.tagAndNamingStrategy)
            .isSameAs(SpringWebfluxClientRequestZipkinTagStrategy.getDefaultInstance());
        assertThat(defaultImpl.tagAndNamingAdapter)
            .isSameAs(SpringWebfluxClientRequestTagAdapter.getDefaultInstance());
    }

    @Test
    public void default_constructor_sets_up_default_config_options() {
        // when
        WingtipsSpringWebfluxExchangeFilterFunction impl = new WingtipsSpringWebfluxExchangeFilterFunction();

        // then
        assertThat(impl.surroundCallsWithSubspan).isTrue();
        assertThat(impl.tagAndNamingStrategy)
            .isSameAs(SpringWebfluxClientRequestZipkinTagStrategy.getDefaultInstance());
        assertThat(impl.tagAndNamingAdapter)
            .isSameAs(SpringWebfluxClientRequestTagAdapter.getDefaultInstance());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void single_arg_constructor_works_as_expected(boolean subspanOptionOn) {
        // when
        WingtipsSpringWebfluxExchangeFilterFunction impl = new WingtipsSpringWebfluxExchangeFilterFunction(
            subspanOptionOn
        );

        // then
        assertThat(impl.surroundCallsWithSubspan).isEqualTo(subspanOptionOn);
        assertThat(impl.tagAndNamingStrategy)
            .isSameAs(SpringWebfluxClientRequestZipkinTagStrategy.getDefaultInstance());
        assertThat(impl.tagAndNamingAdapter)
            .isSameAs(SpringWebfluxClientRequestTagAdapter.getDefaultInstance());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void kitchen_sink_constructor_works_as_expected(boolean subspanOptionOn) {
        // when
        WingtipsSpringWebfluxExchangeFilterFunction impl = new WingtipsSpringWebfluxExchangeFilterFunction(
            subspanOptionOn, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        // then
        assertThat(impl.surroundCallsWithSubspan).isEqualTo(subspanOptionOn);
        assertThat(impl.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(impl.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
    }

    @SuppressWarnings("unused")
    private enum NullConstructorArgScenario {
        TAG_STRATEGY_IS_NULL(
            null,
            mock(HttpTagAndSpanNamingAdapter.class),
            "tagAndNamingStrategy cannot be null - if you really want no strategy, use NoOpHttpTagStrategy"
        ),
        TAG_ADAPTER_IS_NULL(
            mock(HttpTagAndSpanNamingStrategy.class),
            null,
            "tagAndNamingAdapter cannot be null - if you really want no adapter, use NoOpHttpTagAdapter"
        );

        public final HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy;
        public final HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapter;
        public final String expectedErrorMessage;

        NullConstructorArgScenario(
            HttpTagAndSpanNamingStrategy<ClientRequest, ClientResponse> tagAndNamingStrategy,
            HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> tagAndNamingAdapter,
            String expectedErrorMessage
        ) {
            this.tagAndNamingStrategy = tagAndNamingStrategy;
            this.tagAndNamingAdapter = tagAndNamingAdapter;
            this.expectedErrorMessage = expectedErrorMessage;
        }
    }

    @DataProvider
    public static List<List<NullConstructorArgScenario>> nullConstructorArgScenarioDataProvider() {
        return Stream.of(NullConstructorArgScenario.values())
                     .map(Collections::singletonList)
                     .collect(Collectors.toList());
    }

    @UseDataProvider("nullConstructorArgScenarioDataProvider")
    @Test
    public void kitchen_sink_constructor_throws_NullPointerException_if_certain_args_are_null(
        NullConstructorArgScenario scenario
    ) {
        // when
        Throwable ex = catchThrowable(
            () -> new WingtipsSpringWebfluxExchangeFilterFunction(
                true, scenario.tagAndNamingStrategy, scenario.tagAndNamingAdapter
            )
        );

        // then
        assertThat(ex)
            .isInstanceOf(NullPointerException.class)
            .hasMessage(scenario.expectedErrorMessage);
    }

    private enum ThreadAndRequestTracingStateScenario {
        NO_TRACING_STATE_ON_THREAD_OR_IN_REQUEST_ATTRS(false, false),
        TRACING_STATE_ON_THREAD_BUT_NOT_IN_REQUEST_ATTRS(true, false),
        TRACING_STATE_IN_REQUEST_ATTRS_BUT_NOT_ON_THREAD(false, true),
        DIFFERENT_TRACING_STATES_ON_THREAD_VS_IN_REQUEST_ATTRS(true, true);

        private final boolean tracingStateOnThread;
        private final boolean tracingStateInRequestAttr;

        ThreadAndRequestTracingStateScenario(boolean tracingStateOnThread, boolean tracingStateInRequestAttr) {
            this.tracingStateOnThread = tracingStateOnThread;
            this.tracingStateInRequestAttr = tracingStateInRequestAttr;
        }

        public ScenarioSetupResult setupTracingStateOnThreadAndRequestForScenario(ClientRequest origRequest) {
            resetTracing();
            TracingState emptyTracingState = TracingState.getCurrentThreadTracingState();

            ClientRequest requestToUse = origRequest;
            TracingState requestTracingState = null;
            if (tracingStateInRequestAttr) {
                Tracer.getInstance().startRequestWithRootSpan("TracingState for req attr root span");
                Tracer.getInstance().startSubSpan("TracingState for req attr subspan", SpanPurpose.LOCAL_ONLY);
                requestTracingState = TracingState.getCurrentThreadTracingState();
                requestToUse = ClientRequest
                    .from(origRequest)
                    .attribute(TracingState.class.getName(), requestTracingState)
                    .build();
                resetTracing();
            }

            TracingState currentThreadTracingState = null;
            if (tracingStateOnThread) {
                Tracer.getInstance().startRequestWithRootSpan("TracingState for current thread root span");
                Tracer.getInstance().startSubSpan("TracingState for current thread subspan", SpanPurpose.LOCAL_ONLY);
                currentThreadTracingState = TracingState.getCurrentThreadTracingState();
            }

            TracingState expectedTracingState = emptyTracingState;
            if (tracingStateInRequestAttr) {
                // Request attr tracing state wins if it exists.
                assertThat(requestTracingState).isNotNull();
                expectedTracingState = requestTracingState;
            }
            else if (tracingStateOnThread) {
                // Fall back to current thread tracing state if it exists.
                assertThat(currentThreadTracingState).isNotNull();
                expectedTracingState = currentThreadTracingState;
            }

            return new ScenarioSetupResult(requestToUse, expectedTracingState);
        }

        static class ScenarioSetupResult {
            final ClientRequest requestToUse;
            final TracingState expectedTracingStateUsed;

            ScenarioSetupResult(
                ClientRequest requestToUse, TracingState expectedTracingStateUsed
            ) {
                this.requestToUse = requestToUse;
                this.expectedTracingStateUsed = expectedTracingStateUsed;
            }
        }
    }

    @DataProvider
    public static List<List<ThreadAndRequestTracingStateScenario>> threadAndRequestTracingStateScenarioDataProvider() {
        return Stream.of(ThreadAndRequestTracingStateScenario.values())
                     .map(Collections::singletonList)
                     .collect(Collectors.toList());
    }

    @UseDataProvider("threadAndRequestTracingStateScenarioDataProvider")
    @Test
    public void filter_delegates_to_doFilterForCurrentThreadTracingState_with_expected_tracing_state_attached_to_thread_when_delegate_method_is_executed(
        ThreadAndRequestTracingStateScenario scenarioSetup
    ) {
        // given
        ThreadAndRequestTracingStateScenario.ScenarioSetupResult scenario =
            scenarioSetup.setupTracingStateOnThreadAndRequestForScenario(request);

        @SuppressWarnings("unchecked")
        Mono<ClientResponse> expectedResult = mock(Mono.class);
        AtomicReference<TracingState> tracingStateWhenDelegateMethodCalled = new AtomicReference<>();
        doAnswer(invocation -> {
            tracingStateWhenDelegateMethodCalled.set(TracingState.getCurrentThreadTracingState());
            return expectedResult;
        }).when(filterSpy).doFilterForCurrentThreadTracingState(scenario.requestToUse, nextExchangeFunctionMock);

        // when
        Mono<ClientResponse> result = filterSpy.filter(scenario.requestToUse, nextExchangeFunctionMock);

        // then
        assertThat(result).isSameAs(expectedResult);
        verify(filterSpy).doFilterForCurrentThreadTracingState(scenario.requestToUse, nextExchangeFunctionMock);
        assertThat(tracingStateWhenDelegateMethodCalled.get()).isEqualTo(scenario.expectedTracingStateUsed);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    @SuppressWarnings("unchecked")
    public void doFilterForCurrentThreadTracingState_delegates_to_expected_method_depending_on_value_of_surroundCallsWithSubspan(
        boolean subspanOptionOn
    ) {
        // given
        filterSpy = spy(new WingtipsSpringWebfluxExchangeFilterFunction(subspanOptionOn));

        Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        Tracer.getInstance().startSubSpan("fooSubspan", SpanPurpose.LOCAL_ONLY);
        TracingState expectedTracingState = TracingState.getCurrentThreadTracingState();

        Mono<ClientResponse> createSubspanAndExecuteDelegateMethodResultMock = mock(Mono.class);
        Mono<ClientResponse> executeOnlyDelegateMethodResultMock = mock(Mono.class);

        doReturn(createSubspanAndExecuteDelegateMethodResultMock)
            .when(filterSpy).createAsyncSubSpanAndExecute(request, nextExchangeFunctionMock);
        doReturn(executeOnlyDelegateMethodResultMock)
            .when(filterSpy).propagateTracingHeadersAndExecute(request, nextExchangeFunctionMock, expectedTracingState);

        // when
        Mono<ClientResponse> result = filterSpy.doFilterForCurrentThreadTracingState(request, nextExchangeFunctionMock);

        // then

        // We have to do this obvious verification so that we can do a verifyNoMoreInteractions() later.
        verify(filterSpy).doFilterForCurrentThreadTracingState(request, nextExchangeFunctionMock);

        // Verify that we only called the expected delegate method (and returned the result from that delegate)
        //      depending on whether the subspan option is on or off.
        if (subspanOptionOn) {
            verify(filterSpy).createAsyncSubSpanAndExecute(request, nextExchangeFunctionMock);
            verify(filterSpy, never()).propagateTracingHeadersAndExecute(any(), any(), any());
            verifyNoMoreInteractions(filterSpy);
            assertThat(result).isSameAs(createSubspanAndExecuteDelegateMethodResultMock);
        }
        else {
            verify(filterSpy).propagateTracingHeadersAndExecute(request, nextExchangeFunctionMock, expectedTracingState);
            verify(filterSpy, never()).createAsyncSubSpanAndExecute(any(), any());
            verifyNoMoreInteractions(filterSpy);
            assertThat(result).isSameAs(executeOnlyDelegateMethodResultMock);
        }
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void propagateTracingHeadersAndExecute_works_as_expected(
        boolean reqAttrAndMonoContextTracingStateIsNull, boolean propagationSpanHasParent
    ) {
        // given
        Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        Span expectedPropagationSpan = (propagationSpanHasParent)
                                       ? Tracer.getInstance().startSubSpan("fooSubspan", SpanPurpose.LOCAL_ONLY)
                                       : Tracer.getInstance().getCurrentSpan();
        TracingState expectedTracingStateForReqAttrsAndMonoContext = (reqAttrAndMonoContextTracingStateIsNull)
                                                   ? null
                                                   : TracingState.getCurrentThreadTracingState();

        // when
        Mono<ClientResponse> result = filterSpy.propagateTracingHeadersAndExecute(
            request, nextExchangeFunctionMock, expectedTracingStateForReqAttrsAndMonoContext
        );

        // then
        // Verify we get the expected result mono that has a Context populated with the expected tracing state and
        //      returns the expected ClientResponse.
        StepVerifier
            .create(result)
            .expectAccessibleContext()
            .assertThat(
                c -> assertThat(tracingStateFromContext(c)).isEqualTo(expectedTracingStateForReqAttrsAndMonoContext)
            )
            .then()
            .expectNext(responseMock)
            .expectComplete()
            .verify();

        // Extract the request that was passed to the filter chain and verify that it contains the expected tracing
        //      propagation headers, and has the expected tracing state request attribute.
        ArgumentCaptor<ClientRequest> filterChainRequestCaptor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(nextExchangeFunctionMock).exchange(filterChainRequestCaptor.capture());
        ClientRequest filterChainRequest = filterChainRequestCaptor.getValue();
        verifyExpectedTraceHeaders(filterChainRequest.headers(), expectedPropagationSpan);

        assertThat(filterChainRequest.attribute(TracingState.class.getName()))
            .isEqualTo(Optional.ofNullable(expectedTracingStateForReqAttrsAndMonoContext));
    }

    private void verifyExpectedTraceHeaders(HttpHeaders headers, Span expectedPropagationSpan) {
        verifyExpectedTraceHeaders(
            headers,
            expectedPropagationSpan.getTraceId(),
            expectedPropagationSpan.getSpanId(),
            expectedPropagationSpan.isSampleable(),
            expectedPropagationSpan.getParentSpanId()
        );
    }

    private void verifyExpectedTraceHeaders(
        HttpHeaders headers, String expectedTraceId, String expectedSpanId, boolean expectSampled,
        String expectedParentSpanId
    ) {
        assertThat(headers.get(TRACE_ID)).isEqualTo(singletonList(expectedTraceId));
        assertThat(headers.get(SPAN_ID)).isEqualTo(singletonList(expectedSpanId));
        assertThat(headers.get(TRACE_SAMPLED)).isEqualTo(singletonList(expectSampled ? "1" : "0"));
        assertThat(headers.get(PARENT_SPAN_ID))
            .isEqualTo((expectedParentSpanId == null) ? null : singletonList(expectedParentSpanId));
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void createAsyncSubSpanAndExecute_works_as_expected(
        boolean baseTracingStateExists
    ) {
        // given
        if (baseTracingStateExists) {
            Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        }
        TracingState expectedBaseTracingState = TracingState.getCurrentThreadTracingState();
        Span expectedBaseSpan = Tracer.getInstance().getCurrentSpan();

        String expectedSubspanName = UUID.randomUUID().toString();
        doReturn(expectedSubspanName).when(filterSpy)
                                     .getSubspanSpanName(request, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // when
        Mono<ClientResponse> result = filterSpy.createAsyncSubSpanAndExecute(request, nextExchangeFunctionMock);

        // then
        // Verify we get a result mono that has a source that is a
        //      WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper, has a Context populated with a tracing
        //      state, and returns the expected ClientResponse.
        Object resultSource = Whitebox.getInternalState(result, "source");
        assertThat(resultSource).isInstanceOf(WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper.class);

        // (No spans should have been completed before we subscribe to the result Mono.)
        assertThat(spanRecorder.completedSpans).isEmpty();

        AtomicReference<TracingState> propagatedTracingStateRef = new AtomicReference<>();
        AtomicReference<Span> propagatedSpanRef = new AtomicReference<>();
        StepVerifier
            .create(result)
            .expectAccessibleContext()
            .assertThat(
                c -> {
                    TracingState tc = tracingStateFromContext(c);
                    propagatedTracingStateRef.set(tc);
                    assertThat(tc).isNotNull();
                    Span propagatedSpan = tc.spanStack.peek();
                    assertThat(propagatedSpan).isNotNull();
                    assertThat(propagatedSpan.isCompleted()).isFalse();
                    propagatedSpanRef.set(propagatedSpan);
                }
            )
            .then()
            .expectNext(responseMock)
            .expectComplete()
            .verify();

        // Verify that the propagated tracing state is correct.
        TracingState propagatedTracingState = propagatedTracingStateRef.get();
        Span propagatedSpan = propagatedSpanRef.get();
        assertThat(propagatedTracingState).isNotNull();
        assertThat(propagatedSpan).isNotNull();

        verify(filterSpy).getSubspanSpanName(request, tagAndNamingStrategy, tagAndNamingAdapterMock);
        assertThat(propagatedSpan.getSpanName()).isEqualTo(expectedSubspanName);
        if (expectedBaseSpan == null) {
            assertThat(propagatedSpan.getParentSpanId()).isNull();
        }
        else {
            assertThat(propagatedSpan.getTraceId()).isEqualTo(expectedBaseSpan.getTraceId());
            assertThat(propagatedSpan.getParentSpanId()).isEqualTo(expectedBaseSpan.getSpanId());
        }

        // Extract the request that was passed to the filter chain and verify that it contains the expected tracing
        //      propagation headers, and has the expected tracing state request attribute.
        ArgumentCaptor<ClientRequest> filterChainRequestCaptor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(nextExchangeFunctionMock).exchange(filterChainRequestCaptor.capture());
        ClientRequest filterChainRequest = filterChainRequestCaptor.getValue();
        verifyExpectedTraceHeaders(filterChainRequest.headers(), propagatedSpan);

        assertThat(filterChainRequest.attribute(TracingState.class.getName()))
            .isEqualTo(Optional.of(propagatedTracingState));

        // Verify the tag strategy was called correctly.
        assertThat(strategyRequestTaggingMethodCalled.get()).isTrue();
        strategyRequestTaggingArgs.get().verifyArgs(propagatedSpan, request,tagAndNamingAdapterMock);

        // Verify that the returned WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper has the expected values.
        WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper wrapperResult =
            (WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper)resultSource;
        assertThat(wrapperResult.request).isSameAs(request);
        assertThat(wrapperResult.spanAroundCallTracingState).isSameAs(propagatedTracingState);
        assertThat(wrapperResult.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(wrapperResult.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);

        // Verify that the base tracing state was restored.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(expectedBaseTracingState);

        // We've subscribed to the result Mono and verified its behavior, so the propagated span should be completed.
        assertThat(propagatedSpan.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(propagatedSpan));
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void createAsyncSubSpanAndExecute_handles_unexpected_exception_from_filter_chain_by_completing_span_around_call(
        boolean baseTracingStateExists
    ) {
        // given
        if (baseTracingStateExists) {
            Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        }
        TracingState expectedBaseTracingState = TracingState.getCurrentThreadTracingState();
        Span expectedBaseSpan = Tracer.getInstance().getCurrentSpan();

        String expectedSubspanName = UUID.randomUUID().toString();
        doReturn(expectedSubspanName).when(filterSpy)
                                     .getSubspanSpanName(request, tagAndNamingStrategy, tagAndNamingAdapterMock);

        long expectedMinSpanDurationMillis = 25;
        long expectedMinSpanDurationNanos = TimeUnit.MILLISECONDS.toNanos(expectedMinSpanDurationMillis);

        Throwable exThrownByFilterChain = new RuntimeException("Intentional test exception.");
        doAnswer(invocation -> {
            Thread.sleep(expectedMinSpanDurationMillis);
            throw exThrownByFilterChain;
        }).when(nextExchangeFunctionMock).exchange(any());

        // when
        long beforeCallTimeNanos = System.nanoTime();
        Throwable ex = catchThrowable(() -> filterSpy.createAsyncSubSpanAndExecute(request, nextExchangeFunctionMock));
        long expectedMaxSpanDurationNanos = System.nanoTime() - beforeCallTimeNanos;

        // then
        // The exception should have bubbled up.
        verify(nextExchangeFunctionMock).exchange(any());
        assertThat(ex).isSameAs(exThrownByFilterChain);

        // We should have called completeSubspan(...) to complete the propagated span.
        ArgumentCaptor<TracingState> propagatedTracingStateCaptor = ArgumentCaptor.forClass(TracingState.class);
        verify(filterSpy).completeSubspan(
            propagatedTracingStateCaptor.capture(), eq(request), isNull(ClientResponse.class), eq(exThrownByFilterChain)
        );
        TracingState propagatedTracingState = propagatedTracingStateCaptor.getValue();
        assertThat(propagatedTracingState).isNotNull();
        Span propagatedSpan = propagatedTracingState.spanStack.peek();
        assertThat(propagatedSpan).isNotNull();

        // Verify the propagated span has the expected values.
        verify(filterSpy).getSubspanSpanName(request, tagAndNamingStrategy, tagAndNamingAdapterMock);
        assertThat(propagatedSpan.getSpanName()).isEqualTo(expectedSubspanName);
        if (expectedBaseSpan == null) {
            assertThat(propagatedSpan.getParentSpanId()).isNull();
        }
        else {
            assertThat(propagatedSpan.getTraceId()).isEqualTo(expectedBaseSpan.getTraceId());
            assertThat(propagatedSpan.getParentSpanId()).isEqualTo(expectedBaseSpan.getSpanId());
        }

        // The propagated span should be completed, and have the expected minimum duration.
        assertThat(propagatedSpan.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(propagatedSpan));
        assertThat(propagatedSpan.getDurationNanos())
            .isBetween(expectedMinSpanDurationNanos, expectedMaxSpanDurationNanos);

        // Verify that the base tracing state was restored.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(expectedBaseTracingState);
    }

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
    public void completeSubspanAttachedToCurrentThread_works_as_expected_happy_path(
        ExtraCustomTagsScenario scenario
    ) {
        // given
        Span rootSpan = Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        Span currentSpan = Tracer.getInstance().startSubSpan("fooSubspan", SpanPurpose.LOCAL_ONLY);
        Throwable error = mock(Throwable.class);

        // when
        WingtipsSpringWebfluxExchangeFilterFunction.completeSubspanAttachedToCurrentThread(
            request, responseMock, error, tagAndNamingStrategy, tagAndNamingAdapterMock, scenario.extraCustomTags
        );

        // then
        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            currentSpan, request, responseMock, error, tagAndNamingAdapterMock
        );

        if (scenario.extraCustomTags != null) {
            for (Pair<String, String> expectedTag : scenario.extraCustomTags) {
                assertThat(currentSpan.getTags().get(expectedTag.getKey())).isEqualTo(expectedTag.getValue());
            }
        }

        assertThat(currentSpan.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(currentSpan));
        assertThat(Tracer.getInstance().getCurrentSpan()).isEqualTo(rootSpan);
    }

    @Test
    public void completeSubspanAttachedToCurrentThread_does_nothing_if_called_when_current_thread_span_is_null() {
        // given
        assertThat(Tracer.getInstance().getCurrentSpan()).isNull();

        // when
        WingtipsSpringWebfluxExchangeFilterFunction.completeSubspanAttachedToCurrentThread(
            request, responseMock, mock(Throwable.class), tagAndNamingStrategy, tagAndNamingAdapterMock,
            Pair.of("foo", "bar")
        );

        // then
        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isFalse();
        assertThat(spanRecorder.completedSpans).isEmpty();
    }

    @Test
    public void completeSubspanAttachedToCurrentThread_does_nothing_if_called_when_current_thread_span_is_already_completed() {
        // given
        Span spanMock = mock(Span.class);
        Tracer.getInstance().registerWithThread(new ArrayDeque<>(singleton(spanMock)));
        reset(spanMock);
        doReturn(true).when(spanMock).isCompleted();

        assertThat(Tracer.getInstance().getCurrentSpan()).isSameAs(spanMock);

        // when
        WingtipsSpringWebfluxExchangeFilterFunction.completeSubspanAttachedToCurrentThread(
            request, responseMock, mock(Throwable.class), tagAndNamingStrategy, tagAndNamingAdapterMock,
            Pair.of("foo", "bar")
        );

        // then
        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isFalse();
        assertThat(spanRecorder.completedSpans).isEmpty();
        verify(spanMock).isCompleted();
        verifyNoMoreInteractions(spanMock);
    }

    @Test
    public void completeSubspanAttachedToCurrentThread_completes_span_even_if_highly_improbable_exception_occurs() {
        // given
        Span rootSpan = Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        Span currentSpan = Tracer.getInstance().startSubSpan("fooSubspan", SpanPurpose.LOCAL_ONLY);
        RuntimeException improbableException = new RuntimeException("Intentional test exception");

        @SuppressWarnings("unchecked")
        Pair<String, String> explodingTag = mock(Pair.class);
        //noinspection ResultOfMethodCallIgnored
        doThrow(improbableException).when(explodingTag).getKey();

        // when
        Throwable ex = catchThrowable(
            () -> WingtipsSpringWebfluxExchangeFilterFunction.completeSubspanAttachedToCurrentThread(
                request, responseMock, mock(Throwable.class), tagAndNamingStrategy, tagAndNamingAdapterMock, explodingTag
            )
        );

        // then
        assertThat(ex).isSameAs(improbableException);

        assertThat(currentSpan.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(currentSpan));
        assertThat(Tracer.getInstance().getCurrentSpan()).isEqualTo(rootSpan);
    }

    // completeSubspan simply delegates to completeSubspanAttachedToCurrentThread_works_as_expected_happy_path
    //      after attaching the correct tracing state to the thread.
    @Test
    public void completeSubspan_works_as_expected() {
        // given
        Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        Span currentSpanForCompletion = Tracer.getInstance().startSubSpan("fooSubspan", SpanPurpose.LOCAL_ONLY);
        TracingState expectedTracingStateForCompletion = TracingState.getCurrentThreadTracingState();

        // Now that the tracing-state-for-completion has been setup, setup the current thread tracing state to
        //      something completely different.
        Tracer.getInstance().unregisterFromThread();
        Tracer.getInstance().startRequestWithRootSpan("someCompletelyDifferentRootSpan");
        TracingState baseTracingState = TracingState.getCurrentThreadTracingState();

        Throwable error = mock(Throwable.class);

        // when
        filterSpy.completeSubspan(expectedTracingStateForCompletion, request, responseMock, error);

        // then
        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            currentSpanForCompletion, request, responseMock, error, tagAndNamingAdapterMock
        );

        assertThat(currentSpanForCompletion.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(currentSpanForCompletion));

        // The base tracing state should have been restored right before the method call ended.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);
    }

    @Test
    public void completeSubspan_does_nothing_if_passed_null_TracingState() {
        // given
        Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        Span currentSpan = Tracer.getInstance().startSubSpan("fooSubspan", SpanPurpose.LOCAL_ONLY);

        Throwable error = mock(Throwable.class);

        // when
        filterSpy.completeSubspan(null, request, responseMock, error);

        // then
        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isFalse();

        assertThat(currentSpan.isCompleted()).isFalse();
        assertThat(spanRecorder.completedSpans).isEmpty();
    }

    @DataProvider(value = {
        "spanNameFromStrategy   |   PATCH           |   spanNameFromStrategy",
        "null                   |   PATCH           |   webflux_downstream_call-PATCH",
        "                       |   PATCH           |   webflux_downstream_call-PATCH",
        "[whitespace]           |   PATCH           |   webflux_downstream_call-PATCH",
        "null                   |   null            |   webflux_downstream_call-UNKNOWN_HTTP_METHOD",
        "null                   |                   |   webflux_downstream_call-UNKNOWN_HTTP_METHOD",
        "null                   |   [whitespace]    |   webflux_downstream_call-UNKNOWN_HTTP_METHOD",
    }, splitBy = "\\|")
    @Test
    public void getSubspanSpanName_works_as_expected(String strategyResult, String httpMethod, String expectedResult) {
        // given
        if ("[whitespace]".equals(strategyResult)) {
            strategyResult = "  \n\r\t  ";
        }

        if ("[whitespace]".equals(httpMethod)) {
            httpMethod = "  \n\r\t  ";
        }

        initialSpanNameFromStrategy.set(strategyResult);
        ClientRequest requestMock = mock(ClientRequest.class);
        doReturn(HttpMethod.resolve(httpMethod)).when(requestMock).method();

        // when
        String result = filterSpy.getSubspanSpanName(requestMock, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(strategyInitialSpanNameMethodCalled.get()).isTrue();
        strategyInitialSpanNameArgs.get().verifyArgs(requestMock, tagAndNamingAdapterMock);
    }

    @DataProvider(value = {
        "GET    |   GET",
        "POST   |   POST",
        "PATCH  |   PATCH",
        "null   |   UNKNOWN_HTTP_METHOD"
    }, splitBy = "\\|")
    @Test
    public void getRequestMethodAsString_works_as_expected(HttpMethod method, String expectedResult) {
        // when
        String result = filterSpy.getRequestMethodAsString(method);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void subscriberContextWithTracingInfo_works_as_expected() {
        // given
        Context origContext = Context.of("foo", "bar");
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        Context result = filterSpy.subscriberContextWithTracingInfo(origContext, tracingStateMock);

        // then
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(TracingState.class)).isSameAs(tracingStateMock);
        assertThat(result.<String>get("foo")).isEqualTo("bar");

        Context expectedMatchingContextFromUtils = WingtipsSpringWebfluxUtils.subscriberContextWithTracingInfo(
            origContext, tracingStateMock
        );
        assertThat(result.stream().collect(Collectors.toList()))
            .isEqualTo(expectedMatchingContextFromUtils.stream().collect(Collectors.toList()));
    }

    // ========== Tests for WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper inner class ============
    
    @Test
    public void WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper_constructor_sets_fields_as_expected() {
        // given
        @SuppressWarnings("unchecked")
        Mono<ClientResponse> sourceMock = mock(Mono.class);
        TracingState tracingStateMock = mock(TracingState.class);
        
        // when
        WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper impl =
            new WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper(
                sourceMock, request, tracingStateMock, tagAndNamingStrategy, tagAndNamingAdapterMock
            );

        // then
        assertThat(impl.request).isSameAs(request);
        assertThat(impl.spanAroundCallTracingState).isSameAs(tracingStateMock);
        assertThat(impl.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(impl.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper_subscribe_works_as_expected() {
        // given
        // Setup the tracing state for the subscribe call.
        Tracer.getInstance().startRequestWithRootSpan("fooRootSpan");
        Tracer.getInstance().startSubSpan("fooSubspan", SpanPurpose.LOCAL_ONLY);
        TracingState expectedTracingStateInSourceSubscribe =
            TracingState.getCurrentThreadTracingState();

        // Now that the tracing-state-for-subscribe has been setup, setup the current thread tracing state to
        //      something completely different.
        Tracer.getInstance().unregisterFromThread();
        Tracer.getInstance().startRequestWithRootSpan("someCompletelyDifferentRootSpan");
        TracingState baseTracingState = TracingState.getCurrentThreadTracingState();

        Mono<ClientResponse> sourceMock = mock(Mono.class);
        AtomicReference<TracingState> actualSourceSubscribeTracingState = new AtomicReference<>();
        doAnswer(invocation -> {
            actualSourceSubscribeTracingState.set(TracingState.getCurrentThreadTracingState());
            return null;
        }).when(sourceMock).subscribe(any(CoreSubscriber.class));

        WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper impl =
            new WingtipsExchangeFilterFunctionTracingCompletionMonoWrapper(
                sourceMock, request, expectedTracingStateInSourceSubscribe, tagAndNamingStrategy,
                tagAndNamingAdapterMock
            );

        CoreSubscriber<? super ClientResponse> actualSubscriberMock = mock(CoreSubscriber.class);
        Context actualSubscriberContextMock = mock(Context.class);
        doReturn(actualSubscriberContextMock).when(actualSubscriberMock).currentContext();

        // when
        impl.subscribe(actualSubscriberMock);

        // then
        // The source should have been subscribed to with a WingtipsExchangeFilterFunctionTracingCompletionSubscriber
        //      that contains expected values.
        ArgumentCaptor<CoreSubscriber> subscriberCaptor = ArgumentCaptor.forClass(CoreSubscriber.class);
        verify(sourceMock).subscribe(subscriberCaptor.capture());
        assertThat(subscriberCaptor.getValue())
            .isInstanceOf(WingtipsExchangeFilterFunctionTracingCompletionSubscriber.class);
        WingtipsExchangeFilterFunctionTracingCompletionSubscriber subscriberWrapper =
            (WingtipsExchangeFilterFunctionTracingCompletionSubscriber) subscriberCaptor.getValue();
        
        assertThat(subscriberWrapper.actual).isSameAs(actualSubscriberMock);
        assertThat(subscriberWrapper.request).isSameAs(request);
        assertThat(subscriberWrapper.subscriberContext).isSameAs(actualSubscriberContextMock);
        assertThat(subscriberWrapper.spanAroundCallTracingState).isSameAs(expectedTracingStateInSourceSubscribe);
        assertThat(subscriberWrapper.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(subscriberWrapper.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);

        // The tracing state at the time of subscription should be what we expect.
        assertThat(actualSourceSubscribeTracingState.get()).isEqualTo(expectedTracingStateInSourceSubscribe);

        // The base tracing state should have been restored.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);
    }

    // ========== Tests for WingtipsExchangeFilterFunctionTracingCompletionSubscriber inner class ============
    
    @Test
    public void WingtipsExchangeFilterFunctionTracingCompletionSubscriber_constructor_sets_fields_as_expected() {
        // given
        @SuppressWarnings("unchecked")
        CoreSubscriber<ClientResponse> actualSubscriberMock = mock(CoreSubscriber.class);
        Context subscriberContextMock = mock(Context.class);
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        WingtipsExchangeFilterFunctionTracingCompletionSubscriber impl =
            new WingtipsExchangeFilterFunctionTracingCompletionSubscriber(
                actualSubscriberMock, request, subscriberContextMock, tracingStateMock,
                tagAndNamingStrategy, tagAndNamingAdapterMock
            );

        // then
        assertThat(impl.actual).isSameAs(actualSubscriberMock);
        assertThat(impl.request).isSameAs(request);
        assertThat(impl.subscriberContext).isSameAs(subscriberContextMock);
        assertThat(impl.spanAroundCallTracingState).isSameAs(tracingStateMock);
        assertThat(impl.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(impl.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
    }

    private WingtipsExchangeFilterFunctionTracingCompletionSubscriber setupSubscriberWrapper() {
        Tracer.getInstance().startRequestWithRootSpan("TracingState for Subscriber wrapper root span");
        Tracer.getInstance().startSubSpan("TracingState for Subscriber wrapper subspan", SpanPurpose.LOCAL_ONLY);

        @SuppressWarnings("unchecked")
        WingtipsExchangeFilterFunctionTracingCompletionSubscriber result =
            new WingtipsExchangeFilterFunctionTracingCompletionSubscriber(
                mock(CoreSubscriber.class), request, mock(Context.class), TracingState.getCurrentThreadTracingState(),
                tagAndNamingStrategy, tagAndNamingAdapterMock
            );

        Tracer.getInstance().unregisterFromThread();

        return result;
    }

    @Test
    public void WingtipsExchangeFilterFunctionTracingCompletionSubscriber_currentContext_returns_expected_context() {
        // given
        WingtipsExchangeFilterFunctionTracingCompletionSubscriber impl = setupSubscriberWrapper();

        // when
        Context result = impl.currentContext();

        // then
        assertThat(result).isSameAs(impl.subscriberContext);
    }

    @Test
    public void WingtipsExchangeFilterFunctionTracingCompletionSubscriber_onSubscribe_wraps_Subscription_with_one_that_completes_subspan_on_cancel() {
        // given
        WingtipsExchangeFilterFunctionTracingCompletionSubscriber impl = setupSubscriberWrapper();
        Span subspanForCompletion = impl.spanAroundCallTracingState.spanStack.peek();
        assertThat(subspanForCompletion).isNotNull();

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
        assertThat(subspanForCompletion.isCompleted()).isFalse();
        wrappedSubscription.cancel();

        // then - the wrapper subscription passes cancel() calls through to the original subscription (with the
        //      correct subspan tracing state attached to the thread), and it completes the expected subspan correctly.
        verify(origSubscriptionMock).cancel();
        assertThat(tracingStateOnOrigSubscriptionCancel.get()).isEqualTo(impl.spanAroundCallTracingState);

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            subspanForCompletion, request, null, null, tagAndNamingAdapterMock
        );

        assertThat(subspanForCompletion.getTags().get("cancelled")).isEqualTo("true");
        assertThat(subspanForCompletion.getTags().get(KnownZipkinTags.ERROR)).isEqualTo("CANCELLED");

        assertThat(subspanForCompletion.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(subspanForCompletion));

        // The current thread tracing state should be unchanged after the cancel() call.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);
    }

    @Test
    public void WingtipsExchangeFilterFunctionTracingCompletionSubscriber_onNext_calls_actual_subscriber_onNext_and_completes_subspan() {
        // given
        WingtipsExchangeFilterFunctionTracingCompletionSubscriber impl = setupSubscriberWrapper();
        Span subspanForCompletion = impl.spanAroundCallTracingState.spanStack.peek();
        assertThat(subspanForCompletion).isNotNull();

        Tracer.getInstance().startRequestWithRootSpan("someRandomThreadRootSpan");
        TracingState baseTracingState = TracingState.getCurrentThreadTracingState();

        AtomicReference<TracingState> tracingStateOnActualOnNext = new AtomicReference<>();
        doAnswer(invocation -> {
            tracingStateOnActualOnNext.set(TracingState.getCurrentThreadTracingState());
            return null;
        }).when(impl.actual).onNext(any());

        assertThat(spanRecorder.completedSpans).isEmpty();
        assertThat(subspanForCompletion.isCompleted()).isFalse();

        // when
        impl.onNext(responseMock);

        // then
        verify(impl.actual).onNext(responseMock);
        assertThat(tracingStateOnActualOnNext.get()).isEqualTo(impl.spanAroundCallTracingState);

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            subspanForCompletion, request, responseMock, null, tagAndNamingAdapterMock
        );

        assertThat(subspanForCompletion.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(subspanForCompletion));

        // The current thread tracing state should be unchanged after the onNext() call.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);
    }

    @Test
    public void WingtipsExchangeFilterFunctionTracingCompletionSubscriber_onError_calls_actual_subscriber_onError_and_completes_subspan() {
        // given
        WingtipsExchangeFilterFunctionTracingCompletionSubscriber impl = setupSubscriberWrapper();
        Span subspanForCompletion = impl.spanAroundCallTracingState.spanStack.peek();
        assertThat(subspanForCompletion).isNotNull();

        Tracer.getInstance().startRequestWithRootSpan("someRandomThreadRootSpan");
        TracingState baseTracingState = TracingState.getCurrentThreadTracingState();

        AtomicReference<TracingState> tracingStateOnActualOnError = new AtomicReference<>();
        doAnswer(invocation -> {
            tracingStateOnActualOnError.set(TracingState.getCurrentThreadTracingState());
            return null;
        }).when(impl.actual).onError(any());

        Throwable errorMock = mock(Throwable.class);

        assertThat(spanRecorder.completedSpans).isEmpty();
        assertThat(subspanForCompletion.isCompleted()).isFalse();

        // when
        impl.onError(errorMock);

        // then
        verify(impl.actual).onError(errorMock);
        assertThat(tracingStateOnActualOnError.get()).isEqualTo(impl.spanAroundCallTracingState);

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            subspanForCompletion, request, null, errorMock, tagAndNamingAdapterMock
        );

        assertThat(subspanForCompletion.isCompleted()).isTrue();
        assertThat(spanRecorder.completedSpans).isEqualTo(singletonList(subspanForCompletion));

        // The current thread tracing state should be unchanged after the onError() call.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(baseTracingState);
    }

    @Test
    public void WingtipsExchangeFilterFunctionTracingCompletionSubscriber_onComplete_calls_actual_subscriber_onComplete() {
        // given
        WingtipsExchangeFilterFunctionTracingCompletionSubscriber impl = setupSubscriberWrapper();

        // when
        impl.onComplete();

        // then
        verify(impl.actual).onComplete();
    }
}