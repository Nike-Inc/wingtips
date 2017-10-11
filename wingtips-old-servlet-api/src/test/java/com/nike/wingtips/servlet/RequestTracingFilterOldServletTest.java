package com.nike.wingtips.servlet;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.assertj.core.api.ThrowableAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link RequestTracingFilterOldServlet}
 */
@RunWith(DataProviderRunner.class)
public class RequestTracingFilterOldServletTest {

    private HttpServletRequest requestMock;
    private HttpServletResponse responseMock;
    private FilterChain filterChainMock;
    private SpanCapturingFilterChain spanCapturingFilterChain;
    private FilterConfig filterConfigMock;

    private static final String USER_ID_HEADER_KEY = "userId";
    private static final String ALT_USER_ID_HEADER_KEY = "altUserId";
    private static final List<String> USER_ID_HEADER_KEYS = Arrays.asList(USER_ID_HEADER_KEY, ALT_USER_ID_HEADER_KEY);
    private static final String USER_ID_HEADER_KEYS_INIT_PARAM_VALUE_STRING = USER_ID_HEADER_KEYS.toString().replace("[", "").replace("]", "");

    private RequestTracingFilterOldServlet getBasicFilter() {
        RequestTracingFilterOldServlet filter = new RequestTracingFilterOldServlet();

        try {
            filter.init(filterConfigMock);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }

        return filter;
    }

    private RequestTracingFilterOldServlet getFilterWithSkipDispatchOverride(final boolean overrideVal) {
        RequestTracingFilterOldServlet filter = new RequestTracingFilterOldServlet() {

            @Override
            protected boolean skipDispatch(HttpServletRequest request) {
                return overrideVal;
            }
        };

        try {
            filter.init(filterConfigMock);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }

        return filter;
    }

    @Before
    public void setupMethod() {
        requestMock = mock(HttpServletRequest.class);
        responseMock = mock(HttpServletResponse.class);
        filterChainMock = mock(FilterChain.class);
        spanCapturingFilterChain = new SpanCapturingFilterChain();

        filterConfigMock = mock(FilterConfig.class);
        doReturn(USER_ID_HEADER_KEYS_INIT_PARAM_VALUE_STRING)
            .when(filterConfigMock)
            .getInitParameter(RequestTracingFilterOldServlet.USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME);

        resetTracing();
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
    }

    // VERIFY filter init, getUserIdHeaderKeys, and destroy =======================

    @DataProvider
    public static Object[][] userIdHeaderKeysInitParamDataProvider() {

        return new Object[][] {
                { null, null },
                { "", Collections.emptyList() },
                { " \t \n  ", Collections.emptyList() },
                { "asdf", Collections.singletonList("asdf") },
                { " , \n\t, asdf , \t\n  ", Collections.singletonList("asdf") },
                { "ASDF,QWER", Arrays.asList("ASDF", "QWER") },
                { "ASDF, QWER, ZXCV", Arrays.asList("ASDF", "QWER", "ZXCV") }
        };
    }

    @Test
    @UseDataProvider("userIdHeaderKeysInitParamDataProvider")
    public void init_method_gets_user_id_header_key_list_from_init_params_and_getUserIdHeaderKeys_exposes_them(
            String userIdHeaderKeysInitParamValue, List<String> expectedUserIdHeaderKeysList) throws ServletException {
        // given
        RequestTracingFilterOldServlet filter = new RequestTracingFilterOldServlet();
        FilterConfig filterConfig = mock(FilterConfig.class);
        doReturn(userIdHeaderKeysInitParamValue)
            .when(filterConfig)
            .getInitParameter(RequestTracingFilterOldServlet.USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME);
        filter.init(filterConfig);

        // when
        List<String> actualUserIdHeaderKeysList = filter.getUserIdHeaderKeys();

        // then
        assertThat(actualUserIdHeaderKeysList).isEqualTo(expectedUserIdHeaderKeysList);
        if (actualUserIdHeaderKeysList != null) {
            Exception caughtEx = null;
            try {
                actualUserIdHeaderKeysList.add("foo");
            } catch (Exception ex) {
                caughtEx = ex;
            }
            assertThat(caughtEx).isNotNull();
            assertThat(caughtEx).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    public void destroy_does_not_explode() {
        // expect
        getBasicFilter().destroy();
        // No explosion no problem
    }

    // VERIFY doFilter ===================================

    @Test(expected = ServletException.class)
    public void doFilter_should_explode_if_request_is_not_HttpServletRequest() throws IOException, ServletException {
        // expect
        getBasicFilter().doFilter(mock(ServletRequest.class), mock(HttpServletResponse.class), mock(FilterChain.class));
        fail("Expected ServletException but no exception was thrown");
    }

    @Test(expected = ServletException.class)
    public void doFilter_should_explode_if_response_is_not_HttpServletResponse() throws IOException, ServletException {
        // expect
        getBasicFilter().doFilter(mock(HttpServletRequest.class), mock(ServletResponse.class), mock(FilterChain.class));
        fail("Expected ServletException but no exception was thrown");
    }

    @Test
    public void doFilter_should_not_explode_if_request_and_response_are_HttpServletRequests_and_HttpServletResponses() throws IOException, ServletException {
        // expect
        getBasicFilter().doFilter(mock(HttpServletRequest.class), mock(HttpServletResponse.class), mock(FilterChain.class));
        // No explosion no problem
    }

    @Test
    public void doFilter_should_call_doFilterInternal_and_set_ALREADY_FILTERED_ATTRIBUTE_KEY_if_not_already_filtered_and_skipDispatch_returns_false()
            throws IOException, ServletException {
        // given: filter that returns false for skipDispatch and request that returns null for already-filtered attribute
        RequestTracingFilterOldServlet spyFilter = spy(getFilterWithSkipDispatchOverride(false));
        given(requestMock.getAttribute(
            RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(null);

        // when: doFilter() is called
        spyFilter.doFilter(requestMock, responseMock, filterChainMock);

        // then: doFilterInternal should be called and ALREADY_FILTERED_ATTRIBUTE_KEY should be set on the request
        verify(spyFilter).doFilterInternal(requestMock, responseMock, filterChainMock);
        verify(requestMock).setAttribute(RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE, Boolean.TRUE);
    }

    @Test
    public void doFilter_should_not_unset_ALREADY_FILTERED_ATTRIBUTE_KEY_after_running_doFilterInternal() throws IOException, ServletException {
        // given: filter that will run doFilterInternal and a FilterChain we can use to verify state when called
        final RequestTracingFilterOldServlet spyFilter = spy(getFilterWithSkipDispatchOverride(false));
        given(requestMock.getAttribute(
            RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(null);
        final List<Boolean> ifObjectAddedThenSmartFilterChainCalled = new ArrayList<>();
        FilterChain smartFilterChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                // Verify that when the filter chain is called we're in doFilterInternal, and that the request has ALREADY_FILTERED_ATTRIBUTE_KEY set
                verify(spyFilter).doFilterInternal(requestMock, responseMock, this);
                verify(requestMock).setAttribute(RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE, Boolean.TRUE);
                verify(requestMock, times(0)).removeAttribute(RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE);
                ifObjectAddedThenSmartFilterChainCalled.add(true);
            }
        };

        // when: doFilter() is called
        spyFilter.doFilter(requestMock, responseMock, smartFilterChain);

        // then: smartFilterChain's doFilter should have been called and ALREADY_FILTERED_ATTRIBUTE_KEY should still be set on the request
        assertThat(ifObjectAddedThenSmartFilterChainCalled).hasSize(1);
        verify(requestMock, never()).removeAttribute(RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE);
    }

    @Test
    public void doFilter_should_not_unset_ALREADY_FILTERED_ATTRIBUTE_KEY_even_if_filter_chain_explodes() throws IOException, ServletException {
        // given: filter that will run doFilterInternal and a FilterChain we can use to verify state when called and then explodes
        final RequestTracingFilterOldServlet spyFilter = spy(getFilterWithSkipDispatchOverride(false));
        given(requestMock.getAttribute(
            RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(null);
        final List<Boolean> ifObjectAddedThenSmartFilterChainCalled = new ArrayList<>();
        FilterChain smartFilterChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                // Verify that when the filter chain is called we're in doFilterInternal, and that the request has ALREADY_FILTERED_ATTRIBUTE_KEY set
                verify(spyFilter).doFilterInternal(requestMock, responseMock, this);
                verify(requestMock).setAttribute(RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE, Boolean.TRUE);
                verify(requestMock, times(0)).removeAttribute(RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE);
                ifObjectAddedThenSmartFilterChainCalled.add(true);
                throw new IllegalStateException("boom");
            }
        };

        // when: doFilter() is called
        boolean filterChainExploded = false;
        try {
            spyFilter.doFilter(requestMock, responseMock, smartFilterChain);
        }
        catch(IllegalStateException ex) {
            if ("boom".equals(ex.getMessage()))
                filterChainExploded = true;
        }

        // then: smartFilterChain's doFilter should have been called, it should have exploded, and ALREADY_FILTERED_ATTRIBUTE_KEY should still be set on the request
        assertThat(ifObjectAddedThenSmartFilterChainCalled).hasSize(1);
        assertThat(filterChainExploded).isTrue();
        verify(requestMock, never()).removeAttribute(RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE);
    }

    @Test
    public void doFilter_should_not_call_doFilterInternal_if_already_filtered() throws IOException, ServletException {
        // given: filter that returns false for skipDispatch but request that returns non-null for already-filtered attribute
        RequestTracingFilterOldServlet spyFilter = spy(getFilterWithSkipDispatchOverride(false));
        given(requestMock.getAttribute(
            RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(Boolean.TRUE);

        // when: doFilter() is called
        spyFilter.doFilter(requestMock, responseMock, filterChainMock);

        // then: doFilterInternal should not be called
        verify(spyFilter, times(0)).doFilterInternal(requestMock, responseMock, filterChainMock);
    }

    @Test
    public void doFilter_should_not_call_doFilterInternal_if_not_already_filtered_but_skipDispatch_returns_true() throws IOException, ServletException {
        // given: request that returns null for already-filtered attribute but filter that returns true for skipDispatch
        RequestTracingFilterOldServlet spyFilter = spy(getFilterWithSkipDispatchOverride(true));
        given(requestMock.getAttribute(
            RequestTracingFilterOldServlet.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(null);

        // when: doFilter() is called
        spyFilter.doFilter(requestMock, responseMock, filterChainMock);

        // then: doFilterInternal should not be called
        verify(spyFilter, times(0)).doFilterInternal(requestMock, responseMock, filterChainMock);
    }

    // VERIFY doFilterInternal ===================================

    @Test
    public void doFilterInternal_should_create_new_sampleable_span_if_no_parent_in_request_and_it_should_be_completed() throws ServletException, IOException {
        // given: filter
        RequestTracingFilterOldServlet filter = getFilterWithSkipDispatchOverride(false);

        // when: doFilterInternal is called with a request that does not have a parent span
        filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: a new valid sampleable span should be created and completed
        Span span = spanCapturingFilterChain.capturedSpan;
        assertThat(span).isNotNull();
        assertThat(span.getTraceId()).isNotNull();
        assertThat(span.getSpanId()).isNotNull();
        assertThat(span.getSpanName()).isNotNull();
        assertThat(span.getParentSpanId()).isNull();
        assertThat(span.isSampleable()).isTrue();
        assertThat(span.isCompleted()).isTrue();
    }

    @Test
    public void doFilterInternal_should_not_complete_span_until_after_filter_chain_runs() throws ServletException, IOException {
        // given: filter and filter chain that can tell us whether or not the span is complete at the time it is called
        RequestTracingFilterOldServlet filter = getFilterWithSkipDispatchOverride(false);
        final List<Boolean> spanCompletedHolder = new ArrayList<>();
        final List<Span> spanHolder = new ArrayList<>();
        FilterChain smartFilterChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                Span span = Tracer.getInstance().getCurrentSpan();
                spanHolder.add(span);
                if (span != null)
                    spanCompletedHolder.add(span.isCompleted());
            }
        };

        // when: doFilterInternal is called
        filter.doFilterInternal(requestMock, responseMock, smartFilterChain);

        // then: we should be able to validate that the smartFilterChain was called, and when it was called the span had not yet been completed,
        // and after doFilterInternal finished it was completed.
        assertThat(spanHolder).hasSize(1);
        assertThat(spanCompletedHolder).hasSize(1);
        assertThat(spanCompletedHolder.get(0)).isFalse();
        assertThat(spanHolder.get(0).isCompleted()).isTrue();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void doFilterInternal_should_complete_span_even_if_filter_chain_explodes(
        boolean isAsyncRequest
    ) throws ServletException, IOException {
        // given: filter and filter chain that will explode when called
        RequestTracingFilterOldServlet filterSpy = spy(getFilterWithSkipDispatchOverride(false));
        final List<Span> spanContextHolder = new ArrayList<>();
        FilterChain explodingFilterChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                // Verify that the span is not yet completed, keep track of it for later, then explode
                Span span = Tracer.getInstance().getCurrentSpan();
                assertThat(span).isNotNull();
                assertThat(span.isCompleted()).isFalse();
                spanContextHolder.add(span);
                throw new IllegalStateException("boom");
            }
        };

        if (isAsyncRequest) {
            doReturn(true).when(filterSpy).isAsyncRequest(any(HttpServletRequest.class));
        }

        // when: doFilterInternal is called
        boolean filterChainExploded = false;
        try {
            filterSpy.doFilterInternal(requestMock, responseMock, explodingFilterChain);
        }
        catch(IllegalStateException ex) {
            if ("boom".equals(ex.getMessage()))
                filterChainExploded = true;
        }

        // then: we should be able to validate that the filter chain exploded and the span is still completed,
        //       or setup for completion in the case of an async request
        if (isAsyncRequest) {
            assertThat(filterChainExploded).isTrue();
            verify(filterSpy).isAsyncRequest(requestMock);
            verify(filterSpy).setupTracingCompletionWhenAsyncRequestCompletes(eq(requestMock), any(TracingState.class));
            assertThat(spanContextHolder).hasSize(1);
            // The span should not be *completed* for an async request, but the
            //      setupTracingCompletionWhenAsyncRequestCompletes verification above represents the equivalent for
            //      async requests.
            assertThat(spanContextHolder.get(0).isCompleted()).isFalse();
        }
        else {
            assertThat(filterChainExploded).isTrue();
            assertThat(spanContextHolder).hasSize(1);
            assertThat(spanContextHolder.get(0).isCompleted()).isTrue();
        }
    }

    @Test
    public void doFilterInternal_should_set_request_attributes_to_new_span_info_with_user_id() throws ServletException, IOException {
        // given: filter
        RequestTracingFilterOldServlet spyFilter = spy(getFilterWithSkipDispatchOverride(false));
        given(requestMock.getHeader(USER_ID_HEADER_KEY)).willReturn("testUserId");

        // when: doFilterInternal is called
        spyFilter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: request attributes should be set with the new span's info
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;

        assertThat(newSpan.getUserId()).isEqualTo("testUserId");
    }

    @Test
    public void doFilterInternal_should_set_request_attributes_to_new_span_info_with_alt_user_id() throws ServletException, IOException {
        // given: filter
        RequestTracingFilterOldServlet spyFilter = spy(getFilterWithSkipDispatchOverride(false));
        given(requestMock.getHeader(ALT_USER_ID_HEADER_KEY)).willReturn("testUserId");

        // when: doFilterInternal is called
        spyFilter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: request attributes should be set with the new span's info
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;

        assertThat(newSpan.getUserId()).isEqualTo("testUserId");
    }

    @Test
    public void doFilterInternal_should_set_request_attributes_to_new_span_info() throws ServletException, IOException {
        // given: filter
        RequestTracingFilterOldServlet filter = getFilterWithSkipDispatchOverride(false);

        // when: doFilterInternal is called
        filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: request attributes should be set with the new span's info
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;

        verify(requestMock).setAttribute(TraceHeaders.TRACE_SAMPLED, newSpan.isSampleable());
        verify(requestMock).setAttribute(TraceHeaders.TRACE_ID, newSpan.getTraceId());
        verify(requestMock).setAttribute(TraceHeaders.SPAN_ID, newSpan.getSpanId());
        verify(requestMock).setAttribute(TraceHeaders.PARENT_SPAN_ID, newSpan.getParentSpanId());
        verify(requestMock).setAttribute(TraceHeaders.SPAN_NAME, newSpan.getSpanName());
        verify(requestMock).setAttribute(Span.class.getName(), newSpan);
    }

    @Test
    public void doFilterInternal_should_set_trace_id_in_response_header() throws ServletException, IOException {
        // given: filter
        RequestTracingFilterOldServlet filter = getFilterWithSkipDispatchOverride(false);

        // when: doFilterInternal is called
        filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: response header should be set with the span's trace ID
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        verify(responseMock).setHeader(TraceHeaders.TRACE_ID, spanCapturingFilterChain.capturedSpan.getTraceId());
    }

    @Test
    public void doFilterInternal_should_use_parent_span_info_if_present_in_request_headers() throws ServletException, IOException {
        // given: filter and request that has parent span info
        RequestTracingFilterOldServlet filter = getFilterWithSkipDispatchOverride(false);
        Span parentSpan = Span.newBuilder("someParentSpan", null).withParentSpanId(TraceAndSpanIdGenerator.generateId()).withSampleable(false).withUserId("someUser").build();
        given(requestMock.getHeader(TraceHeaders.TRACE_ID)).willReturn(parentSpan.getTraceId());
        given(requestMock.getHeader(TraceHeaders.SPAN_ID)).willReturn(parentSpan.getSpanId());
        given(requestMock.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(parentSpan.getParentSpanId());
        given(requestMock.getHeader(TraceHeaders.SPAN_NAME)).willReturn(parentSpan.getSpanName());
        given(requestMock.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(String.valueOf(parentSpan.isSampleable()));
        given(requestMock.getServletPath()).willReturn("/some/path");
        given(requestMock.getMethod()).willReturn("GET");

        // when: doFilterInternal is called
        filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: the span that is created should use the parent span info as its parent
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;
        assertThat(newSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(newSpan.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(newSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
        assertThat(newSpan.getSpanName()).isEqualTo(HttpSpanFactory.getSpanName(requestMock));
        assertThat(newSpan.isSampleable()).isEqualTo(parentSpan.isSampleable());
        assertThat(newSpan.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
    }

    @Test
    public void doFilterInternal_should_use_user_id_from_parent_span_info_if_present_in_request_headers() throws ServletException, IOException {
        // given: filter and request that has parent span info
        RequestTracingFilterOldServlet spyFilter = spy(getFilterWithSkipDispatchOverride(false));
        given(requestMock.getHeader(ALT_USER_ID_HEADER_KEY)).willReturn("testUserId");

        Span parentSpan = Span.newBuilder("someParentSpan", null).withParentSpanId(TraceAndSpanIdGenerator.generateId()).withSampleable(false).withUserId("someUser").build();
        given(requestMock.getHeader(TraceHeaders.TRACE_ID)).willReturn(parentSpan.getTraceId());
        given(requestMock.getHeader(TraceHeaders.SPAN_ID)).willReturn(parentSpan.getSpanId());
        given(requestMock.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(parentSpan.getParentSpanId());
        given(requestMock.getHeader(TraceHeaders.SPAN_NAME)).willReturn(parentSpan.getSpanName());
        given(requestMock.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(String.valueOf(parentSpan.isSampleable()));
        given(requestMock.getServletPath()).willReturn("/some/path");
        given(requestMock.getMethod()).willReturn("GET");

        // when: doFilterInternal is called
        spyFilter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: the span that is created should use the parent span info as its parent
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;

        assertThat(newSpan.getUserId()).isEqualTo("testUserId");

    }

    @DataProvider(value = {
        "true",
        "false",
    }, splitBy = "\\|")
    @Test
    public void doFilterInternal_should_reset_tracing_info_to_whatever_was_on_the_thread_originally(
        boolean throwExceptionInInnerFinallyBlock
    ) throws ServletException, IOException {
        // given
        final RequestTracingFilterOldServlet filterSpy = spy(getBasicFilter());
        RuntimeException exToThrowInInnerFinallyBlock = null;
        if (throwExceptionInInnerFinallyBlock) {
            exToThrowInInnerFinallyBlock = new RuntimeException("kaboom");
            doThrow(exToThrowInInnerFinallyBlock).when(filterSpy).isAsyncRequest(any(HttpServletRequest.class));
        }
        Tracer.getInstance().startRequestWithRootSpan("someOutsideSpan");
        TracingState originalTracingState = TracingState.getCurrentThreadTracingState();

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);
            }
        });

        // then
        if (throwExceptionInInnerFinallyBlock) {
            assertThat(ex).isSameAs(exToThrowInInnerFinallyBlock);
        }
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(originalTracingState);
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        // The original tracing state was replaced on the thread before returning, but the span used by the filter chain
        //      should *not* come from the original tracing state - it should have come from the incoming headers or
        //      a new one generated.
        assertThat(spanCapturingFilterChain.capturedSpan.getTraceId())
            .isNotEqualTo(originalTracingState.spanStack.peek().getTraceId());
    }

    @Test
    public void doFilterInternal_should_call_setupTracingCompletionWhenAsyncRequestCompletes_when_isAsyncRequest_returns_true(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilterOldServlet filterSpy = spy(getBasicFilter());
        doReturn(true).when(filterSpy).isAsyncRequest(any(HttpServletRequest.class));

        // when
        filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        assertThat(spanCapturingFilterChain.capturedSpan.isCompleted()).isFalse();
        verify(filterSpy).setupTracingCompletionWhenAsyncRequestCompletes(eq(requestMock), any(TracingState.class));
    }

    @Test
    public void doFilterInternal_should_not_call_setupTracingCompletionWhenAsyncRequestCompletes_when_isAsyncRequest_returns_false(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilterOldServlet filterSpy = spy(getBasicFilter());
        doReturn(false).when(filterSpy).isAsyncRequest(any(HttpServletRequest.class));

        // when
        filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        assertThat(spanCapturingFilterChain.capturedSpan.isCompleted()).isTrue();
        verify(filterSpy, never()).setupTracingCompletionWhenAsyncRequestCompletes(
            any(HttpServletRequest.class), any(TracingState.class)
        );
    }

    // VERIFY skipDispatch ==============================

    @Test
    public void skipDispatch_should_return_false() {
        // given: filter
        RequestTracingFilterOldServlet filter = getBasicFilter();

        // when: skipDispatchIsCalled
        boolean result = filter.skipDispatch(requestMock);

        // then: the result should be false
        assertThat(result).isFalse();
    }

    private static class SpanCapturingFilterChain implements FilterChain {

        public Span capturedSpan;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            capturedSpan = Tracer.getInstance().getCurrentSpan();
        }
    }

    // VERIFY isAsyncRequest ==============================

    @Test
    public void isAsyncRequest_should_return_false() {
        // given
        RequestTracingFilterOldServlet filterSpy = spy(getBasicFilter());

        // when
        boolean result = filterSpy.isAsyncRequest(requestMock);

        // then
        assertThat(result).isFalse();
        verify(filterSpy).isAsyncRequest(requestMock);
        verifyNoMoreInteractions(filterSpy);
    }

    // VERIFY setupTracingCompletionWhenAsyncRequestCompletes ============

    @Test
    public void setupTracingCompletionWhenAsyncRequestCompletes_should_do_nothing(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilterOldServlet filterSpy = spy(getBasicFilter());
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        filterSpy.setupTracingCompletionWhenAsyncRequestCompletes(requestMock, tracingStateMock);

        // then
        verify(filterSpy).setupTracingCompletionWhenAsyncRequestCompletes(requestMock, tracingStateMock);
        verifyNoMoreInteractions(filterSpy, requestMock, tracingStateMock);
    }
}
