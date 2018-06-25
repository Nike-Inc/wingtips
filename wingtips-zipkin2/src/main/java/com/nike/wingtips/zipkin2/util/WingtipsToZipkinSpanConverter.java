package com.nike.wingtips.zipkin2.util;

import com.nike.wingtips.Span;

import zipkin2.Endpoint;

/**
 * Simple interface for a class that knows how to convert a Wingtips {@link Span} to a {@link zipkin2.Span}.
 *
 * @author Nic Munroe
 */
public interface WingtipsToZipkinSpanConverter {

    /**
     * @param wingtipsSpan The Wingtips span to convert.
     * @param zipkinEndpoint The Zipkin {@link Endpoint} associated with the current service. This is often a singleton that gets reused
     *                       throughout the life of the service. Used when creating Zipkin client/server/local annotations.
     * @return The given Wingtips {@link Span} after it has been converted to a {@link zipkin2.Span}.
     */
    zipkin2.Span convertWingtipsSpanToZipkinSpan(Span wingtipsSpan, Endpoint zipkinEndpoint);

}
