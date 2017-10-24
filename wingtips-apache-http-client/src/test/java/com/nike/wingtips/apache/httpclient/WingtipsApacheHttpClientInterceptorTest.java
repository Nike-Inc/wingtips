package com.nike.wingtips.apache.httpclient;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static com.nike.wingtips.TraceHeaders.PARENT_SPAN_ID;
import static com.nike.wingtips.TraceHeaders.SPAN_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_SAMPLED;
import static com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor.DEFAULT_REQUEST_IMPL;
import static com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor.DEFAULT_RESPONSE_IMPL;
import static com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor.SPAN_TO_CLOSE_HTTP_CONTEXT_ATTR_KEY;
import static com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor.addTracingInterceptors;
import static com.nike.wingtips.http.HttpRequestTracingUtils.convertSampleableBooleanToExpectedB3Value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link WingtipsApacheHttpClientInterceptor}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsApacheHttpClientInterceptorTest {

    private WingtipsApacheHttpClientInterceptor interceptor;

    private HttpRequest requestMock;
    private HttpResponse responseMock;
    private HttpContext httpContext;
    private RequestLine requestLineMock;

    private String method;
    private String uri;

    @Before
    public void beforeMethod() {
        resetTracing();

        interceptor = new WingtipsApacheHttpClientInterceptor();

        requestMock = mock(HttpRequest.class);
        responseMock = mock(HttpResponse.class);
        httpContext = new BasicHttpContext();
        requestLineMock = mock(RequestLine.class);

        method = "GET";
        uri = "http://localhost:4242/foo/bar";

        doReturn(requestLineMock).when(requestMock).getRequestLine();
        doReturn(method).when(requestLineMock).getMethod();
        doReturn(uri).when(requestLineMock).getUri();
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
    }

    @Test
    public void default_constructor_sets_fields_as_expected() {
        // when
        WingtipsApacheHttpClientInterceptor impl = new WingtipsApacheHttpClientInterceptor();

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
        WingtipsApacheHttpClientInterceptor impl = new WingtipsApacheHttpClientInterceptor(argValue);

        // then
        assertThat(impl.surroundCallsWithSubspan).isEqualTo(argValue);
    }

    @DataProvider(value = {
        "true   |   true",
        "false  |   true",
        "true   |   false",
        "false  |   false",
    }, splitBy = "\\|")
    @Test
    public void process_request_works_as_expected(
        boolean subspanOptionOn, boolean parentSpanExists
    ) throws IOException, HttpException {

        // given
        WingtipsApacheHttpClientInterceptor interceptor = new WingtipsApacheHttpClientInterceptor(subspanOptionOn);
        Span parentSpan = null;
        if (parentSpanExists) {
            parentSpan = Tracer.getInstance().startRequestWithRootSpan("someParentSpan");
        }

        // when
        interceptor.process(requestMock, httpContext);

        // then
        Span spanSetOnHttpContext = null;
        if (subspanOptionOn) {
            spanSetOnHttpContext = (Span) httpContext.getAttribute(SPAN_TO_CLOSE_HTTP_CONTEXT_ATTR_KEY);
            assertThat(spanSetOnHttpContext).isNotNull();

            if (parentSpanExists) {
                assertThat(spanSetOnHttpContext.getTraceId()).isEqualTo(parentSpan.getTraceId());
                assertThat(spanSetOnHttpContext.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
            }

            assertThat(spanSetOnHttpContext.getSpanPurpose()).isEqualTo(SpanPurpose.CLIENT);
            assertThat(spanSetOnHttpContext.getSpanName()).isEqualTo(interceptor.getSubspanSpanName(requestMock));
        }

        Span expectedSpanToPropagate = (subspanOptionOn)
                                       ? spanSetOnHttpContext
                                       : (parentSpanExists) ? parentSpan : null;

        if (expectedSpanToPropagate == null) {
            verify(requestMock, never()).setHeader(anyString(), anyString());
        }
        else {
            verify(requestMock).setHeader(TRACE_ID, expectedSpanToPropagate.getTraceId());
            verify(requestMock).setHeader(SPAN_ID, expectedSpanToPropagate.getSpanId());
            verify(requestMock).setHeader(
                TRACE_SAMPLED, convertSampleableBooleanToExpectedB3Value(expectedSpanToPropagate.isSampleable())
            );
            if (expectedSpanToPropagate.getParentSpanId() == null) {
                verify(requestMock, never()).setHeader(eq(PARENT_SPAN_ID), anyString());
            }
            else {
                verify(requestMock).setHeader(PARENT_SPAN_ID, expectedSpanToPropagate.getParentSpanId());
            }
        }
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void process_response_works_as_expected(
        boolean subspanIsAvailableInHttpContext
    ) throws IOException, HttpException {

        // given
        Span spanMock = null;
        if (subspanIsAvailableInHttpContext){
            spanMock = mock(Span.class);
            httpContext.setAttribute(SPAN_TO_CLOSE_HTTP_CONTEXT_ATTR_KEY, spanMock);
        }

        // when
        interceptor.process(responseMock, httpContext);

        // then
        if (subspanIsAvailableInHttpContext) {
            verify(spanMock).close();
        }

        verifyZeroInteractions(responseMock);
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
        String result = interceptor.getSubspanSpanName(requestMock);

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
        String result = interceptor.getSubspanSpanName(reqWrapperMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void addTracingInterceptors_single_arg_works_as_expected() {
        // given
        HttpClientBuilder builder = HttpClientBuilder.create();

        // when
        addTracingInterceptors(builder);

        // then
        HttpClientBuilderInterceptors builderInterceptors = new HttpClientBuilderInterceptors(builder);
        assertThat(builderInterceptors.firstRequestInterceptors).containsExactly(DEFAULT_REQUEST_IMPL);
        assertThat(builderInterceptors.lastRequestInterceptors).isNullOrEmpty();
        assertThat(builderInterceptors.firstResponseInterceptors).isNullOrEmpty();
        assertThat(builderInterceptors.lastResponseInterceptors).containsExactly(DEFAULT_RESPONSE_IMPL);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void addTracingInterceptors_double_arg_works_as_expected(boolean subspanOptionOn) {
        // given
        HttpClientBuilder builder = HttpClientBuilder.create();

        // when
        addTracingInterceptors(builder, subspanOptionOn);

        // then
        HttpClientBuilderInterceptors builderInterceptors = new HttpClientBuilderInterceptors(builder);

        assertThat(builderInterceptors.firstRequestInterceptors).hasSize(1);
        assertThat(builderInterceptors.lastResponseInterceptors).hasSize(1);

        HttpRequestInterceptor requestInterceptor = builderInterceptors.firstRequestInterceptors.get(0);
        HttpResponseInterceptor responseInterceptor = builderInterceptors.lastResponseInterceptors.get(0);
        assertThat(requestInterceptor).isInstanceOf(WingtipsApacheHttpClientInterceptor.class);
        assertThat(responseInterceptor).isInstanceOf(WingtipsApacheHttpClientInterceptor.class);

        assertThat(((WingtipsApacheHttpClientInterceptor)requestInterceptor).surroundCallsWithSubspan)
            .isEqualTo(subspanOptionOn);
        assertThat(((WingtipsApacheHttpClientInterceptor)responseInterceptor).surroundCallsWithSubspan)
            .isEqualTo(subspanOptionOn);

        assertThat(builderInterceptors.lastRequestInterceptors).isNullOrEmpty();
        assertThat(builderInterceptors.firstResponseInterceptors).isNullOrEmpty();

        if (subspanOptionOn) {
            assertThat(builderInterceptors.firstRequestInterceptors).containsExactly(DEFAULT_REQUEST_IMPL);
            assertThat(builderInterceptors.lastResponseInterceptors).containsExactly(DEFAULT_RESPONSE_IMPL);
        }
    }

    private static class HttpClientBuilderInterceptors {
        public final List<HttpRequestInterceptor> firstRequestInterceptors;
        public final List<HttpRequestInterceptor> lastRequestInterceptors;
        public final List<HttpResponseInterceptor> firstResponseInterceptors;
        public final List<HttpResponseInterceptor> lastResponseInterceptors;

        public HttpClientBuilderInterceptors(HttpClientBuilder builder) {
            this.firstRequestInterceptors =
                (List<HttpRequestInterceptor>) Whitebox.getInternalState(builder, "requestFirst");
            this.lastRequestInterceptors =
                (List<HttpRequestInterceptor>) Whitebox.getInternalState(builder, "requestLast");
            this.firstResponseInterceptors =
                (List<HttpResponseInterceptor>) Whitebox.getInternalState(builder, "responseFirst");
            this.lastResponseInterceptors =
                (List<HttpResponseInterceptor>) Whitebox.getInternalState(builder, "responseLast");
        }
    }

}