package com.nike.wingtips.spring.util;

import com.nike.internal.util.MapBuilder;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link HttpRequestWrapperWithModifiableHeaders}.
 *
 * @author Nic Munroe
 */
public class HttpRequestWrapperWithModifiableHeadersTest {

    private HttpRequest requestMock;
    private URI uri;
    private HttpMethod method;

    @Before
    public void beforeMethod() {
        uri = URI.create("http://localhost:4242/" + UUID.randomUUID().toString());
        method = HttpMethod.PATCH;
        requestMock = mock(HttpRequest.class);
        doReturn(uri).when(requestMock).getURI();
        doReturn(method).when(requestMock).getMethod();
    }

    @Test
    public void constructor_sets_modifiableHeaders_to_a_mutable_copy_of_given_request_headers() {
        // given
        HttpHeaders immutableHeaders = generateImmutableHeaders(
            MapBuilder.builder("foo", singletonList(UUID.randomUUID().toString()))
                      .put("bar", Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                      .build()
        );
        doReturn(immutableHeaders).when(requestMock).getHeaders();

        verifyImmutableHeaders(immutableHeaders);

        // when
        HttpRequestWrapperWithModifiableHeaders wrapper = new HttpRequestWrapperWithModifiableHeaders(requestMock);
        verify(requestMock).getHeaders(); // The constructor should have called requestMock.getHeaders()

        // then
        HttpHeaders wrapperHeaders = wrapper.getHeaders();
        assertThat(wrapperHeaders).isSameAs(wrapper.modifiableHeaders);
        // The call to wrapper.getHeaders() should not have called requestMock.getHeaders()
        verifyNoMoreInteractions(requestMock);
        // Instead we should get back some headers that are equal to requestMock's headers, but mutable.
        assertThat(wrapperHeaders).isEqualTo(immutableHeaders);
        verifyMutableHeaders(wrapperHeaders);
    }

    @Test
    public void verify_non_header_wrapper_methods_pass_through_to_original_request() {
        // given
        doReturn(new HttpHeaders()).when(requestMock).getHeaders();
        HttpRequestWrapperWithModifiableHeaders wrapper = new HttpRequestWrapperWithModifiableHeaders(requestMock);

        // when
        HttpRequest wrappedRequest = wrapper.getRequest();
        URI wrapperUri = wrapper.getURI();
        HttpMethod wrapperMethod = wrapper.getMethod();

        // then
        assertThat(wrappedRequest).isSameAs(requestMock);
        assertThat(wrapperUri).isSameAs(uri);
        assertThat(wrapperMethod).isSameAs(wrapperMethod);
        verify(requestMock).getURI();
        verify(requestMock).getMethod();
    }

    private HttpHeaders generateImmutableHeaders(Map<String, List<String>> headers) {
        HttpHeaders orig = new HttpHeaders();
        orig.putAll(headers);
        return HttpHeaders.readOnlyHttpHeaders(orig);
    }

    private void verifyImmutableHeaders(final HttpHeaders headers) {
        // Verify the headers map itself is immutable.
        {
            Throwable ex = catchThrowable(() -> headers.put("foo", singletonList("bar")));
            assertThat(ex).isInstanceOf(UnsupportedOperationException.class);
        }

        // Verify that each header value list is also immutable.
        {
            headers.forEach((key, valueList) -> {
                Throwable ex = catchThrowable(() -> valueList.add("foo"));
                assertThat(ex).isInstanceOf(UnsupportedOperationException.class);
            });
        }
    }

    private void verifyMutableHeaders(HttpHeaders headers) {
        // Verify that each header value list is mutable.
        {
            headers.forEach((key, valueList) -> {
                Throwable ex = catchThrowable(() -> valueList.add(UUID.randomUUID().toString()));
                assertThat(ex).isNull();
            });
        }

        // Verify that the headers map itself is mutable.
        {
            Throwable ex = catchThrowable(() -> headers.put("foo", singletonList(UUID.randomUUID().toString())));
            assertThat(ex).isNull();
        }
    }

}