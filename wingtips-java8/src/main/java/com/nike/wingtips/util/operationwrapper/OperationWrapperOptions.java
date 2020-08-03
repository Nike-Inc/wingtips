package com.nike.wingtips.util.operationwrapper;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.tags.KnownZipkinTags;
import com.nike.wingtips.util.AsyncWingtipsHelper;
import com.nike.wingtips.util.AsyncWingtipsHelperStatic;
import com.nike.wingtips.util.TracingState;
import com.nike.wingtips.util.spantagger.ErrorSpanTagger;
import com.nike.wingtips.util.spantagger.SpanTagger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * A class representing options for the {@code wrap[Operation]WithSpan(...)} methods found in {@link
 * AsyncWingtipsHelperStatic} and {@link AsyncWingtipsHelper}. These options control how the span surrounding the
 * operation is created and handled, including span name, span purpose, extra tagging, error tagging, and the tracing
 * state you want to be the parent of the span.
 *
 * <p>This class must be built using the {@link Builder}. You can create a builder using the {@link
 * #newBuilder(String, SpanPurpose)} factory method or directly using the builder's {@link
 * Builder#Builder(String, SpanPurpose) constructor}. Set options on the builder using the builder's {@code with*(...)}
 * methods, and then generate the {@link OperationWrapperOptions} using {@link Builder#build()}.
 *
 * <p>The methods where this class is used include:
 * <ul>
 *     <li>{@link AsyncWingtipsHelperStatic#wrapCompletableFutureWithSpan(OperationWrapperOptions, Supplier)}</li>
 *     <li>{@link AsyncWingtipsHelperStatic#wrapCallableWithSpan(OperationWrapperOptions, Callable)}</li>
 *     <li>{@link AsyncWingtipsHelperStatic#wrapSupplierWithSpan(OperationWrapperOptions, Supplier)}</li>
 *     <li>{@link AsyncWingtipsHelperStatic#wrapRunnableWithSpan(OperationWrapperOptions, Runnable)}</li>
 *     <li>
 *         {@link AsyncWingtipsHelperStatic#wrapCheckedRunnableWithSpan(OperationWrapperOptions,
 *         AsyncWingtipsHelper.CheckedRunnable)}
 *     </li>
 * </ul>
 *
 * @param <T> The type parameter for the operation, if any. e.g. if you were wrapping a {@code
 * CompletableFuture<Foo>}, then this type parameter would be {@code Foo}.
 */
public class OperationWrapperOptions<T> {

    /**
     * The {@link Span#getSpanName()} that will be used for the span surrounding the operation. Cannot be null.
     */
    public final @NotNull String operationSpanName;
    /**
     * The {@link SpanPurpose} that will be used for the span surrounding the operation. Cannot be null.
     */
    public final @NotNull SpanPurpose spanPurpose;
    /**
     * The {@link TracingState} that will be used as the parent for the span surrounding the operation.
     * May be null - if this is null then the tracing state that's on the thread when the {@code
     * wrap[Operation]WithSpan(...)} method is called is what will be used as the parent. If this is null, and
     * the thread has no tracing state, then a new root span (new trace) will be created instead as there is no
     * parent to use in that case.
     */
    public final @Nullable TracingState parentTracingState;
    /**
     * This is used to add any arbitrary extra tags you want to the span surrounding the operation. {@link
     * SpanTagger#tagSpan(Span, Object)} will be called with the span surrounding the operation and the
     * result of the operation (if any) when the operation completes, just before the span is completed.
     * May be null - if this is null then no extra tagging will be performed.
     *
     * <p>NOTE: {@link SpanTagger#tagSpan(Span, Object)} will always be called, even if the operation fails with an
     * exception (in that case {@link #errorTagger} would also be executed). Therefore the {@link SpanTagger} you
     * provide must gracefully handle a null payload (the span will always be non-null).
     */
    public final @Nullable SpanTagger<T> spanTagger;
    /**
     * This is used to perform any error tagging on the span surrounding the operation in the case where the operation
     * fails with an exception. {@link ErrorSpanTagger#tagSpanForError(Span, Throwable)} will be called with
     * the span surrounding the operation and the exception produced by the operation, just before the span is
     * completed. This defaults to {@link ErrorSpanTagger#DEFAULT_IMPL}, which will add a {@link KnownZipkinTags#ERROR}
     * tag that has the value of {@link Throwable#toString()}. This is sufficient for most cases, so you
     * can usually leave the default error tagger as-is.
     *
     * <p>NOTE: {@link ErrorSpanTagger#tagSpanForError(Span, Throwable)} will only be called if the operation fails
     * with an exception. If the operation completes normally then this error tagger will not be called. This is in
     * contrast to {@link #spanTagger}, which will always execute whether the operation completes normally or with
     * an exception.
     */
    public final @Nullable ErrorSpanTagger errorTagger;

    /**
     * Creates a new instance using the values stored in the given builder.
     *
     * @param builder The builder to use to pull the options from.
     */
    public OperationWrapperOptions(@NotNull Builder<T> builder) {
        this.operationSpanName = builder.operationSpanName;
        this.spanPurpose = builder.spanPurpose;
        this.parentTracingState = builder.parentTracingState;
        this.spanTagger = builder.spanTagger;
        this.errorTagger = builder.errorTagger;
    }

    /**
     * Creates a new builder for this class with the given span name and span purpose, which are required and must
     * be non-null.
     *
     * @param operationSpanName The {@link Span#getSpanName()} you want for the span surrounding the
     * operation - cannot be null.
     * @param spanPurpose The {@link SpanPurpose} you want for the span surrounding the operation - cannot be null.
     * @param <T> The type parameter for the operation, if any. e.g. if you were wrapping a {@code
     * CompletableFuture<Foo>}, then this type parameter would be {@code Foo}.
     * @return A new {@link Builder} with the given span name and span purpose.
     */
    public static <T> Builder<T> newBuilder(@NotNull String operationSpanName, @NotNull SpanPurpose spanPurpose) {
        return new Builder<>(operationSpanName, spanPurpose);
    }

    /**
     * The builder for {@link OperationWrapperOptions}.
     *
     * @param <T> The type parameter for the operation, if any. e.g. if you were wrapping a {@code
     * CompletableFuture<Foo>}, then this type parameter would be {@code Foo}.
     */
    public static class Builder<T> {
        protected final @NotNull String operationSpanName;
        protected final @NotNull SpanPurpose spanPurpose;
        protected @Nullable TracingState parentTracingState;
        protected @Nullable SpanTagger<T> spanTagger;
        protected @Nullable ErrorSpanTagger errorTagger = ErrorSpanTagger.DEFAULT_IMPL;

        /**
         * Creates a new builder for {@link OperationWrapperOptions} with the given span name and span purpose, which
         * are required and must be non-null.
         *
         * @param operationSpanName The {@link Span#getSpanName()} you want for the span surrounding the
         * operation - cannot be null.
         * @param spanPurpose The {@link SpanPurpose} you want for the span surrounding the operation - cannot be null.
         */
        public Builder(@NotNull String operationSpanName, @NotNull SpanPurpose spanPurpose) {
            if (StringUtils.isBlank(operationSpanName)) {
                throw new IllegalArgumentException("operationSpanName cannot be null, empty, or blank");
            }

            //noinspection ConstantConditions
            if (spanPurpose == null) {
                throw new NullPointerException("spanPurpose cannot be null");
            }

            this.operationSpanName = operationSpanName;
            this.spanPurpose = spanPurpose;
        }

        /**
         * Sets the {@link TracingState} that will be used as the parent for the span surrounding the operation.
         * May be null - if this is null then the tracing state that's on the thread when the {@code
         * wrap[Operation]WithSpan(...)} method is called is what will be used as the parent. If this is null, and
         * the thread has no tracing state, then a new root span (new trace) will be created instead as there is no
         * parent to use in that case.
         *
         * @param parentTracingState The {@link TracingState} you want to use as the parent for the span surrounding
         * the operation, or null if you want the tracing state on the thread when the {@code
         * wrap[Operation]WithSpan(...)} method is called to control the span's parent.
         * @return This builder instance.
         */
        public @NotNull Builder<T> withParentTracingState(
            @Nullable TracingState parentTracingState
        ) {
            this.parentTracingState = parentTracingState;
            return this;
        }

        /**
         * Sets the {@link SpanTagger} that will be used to add any arbitrary extra tags you want to the span
         * surrounding the operation. {@link SpanTagger#tagSpan(Span, Object)} will be called with the span
         * surrounding the operation and the result of the operation (if any) when the operation completes, just
         * before the span is completed. May be null - if this is null then no extra tagging will be performed.
         *
         * <p>NOTE: {@link SpanTagger#tagSpan(Span, Object)} will always be called, even if the operation fails with an
         * exception (in that case {@link #errorTagger} would also be executed). Therefore the {@link SpanTagger} you
         * provide must gracefully handle a null payload (the span will always be non-null).
         *
         * @param spanTagger The {@link SpanTagger} you want to perform tagging on the span surrounding the operation,
         * or null if you don't want any extra tagging performed.
         * @return This builder instance.
         */
        public @NotNull Builder<T> withSpanTagger(
            @Nullable SpanTagger<T> spanTagger
        ) {
            this.spanTagger = spanTagger;
            return this;
        }

        /**
         * Sets the {@link ErrorSpanTagger} that will be used to perform any error tagging on the span surrounding
         * the operation in the case where the operation fails with an exception. {@link
         * ErrorSpanTagger#tagSpanForError(Span, Throwable)} will be called with the span surrounding the operation
         * and the exception produced by the operation, just before the span is completed. This defaults to {@link
         * ErrorSpanTagger#DEFAULT_IMPL}, which will add a {@link KnownZipkinTags#ERROR} tag that has the value of
         * {@link Throwable#toString()}. This is sufficient for most cases, so you can usually leave the default
         * error tagger as-is.
         *
         * <p>NOTE: {@link ErrorSpanTagger#tagSpanForError(Span, Throwable)} will only be called if the operation fails
         * with an exception. If the operation completes normally then this error tagger will not be called. This is in
         * contrast to {@link #spanTagger}, which will always execute whether the operation completes normally or with
         * an exception.
         *
         * @param errorTagger The {@link ErrorSpanTagger} you want to perform error tagging on the span surrounding
         * the operation in the case where the operation fails with an exception, or null if you don't want any
         * error tagging performed.
         * @return This builder instance.
         */
        public @NotNull Builder<T> withErrorTagger(
            @Nullable ErrorSpanTagger errorTagger
        ) {
            this.errorTagger = errorTagger;
            return this;
        }

        /**
         * @return A new {@link OperationWrapperOptions} with its values set based on this builder instance.
         */
        public OperationWrapperOptions<T> build() {
            return new OperationWrapperOptions<>(this);
        }
    }

}
