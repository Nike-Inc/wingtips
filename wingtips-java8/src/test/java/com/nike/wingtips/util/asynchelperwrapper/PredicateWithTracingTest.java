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
import java.util.function.Predicate;

import static com.nike.wingtips.util.asynchelperwrapper.PredicateWithTracing.withTracing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link PredicateWithTracing}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class PredicateWithTracingTest {

    private Predicate predicateMock;
    List<Deque<Span>> currentSpanStackWhenPredicateWasCalled;
    List<Map<String, String>> currentMdcInfoWhenPredicateWasCalled;
    boolean throwExceptionDuringCall;
    boolean returnValIfNoException;
    Object inObj;

    @Before
    public void beforeMethod() {
        predicateMock = mock(Predicate.class);

        inObj = new Object();
        throwExceptionDuringCall = false;
        returnValIfNoException = true;
        currentSpanStackWhenPredicateWasCalled = new ArrayList<>();
        currentMdcInfoWhenPredicateWasCalled = new ArrayList<>();
        doAnswer(invocation -> {
            currentSpanStackWhenPredicateWasCalled.add(Tracer.getInstance().getCurrentSpanStackCopy());
            currentMdcInfoWhenPredicateWasCalled.add(MDC.getCopyOfContextMap());
            if (throwExceptionDuringCall)
                throw new RuntimeException("kaboom");
            return returnValIfNoException;
        }).when(predicateMock).test(inObj);

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
        PredicateWithTracing instance = (useStaticFactory)
                                         ? withTracing(predicateMock)
                                         : new PredicateWithTracing(predicateMock);

        // then
        assertThat(instance.origPredicate).isSameAs(predicateMock);
        assertThat(instance.spanStackForExecution).isEqualTo(spanStackMock);
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
        PredicateWithTracing instance = (useStaticFactory)
                                         ? withTracing(predicateMock, Pair.of(spanStackMock, mdcInfoMock))
                                         : new PredicateWithTracing(predicateMock, Pair.of(spanStackMock, mdcInfoMock)
                                         );

        // then
        assertThat(instance.origPredicate).isSameAs(predicateMock);
        assertThat(instance.spanStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void pair_constructor_sets_fields_as_expected_when_pair_is_null(boolean useStaticFactory) {
        // when
        PredicateWithTracing instance = (useStaticFactory)
                                         ? withTracing(predicateMock, (Pair)null)
                                         : new PredicateWithTracing(predicateMock, (Pair)null);

        // then
        assertThat(instance.origPredicate).isSameAs(predicateMock);
        assertThat(instance.spanStackForExecution).isNull();
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
        PredicateWithTracing instance = (useStaticFactory)
                                         ? withTracing(predicateMock, spanStackMock, mdcInfoMock)
                                         : new PredicateWithTracing(predicateMock, spanStackMock, mdcInfoMock);

        // then
        assertThat(instance.origPredicate).isSameAs(predicateMock);
        assertThat(instance.spanStackForExecution).isEqualTo(spanStackMock);
        assertThat(instance.mdcContextMapForExecution).isEqualTo(mdcInfoMock);
    }

    @Test
    public void constructors_throw_exception_if_passed_null_operator() {
        // given
        final Deque<Span> spanStackMock = mock(Deque.class);
        final Map<String, String> mdcInfoMock = mock(Map.class);

        // expect
        assertThat(catchThrowable(() -> new PredicateWithTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null)))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new PredicateWithTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(catchThrowable(() -> withTracing(null, Pair.of(spanStackMock, mdcInfoMock))))
            .isInstanceOf(IllegalArgumentException.class);

        // and expect
        assertThat(catchThrowable(() -> new PredicateWithTracing(null, spanStackMock, mdcInfoMock)))
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
        PredicateWithTracing instance = new PredicateWithTracing(
            predicateMock, spanStack, mdcInfo
        );
        resetTracing();
        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        // when
        Throwable ex = null;
        Boolean result = null;
        try {
            result = instance.test(inObj);
        }
        catch(Throwable t) {
            ex = t;
        }

        // then
        verify(predicateMock).test(inObj);
        if (throwException) {
            assertThat(ex).isNotNull();
            assertThat(result).isNull();
        }
        else {
            assertThat(ex).isNull();
            assertThat(result).isEqualTo(predicateReturnVal);
        }

        assertThat(currentSpanStackWhenPredicateWasCalled.get(0)).isEqualTo(spanStack);
        assertThat(currentMdcInfoWhenPredicateWasCalled.get(0)).isEqualTo(mdcInfo);

        assertThat(Tracer.getInstance().getCurrentSpanStackCopy()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

}