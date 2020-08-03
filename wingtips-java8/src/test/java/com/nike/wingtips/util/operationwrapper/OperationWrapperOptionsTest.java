package com.nike.wingtips.util.operationwrapper;

import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.util.TracingState;
import com.nike.wingtips.util.operationwrapper.OperationWrapperOptions.Builder;
import com.nike.wingtips.util.spantagger.ErrorSpanTagger;
import com.nike.wingtips.util.spantagger.SpanTagger;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

/**
 * Tests the functionality of {@link OperationWrapperOptions}.
 */
@RunWith(DataProviderRunner.class)
public class OperationWrapperOptionsTest {

    @DataProvider
    public static List<List<SpanPurpose>> spanPurposeDataProvider() {
        return Stream.of(SpanPurpose.values()).map(Collections::singletonList).collect(Collectors.toList());
    }

    @UseDataProvider("spanPurposeDataProvider")
    @Test
    public void constructor_works_as_expected(SpanPurpose spanPurpose) {
        // given
        String spanName = UUID.randomUUID().toString();
        TracingState tracingStateMock = mock(TracingState.class);
        SpanTagger<Object> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);

        Builder<Object> builder = new Builder<>(spanName, spanPurpose)
            .withParentTracingState(tracingStateMock)
            .withSpanTagger(spanTaggerMock)
            .withErrorTagger(errorTaggerMock);

        // when
        OperationWrapperOptions<Object> options = new OperationWrapperOptions<>(builder);

        // then
        assertThat(options.operationSpanName).isEqualTo(spanName);
        assertThat(options.spanPurpose).isEqualTo(spanPurpose);
        assertThat(options.parentTracingState).isEqualTo(tracingStateMock);
        assertThat(options.spanTagger).isEqualTo(spanTaggerMock);
        assertThat(options.errorTagger).isEqualTo(errorTaggerMock);
    }

    @Test
    public void constructor_allows_nulls_for_some_args() {
        // given
        String spanName = UUID.randomUUID().toString();
        SpanPurpose spanPurpose = SpanPurpose.LOCAL_ONLY;
        Builder<Object> builder = new Builder<>(spanName, spanPurpose)
            .withParentTracingState(null)
            .withSpanTagger(null)
            .withErrorTagger(null);

        // when
        OperationWrapperOptions<Object> options = new OperationWrapperOptions<>(builder);

        // then
        assertThat(options.operationSpanName).isEqualTo(spanName);
        assertThat(options.spanPurpose).isEqualTo(spanPurpose);
        assertThat(options.parentTracingState).isNull();
        assertThat(options.spanTagger).isNull();
        assertThat(options.errorTagger).isNull();
    }

    @UseDataProvider("spanPurposeDataProvider")
    @Test
    public void newBuilder_works_as_expected(SpanPurpose spanPurpose) {
        // given
        String spanName = UUID.randomUUID().toString();

        // when
        Builder<?> builder = OperationWrapperOptions.newBuilder(spanName, spanPurpose);

        // then
        assertThat(builder.operationSpanName).isEqualTo(spanName);
        assertThat(builder.spanPurpose).isEqualTo(spanPurpose);
        assertThat(builder.parentTracingState).isNull();
        assertThat(builder.spanTagger).isNull();
        assertThat(builder.errorTagger).isEqualTo(ErrorSpanTagger.DEFAULT_IMPL);
    }

    @UseDataProvider("spanPurposeDataProvider")
    @Test
    public void builder_constructor_works_as_expected(SpanPurpose spanPurpose) {
        // given
        String spanName = UUID.randomUUID().toString();

        // when
        Builder<?> builder = new Builder<>(spanName, spanPurpose);

        // then
        assertThat(builder.operationSpanName).isEqualTo(spanName);
        assertThat(builder.spanPurpose).isEqualTo(spanPurpose);
        assertThat(builder.parentTracingState).isNull();
        assertThat(builder.spanTagger).isNull();
        assertThat(builder.errorTagger).isEqualTo(ErrorSpanTagger.DEFAULT_IMPL);
    }

    private enum BlankStringScenario {
        NULL(null),
        EMPTY(""),
        WHITESPACE("  \t\n\r  ");

        public final String strValue;

        BlankStringScenario(String strValue) {
            this.strValue = strValue;
        }
    }

    @DataProvider
    public static List<List<BlankStringScenario>> blankStringScenarioDataProvider() {
        return Stream.of(BlankStringScenario.values()).map(Collections::singletonList).collect(Collectors.toList());
    }

    @UseDataProvider("blankStringScenarioDataProvider")
    @Test
    public void builder_constructor_throws_IllegalArgumentException_if_passed_blank_span_name(
        BlankStringScenario scenario
    ) {
        // when
        Throwable ex = catchThrowable(() -> new Builder<>(scenario.strValue, SpanPurpose.LOCAL_ONLY));

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("operationSpanName cannot be null, empty, or blank");
    }

    @Test
    public void builder_constructor_throws_NullPointerException_if_passed_null_SpanPurpose() {
        // when
        Throwable ex = catchThrowable(() -> new Builder<>(UUID.randomUUID().toString(), null));

        // then
        assertThat(ex)
            .isInstanceOf(NullPointerException.class)
            .hasMessage("spanPurpose cannot be null");
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void builder_withParentTracingState_works_as_expected(boolean valueIsNull) {
        // given
        Builder<?> builder = new Builder<>(UUID.randomUUID().toString(), SpanPurpose.LOCAL_ONLY);
        TracingState tcMock = (valueIsNull) ? null : mock(TracingState.class);

        // when
        Builder<?> result = builder.withParentTracingState(tcMock);

        // then
        assertThat(builder.parentTracingState).isSameAs(tcMock);
        assertThat(result).isSameAs(builder);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void builder_withSpanTagger_works_as_expected(boolean valueIsNull) {
        // given
        Builder<Object> builder = new Builder<>(UUID.randomUUID().toString(), SpanPurpose.LOCAL_ONLY);
        SpanTagger<Object> taggerMock = (valueIsNull) ? null : mock(SpanTagger.class);

        // when
        Builder<Object> result = builder.withSpanTagger(taggerMock);

        // then
        assertThat(builder.spanTagger).isSameAs(taggerMock);
        assertThat(result).isSameAs(builder);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void builder_withErrorTagger_works_as_expected(boolean valueIsNull) {
        // given
        Builder<Object> builder = new Builder<>(UUID.randomUUID().toString(), SpanPurpose.LOCAL_ONLY);
        ErrorSpanTagger errorTaggerMock = (valueIsNull) ? null : mock(ErrorSpanTagger.class);

        // when
        Builder<Object> result = builder.withErrorTagger(errorTaggerMock);

        // then
        assertThat(builder.errorTagger).isSameAs(errorTaggerMock);
        assertThat(result).isSameAs(builder);
    }

    @UseDataProvider("spanPurposeDataProvider")
    @Test
    public void builder_build_method_works_as_expected(SpanPurpose spanPurpose) {
        // given
        String spanName = UUID.randomUUID().toString();
        TracingState tracingStateMock = mock(TracingState.class);
        SpanTagger<Object> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);

        Builder<Object> builder = new Builder<>(spanName, spanPurpose)
            .withParentTracingState(tracingStateMock)
            .withSpanTagger(spanTaggerMock)
            .withErrorTagger(errorTaggerMock);

        // when
        OperationWrapperOptions<Object> result = builder.build();

        // then
        assertThat(result.operationSpanName).isEqualTo(spanName);
        assertThat(result.spanPurpose).isEqualTo(spanPurpose);
        assertThat(result.parentTracingState).isEqualTo(tracingStateMock);
        assertThat(result.spanTagger).isEqualTo(spanTaggerMock);
        assertThat(result.errorTagger).isEqualTo(errorTaggerMock);
    }

}