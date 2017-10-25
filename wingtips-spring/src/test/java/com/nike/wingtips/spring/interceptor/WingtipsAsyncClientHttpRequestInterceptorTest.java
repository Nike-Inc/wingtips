package com.nike.wingtips.spring.interceptor;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.spring.testutils.TestUtils.SpanRecorder;
import com.nike.wingtips.spring.util.HttpRequestWrapperWithModifiableHeaders;
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link WingtipsAsyncClientHttpRequestInterceptor}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsAsyncClientHttpRequestInterceptorTest {

    private HttpRequest httpRequest;
    private HttpMethod method;
    private URI uri;

    private byte[] body;
    private AsyncClientHttpRequestExecution executionMock;

    private SpanRecorder spanRecorder;
    private TracingState tracingStateAtTimeOfExecution;

    private SettableListenableFuture<ClientHttpResponse> executionResponseFuture;

    @Before
    public void beforeMethod() throws IOException {
        resetTracing();

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);

        method = HttpMethod.PATCH;
        uri = URI.create("http://localhost:4242/" + UUID.randomUUID().toString());
        httpRequest = new HttpRequest() {
            @Override
            public HttpHeaders getHeaders() { return new HttpHeaders(); }

            @Override
            public HttpMethod getMethod() {
                return method;
            }

            @Override
            public URI getURI() {
                return uri;
            }
        };

        body = UUID.randomUUID().toString().getBytes();
        executionMock = mock(AsyncClientHttpRequestExecution.class);
        doAnswer(invocation -> {
            tracingStateAtTimeOfExecution = TracingState.getCurrentThreadTracingState();
            executionResponseFuture = new SettableListenableFuture<>();
            return executionResponseFuture;
        }).when(executionMock).executeAsync(any(HttpRequest.class), any(byte[].class));
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
        WingtipsAsyncClientHttpRequestInterceptor interceptor = new WingtipsAsyncClientHttpRequestInterceptor(subspanOptionOn);

        // then
        assertThat(interceptor.surroundCallsWithSubspan).isEqualTo(subspanOptionOn);
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
        "true   |   true    |   NORMAL_COMPLETION",
        "true   |   false   |   NORMAL_COMPLETION",
        "false  |   true    |   NORMAL_COMPLETION",
        "false  |   false   |   NORMAL_COMPLETION",
        "true   |   true    |   EXCEPTION_THROWN",
        "true   |   false   |   EXCEPTION_THROWN",
        "false  |   true    |   EXCEPTION_THROWN",
        "false  |   false   |   EXCEPTION_THROWN",
        "true   |   true    |   FUTURE_CANCELLED",
        "true   |   false   |   FUTURE_CANCELLED",
        "false  |   true    |   FUTURE_CANCELLED",
        "false  |   false   |   FUTURE_CANCELLED"
    }, splitBy = "\\|")
    @Test
    public void intercept_works_as_expected(
        boolean currentSpanExists, boolean subspanOptionOn, ResponseFutureResult responseFutureCompletionResult
    ) throws IOException {
        // given
        WingtipsAsyncClientHttpRequestInterceptor interceptor = new WingtipsAsyncClientHttpRequestInterceptor(subspanOptionOn);
        Span rootSpan = (currentSpanExists)
                        ? Tracer.getInstance().startRequestWithRootSpan("rootSpan")
                        : null;

        // Tracing info should be propagated on the headers if a current span exists when the interceptor is called,
        //      or if the subspan option is turned on. Or to look at it from the other direction
        //      we should *not* see propagation when no current span exists and the subspan option is off.
        boolean expectTracingInfoPropagation = (currentSpanExists || subspanOptionOn);

        TracingState tracingStateBeforeInterceptorCall = TracingState.getCurrentThreadTracingState();

        // when
        ListenableFuture<ClientHttpResponse> result = interceptor.intercept(httpRequest, body, executionMock);
        assertThat(result).isSameAs(executionResponseFuture);

        // then
        // Before the response future finishes we should have zero completed spans.
        assertThat(spanRecorder.completedSpans).isEmpty();

        // The HttpRequest that was passed to the ClientHttpRequestExecution should be a
        //      HttpRequestWrapperWithModifiableHeaders to allow the headers to be modified.
        HttpRequest executedRequest = extractRequestFromExecution();
        assertThat(executedRequest).isInstanceOf(HttpRequestWrapperWithModifiableHeaders.class);
        assertThat(((HttpRequestWrapperWithModifiableHeaders)executedRequest).getRequest()).isSameAs(httpRequest);

        // The tracing headers should be set on the request based on what the tracing state was at the time of execution
        //      (which depends on whether the subspan option was on, which we'll get to later).
        Span expectedSpanForHeaders = getExpectedSpanForHeaders(expectTracingInfoPropagation,
                                                                tracingStateAtTimeOfExecution);
        verifyExpectedTracingHeaders(executedRequest, expectedSpanForHeaders);

        // Now we can complete the response future to trigger any span closing/etc that might happen.
        switch(responseFutureCompletionResult) {
            case NORMAL_COMPLETION:
                executionResponseFuture.set(mock(ClientHttpResponse.class));
                break;
            case EXCEPTION_THROWN:
                executionResponseFuture.setException(new RuntimeException("kaboom"));
                break;
            case FUTURE_CANCELLED:
                executionResponseFuture.cancel(true);
                break;
            default:
                throw new RuntimeException("Unhandled ResponseFutureResult type: " + responseFutureCompletionResult.name());
        }

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
        }
        else {
            // The subspan option was turned off, so we should *not* have any completed spans, and the tracing state
            //      at time of execution should be the same as when the interceptor was called.
            assertThat(spanRecorder.completedSpans).isEmpty();
            assertThat(tracingStateAtTimeOfExecution).isEqualTo(tracingStateBeforeInterceptorCall);

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
        WingtipsAsyncClientHttpRequestInterceptor interceptor = new WingtipsAsyncClientHttpRequestInterceptor(subspanOptionOn);
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
        Throwable ex = catchThrowable(() -> interceptor.intercept(httpRequest, body, executionMock));

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
        "true",
        "false"
    })
    @Test
    public void getSubspanSpanName_works_as_expected(boolean includeQueryString) {
        // given
        WingtipsAsyncClientHttpRequestInterceptor interceptorSpy = spy(new WingtipsAsyncClientHttpRequestInterceptor());

        HttpMethod method = HttpMethod.OPTIONS;

        String noQueryStringUri = uri.toString();
        if (includeQueryString) {
            uri = URI.create(uri.toString() + "?foo=" + UUID.randomUUID().toString());
        }

        httpRequest = mock(HttpRequest.class);
        doReturn(uri).when(httpRequest).getURI();
        doReturn(method).when(httpRequest).getMethod();

        // when
        String result = interceptorSpy.getSubspanSpanName(httpRequest);

        // then
        assertThat(result).isEqualTo("asyncresttemplate_downstream_call-" + method.name() + "_" + noQueryStringUri);
        verify(httpRequest).getURI();
    }
    
}