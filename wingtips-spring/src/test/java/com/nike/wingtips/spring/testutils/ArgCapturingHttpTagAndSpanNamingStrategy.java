package com.nike.wingtips.spring.testutils;

import com.nike.wingtips.Span;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helper class that gives you a {@link HttpTagAndSpanNamingStrategy} that lets you know when methods are called
 * and what the args were. This is necessary because the entry methods to {@link HttpTagAndSpanNamingStrategy}
 * are final, so they can't be mocked.
 *
 * @author Nic Munroe
 */
public class ArgCapturingHttpTagAndSpanNamingStrategy
    extends HttpTagAndSpanNamingStrategy<HttpRequest, ClientHttpResponse> {

    private final AtomicReference<String> initialSpanName;
    private final AtomicBoolean initialSpanNameMethodCalled;
    private final AtomicBoolean requestTaggingMethodCalled;
    private final AtomicBoolean responseTaggingAndFinalSpanNameMethodCalled;
    private final AtomicReference<InitialSpanNameArgs> initialSpanNameArgs;
    private final AtomicReference<RequestTaggingArgs> requestTaggingArgs;
    private final AtomicReference<ResponseTaggingArgs> responseTaggingArgs;

    public ArgCapturingHttpTagAndSpanNamingStrategy(
        AtomicReference<String> initialSpanName,
        AtomicBoolean initialSpanNameMethodCalled,
        AtomicBoolean requestTaggingMethodCalled,
        AtomicBoolean responseTaggingAndFinalSpanNameMethodCalled,
        AtomicReference<InitialSpanNameArgs> initialSpanNameArgs,
        AtomicReference<RequestTaggingArgs> requestTaggingArgs,
        AtomicReference<ResponseTaggingArgs> responseTaggingArgs
    ) {
        this.initialSpanName = initialSpanName;
        this.initialSpanNameMethodCalled = initialSpanNameMethodCalled;
        this.requestTaggingMethodCalled = requestTaggingMethodCalled;
        this.responseTaggingAndFinalSpanNameMethodCalled = responseTaggingAndFinalSpanNameMethodCalled;
        this.initialSpanNameArgs = initialSpanNameArgs;
        this.requestTaggingArgs = requestTaggingArgs;
        this.responseTaggingArgs = responseTaggingArgs;
    }

    @Override
    protected @Nullable String doGetInitialSpanName(
        @NotNull HttpRequest request, @NotNull HttpTagAndSpanNamingAdapter adapter
    ) {
        initialSpanNameMethodCalled.set(true);
        initialSpanNameArgs.set(new InitialSpanNameArgs(request, adapter));
        return initialSpanName.get();
    }

    @Override
    protected void doHandleResponseAndErrorTagging(
        @NotNull Span span, @Nullable HttpRequest request, @Nullable ClientHttpResponse response,
        @Nullable Throwable error, @NotNull HttpTagAndSpanNamingAdapter adapter
    ) {
        responseTaggingAndFinalSpanNameMethodCalled.set(true);
        responseTaggingArgs.set(
            new ResponseTaggingArgs(span, request, response, error, adapter)
        );
    }

    @Override
    protected void doHandleRequestTagging(
        @NotNull Span span, @NotNull HttpRequest request, @NotNull HttpTagAndSpanNamingAdapter adapter
    ) {
        requestTaggingMethodCalled.set(true);
        requestTaggingArgs.set(new RequestTaggingArgs(span, request, adapter));
    }

    public static class InitialSpanNameArgs {

        public final HttpRequest request;
        public final HttpTagAndSpanNamingAdapter adapter;

        private InitialSpanNameArgs(
            HttpRequest request, HttpTagAndSpanNamingAdapter adapter
        ) {
            this.request = request;
            this.adapter = adapter;
        }

        public void verifyArgs(HttpRequest expectedRequest, HttpTagAndSpanNamingAdapter expectedAdapter) {
            assertThat(request).isSameAs(expectedRequest);
            assertThat(adapter).isSameAs(expectedAdapter);
        }
    }

    public static class RequestTaggingArgs {

        public final Span span;
        public final HttpRequest request;
        public final HttpTagAndSpanNamingAdapter adapter;

        private RequestTaggingArgs(
            Span span, HttpRequest request, HttpTagAndSpanNamingAdapter adapter
        ) {
            this.span = span;
            this.request = request;
            this.adapter = adapter;
        }

        public void verifyArgs(
            Span expectedSpan, HttpRequest expectedRequest, HttpTagAndSpanNamingAdapter expectedAdapter
        ) {
            assertThat(span).isSameAs(expectedSpan);
            assertThat(request).isSameAs(expectedRequest);
            assertThat(adapter).isSameAs(expectedAdapter);
        }
    }

    public static class ResponseTaggingArgs {

        public final Span span;
        public final HttpRequest request;
        public final ClientHttpResponse response;
        public final Throwable error;
        public final HttpTagAndSpanNamingAdapter adapter;

        private ResponseTaggingArgs(
            Span span, HttpRequest request, ClientHttpResponse response, Throwable error,
            HttpTagAndSpanNamingAdapter adapter
        ) {
            this.span = span;
            this.request = request;
            this.response = response;
            this.error = error;
            this.adapter = adapter;
        }

        public void verifyArgs(
            Span expectedSpan, HttpRequest expectedRequest, ClientHttpResponse expectedResponse,
            Throwable expectedError, HttpTagAndSpanNamingAdapter expectedAdapter
        ) {
            assertThat(span).isSameAs(expectedSpan);
            assertThat(request).isSameAs(expectedRequest);
            assertThat(response).isSameAs(expectedResponse);
            if (expectedError instanceof CancellationException) {
                // The SettableListenableFuture throws a *new* CancellationException every time get is called, so
                //      we can't do same-instance comparison. We can't even do object equals comparison. All we can
                //      do is instanceof and message comparison. :(
                assertThat(error).isInstanceOf(CancellationException.class)
                                 .hasMessage(expectedError.getMessage());
            }
            else{
                assertThat(error).isSameAs(expectedError);
            }
            assertThat(adapter).isSameAs(expectedAdapter);
        }
    }
}
