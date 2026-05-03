# Banking Platform

A simulated banking management system built to demonstrate the engineering patterns used by core banking systems — double-entry ledger, idempotent transfers, immutable audit log, and concurrency-safe money movement.

> **Note:** This is a learning/portfolio project. No real money, no real PCI compliance. The point is to model how a real bank's backend works.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security, Flyway
- **Database:** PostgreSQL 16 (Dockerized)
- **Frontend:** React + TypeScript + Vite *(coming soon)*
- **Infrastructure:** Docker Compose, GitHub Actions *(coming soon)*

## Status

🚧 **In active development.** Building session-by-session.

- [x] Session 1 — Project scaffold, Postgres in Docker, Flyway, Actuator health
- [x] Session 2 — Domain models (Customer, UserAccount, Account, AccountHolder, FINTRAC-aligned KYC fields)
- [x] Session 3 — JWT auth (BCrypt, lockout, refresh rotation, theft detection, IDOR-safe /me)
- [ ] Session 4 — Double-entry ledger
- [ ] Session 5 — Transfer service with pessimistic locking + idempotency keys
- [ ] Session 6 — Transaction history, dashboard UI
- [ ] Session 7 — Audit log, fraud rules, observability
- [ ] Session 8 — Spring Batch interest accrual, PDF statements
- [ ] Session 9 — Role-based access control, scheduled transfers
- [ ] Session 10 — Interac e-Transfer simulation
- [ ] Session 11 — CI/CD, deploy live
- [ ] Session 12 — Polish, architecture diagrams, demo seed data

## Run locally

```bash
# Start Postgres
docker compose up -d

# Run the backend
cd backend
mvn spring-boot:run

# Verify
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## Architecture

*(coming in Session 12 — diagram of services, ledger flow, transfer sequence)*

## License

MIT
