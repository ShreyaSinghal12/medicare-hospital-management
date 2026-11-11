package com.medicare.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lab order life cycle: ORDERED → SAMPLE_COLLECTED → PROCESSING → COMPLETED.
 * CANCELLED is possible until the report is finalized. COMPLETED is reached only by finalizing the report.
 */
public enum LabOrderStatus {
    ORDERED, SAMPLE_COLLECTED, PROCESSING, COMPLETED, CANCELLED;

    public boolean isOpen() {
        return this != COMPLETED && this != CANCELLED;
    }

    /** Status changes a doctor may request directly (COMPLETED only via finalize). */
    public Set<LabOrderStatus> manualTargets() {
        return switch (this) {
            case ORDERED -> EnumSet.of(SAMPLE_COLLECTED, CANCELLED);
            case SAMPLE_COLLECTED -> EnumSet.of(PROCESSING, CANCELLED);
            case PROCESSING -> EnumSet.of(CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(LabOrderStatus.class);
        };
    }

    /** A report can be uploaded once the sample has been collected. */
    public boolean acceptsReport() {
        return this == SAMPLE_COLLECTED || this == PROCESSING;
    }
}
