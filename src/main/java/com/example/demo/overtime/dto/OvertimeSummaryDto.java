package com.example.demo.overtime.dto;

import java.util.List;

public record OvertimeSummaryDto(
        Long workerId,
        String workerName,
        List<MonthlyBreakdownDto> months
) {
}
