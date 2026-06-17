# PickMeUp Reboot API

> A Spring Boot REST API rebuilt from a **2021 ROK Air Force Hackathon award-winning project**, migrating a Node.js + Express + EJS monolith to a modern Java backend with intentional architectural decisions.

![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?logo=springboot)
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
| **Framework** | Spring Boot 4.0.6, Spring Security 6 |
| **Persistence** | Spring Data JPA, Hibernate, H2 (dev) / MySQL (prod) |
| **Build** | Gradle (Kotlin DSL) |
| **API Docs** | springdoc-openapi 3 (Swagger UI) |
| **Testing** | JUnit 5, Mockito, AssertJ, Spring Security Test |
| **Auth** | Session-based + BCrypt (see decision below) |

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
├── cart/             (in progress)
├── auth/
│   ├── AuthController.java
│   ├── dto/
│   └── exception/
├── config/
│   └── SecurityConfig.java
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

Instead of a single `GlobalExceptionHandler` accumulating handlers from every domain, each domain has its own `@RestControllerAdvice`:

- `UserExceptionHandler` — `UserNotFoundException`, `DuplicateUserException`
- `ProductExceptionHandler` — `ProductNotFoundException`
- `AuthExceptionHandler` — authentication-related exceptions
- `GlobalExceptionHandler` (in `common/`) — only cross-cutting concerns (validation, unhandled exceptions)

The shared `ErrorResponse` record stays in `common`. This keeps each domain self-contained.

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
| **Unit (Mockito)** | `UserServiceTest`, `CartItemServiceTest` — verifies business logic in isolation |
| **Slice — Repository** | `UserRepositoryTest` (`@DataJpaTest`) — verifies JPA query generation against real H2 |
| **Slice — Controller** | `UserControllerTest`, `AuthControllerTest` (`@WebMvcTest`) — verifies HTTP layer + Spring Security |
| **Integration** | `UserSignupIntegrationTest` (`@SpringBootTest`) — verifies signup → login → authenticated request flow |

## 🚀 Running Locally

```bash
git clone https://github.com/Zakalren/PickMeUp-Reboot-API.git
cd PickMeUp-Reboot-API
./gradlew bootRun
```

The application starts on `http://localhost:8080`.

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

## 📊 Progress

### ✅ Completed

- User domain (entity, repository, service, controller, DTOs, exceptions)
- Product domain (full CRUD)
- Spring Security session-based authentication
- BCrypt password hashing
- Per-domain exception handlers
- Global validation handler
- Swagger UI integration
- Test pyramid: unit + slice + integration tests for User/Auth flows

### 🚧 In Progress

- **Cart domain with JPA associations** (`@ManyToOne` to User and Product)
  - Demonstrating `fetch join` to prevent N+1 problems
- Cart domain test coverage

### 📅 Planned

- README extension with API endpoint reference
- Docker Compose setup for MySQL prod profile
- CI pipeline (GitHub Actions): build + test on PR

## 📚 Original Project

The original PickMeUp (Node.js + Express + EJS) won the **Encouragement Prize** at the **2021 Republic of Korea Air Force Hackathon**.

→ [github.com/Zakalren/PickMeUp](https://github.com/Zakalren/PickMeUp)
