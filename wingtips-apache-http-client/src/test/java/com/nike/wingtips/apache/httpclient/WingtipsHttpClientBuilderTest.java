package com.nike.wingtips.apache.httpclient;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nike.wingtips.TraceHeaders.PARENT_SPAN_ID;
import static com.nike.wingtips.TraceHeaders.SPAN_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_SAMPLED;
import static com.nike.wingtips.http.HttpRequestTracingUtils.convertSampleableBooleanToExpectedB3Value;
import static org.apache.http.HttpVersion.HTTP_1_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link WingtipsHttpClientBuilder}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsHttpClientBuilderTest {

    private WingtipsHttpClientBuilder builder;

    private HttpRequest requestMock;
    private HttpResponse responseMock;
    private HttpContext httpContext;
    private RequestLine requestLineMock;

    private String method;
    private String uri;

    private SpanRecorder spanRecorder;

    @Before
    public void beforeMethod() {
        resetTracing();

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);

        builder = new WingtipsHttpClientBuilder();

        requestMock = mock(HttpRequest.class);
        responseMock = mock(HttpResponse.class);
        httpContext = new BasicHttpContext();
        requestLineMock = mock(RequestLine.class);

        method = "GET";
        uri = "http://localhost:4242/foo/bar";

        doReturn(requestLineMock).when(requestMock).getRequestLine();
        doReturn(method).when(requestLineMock).getMethod();
        doReturn(uri).when(requestLineMock).getUri();
        doReturn(HTTP_1_1).when(requestLineMock).getProtocolVersion();
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
        removeSpanRecorderLifecycleListener();
    }

    private void removeSpanRecorderLifecycleListener() {
        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            if (listener instanceof SpanRecorder) {
                Tracer.getInstance().removeSpanLifecycleListener(listener);
            }
        }
    }

    @Test
    public void default_constructor_sets_fields_as_expected() {
        // when
        WingtipsHttpClientBuilder impl = new WingtipsHttpClientBuilder();

        // then
        assertThat(impl.surroundCallsWithSubspan).isTrue();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void single_arg_constructor_sets_fields_as_expected(boolean argValue) {
        // when
        WingtipsHttpClientBuilder impl = new WingtipsHttpClientBuilder(argValue);

        // then
        assertThat(impl.surroundCallsWithSubspan).isEqualTo(argValue);
    }

    @Test
    public void zero_arg_create_factory_method_sets_fields_as_expected() {
        // when
        WingtipsHttpClientBuilder impl = WingtipsHttpClientBuilder.create();

        // then
        assertThat(impl.surroundCallsWithSubspan).isTrue();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void single_arg_create_factory_method_sets_fields_as_expected(boolean argValue) {
        // when
        WingtipsHttpClientBuilder impl = WingtipsHttpClientBuilder.create(argValue);

        // then
        assertThat(impl.surroundCallsWithSubspan).isEqualTo(argValue);
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
    public void decorateProtocolExec_works_as_expected(
        boolean subspanOptionOn, boolean parentSpanExists, boolean throwExceptionInInnerChain
    ) throws IOException, HttpException {
        // given
        builder = WingtipsHttpClientBuilder.create(subspanOptionOn);
        RuntimeException exceptionToThrowInInnerChain = (throwExceptionInInnerChain)
                                                        ? new RuntimeException("kaboom")
                                                        : null;
        SpanCapturingClientExecChain origCec = spy(new SpanCapturingClientExecChain(exceptionToThrowInInnerChain));
        Span parentSpan = null;
        if (parentSpanExists) {
            parentSpan = Tracer.getInstance().startRequestWithRootSpan("someParentSpan");
        }

        // when
        ClientExecChain result = builder.decorateProtocolExec(origCec);

        // then
        verifyDecoratedClientExecChainPerformsTracingLogic(
            result, origCec, parentSpan, subspanOptionOn
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void decorateProtocolExec_uses_subspan_option_value_at_time_of_creation_not_time_of_execution(
        boolean subspanOptionOn
    ) throws IOException, HttpException {
        // given
        builder = WingtipsHttpClientBuilder.create(subspanOptionOn);
        Span parentSpan = Tracer.getInstance().startRequestWithRootSpan("someParentSpan");

        SpanCapturingClientExecChain origCec = spy(new SpanCapturingClientExecChain());

        // when
        ClientExecChain result = builder.decorateProtocolExec(origCec);
        // Set builder's subspan option to the opposite of what it was when the ClientExecChain was decorated.
        builder.setSurroundCallsWithSubspan(!subspanOptionOn);

        // then
        // Even though the *builder's* subspan option has been flipped, the ClientExecChain should still execute with
        //      the subspan option value from when the ClientExecChain was originally decorated.
        verifyDecoratedClientExecChainPerformsTracingLogic(
            result, origCec, parentSpan, subspanOptionOn
        );
    }

    private void verifyDecoratedClientExecChainPerformsTracingLogic(
        ClientExecChain decoratedCec, SpanCapturingClientExecChain origCecSpy, Span parentSpan, boolean expectSubspan
    ) throws IOException, HttpException {
        // given
        HttpRoute httpRoute = new HttpRoute(new HttpHost("localhost"));
        HttpRequestWrapper requestWrapperSpy = spy(HttpRequestWrapper.wrap(requestMock));
        HttpClientContext httpClientContextMock = mock(HttpClientContext.class);
        HttpExecutionAware httpExecutionAwareMock = mock(HttpExecutionAware.class);

        assertThat(origCecSpy.capturedSpan).isNull();
        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        CloseableHttpResponse result = null;
        Throwable exFromChain = null;
        try {
            result = decoratedCec.execute(
                httpRoute, requestWrapperSpy, httpClientContextMock, httpExecutionAwareMock
            );
        }
        catch(Throwable ex) {
            exFromChain = ex;
        }

        // then
        verify(origCecSpy).execute(httpRoute, requestWrapperSpy, httpClientContextMock, httpExecutionAwareMock);
        if (origCecSpy.exceptionToThrow == null) {
            assertThat(result).isSameAs(origCecSpy.response);
        }
        else {
            assertThat(exFromChain).isSameAs(origCecSpy.exceptionToThrow);
        }

        // The only time the capturedSpan should be null is if expectSubspan is false and parentSpan is null, and then
        //      no tracing propagation headers should have been set.
        //      Otherwise, the tracing propagation headers should match capturedSpan.
        if (origCecSpy.capturedSpan == null) {
            assertThat(expectSubspan).isFalse();
            assertThat(parentSpan).isNull();
            verify(requestWrapperSpy, never()).setHeader(anyString(), anyString());
        }
        else {
            verify(requestWrapperSpy).setHeader(TRACE_ID, origCecSpy.capturedSpan.getTraceId());
            verify(requestWrapperSpy).setHeader(SPAN_ID, origCecSpy.capturedSpan.getSpanId());
            verify(requestWrapperSpy).setHeader(
                TRACE_SAMPLED, convertSampleableBooleanToExpectedB3Value(origCecSpy.capturedSpan.isSampleable())
            );
            if (origCecSpy.capturedSpan.getParentSpanId() == null) {
                verify(requestWrapperSpy, never()).setHeader(eq(PARENT_SPAN_ID), anyString());
            }
            else {
                verify(requestWrapperSpy).setHeader(PARENT_SPAN_ID, origCecSpy.capturedSpan.getParentSpanId());
            }
        }

        // If we have a subspan, then it should have been completed. Otherwise, no spans should have been completed.
        if (expectSubspan) {
            assertThat(spanRecorder.completedSpans).containsExactly(origCecSpy.capturedSpan);
        }
        else {
            assertThat(spanRecorder.completedSpans).isEmpty();
        }
    }

    private static class SpanCapturingClientExecChain implements ClientExecChain {

        public final CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        public final RuntimeException exceptionToThrow;
        public Span capturedSpan;

        SpanCapturingClientExecChain() {
            this(null);
        }

        SpanCapturingClientExecChain(RuntimeException exceptionToThrow) {
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public CloseableHttpResponse execute(HttpRoute route, HttpRequestWrapper request,
                                             HttpClientContext clientContext,
                                             HttpExecutionAware execAware) throws IOException, HttpException {
            capturedSpan = Tracer.getInstance().getCurrentSpan();
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return response;
        }
    }

    private static class SpanRecorder implements SpanLifecycleListener {

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

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void surroundCallsWithSubspan_getter_and_setter_work_as_expected(boolean value) {
        // when
        WingtipsHttpClientBuilder fluentResponse = builder.setSurroundCallsWithSubspan(value);

        // then
        assertThat(fluentResponse).isSameAs(builder);
        assertThat(builder.isSurroundCallsWithSubspan()).isEqualTo(value);
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void getSubspanSpanName_works_as_expected(boolean includeQueryString) {
        // given
        String method = UUID.randomUUID().toString();
        String noQueryStringUri = "http://localhost:4242/foo/bar";
        String uri = (includeQueryString)
                     ? noQueryStringUri + "?a=b&c=d"
                     : noQueryStringUri;

        doReturn(method).when(requestLineMock).getMethod();
        doReturn(uri).when(requestLineMock).getUri();

        String expectedResult = "apachehttpclient_downstream_call-" + method + "_" + noQueryStringUri;

        // when
        String result = builder.getSubspanSpanName(requestMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void getSubspanSpanNameForHttpRequest_works_as_expected_for_HttpRequestWrapper_with_relative_path(
        boolean includeQueryString
    ) {
        // given
        HttpRequestWrapper reqWrapperMock = mock(HttpRequestWrapper.class);

        String host = "http://localhost:4242";
        String method = UUID.randomUUID().toString();
        String noQueryStringRelativeUri = "/foo/bar";
        String relativeUri = (includeQueryString)
                             ? noQueryStringRelativeUri + "?a=b&c=d"
                             : noQueryStringRelativeUri;

        HttpHost httpHost = HttpHost.create(host);
        doReturn(requestLineMock).when(reqWrapperMock).getRequestLine();
        doReturn(httpHost).when(reqWrapperMock).getTarget();

        doReturn(method).when(requestLineMock).getMethod();
        doReturn(relativeUri).when(requestLineMock).getUri();

        String expectedResult = "apachehttpclient_downstream_call-" + method + "_" + host + noQueryStringRelativeUri;

        // when
        String result = builder.getSubspanSpanName(reqWrapperMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }
}