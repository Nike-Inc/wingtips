package com.nike.wingtips.opentracing.propagation;

import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.http.RequestWithHeaders;
import com.nike.wingtips.http.ResponseWithHeaders;
import com.nike.wingtips.opentracing.WingtipsSpanContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class HttpHeadersCodec extends TextMapCodec implements Extractor<TextMap>, Injector<TextMap> {
    private static final Map<String, String> HEADER_KEYS = new HashMap<>();

    static {
        HEADER_KEYS.put(Span.TRACE_ID_FIELD, TraceHeaders.TRACE_ID);
        HEADER_KEYS.put(Span.SPAN_ID_FIELD, TraceHeaders.SPAN_ID);
        HEADER_KEYS.put(Span.PARENT_SPAN_ID_FIELD, TraceHeaders.PARENT_SPAN_ID);
        HEADER_KEYS.put(Span.SPAN_NAME_FIELD, TraceHeaders.SPAN_NAME);
        HEADER_KEYS.put(Span.SAMPLEABLE_FIELD, TraceHeaders.TRACE_SAMPLED);
    }

    public static TextMap toTextMap(RequestWithHeaders requestWithHeaders) {
        Span span = HttpRequestTracingUtils.fromRequestWithHeaders(requestWithHeaders, Collections.<String>emptyList());
        Map<String, Object> spanMap = span.toMap();
        Map<String, String> textMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : spanMap.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                textMap.put(entry.getKey(), String.valueOf(value));
            }
        }
        return new TextMapExtractAdapter(textMap);
    }

    public static TextMap toTextMap(final ResponseWithHeaders responseWithHeaders) {
        return new TextMap() {
            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                throw new UnsupportedOperationException("HttpHeadersCodec.inject should only be used with Tracer.inject()");
            }

            @Override
            public void put(String key, String value) {
                String headerKey = HEADER_KEYS.get(key);
                if (headerKey == null) {
                    headerKey = key;
                }
                responseWithHeaders.setHeader(headerKey, value);
            }
        };
    }

    public WingtipsSpanContext extract(RequestWithHeaders requestWithHeaders) {
        return extract(toTextMap(requestWithHeaders));
    }

    public void inject(WingtipsSpanContext context, ResponseWithHeaders responseWithHeaders) {
        inject(context, toTextMap(responseWithHeaders));
    }
}
