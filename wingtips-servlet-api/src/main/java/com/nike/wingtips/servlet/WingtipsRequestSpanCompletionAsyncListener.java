package com.nike.wingtips.servlet;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.runnableWithTracing;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.ServletRuntime.Servlet3Runtime;
import com.nike.wingtips.tags.HttpTagStrategy;
import com.nike.wingtips.util.TracingState;

/**
 * Helper class for {@link Servlet3Runtime} that implements {@link AsyncListener}, whose job is to complete the
 * overall request span when an async servlet request finishes. You should not need to worry about this class - it
 * is an internal implementation detail for {@link Servlet3Runtime}.
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
class WingtipsRequestSpanCompletionAsyncListener implements AsyncListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    protected final TracingState originalRequestTracingState;
    // Used to prevent two threads from trying to close the span at the same time 
    protected AtomicBoolean alreadyCompleted = new AtomicBoolean(false);
    protected HttpTagStrategy<HttpServletRequest, HttpServletResponse> tagStrategy;
    
    WingtipsRequestSpanCompletionAsyncListener(TracingState originalRequestTracingState, HttpTagStrategy<HttpServletRequest, HttpServletResponse> tagStrategy ) {
        this.originalRequestTracingState = originalRequestTracingState;
        this.tagStrategy = tagStrategy;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        tagSpanWithResponseAttributesAndComplete(event.getSuppliedResponse());
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
            tagCurrentSpanAsErrdAndComplete(event.getThrowable());
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
            tagCurrentSpanAsErrdAndComplete(event.getThrowable());
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
        // Another async event was started (e.g. via asyncContext.dispatch(...), which means this listener was
        //      removed and won't be called on completion unless we re-register (as per the javadocs for this
        //      method from the interface).
        AsyncContext eventAsyncContext = event.getAsyncContext();
        if (eventAsyncContext != null) {
            eventAsyncContext.addListener(this);
        }
    }
    
    /**
     * The response object available from {@code AsyncContext#getResponse()} is only 
     * guaranteed to be a {@code ServletResponse} but it <em>should</em> be an instance of
     * {@code HttpServletResponse}.
     * 
     * @param response
     */
    @SuppressWarnings("deprecation")
    protected void tagSpanWithResponseAttributesAndComplete(final ServletResponse response) {
        // Async servlet stuff can trigger multiple completion methods depending on how the request is processed,
        //      but we only care about the first.
        if (alreadyCompleted.getAndSet(true)) {
            return;
        }

        //noinspection deprecation
        runnableWithTracing(
            new Runnable() {
                @Override
                public void run() {
                    tagSpanWithResponseAttributes(response);
                    Tracer.getInstance().completeRequestSpan();
                }
                
                /**
                 * Broken out as a separate method so we can surround it in a try{} to ensure we don't break the overall
                 * span handling with exceptions from the {@code tagStrategy}.
                 * @param span The span to be tagged
                 * @param responseObj The response context to be used for tag values
                 */
                private void tagSpanWithResponseAttributes(ServletResponse responseObj) {
                    if(response instanceof HttpServletResponse) {
                        try {
                            Span span = Tracer.getInstance().getCurrentSpan();
                            tagStrategy.tagSpanWithResponseAttributes(span, (HttpServletResponse)responseObj);
                        } catch(Throwable taggingException) {
                            logger.warn("Unable to tag span with response attributes", taggingException);
                        }
                    }
                }
            },
            originalRequestTracingState
        ).run();
    }
    
    @SuppressWarnings("deprecation")
    protected void tagCurrentSpanAsErrdAndComplete(final Throwable t) {
        // Async servlet stuff can trigger multiple completion methods depending on how the request is processed,
        //      but we only care about the first.
        if (alreadyCompleted.getAndSet(true)) {
            return;
        }
        
        //noinspection deprecation
        runnableWithTracing(
            new Runnable() {
                @Override
                public void run() {
                    handleErroredRequestTags(Tracer.getInstance().getCurrentSpan(), t);
                    Tracer.getInstance().completeRequestSpan();
                }
                
                /**
                 * Broken out as a separate method so we can surround it in a try{} to ensure we don't break the overall
                 * span handling with exceptions from the {@code tagStrategy}.
                 * @param span The span to be tagged
                 * @param throwable The exception context to use for tag values
                 */
                private void handleErroredRequestTags(Span span, Throwable throwable) {
                    try {
                        tagStrategy.handleErroredRequest(span, throwable);
                    } catch(Throwable taggingException) {
                        logger.warn("Unable to tag errored span with exception", taggingException);
                    }
                }
            },
            originalRequestTracingState
        ).run();
    }

}
