package com.nike.wingtips.spring.interceptor.tag;

import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AsyncClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;

import java.io.IOException;
import java.util.List;

/**
 * Extension of {@link HttpTagAndSpanNamingAdapter} that knows how to handle Spring {@link HttpRequest} and
 * {@link ClientHttpResponse} objects.
 */
public class SpringHttpClientTagAdapter extends HttpTagAndSpanNamingAdapter<HttpRequest, ClientHttpResponse> {

    @SuppressWarnings("WeakerAccess")
    protected static final SpringHttpClientTagAdapter DEFAULT_INSTANCE = new SpringHttpClientTagAdapter();

    /**
     * @return A reusable, thread-safe, singleton instance of this class that can be used by anybody who wants to use
     * this class and does not need any customization.
     */
    @SuppressWarnings("unchecked")
    public static SpringHttpClientTagAdapter getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public @Nullable String getRequestUrl(@Nullable HttpRequest request) {
        if (request == null) {
            return null;
        }

        return request.getURI().toString();
    }

    @Override
    public @Nullable Integer getResponseHttpStatus(@Nullable ClientHttpResponse response) {
        if (response == null) {
            return null;
        }

        try {
            return response.getRawStatusCode();
        }
        catch (IOException ioe) {
            return null;
        }
    }

    @Override
    public @Nullable String getRequestHttpMethod(@Nullable HttpRequest request) {
        if (request == null) {
            return null;
        }

        HttpMethod method = request.getMethod();

        return (method == null) ? "UNKNOWN_HTTP_METHOD" : method.name();
    }

    @Override
    public @Nullable String getRequestUriPathTemplate(
        @Nullable HttpRequest request,
        @Nullable ClientHttpResponse response
    ) {
        // Nothing we can do by default - this needs to be overridden on a per-project basis and given some smarts
        //      based on project-specific knowledge.
        return null;
    }

    @Override
    public @Nullable String getRequestPath(@Nullable HttpRequest request) {
        if (request == null) {
            return null;
        }
        
        return request.getURI().getPath();
    }

    @Override
    public @Nullable String getHeaderSingleValue(@Nullable HttpRequest request, @NotNull String headerKey) {
        if (request == null) {
            return null;
        }

        return request.getHeaders().getFirst(headerKey);
    }

    @Override
    public @Nullable List<String> getHeaderMultipleValue(@Nullable HttpRequest request, @NotNull String headerKey) {
        if (request == null) {
            return null;
        }

        return request.getHeaders().getValuesAsList(headerKey);
    }

    @Override
    public @Nullable String getSpanHandlerTagValue(@Nullable HttpRequest request, @Nullable ClientHttpResponse response) {
        if (request instanceof HttpRequestWrapper) {
            request = ((HttpRequestWrapper) request).getRequest();
        }

        if (request instanceof AsyncClientHttpRequest) {
            return "spring.asyncresttemplate";
        }

        return "spring.resttemplate";
    }
}
