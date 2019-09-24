package com.nike.wingtips.spring.webflux.server;

import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * Extension of {@link HttpTagAndSpanNamingAdapter} that knows how to handle Spring WebFlux {@link ServerWebExchange}
 * and {@link ServerHttpResponse} objects. Intended to be used by {@link WingtipsSpringWebfluxWebFilter}.
 */
public class SpringWebfluxServerRequestTagAdapter extends HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> {

    protected static final SpringWebfluxServerRequestTagAdapter DEFAULT_INSTANCE = new SpringWebfluxServerRequestTagAdapter();

    /**
     * @return A reusable, thread-safe, singleton instance of this class that can be used by anybody who wants to use
     * this class and does not need any customization.
     */
    public static SpringWebfluxServerRequestTagAdapter getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * Since this class represents server requests/responses (not clients), we only want to consider HTTP status codes
     * greater than or equal to 500 to be an error. From a server's perspective, a 4xx response is the correct
     * response to a bad request, and should therefore not be considered an error (again, from the server's
     * perspective - the client may feel differently).
     *
     * @param response The response object.
     * @return The value of {@link #getResponseHttpStatus(ServerHttpResponse)} if it is greater than or equal to 500,
     * or null otherwise.
     */
    @Override
    public @Nullable String getErrorResponseTagValue(@Nullable ServerHttpResponse response) {
        Integer statusCode = getResponseHttpStatus(response);
        if (statusCode != null && statusCode >= 500) {
            return statusCode.toString();
        }

        // Status code does not indicate an error, so return null.
        return null;
    }

    @Override
    public @Nullable String getRequestUrl(@Nullable ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return null;
        }

        return exchange.getRequest().getURI().toString();
    }

    @Override
    public @Nullable Integer getResponseHttpStatus(@Nullable ServerHttpResponse response) {
        if (response == null) {
            return null;
        }

        HttpStatus httpStatus = response.getStatusCode();
        if (httpStatus == null) {
            return null;
        }

        return httpStatus.value();
    }

    @Override
    public @Nullable String getRequestHttpMethod(@Nullable ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return null;
        }

        return exchange.getRequest().getMethodValue();
    }


    @Override
    public @Nullable String getRequestPath(@Nullable ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return null;
        }

        return exchange.getRequest().getURI().getPath();
    }

    @Override
    public @Nullable String getRequestUriPathTemplate(
        @Nullable ServerWebExchange exchange,
        @Nullable ServerHttpResponse response
    ) {
        if (exchange == null) {
            return null;
        }
        
        return WingtipsSpringWebfluxWebFilter.determineUriPathTemplate(exchange);
    }

    @Override
    public @Nullable String getHeaderSingleValue(@Nullable ServerWebExchange exchange, @NotNull String headerKey) {
        if (exchange == null || exchange.getRequest() == null) {
            return null;
        }

        return exchange.getRequest().getHeaders().getFirst(headerKey);
    }

    @Override
    public @Nullable List<String> getHeaderMultipleValue(
        @Nullable ServerWebExchange exchange, @NotNull String headerKey
    ) {
        if (exchange == null || exchange.getRequest() == null) {
            return null;
        }

        return exchange.getRequest().getHeaders().getValuesAsList(headerKey);
    }

    @Override
    public @Nullable String getSpanHandlerTagValue(
        @Nullable ServerWebExchange exchange, @Nullable ServerHttpResponse response
    ) {
        return "spring.webflux.server";
    }
}
