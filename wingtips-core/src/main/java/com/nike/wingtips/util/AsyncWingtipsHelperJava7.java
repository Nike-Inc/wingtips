package com.nike.wingtips.util;

import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.util.asynchelperwrapper.CallableWithTracing;
import com.nike.wingtips.util.asynchelperwrapper.RunnableWithTracing;

import org.slf4j.MDC;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * Helper class that provides static methods for dealing with async stuff in Wingtips, mainly providing easy ways
 * to support distributed tracing and logger MDC when hopping threads using {@link Executor}s, etc. There are various
 * {@code *WithTracing(...)} methods for wrapping objects with the same type that knows how to handle tracing and
 * MDC. You can also call the various {@code linkTracingToCurrentThread(...)} and {@code
 * unlinkTracingFromCurrentThread(...)} methods directly if you want to do it manually yourself.
 *
 * <p>NOTE: This is the Java 7 version of {@code AsyncWingtipsHelper} from the wingtips-java8 module. The Java 8 version
 * contains more options and flexibility and should be used instead of this class in a Java 8 environment. This class
 * really only helps for {@link Runnable}s and {@link Callable}s - the Java 8 version supports many more functional
 * interfaces and lambda types.
 *
 * <p>Here's an example of making the current thread's tracing and MDC info hop to a thread executed by an
 * {@link Executor}:
 * <pre>
 *   import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;
 *
 *   // ...
 *
 *   executor.execute(runnableWithTracing(new Runnable() {
 *       &amp;Override
 *       public void run() {
 *           // Code that needs tracing/MDC wrapping goes here
 *       }
 *   }));
 * </pre>
 *
 * <p>This example shows how you might accomplish tasks in an environment where the tracing information is attached
 * to some request context, and you need to temporarily attach the tracing info in order to do something (e.g. log some
 * messages with tracing info automatically added using MDC):
 * <pre>
 *     import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;
 *
 *     // ...
 *
 *     TracingState tracingInfo = requestContext.getTracingInfo();
 *     runnableWithTracing(
 *         new Runnable() {
 *             &amp;Override
 *             public void run() {
 *                 // Code that needs tracing/MDC wrapping goes here
 *             }
 *         },
 *         tracingInfo
 *     ).run();
 * </pre>
 *
 * <p>If you want to use the link and unlink methods manually to wrap some chunk of code, the general procedure looks
 * like this:
 * <pre>
 *  import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
 *  import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;
 *
 *  // ...
 *
 *  TracingState originalThreadInfo = null;
 *  try {
 *      originalThreadInfo = linkTracingToCurrentThread(...);
 *      // Code that needs tracing/MDC wrapping goes here
 *  }
 *  finally {
 *      unlinkTracingFromCurrentThread(originalThreadInfo);
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
 * @deprecated This class should be considered deprecated - eventually Java 7 support will be dropped. Please use
 * the Java 8 version of this class ({@code AsyncWingtipsHelper} or the static {@code AsyncWingtipsHelperStatic})
 * whenever possible.
 *
 * @author Nic Munroe
 */
@Deprecated
@SuppressWarnings({"WeakerAccess", "DeprecatedIsStillUsed"})
public class AsyncWingtipsHelperJava7 {

    // Intentionally protected - use the static methods.
    protected AsyncWingtipsHelperJava7() { /* do nothing */ }

    /**
     * @return A {@link Runnable} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static Runnable runnableWithTracing(Runnable runnable) {
        return new RunnableWithTracing(runnable);
    }

    /**
     * @return A {@link Runnable} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static Runnable runnableWithTracing(Runnable runnable,
                                               Pair<Deque<Span>, Map<String, String>> threadInfoToLink) {
        return new RunnableWithTracing(runnable, threadInfoToLink);
    }

    /**
     * @return A {@link Runnable} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static Runnable runnableWithTracing(Runnable runnable,
                                               Deque<Span> distributedTraceStackToLink,
                                               Map<String, String> mdcContextMapToLink) {
        return new RunnableWithTracing(runnable, distributedTraceStackToLink, mdcContextMapToLink);
    }

    /**
     * @return A {@link Callable} that wraps the given original so that the <b>current thread's</b> tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * <p>NOTE: The current thread's tracing and MDC info will be extracted using {@link
     * Tracer#getCurrentSpanStackCopy()} and {@link MDC#getCopyOfContextMap()}.
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static <U> Callable<U> callableWithTracing(Callable<U> callable) {
        return new CallableWithTracing<>(callable);
    }

    /**
     * @return A {@link Callable} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     * {@code Pair<Deque<Span>, Map<String, String>>}.
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static <U> Callable<U> callableWithTracing(Callable<U> callable,
                                                      Pair<Deque<Span>, Map<String, String>> threadInfoToLink) {
        return new CallableWithTracing<>(callable, threadInfoToLink);
    }

    /**
     * @return A {@link Callable} that wraps the given original so that the given distributed tracing and MDC
     * information is registered with the thread and therefore available during execution and unregistered after
     * execution.
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static <U> Callable<U> callableWithTracing(Callable<U> callable,
                                                      Deque<Span> distributedTraceStackToLink,
                                                      Map<String, String> mdcContextMapToLink) {
        return new CallableWithTracing<>(callable, distributedTraceStackToLink, mdcContextMapToLink);
    }
    
    /**
     * Links the given distributed tracing and logging MDC info to the current thread. Any existing tracing and MDC info
     * on the current thread will be wiped out and overridden, so if you need to go back to them in the future you'll
     * need to store the copy info returned by this method for later.
     *
     * @param threadInfoToLink
     *     A {@link Pair} containing the distributed trace stack and MDC info you want to link to the current thread.
     *     This argument can be null - if it is null then {@link Tracer} will be setup with an empty trace stack (wiping
     *     out any existing in-progress traces) *and* {@link MDC#clear()} will be called (wiping out any
     *     existing MDC info). The left and/or right portion of the pair can also be null, with any null portion of the
     *     pair causing the corresponding portion to be emptied/cleared while letting any non-null portion link to the
     *     thread as expected. You can pass in a {@link TracingState} for clearer less verbose code since it extends
     *     {@code Pair<Deque<Span>, Map<String, String>>}.
     *
     * @return A *COPY* of the original trace stack and MDC info on the thread when this method was called (before being
     * replaced with the given arguments). The returned {@link TracingState} object will never be null, but the values
     * it contains may be null. A copy is returned rather than the original to prevent undesired behavior (storing the
     * return value and then passing it in to {@link #unlinkTracingFromCurrentThread(Pair)} later should *guarantee*
     * that after calling that unlink method the thread state is exactly as it was right *before* calling this link
     * method. If we returned the original trace stack this contract guarantee could be violated).
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static TracingState linkTracingToCurrentThread(
        Pair<Deque<Span>, Map<String, String>> threadInfoToLink
    ) {
        Deque<Span> distributedTraceStack = (threadInfoToLink == null) ? null : threadInfoToLink.getLeft();
        Map<String, String> mdcContextMap = (threadInfoToLink == null) ? null : threadInfoToLink.getRight();

        return linkTracingToCurrentThread(distributedTraceStack, mdcContextMap);
    }

    /**
     * Links the given distributed tracing and logging MDC info to the current thread. Any existing tracing and MDC info
     * on the current thread will be wiped out and overridden, so if you need to go back to them in the future you'll
     * need to store the copy info returned by this method for later.
     *
     * @param distributedTraceStackToLink
     *     The stack of distributed traces that should be associated with the current thread. This can be null - if it
     *     is null then {@link Tracer} will be setup with an empty trace stack (wiping out any existing in-progress
     *     traces).
     * @param mdcContextMapToLink
     *     The MDC context map to associate with the current thread. This can be null - if it is null then {@link
     *     MDC#clear()} will be called (wiping out any existing MDC info).
     *
     * @return A *COPY* of the original trace stack and MDC info on the thread when this method was called (before being
     * replaced with the given arguments). The returned {@link TracingState} object will never be null, but the values
     * it contains may be null. A copy is returned rather than the original to prevent undesired behavior (storing the
     * return value and then passing it in to {@link #unlinkTracingFromCurrentThread(Pair)} later should *guarantee*
     * that after calling that unlink method the thread state is exactly as it was right *before* calling this link
     * method. If we returned the original trace stack this contract guarantee could be violated).
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static TracingState linkTracingToCurrentThread(
        Deque<Span> distributedTraceStackToLink,
        Map<String, String> mdcContextMapToLink
    ) {
        // Unregister the trace stack so that if there's already a trace on the stack we don't get exceptions when
        //      registering the desired stack with the thread, and keep a copy of the results.
        Map<String, String> callingThreadMdcContextMap = MDC.getCopyOfContextMap();
        Deque<Span> callingThreadTraceStack = Tracer.getInstance().unregisterFromThread();

        // Now setup the trace stack and MDC as desired
        if (mdcContextMapToLink == null)
            MDC.clear();
        else
            MDC.setContextMap(mdcContextMapToLink);

        Tracer.getInstance().registerWithThread(distributedTraceStackToLink);

        // Return the copied original data so that it can be re-linked later (if the caller wants)
        return new TracingState(callingThreadTraceStack, callingThreadMdcContextMap);
    }

    /**
     * Helper method for calling {@link #unlinkTracingFromCurrentThread(Deque, Map)} that
     * gracefully handles the case where the pair you pass in is null - if the pair you pass in is null then {@link
     * #unlinkTracingFromCurrentThread(Deque, Map)} will be called with both arguments null. You can pass in a {@link
     * TracingState} for clearer less verbose code since it extends {@code Pair<Deque<Span>, Map<String, String>>}.
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static void unlinkTracingFromCurrentThread(
        Pair<Deque<Span>, Map<String, String>> threadInfoToResetFor
    ) {
        Deque<Span> traceStackToResetFor = (threadInfoToResetFor == null) ? null : threadInfoToResetFor.getLeft();
        Map<String, String> mdcContextMapToResetFor = (threadInfoToResetFor == null)
                                                      ? null
                                                      : threadInfoToResetFor.getRight();

        unlinkTracingFromCurrentThread(traceStackToResetFor, mdcContextMapToResetFor);
    }

    /**
     * Calls {@link Tracer#unregisterFromThread()} and {@link MDC#clear()} to reset this thread's tracing and
     * MDC state to be completely clean, then (optionally) resets the trace stack and MDC info to the arguments
     * provided. If the trace stack argument is null then the trace stack will *not* be reset, and similarly if the MDC
     * info is null then the MDC info will *not* be reset. So if both are null then when this method finishes the trace
     * stack and MDC will be left in a blank state.
     *
     * @deprecated Please move to the Java 8 version of this class and method ({@code AsyncWingtipsHelper} or the static
     * {@code AsyncWingtipsHelperStatic}) whenever possible.
     */
    @Deprecated
    public static void unlinkTracingFromCurrentThread(Deque<Span> distributedTraceStackToResetFor,
                                                      Map<String, String> mdcContextMapToResetFor) {
        Tracer.getInstance().unregisterFromThread();
        MDC.clear();

        if (mdcContextMapToResetFor != null)
            MDC.setContextMap(mdcContextMapToResetFor);

        if (distributedTraceStackToResetFor != null)
            Tracer.getInstance().registerWithThread(distributedTraceStackToResetFor);
    }
    
}
