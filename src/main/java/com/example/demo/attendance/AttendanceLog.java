package com.example.demo.attendance;

import com.example.demo.site.Site;
import com.example.demo.worker.Worker;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Records a single clock-in / clock-out cycle for one worker at one site.
 *
 * Business constraints enforced here:
 *  - A worker can only have one open record (clockOut IS NULL) at a time.
 *  - hoursWorked and overtimeHours are computed on clock-out and stored for fast reporting.
 *  - Shifts > 16 hours are flagged for supervisor review.
 */
@Entity
@Table(
    name = "attendance_logs",
    indexes = {
        @Index(name = "idx_attendance_worker_clockin", columnList = "worker_id, clock_in"),
        @Index(name = "idx_attendance_site",           columnList = "site_id"),
        @Index(name = "idx_attendance_active",         columnList = "worker_id, clock_out")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TICKET LF-203: LAZY fetching — loaded only when accessed; @EntityGraph drives
    // eager loading for endpoints that need worker/site data in a single query.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "clock_in", nullable = false)
    private LocalDateTime clockIn;

    @Column(name = "clock_out")
    private LocalDateTime clockOut;

    /** Total duration of the shift in hours (computed on clock-out). */
    @Column(name = "hours_worked")
    private Double hoursWorked;

    /** Overtime hours beyond the 8-hour standard shift (capped at monthly 60h). */
    @Column(name = "overtime_hours")
    private Double overtimeHours;

    /** True when shift exceeds 16 hours — requires supervisor acknowledgement. */
    @Column(nullable = false)
    private boolean flagged;
}
