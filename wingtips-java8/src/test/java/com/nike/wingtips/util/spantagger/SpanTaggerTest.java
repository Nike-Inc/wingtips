package com.nike.wingtips.util.spantagger;

import com.nike.wingtips.Span;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the default method functionality of {@link SpanTagger}.
 */
public class SpanTaggerTest {

    @Test
    public void accept_implements_BiConsumer_by_forwarding_to_tagSpan() {
        // given
        SpanTagger<Object> taggerSpy = spy(new DoNothingSpanTagger<>());
        Span span = mock(Span.class);
        Object payload = new Object();

        // when
        taggerSpy.accept(span, payload);

        // then
        verify(taggerSpy).tagSpan(span, payload);
    }

    private static class DoNothingSpanTagger<T> implements SpanTagger<T> {
        @Override
        public void tagSpan(@NotNull Span span, @Nullable T payload) {
            // Do nothing.
        }
    }

}