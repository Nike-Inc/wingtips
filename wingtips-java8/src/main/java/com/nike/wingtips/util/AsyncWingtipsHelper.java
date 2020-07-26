package com.nike.wingtips.util;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.asynchelperwrapper.BiConsumerWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.BiFunctionWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.BiPredicateWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.ConsumerWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.ExecutorServiceWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.FunctionWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.PredicateWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.ScheduledExecutorServiceWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.SupplierWithTracing;
import com.nike.wingtips.util.operationwrapper.OperationWrapperOptions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Helper class that provides methods for dealing with async stuff in Wingtips, mainly providing easy ways
 * to support distributed tracing and logger MDC when hopping threads using {@link CompletableFuture}s, {@link
 * Executor}s, etc. There are various {@code *WithTracing(...)} methods for wrapping
 * objects with the same type that knows how to handle tracing and MDC. You can also call the various {@code
 * linkTracingToCurrentThread(...)} and {@code unlinkTracingFromCurrentThread(...)} methods directly if
 * you want to do it manually yourself.
 *
 * <p>NOTE: This is an interface to allow for custom implementations, however if you only ever need the default behavior
 * you can refer to {@link #DEFAULT_IMPL} for static access. There's also {@link AsyncWingtipsHelperStatic} that has
 * static methods with the same method signatures as this class that simply pass through to {@link #DEFAULT_IMPL},
 * allowing you to do static method imports to keep your code more readable at the expense of flexibility if you
 * ever want to use a non-default implementation.
 *
 * <p>Here's an example of making the current thread's tracing and MDC info hop to a thread executed by an {@link Executor}:
 * <pre>
 *   AsyncWingtipsHelper asyncHelper = AsyncWingtipsHelper.DEFAULT_IMPL;
 *   Executor executor = Executors.newSingleThreadExecutor();
 *
 *   executor.execute(asyncHelper.runnableWithTracing(() -> {
 *       // Code that needs tracing/MDC wrapping goes here
 *   }));
 * </pre>
 *
 * <p>Functionally equivalent to the {@link Executor} example above, but with a {@link ExecutorServiceWithTracing} to
 * automate the thread-hopping behavior whenever the {@link Executor} (or {@link ExecutorService}) executes something
 * (be careful about this if you spin off work that *shouldn't* automatically inherit the calling thread's tracing
 * state - see the warning on {@link #executorServiceWithTracing(ExecutorService)}):
 * <pre>
 *   AsyncWingtipsHelper asyncHelper = AsyncWingtipsHelper.DEFAULT_IMPL;
 *   Executor executor = asyncHelper.executorServiceWithTracing(Executors.newCachedThreadPool());
 *
 *   executor.execute(() -> {
 *       // Code that needs tracing/MDC wrapping goes here
 *   });
 * </pre>
 *
 * <p>And here's a similar example using {@link CompletableFuture}:
 * <pre>
 *   AsyncWingtipsHelper asyncHelper = AsyncWingtipsHelper.DEFAULT_IMPL;
 *
 *   CompletableFuture.supplyAsync(asyncHelper.supplierWithTracing(() -> {
 *       // Supplier code that needs tracing/MDC wrapping goes here.
 *       return foo;
 *   }));
 * </pre>
 *
 * <p>This example shows how you might accomplish tasks in an environment where the tracing information is attached
 * to some request context, and you need to temporarily attach the tracing info in order to do something (e.g. log some
 * messages with tracing info automatically added using MDC):
 * <pre>
 *     AsyncWingtipsHelper asyncHelper = AsyncWingtipsHelper.DEFAULT_IMPL;
 *     TracingState tracingInfo = requestContext.getTracingInfo();
 *
 *     asyncHelper.runnableWithTracing(
 *         () -> {
 *             // Code that needs tracing/MDC wrapping goes here
 *         },
 *         tracingInfo
 *     ).run();
 * </pre>
 *
 * <p>If you want to use the link and unlink methods manually to wrap some chunk of code, the general procedure looks
 * like this:
 * <pre>
 *  AsyncWingtipsHelper asyncHelper = AsyncWingtipsHelper.DEFAULT_IMPL;
 *  TracingState originalThreadInfo = null;
 *  try {
 *      originalThreadInfo = asyncHelper.linkTracingToCurrentThread(...);
 *      // Code that needs tracing/MDC wrapping goes here
 *  }
 *  finally {
 *      asyncHelper.unlinkTracingFromCurrentThread(originalThreadInfo);
 *  }
 * </pre>
 *
 * <p>Following this procedure (either the all-in-one {@code *WithTracing(...)} methods or the manual procedure)
 * guarantees that the code you want wrapped will be wrapped successfully with whatever tracing and MDC info you want,
 * and when it finishes the trace and MDC info will be put back the way it was as if your code never ran.
 *
 * <p>NOTE: If your class only needs one tracing-wrapped type then you can pull in the slightly less verbose static
 * helper methods directly from the class, e.g. {@code RunnableWithTracing.withTracing(...)}, and then your
 * code could be the more compact {@code withTracing(...)} rather than {@code runnableWithTracing(...)}. If you need
 * multiple tracing-wrapped types in the same class then the slightly longer-named methods in this helper class can
 * be used to disambiguate.
 * 
 * <p>WARNING: Be careful with the manual {@code linkTracingToCurrentThread(...)} method to link tracing and MDC.
 * If you fail to guarantee the associated unlink at the end then you risk having traces stomp on each other or having
 * other weird interactions occur that you wouldn't expect or predict. This can mess up your tracing, so before you use
 * the manual linking/unlinking procedure make sure you know what you're doing and test thoroughly in a multi-threaded
 * way under load, and with failure scenarios. For this reason it's recommended that you use the
 * {@code *WithTracing(...)} methods whenever possible instead of manual linking/unlinking.
 *
 * @author Nic Munroe
 */
public interface AsyncWingtipsHelper {

    /**
     * A statically-accessible default implementation of this interface. You can also use {@link
     * AsyncWingtipsHelperStatic} for static access if you know you only ever want the default behavior - that will
     * allow you to do static method imports to keep your code more readable at the expense of flexibility if you
     * ever want to use a non-default implementation.
     */
    AsyncWingtipsHelper DEFAULT_IMPL = new AsyncWingtipsHelperDefaultImpl();
    
    /**
     * @return A {@link Runnable} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    @SuppressWarnings("deprecation")
    default Runnable runnableWithTracing(Runnable runnable) {
        return AsyncWingtipsHelperJava7.runnableWithTracing(runnable);
    }

    /**
     * @return A {@link Runnable} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    @SuppressWarnings("deprecation")
    default Runnable runnableWithTracing(Runnable runnable,
                                         Pair<Deque<Span>, Map<String, String>> threadInfoToLink) {
        return AsyncWingtipsHelperJava7.runnableWithTracing(runnable, threadInfoToLink);
    }

    /**
     * @return A {@link Runnable} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    @SuppressWarnings("deprecation")
    default Runnable runnableWithTracing(Runnable runnable,
                                         Deque<Span> spanStackToLink,
                                         Map<String, String> mdcContextMapToLink) {
        return AsyncWingtipsHelperJava7.runnableWithTracing(
            runnable, spanStackToLink, mdcContextMapToLink
        );
    }

    /**
     * @return A {@link Callable} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    @SuppressWarnings("deprecation")
    default <U> Callable<U> callableWithTracing(Callable<U> callable) {
        return AsyncWingtipsHelperJava7.callableWithTracing(callable);
    }

    /**
     * @return A {@link Callable} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    @SuppressWarnings("deprecation")
    default <U> Callable<U> callableWithTracing(Callable<U> callable,
                                                Pair<Deque<Span>, Map<String, String>> threadInfoToLink) {
        return AsyncWingtipsHelperJava7.callableWithTracing(callable, threadInfoToLink);
    }

    /**
     * @return A {@link Callable} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    @SuppressWarnings("deprecation")
    default <U> Callable<U> callableWithTracing(Callable<U> callable,
                                                Deque<Span> spanStackToLink,
                                                Map<String, String> mdcContextMapToLink) {
        return AsyncWingtipsHelperJava7.callableWithTracing(
            callable, spanStackToLink, mdcContextMapToLink
        );
    }

    /**
     * @return A {@link Supplier} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    default <U> Supplier<U> supplierWithTracing(Supplier<U> supplier) {
        return new SupplierWithTracing<>(supplier);
    }

    /**
     * @return A {@link Supplier} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    default <U> Supplier<U> supplierWithTracing(Supplier<U> supplier,
                                                Pair<Deque<Span>, Map<String, String>> threadInfoToLink) {
        return new SupplierWithTracing<>(supplier, threadInfoToLink);
    }

    /**
     * @return A {@link Supplier} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    default <U> Supplier<U> supplierWithTracing(Supplier<U> supplier,
                                                Deque<Span> spanStackToLink,
                                                Map<String, String> mdcContextMapToLink) {
        return new SupplierWithTracing<>(supplier, spanStackToLink, mdcContextMapToLink);
    }

    /**
     * @return A {@link Function} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    default <T, U> Function<T, U> functionWithTracing(Function<T, U> fn) {
        return new FunctionWithTracing<>(fn);
    }

    /**
     * @return A {@link Function} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    default <T, U> Function<T, U> functionWithTracing(Function<T, U> fn,
                                                      Pair<Deque<Span>, Map<String, String>> threadInfoToLink) {
        return new FunctionWithTracing<>(fn, threadInfoToLink);
    }

    /**
     * @return A {@link Function} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    default <T, U> Function<T, U> functionWithTracing(Function<T, U> fn,
                                                      Deque<Span> spanStackToLink,
                                                      Map<String, String> mdcContextMapToLink) {
        return new FunctionWithTracing<>(fn, spanStackToLink, mdcContextMapToLink);
    }

    /**
     * @return A {@link BiFunction} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    default <T, U, R> BiFunction<T, U, R> biFunctionWithTracing(BiFunction<T, U, R> fn) {
        return new BiFunctionWithTracing<>(fn);
    }

    /**
     * @return A {@link BiFunction} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    default <T, U, R> BiFunction<T, U, R> biFunctionWithTracing(
        BiFunction<T, U, R> fn,
        Pair<Deque<Span>, Map<String, String>> threadInfoToLink
    ) {
        return new BiFunctionWithTracing<>(fn, threadInfoToLink);
    }

    /**
     * @return A {@link BiFunction} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    default <T, U, R> BiFunction<T, U, R> biFunctionWithTracing(BiFunction<T, U, R> fn,
                                                                Deque<Span> spanStackToLink,
                                                                Map<String, String> mdcContextMapToLink) {
        return new BiFunctionWithTracing<>(fn, spanStackToLink, mdcContextMapToLink);
    }

    /**
     * @return A {@link Consumer} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    default <T> Consumer<T> consumerWithTracing(Consumer<T> consumer) {
        return new ConsumerWithTracing<>(consumer);
    }

    /**
     * @return A {@link Consumer} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    default <T> Consumer<T> consumerWithTracing(Consumer<T> consumer,
                                                Pair<Deque<Span>, Map<String, String>> threadInfoToLink) {
        return new ConsumerWithTracing<>(consumer, threadInfoToLink);
    }

    /**
     * @return A {@link Consumer} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    default <T> Consumer<T> consumerWithTracing(Consumer<T> consumer,
                                                Deque<Span> spanStackToLink,
                                                Map<String, String> mdcContextMapToLink) {
        return new ConsumerWithTracing<>(consumer, spanStackToLink, mdcContextMapToLink);
    }

    /**
     * @return A {@link BiConsumer} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    default <T, U> BiConsumer<T, U> biConsumerWithTracing(BiConsumer<T, U> biConsumer) {
        return new BiConsumerWithTracing<>(biConsumer);
    }

    /**
     * @return A {@link BiConsumer} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    default <T, U> BiConsumer<T, U> biConsumerWithTracing(
        BiConsumer<T, U> biConsumer,
        Pair<Deque<Span>, Map<String, String>> threadInfoToLink
    ) {
        return new BiConsumerWithTracing<>(biConsumer, threadInfoToLink);
    }

    /**
     * @return A {@link BiConsumer} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    default <T, U> BiConsumer<T, U> biConsumerWithTracing(BiConsumer<T, U> biConsumer,
                                                          Deque<Span> spanStackToLink,
                                                          Map<String, String> mdcContextMapToLink) {
        return new BiConsumerWithTracing<>(biConsumer, spanStackToLink, mdcContextMapToLink);
    }

    /**
     * @return A {@link Predicate} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    default <T> Predicate<T> predicateWithTracing(Predicate<T> predicate) {
        return new PredicateWithTracing<>(predicate);
    }

    /**
     * @return A {@link Predicate} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    default <T> Predicate<T> predicateWithTracing(Predicate<T> predicate,
                                                  Pair<Deque<Span>, Map<String, String>> threadInfoToLink) {
        return new PredicateWithTracing<>(predicate, threadInfoToLink);
    }

    /**
     * @return A {@link Predicate} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    default <T> Predicate<T> predicateWithTracing(Predicate<T> predicate,
                                                  Deque<Span> spanStackToLink,
                                                  Map<String, String> mdcContextMapToLink) {
        return new PredicateWithTracing<>(predicate, spanStackToLink, mdcContextMapToLink);
    }

    /**
     * @return A {@link BiPredicate} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     */
    default <T, U> BiPredicate<T, U> biPredicateWithTracing(BiPredicate<T, U> biPredicate) {
        return new BiPredicateWithTracing<>(biPredicate);
    }

    /**
     * @return A {@link BiPredicate} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    default <T, U> BiPredicate<T, U> biPredicateWithTracing(
        BiPredicate<T, U> biPredicate,
        Pair<Deque<Span>, Map<String, String>> threadInfoToLink
    ) {
        return new BiPredicateWithTracing<>(biPredicate, threadInfoToLink);
    }

    /**
     * @return A {@link BiPredicate} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     */
    default <T, U> BiPredicate<T, U> biPredicateWithTracing(BiPredicate<T, U> biPredicate,
                                                            Deque<Span> spanStackToLink,
                                                            Map<String, String> mdcContextMapToLink) {
        return new BiPredicateWithTracing<>(biPredicate, spanStackToLink, mdcContextMapToLink);
    }

    /**
     * @return An {@link ExecutorService} that wraps the given delegate {@link ExecutorService} so that when
     * {@link Runnable}s or {@link Callable}s are executed through it they will automatically inherit the tracing state
     * of the thread that called the {@link ExecutorService} method. Equivalent to calling:
     * {@code new ExecutorServiceWithTracing(delegate)}.
     *
     * <p>WARNING: Keep in mind that you should avoid using a {@link ExecutorServiceWithTracing} when spinning off
     * background threads that aren't tied to a specific trace, or in any other situation where an executed
     * {@link Runnable}/{@link Callable} should *not* automatically inherit the calling thread's tracing state!
     */
    @SuppressWarnings("deprecation")
    default ExecutorServiceWithTracing executorServiceWithTracing(ExecutorService delegate) {
        return AsyncWingtipsHelperJava7.executorServiceWithTracing(delegate);
    }

    /**
     * @return A {@link ScheduledExecutorService} that wraps the given delegate {@link ScheduledExecutorService} so
     * that when {@link Runnable}s or {@link Callable}s are scheduled or executed through it they will automatically
     * inherit the tracing state of the thread that called the {@link ScheduledExecutorService} method. Equivalent to
     * calling: {@code new ScheduledExecutorServiceWithTracing(delegate)}.
     *
     * <p>WARNING: Keep in mind that you should avoid using a {@link ScheduledExecutorServiceWithTracing} when spinning
     * off background threads that aren't tied to a specific trace, or in any other situation where an executed
     * {@link Runnable}/{@link Callable} should *not* automatically inherit the calling thread's tracing state!
     */
    @SuppressWarnings("deprecation")
    default ScheduledExecutorServiceWithTracing scheduledExecutorServiceWithTracing(
        ScheduledExecutorService delegate
    ) {
        return AsyncWingtipsHelperJava7.scheduledExecutorServiceWithTracing(delegate);
    }

    /**
     * Links the given distributed tracing and logging MDC info to the current thread. Any existing tracing and MDC info
     * on the current thread will be wiped out and overridden, so if you need to go back to them in the future you'll
     * need to store the copy info returned by this method for later.
     *
     * @param threadInfoToLink
     *     A {@link Pair} containing the span stack and MDC info you want to link to the current thread.
     *     This argument can be null - if it is null then {@link Tracer} will be setup with an empty span stack (wiping
     *     out any existing in-progress traces) *and* {@link MDC#clear()} will be called (wiping out any
     *     existing MDC info). The left and/or right portion of the pair can also be null, with any null portion of the
     *     pair causing the corresponding portion to be emptied/cleared while letting any non-null portion link to the
     *     thread as expected. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     *     {@code Pair<Deque<Span>, Map<String, String>>}.
     *
     * @return A *COPY* of the original span stack and MDC info on the thread when this method was called (before being
     * replaced with the given arguments). The returned {@link TracingState} object will never be null, but the values
     * it contains may be null. A copy is returned rather than the original to prevent undesired behavior (storing the
     * return value and then passing it in to {@link #unlinkTracingFromCurrentThread(Pair)} later should *guarantee*
     * that after calling that unlink method the thread state is exactly as it was right *before* calling this link
     * method. If we returned the original span stack this contract guarantee could be violated).
     */
    @SuppressWarnings("deprecation")
    default TracingState linkTracingToCurrentThread(
        Pair<Deque<Span>, Map<String, String>> threadInfoToLink
    ) {
        return AsyncWingtipsHelperJava7.linkTracingToCurrentThread(threadInfoToLink);
    }

    /**
     * Links the given distributed tracing and logging MDC info to the current thread. Any existing tracing and MDC info
     * on the current thread will be wiped out and overridden, so if you need to go back to them in the future you'll
     * need to store the copy info returned by this method for later.
     *
     * @param spanStackToLink
     *     The stack of distributed traces that should be associated with the current thread. This can be null - if it
     *     is null then {@link Tracer} will be setup with an empty span stack (wiping out any existing in-progress
     *     traces).
     * @param mdcContextMapToLink
     *     The MDC context map to associate with the current thread. This can be null - if it is null then {@link
     *     MDC#clear()} will be called (wiping out any existing MDC info).
     *
     * @return A *COPY* of the original span stack and MDC info on the thread when this method was called (before being
     * replaced with the given arguments). The returned {@link TracingState} object will never be null, but the values
     * it contains may be null. A copy is returned rather than the original to prevent undesired behavior (storing the
     * return value and then passing it in to {@link #unlinkTracingFromCurrentThread(Pair)} later should *guarantee*
     * that after calling that unlink method the thread state is exactly as it was right *before* calling this link
     * method. If we returned the original span stack this contract guarantee could be violated).
     */
    @SuppressWarnings("deprecation")
    default TracingState linkTracingToCurrentThread(
        Deque<Span> spanStackToLink,
        Map<String, String> mdcContextMapToLink
    ) {
        return AsyncWingtipsHelperJava7.linkTracingToCurrentThread(
            spanStackToLink, mdcContextMapToLink
        );
    }

    /**
     * Helper method for calling {@link #unlinkTracingFromCurrentThread(Deque, Map)} that
     * gracefully handles the case where the pair you pass in is null - if the pair you pass in is null then {@link
     * #unlinkTracingFromCurrentThread(Deque, Map)} will be called with both arguments null. You can pass in a {@link
     * TracingState} for clearer less verbose code since it extends {@code Pair<Deque<Span>, Map<String, String>>}.
     */
    @SuppressWarnings("deprecation")
    default void unlinkTracingFromCurrentThread(Pair<Deque<Span>, Map<String, String>> threadInfoToResetFor) {
        AsyncWingtipsHelperJava7.unlinkTracingFromCurrentThread(threadInfoToResetFor);
    }

    /**
     * Calls {@link Tracer#unregisterFromThread()} and {@link MDC#clear()} to reset this thread's tracing and
     * MDC state to be completely clean, then (optionally) resets the span stack and MDC info to the arguments
     * provided. If the span stack argument is null then the span stack will *not* be reset, and similarly if the MDC
     * info is null then the MDC info will *not* be reset. So if both are null then when this method finishes the trace
     * stack and MDC will be left in a blank state.
     */
    @SuppressWarnings("deprecation")
    default void unlinkTracingFromCurrentThread(Deque<Span> spanStackToResetFor,
                                                Map<String, String> mdcContextMapToResetFor) {
        AsyncWingtipsHelperJava7.unlinkTracingFromCurrentThread(
            spanStackToResetFor, mdcContextMapToResetFor
        );
    }

    /**
     * Wraps the {@link CompletableFuture} returned by the given {@code supplier} in a {@link Span} based on the
     * options from the given {@link OperationWrapperOptions} argument. That span will be completed automatically
     * when the {@link CompletableFuture} finishes, and it will be tagged as desired based on the given
     * {@link OperationWrapperOptions}.
     *
     * <p>This is most commonly used to wrap client calls from libraries you don't have control over in a child span,
     * where the library doesn't provide hooks for distributed tracing and hands you back a {@link CompletableFuture}.
     * This method won't be able to propagate the tracing state downstream by itself, but at least you'll have a child
     * span to provide visibility in your application for how the wrapped {@link CompletableFuture} is functioning.
     *
     * <p>Typical usage would look like this - instead of calling {@code executeFooClientRequest()} to kick off
     * a {@link CompletableFuture} you'd do:
     *
     * <pre>
     *     OperationWrapperOptions&lt;Foo> options = ...;
     *     CompletableFuture&lt;Foo> future = wrapCompletableFutureWithSpan(
     *         options,
     *         () -> executeFooClientRequest()
     *     );
     * </pre>
     *
     * <p>NOTE: If the provided {@link OperationWrapperOptions#parentTracingState} is null, then the current thread's
     * tracing state (at the time this method is called) will be used to generate the child span. If the current
     * thread also has no tracing state, then a new root span (new trace) will be created rather than a child span.
     *
     * <p>No matter what options you pass in or what happens in this method, the tracing state on the thread when this
     * method is called will be what's on the thread when this method returns. Any tracing state adjustments like the
     * child or root span will only be in effect while the given {@link Supplier#get()} is called.
     *
     * <p>ALSO NOTE: The span that wraps the {@link CompletableFuture} will be on the thread when the given {@link
     * Supplier#get()} is called in this method. If you have any control over the {@link CompletableFuture}'s creation,
     * then you can use this fact to propagate the tracing state into the {@link CompletableFuture} itself by using
     * {@link #supplierWithTracing(Supplier)} or {@link #runnableWithTracing(Runnable)}.
     * For example:
     *
     * <pre>
     *     OperationWrapperOptions&lt;String> options = ...;
     *     Supplier&lt;CompletableFuture&lt;String>> futureSupplier = () -> {
     *         // The tracing state and span wrapping the CompletableFuture will be on the thread at this point.
     *         return CompletableFuture.supplyAsync(
     *             supplierWithTracing(() -> {
     *                 // And when the CompletableFuture executes you can retrieve the wrapper span,
     *                 //       thanks to supplierWithTracing().
     *                 Span wrapperSpan = Tracer.getInstance().getCurrentSpan();
     *                 return "actual work goes here";
     *             }),
     *             myAsyncExecutor
     *         );
     *     };
     *
     *     CompletableFuture&lt;String> future = wrapCompletableFutureWithSpan(
     *         options,
     *         futureSupplier
     *     );
     * </pre>
     *
     * @param options The set of options controlling various things about the span that will surround the
     * {@link CompletableFuture} (span name, purpose, tagging options, etc). See the {@link OperationWrapperOptions}
     * javadocs for more details. This cannot be null.
     * @param supplier The supplier that will generate the {@link CompletableFuture} you want wrapped with a new span.
     * {@link Supplier#get()} will be called as part of this method. This cannot be null.
     * @param <T> The type parameter for the {@link CompletableFuture}.
     * @return The {@link CompletableFuture} returned by the given supplier's {@link Supplier#get()}. That future
     * will be surrounded with a new child/root span (depending on the given options and what's on the current thread
     * at the time this method is called), and that new span will be automatically completed when the future completes,
     * and tagged based on the given options.
     */
    default <T> CompletableFuture<T> wrapCompletableFutureWithSpan(
        @NotNull OperationWrapperOptions<T> options,
        @NotNull Supplier<CompletableFuture<T>> supplier
    ) {
        //noinspection ConstantConditions
        if (options == null) {
            throw new NullPointerException("options cannot be null.");
        }

        //noinspection ConstantConditions
        if (supplier == null) {
            throw new NullPointerException("supplier cannot be null.");
        }

        // We need to keep track of the current thread info so we can reset this thread's tracing state before we
        //      return. If the options don't specify a specific parent tracing state then we're supposed to use
        //      the current thread tracing state as the parent state, which means we'll need to set originalThreadInfo
        //      to a copy of the current tracing state using TracingState.getCurrentThreadTracingState().
        //      Otherwise, when the options do specify a parent, then we need to link that parent tracing state to the
        //      current thread and linkTracingToCurrentThread() will return the current thread's tracing state.
        TracingState originalThreadInfo;

        if (options.parentTracingState == null) {
            originalThreadInfo = TracingState.getCurrentThreadTracingState();
        }
        else {
            originalThreadInfo = linkTracingToCurrentThread(options.parentTracingState);
        }

        Span spanAroundFuture = null;
        try {
            // Start a span for the CompletableFuture. startSpanInCurrentContext() will do the right thing
            //      regardless of whether it needs to be a root span or a subspan.
            spanAroundFuture = Tracer.getInstance().startSpanInCurrentContext(
                options.operationSpanName, options.spanPurpose
            );

            final Span spanAroundFutureFinal = spanAroundFuture;

            // Get the CompletableFuture result from the supplier.
            CompletableFuture<T> resultFuture = supplier.get();

            // Return a CompletableFuture that finishes the span after resultFuture is done.
            return resultFuture.whenComplete((result, ex) -> {
                // Do any desired tagging.
                AsyncWingtipsHelperDefaultImpl.doSpanTaggingWithoutExceptionPropagation(
                    spanAroundFutureFinal, options, result, ex
                );

                // Span.close() will do the right thing regardless of whether it's the current span on this thread.
                spanAroundFutureFinal.close();
            });
        }
        catch (Exception ex) {
            // An unexpected exception occurred - the supplier is probably bad.
            //      Do any desired tagging and complete the span, if possible.
            AsyncWingtipsHelperDefaultImpl.doSpanTaggingWithoutExceptionPropagation(
                spanAroundFuture, options, null, ex
            );

            AsyncWingtipsHelperDefaultImpl.doCloseSpanIfPossible(spanAroundFuture);

            throw ex;
        }
        finally {
            // Reset back to the original tracing state that was on this thread when this method began.
            unlinkTracingFromCurrentThread(originalThreadInfo);
        }
    }

    /**
     * Executes the given {@link Runnable} and wraps it in a {@link Span} based on the options from the given
     * {@link OperationWrapperOptions} argument. That span will be completed automatically when the
     * {@link Runnable} finishes, and it will be tagged as desired based on the given
     * {@link OperationWrapperOptions}.
     *
     * <p>Typical usage would look like this:
     *
     * <pre>
     *     OperationWrapperOptions&lt;?> options = ...;
     *     wrapRunnableWithSpan(
     *         options,
     *         () -> {
     *             // Runnable work goes here.
     *         }
     *     );
     * </pre>
     *
     * <p>NOTE: If the provided {@link OperationWrapperOptions#parentTracingState} is null, then the current thread's
     * tracing state (at the time this method is called) will be used to generate the child span. If the current
     * thread also has no tracing state, then a new root span (new trace) will be created rather than a child span.
     *
     * <p>No matter what options you pass in or what happens in this method, the tracing state on the thread when this
     * method is called will be what's on the thread when this method returns. Any tracing state adjustments like the
     * child or root span will only be in effect while the given {@link Runnable#run()} is called.
     *
     * <p>ALSO NOTE: This method executes the given {@link Runnable#run()} synchronously and will block until
     * that execution completes. If you're looking for an asynchronous execution, see {@link
     * #wrapCompletableFutureWithSpan(OperationWrapperOptions, Supplier)}.
     *
     * @param options The set of options controlling various things about the span that will surround the
     * {@link Runnable} (span name, purpose, tagging options, etc). See the {@link OperationWrapperOptions}
     * javadocs for more details. This cannot be null.
     * @param runnable The runnable that you want executed and wrapped with a new span. {@link Runnable#run()}
     * will be called as part of this method. This cannot be null.
     */
    default void wrapRunnableWithSpan(
        @NotNull OperationWrapperOptions<?> options,
        @NotNull Runnable runnable
    ) {
        //noinspection ConstantConditions
        if (options == null) {
            throw new NullPointerException("options cannot be null.");
        }

        //noinspection ConstantConditions
        if (runnable == null) {
            throw new NullPointerException("runnable cannot be null.");
        }

        try {
            wrapCallableWithSpan(options, () -> {
                runnable.run();
                return null;
            });
        }
        catch (Exception e) {
            throw AsyncWingtipsHelperDefaultImpl.wrapCheckedExInRuntimeExIfNecessary(e);
        }
    }

    /**
     * Executes the given {@link CheckedRunnable} and wraps it in a {@link Span} based on the options from the given
     * {@link OperationWrapperOptions} argument. That span will be completed automatically when the
     * {@link CheckedRunnable} finishes, and it will be tagged as desired based on the given
     * {@link OperationWrapperOptions}.
     *
     * <p>Typical usage would look like this:
     *
     * <pre>
     *     OperationWrapperOptions&lt;?> options = ...;
     *     wrapCheckedRunnableWithSpan(
     *         options,
     *         () -> {
     *             // CheckedRunnable work goes here.
     *         }
     *     );
     * </pre>
     *
     * <p>NOTE: If the provided {@link OperationWrapperOptions#parentTracingState} is null, then the current thread's
     * tracing state (at the time this method is called) will be used to generate the child span. If the current
     * thread also has no tracing state, then a new root span (new trace) will be created rather than a child span.
     *
     * <p>No matter what options you pass in or what happens in this method, the tracing state on the thread when this
     * method is called will be what's on the thread when this method returns. Any tracing state adjustments like the
     * child or root span will only be in effect while the given {@link CheckedRunnable#run()} is called.
     *
     * <p>ALSO NOTE: This method executes the given {@link CheckedRunnable#run()} synchronously and will block until
     * that execution completes. If you're looking for an asynchronous execution, see {@link
     * #wrapCompletableFutureWithSpan(OperationWrapperOptions, Supplier)}.
     *
     * @param options The set of options controlling various things about the span that will surround the
     * {@link CheckedRunnable} (span name, purpose, tagging options, etc). See the {@link OperationWrapperOptions}
     * javadocs for more details. This cannot be null.
     * @param runnable The checked runnable that you want executed and wrapped with a new span. {@link
     * CheckedRunnable#run()} will be called as part of this method. This cannot be null.
     */
    default void wrapCheckedRunnableWithSpan(
        @NotNull OperationWrapperOptions<?> options,
        @NotNull CheckedRunnable runnable
    ) throws Exception {
        //noinspection ConstantConditions
        if (options == null) {
            throw new NullPointerException("options cannot be null.");
        }

        //noinspection ConstantConditions
        if (runnable == null) {
            throw new NullPointerException("runnable cannot be null.");
        }

        wrapCallableWithSpan(options, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Executes the given {@link Supplier} and wraps it in a {@link Span} based on the options from the given
     * {@link OperationWrapperOptions} argument. That span will be completed automatically when the
     * {@link Supplier} finishes, and it will be tagged as desired based on the given
     * {@link OperationWrapperOptions}.
     *
     * <p>Typical usage would look like this:
     *
     * <pre>
     *     OperationWrapperOptions&lt;Foo> options = ...;
     *     Foo fooResult = wrapSupplierWithSpan(
     *         options,
     *         () -> {
     *             // Supplier work goes here.
     *             return foo;
     *         }
     *     );
     * </pre>
     *
     * <p>NOTE: If the provided {@link OperationWrapperOptions#parentTracingState} is null, then the current thread's
     * tracing state (at the time this method is called) will be used to generate the child span. If the current
     * thread also has no tracing state, then a new root span (new trace) will be created rather than a child span.
     *
     * <p>No matter what options you pass in or what happens in this method, the tracing state on the thread when this
     * method is called will be what's on the thread when this method returns. Any tracing state adjustments like the
     * child or root span will only be in effect while the given {@link Supplier#get()} is called.
     *
     * <p>ALSO NOTE: This method executes the given {@link Supplier#get()} synchronously and will block until
     * that execution completes. If you're looking for an asynchronous execution, see {@link
     * #wrapCompletableFutureWithSpan(OperationWrapperOptions, Supplier)}.
     *
     * @param options The set of options controlling various things about the span that will surround the
     * {@link Supplier} (span name, purpose, tagging options, etc). See the {@link OperationWrapperOptions}
     * javadocs for more details. This cannot be null.
     * @param supplier The supplier that you want executed and wrapped with a new span. {@link Supplier#get()}
     * will be called as part of this method. This cannot be null.
     * @param <T> The type parameter for the {@link Supplier}.
     * @return The result of calling {@link Supplier#get()} on the given supplier.
     */
    default <T> T wrapSupplierWithSpan(
        @NotNull OperationWrapperOptions<T> options,
        @NotNull Supplier<T> supplier
    ) {
        //noinspection ConstantConditions
        if (options == null) {
            throw new NullPointerException("options cannot be null.");
        }

        //noinspection ConstantConditions
        if (supplier == null) {
            throw new NullPointerException("supplier cannot be null.");
        }

        try {
            return wrapCallableWithSpan(options, supplier::get);
        }
        catch (Exception e) {
            throw AsyncWingtipsHelperDefaultImpl.wrapCheckedExInRuntimeExIfNecessary(e);
        }
    }

    /**
     * Executes the given {@link Callable} and wraps it in a {@link Span} based on the options from the given
     * {@link OperationWrapperOptions} argument. That span will be completed automatically when the
     * {@link Callable} finishes, and it will be tagged as desired based on the given
     * {@link OperationWrapperOptions}.
     *
     * <p>Typical usage would look like this:
     *
     * <pre>
     *     OperationWrapperOptions&lt;Foo> options = ...;
     *     Foo fooResult = wrapCallableWithSpan(
     *         options,
     *         () -> {
     *             // Callable work goes here.
     *             return foo;
     *         }
     *     );
     * </pre>
     *
     * <p>NOTE: If the provided {@link OperationWrapperOptions#parentTracingState} is null, then the current thread's
     * tracing state (at the time this method is called) will be used to generate the child span. If the current
     * thread also has no tracing state, then a new root span (new trace) will be created rather than a child span.
     *
     * <p>No matter what options you pass in or what happens in this method, the tracing state on the thread when this
     * method is called will be what's on the thread when this method returns. Any tracing state adjustments like the
     * child or root span will only be in effect while the given {@link Callable#call()} is called.
     *
     * <p>ALSO NOTE: This method executes the given {@link Callable#call()} synchronously and will block until
     * that execution completes. If you're looking for an asynchronous execution, see {@link
     * #wrapCompletableFutureWithSpan(OperationWrapperOptions, Supplier)}.
     *
     * @param options The set of options controlling various things about the span that will surround the
     * {@link Callable} (span name, purpose, tagging options, etc). See the {@link OperationWrapperOptions}
     * javadocs for more details. This cannot be null.
     * @param callable The callable that you want executed and wrapped with a new span. {@link Callable#call()}
     * will be called as part of this method. This cannot be null.
     * @param <T> The type parameter for the {@link Callable}.
     * @return The result of calling {@link Callable#call()} on the given callable.
     */
    default <T> T wrapCallableWithSpan(
        @NotNull OperationWrapperOptions<T> options,
        @NotNull Callable<T> callable
    ) throws Exception {
        //noinspection ConstantConditions
        if (options == null) {
            throw new NullPointerException("options cannot be null.");
        }

        //noinspection ConstantConditions
        if (callable == null) {
            throw new NullPointerException("callable cannot be null.");
        }

        TracingState originalThreadInfo;

        if (options.parentTracingState == null) {
            // No parent tracing state, so we just use the current thread tracing state. We don't need to reset this
            //      thread's tracing state at the end since we start and complete the wrapper span before this method
            //      returns.
            originalThreadInfo = null;
        }
        else {
            // The options specify a parent tracing state, so we need to link it to this thread. And before we
            //      return from this method, we'll reset it back to originalThreadInfo.
            originalThreadInfo = linkTracingToCurrentThread(options.parentTracingState);
        }

        Span spanAroundCall = null;
        T result = null;
        Exception callError = null;
        try {
            // Start a span to wrap the callable. startSpanInCurrentContext() will do the right thing
            //      regardless of whether it needs to be a root span or a subspan.
            spanAroundCall = Tracer.getInstance().startSpanInCurrentContext(
                options.operationSpanName, options.spanPurpose
            );

            // Return the result from the callable.
            result = callable.call();

            return result;
        }
        catch (Exception ex) {
            // Keep track of any error for tagging in the finally block.
            callError = ex;

            throw ex;
        }
        finally {
            try {
                // Do any desired tagging.
                AsyncWingtipsHelperDefaultImpl.doSpanTaggingWithoutExceptionPropagation(
                    spanAroundCall, options, result, callError
                );

                // Close the span wrapping the callable.
                AsyncWingtipsHelperDefaultImpl.doCloseSpanIfPossible(spanAroundCall);
            }
            finally {
                // Reset back to the original tracing state that was on this thread when this method began, if needed.
                //      If originalThreadInfo is null, then it means the current thread tracing state is already correct,
                //      in which case we don't need to do anything.
                if (originalThreadInfo != null) {
                    unlinkTracingFromCurrentThread(originalThreadInfo);
                }
            }
        }
    }

    /**
     * A functional interface that matches {@link Runnable}, but allows checked exceptions to be thrown from the
     * {@link #run()} method. This is for use with {@link
     * #wrapCheckedRunnableWithSpan(OperationWrapperOptions, CheckedRunnable)}.
     */
    @FunctionalInterface
    interface CheckedRunnable {
        /**
         * Executes this {@link CheckedRunnable}.
         *
         * @throws Exception an exception may be thrown from this method.
         */
        void run() throws Exception;
    }

    /**
     * A concrete implementation of {@link AsyncWingtipsHelper} that does nothing extra beyond the default methods
     * defined in the {@link AsyncWingtipsHelper} interface.
     *
     * <p>Normally you'd access this via {@link AsyncWingtipsHelper#DEFAULT_IMPL} rather than creating a new instance.
     * Or you can get the same effect by calling the static methods on {@link AsyncWingtipsHelperStatic}.
     */
    class AsyncWingtipsHelperDefaultImpl implements AsyncWingtipsHelper {
        // Nothing beyond the default, except protected static helper methods that the AsyncWingtipsHelper interface's
        //      default methods can reuse for util stuff.

        private static final Logger logger = LoggerFactory.getLogger(AsyncWingtipsHelperDefaultImpl.class);

        /**
         * @param ex The exception that may need wrapping in a {@link RuntimeException}.
         * @return The given ex if it is a RuntimeException already, otherwise a new {@link RuntimeException} will
         * be returned with the given ex as its cause.
         */
        protected static RuntimeException wrapCheckedExInRuntimeExIfNecessary(Exception ex) {
            if (ex instanceof RuntimeException) {
                return (RuntimeException)ex;
            }
            else {
                return new RuntimeException(ex);
            }
        }

        protected static <T> void doSpanTaggingWithoutExceptionPropagation(
            @Nullable Span span,
            @NotNull OperationWrapperOptions<T> options,
            @Nullable T payload,
            @Nullable Throwable error
        ) {
            //noinspection ConstantConditions
            if (span == null || options == null) {
                return;
            }

            if (options.spanTagger != null) {
                try {
                    options.spanTagger.tagSpan(span, payload);
                }
                catch (Exception ex) {
                    logger.error(
                        "An unexpected error occurred while executing a SpanTagger. This exception will "
                        + "be swallowed to avoid unexpected application breakage, but you should fix your "
                        + "SpanTagger so it doesn't throw an exception.",
                        ex
                    );
                }
            }

            if (options.errorTagger != null && error != null) {
                try {
                    options.errorTagger.tagSpanForError(span, error);
                }
                catch (Exception ex) {
                    logger.error(
                        "An unexpected error occurred while executing a ErrorSpanTagger. This exception will "
                        + "be swallowed to avoid unexpected application breakage, but you should fix your "
                        + "ErrorSpanTagger so it doesn't throw an exception.",
                        ex
                    );
                }
            }
        }

        protected static void doCloseSpanIfPossible(@Nullable Span span) {
            if (span == null) {
                return;
            }

            // Span.close() will do the right thing regardless of whether it's the current span on this thread.
            span.close();
        }
    }

}
