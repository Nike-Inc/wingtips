package com.nike.wingtips.opentracing;

import com.nike.wingtips.Tracer;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class WingtipsScopeManger implements ScopeManager {
    @Override
    public Scope activate(Span span) {
        return activate(span, true);
    }

    @Override
    public Scope activate(Span span, boolean finishSpanOnClose) {
        WingtipsSpan wingtipsSpan = (WingtipsSpan) span;
        return wingtipsSpan.toScope(finishSpanOnClose);
    }

    @Override
    public Scope active() {
        com.nike.wingtips.Span currentSpan = Tracer.getInstance().getCurrentSpan();
        return (currentSpan != null) ? currentSpan.getHandle(Scope.class) : null;
    }
}
