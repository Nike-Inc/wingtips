package com.nike.wingtips.util;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.Tracer.SpanFieldForLoggerMdc;
import com.nike.wingtips.testutil.TestUtils;
import com.nike.wingtips.testutil.TestUtils.SpanRecorder;
import com.nike.wingtips.testutil.Whitebox;
import com.nike.wingtips.util.AsyncWingtipsHelper.AsyncWingtipsHelperDefaultImpl;
import com.nike.wingtips.util.AsyncWingtipsHelper.CheckedRunnable;
import com.nike.wingtips.util.asynchelperwrapper.BiConsumerWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.BiFunctionWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.BiPredicateWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.CallableWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.ConsumerWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.ExecutorServiceWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.FunctionWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.PredicateWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.RunnableWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.ScheduledExecutorServiceWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.SupplierWithTracing;
import com.nike.wingtips.util.operationwrapper.OperationWrapperOptions;
import com.nike.wingtips.util.spantagger.ErrorSpanTagger;
import com.nike.wingtips.util.spantagger.SpanTagger;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.nike.wingtips.testutil.TestUtils.sleepThread;
import static com.nike.wingtips.util.AsyncWingtipsHelper.DEFAULT_IMPL;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.biConsumerWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.biFunctionWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.biPredicateWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.callableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.consumerWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.executorServiceWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.functionWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.predicateWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.scheduledExecutorServiceWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.supplierWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the functionality of {@link AsyncWingtipsHelper}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class AsyncWingtipsHelperTest {

    private Runnable runnableMock;
    private Callable callableMock;
    private Supplier supplierMock;
    private Function functionMock;
    private BiFunction biFunctionMock;
    private Consumer consumerMock;
    private BiConsumer biConsumerMock;
    private Predicate predicateMock;
    private BiPredicate biPredicateMock;
    private ExecutorService executorServiceMock;
    private ScheduledExecutorService scheduledExecutorServiceMock;

    private static SpanRecorder spanRecorder;
    
    @Before
    public void beforeMethod() {
        runnableMock = mock(Runnable.class);
        callableMock = mock(Callable.class);
        supplierMock = mock(Supplier.class);
        functionMock = mock(Function.class);
        biFunctionMock = mock(BiFunction.class);
        consumerMock = mock(Consumer.class);
        biConsumerMock = mock(BiConsumer.class);
        predicateMock = mock(Predicate.class);
        biPredicateMock = mock(BiPredicate.class);
        executorServiceMock = mock(ExecutorService.class);
        scheduledExecutorServiceMock = mock(ScheduledExecutorService.class);

        resetTracing();
    }

    private static void resetTracing() {
        TestUtils.resetTracing();
        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private static TracingState generateTracingInfo() {
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("someSpan");
        TracingState result = new TracingState(
            Tracer.getInstance().getCurrentSpanStackCopy(), new HashMap<>(MDC.getCopyOfContextMap())
        );
        resetTracing();
        return result;
    }

    private static TracingState setupCurrentThreadWithTracingInfo() {
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("request-" + UUID.randomUUID().toString());
        return new TracingState(Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
    }

    @Test
    public void code_coverage_hoops() {
        // jump!
        new AsyncWingtipsHelperStatic();
    }

    private void verifyRunnableWithTracing(Runnable result, Runnable expectedCoreRunnable,
                                                        Deque<Span> expectedSpanStack,
                                                        Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(RunnableWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origRunnable")).isSameAs(expectedCoreRunnable);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void runnableWithTracing_using_current_thread_info_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        Runnable result = (useStaticMethod)
                          ? runnableWithTracing(runnableMock)
                          : DEFAULT_IMPL.runnableWithTracing(runnableMock);

        // then
        verifyRunnableWithTracing(result, runnableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void runnableWithTracing_pair_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Runnable result = (useStaticMethod)
                          ? runnableWithTracing(runnableMock, setupInfo)
                          : DEFAULT_IMPL.runnableWithTracing(runnableMock, setupInfo);

        // then
        verifyRunnableWithTracing(result, runnableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void runnableWithTracing_separate_args_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Runnable result = (useStaticMethod)
                          ? runnableWithTracing(runnableMock, setupInfo.getLeft(), setupInfo.getRight())
                          : DEFAULT_IMPL.runnableWithTracing(runnableMock,
                                                                   setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyRunnableWithTracing(result, runnableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyCallableWithTracing(Callable result, Callable expectedCoreInstance,
                                                        Deque<Span> expectedSpanStack,
                                                        Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(CallableWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origCallable")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void callableWithTracing_using_current_thread_info_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        Callable result = (useStaticMethod)
                          ? callableWithTracing(callableMock)
                          : DEFAULT_IMPL.callableWithTracing(callableMock);

        // then
        verifyCallableWithTracing(result, callableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void callableWithTracing_pair_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Callable result = (useStaticMethod)
                          ? callableWithTracing(callableMock, setupInfo)
                          : DEFAULT_IMPL.callableWithTracing(callableMock, setupInfo);

        // then
        verifyCallableWithTracing(result, callableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void callableWithTracing_separate_args_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Callable result = (useStaticMethod)
                          ? callableWithTracing(callableMock, setupInfo.getLeft(), setupInfo.getRight())
                          : DEFAULT_IMPL.callableWithTracing(callableMock,
                                                                   setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyCallableWithTracing(result, callableMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifySupplierWithTracing(Supplier result, Supplier expectedCoreInstance,
                                                        Deque<Span> expectedSpanStack,
                                                        Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(SupplierWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origSupplier")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void supplierWithTracing_using_current_thread_info_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        Supplier result = (useStaticMethod)
                          ? supplierWithTracing(supplierMock)
                          : DEFAULT_IMPL.supplierWithTracing(supplierMock);

        // then
        verifySupplierWithTracing(result, supplierMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void supplierWithTracing_pair_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Supplier result = (useStaticMethod)
                          ? supplierWithTracing(supplierMock, setupInfo)
                          : DEFAULT_IMPL.supplierWithTracing(supplierMock, setupInfo);

        // then
        verifySupplierWithTracing(result, supplierMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void supplierWithTracing_separate_args_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Supplier result = (useStaticMethod)
                          ? supplierWithTracing(supplierMock, setupInfo.getLeft(), setupInfo.getRight())
                          : DEFAULT_IMPL.supplierWithTracing(supplierMock,
                                                                   setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifySupplierWithTracing(result, supplierMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyFunctionWithTracing(Function result, Function expectedCoreInstance,
                                                        Deque<Span> expectedSpanStack,
                                                        Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(FunctionWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origFunction")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void functionWithTracing_using_current_thread_info_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        Function result = (useStaticMethod)
                          ? functionWithTracing(functionMock)
                          : DEFAULT_IMPL.functionWithTracing(functionMock);

        // then
        verifyFunctionWithTracing(result, functionMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void functionWithTracing_pair_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Function result = (useStaticMethod)
                          ? functionWithTracing(functionMock, setupInfo)
                          : DEFAULT_IMPL.functionWithTracing(functionMock, setupInfo);

        // then
        verifyFunctionWithTracing(result, functionMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void functionWithTracing_separate_args_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Function result = (useStaticMethod)
                          ? functionWithTracing(functionMock, setupInfo.getLeft(), setupInfo.getRight())
                          : DEFAULT_IMPL.functionWithTracing(functionMock,
                                                                   setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyFunctionWithTracing(result, functionMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyBiFunctionWithTracing(BiFunction result, BiFunction expectedCoreInstance,
                                                          Deque<Span> expectedSpanStack,
                                                          Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(BiFunctionWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origBiFunction")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void biFunctionWithTracing_using_current_thread_info_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        BiFunction result = (useStaticMethod)
                            ? biFunctionWithTracing(biFunctionMock)
                            : DEFAULT_IMPL.biFunctionWithTracing(biFunctionMock);

        // then
        verifyBiFunctionWithTracing(result, biFunctionMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void biFunctionWithTracing_pair_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        BiFunction result = (useStaticMethod)
                            ? biFunctionWithTracing(biFunctionMock, setupInfo)
                            : DEFAULT_IMPL.biFunctionWithTracing(biFunctionMock, setupInfo);

        // then
        verifyBiFunctionWithTracing(result, biFunctionMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void biFunctionWithTracing_separate_args_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        BiFunction result = (useStaticMethod)
                            ? biFunctionWithTracing(biFunctionMock, setupInfo.getLeft(), setupInfo.getRight())
                            : DEFAULT_IMPL.biFunctionWithTracing(biFunctionMock,
                                                                       setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyBiFunctionWithTracing(result, biFunctionMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyConsumerWithTracing(Consumer result, Consumer expectedCoreInstance,
                                                        Deque<Span> expectedSpanStack,
                                                        Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(ConsumerWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origConsumer")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void consumerWithTracing_using_current_thread_info_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        Consumer result = (useStaticMethod)
                          ? consumerWithTracing(consumerMock)
                          : DEFAULT_IMPL.consumerWithTracing(consumerMock);

        // then
        verifyConsumerWithTracing(result, consumerMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void consumerWithTracing_pair_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Consumer result = (useStaticMethod)
                          ? consumerWithTracing(consumerMock, setupInfo)
                          : DEFAULT_IMPL.consumerWithTracing(consumerMock, setupInfo);

        // then
        verifyConsumerWithTracing(result, consumerMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void consumerWithTracing_separate_args_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Consumer result = (useStaticMethod)
                          ? consumerWithTracing(consumerMock, setupInfo.getLeft(), setupInfo.getRight())
                          : DEFAULT_IMPL.consumerWithTracing(consumerMock,
                                                                   setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyConsumerWithTracing(result, consumerMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyBiConsumerWithTracing(BiConsumer result, BiConsumer expectedCoreInstance,
                                                          Deque<Span> expectedSpanStack,
                                                          Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(BiConsumerWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origBiConsumer")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void biConsumerWithTracing_using_current_thread_info_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        BiConsumer result = (useStaticMethod)
                            ? biConsumerWithTracing(biConsumerMock)
                            : DEFAULT_IMPL.biConsumerWithTracing(biConsumerMock);

        // then
        verifyBiConsumerWithTracing(result, biConsumerMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void biConsumerWithTracing_pair_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        BiConsumer result = (useStaticMethod)
                            ? biConsumerWithTracing(biConsumerMock, setupInfo)
                            : DEFAULT_IMPL.biConsumerWithTracing(biConsumerMock, setupInfo);

        // then
        verifyBiConsumerWithTracing(result, biConsumerMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void biConsumerWithTracing_separate_args_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        BiConsumer result = (useStaticMethod)
                            ? biConsumerWithTracing(biConsumerMock, setupInfo.getLeft(), setupInfo.getRight())
                            : DEFAULT_IMPL.biConsumerWithTracing(biConsumerMock,
                                                                       setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyBiConsumerWithTracing(result, biConsumerMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyPredicateWithTracing(Predicate result, Predicate expectedCoreInstance,
                                                         Deque<Span> expectedSpanStack,
                                                         Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(PredicateWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origPredicate")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void predicateWithTracing_using_current_thread_info_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        Predicate result = (useStaticMethod)
                          ? predicateWithTracing(predicateMock)
                          : DEFAULT_IMPL.predicateWithTracing(predicateMock);

        // then
        verifyPredicateWithTracing(result, predicateMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void predicateWithTracing_pair_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Predicate result = (useStaticMethod)
                          ? predicateWithTracing(predicateMock, setupInfo)
                          : DEFAULT_IMPL.predicateWithTracing(predicateMock, setupInfo);

        // then
        verifyPredicateWithTracing(result, predicateMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void predicateWithTracing_separate_args_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        Predicate result = (useStaticMethod)
                          ? predicateWithTracing(predicateMock, setupInfo.getLeft(), setupInfo.getRight())
                          : DEFAULT_IMPL.predicateWithTracing(predicateMock,
                                                                   setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyPredicateWithTracing(result, predicateMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    private void verifyBiPredicateWithTracing(BiPredicate result, BiPredicate expectedCoreInstance,
                                                           Deque<Span> expectedSpanStack,
                                                           Map<String, String> expectedMdcInfo) {
        assertThat(result).isInstanceOf(BiPredicateWithTracing.class);
        assertThat(Whitebox.getInternalState(result, "origBiPredicate")).isSameAs(expectedCoreInstance);
        assertThat(Whitebox.getInternalState(result, "spanStackForExecution")).isEqualTo(expectedSpanStack);
        assertThat(Whitebox.getInternalState(result, "mdcContextMapForExecution")).isEqualTo(expectedMdcInfo);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void biPredicateWithTracing_using_current_thread_info_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = setupCurrentThreadWithTracingInfo();

        // when
        BiPredicate result = (useStaticMethod)
                            ? biPredicateWithTracing(biPredicateMock)
                            : DEFAULT_IMPL.biPredicateWithTracing(biPredicateMock);

        // then
        verifyBiPredicateWithTracing(result, biPredicateMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void biPredicateWithTracing_pair_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        BiPredicate result = (useStaticMethod)
                            ? biPredicateWithTracing(biPredicateMock, setupInfo)
                            : DEFAULT_IMPL.biPredicateWithTracing(biPredicateMock, setupInfo);

        // then
        verifyBiPredicateWithTracing(result, biPredicateMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void biPredicateWithTracing_separate_args_works_as_expected(boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> setupInfo = generateTracingInfo();

        // when
        BiPredicate result = (useStaticMethod)
                            ? biPredicateWithTracing(biPredicateMock, setupInfo.getLeft(), setupInfo.getRight())
                            : DEFAULT_IMPL.biPredicateWithTracing(biPredicateMock,
                                                                       setupInfo.getLeft(), setupInfo.getRight());

        // then
        verifyBiPredicateWithTracing(result, biPredicateMock, setupInfo.getLeft(), setupInfo.getRight());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void executorServiceWithTracing_works_as_expected(boolean useStaticMethod) {
        // when
        ExecutorServiceWithTracing result = (useStaticMethod)
                                            ? executorServiceWithTracing(executorServiceMock)
                                            : DEFAULT_IMPL.executorServiceWithTracing(executorServiceMock);

        // then
        assertThat(Whitebox.getInternalState(result, "delegate")).isSameAs(executorServiceMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void scheduledExecutorServiceWithTracing_works_as_expected(boolean useStaticMethod) {
        // when
        ScheduledExecutorServiceWithTracing result =
            (useStaticMethod)
            ? scheduledExecutorServiceWithTracing(scheduledExecutorServiceMock)
            : DEFAULT_IMPL.scheduledExecutorServiceWithTracing(scheduledExecutorServiceMock);

        // then
        assertThat(Whitebox.getInternalState(result, "delegate")).isSameAs(scheduledExecutorServiceMock);
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void linkTracingToCurrentThread_pair_works_as_expected(boolean useNullPair, boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> infoForLinking = (useNullPair) ? null
                                                                              : generateTracingInfo();
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());
        Pair<Deque<Span>, Map<String, String>> expectedPreCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // when
        Pair<Deque<Span>, Map<String, String>> preCallInfo =
            (useStaticMethod)
            ? linkTracingToCurrentThread(infoForLinking)
            : DEFAULT_IMPL.linkTracingToCurrentThread(infoForLinking);
        
        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(preCallInfo).isEqualTo(expectedPreCallInfo);
        if (useNullPair) {
            assertThat(postCallInfo.getLeft()).isNull();
            assertThat(postCallInfo.getRight()).isNullOrEmpty();
        }
        else
            assertThat(postCallInfo).isEqualTo(infoForLinking);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void linkTracingToCurrentThread_pair_works_as_expected_with_non_null_pair_and_null_innards(
        boolean useStaticMethod
    ) {
        // given
        Pair<Deque<Span>, Map<String, String>> infoForLinking = Pair.of(null, null);
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());
        Pair<Deque<Span>, Map<String, String>> expectedPreCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // when
        Pair<Deque<Span>, Map<String, String>> preCallInfo =
            (useStaticMethod)
            ? linkTracingToCurrentThread(infoForLinking)
            : DEFAULT_IMPL.linkTracingToCurrentThread(infoForLinking);
        
        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(preCallInfo).isEqualTo(expectedPreCallInfo);
        assertThat(postCallInfo.getLeft()).isNull();
        assertThat(postCallInfo.getRight()).isNullOrEmpty();
    }

    @DataProvider(value = {
        "true   |   true    |   true",
        "false  |   true    |   true",
        "true   |   false   |   true",
        "false  |   false   |   true",
        "true   |   true    |   false",
        "false  |   true    |   false",
        "true   |   false   |   false",
        "false  |   false   |   false",
    }, splitBy = "\\|")
    @Test
    public void linkTracingToCurrentThread_separate_args_works_as_expected(boolean useNullSpanStack,
                                                                                 boolean useNullMdcInfo,
                                                                                 boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> info = generateTracingInfo();
        info.getRight().put("fooMdcKey", UUID.randomUUID().toString());
        Deque<Span> spanStackForLinking = (useNullSpanStack) ? null : info.getLeft();
        Map<String, String> mdcInfoForLinking = (useNullMdcInfo) ? null : info.getRight();
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());
        Pair<Deque<Span>, Map<String, String>> expectedPreCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        Map<String, String> expectedMdcInfo;
        // The expected MDC info will vary depending on combinations.
        if (useNullMdcInfo) {
            // MDC may still be populated after the call if the span stack is not empty
            if (useNullSpanStack)
                expectedMdcInfo = Collections.emptyMap();
            else {
                // MDC will have been populated with tracing info.
                expectedMdcInfo = new HashMap<>();
                Span expectedSpan = spanStackForLinking.peek();
                expectedMdcInfo.put(SpanFieldForLoggerMdc.TRACE_ID.mdcKey, expectedSpan.getTraceId());
            }
        }
        else {
            // Not null MDC. Start with the MDC info for linking.
            expectedMdcInfo = new HashMap<>(mdcInfoForLinking);
            if (useNullSpanStack) {
                // In the case of a null span stack, the trace info would be removed from the MDC.
                expectedMdcInfo.remove(SpanFieldForLoggerMdc.TRACE_ID.mdcKey);
            }
        }

        // when
        Pair<Deque<Span>, Map<String, String>> preCallInfo =
            (useStaticMethod)
            ? linkTracingToCurrentThread(spanStackForLinking, mdcInfoForLinking)
            : DEFAULT_IMPL.linkTracingToCurrentThread(spanStackForLinking, mdcInfoForLinking);
        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(preCallInfo).isEqualTo(expectedPreCallInfo);
        assertThat(postCallInfo.getLeft()).isEqualTo(spanStackForLinking);
        if (expectedMdcInfo.isEmpty()) {
            assertThat(postCallInfo.getRight()).isNullOrEmpty();
        }
        else {
            assertThat(postCallInfo.getRight()).isEqualTo(expectedMdcInfo);
        }
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void unlinkTracingFromCurrentThread_pair_works_as_expected(boolean useNullPair,
                                                                            boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> infoForLinking = (useNullPair) ? null
                                                                              : generateTracingInfo();
        // Setup the current thread with something that is not ultimately what we expect so that our assertions are
        //      verifying that the unlinkTracingFromCurrentThread method actually did something.
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());

        // when
        if (useStaticMethod) {
            unlinkTracingFromCurrentThread(infoForLinking);
        }
        else {
            DEFAULT_IMPL.unlinkTracingFromCurrentThread(infoForLinking);
        }

        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        if (useNullPair) {
            assertThat(postCallInfo.getLeft()).isNull();
            assertThat(postCallInfo.getRight()).isNullOrEmpty();
        }
        else
            assertThat(postCallInfo).isEqualTo(infoForLinking);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void unlinkTracingFromCurrentThread_pair_works_as_expected_with_non_null_pair_and_null_innards(
        boolean useStaticMethod
    ) {
        // given
        Pair<Deque<Span>, Map<String, String>> infoForLinking = Pair.of(null, null);
        // Setup the current thread with something that is not ultimately what we expect so that our assertions are
        //      verifying that the unlinkTracingFromCurrentThread method actually did something.
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());

        // when
        if (useStaticMethod) {
            unlinkTracingFromCurrentThread(infoForLinking);
        }
        else {
            DEFAULT_IMPL.unlinkTracingFromCurrentThread(infoForLinking);
        }

        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(postCallInfo.getLeft()).isNull();
        assertThat(postCallInfo.getRight()).isNullOrEmpty();
    }

    @DataProvider(value = {
        "true   |   true    |   true",
        "false  |   true    |   true",
        "true   |   false   |   true",
        "false  |   false   |   true",
        "true   |   true    |   false",
        "false  |   true    |   false",
        "true   |   false   |   false",
        "false  |   false   |   false",
    }, splitBy = "\\|")
    @Test
    public void unlinkTracingFromCurrentThread_separate_args_works_as_expected(boolean useNullSpanStack,
                                                                                     boolean useNullMdcInfo,
                                                                                     boolean useStaticMethod) {
        // given
        Pair<Deque<Span>, Map<String, String>> info = generateTracingInfo();
        info.getRight().put("fooMdcKey", UUID.randomUUID().toString());
        Deque<Span> spanStackForLinking = (useNullSpanStack) ? null : info.getLeft();
        Map<String, String> mdcInfoForLinking = (useNullMdcInfo) ? null : info.getRight();
        // Setup the current thread with something that is not ultimately what we expect so that our assertions are
        //      verifying that the unlinkTracingFromCurrentThread method actually did something.
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("foo-" + UUID.randomUUID().toString());

        Map<String, String> expectedMdcInfo;
        // The expected MDC info will vary depending on combinations.
        if (useNullMdcInfo) {
            // MDC may still be populated after the call if the span stack is not empty
            if (useNullSpanStack)
                expectedMdcInfo = Collections.emptyMap();
            else {
                // MDC will have been populated with tracing info.
                expectedMdcInfo = new HashMap<>();
                Span expectedSpan = spanStackForLinking.peek();
                expectedMdcInfo.put(SpanFieldForLoggerMdc.TRACE_ID.mdcKey, expectedSpan.getTraceId());
            }
        }
        else {
            // Not null MDC. Since unlinkTracingFromCurrentThread doesn't call registerWithThread when
            //      the span stack is null we don't need to worry about trace ID being removed from MDC.
            //      Therefore it should match mdcInfoForLinking exactly.
            expectedMdcInfo = new HashMap<>(mdcInfoForLinking);
        }

        // when
        if (useStaticMethod) {
            unlinkTracingFromCurrentThread(spanStackForLinking, mdcInfoForLinking);
        }
        else {
            DEFAULT_IMPL.unlinkTracingFromCurrentThread(spanStackForLinking, mdcInfoForLinking);
        }
        Pair<Deque<Span>, Map<String, String>> postCallInfo = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(),
            MDC.getCopyOfContextMap()
        );

        // then
        assertThat(postCallInfo.getLeft()).isEqualTo(spanStackForLinking);
        if (expectedMdcInfo.isEmpty()) {
            assertThat(postCallInfo.getRight()).isNullOrEmpty();
        }
        else {
            assertThat(postCallInfo.getRight()).isEqualTo(expectedMdcInfo);
        }
    }

    private enum ParentAndCurrentThreadTracingStateScenario {
        PARENT_IS_NULL_AND_CURRENT_IS_NULL(
            () -> null,
            parent -> setupNullCurrentThreadTracingState()
        ),
        PARENT_IS_NULL_AND_CURRENT_IS_DEFINED(
            () -> null,
            parent -> setupDefinedCurrentThreadTracingState()
        ),
        PARENT_IS_DEFINED_AND_CURRENT_IS_NULL(
            AsyncWingtipsHelperTest::generateTracingInfo,
            parent -> setupNullCurrentThreadTracingState()
        ),
        PARENT_IS_DEFINED_AND_CURRENT_IS_DIFFERENT(
            AsyncWingtipsHelperTest::generateTracingInfo,
            parent -> setupDefinedCurrentThreadTracingState()
        ),
        PARENT_AND_CURRENT_MATCH(
            AsyncWingtipsHelperTest::generateTracingInfo,
            parent -> {
                DEFAULT_IMPL.linkTracingToCurrentThread(parent);
                return parent;
            }
        );

        private static TracingState setupNullCurrentThreadTracingState() {
            resetTracing();
            return TracingState.getCurrentThreadTracingState();
        }

        private static TracingState setupDefinedCurrentThreadTracingState() {
            setupCurrentThreadWithTracingInfo();
            Tracer.getInstance().startSubSpan("some-subspan-for-current-thread", SpanPurpose.LOCAL_ONLY);
            return TracingState.getCurrentThreadTracingState();
        }

        public final Supplier<@Nullable TracingState> parentTracingStateSupplier;
        public final Function<@Nullable TracingState, @NotNull TracingState> currentThreadTracingStateSetupFunc;

        ParentAndCurrentThreadTracingStateScenario(
            Supplier<TracingState> parentTracingStateSupplier,
            Function<TracingState, TracingState> currentThreadTracingStateSetupFunc
        ) {
            this.parentTracingStateSupplier = parentTracingStateSupplier;
            this.currentThreadTracingStateSetupFunc = currentThreadTracingStateSetupFunc;
        }

        public ParentAndCurrentThreadTracingState setupTracingStates() {
            return new ParentAndCurrentThreadTracingState(this);
        }

    }

    private static class ParentAndCurrentThreadTracingState {
        private final @Nullable TracingState parentTracingStateOption;
        private final @NotNull TracingState expectedCurrentThreadTracingState;
        private final @Nullable Span expectedParentSpan;

        ParentAndCurrentThreadTracingState(ParentAndCurrentThreadTracingStateScenario scenario) {
            this.parentTracingStateOption = scenario.parentTracingStateSupplier.get();
            this.expectedCurrentThreadTracingState =
                scenario.currentThreadTracingStateSetupFunc.apply(parentTracingStateOption);

            Span currentSpan = Tracer.getInstance().getCurrentSpan();
            assertThat(expectedCurrentThreadTracingState.getActiveSpan()).isSameAs(currentSpan);

            // If the parent tracing state option is null, then that means the current thread tracing state should
            //      be used.
            this.expectedParentSpan = (parentTracingStateOption == null)
                                      ? currentSpan
                                      : parentTracingStateOption.getActiveSpan();
            assertThat(expectedCurrentThreadTracingState.getActiveSpan())
                .isSameAs(Tracer.getInstance().getCurrentSpan());
        }
    }

    @DataProvider
    public static List<List<Object>> parentAndCurrentThreadTracingStateScenarioDataProvider() {
        List<List<Object>> result = new ArrayList<>();
        for (ParentAndCurrentThreadTracingStateScenario scenario : ParentAndCurrentThreadTracingStateScenario.values()) {
            result.add(Arrays.asList(scenario, true));
            result.add(Arrays.asList(scenario, false));
        }

        return result;
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapCompletableFutureWithSpan_works_as_expected_for_completable_futures_that_complete_normally(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        String expectedResultFromFuture = "future-result-" + UUID.randomUUID().toString();
        CompletableFuture<String> origResultFuture = new CompletableFuture<>();

        assertThat(spanRecorder.completedSpans).isEmpty();

        long futureTimeToCompleteMillis = 50;

        // when
        CompletableFuture<String> wrappedResultFuture =
            (useStaticMethod)
            ? AsyncWingtipsHelperStatic.wrapCompletableFutureWithSpan(options, () -> origResultFuture)
            : DEFAULT_IMPL.wrapCompletableFutureWithSpan(options, () -> origResultFuture);

        // then
        // No matter what, we should always be back to the tracing state we started with.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(expectedCurrentThreadTracingState);
        // Since the origResultFuture hasn't completed yet, we shouldn't have any spans finished either.
        assertThat(spanRecorder.completedSpans).isEmpty();

        // and when - wait a little and then complete the origResultFuture.
        sleepThread(futureTimeToCompleteMillis);
        origResultFuture.complete(expectedResultFromFuture);
        String result = wrappedResultFuture.join();

        // then
        // Should still be correct current thread tracing state.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(expectedCurrentThreadTracingState);
        // The wrapped future should be completed, and we should have gotten the expected result.
        assertThat(wrappedResultFuture.isDone()).isTrue();
        assertThat(wrappedResultFuture.isCompletedExceptionally()).isFalse();
        assertThat(result).isEqualTo(expectedResultFromFuture);
        // And now we should have a completed span to represent the origResultFuture work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, futureTimeToCompleteMillis);

        verify(spanTaggerMock).tagSpan(resultSpan, expectedResultFromFuture);
        verifyNoInteractions(errorTaggerMock); // Completed normally, so the error tagger shouldn't be called.

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    private OperationWrapperOptions<String> generateOperationWrapperOptions(
        ParentAndCurrentThreadTracingState scenarioValues,
        String spanName,
        SpanPurpose spanPurpose,
        SpanTagger<String> spanTagger,
        ErrorSpanTagger errorSpanTagger
    ) {
        return OperationWrapperOptions
            .<String>newBuilder(spanName, spanPurpose)
            .withParentTracingState(scenarioValues.parentTracingStateOption)
            .withSpanTagger(spanTagger)
            .withErrorTagger(errorSpanTagger)
            .build();
    }

    private Span verifyWrappedSpanValues(
        String expectedSpanName,
        SpanPurpose expectedSpanPurpose,
        long expectedSpanDurationMillis
    ) {
        // We should have a completed span to represent the work that was wrapped, using the expected options
        //      and having the expected span ancestry.
        assertThat(spanRecorder.completedSpans).hasSize(1);
        Span resultSpan = spanRecorder.completedSpans.get(0);
        assertThat(resultSpan.getSpanName()).isEqualTo(expectedSpanName);
        assertThat(resultSpan.getSpanPurpose()).isEqualTo(expectedSpanPurpose);
        assertThat(resultSpan.getDurationNanos())
            .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(expectedSpanDurationMillis));
        return resultSpan;
    }

    private void verifySpanAncestryForWrapOptions(@NotNull Span span, @Nullable Span expectedParentSpan) {
        if (expectedParentSpan == null) {
            // No expected parent span. It should be a root span.
            assertThat(span.getParentSpanId()).isNull();
            // The only scenario where there could be a root span is if parent tracing state option is null,
            //      *and* the current thread has no tracing state.
            assertThat(Tracer.getInstance().getCurrentSpan()).isNull();
        }
        else {
            // There is an expected parent span. Make sure the resultSpan's trace and parent span IDs match as expected.
            assertThat(span.getTraceId()).isEqualTo(expectedParentSpan.getTraceId());
            assertThat(span.getParentSpanId()).isEqualTo(expectedParentSpan.getSpanId());
        }
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapCompletableFutureWithSpan_works_as_expected_for_completable_futures_that_complete_exceptionally(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        Throwable expectedFutureExceptionCause =
            new Exception("intentional test exception " + UUID.randomUUID().toString());
        CompletableFuture<String> origResultFuture = new CompletableFuture<>();

        assertThat(spanRecorder.completedSpans).isEmpty();

        long futureTimeToCompleteMillis = 50;

        // when
        CompletableFuture<String> wrappedResultFuture =
            (useStaticMethod)
            ? AsyncWingtipsHelperStatic.wrapCompletableFutureWithSpan(options, () -> origResultFuture)
            : DEFAULT_IMPL.wrapCompletableFutureWithSpan(options, () -> origResultFuture);

        // then
        // No matter what, we should always be back to the tracing state we started with.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(expectedCurrentThreadTracingState);
        // Since the origResultFuture hasn't completed yet, we shouldn't have any spans finished either.
        assertThat(spanRecorder.completedSpans).isEmpty();

        // and when - wait a little and then complete the origResultFuture.
        sleepThread(futureTimeToCompleteMillis);
        origResultFuture.completeExceptionally(expectedFutureExceptionCause);
        Throwable resultEx = catchThrowable(wrappedResultFuture::join);

        // then
        // Should still be correct current thread tracing state.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(expectedCurrentThreadTracingState);
        // The wrapped future should be completed, and we should have gotten the expected result.
        assertThat(wrappedResultFuture.isDone()).isTrue();
        assertThat(wrappedResultFuture.isCompletedExceptionally()).isTrue();
        assertThat(resultEx).isInstanceOf(CompletionException.class).hasCause(expectedFutureExceptionCause);
        // And now we should have a completed span to represent the origResultFuture work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, futureTimeToCompleteMillis);

        verify(spanTaggerMock).tagSpan(resultSpan, null);
        verify(errorTaggerMock).tagSpanForError(resultSpan, expectedFutureExceptionCause);

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapCompletableFutureWithSpan_works_as_expected_when_unexpected_exception_occurs_outside_the_future(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        RuntimeException expectedSupplierExceptionCause =
            new RuntimeException("intentional test exception " + UUID.randomUUID().toString());
        long badSupplierTimeToCompleteMillis = 50;
        Supplier<CompletableFuture<String>> badSupplier = () -> {
            sleepThread(badSupplierTimeToCompleteMillis);
            throw expectedSupplierExceptionCause;
        };

        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        Throwable resultEx = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapCompletableFutureWithSpan(options, badSupplier);
            }
            else {
                DEFAULT_IMPL.wrapCompletableFutureWithSpan(options, badSupplier);
            }
        });

        // then
        assertThat(resultEx).isSameAs(expectedSupplierExceptionCause);
        // No matter what, we should always be back to the tracing state we started with.
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(expectedCurrentThreadTracingState);
        // The span intended for the CompletableFuture should have been started and completed before the method threw
        //      the exception to us. That span should have been setup and completed using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(
            expectedSpanName, expectedSpanPurpose, badSupplierTimeToCompleteMillis
        );

        verify(spanTaggerMock).tagSpan(resultSpan, null);
        verify(errorTaggerMock).tagSpanForError(resultSpan, expectedSupplierExceptionCause);

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapCompletableFutureWithSpan_has_expected_tracing_state_attached_to_thread_at_time_of_supplier_execution(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, null, null
        );

        AtomicReference<TracingState> supplierCapturedTracingStateHolder = new AtomicReference<>();
        AtomicReference<TracingState> tracingStateInsideFutureHolder = new AtomicReference<>();
        AtomicReference<String> supplierThreadNameHolder = new AtomicReference<>();
        AtomicReference<String> threadNameInsideFutureHolder = new AtomicReference<>();
        String expectedResult = UUID.randomUUID().toString();
        Supplier<CompletableFuture<String>> badSupplier = () -> {
            TracingState supplierTracingState = TracingState.getCurrentThreadTracingState();
            supplierCapturedTracingStateHolder.set(supplierTracingState);
            supplierThreadNameHolder.set(Thread.currentThread().getName());
            return CompletableFuture.supplyAsync(supplierWithTracing(
                () -> {
                    tracingStateInsideFutureHolder.set(TracingState.getCurrentThreadTracingState());
                    threadNameInsideFutureHolder.set(Thread.currentThread().getName());
                    return expectedResult;
                }),
                Executors.newSingleThreadExecutor()
            );
        };

        // when
        String result = (useStaticMethod)
                        ? AsyncWingtipsHelperStatic.wrapCompletableFutureWithSpan(options, badSupplier).join()
                        : DEFAULT_IMPL.wrapCompletableFutureWithSpan(options, badSupplier).join();

        // then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(supplierCapturedTracingStateHolder.get()).isEqualTo(tracingStateInsideFutureHolder.get());
        verifySpanAncestryForWrapOptions(
            supplierCapturedTracingStateHolder.get().getActiveSpan(),
            scenarioValues.expectedParentSpan
        );
        verifySpanAncestryForWrapOptions(
            tracingStateInsideFutureHolder.get().getActiveSpan(),
            scenarioValues.expectedParentSpan
        );
        assertThat(supplierThreadNameHolder.get()).isNotEqualTo(threadNameInsideFutureHolder.get());
    }

    private enum WrapOperationMethodNullArgScenario {
        NULL_OPTIONS(true, false),
        NULL_OPERATION(false, true);

        private final boolean optionsIsNull;
        private final boolean operationIsNull;

        WrapOperationMethodNullArgScenario(boolean optionsIsNull, boolean operationIsNull) {
            this.optionsIsNull = optionsIsNull;
            this.operationIsNull = operationIsNull;
        }

        public <T> OperationWrapperOptions<T> generateOptions() {
            if (optionsIsNull) {
                return null;
            }

            return OperationWrapperOptions.<T>newBuilder("foo", SpanPurpose.LOCAL_ONLY).build();
        }

        public <T> T generateOperation(Class<T> operationClazz) {
            if (operationIsNull) {
                return null;
            }

            return mock(operationClazz);
        }
    }

    @DataProvider
    public static List<List<Object>> wrapOperationMethodNullArgScenarioDataProvider() {
        List<List<Object>> result = new ArrayList<>();
        for (WrapOperationMethodNullArgScenario scenario : WrapOperationMethodNullArgScenario.values()) {
            result.add(Arrays.asList(scenario, true));
            result.add(Arrays.asList(scenario, false));
        }

        return result;
    }

    @UseDataProvider("wrapOperationMethodNullArgScenarioDataProvider")
    @Test
    public void wrapCompletableFutureWithSpan_throws_NullPointerException_if_passed_null_args(
        WrapOperationMethodNullArgScenario scenario, boolean useStaticMethod
    ) {
        // given
        OperationWrapperOptions<Object> options = scenario.generateOptions();
        Supplier<CompletableFuture<Object>> operation = scenario.generateOperation(Supplier.class);

        String expectedExMessage = (options == null) ? "options cannot be null." : "supplier cannot be null.";

        // when
        Throwable ex = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapCompletableFutureWithSpan(options, operation);
            }
            else {
                DEFAULT_IMPL.wrapCompletableFutureWithSpan(options, operation);
            }
        });

        // then
        assertThat(ex)
            .isInstanceOf(NullPointerException.class)
            .hasMessage(expectedExMessage);
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapCallableWithSpan_works_as_expected_for_callables_that_complete_normally(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) throws Exception {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        String expectedResultFromCallable = "callable-result-" + UUID.randomUUID().toString();
        long callableTimeToCompleteMillis = 50;
        AtomicReference<Span> currentSpanInCallableHolder = new AtomicReference<>();
        Callable<String> callable = () -> {
            sleepThread(callableTimeToCompleteMillis);
            currentSpanInCallableHolder.set(Tracer.getInstance().getCurrentSpan());
            return expectedResultFromCallable;
        };

        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        String result =
            (useStaticMethod)
            ? AsyncWingtipsHelperStatic.wrapCallableWithSpan(options, callable)
            : DEFAULT_IMPL.wrapCallableWithSpan(options, callable);

        // then
        assertThat(result).isEqualTo(expectedResultFromCallable);

        // No matter what, we should always be back to the tracing state we started with.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(expectedCurrentThreadTracingState);
        // And now we should have a completed span to represent the callable work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, callableTimeToCompleteMillis);
        assertThat(currentSpanInCallableHolder.get()).isEqualTo(resultSpan);

        verify(spanTaggerMock).tagSpan(resultSpan, expectedResultFromCallable);
        verifyNoInteractions(errorTaggerMock); // Completed normally, so the error tagger shouldn't be called.

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    private TracingState normalizeTracingState(TracingState origState) {
        Deque<Span> spanStackToUse =
            (origState.spanStack == null || origState.spanStack.isEmpty())
            ? null
            : origState.spanStack;
        Map<String, String> mdcInfoToUse =
            (origState.mdcInfo == null || origState.mdcInfo.isEmpty())
            ? null
            : origState.mdcInfo;
        return new TracingState(spanStackToUse, mdcInfoToUse);
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapCallableWithSpan_works_as_expected_for_callables_that_throw_exceptions(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        long callableTimeToCompleteMillis = 50;
        Exception expectedExceptionFromCallable = new Exception("intentional test exception");
        AtomicReference<Span> currentSpanInCallableHolder = new AtomicReference<>();
        Callable<String> callable = () -> {
            sleepThread(callableTimeToCompleteMillis);
            currentSpanInCallableHolder.set(Tracer.getInstance().getCurrentSpan());
            throw expectedExceptionFromCallable;
        };

        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        Throwable ex = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapCallableWithSpan(options, callable);
            }
            else {
                DEFAULT_IMPL.wrapCallableWithSpan(options, callable);
            }
        });

        // then
        assertThat(ex).isSameAs(expectedExceptionFromCallable);

        // No matter what, we should always be back to the tracing state we started with.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(expectedCurrentThreadTracingState);
        // And now we should have a completed span to represent the callable work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, callableTimeToCompleteMillis);
        assertThat(currentSpanInCallableHolder.get()).isEqualTo(resultSpan);

        verify(spanTaggerMock).tagSpan(resultSpan, null);
        verify(errorTaggerMock).tagSpanForError(resultSpan, expectedExceptionFromCallable);

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    @UseDataProvider("wrapOperationMethodNullArgScenarioDataProvider")
    @Test
    public void wrapCallableWithSpan_throws_NullPointerException_if_passed_null_args(
        WrapOperationMethodNullArgScenario scenario, boolean useStaticMethod
    ) {
        // given
        OperationWrapperOptions<Object> options = scenario.generateOptions();
        Callable<Object> operation = scenario.generateOperation(Callable.class);

        String expectedExMessage = (options == null) ? "options cannot be null." : "callable cannot be null.";

        // when
        Throwable ex = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapCallableWithSpan(options, operation);
            }
            else {
                DEFAULT_IMPL.wrapCallableWithSpan(options, operation);
            }
        });

        // then
        assertThat(ex)
            .isInstanceOf(NullPointerException.class)
            .hasMessage(expectedExMessage);
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapSupplierWithSpan_works_as_expected_for_suppliers_that_complete_normally(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        String expectedResultFromSupplier = "supplier-result-" + UUID.randomUUID().toString();
        long supplierTimeToCompleteMillis = 50;
        AtomicReference<Span> currentSpanInSupplierHolder = new AtomicReference<>();
        Supplier<String> supplier = () -> {
            sleepThread(supplierTimeToCompleteMillis);
            currentSpanInSupplierHolder.set(Tracer.getInstance().getCurrentSpan());
            return expectedResultFromSupplier;
        };

        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        String result =
            (useStaticMethod)
            ? AsyncWingtipsHelperStatic.wrapSupplierWithSpan(options, supplier)
            : DEFAULT_IMPL.wrapSupplierWithSpan(options, supplier);

        // then
        assertThat(result).isEqualTo(expectedResultFromSupplier);

        // No matter what, we should always be back to the tracing state we started with.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(expectedCurrentThreadTracingState);
        // And now we should have a completed span to represent the supplier work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, supplierTimeToCompleteMillis);
        assertThat(currentSpanInSupplierHolder.get()).isEqualTo(resultSpan);

        verify(spanTaggerMock).tagSpan(resultSpan, expectedResultFromSupplier);
        verifyNoInteractions(errorTaggerMock); // Completed normally, so the error tagger shouldn't be called.

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapSupplierWithSpan_works_as_expected_for_suppliers_that_throw_exceptions(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        long supplierTimeToCompleteMillis = 50;
        RuntimeException expectedExceptionFromSupplier = new RuntimeException("intentional test exception");
        AtomicReference<Span> currentSpanInSupplierHolder = new AtomicReference<>();
        Supplier<String> supplier = () -> {
            sleepThread(supplierTimeToCompleteMillis);
            currentSpanInSupplierHolder.set(Tracer.getInstance().getCurrentSpan());
            throw expectedExceptionFromSupplier;
        };

        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        Throwable ex = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapSupplierWithSpan(options, supplier);
            }
            else {
                DEFAULT_IMPL.wrapSupplierWithSpan(options, supplier);
            }
        });

        // then
        assertThat(ex).isSameAs(expectedExceptionFromSupplier);

        // No matter what, we should always be back to the tracing state we started with.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(expectedCurrentThreadTracingState);
        // And now we should have a completed span to represent the supplier work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, supplierTimeToCompleteMillis);
        assertThat(currentSpanInSupplierHolder.get()).isEqualTo(resultSpan);

        verify(spanTaggerMock).tagSpan(resultSpan, null);
        verify(errorTaggerMock).tagSpanForError(resultSpan, expectedExceptionFromSupplier);

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    @UseDataProvider("wrapOperationMethodNullArgScenarioDataProvider")
    @Test
    public void wrapSupplierWithSpan_throws_NullPointerException_if_passed_null_args(
        WrapOperationMethodNullArgScenario scenario, boolean useStaticMethod
    ) {
        // given
        OperationWrapperOptions<Object> options = scenario.generateOptions();
        Supplier<Object> operation = scenario.generateOperation(Supplier.class);

        String expectedExMessage = (options == null) ? "options cannot be null." : "supplier cannot be null.";

        // when
        Throwable ex = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapSupplierWithSpan(options, operation);
            }
            else {
                DEFAULT_IMPL.wrapSupplierWithSpan(options, operation);
            }
        });

        // then
        assertThat(ex)
            .isInstanceOf(NullPointerException.class)
            .hasMessage(expectedExMessage);
    }
    
    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapRunnableWithSpan_works_as_expected_for_runnables_that_complete_normally(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        long runnableTimeToCompleteMillis = 50;
        Runnable runnableMock = mock(Runnable.class);
        AtomicReference<Span> currentSpanInRunnableHolder = new AtomicReference<>();
        doAnswer(invocation -> {
            sleepThread(runnableTimeToCompleteMillis);
            currentSpanInRunnableHolder.set(Tracer.getInstance().getCurrentSpan());
            return null;
        }).when(runnableMock).run();

        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        if (useStaticMethod) {
            AsyncWingtipsHelperStatic.wrapRunnableWithSpan(options, runnableMock);
        }
        else {
            DEFAULT_IMPL.wrapRunnableWithSpan(options, runnableMock);
        }

        // then
        verify(runnableMock).run();

        // No matter what, we should always be back to the tracing state we started with.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(expectedCurrentThreadTracingState);
        // And now we should have a completed span to represent the runnable work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, runnableTimeToCompleteMillis);
        assertThat(currentSpanInRunnableHolder.get()).isEqualTo(resultSpan);

        verify(spanTaggerMock).tagSpan(resultSpan, null);
        verifyNoInteractions(errorTaggerMock); // Completed normally, so the error tagger shouldn't be called.

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapRunnableWithSpan_works_as_expected_for_runnables_that_throw_exceptions(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        long runnableTimeToCompleteMillis = 50;
        RuntimeException expectedExceptionFromRunnable = new RuntimeException("intentional test exception");
        AtomicReference<Span> currentSpanInRunnableHolder = new AtomicReference<>();
        Runnable runnable = () -> {
            sleepThread(runnableTimeToCompleteMillis);
            currentSpanInRunnableHolder.set(Tracer.getInstance().getCurrentSpan());
            throw expectedExceptionFromRunnable;
        };

        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        Throwable ex = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapRunnableWithSpan(options, runnable);
            }
            else {
                DEFAULT_IMPL.wrapRunnableWithSpan(options, runnable);
            }
        });

        // then
        assertThat(ex).isSameAs(expectedExceptionFromRunnable);

        // No matter what, we should always be back to the tracing state we started with.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(expectedCurrentThreadTracingState);
        // And now we should have a completed span to represent the runnable work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, runnableTimeToCompleteMillis);
        assertThat(currentSpanInRunnableHolder.get()).isEqualTo(resultSpan);

        verify(spanTaggerMock).tagSpan(resultSpan, null);
        verify(errorTaggerMock).tagSpanForError(resultSpan, expectedExceptionFromRunnable);

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    @UseDataProvider("wrapOperationMethodNullArgScenarioDataProvider")
    @Test
    public void wrapRunnableWithSpan_throws_NullPointerException_if_passed_null_args(
        WrapOperationMethodNullArgScenario scenario, boolean useStaticMethod
    ) {
        // given
        OperationWrapperOptions<Object> options = scenario.generateOptions();
        Runnable operation = scenario.generateOperation(Runnable.class);

        String expectedExMessage = (options == null) ? "options cannot be null." : "runnable cannot be null.";

        // when
        Throwable ex = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapRunnableWithSpan(options, operation);
            }
            else {
                DEFAULT_IMPL.wrapRunnableWithSpan(options, operation);
            }
        });

        // then
        assertThat(ex)
            .isInstanceOf(NullPointerException.class)
            .hasMessage(expectedExMessage);
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapCheckedRunnableWithSpan_works_as_expected_for_runnables_that_complete_normally(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) throws Exception {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        long runnableTimeToCompleteMillis = 50;
        CheckedRunnable runnableMock = mock(CheckedRunnable.class);
        AtomicReference<Span> currentSpanInRunnableHolder = new AtomicReference<>();
        doAnswer(invocation -> {
            sleepThread(runnableTimeToCompleteMillis);
            currentSpanInRunnableHolder.set(Tracer.getInstance().getCurrentSpan());
            return null;
        }).when(runnableMock).run();

        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        if (useStaticMethod) {
            AsyncWingtipsHelperStatic.wrapCheckedRunnableWithSpan(options, runnableMock);
        }
        else {
            DEFAULT_IMPL.wrapCheckedRunnableWithSpan(options, runnableMock);
        }

        // then
        verify(runnableMock).run();

        // No matter what, we should always be back to the tracing state we started with.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(expectedCurrentThreadTracingState);
        // And now we should have a completed span to represent the runnable work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, runnableTimeToCompleteMillis);
        assertThat(currentSpanInRunnableHolder.get()).isEqualTo(resultSpan);

        verify(spanTaggerMock).tagSpan(resultSpan, null);
        verifyNoInteractions(errorTaggerMock); // Completed normally, so the error tagger shouldn't be called.

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    @UseDataProvider("parentAndCurrentThreadTracingStateScenarioDataProvider")
    @Test
    public void wrapCheckedRunnableWithSpan_works_as_expected_for_runnables_that_throw_exceptions(
        ParentAndCurrentThreadTracingStateScenario scenario, boolean useStaticMethod
    ) {
        // given
        ParentAndCurrentThreadTracingState scenarioValues = scenario.setupTracingStates();

        TracingState expectedCurrentThreadTracingState = scenarioValues.expectedCurrentThreadTracingState;

        String expectedSpanName = "foo-span-" + UUID.randomUUID().toString();
        SpanPurpose expectedSpanPurpose = SpanPurpose.LOCAL_ONLY;
        SpanTagger<String> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        OperationWrapperOptions<String> options = generateOperationWrapperOptions(
            scenarioValues, expectedSpanName, expectedSpanPurpose, spanTaggerMock, errorTaggerMock
        );

        long runnableTimeToCompleteMillis = 50;
        Exception expectedExceptionFromRunnable = new Exception("intentional test exception");
        AtomicReference<Span> currentSpanInRunnableHolder = new AtomicReference<>();
        CheckedRunnable runnable = () -> {
            sleepThread(runnableTimeToCompleteMillis);
            currentSpanInRunnableHolder.set(Tracer.getInstance().getCurrentSpan());
            throw expectedExceptionFromRunnable;
        };

        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        Throwable ex = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapCheckedRunnableWithSpan(options, runnable);
            }
            else {
                DEFAULT_IMPL.wrapCheckedRunnableWithSpan(options, runnable);
            }
        });

        // then
        assertThat(ex).isSameAs(expectedExceptionFromRunnable);

        // No matter what, we should always be back to the tracing state we started with.
        assertThat(normalizeTracingState(TracingState.getCurrentThreadTracingState()))
            .isEqualTo(expectedCurrentThreadTracingState);
        // And now we should have a completed span to represent the runnable work, using the expected options
        //      and having the expected span ancestry.
        Span resultSpan = verifyWrappedSpanValues(expectedSpanName, expectedSpanPurpose, runnableTimeToCompleteMillis);
        assertThat(currentSpanInRunnableHolder.get()).isEqualTo(resultSpan);

        verify(spanTaggerMock).tagSpan(resultSpan, null);
        verify(errorTaggerMock).tagSpanForError(resultSpan, expectedExceptionFromRunnable);

        verifySpanAncestryForWrapOptions(resultSpan, scenarioValues.expectedParentSpan);
    }

    @UseDataProvider("wrapOperationMethodNullArgScenarioDataProvider")
    @Test
    public void wrapCheckedRunnableWithSpan_throws_NullPointerException_if_passed_null_args(
        WrapOperationMethodNullArgScenario scenario, boolean useStaticMethod
    ) {
        // given
        OperationWrapperOptions<Object> options = scenario.generateOptions();
        CheckedRunnable operation = scenario.generateOperation(CheckedRunnable.class);

        String expectedExMessage = (options == null) ? "options cannot be null." : "runnable cannot be null.";

        // when
        Throwable ex = catchThrowable(() -> {
            if (useStaticMethod) {
                AsyncWingtipsHelperStatic.wrapCheckedRunnableWithSpan(options, operation);
            }
            else {
                DEFAULT_IMPL.wrapCheckedRunnableWithSpan(options, operation);
            }
        });

        // then
        assertThat(ex)
            .isInstanceOf(NullPointerException.class)
            .hasMessage(expectedExMessage);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void AsyncWingtipsHelperDefaultImpl_wrapCheckedExInRuntimeExIfNecessary_works_as_expected(
        boolean exIsRuntimeEx
    ) {
        // given
        Exception ex = (exIsRuntimeEx)
                       ? new RuntimeException("intentional test RuntimeException")
                       : new Exception("intentional test non-RuntimeException");

        // when
        RuntimeException result = AsyncWingtipsHelperDefaultImpl
            .wrapCheckedExInRuntimeExIfNecessary(ex);

        // then
        if (exIsRuntimeEx) {
            assertThat(result).isSameAs(ex);
        }
        else {
            assertThat(result)
                .isNotSameAs(ex)
                .hasCause(ex);
        }
    }

    @DataProvider(value = {
        "false  |   false   |   false",
        "true   |   false   |   false",
        "false  |   true    |   false",
        "false  |   false   |   true",
        "true   |   true    |   false",
        "true   |   false   |   true",
        "false  |   true    |   true",
        "true   |   true    |   true",
    }, splitBy = "\\|")
    @Test
    public void AsyncWingtipsHelperDefaultImpl_doSpanTaggingWithoutExceptionPropagation_works_as_expected(
        boolean taggerIsNull, boolean errorTaggerIsNull, boolean errorExIsNull
    ) {
        // given
        Span spanMock = mock(Span.class);
        Object payload = new Object();

        SpanTagger<Object> spanTaggerMock = (taggerIsNull) ? null : mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = (errorTaggerIsNull) ? null : mock(ErrorSpanTagger.class);
        Throwable errorEx = (errorExIsNull) ? null : mock(Throwable.class);

        OperationWrapperOptions<Object> options = OperationWrapperOptions
            .newBuilder("foo", SpanPurpose.LOCAL_ONLY)
            .withSpanTagger(spanTaggerMock)
            .withErrorTagger(errorTaggerMock)
            .build();

        // when
        AsyncWingtipsHelperDefaultImpl.doSpanTaggingWithoutExceptionPropagation(spanMock, options, payload, errorEx);

        // then
        if (spanTaggerMock != null) {
            verify(spanTaggerMock).tagSpan(spanMock, payload);
        }

        if (errorTaggerMock != null) {
            if (errorEx != null) {
                verify(errorTaggerMock).tagSpanForError(spanMock, errorEx);
            }
            else {
                verifyNoInteractions(errorTaggerMock);
            }
        }
    }

    @DataProvider(value = {
        "true   |   true",
        "false  |   true",
        "true   |   false",
    }, splitBy = "\\|")
    @Test
    public void AsyncWingtipsHelperDefaultImpl_doSpanTaggingWithoutExceptionPropagation_gracefully_handles_unexpected_tagger_exceptions(
        boolean taggerThrowsEx, boolean errorTaggerThrowsEx
    ) {
        // given
        Span spanMock = mock(Span.class);
        Object payload = new Object();

        SpanTagger<Object> spanTaggerMock = mock(SpanTagger.class);
        ErrorSpanTagger errorTaggerMock = mock(ErrorSpanTagger.class);
        Throwable errorEx = mock(Throwable.class);

        if (taggerThrowsEx) {
            doThrow(new RuntimeException("intentional span tagger exception"))
                .when(spanTaggerMock).tagSpan(any(), any());
        }

        if (errorTaggerThrowsEx) {
            doThrow(new RuntimeException("intentional error tagger exception"))
                .when(errorTaggerMock).tagSpanForError(any(), any());
        }

        OperationWrapperOptions<Object> options = OperationWrapperOptions
            .newBuilder("foo", SpanPurpose.LOCAL_ONLY)
            .withSpanTagger(spanTaggerMock)
            .withErrorTagger(errorTaggerMock)
            .build();

        // when
        Throwable ex = catchThrowable(
            () -> AsyncWingtipsHelperDefaultImpl.doSpanTaggingWithoutExceptionPropagation(
                spanMock, options, payload, errorEx
            )
        );

        // then
        assertThat(ex).isNull();
        verify(spanTaggerMock).tagSpan(spanMock, payload);
        verify(errorTaggerMock).tagSpanForError(spanMock, errorEx);
    }

    @DataProvider(value = {
        "true   |   true",
        "false  |   true",
        "true   |   false",
    }, splitBy = "\\|")
    @Test
    public void AsyncWingtipsHelperDefaultImpl_doSpanTaggingWithoutExceptionPropagation_does_nothing_when_span_or_options_are_null(
        boolean spanIsNull, boolean optionsIsNull
    ) {
        // given
        Span spanMock = (spanIsNull) ? null : mock(Span.class);
        OperationWrapperOptions<Object> optionsMock = (optionsIsNull) ? null : mock(OperationWrapperOptions.class);
        Object payload = mock(Object.class);
        Throwable errorEx = mock(Throwable.class);

        // when
        Throwable ex = catchThrowable(
            () -> AsyncWingtipsHelperDefaultImpl.doSpanTaggingWithoutExceptionPropagation(
                spanMock, optionsMock, payload, errorEx
            )
        );

        // then
        assertThat(ex).isNull();
        verifyNoInteractions(payload, errorEx);
        if (spanMock != null) {
            verifyNoInteractions(spanMock);
        }

        if (optionsMock != null) {
            verifyNoInteractions(optionsMock);
        }
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void AsyncWingtipsHelperDefaultImpl_doCloseSpanIfPossible_works_as_expected(boolean spanIsNull) {
        // given
        Span spanMock = (spanIsNull) ? null : mock(Span.class);

        // when
        AsyncWingtipsHelperDefaultImpl.doCloseSpanIfPossible(spanMock);

        // then
        if (spanMock != null) {
            verify(spanMock).close();
        }
    }

}
