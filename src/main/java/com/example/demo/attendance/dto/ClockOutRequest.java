package com.example.demo.attendance.dto;

import jakarta.validation.constraints.NotNull;

public record ClockOutRequest(
        @NotNull(message = "workerId is required") Long workerId,
        @NotNull(message = "siteId is required") Long siteId
) {
}
