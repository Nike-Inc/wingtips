package com.nike.wingtips.sampling;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the functionality of {@link SampleAllTheThingsStrategy}
 */
public class SampleAllTheThingsStrategyTest {

    @Test
    public void isNextRootSpanSampleable_returns_true() {
        SampleAllTheThingsStrategy strategy = new SampleAllTheThingsStrategy();
        for (int i = 0; i < 100; i++) {
            assertThat(strategy.isNextRootSpanSampleable()).isTrue();
        }
    }

}
