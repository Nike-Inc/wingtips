package com.nike.wingtips.spring.interceptor;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
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
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link WingtipsClientHttpRequestInterceptor}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsClientHttpRequestInterceptorTest {

    private HttpRequest requestMock;
    private HttpHeaders headersMock;
    private HttpMethod method;
    private URI uri;

    private byte[] body;
    private ClientHttpResponse responseMock;
    private ClientHttpRequestExecution executionMock;

    private SpanRecorder spanRecorder;
    private TracingState tracingStateAtTimeOfExecution;

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

        responseMock = mock(ClientHttpResponse.class);

        executionMock = mock(ClientHttpRequestExecution.class);
        doAnswer(invocation -> {
            tracingStateAtTimeOfExecution = TracingState.getCurrentThreadTracingState();
            return responseMock;
        }).when(executionMock).execute(any(HttpRequest.class), any(byte[].class));
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    @Test
    public void default_constructor_creates_instance_with_subspan_option_on() {
        // when
        WingtipsClientHttpRequestInterceptor interceptor = new WingtipsClientHttpRequestInterceptor();

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
        WingtipsClientHttpRequestInterceptor interceptor = new WingtipsClientHttpRequestInterceptor(subspanOptionOn);

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
        WingtipsClientHttpRequestInterceptor interceptor = new WingtipsClientHttpRequestInterceptor(
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
            () -> new WingtipsClientHttpRequestInterceptor(true, scenario.strategy, scenario.adapter)
        );

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(scenario.expectedExceptionMessage);
    }

    @Test
    public void DEFAULT_IMPL_is_instance_with_subspan_option_on() {
        // expect
        assertThat(WingtipsClientHttpRequestInterceptor.DEFAULT_IMPL.surroundCallsWithSubspan).isTrue();
    }

    private HttpRequest extractRequestFromExecution() {
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        try {
            verify(executionMock).execute(requestCaptor.capture(), eq(body));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return requestCaptor.getValue();
    }

    @DataProvider(value = {
        "true   |   true    |   true",
        "false  |   true    |   true",
        "true   |   false   |   true",
        "false  |   false   |   true",
        "true   |   true    |   false",
        "false  |   true    |   false",
        "true   |   false   |   false",
        "false  |   false   |   false"
    }, splitBy = "\\|")
    @Test
    public void intercept_works_as_expected(
        boolean currentSpanExists, boolean subspanOptionOn, boolean throwExceptionDuringExecution
    ) throws IOException {
        // given
        WingtipsClientHttpRequestInterceptor interceptor = new WingtipsClientHttpRequestInterceptor(
            subspanOptionOn, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        RuntimeException exceptionToThrowDuringExecution = (throwExceptionDuringExecution)
                                                        ? new RuntimeException("kaboom")
                                                        : null;

        if (exceptionToThrowDuringExecution != null) {
            doAnswer(invocation -> {
                tracingStateAtTimeOfExecution = TracingState.getCurrentThreadTracingState();

                throw exceptionToThrowDuringExecution;
            }).when(executionMock).execute(any(HttpRequest.class), any(byte[].class));
        }

        // expect
        execute_and_validate_intercept_worked_as_expected(
            interceptor, currentSpanExists, subspanOptionOn, exceptionToThrowDuringExecution
        );
    }

    public void execute_and_validate_intercept_worked_as_expected(
        WingtipsClientHttpRequestInterceptor interceptor,
        boolean currentSpanExists,
        boolean subspanOptionOn,
        Throwable expectedError
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
        ClientHttpResponse response = null;
        Throwable actualExFromInterceptor = null;
        try {
            response = interceptor.intercept(requestMock, body, executionMock);
        }
        catch (Throwable ex) {
            actualExFromInterceptor = ex;
        }

        // then
        if (expectedError == null) {
            assertThat(response).isNotNull();
            assertThat(actualExFromInterceptor).isNull();
        }
        else {
            assertThat(response).isNull();
            assertThat(actualExFromInterceptor).isSameAs(expectedError);
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

            assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
            assertThat(strategyResponseTaggingArgs.get()).isNotNull();
            strategyResponseTaggingArgs.get().verifyArgs(
                completedSpan, executedRequest, response, expectedError, interceptor.tagAndNamingAdapter
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
        "spanNameFromStrategy   |   PATCH           |   spanNameFromStrategy",
        "null                   |   PATCH           |   resttemplate_downstream_call-PATCH",
        "                       |   PATCH           |   resttemplate_downstream_call-PATCH",
        "[whitespace]           |   PATCH           |   resttemplate_downstream_call-PATCH",
        "null                   |   null            |   resttemplate_downstream_call-UNKNOWN_HTTP_METHOD",
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

        WingtipsClientHttpRequestInterceptor interceptor = new WingtipsClientHttpRequestInterceptor(
            true, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        // when
        String result = interceptor.getSubspanSpanName(requestMock, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

}