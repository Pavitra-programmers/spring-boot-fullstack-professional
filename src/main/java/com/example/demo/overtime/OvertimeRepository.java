package com.example.demo.overtime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OvertimeRepository extends JpaRepository<OvertimeEntry, Long> {

    /**
     * Fetch all overtime entries for a worker within a date range.
     * Used for:
     *  - Monthly settlement (settlement service filters to PENDING)
     *  - Checking how many hours remain before the 60h cap
     */
    @Query("SELECT o FROM OvertimeEntry o WHERE o.worker.id = :workerId " +
           "AND o.date >= :startDate AND o.date <= :endDate")
    List<OvertimeEntry> findByWorkerAndDateRange(
            @Param("workerId") Long workerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Sum of overtime hours already logged for a worker in a given month.
     * Used on clock-out to enforce the 60-hour monthly cap.
     * Returns 0.0 (not null) when no entries exist.
     */
    @Query("SELECT COALESCE(SUM(o.overtimeHours), 0.0) FROM OvertimeEntry o " +
           "WHERE o.worker.id = :workerId " +
           "AND o.date >= :startDate AND o.date <= :endDate")
    Double sumOvertimeHoursForPeriod(
            @Param("workerId") Long workerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Fetch all overtime entries for a worker with the related attendance record,
     * sorted by date descending. Used for the overtime summary endpoint.
     */
    @Query("SELECT o FROM OvertimeEntry o JOIN FETCH o.attendance " +
           "WHERE o.worker.id = :workerId ORDER BY o.date DESC")
    List<OvertimeEntry> findAllByWorkerIdWithAttendance(@Param("workerId") Long workerId);
}
