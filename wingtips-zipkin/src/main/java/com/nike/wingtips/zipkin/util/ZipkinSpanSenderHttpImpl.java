package com.nike.wingtips.zipkin.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import zipkin.reporter.urlconnection.URLConnectionReporter;

/**
 * An implementation of {@link ZipkinSpanSenderZipkinReporterImpl} that uses {@link URLConnectionReporter} as the underlying Zipkin reporter.
 *
 * @author Nic Munroe
 */
public class ZipkinSpanSenderHttpImpl extends ZipkinSpanSenderZipkinReporterImpl {

    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;

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
    public ZipkinSpanSenderHttpImpl(String postZipkinSpansBaseUrl, boolean compressZipkinSpanPayload, int connectTimeoutMillis, int readTimeoutMillis,
                                    int batchSendingPeriodMillis) {
        super(generateURLConnectionReporter(postZipkinSpansBaseUrl, compressZipkinSpanPayload, connectTimeoutMillis, readTimeoutMillis), batchSendingPeriodMillis);
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
    public ZipkinSpanSenderHttpImpl(String postZipkinSpansBaseUrl, boolean compressZipkinSpanPayload) {
        this(postZipkinSpansBaseUrl, compressZipkinSpanPayload, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS,
             DEFAULT_SPAN_BATCH_SENDING_PERIOD_MILLIS);
    }

    protected static URLConnectionReporter generateURLConnectionReporter(
        String postZipkinSpansBaseUrl, boolean compressZipkinSpanPayload, int connectTimeoutMillis, int readTimeoutMillis
    ) {
        URL postZipkinSpansFinalUrl;
        try {
            String urlString = postZipkinSpansBaseUrl + (postZipkinSpansBaseUrl.endsWith("/") ? "" : "/") + "api/v1/spans";
            postZipkinSpansFinalUrl = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        return URLConnectionReporter.builder()
                                    .endpoint(postZipkinSpansFinalUrl)
                                    .compressionEnabled(compressZipkinSpanPayload)
                                    .connectTimeout(connectTimeoutMillis)
                                    .readTimeout(readTimeoutMillis)
                                    .executor(configureExecutorServiceForSendingSpans())
                                    .build();
    }

    protected static ExecutorService configureExecutorServiceForSendingSpans() {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(r, "zipkin-span-sender");
            }
        });
    }
}
