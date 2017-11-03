package com.nike.wingtips.opentracing;

import com.nike.wingtips.Span;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class WingtipsSpanContextImpl implements WingtipsSpanContext {
    private final Span span;
    private final Map<String, String> baggageItems = new ConcurrentHashMap<>();

    public WingtipsSpanContextImpl(Span span) {
        this.span = span;
    }

    @Override
    public String getTraceId() {
        return span.getTraceId();
    }

    @Override
    public String getSpanId() {
        return span.getSpanId();
    }

    @Override
    public String getParentSpanId() {
        return span.getParentSpanId();
    }

    @Override
    public String getSpanName() {
        return span.getSpanName();
    }

    @Override
    public boolean isSampleable() {
        return span.isSampleable();
    }

    @Override
    public String getUserId() {
        return span.getUserId();
    }

    @Override
    public long getSpanStartTimeEpochMicros() {
        return span.getSpanStartTimeEpochMicros();
    }

    @Override
    public long getSpanStartTimeNanos() {
        return span.getSpanStartTimeNanos();
    }

    @Override
    public Span.SpanPurpose getSpanPurpose() {
        return span.getSpanPurpose();
    }

    @Override
    public Iterable<Map.Entry<String, Object>> spanEntries() {
        Map<String, Object> map = span.toMap();
        return map.entrySet();
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return baggageItems.entrySet();
    }

    public void setBaggageItems(Map<String, String> baggageItems) {
        this.baggageItems.putAll(baggageItems);
    }

    public void setBaggageItem(String key, String value) {
        baggageItems.put(key, value);
    }

    public String getBaggageItem(String key) {
        return baggageItems.get(key);
    }
}
