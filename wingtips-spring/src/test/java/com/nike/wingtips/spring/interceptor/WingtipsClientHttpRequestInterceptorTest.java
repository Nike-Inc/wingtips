package com.nike.wingtips.spring.interceptor;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.spring.testutils.TestUtils.SpanRecorder;
import com.nike.wingtips.spring.util.HttpRequestWrapperWithModifiableHeaders;
import com.nike.wingtips.tags.HttpTagStrategy;
import com.nike.wingtips.tags.KnownOpenTracingTags;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link WingtipsClientHttpRequestInterceptor}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsClientHttpRequestInterceptorTest {

    private HttpRequest httpRequest;
    private HttpMethod method;
    private URI uri;

    private byte[] body;
    private ClientHttpResponse response;
    private HttpStatus httpResponseStatus;
    private ClientHttpRequestExecution executionMock;

    private SpanRecorder spanRecorder;
    private TracingState tracingStateAtTimeOfExecution;

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

        response = new ClientHttpResponse() {

            @Override public InputStream getBody() throws IOException { return null; }

            @Override public HttpHeaders getHeaders() { return null; }

            @Override public HttpStatus getStatusCode() throws IOException { return httpResponseStatus; }

            @Override public int getRawStatusCode() throws IOException { return httpResponseStatus.value(); }

            @Override public String getStatusText() throws IOException { return httpResponseStatus.toString(); }

            @Override public void close() {}

        };

        executionMock = mock(ClientHttpRequestExecution.class);
        doAnswer(invocation -> {
            tracingStateAtTimeOfExecution = TracingState.getCurrentThreadTracingState();
            return response;
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
            "true   |   true   | OK",
            "true   |   true   | INTERNAL_SERVER_ERROR",
            "true   |   false  | OK",
            "true   |   false  | INTERNAL_SERVER_ERROR",
            "false  |   true   | OK",
            "false  |   true   | INTERNAL_SERVER_ERROR",
            "false  |   false  | OK",
            "false  |   false  | INTERNAL_SERVER_ERROR"
    }, splitBy = "\\|")
    @Test
    public void intercept_works_as_expected(
            boolean currentSpanExists, boolean subspanOptionOn, HttpStatus responseStatus
            ) throws IOException {
        // when
        this.httpResponseStatus = responseStatus;
        boolean tagsExpected = true;

        // given
        WingtipsClientHttpRequestInterceptor interceptor = new WingtipsClientHttpRequestInterceptor(subspanOptionOn);
        
        // then
        execute_and_validate_intercept_worked_as_expected(interceptor, currentSpanExists, subspanOptionOn, responseStatus, tagsExpected);
    }
    
    public void execute_and_validate_intercept_worked_as_expected(
            WingtipsClientHttpRequestInterceptor interceptor, boolean currentSpanExists, boolean subspanOptionOn, HttpStatus responseStatus, boolean tagsExpected
            ) throws IOException  {
        Span rootSpan = (currentSpanExists)
                ? Tracer.getInstance().startRequestWithRootSpan("rootSpan")
                        : null;

        // Tracing info should be propagated on the headers if a current span exists when the interceptor is called,
        //      or if the subspan option is turned on. Or to look at it from the other direction
        //      we should *not* see propagation when no current span exists and the subspan option is off.
        boolean expectTracingInfoPropagation = (currentSpanExists || subspanOptionOn);

        TracingState tracingStateBeforeInterceptorCall = TracingState.getCurrentThreadTracingState();

        // when
        interceptor.intercept(httpRequest, body, executionMock);

        // then
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

            if (tagsExpected) {
                // The completed span has the appropriate tags
                assertThat(completedSpan.getTags().get(KnownOpenTracingTags.HTTP_METHOD)).isEqualTo(method.toString());
                assertThat(completedSpan.getTags().get(KnownOpenTracingTags.HTTP_URL)).isEqualTo(uri.toURL().toString());
                assertThat(completedSpan.getTags().get(KnownOpenTracingTags.HTTP_STATUS)).isEqualTo(String.valueOf(httpResponseStatus.value()));
    
                //Default behavior is to mark span as err'd if response code >= 500
                if(httpResponseStatus.is5xxServerError()) {
                    assertThat(completedSpan.getTags().get(KnownOpenTracingTags.ERROR)).isEqualTo(Boolean.TRUE.toString());
                } else {
                    assertThat(completedSpan.getTags().get(KnownOpenTracingTags.ERROR)).isNull();
                }
            }
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
        WingtipsClientHttpRequestInterceptor interceptor = new WingtipsClientHttpRequestInterceptor(subspanOptionOn);
        Span rootSpan = (currentSpanExists)
                ? Tracer.getInstance().startRequestWithRootSpan("rootSpan")
                        : null;

                TracingState tracingStateBeforeInterceptorCall = TracingState.getCurrentThreadTracingState();

                RuntimeException executionExplosion = new RuntimeException("kaboom");
                doAnswer(invocation -> {
                    tracingStateAtTimeOfExecution = TracingState.getCurrentThreadTracingState();
                    throw executionExplosion;
                }).when(executionMock).execute(any(HttpRequest.class), any(byte[].class));

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

                    // Tag the span as error when execution explodes
                    assertThat(completedSpan.getTags().get(KnownOpenTracingTags.ERROR)).isEqualTo(Boolean.TRUE.toString());

                    // The request tags should be present
                    assertThat(completedSpan.getTags().get(KnownOpenTracingTags.HTTP_METHOD)).isEqualTo(method.toString());
                    assertThat(completedSpan.getTags().get(KnownOpenTracingTags.HTTP_URL)).isEqualTo(uri.toURL().toString());
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
        "OK",
        "INTERNAL_SERVER_ERROR"
    })
    @Test
    public void intercept_works_when_tagstrategy_explodes(HttpStatus responseStatus) throws IOException {
        // when
        this.httpResponseStatus = responseStatus;
        boolean tagsExpected = false; // We don't expect any tags present
        boolean subspanOptionOn = true; // Required to be true for the tag strategy to be used
        boolean currentSpanExists = false;  // We're indifferent on this value
        HttpTagStrategy<HttpRequest, ClientHttpResponse> explodingTagStrategy = mock(HttpTagStrategy.class);
        doThrow(new RuntimeException("boom")).when(explodingTagStrategy).tagSpanWithRequestAttributes(any(Span.class), any(HttpRequest.class));
        doThrow(new RuntimeException("boom")).when(explodingTagStrategy).tagSpanWithResponseAttributes(any(Span.class), any(ClientHttpResponse.class));
        doThrow(new RuntimeException("boom")).when(explodingTagStrategy).handleErroredRequest(any(Span.class), any(Throwable.class));

        // given
        WingtipsClientHttpRequestInterceptor interceptor = new WingtipsClientHttpRequestInterceptor(subspanOptionOn, explodingTagStrategy);
        
        // then
        execute_and_validate_intercept_worked_as_expected(interceptor, currentSpanExists, subspanOptionOn, responseStatus, tagsExpected);
    }

    @DataProvider(value = {
            "true",
            "false"
    })
    @Test
    public void getSubspanSpanName_works_as_expected(boolean includeQueryString) {
        // given
        WingtipsClientHttpRequestInterceptor interceptorSpy = spy(new WingtipsClientHttpRequestInterceptor());

        HttpMethod method = HttpMethod.PATCH;

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
        assertThat(result).isEqualTo("resttemplate_downstream_call-" + method.name() + "_" + noQueryStringUri);
        verify(httpRequest).getURI();
    }

}