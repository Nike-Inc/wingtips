package com.nike.wingtips.zipkin.util;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.assertj.core.api.ThrowableAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import zipkin.Codec;
import zipkin.junit.HttpFailure;
import zipkin.junit.ZipkinRule;
import zipkin.reporter.internal.AwaitableCallback;
import zipkin.reporter.urlconnection.URLConnectionReporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link ZipkinSpanSenderHttpImpl}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class ZipkinSpanSenderHttpImplTest {

    @Rule
    public final ZipkinRule zipkinRule = new ZipkinRule();

    private final Random random = new Random(System.nanoTime());

    private String baseUrl;
    private boolean compressPayloads;
    private ZipkinSpanSenderHttpImpl implSpy;

    @Before
    public void beforeMethod() {
        baseUrl = zipkinRule.httpUrl();
        compressPayloads = true;
        implSpy = spy(new ZipkinSpanSenderHttpImpl(baseUrl, compressPayloads));
    }

    @DataProvider(value = {
        "http://localhost:4242  |   http://localhost:4242/api/v1/spans  |   true    |   1042",
        "http://localhost:4242/ |   http://localhost:4242/api/v1/spans  |   false   |   0",
    }, splitBy = "\\|")
    @Test
    public void kitchen_sink_constructor_configures_zipkin_reporter_and_sets_fields_and_kicks_off_scheduled_job_as_expected(
        String baseUrl, String expectedFinalUrl, boolean compressPayloadOpt, int batchSendingPeriodMillis
    ) throws MalformedURLException, ExecutionException, InterruptedException {
        // given
        int connectTimeoutMillis = Math.abs(random.nextInt());
        int readTimeoutMillis = Math.abs(random.nextInt());
        final ScheduledExecutorService schedulerMock = mock(ScheduledExecutorService.class);

        // when
        ZipkinSpanSenderHttpImpl impl = new ZipkinSpanSenderHttpImpl(
            baseUrl, compressPayloadOpt, connectTimeoutMillis, readTimeoutMillis, batchSendingPeriodMillis
        ) {
            @Override
            protected ScheduledExecutorService configureScheduledExecutorServiceForSchedulingBatchSends() {
                return schedulerMock;
            }
        };

        // then
        assertThat(impl.zipkinReporter).isInstanceOf(URLConnectionReporter.class);
        URLConnectionReporter httpReporter = (URLConnectionReporter) impl.zipkinReporter;
        assertThat(Whitebox.getInternalState(httpReporter, "endpoint")).isEqualTo(new URL(expectedFinalUrl));
        assertThat(Whitebox.getInternalState(httpReporter, "compressionEnabled")).isEqualTo(compressPayloadOpt);
        assertThat(Whitebox.getInternalState(httpReporter, "connectTimeout")).isEqualTo(connectTimeoutMillis);
        assertThat(Whitebox.getInternalState(httpReporter, "readTimeout")).isEqualTo(readTimeoutMillis);
        assertThat(impl.senderJob).isNotNull();

        assertThat(impl.senderJob.zipkinSpanSender).isSameAs(impl);
        assertThat(impl.senderJob.zipkinSpanSendingQueue).isSameAs(impl.zipkinSpanSendingQueue);

        if (batchSendingPeriodMillis > 0) {
            verify(schedulerMock).scheduleAtFixedRate(impl.senderJob, batchSendingPeriodMillis, batchSendingPeriodMillis, TimeUnit.MILLISECONDS);
        }
        else {
            verifyZeroInteractions(schedulerMock);
        }

        // and when
        ExecutorService sendingExecutor = (ExecutorService) Whitebox.getInternalState(httpReporter, "executor");
        final List<String> threadNameHolder = new ArrayList<>();
        sendingExecutor.submit(new Runnable() {
            @Override
            public void run() {
                threadNameHolder.add(Thread.currentThread().getName());
            }
        }).get();

        // then
        assertThat(threadNameHolder).containsExactly("zipkin-span-sender");
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void convenience_constructor_configures_zipkin_reporter_and_sets_fields_and_kicks_off_scheduled_job_as_expected(boolean compressPayloadOpt)
        throws MalformedURLException, ExecutionException, InterruptedException {
        // given
        String baseUrl = "http://localhost:4242";
        final ScheduledExecutorService schedulerMock = mock(ScheduledExecutorService.class);

        // when
        ZipkinSpanSenderHttpImpl impl = new ZipkinSpanSenderHttpImpl(baseUrl, compressPayloadOpt) {
            @Override
            protected ScheduledExecutorService configureScheduledExecutorServiceForSchedulingBatchSends() {
                return schedulerMock;
            }
        };

        // then
        assertThat(impl.zipkinReporter).isInstanceOf(URLConnectionReporter.class);
        URLConnectionReporter httpReporter = (URLConnectionReporter) impl.zipkinReporter;
        assertThat(Whitebox.getInternalState(httpReporter, "endpoint")).isEqualTo(new URL(baseUrl + "/api/v1/spans"));
        assertThat(Whitebox.getInternalState(httpReporter, "compressionEnabled")).isEqualTo(compressPayloadOpt);
        assertThat(Whitebox.getInternalState(httpReporter, "connectTimeout")).isEqualTo(ZipkinSpanSenderHttpImpl.DEFAULT_CONNECT_TIMEOUT_MILLIS);
        assertThat(Whitebox.getInternalState(httpReporter, "readTimeout")).isEqualTo(ZipkinSpanSenderHttpImpl.DEFAULT_READ_TIMEOUT_MILLIS);
        assertThat(impl.senderJob).isNotNull();

        assertThat(impl.senderJob.zipkinSpanSender).isSameAs(impl);
        assertThat(impl.senderJob.zipkinSpanSendingQueue).isSameAs(impl.zipkinSpanSendingQueue);
        verify(schedulerMock).scheduleAtFixedRate(impl.senderJob,
                                                  ZipkinSpanSenderZipkinReporterImpl.DEFAULT_SPAN_BATCH_SENDING_PERIOD_MILLIS,
                                                  ZipkinSpanSenderZipkinReporterImpl.DEFAULT_SPAN_BATCH_SENDING_PERIOD_MILLIS,
                                                  TimeUnit.MILLISECONDS);

        // and when
        ExecutorService sendingExecutor = (ExecutorService) Whitebox.getInternalState(httpReporter, "executor");
        final List<String> threadNameHolder = new ArrayList<>();
        sendingExecutor.submit(new Runnable() {
            @Override
            public void run() {
                threadNameHolder.add(Thread.currentThread().getName());
            }
        }).get();

        // then
        assertThat(threadNameHolder).containsExactly("zipkin-span-sender");
    }

    @Test
    public void constructor_throws_wrapped_MalformedURLException_if_url_is_malformed() {
        // given
        final String badUrl = "iamnotaurl!@#%^*&&*";

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                new ZipkinSpanSenderHttpImpl(badUrl, true);
            }
        });

        // then
        assertThat(ex)
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(MalformedURLException.class);
    }

    @Test
    public void sendSpans_sends_to_zipkin_server_as_expected() throws InterruptedException {
        // given
        List<zipkin.Span> zipkinSpans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            zipkinSpans.add(zipkinSpan(random.nextLong(), UUID.randomUUID().toString()));
        }
        AwaitableCallback blockingCallback = new AwaitableCallback();

        // when
        implSpy.sendSpans(zipkinSpans, blockingCallback);
        blockingCallback.await();

        // then
        assertThat(zipkinRule.httpRequestCount()).isEqualTo(1);
        // Each span has its own trace ID
        List<zipkin.Span>[] expectedTraces = new List[zipkinSpans.size()];
        for (int i = 0; i < zipkinSpans.size(); i++) {
            zipkin.Span span = zipkinSpans.get(i);
            expectedTraces[i] = Collections.singletonList(span);
        }
        assertThat(zipkinRule.getTraces()).contains(expectedTraces);
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void sendSpans_sends_compressed_and_uncompressed_span_payloads_as_expected(boolean compressPayload) throws IOException, InterruptedException {
        MockWebServer zipkinServer = new MockWebServer();
        try {
            // given
            zipkinServer.start(0);
            zipkinServer.enqueue(new MockResponse());

            Whitebox.setInternalState(implSpy.zipkinReporter, "compressionEnabled", compressPayload);
            Whitebox.setInternalState(implSpy.zipkinReporter, "endpoint", new URL(zipkinServer.url("/api/v1/spans").toString()));

            List<zipkin.Span> sentSpans = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                sentSpans.add(zipkinSpan(random.nextLong(), UUID.randomUUID().toString()));
            }
            long expectedUncompressedPayloadSize = Codec.JSON.writeSpans(sentSpans).length;
            AwaitableCallback blockingCallback = new AwaitableCallback();

            // when
            implSpy.sendSpans(sentSpans, blockingCallback);
            blockingCallback.await();

            // then
            RecordedRequest zipkinServerReq = zipkinServer.takeRequest();
            long receivedByteCount = zipkinServerReq.getBodySize();
            if (compressPayload)
                assertThat(receivedByteCount).isLessThan(expectedUncompressedPayloadSize);
            else
                assertThat(receivedByteCount).isEqualTo(expectedUncompressedPayloadSize);

            byte[] receivedPayloadBytes = zipkinServerReq.getBody().readByteArray();
            byte[] deserializableBytes = (compressPayload) ? unGzip(receivedPayloadBytes) : receivedPayloadBytes;

            List<zipkin.Span> receivedSpans = Codec.JSON.readSpans(deserializableBytes);
            assertThat(receivedSpans).isEqualTo(sentSpans);
        } finally {
            zipkinServer.shutdown();
        }
    }

    private byte[] unGzip(byte[] orig) throws IOException {
        ByteArrayInputStream gzippedBytes = new ByteArrayInputStream(orig);
        GZIPInputStream gzipInputStream = new GZIPInputStream(gzippedBytes);
        ByteArrayOutputStream ungzippedBytes = new ByteArrayOutputStream();

        int res = 0;
        byte buf[] = new byte[1024];
        while (res >= 0) {
            res = gzipInputStream.read(buf, 0, buf.length);
            if (res > 0) {
                ungzippedBytes.write(buf, 0, res);
            }
        }
        return ungzippedBytes.toByteArray();
    }

    @Test
    public void sendSpans_does_not_propagate_5xx_errors() throws Exception {
        // given
        zipkinRule.enqueueFailure(HttpFailure.sendErrorResponse(500, "Server Error!"));
        final AwaitableCallback blockingCallback = new AwaitableCallback();

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                implSpy.sendSpans(Collections.singletonList(zipkinSpan(42, "foo")), blockingCallback);
            }
        });

        // then
        assertThat(ex).isNull();

        // and when
        Throwable callbackEx = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                blockingCallback.await();
            }
        });

        // then
        assertThat(callbackEx)
            .isNotNull()
            .hasMessageContaining("Server returned HTTP response code: 500");
    }


    @Test
    public void sendSpans_does_not_propagate_connection_errors() throws Exception {
        // given
        zipkinRule.enqueueFailure(HttpFailure.disconnectDuringBody());
        final AwaitableCallback blockingCallback = new AwaitableCallback();

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                implSpy.sendSpans(Collections.singletonList(zipkinSpan(42, "foo")), blockingCallback);
            }
        });

        // then
        assertThat(ex).isNull();

        // and when
        Throwable callbackEx = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                blockingCallback.await();
            }
        });

        // then
        assertThat(callbackEx)
            .isNotNull()
            .hasMessageContaining("Unexpected end of file from server");
    }

    static zipkin.Span zipkinSpan(long traceId, String spanName) {
        return zipkin.Span.builder().traceId(traceId).id(traceId).name(spanName).build();
    }

}