package com.nike.wingtips.http;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.nike.wingtips.TraceHeaders;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests the functionality of {@link HttpRequestTracingUtils}
 */
@RunWith(DataProviderRunner.class)
public class HttpRequestTracingUtilsTest {

    private String sampleTraceID = TraceAndSpanIdGenerator.generateId();
    private String sampleSpanID = TraceAndSpanIdGenerator.generateId();
    private String sampleParentSpanID = TraceAndSpanIdGenerator.generateId();
    @SuppressWarnings("FieldCanBeLocal")
    private String userId = "userId";
    private RequestWithHeaders request;
    
    private static final String USER_ID_HEADER_KEY = "userid";
    private static final String ALT_USER_ID_HEADER_KEY = "altuserid";

    private static final List<String> USER_ID_HEADER_KEYS = Arrays.asList(USER_ID_HEADER_KEY, ALT_USER_ID_HEADER_KEY);

    @Before
    public void onSetup() {
        request = mock(RequestWithHeaders.class);
    }

    @Test
    public void constructor_is_private() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<HttpRequestTracingUtils> defaultConstructor = HttpRequestTracingUtils.class.getDeclaredConstructor();
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
        HttpRequestTracingUtils instance = defaultConstructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    public void fromRequestWithHeaders_generates_span_from_headers_in_request() {
        // given: a set of standard span header values
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(Boolean.TRUE.toString());
        given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn(sampleSpanID);
        given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(sampleParentSpanID);
        given(request.getHeader(USER_ID_HEADER_KEY)).willReturn(userId);

        // when: creating span object from HTTP request
        Span goodSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then: ensure span object gets identical values from corresponding headers, and the span purpose is set to SERVER
        assertThat(goodSpan.getTraceId()).isEqualTo(sampleTraceID);
        assertThat(goodSpan.isSampleable()).isTrue();
        assertThat(goodSpan.getSpanId()).isEqualTo(sampleSpanID);
        assertThat(goodSpan.getParentSpanId()).isEqualTo(sampleParentSpanID);
        assertThat(goodSpan.getUserId()).isEqualTo(userId);
        assertThat(goodSpan.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
    }

    @Test
    public void fromRequestWithHeaders_generates_span_from_headers_in_request_for_all_user_id_header_keys() {
        // Verify more than 1 distinct user ID header key.
        assertThat(new HashSet<>(USER_ID_HEADER_KEYS).size()).isGreaterThan(1);
        for (String userIdHeaderKey : USER_ID_HEADER_KEYS) {
            // given: a set of standard span header values for the header key
            String userIdValue = UUID.randomUUID().toString();
            RequestWithHeaders request = mock(RequestWithHeaders.class);
            given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
            given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(Boolean.TRUE.toString());
            given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn(sampleSpanID);
            given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(sampleParentSpanID);
            given(request.getHeader(userIdHeaderKey)).willReturn(userIdValue);

            // when: creating span object from HTTP request
            Span goodSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

            // then: ensure span object gets identical values from corresponding headers, and sets the span purpose to SERVER
            assertThat(goodSpan.getTraceId()).isEqualTo(sampleTraceID);
            assertThat(goodSpan.isSampleable()).isTrue();
            assertThat(goodSpan.getSpanId()).isEqualTo(sampleSpanID);
            assertThat(goodSpan.getParentSpanId()).isEqualTo(sampleParentSpanID);
            assertThat(goodSpan.getUserId()).isEqualTo(userIdValue);
            assertThat(goodSpan.getSpanPurpose()).isEqualTo(SpanPurpose.SERVER);
        }
    }

    @Test
    public void fromRequestWithHeaders_returns_null_if_passed_null_request() {
        // expect
        assertThat(HttpRequestTracingUtils.fromRequestWithHeaders(null, USER_ID_HEADER_KEYS)).isNull();
    }

    @Test
    public void fromRequestWithHeaders_generates_valid_span_with_minimum_of_trace_id() {
        // given
        String traceId = UUID.randomUUID().toString();
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(traceId);

        // when
        Span result = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTraceId()).isEqualTo(traceId);
        assertThat(result.getSpanId()).isNotEmpty();
    }

    @Test
    public void fromRequestWithHeaders_returns_null_if_request_is_missing_trace_id_header() {
        // given
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(null);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(null);
        given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn(null);
        given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(null);

        // when
        Span newSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then
        assertThat(newSpan).isNull();
    }

    @DataProvider
    public static Object[][] nullAndEmptyStrings() {
        return new Object[][] {
                { null },
                { "" },
                { " " },
                { " \t \n  " }
        };
    }

    @Test
    @UseDataProvider("nullAndEmptyStrings")
    public void fromRequestWithHeaders_sets_span_name_to_unspecified_value_if_span_name_is_missing_or_empty(String nullOrEmptySpanName) {
        // given
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(UUID.randomUUID().toString());
        given(request.getHeader(TraceHeaders.SPAN_NAME)).willReturn(nullOrEmptySpanName);
        given(request.getAttribute(TraceHeaders.SPAN_NAME)).willReturn(nullOrEmptySpanName);

        // when
        Span result = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then
        assertThat(result.getSpanName()).isEqualTo(HttpRequestTracingUtils.UNSPECIFIED_SPAN_NAME);
    }

    @Test
    public void getUserIdFromRequestWithHeaders_returns_null_if_passed_null_request() {
        // expect
        assertThat(HttpRequestTracingUtils.getUserIdFromRequestWithHeaders(null, USER_ID_HEADER_KEYS)).isNull();
    }

    @Test
    public void fromRequestWithHeaders_sets_user_id_to_null_if_passed_null_or_empty_userIdHeaderKeys_list() {
        // given
        String traceId = UUID.randomUUID().toString();
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(traceId);
        List<List<String>> badLists = Arrays.asList(null, Collections.<String>emptyList());

        for (List<String> badList : badLists) {
            // when
            Span result = HttpRequestTracingUtils.fromRequestWithHeaders(request, badList);

            // expect
            assertThat(result.getTraceId()).isEqualTo(traceId);
            assertThat(result.getUserId()).isNull();
        }
    }

    @Test
    @UseDataProvider("nullAndEmptyStrings")
    public void fromRequestWithHeaders_sets_user_id_to_null_if_request_returns_null_or_empty_for_user_id(String nullOrEmptyUserId) {
        // given
        String traceId = UUID.randomUUID().toString();
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(traceId);
        given(request.getHeader(USER_ID_HEADER_KEY)).willReturn(nullOrEmptyUserId);
        given(request.getAttribute(USER_ID_HEADER_KEY)).willReturn(nullOrEmptyUserId);

        // when
        Span result = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // expect
        assertThat(result.getTraceId()).isEqualTo(traceId);
        assertThat(result.getUserId()).isNull();
    }

    @Test
    @UseDataProvider("nullAndEmptyStrings")
    public void fromRequestWithHeaders_sets_sampleable_to_true_if_sampled_header_is_null_or_missing_from_both_headers_and_attributes(String nullOrEmptySampledString) {
        // given: request where the request header and attribute for TRACE_SAMPLED returns null or empty string
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(nullOrEmptySampledString);
        given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(nullOrEmptySampledString);

        // when: creating span from request
        Span newSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then: new span should be sampleable
        assertThat(newSpan.isSampleable()).isTrue();
    }

    @Test
    public void fromRequestWithHeaders_sets_sampleable_to_attribute_value_if_sampled_header_is_missing_but_attribute_exists() {
        // given: request where the header for TRACE_SAMPLED returns null but the attribute returns false
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(null);
        given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(false);

        // when: creating span from request
        Span newSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then: new span should be disabled
        assertThat(newSpan.isSampleable()).isFalse();
    }

    @Test
    public void fromRequestWithHeaders_sets_sampleable_to_attribute_value_if_sampled_header_is_empty_but_attribute_exists() {
        // given: request where the header for TRACE_SAMPLED returns null but the attribute returns false
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(" ");
        given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(false);

        // when: creating span from request
        Span newSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then: new span should be disabled
        assertThat(newSpan.isSampleable()).isFalse();
    }

    @DataProvider(value = {
        "0      |   false",
        "1      |   true",
        "2      |   true",
        "42     |   true",
        "false  |   false",
        "FALSE  |   false",
        "FaLsE  |   false",
        "true   |   true",
        "TRUE   |   true",
        "TrUe   |   true",
        "true   |   true",
        "bad    |   true",
    }, splitBy = "\\|")
    @Test
    public void fromRequestWithHeaders_extracts_sampleable_as_expected(String receivedValue, boolean expectedSampleableResult) {
        // Verify via headers
        {
            // given
            given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
            given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(receivedValue);
            given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(null);

            // when
            Span newSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

            // then
            assertThat(newSpan.isSampleable()).isEqualTo(expectedSampleableResult);
        }

        // Verify via attribute
        {
            // given
            given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(sampleTraceID);
            given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(null);
            given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(receivedValue);

            // when
            Span newSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

            // then
            assertThat(newSpan.isSampleable()).isEqualTo(expectedSampleableResult);
        }
    }

    @Test
    public void fromRequestWithHeaders_uses_headers_first_even_if_attributes_exist() {
        // given: request that has both headers and attributes set
        String headerTraceId = UUID.randomUUID().toString();
        String headerSpanId = UUID.randomUUID().toString();
        String headerParentSpanId = UUID.randomUUID().toString();
        String headerSpanName = UUID.randomUUID().toString();
        String headerUserId = UUID.randomUUID().toString();
        String headerTraceSampled = "false";
        String attributeTraceId = UUID.randomUUID().toString();
        String attributeSpanId = UUID.randomUUID().toString();
        String attributeParentSpanId = UUID.randomUUID().toString();
        String attributeSpanName = UUID.randomUUID().toString();
        String attributeUserId = UUID.randomUUID().toString();
        String attributeTraceSampled = "true";
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(headerTraceId);
        given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn(headerSpanId);
        given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(headerParentSpanId);
        given(request.getHeader(TraceHeaders.SPAN_NAME)).willReturn(headerSpanName);
        given(request.getHeader(USER_ID_HEADER_KEY)).willReturn(headerUserId);
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn(headerTraceSampled);
        given(request.getAttribute(TraceHeaders.TRACE_ID)).willReturn(attributeTraceId);
        given(request.getAttribute(TraceHeaders.SPAN_ID)).willReturn(attributeSpanId);
        given(request.getAttribute(TraceHeaders.PARENT_SPAN_ID)).willReturn(attributeParentSpanId);
        given(request.getAttribute(TraceHeaders.SPAN_NAME)).willReturn(attributeSpanName);
        given(request.getAttribute(USER_ID_HEADER_KEY)).willReturn(attributeUserId);
        given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(attributeTraceSampled);

        // when: creating span from request
        Span newSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then: all values will be from the headers, not the attributes
        assertThat(newSpan.getTraceId()).isEqualTo(headerTraceId);
        assertThat(newSpan.getSpanId()).isEqualTo(headerSpanId);
        assertThat(newSpan.getParentSpanId()).isEqualTo(headerParentSpanId);
        assertThat(newSpan.getSpanName()).isEqualTo(headerSpanName);
        assertThat(newSpan.getUserId()).isEqualTo(headerUserId);
        assertThat(String.valueOf(newSpan.isSampleable())).isEqualTo(headerTraceSampled);
    }

    @Test
    public void fromRequestWithHeaders_uses_attributes_as_backups_when_headers_do_not_exist() {
        // given: request that has only attributes set (no headers)
        String attributeTraceId = UUID.randomUUID().toString();
        String attributeSpanId = UUID.randomUUID().toString();
        String attributeParentSpanId = UUID.randomUUID().toString();
        String attributeSpanName = UUID.randomUUID().toString();
        String attributeUserId = UUID.randomUUID().toString();
        String attributeTraceSampled = "false";
        given(request.getAttribute(TraceHeaders.TRACE_ID)).willReturn(attributeTraceId);
        given(request.getAttribute(TraceHeaders.SPAN_ID)).willReturn(attributeSpanId);
        given(request.getAttribute(TraceHeaders.PARENT_SPAN_ID)).willReturn(attributeParentSpanId);
        given(request.getAttribute(TraceHeaders.SPAN_NAME)).willReturn(attributeSpanName);
        given(request.getAttribute(USER_ID_HEADER_KEY)).willReturn(attributeUserId);
        given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(attributeTraceSampled);

        // when: creating span from request
        Span newSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then: all values will be from the attributes
        assertThat(newSpan.getTraceId()).isEqualTo(attributeTraceId);
        assertThat(newSpan.getSpanId()).isEqualTo(attributeSpanId);
        assertThat(newSpan.getParentSpanId()).isEqualTo(attributeParentSpanId);
        assertThat(newSpan.getSpanName()).isEqualTo(attributeSpanName);
        assertThat(newSpan.getUserId()).isEqualTo(attributeUserId);
        assertThat(String.valueOf(newSpan.isSampleable())).isEqualTo(attributeTraceSampled);
    }

    @Test
    public void fromRequestWithHeaders_uses_attributes_as_backups_when_headers_are_empty() {
        // given: request that has both headers and attributes set, but the headers are empty or whitespace-only
        String attributeTraceId = UUID.randomUUID().toString();
        String attributeSpanId = UUID.randomUUID().toString();
        String attributeParentSpanId = UUID.randomUUID().toString();
        String attributeSpanName = UUID.randomUUID().toString();
        String attributeUserId = UUID.randomUUID().toString();
        String attributeTraceSampled = "false";
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(" ");
        given(request.getHeader(TraceHeaders.SPAN_ID)).willReturn("");
        given(request.getHeader(TraceHeaders.PARENT_SPAN_ID)).willReturn(" ");
        given(request.getHeader(TraceHeaders.SPAN_NAME)).willReturn(" \n \t ");
        given(request.getHeader(USER_ID_HEADER_KEY)).willReturn("\t\n");
        given(request.getHeader(TraceHeaders.TRACE_SAMPLED)).willReturn("");
        given(request.getAttribute(TraceHeaders.TRACE_ID)).willReturn(attributeTraceId);
        given(request.getAttribute(TraceHeaders.SPAN_ID)).willReturn(attributeSpanId);
        given(request.getAttribute(TraceHeaders.PARENT_SPAN_ID)).willReturn(attributeParentSpanId);
        given(request.getAttribute(TraceHeaders.SPAN_NAME)).willReturn(attributeSpanName);
        given(request.getAttribute(USER_ID_HEADER_KEY)).willReturn(attributeUserId);
        given(request.getAttribute(TraceHeaders.TRACE_SAMPLED)).willReturn(attributeTraceSampled);

        // when: creating span from request
        Span newSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then: all values will be from the attributes, not the headers
        assertThat(newSpan.getTraceId()).isEqualTo(attributeTraceId);
        assertThat(newSpan.getSpanId()).isEqualTo(attributeSpanId);
        assertThat(newSpan.getParentSpanId()).isEqualTo(attributeParentSpanId);
        assertThat(newSpan.getSpanName()).isEqualTo(attributeSpanName);
        assertThat(newSpan.getUserId()).isEqualTo(attributeUserId);
        assertThat(String.valueOf(newSpan.isSampleable())).isEqualTo(attributeTraceSampled);
    }

    @Test
    public void fromRequestWithHeaders_gets_fresh_span_ids_even_with_same_trace_id() {
        // given: a request with a trace ID
        String traceId = UUID.randomUUID().toString();
        given(request.getHeader(TraceHeaders.TRACE_ID)).willReturn(traceId);

        // when: we use it to create multiple spans
        Span firstSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);
        Span secondSpan = HttpRequestTracingUtils.fromRequestWithHeaders(request, USER_ID_HEADER_KEYS);

        // then: each span gets its own unique span ID
        assertThat(firstSpan.getTraceId()).isEqualTo(traceId);
        assertThat(secondSpan.getTraceId()).isEqualTo(traceId);
        assertThat(firstSpan.getSpanId()).isNotEmpty();
        assertThat(secondSpan.getSpanId()).isNotEmpty();
        assertThat(firstSpan.getSpanId()).isNotEqualTo(secondSpan.getTraceId());
    }

}
