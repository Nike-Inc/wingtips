package com.nike.wingtips.zipkin.util;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.assertj.core.api.ThrowableAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import zipkin.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link ZipkinSpanSenderZipkinReporterImpl}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class ZipkinSpanSenderZipkinReporterImplTest {

    private final Random random = new Random(System.nanoTime());

    private ZipkinSpanSenderZipkinReporterImpl implSpy;
    private Reporter zipkinReporterMock;
    private int batchSendingPeriodMillis;
    private ScheduledExecutorService schedulerMock;
    private Throwable errorForCallback;

    private static class ZipkinSpanSenderZipkinReporterImplForTesting extends ZipkinSpanSenderZipkinReporterImpl {
        public ScheduledExecutorService schedulerMock;

        public ZipkinSpanSenderZipkinReporterImplForTesting(Reporter reporterToUse, int batchSendingPeriodMillis) {
            super(reporterToUse, batchSendingPeriodMillis);
        }

        @Override
        protected ScheduledExecutorService configureScheduledExecutorServiceForSchedulingBatchSends() {
            if (schedulerMock == null)
                schedulerMock = mock(ScheduledExecutorService.class);
            return schedulerMock;
        }
    }

    @Before
    public void beforeMethod() {
        zipkinReporterMock = mock(Reporter.class);
        batchSendingPeriodMillis = 4242;
        implSpy = spy(new ZipkinSpanSenderZipkinReporterImplForTesting(zipkinReporterMock, batchSendingPeriodMillis));
        schedulerMock = ((ZipkinSpanSenderZipkinReporterImplForTesting)implSpy).schedulerMock;

        errorForCallback = null;

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Reporter.Callback callback = (Reporter.Callback)invocation.getArguments()[1];

                if (errorForCallback == null)
                    callback.onComplete();
                else
                    callback.onError(errorForCallback);

                return null;
            }
        }).when(zipkinReporterMock).report(anyList(), any(Reporter.Callback.class));
    }

    @DataProvider(value = {
        "1042",
        "0",
    }, splitBy = "\\|")
    @Test
    public void constructor_sets_fields_and_kicks_off_scheduled_job_as_expected(int batchSendingPeriodMillis) throws MalformedURLException {
        // given
        int connectTimeoutMillis = Math.abs(random.nextInt());
        int readTimeoutMillis = Math.abs(random.nextInt());
        final ScheduledExecutorService schedulerMock = mock(ScheduledExecutorService.class);

        // when
        ZipkinSpanSenderZipkinReporterImpl impl = new ZipkinSpanSenderZipkinReporterImpl(zipkinReporterMock, batchSendingPeriodMillis) {
            @Override
            protected ScheduledExecutorService configureScheduledExecutorServiceForSchedulingBatchSends() {
                return schedulerMock;
            }
        };

        // then
        assertThat(impl.zipkinReporter).isSameAs(zipkinReporterMock);
        assertThat(impl.senderJob).isNotNull();
        assertThat(impl.zipkinSpanSendingScheduler).isSameAs(schedulerMock);

        assertThat(impl.senderJob.zipkinSpanSender).isSameAs(impl);
        assertThat(impl.senderJob.zipkinSpanSendingQueue).isSameAs(impl.zipkinSpanSendingQueue);

        if (batchSendingPeriodMillis > 0) {
            verify(schedulerMock).scheduleAtFixedRate(impl.senderJob, batchSendingPeriodMillis, batchSendingPeriodMillis, TimeUnit.MILLISECONDS);
        }
        else {
            verifyZeroInteractions(schedulerMock);
        }
    }

    @Test
    public void handleSpan_offers_span_to_zipkinSpanSendingQueue() {
        // given
        zipkin.Span zipkinSpan = zipkinSpan(42, "foo");
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
    public void sendSpans_delegates_to_zipkinReporter() throws IOException {
        // given
        List<zipkin.Span> spanList = Collections.singletonList(zipkinSpan(42, "foo"));

        // when
        implSpy.sendSpans(spanList);

        // then
        verify(zipkinReporterMock).report(eq(spanList), any(Reporter.Callback.class));
    }

    @Test
    public void sendSpans_with_span_list_does_not_propagate_error_thrown_by_zipkinReporter() throws IOException {
        // given
        RuntimeException exceptionFromReporter = new RuntimeException("kaboom");
        doThrow(exceptionFromReporter).when(zipkinReporterMock).report(anyList(), any(Reporter.Callback.class));
        Logger loggerMock = mock(Logger.class);
        Whitebox.setInternalState(implSpy, "logger", loggerMock);

        // when
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                implSpy.sendSpans(Collections.singletonList(zipkinSpan(42, "foo")));
            }
        });

        // then
        verify(zipkinReporterMock).report(anyList(), any(Reporter.Callback.class));
        assertThat(ex).isNull();
        verify(loggerMock).error(anyString(), any(), eq(exceptionFromReporter.toString()));
    }

    @Test
    public void callback_onError_logs_error() throws IOException {
        // given
        errorForCallback = new RuntimeException("kaboom");
        Logger loggerMock = mock(Logger.class);
        Whitebox.setInternalState(implSpy, "logger", loggerMock);

        // when
        implSpy.sendSpans(Collections.singletonList(zipkinSpan(42, "foo")));

        // then
        verify(zipkinReporterMock).report(anyList(), any(Reporter.Callback.class));
        verify(loggerMock).error(anyString(), any(), eq(errorForCallback.toString()));
    }

    @Test
    public void ZipkinSpanSenderJob_drains_from_blocking_queue_and_calls_sendSpans_method() {
        // given
        ZipkinSpanSenderZipkinReporterImpl senderImplMock = mock(ZipkinSpanSenderZipkinReporterImpl.class);
        BlockingQueue<zipkin.Span> spanBlockingQueueSpy = spy(new LinkedBlockingQueue<zipkin.Span>());
        List<zipkin.Span> zipkinSpans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            zipkinSpans.add(zipkinSpan(random.nextLong(), UUID.randomUUID().toString()));
        }
        spanBlockingQueueSpy.addAll(zipkinSpans);

        ZipkinSpanSenderZipkinReporterImpl.ZipkinSpanSenderJob senderJob =
            new ZipkinSpanSenderZipkinReporterImpl.ZipkinSpanSenderJob(senderImplMock, spanBlockingQueueSpy);

        // when
        senderJob.run();

        // then
        verify(spanBlockingQueueSpy).drainTo(any(Collection.class));
        verify(senderImplMock).sendSpans(zipkinSpans);
    }

    @Test
    public void ZipkinSpanSenderJob_does_nothing_if_blocking_queue_is_empty() {
        // given
        ZipkinSpanSenderZipkinReporterImpl senderImplMock = mock(ZipkinSpanSenderZipkinReporterImpl.class);
        BlockingQueue<zipkin.Span> emptySpanBlockingQueueSpy = spy(new LinkedBlockingQueue<zipkin.Span>());

        ZipkinSpanSenderZipkinReporterImpl.ZipkinSpanSenderJob senderJob =
            new ZipkinSpanSenderZipkinReporterImpl.ZipkinSpanSenderJob(senderImplMock, emptySpanBlockingQueueSpy);

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
        ZipkinSpanSenderZipkinReporterImpl senderImplMock = mock(ZipkinSpanSenderZipkinReporterImpl.class);
        BlockingQueue<zipkin.Span> spanBlockingQueueMock = mock(BlockingQueue.class);
        doReturn(false).when(spanBlockingQueueMock).isEmpty();
        doReturn(0).when(spanBlockingQueueMock).drainTo(any(Collection.class));

        ZipkinSpanSenderZipkinReporterImpl.ZipkinSpanSenderJob senderJob =
            new ZipkinSpanSenderZipkinReporterImpl.ZipkinSpanSenderJob(senderImplMock, spanBlockingQueueMock);

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
        ZipkinSpanSenderZipkinReporterImpl senderImplMock = mock(ZipkinSpanSenderZipkinReporterImpl.class);
        BlockingQueue<zipkin.Span> spanBlockingQueueMock = mock(BlockingQueue.class);
        doThrow(new RuntimeException("kaboom")).when(spanBlockingQueueMock).isEmpty();

        final ZipkinSpanSenderZipkinReporterImpl.ZipkinSpanSenderJob senderJob =
            new ZipkinSpanSenderZipkinReporterImpl.ZipkinSpanSenderJob(senderImplMock, spanBlockingQueueMock);

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
    public void configureScheduledExecutorServiceForSchedulingBatchSends_returns_ScheduledExecutorService_with_named_thread()
        throws ExecutionException, InterruptedException {

        // given
        ScheduledExecutorService scheduler = new ZipkinSpanSenderZipkinReporterImpl(zipkinReporterMock, 0)
            .configureScheduledExecutorServiceForSchedulingBatchSends();
        final List<String> threadNameHolder = new ArrayList<>();

        // when
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                threadNameHolder.add(Thread.currentThread().getName());
            }
        }, 0, TimeUnit.MILLISECONDS).get();

        // then
        assertThat(threadNameHolder).containsExactly("zipkin-span-sending-job-scheduler");
        scheduler.shutdown();
    }

    static zipkin.Span zipkinSpan(long traceId, String spanName) {
        return zipkin.Span.builder().traceId(traceId).id(traceId).name(spanName).build();
    }

}