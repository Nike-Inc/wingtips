package com.nike.wingtips.servlet;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.tag.ServletRequestTagAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.tags.NoOpHttpTagStrategy;
import com.nike.wingtips.tags.OpenTracingHttpTagStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.InitialSpanNameArgs;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.RequestTaggingArgs;
import com.nike.wingtips.testutils.ArgCapturingHttpTagAndSpanNamingStrategy.ResponseTaggingArgs;
import com.nike.wingtips.util.TracingState;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.nike.wingtips.servlet.RequestTracingFilter.TAG_AND_SPAN_NAMING_ADAPTER_INIT_PARAM_NAME;
import static com.nike.wingtips.servlet.RequestTracingFilter.TAG_AND_SPAN_NAMING_STRATEGY_INIT_PARAM_NAME;
import static com.nike.wingtips.servlet.ServletRuntime.ASYNC_LISTENER_CLASSNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link RequestTracingFilter}
 */
@RunWith(DataProviderRunner.class)
public class RequestTracingFilterTest {

    private HttpServletRequest requestMock;
    private HttpServletResponse responseMock;
    private FilterChain filterChainMock;
    private SpanCapturingFilterChain spanCapturingFilterChain;
    @SuppressWarnings("FieldCanBeLocal")
    private AsyncContext listenerCapturingAsyncContext;
    private List<AsyncListener> capturedAsyncListeners;
    private FilterConfig filterConfigMock;
    private ServletRuntime servletRuntimeMock;
    private HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> tagAndNamingStrategy;
    private HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> tagAndNamingAdapterMock;

    private AtomicReference<String> initialSpanNameFromStrategy;
    private AtomicBoolean strategyInitialSpanNameMethodCalled;
    private AtomicBoolean strategyRequestTaggingMethodCalled;
    private AtomicBoolean strategyResponseTaggingAndFinalSpanNameMethodCalled;
    private AtomicReference<InitialSpanNameArgs> strategyInitialSpanNameArgs;
    private AtomicReference<RequestTaggingArgs> strategyRequestTaggingArgs;
    private AtomicReference<ResponseTaggingArgs> strategyResponseTaggingArgs;

    private static final String USER_ID_HEADER_KEY = "userId";
    private static final String ALT_USER_ID_HEADER_KEY = "altUserId";
    private static final List<String> USER_ID_HEADER_KEYS = Arrays.asList(USER_ID_HEADER_KEY, ALT_USER_ID_HEADER_KEY);
    private static final String USER_ID_HEADER_KEYS_INIT_PARAM_VALUE_STRING =
        USER_ID_HEADER_KEYS.toString().replace("[", "").replace("]", "");

    private RequestTracingFilter getBasicFilter() {
        RequestTracingFilter filter = new RequestTracingFilter();

        try {
            filter.init(filterConfigMock);
            filter.tagAndNamingStrategy = tagAndNamingStrategy;
            filter.tagAndNamingAdapter = tagAndNamingAdapterMock;
        }
        catch (ServletException e) {
            throw new RuntimeException(e);
        }

        return filter;
    }

    private void setupAsyncContextWorkflow() {
        listenerCapturingAsyncContext = mock(AsyncContext.class);
        capturedAsyncListeners = new ArrayList<>();

        doReturn(listenerCapturingAsyncContext).when(requestMock).getAsyncContext();
        doReturn(true).when(requestMock).isAsyncStarted();

        doAnswer(invocation -> {
            capturedAsyncListeners.add((AsyncListener) invocation.getArguments()[0]);
            return null;
        }).when(listenerCapturingAsyncContext).addListener(
            any(AsyncListener.class), any(ServletRequest.class), any(ServletResponse.class)
        );
    }

    @Before
    public void setupMethod() {
        requestMock = mock(HttpServletRequest.class);
        responseMock = mock(HttpServletResponse.class);
        filterChainMock = mock(FilterChain.class);
        spanCapturingFilterChain = new SpanCapturingFilterChain();

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

        filterConfigMock = mock(FilterConfig.class);
        doReturn(USER_ID_HEADER_KEYS_INIT_PARAM_VALUE_STRING)
            .when(filterConfigMock)
            .getInitParameter(RequestTracingFilter.USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME);

        servletRuntimeMock = mock(ServletRuntime.class);

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

    // VERIFY filter init,
    //               initializeUserIdHeaderKeys, getUserIdHeaderKeys,
    //               initializeTagAndNamingStrategy, getTagStrategyFromName,
    //               initializeTagAndNamingAdapter, getTagAdapterFromName,
    //               all the get*Strategy() methods,
    //               getDefaultTagAdapter
    //               and destroy =======================

    @Test
    public void init_method_delegates_to_helpers_to_initialize_fields() throws ServletException {
        // given
        RequestTracingFilter filterSpy = spy(new RequestTracingFilter());

        List<String> expectedUserIdHeaderKeys = Arrays.asList(UUID.randomUUID().toString(),
                                                              UUID.randomUUID().toString());

        doReturn(expectedUserIdHeaderKeys).when(filterSpy).initializeUserIdHeaderKeys(any(FilterConfig.class));
        doReturn(tagAndNamingStrategy).when(filterSpy).initializeTagAndNamingStrategy(any(FilterConfig.class));
        doReturn(tagAndNamingAdapterMock).when(filterSpy).initializeTagAndNamingAdapter(any(FilterConfig.class));

        // when
        filterSpy.init(filterConfigMock);

        // then
        assertThat(filterSpy.userIdHeaderKeysFromInitParam).isSameAs(expectedUserIdHeaderKeys);

        assertThat(filterSpy.tagAndNamingStrategy).isSameAs(tagAndNamingStrategy);
        assertThat(filterSpy.tagAndNamingAdapter).isSameAs(tagAndNamingAdapterMock);

        verify(filterSpy).init(filterConfigMock);
        verify(filterSpy).initializeUserIdHeaderKeys(filterConfigMock);
        verify(filterSpy).initializeTagAndNamingStrategy(filterConfigMock);
        verify(filterSpy).initializeTagAndNamingAdapter(filterConfigMock);
        verifyNoMoreInteractions(filterSpy);
    }

    @DataProvider
    public static Object[][] userIdHeaderKeysInitParamDataProvider() {

        return new Object[][]{
            {null, null},
            {"", Collections.emptyList()},
            {" \t \n  ", Collections.emptyList()},
            {"asdf", Collections.singletonList("asdf")},
            {" , \n\t, asdf , \t\n  ", Collections.singletonList("asdf")},
            {"ASDF,QWER", Arrays.asList("ASDF", "QWER")},
            {"ASDF, QWER, ZXCV", Arrays.asList("ASDF", "QWER", "ZXCV")}
        };
    }
    
    @Test
    @UseDataProvider("userIdHeaderKeysInitParamDataProvider")
    public void initializeUserIdHeaderKeys_gets_user_id_header_key_list_from_init_params(
        String userIdHeaderKeysInitParamValue,
        List<String> expectedUserIdHeaderKeysList
    ) {
        // given
        RequestTracingFilter filter = new RequestTracingFilter();
        doReturn(userIdHeaderKeysInitParamValue)
            .when(filterConfigMock)
            .getInitParameter(RequestTracingFilter.USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME);

        // when
        List<String> actualUserIdHeaderKeysList = filter.initializeUserIdHeaderKeys(filterConfigMock);

        // then
        assertThat(actualUserIdHeaderKeysList).isEqualTo(expectedUserIdHeaderKeysList);
        if (actualUserIdHeaderKeysList != null) {
            Exception caughtEx = null;
            try {
                actualUserIdHeaderKeysList.add("foo");
            }
            catch (Exception ex) {
                caughtEx = ex;
            }
            assertThat(caughtEx).isNotNull();
            assertThat(caughtEx).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    public void getUserIdHeaderKeys_returns_userIdHeaderKeysFromInitParam_field() {
        // given
        RequestTracingFilter filter = new RequestTracingFilter();
        List<String> expectedUserIdHeaderKeys = Arrays.asList(UUID.randomUUID().toString(),
                                                              UUID.randomUUID().toString());
        filter.userIdHeaderKeysFromInitParam = expectedUserIdHeaderKeys;

        // when
        List<String> result = filter.getUserIdHeaderKeys();

        // then
        assertThat(result).isSameAs(expectedUserIdHeaderKeys);
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void initializeTagAndNamingStrategy_delegates_to_getTagStrategyFromName_and_returns_default_if_exception_is_thrown(
        boolean throwException
    ) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        // given
        RequestTracingFilter filterSpy = spy(new RequestTracingFilter());

        String tagStrategyFromFilterConfig = UUID.randomUUID().toString();
        doReturn(tagStrategyFromFilterConfig)
            .when(filterConfigMock).getInitParameter(TAG_AND_SPAN_NAMING_STRATEGY_INIT_PARAM_NAME);
        
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> strategyFromDesiredMethodMock =
            mock(HttpTagAndSpanNamingStrategy.class);
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> fallbackDefaultStrategyMock =
            mock(HttpTagAndSpanNamingStrategy.class);

        if (throwException) {
            doThrow(new RuntimeException("intentional exception")).when(filterSpy).getTagStrategyFromName(anyString());
        }
        else {
            doReturn(strategyFromDesiredMethodMock).when(filterSpy).getTagStrategyFromName(anyString());
        }

        doReturn(fallbackDefaultStrategyMock).when(filterSpy).getDefaultTagStrategy();

        // when
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> result =
            filterSpy.initializeTagAndNamingStrategy(filterConfigMock);

        // then
        verify(filterSpy).getTagStrategyFromName(tagStrategyFromFilterConfig);

        if (throwException) {
            assertThat(result).isSameAs(fallbackDefaultStrategyMock);
            verify(filterSpy).getDefaultTagStrategy();
        }
        else {
            assertThat(result).isSameAs(strategyFromDesiredMethodMock);
            verify(filterSpy, never()).getDefaultTagStrategy();
        }
    }

    @DataProvider(value = {
        "ZIPKIN",
        "Zipkin",
        "opentracing",
        "OpenTracing",
        "NONE",
        "NoNe",
        "NOOP",
        "null",
        "",
        " ",
        " \t\r\n  "
    })
    @Test
    public void getTagStrategyFromName_returns_expected_strategies_for_known_short_names(
        String knownStrategyShortName
    ) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        // given
        RequestTracingFilter filterSpy = spy(new RequestTracingFilter());
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> zipkinStrategyMock =
            mock(HttpTagAndSpanNamingStrategy.class);
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> openTracingStrategyMock =
            mock(HttpTagAndSpanNamingStrategy.class);
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> noOpStrategyMock =
            mock(HttpTagAndSpanNamingStrategy.class);

        doReturn(zipkinStrategyMock).when(filterSpy).getZipkinHttpTagStrategy();
        doReturn(openTracingStrategyMock).when(filterSpy).getOpenTracingHttpTagStrategy();
        doReturn(noOpStrategyMock).when(filterSpy).getNoOpTagStrategy();

        // when
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse>
            result = filterSpy.getTagStrategyFromName(knownStrategyShortName);

        // then

        // Default is Zipkin
        if (StringUtils.isBlank(knownStrategyShortName) || "zipkin".equalsIgnoreCase(knownStrategyShortName)) {
            assertThat(result).isSameAs(zipkinStrategyMock);
        }
        else if ("opentracing".equalsIgnoreCase(knownStrategyShortName)) {
            assertThat(result).isSameAs(openTracingStrategyMock);
        }
        else if ("none".equalsIgnoreCase(knownStrategyShortName) || "noop".equalsIgnoreCase(knownStrategyShortName)) {
            assertThat(result).isSameAs(noOpStrategyMock);
        }
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void getTagStrategyFromName_returns_expected_strategy_for_fully_qualified_classname(
        boolean useClassThatExists
    ) {
        // given
        RequestTracingFilter filter = new RequestTracingFilter();
        String classname = (useClassThatExists)
                           ? TagStrategyExtension.class.getName()
                           : "foo.doesnotexist.BlahStrategy" + UUID.randomUUID().toString();

        AtomicReference<HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse>> resultHolder =
            new AtomicReference<>();
        
        // when
        Throwable ex = catchThrowable(() -> resultHolder.set(filter.getTagStrategyFromName(classname)));

        // then
        if (useClassThatExists) {
            assertThat(ex).isNull();
            assertThat(resultHolder.get())
                .isNotNull()
                .isInstanceOf(TagStrategyExtension.class);
        }
        else {
            assertThat(ex).isInstanceOf(ClassNotFoundException.class);
            assertThat(resultHolder.get()).isNull();
        }
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void initializeTagAndNamingAdapter_delegates_to_getTagAdapterFromName_and_returns_default_if_exception_is_thrown(
        boolean throwException
    ) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        // given
        RequestTracingFilter filterSpy = spy(new RequestTracingFilter());

        String tagAdapterFromFilterConfig = UUID.randomUUID().toString();
        doReturn(tagAdapterFromFilterConfig)
            .when(filterConfigMock).getInitParameter(TAG_AND_SPAN_NAMING_ADAPTER_INIT_PARAM_NAME);

        HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> adapterFromDesiredMethodMock =
            mock(HttpTagAndSpanNamingAdapter.class);
        HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> fallbackDefaultAdapterMock =
            mock(HttpTagAndSpanNamingAdapter.class);

        if (throwException) {
            doThrow(new RuntimeException("intentional exception")).when(filterSpy).getTagAdapterFromName(anyString());
        }
        else {
            doReturn(adapterFromDesiredMethodMock).when(filterSpy).getTagAdapterFromName(anyString());
        }

        doReturn(fallbackDefaultAdapterMock).when(filterSpy).getDefaultTagAdapter();

        // when
        HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> result =
            filterSpy.initializeTagAndNamingAdapter(filterConfigMock);

        // then
        verify(filterSpy).getTagAdapterFromName(tagAdapterFromFilterConfig);

        if (throwException) {
            assertThat(result).isSameAs(fallbackDefaultAdapterMock);
            verify(filterSpy).getDefaultTagAdapter();
        }
        else {
            assertThat(result).isSameAs(adapterFromDesiredMethodMock);
            verify(filterSpy, never()).getDefaultTagAdapter();
        }
    }

    @DataProvider(value = {
        "null",
        "",
        " ",
        " \t\r\n  "
    })
    @Test
    public void getTagAdapterFromName_returns_default_adapter_if_passed_null_or_blank_string(
        String adapterName
    ) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        // given
        RequestTracingFilter filterSpy = spy(new RequestTracingFilter());
        HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> defaultAdapterMock =
            mock(HttpTagAndSpanNamingAdapter.class);

        doReturn(defaultAdapterMock).when(filterSpy).getDefaultTagAdapter();

        // when
        HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse>
            result = filterSpy.getTagAdapterFromName(adapterName);

        // then
        assertThat(result).isSameAs(defaultAdapterMock);
        verify(filterSpy).getDefaultTagAdapter();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void getTagAdapterFromName_returns_expected_strategy_for_fully_qualified_classname(
        boolean useClassThatExists
    ) {
        // given
        RequestTracingFilter filter = new RequestTracingFilter();
        String classname = (useClassThatExists)
                           ? TagAdapterExtension.class.getName()
                           : "foo.doesnotexist.BlahAdapter" + UUID.randomUUID().toString();

        AtomicReference<HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse>> resultHolder =
            new AtomicReference<>();

        // when
        Throwable ex = catchThrowable(() -> resultHolder.set(filter.getTagAdapterFromName(classname)));

        // then
        if (useClassThatExists) {
            assertThat(ex).isNull();
            assertThat(resultHolder.get())
                .isNotNull()
                .isInstanceOf(TagAdapterExtension.class);
        }
        else {
            assertThat(ex).isInstanceOf(ClassNotFoundException.class);
            assertThat(resultHolder.get()).isNull();
        }
    }

    @Test
    public void getZipkinHttpTagStrategy_works_as_expected() {
        // given
        RequestTracingFilter filter = new RequestTracingFilter();

        // expect
        assertThat(filter.getZipkinHttpTagStrategy())
            .isNotNull()
            .isSameAs(ZipkinHttpTagStrategy.getDefaultInstance());
    }

    @Test
    public void getOpenTracingHttpTagStrategy_works_as_expected() {
        // given
        RequestTracingFilter filter = new RequestTracingFilter();

        // expect
        assertThat(filter.getOpenTracingHttpTagStrategy())
            .isNotNull()
            .isSameAs(OpenTracingHttpTagStrategy.getDefaultInstance());
    }

    @Test
    public void getNoOpTagStrategy_works_as_expected() {
        // given
        RequestTracingFilter filter = new RequestTracingFilter();

        // expect
        assertThat(filter.getNoOpTagStrategy())
            .isNotNull()
            .isSameAs(NoOpHttpTagStrategy.getDefaultInstance());
    }

    @Test
    public void getDefaultTagStrategy_delegates_to_getZipkinHttpTagStrategy() {
        // given
        RequestTracingFilter filterSpy = spy(new RequestTracingFilter());
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> zipkinStrategyMock =
            mock(HttpTagAndSpanNamingStrategy.class);
        doReturn(zipkinStrategyMock).when(filterSpy).getZipkinHttpTagStrategy();

        // when
        HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> result =
            filterSpy.getDefaultTagStrategy();

        // then
        assertThat(result).isSameAs(zipkinStrategyMock);
        verify(filterSpy).getDefaultTagStrategy();
    }

    @Test
    public void getDefaultTagAdapter_works_as_expected() {
        // given
        RequestTracingFilter filter = new RequestTracingFilter();

        // expect
        assertThat(filter.getDefaultTagAdapter())
            .isNotNull()
            .isSameAs(ServletRequestTagAdapter.getDefaultInstance());
    }

    @Test
    public void destroy_does_nothing() {
        // given
        RequestTracingFilter filterSpy = spy(new RequestTracingFilter());

        // when
        Throwable ex = catchThrowable(filterSpy::destroy);

        // then
        assertThat(ex).isNull();
        verify(filterSpy).destroy();
        verifyNoMoreInteractions(filterSpy);
    }

    // VERIFY doFilter ===================================

    @Test(expected = ServletException.class)
    public void doFilter_should_explode_if_request_is_not_HttpServletRequest() throws IOException, ServletException {
        // expect
        getBasicFilter().doFilter(mock(ServletRequest.class), mock(HttpServletResponse.class), mock(FilterChain.class));
        fail("Expected ServletException but no exception was thrown");
    }

    @Test(expected = ServletException.class)
    public void doFilter_should_explode_if_response_is_not_HttpServletResponse() throws IOException, ServletException {
        // expect
        getBasicFilter().doFilter(mock(HttpServletRequest.class), mock(ServletResponse.class), mock(FilterChain.class));
        fail("Expected ServletException but no exception was thrown");
    }

    @Test
    public void doFilter_should_not_explode_if_request_and_response_are_HttpServletRequests_and_HttpServletResponses(
    ) throws IOException, ServletException {
        // expect
        getBasicFilter().doFilter(
            mock(HttpServletRequest.class), mock(HttpServletResponse.class), mock(FilterChain.class)
        );
        // No explosion no problem
    }

    @Test
    public void doFilter_should_call_doFilterInternal_and_set_ALREADY_FILTERED_ATTRIBUTE_KEY_if_not_already_filtered_and_skipDispatch_returns_false()
        throws IOException, ServletException {
        // given: filter that returns false for skipDispatch and request that returns null for already-filtered attribute
        RequestTracingFilter spyFilter = spy(getBasicFilter());
        given(requestMock.getAttribute(
            RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(null);

        // when: doFilter() is called
        spyFilter.doFilter(requestMock, responseMock, filterChainMock);

        // then: doFilterInternal should be called and ALREADY_FILTERED_ATTRIBUTE_KEY should be set on the request
        verify(spyFilter).doFilterInternal(requestMock, responseMock, filterChainMock);
        verify(requestMock).setAttribute(RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE, Boolean.TRUE);
    }

    @Test
    public void doFilter_should_not_unset_ALREADY_FILTERED_ATTRIBUTE_KEY_after_running_doFilterInternal(
    ) throws IOException, ServletException {
        // given: filter that will run doFilterInternal and a FilterChain we can use to verify state when called
        final RequestTracingFilter spyFilter = spy(getBasicFilter());
        given(requestMock.getAttribute(
            RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(null);
        final List<Boolean> ifObjectAddedThenSmartFilterChainCalled = new ArrayList<>();
        FilterChain smartFilterChain = new FilterChain() {
            @Override
            public void doFilter(
                ServletRequest request, ServletResponse response
            ) throws IOException, ServletException {
                // Verify that when the filter chain is called we're in doFilterInternal, and that the request has ALREADY_FILTERED_ATTRIBUTE_KEY set
                verify(spyFilter).doFilterInternal(requestMock, responseMock, this);
                verify(requestMock).setAttribute(
                    RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE, Boolean.TRUE
                );
                verify(requestMock, times(0)).removeAttribute(
                    RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE
                );
                ifObjectAddedThenSmartFilterChainCalled.add(true);
            }
        };

        // when: doFilter() is called
        spyFilter.doFilter(requestMock, responseMock, smartFilterChain);

        // then: smartFilterChain's doFilter should have been called and ALREADY_FILTERED_ATTRIBUTE_KEY should still be set on the request
        assertThat(ifObjectAddedThenSmartFilterChainCalled).hasSize(1);
        verify(requestMock, never()).removeAttribute(RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE);
    }

    @Test
    public void doFilter_should_not_unset_ALREADY_FILTERED_ATTRIBUTE_KEY_even_if_filter_chain_explodes(
    ) throws IOException, ServletException {
        // given: filter that will run doFilterInternal and a FilterChain we can use to verify state when called and then explodes
        final RequestTracingFilter spyFilter = spy(getBasicFilter());
        given(requestMock.getAttribute(
            RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(null);
        final List<Boolean> ifObjectAddedThenSmartFilterChainCalled = new ArrayList<>();
        FilterChain smartFilterChain = new FilterChain() {
            @Override
            public void doFilter(
                ServletRequest request, ServletResponse response
            ) throws IOException, ServletException {
                // Verify that when the filter chain is called we're in doFilterInternal, and that the request has ALREADY_FILTERED_ATTRIBUTE_KEY set
                verify(spyFilter).doFilterInternal(requestMock, responseMock, this);
                verify(requestMock).setAttribute(
                    RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE, Boolean.TRUE
                );
                verify(requestMock, times(0)).removeAttribute(
                    RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE
                );
                ifObjectAddedThenSmartFilterChainCalled.add(true);
                throw new IllegalStateException("boom");
            }
        };

        // when: doFilter() is called
        boolean filterChainExploded = false;
        try {
            spyFilter.doFilter(requestMock, responseMock, smartFilterChain);
        }
        catch (IllegalStateException ex) {
            if ("boom".equals(ex.getMessage())) {
                filterChainExploded = true;
            }
        }

        // then: smartFilterChain's doFilter should have been called, it should have exploded, and ALREADY_FILTERED_ATTRIBUTE_KEY should still be set on the request
        assertThat(ifObjectAddedThenSmartFilterChainCalled).hasSize(1);
        assertThat(filterChainExploded).isTrue();
        verify(requestMock, never()).removeAttribute(RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE);
    }

    @Test
    public void doFilter_should_not_call_doFilterInternal_if_already_filtered() throws IOException, ServletException {
        // given: filter that returns false for skipDispatch but request that returns non-null for already-filtered attribute
        RequestTracingFilter spyFilter = spy(getBasicFilter());
        given(requestMock.getAttribute(
            RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(Boolean.TRUE);

        // when: doFilter() is called
        spyFilter.doFilter(requestMock, responseMock, filterChainMock);

        // then: doFilterInternal should not be called
        verify(spyFilter, times(0)).doFilterInternal(requestMock, responseMock, filterChainMock);
    }

    @Test
    public void doFilter_should_not_call_doFilterInternal_if_not_already_filtered_but_skipDispatch_returns_true(
    ) throws IOException, ServletException {
        // given: request that returns null for already-filtered attribute but filter that returns true for skipDispatch
        RequestTracingFilter spyFilter = spy(getBasicFilter());
        doReturn(true).when(spyFilter).skipDispatch(any(HttpServletRequest.class));
        given(requestMock.getAttribute(RequestTracingFilter.FILTER_HAS_ALREADY_EXECUTED_ATTRIBUTE)).willReturn(null);

        // when: doFilter() is called
        spyFilter.doFilter(requestMock, responseMock, filterChainMock);

        // then: doFilterInternal should not be called
        verify(spyFilter, times(0)).doFilterInternal(requestMock, responseMock, filterChainMock);
        verify(spyFilter).skipDispatch(requestMock);
    }

    // VERIFY doFilterInternal ===================================

    @Test
    public void doFilterInternal_should_create_new_sampleable_span_if_no_parent_in_request_and_it_should_be_completed_and_tags_should_be_handled(
    ) throws ServletException, IOException {
        // given: filter
        RequestTracingFilter filter = getBasicFilter();

        // when: doFilterInternal is called with a request that does not have a parent span
        filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: a new valid sampleable span should be created and completed,
        //      and tagging should have been done as expected
        Span span = spanCapturingFilterChain.capturedSpan;
        assertThat(span).isNotNull();
        assertThat(span.getTraceId()).isNotNull();
        assertThat(span.getSpanId()).isNotNull();
        assertThat(span.getSpanName()).isNotNull();
        assertThat(span.getParentSpanId()).isNull();
        assertThat(span.isSampleable()).isTrue();
        assertThat(span.isCompleted()).isTrue();

        assertThat(strategyRequestTaggingMethodCalled.get()).isTrue();
        strategyRequestTaggingArgs.get().verifyArgs(span, requestMock, filter.tagAndNamingAdapter);

        assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
        strategyResponseTaggingArgs.get().verifyArgs(
            span, requestMock, responseMock, null, filter.tagAndNamingAdapter
        );
    }

    @Test
    public void doFilterInternal_should_not_complete_span_or_response_tags_until_after_filter_chain_runs(
    ) throws ServletException, IOException {
        // given: filter and filter chain that can tell us whether or not the span is complete at the time it is called
        RequestTracingFilter filter = getBasicFilter();
        AtomicBoolean spanCompletedHolder = new AtomicBoolean(false);
        AtomicReference<Span> spanHolder = new AtomicReference<>();
        AtomicReference<Boolean> requestTagsExecutedAtTimeOfFilterChain = new AtomicReference<>();
        AtomicReference<Boolean> responseTagsExecutedAtTimeOfFilterChain = new AtomicReference<>();
        FilterChain smartFilterChain = (request, response) -> {
            Span span = Tracer.getInstance().getCurrentSpan();
            spanHolder.set(span);
            if (span != null) {
                spanCompletedHolder.set(span.isCompleted());
            }
            requestTagsExecutedAtTimeOfFilterChain.set(strategyRequestTaggingMethodCalled.get());
            responseTagsExecutedAtTimeOfFilterChain.set(strategyResponseTaggingAndFinalSpanNameMethodCalled.get());
        };

        // when: doFilterInternal is called
        filter.doFilterInternal(requestMock, responseMock, smartFilterChain);

        // then: we should be able to validate that the smartFilterChain was called, and when it was called the span
        //       had not yet been completed, and after doFilterInternal finished it was completed. Similarly, when
        //       the chain is being run, request tags should be done but response tags should not.
        assertThat(spanHolder.get()).isNotNull();
        assertThat(spanCompletedHolder.get()).isFalse();
        assertThat(spanHolder.get().isCompleted()).isTrue();
        assertThat(requestTagsExecutedAtTimeOfFilterChain.get()).isTrue();
        assertThat(responseTagsExecutedAtTimeOfFilterChain.get()).isFalse();
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void doFilterInternal_should_complete_span_and_response_tags_even_if_filter_chain_explodes(
        boolean isAsyncRequest
    ) throws ServletException, IOException {
        // given: filter and filter chain that will explode when called
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        AtomicReference<Span> spanContextHolder = new AtomicReference<>();
        FilterChain explodingFilterChain = (request, response) -> {
            // Verify that the span is not yet completed, keep track of it for later, then explode
            Span span = Tracer.getInstance().getCurrentSpan();
            assertThat(span).isNotNull();
            assertThat(span.isCompleted()).isFalse();
            spanContextHolder.set(span);
            throw new IllegalStateException("boom");
        };

        if (isAsyncRequest) {
            setupAsyncContextWorkflow();
        }

        // when: doFilterInternal is called
        boolean filterChainExploded = false;
        Throwable errorThrown = null;
        try {
            filterSpy.doFilterInternal(requestMock, responseMock, explodingFilterChain);
        }
        catch (IllegalStateException ex) {
            errorThrown = ex;
            if ("boom".equals(ex.getMessage())) {
                filterChainExploded = true;
            }
        }

        // then: we should be able to validate that the filter chain exploded and the span is still completed,
        //       or setup for completion in the case of an async request
        if (isAsyncRequest) {
            assertThat(filterChainExploded).isTrue();
            verify(filterSpy).isAsyncRequest(requestMock);
            verify(filterSpy).setupTracingCompletionWhenAsyncRequestCompletes(
                eq(requestMock),
                eq(responseMock),
                any(TracingState.class),
                any(HttpTagAndSpanNamingStrategy.class),
                any(HttpTagAndSpanNamingAdapter.class)
            );
            assertThat(spanContextHolder.get()).isNotNull();
            // The span should not be *completed* for an async request, but the
            //      setupTracingCompletionWhenAsyncRequestCompletes verification above represents the equivalent for
            //      async requests. The response tagging happens in there as well.
            assertThat(spanContextHolder.get().isCompleted()).isFalse();
        }
        else {
            assertThat(filterChainExploded).isTrue();
            assertThat(spanContextHolder.get()).isNotNull();
            assertThat(spanContextHolder.get().isCompleted()).isTrue();

            assertThat(strategyResponseTaggingAndFinalSpanNameMethodCalled.get()).isTrue();
            // Response tags should be executed with the error that was thrown.
            strategyResponseTaggingArgs.get().verifyArgs(
                spanContextHolder.get(), requestMock, responseMock, errorThrown, filterSpy.tagAndNamingAdapter
            );
        }

        // No matter what, the request tagging should have been done.
        assertThat(strategyRequestTaggingMethodCalled.get()).isTrue();
        strategyRequestTaggingArgs.get().verifyArgs(
            spanContextHolder.get(), requestMock, filterSpy.tagAndNamingAdapter
        );
    }

    @Test
    public void doFilterInternal_should_set_request_attributes_to_new_span_info_with_user_id(
    ) throws ServletException, IOException {
        // given: filter
        RequestTracingFilter spyFilter = spy(getBasicFilter());
        given(requestMock.getHeader(USER_ID_HEADER_KEY)).willReturn("testUserId");

        // when: doFilterInternal is called
        spyFilter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: request attributes should be set with the new span's info
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;

        assertThat(newSpan.getUserId()).isEqualTo("testUserId");
    }

    @Test
    public void doFilterInternal_should_set_request_attributes_to_new_span_info_with_alt_user_id(
    ) throws ServletException, IOException {
        // given: filter
        RequestTracingFilter spyFilter = spy(getBasicFilter());
        given(requestMock.getHeader(ALT_USER_ID_HEADER_KEY)).willReturn("testUserId");

        // when: doFilterInternal is called
        spyFilter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: request attributes should be set with the new span's info
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;

        assertThat(newSpan.getUserId()).isEqualTo("testUserId");
    }

    @Test
    public void doFilterInternal_should_set_request_attributes_to_new_span_info() throws ServletException, IOException {
        // given: filter
        RequestTracingFilter filter = getBasicFilter();

        // when: doFilterInternal is called
        filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: request attributes should be set with the new span's info
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;

        verify(requestMock).setAttribute(TraceHeaders.TRACE_SAMPLED, newSpan.isSampleable());
        verify(requestMock).setAttribute(TraceHeaders.TRACE_ID, newSpan.getTraceId());
        verify(requestMock).setAttribute(TraceHeaders.SPAN_ID, newSpan.getSpanId());
        verify(requestMock).setAttribute(TraceHeaders.PARENT_SPAN_ID, newSpan.getParentSpanId());
        verify(requestMock).setAttribute(TraceHeaders.SPAN_NAME, newSpan.getSpanName());
        verify(requestMock).setAttribute(Span.class.getName(), newSpan);
    }

    @Test
    public void doFilterInternal_should_set_trace_id_in_response_header() throws ServletException, IOException {
        // given: filter
        RequestTracingFilter filter = getBasicFilter();

        // when: doFilterInternal is called
        filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: response header should be set with the span's trace ID
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        verify(responseMock).setHeader(TraceHeaders.TRACE_ID, spanCapturingFilterChain.capturedSpan.getTraceId());
    }

    @Test
    public void doFilterInternal_should_use_parent_span_info_if_present_in_request_headers(
    ) throws ServletException, IOException {
        // given: filter and request that has parent span info
        RequestTracingFilter filter = getBasicFilter();
        Span parentSpan = Span.newBuilder("someParentSpan", null)
                              .withParentSpanId(TraceAndSpanIdGenerator.generateId())
                              .withSampleable(false)
                              .withUserId("someUser")
                              .build();
        given(requestMock.getHeader(TraceHeaders.TRACE_ID)).willReturn(parentSpan.getTraceId());
        given(requestMock.getHeader(TraceHeaders.SPAN_ID)).willReturn(parentSpan.getSpanId());
        given(requestMock.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(parentSpan.getParentSpanId());
        given(requestMock.getHeader(TraceHeaders.SPAN_NAME)).willReturn(parentSpan.getSpanName());
        given(requestMock.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(String.valueOf(parentSpan.isSampleable()));
        given(requestMock.getServletPath()).willReturn("/some/path");
        given(requestMock.getMethod()).willReturn("GET");

        // when: doFilterInternal is called
        filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: the span that is created should use the parent span info as its parent
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;
        assertThat(newSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
        assertThat(newSpan.getSpanId()).isNotEqualTo(parentSpan.getSpanId());
        assertThat(newSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
        assertThat(newSpan.getSpanName()).isEqualTo(
            filter.getInitialSpanName(requestMock, filter.tagAndNamingStrategy, filter.tagAndNamingAdapter)
        );
        assertThat(newSpan.isSampleable()).isEqualTo(parentSpan.isSampleable());
        assertThat(newSpan.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
    }

    @Test
    public void doFilterInternal_should_use_user_id_from_parent_span_info_if_present_in_request_headers(
    ) throws ServletException, IOException {
        // given: filter and request that has parent span info
        RequestTracingFilter spyFilter = spy(getBasicFilter());
        given(requestMock.getHeader(ALT_USER_ID_HEADER_KEY)).willReturn("testUserId");

        Span parentSpan = Span.newBuilder("someParentSpan", null)
                              .withParentSpanId(TraceAndSpanIdGenerator.generateId())
                              .withSampleable(false)
                              .withUserId("someUser")
                              .build();
        given(requestMock.getHeader(TraceHeaders.TRACE_ID)).willReturn(parentSpan.getTraceId());
        given(requestMock.getHeader(TraceHeaders.SPAN_ID)).willReturn(parentSpan.getSpanId());
        given(requestMock.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(parentSpan.getParentSpanId());
        given(requestMock.getHeader(TraceHeaders.SPAN_NAME)).willReturn(parentSpan.getSpanName());
        given(requestMock.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(String.valueOf(parentSpan.isSampleable()));
        given(requestMock.getServletPath()).willReturn("/some/path");
        given(requestMock.getMethod()).willReturn("GET");

        // when: doFilterInternal is called
        spyFilter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then: the span that is created should use the parent span info as its parent
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        Span newSpan = spanCapturingFilterChain.capturedSpan;

        assertThat(newSpan.getUserId()).isEqualTo("testUserId");
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void doFilterInternal_should_use_getInitialSpanName_for_span_name(
        boolean parentSpanExists
    ) throws ServletException, IOException {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());

        filterSpy.tagAndNamingStrategy = tagAndNamingStrategy;
        filterSpy.tagAndNamingAdapter = tagAndNamingAdapterMock;

        String expectedSpanName = UUID.randomUUID().toString();
        doReturn(expectedSpanName).when(filterSpy).getInitialSpanName(
            any(HttpServletRequest.class), any(HttpTagAndSpanNamingStrategy.class), any(HttpTagAndSpanNamingAdapter.class)
        );

        if (parentSpanExists) {
            given(requestMock.getHeader(TraceHeaders.TRACE_ID)).willReturn(TraceAndSpanIdGenerator.generateId());
            given(requestMock.getHeader(TraceHeaders.SPAN_ID)).willReturn(TraceAndSpanIdGenerator.generateId());
        }

        // when
        filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then
        assertThat(spanCapturingFilterChain.captureSpanCopyAtTimeOfDoFilter).isNotNull();
        assertThat(spanCapturingFilterChain.captureSpanCopyAtTimeOfDoFilter.getSpanName()).isEqualTo(expectedSpanName);

        verify(filterSpy).getInitialSpanName(requestMock, tagAndNamingStrategy, tagAndNamingAdapterMock);
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false",
    }, splitBy = "\\|")
    @Test
    public void doFilterInternal_should_reset_tracing_info_to_whatever_was_on_the_thread_originally(
        boolean isAsync, boolean throwExceptionInInnerFinallyBlock
    ) {
        // given
        final RequestTracingFilter filter = getBasicFilter();
        if (isAsync) {
            setupAsyncContextWorkflow();
        }
        RuntimeException exToThrowInInnerFinallyBlock = null;
        if (throwExceptionInInnerFinallyBlock) {
            exToThrowInInnerFinallyBlock = new RuntimeException("kaboom");
            doThrow(exToThrowInInnerFinallyBlock).when(requestMock).isAsyncStarted();
        }
        Tracer.getInstance().startRequestWithRootSpan("someOutsideSpan");
        TracingState originalTracingState = TracingState.getCurrentThreadTracingState();

        // when
        Throwable ex = catchThrowable(
            () -> filter.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain)
        );

        // then
        if (throwExceptionInInnerFinallyBlock) {
            assertThat(ex).isSameAs(exToThrowInInnerFinallyBlock);
        }
        assertThat(TracingState.getCurrentThreadTracingState()).isEqualTo(originalTracingState);
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        // The original tracing state was replaced on the thread before returning, but the span used by the filter chain
        //      should *not* come from the original tracing state - it should have come from the incoming headers or
        //      a new one generated.
        assertThat(spanCapturingFilterChain.capturedSpan.getTraceId())
            .isNotEqualTo(originalTracingState.spanStack.peek().getTraceId());
    }

    @Test
    public void doFilterInternal_should_call_setupTracingCompletionWhenAsyncRequestCompletes_when_isAsyncRequest_returns_true(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        setupAsyncContextWorkflow();
        doReturn(true).when(filterSpy).isAsyncRequest(any(HttpServletRequest.class));

        // when
        filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        assertThat(spanCapturingFilterChain.capturedSpan.isCompleted()).isFalse();
        verify(filterSpy).setupTracingCompletionWhenAsyncRequestCompletes(
            eq(requestMock),
            eq(responseMock),
            any(TracingState.class),
            any(HttpTagAndSpanNamingStrategy.class),
            any(HttpTagAndSpanNamingAdapter.class)
        );
    }

    @Test
    public void doFilterInternal_should_not_call_setupTracingCompletionWhenAsyncRequestCompletes_when_isAsyncRequest_returns_false(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        doReturn(false).when(filterSpy).isAsyncRequest(any(HttpServletRequest.class));

        // when
        filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        assertThat(spanCapturingFilterChain.capturedSpan.isCompleted()).isTrue();
        verify(filterSpy, never()).setupTracingCompletionWhenAsyncRequestCompletes(
            any(HttpServletRequest.class), any(HttpServletResponse.class), any(TracingState.class),
            any(HttpTagAndSpanNamingStrategy.class), any(HttpTagAndSpanNamingAdapter.class)
        );
    }

    @Test
    public void doFilterInternal_should_add_async_listener_but_not_complete_span_when_async_request_is_detected(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        setupAsyncContextWorkflow();

        // when
        filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        assertThat(spanCapturingFilterChain.capturedSpan.isCompleted()).isFalse();
        assertThat(capturedAsyncListeners).hasSize(1);
        assertThat(capturedAsyncListeners.get(0)).isInstanceOf(WingtipsRequestSpanCompletionAsyncListener.class);
        verify(filterSpy).setupTracingCompletionWhenAsyncRequestCompletes(
            eq(requestMock),
            eq(responseMock),
            any(TracingState.class),
            any(HttpTagAndSpanNamingStrategy.class),
            any(HttpTagAndSpanNamingAdapter.class)
        );
    }

    @Test
    public void doFilterInternal_should_not_add_async_listener_when_isAsyncRequest_returns_false(
    ) throws ServletException, IOException {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        doReturn(false).when(filterSpy).isAsyncRequest(any(HttpServletRequest.class));
        setupAsyncContextWorkflow();

        // when
        filterSpy.doFilterInternal(requestMock, responseMock, spanCapturingFilterChain);

        // then
        assertThat(spanCapturingFilterChain.capturedSpan).isNotNull();
        assertThat(spanCapturingFilterChain.capturedSpan.isCompleted()).isTrue();
        assertThat(capturedAsyncListeners).hasSize(0);
        verify(filterSpy, never()).setupTracingCompletionWhenAsyncRequestCompletes(
            any(HttpServletRequest.class), any(HttpServletResponse.class), any(TracingState.class),
            any(HttpTagAndSpanNamingStrategy.class), any(HttpTagAndSpanNamingAdapter.class)
        );
    }

    // VERIFY getInitialSpanName ========================

    @DataProvider(value = {
        // Name from strategy always wins
        "someStrategyName   |   GET     |   /some/http/route    |   someStrategyName",

        // Null/blank name from strategy defers to HttpSpanFactory.getSpanName().
        "null               |   GET     |   /some/http/route    |   GET /some/http/route",
        "                   |   GET     |   /some/http/route    |   GET /some/http/route",
        "[whitespace]       |   GET     |   /some/http/route    |   GET /some/http/route",
        "null               |   null    |   /some/http/route    |   UNKNOWN_HTTP_METHOD /some/http/route",
        "null               |   null    |   null                |   UNKNOWN_HTTP_METHOD"
    }, splitBy = "\\|")
    @Test
    public void getInitialSpanName_works_as_expected(
        String strategyResult, String httpMethod, String httpRoute, String expectedResult
    ) {
        // given
        RequestTracingFilter filter = getBasicFilter();

        if ("[whitespace]".equals(strategyResult)) {
            strategyResult = "  \t\r\n  ";
        }

        initialSpanNameFromStrategy.set(strategyResult);

        doReturn(httpMethod).when(requestMock).getMethod();
        doReturn(httpRoute).when(requestMock).getAttribute(KnownZipkinTags.HTTP_ROUTE);

        // when
        String result = filter.getInitialSpanName(requestMock, tagAndNamingStrategy, tagAndNamingAdapterMock);

        // then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(strategyInitialSpanNameMethodCalled.get()).isTrue();
        strategyInitialSpanNameArgs.get().verifyArgs(requestMock, tagAndNamingAdapterMock);
    }

    // VERIFY getServletRuntime =========================

    @Test
    public void getServletRuntime_returns_value_of_ServletRuntime_determineServletRuntime_method_and_caches_result() {
        // given
        Class<? extends ServletRuntime> expectedServletRuntimeClass =
            ServletRuntime.determineServletRuntime(requestMock.getClass(), ASYNC_LISTENER_CLASSNAME).getClass();

        RequestTracingFilter filter = getBasicFilter();
        assertThat(filter.servletRuntime).isNull();

        // when
        ServletRuntime result = filter.getServletRuntime(requestMock);

        // then
        assertThat(result.getClass()).isEqualTo(expectedServletRuntimeClass);
        assertThat(filter.servletRuntime).isSameAs(result);
    }

    @Test
    public void getServletRuntime_uses_cached_value_if_possible() {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        ServletRuntime servletRuntimeMock = mock(ServletRuntime.class);
        filterSpy.servletRuntime = servletRuntimeMock;

        // when
        ServletRuntime result = filterSpy.getServletRuntime(mock(HttpServletRequest.class));

        // then
        assertThat(result).isSameAs(servletRuntimeMock);
    }

    // VERIFY isAsyncRequest ==============================

    @DataProvider(value = {
        "true",
        "false"
    }, splitBy = "\\|")
    @Test
    public void isAsyncRequest_delegates_to_ServletRuntime(boolean servletRuntimeResult) {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        doReturn(servletRuntimeMock).when(filterSpy).getServletRuntime(any(HttpServletRequest.class));
        doReturn(servletRuntimeResult).when(servletRuntimeMock).isAsyncRequest(any(HttpServletRequest.class));

        // when
        boolean result = filterSpy.isAsyncRequest(requestMock);

        // then
        assertThat(result).isEqualTo(servletRuntimeResult);
        verify(filterSpy).getServletRuntime(requestMock);
        verify(servletRuntimeMock).isAsyncRequest(requestMock);
    }

    // VERIFY setupTracingCompletionWhenAsyncRequestCompletes ============

    @Test
    public void setupTracingCompletionWhenAsyncRequestCompletes_delegates_to_ServletRuntime() {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        doReturn(servletRuntimeMock).when(filterSpy).getServletRuntime(any(HttpServletRequest.class));
        TracingState tracingStateMock = mock(TracingState.class);

        // when
        filterSpy.setupTracingCompletionWhenAsyncRequestCompletes(
            requestMock, responseMock, tracingStateMock, tagAndNamingStrategy, tagAndNamingAdapterMock
        );

        // then
        verify(filterSpy).setupTracingCompletionWhenAsyncRequestCompletes(
            requestMock, responseMock, tracingStateMock, tagAndNamingStrategy, tagAndNamingAdapterMock
        );
        verify(filterSpy).getServletRuntime(requestMock);
        verify(servletRuntimeMock).setupTracingCompletionWhenAsyncRequestCompletes(
            requestMock, responseMock, tracingStateMock, tagAndNamingStrategy, tagAndNamingAdapterMock
        );
        verifyNoMoreInteractions(filterSpy, servletRuntimeMock, requestMock, tracingStateMock);
    }

    // VERIFY isAsyncDispatch ===========================

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    @SuppressWarnings("deprecation")
    public void isAsyncDispatch_delegates_to_ServletRuntime(boolean servletRuntimeResult) {
        // given
        RequestTracingFilter filterSpy = spy(getBasicFilter());
        doReturn(servletRuntimeMock).when(filterSpy).getServletRuntime(any(HttpServletRequest.class));
        doReturn(servletRuntimeResult).when(servletRuntimeMock).isAsyncDispatch(any(HttpServletRequest.class));

        // when
        boolean result = filterSpy.isAsyncDispatch(requestMock);

        // then
        assertThat(result).isEqualTo(servletRuntimeResult);
        verify(filterSpy).getServletRuntime(requestMock);
        verify(servletRuntimeMock).isAsyncDispatch(requestMock);
    }

    // VERIFY skipDispatch ==============================

    @Test
    public void skipDispatch_should_return_false() {
        // given: filter
        RequestTracingFilter filter = getBasicFilter();

        // when: skipDispatchIsCalled
        boolean result = filter.skipDispatch(requestMock);

        // then: the result should be false
        assertThat(result).isFalse();
    }

    private static class SpanCapturingFilterChain implements FilterChain {

        Span capturedSpan;
        Span captureSpanCopyAtTimeOfDoFilter;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            capturedSpan = Tracer.getInstance().getCurrentSpan();
            captureSpanCopyAtTimeOfDoFilter = Span.newBuilder(capturedSpan).build();
        }
    }

    public static class TagStrategyExtension extends HttpTagAndSpanNamingStrategy<HttpServletRequest, HttpServletResponse> {
        @Override
        protected void doHandleRequestTagging(
            @NotNull Span span, @NotNull HttpServletRequest request,
            @NotNull HttpTagAndSpanNamingAdapter<HttpServletRequest, ?> adapter
        ) {

        }

        @Override
        protected void doHandleResponseAndErrorTagging(
            @NotNull Span span, @Nullable HttpServletRequest request, @Nullable HttpServletResponse response,
            @Nullable Throwable error,
            @NotNull HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> adapter
        ) {

        }
    }

    public static class TagAdapterExtension extends HttpTagAndSpanNamingAdapter<HttpServletRequest, HttpServletResponse> {
        @Override
        public @Nullable String getRequestUrl(@Nullable HttpServletRequest request) {
            return null;
        }

        @Override
        public @Nullable String getRequestPath(
            @Nullable HttpServletRequest request
        ) {
            return null;
        }

        @Override
        public @Nullable String getRequestUriPathTemplate(
            @Nullable HttpServletRequest request, @Nullable HttpServletResponse response
        ) {
            return null;
        }

        @Override
        public @Nullable Integer getResponseHttpStatus(
            @Nullable HttpServletResponse response
        ) {
            return null;
        }

        @Override
        public @Nullable String getRequestHttpMethod(
            @Nullable HttpServletRequest request
        ) {
            return null;
        }

        @Override
        public @Nullable String getHeaderSingleValue(
            @Nullable HttpServletRequest request, @NotNull String headerKey
        ) {
            return null;
        }

        @Override
        public @Nullable List<String> getHeaderMultipleValue(
            @Nullable HttpServletRequest request, @NotNull String headerKey
        ) {
            return null;
        }

        @Override
        public @Nullable String getSpanHandlerTagValue(
            @Nullable HttpServletRequest request, @Nullable HttpServletResponse response
        ) {
            return null;
        }
    }
}
