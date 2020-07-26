package com.nike.wingtips.testutil;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;

import org.slf4j.MDC;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Some util methods to ease testing.
 */
public class TestUtils {

    public static void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
        Tracer.getInstance().removeAllSpanLifecycleListeners();
    }

    public static void sleepThread(long sleepTimeMillis) {
        try {
            Thread.sleep(sleepTimeMillis);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class SpanRecorder implements SpanLifecycleListener {

        public final List<Span> completedSpans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void spanStarted(Span span) { }

        @Override
        public void spanSampled(Span span) { }

        @Override
        public void spanCompleted(Span span) {
            // Create a copy so we know what the span looked like exactly when it was completed (in case other tags
            //      are added after completion, for example).
            completedSpans.add(Span.newBuilder(span).build());
        }

        public void waitUntilSpanRecorderHasExpectedNumSpans(int expectedNumSpans, Duration timeout) {
            long timeoutMillis = timeout.toMillis();
            long startTimeMillis = System.currentTimeMillis();
            while (completedSpans.size() < expectedNumSpans) {
                sleepThread(10);

                long timeSinceStart = System.currentTimeMillis() - startTimeMillis;
                if (timeSinceStart > timeoutMillis) {
                    throw new RuntimeException(
                        "spanRecorder did not have the expected number of spans after waiting "
                        + timeoutMillis + " milliseconds"
                    );
                }
            }
        }
    }

}
