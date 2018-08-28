package com.nike.wingtips.tags;

import com.nike.wingtips.Span;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionalty of {@link ZipkinHttpTagStrategy}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class ZipkinHttpTagStrategyTest {

    private ZipkinHttpTagStrategy<Object, Object> implSpy;
    private Span spanMock;
    private Object requestMock;
    private Object responseMock;
    private Throwable errorMock;
    private HttpTagAndSpanNamingAdapter<Object, Object> adapterMock;

    @Before
    public void beforeMethod() {
        implSpy = spy(new ZipkinHttpTagStrategy<>());

        spanMock = mock(Span.class);
        requestMock = mock(Object.class);
        responseMock = mock(Object.class);
        errorMock = mock(Throwable.class);
        adapterMock = mock(HttpTagAndSpanNamingAdapter.class);
    }

    @Test
    public void getDefaultInstance_returns_DEFAULT_INSTANCE() {
        // expect
        assertThat(ZipkinHttpTagStrategy.getDefaultInstance()).isSameAs(ZipkinHttpTagStrategy.DEFAULT_INSTANCE);
    }

    @Test
    public void doHandleRequestTagging_puts_expected_tags_based_on_adapter_results() {
        // given
        String adapterHttpMethod = "httpmethod-" + UUID.randomUUID().toString();
        String adapterPath = "path-" + UUID.randomUUID().toString();
        String adapterHttpUrl = "url-" + UUID.randomUUID().toString();
        String adapterRoute = "route-" + UUID.randomUUID().toString();

        doReturn(adapterHttpMethod).when(adapterMock).getRequestHttpMethod(anyObject());
        doReturn(adapterPath).when(adapterMock).getRequestPath(anyObject());
        doReturn(adapterHttpUrl).when(adapterMock).getRequestUrl(anyObject());
        doReturn(adapterRoute).when(adapterMock).getRequestUriPathTemplate(anyObject(), anyObject());

        // when
        implSpy.doHandleRequestTagging(spanMock, requestMock, adapterMock);

        // then
        verify(adapterMock).getRequestHttpMethod(requestMock);
        verify(adapterMock).getRequestPath(requestMock);
        verify(adapterMock).getRequestUrl(requestMock);
        verify(adapterMock).getRequestUriPathTemplate(requestMock, null);
        
        verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownZipkinTags.HTTP_METHOD, adapterHttpMethod);
        verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownZipkinTags.HTTP_PATH, adapterPath);
        verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownZipkinTags.HTTP_URL, adapterHttpUrl);
        verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownZipkinTags.HTTP_ROUTE, adapterRoute);
    }

    private enum ErrorTaggingScenario {
        ERROR_IS_NOT_NULL_AND_HAS_MESSAGE(new RuntimeException("boom"), null, true, "boom"),
        ERROR_IS_NOT_NULL_BUT_HAS_NO_MESSAGE(
            new RuntimeException(), null, true, RuntimeException.class.getSimpleName()
        ),
        ERROR_IS_NULL_BUT_ADAPTER_ERROR_TAG_VALUE_IS_NOT_BLANK(null, "foo", true, "foo"),
        ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_NULL(null, null, false, null),
        ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_EMPTY(null, "", false, null),
        ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_WHITESPACE(null, "  \n\r\t  ", false, null);

        public final Throwable error;
        public final String adapterErrorTagValue;
        public final boolean expectErrorTagPutOnSpan;
        public final String expectedErrorTagValue;

        ErrorTaggingScenario(
            Throwable error, String adapterErrorTagValue, boolean expectErrorTagPutOnSpan,
            String expectedErrorTagValue
        ) {
            this.error = error;
            this.adapterErrorTagValue = adapterErrorTagValue;
            this.expectErrorTagPutOnSpan = expectErrorTagPutOnSpan;
            this.expectedErrorTagValue = expectedErrorTagValue;
        }
    }

    @DataProvider(value = {
        "ERROR_IS_NOT_NULL_AND_HAS_MESSAGE",
        "ERROR_IS_NOT_NULL_BUT_HAS_NO_MESSAGE",
        "ERROR_IS_NULL_BUT_ADAPTER_ERROR_TAG_VALUE_IS_NOT_BLANK",
        "ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_NULL",
        "ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_EMPTY",
        "ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_WHITESPACE"
    })
    @Test
    public void doHandleResponseAndErrorTagging_puts_expected_tags_based_on_adapter_results_and_error_existence(
        ErrorTaggingScenario scenario
    ) {
        // given
        Integer adapterHttpStatus = 42;
        String adapterRoute = "route-" + UUID.randomUUID().toString();

        doReturn(adapterHttpStatus).when(adapterMock).getResponseHttpStatus(anyObject());
        doReturn(adapterRoute).when(adapterMock).getRequestUriPathTemplate(anyObject(), anyObject());

        doReturn(scenario.adapterErrorTagValue).when(adapterMock).getErrorResponseTagValue(anyObject());

        // when
        implSpy.doHandleResponseAndErrorTagging(spanMock, requestMock, responseMock, scenario.error, adapterMock);

        // then
        verify(adapterMock).getRequestUriPathTemplate(requestMock, responseMock);
        verify(adapterMock).getResponseHttpStatus(responseMock);

        verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownZipkinTags.HTTP_ROUTE, adapterRoute);
        verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownZipkinTags.HTTP_STATUS_CODE, adapterHttpStatus);

        if (scenario.error == null) {
            // This call is only made if error is null.
            verify(adapterMock).getErrorResponseTagValue(responseMock);
        }
        else {
            verify(adapterMock, never()).getErrorResponseTagValue(anyObject());
        }

        if (scenario.expectErrorTagPutOnSpan) {
            verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownZipkinTags.ERROR, scenario.expectedErrorTagValue);
        }
    }
    
}