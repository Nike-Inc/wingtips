package com.nike.wingtips.apache.httpclient.util;

import com.nike.wingtips.Span;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static com.nike.wingtips.TraceHeaders.PARENT_SPAN_ID;
import static com.nike.wingtips.TraceHeaders.SPAN_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_SAMPLED;
import static com.nike.wingtips.http.HttpRequestTracingUtils.convertSampleableBooleanToExpectedB3Value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link WingtipsApacheHttpClientUtil}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsApacheHttpClientUtilTest {

    private HttpRequest requestMock;
    private RequestLine requestLineMock;

    @Before
    public void beforeMethod() {
        requestMock = mock(HttpRequest.class);
        requestLineMock = mock(RequestLine.class);

        doReturn(requestLineMock).when(requestMock).getRequestLine();
    }

    @Test
    public void code_coverage_hoops() {
        // jump!
        new WingtipsApacheHttpClientUtil();
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void propagateTracingHeaders_works_as_expected(
        boolean requestIsNull, boolean spanIsNull
    ) {
        // given
        if (requestIsNull)
            requestMock = null;

        Span spanSpy = (spanIsNull)
                       ? null
                       : spy(Span.newBuilder(UUID.randomUUID().toString(), Span.SpanPurpose.CLIENT)
                                 .withParentSpanId(UUID.randomUUID().toString())
                                 .build());

        // when
        WingtipsApacheHttpClientUtil.propagateTracingHeaders(requestMock, spanSpy);

        // then
        if (requestIsNull || spanIsNull) {
            if (requestMock != null)
                verifyZeroInteractions(requestMock);

            if (spanSpy != null)
                verifyZeroInteractions(spanSpy);
        }
        else {
            verify(requestMock).setHeader(TRACE_ID, spanSpy.getTraceId());
            verify(requestMock).setHeader(SPAN_ID, spanSpy.getSpanId());
            verify(requestMock)
                .setHeader(TRACE_SAMPLED, convertSampleableBooleanToExpectedB3Value(spanSpy.isSampleable()));
            verify(requestMock).setHeader(PARENT_SPAN_ID, spanSpy.getParentSpanId());
        }
    }

    // See https://github.com/openzipkin/b3-propagation - we should pass "1" if it's sampleable, "0" if it's not.
    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void propagateTracingHeaders_uses_B3_spec_for_sampleable_header_value(
        boolean sampleable
    ) {
        // given
        Span span = Span.newBuilder("foo", Span.SpanPurpose.CLIENT)
                        .withSampleable(sampleable)
                        .build();

        // when
        WingtipsApacheHttpClientUtil.propagateTracingHeaders(requestMock, span);

        // then
        verify(requestMock)
            .setHeader(TRACE_SAMPLED, convertSampleableBooleanToExpectedB3Value(span.isSampleable()));
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void propagateTracingHeaders_only_sends_parent_span_id_header_if_parent_span_id_exists(
        boolean parentSpanIdExists
    ) {
        // given
        String parentSpanId = (parentSpanIdExists) ? UUID.randomUUID().toString() : null;
        Span span = Span.newBuilder("foo", Span.SpanPurpose.CLIENT)
                        .withParentSpanId(parentSpanId)
                        .build();

        // when
        WingtipsApacheHttpClientUtil.propagateTracingHeaders(requestMock, span);

        // then
        if (parentSpanIdExists) {
            verify(requestMock).setHeader(PARENT_SPAN_ID, parentSpanId);
        }
        else {
            verify(requestMock, never()).setHeader(eq(PARENT_SPAN_ID), anyString());
        }
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
        String result = WingtipsApacheHttpClientUtil.getSubspanSpanName(requestMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void getSubspanSpanName_works_as_expected_for_HttpRequestWrapper_with_relative_path(
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
        String result = WingtipsApacheHttpClientUtil.getSubspanSpanName(reqWrapperMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

}