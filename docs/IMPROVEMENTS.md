# 개선 포인트 정리

> 2026-07-03 기준 코드베이스 전체 리뷰 결과. 2026-07-20 주문 도메인(체크아웃/취소/페이지네이션)
> 완성 이후 2차 리뷰로 #22~#40 추가.
> 심각도 순으로 정리. 각 항목에 근거와 트레이드오프를 함께 적음.

> **📌 이 문서는 2026-07-20부로 동결(archived)됨.** 아래는 최초 리뷰(#1~#21)의
> 기록이며 전부 처리 완료. **새 개선 아이디어는 여기 추가하지 않고
> [GitHub Issues](https://github.com/Zakalren/PickMeUp-Reboot-API/issues)로
> 등록**할 것 — 근거/트레이드오프도 이슈 본문에 적는다.

## 처리 현황 (2026-07-03)

- ✅ #22 완료 (2026-07-20): 상품 삭제 FK 충돌 처리. `ProductService.delete()`가
  `deleteById` 직후 명시적으로 `flush()`해 FK 위반을 메서드 안에서(트랜잭션
  커밋 시점이 아니라) 즉시 드러낸 뒤 `DataIntegrityViolationException` →
  `ProductInUseException` → 409 `PRODUCT_IN_USE`로 변환. 실제 H2 FK 경로로
  검증하는 통합 테스트(`ProductDeleteIntegrationTest`)를 추가하는 과정에서,
  시딩한 `CartItem`을 같은 트랜잭션의 영속성 컨텍스트에 남겨두면 실제 DB FK
  위반보다 먼저 Hibernate 자체의 flush-time 참조 무결성 사전 검사가
  `TransientPropertyValueException`(다른 예외 타입이라 catch를 안 탐)을
  던진다는 걸 발견 — `open-in-view: false`인 실제 운영 흐름에선 재현되지
  않는 테스트 전용 함정이라 `em.flush(); em.clear();`로 우회.
- ✅ #23 완료 (2026-07-20): 회원가입 rate limiting. `SignupRateLimitFilter`
  (Bucket4j, IP당 10회/10분, 성공·실패 모두 소비)를 `LoginRateLimitFilter`와
  같은 방식(빈 아님, addFilterBefore)으로 등록. 로그인과 달리 실패만이 아니라
  매 요청이 토큰을 소비하는 이유: 회원가입은 정당한 사용자에게 재시도 급증
  시나리오가 없고, 성공·`DUPLICATE_USER` 실패 모두 군번 존재 여부를 흘리기
  때문. 구현 중 값을 20으로 올려 테스트를 통과시키려던 시도가 있었으나 기각
  — 원인은 `UserSignupIntegrationTest`/`OrderCheckoutIntegrationTest`/
  `OrderCancelIntegrationTest`가 같은 캐시된 `@SpringBootTest` 컨텍스트를
  공유해 회원가입 호출이 우연히 한 IP로 몰린 테스트 아티팩트였음. 실제 보안
  임계값을 낮추는 대신 세 통합 테스트에 `LoginRateLimitTest`와 같은 클래스
  전용 IP(`10.0.2.x`)를 부여해 근본 원인을 고쳤고, 한도는 10/10분 그대로 유지.

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
- ✅ 주문 목록 페이지네이션 완료 (2026-07-19): `GET /api/orders`가 무제한
  JOIN FETCH에서 `Pageable`(기본 size 20, id DESC) + `PagedModel`로 전환 —
  `GET /api/products`와 동일한 형태. fetch join + 페이징은 Hibernate가 인메모리
  페이징(HHH000104)으로 처리하는 함정이 있어, **two-query** 방식 채택:
  (1) fetch join 없는 페이지 쿼리로 Order만 조회 → (2) 그 페이지의 order id들로
  `OrderItem`을 `IN` 한 번에 조회 → 서비스에서 그룹핑. 페이지가 꽉 차지 않으면
  count 쿼리가 생략되어 **페이지당 정확히 2 statement**(페이지 크기와 무관)가
  되어 이 프로젝트의 Hibernate Statistics N+1 검증 스타일로 고정·단언 가능.
  `@BatchSize`는 실제로도 2쿼리지만 배치 크기 튜닝에 좌우되는 암묵적·설정 의존
  방식이라, "설계 결정을 커밋 히스토리로 문서화"라는 목표에는 명시적 두 번째
  리포지토리 호출이 더 맞음. 결과적으로 `OrderResponse.from(Order)`는 목록에서
  `order.getItems()`(여기선 lazy 유지)를 읽을 수 없어 외부 items를 받는 오버로드가
  생김. 단일 조회 `GET /api/orders/{id}`는 그대로 fetch join 단일 쿼리 유지.

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

### 22. 장바구니에 담긴 상품을 삭제하면 500 에러 (2026-07-20 발견)

`db/migration/V1__init.sql:43`, `ProductService.java:58-64`, `ProductExceptionHandler.java`

`cart_items.product_id` FK(`fk_cart_items_product`)에 `ON DELETE` 절이 없어 MySQL이
기본값 RESTRICT로 삭제를 거부한다. `order_items` FK는 이미 `ON DELETE SET NULL` +
name/price 스냅샷으로 이 문제를 해결했는데(V5 마이그레이션), cart 쪽은 order 도메인이
생기기 전 설계라 같은 처리가 빠져 있다.

**재현 시나리오**: 사용자 A가 상품 #7을 장바구니에 담아둔 채 체크아웃하지 않음 →
관리자가 `DELETE /api/products/7` 호출 → MySQL이 FK 제약 위반으로 거부 →
`DataIntegrityViolationException`이 어디서도 잡히지 않고 `GlobalExceptionHandler`의
catch-all까지 흘러가 `500 INTERNAL_SERVER_ERROR`로 응답. 관리자는 왜 삭제가 안 되는지
알 방법이 없고, 장바구니에 한 번이라도 담긴 상품은 사실상 삭제 불가능해진다.

**해결 방향**: `ProductExceptionHandler`에 `DataIntegrityViolationException` →
409(예: `PRODUCT_IN_USE`) 핸들러 추가. 또는 order와 동일하게 FK를
`ON DELETE SET NULL`로 바꾸고 `CartItem`에도 스냅샷을 둘지 결정 필요 — 단, 장바구니는
주문과 달리 "이력 보존"이 목적이 아니므로 오히려 409로 막고 관리자에게 명시적으로
알리는 쪽이 더 맞을 수 있음(트레이드오프 판단 필요).

### 23. 회원가입에 rate limiting·계정 열거 방어 없음 (2026-07-20 발견)

`UserService.java:22-24`, `DuplicateUserException.java:29-32`, `SecurityConfig.java:60`

로그인(`/api/auth/login`)은 #7에서 Bucket4j 필터로 보호되는데, `/api/users/signup`은
`permitAll`이면서 아무 제한이 없다. `serviceNumber`(군번, 4~20자)가 예측 가능한
포맷을 따를 가능성이 높은 도메인 특성상, 응답이 `201`(성공)이냐 `409 DUPLICATE_USER`냐로
유효한 군번을 무제한으로 열거할 수 있다. 또한 로그인 경로가 막아둔 것과 동일한 종류의
공격(요청당 무제한 BCrypt 해싱 유발 → CPU 증폭)이 회원가입에는 그대로 뚫려 있다.

**해결 방향**: #7과 동일한 패턴(IP당 토큰 버킷)을 signup에도 적용하거나, 최소한 같은
필터를 두 경로에 공유하도록 확장. 열거 방어까지 하려면 성공/실패 응답 시간과 형태를
통일하는 것도 고려(다만 회원가입은 결과가 최종적으로 드러나는 UX 특성상 완전한 방어는
어려움 — 최소 rate limit만으로도 실질적 방어 효과는 큼).

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

> ✅ 위 #7은 2026-07-12에 로그인 경로만 처리 완료. 회원가입 경로는 #23으로 별도 추적.

### 24. `LoginRequest`에 크기 제한 없음 (2026-07-20 발견)

`auth/dto/LoginRequest.java:5-8`

다른 모든 입력 DTO(`UserSignupRequest`, cart DTO들)는 `@Size`/`@Min`으로 상한을 두는데
`LoginRequest`는 `@NotBlank`뿐이다. `application.yml`에 `server.tomcat.max-http-form-post-size`
같은 요청 크기 제한도 없어서, 유일하게 완전 비인증으로 항상 열려 있는 엔드포인트가
페이로드 크기 제한이 없는 상태다. Bucket4j는 실패 횟수만 세지 페이로드 크기는 보지
않으므로, 대용량 payload를 반복 전송하는 저비용 DoS 벡터가 남아 있음.

**해결 방향**: `LoginRequest` 필드에 `@Size(max = ...)` 추가(다른 DTO와 동일 패턴).

### 25. BCrypt 72바이트 truncation을 침묵 허용 (2026-07-20 발견)

`user/dto/UserSignupRequest.java:12`, `SecurityConfig.java:93-95`

`@Size(min = 8, max = 100)`으로 비밀번호를 100자까지 받지만, 기본 `BCryptPasswordEncoder`는
72바이트 이후를 조용히 버리고 해싱한다. 90자짜리 비밀번호를 설정한 사용자는 전체가
보호된다고 믿지만 실제로는 앞 72바이트만 유효하며, 72바이트 이후만 다른 두 비밀번호가
동일한 해시로 취급된다. 보안 취약점이라기보다 사용자 기대와 실제 동작의 불일치.

**해결 방향**: `@Size(max = 72)`로 상한을 맞추거나, truncation을 명시하는 주석/문서화.

### 26. CI 워크플로 일부 job에 `permissions:` 블록 없음 (2026-07-20 발견)

`.github/workflows/ci.yml` — `test`(16행), `prod-boot-check`(44행), `deploy`(172행) job에
`permissions:` 블록이 없어 저장소 기본 토큰 권한을 그대로 상속한다. `build-image`(116행),
`merge-image`(150행)는 이미 `contents: read`/`packages: write`로 최소 권한을 명시하고
있는데 세 job만 빠짐. 저장소의 기본 워크플로 권한이 read-write로 설정돼 있다면(구형
저장소의 과거 기본값) `GITHUB_TOKEN`이 필요 이상으로 넓은 권한을 갖게 됨 — 특히
`deploy`는 배포 비밀(`DEPLOY_SSH_KEY`)을 다루므로 영향이 크다.

**해결 방향**: 세 job에도 최소 권한(`permissions: contents: read` 등)을 명시.

### 27. SSH 배포 host key가 최초 접속 시 무조건 신뢰됨 (2026-07-20 발견)

`.github/workflows/ci.yml:189-191`

`StrictHostKeyChecking=accept-new`는 최초 접속(또는 호스트 키 교체 이후 첫 접속)에서
서버가 제시하는 키를 검증 없이 그대로 신뢰한다. `known_hosts`가 저장소에 pinning되어
있지 않음. CI 러너에서 배포 서버로의 경로가 탈취되는 경우(DNS 하이재킹 등) 중간자
공격에 노출될 수 있음. CI에서 흔한 트레이드오프이긴 하나, 실제 프로덕션 SSH 키가
오가는 채널이라 기록해 둘 가치가 있음.

**해결 방향**: 배포 서버의 host key를 리포지토리 시크릿이나 `known_hosts` 파일로
pinning(`StrictHostKeyChecking=yes` + 고정된 known_hosts).

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

> ✅ 완료 (2026-07-05, products) / 2026-07-19(orders). 아래 #28~#30은 그 이후 발견된
> 후속 이슈.

### 28. 주문 목록과 단건 조회의 아이템 정렬이 서로 다름 (2026-07-20 발견)

`order/Order.java:39`, `order/OrderItemRepository.java:11`, `order/OrderService.java:72-76`

`Order.items`에는 `@OrderBy("id ASC")`가 붙어 있어 `findByIdAndUserIdWithItems`(단건
조회, `GET /api/orders/{id}`)의 fetch join 결과는 항상 id 오름차순이다. 반면 목록
페이지네이션이 쓰는 `findByOrderIdIn(List<Long>)`에는 `ORDER BY`가 전혀 없고,
`OrderService.findMyOrders`가 이 결과를 `Collectors.groupingBy`로 묶기 때문에 각
주문의 items 순서는 DB가 우연히 반환하는 행 순서에 달려 있다(H2와 MySQL이 다를 수
있고, 같은 DB라도 보장되지 않음).

**재현 시나리오**: 아이템 [A, B, C] 순서로 삽입된 주문 하나. `GET /api/orders/{id}`는
항상 `[A, B, C]`. `GET /api/orders`(목록)는 같은 주문을 `[B, A, C]` 등 다른 순서로
보여줄 수 있음 — 같은 데이터인데 엔드포인트에 따라 결과가 달라지는 사용자 체감
버그.

**해결 방향**: `findByOrderIdIn` 쿼리에 `ORDER BY oi.order.id, oi.id` 명시.

### 29. `orders` 테이블에 목록 조회 패턴에 맞는 복합 인덱스 없음 (2026-07-20 발견)

`db/migration/V5__create_orders.sql`, `order/OrderController.java:38`

`orders`는 `user_id` 단일 컬럼 인덱스만 있는데, 실제 목록 조회는
`WHERE user_id = ? ORDER BY id DESC LIMIT`(기본 정렬 `id,desc`) 패턴이다. 단일
인덱스로는 필터링만 인덱스를 타고 정렬은 filesort(대량 시 임시 테이블)로 처리됨.
사용자 주문 이력이 늘어날수록 페이지네이션이 느려짐.

**해결 방향**: `(user_id, id)` 복합 인덱스로 교체하는 Flyway 마이그레이션 추가.
`CartItem`의 #16(중복 인덱스 제거)과 반대로, 여기는 인덱스를 추가해야 하는 케이스.

### 30. order/product 목록 API에 페이지 크기 상한이 없음 (2026-07-20 발견)

`order/OrderController.java:34-41`, `product/ProductController.java:22-31`,
`application.yml`

`@PageableDefault(size = 20)`만 있고 `spring.data.web.pageable.max-page-size` 설정이
없다. Spring Data Web의 내장 기본 상한(2000)에만 암묵적으로 의존하는 상태 — 의도된
방어가 아니라 프레임워크 기본값이 우연히 막아주는 것. `GET /api/orders?size=2000`
같은 요청이 가능하고, order 목록은 페이지당 최대 2000개 id로 `IN` 쿼리까지 추가로
발생함(두 번째 쿼리).

**해결 방향**: `application.yml`에 `spring.data.web.pageable.max-page-size`를
명시적으로 설정(예: 100).

### 31. 장바구니 "기존 라인 수량 증가" 경로에서 회피 가능한 N+1 (2026-07-20 발견)

`cart/CartItemRepository.java:17`, `cart/CartItemService.java:46-50`,
`cart/CartItem.java:72-77`

`findByUserIdAndProductId`는 `JOIN FETCH`가 없는데(read 경로가 쓰는
`findByUserIdWithProduct`는 있음), `add()`의 "이미 있는 상품" 분기가 이걸로 조회한
뒤 `increaseQuantity()` → `product.validateStockAvailable()`에서 lazy `Product`
프록시의 `stock`을 참조해 추가 SELECT가 한 번 더 나간다. 트랜잭션 안이라 예외는
안 나지만(500 아님), 장바구니에서 가장 흔한 조작(기존 상품 수량 추가)마다 회피
가능한 쿼리가 하나씩 더 나가는 셈.

**해결 방향**: `findByUserIdAndProductId`에도 `JOIN FETCH product` 추가.

### 32. `incrementStock`이 `decrementStock`과 달리 상한 가드가 없음 (2026-07-20 발견, 이론적)

`product/ProductRepository.java:30-32`, `order/OrderService.java:106-108`

`decrementStock`은 `WHERE stock >= :quantity`로 하한을 지키는데, 취소 시 재입고에
쓰이는 `incrementStock`(`stock = stock + :quantity`)에는 대응하는 상한 가드가 없다.
관리자가 `stock`을 `Integer.MAX_VALUE` 근처로 설정하고(#40 참고, 현재 상한 검증
없음) 주문 취소가 충분히 누적되면 MySQL strict mode에서 정수 범위 초과로 UPDATE가
실패할 수 있고, 이는 `OrderService.cancel()` 어디서도 잡히지 않아 취소 도중 500이
날 수 있다. 현실적인 재고 수치로는 거의 도달 불가능하지만, 대칭적으로 가드를
맞춰두면 방어적으로 깔끔함.

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

> ✅ 완료 (2026-07-12). 아래 #33~#40은 그 이후 발견된 코드 품질 항목.

### 33. `ProductRepository`에 호출되지 않는 메서드 두 개 (2026-07-20 발견)

`product/ProductRepository.java:13,15`

`findByCategory(String)`, `existsByName(String)` 모두 `src/main`, `src/test` 어디서도
호출되지 않음(#19에서 정리했던 `existsByUserIdAndProductId`와 동일한 패턴). `Product`
생성 시 이름 중복을 막지 않고 카테고리 필터링 기능도 없으므로, 이 메서드들은 흔적만
남은 죽은 코드이거나 실제로 필요한 기능이 구현 안 된 것 중 하나. `User` 도메인은
`DuplicateUserException`으로 이름 중복 방지 선례가 있어, "삭제" 또는 "실제로 이름
중복 검증 붙이기" 둘 중 하나를 정할 것.

**해결 방향**: 계획 없으면 YAGNI로 제거. 필요하면 `ProductService.create()`에서
`existsByName` 활용해 409 처리 추가.

### 34. `ProductRepositoryTest` 부재 — 관련 테스트가 엉뚱한 도메인에 위치 (2026-07-20 발견)

`src/test/java/dev/zakalren/pickmeup/product/`, `order/OrderRepositoryTest.java`

`cart`, `order`, `user` 도메인은 모두 자체 `@DataJpaTest`가 있는데 `product`만 없다.
`Product`의 커스텀 쿼리(`decrementStock`, `incrementStock`, `findStockById`)는
`OrderRepositoryTest`의 `DecrementStock`/`IncrementStock` nested class로만
간접 테스트되고 있어, `product` 도메인만 보는 사람은 이 테스트의 존재를 발견하기
어렵다. #33의 죽은 메서드도 자체 `ProductRepositoryTest`가 있었다면 자연스럽게
드러났을 가능성이 큼.

**해결 방향**: `ProductRepositoryTest`를 신설하고, stock 관련 쿼리 테스트를
`order` 쪽에서 이관(또는 두 곳에서 각자 관점으로 검증 — order는 원자성/동시성
관점, product는 쿼리 자체의 정확성 관점).

### 35. 코드 커버리지 도구 미설정 (2026-07-20 발견)

`build.gradle.kts:1-53`

Jacoco 등 커버리지 플러그인이 없다. "테스트 피라미드를 통한 엔지니어링 역량 증명"이
프로젝트 목표(CLAUDE.md)인 만큼, 커버리지 리포트/게이트가 없는 건 포트폴리오
관점에서 아쉬운 공백. 있었다면 #33 같은 죽은 코드도 자동으로 드러났을 것.

**해결 방향**: Jacoco 플러그인 추가 + `build/reports/jacoco`를 README에 노출하거나
CI에 리포트 업로드 단계 추가(머지 게이트로 강제할지는 별도 판단 — 과도한 커버리지
강제는 테스트 품질보다 숫자를 좇게 만들 위험도 있음).

### 36. `Product.java`의 오래된 주석 (2026-07-20 발견)

`product/Product.java:36`

```java
// Available inventory. Cart quantities may not exceed it (enforced by
// CartItem); actual decrement is deferred until an order domain exists.
```

order 도메인이 이미 완성되어 실제로 차감이 구현된 지금은 마지막 문장("actual
decrement is deferred until an order domain exists")이 사실과 다름. 소소하지만
읽는 사람을 혼란시킬 수 있는 stale 주석.

**해결 방향**: 주석 갱신 또는 삭제.

### 37. Validation 메시지 언어가 DTO마다 혼재 (2026-07-20 발견)

`product/dto/ProductRequest.java`, `user/dto/UserSignupRequest.java`,
`cart/dto/AddCartItemRequest.java`, `cart/dto/UpdateCartItemRequest.java`

`ProductRequest`, `UserSignupRequest`의 일부 필드(`password`, `telNumber`)는 한글
`message`를 명시하는데, cart DTO들은 영어 메시지를 쓰고, `UserSignupRequest`의
`serviceNumber`/`name`/`affiliatedUnit`/`rank`는 아예 override 없이 Jakarta 기본
영어 메시지가 나간다. CLAUDE.md의 "Korean 주석은 테스트에만" 규칙은 코드 주석
얘기지만, 클라이언트가 실제로 받는 validation 에러 메시지 언어가 엔드포인트마다
달라지는 건 별개로 정리가 필요한 일관성 문제.

**해결 방향**: 메시지 언어를 하나로 통일(권장: 한글 — 프로젝트가 한국어 커뮤니케이션
선호이고 원본이 ROKAF 도메인이므로)하고 전 DTO에 일괄 적용.

### 38. Swagger 애노테이션이 전 컨트롤러에 전무 (2026-07-20 발견, order만의 문제 아님)

`OrderController`, `ProductController`, `CartItemController`, `UserController`,
`AuthController` 전부

`springdoc-openapi-starter-webmvc-ui`가 의존성에 있는데(`build.gradle.kts:26`)
`@Operation`/`@ApiResponse` 등 springdoc 애노테이션이 어느 컨트롤러에도 없다.
springdoc의 자동 추론(메서드 시그니처/DTO 기반)에만 의존 중 — Swagger UI는 뜨지만
에러 코드별 설명, 예제 응답 등은 README의 API 표로만 확인 가능하고 Swagger 자체에는
없음.

**해결 방향**: 최소한 각 엔드포인트에 `@Operation(summary=...)` +
`@ApiResponse(responseCode, description)` 매핑을 README API 표와 맞춰 추가. 전
컨트롤러 동시 작업이라 범위가 크므로 별도 PR로 분리 권장.

### 39. `CartItemRepository.findByUserId` 죽은 코드 (2026-07-20 발견)

`cart/CartItemRepository.java:12`

product join이 없는 `findByUserId(Long)`가 선언만 되어 있고 어디서도 호출되지
않음(`findByUserIdWithProduct`만 실제로 쓰임). 지금 당장 버그는 아니지만,
`open-in-view: false`(#11) 상태에서 나중에 누군가 이 메서드를 트랜잭션 밖에서
`product`에 접근할 목적으로 쓰면 바로 `LazyInitializationException`을 만나는
함정 코드.

**해결 방향**: #19와 같은 판단 — 계획 없으면 제거.

### 40. quantity/stock/price에 상한 검증(`@Max`) 없음 (2026-07-20 발견)

`cart/dto/AddCartItemRequest.java:10-12`, `UpdateCartItemRequest.java:7-9`,
`product/dto/ProductRequest.java:16-26`

`@Min`만 있고 `@Max`가 없다. 현재는 `Order.place`가 곱셈 전에 `long`으로 캐스팅하고
(`Order.java:65`), `CartItem.validateQuantity`가 int 오버플로 시 음수로 wrap되는
성질을 우연히 이용해 막고 있어서(`CartItem.java:84-88`) 당장 뚫리는 취약점은
아니다. 다만 "우연히 막힘"에 의존하는 방어는 방어라 부르기 애매하고, #32(재입고
상한 가드 비대칭)와 함께 보면 비현실적으로 큰 값(예: `stock = 2_000_000_000`)을
막을 명시적 장치가 없다는 게 근본 원인.

**해결 방향**: 비즈니스적으로 합리적인 상한을 정해(`@Max(9999)` 등) 명시적으로
방어. #32와 함께 처리하면 자연스러움.

---

## 🧪 테스트 공백

| 대상 | 현황 | 비고 |
|---|---|---|
| `CartItemControllerTest` | ✅ 완료 | #1 버그를 잡을 수 있었던 테스트 |
| `ProductService/Controller` 테스트 | ✅ 완료 | |
| `AuthController` 로그아웃 테스트 | ✅ 완료 | |
| prod 프로파일 기동 검증 | ✅ 완료 (CI `prod-boot-check`) | |
| `ProductRepositoryTest` (2026-07-20 발견) | 없음 | #34 참고 — stock 쿼리 테스트가 `order` 도메인에 잘못 위치 |

기타: `UserSignupIntegrationTest`는 `@Transactional` 롤백을 쓰면서 `deleteAll()`도 호출 —
중복이므로 하나만 남기기. (→ 처리 완료, 2026-07-05)

---

## 권장 처리 순서 (2026-07-03 라운드, 완료)

1. **#1 오타 수정** (앱이 안 뜸) + `CartItemControllerTest` 작성으로 재발 방지
2. **#3, #4** 세션 고정 + SameSite (보안, 각각 몇 줄)
3. **#8** advice 스코핑 (조용히 버그를 가리는 중)
4. **#11** OSIV off (지금 진행 중인 N+1 검증 작업의 전제)
5. **#2, #14, #20** prod 설정 정리 → **#12** Flyway (Docker Compose 계획과 함께)
6. **#5** Role 모델, **#10** CORS/배포 토폴로지 결정
7. 나머지는 리팩토링 기회에 순차 처리

## 권장 처리 순서 (2026-07-20 라운드, 신규)

1. **#22** 상품 삭제 500 에러 — 사용자 대면 버그, 수정 범위 작음(예외 핸들러 추가)
2. **#23** 회원가입 rate limiting — #7과 동일 패턴 재사용 가능, 보안 공백
3. **#24** `LoginRequest` 크기 제한 — 한 줄 수정
4. **#28** 주문 아이템 정렬 불일치 — 쿼리 한 줄, 사용자 체감 버그
5. **#29, #30** orders 복합 인덱스 + 페이지 크기 상한 — 스케일 대비, 마이그레이션 하나 + 설정 한 줄
6. **#31, #32, #40** 장바구니 N+1, incrementStock 가드, quantity/stock 상한 — 묶어서 한 PR
7. **#33, #34, #39** 죽은 코드 정리 + `ProductRepositoryTest` 신설
8. **#25~#27** BCrypt truncation, CI permissions, SSH host-key pinning — 인프라 성격이라 별도 PR
9. **#35~#38** Jacoco, stale 주석, validation 메시지 통일, Swagger 문서화 — 리팩토링 기회에 순차 처리
