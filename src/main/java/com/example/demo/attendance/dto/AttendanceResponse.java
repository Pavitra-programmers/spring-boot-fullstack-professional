package com.example.demo.attendance.dto;

import com.example.demo.worker.Designation;

import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id,
        Long workerId,
        String workerName,
        Designation designation,
        Long siteId,
        String siteName,
        LocalDateTime clockIn,
        LocalDateTime clockOut,
        Double hoursWorked,
        Double overtimeHours,
        boolean flagged
) {
}
