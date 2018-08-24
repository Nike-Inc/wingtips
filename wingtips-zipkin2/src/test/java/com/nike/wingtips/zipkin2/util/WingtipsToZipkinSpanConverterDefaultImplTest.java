package com.nike.wingtips.zipkin2.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.HashMap;
import java.util.Map;
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
        SpanPurpose spanPurpose = wingtipsSpan.getSpanPurpose();

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
    public void convertWingtipsSpanToZipkinSpan_works_as_expected_for_all_non_null_info(SpanPurpose spanPurpose) {
        // given
        String spanName = UUID.randomUUID().toString();
        String traceId = generateId();
        String spanId = generateId();
        String parentId = generateId();
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(durationNanos);
        Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        Map<String, String> tags = createSingleTagMap();
        Span wingtipsSpan = new Span(traceId, parentId, spanId, spanName, true, null, spanPurpose, startTimeEpochMicros, null, durationNanos, tags);

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
        assertThat(zipkinSpan.tags()).isEqualTo(wingtipsSpan.getTags());

        verifySpanPurposeRelatedStuff(zipkinSpan, wingtipsSpan);
    }

    protected Map<String,String> createSingleTagMap() {
        Map<String,String> singleValue = new HashMap<String,String>(1);
        singleValue.put("tagName", "tagValue");
        return singleValue;
    }
    
    protected Map<String,String> createMultipleTagMap() {
        Map<String,String> multipleValues = createSingleTagMap();
        multipleValues.put("secondTag", "secondValue");
        return multipleValues;
    }
    
    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN"
    }, splitBy = "\\|")
    @Test
    public void convertWingtipsSpanToZipkinSpan_works_as_expected_for_all_nullable_info(SpanPurpose spanPurpose) {
        // given
        // Not a lot that can really be null - just parent span ID
        String spanName = UUID.randomUUID().toString();
        String traceId = generateId();
        String spanId = generateId();
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(durationNanos);
        Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        Span wingtipsSpan = new Span(traceId, null, spanId, spanName, true, null, spanPurpose, startTimeEpochMicros, null, durationNanos, null);

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
        assertThat(zipkinSpan.tags()).isEqualTo(wingtipsSpan.getTags());

        verifySpanPurposeRelatedStuff(zipkinSpan, wingtipsSpan);
    }

    @Test
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void convertWingtipsSpanToZipkinSpan_works_as_expected_for_128_bit_trace_id() {
        // given
        String high64Bits = "463ac35c9f6413ad";
        String low64Bits = "48485a3953bb6124";
        String hex128Bits = high64Bits + low64Bits;

        String traceId128Bits = hex128Bits;
        String spanId = low64Bits;
        String spanName = UUID.randomUUID().toString();
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        Span wingtipsSpan = Span.newBuilder(spanName, SpanPurpose.CLIENT)
                                .withTraceId(traceId128Bits)
                                .withSpanId(spanId)
                                .withSpanStartTimeEpochMicros(startTimeEpochMicros)
                                .withDurationNanos(durationNanos)
                                .build();

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.traceId()).isEqualTo(hex128Bits);
    }
    
    @Test
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void convertWingtipsSpanToZipkinSpan_works_as_expected_for_multiple_tags() {
        // given
        String high64Bits = "463ac35c9f6413ad";
        String low64Bits = "48485a3953bb6124";
        String hex128Bits = high64Bits + low64Bits;

        String traceId128Bits = hex128Bits;
        String spanId = low64Bits;
        String spanName = UUID.randomUUID().toString();
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        
        Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        Span wingtipsSpan = Span.newBuilder(spanName, SpanPurpose.CLIENT)
                                .withTraceId(traceId128Bits)
                                .withSpanId(spanId)
                                .withSpanStartTimeEpochMicros(startTimeEpochMicros)
                                .withDurationNanos(durationNanos)
                                .withTags(createMultipleTagMap())
                                .build();

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.tags()).isEqualTo(createMultipleTagMap());
    }

    @DataProvider(value = {
        "                                      ", // empty trace ID
        "123e4567-e89b-12d3-a456-426655440000  "  // UUID format (hyphens and also >32 chars)
    }, splitBy = "\\|")
    @Test
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void convertWingtipsSpanToZipkinSpan_throws_IllegalArgumentException_when_passed_wingtipsSpan_with_bad_traceId_format(final String badHexString) {
        // given
        String badTraceId = badHexString;
        String spanId = "48485a3953bb6124";
        String spanName = UUID.randomUUID().toString();
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        final Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        final Span wingtipsSpan = Span.newBuilder(spanName, SpanPurpose.CLIENT)
                                      .withTraceId(badTraceId)
                                      .withSpanId(spanId)
                                      .withSpanStartTimeEpochMicros(startTimeEpochMicros)
                                      .withDurationNanos(durationNanos)
                                      .build();

        // when
        Throwable ex = catchThrowable(() -> impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint));

        // then
        assertThat(ex).isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unused")
    private enum WingtipsSpanPurposeToZipkinKindScenario {
        SERVER(SpanPurpose.SERVER, zipkin2.Span.Kind.SERVER),
        CLIENT(SpanPurpose.CLIENT, zipkin2.Span.Kind.CLIENT),
        LOCAL_ONLY(SpanPurpose.LOCAL_ONLY, null),
        UNKNOWN(SpanPurpose.UNKNOWN, null),
        NULL(null, null);

        public final SpanPurpose wingtipsSpanPurpose;
        public final zipkin2.Span.Kind expectedZipkinKind;

        WingtipsSpanPurposeToZipkinKindScenario(SpanPurpose wingtipsSpanPurpose,
                                                zipkin2.Span.Kind expectedZipkinKind) {
            this.wingtipsSpanPurpose = wingtipsSpanPurpose;
            this.expectedZipkinKind = expectedZipkinKind;
        }
    }

    @DataProvider(value = {
        "SERVER",
        "CLIENT",
        "LOCAL_ONLY",
        "UNKNOWN",
        "NULL"
    })
    @Test
    public void determineZipkinKind_returns_expected_Zipkin_Kind_for_wingtips_SpanPurpose(
        WingtipsSpanPurposeToZipkinKindScenario scenario
    ) {
        // given
        Span wingtipsSpan = Span.newBuilder("foo", scenario.wingtipsSpanPurpose).build();
        // It's technically impossible under normal circumstances to have a null SpanPurpose on a wingtips span
        //      since it will be auto-converted to UNKNOWN, but that's the only way we can trigger the default/unhandled
        //      case in the method, so we'll use reflection to force it.
        if (scenario.wingtipsSpanPurpose == null) {
            Whitebox.setInternalState(wingtipsSpan, "spanPurpose", null);
        }

        // when
        zipkin2.Span.Kind result = impl.determineZipkinKind(wingtipsSpan);

        // then
        assertThat(result).isEqualTo(scenario.expectedZipkinKind);
    }
}
