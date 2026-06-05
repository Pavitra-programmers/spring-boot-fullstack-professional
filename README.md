# LaborFlex — Workforce Attendance & Overtime Management

Spring Boot backend for construction-industry HR management.
Tracks daily worker attendance, computes overtime, and handles payroll settlement.

---

## HRMS Selection Rationale

Built on top of the **AmigoCode Spring Boot Fullstack** template (originally a student management demo). Chosen because:
- Clean 3-layer architecture (Controller → Service → Repository) that mirrors production patterns
- PostgreSQL already configured — straightforward to extend for Supabase
- Minimal boilerplate, making new feature additions easy to trace

Upgraded from Spring Boot 2.4.3 / Java 15 → **Spring Boot 3.1.5 / Java 17** to meet the assignment's Java 17+ requirement and adopt the modern `jakarta.*` namespace.

---

## Local Setup

### Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL (local) **OR** a Supabase project
- Redis (local) — app starts and works without it (graceful fallback, TICKET LF-202)

### 1. Database

**Option A — Local PostgreSQL:**
```bash
createdb laborflex
```

**Option B — Supabase:**
1. Create a project at supabase.com
2. Go to Project Settings → Database → Connection String
3. Copy the **connection pooler** URL (port 6543, not 5432) — see TICKET LF-205

### 2. Environment Variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/laborflex
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword

# Optional — app falls back to localhost:6379 if not set
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Optional — defaults to http://localhost:3000 (TICKET LF-201)
export CORS_ALLOWED_ORIGINS=http://localhost:3000
```

### 3. Run

```bash
mvn spring-boot:run

# With dev profile (Supabase URL):
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

App starts on `http://localhost:8080`.

---

## API Reference

### Attendance

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/attendance/clock-in` | Worker arrives at site |
| POST | `/api/attendance/clock-out` | Worker leaves; overtime auto-calculated |
| GET | `/api/attendance/active` | Real-time list of clocked-in workers (Redis-backed) |
| GET | `/api/attendance/log` | Paginated history with date-range filter |

**Clock-in:**
```json
POST /api/attendance/clock-in
{ "workerId": 1, "siteId": 1 }
```

**Clock-out:**
```json
POST /api/attendance/clock-out
{ "workerId": 1, "siteId": 1 }
```

**Attendance log with filters:**
```
GET /api/attendance/log?workerId=1&from=2024-01-01T00:00:00&to=2024-01-31T23:59:59&page=0&size=20
```

### Overtime

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/overtime/summary/{workerId}` | Monthly breakdown with payout amounts |
| POST | `/api/overtime/settle/{workerId}?year=2024&month=5` | Mark month as settled (past months only) |

---

## Business Rules

| Rule | Implemented In |
|------|----------------|
| Standard shift = 8 hours; excess = overtime | `AttendanceService.clockOut()` |
| OT rate: 1.5× first 2h, 2× beyond 2h | `calculateOvertimeAmount()` |
| Monthly OT cap: 60 hours per worker | `sumOvertimeHoursForPeriod()` check on clock-out |
| Shifts > 16 hours flagged for review | `AttendanceLog.flagged` |
| No double clock-in | `findByWorkerIdAndClockOutIsNull()` check |
| Cannot settle current month | `OvertimeService` guards with `YearMonth.isBefore()` |
| Settlement is atomic (all-or-nothing) | `@Transactional` on `settleOvertime()` |
| SMS only after successful settlement | `@TransactionalEventListener(AFTER_COMMIT)` |

---

## Ticket Fixes

### LF-201 — CORS
- Added `CorsConfigurationSource` bean wired into Spring Security filter chain
- Allowed origins externalized to `cors.allowed-origins` env var
- `@CrossOrigin` on controllers alone does NOT fix this — Security runs before MVC
- Files: `CorsConfig.java`, `SecurityConfig.java`

### LF-202 — Redis Graceful Degradation
- Short timeouts: `spring.data.redis.timeout=2000ms` prevents infinite hang
- `CustomCacheErrorHandler` logs and swallows all Redis exceptions for `@Cacheable` methods
- Manual Redis operations in `AttendanceService` wrapped in `try-catch` with DB fallback
- Files: `RedisConfig.java`, `CustomCacheErrorHandler.java`

### LF-203 — Attendance History Performance
- `@EntityGraph(attributePaths = {"worker", "site"})` eliminates N+1 — worker + site loaded in single LEFT JOIN
- `Pageable` adds database-level LIMIT/OFFSET
- Separate `countQuery` in `@Query` avoids count-query duplication issues
- Files: `AttendanceRepository.findByFilters()`

### LF-204 — Settlement Data Integrity & SMS Timing
- `@Transactional` wraps all settlement writes — any failure rolls back everything
- `SettlementEvent` published inside the transaction
- `@TransactionalEventListener(AFTER_COMMIT)` on `SmsNotificationService` — SMS fires only after successful commit, never on rollback
- `@Async` prevents blocking HTTP thread
- Files: `OvertimeService.java`, `SmsNotificationService.java`, `SettlementEvent.java`

### LF-205 — Connection Pool Exhaustion
- `max-lifetime=1700000` (28 min) — below Supabase's 30-min server-side connection kill
- `keepalive-time=60000` — HikariCP pings idle connections every 1 min
- `spring.jpa.open-in-view=false` — DB connection not held during serialization
- Use Supabase PgBouncer URL (port 6543) in dev/prod profiles
- Files: `application.yml`, `application-dev.yml`

---

## Design Decisions & Tradeoffs

| Decision | Rationale | Tradeoff |
|----------|-----------|----------|
| Redis Hash for active workers instead of `@Cacheable` | O(1) add/remove per worker; atomic hash updates; natural TTL matching max shift | More code than `@Cacheable`; requires manual JSON serialization |
| Store computed hours/OT in `AttendanceLog` | Fast reporting queries — no recalculation needed | Data duplication; must be consistent with `OvertimeEntry` |
| 16-hour TTL on active worker hash | Auto-expires missed clock-outs; supervisors don't see ghost entries | Late-arriving Redis entries may survive after a real clock-out if Redis was down |
| `@EntityGraph` over `JOIN FETCH` with pagination | Avoids Hibernate's in-memory pagination warning for to-one relationships | Adds Spring Data JPA annotation coupling |
| SMS via `@TransactionalEventListener` | Clean separation of concerns; SMS failure never rolls back money | Slight complexity — developers must understand the two-phase event mechanism |

---

## AI Tools Used

**Claude (Anthropic, claude-sonnet-4-6)**
- Implemented Part 1 (entities, services, controllers, Redis integration) and Part 2 (all 5 ticket fixes)
- Prompted with the full assignment spec and existing codebase structure
- All business logic (overtime calculation tiers, monthly cap, atomic settlement, connection pool settings) was cross-checked against the spec before accepting
- Entity graph strategy and `CachingConfigurer` pattern were explicitly requested with rationale

---

## Project Structure

```
src/main/java/com/example/demo/
├── DemoApplication.java             ← @EnableAsync added
├── attendance/
│   ├── AttendanceController.java
│   ├── AttendanceLog.java
│   ├── AttendanceRepository.java    ← @EntityGraph, Pageable (LF-203)
│   ├── AttendanceService.java       ← Redis + DB fallback (LF-202)
│   └── dto/
├── config/
│   ├── CorsConfig.java              ← LF-201
│   ├── CustomCacheErrorHandler.java ← LF-202
│   ├── RedisConfig.java             ← LF-202
│   └── SecurityConfig.java          ← LF-201
├── exception/
│   └── GlobalExceptionHandler.java
├── notification/
│   ├── SettlementEvent.java         ← LF-204
│   └── SmsNotificationService.java  ← LF-204
├── overtime/
│   ├── OvertimeController.java
│   ├── OvertimeEntry.java
│   ├── OvertimeRepository.java
│   ├── OvertimeService.java         ← @Transactional settlement (LF-204)
│   ├── SettlementStatus.java
│   └── dto/
├── site/
├── student/                         ← original demo
└── worker/
```
