package com.example.demo.site;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * A construction site where workers are deployed.
 */
@Entity
@Table(name = "sites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column
    private String location;

    @Column(nullable = false)
    private boolean active;
}
