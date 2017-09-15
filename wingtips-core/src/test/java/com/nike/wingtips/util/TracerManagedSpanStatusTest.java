package com.nike.wingtips.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the functionality of {@link TracerManagedSpanStatus}.
 *
 * @author Nic Munroe
 */
public class TracerManagedSpanStatusTest {

    @Test
    public void verify_TracerManagedSpanInfo_behavior() {
        for (TracerManagedSpanStatus info : TracerManagedSpanStatus.values()) {
            assertThat(TracerManagedSpanStatus.valueOf(info.name())).isEqualTo(info); // code coverage hoop

            switch(info) {
                case MANAGED_CURRENT_ROOT_SPAN:
                case MANAGED_CURRENT_SUB_SPAN: // Intentional fall-through
                    assertThat(info.isCurrentSpanForThisThread()).isTrue();
                    assertThat(info.isManagedByTracerForThisThread()).isTrue();
                    break;
                case MANAGED_NON_CURRENT_ROOT_SPAN: //intentional fall-through
                case MANAGED_NON_CURRENT_SUB_SPAN:
                    assertThat(info.isCurrentSpanForThisThread()).isFalse();
                    assertThat(info.isManagedByTracerForThisThread()).isTrue();
                    break;
                case UNMANAGED_SPAN:
                    assertThat(info.isCurrentSpanForThisThread()).isFalse();
                    assertThat(info.isManagedByTracerForThisThread()).isFalse();
                    break;
                default:
                    throw new IllegalStateException(
                        "Unhandled TracerManagedSpanStatus type: " + info.name() + ". Make sure all relevant switch "
                        + "statements in the Wingtips libraries (not just this test) get updated to support it!"
                    );
            }
        }
    }

}