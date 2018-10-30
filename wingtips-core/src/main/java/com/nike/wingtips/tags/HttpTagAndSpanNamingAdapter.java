package com.nike.wingtips.tags;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.http.HttpRequestTracingUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An implementation of this class knows how to extract basic HTTP properties from HTTP Request and Response objects
 * for a given HTTP framework or library, and how to handle initial and final span names for that framework or library.
 *
 * <p>This functionality is used by {@link HttpTagAndSpanNamingStrategy} implementations to extract tag values and
 * determine span names.
 *
 * <p>The methods related to span names ({@link #getInitialSpanName(Object)}, {@link
 * #getFinalSpanName(Object, Object)}, and {@link #getSpanNamePrefix(Object)}) have default implementations already
 * filled in that should cover most use cases, but are overrideable if you need to adjust their behavior. The
 * remaining methods are abstract and must be supplied by concrete implementations.
 *
 * <p>NOTE: The {@link #getErrorResponseTagValue(Object)} method assumes any 4xx or 5xx HTTP response status code
 * indicates that the span should be tagged with an "error" tag. This is usually true for most client call responses,
 * however it is usually not true for server responses since a 4xx means the caller made a mistake, not the server.
 * So if you are creating an implementation of this class to handle server calls, you may want to override that
 * method to only consider 5xx (or greater) HTTP status codes as indicating an error.
 *
 * @author Brandon Currie
 * @author Nic Munroe
 */
public abstract class HttpTagAndSpanNamingAdapter<REQ, RES> {

    /**
     * Returns the value that should be used for the "error" {@link com.nike.wingtips.Span#putTag(String, String)}
     * associated with the given response, or null if this response does not indicate an error. The criteria for
     * determining an error can change depending on whether it's a client span or server span, or there may even be
     * project-specific logic.
     *
     * <p>By default, this method considers a response to indicate an error if the {@link
     * #getResponseHttpStatus(Object)} is greater than or equal to 400, and will return that status code as a string.
     * This is a client-span-centric view - implementations of this class that represent server spans may want to
     * override this to have it only consider status codes greater than or equal to 500 to be errors.
     *
     * <p>NOTE: It's important that you return something non-empty and non-blank if want the span to be considered an
     * error. In particular, empty strings or strings that consist of only whitespace may be treated by callers
     * of this method the same as {@code null}.
     *
     * @param response The response object to be inspected - <b>this may be null!</b>
     *
     * @return The value that should be used for the "error" {@link com.nike.wingtips.Span#putTag(String, String)}
     * associated with the given response, or null if this response does not indicate an error.
     */
    public @Nullable String getErrorResponseTagValue(@Nullable RES response) {
        Integer statusCode = getResponseHttpStatus(response);
        if (statusCode != null && statusCode >= 400) {
            return statusCode.toString();
        }

        // Status code does not indicate an error, so return null.
        return null;
    }

    /**
     * @param request The request object to be inspected - <b>this may be null!</b>
     *
     * @return The full URL of the request, including scheme, host, path, and query params, or null if the full URL
     * could not be determined. e.g.: {@code http://some.host:8080/foo/bar/12345?thing=stuff}
     */
    public abstract @Nullable String getRequestUrl(@Nullable REQ request);

    /**
     * @param request The request object to be inspected - <b>this may be null!</b>
     *
     * @return The path of the request (similar to {@link #getRequestUrl(Object)}, but omits scheme, host, and query
     * params), or null if the path could not be determined. e.g.: {@code /foo/bar/12345}
     */
    public abstract @Nullable String getRequestPath(@Nullable REQ request);

    /**
     * Returns the path template associated with the given request/response (i.e. {@code /foo/:id} instead of
     * {@code /foo/12345}), or null if the path template could not be determined.
     *
     * @param request The request object to be inspected - <b>this may be null!</b>
     * @param response The response object to be inspected - <b>this may be null!</b>
     *
     * @return The path template associated with the given request/response, or null if the path template could
     * not be determined. e.g.: <code>/foo/bar/{id}</code>
     */
    public abstract @Nullable String getRequestUriPathTemplate(@Nullable REQ request, @Nullable RES response);

    /**
     * @param response The response object to be inspected - <b>this may be null!</b>
     *
     * @return The HTTP status code (e.g. 200, 400, 503, etc) for the given response, or null if the HTTP status code
     * could not be determined.
     */
    public abstract @Nullable Integer getResponseHttpStatus(@Nullable RES response);

    /**
     * @param request The request object to be inspected - <b>this may be null!</b>
     *
     * @return The HTTP method (e.g. "GET", "POST", etc) for the given request, or null if the HTTP method could not
     * be determined.
     */
    public abstract @Nullable String getRequestHttpMethod(@Nullable REQ request);

    /**
     * @param request The request object to be inspected - <b>this may be null!</b>
     * @param headerKey The header key (name) to look for - should never be null.
     *
     * @return The single header value associated with the given header key, or null if no such header exists for
     * the given request. If there are multiple values associated with the header key, then only the first one will
     * be returned.
     */
    public abstract @Nullable String getHeaderSingleValue(@Nullable REQ request, @NotNull String headerKey);

    /**
     * @param request The request object to be inspected - <b>this may be null!</b>
     * @param headerKey The header key (name) to look for - should never be null.
     *
     * @return All the header values associated with the given header key, or null if no such header exists for the
     * given request. Implementations may choose to return an empty list instead of null if no such header exists.
     */
    public abstract @Nullable List<String> getHeaderMultipleValue(@Nullable REQ request, @NotNull String headerKey);

    /**
     * @param request The request object to be inspected - <b>this may be null!</b>
     *
     * @return The desired prefix that should be prepended before any span name produced by {@link
     * #getInitialSpanName(Object)} or {@link #getFinalSpanName(Object, Object)}, or null if no prefix is desired.
     * Defaults to null - if you want a non-null prefix you'll need to override this method.
     */
    public @Nullable String getSpanNamePrefix(@Nullable REQ request) {
        return null;
    }

    /**
     * By default this method uses a combination of {@link #getSpanNamePrefix(Object)} and {@link
     * HttpRequestTracingUtils#generateSafeSpanName(Object, Object, HttpTagAndSpanNamingAdapter)} to generate a name.
     * You can override this method if you need different behavior.
     *
     * @param request The request object to be inspected - <b>this may be null!</b>
     *
     * @return The initial span name that this adapter wants to use for a span around the given request, or null
     * if this adapter can't (or doesn't want to) come up with a name. Callers should always check for a null return
     * value, and come up with a reasonable fallback name in that case.
     */
    public @Nullable String getInitialSpanName(@Nullable REQ request) {
        String prefix = getSpanNamePrefix(request);

        String defaultSpanName = HttpRequestTracingUtils.generateSafeSpanName(request, null, this);

        return (StringUtils.isBlank(prefix))
               ? defaultSpanName
               : prefix + "-" + defaultSpanName;
    }

    /**
     * By default this method uses a combination of {@link #getSpanNamePrefix(Object)} and {@link
     * HttpRequestTracingUtils#generateSafeSpanName(Object, Object, HttpTagAndSpanNamingAdapter)} to generate a name.
     * You can override this method if you need different behavior.
     *
     * @param request The request object to be inspected - <b>this may be null!</b>
     * @param response The response object to be inspected - <b>this may be null!</b>
     *
     * @return The final span name that this adapter wants to use for a span around the given request, or null
     * if this adapter can't (or doesn't want to) come up with a name. Callers should always check for a null return
     * value, and should not change the preexisting initial span name if this returns null.
     */
    public @Nullable String getFinalSpanName(@Nullable REQ request, @Nullable RES response) {
        String prefix = getSpanNamePrefix(request);

        String defaultSpanName = HttpRequestTracingUtils.generateSafeSpanName(request, response, this);

        return (StringUtils.isBlank(prefix))
               ? defaultSpanName
               : prefix + "-" + defaultSpanName;
    }

    /**
     * @return The value that should be used for the {@link WingtipsTags#SPAN_HANDLER} tag, or null if you don't want
     * that tag added to spans. See the javadocs for {@link WingtipsTags#SPAN_HANDLER} for more details on what this
     * value should look like.
     */
    public abstract @Nullable String getSpanHandlerTagValue(@Nullable REQ request, @Nullable RES response);
}
