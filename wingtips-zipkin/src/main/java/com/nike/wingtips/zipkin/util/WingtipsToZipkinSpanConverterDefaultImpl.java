package com.nike.wingtips.zipkin.util;

import com.nike.wingtips.Span;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import zipkin2.Endpoint;

/**
 * Default implementation of {@link WingtipsToZipkinSpanConverter} that knows how to create the appropriate client/server/local annotations
 * for the {@link zipkin2.Span} based on the Wingtips {@link Span}'s {@link Span#getSpanPurpose()}.
 *
 * @author Nic Munroe
 */
public class WingtipsToZipkinSpanConverterDefaultImpl implements WingtipsToZipkinSpanConverter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public zipkin2.Span convertWingtipsSpanToZipkinSpan(Span wingtipsSpan, Endpoint zipkinEndpoint, String localComponentNamespace) {
        String traceId = wingtipsSpan.getTraceId();
        long startEpochMicros = wingtipsSpan.getSpanStartTimeEpochMicros();
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(wingtipsSpan.getDurationNanos());

        return createNewZipkinSpanBuilderWithSpanPurposeAnnotations(wingtipsSpan, startEpochMicros, durationMicros, zipkinEndpoint, localComponentNamespace)
            .id(wingtipsSpan.getSpanId())
            .name(wingtipsSpan.getSpanName())
            .parentId(wingtipsSpan.getParentSpanId())
            .traceId(traceId)
            .build();
    }

    protected zipkin2.Span.Builder createNewZipkinSpanBuilderWithSpanPurposeAnnotations(
        Span wingtipsSpan, long startEpochMicros, long durationMicros, Endpoint zipkinEndpoint, String localComponentNamespace
    ) {
        zipkin2.Span.Builder zsb = zipkin2.Span.newBuilder()
            .timestamp(startEpochMicros)
            .duration(durationMicros)
            .localEndpoint(zipkinEndpoint);

        switch(wingtipsSpan.getSpanPurpose()) {
            case SERVER:
                zsb.kind(zipkin2.Span.Kind.SERVER);
                break;
            case CLIENT:
                zsb.kind(zipkin2.Span.Kind.CLIENT);
                break;
            case LOCAL_ONLY:
            case UNKNOWN:       // intentional fall-through: local and unknown span purpose are treated the same way
                zsb.putTag("lc", localComponentNamespace);
                break;
            default:
                logger.warn("Unhandled SpanPurpose type: " + wingtipsSpan.getSpanPurpose().name());
        }

        return zsb;
    }
}
