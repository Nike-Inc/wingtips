package com.nike.wingtips.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;

/**
 * Represents a span's *current* managed status info relative to {@link Tracer} on the current thread at the time {@link
 * Span#getCurrentTracerManagedSpanStatus()} is called. This status is recalculated every time {@link
 * Span#getCurrentTracerManagedSpanStatus()} is called and is only relevant/correct until {@link Tracer}'s state is
 * modified (i.e. by starting a subspan, completing a span, using any of the asynchronous helper methods to modify the
 * span stack in any way, etc), so it should only be considered relevant for the moment the call is made.
 * Here is what this enum can tell you:
 * <ul>
 *     <li>
 *         {@link #isManagedByTracerForThisThread()} (or the enum value itself) indicates whether the span
 *         associated with this {@link TracerManagedSpanStatus} is "managed" by {@link Tracer} (i.e. it's in {@link
 *         Tracer}'s span stack for the current thread) or "unmanaged" (i.e. it's a loose span not found in {@link
 *         Tracer}'s span stack for the current thread).
 *     </li>
 *     <li>
 *         {@link #isCurrentSpanForThisThread()} indicates whether the span associated with this {@link
 *         TracerManagedSpanStatus} is the {@link Tracer#getCurrentSpan()} and will therefore be the span completed when
 *         {@link Tracer#completeSubSpan()} or {@link Tracer#completeRequestSpan()} is called.
 *     </li>
 *     <li>
 *         The specific enum instance you get back can also indicate whether the span is a root span (the
 *         bottom-most span on {@link Tracer}'s span stack) or a subspan. {@link #MANAGED_CURRENT_ROOT_SPAN} and
 *         {@link #MANAGED_NON_CURRENT_ROOT_SPAN} are root spans, while {@link #MANAGED_CURRENT_SUB_SPAN} and
 *         {@link #MANAGED_NON_CURRENT_SUB_SPAN} are subspans. There's no way to know whether an {@link
 *         #UNMANAGED_SPAN} is a root span or a subspan since it's unmanaged - it's up to you to track unmanaged
 *         spans.
 *     </li>
 * </ul>
 *
 * <p>NOTE: "Root span" and "subspan" in this context refer to the location of the span in {@link Tracer}'s span
 * stack *only* - it does not refer to whether or not the span has a non-null {@link Span#getParentSpanId()}.
 * Often the two are in sync (i.e. a root span here will have null parent span ID, while a subspan here will
 * have a non-null parent span ID), but there are times when they won't be (some valid use cases like receiving an
 * upstream call with parent span information in the headers, others caused by misuse of {@link Tracer}'s API).
 *
 * <p>ALSO NOTE: Most app-level developers should not need to worry about this at all.
 */
public enum TracerManagedSpanStatus {
    /**
     * Represents a managed root span that is also the {@link Tracer#getCurrentSpan()}. Managed root span in this
     * context means it is the bottom-most span in {@link Tracer}'s span stack, it does not have any relationship
     * with {@link Span#getParentSpanId()} which may or may not be null.
     */
    MANAGED_CURRENT_ROOT_SPAN(true, true),
    /**
     * Represents a managed subspan that is also the {@link Tracer#getCurrentSpan()}. Managed subspan in this
     * context means it is *NOT* the bottom-most span in {@link Tracer}'s span stack but it is found somewhere in
     * the span stack.
     */
    MANAGED_CURRENT_SUB_SPAN(true, true),
    /**
     * Represents a managed root span that is *NOT* the {@link Tracer#getCurrentSpan()}. Managed root span in this
     * context means it is the bottom-most span in {@link Tracer}'s span stack, it does not have any relationship
     * with {@link Span#getParentSpanId()} which may or may not be null.
     */
    MANAGED_NON_CURRENT_ROOT_SPAN(true, false),
    /**
     * Represents a managed subspan that is *NOT* the {@link Tracer#getCurrentSpan()}. Managed subspan in this
     * context means it is *NOT* the bottom-most span in {@link Tracer}'s span stack but it is found somewhere in
     * the span stack.
     */
    MANAGED_NON_CURRENT_SUB_SPAN(true, false),
    /**
     * Represents an unmanaged span - i.e. it is not found anywhere in {@link Tracer}'s span stack.
     */
    UNMANAGED_SPAN(false, false);

    private final boolean managedByTracerForThisThread;
    private final boolean currentSpanForThisThread;

    TracerManagedSpanStatus(boolean managedByTracerForThisThread, boolean currentSpanForThisThread) {
        this.managedByTracerForThisThread = managedByTracerForThisThread;
        this.currentSpanForThisThread = currentSpanForThisThread;
    }

    /**
     * @return true if the span associated with this {@link TracerManagedSpanStatus} is "managed" by {@link Tracer} (i.e.
     * it's in {@link Tracer}'s span stack for the current thread), or false if the span is "unmanaged" (i.e. it's
     * a loose span not found in {@link Tracer}'s span stack for the current thread).
     */
    public boolean isManagedByTracerForThisThread() {
        return managedByTracerForThisThread;
    }

    /**
     * @return true if the span associated with this {@link TracerManagedSpanStatus} is the {@link Tracer#getCurrentSpan()}
     * and will therefore be the span completed when {@link Tracer#completeSubSpan()} or {@link
     * Tracer#completeRequestSpan()} is called.
     */
    public boolean isCurrentSpanForThisThread() {
        return currentSpanForThisThread;
    }
}
