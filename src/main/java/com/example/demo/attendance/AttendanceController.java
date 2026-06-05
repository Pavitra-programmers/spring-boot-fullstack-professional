package com.example.demo.attendance;

import com.example.demo.attendance.dto.ActiveWorkerDto;
import com.example.demo.attendance.dto.AttendanceResponse;
import com.example.demo.attendance.dto.ClockInRequest;
import com.example.demo.attendance.dto.ClockOutRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Attendance API — clock-in / clock-out, real-time active workers, history log.
 *
 * Endpoints:
 *   POST /api/attendance/clock-in       → record worker arrival
 *   POST /api/attendance/clock-out      → record departure + compute overtime
 *   GET  /api/attendance/active         → Redis-backed real-time active list
 *   GET  /api/attendance/log            → paginated history with date-range filter
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/clock-in")
    public ResponseEntity<AttendanceResponse> clockIn(
            @Valid @RequestBody ClockInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceService.clockIn(request));
    }

    @PostMapping("/clock-out")
    public ResponseEntity<AttendanceResponse> clockOut(
            @Valid @RequestBody ClockOutRequest request) {
        return ResponseEntity.ok(attendanceService.clockOut(request));
    }

    /**
     * Real-time list of currently clocked-in workers.
     * Reads from Redis hash (16h TTL) — falls back to DB if Redis is unavailable.
     * Designed for site supervisors managing 40+ workers per shift.
     */
    @GetMapping("/active")
    public ResponseEntity<List<ActiveWorkerDto>> getActiveWorkers() {
        return ResponseEntity.ok(attendanceService.getActiveWorkers());
    }

    /**
     * Paginated attendance history with optional filters.
     *
     * Query params:
     *   workerId  — filter to one worker (optional)
     *   from      — ISO datetime, default: 30 days ago
     *   to        — ISO datetime, default: now
     *   page/size — standard Spring Pageable params (default size=20)
     *   sort      — default: clockIn,desc
     *
     * Example: GET /api/attendance/log?workerId=42&from=2024-01-01T00:00:00&page=0&size=10
     */
    @GetMapping("/log")
    public ResponseEntity<Page<AttendanceResponse>> getAttendanceLog(
            @RequestParam(required = false) Long workerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "clockIn") Pageable pageable) {
        return ResponseEntity.ok(
                attendanceService.getAttendanceLog(workerId, from, to, pageable));
    }
}
