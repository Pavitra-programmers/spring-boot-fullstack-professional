package com.example.demo.attendance.dto;

import com.example.demo.worker.Designation;

import java.time.LocalDateTime;

/**
 * Lightweight projection stored in Redis Hash for real-time active worker lookups.
 * Serialized as JSON via Jackson (records are natively supported in Jackson 2.12+).
 * Hash key: attendance:active, field: workerId.toString(), TTL: 16 hours.
 */
public record ActiveWorkerDto(
        Long workerId,
        String workerName,
        String phone,
        Designation designation,
        Long siteId,
        String siteName,
        LocalDateTime clockIn
) {
}
