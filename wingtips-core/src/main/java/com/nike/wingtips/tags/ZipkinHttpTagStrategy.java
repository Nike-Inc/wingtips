package com.nike.wingtips.tags;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Applies {@code Zipkin} standard tags for:
 * <ul>
 *     <li>http.method</li>
 *     <li>http.path</li>
 *     <li>http.url</li>
 *     <li>http.route</li>
 *     <li>http.status_code</li>
 *     <li>error</li>
 * </ul>
 *
 * The following known Zipkin tags are <strong>unimplemented</strong> in this strategy:
 * <ul>
 *     <li>http.request.size</li>
 *     <li>http.response.size</li>
 *     <li>http.host</li>
 * </ul>
 *
 * @param <REQ> The expected request object type to be inspected
 * @param <RES> The expected response object type to be inspected
 *
 * @see <a href='https://github.com/openzipkin/brave/tree/master/instrumentation/http#span-data-policy'>Zipkin's Span Data Policy</a>
 *
 * @author Brandon Currie
 * @author Nic Munroe
 */
public class ZipkinHttpTagStrategy<REQ, RES> extends HttpTagAndSpanNamingStrategy<REQ, RES> {

    @SuppressWarnings("WeakerAccess")
    protected static final ZipkinHttpTagStrategy<?, ?> DEFAULT_INSTANCE = new ZipkinHttpTagStrategy<>();

    /**
     * @return A reusable, thread-safe, singleton instance of this class that can be used by anybody who wants to use
     * this class and does not need any customization.
     */
    @SuppressWarnings("unchecked")
    public static <REQ, RES> ZipkinHttpTagStrategy<REQ, RES> getDefaultInstance() {
        return (ZipkinHttpTagStrategy<REQ, RES>) DEFAULT_INSTANCE;
    }

    @Override
    protected void doHandleRequestTagging(
        @NotNull Span span,
        @NotNull REQ request,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, ?> adapter
    ) {
        putTagIfValueIsNotBlank(span, KnownZipkinTags.HTTP_METHOD, adapter.getRequestHttpMethod(request));
        putTagIfValueIsNotBlank(span, KnownZipkinTags.HTTP_PATH, adapter.getRequestPath(request));
        putTagIfValueIsNotBlank(span, KnownZipkinTags.HTTP_URL, adapter.getRequestUrl(request));
        putTagIfValueIsNotBlank(span, KnownZipkinTags.HTTP_ROUTE, adapter.getRequestUriPathTemplate(request, null));
    }

    @Override
    protected void doHandleResponseAndErrorTagging(
        @NotNull Span span,
        @Nullable REQ request,
        @Nullable RES response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    ) {
        // Now that we have both request and response, we'll re-try to get the route.
        putTagIfValueIsNotBlank(span, KnownZipkinTags.HTTP_ROUTE, adapter.getRequestUriPathTemplate(request, response));

        putTagIfValueIsNotBlank(span, KnownZipkinTags.HTTP_STATUS_CODE, adapter.getResponseHttpStatus(response));

        // For error tagging, we'll defer to the error Throwable if it's not null.
        if (error != null) {
            String message = error.getMessage();
            if (message == null) {
                message = error.getClass().getSimpleName();
            }
            addErrorTagToSpan(span, message);
        }
        else {
            // The error Throwable was null, so we'll see if the adapter thinks this is an error response.
            String errorTagValue = adapter.getErrorResponseTagValue(response);
            if (StringUtils.isNotBlank(errorTagValue)) {
                addErrorTagToSpan(span, errorTagValue);
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void addErrorTagToSpan(Span span, String errorTagValue) {
        putTagIfValueIsNotBlank(span, KnownZipkinTags.ERROR, errorTagValue);
    }
}
