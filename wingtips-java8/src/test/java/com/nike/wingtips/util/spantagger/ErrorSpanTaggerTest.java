package com.nike.wingtips.util.spantagger;

import com.nike.wingtips.Span;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the default method functionality of {@link ErrorSpanTagger}.
 */
public class ErrorSpanTaggerTest {

    @Test
    public void accept_implements_BiConsumer_by_forwarding_to_tagSpanForError() {
        // given
        ErrorSpanTagger taggerSpy = spy(new DoNothingErrorSpanTagger());
        Span span = mock(Span.class);
        Throwable error = mock(Throwable.class);

        // when
        taggerSpy.accept(span, error);

        // then
        verify(taggerSpy).tagSpanForError(span, error);
    }

    @Test
    public void default_impl_is_instance_of_DefaultErrorSpanTagger() {
        // expect
        assertThat(ErrorSpanTagger.DEFAULT_IMPL).isInstanceOf(DefaultErrorSpanTagger.class);
    }

    private static class DoNothingErrorSpanTagger implements ErrorSpanTagger {
        @Override
        public void tagSpanForError(@NotNull Span span, @NotNull Throwable error) {
            // Do nothing.
        }
    }

}