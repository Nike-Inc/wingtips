package com.nike.wingtips.tags;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In an effort to match tag patterns consistent with other implementations and with the OpenTracing standards, this class
 * is responsible for adding the following tags to the {@code Span}:
 * <ul>
 *     <li>http.url</li>
 *     <li>http.status_code</li>
 *     <li>http.method</li>
 *     <li>error</li>
 * </ul>
 *
 * These tag names are based on names provided via OpenTracing's
 * <a href="https://github.com/opentracing/opentracing-java/blob/master/opentracing-api/src/main/java/io/opentracing/tag/Tags.java">
 *     Tags.java
 * </a>
 */
public class OpenTracingHttpTagStrategy<REQ, RES> extends HttpTagAndSpanNamingStrategy<REQ, RES> {

    @SuppressWarnings("WeakerAccess")
    protected static final OpenTracingHttpTagStrategy<?, ?> DEFAULT_INSTANCE = new OpenTracingHttpTagStrategy<>();

    /**
     * @return A reusable, thread-safe, singleton instance of this class that can be used by anybody who wants to use
     * this class and does not need any customization.
     */
    @SuppressWarnings("unchecked")
    public static <REQ, RES> OpenTracingHttpTagStrategy<REQ, RES> getDefaultInstance() {
        return (OpenTracingHttpTagStrategy<REQ, RES>) DEFAULT_INSTANCE;
    }

    @Override
    protected void doHandleRequestTagging(
        @NotNull Span span,
        @NotNull REQ request,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, ?> adapter
    ) {
        putTagIfValueIsNotBlank(span, KnownOpenTracingTags.HTTP_METHOD, adapter.getRequestHttpMethod(request));
        putTagIfValueIsNotBlank(span, KnownOpenTracingTags.HTTP_URL, adapter.getRequestUrl(request));
    }

    @Override
    protected void doHandleResponseAndErrorTagging(
        @NotNull Span span,
        @Nullable REQ request,
        @Nullable RES response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    ) {
        putTagIfValueIsNotBlank(span, KnownOpenTracingTags.HTTP_STATUS, adapter.getResponseHttpStatus(response));

        if (error != null || StringUtils.isNotBlank(adapter.getErrorResponseTagValue(response))) {
            // OpenTracing doesn't expect you to pass messages with the error tag, just error=true.
            //      So we don't need to do anything with the given error Throwable or returned
            //      getErrorResponseTagValue(), other than have them trigger adding the error=true tag.
            span.putTag(KnownOpenTracingTags.ERROR, "true");
        }
    }
}
