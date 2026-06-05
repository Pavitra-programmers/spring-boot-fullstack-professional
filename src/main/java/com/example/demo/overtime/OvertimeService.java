package com.example.demo.overtime;

import com.example.demo.exception.BusinessRuleException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.notification.SettlementEvent;
import com.example.demo.overtime.dto.MonthlyBreakdownDto;
import com.example.demo.overtime.dto.OvertimeEntryDto;
import com.example.demo.overtime.dto.OvertimeSummaryDto;
import com.example.demo.overtime.dto.SettlementResponse;
import com.example.demo.worker.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Overtime summary and settlement logic.
 *
 * TICKET LF-204 — Atomic settlement:
 *   The settlement method is @Transactional with default propagation (REQUIRED).
 *   All OvertimeEntry rows for the requested month are updated in a single
 *   transaction — if ANY write fails, the whole thing rolls back (no partial
 *   settlements). The SMS event is published after the repository writes but
 *   before the transaction commits; the @TransactionalEventListener in
 *   SmsNotificationService fires only after a successful COMMIT.
 *
 * Why "cannot settle current month":
 *   Overtime entries may still be added during the current month. Settling
 *   early would leave newly added entries in PENDING, creating a split-month
 *   state that confuses payroll. Only completed months can be settled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OvertimeService {

    private final OvertimeRepository   overtimeRepository;
    private final WorkerRepository     workerRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ─────────────────────────────────────────────────────────────────
    // GET /api/overtime/summary/{workerId}
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OvertimeSummaryDto getOvertimeSummary(Long workerId) {
        var worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + workerId));

        List<OvertimeEntry> entries = overtimeRepository.findAllByWorkerIdWithAttendance(workerId);

        // Group by year-month, then build per-month breakdown DTOs
        Map<YearMonth, List<OvertimeEntry>> byMonth = entries.stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getDate())));

        List<MonthlyBreakdownDto> months = byMonth.entrySet().stream()
                .sorted(Map.Entry.<YearMonth, List<OvertimeEntry>>comparingByKey().reversed())
                .map(entry -> buildMonthlyBreakdown(entry.getKey(), entry.getValue()))
                .toList();

        return new OvertimeSummaryDto(workerId, worker.getName(), months);
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /api/overtime/settle/{workerId}?year=&month=
    // ─────────────────────────────────────────────────────────────────

    /**
     * TICKET LF-204: @Transactional here means either ALL entries for the
     * requested month are marked SETTLED, or NONE are (rollback on any failure).
     *
     * The SMS SettlementEvent is published inside the transaction. Spring's
     * @TransactionalEventListener(AFTER_COMMIT) in SmsNotificationService
     * guarantees the notification fires only after the DB commit succeeds.
     * If a rollback occurs, the listener is never invoked — no false SMS.
     */
    @Transactional
    public SettlementResponse settleOvertime(Long workerId, int year, int month) {
        var worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + workerId));

        // Business rule: cannot settle current or future months
        YearMonth requested = YearMonth.of(year, month);
        YearMonth current   = YearMonth.now();
        if (!requested.isBefore(current)) {
            throw new BusinessRuleException(
                    "Cannot settle current or future month. Requested: " + requested
                    + ", current: " + current + ". Only past months can be settled.");
        }

        var startDate = requested.atDay(1);
        var endDate   = requested.atEndOfMonth();

        List<OvertimeEntry> pending = overtimeRepository
                .findByWorkerAndDateRange(workerId, startDate, endDate)
                .stream()
                .filter(e -> e.getSettlementStatus() == SettlementStatus.PENDING)
                .toList();

        if (pending.isEmpty()) {
            throw new BusinessRuleException(
                    "No PENDING overtime entries for worker " + workerId
                    + " in " + requested + ". Already settled or no overtime recorded.");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OvertimeEntry entry : pending) {
            entry.setSettlementStatus(SettlementStatus.SETTLED);
            totalAmount = totalAmount.add(entry.getAmount());
        }

        overtimeRepository.saveAll(pending); // single batch UPDATE

        log.info("[Settlement] Worker {} — {}/{}: {} entries settled, total ₹{}",
                worker.getName(), year, month, pending.size(), totalAmount);

        // TICKET LF-204: Publish inside transaction — SMS listener fires AFTER_COMMIT only
        eventPublisher.publishEvent(
                new SettlementEvent(workerId, worker.getName(), worker.getPhone(),
                        year, month, totalAmount));

        return new SettlementResponse(workerId, year, month, pending.size(), totalAmount);
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private MonthlyBreakdownDto buildMonthlyBreakdown(YearMonth ym, List<OvertimeEntry> entries) {
        double     totalHours  = entries.stream().mapToDouble(OvertimeEntry::getOvertimeHours).sum();
        BigDecimal totalAmount = entries.stream()
                .map(OvertimeEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Month is SETTLED only if every single entry is settled
        SettlementStatus status = entries.stream()
                .allMatch(e -> e.getSettlementStatus() == SettlementStatus.SETTLED)
                ? SettlementStatus.SETTLED
                : SettlementStatus.PENDING;

        List<OvertimeEntryDto> entryDtos = entries.stream()
                .map(e -> new OvertimeEntryDto(
                        e.getId(), e.getDate(), e.getOvertimeHours(),
                        e.getRate(), e.getAmount(), e.getSettlementStatus()))
                .toList();

        return new MonthlyBreakdownDto(
                ym.getYear(), ym.getMonthValue(),
                totalHours, totalAmount, status, entryDtos);
    }
}
