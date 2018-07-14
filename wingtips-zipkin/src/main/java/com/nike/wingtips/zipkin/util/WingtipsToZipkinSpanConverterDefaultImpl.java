package com.nike.wingtips.zipkin.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.TraceAndSpanIdGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;

/**
 * Default implementation of {@link WingtipsToZipkinSpanConverter} that knows how to create the appropriate client/server/local annotations
 * for the {@link zipkin.Span} based on the Wingtips {@link Span}'s {@link Span#getSpanPurpose()}.
 *
 * @deprecated Please migrate to the wingtips-zipkin2 dependency.
 * 
 * @author Nic Munroe
 */
@Deprecated
public class WingtipsToZipkinSpanConverterDefaultImpl implements WingtipsToZipkinSpanConverter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public zipkin.Span convertWingtipsSpanToZipkinSpan(Span wingtipsSpan, Endpoint zipkinEndpoint, String localComponentNamespace) {
        String traceId = wingtipsSpan.getTraceId();
        long startEpochMicros = wingtipsSpan.getSpanStartTimeEpochMicros();
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(wingtipsSpan.getDurationNanos());

        return createNewZipkinSpanBuilderWithSpanPurposeAnnotations(wingtipsSpan, startEpochMicros, durationMicros, zipkinEndpoint, localComponentNamespace)
            .id(nullSafeLong(wingtipsSpan.getSpanId()))
            .name(wingtipsSpan.getSpanName())
            .parentId(nullSafeLong(wingtipsSpan.getParentSpanId()))
            .timestamp(startEpochMicros)
            .traceIdHigh(traceId.length() == 32 ? nullSafeLong(traceId, 0) : 0)
            .traceId(nullSafeLong(traceId))
            .duration(durationMicros)
            .build();
    }

    protected zipkin.Span.Builder createNewZipkinSpanBuilderWithSpanPurposeAnnotations(
        Span wingtipsSpan, long startEpochMicros, long durationMicros, Endpoint zipkinEndpoint, String localComponentNamespace
    ) {
        zipkin.Span.Builder zsb = zipkin.Span.builder();

        switch(wingtipsSpan.getSpanPurpose()) {
            case SERVER:
                zsb.addAnnotation(Annotation.create(startEpochMicros, Constants.SERVER_RECV, zipkinEndpoint))
                   .addAnnotation(Annotation.create(startEpochMicros + durationMicros, Constants.SERVER_SEND, zipkinEndpoint));

                break;
            case CLIENT:
                zsb.addAnnotation(Annotation.create(startEpochMicros, Constants.CLIENT_SEND, zipkinEndpoint))
                   .addAnnotation(Annotation.create(startEpochMicros + durationMicros, Constants.CLIENT_RECV, zipkinEndpoint));

                break;
            case LOCAL_ONLY:
            case UNKNOWN:       // intentional fall-through: local and unknown span purpose are treated the same way
                zsb.addBinaryAnnotation(BinaryAnnotation.create(Constants.LOCAL_COMPONENT, localComponentNamespace, zipkinEndpoint));

                break;
            default:
                logger.warn("Unhandled SpanPurpose type: " + wingtipsSpan.getSpanPurpose().name());
        }

        return zsb;
    }

    protected Long nullSafeLong(String lowerHexStr) {
        if (lowerHexStr == null)
            return null;

        return TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(lowerHexStr);
    }

    protected Long nullSafeLong(String lowerHexStr, int index) {
        if (lowerHexStr == null)
            return null;

        return TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(lowerHexStr, index);
    }
}
