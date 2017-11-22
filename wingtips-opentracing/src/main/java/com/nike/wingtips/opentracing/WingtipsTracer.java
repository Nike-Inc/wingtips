package com.nike.wingtips.opentracing;

import com.nike.wingtips.opentracing.propagation.Extractor;
import com.nike.wingtips.opentracing.propagation.Injector;
import com.nike.wingtips.opentracing.propagation.PropagationRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class WingtipsTracer implements Tracer {
    private final ScopeManager scopeManager = new WingtipsScopeManger();
    private final PropagationRegistry registry;

    public WingtipsTracer() {
        this(PropagationRegistry.getDefault());
    }

    public WingtipsTracer(PropagationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ScopeManager scopeManager() {
        return scopeManager;
    }

    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new WingtipsSpanBuilder(operationName);
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        Injector<C> injector = registry.getInjector(format);
        if (injector == null) {
            throw new IllegalArgumentException(format.toString());
        }
        injector.inject((WingtipsSpanContext) spanContext, carrier);
    }

    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        Extractor<C> extractor = registry.getExtractor(format);
        if (extractor == null) {
            throw new IllegalArgumentException(format.toString());
        }
        return extractor.extract(carrier);
    }

    private class WingtipsSpanBuilder implements Tracer.SpanBuilder {
        private final com.nike.wingtips.Span.Builder builder;
        private final Map<String, Object> tags = new HashMap<>();

        private String parentId;
        private boolean ignoringActiveSpan;

        private WingtipsSpanBuilder(String operationName) {
            com.nike.wingtips.Span currentSpan = com.nike.wingtips.Tracer.getInstance().getCurrentSpan();
            if (currentSpan == null) {
                builder = com.nike.wingtips.Span.newBuilder(operationName, com.nike.wingtips.Span.SpanPurpose.SERVER);
            } else {
                parentId = currentSpan.getSpanId();
                builder = com.nike.wingtips.Span.newBuilder(currentSpan)
                    .withParentSpanId(parentId)
                    .withSpanName(operationName)
                    .withSpanId(null)
                    .withSpanStartTimeEpochMicros(null)
                    .withSpanStartTimeNanos(null)
                    .withDurationNanos(null)
                    .withSpanId(null);
            }
        }

        @Override
        public SpanBuilder asChildOf(SpanContext parent) {
            return addReference(References.CHILD_OF, parent);
        }

        @Override
        public SpanBuilder asChildOf(Span parent) {
            return addReference(References.CHILD_OF, parent != null ? parent.context() : null);
        }

        @Override
        public SpanBuilder addReference(String referenceType, SpanContext context) {
            if (context != null && (referenceType.equals(References.CHILD_OF) || referenceType.equals(References.FOLLOWS_FROM))) {
                WingtipsSpanContext wsc = WingtipsSpanContext.class.cast(context);

                if (parentId != null && !parentId.equals(wsc.getSpanId())) {
                    throw new IllegalArgumentException(String.format("Referencing different parent Span from initial parent Span: initial [%s] != builder [%s]", parentId, wsc.getSpanId()));
                }

                builder
                    .withTraceId(wsc.getTraceId())
                    .withParentSpanId(wsc.getSpanId())
                    .withSampleable(wsc.isSampleable())
                    .withSpanPurpose(wsc.getSpanPurpose())
                    .withUserId(wsc.getUserId());
            }
            return this;
        }

        @Override
        public SpanBuilder ignoreActiveSpan() {
            ignoringActiveSpan = true;
            return this;
        }

        private SpanBuilder addTag(String key, Object value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public SpanBuilder withTag(String key, String value) {
            return addTag(key, value);
        }

        @Override
        public SpanBuilder withTag(String key, boolean value) {
            return addTag(key, value);
        }

        @Override
        public SpanBuilder withTag(String key, Number value) {
            return addTag(key, value);
        }

        @Override
        public SpanBuilder withStartTimestamp(long microseconds) {
            builder.withSpanStartTimeEpochMicros(microseconds);
            builder.withSpanStartTimeNanos(TimeUnit.MICROSECONDS.toNanos(microseconds));
            return this;
        }

        @Override
        public Scope startActive() {
            return WingtipsTracer.this.scopeManager().activate(startManual());
        }

        @Override
        public Scope startActive(boolean finishSpanOnClose) {
            return WingtipsTracer.this.scopeManager().activate(startManual(), finishSpanOnClose);
        }

        @Override
        public Span startManual() {
            if (!ignoringActiveSpan) {
                Scope activeScope = scopeManager().active();
                if (activeScope != null) {
                    asChildOf(activeScope.span());
                }
            }
            return new WingtipsSpan(builder.build(), tags);
        }

        @Override
        public Span start() {
            return startManual();
        }
    }
}
