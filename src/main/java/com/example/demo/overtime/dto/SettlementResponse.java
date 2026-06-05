package com.example.demo.overtime.dto;

import java.math.BigDecimal;

public record SettlementResponse(
        Long workerId,
        int year,
        int month,
        int entriesSettled,
        BigDecimal totalSettledAmount
) {
}
