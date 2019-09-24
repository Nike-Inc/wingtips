package com.nike.wingtips.spring.webflux.client;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link SpringWebfluxClientRequestTagAdapter}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class SpringWebfluxClientRequestTagAdapterTest {

    private SpringWebfluxClientRequestTagAdapter implSpy;
    private ClientRequest requestMock;
    private ClientResponse responseMock;

    @Before
    public void beforeMethod() {
        implSpy = spy(new SpringWebfluxClientRequestTagAdapter());
        requestMock = mock(ClientRequest.class);
        responseMock = mock(ClientResponse.class);
    }

    @Test
    public void getDefaultInstance_returns_DEFAULT_INSTANCE() {
        // expect
        assertThat(SpringWebfluxClientRequestTagAdapter.getDefaultInstance())
            .isSameAs(SpringWebfluxClientRequestTagAdapter.DEFAULT_INSTANCE);
    }

    @Test
    public void getRequestUrl_returns_request_URI_toString() {
        // given
        URI expectedFullUri = URI.create("/foo/bar/" + UUID.randomUUID().toString() + "?stuff=things");
        doReturn(expectedFullUri).when(requestMock).url();

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

    @Test
    public void getRequestUrl_returns_null_when_request_url_is_null() {
        // given
        doReturn(null).when(requestMock).url();

        // expect
        assertThat(implSpy.getRequestUrl(requestMock)).isNull();
    }

    @DataProvider(value = {
        "200",
        "300",
        "400",
        "500",
        "999"
    })
    @Test
    public void getResponseHttpStatus_returns_value_from_response_rawStatusCode(int responseMethodValue) {
        // given
        doReturn(responseMethodValue).when(responseMock).rawStatusCode();

        // when
        Integer result = implSpy.getResponseHttpStatus(responseMock);

        // then
        assertThat(result).isEqualTo(responseMethodValue);
        verify(responseMock).rawStatusCode();
    }

    @Test
    public void getResponseHttpStatus_returns_null_when_passed_null() {
        // expect
        assertThat(implSpy.getResponseHttpStatus(null)).isNull();
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
        "null       |   null"
    }, splitBy = "\\|")
    @Test
    public void getRequestHttpMethod_works_as_expected(HttpMethod httpMethod, String expectedResult) {
        // given
        doReturn(httpMethod).when(requestMock).method();

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
        verifyZeroInteractions(requestMock, responseMock);
    }

    @Test
    public void getRequestPath_returns_request_URI_path() {
        // given
        String expectedPath = "/foo/bar/" + UUID.randomUUID().toString();

        URI fullUri = URI.create(expectedPath + "?stuff=things");
        doReturn(fullUri).when(requestMock).url();

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
    public void getRequestPath_returns_null_when_request_url_null() {
        // given
        doReturn(null).when(requestMock).url();

        // expect
        assertThat(implSpy.getRequestPath(requestMock)).isNull();
    }

    @Test
    public void getHeaderSingleValue_returns_result_of_calling_request_headers_getFirst() {
        // given
        String expectedHeaderValue = UUID.randomUUID().toString();
        String headerKey = UUID.randomUUID().toString();

        HttpHeaders headersMock = mock(HttpHeaders.class);
        doReturn(headersMock).when(requestMock).headers();
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
        doReturn(headersMock).when(requestMock).headers();
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

    @Test
    public void getSpanHandlerTagValue_works_as_expected() {
        // expect
        assertThat(implSpy.getSpanHandlerTagValue(requestMock, responseMock)).isEqualTo("spring.webflux.client");
        verifyZeroInteractions(requestMock, responseMock);
    }
}