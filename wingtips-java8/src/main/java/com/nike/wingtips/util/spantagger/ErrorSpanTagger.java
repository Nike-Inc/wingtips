package com.nike.wingtips.util.spantagger;

import com.nike.wingtips.Span;
import com.nike.wingtips.util.AsyncWingtipsHelper;
import com.nike.wingtips.util.AsyncWingtipsHelperStatic;
import com.nike.wingtips.util.operationwrapper.OperationWrapperOptions;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * A functional interface that handles error tagging for spans when an operation fails with an exception. Used by
 * {@link OperationWrapperOptions}, which is in turn used by the {@code wrap[Operation]WithSpan(...)} methods found
 * in {@link AsyncWingtipsHelperStatic} and {@link AsyncWingtipsHelper}.
 */
@FunctionalInterface
public interface ErrorSpanTagger extends BiConsumer<@NotNull Span, @NotNull Throwable> {

    /**
     * Reusable default thread-safe implementation of this interface. See the javadocs of {@link
     * DefaultErrorSpanTagger} for more details.
     */
    ErrorSpanTagger DEFAULT_IMPL = new DefaultErrorSpanTagger();

    /**
     * Default implementation of {@link BiConsumer} - this simply calls {@link #tagSpanForError(Span, Throwable)}.
     *
     * @param span The span to tag - should never be null.
     * @param error The error thrown by the operation associated with the given span - should never be null.
     */
    @Override
    default void accept(@NotNull Span span, @NotNull Throwable error) {
        tagSpanForError(span, error);
    }

    /**
     * Performs any desired error tagging on the given span after the operation fails with the given error but before
     * the span is completed.
     *
     * @param span The span to tag - should never be null.
     * @param error The error thrown by the operation associated with the given span - should never be null.
     */
    void tagSpanForError(@NotNull Span span, @NotNull Throwable error);

}
