package com.nike.wingtips.zipkin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import zipkin.Codec;

/**
 * A default no-dependencies implementation of {@link ZipkinSpanSender} that collects spans into batches and sends them to the Zipkin server
 * at a regular intervals over HTTP.
 *
 * @author Nic Munroe
 */
public class ZipkinSpanSenderDefaultHttpImpl implements ZipkinSpanSender {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final int DEFAULT_SPAN_BATCH_SENDING_PERIOD_MILLIS = 1000;
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;

    protected final URL postZipkinSpansUrl;
    protected final boolean compressZipkinSpanPayload;
    protected final int connectTimeoutMillis;
    protected final int readTimeoutMillis;
    protected final ZipkinSpanSenderJob senderJob;

    protected final BlockingQueue<zipkin.Span> zipkinSpanSendingQueue = new LinkedBlockingQueue<>();
    protected final ScheduledExecutorService zipkinSpanSendingScheduler;

    /**
     * Kitchen-sink constructor that creates a new instance allowing you to specify all the given configuration options.
     *
     * @param postZipkinSpansBaseUrl The base URL of the Zipkin server. This should include the scheme, host, and port (if non-standard for the scheme).
     *                               e.g. {@code http://localhost:9411}, or {@code https://zipkinserver.doesnotexist.com/}
     * @param compressZipkinSpanPayload Pass in true if the payload sent to the Zipkin server should be gzipped, false to pass the payload uncompressed.
     * @param connectTimeoutMillis The timeout in milliseconds that should be used when attempting to connect to the Zipkin server.
     * @param readTimeoutMillis The read timeout in milliseconds that should be used when waiting for a response from the Zipkin server.
     * @param batchSendingPeriodMillis The period in milliseconds that should be used between sending span batches to the Zipkin server. If you pass in
     *                                 0 it will disable automatic batch sending, at which point {@link #flush()} is the only
     *                                 way to send spans. <b>IMPORTANT NOTE:</b> The queue that stores spans is unbounded, so make sure you
     *                                 select a period that is short enough to keep the queue from getting too big and taking up too much memory on
     *                                 your server.
     */
    public ZipkinSpanSenderDefaultHttpImpl(String postZipkinSpansBaseUrl, boolean compressZipkinSpanPayload, int connectTimeoutMillis, int readTimeoutMillis,
                                           int batchSendingPeriodMillis) {
        try {
            String urlString = postZipkinSpansBaseUrl + (postZipkinSpansBaseUrl.endsWith("/") ? "" : "/") + "api/v1/spans";
            this.postZipkinSpansUrl = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        this.compressZipkinSpanPayload = compressZipkinSpanPayload;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.senderJob = new ZipkinSpanSenderJob(this, zipkinSpanSendingQueue);

        this.zipkinSpanSendingScheduler = configureScheduledExecutorServiceForBatching();

        if (batchSendingPeriodMillis > 0) {
            zipkinSpanSendingScheduler.scheduleAtFixedRate(senderJob, batchSendingPeriodMillis,
                                                           batchSendingPeriodMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Convenience constructor that calls the kitchen-sink constructor passing in {@link #DEFAULT_CONNECT_TIMEOUT_MILLIS},
     * {@link #DEFAULT_READ_TIMEOUT_MILLIS}, and {@link #DEFAULT_SPAN_BATCH_SENDING_PERIOD_MILLIS} for the default connect timeout,
     * read timeout, and span batching period respectively.
     *
     * @param postZipkinSpansBaseUrl The base URL of the Zipkin server. This should include the scheme, host, and port (if non-standard for the scheme).
     *                               e.g. {@code http://localhost:9411}, or {@code https://zipkinserver.doesnotexist.com/}
     * @param compressZipkinSpanPayload Pass in true if the payload sent to the Zipkin server should be gzipped, false to pass the payload uncompressed.
     */
    public ZipkinSpanSenderDefaultHttpImpl(String postZipkinSpansBaseUrl, boolean compressZipkinSpanPayload) {
        this(postZipkinSpansBaseUrl, compressZipkinSpanPayload, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS,
             DEFAULT_SPAN_BATCH_SENDING_PERIOD_MILLIS);
    }

    @Override
    public void handleSpan(zipkin.Span span) {
        zipkinSpanSendingQueue.offer(span);
    }

    @Override
    public void flush() {
        zipkinSpanSendingScheduler.execute(senderJob);
    }

    protected ScheduledExecutorService configureScheduledExecutorServiceForBatching() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(r, "zipkin-span-sender");
            }
        });
    }

    protected void sendSpans(List<zipkin.Span> spanList) {
        try {
            sendSpans(Codec.JSON.writeSpans(spanList));
        } catch (IOException e) {
            Set<String> affectedTraceIds = new HashSet<>(spanList.size());
            for (zipkin.Span span : spanList) {
                affectedTraceIds.add(String.valueOf(span.traceId));
            }
            logger.error("An error occurred attempting to post Zipkin spans to the Zipkin server. affected_trace_ids={}, exception_cause=\"{}\"",
                         affectedTraceIds.toString(), e.toString());
        }
    }

    /**
     * <p>
     *     This method uses basic JDK classes to POST the given payload bytes (representing a list of Zipkin Spans that have been serialized to JSON)
     *     to the Zipkin server endpoint at {@link #postZipkinSpansUrl}. This gives a simple workable implementation that requires no outside dependencies,
     *     however it may lack some of the flexibility your project requires (e.g. around connection pooling). You can extend this class and override
     *     this method to use a different HTTP client for sending spans to Zipkin if desired.
     * </p>
     * <p>
     *     This code was derived from the Zipkin/Brave repository's brave-spancollector-http module v3.9.1
     *     (https://github.com/openzipkin/brave/blob/master/brave-spancollector-http/src/main/java/com/github/kristofa/brave/http/HttpSpanCollector.java)
     *     and licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
     * </p>
     */
    protected void sendSpans(byte[] spanListJsonPayloadBytes) throws IOException {
        logger.trace("Sending spans to zipkin");

        // intentionally not closing the connection, so as to use keep-alives
        HttpURLConnection connection = (HttpURLConnection) postZipkinSpansUrl.openConnection();
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Content-Type", "application/json");
        if (compressZipkinSpanPayload) {
            connection.addRequestProperty("Content-Encoding", "gzip");
            ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
            try (GZIPOutputStream compressor = new GZIPOutputStream(gzipped)) {
                compressor.write(spanListJsonPayloadBytes);
            }
            spanListJsonPayloadBytes = gzipped.toByteArray();
        }
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(spanListJsonPayloadBytes.length);
        connection.getOutputStream().write(spanListJsonPayloadBytes);

        try (InputStream in = connection.getInputStream()) {
            while (in.read() != -1) ; // skip
        } catch (IOException e) {
            try (InputStream err = connection.getErrorStream()) {
                if (err != null) { // possible, if the connection was dropped
                    while (err.read() != -1) ; // skip
                }
            }
            throw e;
        }
    }

    protected static class ZipkinSpanSenderJob implements Runnable {
        private final Logger logger = LoggerFactory.getLogger(this.getClass());

        protected final ZipkinSpanSenderDefaultHttpImpl zipkinSpanSender;
        protected final BlockingQueue<zipkin.Span> zipkinSpanSendingQueue;

        public ZipkinSpanSenderJob(ZipkinSpanSenderDefaultHttpImpl zipkinSpanSender, BlockingQueue<zipkin.Span> zipkinSpanSendingQueue) {
            this.zipkinSpanSender = zipkinSpanSender;
            this.zipkinSpanSendingQueue = zipkinSpanSendingQueue;
        }

        @Override
        public void run() {
            try {
                if (zipkinSpanSendingQueue.isEmpty())
                    return;

                List<zipkin.Span> drainedSpans = new ArrayList<>(zipkinSpanSendingQueue.size());
                zipkinSpanSendingQueue.drainTo(drainedSpans);
                if (!drainedSpans.isEmpty())
                    zipkinSpanSender.sendSpans(drainedSpans);
            }
            catch(Throwable ex) {
                logger.error("An unexpected error occurred attempting to post Zipkin spans to the Zipkin server.", ex);
            }
        }
    }
}
