package com.nike.wingtips.spring.webflux.server;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link RequestWithHeadersServerWebExchangeAdapter}.
 *
 * @author Nic Munroe
 */
public class RequestWithHeadersServerWebExchangeAdapterTest {

    private RequestWithHeadersServerWebExchangeAdapter adapter;

    private ServerWebExchange exchangeMock;
    @SuppressWarnings("FieldCanBeLocal")
    private ServerHttpRequest requestMock;
    private HttpHeaders headersMock;

    @Before
    public void setupMethod() {
        exchangeMock = mock(ServerWebExchange.class);
        requestMock = mock(ServerHttpRequest.class);
        headersMock = mock(HttpHeaders.class);

        doReturn(requestMock).when(exchangeMock).getRequest();
        doReturn(headersMock).when(requestMock).getHeaders();

        adapter = new RequestWithHeadersServerWebExchangeAdapter(exchangeMock);
    }

    @Test
    public void constructor_throws_NullPointerException_if_passed_null_arg() {
        // when
        @SuppressWarnings("ConstantConditions")
        Throwable ex = catchThrowable(() -> new RequestWithHeadersServerWebExchangeAdapter(null));

        // then
        assertThat(ex).isInstanceOf(NullPointerException.class).hasMessage("exchange cannot be null");
    }

    @Test
    public void getHeader_delegates_to_request_headers_getFirst() {
        // given
        String headerName = "someHeader";
        String headerValue = UUID.randomUUID().toString();
        doReturn(headerValue).when(headersMock).getFirst(headerName);

        // when
        String actual = adapter.getHeader(headerName);

        // then
        assertThat(actual).isEqualTo(headerValue);
        verify(headersMock).getFirst(headerName);
    }

    @Test
    public void getAttribute_delegates_to_ServerWebExchange() {
        // given
        String attributeKey = "someAttr";
        Object attributeValue = UUID.randomUUID();
        doReturn(attributeValue).when(exchangeMock).getAttribute(attributeKey);

        // when
        Object actual = adapter.getAttribute(attributeKey);

        // then
        assertThat(actual).isEqualTo(attributeValue);
        verify(exchangeMock).getAttribute(attributeKey);
    }
}