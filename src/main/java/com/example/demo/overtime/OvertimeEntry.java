package com.example.demo.overtime;

import com.example.demo.attendance.AttendanceLog;
import com.example.demo.worker.Worker;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tracks earned overtime for a single attendance shift.
 *
 * Rate tiers (applied in OvertimeService):
 *  - First 2 overtime hours: 1.5 × hourly rate
 *  - Beyond 2 overtime hours: 2.0 × hourly rate
 *
 * Monthly cap: 60 hours per worker — excess hours are silently trimmed on clock-out.
 *
 * TICKET LF-204: Settlement is atomic — all PENDING entries for a month are
 * flipped to SETTLED in one transaction; partial commits are prevented.
 */
@Entity
@Table(
    name = "overtime_entries",
    indexes = {
        @Index(name = "idx_overtime_worker_date",   columnList = "worker_id, date"),
        @Index(name = "idx_overtime_settlement",    columnList = "worker_id, settlement_status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id", nullable = false)
    private AttendanceLog attendance;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "overtime_hours", nullable = false)
    private Double overtimeHours;

    /** Base hourly rate (dailyWage / 8) used as the multiplier base. */
    @Column(nullable = false)
    private Double rate;

    /** Total payout amount already accounting for 1.5× / 2× tiers. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false)
    private SettlementStatus settlementStatus;
}
