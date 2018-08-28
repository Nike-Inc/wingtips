package com.nike.wingtips.tags;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;
import com.nike.wingtips.SpanMutator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * There are many libraries that facilitate HTTP Requests. This abstract class allows for a consistent approach to
 * naming and tagging a span with request and response details without having knowledge of the underlying libraries
 * facilitating the request/response.
 *
 * <p>Callers interface with the following public methods, which are final and surrounded with try/catch to avoid
 * having implementation exceptions bubble out (errors are logged, but do not propagate up to callers):
 * <ul>
 *     <li>{@link #getInitialSpanName(Object, HttpTagAndSpanNamingAdapter)}</li>
 *     <li>{@link #handleRequestTagging(Span, Object, HttpTagAndSpanNamingAdapter)}</li>
 *     <li>
 *         {@link #handleResponseTaggingAndFinalSpanName(Span, Object, Object, Throwable, HttpTagAndSpanNamingAdapter)}
 *     </li>
 * </ul>
 *
 * <p>Besides the try/catch, those caller-facing methods don't do anything themselves other than delegate to protected
 * (overrideable) methods that are intended for concrete implementations to flesh out. Some of those protected methods
 * are abstract and *must* be implemented, others have a default implementation that should serve for most use cases,
 * but are still overrideable if needed.
 *
 * <p>From a caller's standpoint, integration with this class is typically done in a request/response interceptor or
 * filter, in a pattern that looks something like:
 * <pre>
 * HttpTagAndSpanNamingStrategy&lt;RequestObj, ResponseObj> tagAndSpanNamingStrategy = ...;
 * HttpTagAndSpanNamingAdapter&lt;RequestObj, ResponseObj> tagAndSpanNamingAdapter = ...;
 *
 * public ResponseObj interceptRequest(RequestObj request) {
 *     // This code assumes you're surrounding the call with a span.
 *     Span spanAroundCall = generateSpanAroundCall(
 *         // Use the tag/name strategy's getInitialSpanName() method to generate the span name.
 *         tagAndSpanNamingStrategy.getInitialSpanName(request, tagAndSpanNamingAdapter)
 *     );
 *
 *     Throwable errorForTagging = null;
 *     ResponseObj response = null;
 *     try {
 *         // Do the request tagging.
 *         tagAndSpanNamingStrategy.handleRequestTagging(spanAroundCall, request, tagAndSpanNamingAdapter);
 *
 *         // Keep a handle on the response for later so we can do response tagging and final span name.
 *         response = executeRequest(request);
 *
 *         return response;
 *     } catch(Throwable exception) {
 *         // Keep a handle on any error that gets thrown so it can contribute to the response tagging,
 *         //      but otherwise throw the exception like normal.
 *         errorForTagging = exception;
 *
 *         throw exception;
 *     }
 *     finally {
 *         try {
 *             // Handle response/error tagging and final span name.
 *             tagAndSpanNamingStrategy.handleResponseTaggingAndFinalSpanName(
 *                 spanAroundCall, request, response, errorForTagging, tagAndSpanNamingAdapter
 *             );
 *         }
 *         finally {
 *             // Span.close() contains the span-finishing logic we want - if the spanAroundCall was an overall span
 *             //      (new trace) then tracer.completeRequestSpan() will be called, otherwise it's a subspan and
 *             //      tracer.completeSubSpan() will be called.
 *             spanAroundCall.close();
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>Async request/response scenarios will look different, and/or the framework may require a different solution where
 * it's not a simple single method call that you surround with the naming and tagging logic, but the critical pieces
 * should always be the same:
 * <ol>
 *     <li>
 *         Call {@link #getInitialSpanName(Object, HttpTagAndSpanNamingAdapter)} to generate the initial span name.
 *         That method can return null, so you should have a backup naming strategy in case null is returned.
 *     </li>
 *     <li>Call {@link #handleRequestTagging(Span, Object, HttpTagAndSpanNamingAdapter)} before making the request.</li>
 *     <li>Keep track of the request, response, and any exception that gets thrown, so they can be used later.</li>
 *     <li>
 *         After the response finishes, or an exception is thrown that stops the request from completing normally, then
 *         call {@link
 *         #handleResponseTaggingAndFinalSpanName(Span, Object, Object, Throwable, HttpTagAndSpanNamingAdapter)},
 *         passing in the request, response, and exception (if any) that you've kept track of.
 *     </li>
 *     <li>Use try, catch, and finally blocks to keep things safe and contained in case things go wrong.</li>
 * </ol>
 *
 * @param <REQ> The object type representing the http request
 * @param <RES> The object type representing the http response
 *
 * @author Brandon Currie
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
public abstract class HttpTagAndSpanNamingStrategy<REQ, RES> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Handles tagging the given span with tags related to the given request, according to this tag strategy's
     * requirements.
     *
     * <p>This method does the actual request-tagging work for the public-facing {@link
     * #handleRequestTagging(Span, Object, HttpTagAndSpanNamingAdapter)}.
     *
     * @param span The span to tag - will never be null.
     * @param request The incoming request - will never be null.
     * @param adapter The adapter to handle the incoming request - will never be null.
     */
    protected abstract void doHandleRequestTagging(
        @NotNull Span span,
        @NotNull REQ request,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, ?> adapter
    );

    /**
     * Handles tagging the given span with tags related to the given response and/or error, according to this
     * tag strategy's requirements.
     *
     * <p>This method does the actual response-and-error-tagging work for the public-facing {@link
     * #handleResponseTaggingAndFinalSpanName(Span, Object, Object, Throwable, HttpTagAndSpanNamingAdapter)}.
     *
     * @param span The span to name - will never be null.
     * @param request The request object - this may be null.
     * @param response The response object - this may be null.
     * @param error The error that prevented the request/response from completing normally, or null if no such error
     * occurred.
     * @param adapter The adapter to handle the request and response - will never be null.
     */
    protected abstract void doHandleResponseAndErrorTagging(
        @NotNull Span span,
        @Nullable REQ request,
        @Nullable RES response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    );

    /**
     * Returns the span name that should be used for the given request according to this strategy and/or the given
     * adapter. By default this will delegate to the adapter's {@link
     * HttpTagAndSpanNamingAdapter#getInitialSpanName(Object)} unless this strategy overrides
     * {@link #doGetInitialSpanName(Object, HttpTagAndSpanNamingAdapter)}.
     *
     * <p><b>NOTE: This method may return null if the strategy and adapter have no opinion, or if something goes wrong.
     * Callers must always check for a null return value, and generate a backup span name in those cases.</b>
     *
     * <p>This method is final and delegates to {@link #doGetInitialSpanName(Object, HttpTagAndSpanNamingAdapter)}.
     * That delegate method call is surrounded with a try/catch so that it this method will never throw an exception.
     * If an exception occurs then the error will be logged and null will be returned. Since this method is final, if
     * you want to override the behavior of this method then you should override {@link
     * #doGetInitialSpanName(Object, HttpTagAndSpanNamingAdapter)}.
     *
     * @param request The incoming request - should never be null.
     * @param adapter The adapter to handle the incoming request - should never be null (use {@link NoOpHttpTagAdapter}
     * if you don't want a real impl).
     *
     * @return The span name that should be used for the given request according to this strategy and/or the given
     * adapter, or null if the span name should be deferred to the caller.
     */
    public final @Nullable String getInitialSpanName(
        @NotNull REQ request,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, ?> adapter
    ) {
        //noinspection ConstantConditions
        if (request == null || adapter == null) {
            return null;
        }

        try {
            return doGetInitialSpanName(request, adapter);
        }
        catch (Throwable t) {
            // Impl methods should never throw an exception. If you're seeing this error pop up, the impl needs to
            //      be fixed.
            logger.error(
                "An unexpected error occurred while getting the initial span name. The error will be swallowed to "
                + "avoid doing any damage and null will be returned, but your span name may not be what you expect. "
                + "This error should be fixed.",
                t
            );
            return null;
        }
    }

    /**
     * Handles tagging the given span with tags related to the given request based on rules specific to this
     * particular class' implementation.
     *
     * <p>This method is final and delegates to {@link
     * #doHandleRequestTagging(Span, Object, HttpTagAndSpanNamingAdapter)}. That delegate method call is surrounded
     * with a try/catch so that it this method will never throw an exception. If an exception occurs then the error
     * will be logged but will not propagate outside this method. Since this method is final, if you want to override
     * the behavior of this method then you should override {@link
     * #doHandleRequestTagging(Span, Object, HttpTagAndSpanNamingAdapter)}.
     *
     * @param span The span to tag - should never be null.
     * @param request The incoming request - should never be null.
     * @param adapter The adapter to handle the incoming request - should never be null (use {@link NoOpHttpTagAdapter}
     * if you don't want a real impl).
     */
    public final void handleRequestTagging(
        @NotNull Span span,
        @NotNull REQ request,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, ?> adapter
    ) {
        //noinspection ConstantConditions
        if (span == null || request == null || adapter == null) {
            return;
        }

        try {
            doHandleRequestTagging(span, request, adapter);
        }
        catch (Throwable t) {
            // Impl methods should never throw an exception. If you're seeing this error pop up, the impl needs to
            //      be fixed.
            logger.error(
                "An unexpected error occurred while handling request tagging. The error will be swallowed to avoid "
                + "doing any damage, but your span may be missing some expected tags. This error should be fixed.",
                t
            );
        }
    }

    /**
     * Handles tagging the given span with tags related to the given response and/or error, and also handles setting
     * the span's final span name.
     *
     * <p>The span name is finalized here after the response because the ideal span name includes the low-cardinality
     * HTTP path template (e.g. {@code /foo/:id} rather than {@code /foo/12345}), however in many frameworks you don't
     * know the path template until after the request has been processed. So calling this method may cause the span's
     * {@link Span#getSpanName()} to change.
     *
     * <p>This method is final and delegates to the following methods to do the actual work:
     * <ul>
     *     <li>
     *         {@link #doHandleResponseAndErrorTagging(Span, Object, Object, Throwable, HttpTagAndSpanNamingAdapter)}
     *     </li>
     *     <li>
     *         {@link #doDetermineAndSetFinalSpanName(Span, Object, Object, Throwable, HttpTagAndSpanNamingAdapter)}
     *     </li>
     *     <li>{@link #doExtraWingtipsTagging(Span, Object, Object, Throwable, HttpTagAndSpanNamingAdapter)}</li>
     * </ul>
     *
     * Those delegate method calls are surrounded with try/catch blocks so that it this method will never throw an
     * exception, and an exception in one won't prevent the others from executing. If an exception occurs then the
     * error will be logged but will not propagate outside this method. Since this method is final, if you want to
     * override the behavior of this method then you should override the relevant delegate method(s).
     *
     * @param span The span to tag - should never be null.
     * @param request The request object - this can be null if you don't have it anymore when this method is called,
     * however you should pass it if at all possible as it may be critical to determining the final span name.
     * @param response The response object - this can be null if an exception was thrown (thus preventing the response
     * object from being created).
     * @param error The error that prevented the request/response from completing normally, or null if no such error
     * occurred.
     * @param adapter The adapter to handle the request and response - should never be null (use
     * {@link NoOpHttpTagAdapter} if you don't want a real impl).
     */
    public final void handleResponseTaggingAndFinalSpanName(
        @NotNull Span span,
        @Nullable REQ request,
        @Nullable RES response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    ) {
        //noinspection ConstantConditions
        if (span == null || adapter == null) {
            return;
        }

        try {
            doHandleResponseAndErrorTagging(span, request, response, error, adapter);
        }
        catch (Throwable t) {
            // Impl methods should never throw an exception. If you're seeing this error pop up, the impl needs to
            //      be fixed.
            logger.error(
                "An unexpected error occurred while handling response tagging. The error will be swallowed to avoid "
                + "doing any damage, but your span may be missing some expected tags. This error should be fixed.",
                t
            );
        }

        try {
            doDetermineAndSetFinalSpanName(span, request, response, error, adapter);
        }
        catch (Throwable t) {
            // Impl methods should never throw an exception. If you're seeing this error pop up, the impl needs to
            //      be fixed.
            logger.error(
                "An unexpected error occurred while finalizing the span name. The error will be swallowed to avoid "
                + "doing any damage, but the final span name may not be what you expect. This error should be fixed.",
                t
            );
        }

        try {
            doExtraWingtipsTagging(span, request, response, error, adapter);
        }
        catch (Throwable t) {
            // Impl methods should never throw an exception. If you're seeing this error pop up, the impl needs to
            //      be fixed.
            logger.error(
                "An unexpected error occurred while doing Wingtips tagging. The error will be swallowed to avoid "
                + "doing any damage, but the final span name may not be what you expect. This error should be fixed.",
                t
            );
        }
    }

    /**
     * Returns the span name that should be used for the given request according to this strategy and/or the given
     * adapter. By default this will delegate to the adapter's {@link
     * HttpTagAndSpanNamingAdapter#getInitialSpanName(Object)} unless you override this method.
     *
     * <p><b>NOTE: This method may return null if the strategy and adapter have no opinion, or if something goes wrong.
     * Callers must always check for a null return value, and generate a backup span name in those cases.</b>
     *
     * <p>This method does the actual work for the public-facing {@link
     * #getInitialSpanName(Object, HttpTagAndSpanNamingAdapter)}.
     *
     * @param request The incoming request - will never be null.
     * @param adapter The adapter to handle the incoming request - will never be null.
     *
     * @return The span name that should be used for the given request according to this strategy and/or the given
     * adapter, or null if the span name should be deferred to the caller.
     */
    protected @Nullable String doGetInitialSpanName(
        @NotNull REQ request,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, ?> adapter
    ) {
        return adapter.getInitialSpanName(request);
    }

    /**
     * Determines the final span name that should be used for the given request/response according to this strategy
     * and/or the given adapter, and then calls {@link SpanMutator#changeSpanName(Span, String)} to actually change
     * the span name (if and only if a non-blank final span name is generated).
     *
     * <p>By default this will delegate to the adapter's {@link
     * HttpTagAndSpanNamingAdapter#getFinalSpanName(Object, Object)} for generating the final span name unless you
     * override this method.
     *
     * <p>This method does the actual final-span-naming work for the public-facing {@link
     * #handleResponseTaggingAndFinalSpanName(Span, Object, Object, Throwable, HttpTagAndSpanNamingAdapter)}.
     *
     * @param span The span to name - will never be null.
     * @param request The request object - this may be null.
     * @param response The response object - this may be null.
     * @param error The error that prevented the request/response from completing normally, or null if no such error
     * occurred.
     * @param adapter The adapter to handle the request and response - will never be null.
     */
    protected void doDetermineAndSetFinalSpanName(
        @NotNull Span span,
        @Nullable REQ request,
        @Nullable RES response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    ) {
        String finalSpanName = adapter.getFinalSpanName(request, response);

        if (StringUtils.isNotBlank(finalSpanName)) {
            SpanMutator.changeSpanName(span, finalSpanName);
        }
    }

    /**
     * Adds any extra tags that Wingtips wants to add that aren't normally a part of specific tag strategies like
     * Zipkin or OpenTracing.
     *
     * <p>By default this will add the {@link WingtipsTags#SPAN_HANDLER} tag with the value that comes from
     * {@link HttpTagAndSpanNamingAdapter#getSpanHandlerTagValue(Object, Object)}.
     *
     * <p>This method does the actual extra-wingtips-tagging work for the public-facing {@link
     * #handleResponseTaggingAndFinalSpanName(Span, Object, Object, Throwable, HttpTagAndSpanNamingAdapter)}.
     *
     * @param span The span to tag - will never be null.
     * @param request The request object - this may be null.
     * @param response The response object - this may be null.
     * @param error The error that prevented the request/response from completing normally, or null if no such error
     * occurred.
     * @param adapter The adapter to handle the request and response - will never be null.
     */
    @SuppressWarnings("unused")
    protected void doExtraWingtipsTagging(
        @NotNull Span span,
        @Nullable REQ request,
        @Nullable RES response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    ) {
        putTagIfValueIsNotBlank(span, WingtipsTags.SPAN_HANDLER, adapter.getSpanHandlerTagValue(request, response));
    }

    /**
     * A helper method that can be used by subclasses for putting a tag value on the given span (via {@link
     * Span#putTag(String, String)}) if and only if the tag value is not null and its {@link Object#toString()} is not
     * blank (according to {@link StringUtils#isBlank(CharSequence)}).
     *
     * @param span The span to tag - should never be null.
     * @param tagKey The key to use when calling {@link Span#putTag(String, String)} - should never be null.
     * @param tagValue The tag value to use if and only if it is not null and its {@link Object#toString()} is not
     * blank.
     */
    protected void putTagIfValueIsNotBlank(
        @NotNull Span span,
        @NotNull String tagKey,
        @Nullable Object tagValue
    ) {
        //noinspection ConstantConditions
        if (tagValue == null || span == null || tagKey == null) {
            return;
        }

        // tagValue is not null. Convert to string and check for blank.
        String tagValueString = tagValue.toString();

        if (StringUtils.isBlank(tagValueString)) {
            return;
        }

        // tagValue is not blank. Add it to the given span.
        span.putTag(tagKey, tagValueString);
    }

}
