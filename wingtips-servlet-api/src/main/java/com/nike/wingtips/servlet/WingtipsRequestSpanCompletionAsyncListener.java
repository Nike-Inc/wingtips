package com.nike.wingtips.servlet;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.ServletRuntime.Servlet3Runtime;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.NoOpHttpTagAdapter;
import com.nike.wingtips.tags.NoOpHttpTagStrategy;
import com.nike.wingtips.util.TracingState;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.runnableWithTracing;

/**
 * Helper class for {@link Servlet3Runtime} that implements {@link AsyncListener}, whose job is to complete the
 * overall request span when an async servlet request finishes. You should not need to worry about this class - it
 * is an internal implementation detail for {@link Servlet3Runtime}.
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
class WingtipsRequestSpanCompletionAsyncListener implements AsyncListener {

    protected final TracingState originalRequestTracingState;
    // Used to prevent two threads from trying to close the span at the same time 
    protected final AtomicBoolean alreadyCompleted = new AtomicBoolean(false);
    protected final HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> tagAndNamingStrategy;
    protected final HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> tagAndNamingAdapter;

    WingtipsRequestSpanCompletionAsyncListener(
        TracingState originalRequestTracingState,
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> tagAndNamingStrategy,
        HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> tagAndNamingAdapter
    ) {
        this.originalRequestTracingState = originalRequestTracingState;

        // We should never get a null tag strategy or tag adapter in reality, but just in case we do, default to
        //      the no-op impls.
        if (tagAndNamingStrategy == null) {
            tagAndNamingStrategy = NoOpHttpTagStrategy.getDefaultInstance();
        }

        if (tagAndNamingAdapter == null) {
            tagAndNamingAdapter = NoOpHttpTagAdapter.getDefaultInstance();
        }

        this.tagAndNamingStrategy = tagAndNamingStrategy;
        this.tagAndNamingAdapter = tagAndNamingAdapter;
    }

    @Override
    public void onComplete(AsyncEvent event) {
        completeRequestSpan(event);
    }

    @Override
    public void onTimeout(AsyncEvent event) {
        /*
            NOTE: Unfortunately the response in the event won't have had its final HTTP status code set yet, so if we
            call completeRequestSpan() here, we'll get an incorrect HTTP status code tag (and possibly incorrect
            final span name since it can sometimes be affected by HTTP status code).

            According to the Servlet 3 specification
            (http://download.oracle.com/otn-pub/jcp/servlet-3.0-fr-eval-oth-JSpec/servlet-3_0-final-spec.pdf),
            onComplete() should always be called by the container even in the case of timeout or error, and the final
            HTTP status code should be set by then. So we'll just defer to onComplete() for finalizing the span and do
            nothing here.

            See sections 2.3.3.3 of the Servlet 3 specification for more details.
        */
    }

    @Override
    public void onError(AsyncEvent event) {
        /*
            NOTE: Unfortunately the response in the event won't have had its final HTTP status code set yet, so if we
            call completeRequestSpan() here, we'll get an incorrect HTTP status code tag (and possibly incorrect
            final span name since it can sometimes be affected by HTTP status code).

            According to the Servlet 3 specification
            (http://download.oracle.com/otn-pub/jcp/servlet-3.0-fr-eval-oth-JSpec/servlet-3_0-final-spec.pdf),
            onComplete() should always be called by the container even in the case of timeout or error, and the final
            HTTP status code should be set by then. So we'll just defer to onComplete() for finalizing the span and do
            nothing here.

            See sections 2.3.3.3 of the Servlet 3 specification for more details.
        */
    }

    @Override
    public void onStartAsync(AsyncEvent event) {
        // Another async event was started (e.g. via asyncContext.dispatch(...), which means this listener was
        //      removed and won't be called on completion unless we re-register (as per the javadocs for this
        //      method from the interface).
        AsyncContext eventAsyncContext = event.getAsyncContext();
        if (eventAsyncContext != null) {
            eventAsyncContext.addListener(this, event.getSuppliedRequest(), event.getSuppliedResponse());
        }
    }

    /**
     * Does the work of doing the final span tagging and naming, and completes the span for {@link
     * #originalRequestTracingState}. The request, response, and any error needed for the tagging/naming are pulled
     * from the given {@link AsyncEvent}. This method is configured to only ever execute once (via the
     * {@link #alreadyCompleted} atomic boolean) - subsequent calls will return immediately without doing anything.
     *
     * @param event The {@link AsyncEvent} that triggered finalizing the request span.
     */
    @SuppressWarnings("deprecation")
    protected void completeRequestSpan(AsyncEvent event) {
        // Async servlet stuff can trigger multiple completion methods depending on how the request is processed,
        //      but we only care about the first.
        if (alreadyCompleted.getAndSet(true)) {
            return;
        }

        ServletRequest request = event.getSuppliedRequest();
        ServletResponse response = event.getSuppliedResponse();

        final HttpServletRequest httpRequest = (request instanceof HttpServletRequest)
                                         ? (HttpServletRequest) request
                                         : null;
        final HttpServletResponse httpResponse = (response instanceof HttpServletResponse)
                                           ? (HttpServletResponse) response
                                           : null;
        final Throwable error = event.getThrowable();

        // Reattach the original tracing state and handle span finalization/completion.
        //noinspection deprecation
        runnableWithTracing(
            new Runnable() {
                @Override
                public void run() {
                    Span span = Tracer.getInstance().getCurrentSpan();

                    try {
                        // Handle response/error tagging and final span name.
                        tagAndNamingStrategy.handleResponseTaggingAndFinalSpanName(
                            span, httpRequest, httpResponse, error, tagAndNamingAdapter
                        );
                    }
                    finally {
                        // Complete the overall request span.
                        Tracer.getInstance().completeRequestSpan();
                    }
                }
            },
            originalRequestTracingState
        ).run();
    }
}
