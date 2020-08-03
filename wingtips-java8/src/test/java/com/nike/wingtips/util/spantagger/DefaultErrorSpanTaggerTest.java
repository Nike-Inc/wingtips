package com.nike.wingtips.util.spantagger;

import com.nike.wingtips.Span;
import com.nike.wingtips.tags.KnownZipkinTags;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the functionality of {@link DefaultErrorSpanTagger}.
 */
@RunWith(DataProviderRunner.class)
public class DefaultErrorSpanTaggerTest {

    @DataProvider(value = {
        "false  |   false   |   true",
        "true   |   false   |   false",
        "false  |   true    |   false",
        "true   |   true    |   false",
    }, splitBy = "\\|")
    @Test
    @SuppressWarnings("ConstantConditions")
    public void tagSpanForError_works_as_expected(boolean spanIsNull, boolean errorIsNull, boolean expectErrorTag) {
        // given
        DefaultErrorSpanTagger tagger = new DefaultErrorSpanTagger();
        Span span = (spanIsNull) ? null : mock(Span.class);
        Throwable error = (errorIsNull)
                          ? null
                          : new Exception("Intentional test exception: " + UUID.randomUUID().toString());

        // when
        tagger.tagSpanForError(span, error);

        // then
        if (expectErrorTag) {
            verify(span).putTag(KnownZipkinTags.ERROR, error.toString());
        }
        else if (span != null) {
            verifyNoInteractions(span);
        }
    }
}