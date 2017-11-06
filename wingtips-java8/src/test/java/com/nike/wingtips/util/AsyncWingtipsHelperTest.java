package com.nike.wingtips.util;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.asynchelperwrapper.BiConsumerWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.BiFunctionWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.BiPredicateWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.CallableWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.ConsumerWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.ExecutorServiceWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.FunctionWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.PredicateWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.RunnableWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.SupplierWithTracing;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.supplierWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
        
        resetTracing();
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
    }

    private Pair<Deque<Span>, Map<String, String>> generateTracingInfo() {
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("someSpan");
        Pair<Deque<Span>, Map<String, String>> result = Pair.of(
            Tracer.getInstance().getCurrentSpanStackCopy(), new HashMap<>(MDC.getCopyOfContextMap())
        );
        resetTracing();
        return result;
    }

    private Pair<Deque<Span>, Map<String, String>> setupCurrentThreadWithTracingInfo() {
        resetTracing();
        Tracer.getInstance().startRequestWithRootSpan("request-" + UUID.randomUUID().toString());
        return Pair.of(Tracer.getInstance().getCurrentSpanStackCopy(), MDC.getCopyOfContextMap());
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
                expectedMdcInfo.put(Tracer.TRACE_ID_MDC_KEY, expectedSpan.getTraceId());
                expectedMdcInfo.put(Tracer.SPAN_JSON_MDC_KEY, expectedSpan.toJSON());
            }
        }
        else {
            // Not null MDC. Start with the MDC info for linking.
            expectedMdcInfo = new HashMap<>(mdcInfoForLinking);
            if (useNullSpanStack) {
                // In the case of a null span stack, the trace info would be removed from the MDC.
                expectedMdcInfo.remove(Tracer.TRACE_ID_MDC_KEY);
                expectedMdcInfo.remove(Tracer.SPAN_JSON_MDC_KEY);
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
                expectedMdcInfo.put(Tracer.TRACE_ID_MDC_KEY, expectedSpan.getTraceId());
                expectedMdcInfo.put(Tracer.SPAN_JSON_MDC_KEY, expectedSpan.toJSON());
            }
        }
        else {
            // Not null MDC. Since unlinkTracingFromCurrentThread doesn't call registerWithThread when
            //      the span stack is null we don't need to worry about trace ID and span JSON being removed from MDC.
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

}
