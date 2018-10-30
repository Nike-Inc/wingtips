package com.nike.wingtips.servlet;

import com.nike.wingtips.servlet.ServletRuntime.Servlet2Runtime;
import com.nike.wingtips.servlet.ServletRuntime.Servlet3Runtime;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.util.TracingState;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link ServletRuntime}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class ServletRuntimeTest {

    private Servlet2Runtime servlet2Runtime;
    private Servlet3Runtime servlet3Runtime;

    private HttpServletRequest requestMock;
    private HttpServletResponse responseMock;

    @Before
    public void beforeMethod() {
        servlet2Runtime = new Servlet2Runtime();
        servlet3Runtime = new Servlet3Runtime();

        requestMock = mock(HttpServletRequest.class);
        responseMock = mock(HttpServletResponse.class);
    }

    @DataProvider(value = {
        "true   |   true    |   true",
        "true   |   false   |   false",
        "false  |   true    |   false",
        "false  |   false   |   false"
    }, splitBy = "\\|")
    @Test
    public void determineServletRuntime_returns_ServletRuntime_based_on_arguments(
        boolean classHasServlet3Method, boolean useAsyncListenerClassThatExists, boolean expectServlet3Runtime
    ) {
        // given
        Class<?> servletRequestClass = (classHasServlet3Method)
                                       ? GoodFakeServletRequest.class
                                       : BadFakeServletRequest.class;

        String asyncListenerClassname = (useAsyncListenerClassThatExists)
                                        ? AsyncListener.class.getName()
                                        : "does.not.exist.AsyncListener" + UUID.randomUUID().toString();

        // when
        ServletRuntime result = ServletRuntime.determineServletRuntime(servletRequestClass, asyncListenerClassname);

        // then
        if (expectServlet3Runtime) {
            assertThat(result).isInstanceOf(Servlet3Runtime.class);
        }
        else {
            assertThat(result).isInstanceOf(Servlet2Runtime.class);
        }
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

    // Servlet2Runtime tests =======================================

    @Test
    public void servlet2_isAsyncRequest_should_return_false() {
        // given
        Servlet2Runtime implSpy = spy(servlet2Runtime);

        // when
        boolean result = implSpy.isAsyncRequest(requestMock);

        // then
        assertThat(result).isFalse();
        verify(implSpy).isAsyncRequest(requestMock);
        verifyNoMoreInteractions(implSpy);
    }

    @Test
    public void servlet2_setupTracingCompletionWhenAsyncRequestCompletes_should_throw_IllegalStateException(
    ) {
        // when
        Throwable ex = catchThrowable(
            () -> servlet2Runtime.setupTracingCompletionWhenAsyncRequestCompletes(
                requestMock,
                responseMock,
                mock(TracingState.class),
                mock(HttpTagAndSpanNamingStrategy.class),
                mock(HttpTagAndSpanNamingAdapter.class)
            )
        );

        // then
        assertThat(ex)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("This method should never be called in a pre-Servlet-3.0 environment.");
    }

    @Test
    public void servlet2_isAsyncDispatch_should_return_false() {
        // given
        Servlet2Runtime implSpy = spy(servlet2Runtime);

        // when
        boolean result = implSpy.isAsyncDispatch(requestMock);

        // then
        assertThat(result).isFalse();
        verify(implSpy).isAsyncDispatch(requestMock);
        verifyNoMoreInteractions(implSpy);
    }

    // Servlet3Runtime tests =======================================

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void isAsyncRequest_should_return_the_value_of_request_isAsyncStarted(boolean requestIsAsyncStarted) {
        // given
        Servlet3Runtime implSpy = spy(servlet3Runtime);
        doReturn(requestIsAsyncStarted).when(requestMock).isAsyncStarted();

        // when
        boolean result = implSpy.isAsyncRequest(requestMock);

        // then
        assertThat(result).isEqualTo(requestIsAsyncStarted);
        verify(requestMock).isAsyncStarted();
        verify(implSpy).isAsyncRequest(requestMock);
        verifyNoMoreInteractions(implSpy);
    }

    @Test
    public void setupTracingCompletionWhenAsyncRequestCompletes_should_add_WingtipsRequestSpanCompletionAsyncListener(
    ) {
        // given
        AsyncContext asyncContextMock = mock(AsyncContext.class);
        doReturn(asyncContextMock).when(requestMock).getAsyncContext();
        TracingState tracingStateMock = mock(TracingState.class);
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> tagStrategyMock =
            mock(HttpTagAndSpanNamingStrategy.class);
        HttpTagAndSpanNamingAdapter<HttpServletRequest,HttpServletResponse> tagAdapterMock =
            mock(HttpTagAndSpanNamingAdapter.class);

        ArgumentCaptor<AsyncListener> listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);

        // when
        servlet3Runtime.setupTracingCompletionWhenAsyncRequestCompletes(
            requestMock, responseMock, tracingStateMock, tagStrategyMock, tagAdapterMock
        );

        // then
        verify(asyncContextMock).addListener(listenerCaptor.capture(), eq(requestMock), eq(responseMock));
        List<AsyncListener> addedListeners = listenerCaptor.getAllValues();
        assertThat(addedListeners).hasSize(1);
        assertThat(addedListeners.get(0)).isInstanceOf(WingtipsRequestSpanCompletionAsyncListener.class);
        WingtipsRequestSpanCompletionAsyncListener listener =
            (WingtipsRequestSpanCompletionAsyncListener)addedListeners.get(0);
        assertThat(listener.originalRequestTracingState).isSameAs(tracingStateMock);
        assertThat(listener.tagAndNamingStrategy).isSameAs(tagStrategyMock);
        assertThat(listener.tagAndNamingAdapter).isSameAs(tagAdapterMock);
    }

    @DataProvider(value = {
        "FORWARD    |   false",
        "INCLUDE    |   false",
        "REQUEST    |   false",
        "ASYNC      |   true",
        "ERROR      |   false"
    }, splitBy = "\\|")
    @Test
    public void servlet3_isAsyncDispatch_returns_result_based_on_request_dispatcher_type(
        DispatcherType dispatcherType, boolean expectedResult
    ) {
        // given
        doReturn(dispatcherType).when(requestMock).getDispatcherType();

        // when
        boolean result = servlet3Runtime.isAsyncDispatch(requestMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }
}