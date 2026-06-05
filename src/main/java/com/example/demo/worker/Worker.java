package com.example.demo.worker;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

/**
 * A daily-wage construction worker registered in the system.
 * dailyWage is the standard 8-hour day pay; overtime rates are derived from it.
 */
@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String phone;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Designation designation;

    /**
     * Daily wage for an 8-hour standard shift.
     * Hourly rate = dailyWage / 8; used for overtime calculations.
     */
    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyWage;

    @Column(nullable = false)
    private boolean active;
}
