package com.medicare.enums;

/**
 * How a payment was made. Non-cash methods are "simulated": no payment gateway is connected and only a
 * reference (e.g. UPI transaction id or card terminal slip number) is stored, never card details.
 */
public enum PaymentMethod {
    CASH, CARD_SIMULATED, UPI_SIMULATED, BANK_TRANSFER_SIMULATED
}
