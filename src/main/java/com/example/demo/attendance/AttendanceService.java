package com.example.demo.attendance;

import com.example.demo.attendance.dto.ActiveWorkerDto;
import com.example.demo.attendance.dto.AttendanceResponse;
import com.example.demo.attendance.dto.ClockInRequest;
import com.example.demo.attendance.dto.ClockOutRequest;
import com.example.demo.exception.BusinessRuleException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.overtime.OvertimeEntry;
import com.example.demo.overtime.OvertimeRepository;
import com.example.demo.overtime.SettlementStatus;
import com.example.demo.site.Site;
import com.example.demo.site.SiteRepository;
import com.example.demo.worker.Worker;
import com.example.demo.worker.WorkerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Core business logic for worker clock-in / clock-out and attendance reporting.
 *
 * Redis is used as a real-time cache for active workers (site supervisors need
 * sub-second lookups when managing 40+ workers per shift). All Redis operations
 * are wrapped in try-catch — a Redis outage degrades gracefully to DB queries.
 * (TICKET LF-202)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final String ACTIVE_WORKERS_HASH_KEY = "attendance:active";
    private static final long   REDIS_TTL_HOURS         = 16;   // max shift duration
    private static final double STANDARD_HOURS          = 8.0;
    private static final double OVERTIME_CAP_PER_MONTH  = 60.0;
    private static final double FLAG_THRESHOLD_HOURS    = 16.0;
    private static final double TIER1_HOURS             = 2.0;  // 1.5× rate applies
    private static final double TIER1_MULTIPLIER        = 1.5;
    private static final double TIER2_MULTIPLIER        = 2.0;

    private final AttendanceRepository attendanceRepository;
    private final WorkerRepository     workerRepository;
    private final SiteRepository       siteRepository;
    private final OvertimeRepository   overtimeRepository;
    private final StringRedisTemplate  redisTemplate;
    private final ObjectMapper         objectMapper;

    // ─────────────────────────────────────────────────────────────────
    // Clock-in
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    public AttendanceResponse clockIn(ClockInRequest request) {
        Worker worker = workerRepository.findById(request.workerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Worker not found: " + request.workerId()));

        if (!worker.isActive()) {
            throw new BusinessRuleException(
                    "Worker '" + worker.getName() + "' is inactive and cannot clock in.");
        }

        Site site = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Site not found: " + request.siteId()));

        // Prevent double clock-in — one open record per worker at a time
        attendanceRepository.findByWorkerIdAndClockOutIsNull(request.workerId())
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "Worker '" + worker.getName() + "' is already clocked in since "
                            + existing.getClockIn() + ". Clock out first.");
                });

        AttendanceLog log = AttendanceLog.builder()
                .worker(worker)
                .site(site)
                .clockIn(LocalDateTime.now())
                .flagged(false)
                .build();

        log = attendanceRepository.save(log);

        // Cache in Redis hash — supervisor dashboard reads from here in real-time
        cacheActiveWorker(worker, site, log);

        return toResponse(log);
    }

    // ─────────────────────────────────────────────────────────────────
    // Clock-out (atomic: updates attendance + creates overtime in one TX)
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    public AttendanceResponse clockOut(ClockOutRequest request) {
        AttendanceLog attendance = attendanceRepository
                .findByWorkerIdAndClockOutIsNull(request.workerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active clock-in found for worker: " + request.workerId()
                        + ". Did the worker clock in?"));

        LocalDateTime clockOutTime = LocalDateTime.now();
        double minutesWorked = Duration.between(attendance.getClockIn(), clockOutTime).toMinutes();
        double hoursWorked   = Math.round((minutesWorked / 60.0) * 100.0) / 100.0;

        attendance.setClockOut(clockOutTime);
        attendance.setHoursWorked(hoursWorked);
        attendance.setFlagged(hoursWorked > FLAG_THRESHOLD_HOURS);

        // ── Overtime calculation ────────────────────────────────────
        double rawOvertime = Math.max(0.0, hoursWorked - STANDARD_HOURS);

        if (rawOvertime > 0) {
            // Enforce 60-hour monthly cap — silently trim excess
            LocalDate today       = LocalDate.now();
            LocalDate monthStart  = today.withDayOfMonth(1);
            LocalDate monthEnd    = today.withDayOfMonth(today.lengthOfMonth());

            double usedThisMonth = overtimeRepository
                    .sumOvertimeHoursForPeriod(request.workerId(), monthStart, monthEnd);
            double remainingCap  = OVERTIME_CAP_PER_MONTH - usedThisMonth;
            double overtimeHours = Math.min(rawOvertime, Math.max(0.0, remainingCap));

            if (overtimeHours > 0) {
                attendance.setOvertimeHours(overtimeHours);

                Worker worker      = attendance.getWorker();
                double hourlyRate  = worker.getDailyWage().doubleValue() / STANDARD_HOURS;
                double amount      = calculateOvertimeAmount(overtimeHours, hourlyRate);

                OvertimeEntry entry = OvertimeEntry.builder()
                        .worker(worker)
                        .attendance(attendance)
                        .date(today)
                        .overtimeHours(overtimeHours)
                        .rate(hourlyRate)
                        .amount(BigDecimal.valueOf(amount)
                                .setScale(2, RoundingMode.HALF_UP))
                        .settlementStatus(SettlementStatus.PENDING)
                        .build();

                overtimeRepository.save(entry);
            }
        }

        attendance = attendanceRepository.save(attendance);

        // Remove worker from Redis active set
        removeFromRedis(request.workerId());

        return toResponse(attendance);
    }

    // ─────────────────────────────────────────────────────────────────
    // Active workers (Redis → DB fallback)   TICKET LF-202
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ActiveWorkerDto> getActiveWorkers() {
        // Try Redis first — O(n) scan of the hash is fast for typical shift sizes
        try {
            Map<Object, Object> entries =
                    redisTemplate.opsForHash().entries(ACTIVE_WORKERS_HASH_KEY);
            if (!entries.isEmpty()) {
                return entries.values().stream()
                        .map(v -> {
                            try {
                                return objectMapper.readValue(v.toString(), ActiveWorkerDto.class);
                            } catch (JsonProcessingException e) {
                                log.warn("[Redis] Failed to deserialize active worker entry: {}", e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (Exception e) {
            // TICKET LF-202: Redis unavailable — fall through to DB query
            log.warn("[Redis] Unavailable for active workers lookup. Using database. Cause: {}", e.getMessage());
        }

        // Fallback: query DB with @EntityGraph to avoid N+1
        return attendanceRepository.findAllActive().stream()
                .map(a -> new ActiveWorkerDto(
                        a.getWorker().getId(),
                        a.getWorker().getName(),
                        a.getWorker().getPhone(),
                        a.getWorker().getDesignation(),
                        a.getSite().getId(),
                        a.getSite().getName(),
                        a.getClockIn()))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────
    // Attendance log — paginated, date-range filtered   TICKET LF-203
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getAttendanceLog(
            Long workerId, LocalDateTime from, LocalDateTime to, Pageable pageable) {

        // Default to last 30 days when no range supplied
        if (from == null) from = LocalDateTime.now().minusDays(30);
        if (to   == null) to   = LocalDateTime.now();

        // TICKET LF-203: repository uses @EntityGraph to load worker+site in one
        // LEFT JOIN query, and Pageable for LIMIT/OFFSET — no N+1, no full-table scan.
        return attendanceRepository.findByFilters(workerId, from, to, pageable)
                .map(this::toResponse);
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Overtime rate tiers:
     *   hours ≤ 2 → 1.5× hourly rate
     *   hours > 2 → 1.5× for the first 2h, then 2× for the rest
     */
    private double calculateOvertimeAmount(double overtimeHours, double hourlyRate) {
        if (overtimeHours <= TIER1_HOURS) {
            return overtimeHours * hourlyRate * TIER1_MULTIPLIER;
        }
        return (TIER1_HOURS * hourlyRate * TIER1_MULTIPLIER)
             + ((overtimeHours - TIER1_HOURS) * hourlyRate * TIER2_MULTIPLIER);
    }

    private void cacheActiveWorker(Worker worker, Site site, AttendanceLog attendance) {
        try {
            ActiveWorkerDto dto = new ActiveWorkerDto(
                    worker.getId(), worker.getName(), worker.getPhone(),
                    worker.getDesignation(), site.getId(), site.getName(),
                    attendance.getClockIn());
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForHash().put(ACTIVE_WORKERS_HASH_KEY, worker.getId().toString(), json);
            redisTemplate.expire(ACTIVE_WORKERS_HASH_KEY, REDIS_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            // TICKET LF-202: Redis failure must not block a clock-in
            log.warn("[Redis] Failed to cache active worker {}. Clock-in still recorded in DB. Cause: {}",
                    worker.getId(), e.getMessage());
        }
    }

    private void removeFromRedis(Long workerId) {
        try {
            redisTemplate.opsForHash().delete(ACTIVE_WORKERS_HASH_KEY, workerId.toString());
        } catch (Exception e) {
            log.warn("[Redis] Failed to remove worker {} from active set. Entry will expire via TTL. Cause: {}",
                    workerId, e.getMessage());
        }
    }

    private AttendanceResponse toResponse(AttendanceLog a) {
        return new AttendanceResponse(
                a.getId(),
                a.getWorker().getId(),
                a.getWorker().getName(),
                a.getWorker().getDesignation(),
                a.getSite().getId(),
                a.getSite().getName(),
                a.getClockIn(),
                a.getClockOut(),
                a.getHoursWorked(),
                a.getOvertimeHours(),
                a.isFlagged());
    }
}
