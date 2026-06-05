package com.example.demo.notification;

import java.math.BigDecimal;

/**
 * Domain event published after a successful overtime settlement.
 *
 * TICKET LF-204: This event is consumed by SmsNotificationService via
 * @TransactionalEventListener(phase = AFTER_COMMIT). Spring only fires
 * AFTER_COMMIT listeners once the DB transaction is fully committed, so
 * if the settlement transaction rolls back, the SMS is never sent.
 */
public record SettlementEvent(
        Long workerId,
        String workerName,
        String phone,
        int year,
        int month,
        BigDecimal totalAmount
) {
}
