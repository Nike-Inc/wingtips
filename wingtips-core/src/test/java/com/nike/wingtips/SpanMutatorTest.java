package com.nike.wingtips;

import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link SpanMutator}.
 *
 * @author Nic Munroe
 */
public class SpanMutatorTest {
    
    @Test
    public void changeSpanName_works_as_expected() {
        // given
        Span span = Span.newBuilder("origSpanName", Span.SpanPurpose.SERVER).build();
        String newSpanName = UUID.randomUUID().toString();

        assertThat(span.getSpanName()).isNotEqualTo(newSpanName);

        // when
        SpanMutator.changeSpanName(span, newSpanName);

        // then
        assertThat(span.getSpanName()).isEqualTo(newSpanName);
    }

    @Test
    public void changeSpanName_does_nothing_if_passed_null_span() {
        // expect - no exception thrown when passed null span.
        SpanMutator.changeSpanName(null, "foo");
    }

    @Test
    public void changeSpanName_does_nothing_if_passed_null_newName() {
        // given
        Span spanMock = mock(Span.class);

        // when
        SpanMutator.changeSpanName(spanMock, null);

        // then
        verifyZeroInteractions(spanMock);
    }
}