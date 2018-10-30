package com.nike.wingtips.tags;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link NoOpHttpTagAdapter}.
 *
 * @author Nic Munroe
 */
public class NoOpHttpTagAdapterTest {

    private NoOpHttpTagAdapter<Object, Object> implSpy;
    private Object requestMock;
    private Object responseMock;

    @Before
    public void beforeMethod() {
        implSpy = spy(new NoOpHttpTagAdapter<>());
        requestMock = mock(Object.class);
        responseMock = mock(Object.class);
    }

    @Test
    public void getDefaultInstance_returns_DEFAULT_INSTANCE() {
        // expect
        assertThat(NoOpHttpTagAdapter.getDefaultInstance()).isSameAs(NoOpHttpTagAdapter.DEFAULT_INSTANCE);
    }

    @Test
    public void getErrorResponseTagValue_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getErrorResponseTagValue(responseMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getErrorResponseTagValue(responseMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(responseMock);
    }

    @Test
    public void getRequestUrl_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getRequestUrl(requestMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getRequestUrl(requestMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock);
    }

    @Test
    public void getRequestPath_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getRequestPath(requestMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getRequestPath(requestMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock);
    }

    @Test
    public void getResponseHttpStatus_returns_null_and_does_nothing_else() {
        // when
        Integer result = implSpy.getResponseHttpStatus(responseMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getResponseHttpStatus(responseMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(responseMock);
    }

    @Test
    public void getRequestHttpMethod_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getRequestHttpMethod(requestMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getRequestHttpMethod(requestMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock);
    }

    @Test
    public void getHeaderSingleValue_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getHeaderSingleValue(requestMock, "foo");

        // then
        assertThat(result).isNull();
        verify(implSpy).getHeaderSingleValue(requestMock, "foo");
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock);
    }

    @Test
    public void getHeaderMultipleValue_returns_null_and_does_nothing_else() {
        // when
        List<String> result = implSpy.getHeaderMultipleValue(requestMock, "foo");

        // then
        assertThat(result).isNull();
        verify(implSpy).getHeaderMultipleValue(requestMock, "foo");
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock);
    }

    @Test
    public void getSpanNamePrefix_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getSpanNamePrefix(requestMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getSpanNamePrefix(requestMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock);
    }

    @Test
    public void getInitialSpanName_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getInitialSpanName(requestMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getInitialSpanName(requestMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock);
    }

    @Test
    public void getFinalSpanName_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getFinalSpanName(requestMock, responseMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getFinalSpanName(requestMock, responseMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock, responseMock);
    }

    @Test
    public void getRequestUriPathTemplate_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getRequestUriPathTemplate(requestMock, responseMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getRequestUriPathTemplate(requestMock, responseMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock, responseMock);
    }

    @Test
    public void getSpanHandlerTagValue_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.getSpanHandlerTagValue(requestMock, responseMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).getSpanHandlerTagValue(requestMock, responseMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock, responseMock);
    }

}