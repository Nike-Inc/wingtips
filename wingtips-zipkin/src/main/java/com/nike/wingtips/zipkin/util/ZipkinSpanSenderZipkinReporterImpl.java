package com.nike.wingtips.zipkin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import zipkin.reporter.Reporter;

/**
 * A simple implementation of {@link ZipkinSpanSender} that collects spans into batches and sends them to a Zipkin server at a regular intervals
 * using a Zipkin {@link Reporter} delegate.
 *
 * @author Nic Munroe
 */
public class ZipkinSpanSenderZipkinReporterImpl implements ZipkinSpanSender {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final int DEFAULT_SPAN_BATCH_SENDING_PERIOD_MILLIS = 1000;

    protected final ZipkinSpanSenderJob senderJob;

    protected final BlockingQueue<zipkin.Span> zipkinSpanSendingQueue = new LinkedBlockingQueue<>();
    protected final Reporter zipkinReporter;
    protected final ScheduledExecutorService zipkinSpanSendingScheduler;

    /**
     * @param zipkinReporter The Zipkin {@link Reporter} delegate that you want to use for sending the spans to a Zipkin server.
     * @param batchSendingPeriodMillis The period in milliseconds that should be used between sending span batches to the Zipkin server. If you pass in
     *                                 0 it will disable automatic batch sending, at which point {@link #flush()} is the only
     *                                 way to send spans. <b>IMPORTANT NOTE:</b> The queue that stores spans is unbounded, so make sure you
     *                                 select a period that is short enough to keep the queue from getting too big and taking up too much memory on
     *                                 your server.
     */
    public ZipkinSpanSenderZipkinReporterImpl(Reporter zipkinReporter, int batchSendingPeriodMillis) {
        this.zipkinReporter = zipkinReporter;

        this.senderJob = new ZipkinSpanSenderJob(this, zipkinSpanSendingQueue);

        this.zipkinSpanSendingScheduler = configureScheduledExecutorServiceForSchedulingBatchSends();

        if (batchSendingPeriodMillis > 0) {
            zipkinSpanSendingScheduler.scheduleAtFixedRate(senderJob, batchSendingPeriodMillis,
                                                           batchSendingPeriodMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void handleSpan(zipkin.Span span) {
        zipkinSpanSendingQueue.offer(span);
    }

    @Override
    public void flush() {
        zipkinSpanSendingScheduler.execute(senderJob);
    }

    protected ScheduledExecutorService configureScheduledExecutorServiceForSchedulingBatchSends() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(r, "zipkin-span-sending-job-scheduler");
            }
        });
    }

    protected void sendSpans(final List<zipkin.Span> spanList) {
        sendSpans(spanList, new Reporter.Callback() {
            @Override
            public void onComplete() {
                // Do nothing
            }

            @Override
            public void onError(Throwable t) {
                logger.error("An error occurred attempting to post Zipkin spans to the Zipkin server. affected_trace_ids=\"{}\", exception_cause=\"{}\"",
                             extractTraceIds(spanList), t.toString());
            }
        });
    }

    protected void sendSpans(final List<zipkin.Span> spanList, Reporter.Callback callback) {
        try {
            zipkinReporter.report(spanList, callback);
        } catch (Throwable t) {
            logger.error("An error occurred attempting to post Zipkin spans to the Zipkin server. affected_trace_ids=\"{}\", exception_cause=\"{}\"",
                         extractTraceIds(spanList), t.toString());
        }
    }

    protected Set<String> extractTraceIds(List<zipkin.Span> spanList) {
        Set<String> traceIds = new HashSet<>(spanList.size());
        for (zipkin.Span span : spanList) {
            traceIds.add(String.valueOf(span.traceId));
        }
        return traceIds;
    }

    protected static class ZipkinSpanSenderJob implements Runnable {
        private final Logger logger = LoggerFactory.getLogger(this.getClass());

        protected final ZipkinSpanSenderZipkinReporterImpl zipkinSpanSender;
        protected final BlockingQueue<zipkin.Span> zipkinSpanSendingQueue;

        public ZipkinSpanSenderJob(ZipkinSpanSenderZipkinReporterImpl zipkinSpanSender, BlockingQueue<zipkin.Span> zipkinSpanSendingQueue) {
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
