package com.nike.wingtips.zipkin2.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import zipkin2.Endpoint;

/**
 * Default implementation of {@link WingtipsToZipkinSpanConverter} that knows how to convert a Wingtips span to a
 * Zipkin span.
 *
 * @author Nic Munroe
 */
public class WingtipsToZipkinSpanConverterDefaultImpl implements WingtipsToZipkinSpanConverter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public zipkin2.Span convertWingtipsSpanToZipkinSpan(Span wingtipsSpan, Endpoint zipkinEndpoint) {
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(wingtipsSpan.getDurationNanos());

        zipkin2.Span.Builder builder = zipkin2.Span
            .newBuilder()
            .id(wingtipsSpan.getSpanId())
            .name(wingtipsSpan.getSpanName())
            .parentId(wingtipsSpan.getParentSpanId())
            .traceId(wingtipsSpan.getTraceId())
            .timestamp(wingtipsSpan.getSpanStartTimeEpochMicros())
            .duration(durationMicros)
            .localEndpoint(zipkinEndpoint)
            .kind(determineZipkinKind(wingtipsSpan));
        
        // Iterate over existing tags and add them one-by-one, no current interface to set a collection of tags
        for (Map.Entry<String, String> tagEntry : wingtipsSpan.getTags().entrySet()) {
            builder.putTag(tagEntry.getKey(), tagEntry.getValue());
        }
            
        return builder.build();
    }

    @SuppressWarnings("WeakerAccess")
    protected zipkin2.Span.Kind determineZipkinKind(Span wingtipsSpan) {
        SpanPurpose wtsp = wingtipsSpan.getSpanPurpose();

        // Clunky if checks necessary to avoid code coverage gaps with a switch statement
        //      due to unreachable default case. :(
        if (SpanPurpose.SERVER == wtsp) {
            return zipkin2.Span.Kind.SERVER;
        }
        else if (SpanPurpose.CLIENT == wtsp) {
            return zipkin2.Span.Kind.CLIENT;
        }
        else if (SpanPurpose.LOCAL_ONLY == wtsp || SpanPurpose.UNKNOWN == wtsp) {
            // No Zipkin Kind associated with these SpanPurposes.
            return null;
        }
        else {
            // This case should technically be impossible, but in case it happens we'll log a warning and default to
            //      no Zipkin kind.
            logger.warn("Unhandled SpanPurpose type: {}", String.valueOf(wtsp));
            return null;
        }
    }
}
