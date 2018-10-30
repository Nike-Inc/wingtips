package com.nike.wingtips.apache.httpclient;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.apache.httpclient.testutils.ArgCapturingHttpTagAndSpanNamingStrategy;
import com.nike.wingtips.apache.httpclient.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.InitialSpanNameArgs;
import com.nike.wingtips.apache.httpclient.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.RequestTaggingArgs;
import com.nike.wingtips.apache.httpclient.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.ResponseTaggingArgs;
import com.nike.wingtips.apache.httpclient.tag.ApacheHttpClientTagAdapter;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.nike.wingtips.TraceHeaders.PARENT_SPAN_ID;
import static com.nike.wingtips.TraceHeaders.SPAN_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_SAMPLED;
import static com.nike.wingtips.http.HttpRequestTracingUtils.convertSampleableBooleanToExpectedB3Value;
import static org.apache.http.HttpVersion.HTTP_1_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link WingtipsHttpClientBuilder}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsHttpClientBuilderTest {

    private WingtipsHttpClientBuilder builder;

    private HttpRequest requestMock;
    private HttpResponse responseMock;
    private HttpContext httpContext;
    private RequestLine requestLineMock;
    private StatusLine statusLineMock;

    private HttpTagAndSpanNamingStrategy<HttpRequest, HttpResponse> tagAndNamingStrategy;
    private HttpTagAndSpanNamingAdapter<HttpRequest, HttpResponse> tagAndNamingAdapterMock;
    private AtomicReference<String> initialSpanNameFromStrategy;
    private AtomicBoolean strategyInitialSpanNameMethodCalled;
    private AtomicBoolean strategyRequestTaggingMethodCalled;
    private AtomicBoolean strategyResponseTaggingAndFinalSpanNameMethodCalled;
    private AtomicReference<InitialSpanNameArgs> strategyInitialSpanNameArgs;
    private AtomicReference<RequestTaggingArgs> strategyRequestTaggingArgs;
    private AtomicReference<ResponseTaggingArgs> strategyResponseTaggingArgs;

    private String method;
    private String uri;

    private int responseCode;

    private SpanRecorder spanRecorder;

    @Before
    public void beforeMethod() {
        resetTracing();

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);

        initialSpanNameFromStrategy = new AtomicReference<>("span-name-from-strategy-" + UUID.randomUUID().toString());
        strategyInitialSpanNameMethodCalled = new AtomicBoolean(false);
        strategyRequestTaggingMethodCalled = new AtomicBoolean(false);
        strategyResponseTaggingAndFinalSpanNameMethodCalled = new AtomicBoolean(false);
        strategyInitialSpanNameArgs = new AtomicReference<>(null);
        strategyRequestTaggingArgs = new AtomicReference<>(null);
        strategyResponseTaggingArgs = new AtomicReference<>(null);
        tagAndNamingStrategy = new ArgCapturingHttpTagAndSpanNamingStrategy(
            initialSpanNameFromStrategy, strategyInitialSpanNameMethodCalled, strategyRequestTaggingMethodCalled,
            strategyResponseTaggingAndFinalSpanNameMethodCalled, strategyInitialSpanNameArgs,
            strategyRequestTaggingArgs, strategyResponseTaggingArgs
        );
        tagAndNamingAdapterMock = mock(HttpTagAndSpanNamingAdapter.class);

        builder = new WingtipsHttpClientBuilder(true, tagAndNamingStrategy, tagAndNamingAdapterMock);

        requestMock = mock(HttpRequest.class);
        responseMock = mock(HttpResponse.class);
        httpContext = new BasicHttpContext();
        requestLineMock = mock(RequestLine.class);
        statusLineMock = mock(StatusLine.class);

        method = "GET";
        uri = "http://localhost:4242/foo/bar";
        responseCode = 500;

        doReturn(requestLineMock).when(requestMock).getRequestLine();
        doReturn(method).when(requestLineMock).getMethod();
        doReturn(uri).when(requestLineMock).getUri();
        doReturn(HTTP_1_1).when(requestLineMock).getProtocolVersion();

        doReturn(statusLineMock).when(responseMock).getStatusLine();
        doReturn(responseCode).when(statusLineMock).getStatusCode();
    }

    @After
    public void afterMethod() {
        resetTracing();
    }

    private void resetTracing() {
        MDC.clear();
        Tracer.getInstance().unregisterFromThread();
        removeSpanRecorderLifecycleListener();
    }

    private void removeSpanRecorderLifecycleListener() {
        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            if (listener instanceof SpanRecorder) {
                Tracer.getInstance().removeSpanLifecycleListener(listener);
            }
        }
    }

    @Test
    public void default_constructor_sets_fields_as_expected() {
        // when
        WingtipsHttpClientBuilder impl = new WingtipsHttpClientBuilder();

        // then
        assertThat(impl.surroundCallsWithSubspan).isTrue();
        assertThat(impl.tagAndNamingStrategy).isSameAs(ZipkinHttpTagStrategy.getDefaultInstance());
        assertThat(impl.tagAndNamingAdapter).isSameAs(ApacheHttpClientTagAdapter.getDefaultInstance());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void single_arg_constructor_sets_fields_as_expected(boolean argValue) {
        // when
        WingtipsHttpClientBuilder impl = new WingtipsHttpClientBuilder(argValue);

        // then
        assertThat(impl.surroundCallsWithSubspan).isEqualTo(argValue);
        assertThat(impl.tagAndNamingStrategy).isSameAs(ZipkinHttpTagStrategy.getDefaultInstance());
        assertThat(impl.tagAndNamingAdapter).isSameAs(ApacheHttpClientTagAdapter.getDefaultInstance());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void constructor_with_tag_and_span_naming_args_sets_fields_as_expected(boolean subspanArgValue) {
        // when
        WingtipsHttpClientBuilder impl = new WingtipsHttpClientBuilder(
            subspanArgValue, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        // then
        assertThat(impl.surroundCallsWithSubspan).isEqualTo(subspanArgValue);
        assertThat(impl.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(impl.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
    }

    private enum NullConstructorArgsScenario {
        NULL_STRATEGY_ARG(
            null,
            mock(HttpTagAndSpanNamingAdapter.class),
            "tagAndNamingStrategy cannot be null - if you really want no strategy, use NoOpHttpTagStrategy"
        ),
        NULL_ADAPTER_ARG(
            mock(HttpTagAndSpanNamingStrategy.class),
            null,
            "tagAndNamingAdapter cannot be null - if you really want no adapter, use NoOpHttpTagAdapter"
        );

        public final HttpTagAndSpanNamingStrategy<HttpRequest, HttpResponse> strategy;
        public final HttpTagAndSpanNamingAdapter<HttpRequest, HttpResponse> adapter;
        public final String expectedExceptionMessage;

        NullConstructorArgsScenario(
            HttpTagAndSpanNamingStrategy<HttpRequest, HttpResponse> strategy,
            HttpTagAndSpanNamingAdapter<HttpRequest, HttpResponse> adapter,
            String expectedExceptionMessage
        ) {
            this.strategy = strategy;
            this.adapter = adapter;
            this.expectedExceptionMessage = expectedExceptionMessage;
        }
    }

    @DataProvider(value = {
        "NULL_STRATEGY_ARG",
        "NULL_ADAPTER_ARG"
    })
    @Test
    public void constructor_with_tag_and_span_naming_args_throws_IllegalArgumentException_if_passed_null_args(
        NullConstructorArgsScenario scenario
    ) {
        // when
        Throwable ex = catchThrowable(
            () -> new WingtipsHttpClientBuilder(true, scenario.strategy, scenario.adapter)
        );

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(scenario.expectedExceptionMessage);
    }

    @Test
    public void zero_arg_create_factory_method_sets_fields_as_expected() {
        // when
        WingtipsHttpClientBuilder impl = WingtipsHttpClientBuilder.create();

        // then
        assertThat(impl.surroundCallsWithSubspan).isTrue();
        assertThat(impl.tagAndNamingStrategy).isSameAs(ZipkinHttpTagStrategy.getDefaultInstance());
        assertThat(impl.tagAndNamingAdapter).isSameAs(ApacheHttpClientTagAdapter.getDefaultInstance());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void single_arg_create_factory_method_sets_fields_as_expected(boolean argValue) {
        // when
        WingtipsHttpClientBuilder impl = WingtipsHttpClientBuilder.create(argValue);

        // then
        assertThat(impl.surroundCallsWithSubspan).isEqualTo(argValue);
        assertThat(impl.tagAndNamingStrategy).isSameAs(ZipkinHttpTagStrategy.getDefaultInstance());
        assertThat(impl.tagAndNamingAdapter).isSameAs(ApacheHttpClientTagAdapter.getDefaultInstance());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void create_factory_method_with_tag_and_span_naming_args_sets_fields_as_expected(boolean subspanArgValue) {
        // when
        WingtipsHttpClientBuilder impl = WingtipsHttpClientBuilder.create(
            subspanArgValue, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        // then
        assertThat(impl.surroundCallsWithSubspan).isEqualTo(subspanArgValue);
        assertThat(impl.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(impl.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);
    }

    @DataProvider(value = {
        "NULL_STRATEGY_ARG",
        "NULL_ADAPTER_ARG"
    })
    @Test
    public void create_factory_method_with_tag_and_span_naming_args_throws_IllegalArgumentException_if_passed_null_args(
        NullConstructorArgsScenario scenario
    ) {
        // when
        Throwable ex = catchThrowable(
            () -> WingtipsHttpClientBuilder.create(true, scenario.strategy, scenario.adapter)
        );

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(scenario.expectedExceptionMessage);
    }

    @DataProvider(value = {
        "true   |   true    |   true",
        "false  |   true    |   true",
        "true   |   false   |   true",
        "false  |   false   |   true",
        "true   |   true    |   false",
        "false  |   true    |   false",
        "true   |   false   |   false",
        "false  |   false   |   false"
    }, splitBy = "\\|")
    @Test
    public void decorateProtocolExec_works_as_expected(
        boolean subspanOptionOn, boolean parentSpanExists, boolean throwExceptionInInnerChain
    ) throws IOException, HttpException {
        // given
        builder = WingtipsHttpClientBuilder.create(subspanOptionOn, tagAndNamingStrategy, tagAndNamingAdapterMock);
        RuntimeException exceptionToThrowInInnerChain = (throwExceptionInInnerChain)
                                                        ? new RuntimeException("kaboom")
                                                        : null;
        SpanCapturingClientExecChain origCec = spy(new SpanCapturingClientExecChain(exceptionToThrowInInnerChain));
        Span parentSpan = null;
        if (parentSpanExists) {
            parentSpan = Tracer.getInstance().startRequestWithRootSpan("someParentSpan");
        }

        // when
        ClientExecChain result = builder.decorateProtocolExec(origCec);

        // then
        verifyDecoratedClientExecChainPerformsTracingLogic(
            result, origCec, parentSpan, subspanOptionOn, exceptionToThrowInInnerChain
        );
    }

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void decorateProtocolExec_uses_subspan_option_value_at_time_of_creation_not_time_of_execution(
        boolean subspanOptionOn
    ) throws IOException, HttpException {
        // given
        builder = WingtipsHttpClientBuilder.create(subspanOptionOn, tagAndNamingStrategy, tagAndNamingAdapterMock);
        Span parentSpan = Tracer.getInstance().startRequestWithRootSpan("someParentSpan");

        SpanCapturingClientExecChain origCec = spy(new SpanCapturingClientExecChain());

        // when
        ClientExecChain result = builder.decorateProtocolExec(origCec);
        // Set builder's subspan option to the opposite of what it was when the ClientExecChain was decorated.
        builder.setSurroundCallsWithSubspan(!subspanOptionOn);

        // then
        // Even though the *builder's* subspan option has been flipped, the ClientExecChain should still execute with
        //      the subspan option value from when the ClientExecChain was originally decorated.
        verifyDecoratedClientExecChainPerformsTracingLogic(
            result, origCec, parentSpan, subspanOptionOn, null
        );
    }

    private void verifyDecoratedClientExecChainPerformsTracingLogic(
        ClientExecChain decoratedCec, SpanCapturingClientExecChain origCecSpy, Span parentSpan,
        boolean expectSubspan, Throwable expectedError
    ) throws IOException, HttpException {
        // given
        HttpRoute httpRoute = new HttpRoute(new HttpHost("localhost"));
        HttpRequestWrapper requestWrapperSpy = spy(HttpRequestWrapper.wrap(requestMock));
        HttpClientContext httpClientContextMock = mock(HttpClientContext.class);
        HttpExecutionAware httpExecutionAwareMock = mock(HttpExecutionAware.class);

        assertThat(origCecSpy.capturedSpan).isNull();
        assertThat(spanRecorder.completedSpans).isEmpty();

        // when
        CloseableHttpResponse result = null;
        Throwable exFromChain = null;
        try {
            result = decoratedCec.execute(
                httpRoute, requestWrapperSpy, httpClientContextMock, httpExecutionAwareMock
            );
        }
        catch (Throwable ex) {
            exFromChain = ex;
        }

        // then
        verify(origCecSpy).execute(httpRoute, requestWrapperSpy, httpClientContextMock, httpExecutionAwareMock);
        if (origCecSpy.exceptionToThrow == null) {
            assertThat(result).isSameAs(origCecSpy.response);
        }
        else {
            assertThat(exFromChain).isSameAs(origCecSpy.exceptionToThrow);
        }

        // The only time the capturedSpan should be null is if expectSubspan is false and parentSpan is null, and then
        //      no tracing propagation headers should have been set.
        //      Otherwise, the tracing propagation headers should match capturedSpan.
        if (origCecSpy.capturedSpan == null) {
            assertThat(expectSubspan).isFalse();
            assertThat(parentSpan).isNull();
            verify(requestWrapperSpy, never()).setHeader(anyString(), anyString());
        }
        else {
            verify(requestWrapperSpy).setHeader(TRACE_ID, origCecSpy.capturedSpan.getTraceId());
            verify(requestWrapperSpy).setHeader(SPAN_ID, origCecSpy.capturedSpan.getSpanId());
            verify(requestWrapperSpy).setHeader(
                TRACE_SAMPLED, convertSampleableBooleanToExpectedB3Value(origCecSpy.capturedSpan.isSampleable())
            );
            if (origCecSpy.capturedSpan.getParentSpanId() == null) {
                verify(requestWrapperSpy, never()).setHeader(eq(PARENT_SPAN_ID), anyString());
            }
            else {
                verify(requestWrapperSpy).setHeader(PARENT_SPAN_ID, origCecSpy.capturedSpan.getParentSpanId());
            }
        }

        // If we have a subspan, then it should have been completed. Otherwise, no spans should have been completed.
        //      Also, if we have a subspan, then request and response tagging should have been done.
        if (expectSubspan) {
            assertThat(spanRecorder.completedSpans).containsExactly(origCecSpy.capturedSpan);

            // Verify the request tags were set
            assertThat(strategyInitialSpanNameMethodCalled.get()).isTrue();
            assertThat(strategyInitialSpanNameArgs.get()).isNotNull();
            strategyInitialSpanNameArgs.get().verifyArgs(requestWrapperSpy, tagAndNamingAdapterMock);

            assertThat(strategyRequestTaggingMethodCalled.get()).isTrue();
            assertThat(strategyRequestTaggingArgs.get()).isNotNull();
            strategyRequestTaggingArgs.get().verifyArgs(
                origCecSpy.capturedSpan, requestWrapperSpy, tagAndNamingAdapterMock
            );

            // Verify the response tags were set
            assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
            assertThat(strategyResponseTaggingArgs.get()).isNotNull();
            strategyResponseTaggingArgs.get().verifyArgs(
                origCecSpy.capturedSpan, requestWrapperSpy, result, expectedError, tagAndNamingAdapterMock
            );
        }
        else {
            assertThat(spanRecorder.completedSpans).isEmpty();

            // None of the tag/naming stuff should have been called since there was no subspan.
            assertThat(strategyInitialSpanNameMethodCalled.get()).isFalse();
            assertThat(strategyInitialSpanNameArgs.get()).isNull();

            assertThat(strategyRequestTaggingMethodCalled.get()).isFalse();
            assertThat(strategyRequestTaggingArgs.get()).isNull();

            assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isFalse();
            assertThat(strategyResponseTaggingArgs.get()).isNull();
        }
    }

    private static class SpanCapturingClientExecChain implements ClientExecChain {

        public final CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        public final StatusLine statusLineMock = mock(StatusLine.class);
        
        public final RuntimeException exceptionToThrow;
        public Span capturedSpan;

        SpanCapturingClientExecChain() {
            this(null);
        }

        SpanCapturingClientExecChain(RuntimeException exceptionToThrow) {
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public CloseableHttpResponse execute(HttpRoute route, HttpRequestWrapper request,
                                             HttpClientContext clientContext,
                                             HttpExecutionAware execAware) throws IOException, HttpException {
            capturedSpan = Tracer.getInstance().getCurrentSpan();
            
            doReturn(statusLineMock).when(response).getStatusLine();
            
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            // Return a graceful 500 from the response
            doReturn(500).when(statusLineMock).getStatusCode();
            return response;
        }
    }

    private static class SpanRecorder implements SpanLifecycleListener {

        public final List<Span> completedSpans = new ArrayList<>();

        @Override
        public void spanStarted(Span span) { }

        @Override
        public void spanSampled(Span span) { }

        @Override
        public void spanCompleted(Span span) {
            completedSpans.add(span);
        }
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void surroundCallsWithSubspan_getter_and_setter_work_as_expected(boolean value) {
        // when
        WingtipsHttpClientBuilder fluentResponse = builder.setSurroundCallsWithSubspan(value);

        // then
        assertThat(fluentResponse).isSameAs(builder);
        assertThat(builder.isSurroundCallsWithSubspan()).isEqualTo(value);
    }

    @DataProvider(value = {
        "spanNameFromStrategy   |   someHttpMethod  |   spanNameFromStrategy",
        "null                   |   someHttpMethod  |   apachehttpclient_downstream_call-someHttpMethod",
        "                       |   someHttpMethod  |   apachehttpclient_downstream_call-someHttpMethod",
        "[whitespace]           |   someHttpMethod  |   apachehttpclient_downstream_call-someHttpMethod",
        "null                   |   null            |   apachehttpclient_downstream_call-UNKNOWN_HTTP_METHOD",
        "null                   |                   |   apachehttpclient_downstream_call-UNKNOWN_HTTP_METHOD",
        "null                   |   [whitespace]    |   apachehttpclient_downstream_call-UNKNOWN_HTTP_METHOD",
    }, splitBy = "\\|")
    @Test
    public void getSubspanSpanName_works_as_expected(String strategyResult, String httpMethod, String expectedResult) {
        // given
        if ("[whitespace]".equals(strategyResult)) {
            strategyResult = "  \n\r\t  ";
        }

        if ("[whitespace]".equals(httpMethod)) {
            httpMethod = "  \n\r\t  ";
        }

        initialSpanNameFromStrategy.set(strategyResult);
        doReturn(httpMethod).when(requestLineMock).getMethod();

        // when
        String result = builder.getSubspanSpanName(requestMock, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }
}