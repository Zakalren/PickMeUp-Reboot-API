# PickMeUp Reboot API — Project Context

## Project Goals

This is a Spring Boot REST API rebuilt from a 2021 ROK Air Force Hackathon
award-winning project. The original was Express.js + MongoDB + EJS monolith.

Goals:
1. Migrate to modern JVM stack for Japanese backend roles
2. Practice modern architectural patterns
3. Document architectural decisions through commit history

## Tech Stack

- Java 25 LTS
- Spring Boot 4.0.6
- Spring Security 6
- Spring Data JPA + Hibernate
- H2 (dev) / MySQL (prod)
- Gradle (Kotlin DSL)
- JUnit 5, Mockito, AssertJ
- springdoc-openapi 3 (Swagger UI)

## Architectural Decisions (Important!)

1. **REST API + Separated Frontend** (vs original monolithic EJS)
2. **Session-based Auth over JWT** — chosen after trade-off evaluation for
   single-backend domain (immediate logout, server-side revocation,
   simpler attack surface)
3. **Domain-driven Package Structure** — by business domain (user, product,
   cart, auth), not by technical layer
4. **Encapsulated Entity Design** — no public setters, static factory
   methods, @NoArgsConstructor(access = PROTECTED)
5. **Per-Domain Exception Handlers** — each domain has its own
   @RestControllerAdvice

## Coding Conventions

- Commit messages: plain verb-first (e.g., "Implement CartItemService#add
  unit test", "Add session-based authentication"). No conventional commits
  prefix like "feat:" or "fix:".
- Tests follow pyramid: unit (Mockito) + slice (@WebMvcTest, @DataJpaTest)
    + integration (@SpringBootTest)
- Use `.with(user(...))` over `@WithMockUser` for slice tests (more
  reliable in Spring Boot 4)
- Use `@MockitoBean` (not deprecated `@MockBean`)
- Korean comments OK in test files, English in production code

## Package Structure

dev.zakalren.pickmeup
├── user/
├── product/
├── cart/             (in progress)
├── auth/
├── config/
└── common/

## Current Progress

✅ Completed:
- User domain (entity, repository, service, controller, DTOs, exceptions)
- Product domain (full CRUD)
- Spring Security session-based authentication
  (+ session fixation protection, SameSite=Lax cookie)
- Per-domain exception handlers (scoped via assignableTypes)
- Role-based authorization (UserRole USER/ADMIN; product writes are
  admin-only)
- Test pyramid for User/Auth flows
- CartItem entity, repository, service, controller, DTOs
- CartItemServiceTest, CartItemRepositoryTest (N+1 verification),
  CartItemControllerTest, ProductControllerTest
- Flyway migrations (V1 init, V2 user role) + Docker Compose for MySQL
  — verified against MySQL 8.4 (validate passes, full auth/cart smoke test)
  — note: Boot 4 needs the spring-boot-flyway module, flyway-core alone
    does not auto-configure
- Dev-profile CORS; prod is same-origin behind a reverse proxy

🚧 In Progress:
- Improvement backlog: see docs/IMPROVEMENTS.md (처리 현황 section)

✅ CI (GitHub Actions, .github/workflows/ci.yml):
- test job: gradle build on H2
- prod-boot-check job: boots prod profile against MySQL 8.4 service
  container (Flyway + validate + HTTP smoke)

✅ CD (stage A):
- Multi-stage Dockerfile (Temurin 25 JDK build / JRE-alpine non-root
  runtime, Boot layer extraction), verified against compose MySQL
  — same JDK distribution as CI runners (setup-java: temurin)
- CI build-image job publishes to GHCR (:latest + :sha-<commit>) after
  both verification jobs pass, main pushes only

📅 Planned:
- CD stage B/C: pick a deploy target (PaaS or VM) and automate deploy
- Error response format unification (extend ResponseEntityExceptionHandler)
- Cart concurrency handling (unique-violation on add, optimistic locking)

## Communication Preferences

- Respond in Korean (한국어로 답변) unless specifically asked otherwise
- Be direct and honest. If something I suggest is wrong, push back with
  clear reasoning.
- Don't sugarcoat. Point out things I might be missing.
- Help me think through trade-offs rather than just giving answers.