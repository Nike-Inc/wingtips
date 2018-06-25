package com.nike.wingtips.zipkin2.util;

import com.nike.wingtips.Span;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import zipkin2.Endpoint;

import static com.nike.wingtips.TraceAndSpanIdGenerator.generateId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

/**
 * Tests the functionality of {@link WingtipsToZipkinSpanConverterDefaultImpl}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsToZipkinSpanConverterDefaultImplTest {

    private WingtipsToZipkinSpanConverterDefaultImpl impl = new WingtipsToZipkinSpanConverterDefaultImpl();
    private final Random random = new Random(System.nanoTime());

    private void verifySpanPurposeRelatedStuff(zipkin2.Span zipkinSpan, Span wingtipsSpan) {
        Span.SpanPurpose spanPurpose = wingtipsSpan.getSpanPurpose();

        switch(spanPurpose) {
            case SERVER:
                assertThat(zipkinSpan.kind()).isEqualTo(zipkin2.Span.Kind.SERVER);

                break;
            case CLIENT:
                assertThat(zipkinSpan.kind()).isEqualTo(zipkin2.Span.Kind.CLIENT);

                break;
            case LOCAL_ONLY:
            case UNKNOWN:
                // intentional fall-through: local and unknown span purpose are treated the same way

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
        String traceId = generateId();
        String spanId = generateId();
        String parentId = generateId();
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(durationNanos);
        Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        Span wingtipsSpan = new Span(traceId, parentId, spanId, spanName, true, null, spanPurpose, startTimeEpochMicros, null, durationNanos);

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.id()).isEqualTo(wingtipsSpan.getSpanId());
        assertThat(zipkinSpan.name()).isEqualTo(wingtipsSpan.getSpanName());
        assertThat(zipkinSpan.parentId()).isEqualTo(wingtipsSpan.getParentSpanId());
        assertThat(zipkinSpan.timestamp()).isEqualTo(wingtipsSpan.getSpanStartTimeEpochMicros());
        assertThat(zipkinSpan.traceId()).isEqualTo(wingtipsSpan.getTraceId());
        assertThat(zipkinSpan.duration()).isEqualTo(durationMicros);
        assertThat(zipkinSpan.localEndpoint()).isEqualTo(zipkinEndpoint);

        verifySpanPurposeRelatedStuff(zipkinSpan, wingtipsSpan);
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
        String traceId = generateId();
        String spanId = generateId();
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(durationNanos);
        Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        Span wingtipsSpan = new Span(traceId, null, spanId, spanName, true, null, spanPurpose, startTimeEpochMicros, null, durationNanos);

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.id()).isEqualTo(wingtipsSpan.getSpanId());
        assertThat(zipkinSpan.name()).isEqualTo(wingtipsSpan.getSpanName());
        assertThat(zipkinSpan.parentId()).isNull();
        assertThat(zipkinSpan.timestamp()).isEqualTo(wingtipsSpan.getSpanStartTimeEpochMicros());
        assertThat(zipkinSpan.traceId()).isEqualTo(wingtipsSpan.getTraceId());
        assertThat(zipkinSpan.duration()).isEqualTo(durationMicros);
        assertThat(zipkinSpan.localEndpoint()).isEqualTo(zipkinEndpoint);

        verifySpanPurposeRelatedStuff(zipkinSpan, wingtipsSpan);
    }

    @Test
    public void convertWingtipsSpanToZipkinSpan_works_as_expected_for_128_bit_trace_id() {
        // given
        String high64Bits = "463ac35c9f6413ad";
        String low64Bits = "48485a3953bb6124";
        String hex128Bits = high64Bits + low64Bits;

        String spanName = UUID.randomUUID().toString();
        String traceId = hex128Bits;
        String spanId = low64Bits;
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        Span wingtipsSpan = new Span(traceId, null, spanId, spanName, true, null, Span.SpanPurpose.CLIENT, startTimeEpochMicros, null, durationNanos);

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.traceId()).isEqualTo(hex128Bits);
    }

    @DataProvider(value = {
        "                                      ", // empty trace ID
        "123e4567-e89b-12d3-a456-426655440000  "  // UUID format (hyphens and also >32 chars)
    }, splitBy = "\\|")
    @Test
    public void convertWingtipsSpanToZipkinSpan_throws_IllegalArgumentException(final String badHexString) {
        // given
        String spanName = UUID.randomUUID().toString();
        String traceId = badHexString;
        String spanId = "48485a3953bb6124";
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        final Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        final Span wingtipsSpan = new Span(traceId, null, spanId, spanName, true, null, Span.SpanPurpose.CLIENT, startTimeEpochMicros, null, durationNanos);

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);
            }
        });

        // then
        assertThat(ex).isInstanceOf(IllegalArgumentException.class);
    }
}
