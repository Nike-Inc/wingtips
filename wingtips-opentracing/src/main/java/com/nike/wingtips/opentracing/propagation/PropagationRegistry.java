package com.nike.wingtips.opentracing.propagation;

import java.util.HashMap;
import java.util.Map;

import io.opentracing.propagation.Format;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class PropagationRegistry {
    private final Map<Format<?>, Injector<?>> injectors = new HashMap<>();
    private final Map<Format<?>, Extractor<?>> extractors = new HashMap<>();

    public static PropagationRegistry getDefault() {
        PropagationRegistry registry = new PropagationRegistry();

        TextMapCodec textMapCodec = new TextMapCodec();
        registry.registerInjector(Format.Builtin.TEXT_MAP, textMapCodec);
        registry.registerExtractor(Format.Builtin.TEXT_MAP, textMapCodec);

        HttpHeadersCodec httpHeadersCodec = new HttpHeadersCodec();
        registry.registerInjector(Format.Builtin.HTTP_HEADERS, httpHeadersCodec);
        registry.registerExtractor(Format.Builtin.HTTP_HEADERS, httpHeadersCodec);

        return registry;
    }

    @SuppressWarnings("unchecked")
    public <T> Injector<T> getInjector(Format<T> format) {
        return (Injector<T>) injectors.get(format);
    }

    @SuppressWarnings("unchecked")
    public <T> Extractor<T> getExtractor(Format<T> format) {
        return (Extractor<T>) extractors.get(format);
    }

    public <T> void registerInjector(Format<T> format, Injector<T> injector) {
        injectors.put(format, injector);
    }

    public <T> void registerExtractor(Format<T> format, Extractor<T> extractor) {
        extractors.put(format, extractor);
    }
}
