package com.nike.wingtips.zipkin.util;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import java.util.concurrent.LinkedBlockingQueue;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.junit.HttpFailure;
import zipkin2.junit.ZipkinRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link ZipkinSpanSenderDefaultHttpImpl}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class ZipkinSpanSenderDefaultHttpImplTest {

    @Rule
    public final ZipkinRule zipkinRule = new ZipkinRule();

    private final Random random = new Random(System.nanoTime());

    private String baseUrl;
    private boolean compressPayloads;
    private ZipkinSpanSenderDefaultHttpImpl implSpy;
    private ScheduledExecutorService schedulerMock;

    private static class ZipkinSpanSenderDefaultHttpImplForTesting extends ZipkinSpanSenderDefaultHttpImpl {
        public ScheduledExecutorService schedulerMock;

        public ZipkinSpanSenderDefaultHttpImplForTesting(String postZipkinSpansBaseUrl, boolean compressZipkinSpanPayload) {
            super(postZipkinSpansBaseUrl, compressZipkinSpanPayload);
        }

        @Override
        protected ScheduledExecutorService configureScheduledExecutorServiceForBatching() {
            if (schedulerMock == null)
                schedulerMock = mock(ScheduledExecutorService.class);
            return schedulerMock;
        }
    }

    @Before
    public void beforeMethod() {
        baseUrl = zipkinRule.httpUrl();
        compressPayloads = true;
        implSpy = spy(new ZipkinSpanSenderDefaultHttpImplForTesting(baseUrl, compressPayloads));
        schedulerMock = ((ZipkinSpanSenderDefaultHttpImplForTesting)implSpy).schedulerMock;
    }

    @DataProvider(value = {
        "http://localhost:4242  |   http://localhost:4242/api/v1/spans  |   true    |   1042",
        "http://localhost:4242/ |   http://localhost:4242/api/v1/spans  |   false   |   0",
    }, splitBy = "\\|")
    @Test
    public void kitchen_sink_constructor_sets_fields_and_kicks_off_scheduled_job_as_expected(
        String baseUrl, String expectedFinalUrl, boolean compressPayloadOpt, int batchSendingPeriodMillis
    ) throws MalformedURLException {
        // given
        int connectTimeoutMillis = Math.abs(random.nextInt());
        int readTimeoutMillis = Math.abs(random.nextInt());
        final ScheduledExecutorService schedulerMock = mock(ScheduledExecutorService.class);

        // when
        ZipkinSpanSenderDefaultHttpImpl impl = new ZipkinSpanSenderDefaultHttpImpl(
            baseUrl, compressPayloadOpt, connectTimeoutMillis, readTimeoutMillis, batchSendingPeriodMillis
        ) {
            @Override
            protected ScheduledExecutorService configureScheduledExecutorServiceForBatching() {
                return schedulerMock;
            }
        };

        // then
        assertThat(impl.postZipkinSpansUrl).isEqualTo(new URL(expectedFinalUrl));
        assertThat(impl.compressZipkinSpanPayload).isEqualTo(compressPayloadOpt);
        assertThat(impl.connectTimeoutMillis).isEqualTo(connectTimeoutMillis);
        assertThat(impl.readTimeoutMillis).isEqualTo(readTimeoutMillis);
        assertThat(impl.senderJob).isNotNull();

        assertThat(impl.senderJob.zipkinSpanSender).isSameAs(impl);
        assertThat(impl.senderJob.zipkinSpanSendingQueue).isSameAs(impl.zipkinSpanSendingQueue);

        if (batchSendingPeriodMillis > 0) {
            verify(schedulerMock).scheduleAtFixedRate(impl.senderJob, batchSendingPeriodMillis, batchSendingPeriodMillis, TimeUnit.MILLISECONDS);
        }
        else {
            verifyZeroInteractions(schedulerMock);
        }
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void convenience_constructor_sets_fields_and_kicks_off_scheduled_job_as_expected(boolean compressPayloadOpt) throws MalformedURLException {
        // given
        String baseUrl = "http://localhost:4242";
        final ScheduledExecutorService schedulerMock = mock(ScheduledExecutorService.class);

        // when
        ZipkinSpanSenderDefaultHttpImpl impl = new ZipkinSpanSenderDefaultHttpImpl(baseUrl, compressPayloadOpt) {
            @Override
            protected ScheduledExecutorService configureScheduledExecutorServiceForBatching() {
                return schedulerMock;
            }
        };

        // then
        assertThat(impl.postZipkinSpansUrl).isEqualTo(new URL(baseUrl + "/api/v1/spans"));
        assertThat(impl.compressZipkinSpanPayload).isEqualTo(compressPayloadOpt);
        assertThat(impl.connectTimeoutMillis).isEqualTo(ZipkinSpanSenderDefaultHttpImpl.DEFAULT_CONNECT_TIMEOUT_MILLIS);
        assertThat(impl.readTimeoutMillis).isEqualTo(ZipkinSpanSenderDefaultHttpImpl.DEFAULT_READ_TIMEOUT_MILLIS);
        assertThat(impl.senderJob).isNotNull();

        assertThat(impl.senderJob.zipkinSpanSender).isSameAs(impl);
        assertThat(impl.senderJob.zipkinSpanSendingQueue).isSameAs(impl.zipkinSpanSendingQueue);
        verify(schedulerMock).scheduleAtFixedRate(impl.senderJob,
                                                  ZipkinSpanSenderDefaultHttpImpl.DEFAULT_SPAN_BATCH_SENDING_PERIOD_MILLIS,
                                                  ZipkinSpanSenderDefaultHttpImpl.DEFAULT_SPAN_BATCH_SENDING_PERIOD_MILLIS,
                                                  TimeUnit.MILLISECONDS);
    }

    @Test
    public void constructor_throws_wrapped_MalformedURLException_if_url_is_malformed() {
        // given
        final String badUrl = "iamnotaurl!@#%^*&&*";

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                new ZipkinSpanSenderDefaultHttpImpl(badUrl, true);
            }
        });

        // then
        assertThat(ex)
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(MalformedURLException.class);
    }

    @Test
    public void handleSpan_offers_span_to_zipkinSpanSendingQueue() {
        // given
        zipkin2.Span zipkinSpan = zipkinSpan(42, "foo");
        assertThat(implSpy.zipkinSpanSendingQueue.isEmpty());

        // when
        implSpy.handleSpan(zipkinSpan);

        // then
        assertThat(implSpy.zipkinSpanSendingQueue)
            .hasSize(1)
            .contains(zipkinSpan);
    }

    @Test
    public void flush_kicks_off_sender_job_immediately() {
        // when
        implSpy.flush();

        // then
        verify(schedulerMock).execute(implSpy.senderJob);
    }

    @Test
    public void sendSpans_with_span_list_delegates_to_sendSpans_with_byte_array() throws IOException {
        // given
        List<zipkin2.Span> zipkinSpans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            zipkinSpans.add(zipkinSpan(random.nextLong(), UUID.randomUUID().toString()));
        }
        byte[] expectedBytesPayload = SpanBytesEncoder.JSON_V2.encodeList(zipkinSpans);

        // when
        implSpy.sendSpans(zipkinSpans);

        // then
        verify(implSpy).sendSpans(expectedBytesPayload);
    }

    @Test
    public void sendSpans_with_span_list_does_not_propagate_IOException_error_thrown_by_sendSpans_with_byte_array() throws IOException {
        // given
        doThrow(new IOException("kaboom")).when(implSpy).sendSpans(any(byte[].class));

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                implSpy.sendSpans(Collections.singletonList(zipkinSpan(42, "foo")));
            }
        });

        // then
        verify(implSpy).sendSpans(any(byte[].class));
        assertThat(ex).isNull();
    }

    @Test
    public void sendSpans_with_span_list_propagates_RuntimeExceptions_thrown_by_sendSpans_with_byte_array() throws IOException {
        // given
        RuntimeException runtimeException = new RuntimeException("kaboom");
        doThrow(runtimeException).when(implSpy).sendSpans(any(byte[].class));

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                implSpy.sendSpans(Collections.singletonList(zipkinSpan(42, "foo")));
            }
        });

        // then
        verify(implSpy).sendSpans(any(byte[].class));
        assertThat(ex).isSameAs(runtimeException);
    }

    @Test
    public void sendSpans_sends_to_zipkin_server_as_expected() {
        // given
        List<zipkin2.Span> zipkinSpans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            zipkinSpans.add(zipkinSpan(random.nextLong(), UUID.randomUUID().toString()));
        }

        // when
        implSpy.sendSpans(zipkinSpans);

        // then
        assertThat(zipkinRule.httpRequestCount()).isEqualTo(1);
        // Each span has its own trace ID
        List<zipkin2.Span>[] expectedTraces = new List[zipkinSpans.size()];
        for (int i = 0; i < zipkinSpans.size(); i++) {
            zipkin2.Span span = zipkinSpans.get(i);
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

            Whitebox.setInternalState(implSpy, "compressZipkinSpanPayload", compressPayload);
            Whitebox.setInternalState(implSpy, "postZipkinSpansUrl", new URL(zipkinServer.url("/api/v1/spans").toString()));

            List<zipkin2.Span> sentSpans = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                sentSpans.add(zipkinSpan(random.nextLong(), UUID.randomUUID().toString()));
            }
            long expectedUncompressedPayloadSize = SpanBytesEncoder.JSON_V2.encodeList(sentSpans).length;

            // when
            implSpy.sendSpans(sentSpans);

            // then
            RecordedRequest zipkinServerReq = zipkinServer.takeRequest();
            long receivedByteCount = zipkinServerReq.getBodySize();
            if (compressPayload)
                assertThat(receivedByteCount).isLessThan(expectedUncompressedPayloadSize);
            else
                assertThat(receivedByteCount).isEqualTo(expectedUncompressedPayloadSize);

            byte[] receivedPayloadBytes = zipkinServerReq.getBody().readByteArray();
            byte[] deserializableBytes = (compressPayload) ? unGzip(receivedPayloadBytes) : receivedPayloadBytes;

            List<zipkin2.Span> receivedSpans = SpanBytesDecoder.JSON_V2.decodeList(deserializableBytes);
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
    public void ZipkinSpanSenderJob_drains_from_blocking_queue_and_calls_sendSpans_method() {
        // given
        ZipkinSpanSenderDefaultHttpImpl senderImplMock = mock(ZipkinSpanSenderDefaultHttpImpl.class);
        BlockingQueue<zipkin2.Span> spanBlockingQueueSpy = spy(new LinkedBlockingQueue<Span>());
        List<zipkin2.Span> zipkinSpans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            zipkinSpans.add(zipkinSpan(random.nextLong(), UUID.randomUUID().toString()));
        }
        spanBlockingQueueSpy.addAll(zipkinSpans);

        ZipkinSpanSenderDefaultHttpImpl.ZipkinSpanSenderJob senderJob =
            new ZipkinSpanSenderDefaultHttpImpl.ZipkinSpanSenderJob(senderImplMock, spanBlockingQueueSpy);

        // when
        senderJob.run();

        // then
        verify(spanBlockingQueueSpy).drainTo(any(Collection.class));
        verify(senderImplMock).sendSpans(zipkinSpans);
    }

    @Test
    public void ZipkinSpanSenderJob_does_nothing_if_blocking_queue_is_empty() {
        // given
        ZipkinSpanSenderDefaultHttpImpl senderImplMock = mock(ZipkinSpanSenderDefaultHttpImpl.class);
        BlockingQueue<zipkin2.Span> emptySpanBlockingQueueSpy = spy(new LinkedBlockingQueue<zipkin2.Span>());

        ZipkinSpanSenderDefaultHttpImpl.ZipkinSpanSenderJob senderJob =
            new ZipkinSpanSenderDefaultHttpImpl.ZipkinSpanSenderJob(senderImplMock, emptySpanBlockingQueueSpy);

        // when
        senderJob.run();

        // then
        verify(emptySpanBlockingQueueSpy).isEmpty();
        verify(emptySpanBlockingQueueSpy, never()).drainTo(any(Collection.class));
        verifyZeroInteractions(senderImplMock);
    }

    @Test
    public void ZipkinSpanSenderJob_does_nothing_if_blocking_queue_isEmpty_method_returns_false_but_queue_empties_before_draining() {
        // given
        ZipkinSpanSenderDefaultHttpImpl senderImplMock = mock(ZipkinSpanSenderDefaultHttpImpl.class);
        BlockingQueue<zipkin2.Span> spanBlockingQueueMock = mock(BlockingQueue.class);
        doReturn(false).when(spanBlockingQueueMock).isEmpty();
        doReturn(0).when(spanBlockingQueueMock).drainTo(any(Collection.class));

        ZipkinSpanSenderDefaultHttpImpl.ZipkinSpanSenderJob senderJob =
            new ZipkinSpanSenderDefaultHttpImpl.ZipkinSpanSenderJob(senderImplMock, spanBlockingQueueMock);

        // when
        senderJob.run();

        // then
        verify(spanBlockingQueueMock).isEmpty();
        verify(spanBlockingQueueMock).drainTo(any(Collection.class));
        verifyZeroInteractions(senderImplMock);
    }

    @Test
    public void ZipkinSpanSenderJob_does_not_propagate_any_errors() {
        // given
        ZipkinSpanSenderDefaultHttpImpl senderImplMock = mock(ZipkinSpanSenderDefaultHttpImpl.class);
        BlockingQueue<zipkin2.Span> spanBlockingQueueMock = mock(BlockingQueue.class);
        doThrow(new RuntimeException("kaboom")).when(spanBlockingQueueMock).isEmpty();

        final ZipkinSpanSenderDefaultHttpImpl.ZipkinSpanSenderJob senderJob =
            new ZipkinSpanSenderDefaultHttpImpl.ZipkinSpanSenderJob(senderImplMock, spanBlockingQueueMock);

        // when
        Throwable propagatedEx = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                senderJob.run();
            }
        });

        // then
        verify(spanBlockingQueueMock).isEmpty();
        assertThat(propagatedEx).isNull();

        verify(spanBlockingQueueMock, never()).drainTo(any(Collection.class));
        verifyZeroInteractions(senderImplMock);
    }

    @Test
    public void sendSpans_does_not_propagate_5xx_errors() throws Exception {
        // given
        zipkinRule.enqueueFailure(HttpFailure.sendErrorResponse(500, "Server Error!"));

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                implSpy.sendSpans(Collections.singletonList(zipkinSpan(42, "foo")));
            }
        });

        // then
        assertThat(ex).isNull();
    }


    @Test
    public void sendSpans_does_not_propagate_connection_errors() throws Exception {
        // given
        zipkinRule.enqueueFailure(HttpFailure.disconnectDuringBody());

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                implSpy.sendSpans(Collections.singletonList(zipkinSpan(42, "foo")));
            }
        });

        // then
        assertThat(ex).isNull();
    }

    static zipkin2.Span zipkinSpan(long traceIdLong, String spanName) {
        String traceId = Long.toHexString(traceIdLong);
        return zipkin2.Span.newBuilder().traceId(traceId).id(traceId).name(spanName).build();
    }

}