package com.nike.wingtips.zipkin;

import com.nike.wingtips.Span;
import com.nike.wingtips.zipkin.util.WingtipsToZipkinSpanConverter;
import com.nike.wingtips.zipkin.util.WingtipsToZipkinSpanConverterDefaultImpl;
import com.nike.wingtips.zipkin.util.ZipkinSpanSender;
import com.nike.wingtips.zipkin.util.ZipkinSpanSenderDefaultHttpImpl;

import org.assertj.core.api.ThrowableAssert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import zipkin.Endpoint;

import static com.nike.wingtips.zipkin.WingtipsToZipkinLifecycleListener.MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS;
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
public class WingtipsToZipkinLifecycleListenerTest {

    private WingtipsToZipkinLifecycleListener listener;
    private String serviceName;
    private String localComponentNamespace;
    private WingtipsToZipkinSpanConverter spanConverterMock;
    private ZipkinSpanSender spanSenderMock;
    private Span spanMock;

    @Before
    public void beforeMethod() {
        serviceName = UUID.randomUUID().toString();
        localComponentNamespace = UUID.randomUUID().toString();
        spanConverterMock = mock(WingtipsToZipkinSpanConverter.class);
        spanSenderMock = mock(ZipkinSpanSender.class);

        listener = new WingtipsToZipkinLifecycleListener(serviceName, localComponentNamespace, spanConverterMock, spanSenderMock);

        spanMock = mock(Span.class);
    }

    @Test
    public void kitchen_sink_constructor_sets_fields_as_expected() {
        // when
        WingtipsToZipkinLifecycleListener listener = new WingtipsToZipkinLifecycleListener(serviceName, localComponentNamespace, spanConverterMock, spanSenderMock);

        // then
        assertThat(listener.serviceName).isEqualTo(serviceName);
        assertThat(listener.zipkinEndpoint.serviceName).isEqualTo(serviceName);
        assertThat(listener.localComponentNamespace).isEqualTo(localComponentNamespace);
        assertThat(listener.zipkinSpanConverter).isSameAs(spanConverterMock);
        assertThat(listener.zipkinSpanSender).isSameAs(spanSenderMock);
    }

    @Test
    public void convenience_constructor_sets_fields_as_expected() throws MalformedURLException {
        // given
        String baseUrl = "http://localhost:4242";

        // when
        WingtipsToZipkinLifecycleListener listener = new WingtipsToZipkinLifecycleListener(serviceName, localComponentNamespace, baseUrl);

        // then
        assertThat(listener.serviceName).isEqualTo(serviceName);
        assertThat(listener.zipkinEndpoint.serviceName).isEqualTo(serviceName);
        assertThat(listener.localComponentNamespace).isEqualTo(localComponentNamespace);
        assertThat(listener.zipkinSpanConverter).isInstanceOf(WingtipsToZipkinSpanConverterDefaultImpl.class);
        assertThat(listener.zipkinSpanSender).isInstanceOf(ZipkinSpanSenderDefaultHttpImpl.class);
        ZipkinSpanSenderDefaultHttpImpl spanSender = (ZipkinSpanSenderDefaultHttpImpl)listener.zipkinSpanSender;
        assertThat(Whitebox.getInternalState(spanSender, "postZipkinSpansUrl")).isEqualTo(new URL(baseUrl + "/api/v1/spans"));
    }

    @Test
    public void spanStarted_does_nothing() {
        // when
        listener.spanStarted(spanMock);

        // then
        verifyZeroInteractions(spanConverterMock, spanSenderMock, spanMock);
    }

    @Test
    public void spanSampled_does_nothing() {
        // when
        listener.spanSampled(spanMock);

        // then
        verifyZeroInteractions(spanConverterMock, spanSenderMock, spanMock);
    }

    @Test
    public void spanCompleted_converts_to_zipkin_span_and_passes_it_to_zipkinSpanSender() {
        // given
        zipkin.Span zipkinSpan = zipkin.Span.builder().traceId(42).id(4242).name("foo").build();
        doReturn(zipkinSpan).when(spanConverterMock).convertWingtipsSpanToZipkinSpan(any(Span.class), any(Endpoint.class), any(String.class));

        // when
        listener.spanCompleted(spanMock);

        // then
        verify(spanConverterMock).convertWingtipsSpanToZipkinSpan(spanMock, listener.zipkinEndpoint, localComponentNamespace);
        verify(spanSenderMock).handleSpan(zipkinSpan);
    }

    @Test
    public void spanCompleted_does_not_propagate_exceptions_generated_by_span_converter() {
        // given
        doThrow(new RuntimeException("kaboom")).when(spanConverterMock).convertWingtipsSpanToZipkinSpan(any(Span.class), any(Endpoint.class), any(String.class));

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                listener.spanCompleted(spanMock);
            }
        });

        // then
        verify(spanConverterMock).convertWingtipsSpanToZipkinSpan(spanMock, listener.zipkinEndpoint, localComponentNamespace);
        verifyZeroInteractions(spanSenderMock);
        assertThat(ex).isNull();
    }

    @Test
    public void spanCompleted_does_not_propagate_exceptions_generated_by_span_sender() {
        // given
        doThrow(new RuntimeException("kaboom")).when(spanSenderMock).handleSpan(any(zipkin.Span.class));

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                listener.spanCompleted(spanMock);
            }
        });

        // then
        verify(spanSenderMock).handleSpan(any(zipkin.Span.class));
        assertThat(ex).isNull();
    }

    @Test
    public void spanCompleted_logs_error_during_handling_if_time_since_lastSpanHandlingErrorLogTimeEpochMillis_is_greater_than_MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS() throws InterruptedException {
        // given
        Logger loggerMock = mock(Logger.class);
        Whitebox.setInternalState(listener, "zipkinConversionOrReportingErrorLogger", loggerMock);
        long lastLogTimeToSet = System.currentTimeMillis() - (MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS + 10);
        Whitebox.setInternalState(listener, "lastSpanHandlingErrorLogTimeEpochMillis", lastLogTimeToSet);
        doThrow(new RuntimeException("kaboom")).when(spanSenderMock).handleSpan(any(zipkin.Span.class));

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
    public void spanCompleted_does_not_log_an_error_during_handling_if_time_since_lastSpanHandlingErrorLogTimeEpochMillis_is_less_than_MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS() throws InterruptedException {
        // given
        Logger loggerMock = mock(Logger.class);
        Whitebox.setInternalState(listener, "zipkinConversionOrReportingErrorLogger", loggerMock);
        long lastLogTimeToSet = System.currentTimeMillis() - (MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS - 1000);
        Whitebox.setInternalState(listener, "lastSpanHandlingErrorLogTimeEpochMillis", lastLogTimeToSet);
        doThrow(new RuntimeException("kaboom")).when(spanSenderMock).handleSpan(any(zipkin.Span.class));

        // when
        listener.spanCompleted(spanMock);

        // then
        verifyZeroInteractions(loggerMock);
        // Also verify that the lastSpanHandlingErrorLogTimeEpochMillis value was *not* updated.
        assertThat((long)Whitebox.getInternalState(listener, "lastSpanHandlingErrorLogTimeEpochMillis")).isEqualTo(lastLogTimeToSet);
    }
}