package com.nike.wingtips.apache.httpclient;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.apache.httpclient.testutils.ArgCapturingHttpTagAndSpanNamingStrategy;
import com.nike.wingtips.apache.httpclient.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.InitialSpanNameArgs;
import com.nike.wingtips.apache.httpclient.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.RequestTaggingArgs;
import com.nike.wingtips.apache.httpclient.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.ResponseTaggingArgs;
import com.nike.wingtips.apache.httpclient.tag.ApacheHttpClientTagAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.nike.wingtips.TraceHeaders.PARENT_SPAN_ID;
import static com.nike.wingtips.TraceHeaders.SPAN_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_ID;
import static com.nike.wingtips.TraceHeaders.TRACE_SAMPLED;
import static com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor.DEFAULT_REQUEST_IMPL;
import static com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor.DEFAULT_RESPONSE_IMPL;
import static com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor.SPAN_TO_CLOSE_HTTP_CONTEXT_ATTR_KEY;
import static com.nike.wingtips.apache.httpclient.WingtipsApacheHttpClientInterceptor.addTracingInterceptors;
import static com.nike.wingtips.http.HttpRequestTracingUtils.convertSampleableBooleanToExpectedB3Value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link WingtipsApacheHttpClientInterceptor}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsApacheHttpClientInterceptorTest {

    private WingtipsApacheHttpClientInterceptor interceptor;

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

    @Before
    public void beforeMethod() {
        resetTracing();

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

        interceptor = new WingtipsApacheHttpClientInterceptor(true, tagAndNamingStrategy, tagAndNamingAdapterMock);

        requestMock = mock(HttpRequest.class);
        responseMock = mock(HttpResponse.class);
        httpContext = new BasicHttpContext();
        requestLineMock = mock(RequestLine.class);
        statusLineMock = mock(StatusLine.class);

        method = "GET";
        uri = "http://localhost:4242/foo/bar";

        responseCode = 200;

        doReturn(requestLineMock).when(requestMock).getRequestLine();
        doReturn(method).when(requestLineMock).getMethod();
        doReturn(uri).when(requestLineMock).getUri();

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
    }

    @Test
    public void default_constructor_sets_fields_as_expected() {
        // when
        WingtipsApacheHttpClientInterceptor impl = new WingtipsApacheHttpClientInterceptor();

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
        WingtipsApacheHttpClientInterceptor impl = new WingtipsApacheHttpClientInterceptor(argValue);

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
        WingtipsApacheHttpClientInterceptor impl = new WingtipsApacheHttpClientInterceptor(
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
            () -> new WingtipsApacheHttpClientInterceptor(true, scenario.strategy, scenario.adapter)
        );

        // then
        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(scenario.expectedExceptionMessage);
    }

    @DataProvider(value = {
        "true   |   true  | 200",
        "true   |   true  | 500",
        "false  |   true  | 200",
        "false  |   true  | 500",
        "true   |   false | 200",
        "true   |   false | 500",
        "false  |   false | 200",
        "false  |   false | 500"
    }, splitBy = "\\|")
    @Test
    public void process_request_works_as_expected(
        boolean subspanOptionOn, boolean parentSpanExists, int responseCode
    ) {
        // when
        interceptor = new WingtipsApacheHttpClientInterceptor(
            subspanOptionOn, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        // then
        execute_and_validate_request_works_as_expected(
            interceptor, subspanOptionOn, parentSpanExists, responseCode
        );
    }

    public void execute_and_validate_request_works_as_expected(
        WingtipsApacheHttpClientInterceptor interceptor,
        boolean subspanOptionOn,
        boolean parentSpanExists,
        int responseCode
    ) {
        Span parentSpan = null;
        if (parentSpanExists) {
            parentSpan = Tracer.getInstance().startRequestWithRootSpan("someParentSpan");
        }
        this.responseCode = responseCode;

        // when
        interceptor.process(requestMock, httpContext);

        // then
        Span spanSetOnHttpContext = null;
        if (subspanOptionOn) {
            spanSetOnHttpContext = (Span) httpContext.getAttribute(SPAN_TO_CLOSE_HTTP_CONTEXT_ATTR_KEY);
            assertThat(spanSetOnHttpContext).isNotNull();

            if (parentSpanExists) {
                assertThat(spanSetOnHttpContext.getTraceId()).isEqualTo(parentSpan.getTraceId());
                assertThat(spanSetOnHttpContext.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
            }

            assertThat(spanSetOnHttpContext.getSpanPurpose()).isEqualTo(SpanPurpose.CLIENT);
            assertThat(spanSetOnHttpContext.getSpanName())
                .isEqualTo(interceptor.getSubspanSpanName(
                    requestMock, interceptor.tagAndNamingStrategy, interceptor.tagAndNamingAdapter
                ));

            assertThat(strategyInitialSpanNameMethodCalled.get()).isTrue();
            assertThat(strategyInitialSpanNameArgs.get()).isNotNull();
            strategyInitialSpanNameArgs.get().verifyArgs(requestMock, interceptor.tagAndNamingAdapter);

            assertThat(strategyRequestTaggingMethodCalled.get()).isTrue();
            assertThat(strategyRequestTaggingArgs.get()).isNotNull();
            strategyRequestTaggingArgs.get().verifyArgs(
                spanSetOnHttpContext, requestMock, interceptor.tagAndNamingAdapter
            );
        }
        else {
            assertThat(strategyInitialSpanNameMethodCalled.get()).isFalse();
            assertThat(strategyInitialSpanNameArgs.get()).isNull();

            assertThat(strategyRequestTaggingMethodCalled.get()).isFalse();
            assertThat(strategyRequestTaggingArgs.get()).isNull();
        }

        Span expectedSpanToPropagate = (subspanOptionOn)
                                       ? spanSetOnHttpContext
                                       : (parentSpanExists) ? parentSpan : null;

        if (expectedSpanToPropagate == null) {
            verify(requestMock, never()).setHeader(anyString(), anyString());
        }
        else {
            verify(requestMock).setHeader(TRACE_ID, expectedSpanToPropagate.getTraceId());
            verify(requestMock).setHeader(SPAN_ID, expectedSpanToPropagate.getSpanId());
            verify(requestMock).setHeader(
                TRACE_SAMPLED, convertSampleableBooleanToExpectedB3Value(expectedSpanToPropagate.isSampleable())
            );
            if (expectedSpanToPropagate.getParentSpanId() == null) {
                verify(requestMock, never()).setHeader(eq(PARENT_SPAN_ID), anyString());
            }
            else {
                verify(requestMock).setHeader(PARENT_SPAN_ID, expectedSpanToPropagate.getParentSpanId());
            }
        }
    }

    private enum ProcessResponseScenario {
        SUBSPAN_IS_NOT_AVAILABLE_IN_HTTP_CONTEXT(
            () -> null, () -> null
        ),
        SUBSPAN_IS_AVAILABLE_IN_HTTP_CONTEXT_BUT_REQUEST_IS_NOT(
            () -> mock(Span.class), () -> null
        ),
        SUBSPAN_AND_REQUEST_ARE_AVAILABLE_IN_HTTP_CONTEXT(
            () -> mock(Span.class), () -> mock(HttpRequest.class)
        ),
        SUBSPAN_AND_REQUEST_ARE_AVAILABLE_IN_HTTP_CONTEXT_BUT_REQUEST_IS_NOT_HTTP_REQUEST(
            () -> mock(Span.class), () -> mock(Object.class)
        );

        public final Supplier<Span> spanMockSupplier;
        public final Supplier<Object> requestObjSupplier;

        ProcessResponseScenario(Supplier<Span> spanMockSupplier, Supplier<Object> requestObjSupplier) {
            this.spanMockSupplier = spanMockSupplier;
            this.requestObjSupplier = requestObjSupplier;
        }
    }

    @DataProvider(value = {
        "SUBSPAN_IS_NOT_AVAILABLE_IN_HTTP_CONTEXT",
        "SUBSPAN_IS_AVAILABLE_IN_HTTP_CONTEXT_BUT_REQUEST_IS_NOT",
        "SUBSPAN_AND_REQUEST_ARE_AVAILABLE_IN_HTTP_CONTEXT",
        "SUBSPAN_AND_REQUEST_ARE_AVAILABLE_IN_HTTP_CONTEXT_BUT_REQUEST_IS_NOT_HTTP_REQUEST"
    })
    @Test
    public void process_response_works_as_expected(
        ProcessResponseScenario scenario
    ) {

        // given
        Span spanMock = scenario.spanMockSupplier.get();
        if (spanMock != null) {
            httpContext.setAttribute(SPAN_TO_CLOSE_HTTP_CONTEXT_ATTR_KEY, spanMock);
        }

        Object requestObj = scenario.requestObjSupplier.get();
        if (requestObj != null) {
            httpContext.setAttribute(HttpCoreContext.HTTP_REQUEST, requestObj);
        }

        HttpRequest expectedRequestObjForTagging = (requestObj instanceof HttpRequest)
                                                   ? (HttpRequest) requestObj
                                                   : null;

        // when
        interceptor.process(responseMock, httpContext);

        // then
        if (spanMock != null) {
            assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
            assertThat(strategyResponseTaggingArgs.get()).isNotNull();
            strategyResponseTaggingArgs.get().verifyArgs(
                spanMock, expectedRequestObjForTagging, responseMock, null, interceptor.tagAndNamingAdapter
            );

            verify(spanMock).close();
        }
        else {
            assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isFalse();
            assertThat(strategyResponseTaggingArgs.get()).isNull();
        }
    }

    @Test
    public void process_response_closes_span_no_matter_what() {

        // given
        Span spanMock = mock(Span.class);
        httpContext = spy(httpContext);
        httpContext.setAttribute(SPAN_TO_CLOSE_HTTP_CONTEXT_ATTR_KEY, spanMock);
        RuntimeException expectedEx = new RuntimeException("boom");
        doThrow(expectedEx).when(httpContext).getAttribute(HttpCoreContext.HTTP_REQUEST);

        // when
        Throwable ex = catchThrowable(() -> interceptor.process(responseMock, httpContext));

        // then
        assertThat(ex).isSameAs(expectedEx);
        verify(httpContext).getAttribute(HttpCoreContext.HTTP_REQUEST);
        verify(spanMock).close();
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
        String result = interceptor.getSubspanSpanName(requestMock, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void addTracingInterceptors_single_arg_works_as_expected() {
        // given
        HttpClientBuilder builder = HttpClientBuilder.create();

        // when
        addTracingInterceptors(builder);

        // then
        HttpClientBuilderInterceptors builderInterceptors = new HttpClientBuilderInterceptors(builder);
        assertThat(builderInterceptors.firstRequestInterceptors).containsExactly(DEFAULT_REQUEST_IMPL);
        assertThat(builderInterceptors.lastRequestInterceptors).isNullOrEmpty();
        assertThat(builderInterceptors.firstResponseInterceptors).isNullOrEmpty();
        assertThat(builderInterceptors.lastResponseInterceptors).containsExactly(DEFAULT_RESPONSE_IMPL);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void addTracingInterceptors_double_arg_works_as_expected(boolean subspanOptionOn) {
        // given
        HttpClientBuilder builder = HttpClientBuilder.create();

        // when
        addTracingInterceptors(builder, subspanOptionOn);

        // then
        HttpClientBuilderInterceptors builderInterceptors = new HttpClientBuilderInterceptors(builder);

        assertThat(builderInterceptors.firstRequestInterceptors).hasSize(1);
        assertThat(builderInterceptors.lastResponseInterceptors).hasSize(1);

        HttpRequestInterceptor requestInterceptor = builderInterceptors.firstRequestInterceptors.get(0);
        HttpResponseInterceptor responseInterceptor = builderInterceptors.lastResponseInterceptors.get(0);
        assertThat(requestInterceptor).isInstanceOf(WingtipsApacheHttpClientInterceptor.class);
        assertThat(responseInterceptor).isInstanceOf(WingtipsApacheHttpClientInterceptor.class);

        assertThat(((WingtipsApacheHttpClientInterceptor) requestInterceptor).surroundCallsWithSubspan)
            .isEqualTo(subspanOptionOn);
        assertThat(((WingtipsApacheHttpClientInterceptor) responseInterceptor).surroundCallsWithSubspan)
            .isEqualTo(subspanOptionOn);

        assertThat(builderInterceptors.lastRequestInterceptors).isNullOrEmpty();
        assertThat(builderInterceptors.firstResponseInterceptors).isNullOrEmpty();

        if (subspanOptionOn) {
            assertThat(builderInterceptors.firstRequestInterceptors).containsExactly(DEFAULT_REQUEST_IMPL);
            assertThat(builderInterceptors.lastResponseInterceptors).containsExactly(DEFAULT_RESPONSE_IMPL);
        }
    }

    private static class HttpClientBuilderInterceptors {

        public final List<HttpRequestInterceptor> firstRequestInterceptors;
        public final List<HttpRequestInterceptor> lastRequestInterceptors;
        public final List<HttpResponseInterceptor> firstResponseInterceptors;
        public final List<HttpResponseInterceptor> lastResponseInterceptors;

        public HttpClientBuilderInterceptors(HttpClientBuilder builder) {
            this.firstRequestInterceptors =
                (List<HttpRequestInterceptor>) Whitebox.getInternalState(builder, "requestFirst");
            this.lastRequestInterceptors =
                (List<HttpRequestInterceptor>) Whitebox.getInternalState(builder, "requestLast");
            this.firstResponseInterceptors =
                (List<HttpResponseInterceptor>) Whitebox.getInternalState(builder, "responseFirst");
            this.lastResponseInterceptors =
                (List<HttpResponseInterceptor>) Whitebox.getInternalState(builder, "responseLast");
        }
    }
}