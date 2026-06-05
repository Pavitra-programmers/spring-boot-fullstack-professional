package com.example.demo.overtime;

/**
 * Lifecycle state of an overtime entry's payout settlement.
 * PENDING  → earned but not yet paid out
 * SETTLED  → payout confirmed; entry is immutable after this point
 */
public enum SettlementStatus {
    PENDING,
    SETTLED
}
