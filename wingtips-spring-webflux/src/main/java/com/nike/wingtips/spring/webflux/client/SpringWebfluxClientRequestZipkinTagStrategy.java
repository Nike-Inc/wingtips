package com.nike.wingtips.spring.webflux.client;

import com.nike.wingtips.Span;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;

import org.jetbrains.annotations.NotNull;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * An extension of {@link ZipkinHttpTagStrategy} intended for use with {@link
 * WingtipsSpringWebfluxExchangeFilterFunction}. This will add the {@link #SPRING_LOG_ID_TAG_KEY} tag to the span
 * (with a value of the request's {@link ClientRequest#logPrefix()}) in addition to all the usual Zipkin tags.
 * This extra tag allows you to correlate the span with certain log messages that Spring might output as part of a
 * Webflux {@link org.springframework.web.reactive.function.client.WebClient WebClient} call. This is especially
 * useful when those log messages are output on different threads that aren't hooked up with the Wingtips tracing
 * state, and therefore aren't tagged with the trace ID.
 *
 * @author Nic Munroe
 */
public class SpringWebfluxClientRequestZipkinTagStrategy extends ZipkinHttpTagStrategy<ClientRequest, ClientResponse> {

    /**
     * The key for a tag key/value pair, where the value is the request's {@link ClientRequest#logPrefix()}.
     */
    public static final String SPRING_LOG_ID_TAG_KEY = "webflux_log_id";

    protected static final SpringWebfluxClientRequestZipkinTagStrategy DEFAULT_INSTANCE =
        new SpringWebfluxClientRequestZipkinTagStrategy();

    /**
     * @return A reusable, thread-safe, singleton instance of this class that can be used by anybody who wants to use
     * this class and does not need any customization.
     */
    public static SpringWebfluxClientRequestZipkinTagStrategy getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    protected void doHandleRequestTagging(
        @NotNull Span span,
        @NotNull ClientRequest request,
        @NotNull HttpTagAndSpanNamingAdapter<ClientRequest, ?> adapter
    ) {
        super.doHandleRequestTagging(span, request, adapter);
        putTagIfValueIsNotBlank(span, SPRING_LOG_ID_TAG_KEY, request.logPrefix().trim());
    }
}
