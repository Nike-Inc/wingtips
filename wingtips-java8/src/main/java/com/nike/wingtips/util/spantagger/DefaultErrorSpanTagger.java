package com.nike.wingtips.util.spantagger;

import com.nike.wingtips.Span;
import com.nike.wingtips.tags.KnownZipkinTags;

import org.jetbrains.annotations.NotNull;

/**
 * A default implementation of {@link ErrorSpanTagger} that simply adds a tag to the span with the key {@link
 * KnownZipkinTags#ERROR} and value {@link Throwable#toString()}.
 */
public class DefaultErrorSpanTagger implements ErrorSpanTagger {

    @Override
    public void tagSpanForError(@NotNull Span span, @NotNull Throwable error) {
        //noinspection ConstantConditions
        if (span != null && error != null) {
            span.putTag(KnownZipkinTags.ERROR, error.toString());
        }
    }
}
