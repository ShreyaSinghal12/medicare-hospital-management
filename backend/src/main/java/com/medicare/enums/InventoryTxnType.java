package com.medicare.enums;

/** Stock ledger entry types. Quantities are always positive; {@link #isIncrease()} gives the direction. */
public enum InventoryTxnType {
    STOCK_IN, STOCK_OUT, DISPENSE, ADJUSTMENT_IN, ADJUSTMENT_OUT, EXPIRED_WRITE_OFF;

    public boolean isIncrease() {
        return this == STOCK_IN || this == ADJUSTMENT_IN;
    }
}
