package com.nike.wingtips.servlet;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.NoOpHttpTagAdapter;
import com.nike.wingtips.tags.NoOpHttpTagStrategy;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy;
import com.nike.wingtips.util.TracingState;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Verifies the functionality of {@link WingtipsRequestSpanCompletionAsyncListener}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsRequestSpanCompletionAsyncListenerTest {

    private WingtipsRequestSpanCompletionAsyncListener implSpy;
    private TracingState tracingState;
    private Span tracingStateSpan;
    private AsyncEvent asyncEventMock;
    private HttpServletRequest requestMock;
    private HttpServletResponse responseMock;
    private Throwable errorThrown;

    private HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> tagAndNamingStrategy;
    private HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> tagAndNamingAdapterMock;

    private AtomicReference<String> initialSpanNameFromStrategy;
    private AtomicBoolean strategyInitialSpanNameMethodCalled;
    private AtomicBoolean strategyRequestTaggingMethodCalled;
    private AtomicBoolean strategyResponseTaggingAndFinalSpanNameMethodCalled;
    private AtomicReference<ArgCapturingHttpTagAndSpanNamingStrategy.InitialSpanNameArgs> strategyInitialSpanNameArgs;
    private AtomicReference<ArgCapturingHttpTagAndSpanNamingStrategy.RequestTaggingArgs> strategyRequestTaggingArgs;
    private AtomicReference<ArgCapturingHttpTagAndSpanNamingStrategy.ResponseTaggingArgs> strategyResponseTaggingArgs;

    @Before
    public void beforeMethod() {
        Tracer.getInstance().startRequestWithRootSpan("someRequestSpan");
        tracingState = TracingState.getCurrentThreadTracingState();
        assertThat(tracingState.spanStack).hasSize(1);
        tracingStateSpan = tracingState.spanStack.peek();

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

        implSpy = spy(
            new WingtipsRequestSpanCompletionAsyncListener(tracingState, tagAndNamingStrategy, tagAndNamingAdapterMock)
        );
        asyncEventMock = mock(AsyncEvent.class);
        requestMock = mock(HttpServletRequest.class);
        responseMock = mock(HttpServletResponse.class);
        errorThrown = new Exception("Kaboom");

        doReturn(requestMock).when(asyncEventMock).getSuppliedRequest();
        doReturn(responseMock).when(asyncEventMock).getSuppliedResponse();
        doReturn(errorThrown).when(asyncEventMock).getThrowable();
        
        resetTracing();
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            Tracer.getInstance().removeSpanLifecycleListener(listener);
        }
    }

    @Test
    public void constructor_sets_fields_as_expected() {
        // given
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        WingtipsRequestSpanCompletionAsyncListener impl =
            new WingtipsRequestSpanCompletionAsyncListener(tracingStateMock, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // then
        assertThat(impl.originalRequestTracingState).isSameAs(tracingStateMock);
        assertThat(impl.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(impl.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
        assertThat(impl.alreadyCompleted.get()).isFalse();
    }

    @Test
    public void constructor_uses_default_NoOpHttpTagStrategy_if_passed_null_tag_strategy() {
        // when
        WingtipsRequestSpanCompletionAsyncListener impl =
            new WingtipsRequestSpanCompletionAsyncListener(tracingState, null, tagAndNamingAdapterMock);

        // then
        assertThat(impl.tagAndNamingStrategy).isSameAs(NoOpHttpTagStrategy.getDefaultInstance());

        assertThat(impl.originalRequestTracingState).isSameAs(tracingState);
        assertThat(impl.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
    }

    @Test
    public void constructor_uses_default_NoOpHttpTagAdapter_if_passed_null_tag_adapter() {
        // when
        WingtipsRequestSpanCompletionAsyncListener impl =
            new WingtipsRequestSpanCompletionAsyncListener(tracingState, tagAndNamingStrategy, null);

        // then
        assertThat(impl.tagAndNamingAdapter).isSameAs(NoOpHttpTagAdapter.getDefaultInstance());

        assertThat(impl.originalRequestTracingState).isSameAs(tracingState);
        assertThat(impl.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
    }

    @Test
    public void onComplete_calls_completeRequestSpan_and_does_nothing_else() {
        // when
        implSpy.onComplete(asyncEventMock);

        // then
        verify(implSpy).onComplete(asyncEventMock);
        verify(implSpy).completeRequestSpan(asyncEventMock);
        verifyNoMoreInteractions(implSpy);
    }

    @Test
    public void onTimeout_does_nothing() {
        // when
        implSpy.onTimeout(asyncEventMock);

        // then
        verify(implSpy).onTimeout(asyncEventMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(asyncEventMock);
    }

    @Test
    public void onError_does_nothing() {
        // when
        implSpy.onError(asyncEventMock);

        // then
        verify(implSpy).onError(asyncEventMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(asyncEventMock);
    }

    @Test
    public void onStartAsync_propagates_the_listener_to_the_new_AsyncContext() {
        // given
        AsyncContext asyncContextMock = mock(AsyncContext.class);
        doReturn(asyncContextMock).when(asyncEventMock).getAsyncContext();

        // when
        implSpy.onStartAsync(asyncEventMock);

        // then
        verify(asyncContextMock).addListener(implSpy, requestMock, responseMock);
    }

    @Test
    public void onStartAsync_does_nothing_if_asyncEvent_has_null_AsyncContext() {
        // given
        // This should never happen in reality, but we protect against null pointer exceptions anyway.
        doReturn(null).when(asyncEventMock).getAsyncContext();

        // when
        implSpy.onStartAsync(asyncEventMock);

        // then
        verify(implSpy).onStartAsync(asyncEventMock);
        verifyNoMoreInteractions(implSpy);
    }

    private enum TaggingResourceScenario {
        ALL_RESOURCES_EXIST(
            mock(HttpServletRequest.class), mock(HttpServletResponse.class), mock(Throwable.class)
        ),
        REQUEST_IS_NULL(
            null, mock(HttpServletResponse.class), mock(Throwable.class)
        ),
        REQUEST_IS_NOT_HTTP_SERVLET_REQUEST(
            mock(ServletRequest.class), mock(HttpServletResponse.class), mock(Throwable.class)
        ),
        RESPONSE_IS_NULL(
            mock(HttpServletRequest.class), null, mock(Throwable.class)
        ),
        RESPONSE_IS_NOT_HTTP_SERVLET_REQUEST(
            mock(HttpServletRequest.class), mock(ServletResponse.class), mock(Throwable.class)
        ),
        ERROR_IS_NULL(
            mock(HttpServletRequest.class), mock(HttpServletResponse.class), null
        );

        public final ServletRequest requestObj;
        public final HttpServletRequest expectedRequestObjForTagging;
        public final ServletResponse responseObj;
        public final HttpServletResponse expectedResponseObjForTagging;
        public final Throwable errorObj;

        TaggingResourceScenario(ServletRequest requestObj, ServletResponse responseObj, Throwable errorObj) {
            this.requestObj = requestObj;
            this.expectedRequestObjForTagging = (requestObj instanceof HttpServletRequest)
                                                ? (HttpServletRequest) requestObj
                                                : null;
            this.responseObj = responseObj;
            this.expectedResponseObjForTagging = (responseObj instanceof HttpServletResponse)
                                                 ? (HttpServletResponse) responseObj
                                                 : null;
            this.errorObj = errorObj;
        }
    }

    @DataProvider(value = {
        "ALL_RESOURCES_EXIST",
        "REQUEST_IS_NULL",
        "REQUEST_IS_NOT_HTTP_SERVLET_REQUEST",
        "RESPONSE_IS_NULL",
        "RESPONSE_IS_NOT_HTTP_SERVLET_REQUEST",
        "ERROR_IS_NULL"
    })
    @Test
    public void completeRequestSpan_completes_request_span_as_expected(TaggingResourceScenario scenario) {
        // given
        Tracer.getInstance().startRequestWithRootSpan("someOtherUnrelatedSpan");
        TracingState unrelatedThreadTracingState = TracingState.getCurrentThreadTracingState();
        SpanRecorder spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);
        assertThat(tracingStateSpan.isCompleted()).isFalse();
        assertThat(implSpy.alreadyCompleted.get()).isFalse();

        doReturn(scenario.requestObj).when(asyncEventMock).getSuppliedRequest();
        doReturn(scenario.responseObj).when(asyncEventMock).getSuppliedResponse();
        doReturn(scenario.errorObj).when(asyncEventMock).getThrowable();

        // when
        implSpy.completeRequestSpan(asyncEventMock);

        // then
        assertThat(spanRecorder.completedSpans).hasSize(1);
        Span completedSpan = spanRecorder.completedSpans.get(0);
        assertThat(completedSpan).isSameAs(tracingStateSpan);
        assertThat(tracingStateSpan.isCompleted()).isTrue();
        assertThat(implSpy.alreadyCompleted.get()).isTrue();

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            completedSpan,
            scenario.expectedRequestObjForTagging,
            scenario.expectedResponseObjForTagging,
            scenario.errorObj,
            tagAndNamingAdapterMock
        );

        // Tracing state got reset back to original from when the method was called.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(unrelatedThreadTracingState);
    }

    @Test
    public void completeRequestSpan_marks_listener_as_completed_even_if_unexpected_exception_occurs() {
        // given
        Tracer.getInstance().startRequestWithRootSpan("someOtherUnrelatedSpan");
        TracingState unrelatedThreadTracingState = TracingState.getCurrentThreadTracingState();

        final RuntimeException expectedExplosion = new RuntimeException("kaboom");

        SpanRecorder explodingSpanRecorder = new SpanRecorder() {
            @Override
            public void spanCompleted(Span span) {
                throw expectedExplosion;
            }
        };
        Tracer.getInstance().addSpanLifecycleListener(explodingSpanRecorder);
        assertThat(implSpy.alreadyCompleted.get()).isFalse();

        // when
        Throwable actualEx = catchThrowable(() -> implSpy.completeRequestSpan(asyncEventMock));

        // then
        assertThat(actualEx).isSameAs(expectedExplosion);
        assertThat(explodingSpanRecorder.completedSpans).hasSize(0);
        assertThat(implSpy.alreadyCompleted.get()).isTrue();

        // Tracing state got reset back to original from when the method was called.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(unrelatedThreadTracingState);
    }

    @Test
    public void completeRequestSpan_does_nothing_if_listener_is_already_marked_completed() {
        // given
        implSpy.alreadyCompleted.set(true);

        assertThat(tracingStateSpan.isCompleted()).isFalse();

        // when
        implSpy.completeRequestSpan(asyncEventMock);

        // then
        assertThat(tracingStateSpan.isCompleted()).isFalse();
        assertThat(implSpy.alreadyCompleted.get()).isTrue();
    }
    
    public static class SpanRecorder implements SpanLifecycleListener {

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

}