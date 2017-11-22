package com.nike.wingtips.opentracing;

import io.opentracing.Scope;
import io.opentracing.Span;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
class WingtipsScope implements Scope {
    private final WingtipsSpan span;
    private final boolean finishOnClose;

    WingtipsScope(WingtipsSpan span, boolean finishOnClose) {
        this.span = span;
        this.finishOnClose = finishOnClose;
    }

    @Override
    public void close() {
        if (finishOnClose) {
            span.finish();
        }
    }

    @Override
    public Span span() {
        return span;
    }
}
