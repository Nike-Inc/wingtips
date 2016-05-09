package com.nike.wingtips.http;

/**
 * Facade around a request that contains headers (and optionally attributes). Allows {@link HttpRequestTracingUtils} to be used by different frameworks that have different
 * request types (e.g. {@code HttpServletRequest} for servlet-based containers/frameworks, and {@code HttpRequest} for Netty HTTP requests).
 *
 * @author Nic Munroe
 */
public interface RequestWithHeaders {

    /**
     * The header value associated with the given name, or null if no such header exists. If the value for this headerName is a multi-value list, then only the first item
     * should be returned.
     */
    String getHeader(String headerName);

    /**
     * OPTIONAL - If the request you're wrapping supports attributes (e.g. {@code ServletRequest}) then you can expose them here. These are used as backups only if data is
     * not found via {@link #getHeader(String)} and are not required. If the request type you're wrapping doesn't support attributes then this can safely always return null.
     */
    Object getAttribute(String name);
}
