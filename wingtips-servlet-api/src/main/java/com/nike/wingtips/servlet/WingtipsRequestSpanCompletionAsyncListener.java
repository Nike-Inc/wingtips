package com.nike.wingtips.servlet;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.TracingState;

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.runnableWithTracing;

/**
 * Helper class for {@link RequestTracingFilter} that implements {@link AsyncListener}, whose job is to complete the
 * overall request span when an async servlet request finishes. You should not need to worry about this class - it
 * is an internal implementation detail for {@link RequestTracingFilter}.
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
class WingtipsRequestSpanCompletionAsyncListener implements AsyncListener {

    protected final TracingState originalRequestTracingState;
    protected boolean alreadyCompleted = false;

    WingtipsRequestSpanCompletionAsyncListener(TracingState originalRequestTracingState) {
        this.originalRequestTracingState = originalRequestTracingState;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        completeRequestSpan();
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        completeRequestSpan();
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        completeRequestSpan();
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

    protected void completeRequestSpan() {
        // Async servlet stuff can trigger multiple completion methods depending on how the request is processed,
        //      but we only care about the first.
        if (alreadyCompleted) {
            return;
        }

        try {
            //noinspection deprecation
            runnableWithTracing(
                new Runnable() {
                    @Override
                    public void run() {
                        Tracer.getInstance().completeRequestSpan();
                    }
                },
                originalRequestTracingState
            ).run();
        }
        finally {
            alreadyCompleted = true;
        }
    }
}
