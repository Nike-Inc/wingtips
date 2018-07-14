package com.nike.wingtips.zipkin2;

import com.nike.wingtips.Span;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverter;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverterDefaultImpl;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import zipkin2.Endpoint;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import static com.nike.wingtips.zipkin2.WingtipsToZipkinLifecycleListener.MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link WingtipsToZipkinLifecycleListener}
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsToZipkinLifecycleListenerTest {

    private WingtipsToZipkinLifecycleListener listener;
    private String serviceName;
    private WingtipsToZipkinSpanConverter spanConverterMock;
    private Reporter<zipkin2.Span> spanReporterMock;
    private Span spanMock;

    @Before
    @SuppressWarnings("unchecked")
    public void beforeMethod() {
        serviceName = UUID.randomUUID().toString();
        spanConverterMock = mock(WingtipsToZipkinSpanConverter.class);
        spanReporterMock = mock(Reporter.class);

        listener = new WingtipsToZipkinLifecycleListener(serviceName, spanConverterMock, spanReporterMock);

        spanMock = mock(Span.class);
    }

    @Test
    public void kitchen_sink_constructor_sets_fields_as_expected() {
        // when
        WingtipsToZipkinLifecycleListener listener = new WingtipsToZipkinLifecycleListener(
            serviceName, spanConverterMock, spanReporterMock
        );

        // then
        assertThat(listener.serviceName).isEqualTo(serviceName);
        assertThat(listener.zipkinEndpoint.serviceName()).isEqualTo(serviceName);
        assertThat(listener.zipkinSpanConverter).isSameAs(spanConverterMock);
        assertThat(listener.zipkinSpanReporter).isSameAs(spanReporterMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void convenience_constructor_sets_fields_as_expected(boolean baseUrlTrailingSlash) throws MalformedURLException {
        // given
        String baseUrlWithoutTrailingSlash = "http://localhost:4242";
        String baseUrl = (baseUrlTrailingSlash)
                         ? baseUrlWithoutTrailingSlash + "/"
                         : baseUrlWithoutTrailingSlash;

        // when
        WingtipsToZipkinLifecycleListener listener = new WingtipsToZipkinLifecycleListener(serviceName, baseUrl);

        // then
        assertThat(listener.serviceName).isEqualTo(serviceName);
        assertThat(listener.zipkinEndpoint.serviceName()).isEqualTo(serviceName);
        assertThat(listener.zipkinSpanConverter).isInstanceOf(WingtipsToZipkinSpanConverterDefaultImpl.class);
        assertThat(listener.zipkinSpanReporter).isInstanceOf(AsyncReporter.class);
        Object spanSender = Whitebox.getInternalState(listener.zipkinSpanReporter, "sender");
        assertThat(spanSender).isInstanceOf(URLConnectionSender.class);
        assertThat(Whitebox.getInternalState(spanSender, "endpoint"))
            .isEqualTo(new URL(baseUrlWithoutTrailingSlash + "/api/v2/spans"));
    }

    @Test
    public void spanStarted_does_nothing() {
        // when
        listener.spanStarted(spanMock);

        // then
        verifyZeroInteractions(spanConverterMock, spanReporterMock, spanMock);
    }

    @Test
    public void spanSampled_does_nothing() {
        // when
        listener.spanSampled(spanMock);

        // then
        verifyZeroInteractions(spanConverterMock, spanReporterMock, spanMock);
    }

    @Test
    public void spanCompleted_converts_to_zipkin_span_and_passes_it_to_zipkinSpanReporter() {
        // given
        zipkin2.Span zipkinSpan = zipkin2.Span.newBuilder().traceId("42").id("4242").name("foo").build();
        doReturn(zipkinSpan).when(spanConverterMock).convertWingtipsSpanToZipkinSpan(any(Span.class), any(Endpoint.class));

        // when
        listener.spanCompleted(spanMock);

        // then
        verify(spanConverterMock).convertWingtipsSpanToZipkinSpan(spanMock, listener.zipkinEndpoint);
        verify(spanReporterMock).report(zipkinSpan);
    }

    @Test
    public void spanCompleted_does_not_propagate_exceptions_generated_by_span_converter() {
        // given
        doThrow(new RuntimeException("kaboom")).when(spanConverterMock).convertWingtipsSpanToZipkinSpan(any(Span.class), any(Endpoint.class));

        // when
        Throwable ex = catchThrowable(() -> listener.spanCompleted(spanMock));

        // then
        verify(spanConverterMock).convertWingtipsSpanToZipkinSpan(spanMock, listener.zipkinEndpoint);
        verifyZeroInteractions(spanReporterMock);
        assertThat(ex).isNull();
    }

    @Test
    public void spanCompleted_does_not_propagate_exceptions_generated_by_span_reporter() {
        // given
        doThrow(new RuntimeException("kaboom")).when(spanReporterMock).report(any(zipkin2.Span.class));

        // when
        Throwable ex = catchThrowable(() -> listener.spanCompleted(spanMock));

        // then
        verify(spanReporterMock).report(any(zipkin2.Span.class));
        assertThat(ex).isNull();
    }

    @Test
    public void spanCompleted_logs_error_during_handling_if_time_since_lastSpanHandlingErrorLogTimeEpochMillis_is_greater_than_MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS() {
        // given
        Logger loggerMock = mock(Logger.class);
        Whitebox.setInternalState(listener, "zipkinConversionOrReportingErrorLogger", loggerMock);
        long lastLogTimeToSet = System.currentTimeMillis() - (MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS + 10);
        Whitebox.setInternalState(listener, "lastSpanHandlingErrorLogTimeEpochMillis", lastLogTimeToSet);
        doThrow(new RuntimeException("kaboom")).when(spanReporterMock).report(any(zipkin2.Span.class));

        // when
        long before = System.currentTimeMillis();
        listener.spanCompleted(spanMock);
        long after = System.currentTimeMillis();

        // then
        verify(loggerMock).warn(anyString(), anyLong(), anyString(), anyString());
        // Also verify that the lastSpanHandlingErrorLogTimeEpochMillis value got updated.
        assertThat((long)Whitebox.getInternalState(listener, "lastSpanHandlingErrorLogTimeEpochMillis")).isBetween(before, after);
    }

    @Test
    public void spanCompleted_does_not_log_an_error_during_handling_if_time_since_lastSpanHandlingErrorLogTimeEpochMillis_is_less_than_MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS() {
        // given
        Logger loggerMock = mock(Logger.class);
        Whitebox.setInternalState(listener, "zipkinConversionOrReportingErrorLogger", loggerMock);
        long lastLogTimeToSet = System.currentTimeMillis() - (MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS - 1000);
        Whitebox.setInternalState(listener, "lastSpanHandlingErrorLogTimeEpochMillis", lastLogTimeToSet);
        doThrow(new RuntimeException("kaboom")).when(spanReporterMock).report(any(zipkin2.Span.class));

        // when
        listener.spanCompleted(spanMock);

        // then
        verifyZeroInteractions(loggerMock);
        // Also verify that the lastSpanHandlingErrorLogTimeEpochMillis value was *not* updated.
        assertThat((long)Whitebox.getInternalState(listener, "lastSpanHandlingErrorLogTimeEpochMillis")).isEqualTo(lastLogTimeToSet);
    }
}