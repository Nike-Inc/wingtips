package com.nike.wingtips.spring.util;

import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMessage;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link HttpHeadersForPropagation}.
 *
 * @author Nic Munroe
 */
public class HttpHeadersForPropagationTest {

    @Test
    public void constructor_with_HttpHeaders_arg_sets_fields_as_expected() {
        // given
        HttpHeaders headersMock = mock(HttpHeaders.class);

        // when
        HttpHeadersForPropagation impl = new HttpHeadersForPropagation(headersMock);

        // then
        assertThat(impl.httpHeaders).isSameAs(headersMock);
    }

    @Test
    public void constructor_with_HttpHeaders_arg_throws_IllegalArgumentException_when_passed_null() {
        // when
        Throwable ex = catchThrowable(() -> new HttpHeadersForPropagation((HttpHeaders)null));

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("httpHeaders cannot be null");
    }

    @Test
    public void constructor_with_HttpMessage_arg_sets_fields_as_expected() {
        // given
        HttpHeaders headersMock = mock(HttpHeaders.class);
        HttpMessage messageMock = mock(HttpMessage.class);
        doReturn(headersMock).when(messageMock).getHeaders();

        // when
        HttpHeadersForPropagation impl = new HttpHeadersForPropagation(messageMock);

        // then
        assertThat(impl.httpHeaders).isSameAs(headersMock);
    }

    @Test
    public void constructor_with_HttpMessage_arg_throws_IllegalArgumentException_when_passed_null() {
        // when
        Throwable ex = catchThrowable(() -> new HttpHeadersForPropagation((HttpMessage)null));

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("httpMessage cannot be null");
    }

    @Test
    public void setHeader_passes_through_to_httpHeaders() {
        // given
        HttpHeaders headersMock = mock(HttpHeaders.class);
        HttpHeadersForPropagation impl = new HttpHeadersForPropagation(headersMock);
        String headerKey = UUID.randomUUID().toString();
        String headerValue = UUID.randomUUID().toString();

        // when
        impl.setHeader(headerKey, headerValue);

        // then
        verify(headersMock).set(headerKey, headerValue);
    }
    
}