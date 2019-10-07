package com.nike.wingtips.spring.webflux.server;

import com.nike.wingtips.http.RequestWithHeaders;

import org.jetbrains.annotations.NotNull;
import org.springframework.web.server.ServerWebExchange;

/**
 * A {@link RequestWithHeaders} that pulls request headers and attributes from a {@link ServerWebExchange}.
 *
 * @author Nic Munroe
 */
public class RequestWithHeadersServerWebExchangeAdapter implements RequestWithHeaders {

    @SuppressWarnings("WeakerAccess")
    protected final @NotNull ServerWebExchange exchange;

    @SuppressWarnings("ConstantConditions")
    public RequestWithHeadersServerWebExchangeAdapter(@NotNull ServerWebExchange exchange) {
        if (exchange == null) {
            throw new NullPointerException("exchange cannot be null");
        }

        this.exchange = exchange;
    }

    @Override
    public String getHeader(String headerName) {
        return exchange.getRequest().getHeaders().getFirst(headerName);
    }

    @Override
    public Object getAttribute(String name) {
        return exchange.getAttribute(name);
    }
}
