package com.nike.wingtips.zipkin.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.TimestampedAnnotation;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.assertj.core.api.Condition;
import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;

import static com.nike.wingtips.TraceAndSpanIdGenerator.generateId;
import static com.nike.wingtips.TraceAndSpanIdGenerator.unsignedLowerHexStringToLong;
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

    private final Endpoint zipkinEndpoint = Endpoint.create(UUID.randomUUID().toString(), 42);

    private final String tagOneKey = "tagOneKey";
    private final String tagOneValue = "tagOneValue";
    private final zipkin.BinaryAnnotation tagOneAsAnnotation = BinaryAnnotation.create(
        tagOneKey, tagOneValue, zipkinEndpoint
    );
    
    private final String tagTwoKey = "tagTwoKey";
    private final String tagTwoValue = "tagTwoValue";
    private final zipkin.BinaryAnnotation tagTwoAsAnnotation = BinaryAnnotation.create(
        tagTwoKey, tagTwoValue, zipkinEndpoint
    );

    private final long annotationOneTimestamp = 12345;
    private final String annotationOneValue = "annotationOneValue";
    private final zipkin.Annotation annotationOneAsZipkinAnnotation = Annotation.create(
        annotationOneTimestamp, annotationOneValue, zipkinEndpoint
    );

    private final long annotationTwoTimestamp = 67890;
    private final String annotationTwoValue = "annotationTwoValue";
    private final zipkin.Annotation annotationTwoAsZipkinAnnotation = Annotation.create(
        annotationTwoTimestamp, annotationTwoValue, zipkinEndpoint
    );
    
    private void verifySpanPurposeRelatedStuff(
        zipkin.Span zipkinSpan,
        Span wingtipsSpan,
        Endpoint zipkinEndpoint,
        String localComponentNamespace
    ) {
        Span.SpanPurpose spanPurpose = wingtipsSpan.getSpanPurpose();
        long startTimeEpochMicros = wingtipsSpan.getSpanStartTimeEpochMicros();
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(wingtipsSpan.getDurationNanos());

        int numWingtipsAnnotations = wingtipsSpan.getTimestampedAnnotations().size();

        switch(spanPurpose) {
            case SERVER:
                assertThat(zipkinSpan.annotations).hasSize(2 + numWingtipsAnnotations);
                assertBinaryAnnotationsAreEmptyOrOnlyHaveStringTags(zipkinSpan);
                

                assertThat(zipkinSpan.annotations).contains(
                    Annotation.create(startTimeEpochMicros, Constants.SERVER_RECV, zipkinEndpoint)
                );
                assertThat(zipkinSpan.annotations).contains(
                    Annotation.create(startTimeEpochMicros + durationMicros, Constants.SERVER_SEND, zipkinEndpoint)
                );

                break;
            case CLIENT:
                assertThat(zipkinSpan.annotations).hasSize(2 + numWingtipsAnnotations);
                assertBinaryAnnotationsAreEmptyOrOnlyHaveStringTags(zipkinSpan);

                assertThat(zipkinSpan.annotations).contains(
                    Annotation.create(startTimeEpochMicros, Constants.CLIENT_SEND, zipkinEndpoint)
                );
                assertThat(zipkinSpan.annotations).contains(
                    Annotation.create(startTimeEpochMicros + durationMicros, Constants.CLIENT_RECV, zipkinEndpoint)
                );

                break;
            case LOCAL_ONLY:
            case UNKNOWN:       // intentional fall-through: local and unknown span purpose are treated the same way
                assertThat(zipkinSpan.annotations).hasSize(numWingtipsAnnotations);

                assertThat(zipkinSpan.binaryAnnotations).contains(
                    BinaryAnnotation.create(Constants.LOCAL_COMPONENT, localComponentNamespace, zipkinEndpoint)
                );

                break;
            default:
                throw new IllegalStateException("Unhandled spanPurpose: " + spanPurpose.name());
        }
    }
    
    private void assertBinaryAnnotationsAreEmptyOrOnlyHaveStringTags(zipkin.Span span) {
        //Asserts that binary annotations should be empty or only contain tags (string type)
        Condition<BinaryAnnotation> stringTypeOnly = new Condition<BinaryAnnotation>() {
            public boolean matches(BinaryAnnotation value) {
                return value.type == BinaryAnnotation.Type.STRING && !Constants.CORE_ANNOTATIONS.contains(value.key);
            }
        };
        assertThat(span.binaryAnnotations).are(stringTypeOnly);
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
        String localComponentNamespace = UUID.randomUUID().toString();
        Map<String,String> tags = createMultipleTagMap();
        List<TimestampedAnnotation> annotations = createMultipleTimestampedAnnotationList();
        Span wingtipsSpan = new Span(
            traceId, parentId, spanId, spanName, true, null, spanPurpose, startTimeEpochMicros, null, durationNanos,
            tags, annotations
        );

        // when
        zipkin.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint, localComponentNamespace);

        // then
        assertThat(zipkinSpan.id).isEqualTo(unsignedLowerHexStringToLong(wingtipsSpan.getSpanId()));
        assertThat(zipkinSpan.name).isEqualTo(wingtipsSpan.getSpanName());
        assertThat(zipkinSpan.parentId).isEqualTo(unsignedLowerHexStringToLong(wingtipsSpan.getParentSpanId()));
        assertThat(zipkinSpan.timestamp).isEqualTo(wingtipsSpan.getSpanStartTimeEpochMicros());
        assertThat(zipkinSpan.traceId).isEqualTo(unsignedLowerHexStringToLong(wingtipsSpan.getTraceId()));
        assertThat(zipkinSpan.duration).isEqualTo(durationMicros);
        assertThat(zipkinSpan.binaryAnnotations).contains(tagOneAsAnnotation, tagTwoAsAnnotation);
        assertThat(zipkinSpan.annotations).contains(annotationOneAsZipkinAnnotation, annotationTwoAsZipkinAnnotation);
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
        String traceId = generateId();
        String spanId = generateId();
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(durationNanos);
        String localComponentNamespace = UUID.randomUUID().toString();
        Span wingtipsSpan = new Span(
            traceId, null, spanId, spanName, true, null, spanPurpose, startTimeEpochMicros, null, durationNanos, null,
            null
        );

        // when
        zipkin.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint, localComponentNamespace);

        // then
        assertThat(zipkinSpan.id).isEqualTo(unsignedLowerHexStringToLong(wingtipsSpan.getSpanId()));
        assertThat(zipkinSpan.name).isEqualTo(wingtipsSpan.getSpanName());
        assertThat(zipkinSpan.parentId).isNull();
        assertThat(zipkinSpan.timestamp).isEqualTo(wingtipsSpan.getSpanStartTimeEpochMicros());
        assertThat(zipkinSpan.traceId).isEqualTo(unsignedLowerHexStringToLong(wingtipsSpan.getTraceId()));
        assertThat(zipkinSpan.duration).isEqualTo(durationMicros);
        assertThat(zipkinSpan.binaryAnnotations).doesNotContain(tagOneAsAnnotation, tagTwoAsAnnotation);
        assertThat(zipkinSpan.annotations)
            .doesNotContain(annotationOneAsZipkinAnnotation, annotationTwoAsZipkinAnnotation);

        verifySpanPurposeRelatedStuff(zipkinSpan, wingtipsSpan, zipkinEndpoint, localComponentNamespace);
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
        Endpoint zipkinEndpoint = Endpoint.create(UUID.randomUUID().toString(), 42);
        String localComponentNamespace = UUID.randomUUID().toString();
        Map<String,String> tags = createSingleTagMap();
        List<TimestampedAnnotation> annotations = createSingleTimestampedAnnotationList();
        Span wingtipsSpan = new Span(
            traceId, null, spanId, spanName, true, null, Span.SpanPurpose.CLIENT, startTimeEpochMicros, null,
            durationNanos, tags, annotations
        );

        // when
        zipkin.Span zipkinSpan = impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint, localComponentNamespace);

        // then
        assertThat(zipkinSpan.traceIdHigh).isEqualTo(unsignedLowerHexStringToLong(high64Bits));
        assertThat(zipkinSpan.traceId).isEqualTo(unsignedLowerHexStringToLong(low64Bits));
    }

    protected Map<String,String> createSingleTagMap() {
        Map<String,String> singleValue = new HashMap<String,String>(1);
        singleValue.put(tagOneKey, tagOneValue);
        return singleValue;
    }

    protected Map<String,String> createMultipleTagMap() {
        Map<String,String> multipleValues = createSingleTagMap();
        multipleValues.put(tagTwoKey, tagTwoValue);
        return multipleValues;
    }

    protected List<TimestampedAnnotation> createSingleTimestampedAnnotationList() {
        List<TimestampedAnnotation> singleAnnotationList = new ArrayList<>();
        singleAnnotationList.add(TimestampedAnnotation.forEpochMicros(annotationOneTimestamp, annotationOneValue));
        return singleAnnotationList;
    }

    protected List<TimestampedAnnotation> createMultipleTimestampedAnnotationList() {
        List<TimestampedAnnotation> multipleAnnotationList = createSingleTimestampedAnnotationList();
        multipleAnnotationList.add(TimestampedAnnotation.forEpochMicros(annotationTwoTimestamp, annotationTwoValue));
        return multipleAnnotationList;
    }

    @DataProvider(value = {
        "                                      ", // empty trace ID
        "123e4567-e89b-12d3-a456-426655440000  "  // UUID format (hyphens and also >32 chars)
    }, splitBy = "\\|")
    @Test
    public void convertWingtipsSpanToZipkinSpan_throws_NumberFormatException_for_illegal_args(final String badHexString) {
        // given
        String spanName = UUID.randomUUID().toString();
        String traceId = badHexString;
        String spanId = "48485a3953bb6124";
        long startTimeEpochMicros = Math.abs(random.nextLong());
        long durationNanos = Math.abs(random.nextLong());
        final Endpoint zipkinEndpoint = Endpoint.create(UUID.randomUUID().toString(), 42);
        final String localComponentNamespace = UUID.randomUUID().toString();
        Map<String,String> tags = createSingleTagMap();
        List<TimestampedAnnotation> annotations = createSingleTimestampedAnnotationList();
        final Span wingtipsSpan = new Span(
            traceId, null, spanId, spanName, true, null, Span.SpanPurpose.CLIENT, startTimeEpochMicros, null,
            durationNanos, tags, annotations
        );

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                impl.convertWingtipsSpanToZipkinSpan(wingtipsSpan, zipkinEndpoint, localComponentNamespace);
            }
        });

        // then
        assertThat(ex).isInstanceOf(NumberFormatException.class);
    }
}
