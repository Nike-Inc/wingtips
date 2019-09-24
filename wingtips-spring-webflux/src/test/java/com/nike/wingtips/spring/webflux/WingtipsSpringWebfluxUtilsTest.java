package com.nike.wingtips.spring.webflux;

import com.nike.internal.util.MapBuilder;
import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.util.TracingState;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;
import java.util.UUID;

import reactor.util.context.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link WingtipsSpringWebfluxUtils}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsSpringWebfluxUtilsTest {

    private ServerWebExchange exchangeMock;

    @Before
    public void beforeMethod() {
        exchangeMock = mock(ServerWebExchange.class);
    }

    @Test
    public void code_coverage_hoops() {
        // jump!
        new WingtipsSpringWebfluxUtils();
    }

    @Test
    public void addTracingInfoToRequestAttributes_adds_expected_attributes() {
        // given
        @SuppressWarnings("unchecked")
        Map<String, Object> attrsMock = mock(Map.class);
        doReturn(attrsMock).when(exchangeMock).getAttributes();

        Span span = Span.newBuilder("foo", Span.SpanPurpose.SERVER).build();
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        WingtipsSpringWebfluxUtils.addTracingInfoToRequestAttributes(tracingStateMock, span, exchangeMock);

        // then
        verify(attrsMock).put(TraceHeaders.TRACE_ID, span.getTraceId());
        verify(attrsMock).put(TraceHeaders.SPAN_ID, span.getSpanId());
        verify(attrsMock).put(TracingState.class.getName(), tracingStateMock);
        verifyNoMoreInteractions(attrsMock);
    }

    @Test
    public void tracingStateFromExchange_returns_value_from_exchange_attributes() {
        // given
        TracingState expectedResult = mock(TracingState.class);
        doReturn(expectedResult).when(exchangeMock).getAttribute(TracingState.class.getName());

        // when
        TracingState result = WingtipsSpringWebfluxUtils.tracingStateFromExchange(exchangeMock);

        // then
        assertThat(result).isSameAs(expectedResult);
        verify(exchangeMock).getAttribute(TracingState.class.getName());
    }

    @Test
    public void subscriberContextWithTracingInfo_works_as_expected() {
        // given
        Map<String, String> origContextPairs = MapBuilder
            .builder("foo", UUID.randomUUID().toString())
            .put("bar", UUID.randomUUID().toString())
            .build();
        Context origContext = Context.of(origContextPairs);
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        Context result = WingtipsSpringWebfluxUtils.subscriberContextWithTracingInfo(origContext, tracingStateMock);

        // then
        assertThat(result.size()).isEqualTo(origContextPairs.size() + 1);
        origContextPairs.forEach(
            (k, v) -> assertThat(result.<String>get(k)).isEqualTo(v)
        );
        assertThat(result.get(TracingState.class)).isSameAs(tracingStateMock);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void tracingStateFromContext_works_as_expected(boolean contextHasTracingState) {
        // given
        TracingState tracingStateMock = mock(TracingState.class);
        Context context = (contextHasTracingState)
                          ? Context.of(TracingState.class, tracingStateMock)
                          : Context.empty();

        TracingState expectedResult = (contextHasTracingState) ? tracingStateMock : null;

        // when
        TracingState result = WingtipsSpringWebfluxUtils.tracingStateFromContext(context);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }
}