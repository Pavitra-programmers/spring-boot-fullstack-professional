package com.example.demo.overtime.dto;

import com.example.demo.overtime.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OvertimeEntryDto(
        Long id,
        LocalDate date,
        double overtimeHours,
        double rate,
        BigDecimal amount,
        SettlementStatus settlementStatus
) {
}
