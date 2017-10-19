package com.nike.wingtips.spring.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.support.HttpRequestWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link HttpRequestWrapper} that guarantees {@link #getHeaders()} is modifiable, both from the header map
 * perspective and the header-value-list perspective.
 *
 * @author Nic Munroe
 */
public class HttpRequestWrapperWithModifiableHeaders extends HttpRequestWrapper {

    @SuppressWarnings("WeakerAccess")
    protected final HttpHeaders modifiableHeaders;

    /**
     * Create a new {@code HttpRequest} wrapping the given request object.
     *
     * @param request The request object to be wrapped
     */
    public HttpRequestWrapperWithModifiableHeaders(HttpRequest request) {
        super(request);

        modifiableHeaders = new HttpHeaders();
        for (Map.Entry<String, List<String>> origHeaderEntry : request.getHeaders().entrySet()) {
            modifiableHeaders.put(origHeaderEntry.getKey(), new ArrayList<>(origHeaderEntry.getValue()));
        }
    }

    /**
     * @return A mutable copy of the wrapped request's headers.
     */
    @Override
    public HttpHeaders getHeaders() {
        return modifiableHeaders;
    }
}
