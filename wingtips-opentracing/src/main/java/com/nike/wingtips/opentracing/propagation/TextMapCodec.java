package com.nike.wingtips.opentracing.propagation;

import com.nike.wingtips.Span;
import com.nike.wingtips.opentracing.WingtipsSpanContext;
import com.nike.wingtips.opentracing.WingtipsSpanContextImpl;

import java.util.HashMap;
import java.util.Map;

import io.opentracing.propagation.TextMap;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TextMapCodec implements Injector<TextMap>, Extractor<TextMap> {
    /**
     * Key prefix used for baggage items
     */
    private static final String BAGGAGE_KEY_PREFIX = "wingtipsctx-";

    private final String baggagePrefix;

    private final PropagationUtils utils;

    public TextMapCodec() {
        this(true);
    }

    public TextMapCodec(boolean urlEncoding) {
        this(builder().withUrlEncoding(urlEncoding));
    }

    private TextMapCodec(Builder builder) {
        this.baggagePrefix = builder.baggagePrefix;
        this.utils = new PropagationUtils(builder.urlEncoding);
    }

    @Override
    public void inject(WingtipsSpanContext spanContext, TextMap carrier) {
        for (Map.Entry<String, Object> entry : spanContext.spanEntries()) {
            Object value = entry.getValue();
            if (value != null) {
                carrier.put(entry.getKey(), utils.encodedValue(String.valueOf(value)));
            }
        }
        for (Map.Entry<String, String> entry : spanContext.baggageItems()) {
            carrier.put(utils.prefixedKey(entry.getKey(), baggagePrefix), utils.encodedValue(entry.getValue()));
        }
    }

    @Override
    public WingtipsSpanContext extract(TextMap carrier) {
        Map<String, String> spanMap = new HashMap<>();
        Map<String, String> baggage = new HashMap<>();
        for (Map.Entry<String, String> entry : carrier) {
            String key = entry.getKey();
            String value = utils.decodedValue(entry.getValue());
            if (key.startsWith(baggagePrefix)) {
                baggage.put(utils.unprefixedKey(key, baggagePrefix), value);
            } else {
                spanMap.put(key, value);
            }
        }
        Span span = Span.fromKeyValueMap(spanMap);
        WingtipsSpanContextImpl context = new WingtipsSpanContextImpl(span);
        context.setBaggageItems(baggage);
        return context;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer
            .append("TextMapCodec{")
            .append("baggagePrefix=")
            .append(baggagePrefix)
            .append(',')
            .append("urlEncoding=")
            .append(utils.urlEncoding)
            .append('}');
        return buffer.toString();
    }

    /**
     * Returns a builder for TextMapCodec.
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean urlEncoding;
        private String baggagePrefix = BAGGAGE_KEY_PREFIX;

        public Builder withUrlEncoding(boolean urlEncoding) {
            this.urlEncoding = urlEncoding;
            return this;
        }

        public Builder withBaggagePrefix(String baggagePrefix) {
            this.baggagePrefix = baggagePrefix;
            return this;
        }

        public TextMapCodec build() {
            return new TextMapCodec(this);
        }
    }
}
