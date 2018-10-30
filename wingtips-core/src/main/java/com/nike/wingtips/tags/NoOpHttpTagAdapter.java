package com.nike.wingtips.tags;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A no-op implementation of {@link HttpTagAndSpanNamingAdapter}. All methods will return null.
 *
 * @author Nic Munroe
 */
public class NoOpHttpTagAdapter<REQ, RES> extends HttpTagAndSpanNamingAdapter<REQ, RES> {

    @SuppressWarnings("WeakerAccess")
    protected static final NoOpHttpTagAdapter<?, ?> DEFAULT_INSTANCE = new NoOpHttpTagAdapter<>();

    /**
     * @return A reusable, thread-safe, singleton instance of this class that can be used by anybody who wants to use
     * this class and does not need any customization.
     */
    @SuppressWarnings("unchecked")
    public static <REQ, RES> NoOpHttpTagAdapter<REQ, RES> getDefaultInstance() {
        return (NoOpHttpTagAdapter<REQ, RES>) DEFAULT_INSTANCE;
    }

    @Override
    public @Nullable String getErrorResponseTagValue(@Nullable RES response) {
        return null;
    }

    @Override
    public @Nullable String getRequestUrl(@Nullable REQ request) {
        return null;
    }

    @Override
    public @Nullable String getRequestPath(@Nullable REQ request) {
        return null;
    }

    @Override
    public @Nullable Integer getResponseHttpStatus(@Nullable RES response) {
        return null;
    }

    @Override
    public @Nullable String getRequestHttpMethod(@Nullable REQ request) {
        return null;
    }

    @Override
    public @Nullable String getHeaderSingleValue(@Nullable REQ request, @NotNull String headerKey) {
        return null;
    }

    @Override
    public @Nullable List<String> getHeaderMultipleValue(@Nullable REQ request, @NotNull String headerKey) {
        return null;
    }

    @Override
    public @Nullable String getSpanNamePrefix(@Nullable REQ request) {
        return null;
    }

    @Override
    public @Nullable String getInitialSpanName(@Nullable REQ request) {
        return null;
    }

    @Override
    public @Nullable String getFinalSpanName(@Nullable REQ request, @Nullable RES response) {
        return null;
    }

    @Override
    public @Nullable String getRequestUriPathTemplate(@Nullable REQ request, @Nullable RES response) {
        return null;
    }

    @Override
    public @Nullable String getSpanHandlerTagValue(@Nullable REQ request, @Nullable RES response) {
        return null;
    }
}
