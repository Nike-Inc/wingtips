/*
 * Copyright 2016-2017 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.nike.wingtips.opentracing;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;

/**
 * @author Ales Justin
 */
public class TestUtils {
    public static class SpanRecorder implements SpanLifecycleListener {

        public final List<com.nike.wingtips.Span> completedSpans = new ArrayList<>();

        @Override
        public void spanStarted(com.nike.wingtips.Span span) { }

        @Override
        public void spanSampled(com.nike.wingtips.Span span) { }

        @Override
        public void spanCompleted(com.nike.wingtips.Span span) {
            completedSpans.add(span);
        }
    }

    public static void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();

        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            Tracer.getInstance().removeSpanLifecycleListener(listener);
        }
    }

    public static long currentTimeMacros() {
        return toMacros(System.currentTimeMillis());
    }

    public static long toMacros(long millis) {
        return TimeUnit.MILLISECONDS.toMicros(millis);
    }

    public static long toFinishMacros(Span span) {
        return span.getSpanStartTimeEpochMicros() + TimeUnit.NANOSECONDS.toMicros(span.getDurationNanos());
    }
}
