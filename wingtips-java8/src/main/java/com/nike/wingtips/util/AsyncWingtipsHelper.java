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

    class AsyncWingtipsHelperDefaultImpl implements AsyncWingtipsHelper {
        // Nothing beyond the default.
    }

}
