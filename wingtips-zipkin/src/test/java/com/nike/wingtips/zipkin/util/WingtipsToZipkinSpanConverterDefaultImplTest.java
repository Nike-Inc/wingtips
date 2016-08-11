package com.nike.wingtips.zipkin.util;

import com.nike.wingtips.Span;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the functionality of {@link WingtipsToZipkinSpanConverterDefaultImpl}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsToZipkinSpanConverterDefaultImplTest {

    private WingtipsToZipkinSpanConverterDefaultImpl impl = new WingtipsToZipkinSpanConverterDefaultImpl();
    private final Random random = new Random(System.nanoTime());

    private void verifySpanPurposeRelatedStuff(zipkin.Span zipkinSpan, Span wingtipsSpan, Endpoint zipkinEndpoint, String localComponentNamespace) {
        Span.SpanPurpose spanPurpose = wingtipsSpan.getSpanPurpose();
        long startTimeEpochMicros = wingtipsSpan.getSpanStartTimeEpochMicros();
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(wingtipsSpan.getDurationNanos());

        switch(spanPurpose) {
            case SERVER:
                assertThat(zipkinSpan.annotations).hasSize(2);
                assertThat(zipkinSpan.binaryAnnotations).isEmpty();

                assertThat(zipkinSpan.annotations.get(0)).isEqualTo(Annotation.create(startTimeEpochMicros, Constants.SERVER_RECV, zipkinEndpoint));
                assertThat(zipkinSpan.annotations.get(1)).isEqualTo(Annotation.create(startTimeEpochMicros + durationMicros, Constants.SERVER_SEND, zipkinEndpoint));

                break;
            case CLIENT:
                assertThat(zipkinSpan.annotations).hasSize(2);
                assertThat(zipkinSpan.binaryAnnotations).isEmpty();

                assertThat(zipkinSpan.annotations.get(0)).isEqualTo(Annotation.create(startTimeEpochMicros, Constants.CLIENT_SEND, zipkinEndpoint));
                assertThat(zipkinSpan.annotations.get(1)).isEqualTo(Annotation.create(startTimeEpochMicros + durationMicros, Constants.CLIENT_RECV, zipkinEndpoint));

                break;
            case LOCAL_ONLY:
            case UNKNOWN:       // intentional fall-through: local and unknown span purpose are treated the same way
                assertThat(zipkinSpan.annotations).isEmpty();
                assertThat(zipkinSpan.binaryAnnotations).hasSize(1);

                assertThat(zipkinSpan.binaryAnnotations.get(0)).isEqualTo(BinaryAnnotation.create(Constants.LOCAL_COMPONENT, localComponentNamespace, zipkinEndpoint));

                break;
            default:
                throw new IllegalStateException("Unhandled spanPurpose: " + spanPurpose.name());
        }
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void convertWingtipsSpanToZipkinSpan_works_as_expected_for_all_non_null_info(Span.SpanPurpose spanPurpose) {
        // given
        String spanName = UUID.randomUUID().toString();
        String traceId = String.valueOf(random.nextLong());
        String spanId = String.valueOf(random.nextLong());
        String parentId = String.valueOf(random.nextLong());
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(durationNanos);
        Endpoint zipkinEndpoint = Endpoint.create(UUID.randomUUID().toString(), 42);
        String localComponentNamespace = UUID.randomUUID().toString();
        Span wingtipsSpan = new Span(traceId, parentId, spanId, spanName, true, null, spanPurpose, startTimeEpochMicros, null, durationNanos);

        // when
        zipkin.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint, localComponentNamespace);

        // then
        assertThat(zipkinSpan.id).isEqualTo(Long.valueOf(wingtipsSpan.getSpanId()));
        assertThat(zipkinSpan.name).isEqualTo(wingtipsSpan.getSpanName());
        assertThat(zipkinSpan.parentId).isEqualTo(Long.valueOf(wingtipsSpan.getParentSpanId()));
        assertThat(zipkinSpan.timestamp).isEqualTo(wingtipsSpan.getSpanStartTimeEpochMicros());
        assertThat(zipkinSpan.traceId).isEqualTo(Long.valueOf(wingtipsSpan.getTraceId()));
        assertThat(zipkinSpan.duration).isEqualTo(durationMicros);

        verifySpanPurposeRelatedStuff(zipkinSpan, wingtipsSpan, zipkinEndpoint, localComponentNamespace);
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void convertWingtipsSpanToZipkinSpan_works_as_expected_for_all_nullable_info(Span.SpanPurpose spanPurpose) {
        // given
        // Not a lot that can really be null - just parent span ID
        String spanName = UUID.randomUUID().toString();
        String traceId = String.valueOf(random.nextLong());
        String spanId = String.valueOf(random.nextLong());
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(durationNanos);
        Endpoint zipkinEndpoint = Endpoint.create(UUID.randomUUID().toString(), 42);
        String localComponentNamespace = UUID.randomUUID().toString();
        Span wingtipsSpan = new Span(traceId, null, spanId, spanName, true, null, spanPurpose, startTimeEpochMicros, null, durationNanos);

        // when
        zipkin.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint, localComponentNamespace);

        // then
        assertThat(zipkinSpan.id).isEqualTo(Long.valueOf(wingtipsSpan.getSpanId()));
        assertThat(zipkinSpan.name).isEqualTo(wingtipsSpan.getSpanName());
        assertThat(zipkinSpan.parentId).isNull();
        assertThat(zipkinSpan.timestamp).isEqualTo(wingtipsSpan.getSpanStartTimeEpochMicros());
        assertThat(zipkinSpan.traceId).isEqualTo(Long.valueOf(wingtipsSpan.getTraceId()));
        assertThat(zipkinSpan.duration).isEqualTo(durationMicros);

        verifySpanPurposeRelatedStuff(zipkinSpan, wingtipsSpan, zipkinEndpoint, localComponentNamespace);
    }

}