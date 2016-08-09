package com.nike.wingtips.zipkin.util;

import com.nike.wingtips.Span;

import zipkin.Endpoint;

/**
 * Simple interface for a class that knows how to convert a Wingtips {@link Span} to a {@link zipkin.Span}.
 *
 * @author Nic Munroe
 */
public interface WingtipsToZipkinSpanConverter {

    /**
     * @param wingtipsSpan The Wingtips span to convert.
     * @param zipkinEndpoint The Zipkin {@link Endpoint} associated with the current service. This is often a singleton that gets reused
     *                       throughout the life of the service. Used when creating Zipkin client/server/local annotations.
     * @param localComponentNamespace The {@link zipkin.Constants#LOCAL_COMPONENT} namespace that should be used when creating certain Zipkin
     *                                annotations when the Wingtips span's {@link Span#getSpanPurpose()} is
     *                                {@link com.nike.wingtips.Span.SpanPurpose#LOCAL_ONLY}. See the {@link zipkin.Constants#LOCAL_COMPONENT}
     *                                javadocs for more information on what this is and how it's used by the Zipkin server, so you know
     *                                what value you should send.
     * @return The given Wingtips {@link Span} after it has been converted to a {@link zipkin.Span}.
     */
    zipkin.Span convertWingtipsSpanToZipkinSpan(Span wingtipsSpan, Endpoint zipkinEndpoint, String localComponentNamespace);

}
