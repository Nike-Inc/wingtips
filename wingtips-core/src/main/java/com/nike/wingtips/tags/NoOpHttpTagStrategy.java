package com.nike.wingtips.tags;

import com.nike.wingtips.Span;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A no-op implementation of {@link HttpTagAndSpanNamingStrategy}. All methods will return null or otherwise do nothing.
 */
public class NoOpHttpTagStrategy<REQ, RES> extends HttpTagAndSpanNamingStrategy<REQ, RES> {

    @SuppressWarnings("WeakerAccess")
    protected static final NoOpHttpTagStrategy<?, ?> DEFAULT_INSTANCE = new NoOpHttpTagStrategy<>();

    /**
     * @return A reusable, thread-safe, singleton instance of this class that can be used by anybody who wants to use
     * this class and does not need any customization.
     */
    @SuppressWarnings("unchecked")
    public static <REQ, RES> NoOpHttpTagStrategy<REQ, RES> getDefaultInstance() {
        return (NoOpHttpTagStrategy<REQ, RES>) DEFAULT_INSTANCE;
    }

    @Override
    protected @Nullable String doGetInitialSpanName(
        @NotNull REQ request,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, ?> adapter
    ) {
        return null;
    }

    @Override
    protected void doDetermineAndSetFinalSpanName(
        @NotNull Span span,
        @Nullable REQ request,
        @Nullable RES response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    ) {
        // Intentionally do nothing.
    }

    @Override
    protected void doHandleRequestTagging(
        @NotNull Span span,
        @NotNull REQ request,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, ?> adapter
    ) {
        // Intentionally do nothing.
    }

    @Override
    protected void doHandleResponseAndErrorTagging(
        @NotNull Span span,
        @Nullable REQ request,
        @Nullable RES response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    ) {
        // Intentionally do nothing.
    }

    @Override
    protected void doExtraWingtipsTagging(
        @NotNull Span span,
        @Nullable REQ request,
        @Nullable RES response,
        @Nullable Throwable error,
        @NotNull HttpTagAndSpanNamingAdapter<REQ, RES> adapter
    ) {
        // Intentionally do nothing.
    }
}
