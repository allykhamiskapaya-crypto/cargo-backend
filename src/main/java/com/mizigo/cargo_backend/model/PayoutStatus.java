package com.mizigo.cargo_backend.model;

public enum PayoutStatus {
    WAITING_FOR_DELIVERY,
    WAITING_FOR_PAYMENT,
    READY_FOR_PAYOUT,
    PAID_OUT,
    FAILED
}