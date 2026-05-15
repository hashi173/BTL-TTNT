# Hashiji Cafe -- Coffee Shop Management System

A full-stack coffee shop ordering and admin management platform built with **Java 17 / Spring Boot 3**. The current scope covers storefront ordering, product/category/topping management, order tracking, admin reporting, and an AI recommendation/evaluation dashboard.

---

## Key Features

### Customer Storefront
- **Responsive homepage:** Product menu, category filtering, search, and personalized recommendations.
- **Shopping cart:** Product size, toppings, sugar/ice options, notes, and session-backed cart state.
- **Checkout & tracking:** Anonymous or logged-in checkout, generated `ORD-XXXXXX` tracking codes, public order lookup, and PDF invoices.
- **Profile pages:** Registered users can update profile details and view their order history.

### Admin Dashboard
- **Product management:** Create/edit products, upload product images, paste image URLs, and activate/deactivate products.
- **Catalog management:** Category and topping CRUD with cache eviction after catalog changes.
- **Order workflow:** Manage `PENDING -> CONFIRMED -> SHIPPING -> COMPLETED` orders and cancellations.
- **Reporting:** Dashboard and monthly history pages with Chart.js revenue and top-product visualizations.
- **User operations:** Admin user list, status toggle, and password reset.

### AI Recommendation Module
- **Hybrid recommendations:** Collaborative Filtering + Rule-based fallback for cold start users.
- **Cross-selling:** "You may also like" suggestions based on co-occurrence and category fallback.
- **Demo seeding:** `DataSeeder.java` creates 50 products, 30 users, and 10 months of synthetic order history for repeatable demos.

### Persistence & Security
- **UUID primary keys:** Used across entities while human-readable codes are generated for display.
- **Spring Data JPA:** Hibernate manages schema updates in dev/demo environments.
- **Transactions:** Checkout writes orders and order items atomically through `OrderService`.
- **Spring Security:** Form login, BCrypt password hashing, role-based admin access, and CSRF protection.
- **Caching:** Spring Cache defaults to in-memory cache, with Redis support available through `spring.cache.type=redis`.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2, Spring Security, Spring Data JPA |
| Database | PostgreSQL (Docker, local, or cloud) |
| Frontend | Thymeleaf, Bootstrap 5, HTMX, Chart.js |
| Caching | Spring Cache (Simple by default, Redis optional) |
| Build | Maven Wrapper |
| Tests | JUnit 5, Spring Boot Test, H2 |

---

## Quick Start

### Docker

```bash
docker compose up -d --build
```

The application runs at `http://localhost:8080`, and pgAdmin runs at `http://localhost:5050`.

### Local Dev

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The default `dev` profile enables demo data seeding through `src/main/resources/application-dev.properties`.

Default admin account:

| Username | Password |
|---|---|
| `admin` | `123456` |

---

## Project Structure

```text
src/main/java/com/coffeeshop/
  config/       -- Security, seeding, MVC, cache, metadata backfill
  controller/   -- MVC controllers for storefront, admin, AI, users, orders
  dto/          -- Cart DTOs
  entity/       -- JPA entities
  repository/   -- Spring Data JPA repositories
  service/      -- Business logic and AI services
  util/         -- Shared display/formatting helpers

src/main/resources/
  application*.properties
  seed-data.sql       -- Manual SQL-only demo data
  templates/          -- Thymeleaf templates
  static/             -- CSS, JS, and image assets
  messages.properties -- Message bundle
```

---

## Documentation

- [Deployment Guide](docs/DEPLOYMENT.md)
- [Docker Guide](docs/DOCKER_GUIDE.md)
- [AI System](docs/AI-SYSTEM.md)
- [AI Crash Course (Hướng dẫn Báo cáo Dành cho SV)](docs/AI-CRASH-COURSE.md)
- [Giải Mã Thuật Toán AI (Toán học & Logic)](docs/AI-ALGORITHM-EXPLAINED.md)

---

## License

This project is for educational and portfolio purposes.
