package com.nike.wingtips.opentracing;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.opentracing.Span;
import io.opentracing.SpanContext;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class WingtipsSpan implements Span {
    private final com.nike.wingtips.Span span;
    private final WingtipsSpanContextImpl context;
    private final Map<String, Object> tags = new ConcurrentHashMap<>();

    WingtipsSpan(com.nike.wingtips.Span span, Map<String, Object> tags) {
        this.span = span;
        this.context = new WingtipsSpanContextImpl(span);
        this.tags.putAll(tags);
    }

    WingtipsScope toScope(boolean finishSpanOnClose) {
        WingtipsScope wingtipsScope = new WingtipsScope(this, finishSpanOnClose);
        span.setHandle(wingtipsScope);
        span.handleScope();
        return wingtipsScope;
    }

    @Override
    public SpanContext context() {
        return context;
    }

    private Span addTag(String key, Object value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public Span setTag(String key, String value) {
        return addTag(key, value);
    }

    @Override
    public Span setTag(String key, boolean value) {
        return addTag(key, value);
    }

    @Override
    public Span setTag(String key, Number value) {
        return addTag(key, value);
    }

    @Override
    public Span log(Map<String, ?> fields) {
        return log(nowMicros(), fields);
    }

    @Override
    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        return this; // TODO
    }

    @Override
    public Span log(String event) {
        return log(Collections.singletonMap("event", event));
    }

    @Override
    public Span log(long timestampMicroseconds, String event) {
        return log(timestampMicroseconds, Collections.singletonMap("event", event));
    }

    @Override
    public Span setBaggageItem(String key, String value) {
        context.setBaggageItem(key, value);
        return this;
    }

    @Override
    public String getBaggageItem(String key) {
        return context.getBaggageItem(key);
    }

    @Override
    public Span setOperationName(String operationName) {
        return this; // TODO
    }

    @Override
    public void finish() {
        span.close();
    }

    @Override
    public void finish(long finishMicros) {
        span.close(TimeUnit.MICROSECONDS.toNanos(finishMicros));
    }

    private static long nowMicros() {
        return TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
    }
}
