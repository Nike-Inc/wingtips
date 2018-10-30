package com.nike.wingtips.servlet.tag;

import com.nike.wingtips.tags.KnownZipkinTags;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link ServletRequestTagAdapter}.
 */
@RunWith(DataProviderRunner.class)
public class ServletRequestTagAdapterTest {

    private ServletRequestTagAdapter adapterSpy;
    private HttpServletRequest requestMock;
    private HttpServletResponse responseMock;

    @Before
    public void setup() {
        adapterSpy = spy(new ServletRequestTagAdapter());
        requestMock = mock(HttpServletRequest.class);
        responseMock = mock(HttpServletResponse.class);
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
        doReturn(statusCode).when(adapterSpy).getResponseHttpStatus(any(HttpServletResponse.class));

        // when
        String result = adapterSpy.getErrorResponseTagValue(responseMock);

        // then
        assertThat(result).isEqualTo(expectedTagValue);
        verify(adapterSpy).getErrorResponseTagValue(responseMock);
        verify(adapterSpy).getResponseHttpStatus(responseMock);
        verifyNoMoreInteractions(adapterSpy);
    }

    @DataProvider(value = {
        "http://some.host:4242/foo/bar  |   queryStr=stuff  |   http://some.host:4242/foo/bar?queryStr=stuff",
        "http://some.host:4242/foo/bar  |   null            |   http://some.host:4242/foo/bar",
        "http://some.host:4242/foo/bar  |                   |   http://some.host:4242/foo/bar",
        "http://some.host:4242/foo/bar  |   [whitespace]    |   http://some.host:4242/foo/bar",
    }, splitBy = "\\|")
    @Test
    public void getRequestUrl_works_as_expected(
        String requestUrlNoQueryString, String queryString, String expectedResult
    ) {
        // given
        if ("[whitespace]".equals(queryString)) {
            queryString = "  \t\r\n  ";
        }
        StringBuffer requestUrlNoQueryStrBuffer = new StringBuffer(requestUrlNoQueryString);

        doReturn(requestUrlNoQueryStrBuffer).when(requestMock).getRequestURL();
        doReturn(queryString).when(requestMock).getQueryString();

        // when
        String result = adapterSpy.getRequestUrl(requestMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getRequestUrl_returns_null_if_passed_null() {
        // expect
        assertThat(adapterSpy.getRequestUrl(null)).isNull();
    }

    @Test
    public void getResponseHttpStatus_works_as_expected() {
        // given
        Integer expectedResult = 42;
        doReturn(expectedResult).when(responseMock).getStatus();

        // when
        Integer result = adapterSpy.getResponseHttpStatus(responseMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getResponseHttpStatus_returns_null_if_passed_null() {
        // expect
        assertThat(adapterSpy.getResponseHttpStatus(null)).isNull();
    }

    @Test
    public void getRequestHttpMethod_works_as_expected() {
        // given
        String expectedResult = UUID.randomUUID().toString();
        doReturn(expectedResult).when(requestMock).getMethod();

        // when
        String result = adapterSpy.getRequestHttpMethod(requestMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getRequestHttpMethod_returns_null_if_passed_null() {
        // expect
        assertThat(adapterSpy.getRequestHttpMethod(null)).isNull();
    }

    @Test
    public void getRequestPath_works_as_expected() {
        // given
        String expectedResult = UUID.randomUUID().toString();
        doReturn(expectedResult).when(requestMock).getRequestURI();

        // when
        String result = adapterSpy.getRequestPath(requestMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getRequestPath_returns_null_if_passed_null() {
        // expect
        assertThat(adapterSpy.getRequestPath(null)).isNull();
    }

    // Basically a copy of the HttpSpanFactory.determineUriPathTemplate() test, since getRequestUriPathTemplate
    //      just delegates to HttpSpanFactory.determineUriPathTemplate().
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

        doReturn(httpRouteRequestAttr).when(requestMock).getAttribute(KnownZipkinTags.HTTP_ROUTE);
        doReturn(springMatchingPatternRequestAttr)
            .when(requestMock).getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern");

        // when
        String result = adapterSpy.getRequestUriPathTemplate(requestMock, responseMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getHeaderSingleValue_works_as_expected() {
        // given
        String headerKey = UUID.randomUUID().toString();

        String expectedResult = UUID.randomUUID().toString();
        doReturn(expectedResult).when(requestMock).getHeader(anyString());

        // when
        String result = adapterSpy.getHeaderSingleValue(requestMock, headerKey);

        // then
        assertThat(result).isEqualTo(expectedResult);
        verify(requestMock).getHeader(headerKey);
    }

    @Test
    public void getHeaderSingleValue_returns_null_if_passed_null_request() {
        // expect
        assertThat(adapterSpy.getHeaderSingleValue(null, "foo")).isNull();
    }

    @Test
    public void getHeaderMultipleValue_works_as_expected() {
        // given
        String headerKey = UUID.randomUUID().toString();

        List<String> expectedResult = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        doReturn(Collections.enumeration(expectedResult)).when(requestMock).getHeaders(anyString());

        // when
        List<String> result = adapterSpy.getHeaderMultipleValue(requestMock, headerKey);

        // then
        assertThat(result).isEqualTo(expectedResult);
        verify(requestMock).getHeaders(headerKey);
    }

    @Test
    public void getHeaderMultipleValue_returns_null_if_request_headers_Enumeration_is_null() {
        // given
        String headerKey = UUID.randomUUID().toString();

        doReturn(null).when(requestMock).getHeaders(anyString());

        // when
        List<String> result = adapterSpy.getHeaderMultipleValue(requestMock, headerKey);

        // then
        assertThat(result).isNull();
        verify(requestMock).getHeaders(headerKey);
    }

    @Test
    public void getHeaderMultipleValue_returns_null_if_passed_null_request() {
        // expect
        assertThat(adapterSpy.getHeaderMultipleValue(null, "foo")).isNull();
    }

    @Test
    public void getSpanHandlerTagValue_returns_expected_value() {
        // expect
        assertThat(adapterSpy.getSpanHandlerTagValue(requestMock, responseMock)).isEqualTo("servlet");
    }
}
