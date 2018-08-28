package com.nike.wingtips.tags;

import com.nike.wingtips.Span;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link NoOpHttpTagStrategy}.
 *
 * @author Nic Munroe
 */
public class NoOpHttpTagStrategyTest {

    private NoOpHttpTagStrategy<Object, Object> implSpy;
    private Span spanMock;
    private Object requestMock;
    private Object responseMock;
    private Throwable errorMock;
    private HttpTagAndSpanNamingAdapter<Object, Object> adapterMock;

    @Before
    public void beforeMethod() {
        implSpy = spy(new NoOpHttpTagStrategy<>());

        spanMock = mock(Span.class);
        requestMock = mock(Object.class);
        responseMock = mock(Object.class);
        errorMock = mock(Throwable.class);
        adapterMock = mock(HttpTagAndSpanNamingAdapter.class);
    }

    @Test
    public void getDefaultInstance_returns_DEFAULT_INSTANCE() {
        // expect
        assertThat(NoOpHttpTagStrategy.getDefaultInstance()).isSameAs(NoOpHttpTagStrategy.DEFAULT_INSTANCE);
    }

    @Test
    public void doGetInitialSpanName_returns_null_and_does_nothing_else() {
        // when
        String result = implSpy.doGetInitialSpanName(requestMock, adapterMock);

        // then
        assertThat(result).isNull();
        verify(implSpy).doGetInitialSpanName(requestMock, adapterMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(requestMock, adapterMock);
    }

    @Test
    public void doDetermineAndSetFinalSpanName_does_nothing() {
        // when
        implSpy.doDetermineAndSetFinalSpanName(spanMock, requestMock, responseMock, errorMock, adapterMock);

        // then
        verify(implSpy).doDetermineAndSetFinalSpanName(spanMock, requestMock, responseMock, errorMock, adapterMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(spanMock, requestMock, responseMock, errorMock, adapterMock);
    }

    @Test
    public void doHandleRequestTagging_does_nothing() {
        // when
        implSpy.doHandleRequestTagging(spanMock, requestMock, adapterMock);

        // then
        verify(implSpy).doHandleRequestTagging(spanMock, requestMock, adapterMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(spanMock, requestMock, adapterMock);
    }

    @Test
    public void doHandleResponseAndErrorTagging_does_nothing() {
        // when
        implSpy.doHandleResponseAndErrorTagging(spanMock, requestMock, responseMock, errorMock, adapterMock);

        // then
        verify(implSpy).doHandleResponseAndErrorTagging(spanMock, requestMock, responseMock, errorMock, adapterMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(spanMock, requestMock, responseMock, errorMock, adapterMock);
    }

    @Test
    public void doExtraWingtipsTagging_does_nothing() {
        // when
        implSpy.doExtraWingtipsTagging(spanMock, requestMock, responseMock, errorMock, adapterMock);

        // then
        verify(implSpy).doExtraWingtipsTagging(spanMock, requestMock, responseMock, errorMock, adapterMock);
        verifyNoMoreInteractions(implSpy);
        verifyZeroInteractions(spanMock, requestMock, responseMock, errorMock, adapterMock);
    }

}