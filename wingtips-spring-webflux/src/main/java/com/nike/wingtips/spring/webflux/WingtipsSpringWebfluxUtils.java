package com.nike.wingtips.spring.webflux;

import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.util.TracingState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.server.ServerWebExchange;

import reactor.util.context.Context;

/**
 * Contains some static helper methods for working with Spring WebFlux.
 *
 * @author Nic Munroe
 */
public class WingtipsSpringWebfluxUtils {

    // Intentionally protected - all access should be through the static methods.
    protected WingtipsSpringWebfluxUtils() {
        // Do nothing.
    }

    /**
     * Adds the appropriate tracing info to the given {@link ServerWebExchange}'s request attributes. This includes
     * {@link TraceHeaders#TRACE_ID} (attr key) for the trace ID string (attr value), {@link TraceHeaders#SPAN_ID}
     * (attr key) for the span ID string (attr value), and {@code TracingState.class.getName()} (attr key) for the
     * {@link TracingState} (attr value) associated with the overall server request.
     *
     * <p>Intended for use by {@link com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter
     * WingtipsSpringWebfluxWebFilter} once the tracing state for the request has been setup.
     *
     * @param tracingState The {@link TracingState} associated with the overall server request.
     * @param overallRequestSpan The overall request span associated with the overall server request (this should
     * match the given {@link TracingState}'s current span).
     * @param exchange The {@link ServerWebExchange} associated with the server request.
     */
    public static void addTracingInfoToRequestAttributes(
        @NotNull TracingState tracingState,
        @NotNull Span overallRequestSpan,
        @NotNull ServerWebExchange exchange
    ) {
        exchange.getAttributes().put(TraceHeaders.TRACE_ID, overallRequestSpan.getTraceId());
        exchange.getAttributes().put(TraceHeaders.SPAN_ID, overallRequestSpan.getSpanId());
        exchange.getAttributes().put(TracingState.class.getName(), tracingState);
    }

    /**
     * Use this to pull the request's tracing state from the given {@link ServerWebExchange}. This should always
     * be available if you're using {@link com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter
     * WingtipsSpringWebfluxWebFilter}.
     *
     * @param exchange The {@link ServerWebExchange} to extract the {@link TracingState} from.
     * @return The {@link TracingState} pulled from the given {@link ServerWebExchange#getAttribute(String)}
     * (using {@code TracingState.class.getName()} as the attr key), or null if there was no tracing state attr.
     */
    public static @Nullable TracingState tracingStateFromExchange(@NotNull ServerWebExchange exchange) {
        return exchange.getAttribute(TracingState.class.getName());
    }

    /**
     * Uses {@link Context#put(Object, Object)} to return a new {@link Context} that contains
     * {@code TracingState.class} as a key, and the given {@link TracingState} as the value for that key.
     *
     * <p>You can use {@link #tracingStateFromContext(Context)} as a convenience helper method for pulling
     * the {@link TracingState} out of a {@link Context}.
     *
     * @param origContext The original {@link Context} that you want tracing state added to.
     * @param tracingState The {@link TracingState} to add to the given {@link Context}.
     * @return A new {@link Context} that matches the original, but also contains a new key/value pair of
     * {@code TracingState.class} -> {@link TracingState}.
     */
    public static @NotNull Context subscriberContextWithTracingInfo(
        @NotNull Context origContext,
        @NotNull TracingState tracingState
    ) {
        return origContext.put(TracingState.class, tracingState);
    }

    /**
     * @param context The {@link Context} that may contain a {@code TracingState.class} -> {@link TracingState}
     * key/value pair.
     * @return The {@link TracingState} pulled from the given {@link Context} (using {@code TracingState.class} as
     * the key), or null if there was no tracing state key/value pair.
     */
    public static @Nullable TracingState tracingStateFromContext(@NotNull Context context) {
        return context.getOrDefault(TracingState.class, null);
    }

}
