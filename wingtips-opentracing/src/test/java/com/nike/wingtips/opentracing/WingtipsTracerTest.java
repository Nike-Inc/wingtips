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

import static com.nike.wingtips.opentracing.TestUtils.currentTimeMacros;
import static com.nike.wingtips.opentracing.TestUtils.resetTracing;
import static com.nike.wingtips.opentracing.TestUtils.toFinishMacros;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.nike.wingtips.http.RequestWithHeaders;
import com.nike.wingtips.http.ResponseWithHeaders;
import com.nike.wingtips.opentracing.propagation.HttpHeadersCodec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Ales Justin
 */
public class WingtipsTracerTest {
    private TestUtils.SpanRecorder spanRecorder = new TestUtils.SpanRecorder();

    @Before
    public void setup() {
        spanRecorder.completedSpans.clear();
        com.nike.wingtips.Tracer.getInstance().addSpanLifecycleListener(spanRecorder);
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    @Test
    public void testCurrentSpan() {
        WingtipsTracer tracer = new WingtipsTracer();
        Span span = tracer.buildSpan("tester").startManual();
        Scope scope = tracer.scopeManager().activate(span);
        assertSame(span, scope.span());
        scope.close();
        assertEquals(1, spanRecorder.completedSpans.size());
    }

    @Test
    public void testParentSpan() {
        WingtipsTracer tracer = new WingtipsTracer();
        Span root = tracer.buildSpan("root").startManual();
        tracer.scopeManager().activate(root);
        Scope scope = tracer.buildSpan("child1").startActive();
        scope.close();
        assertEquals(1, spanRecorder.completedSpans.size());
        assertEquals("child1", spanRecorder.completedSpans.get(0).getSpanName());
        root.finish();
        assertEquals(2, spanRecorder.completedSpans.size());
    }

    @Test
    public void testRootSpan() {
        // Create and finish a root Span.
        WingtipsTracer tracer = new WingtipsTracer();

        Span span = tracer.buildSpan("tester").withStartTimestamp(1000).startManual();
        span.setTag("string", "foo");
        span.setTag("int", 7);
        span.log("foo");
        Map<String, Object> fields = new HashMap<>();
        fields.put("f1", 4);
        fields.put("f2", "two");
        span.log(1002, fields);
        span.log(1003, "event name");
        span.finish(2000);

        List<com.nike.wingtips.Span> finishedSpans = spanRecorder.completedSpans;

        // Check that the Span looks right.
        assertEquals(1, finishedSpans.size());
        com.nike.wingtips.Span finishedSpan = finishedSpans.get(0);
        assertEquals("tester", finishedSpan.getSpanName());
        assertNull(finishedSpan.getParentSpanId());
        assertNotNull(finishedSpan.getTraceId());
        assertNotNull(finishedSpan.getSpanId());
        assertEquals(1000, finishedSpan.getSpanStartTimeEpochMicros());
        assertEquals(2000, toFinishMacros(finishedSpan));
        /*
        Map<String, Object> tags = finishedSpan.tags();
        assertEquals(2, tags.size());
        assertEquals(7, tags.get("int"));
        assertEquals("foo", tags.get("string"));
        List<MockSpan.LogEntry> logs = finishedSpan.logEntries();
        assertEquals(3, logs.size());
        {
            MockSpan.LogEntry log = logs.get(0);
            assertEquals(1, log.fields().size());
            assertEquals("foo", log.fields().get("event"));
        }
        {
            MockSpan.LogEntry log = logs.get(1);
            assertEquals(1002, log.timestampMicros());
            assertEquals(4, log.fields().get("f1"));
            assertEquals("two", log.fields().get("f2"));
        }
        {
            MockSpan.LogEntry log = logs.get(2);
            assertEquals(1003, log.timestampMicros());
            assertEquals("event name", log.fields().get("event"));
        }
        */
    }

    @Test
    public void testChildSpan() {
        // Create and finish a root Span.
        WingtipsTracer tracer = new WingtipsTracer();

        Span parent = tracer.buildSpan("parent").withStartTimestamp(1000).startManual();
        Span child = tracer.buildSpan("child").withStartTimestamp(1100).asChildOf(parent).startManual();
        child.finish(1900);
        parent.finish(2000);

        List<com.nike.wingtips.Span> finishedSpans = spanRecorder.completedSpans;

        // Check that the Spans look right.
        assertEquals(2, finishedSpans.size());
        com.nike.wingtips.Span child2 = finishedSpans.get(0);
        com.nike.wingtips.Span parent2 = finishedSpans.get(1);
        assertEquals("child", child2.getSpanName());
        assertEquals("parent", parent2.getSpanName());
        assertEquals(parent2.getSpanId(), child2.getParentSpanId());
        assertEquals(parent2.getTraceId(), child2.getTraceId());

    }

    @Test
    public void testStartTimestamp() throws InterruptedException {
        WingtipsTracer tracer = new WingtipsTracer();

        Tracer.SpanBuilder fooSpan = tracer.buildSpan("foo");
        Thread.sleep(2);
        long startMicros = currentTimeMacros();
        fooSpan.startManual().finish();

        List<com.nike.wingtips.Span> finishedSpans = spanRecorder.completedSpans;

        Assert.assertEquals(1, finishedSpans.size());
        com.nike.wingtips.Span span = finishedSpans.get(0);
        Assert.assertTrue(startMicros <= span.getSpanStartTimeEpochMicros());

        long currentTM = currentTimeMacros();
        long finisihTM = toFinishMacros(span);
        Assert.assertTrue(currentTM >= finisihTM);
    }

    @Test
    public void testStartExplicitTimestamp() throws InterruptedException {
        WingtipsTracer tracer = new WingtipsTracer();
        long startMicros = 2000;

        tracer.buildSpan("foo")
            .withStartTimestamp(startMicros)
            .startManual()
            .finish();

        List<com.nike.wingtips.Span> finishedSpans = spanRecorder.completedSpans;

        Assert.assertEquals(1, finishedSpans.size());
        Assert.assertEquals(startMicros, finishedSpans.get(0).getSpanStartTimeEpochMicros());
    }

    @Test
    public void testTextMapPropagatorTextMap() {
        WingtipsTracer tracer = new WingtipsTracer();
        HashMap<String, String> injectMap = new HashMap<>();
        injectMap.put("foobag", "donttouch");

        Span parentSpan = tracer.buildSpan("foo").startManual();
        parentSpan.setBaggageItem("foobag", "fooitem");
        parentSpan.finish();

        tracer.inject(parentSpan.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(injectMap));

        SpanContext extract = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(injectMap));

        Span childSpan = tracer.buildSpan("bar")
            .asChildOf(extract)
            .startManual();
        childSpan.setBaggageItem("barbag", "baritem");
        childSpan.finish();

        List<com.nike.wingtips.Span> finishedSpans = spanRecorder.completedSpans;

        Assert.assertEquals(2, finishedSpans.size());
        Assert.assertEquals(finishedSpans.get(0).getTraceId(), finishedSpans.get(1).getTraceId());
        Assert.assertEquals(finishedSpans.get(0).getSpanId(), finishedSpans.get(1).getParentSpanId());
        /*
        Assert.assertEquals("fooitem", finishedSpans.get(0).getBaggageItem("foobag"));
        Assert.assertNull(finishedSpans.get(0).getBaggageItem("barbag"));
        Assert.assertEquals("fooitem", finishedSpans.get(1).getBaggageItem("foobag"));
        Assert.assertEquals("baritem", finishedSpans.get(1).getBaggageItem("barbag"));
        Assert.assertEquals("donttouch", injectMap.get("foobag"));
        */
    }

    @Test
    public void testTextMapPropagatorHttpHeaders() {
        WingtipsTracer tracer = new WingtipsTracer();

        Span parentSpan = tracer.buildSpan("foo").startManual();
        parentSpan.finish();

        final Map<String, String> map = new HashMap<>();

        TextMap inCarrier = HttpHeadersCodec.toTextMap(new ResponseWithHeaders() {
            @Override
            public void setHeader(String headerName, String headerValue) {
                map.put(headerName, headerValue);
            }

            @Override
            public void setAttribute(String name, Object attribute) {
            }
        });
        tracer.inject(parentSpan.context(), Format.Builtin.HTTP_HEADERS, inCarrier);

        TextMap outCarrier = HttpHeadersCodec.toTextMap(new RequestWithHeaders() {
            @Override
            public String getHeader(String headerName) {
                return map.get(headerName);
            }

            @Override
            public Object getAttribute(String name) {
                return null;
            }
        });
        SpanContext extract = tracer.extract(Format.Builtin.HTTP_HEADERS, outCarrier);

        tracer.buildSpan("bar")
            .asChildOf(extract)
            .startManual()
            .finish();

        List<com.nike.wingtips.Span> finishedSpans = spanRecorder.completedSpans;

        Assert.assertEquals(2, finishedSpans.size());
        Assert.assertEquals(finishedSpans.get(0).getTraceId(), finishedSpans.get(1).getTraceId());
        Assert.assertEquals(finishedSpans.get(0).getSpanId(), finishedSpans.get(1).getParentSpanId());
    }
}
