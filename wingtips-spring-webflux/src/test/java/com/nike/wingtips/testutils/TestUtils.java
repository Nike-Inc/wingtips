package com.nike.wingtips.testutils;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Some util methods to ease testing.
 *
 * @author Nic Munroe
 */
public class TestUtils {

    public static void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
        Tracer.getInstance().removeAllSpanLifecycleListeners();
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
    }

}
