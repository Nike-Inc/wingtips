package com.nike.wingtips.spring.webflux.server;

import com.nike.wingtips.tags.KnownZipkinTags;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link SpringWebfluxServerRequestTagAdapter}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class SpringWebfluxServerRequestTagAdapterTest {

    private SpringWebfluxServerRequestTagAdapter adapterSpy;

    private ServerWebExchange exchangeMock;
    private ServerHttpRequest requestMock;
    private HttpHeaders headersMock;
    private ServerHttpResponse responseMock;

    @Before
    public void beforeMethod() {
        adapterSpy = spy(new SpringWebfluxServerRequestTagAdapter());

        exchangeMock = mock(ServerWebExchange.class);
        requestMock = mock(ServerHttpRequest.class);
        headersMock = mock(HttpHeaders.class);
        responseMock = mock(ServerHttpResponse.class);

        doReturn(requestMock).when(exchangeMock).getRequest();
        doReturn(headersMock).when(requestMock).getHeaders();
    }

    @Test
    public void getDefaultInstance_returns_DEFAULT_INSTANCE() {
        // expect
        assertThat(SpringWebfluxServerRequestTagAdapter.getDefaultInstance())
            .isSameAs(SpringWebfluxServerRequestTagAdapter.DEFAULT_INSTANCE);
    }

    @DataProvider(value = {
        "null   |   null",
        "200    |   null",
        "300    |   null",
        "400    |   null",
        "499    |   null",
        "500    |   500",
        "599    |   599",
        "999    |   999"
    }, splitBy = "\\|")
    @Test
    public void getErrorResponseTagValue_works_as_expected(Integer statusCode, String expectedTagValue) {
        // given
        doReturn(statusCode).when(adapterSpy).getResponseHttpStatus(any(ServerHttpResponse.class));

        // when
        String result = adapterSpy.getErrorResponseTagValue(responseMock);

        // then
        assertThat(result).isEqualTo(expectedTagValue);
        verify(adapterSpy).getErrorResponseTagValue(responseMock);
        verify(adapterSpy).getResponseHttpStatus(responseMock);
        verifyNoMoreInteractions(adapterSpy);
    }

    @Test
    public void getRequestUrl_returns_request_URI_toString() {
        // given
        URI expectedFullUri = URI.create("http://localhost:1234/foo/bar/" + UUID.randomUUID().toString() + "?stuff=things");
        doReturn(expectedFullUri).when(requestMock).getURI();

        // when
        String result = adapterSpy.getRequestUrl(exchangeMock);

        // then
        assertThat(result).isEqualTo(expectedFullUri.toString());
    }

    @Test
    public void getRequestUrl_returns_null_when_passed_null_exchange() {
        // expect
        assertThat(adapterSpy.getRequestUrl(null)).isNull();
    }

    @Test
    public void getRequestUrl_returns_null_when_exchange_request_is_null() {
        // given
        doReturn(null).when(exchangeMock).getRequest();

        // expect
        assertThat(adapterSpy.getRequestUrl(exchangeMock)).isNull();
    }

    @Test
    public void getResponseHttpStatus_works_as_expected() {
        // given
        HttpStatus expectedResult = HttpStatus.I_AM_A_TEAPOT;
        doReturn(expectedResult).when(responseMock).getStatusCode();

        // when
        Integer result = adapterSpy.getResponseHttpStatus(responseMock);

        // then
        assertThat(result).isEqualTo(expectedResult.value());
    }

    @Test
    public void getResponseHttpStatus_returns_null_when_passed_null() {
        // expect
        assertThat(adapterSpy.getResponseHttpStatus(null)).isNull();
    }

    @Test
    public void getResponseHttpStatus_returns_null_when_response_getStatusCode_returns_null() {
        // given
        doReturn(null).when(responseMock).getStatusCode();

        // expect
        assertThat(adapterSpy.getResponseHttpStatus(responseMock)).isNull();
    }

    @Test
    public void getRequestHttpMethod_works_as_expected() {
        // given
        String expectedResult = UUID.randomUUID().toString();
        doReturn(expectedResult).when(requestMock).getMethodValue();

        // when
        String result = adapterSpy.getRequestHttpMethod(exchangeMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getRequestHttpMethod_returns_null_when_passed_null_exchange() {
        // expect
        assertThat(adapterSpy.getRequestHttpMethod(null)).isNull();
    }

    @Test
    public void getRequestHttpMethod_returns_null_when_exchange_request_is_null() {
        // given
        doReturn(null).when(exchangeMock).getRequest();

        // expect
        assertThat(adapterSpy.getRequestHttpMethod(exchangeMock)).isNull();
    }

    @Test
    public void getRequestPath_works_as_expected() {
        // given
        URI fullUri = URI.create("http://localhost:1234/foo/bar/" + UUID.randomUUID().toString() + "?stuff=things");
        doReturn(fullUri).when(requestMock).getURI();

        String expectedResult = fullUri.getPath();

        // when
        String result = adapterSpy.getRequestPath(exchangeMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getRequestPath_returns_null_when_passed_null_exchange() {
        // expect
        assertThat(adapterSpy.getRequestPath(null)).isNull();
    }

    @Test
    public void getRequestPath_returns_null_when_exchange_request_is_null() {
        // given
        doReturn(null).when(exchangeMock).getRequest();

        // expect
        assertThat(adapterSpy.getRequestPath(exchangeMock)).isNull();
    }

    // Basically a copy of the WingtipsSpringWebfluxWebFilter.determineUriPathTemplate() test,
    //      since getRequestUriPathTemplate just delegates to WingtipsSpringWebfluxWebFilter.determineUriPathTemplate().
    @DataProvider(value = {
        // http.route takes precedence
        "/some/http/route   |   /some/spring/pattern    |   /some/http/route",

        "/some/http/route   |   null                    |   /some/http/route",
        "/some/http/route   |                           |   /some/http/route",
        "/some/http/route   |   [whitespace]            |   /some/http/route",

        // Spring matching pattern request attr is used if http.route is null/blank
        "null               |   /some/spring/pattern    |   /some/spring/pattern",
        "                   |   /some/spring/pattern    |   /some/spring/pattern",
        "[whitespace]       |   /some/spring/pattern    |   /some/spring/pattern",

        // null returned if both request attrs are null/blank
        "null               |   null                    |   null",
        "                   |                           |   null",
        "[whitespace]       |   [whitespace]            |   null",
    }, splitBy = "\\|")
    @Test
    public void getRequestUriPathTemplate_works_as_expected(
        String httpRouteRequestAttr,
        String springMatchingPatternRequestAttr,
        String expectedResult
    ) {
        // given
        if ("[whitespace]".equals(httpRouteRequestAttr)) {
            httpRouteRequestAttr = "  \t\r\n  ";
        }

        if ("[whitespace]".equals(springMatchingPatternRequestAttr)) {
            springMatchingPatternRequestAttr = "  \t\r\n  ";
        }

        doReturn(httpRouteRequestAttr).when(exchangeMock).getAttribute(KnownZipkinTags.HTTP_ROUTE);
        doReturn(springMatchingPatternRequestAttr)
            .when(exchangeMock).getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        // when
        String result = adapterSpy.getRequestUriPathTemplate(exchangeMock, responseMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getRequestUriPathTemplate_returns_null_when_passed_null_exchange() {
        // expect
        assertThat(adapterSpy.getRequestUriPathTemplate(null, responseMock)).isNull();
    }

    @Test
    public void getHeaderSingleValue_works_as_expected() {
        // given
        String headerKey = UUID.randomUUID().toString();

        String expectedResult = UUID.randomUUID().toString();
        doReturn(expectedResult).when(headersMock).getFirst(anyString());

        // when
        String result = adapterSpy.getHeaderSingleValue(exchangeMock, headerKey);

        // then
        assertThat(result).isEqualTo(expectedResult);
        verify(headersMock).getFirst(headerKey);
    }

    @Test
    public void getHeaderSingleValue_returns_null_when_passed_null_exchange() {
        // expect
        assertThat(adapterSpy.getHeaderSingleValue(null, "foo")).isNull();
    }

    @Test
    public void getHeaderSingleValue_returns_null_when_exchange_request_is_null() {
        // given
        doReturn(null).when(exchangeMock).getRequest();

        // expect
        assertThat(adapterSpy.getHeaderSingleValue(exchangeMock, "foo")).isNull();
    }

    @Test
    public void getHeaderMultipleValue_works_as_expected() {
        // given
        String headerKey = UUID.randomUUID().toString();

        List<String> expectedResult = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        doReturn(expectedResult).when(headersMock).getValuesAsList(anyString());

        // when
        List<String> result = adapterSpy.getHeaderMultipleValue(exchangeMock, headerKey);

        // then
        assertThat(result).isEqualTo(expectedResult);
        verify(headersMock).getValuesAsList(headerKey);
    }

    @Test
    public void getHeaderMultipleValue_returns_null_when_passed_null_exchange() {
        // expect
        assertThat(adapterSpy.getHeaderMultipleValue(null, "foo")).isNull();
    }

    @Test
    public void getHeaderMultipleValue_returns_null_when_exchange_request_is_null() {
        // given
        doReturn(null).when(exchangeMock).getRequest();

        // expect
        assertThat(adapterSpy.getHeaderMultipleValue(exchangeMock, "foo")).isNull();
    }

    @Test
    public void getSpanHandlerTagValue_returns_expected_value() {
        // expect
        assertThat(adapterSpy.getSpanHandlerTagValue(exchangeMock, responseMock)).isEqualTo("spring.webflux.server");
    }
}