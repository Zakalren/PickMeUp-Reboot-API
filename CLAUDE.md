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
- Spring Boot 4.1.0
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

- All changes land via branch → PR → CI → review → merge, like a team
  project (no direct push to main). Full process: /pr-workflow skill
  (`.claude/skills/pr-workflow/SKILL.md`).
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
├── cart/
├── order/
├── auth/
├── config/
└── common/

## Current Progress

Where progress is tracked (don't duplicate between these):
- **README.md** — public-facing summary (completed / in progress / planned)
- **GitHub Issues** — improvement backlog going forward. File new ideas as
  issues instead of appending to IMPROVEMENTS.md; when one lands, close it
  and update README's Progress section. Reasoning goes in the issue body/
  comments, not mirrored here.
- **docs/IMPROVEMENTS.md** — frozen historical record of the initial
  2026-07-03 codebase review (처리 현황 section) and how each item was
  resolved. No new entries.

Quick orientation: all four domains (user, product, cart, auth) are
implemented with the full test pyramid, session auth + role-based
authorization, Flyway migrations, unified ErrorResponse error format.
CI/CD runs test + prod-boot-check (MySQL 8.4) → multi-arch image on GHCR
→ SSH deploy to an OCI arm64 instance.

Environment facts not recorded in the repo:
- Deploy keypair: ~/.ssh/pickmeup-deploy.key (WSL side)
- Server: /opt/pickmeup, app on 127.0.0.1:8090 behind nginx
  (pickmeup.zakalren.dev, wildcard TLS), MySQL container without
  published ports; nginx config lives on the server only

## Communication Preferences

- Respond in Korean (한국어로 답변) unless specifically asked otherwise
- Be direct and honest. If something I suggest is wrong, push back with
  clear reasoning.
- Don't sugarcoat. Point out things I might be missing.
- Help me think through trade-offs rather than just giving answers.
