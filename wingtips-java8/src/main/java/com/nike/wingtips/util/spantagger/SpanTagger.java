package com.nike.wingtips.util.spantagger;

import com.nike.wingtips.Span;
import com.nike.wingtips.util.AsyncWingtipsHelper;
import com.nike.wingtips.util.AsyncWingtipsHelperStatic;
import com.nike.wingtips.util.operationwrapper.OperationWrapperOptions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * A functional interface that handles extra tagging for spans. Used by {@link OperationWrapperOptions}, which is
 * in turn used by the {@code wrap[Operation]WithSpan(...)} methods found in {@link AsyncWingtipsHelperStatic} and
 * {@link AsyncWingtipsHelper}.
 */
@FunctionalInterface
public interface SpanTagger<T> extends BiConsumer<@NotNull Span, @Nullable T> {

    /**
     * Default implementation of {@link BiConsumer} - this simply calls {@link #tagSpan(Span, Object)}.
     *
     * @param span The span to tag - should never be null.
     * @param t The payload of the operation associated with the given span - may be null.
     */
    @Override
    default void accept(@NotNull Span span, @Nullable T t) {
        tagSpan(span, t);
    }

    /**
     * Performs any desired extra tagging on the given span after the operation finishes but before the span is
     * completed.
     *
     * @param span The span to tag - should never be null.
     * @param payload The payload of the operation associated with the given span - may be null.
     */
    void tagSpan(@NotNull Span span, @Nullable T payload);
}
