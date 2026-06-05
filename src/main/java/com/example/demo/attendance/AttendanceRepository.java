package com.example.demo.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceLog, Long> {

    /**
     * Find an open (not yet clocked-out) attendance record for a worker.
     * Used to prevent double clock-ins and to locate the record on clock-out.
     * @EntityGraph loads worker+site in the same JOIN query — no N+1.
     */
    @EntityGraph(attributePaths = {"worker", "site"})
    Optional<AttendanceLog> findByWorkerIdAndClockOutIsNull(Long workerId);

    /**
     * TICKET LF-203: Paginated attendance log with filters.
     *
     * @EntityGraph on a @ManyToOne relationship adds LEFT JOINs, solving the
     * N+1 problem without Hibernate's "in-memory pagination" warning
     * (that warning only applies to to-many / collection fetches).
     *
     * The separate countQuery avoids duplicates in the count calculation.
     */
    @EntityGraph(attributePaths = {"worker", "site"})
    @Query(value = "SELECT a FROM AttendanceLog a WHERE " +
                   "(:workerId IS NULL OR a.worker.id = :workerId) AND " +
                   "a.clockIn >= :from AND a.clockIn <= :to",
           countQuery = "SELECT COUNT(a) FROM AttendanceLog a WHERE " +
                        "(:workerId IS NULL OR a.worker.id = :workerId) AND " +
                        "a.clockIn >= :from AND a.clockIn <= :to")
    Page<AttendanceLog> findByFilters(
            @Param("workerId") Long workerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * Returns all currently active (clocked-in) workers.
     * Used as the Redis fallback for GET /api/attendance/active.
     */
    @EntityGraph(attributePaths = {"worker", "site"})
    @Query("SELECT a FROM AttendanceLog a WHERE a.clockOut IS NULL")
    List<AttendanceLog> findAllActive();
}
