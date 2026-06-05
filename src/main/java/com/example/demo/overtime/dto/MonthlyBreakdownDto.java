package com.example.demo.overtime.dto;

import com.example.demo.overtime.SettlementStatus;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyBreakdownDto(
        int year,
        int month,
        double totalOvertimeHours,
        BigDecimal totalAmount,
        SettlementStatus status,
        List<OvertimeEntryDto> entries
) {
}
