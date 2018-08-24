package com.nike.wingtips;

/**
 * The {@link Span#complete()} method is package protected and we'd like to keep it that way. For testing
 * we need a way to create spans in a completed state so this class is a bridge between the packages.
 *
 * See some of the test use cases from {@link com.nike.wingtips.util.parser.SpanParserTest} for examples.
 */
public class TestSpanCompleter {

    /**
     * Calls {@link Span#complete()} on the given span.
     */
    public static void completeSpan(Span span) {
        span.complete();
    }
}
