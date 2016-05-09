package com.nike.wingtips.sampling;

/**
 * Pluggable strategy for {@link com.nike.wingtips.Tracer} that determines whether or not the next root span should be sampled (i.e. request span that has no parent and is not
 * explicitly requested to be sampled or ignored).
 * Call {@link com.nike.wingtips.Tracer#setRootSpanSamplingStrategy(RootSpanSamplingStrategy)} to tell the tracer to use a specific strategy.
 *
 * @author Nic Munroe
 */
public interface RootSpanSamplingStrategy {

    /**
     * @return true if the next root span should be sampled, false otherwise. NOTE: This method is not deterministic - you may get a different response every time you call it.
     *          Therefore you should not call this method multiple times for the same root span. Call it once and store the result.
     */
    boolean isNextRootSpanSampleable();

}
