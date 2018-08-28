package com.nike.wingtips.servlet;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.tags.KnownZipkinTags;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;

/**
 * Tests the functionality of {@link HttpSpanFactory}.
 */
@RunWith(DataProviderRunner.class)
public class HttpSpanFactoryTest {

    private String sampleTraceID = TraceAndSpanIdGenerator.generateId();
    private String sampleSpanID = TraceAndSpanIdGenerator.generateId();
    private String sampleParentSpanID = TraceAndSpanIdGenerator.generateId();
    @SuppressWarnings("FieldCanBeLocal")
    private String userId = "userId";
    private String altUserId = "altUserId";
    private HttpServletRequest request;

    private static final String USER_ID_HEADER_KEY = "userIdHeader";
    private static final String ALT_USER_ID_HEADER_KEY = "altUserIdHeader";
    private static final List<String> USER_ID_HEADER_KEYS = Arrays.asList(USER_ID_HEADER_KEY, ALT_USER_ID_HEADER_KEY);

    @Before
    public void onSetup() {
        request = Mockito.mock(HttpServletRequest.class);
    }

    @Test
    public void constructor_is_private() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<HttpSpanFactory> defaultConstructor = HttpSpanFactory.class.getDeclaredConstructor();
        Exception caughtException = null;
        try {
            defaultConstructor.newInstance();
        }
        catch (Exception ex) {
            caughtException = ex;
        }

        assertThat(caughtException).isNotNull();
        assertThat(caughtException).isInstanceOf(IllegalAccessException.class);

        // Set the constructor to accessible and create one. Why? Code coverage. :p
        defaultConstructor.setAccessible(true);
        HttpSpanFactory instance = defaultConstructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    public void fromHttpServletRequest_creates_span_with_all_values_if_all_values_available_from_request_headers() {
        // given: a set of standard Span header values
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(Boolean.FALSE.toString());
        given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn(sampleSpanID);
        given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(sampleParentSpanID);
        given(request.getHeader(USER_ID_HEADER_KEY)).willReturn(userId);

        // when: creating Span object from HTTP request
        long beforeCallNanos = System.nanoTime();
        Span goodSpan = HttpSpanFactory.fromHttpServletRequest(request, USER_ID_HEADER_KEYS);
        long afterCallNanos = System.nanoTime();

        // then: ensure Span object gets identical values from corresponding headers
        assertThat(goodSpan.getTraceId()).isEqualTo(sampleTraceID);
        assertThat(goodSpan.isSampleable()).isFalse();
        assertThat(goodSpan.getSpanId()).isEqualTo(sampleSpanID);
        assertThat(goodSpan.getParentSpanId()).isEqualTo(sampleParentSpanID);
        assertThat(goodSpan.getUserId()).isEqualTo(userId);
        assertThat(goodSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(goodSpan.isCompleted()).isFalse();
        assertThat(goodSpan.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
    }

    @Test
    public void fromHttpServletRequest_creates_span_with_all_values_minus_user_id_if_user_id_list_is_not_passed_in() {
        // given: a set of standard Span header values
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(Boolean.FALSE.toString());
        given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn(sampleSpanID);
        given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(sampleParentSpanID);
        given(request.getHeader(USER_ID_HEADER_KEY)).willReturn(userId);

        // when: creating Span object from HTTP request
        long beforeCallNanos = System.nanoTime();
        Span goodSpan = HttpSpanFactory.fromHttpServletRequest(request, null);
        long afterCallNanos = System.nanoTime();

        // then: ensure Span object gets identical values from corresponding headers
        assertThat(goodSpan.getTraceId()).isEqualTo(sampleTraceID);
        assertThat(goodSpan.isSampleable()).isFalse();
        assertThat(goodSpan.getSpanId()).isEqualTo(sampleSpanID);
        assertThat(goodSpan.getParentSpanId()).isEqualTo(sampleParentSpanID);
        assertThat(goodSpan.getUserId()).isNull();
        assertThat(goodSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(goodSpan.isCompleted()).isFalse();
        assertThat(goodSpan.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
    }

    @Test
    public void fromHttpServletRequest_pulls_from_alt_user_id_if_specified_in_header_and_primary_is_missing() {
        // given: a set of standard Span header values with the alt user ID header specified instead of the primary
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(Boolean.TRUE.toString());
        given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn(sampleSpanID);
        given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(sampleParentSpanID);
        given(request.getHeader(ALT_USER_ID_HEADER_KEY)).willReturn(altUserId);

        // when: creating Span object from HTTP request
        long beforeCallNanos = System.nanoTime();
        Span goodSpan = HttpSpanFactory.fromHttpServletRequest(request, USER_ID_HEADER_KEYS);
        long afterCallNanos = System.nanoTime();

        // then: ensure Span object gets identical values from corresponding headers
        assertThat(goodSpan.getTraceId()).isEqualTo(sampleTraceID);
        assertThat(goodSpan.isSampleable()).isTrue();
        assertThat(goodSpan.getSpanId()).isEqualTo(sampleSpanID);
        assertThat(goodSpan.getParentSpanId()).isEqualTo(sampleParentSpanID);
        assertThat(goodSpan.getUserId()).isEqualTo(altUserId);
        assertThat(goodSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(goodSpan.isCompleted()).isFalse();
        assertThat(goodSpan.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
    }

    @Test
    public void fromHttpServletRequest_returns_null_if_traceId_header_is_missing() {
        // given: a set of standard Span header values, *minus* trace ID
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(Boolean.FALSE.toString());
        given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn(sampleSpanID);
        given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(sampleParentSpanID);
        given(request.getHeader(USER_ID_HEADER_KEY)).willReturn(userId);

        // when: creating Span object from HTTP request
        Span span = HttpSpanFactory.fromHttpServletRequest(request, USER_ID_HEADER_KEYS);

        // then: ensure Span object returned is null
        assertThat(span).isNull();
    }

    @Test
    public void fromHttpServletRequestOrCreateRootSpan_pulls_from_headers_if_available() {
        // given: a set of standard Span header values
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(Boolean.TRUE.toString());
        given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn(sampleSpanID);
        given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(sampleParentSpanID);
        given(request.getHeader(USER_ID_HEADER_KEY)).willReturn(userId);

        // when: creating Span object from HTTP request using fromHttpServletRequestOrCreateRootSpan
        long beforeCallNanos = System.nanoTime();
        Span goodSpan = HttpSpanFactory.fromHttpServletRequestOrCreateRootSpan(request, USER_ID_HEADER_KEYS);
        long afterCallNanos = System.nanoTime();

        // then: ensure Span object gets identical values from corresponding headers
        assertThat(goodSpan.getTraceId()).isEqualTo(sampleTraceID);
        assertThat(goodSpan.isSampleable()).isTrue();
        assertThat(goodSpan.getSpanId()).isEqualTo(sampleSpanID);
        assertThat(goodSpan.getParentSpanId()).isEqualTo(sampleParentSpanID);
        assertThat(goodSpan.getUserId()).isEqualTo(userId);
        assertThat(goodSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(goodSpan.isCompleted()).isFalse();
        assertThat(goodSpan.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
    }

    @Test
    public void fromHttpServletRequestOrCreateRootSpan_returns_new_root_span_if_traceId_is_missing_from_headers() {
        // given: no trace ID in headers, but user ID exists
        given(request.getHeader(ALT_USER_ID_HEADER_KEY)).willReturn(altUserId);

        // when: creating Span object from HTTP request using fromHttpServletRequestOrCreateRootSpan
        long beforeCallNanos = System.nanoTime();
        Span newSpan = HttpSpanFactory.fromHttpServletRequestOrCreateRootSpan(request, USER_ID_HEADER_KEYS);
        long afterCallNanos = System.nanoTime();

        // then: ensure root span object is created even though there was no trace ID header, and the returned span contains the expected user ID
        assertThat(newSpan).isNotNull();
        assertThat(newSpan.getParentSpanId()).isNull();
        assertThat(newSpan.getUserId()).isEqualTo(altUserId);
        assertThat(newSpan.getSpanStartTimeNanos()).isBetween(beforeCallNanos, afterCallNanos);
        assertThat(newSpan.isCompleted()).isFalse();
        assertThat(newSpan.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
    }

    @Test
    public void fromHttpServletRequest_returns_null_if_passed_null_request() {
        // when
        Span nullSpan = HttpSpanFactory.fromHttpServletRequest(null, USER_ID_HEADER_KEYS);

        // then
        //noinspection ConstantConditions
        assertThat(nullSpan).isNull();
    }

    @Test
    public void getUserIdFromHttpServletRequest_returns_null_if_passed_null_request() {
        // when
        String nullUserId = HttpSpanFactory.getUserIdFromHttpServletRequest(null, USER_ID_HEADER_KEYS);

        // then
        //noinspection ConstantConditions
        assertThat(nullUserId).isNull();
    }

    @Test
    public void fromHttpServletRequest_defaults_span_sampleable_value_to_true_if_request_header_and_attribute_is_null() {
        // given: request where the request header and attribute for TRACE_SAMPLED returns null
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(null);
        given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(null);

        // when: creating span from request
        Span newSpan = HttpSpanFactory.fromHttpServletRequest(request, USER_ID_HEADER_KEYS);

        // then: new span should be sampleable
        assertThat(newSpan.isSampleable()).isTrue();
    }

    @Test
    public void fromHttpServletRequest_sets_span_sampleable_to_false_if_trace_sampled_header_is_null_but_attribute_returns_false() {
        // given: request where the header for TRACE_SAMPLED returns null but the attribute returns false
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(null);
        given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(false);

        // when: creating span from request
        Span newSpan = HttpSpanFactory.fromHttpServletRequest(request, USER_ID_HEADER_KEYS);

        // then: new span should be disabled
        assertThat(newSpan.isSampleable()).isFalse();
    }

    @Test
    public void fromHttpServletRequest_sets_span_sampleable_to_false_if_trace_sampled_header_is_empty_but_attribute_returns_false() {
        // given: request where the header for TRACE_SAMPLED returns null but the attribute returns false
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(" ");
        given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(false);

        // when: creating span from request
        Span newSpan = HttpSpanFactory.fromHttpServletRequest(request, USER_ID_HEADER_KEYS);

        // then: new span should be disabled
        assertThat(newSpan.isSampleable()).isFalse();
    }


    @Test
    public void fromHttpServletRequest_generates_new_spanId_if_missing_from_headers() {
        // given: a request with a trace ID but no span ID in the headers
        String traceId = UUID.randomUUID().toString();
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(traceId);

        // when: we use it to create span objects
        Span firstSpan = HttpSpanFactory.fromHttpServletRequest(request, USER_ID_HEADER_KEYS);
        Span secondSpan = HttpSpanFactory.fromHttpServletRequest(request, USER_ID_HEADER_KEYS);

        // then: ensure each call generates a span with the same trace ID but new span ID
        assertThat(firstSpan.getTraceId()).isEqualTo(traceId);
        assertThat(secondSpan.getTraceId()).isEqualTo(traceId);

        assertThat(firstSpan.getSpanId()).isNotEmpty();
        assertThat(secondSpan.getSpanId()).isNotEmpty();

        assertThat(firstSpan.getSpanId()).isNotEqualTo(secondSpan.getSpanId());
    }

    @DataProvider(value = {
        // http.route takes precedence
        "/some/http/route   |   /some/spring/pattern    |   /some/http/route",

        "/some/http/route   |   null                    |   /some/http/route",
        "/some/http/route   |                           |   /some/http/route",
        "/some/http/route   |   [whitespace]            |   /some/http/route",

        // Spring matching pattern request attr is used if http.route is null/blank
        "null               |   /some/spring/pattern    |   /some/spring/pattern",
        "                   |   /some/spring/pattern    |   /some/spring/pattern",
        "[whitespace]       |   /some/spring/pattern    |   /some/spring/pattern",

        // null returned if both request attrs are null/blank
        "null               |   null                    |   null",
        "                   |                           |   null",
        "[whitespace]       |   [whitespace]            |   null",
    }, splitBy = "\\|")
    @Test
    public void determineUriPathTemplate_works_as_expected(
        String httpRouteRequestAttr,
        String springMatchingPatternRequestAttr,
        String expectedResult
    ) {
        // given
        if ("[whitespace]".equals(httpRouteRequestAttr)) {
            httpRouteRequestAttr = "  \t\r\n  ";
        }

        if ("[whitespace]".equals(springMatchingPatternRequestAttr)) {
            springMatchingPatternRequestAttr = "  \t\r\n  ";
        }

        doReturn(httpRouteRequestAttr).when(request).getAttribute(KnownZipkinTags.HTTP_ROUTE);
        doReturn(springMatchingPatternRequestAttr)
            .when(request).getAttribute(HttpSpanFactory.SPRING_BEST_MATCHING_PATTERN_REQUEST_ATTRIBUTE_KEY);

        // when
        String result = HttpSpanFactory.determineUriPathTemplate(request);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void determineUriPathTemplate_returns_null_if_passed_null() {
        // expect
        assertThat(HttpSpanFactory.determineUriPathTemplate(null)).isNull();
    }

    @DataProvider(value = {
        "GET            |   /some/http/route    |   /some/spring/pattern    |   GET /some/http/route",
        "GET            |   /some/http/route    |   null                    |   GET /some/http/route",
        "GET            |   /some/http/route    |                           |   GET /some/http/route",
        "GET            |   /some/http/route    |   [whitespace]            |   GET /some/http/route",
        "GET            |   null                |   /some/spring/pattern    |   GET /some/spring/pattern",
        "GET            |                       |   /some/spring/pattern    |   GET /some/spring/pattern",
        "GET            |   [whitespace]        |   /some/spring/pattern    |   GET /some/spring/pattern",
        "GET            |   null                |   null                    |   GET",
        "GET            |                       |                           |   GET",
        "GET            |   [whitespace]        |   [whitespace]            |   GET",
        "null           |   /some/http/route    |   null                    |   UNKNOWN_HTTP_METHOD /some/http/route",
        "               |   /some/http/route    |   null                    |   UNKNOWN_HTTP_METHOD /some/http/route",
        "[whitespace]   |   /some/http/route    |   null                    |   UNKNOWN_HTTP_METHOD /some/http/route",
        "null           |   null                |   /some/spring/pattern    |   UNKNOWN_HTTP_METHOD /some/spring/pattern",
        "               |   null                |   /some/spring/pattern    |   UNKNOWN_HTTP_METHOD /some/spring/pattern",
        "[whitespace]   |   null                |   /some/spring/pattern    |   UNKNOWN_HTTP_METHOD /some/spring/pattern",
        "null           |   /some/http/route    |   /some/spring/pattern    |   UNKNOWN_HTTP_METHOD /some/http/route",
        "null           |   null                |   null                    |   UNKNOWN_HTTP_METHOD",
        "               |                       |                           |   UNKNOWN_HTTP_METHOD",
        "[whitespace]   |   [whitespace]        |   [whitespace]            |   UNKNOWN_HTTP_METHOD",
    }, splitBy = "\\|")
    @Test
    public void getSpanName_works_as_expected(
        String httpMethod,
        String httpRouteRequestAttr,
        String springMatchingPatternRequestAttr,
        String expectedResult
    ) {
        // given
        if ("[whitespace]".equals(httpMethod)) {
            httpMethod = "  \t\r\n  ";
        }

        if ("[whitespace]".equals(httpRouteRequestAttr)) {
            httpRouteRequestAttr = "  \t\r\n  ";
        }

        if ("[whitespace]".equals(springMatchingPatternRequestAttr)) {
            springMatchingPatternRequestAttr = "  \t\r\n  ";
        }

        doReturn(httpMethod).when(request).getMethod();
        doReturn(httpRouteRequestAttr).when(request).getAttribute(KnownZipkinTags.HTTP_ROUTE);
        doReturn(springMatchingPatternRequestAttr)
            .when(request).getAttribute(HttpSpanFactory.SPRING_BEST_MATCHING_PATTERN_REQUEST_ATTRIBUTE_KEY);

        // when
        String result = HttpSpanFactory.getSpanName(request);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getSpanName_returns_UNKNOWN_HTTP_METHOD_when_passed_null_request() {
        // expect
        assertThat(HttpSpanFactory.getSpanName(null)).isEqualTo("UNKNOWN_HTTP_METHOD");
    }
}
