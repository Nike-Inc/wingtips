package com.nike.wingtips.opentracing.propagation;

import com.nike.wingtips.opentracing.WingtipsSpanContext;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public interface Injector<T> {
    void inject(WingtipsSpanContext spanContext, T carrier);
}
