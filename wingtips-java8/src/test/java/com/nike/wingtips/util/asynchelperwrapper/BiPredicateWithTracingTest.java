package com.nike.wingtips.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import static com.nike.wingtips.util.asynchelperwrapper.BiPredicateWithTracing.withTracing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link BiPredicateWithTracing}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class BiPredicateWithTracingTest {

    private BiPredicate biPredicateMock;
    List<Deque<Span>> currentSpanStackWhenBiPredicateWasCalled;
    List<Map<String, String>> currentMdcInfoWhenBiPredicateWasCalled;
    boolean throwExceptionDuringCall;
    boolean returnValIfNoException;
    Object inObj1;
    Object inObj2;

    @Before
    public void beforeMethod() {
        biPredicateMock = mock(BiPredicate.class);

        inObj1 = new Object();
        inObj2 = new Object();
        throwExceptionDuringCall = false;
        returnValIfNoException = true;
        currentSpanStackWhenBiPredicateWasCalled = new ArrayList<>();
        currentMdcInfoWhenBiPredicateWasCalled = new ArrayList<>();
        doAnswer(invocation -> {
            currentSpanStackWhenBiPredicateWasCalled.add(Tracer.getInstance().getCurrentSpanStackCopy());
            currentMdcInfoWhenBiPredicateWasCalled.add(MDC.getCopyOfContextMap());
            if (throwExceptionDuringCall)
                throw new RuntimeException("kaboom");
            return returnValIfNoException;
        }).when(biPredicateMock).test(inObj1, inObj2);

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

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void current_thread_info_constructor_sets_fields_as_expected(boolean useStaticFactory) {
        // given
        Tracer.getInstance().startRequestWithRootSpan("request-" + UUID.randomUUID().toString());
        Deque<Span> spanStackMock = Tracer.getInstance().getCurrentSpanStackCopy();
        Map<String, String> mdcInfoMock = MDC.getCopyOfContextMap();

        // when
        BiPredicateWithTracing instance = (useStaticFactory)
                                         ? withTracing(biPredicateMock)
                                         : new BiPredicateWithTracing(biPredicateMock);

        // then
        assertThat(instance.origBiPredicate).isSameAs(biPredicateMock);
        assertThat(instance.distributedTraceStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @DataProvider(value = {
        "true   |   true    |   true",
        "true   |   false   |   true",
        "false  |   true    |   true",
        "false  |   false   |   true",
        "true   |   true    |   false",
        "true   |   false   |   false",
        "false  |   true    |   false",
        "false  |   false   |   false",
    }, splitBy = "\\|")
    @Test
    public void pair_constructor_sets_fields_as_expected(
        boolean nullSpanStack, boolean nullMdcInfo, boolean useStaticFactory
    ) {
        // given
        Deque<Span> spanStackMock = (nullSpanStack) ? null : mock(Deque.class);
        Map<String, String> mdcInfoMock = (nullMdcInfo) ? null : mock(Map.class);

        // when
        BiPredicateWithTracing instance = (useStaticFactory)
                                         ? withTracing(biPredicateMock, Pair.of(spanStackMock, mdcInfoMock))
                                         : new BiPredicateWithTracing(biPredicateMock, Pair.of(spanStackMock, mdcInfoMock)
                                         );

        // then
        assertThat(instance.origBiPredicate).isSameAs(biPredicateMock);
        assertThat(instance.distributedTraceStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void pair_constructor_sets_fields_as_expected_when_pair_is_null(boolean useStaticFactory) {
        // when
        BiPredicateWithTracing instance = (useStaticFactory)
                                         ? withTracing(biPredicateMock, (Pair)null)
                                         : new BiPredicateWithTracing(biPredicateMock, (Pair)null);

        // then
        assertThat(instance.origBiPredicate).isSameAs(biPredicateMock);
        assertThat(instance.distributedTraceStackForExecution).isNull();
        assertThat(instance.mdcContextMapForExecution).isNull();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void kitchen_sink_constructor_sets_fields_as_expected(boolean useStaticFactory) {
        // given
        Deque<Span> spanStackMock = mock(Deque.class);
        Map<String, String> mdcInfoMock = mock(Map.class);

        // when
        BiPredicateWithTracing instance = (useStaticFactory)
                                         ? withTracing(biPredicateMock, spanStackMock, mdcInfoMock)
                                         : new BiPredicateWithTracing(biPredicateMock, spanStackMock, mdcInfoMock);

        // then
        assertThat(instance.origBiPredicate).isSameAs(biPredicateMock);
        assertThat(instance.distributedTraceStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @Test
    public void constructors_throw_exception_if_passed_null_operator() {
        // given
        final Deque<Span> spanStackMock = mock(Deque.class);
        final Map<String, String> mdcInfoMock = mock(Map.class);

        // expect
        assertThat(catchThrowable(() -> new BiPredicateWithTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new BiPredicateWithTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new BiPredicateWithTracing(null, spanStackMock, mdcInfoMock)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null, spanStackMock, mdcInfoMock)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void test_handles_tracing_and_mdc_info_as_expected(boolean throwException, boolean predicateReturnVal) {
        // given
        throwExceptionDuringCall = throwException;
        returnValIfNoException = predicateReturnVal;
        Tracer.getInstance().startRequestWithRootSpan("foo");
        Deque<Span> spanStack = Tracer.getInstance().getCurrentSpanStackCopy();
        Map<String, String> mdcInfo = MDC.getCopyOfContextMap();
        BiPredicateWithTracing instance = new BiPredicateWithTracing(
            biPredicateMock, spanStack, mdcInfo
        );
        resetTracing();
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        // when
        Throwable ex = null;
        Boolean result = null;
        try {
            result = instance.test(inObj1, inObj2);
        }
        catch(Throwable t) {
            ex = t;
        }

        // then
        verify(biPredicateMock).test(inObj1, inObj2);
        if (throwException) {
            assertThat(ex).isNotNull();
            assertThat(result).isNull();
        }
        else {
            assertThat(ex).isNull();
            assertThat(result).isEqualTo(predicateReturnVal);
        }

        assertThat(currentSpanStackWhenBiPredicateWasCalled.get(0)).isEqualTo(spanStack);
        assertThat(currentMdcInfoWhenBiPredicateWasCalled.get(0)).isEqualTo(mdcInfo);

        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

}