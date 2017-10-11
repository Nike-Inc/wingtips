package com.nike.wingtips.servlet;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.assertj.core.api.ThrowableAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link RequestTracingFilter}
 */
@RunWith(DataProviderRunner.class)
public class RequestTracingFilterTest {

    private HttpServletRequest requestMock;
    private HttpServletResponse responseMock;
    private SpanCapturingFilterChain spanCapturingFilterChain;
    private AsyncContext listenerCapturingAsyncContext;
    private List<AsyncListener> capturedAsyncListeners;
    private FilterConfig filterConfigMock;

    private RequestTracingFilter getBasicFilter() {
        RequestTracingFilter filter = new RequestTracingFilter();

        try {
            filter.init(filterConfigMock);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }

        return filter;
    }

    private void setupAsyncContextWorkflow() {
        listenerCapturingAsyncContext = mock(AsyncContext.class);
        capturedAsyncListeners = new ArrayList<>();

        doReturn(listenerCapturingAsyncContext).when(requestMock).getAsyncContext();
        doReturn(true).when(requestMock).isAsyncStarted();

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                capturedAsyncListeners.add((AsyncListener) invocation.getArguments()[0]);
                return null;
            }
        }).when(listenerCapturingAsyncContext).addListener(any(AsyncListener.class));
    }

    @Before
    public void setupMethod() {
        requestMock = mock(HttpServletRequest.class);
        responseMock = mock(HttpServletResponse.class);
        spanCapturingFilterChain = new SpanCapturingFilterChain();

        filterConfigMock = mock(FilterConfig.class);

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

    // VERIFY doFilterInternal ===================================

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false",
    }, splitBy = "\\|")
    @Test
    public void doFilterInternal_should_reset_tracing_info_to_whatever_was_on_the_thread_originally(
        boolean isAsync, boolean throwExceptionInInnerFinallyBlock
    ) throws ServletException, IOException {
        // given
        final RequestTracingFilter filter = getBasicFilter();
        if (isAsync) {
            setupAsyncContextWorkflow();
        }
        RuntimeException exToThrowInInnerFinallyBlock = null;
        if (throwExceptionInInnerFinallyBlock) {
            exToThrowInInnerFinallyBlock = new RuntimeException("kaboom");
            doThrow(exToThrowInInnerFinallyBlock).when(requestMock).isAsyncStarted();
        }
        Tracer.getInstance().startRequestWithRootSpan("someOutsideSpan");
        TracingState originalTracingState = TracingState.getCurrentThreadTracingState();

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);
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
    public void doFilterInternal_should_add_async_listener_but_not_complete_span_when_async_request_is_detected(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        setupAsyncContextWorkflow();

        // when
        filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        assertThat(spanCapturingFilterChain.capturedSpan.isCompleted()).isFalse();
        assertThat(capturedAsyncListeners).hasSize(1);
        assertThat(capturedAsyncListeners.get(0)).isInstanceOf(WingtipsRequestSpanCompletionAsyncListener.class);
        verify(filterSpy).setupTracingCompletionWhenAsyncRequestCompletes(eq(requestMock), any(TracingState.class));
    }

    @Test
    public void doFilterInternal_should_not_add_async_listener_when_isAsyncRequest_returns_false(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        doReturn(false).when(filterSpy).isAsyncRequest(any(HttpServletRequest.class));
        setupAsyncContextWorkflow();

        // when
        filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        assertThat(spanCapturingFilterChain.capturedSpan.isCompleted()).isTrue();
        assertThat(capturedAsyncListeners).hasSize(0);
        verify(filterSpy, never()).setupTracingCompletionWhenAsyncRequestCompletes(
            any(HttpServletRequest.class), any(TracingState.class)
        );
    }

    private static class SpanCapturingFilterChain implements FilterChain {

        public Span capturedSpan;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            capturedSpan = Tracer.getInstance().getCurrentSpan();
        }
    }

    // VERIFY isAsyncDispatch ===========================
    
    @DataProvider(value = {
        "FORWARD    |   false",
        "INCLUDE    |   false",
        "REQUEST    |   false",
        "ASYNC      |   true",
        "ERROR      |   false"
    }, splitBy = "\\|")
    @Test
    public void isAsyncDispatch_returns_result_based_on_request_dispatcher_type(
        DispatcherType dispatcherType, boolean expectedResult
    ) {
        // given
        doReturn(dispatcherType).when(requestMock).getDispatcherType();
        RequestTracingFilter filter = getBasicFilter();

        // when
        boolean result = filter.isAsyncDispatch(requestMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    // VERIFY isAsyncRequest ==============================

    @DataProvider(value = {
        "true   |   true    |   true",
        "true   |   false   |   false",
        "false  |   true    |   false",
        "false  |   false   |   false",
    }, splitBy = "\\|")
    @Test
    public void isAsyncRequest_should_return_the_value_of_request_isAsyncStarted_unless_containerSupportsAsyncRequests_is_false(
        boolean containerSupportsAsyncRequests, boolean isAsyncStarted, boolean expectedResult
    ) {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        doReturn(isAsyncStarted).when(requestMock).isAsyncStarted();
        doReturn(containerSupportsAsyncRequests).when(filterSpy).containerSupportsAsyncRequests(requestMock);

        // when
        boolean result = filterSpy.isAsyncRequest(requestMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
        if (containerSupportsAsyncRequests) {
            verify(requestMock).isAsyncStarted();
        }
        else {
            verify(requestMock, never()).isAsyncStarted();
        }
        verify(filterSpy).containerSupportsAsyncRequests(requestMock);
        verify(filterSpy).isAsyncRequest(requestMock);
        verifyNoMoreInteractions(filterSpy);
    }

    // VERIFY setupTracingCompletionWhenAsyncRequestCompletes ============

    @Test
    public void setupTracingCompletionWhenAsyncRequestCompletes_should_add_WingtipsRequestSpanCompletionAsyncListener(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilter filter = getBasicFilter();
        setupAsyncContextWorkflow();
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        filter.setupTracingCompletionWhenAsyncRequestCompletes(requestMock, tracingStateMock);

        // then
        assertThat(capturedAsyncListeners).hasSize(1);
        assertThat(capturedAsyncListeners.get(0)).isInstanceOf(WingtipsRequestSpanCompletionAsyncListener.class);
        WingtipsRequestSpanCompletionAsyncListener listener =
            (WingtipsRequestSpanCompletionAsyncListener)capturedAsyncListeners.get(0);
        assertThat(listener.originalRequestTracingState).isSameAs(tracingStateMock);
    }

    // VERIFY supportsAsyncRequests ====================

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void supportsAsyncRequests_returns_true_if_class_contains_getAsyncContext_otherwise_false(
        boolean useClassWithExpectedMethod
    ) {
        // given
        Class<?> servletRequestClass = (useClassWithExpectedMethod)
                                       ? GoodFakeServletRequest.class
                                       : BadFakeServletRequest.class;
        RequestTracingFilter filter = getBasicFilter();

        // when
        boolean result = filter.supportsAsyncRequests(servletRequestClass);

        // then
        assertThat(result).isEqualTo(useClassWithExpectedMethod);
    }

    /**
     * Dummy class that has a good getAsyncContext function
     */
    private static final class GoodFakeServletRequest {
        public Object getAsyncContext() {
            return Boolean.TRUE;
        }
    }

    /**
     * Dummy class that does NOT have a getAsyncContext function
     */
    private static final class BadFakeServletRequest {
    }

    // VERIFY containerSupportsAsyncRequests ====================

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void containerSupportsAsyncRequests_returns_value_of_supportsAsyncRequests_method_and_caches_result(
        boolean supportsAsyncRequestsValue
    ) {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        doReturn(supportsAsyncRequestsValue).when(filterSpy).supportsAsyncRequests(any(Class.class));
        assertThat(filterSpy.containerSupportsAsyncRequests).isNull();

        // when
        boolean result = filterSpy.containerSupportsAsyncRequests(requestMock);

        // then
        assertThat(result).isEqualTo(supportsAsyncRequestsValue);
        verify(filterSpy).supportsAsyncRequests(requestMock.getClass());
        assertThat(filterSpy.containerSupportsAsyncRequests).isEqualTo(result);
    } 

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void containerSupportsAsyncRequests_uses_cached_value_if_possible(boolean cachedValue) {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        filterSpy.containerSupportsAsyncRequests = cachedValue;

        // when
        boolean result = filterSpy.containerSupportsAsyncRequests(mock(ServletRequest.class));

        // then
        assertThat(result).isEqualTo(cachedValue);
        verify(filterSpy, never()).supportsAsyncRequests(any(Class.class));
    }

}
