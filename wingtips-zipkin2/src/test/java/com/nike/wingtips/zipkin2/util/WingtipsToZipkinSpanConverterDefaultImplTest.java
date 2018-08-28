package com.nike.wingtips.zipkin2.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceAndSpanIdGenerator;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Test
    public void default_constructor_sets_fields_as_expected() {
        // given
        impl = new WingtipsToZipkinSpanConverterDefaultImpl();

        // expect
        assertThat(impl.enableIdSanitization).isFalse();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void constructor_with_args_sets_fields_as_expected(boolean enableSanitization) {
        // given
        impl = new WingtipsToZipkinSpanConverterDefaultImpl(enableSanitization);

        // expect
        assertThat(impl.enableIdSanitization).isEqualTo(enableSanitization);
    }

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

        String traceId128Bits = high64Bits + low64Bits;
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        Span wingtipsSpan = Span.newBuilder("foo", SpanPurpose.CLIENT)
                                .withTraceId(traceId128Bits)
                                .withSpanStartTimeEpochMicros(startTimeEpochMicros)
                                .withDurationNanos(durationNanos)
                                .build();

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.traceId()).isEqualTo(traceId128Bits);
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
            "   \t\n\r   ",
            ""
    }, splitBy = "\\|")
    @Test
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void convertWingtipsSpanToZipkinSpan_throws_IllegalArgumentException_when_passed_wingtipsSpan_with_empty_traceId_format(final String emptyString) {
        // given
        String emptyTraceId = emptyString;
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        final Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        final Span wingtipsSpan = Span.newBuilder("foo", SpanPurpose.CLIENT)
                .withTraceId(emptyTraceId)
                .withSpanStartTimeEpochMicros(startTimeEpochMicros)
                .withDurationNanos(durationNanos)
                .build();

        // when
        Throwable ex = catchThrowable(() -> impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint));

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("traceId is empty");
    }

    private enum IdSanitizationScenario {
        NOT_HEX_STRING(
            "notahexstring",
            "15afe1fe8fe52ed3cd3efd908c45653b", // SHA 256 hash of original ID, take 32 chars
            "15afe1fe8fe52ed3"                  // SHA 256 hash of original ID, take 16 chars
        ),
        UPPERCASE_HEX_STRING(
            "DAA63E253DAB8990",
            "daa63e253dab8990", // Original ID lowercased
            "daa63e253dab8990"
        ),
        MIXED_CASE_HEX_STRING(
            "dAA63e253DaB8990",
            "daa63e253dab8990",  // Original ID lowercased
            "daa63e253dab8990"
        ),
        LONGER_THAN_16_CHARS_BUT_SHORTER_THAN_32_RAW_LONG(
            "1234567890123456789",
            "112210f47de98115", // Original ID converted to a Long, then converted to lowerhex.
            "112210f47de98115"
        ),
        LONGER_THAN_16_CHARS_BUT_SHORTER_THAN_32_NOT_A_RAW_LONG(
            "daa63e253dab8990123",
            "a3cd6fe61020c277ac30d83d18e670e5", // SHA 256 hash of original ID, take 32 chars
            "a3cd6fe61020c277"                  // SHA 256 hash of original ID, take 16 chars
        ),
        ID_IS_A_UUID(
            "98943667-2429-4019-910e-0f219a43949b",
            "9894366724294019910e0f219a43949b", // Original ID with dashes removed
            "9f26a747d46a3dd5"                  // SHA 256 hash of original ID, take 16 chars
        ),
        LONGER_THAN_32_CHARS_NOT_UUID(
            "daa63e253dab8990daa63e253dab89901234",
            "47035ab8524e9e68d14de5bf4117e555", // SHA 256 hash of original ID, take 32 chars
            "47035ab8524e9e68"                  // SHA 256 hash of original ID, take 16 chars
        ),
        EXACTLY_32_CHARS_LOWERHEX(
            "1a2b3c4d5e6f1a2b3c4d5e6f1a2b3c4d",
            "1a2b3c4d5e6f1a2b3c4d5e6f1a2b3c4d", // No need to sanitize - this is valid for trace ID
            "fe31730df9f857ee"                  // SHA 256 hash of original ID, take 16 chars
        );

        public final String originalId;
        public final String expectedSanitizedResultForTraceId; // Trace ID -> 128 bit allowed
        public final String expectedSanitizedResultForSpanIdOrParentSpanId; // Non-Trace ID -> 128 bit not allowed

        IdSanitizationScenario(
            String originalId,
            String expectedSanitizedResultForTraceId,
            String expectedSanitizedResultForSpanIdOrParentSpanId
        ) {
            this.originalId = originalId;
            this.expectedSanitizedResultForTraceId = expectedSanitizedResultForTraceId;
            this.expectedSanitizedResultForSpanIdOrParentSpanId = expectedSanitizedResultForSpanIdOrParentSpanId;
        }
    }

    @DataProvider
    @SuppressWarnings("unused")
    public static List<List<IdSanitizationScenario>> idSanitizationScenarios() {
        return Arrays.stream(IdSanitizationScenario.values())
                     .map(Collections::singletonList)
                     .collect(Collectors.toList());
    }

    @UseDataProvider("idSanitizationScenarios")
    @Test
    public void convertWingtipsSpanToZipkinSpan_sanitizes_traceId_as_expected_when_sanitization_is_enabled(
        IdSanitizationScenario scenario
    ) {
        // given
        impl = new WingtipsToZipkinSpanConverterDefaultImpl(true);
        final Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        final Span wingtipsSpan = Span.newBuilder("foo", SpanPurpose.CLIENT)
                .withTraceId(scenario.originalId)
                .withSpanStartTimeEpochMicros(Math.abs(random.nextLong()))
                .withDurationNanos(Math.abs(random.nextLong()))
                .build();

        String expectedZipkinInvalidIdTagValue = (scenario.expectedSanitizedResultForTraceId.equals(scenario.originalId))
                                                 ? null
                                                 : scenario.originalId;

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.traceId()).isEqualTo(scenario.expectedSanitizedResultForTraceId);
        assertThat(zipkinSpan.tags().get("invalid.trace_id")).isEqualTo(expectedZipkinInvalidIdTagValue);
    }

    @UseDataProvider("idSanitizationScenarios")
    @Test
    public void convertWingtipsSpanToZipkinSpan_sanitizes_spanId_as_expected_when_sanitization_is_enabled(
        IdSanitizationScenario scenario
    ) {
        // given
        impl = new WingtipsToZipkinSpanConverterDefaultImpl(true);
        final Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        final Span wingtipsSpan = Span.newBuilder("foo", SpanPurpose.CLIENT)
                                      .withSpanId(scenario.originalId)
                                      .withSpanStartTimeEpochMicros(Math.abs(random.nextLong()))
                                      .withDurationNanos(Math.abs(random.nextLong()))
                                      .build();

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.id()).isEqualTo(scenario.expectedSanitizedResultForSpanIdOrParentSpanId);
        assertThat(zipkinSpan.tags().get("invalid.span_id")).isEqualTo(scenario.originalId);
    }

    @UseDataProvider("idSanitizationScenarios")
    @Test
    public void convertWingtipsSpanToZipkinSpan_sanitizes_parentSpanId_as_expected_when_sanitization_is_enabled(
        IdSanitizationScenario scenario
    ) {
        // given
        impl = new WingtipsToZipkinSpanConverterDefaultImpl(true);
        final Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        final Span wingtipsSpan = Span.newBuilder("foo", SpanPurpose.CLIENT)
                                      .withParentSpanId(scenario.originalId)
                                      .withSpanStartTimeEpochMicros(Math.abs(random.nextLong()))
                                      .withDurationNanos(Math.abs(random.nextLong()))
                                      .build();

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.parentId()).isEqualTo(scenario.expectedSanitizedResultForSpanIdOrParentSpanId);
        assertThat(zipkinSpan.tags().get("invalid.parent_id")).isEqualTo(scenario.originalId);
    }

    @Test
    public void convertWingtipsSpanToZipkinSpan_sanitizes_multiple_IDs_as_expected_when_sanitization_is_enabled() {
        // given
        impl = new WingtipsToZipkinSpanConverterDefaultImpl(true);

        String badTraceId = UUID.randomUUID().toString();
        String expectedSanitizedTraceId = badTraceId.replace("-", "");

        String badSpanId = "notahexstring";
        String expectedSanitizedSpanId = "15afe1fe8fe52ed3"; // SHA 256 hash of original span ID, take 16 chars

        String badParentSpanId = TraceAndSpanIdGenerator.generateId().toUpperCase();
        String expectedSanitizedParentSpanId = badParentSpanId.toLowerCase();

        assertThat(expectedSanitizedTraceId).isNotEqualTo(badTraceId);
        assertThat(expectedSanitizedSpanId).isNotEqualTo(badSpanId);
        assertThat(expectedSanitizedParentSpanId).isNotEqualTo(badParentSpanId);

        final Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        final Span wingtipsSpan = Span.newBuilder("foo", SpanPurpose.CLIENT)
                                      .withTraceId(badTraceId)
                                      .withSpanId(badSpanId)
                                      .withParentSpanId(badParentSpanId)
                                      .withSpanStartTimeEpochMicros(Math.abs(random.nextLong()))
                                      .withDurationNanos(Math.abs(random.nextLong()))
                                      .build();

        // when
        zipkin2.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint);

        // then
        assertThat(zipkinSpan.traceId()).isEqualTo(expectedSanitizedTraceId);
        assertThat(zipkinSpan.id()).isEqualTo(expectedSanitizedSpanId);
        assertThat(zipkinSpan.parentId()).isEqualTo(expectedSanitizedParentSpanId);
        assertThat(zipkinSpan.tags().get("invalid.trace_id")).isEqualTo(badTraceId);
        assertThat(zipkinSpan.tags().get("invalid.span_id")).isEqualTo(badSpanId);
        assertThat(zipkinSpan.tags().get("invalid.parent_id")).isEqualTo(badParentSpanId);
    }

    @UseDataProvider("idSanitizationScenarios")
    @Test
    public void convertWingtipsSpanToZipkinSpan_does_not_sanitize_ids_if_enableIdSanitization_is_false(
        IdSanitizationScenario scenario
    ) {
        // given
        impl = new WingtipsToZipkinSpanConverterDefaultImpl(false);
        final Endpoint zipkinEndpoint = Endpoint.newBuilder().serviceName(UUID.randomUUID().toString()).build();
        final Span wingtipsSpan = Span.newBuilder("foo", SpanPurpose.CLIENT)
                                      .withTraceId(scenario.originalId)
                                      .withSpanId(scenario.originalId)
                                      .withSpanStartTimeEpochMicros(Math.abs(random.nextLong()))
                                      .withDurationNanos(Math.abs(random.nextLong()))
                                      .build();

        String expectedExceptionMessageSuffix = (scenario.originalId.length() > 16)
                                                ? "id.length > 16"
                                                : "should be lower-hex encoded with no prefix";

        // when
        Throwable ex = catchThrowable(() -> impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint));

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageEndingWith(expectedExceptionMessageSuffix);
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

    // Verify the method at a per-character level to catch all the branching logic.
    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void isHex_works_as_expected(boolean allowUppercase) {
        for (char c = Character.MIN_VALUE; c < Character.MAX_VALUE; c++) {
            // given
            boolean isHexDigit = (c >= '0') && (c <= '9');
            boolean isHexLowercase = (c >= 'a') && (c <= 'f');
            boolean isHexUppercase = (c >= 'A') && (c <= 'F');

            boolean expectedResult = isHexDigit || isHexLowercase || (allowUppercase && isHexUppercase);

            // when
            boolean result = impl.isHex(String.valueOf(c), allowUppercase);

            // then
            assertThat(result)
                .withFailMessage("Did not get expected result for char with int value " + (int)c +
                                 ". Expected result: " + expectedResult)
                .isEqualTo(expectedResult);
        }
    }
}
