package com.nike.wingtips;

public class TestSpanCompleter {

	/**
	 * The {@code Span.complete()} method is package protected and we'd like to keep it that way.  For testing
	 * we need a way to create spans in a completed state so this class is a bridge between the packages.  
	 * 
	 * @see test use cases from {@code com.nike.wingtips.util.SpanParserTest}
	 * @param span
	 */
	public static void completeSpan(Span span) {
		span.complete();
	}
}
