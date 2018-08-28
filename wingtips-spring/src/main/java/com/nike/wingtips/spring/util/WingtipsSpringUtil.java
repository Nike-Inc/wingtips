package com.nike.wingtips.spring.util;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.http.HttpRequestTracingUtils;
import com.nike.wingtips.spring.interceptor.WingtipsAsyncClientHttpRequestInterceptor;
import com.nike.wingtips.spring.interceptor.WingtipsClientHttpRequestInterceptor;
import com.nike.wingtips.spring.interceptor.tag.SpringHttpClientTagAdapter;
import com.nike.wingtips.spring.util.asynchelperwrapper.FailureCallbackWithTracing;
import com.nike.wingtips.spring.util.asynchelperwrapper.ListenableFutureCallbackWithTracing;
import com.nike.wingtips.spring.util.asynchelperwrapper.SuccessCallbackWithTracing;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.NoOpHttpTagAdapter;
import com.nike.wingtips.tags.NoOpHttpTagStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;
import com.nike.wingtips.util.TracingState;

import org.slf4j.MDC;
import org.springframework.http.HttpMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SuccessCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Deque;
import java.util.Map;

/**
 * Contains helper methods for integrating Wingtips in a Spring environment. In particular there are helpers for
 * creating a {@link RestTemplate} or {@link AsyncRestTemplate} with Wingtips tracing interceptors ({@link
 * WingtipsClientHttpRequestInterceptor} or {@link WingtipsAsyncClientHttpRequestInterceptor}) already applied.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
public class WingtipsSpringUtil {

    /**
     * Intentionally protected - use the static methods.
     */
    protected WingtipsSpringUtil() {
        // Do nothing
    }

    /**
     * @return A new {@link RestTemplate} instance with a {@link WingtipsClientHttpRequestInterceptor} already added
     * and configured to surround downstream calls with a subspan, and using the default
     * {@link HttpTagAndSpanNamingStrategy} and {@link HttpTagAndSpanNamingAdapter} ({@link ZipkinHttpTagStrategy} and
     * {@link SpringHttpClientTagAdapter}).
     */
    public static RestTemplate createTracingEnabledRestTemplate() {
        return createTracingEnabledRestTemplate(true);
    }

    /**
     * @param surroundCallsWithSubspan Pass in true to have the returned {@link RestTemplate} surround all calls with
     * a subspan and propagate the subspan's tracing info, or false to have only the current span propagated at the
     * time of the call (no subspan).
     * @return A new {@link RestTemplate} instance with a {@link WingtipsClientHttpRequestInterceptor} already added
     * and with the subspan option on or off depending on the value of the {@code surroundCallsWithSubspan} argument,
     * and using the default {@link HttpTagAndSpanNamingStrategy} and {@link HttpTagAndSpanNamingAdapter}
     * ({@link ZipkinHttpTagStrategy} and {@link SpringHttpClientTagAdapter}).
     */
    public static RestTemplate createTracingEnabledRestTemplate(boolean surroundCallsWithSubspan) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(
            new WingtipsClientHttpRequestInterceptor(surroundCallsWithSubspan)
        );
        return restTemplate;
    }

    /**
     * @param tagAndNamingStrategy The span tag and naming strategy to use - cannot be null. If you really want no
     * tag and naming strategy, then pass in {@link NoOpHttpTagStrategy#getDefaultInstance()}.
     * @param tagAndNamingAdapter The tag and naming adapter to use - cannot be null. If you really want no tag and
     * naming adapter, then pass in {@link NoOpHttpTagAdapter#getDefaultInstance()}.
     * @return A new {@link RestTemplate} instance with a {@link WingtipsClientHttpRequestInterceptor}
     * already added, and with the subspan option and tag/naming strategy and adapter set to the given arguments.
     */
    public static RestTemplate createTracingEnabledRestTemplate(
        boolean surroundCallsWithSubspan,
        HttpTagAndSpanNamingStrategy<HttpRequest, ClientHttpResponse> tagAndNamingStrategy,
        HttpTagAndSpanNamingAdapter<HttpRequest, ClientHttpResponse> tagAndNamingAdapter
    ) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(
            new WingtipsClientHttpRequestInterceptor(
                surroundCallsWithSubspan,
                tagAndNamingStrategy,
                tagAndNamingAdapter
            )
        );
        return restTemplate;
    }

    /**
     * @return A new {@link AsyncRestTemplate} instance with a {@link WingtipsAsyncClientHttpRequestInterceptor}
     * already added and configured to surround downstream calls with a subspan, and using the default
     * {@link HttpTagAndSpanNamingStrategy} and {@link HttpTagAndSpanNamingAdapter} ({@link ZipkinHttpTagStrategy} and
     * {@link SpringHttpClientTagAdapter}).
     */
    public static AsyncRestTemplate createTracingEnabledAsyncRestTemplate() {
        return createTracingEnabledAsyncRestTemplate(true);
    }

    /**
     * @param surroundCallsWithSubspan Pass in true to have the returned {@link AsyncRestTemplate} surround all calls
     * with a subspan and propagate the subspan's tracing info, or false to have only the current span propagated at
     * the time of the call (no subspan).
     * @return A new {@link AsyncRestTemplate} instance with a {@link WingtipsAsyncClientHttpRequestInterceptor}
     * already added and with the subspan option on or off depending on the value of the {@code
     * surroundCallsWithSubspan} argument, and using the default {@link HttpTagAndSpanNamingStrategy} and
     * {@link HttpTagAndSpanNamingAdapter} ({@link ZipkinHttpTagStrategy} and {@link SpringHttpClientTagAdapter}).
     */
    public static AsyncRestTemplate createTracingEnabledAsyncRestTemplate(boolean surroundCallsWithSubspan) {
        AsyncRestTemplate asyncRestTemplate = new AsyncRestTemplate();
        asyncRestTemplate.getInterceptors().add(
            new WingtipsAsyncClientHttpRequestInterceptor(surroundCallsWithSubspan)
        );
        return asyncRestTemplate;
    }
    
    /**
     * @param tagAndNamingStrategy The span tag and naming strategy to use - cannot be null. If you really want no
     * tag and naming strategy, then pass in {@link NoOpHttpTagStrategy#getDefaultInstance()}.
     * @param tagAndNamingAdapter The tag and naming adapter to use - cannot be null. If you really want no tag and
     * naming adapter, then pass in {@link NoOpHttpTagAdapter#getDefaultInstance()}.
     * @return A new {@link AsyncRestTemplate} instance with a {@link WingtipsAsyncClientHttpRequestInterceptor}
     * already added, and with the subspan option and tag/naming strategy and adapter set to the given arguments.
     */
    public static AsyncRestTemplate createTracingEnabledAsyncRestTemplate(
        boolean surroundCallsWithSubspan,
        HttpTagAndSpanNamingStrategy<HttpRequest, ClientHttpResponse> tagAndNamingStrategy,
        HttpTagAndSpanNamingAdapter<HttpRequest, ClientHttpResponse> tagAndNamingAdapter
    ) {
        AsyncRestTemplate asyncRestTemplate = new AsyncRestTemplate();
        asyncRestTemplate.getInterceptors().add(
            new WingtipsAsyncClientHttpRequestInterceptor(
                surroundCallsWithSubspan,
                tagAndNamingStrategy,
                tagAndNamingAdapter
            )
        );
        return asyncRestTemplate;
    }

    /**
     * Sets the tracing headers on the given {@link HttpMessage#getHeaders()} with values from the given {@link Span}.
     * Does nothing if any of the given arguments are null (i.e. it is safe to pass null, but nothing will happen).
     * Usually you'd want to use one of the interceptors to handle tracing propagation for you
     * ({@link WingtipsClientHttpRequestInterceptor} or {@link WingtipsAsyncClientHttpRequestInterceptor}), however
     * you can call this method to do manual propagation if needed.
     *
     * <p>This method conforms to the <a href="https://github.com/openzipkin/b3-propagation">B3 propagation spec</a>.
     *
     * @param httpMessage The {@link HttpMessage} to set tracing headers on. Can be null - if this is null then this
     * method will do nothing.
     * @param span The {@link Span} to get the tracing info from to set on the headers. Can be null - if this is null
     * then this method will do nothing.
     */
    public static void propagateTracingHeaders(HttpMessage httpMessage, Span span) {
        HttpHeadersForPropagation headersForPropagation = (httpMessage == null)
                                                          ? null
                                                          : new HttpHeadersForPropagation(httpMessage);
        HttpRequestTracingUtils.propagateTracingHeaders(headersForPropagation, span);
    }

    /**
     * @param method The HTTP method.
     * @return "UNKNOWN_HTTP_METHOD" if the method is null, otherwise {@link HttpMethod#name()}.
     */
    public static String getRequestMethodAsString(HttpMethod method) {
        if (method == null) {
            return "UNKNOWN_HTTP_METHOD";
        }

        return method.name();
    }

    /**
     * @return A {@link SuccessCallback} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    public static <T> SuccessCallback<T> successCallbackWithTracing(SuccessCallback<T> successCallback) {
        return new SuccessCallbackWithTracing<>(successCallback);
    }

    /**
     * @return A {@link SuccessCallback} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    public static <T> SuccessCallback<T> successCallbackWithTracing(
        SuccessCallback<T> successCallback,
        Pair<Deque<Span>, Map<String, String>> threadInfoToLink
    ) {
        return new SuccessCallbackWithTracing<>(successCallback, threadInfoToLink);
    }

    /**
     * @return A {@link SuccessCallback} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    public static <T> SuccessCallback<T> successCallbackWithTracing(
        SuccessCallback<T> successCallback,
        Deque<Span> spanStackToLink,
        Map<String, String> mdcContextMapToLink
    ) {
        return new SuccessCallbackWithTracing<>(successCallback, spanStackToLink, mdcContextMapToLink);
    }

    /**
     * @return A {@link FailureCallback} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    public static FailureCallback failureCallbackWithTracing(FailureCallback failureCallback) {
        return new FailureCallbackWithTracing(failureCallback);
    }

    /**
     * @return A {@link FailureCallback} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    public static FailureCallback failureCallbackWithTracing(FailureCallback failureCallback,
                                                             Pair<Deque<Span>, Map<String, String>> threadInfoToLink) {
        return new FailureCallbackWithTracing(failureCallback, threadInfoToLink);
    }

    /**
     * @return A {@link FailureCallback} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    public static FailureCallback failureCallbackWithTracing(FailureCallback failureCallback,
                                                             Deque<Span> spanStackToLink,
                                                             Map<String, String> mdcContextMapToLink) {
        return new FailureCallbackWithTracing(failureCallback, spanStackToLink, mdcContextMapToLink);
    }

    /**
     * @return A {@link ListenableFutureCallback} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    public static <T> ListenableFutureCallback<T> listenableFutureCallbackWithTracing(
        ListenableFutureCallback<T> listenableFutureCallback
    ) {
        return new ListenableFutureCallbackWithTracing<>(listenableFutureCallback);
    }

    /**
     * @return A {@link ListenableFutureCallback} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    public static <T> ListenableFutureCallback<T> listenableFutureCallbackWithTracing(
        ListenableFutureCallback<T> listenableFutureCallback,
        Pair<Deque<Span>, Map<String, String>> threadInfoToLink
    ) {
        return new ListenableFutureCallbackWithTracing<>(listenableFutureCallback, threadInfoToLink);
    }

    /**
     * @return A {@link ListenableFutureCallback} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    public static <T> ListenableFutureCallback<T> listenableFutureCallbackWithTracing(
        ListenableFutureCallback<T> listenableFutureCallback,
        Deque<Span> spanStackToLink,
        Map<String, String> mdcContextMapToLink
    ) {
        return new ListenableFutureCallbackWithTracing<>(listenableFutureCallback, spanStackToLink, mdcContextMapToLink);
    }
}
