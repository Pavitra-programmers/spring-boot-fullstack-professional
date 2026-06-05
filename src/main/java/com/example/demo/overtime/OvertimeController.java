package com.example.demo.overtime;

import com.example.demo.overtime.dto.OvertimeSummaryDto;
import com.example.demo.overtime.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Overtime API — monthly summary and settlement.
 *
 * Endpoints:
 *   GET  /api/overtime/summary/{workerId}              → monthly breakdown with payout amounts
 *   POST /api/overtime/settle/{workerId}?year=&month=  → mark month as settled (past months only)
 */
@RestController
@RequestMapping("/api/overtime")
@RequiredArgsConstructor
public class OvertimeController {

    private final OvertimeService overtimeService;

    /**
     * Returns all overtime history for a worker, grouped by month.
     * Each month shows total hours, total payout, settlement status, and entry-level detail.
     */
    @GetMapping("/summary/{workerId}")
    public ResponseEntity<OvertimeSummaryDto> getOvertimeSummary(
            @PathVariable Long workerId) {
        return ResponseEntity.ok(overtimeService.getOvertimeSummary(workerId));
    }

    /**
     * Settle all PENDING overtime entries for a worker in the given month.
     *
     * Rules enforced:
     *   - Cannot settle current or future months.
     *   - Settlement is atomic — all entries or none (TICKET LF-204).
     *   - SMS notification fires only after successful DB commit.
     *
     * Example: POST /api/overtime/settle/42?year=2024&month=5
     */
    @PostMapping("/settle/{workerId}")
    public ResponseEntity<SettlementResponse> settleOvertime(
            @PathVariable Long workerId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(overtimeService.settleOvertime(workerId, year, month));
    }
}
