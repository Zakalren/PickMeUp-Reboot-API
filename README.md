# PickMeUp Reboot API

> A Spring Boot REST API rebuilt from a **2021 ROK Air Force Hackathon award-winning project**, migrating a Node.js + Express + EJS monolith to a modern Java backend with intentional architectural decisions.

[![CI](https://github.com/Zakalren/PickMeUp-Reboot-API/actions/workflows/ci.yml/badge.svg)](https://github.com/Zakalren/PickMeUp-Reboot-API/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=springboot)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6-green?logo=springsecurity)
![JPA](https://img.shields.io/badge/JPA-Hibernate-blue)
![Tests](https://img.shields.io/badge/Tests-JUnit%205-red?logo=junit5)
![Status](https://img.shields.io/badge/Status-In%20Progress-yellow)

## 📌 Why This Rebuild

The [original PickMeUp](https://github.com/Zakalren/PickMeUp) was awarded the **Encouragement Prize at the 2021 ROK Air Force Hackathon** for designing a contactless pickup system for military Base Exchange (BX) stores. It was a monolithic Node.js + Express + EJS application.

This rebuild has three explicit goals:

1. **Migrate to a modern JVM stack** suitable for Japanese new-graduate backend roles (Mercari, LINE Yahoo, Rakuten, etc.) where Spring Boot / Java is the dominant backend technology.
2. **Practice modern architectural patterns** in depth — domain-driven packaging, separation of API and frontend, layered testing.
3. **Document architectural decisions** through commit history, so future readers (including interviewers) can trace the reasoning.

This is a **learning-driven portfolio project**, not a production system. Every architectural choice is intentional and explained.

## 🏗️ Tech Stack

| Layer | Tools |
|---|---|
| **Language** | Java 25 LTS |
| **Framework** | Spring Boot 4.1.0, Spring Security 6 |
| **Persistence** | Spring Data JPA, Hibernate, H2 (dev) / MySQL 8.4 (prod) |
| **Schema Migration** | Flyway (versioned SQL, `ddl-auto: validate` in prod) |
| **Build** | Gradle (Kotlin DSL) |
| **API Docs** | springdoc-openapi 3 (Swagger UI) |
| **Testing** | JUnit 5, Mockito, AssertJ, Spring Security Test |
| **Auth** | Session-based + BCrypt, role-based authorization (see decisions below) |
| **CI/CD** | GitHub Actions (test + prod-profile boot check → multi-arch image on GHCR → SSH deploy), Dependabot |
| **Container** | Multi-stage Dockerfile (Temurin 25 JDK build / JRE-alpine non-root runtime) |
| **Deployment** | OCI arm64 instance, nginx reverse proxy + TLS (same-origin topology) |

## 🧠 Architectural Decisions

This section is the heart of the rebuild — the *why* behind each choice.

### 1. REST API + Separated Frontend (vs. Original Monolithic EJS)

The original PickMeUp rendered HTML directly via EJS. This rebuild **intentionally separates the backend API from the frontend**, aligning with modern API-first patterns used by Mercari, LINE Yahoo, and similar companies. The same backend can serve web, mobile, and external partners.

### 2. Session-Based Auth Over JWT

After evaluating both options, **session-based authentication was deliberately chosen over JWT** for this single-backend domain. Rationale:

- **Immediate logout / server-side revocation** — critical for security-sensitive domains like military BX. JWT requires either short expirations or a blacklist (which negates statelessness).
- **No microservice / SSO requirements** — JWT's stateless advantage applies when authentication needs to cross service boundaries. This project has a single backend.
- **Simpler attack surface** — no token secret to leak, no algorithm-confusion vulnerabilities.

In a microservices environment like Mercari's production, JWT or OAuth2 Resource Server would be more appropriate. The decision is contextual.

### 3. Domain-Driven Package Structure

Packages are organized by **business domain** (`user`, `product`, `cart`, `auth`), not by technical layer (`controllers`, `services`, `repositories`). Each domain owns its own DTOs, exceptions, and exception handlers.

```
dev.zakalren.pickmeup
├── user/
│   ├── User.java
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   ├── CustomUserDetailsService.java
│   ├── dto/
│   └── exception/
├── product/
│   ├── Product.java
│   ├── ProductController.java
│   ├── ...
│   ├── dto/
│   └── exception/
├── cart/
│   ├── CartItem.java
│   ├── CartItemController.java
│   ├── ...
│   ├── dto/
│   └── exception/
├── order/
│   ├── Order.java
│   ├── OrderItem.java
│   ├── OrderController.java
│   ├── ...
│   ├── dto/
│   └── exception/
├── auth/
│   ├── AuthController.java
│   ├── dto/
│   └── exception/
├── config/
│   ├── SecurityConfig.java
│   └── CorsConfig.java   (dev profile only)
└── common/
    └── GlobalExceptionHandler.java
```

This structure scales better when domains grow — each can later be extracted into a microservice if needed.

### 4. Encapsulated Entity Design

Entities expose **no public setters**. All state changes go through intention-revealing methods:

```java
// Not this:
user.setEncodedPassword(newPassword);

// But this:
user.create(serviceNumber, encodedPassword, name, ...);  // static factory
user.updateQuantity(newQuantity);                         // domain operation
```

`@NoArgsConstructor(access = PROTECTED)` prevents external instantiation while still allowing Hibernate's proxy mechanism to work.

### 5. Per-Domain Exception Handlers

Instead of a single `GlobalExceptionHandler` accumulating handlers from every domain, each domain has its own `@RestControllerAdvice` **scoped to its own controller via `assignableTypes`** (an unscoped advice silently applies globally — package placement alone does not isolate it):

- `UserExceptionHandler` — `UserNotFoundException`, `DuplicateUserException`
- `ProductExceptionHandler` — `ProductNotFoundException`
- `CartExceptionHandler` — cart exceptions plus the cross-domain not-found exceptions cart endpoints can surface
- `AuthExceptionHandler` — authentication-related exceptions
- `GlobalExceptionHandler` (in `common/`) — only cross-cutting concerns (validation, unhandled exceptions)

The shared `ErrorResponse` record stays in `common`. This keeps each domain self-contained.

### 6. Same-Origin Deployment Topology

Prod serves the front-end and API from **one origin behind a reverse proxy**. Combined with session-cookie auth this keeps `SameSite=Lax` effective as CSRF mitigation and requires no CORS in production; CORS is opened only in the dev profile for a locally served front-end. Cross-origin deployment would force `SameSite=None` cookies plus CSRF tokens — a deliberate trade-off decision.

### 7. CI Verifies the Prod Profile, Not Just Tests

H2-based tests cannot catch prod-only failures (MySQL reserved words, missing Flyway auto-configuration, schema drift). The CI pipeline therefore boots the actual prod profile against a real MySQL 8.4 service container on every push — Flyway migrations apply, Hibernate `validate` passes, and the app must answer HTTP before an image is published.

## 🧪 Testing Strategy

This project follows the **test pyramid**:

```
       ╱─────────────╲
      ╱  Integration  ╲     @SpringBootTest
     ╱      (few)      ╲    Full context, real flows
    ╱───────────────────╲
   ╱       Slice         ╲  @WebMvcTest, @DataJpaTest
  ╱       (medium)        ╲ Partial Spring context
 ╱─────────────────────────╲
╱         Unit              ╲  Mockito + JUnit
╲       (many, fast)        ╱  No Spring, fastest
 ╲─────────────────────────╱
```

### Test Coverage

| Type | Examples |
|---|---|
| **Unit (Mockito)** | `UserServiceTest`, `ProductServiceTest`, `CartItemServiceTest` — verifies business logic in isolation |
| **Slice — Repository** | `UserRepositoryTest`, `CartItemRepositoryTest` (`@DataJpaTest`) — JPA query generation, N+1 detection via Hibernate Statistics |
| **Slice — Controller** | `UserControllerTest`, `AuthControllerTest`, `CartItemControllerTest`, `ProductControllerTest` (`@WebMvcTest`) — HTTP layer, Spring Security, role-based access rules |
| **Integration** | `UserSignupIntegrationTest` (`@SpringBootTest`) — signup → login → authenticated request flow, session-id rotation on login, logout session invalidation |
| **CI-only** | `prod-boot-check` job — boots the prod profile against real MySQL 8.4 (Flyway + `validate` + HTTP smoke) |

## 🚀 Running Locally

```bash
git clone https://github.com/Zakalren/PickMeUp-Reboot-API.git
cd PickMeUp-Reboot-API
./gradlew bootRun
```

The application starts on `http://localhost:8080` (dev profile, in-memory H2).

### Prod Profile (MySQL + Flyway)

```bash
docker compose up -d   # MySQL 8.4 with healthcheck
DB_USERNAME=pickmeup DB_PASSWORD=pickmeup-local \
  ./gradlew bootRun --args='--spring.profiles.active=prod'
```

Or run the published container image:

```bash
docker pull ghcr.io/zakalren/pickmeup-reboot-api:latest
```

### Quick Verification

| URL | Purpose |
|---|---|
| `http://localhost:8080/swagger-ui/index.html` | Interactive API documentation |
| `http://localhost:8080/h2-console` | H2 database console (dev profile) |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3 spec (JSON) |

### Running Tests

```bash
./gradlew test
```

HTML report is generated at `build/reports/tests/test/index.html`.

## 📖 API Endpoints

Authentication is a session cookie (`JSESSIONID`) issued on login. Every
**Session** endpoint answers `401` without a valid session; **Admin** endpoints
additionally answer `403` for non-admin users. All errors share the unified
`ErrorResponse` shape — `{"code", "message", "fieldErrors"?}` — and request
validation failures return `400 VALIDATION_FAILED` with per-field messages.
Full request/response schemas are on Swagger UI (dev profile).

### Auth

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| POST | `/api/auth/login` | Public | 200 | 401 `INVALID_CREDENTIALS` · 429 `LOGIN_RATE_LIMITED` (per-IP, failed attempts only, with `Retry-After`) |
| POST | `/api/auth/logout` | Session | 204 | — |

### Users

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| POST | `/api/users/signup` | Public | 201 | 409 `DUPLICATE_USER` |
| GET | `/api/users/me` | Session | 200 | 404 `USER_NOT_FOUND` |

### Products

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| GET | `/api/products` | Public | 200 | — (paginated: `page`, `size` default 20, `sort` default `id`; stable `PagedModel` shape) |
| GET | `/api/products/{id}` | Public | 200 | 404 `PRODUCT_NOT_FOUND` |
| POST | `/api/products` | Admin | 201 | — |
| PUT | `/api/products/{id}` | Admin | 200 | 404 `PRODUCT_NOT_FOUND` |
| DELETE | `/api/products/{id}` | Admin | 204 | 404 `PRODUCT_NOT_FOUND` |

### Cart

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| GET | `/api/cart-items` | Session | 200 | — |
| POST | `/api/cart-items` | Session | 201 | 404 `PRODUCT_NOT_FOUND` · 409 `INSUFFICIENT_STOCK` · 409 `CART_ITEM_CONFLICT` (concurrent add) |
| PUT | `/api/cart-items/{id}` | Session | 200 | 404 `CART_ITEM_NOT_FOUND` · 409 `INSUFFICIENT_STOCK` · 409 `CART_ITEM_CONFLICT` (version conflict) |
| DELETE | `/api/cart-items/{id}` | Session | 204 | 404 `CART_ITEM_NOT_FOUND` |

### Orders

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| POST | `/api/orders` | Session | 201 | 400 `EMPTY_CART` · 409 `INSUFFICIENT_STOCK` (atomic stock check) · 409 `ORDER_CONFLICT` (cart modified concurrently) |
| GET | `/api/orders` | Session | 200 | — (paginated: `page`, `size` default 20, `sort` default `id,desc`; stable `PagedModel` shape) |
| GET | `/api/orders/{id}` | Session | 200 | 404 `ORDER_NOT_FOUND` (also for another user's order — no id enumeration) |
| POST | `/api/orders/{id}/cancel` | Session | 200 | 404 `ORDER_NOT_FOUND` (also for another user's order) · 409 `ORDER_ALREADY_CANCELLED` (idempotent-safe atomic restock) |

## ⚙️ CI/CD Pipeline

Every push and PR runs `.github/workflows/ci.yml`:

```
push / PR
   ├── test             Gradle build + full test pyramid (H2)
   ├── prod-boot-check  Boots prod profile against MySQL 8.4 service container:
   │                    Flyway migrations → Hibernate validate → HTTP smoke test
   ├── build-image      (main pushes only, after both pass)
   │                    Multi-stage Docker build on native amd64 + arm64 runners
   ├── merge-image      Stitches per-arch images into one multi-arch manifest → GHCR
   │                    tags: :latest + immutable :sha-<commit>
   └── deploy           SSH to the OCI arm64 instance → compose pull/up,
                        then smoke test through the nginx reverse proxy
```

Dependabot opens weekly PRs for workflow actions, Gradle dependencies (minor/patch grouped), and Docker base images — each gated by the same pipeline.

## 📊 Progress

### ✅ Completed

- User domain (entity, repository, service, controller, DTOs, exceptions)
- Product domain (full CRUD)
- Cart domain with JPA associations (`@ManyToOne` to User and Product,
  fetch join + Hibernate Statistics N+1 verification)
- Spring Security session-based authentication, hardened:
  session-fixation protection (session-id rotation on login),
  `SameSite=Lax` / `HttpOnly` session cookie
- Role-based authorization (`USER`/`ADMIN` — product management is admin-only)
- Per-domain exception handlers, properly scoped with `assignableTypes`
- Flyway schema migrations (V1 init, V2 user role) + Docker Compose for MySQL
- Multi-stage Dockerfile (Temurin 25, JRE-alpine non-root runtime, Boot layer extraction)
- CI/CD: GitHub Actions (test + prod-boot-check → GHCR publish), Dependabot
- Deployment (CD stage C): multi-arch images (native amd64/arm64 builds +
  manifest merge) auto-deployed over SSH to an OCI arm64 instance behind an
  nginx reverse proxy with TLS — realizing the same-origin topology decision
- Unified error response format: every error — domain, validation, framework
  (unreadable JSON, type mismatch), unhandled 500 — returns the same
  `ErrorResponse` shape via `ResponseEntityExceptionHandler`
- Cart concurrency handling: optimistic locking (`@Version`) for quantity
  updates, unique-index race on concurrent add surfaced as 409 Conflict
- Paginated product listing (`Pageable` + stable `PagedModel` JSON shape)
- Login brute-force protection: per-IP token bucket (Bucket4j) counting
  only failed attempts → 429 with `Retry-After`; real client IP restored
  behind the reverse proxy (`forward-headers-strategy: native`)
- Product stock: cart quantities validated against available inventory
  (encapsulated in the entity), exceeding stock returns 409 Conflict
- Order (checkout) domain: cart-to-order in one transaction with atomic
  conditional stock decrement (`stock = stock - ? WHERE stock >= ?`) —
  oversell-proof under concurrency, deadlock-avoiding decrement order,
  and name/price snapshots so order history survives catalog edits
- Order cancellation (`POST /api/orders/{id}/cancel`): state transition
  (`PLACED → CANCELLED`, history preserved) with atomic conditional restock
  (`stock = stock + ?`); re-cancelling returns 409 `ORDER_ALREADY_CANCELLED`,
  and the guarded UPDATE makes concurrent double-restock structurally impossible
- Paginated order listing: two-query pagination (paged order query + a single
  `IN` items query, grouped in the service) — a fixed 2-statements-per-page
  count verified via Hibernate Statistics, avoiding the fetch-join paging trap
- Test pyramid across all domains: unit + slice + integration
- Improvement backlog with reasoning: [`docs/IMPROVEMENTS.md`](docs/IMPROVEMENTS.md)

### 🚧 In Progress

- Working through the improvement backlog ([`docs/IMPROVEMENTS.md`](docs/IMPROVEMENTS.md))

### 📅 Planned

- 2026-07-20 review added 19 new backlog items to
  [`docs/IMPROVEMENTS.md`](docs/IMPROVEMENTS.md) (#22-#40) covering a
  cart-item FK delete bug, signup abuse protection, order listing
  consistency/indexing, and several code-quality gaps; recommended
  processing order is at the bottom of that doc

## 📚 Original Project

The original PickMeUp (Node.js + Express + EJS) won the **Encouragement Prize** at the **2021 Republic of Korea Air Force Hackathon**.

→ [github.com/Zakalren/PickMeUp](https://github.com/Zakalren/PickMeUp)
