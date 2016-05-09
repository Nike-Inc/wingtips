package com.nike.wingtips.sampling;

/**
 * Simple {@link RootSpanSamplingStrategy} that always returns true, indicating that every trace should be sampled.
 *
 * @author Nic Munroe
 */
public class SampleAllTheThingsStrategy implements RootSpanSamplingStrategy {

    @Override
    public boolean isNextRootSpanSampleable() {
        return true;
    }
}
