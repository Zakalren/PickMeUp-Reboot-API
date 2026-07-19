# 개선 포인트 정리

> 2026-07-03 기준 코드베이스 전체 리뷰 결과.
> 심각도 순으로 정리. 각 항목에 근거와 트레이드오프를 함께 적음.

## 처리 현황 (2026-07-03)

- ✅ 완료: #1(+CartItemControllerTest), #2, #3, #4(SameSite=Lax), #5(+ProductControllerTest),
  #6(H2 콘솔 developmentOnly), #8, #10(dev CORS + prod 리버스 프록시 결정), #11, #12, #14, #16, #20
- ✅ #12 MySQL 실검증 완료 (2026-07-03): Flyway V1/V2 적용 → Hibernate validate 통과 →
  가입/로그인/장바구니/권한(403)/로그아웃 스모크 테스트 전부 정상.
  단, Boot 4는 Flyway 자동구성이 별도 모듈이라 `spring-boot-flyway` 의존성이 필요했음
  (flyway-core만으로는 자동구성이 동작하지 않아 validate가 빈 스키마에서 실패).
- ✅ #9, #18 완료 (2026-07-04): `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를
  상속 — 프레임워크 예외(파싱 실패, 타입 미스매치 등)와 미처리 Exception(500, 내부 메시지
  비노출)까지 전부 `ErrorResponse` 포맷으로 통일. `ErrorResponse`는 `@JsonInclude(NON_NULL)`
  + `of()` 정적 팩토리. 주의: advice 간에는 예외 구체성이 아니라 **순서**로 핸들러가
  결정되므로, 전역 catch-all이 도메인 예외를 삼키지 않도록 도메인 advice 4개에 `@Order(1)` 부여.
- ✅ #13, #15, #17, #19, Swagger prod 차단(#6 잔여), ProductService 단위 테스트,
  로그아웃 테스트 완료 (2026-07-05):
  - #13: `CartItem`에 `@Version`(+Flyway V3) — increase/update 경합은 커밋 시점
    `OptimisticLockingFailureException` → 409. add의 find-then-insert 레이스는
    unique 인덱스 위반(`DataIntegrityViolationException`)을 `CartItemConflictException`으로
    변환 → 409. 트랜잭션이 이미 rollback-only라 서버측 재시도는 불가 — 클라이언트가
    재시도하면 증가 경로를 탄다.
  - #15: `GET /api/products`가 `Pageable`(기본 size 20, id 정렬) + `PagedModel` 반환.
    `PageImpl` 직접 직렬화는 Boot가 비권장(포맷 불안정)이라 `PagedModel`로 감쌈.
  - #17: `SecurityContextRepository`를 빈으로 정의해 필터 체인(`.securityContext()`)과
    `AuthController`가 같은 인스턴스 공유.
  - 로그아웃 통합 테스트가 세션 무효화(재사용 시 401)까지 검증.
    `UserSignupIntegrationTest`의 `deleteAll()`/`@Transactional` 중복도 정리(롤백만 사용).
- ✅ #7, #21 완료 (2026-07-12):
  - #7: 로그인 실패를 클라이언트 IP당 토큰 버킷(Bucket4j, 5회/분)으로 제한 → 초과 시
    429 LOGIN_RATE_LIMITED + Retry-After. **실패(401)만 토큰을 소비** — 요청 전체를
    세면 정상 사용자가 잠기고, 계정 단위 잠금은 피해자 계정에 대한 DoS가 가능함.
    필터는 빈으로 만들지 않고 체인 안에서 생성(Filter 빈은 서블릿 컨테이너에도 자동
    등록되어 요청당 두 번 실행됨). prod는 nginx 뒤라 `forward-headers-strategy: native`로
    실제 클라이언트 IP를 복원. in-memory 버킷은 단일 인스턴스 전제 — 수평 확장 시
    bucket4j-redis 같은 공유 백엔드로 교체 필요.
    리뷰 반영 2건: (1) 경로 매칭을 원본 `getRequestURI()` 문자열 비교에서
    `PathPatternRequestMatcher`(디코딩된 경로)로 교체 — `%6C` 같은 인코딩 변형이
    필터만 우회하고 컨트롤러엔 정상 라우팅되는 우회로가 있었음(회귀 테스트 추가).
    (2) 확인 후 소비(estimate→consume)를 선소비 후 비실패 환불(tryConsume→addTokens)로
    교체 — 전자는 동시 버스트가 토큰 소비 전에 전부 통과해 한도가 버스트에는 무력했음.
  - #21: `Product.stock`(+Flyway V4, 기존 행은 0으로 backfill) + Request/Response 반영.
    재고 검증은 CartItem 엔티티에 캡슐화(결정 #4) — 담기/증가/수량변경 모두 누적
    수량 기준으로 검사, 초과 시 `InsufficientStockException` → 409 INSUFFICIENT_STOCK.
    재고 **차감**은 주문 도메인 없이는 의미가 없어 구현하지 않음(아래 백로그).
    재고는 예약되지 않음 — 관리자가 재고를 낮춰도 기존 장바구니 라인은 다음 수량
    변경 시점에야 재검증됨. stock은 응답 DTO로 공개(품절 표시가 필요한 storefront
    특성상 의도된 노출). 배포 주의: 기존 상품은 0으로 backfill되므로 관리자가
    재고를 설정하기 전까지 담기가 409로 거부됨.
- ❎ Testcontainers 기반 prod 스키마 검증: **도입하지 않기로 결정** (2026-07-12).
  CI prod-boot-check가 실제 MySQL 8.4 + Flyway + Hibernate validate + HTTP 스모크로
  동일 영역을 이미 커버함. 로컬에서 prod 스키마 재현이 필요해지는 시점에 재검토.
- ✅ 주문(checkout) 도메인 완료 (2026-07-19):
  - `order/` 패키지: `Order`/`OrderItem`, POST /api/orders(카트 전체 결제),
    GET /api/orders, GET /api/orders/{id}. 조회는 소유자 스코프
    (`findByIdAndUserId`) — 타인 주문도 404로 응답해 주문 id 열거를 막음.
  - 재고 차감은 조건부 원자 UPDATE(`stock = stock - ? WHERE stock >= ?`) —
    0행이면 409 INSUFFICIENT_STOCK + 전체 롤백. 동시 주문은 행 잠금으로
    직렬화되어 오버셀이 구조적으로 불가능. 상품별 차감은 **productId 오름차순**으로
    수행해 상품 집합이 겹치는 동시 주문 간 교차 데드락을 방지.
  - 벌크 UPDATE는 영속성 컨텍스트를 우회해 로딩된 Product의 stock이 stale해짐 —
    서비스 흐름을 "스냅샷 캡처 → 차감 → 카트 삭제 → 저장"으로 고정해 차감 이후
    상품 상태를 읽지 않음(에러 메시지의 최신 재고만 스칼라 쿼리로 조회).
  - `order_items`는 상품명/가격 **스냅샷**을 보존 — 카탈로그 수정·삭제와 무관하게
    주문 이력이 그대로 읽힘. product FK는 ON DELETE SET NULL로 상품 삭제를
    막지 않음. 주문은 불변(상태 없음) — 취소는 아래 백로그.
- ✅ 주문 취소 완료 (2026-07-19): `POST /api/orders/{id}/cancel` — DELETE가 아니라
  상태 전이. 주문 행은 삭제하지 않고 `PLACED → CANCELLED`로만 바꿔 이력이 계속
  읽힘(응답에 `status` 필드 추가). 각 라인 상품을 조건부 원자 UPDATE(`stock = stock + ?`,
  재고 차감의 거울상)로 재입고 — productId 오름차순으로 수행해 동시 주문과의 교차
  데드락을 방지. 상품이 이미 삭제된 라인(`OrderItem.product == null`, FK
  ON DELETE SET NULL)은 재입고 대상이 없으므로 건너뜀.
  **멱등성/충돌 처리**: 소유자 확인 + 상태 가드를 한 개의 조건부 UPDATE로 합침
  (`WHERE id=? AND user_id=? AND status='PLACED'`) — 재고 차감과 같은 트레이드오프로,
  행 잠금이 직렬화하므로 동시 이중 취소가 재고를 이중 재입고할 수 없음
  (SELECT-then-UPDATE의 TOCTOU 간극이 없음). 0행은 모호(이미취소/타인/없음)해
  서비스가 사전 `findByIdAndUserIdWithItems`로 존재·소유를 먼저 확인해 404와 409를
  구분. 이미 취소된 주문 재취소는 **409 ORDER_ALREADY_CANCELLED** — 조용한 204가
  아니라 기존 409 충돌 계열(INSUFFICIENT_STOCK, ORDER_CONFLICT)과 일관되게, 이중
  취소를 명시적으로 드러냄. 벌크 UPDATE는 영속성 컨텍스트를 우회하므로 응답의
  `status`는 재조회(1차 캐시가 stale) 대신 DTO에서 CANCELLED로 명시해 만듦.
- 📋 남은 항목:
  - 주문 목록 페이지네이션 — fetch join+페이징은 인메모리 페이징 함정이 있어
    two-query 방식(id 페이징 → items 로딩) 또는 @BatchSize로 설계 필요

---

## 🔴 Critical — 앱이 동작하지 않거나 즉시 수정 필요

### 1. `CartItemController`의 `${cartItemId}` 오타로 애플리케이션 기동 불가

`CartItemController.java:46, 57`

```java
@PutMapping("/${cartItemId}")     // ❌ 프로퍼티 플레이스홀더 문법
@DeleteMapping("/${cartItemId}")  // ❌
```

`{cartItemId}`(URI 템플릿 변수)가 아니라 `${cartItemId}`(프로퍼티 플레이스홀더)로 되어 있음.
Spring이 매핑 등록 시 `cartItemId`라는 프로퍼티를 찾다가 `PlaceholderResolutionException`을
던져 **컨텍스트 기동 자체가 실패**함.

**실측 결과** (`gradlew test`, 2026-07-03):

```
29 tests completed, 4 failed
- PickMeUpApplicationTests > contextLoads() FAILED
- UserSignupIntegrationTest 3건 FAILED
  Caused by: org.springframework.util.PlaceholderResolutionException
```

즉 현재 main 브랜치는 `bootRun`도 실패하는 상태. 서비스/리포지토리 슬라이스 테스트만
통과하고 있어서 눈에 안 띄었던 것.

**교훈**: 계획 중인 `CartItemControllerTest`(@WebMvcTest)가 하나라도 있었으면 커밋 시점에
잡혔을 버그. 컨트롤러 슬라이스 테스트의 우선순위를 올릴 근거가 됨.

### 2. prod MySQL 포트 오타

`application.yml:40`

```yaml
url: jdbc:mysql://localhost:3006/pickmeup?serverTimezone=Asia/Tokyo&...
```

- `3006` → `3306` 오타.
- `serverTimezone=Asia/Tokyo`도 의도인지 확인 필요 — 원본 프로젝트 도메인(ROKAF)은
  Asia/Seoul. 어느 쪽이든 **의도를 정하고 주석이나 커밋 메시지로 남길 것**.

---

## 🟠 Security — 세션 기반 인증을 선택했기 때문에 생기는 숙제들

세션 인증은 아키텍처 결정 #2로 채택했는데, JWT 대비 얻은 장점(즉시 로그아웃, 서버측 폐기)의
대가로 아래 항목들이 **필수**가 됨. 현재는 대가만 치르고 방어는 안 되어 있는 상태.

### 3. 로그인 시 세션 고정(Session Fixation) 방어 누락

`AuthController.java:36-53`

시큐리티 필터 체인의 form login을 끄고 컨트롤러에서 수동으로 인증하는 구조인데, 이렇게 하면
필터가 해주던 **세션 ID 회전(`changeSessionId`)이 실행되지 않음**. 로그인 전후로 같은
JSESSIONID가 유지되므로, 공격자가 미리 심어둔 세션 ID가 로그인 후에도 유효함.

```java
// saveContext 전에 추가
httpRequest.changeSessionId();
```

또는 `SessionAuthenticationStrategy`를 주입받아 호출. 수동 인증 방식을 유지할 거라면
필터가 해주던 일 중 무엇을 직접 해야 하는지 목록화해 둘 것.

### 4. CSRF 완전 비활성화

`SecurityConfig.java:27`

**JWT를 헤더로 보내는 구조라면 CSRF disable이 표준이지만, 세션 쿠키 인증에서는 아님.**
브라우저가 쿠키를 자동 전송하므로 악성 사이트에서 `POST /api/cart-items` 같은 상태 변경
요청을 위조할 수 있음.

선택지 (트레이드오프):

| 방안 | 장점 | 단점 |
|---|---|---|
| `CookieCsrfTokenRepository` + 프론트에서 헤더 전송 | 정석, 완전한 방어 | 프론트 연동 코드 필요 |
| 세션 쿠키 `SameSite=Strict/Lax` 설정만 | 설정 한 줄 (`server.servlet.session.cookie.same-site: lax`) | 구형 브라우저 미보호, Lax는 GET 내비게이션 허용 |

최소한 SameSite 설정은 지금 바로 넣을 것. 분리형 프론트가 **다른 도메인**에 올라간다면
SameSite=None + Secure가 필요해지면서 CSRF 토큰이 사실상 필수가 됨 → #10(CORS)과 함께 결정.

### 5. 권한(Role) 모델 부재 — 아무 사용자나 상품 생성/수정/삭제 가능

`SecurityConfig.java:38`, `CustomUserDetailsService.java:24`

모든 사용자가 `ROLE_USER` 고정이고, `POST/PUT/DELETE /api/products`는
`anyRequest().authenticated()`에 걸려 **로그인만 하면 누구나 상품을 삭제할 수 있음**.

- `User` 엔티티에 role 필드 추가 (enum: `USER`, `ADMIN`)
- `requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")` 등으로 제한

### 6. H2 콘솔·Swagger가 prod에서도 열림

`SecurityConfig.java:39-40`, `build.gradle.kts`

- `/h2-console/**`, `/swagger-ui/**` permitAll이 프로파일 구분 없이 적용됨.
- `spring-boot-h2console` 의존성이 `implementation`이라 **prod jar에도 포함**됨 →
  `developmentOnly`로 이동.
- Swagger는 prod에서 `springdoc.api-docs.enabled=false`로 끄거나 인증 뒤로 숨길 것.

### 7. 로그인 브루트포스 방어 부재 (백로그)

계정 잠금이나 rate limiting이 없음. 지금 단계에서 필수는 아니지만, 열거 공격 방어
(`AuthExceptionHandler`의 통합 401 응답)를 이미 해놓은 만큼 로드맵에는 올려둘 것.
Bucket4j 필터 또는 실패 카운트 기반 잠금.

---

## 🟡 설계 — 아키텍처 결정이 의도대로 동작하지 않는 부분

### 8. "도메인별 예외 핸들러"가 실제로는 전역으로 동작함

`CartExceptionHandler.java` 등 모든 핸들러

아키텍처 결정 #5(Per-Domain Exception Handlers)의 의도와 달리, `@RestControllerAdvice`는
**범위를 지정하지 않으면 모든 컨트롤러에 적용됨**. 패키지를 나눈 것은 파일 위치일 뿐
동작에는 영향이 없음.

특히 위험한 것: `CartExceptionHandler`의 `IllegalArgumentException` → 400 매핑이
**전 도메인의 IllegalArgumentException을 흡수**함. 다른 도메인에서 프로그래밍 버그로
IllegalArgumentException이 나도 500이 아닌 400 "INVALID_REQUEST"로 응답되어 버그가 가려짐.

```java
@RestControllerAdvice(assignableTypes = CartItemController.class)
```

각 핸들러에 `assignableTypes` 또는 `basePackageClasses`를 지정해야 결정 #5가 실제로 구현됨.
(대안: IllegalArgumentException을 잡지 말고 도메인 예외 —`InvalidQuantityException` 등—를
정의하는 것이 더 깔끔함. 엔티티가 던지는 예외 타입도 함께 바꿔야 함.)

### 9. 에러 응답 포맷 불일치

`GlobalExceptionHandler.java`

`MethodArgumentNotValidException`만 처리하고 있어서 그 외 흔한 예외들은 Boot 기본
whitelabel JSON으로 떨어짐 → 클라이언트가 두 가지 에러 포맷을 처리해야 함:

- `HttpMessageNotReadableException` (JSON 파싱 실패) → 기본 400
- `MethodArgumentTypeMismatchException` (`/api/products/abc`) → 기본 400
- 그 외 `Exception` → 기본 500 (내부 정보 노출 가능성)

`ResponseEntityExceptionHandler`를 상속하거나 위 세 가지 핸들러를 추가해서
모든 에러가 `ErrorResponse` 포맷으로 나가게 통일할 것.

### 10. CORS 설정 부재 — 결정 #1(분리형 프론트)과 충돌

아키텍처 결정 #1이 REST API + 분리 프론트인데 CORS 설정이 전혀 없음. 프론트가 다른
origin에서 뜨는 순간 전부 막힘. 게다가 세션 쿠키 인증이라:

- `allowCredentials(true)` + 명시적 origin 필요 (와일드카드 불가)
- cross-site면 쿠키에 `SameSite=None; Secure` 필요 → #4의 CSRF 방어가 필수로 승격

**권장**: 리버스 프록시(nginx)로 같은 origin에 프론트/API를 묶는 구성을 기본으로 하고
(CORS·SameSite 문제가 모두 사라짐), CORS는 로컬 개발용 프로파일에만 여는 것.
어느 쪽이든 결정을 내리고 문서화할 것 — 세션 인증 선택(결정 #2)의 정합성이 여기 달려 있음.

### 11. OSIV(Open Session In View)가 기본값(true)으로 켜져 있음

`application.yml`

N+1을 Hibernate Statistics로 검증까지 하는 프로젝트인데 OSIV가 켜져 있으면:

- 직렬화 시점까지 영속성 컨텍스트가 살아 있어 **lazy loading 누락이 500이 아니라
  조용한 N+1로 나타남** → fetch join 빠뜨린 걸 개발 중에 못 알아챔
- 요청 내내 DB 커넥션 점유

```yaml
spring:
  jpa:
    open-in-view: false
```

끄면 `LazyInitializationException`이 즉시 터져서 fetch 전략 누락을 강제로 드러내 줌.
이 프로젝트의 학습 목표(N+1 검증)와 정확히 맞는 설정.

### 12. 스키마 마이그레이션 도구 부재

prod는 `ddl-auto: validate`인데 **스키마를 만들 수단이 없음**. 첫 배포에서 validate가
바로 실패함. Docker Compose(계획됨)와 함께 Flyway 도입 권장:

- `V1__init.sql`에 현재 스키마 덤프
- 이후 엔티티 변경은 마이그레이션 파일로 — "커밋 히스토리로 설계 결정 문서화"라는
  프로젝트 목표와도 잘 맞음

### 13. 장바구니 동시성 문제 두 가지

`CartItemService.java:38-55`, `CartItem.java`

1. **add의 find-then-save 레이스**: 같은 상품을 동시에 두 번 담으면 둘 다
   `findByUserIdAndProductId`에서 빈 결과를 받고 insert 시도 → unique index
   (`idx_cart_items_user_product`) 위반 → `DataIntegrityViolationException` → **500**.
   인덱스를 잘 걸어둔 덕에 데이터는 안 깨지지만, 예외 처리가 없어 사용자는 500을 봄.
   → catch 후 재시도(증가 처리)하거나 최소한 409로 변환.
2. **increaseQuantity의 lost update**: 조회 후 메모리에서 더하는 방식이라 동시 요청 시
   한쪽 증가분이 유실됨. `@Version`(낙관적 락) 추가가 가장 저렴한 해결책.

장바구니라 실해가 크진 않지만, "현대적 패턴 연습"이 목표라면 낙관적 락 + 재시도는
좋은 연습 소재.

### 14. `User.rank` — MySQL 8 예약어

`User.java:38-39`

`RANK`는 MySQL 8.0.2부터 예약어(윈도우 함수). H2 dev에서는 통과해도 **MySQL prod에서
DDL/쿼리가 깨짐**. dev와 prod의 DB가 달라서 생기는 전형적인 늦은 발견 케이스.

```java
@Column(name = "military_rank", length = 20)
private String rank;
```

Flyway 도입(#12) 전에 고쳐야 마이그레이션 히스토리가 깔끔함.

### 15. 목록 API 페이지네이션 부재

`GET /api/products`가 무제한 `findAll()`. 데이터가 늘면 그대로 응답 크기가 폭발.
`Pageable` 받아서 `Page<ProductResponse>` 반환으로 변경. 장바구니는 사용자당 건수가
제한적이라 그대로 둬도 무방.

---

## 🟢 코드 품질 — 동작은 하지만 개선하면 좋은 것들

### 16. 중복 인덱스

`CartItem.java:16-18` — `idx_cart_items_user(user_id)`는
`idx_cart_items_user_product(user_id, product_id)`의 왼쪽 접두사라서 불필요.
유니크 복합 인덱스가 user_id 단독 조회도 커버함. 단독 인덱스 제거 가능.

### 17. `AuthController`가 `SecurityContextRepository`를 직접 `new`

`AuthController.java:29` — 필터 체인이 쓰는 리포지토리와 별개 인스턴스.
`HttpSessionSecurityContextRepository`는 무상태라 현재는 우연히 동작하지만,
설정을 바꾸면(예: 커스텀 리포지토리) 둘이 어긋남. `SecurityConfig`에서 빈으로 정의해
필터 체인(`.securityContext(...)`)과 컨트롤러가 **같은 인스턴스를 공유**하게 할 것.

### 18. `ErrorResponse.fieldErrors`가 대부분 `"fieldErrors": null`로 직렬화됨

`ErrorResponse.java` — 검증 오류 외의 모든 에러 응답에 null 필드가 노출됨.
`@JsonInclude(JsonInclude.Include.NON_NULL)` 한 줄이면 해결. 덤으로
`ErrorResponse.of(code, message)` 정적 팩토리를 만들면 `null` 인자 전달이 사라짐.

### 19. 사용하지 않는 리포지토리 메서드

`CartItemRepository.java` — `existsByUserIdAndProductId`, `deleteByUserIdAndProductId`는
프로덕션 코드 어디서도 안 씀. 특히 derived delete는 `@Modifying` 없이 쓰면 select 후
건별 delete라 함정이 있음. 쓸 계획이 없으면 제거 (YAGNI).

### 20. prod DB 자격증명의 빈 문자열 기본값

`application.yml:42-43` — `${DB_USERNAME:}` 처럼 기본값이 빈 문자열이면 env 누락 시
기동은 되고 **DB 연결에서야 알 수 없는 인증 오류**로 실패함. 기본값을 제거해서
(`${DB_USERNAME}`) 프로퍼티 해석 단계에서 fail-fast 하게 할 것.

### 21. 재고(stock) 개념 부재

`Product`에 재고가 없어서 장바구니 수량에 사실상 상한이 없음(Integer 오버플로는
`validateQuantity`가 우연히 막아 줌). 도메인 확장 시 재고 차감·동시성(#13)과 묶어서
설계하면 좋은 학습 소재.

---

## 🧪 테스트 공백

| 대상 | 현황 | 비고 |
|---|---|---|
| `CartItemControllerTest` | 없음 (계획됨) | **#1 버그를 잡을 수 있었던 테스트. 최우선** |
| `ProductService/Controller` 테스트 | 없음 | CRUD 전체가 무테스트 |
| `AuthController` 로그아웃 테스트 | 없음 | 세션 무효화 검증 |
| prod 프로파일 기동 검증 | 없음 | #2, #14가 잡히지 않은 이유. Docker Compose 도입 시 Testcontainers-MySQL로 `contextLoads` 하나만 있어도 됨 |

기타: `UserSignupIntegrationTest`는 `@Transactional` 롤백을 쓰면서 `deleteAll()`도 호출 —
중복이므로 하나만 남기기.

---

## 권장 처리 순서

1. **#1 오타 수정** (앱이 안 뜸) + `CartItemControllerTest` 작성으로 재발 방지
2. **#3, #4** 세션 고정 + SameSite (보안, 각각 몇 줄)
3. **#8** advice 스코핑 (조용히 버그를 가리는 중)
4. **#11** OSIV off (지금 진행 중인 N+1 검증 작업의 전제)
5. **#2, #14, #20** prod 설정 정리 → **#12** Flyway (Docker Compose 계획과 함께)
6. **#5** Role 모델, **#10** CORS/배포 토폴로지 결정
7. 나머지는 리팩토링 기회에 순차 처리
