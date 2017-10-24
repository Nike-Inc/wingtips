package com.nike.wingtips.apache.httpclient.util;

import org.apache.http.HttpRequest;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link HttpRequestForPropagation}.
 *
 * @author Nic Munroe
 */
public class HttpRequestForPropagationTest {

    @Test
    public void constructor_sets_fields_as_expected() {
        // given
        HttpRequest requestMock = mock(HttpRequest.class);

        // when
        HttpRequestForPropagation impl = new HttpRequestForPropagation(requestMock);

        // then
        assertThat(impl.httpRequest).isSameAs(requestMock);
    }

    @Test
    public void constructor_throws_IllegalArgumentException_when_passed_null() {
        // when
        Throwable ex = catchThrowable(() -> new HttpRequestForPropagation(null));

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("httpRequest cannot be null");
    }

    @Test
    public void setHeader_passes_through_to_httpRequest() {
        // given
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpRequestForPropagation impl = new HttpRequestForPropagation(requestMock);
        String headerKey = UUID.randomUUID().toString();
        String headerValue = UUID.randomUUID().toString();

        // when
        impl.setHeader(headerKey, headerValue);

        // then
        verify(requestMock).setHeader(headerKey, headerValue);
    }

}