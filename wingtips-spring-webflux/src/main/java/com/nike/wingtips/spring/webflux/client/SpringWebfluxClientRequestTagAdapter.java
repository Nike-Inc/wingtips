package com.nike.wingtips.spring.webflux.client;

import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.util.List;

/**
 * Extension of {@link HttpTagAndSpanNamingAdapter} that knows how to handle Spring WebFlux {@link ClientRequest}
 * and {@link ClientResponse} objects. Intended to be used by {@link WingtipsSpringWebfluxExchangeFilterFunction}.
 */
public class SpringWebfluxClientRequestTagAdapter extends HttpTagAndSpanNamingAdapter<ClientRequest, ClientResponse> {

    protected static final SpringWebfluxClientRequestTagAdapter
        DEFAULT_INSTANCE = new SpringWebfluxClientRequestTagAdapter();

    /**
     * @return A reusable, thread-safe, singleton instance of this class that can be used by anybody who wants to use
     * this class and does not need any customization.
     */
    public static SpringWebfluxClientRequestTagAdapter getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public @Nullable String getRequestUrl(@Nullable ClientRequest request) {
        if (request == null || request.url() == null) {
            return null;
        }

        return request.url().toString();
    }

    @Override
    public @Nullable Integer getResponseHttpStatus(@Nullable ClientResponse response) {
        if (response == null) {
            return null;
        }

        return response.rawStatusCode();
    }

    @Override
    public @Nullable String getRequestHttpMethod(@Nullable ClientRequest request) {
        if (request == null || request.method() == null) {
            return null;
        }

        return request.method().name();
    }


    @Override
    public @Nullable String getRequestPath(@Nullable ClientRequest request) {
        if (request == null || request.url() == null) {
            return null;
        }

        return request.url().getPath();
    }

    @Override
    public @Nullable String getRequestUriPathTemplate(
        @Nullable ClientRequest request,
        @Nullable ClientResponse response
    ) {
        // Nothing we can do by default - this needs to be overridden on a per-project basis and given some smarts
        //      based on project-specific knowledge.
        return null;
    }

    @Override
    public @Nullable String getHeaderSingleValue(@Nullable ClientRequest request, @NotNull String headerKey) {
        if (request == null) {
            return null;
        }

        return request.headers().getFirst(headerKey);
    }

    @Override
    public @Nullable List<String> getHeaderMultipleValue(
        @Nullable ClientRequest request, @NotNull String headerKey
    ) {
        if (request == null) {
            return null;
        }

        return request.headers().getValuesAsList(headerKey);
    }

    @Override
    public @Nullable String getSpanHandlerTagValue(
        @Nullable ClientRequest request, @Nullable ClientResponse response
    ) {
        return "spring.webflux.client";
    }
}
