package com.nike.wingtips.spring.interceptor.tag;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AsyncClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link SpringHttpClientTagAdapter}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class SpringHttpClientTagAdapterTest {

    private SpringHttpClientTagAdapter implSpy;
    private HttpRequest requestMock;
    private ClientHttpResponse responseMock;

    @Before
    public void beforeMethod() {
        implSpy = spy(new SpringHttpClientTagAdapter());
        requestMock = mock(HttpRequest.class);
        responseMock = mock(ClientHttpResponse.class);
    }

    @Test
    public void getDefaultInstance_returns_DEFAULT_INSTANCE() {
        // expect
        assertThat(SpringHttpClientTagAdapter.getDefaultInstance())
            .isSameAs(SpringHttpClientTagAdapter.DEFAULT_INSTANCE);
    }

    @Test
    public void getRequestUrl_returns_request_URI_toString() {
        // given
        URI expectedFullUri = URI.create("/foo/bar/" + UUID.randomUUID().toString() + "?stuff=things");
        doReturn(expectedFullUri).when(requestMock).getURI();

        // when
        String result = implSpy.getRequestUrl(requestMock);

        // then
        assertThat(result).isEqualTo(expectedFullUri.toString());
    }

    @Test
    public void getRequestUrl_returns_null_when_passed_null() {
        // expect
        assertThat(implSpy.getRequestUrl(null)).isNull();
    }

    @DataProvider(value = {
        "200",
        "300",
        "400",
        "500",
        "999"
    })
    @Test
    public void getResponseHttpStatus_returns_value_from_response_getRawStatusCode(
        int responseMethodValue
    ) throws IOException {
        // given
        doReturn(responseMethodValue).when(responseMock).getRawStatusCode();

        // when
        Integer result = implSpy.getResponseHttpStatus(responseMock);

        // then
        assertThat(result).isEqualTo(responseMethodValue);
        verify(responseMock).getRawStatusCode();
    }

    @Test
    public void getResponseHttpStatus_returns_null_when_passed_null() {
        // expect
        assertThat(implSpy.getResponseHttpStatus(null)).isNull();
    }

    @Test
    public void getResponseHttpStatus_returns_null_when_response_getRawStatusCode_throws_IOException(
    ) throws IOException {
        // given
        doThrow(new IOException("intentional exception")).when(responseMock).getRawStatusCode();

        // when
        Integer result = implSpy.getResponseHttpStatus(responseMock);

        // then
        assertThat(result).isNull();
        verify(responseMock).getRawStatusCode();
    }

    @DataProvider(value = {
        "GET        |   GET",
        "HEAD       |   HEAD",
        "POST       |   POST",
        "PUT        |   PUT",
        "PATCH      |   PATCH",
        "DELETE     |   DELETE", 
        "OPTIONS    |   OPTIONS",
        "TRACE      |   TRACE",
        "null       |   UNKNOWN_HTTP_METHOD"
    }, splitBy = "\\|")
    @Test
    public void getRequestHttpMethod_works_as_expected(HttpMethod httpMethod, String expectedResult) {
        // given
        doReturn(httpMethod).when(requestMock).getMethod();

        // when
        String result = implSpy.getRequestHttpMethod(requestMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getRequestHttpMethod_returns_null_when_passed_null() {
        // expect
        assertThat(implSpy.getRequestHttpMethod(null)).isNull();
    }

    @Test
    public void getRequestUriPathTemplate_returns_null() {
        // expect
        assertThat(implSpy.getRequestUriPathTemplate(requestMock, responseMock)).isNull();
    }

    @Test
    public void getRequestPath_returns_request_URI_path() {
        // given
        String expectedPath = "/foo/bar/" + UUID.randomUUID().toString();

        URI fullUri = URI.create(expectedPath + "?stuff=things");
        doReturn(fullUri).when(requestMock).getURI();

        assertThat(fullUri.toString()).contains("?");
        assertThat(expectedPath).doesNotContain("?");
        assertThat(fullUri.getPath()).isEqualTo(expectedPath);

        // when
        String result = implSpy.getRequestPath(requestMock);

        // then
        assertThat(result).isEqualTo(expectedPath);
    }

    @Test
    public void getRequestPath_returns_null_when_passed_null() {
        // expect
        assertThat(implSpy.getRequestPath(null)).isNull();
    }

    @Test
    public void getHeaderSingleValue_returns_result_of_calling_request_headers_getFirst() {
        // given
        String expectedHeaderValue = UUID.randomUUID().toString();
        String headerKey = UUID.randomUUID().toString();

        HttpHeaders headersMock = mock(HttpHeaders.class);
        doReturn(headersMock).when(requestMock).getHeaders();
        doReturn(expectedHeaderValue).when(headersMock).getFirst(headerKey);

        // when
        String result = implSpy.getHeaderSingleValue(requestMock, headerKey);

        // then
        assertThat(result).isEqualTo(expectedHeaderValue);
        verify(headersMock).getFirst(headerKey);
    }

    @Test
    public void getHeaderSingleValue_returns_null_when_passed_null_request() {
        // expect
        assertThat(implSpy.getHeaderSingleValue(null, "foo")).isNull();
    }

    @Test
    public void getHeaderMultipleValue_returns_result_of_calling_request_headers_getValuesAsList() {
        // given
        List<String> expectedHeaderValues = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        String headerKey = UUID.randomUUID().toString();

        HttpHeaders headersMock = mock(HttpHeaders.class);
        doReturn(headersMock).when(requestMock).getHeaders();
        doReturn(expectedHeaderValues).when(headersMock).getValuesAsList(headerKey);

        // when
        List<String> result = implSpy.getHeaderMultipleValue(requestMock, headerKey);

        // then
        assertThat(result).isEqualTo(expectedHeaderValues);
        verify(headersMock).getValuesAsList(headerKey);
    }

    @Test
    public void getHeaderMultipleValue_returns_null_when_passed_null_request() {
        // expect
        assertThat(implSpy.getHeaderMultipleValue(null, "foo")).isNull();
    }

    @DataProvider(value = {
        "true   |   spring.asyncresttemplate",
        "false  |   spring.resttemplate"
    }, splitBy = "\\|")
    @Test
    public void getSpanHandlerTagValue_works_as_expected_when_request_is_not_an_HttpRequestWrapper(
        boolean requestIsAsyncClientHttpRequest, String expectedResult
    ) {
        // given
        HttpRequest request = (requestIsAsyncClientHttpRequest)
                              ? mock(AsyncClientHttpRequest.class)
                              : mock(HttpRequest.class);

        // when
        String result = implSpy.getSpanHandlerTagValue(request, responseMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @DataProvider(value = {
        "true   |   spring.asyncresttemplate",
        "false  |   spring.resttemplate"
    }, splitBy = "\\|")
    @Test
    public void getSpanHandlerTagValue_works_as_expected_when_request_is_an_HttpRequestWrapper(
        boolean wrappedRequestIsAsyncClientHttpRequest, String expectedResult
    ) {
        // given
        HttpRequestWrapper request = mock(HttpRequestWrapper.class);
        HttpRequest wrappedRequest = (wrappedRequestIsAsyncClientHttpRequest)
                                     ? mock(AsyncClientHttpRequest.class)
                                     : mock(HttpRequest.class);

        doReturn(wrappedRequest).when(request).getRequest();

        // when
        String result = implSpy.getSpanHandlerTagValue(request, responseMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }
}