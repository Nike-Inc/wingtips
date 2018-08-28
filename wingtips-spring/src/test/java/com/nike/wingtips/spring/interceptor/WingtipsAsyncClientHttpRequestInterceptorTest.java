package com.nike.wingtips.spring.interceptor;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.spring.interceptor.WingtipsAsyncClientHttpRequestInterceptor.SpanAroundAsyncCallFinisher;
import com.nike.wingtips.spring.interceptor.tag.SpringHttpClientTagAdapter;
import com.nike.wingtips.spring.testutils.ArgCapturingHttpTagAndSpanNamingStrategy;
import com.nike.wingtips.spring.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.InitialSpanNameArgs;
import com.nike.wingtips.spring.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.RequestTaggingArgs;
import com.nike.wingtips.spring.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.ResponseTaggingArgs;
import com.nike.wingtips.spring.testutils.TestUtils.SpanRecorder;
import com.nike.wingtips.spring.util.HttpRequestWrapperWithModifiableHeaders;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;
import com.nike.wingtips.util.TracingState;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.nike.wingtips.spring.testutils.TestUtils.getExpectedSpanForHeaders;
import static com.nike.wingtips.spring.testutils.TestUtils.normalizeTracingState;
import static com.nike.wingtips.spring.testutils.TestUtils.resetTracing;
import static com.nike.wingtips.spring.testutils.TestUtils.verifyExpectedTracingHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link WingtipsAsyncClientHttpRequestInterceptor}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsAsyncClientHttpRequestInterceptorTest {

    private HttpRequest requestMock;
    private HttpHeaders headersMock;
    private HttpMethod method;
    private URI uri;

    private ClientHttpResponse normalCompletionResponse;
    private int normalResponseCode;

    private byte[] body;
    private AsyncClientHttpRequestExecution executionMock;

    private SpanRecorder spanRecorder;
    private TracingState tracingStateAtTimeOfExecution;

    private SettableListenableFuture<ClientHttpResponse> executionResponseFuture;

    private HttpTagAndSpanNamingStrategy<HttpRequest, ClientHttpResponse> tagAndNamingStrategy;
    private HttpTagAndSpanNamingAdapter<HttpRequest, ClientHttpResponse> tagAndNamingAdapterMock;
    private AtomicReference<String> initialSpanNameFromStrategy;
    private AtomicBoolean strategyInitialSpanNameMethodCalled;
    private AtomicBoolean strategyRequestTaggingMethodCalled;
    private AtomicBoolean strategyResponseTaggingAndFinalSpanNameMethodCalled;
    private AtomicReference<InitialSpanNameArgs> strategyInitialSpanNameArgs;
    private AtomicReference<RequestTaggingArgs> strategyRequestTaggingArgs;
    private AtomicReference<ResponseTaggingArgs> strategyResponseTaggingArgs;

    @Before
    public void beforeMethod() throws IOException {
        resetTracing();

        initialSpanNameFromStrategy = new AtomicReference<>("span-name-from-strategy-" + UUID.randomUUID().toString());
        strategyInitialSpanNameMethodCalled = new AtomicBoolean(false);
        strategyRequestTaggingMethodCalled = new AtomicBoolean(false);
        strategyResponseTaggingAndFinalSpanNameMethodCalled = new AtomicBoolean(false);
        strategyInitialSpanNameArgs = new AtomicReference<>(null);
        strategyRequestTaggingArgs = new AtomicReference<>(null);
        strategyResponseTaggingArgs = new AtomicReference<>(null);
        tagAndNamingStrategy = new ArgCapturingHttpTagAndSpanNamingStrategy(
            initialSpanNameFromStrategy, strategyInitialSpanNameMethodCalled, strategyRequestTaggingMethodCalled,
            strategyResponseTaggingAndFinalSpanNameMethodCalled, strategyInitialSpanNameArgs,
            strategyRequestTaggingArgs, strategyResponseTaggingArgs
        );
        tagAndNamingAdapterMock = mock(HttpTagAndSpanNamingAdapter.class);

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);

        method = HttpMethod.PATCH;
        uri = URI.create("http://localhost:4242/" + UUID.randomUUID().toString());
        headersMock = mock(HttpHeaders.class);
        requestMock = mock(HttpRequest.class);
        doReturn(headersMock).when(requestMock).getHeaders();
        doReturn(method).when(requestMock).getMethod();
        doReturn(uri).when(requestMock).getURI();

        body = UUID.randomUUID().toString().getBytes();
        executionMock = mock(AsyncClientHttpRequestExecution.class);
        doAnswer(invocation -> {
            tracingStateAtTimeOfExecution = TracingState.getCurrentThreadTracingState();
            executionResponseFuture = new SettableListenableFuture<>();
            return executionResponseFuture;
        }).when(executionMock).executeAsync(any(HttpRequest.class), any(byte[].class));

        normalCompletionResponse = mock(ClientHttpResponse.class);
        normalResponseCode = 200; //Normal
        doReturn(normalResponseCode).when(normalCompletionResponse).getRawStatusCode();
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    @Test
    public void default_constructor_creates_instance_with_subspan_option_on() {
        // when
        WingtipsAsyncClientHttpRequestInterceptor interceptor = new WingtipsAsyncClientHttpRequestInterceptor();

        // then
        assertThat(interceptor.surroundCallsWithSubspan).isTrue();
        assertThat(interceptor.tagAndNamingStrategy).isSameAs(ZipkinHttpTagStrategy.getDefaultInstance());
        assertThat(interceptor.tagAndNamingAdapter).isSameAs(SpringHttpClientTagAdapter.getDefaultInstance());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void single_arg_constructor_creates_instance_with_subspan_option_set_to_desired_value(
        boolean subspanOptionOn
    ) {
        // when
        WingtipsAsyncClientHttpRequestInterceptor interceptor =
            new WingtipsAsyncClientHttpRequestInterceptor(subspanOptionOn);

        // then
        assertThat(interceptor.surroundCallsWithSubspan).isEqualTo(subspanOptionOn);
        assertThat(interceptor.tagAndNamingStrategy).isSameAs(ZipkinHttpTagStrategy.getDefaultInstance());
        assertThat(interceptor.tagAndNamingAdapter).isSameAs(SpringHttpClientTagAdapter.getDefaultInstance());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void constructor_with_tag_and_span_naming_args_sets_fields_as_expected(boolean subspanOptionOn) {
        // when
        WingtipsAsyncClientHttpRequestInterceptor interceptor = new WingtipsAsyncClientHttpRequestInterceptor(
            subspanOptionOn, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        // then
        assertThat(interceptor.surroundCallsWithSubspan).isEqualTo(subspanOptionOn);
        assertThat(interceptor.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(interceptor.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
    }

    private enum NullConstructorArgsScenario {
        NULL_STRATEGY_ARG(
            null,
            mock(HttpTagAndSpanNamingAdapter.class),
            "tagAndNamingStrategy cannot be null - if you really want no strategy, use NoOpHttpTagStrategy"
        ),
        NULL_ADAPTER_ARG(
            mock(HttpTagAndSpanNamingStrategy.class),
            null,
            "tagAndNamingAdapter cannot be null - if you really want no adapter, use NoOpHttpTagAdapter"
        );

        public final HttpTagAndSpanNamingStrategy<HttpRequest, ClientHttpResponse> strategy;
        public final HttpTagAndSpanNamingAdapter<HttpRequest, ClientHttpResponse> adapter;
        public final String expectedExceptionMessage;

        NullConstructorArgsScenario(
            HttpTagAndSpanNamingStrategy<HttpRequest, ClientHttpResponse> strategy,
            HttpTagAndSpanNamingAdapter<HttpRequest, ClientHttpResponse> adapter,
            String expectedExceptionMessage
        ) {
            this.strategy = strategy;
            this.adapter = adapter;
            this.expectedExceptionMessage = expectedExceptionMessage;
        }
    }

    @DataProvider(value = {
        "NULL_STRATEGY_ARG",
        "NULL_ADAPTER_ARG"
    })
    @Test
    public void constructor_with_tag_and_span_naming_args_throws_IllegalArgumentException_if_passed_null_args(
        NullConstructorArgsScenario scenario
    ) {
        // when
        Throwable ex = catchThrowable(
            () -> new WingtipsAsyncClientHttpRequestInterceptor(true, scenario.strategy, scenario.adapter)
        );

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(scenario.expectedExceptionMessage);
    }

    @Test
    public void DEFAULT_IMPL_is_instance_with_subspan_option_on() {
        // expect
        assertThat(WingtipsAsyncClientHttpRequestInterceptor.DEFAULT_IMPL.surroundCallsWithSubspan).isTrue();
    }

    private HttpRequest extractRequestFromExecution() {
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        try {
            verify(executionMock).executeAsync(requestCaptor.capture(), eq(body));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return requestCaptor.getValue();
    }

    private enum ResponseFutureResult {
        NORMAL_COMPLETION,
        EXCEPTION_THROWN,
        FUTURE_CANCELLED
    }
    
    @DataProvider(value = {
        "true   |   true    |   NORMAL_COMPLETION   |   false",
        "true   |   false   |   NORMAL_COMPLETION   |   false",
        "false  |   true    |   NORMAL_COMPLETION   |   false",
        "false  |   false   |   NORMAL_COMPLETION   |   false",
        "true   |   true    |   EXCEPTION_THROWN    |   false",
        "true   |   false   |   EXCEPTION_THROWN    |   false",
        "false  |   true    |   EXCEPTION_THROWN    |   false",
        "false  |   false   |   EXCEPTION_THROWN    |   false",
        "true   |   true    |   FUTURE_CANCELLED    |   false",
        "true   |   false   |   FUTURE_CANCELLED    |   false",
        "false  |   true    |   FUTURE_CANCELLED    |   false",
        "false  |   false   |   FUTURE_CANCELLED    |   false",
        "true   |   true    |   FUTURE_CANCELLED    |   false",
        "true   |   false   |   FUTURE_CANCELLED    |   false",
        "false  |   true    |   FUTURE_CANCELLED    |   false",
        "false  |   false   |   FUTURE_CANCELLED    |   false",
        "true   |   true    |   null                |   true",
        "true   |   false   |   null                |   true",
        "false  |   true    |   null                |   true",
        "false  |   false   |   null                |   true"
    }, splitBy = "\\|")
    @Test
    public void expected_successful_execution(
        boolean currentSpanExists,
        boolean subspanOptionOn,
        ResponseFutureResult responseFutureCompletionResult,
        boolean throwExceptionWhenExecuteAsyncCalled
    ) throws IOException {
        // given
        WingtipsAsyncClientHttpRequestInterceptor defaultInterceptor = new WingtipsAsyncClientHttpRequestInterceptor(
            subspanOptionOn, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        Function<SettableListenableFuture<ClientHttpResponse>, Pair<ClientHttpResponse, Throwable>>
            responseFutureFinisher = null;

        if (responseFutureCompletionResult != null) {
            switch (responseFutureCompletionResult) {
                case NORMAL_COMPLETION:
                    responseFutureFinisher = future -> {
                        future.set(normalCompletionResponse);
                        return Pair.of(normalCompletionResponse, null);
                    };
                    break;
                case EXCEPTION_THROWN:
                    responseFutureFinisher = future -> {
                        Throwable error = new RuntimeException("kaboom");
                        future.setException(error);
                        return Pair.of(null, error);
                    };
                    break;
                case FUTURE_CANCELLED:
                    responseFutureFinisher = future -> {
                        future.cancel(true);
                        Throwable cancellationError = catchThrowable(future::get);
                        assertThat(cancellationError).isInstanceOf(CancellationException.class);
                        return Pair.of(null, cancellationError);
                    };
                    break;
                default:
                    throw new RuntimeException(
                        "Unhandled ResponseFutureResult type: " + responseFutureCompletionResult.name());
            }
        }

        Throwable executeAsyncException = (throwExceptionWhenExecuteAsyncCalled)
                                          ? new RuntimeException("Intentional exception during executeAsync()")
                                          : null;
        if (executeAsyncException != null) {
            doAnswer(invocation -> {
                tracingStateAtTimeOfExecution = TracingState.getCurrentThreadTracingState();
                throw executeAsyncException;
            }).when(executionMock).executeAsync(any(HttpRequest.class), any(byte[].class));
        }

        intercept_worked_as_expected(
            defaultInterceptor, currentSpanExists, subspanOptionOn, responseFutureFinisher, executeAsyncException
        );
    }

    public void intercept_worked_as_expected(
        WingtipsAsyncClientHttpRequestInterceptor interceptor,
        boolean currentSpanExists,
        boolean subspanOptionOn,
        Function<SettableListenableFuture<ClientHttpResponse>, Pair<ClientHttpResponse, Throwable>> responseFutureFinisher,
        Throwable expectedAsyncExecutionError
    ) {

        Span rootSpan = (currentSpanExists)
                        ? Tracer.getInstance().startRequestWithRootSpan("rootSpan")
                        : null;

        // Tracing info should be propagated on the headers if a current span exists when the interceptor is called,
        //      or if the subspan option is turned on. Or to look at it from the other direction
        //      we should *not* see propagation when no current span exists and the subspan option is off.
        boolean expectTracingInfoPropagation = (currentSpanExists || subspanOptionOn);

        TracingState tracingStateBeforeInterceptorCall = TracingState.getCurrentThreadTracingState();

        // when
        ListenableFuture<ClientHttpResponse> result = null;
        Throwable actualExFromInterceptor = null;
        try {
            result = interceptor.intercept(requestMock, body, executionMock);
        }
        catch (Throwable ex) {
            actualExFromInterceptor = ex;
        }

        // then
        if (expectedAsyncExecutionError == null) {
            assertThat(result).isSameAs(executionResponseFuture);
            assertThat(actualExFromInterceptor).isNull();
        }
        else {
            assertThat(result).isNull();
            assertThat(actualExFromInterceptor).isSameAs(expectedAsyncExecutionError);
        }

        // Before the response future finishes we should have zero completed spans, unless executeAsync() threw an
        //      error, in which case any subspan should be completed now.
        if (expectedAsyncExecutionError != null && subspanOptionOn) {
            assertThat(spanRecorder.completedSpans).hasSize(1);
        }
        else {
            assertThat(spanRecorder.completedSpans).isEmpty();
        }

        // The HttpRequest that was passed to the ClientHttpRequestExecution should be a
        //      HttpRequestWrapperWithModifiableHeaders to allow the headers to be modified.
        HttpRequest executedRequest = extractRequestFromExecution();
        assertThat(executedRequest).isInstanceOf(HttpRequestWrapperWithModifiableHeaders.class);
        assertThat(((HttpRequestWrapperWithModifiableHeaders) executedRequest).getRequest()).isSameAs(requestMock);

        // The tracing headers should be set on the request based on what the tracing state was at the time of execution
        //      (which depends on whether the subspan option was on, which we'll get to later).
        Span expectedSpanForHeaders = getExpectedSpanForHeaders(
            expectTracingInfoPropagation,
            tracingStateAtTimeOfExecution
        );
        verifyExpectedTracingHeaders(executedRequest, expectedSpanForHeaders);

        // Now we can complete the response future to trigger any span closing/etc that might happen, and retrieve
        //      the expected response (which may be null, depending on how the future was finished).
        //      If expectedAsyncExecutionError is not null, then the future was never created, so we put in a stand-in
        //      to expect null response and expectedAsyncExecutionError as the error.
        Pair<ClientHttpResponse, Throwable> expectedResult =
            (expectedAsyncExecutionError == null)
            ? responseFutureFinisher.apply(executionResponseFuture)
            : Pair.of(null, expectedAsyncExecutionError);

        if (subspanOptionOn) {
            // The subspan option was on so we should have a span that was completed.
            assertThat(spanRecorder.completedSpans).hasSize(1);
            Span completedSpan = spanRecorder.completedSpans.get(0);

            if (currentSpanExists) {
                // A span was already on the stack when the interceptor was called, so the completed span should be
                //      a subspan of the root span.
                assertThat(completedSpan.getTraceId()).isEqualTo(rootSpan.getTraceId());
                assertThat(completedSpan.getParentSpanId()).isEqualTo(rootSpan.getSpanId());
            }
            else {
                // There was no span on the stack when the interceptor was called, so the completed span should itself
                //      be a root span.
                assertThat(completedSpan.getParentSpanId()).isNull();
            }

            // The completed span should have been the one that was used when propagating tracing headers.
            assertThat(completedSpan).isEqualTo(expectedSpanForHeaders);

            // The completed span should have been a CLIENT span.
            assertThat(completedSpan.getSpanPurpose()).isEqualTo(SpanPurpose.CLIENT);

            // The completed span should have a name from getSubspanSpanName().
            assertThat(completedSpan.getSpanName()).isEqualTo(initialSpanNameFromStrategy.get());

            // Verify that the span name and tagging strategy was called as expected for request and response tagging
            //      and span naming.
            assertThat(strategyInitialSpanNameMethodCalled.get()).isTrue();
            assertThat(strategyInitialSpanNameArgs.get()).isNotNull();
            strategyInitialSpanNameArgs.get().verifyArgs(executedRequest, interceptor.tagAndNamingAdapter);

            assertThat(strategyRequestTaggingMethodCalled.get()).isTrue();
            assertThat(strategyRequestTaggingArgs.get()).isNotNull();
            strategyRequestTaggingArgs.get().verifyArgs(
                completedSpan, executedRequest, interceptor.tagAndNamingAdapter
            );

            ClientHttpResponse expectedResponseForTagging = expectedResult.getLeft();
            Throwable expectedErrorForTagging = expectedResult.getRight();

            assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
            assertThat(strategyResponseTaggingArgs.get()).isNotNull();
            strategyResponseTaggingArgs.get().verifyArgs(
                completedSpan, executedRequest, expectedResponseForTagging, expectedErrorForTagging,
                interceptor.tagAndNamingAdapter
            );
        }
        else {
            // The subspan option was turned off, so we should *not* have any completed spans, and the tracing state
            //      at time of execution should be the same as when the interceptor was called.
            assertThat(spanRecorder.completedSpans).isEmpty();
            assertThat(tracingStateAtTimeOfExecution).isEqualTo(tracingStateBeforeInterceptorCall);

            // Verify that tag and span naming strategy was not called.
            assertThat(strategyInitialSpanNameMethodCalled.get()).isFalse();
            assertThat(strategyInitialSpanNameArgs.get()).isNull();

            assertThat(strategyRequestTaggingMethodCalled.get()).isFalse();
            assertThat(strategyRequestTaggingArgs.get()).isNull();

            assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isFalse();
            assertThat(strategyResponseTaggingArgs.get()).isNull();
            
            // We already verified that the propagation headers were added (or not) as appropriate depending on
            //      whether the tracingStateAtTimeOfExecution had a span (or not). So we'll do one last explicit
            //      verification that tracingStateAtTimeOfExecution is populated (or not) based on whether a current
            //      span existed when the interceptor was called.
            if (currentSpanExists) {
                assertThat(tracingStateAtTimeOfExecution.spanStack).isNotEmpty();
            }
            else {
                assertThat(tracingStateAtTimeOfExecution.spanStack).isNullOrEmpty();
            }
        }

        // After all is said and done, we should end up with the same tracing state as when we called the interceptor.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(normalizeTracingState(tracingStateBeforeInterceptorCall));
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void intercept_handles_span_closing_logic_even_if_execution_explodes(
        boolean currentSpanExists, boolean subspanOptionOn
    ) throws IOException {
        // given
        WingtipsAsyncClientHttpRequestInterceptor interceptor = new WingtipsAsyncClientHttpRequestInterceptor(
            subspanOptionOn, tagAndNamingStrategy, tagAndNamingAdapterMock
        );
        Span rootSpan = (currentSpanExists)
                        ? Tracer.getInstance().startRequestWithRootSpan("rootSpan")
                        : null;

        TracingState tracingStateBeforeInterceptorCall = TracingState.getCurrentThreadTracingState();

        RuntimeException executionExplosion = new RuntimeException("kaboom");
        doAnswer(invocation -> {
            tracingStateAtTimeOfExecution = TracingState.getCurrentThreadTracingState();
            throw executionExplosion;
        }).when(executionMock).executeAsync(any(HttpRequest.class), any(byte[].class));

        // when
        Throwable ex = catchThrowable(() -> interceptor.intercept(requestMock, body, executionMock));

        // then
        assertThat(ex).isSameAs(executionExplosion);

        if (subspanOptionOn) {
            // The subspan option was on so we should have a span that was completed.
            assertThat(spanRecorder.completedSpans).hasSize(1);
            Span completedSpan = spanRecorder.completedSpans.get(0);

            if (currentSpanExists) {
                // A span was already on the stack when the interceptor was called, so the completed span should be
                //      a subspan of the root span.
                assertThat(completedSpan.getTraceId()).isEqualTo(rootSpan.getTraceId());
                assertThat(completedSpan.getParentSpanId()).isEqualTo(rootSpan.getSpanId());
            }
            else {
                // There was no span on the stack when the interceptor was called, so the completed span should itself
                //      be a root span.
                assertThat(completedSpan.getParentSpanId()).isNull();
            }

            // The completed span should have been a CLIENT span.
            assertThat(completedSpan.getSpanPurpose()).isEqualTo(SpanPurpose.CLIENT);
        }
        else {
            // The subspan option was turned off, so we should *not* have any completed spans, and the tracing state
            //      at time of execution should be the same as when the interceptor was called.
            assertThat(spanRecorder.completedSpans).isEmpty();
            assertThat(tracingStateAtTimeOfExecution).isEqualTo(tracingStateBeforeInterceptorCall);
        }

        // After all is said and done, we should end up with the same tracing state as when we called the interceptor.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(normalizeTracingState(tracingStateBeforeInterceptorCall));
    }

    @DataProvider(value = {
        "spanNameFromStrategy   |   PATCH           |   spanNameFromStrategy",
        "null                   |   PATCH           |   asyncresttemplate_downstream_call-PATCH",
        "                       |   PATCH           |   asyncresttemplate_downstream_call-PATCH",
        "[whitespace]           |   PATCH           |   asyncresttemplate_downstream_call-PATCH",
        "null                   |   null            |   asyncresttemplate_downstream_call-UNKNOWN_HTTP_METHOD",
    }, splitBy = "\\|")
    @Test
    public void getSubspanSpanName_works_as_expected(
        String strategyResult, HttpMethod httpMethod, String expectedResult
    ) {
        // given
        if ("[whitespace]".equals(strategyResult)) {
            strategyResult = "  \n\r\t  ";
        }

        initialSpanNameFromStrategy.set(strategyResult);
        doReturn(httpMethod).when(requestMock).getMethod();

        WingtipsAsyncClientHttpRequestInterceptor interceptor = new WingtipsAsyncClientHttpRequestInterceptor(
            true, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        // when
        String result = interceptor.getSubspanSpanName(requestMock, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    // Unlikely to happen in practice, but let's test it anyway.
    @Test
    public void createAsyncSubSpanAndExecute_trigger_null_subspanFinisher_in_catch_block_branch_for_code_coverage() {
        // given
        Tracer.getInstance().startRequestWithRootSpan("someRootSpan");
        TracingState tracingStateBeforeInterceptorCall = TracingState.getCurrentThreadTracingState();

        WingtipsAsyncClientHttpRequestInterceptor interceptorSpy = spy(new WingtipsAsyncClientHttpRequestInterceptor(
            true, tagAndNamingStrategy, tagAndNamingAdapterMock
        ));

        RuntimeException explodingSubspanNameMethodEx =
            new RuntimeException("Intentional exception thrown by getSubspanSpanName()");

        doThrow(explodingSubspanNameMethodEx).when(interceptorSpy).getSubspanSpanName(
            any(HttpRequest.class), any(HttpTagAndSpanNamingStrategy.class), any(HttpTagAndSpanNamingAdapter.class)
        );

        HttpRequestWrapperWithModifiableHeaders wrapperRequest =
            new HttpRequestWrapperWithModifiableHeaders(requestMock);
        byte[] body = new byte[]{42};


        // when
        Throwable ex = catchThrowable(
            () -> interceptorSpy.createAsyncSubSpanAndExecute(wrapperRequest, body, executionMock)
        );

        // then
        assertThat(ex).isSameAs(explodingSubspanNameMethodEx);
        verify(interceptorSpy).getSubspanSpanName(wrapperRequest, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // TracingState should have been reset even though an exception occurred in some unexpected place.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(normalizeTracingState(tracingStateBeforeInterceptorCall));
    }

    // Another one that's unlikely to happen in practice, but let's test it anyway.
    @Test
    public void SpanAroundAsyncCallFinisher_finishCallSpan_does_nothing_if_spanAroundCallTracingState_is_null() {
        // given
        SpanAroundAsyncCallFinisher finisherSpy = spy(new SpanAroundAsyncCallFinisher(
            null, requestMock, tagAndNamingStrategy, tagAndNamingAdapterMock
        ));

        ClientHttpResponse responseMock = mock(ClientHttpResponse.class);
        Throwable errorMock = mock(Throwable.class);

        // when
        finisherSpy.finishCallSpan(responseMock, errorMock);

        // then
        verify(finisherSpy).finishCallSpan(responseMock, errorMock);
        verifyNoMoreInteractions(finisherSpy);
        verifyZeroInteractions(responseMock, errorMock);
    }
}