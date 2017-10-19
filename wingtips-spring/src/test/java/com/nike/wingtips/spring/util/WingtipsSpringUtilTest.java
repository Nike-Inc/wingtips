package com.nike.wingtips.spring.util;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.spring.interceptor.WingtipsAsyncClientHttpRequestInterceptor;
import com.nike.wingtips.spring.interceptor.WingtipsClientHttpRequestInterceptor;
import com.nike.wingtips.spring.util.asynchelperwrapper.FailureCallbackWithTracing;
import com.nike.wingtips.spring.util.asynchelperwrapper.ListenableFutureCallbackWithTracing;
import com.nike.wingtips.spring.util.asynchelperwrapper.SuccessCallbackWithTracing;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMessage;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SuccessCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.nike.wingtips.TraceHeaders.PARENT_SPAN_ID;
import static com.nike.wingtips.TraceHeaders.SPAN_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_SAMPLED;
import static com.nike.wingtips.spring.testutils.TestUtils.convertSampleableBooleanToExpectedB3Value;
import static com.nike.wingtips.spring.util.WingtipsSpringUtil.failureCallbackWithTracing;
import static com.nike.wingtips.spring.util.WingtipsSpringUtil.listenableFutureCallbackWithTracing;
import static com.nike.wingtips.spring.util.WingtipsSpringUtil.successCallbackWithTracing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link WingtipsSpringUtil}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsSpringUtilTest {

    private HttpMessage httpMessageMock;
    private HttpHeaders headersMock;

    private SuccessCallback successCallbackMock;
    private FailureCallback failureCallbackMock;
    private ListenableFutureCallback listenableFutureCallbackMock;

    @Before
    public void beforeMethod() {
        resetTracing();

        httpMessageMock = mock(HttpMessage.class);
        headersMock = mock(HttpHeaders.class);
        doReturn(headersMock).when(httpMessageMock).getHeaders();

        successCallbackMock = mock(SuccessCallback.class);
        failureCallbackMock = mock(FailureCallback.class);
        listenableFutureCallbackMock = mock(ListenableFutureCallback.class);
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private void verifySubspanOptionValue(Object wingtipsInterceptor, boolean expectedSubspanOptionValue) {
        assertThat(Whitebox.getInternalState(wingtipsInterceptor, "surroundCallsWithSubspan"))
            .isEqualTo(expectedSubspanOptionValue);
    }

    @Test
    public void code_coverage_hoops() {
        // Jump!
        Throwable ex = catchThrowable(WingtipsSpringUtil::new);
        assertThat(ex).isNull();
    }

    @Test
    public void createTracingEnabledRestTemplate_no_args_returns_RestTemplate_with_wingtips_interceptor_added_with_subspan_option_on() {
        // when
        RestTemplate result = WingtipsSpringUtil.createTracingEnabledRestTemplate();

        // then
        assertThat(result.getInterceptors()).hasSize(1);
        assertThat(result.getInterceptors().get(0)).isInstanceOf(WingtipsClientHttpRequestInterceptor.class);
        verifySubspanOptionValue(result.getInterceptors().get(0), true);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void createTracingEnabledRestTemplate_single_arg_returns_RestTemplate_with_wingtips_interceptor_added_with_subspan_option_set_to_expected_value(
        boolean subspanOptionOn
    ) {
        // when
        RestTemplate result = WingtipsSpringUtil.createTracingEnabledRestTemplate(subspanOptionOn);

        // then
        assertThat(result.getInterceptors()).hasSize(1);
        assertThat(result.getInterceptors().get(0)).isInstanceOf(WingtipsClientHttpRequestInterceptor.class);
        verifySubspanOptionValue(result.getInterceptors().get(0), subspanOptionOn);
    }

    @Test
    public void createTracingEnabledAsyncRestTemplate_no_args_returns_AsyncRestTemplate_with_wingtips_interceptor_added_with_subspan_option_on() {
        // when
        AsyncRestTemplate result = WingtipsSpringUtil.createTracingEnabledAsyncRestTemplate();

        // then
        assertThat(result.getInterceptors()).hasSize(1);
        assertThat(result.getInterceptors().get(0)).isInstanceOf(WingtipsAsyncClientHttpRequestInterceptor.class);
        verifySubspanOptionValue(result.getInterceptors().get(0), true);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void createTracingEnabledAsyncRestTemplate_single_arg_returns_AsyncRestTemplate_with_wingtips_interceptor_added_with_subspan_option_set_to_expected_value(
        boolean subspanOptionOn
    ) {
        // when
        AsyncRestTemplate result = WingtipsSpringUtil.createTracingEnabledAsyncRestTemplate(subspanOptionOn);

        // then
        assertThat(result.getInterceptors()).hasSize(1);
        assertThat(result.getInterceptors().get(0)).isInstanceOf(WingtipsAsyncClientHttpRequestInterceptor.class);
        verifySubspanOptionValue(result.getInterceptors().get(0), subspanOptionOn);
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void setTracingPropagationHeaders_works_as_expected(
        boolean httpMessageIsNull, boolean spanIsNull
    ) {
        // given
        if (httpMessageIsNull)
            httpMessageMock = null;

        Span span = (spanIsNull)
                    ? null
                    : Span.newBuilder(UUID.randomUUID().toString(), SpanPurpose.CLIENT)
                          .withParentSpanId(UUID.randomUUID().toString())
                          .build();

        // when
        WingtipsSpringUtil.setTracingPropagationHeaders(httpMessageMock, span);

        // then
        if (httpMessageIsNull || spanIsNull) {
            verifyZeroInteractions(headersMock);
        }
        else {
            verify(headersMock).set(TRACE_ID, span.getTraceId());
            verify(headersMock).set(SPAN_ID, span.getSpanId());
            verify(headersMock).set(TRACE_SAMPLED, convertSampleableBooleanToExpectedB3Value(span.isSampleable()));
            verify(headersMock).set(PARENT_SPAN_ID, span.getParentSpanId());
        }
    }

    // See https://github.com/openzipkin/b3-propagation - we should pass "1" if it's sampleable, "0" if it's not.
    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void setTracingPropagationHeaders_uses_B3_spec_for_sampleable_header_value(
        boolean sampleable
    ) {
        // given
        Span span = Span.newBuilder("foo", SpanPurpose.CLIENT)
                        .withSampleable(sampleable)
                        .build();

        // when
        WingtipsSpringUtil.setTracingPropagationHeaders(httpMessageMock, span);

        // then
        verify(headersMock).set(TRACE_SAMPLED, convertSampleableBooleanToExpectedB3Value(span.isSampleable()));
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void setTracingPropagationHeaders_only_sends_parent_span_id_header_if_parent_span_id_exists(
        boolean parentSpanIdExists
    ) {
        // given
        String parentSpanId = (parentSpanIdExists) ? UUID.randomUUID().toString() : null;
        Span span = Span.newBuilder("foo", SpanPurpose.CLIENT)
                        .withParentSpanId(parentSpanId)
                        .build();

        // when
        WingtipsSpringUtil.setTracingPropagationHeaders(httpMessageMock, span);

        // then
        if (parentSpanIdExists) {
            verify(headersMock).set(PARENT_SPAN_ID, parentSpanId);
        }
        else {
            verify(headersMock, never()).set(eq(PARENT_SPAN_ID), anyString());
        }
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
    }

    private Pair<Deque<Span>, Map<String, String>> generateTracingInfo() {
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("someSpan");
        Pair<Deque<Span>, Map<String, String>> result = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(), new HashMap<>(MDC.getCopyOfContextMap())
        );
        resetTracing();
        return result;
    }

    private Pair<Deque<Span>, Map<String, String>> setupCurrentThreadWithTracingInfo() {
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("request-" + UUID.randomUUID().toString());
        return Pair.of(Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
    }
    
    private void verifySuccessCallbackWithTracing(SuccessCallback result,
                                                  SuccessCallback expectedCoreInstance,
                                                  Deque<Span> expectedSpanStack,
                                                  Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(SuccessCallbackWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origSuccessCallback")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "distributedTraceStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @Test
    public void successCallbackWithTracing_using_current_thread_info_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        SuccessCallback result = successCallbackWithTracing(successCallbackMock);

        // then
        verifySuccessCallbackWithTracing(result, successCallbackMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void successCallbackWithTracing_pair_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        SuccessCallback result = successCallbackWithTracing(successCallbackMock, setupInfo);

        // then
        verifySuccessCallbackWithTracing(result, successCallbackMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void successCallbackWithTracing_separate_args_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        SuccessCallback result = successCallbackWithTracing(
            successCallbackMock, setupInfo.getLeft(), setupInfo.getRight()
        );

        // then
        verifySuccessCallbackWithTracing(result, successCallbackMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyFailureCallbackWithTracing(FailureCallback result,
                                                  FailureCallback expectedCoreInstance,
                                                  Deque<Span> expectedSpanStack,
                                                  Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(FailureCallbackWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origFailureCallback")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "distributedTraceStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @Test
    public void failureCallbackWithTracing_using_current_thread_info_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        FailureCallback result = failureCallbackWithTracing(failureCallbackMock);

        // then
        verifyFailureCallbackWithTracing(result, failureCallbackMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void failureCallbackWithTracing_pair_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        FailureCallback result = failureCallbackWithTracing(failureCallbackMock, setupInfo);

        // then
        verifyFailureCallbackWithTracing(result, failureCallbackMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void failureCallbackWithTracing_separate_args_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        FailureCallback result = failureCallbackWithTracing(
            failureCallbackMock, setupInfo.getLeft(), setupInfo.getRight()
        );

        // then
        verifyFailureCallbackWithTracing(result, failureCallbackMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyListenableFutureCallbackWithTracing(ListenableFutureCallback result,
                                                           ListenableFutureCallback expectedCoreInstance,
                                                           Deque<Span> expectedSpanStack,
                                                           Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(ListenableFutureCallbackWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origListenableFutureCallback")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "distributedTraceStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @Test
    public void listenableFutureCallbackWithTracing_using_current_thread_info_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        ListenableFutureCallback result = listenableFutureCallbackWithTracing(listenableFutureCallbackMock);

        // then
        verifyListenableFutureCallbackWithTracing(result, listenableFutureCallbackMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void listenableFutureCallbackWithTracing_pair_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        ListenableFutureCallback result = listenableFutureCallbackWithTracing(listenableFutureCallbackMock, setupInfo);

        // then
        verifyListenableFutureCallbackWithTracing(result, listenableFutureCallbackMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @Test
    public void listenableFutureCallbackWithTracing_separate_args_works_as_expected() {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        ListenableFutureCallback result = listenableFutureCallbackWithTracing(
            listenableFutureCallbackMock, setupInfo.getLeft(), setupInfo.getRight()
        );

        // then
        verifyListenableFutureCallbackWithTracing(result, listenableFutureCallbackMock, setupInfo.getLeft(), setupInfo.getRight());
    }

}