package com.example.demo.attendance.dto;

import jakarta.validation.constraints.NotNull;

public record ClockInRequest(
        @NotNull(message = "workerId is required") Long workerId,
        @NotNull(message = "siteId is required") Long siteId
) {
}
