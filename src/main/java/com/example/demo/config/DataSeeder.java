package com.example.demo.config;

import com.example.demo.site.Site;
import com.example.demo.site.SiteRepository;
import com.example.demo.worker.Designation;
import com.example.demo.worker.Worker;
import com.example.demo.worker.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds minimal test data on startup if the tables are empty.
 * Safe to run multiple times — only inserts when count == 0.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final WorkerRepository workerRepository;
    private final SiteRepository   siteRepository;

    @Override
    public void run(String... args) {
        seedSites();
        seedWorkers();
    }

    private void seedSites() {
        if (siteRepository.count() > 0) return;
        siteRepository.saveAll(List.of(
            Site.builder().name("Bandra-Worli Sea Link").location("Bandra, Mumbai").active(true).build(),
            Site.builder().name("Metro Phase 3").location("Andheri, Mumbai").active(true).build(),
            Site.builder().name("IT Park Block C").location("Whitefield, Bangalore").active(true).build()
        ));
        log.info("[Seeder] Inserted 3 sites.");
    }

    private void seedWorkers() {
        if (workerRepository.count() > 0) return;
        workerRepository.saveAll(List.of(
            Worker.builder().name("Raju Sharma").phone("9876543210").designation(Designation.MASON).dailyWage(new BigDecimal("600.00")).active(true).build(),
            Worker.builder().name("Suresh Yadav").phone("9876543211").designation(Designation.CARPENTER).dailyWage(new BigDecimal("700.00")).active(true).build(),
            Worker.builder().name("Mohan Verma").phone("9876543212").designation(Designation.ELECTRICIAN).dailyWage(new BigDecimal("800.00")).active(true).build(),
            Worker.builder().name("Ramesh Patel").phone("9876543213").designation(Designation.PLUMBER).dailyWage(new BigDecimal("750.00")).active(true).build(),
            Worker.builder().name("Vikram Singh").phone("9876543214").designation(Designation.FOREMAN).dailyWage(new BigDecimal("900.00")).active(true).build(),
            Worker.builder().name("Arjun Das").phone("9876543215").designation(Designation.LABORER).dailyWage(new BigDecimal("500.00")).active(true).build()
        ));
        log.info("[Seeder] Inserted 6 workers.");
    }
}
