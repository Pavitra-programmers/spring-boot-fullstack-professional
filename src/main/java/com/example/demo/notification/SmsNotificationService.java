package com.example.demo.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * TICKET LF-204 — Transactionally-safe SMS notification.
 *
 * Root cause of the original bug:
 *   SMS was sent INSIDE the settlement transaction, before COMMIT.
 *   If the transaction later rolled back (e.g. a DB constraint violation
 *   on entry #3 of 5), the worker had already received an SMS confirming
 *   a settlement that never actually happened.
 *
 * Fix:
 *   1. OvertimeService publishes a SettlementEvent via ApplicationEventPublisher.
 *   2. This listener uses @TransactionalEventListener(phase = AFTER_COMMIT).
 *      Spring only invokes it once the DB transaction is committed — if the
 *      transaction rolls back, this method is never called.
 *   3. @Async runs the SMS in a separate thread so it doesn't hold the
 *      HTTP response thread open.
 *   4. Any SMS gateway failure is caught and logged for retry-queue integration
 *      (Twilio, AWS SNS, etc.) — a failed notification does NOT roll back the
 *      already-committed settlement.
 */
@Slf4j
@Service
public class SmsNotificationService {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onSettlementCommitted(SettlementEvent event) {
        try {
            String message = buildMessage(event);
            log.info("[SMS] Sending to worker {} ({}): {}",
                    event.workerName(), event.phone(), message);

            // TODO: Integrate real SMS gateway — example:
            // twilioClient.messages.create(event.phone(), FROM_NUMBER, message);

        } catch (Exception e) {
            // A failed SMS must NOT affect the already-committed settlement.
            // Log for retry queue integration (Redis List / SQS dead-letter queue).
            log.error("[SMS] Notification failed for worker {} (month={}/{}) — queuing for retry. Cause: {}",
                    event.workerId(), event.year(), event.month(), e.getMessage());

            // TODO: Push to retry queue:
            // retryQueue.push(new SmsRetryPayload(event));
        }
    }

    private String buildMessage(SettlementEvent event) {
        return String.format(
                "Dear %s, your overtime for %d/%02d has been settled. Amount: ₹%.2f. -LaborFlex",
                event.workerName(), event.year(), event.month(),
                event.totalAmount().doubleValue());
    }
}
