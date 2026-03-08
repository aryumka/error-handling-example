# error-handling-example

## 에러 처리 설계

### 1. 에러를 세 가지로 나눔

- **BusinessError** — 도메인 규칙 위반 (예: 고객 없음, 상태 불일치). 예상 가능한 실패
- **InfraException** — 인프라 장애 (예: PG사 통신 오류, 재고 시스템 오류). 원인을 아는 예외적 상황
- **Unknown** — 미처리 예외 (예: NPE, ClassCastException). 예상하지 못한 버그

**기준**: 호출자가 대응할 수 있으면 BusinessError, 원인을 아는 인프라 장애면 InfraException, 나머지는 Unknown

### 2. 처리 방식을 다르게 함

- BusinessError → `Result`로 값 처리 → 400 응답
- InfraException → 예외로 throw → `@ExceptionHandler`에서 500 응답
- 미처리 예외 → fallback handler에서 500 UNKNOWN 응답

**이유**: 예상 가능한 실패는 타입으로 강제하고, 인프라 장애는 예외로 전파해서 관심사를 분리

### 3. 알림과 에러 분류를 분리함

- 에러 분류(400/500)는 타입이 결정
- Slack 알림은 에러 타입과 별개로 정책으로 결정 (`alertIfNeeded`)

**이유**: 로그 기반 알림은 에러 로그가 찍히면 일괄 발송되므로 "이 에러는 알림, 이 에러는 무시" 같은 선별이 어렵다.
알림을 위해 400까지 `log.error`로 찍으면 ERROR 비율이 부풀어서 에러율 지표가 왜곡된다.
같은 400이라도 `OrderNotReadyError`는 모니터링 해야 하고, `CustomerNotFoundError`는 정상 흐름이다.
이런 판단은 에러 타입이 아니라 내부 정책으로 결정해야 유연하다.

### 4. 모니터링은 공통 로깅으로 처리

- `toResponseEntity()`에서 모든 BusinessError를 일괄 로깅
- `GlobalExceptionHandler`에서 InfraException을 MDC(`errorModule`)와 함께 로깅
- 어떤 에러를 볼지는 코드가 아니라 Grafana/OpenSearch에서 결정

**이유**: 모니터링 대상은 수시로 바뀔 수 있으므로 코드 배포 없이 대시보드에서 조정

### 5. 인프라 예외에 module을 부여함

- `InfraException(message, module = "payment")` → MDC에 `errorModule`로 기록
- BusinessError에는 module 없음

**이유**: 인프라 오류는 담당 팀 식별이 필요하지만, 비즈니스 에러는 특정 팀 귀책이 아님

### 에러 응답 바디

```json
{
  "error": "PAYMENT_GATEWAY_ERROR",
  "module": "payment",
  "message": "PG사 통신 중 오류가 발생했습니다."
}
```

| 필드 | 설명 | 값 |
|------|------|-----|
| `error` | 에러 코드. 클라이언트가 분기 처리할 때 사용 | `CUSTOMER_NOT_FOUND`, `PAYMENT_GATEWAY_ERROR` 등 |
| `module` | 담당 모듈. INFRA 에러만 포함, 모니터링/팀 라우팅용 | `payment`, `unknown` 등 |
| `message` | 사용자 표시용 메시지 | 한글 에러 메시지 |

`error` 필드는 클라이언트가 에러별 분기 처리에 사용하는 고정 코드다.
API 계약으로 유지되므로 내부 리팩토링이 클라이언트에 breaking change를 일으키지 않는다.

- 400 응답: `module` 없음 (특정 팀 귀책이 아님)
- 500/503 응답: `module` 있음 (담당 팀 식별)

`type` 필드는 두지 않았다. 에러 분류는 HTTP 상태 코드(400/500/503)로 이미 드러나고, 구체적인 에러는 `error` 필드로 구분할 수 있으므로 중복이다.

심각도(severity) 필드도 같은 이유로 두지 않았다. 400은 예상된 실패, 500은 시스템 이상, 503은 일시적 장애(재시도 가능) — 상태 코드 자체가 심각도 역할을 한다.

### BusinessError를 400 하나로 통일한 이유

`CustomerNotFoundError`를 404로, `OrderNotReadyError`를 409로 세분화할 수도 있지만,
상태 코드만으로는 클라이언트가 구체적인 대응을 결정하기 어렵다.
409 Conflict를 받아도 "뭐가 충돌인지"는 결국 `error` 필드를 봐야 한다.
상태 코드 세분화는 같은 정보를 두 곳(`error` 필드 + 상태 코드)에 중복하는 셈이므로,
400으로 통일하고 `error` 필드로 분기하는 방식을 선택했다.

### 503 Service Unavailable

`InfraException`에 `retryable = true`가 설정된 예외는 500 대신 503으로 응답하고 `Retry-After` 헤더를 포함한다.
클라이언트는 이 헤더를 보고 재시도 여부와 간격을 판단할 수 있다.

- `PaymentGatewayException` → 서비스에서 3회 재시도 후 실패 시 503, `Retry-After: 30`
- `PaymentLogicException` → 500 (재시도 무의미)

## 구현 방식
### Result 패턴

`Result<E, T>`는 함수형 프로그래밍의 Either 모나드에서 가져온 패턴이다.
성공(`T`)과 실패(`E`)를 하나의 타입으로 표현하고, `map`/`flatMap`/`recover` 등의 연산으로 합성한다.

모나드의 핵심은 **실패가 자동 전파**된다는 점이다.
체인 중간에 `Failure`가 발생하면 이후 연산은 건너뛰고 실패가 끝까지 전달된다.
`try-catch`와 달리 이 흐름이 타입으로 강제되어, 실패 처리를 빠뜨릴 수 없다.

```
Success(customer) → map → flatMap → Success(결과)
Failure(에러)     → map → flatMap → Failure(에러)  ← 중간 연산 스킵
```

Kotlin 표준 라이브러리의 `kotlin.Result`는 에러 타입을 `Throwable`로 고정하지만,
이 프로젝트의 `Result<E, T>`는 에러 타입을 제네릭으로 열어서
도메인에 맞는 에러 계층(`BusinessError` 등)을 사용할 수 있다.

### Java에서 비슷하게 구현하려면

**방법 1: vavr 라이브러리 `Either`** — Result와 가장 유사하지만 `bind()`가 없어서 flatMap 중첩을 피할 수 없다.

```java
getCustomer(id).flatMap(customer ->
    getProgress(orderId).flatMap(progress ->
        getItems(orderId).flatMap(items ->
            // 깊어짐...
        )
    )
);
```

**방법 2: checked exception** — 실패가 시그니처에 드러나고 명령형 스타일이라 중첩 없이 쓸 수 있다.
다만 합성(`map`/`recover`)이 안 되고, catch 블록이 반복되며, 실무에서 기피되는 경향이 있다.

```java
Customer getCustomer(...) throws CustomerNotFoundException;
// 호출자가 try-catch를 강제당함 — 실패 시그니처 명시라는 목적은 Result와 동일
```

**방법 3: Go 스타일 응답 래퍼** — 결과와 에러를 하나의 객체에 담아 반환한다. 단순하지만 에러 체크를 빼먹어도 컴파일러가 잡아주지 않는다.

```java
Response<Customer> response = getCustomer(id);
if (response.getError() != null) { return Response.error(response.getError()); }
// error 체크를 안 해도 컴파일 됨 — response.getData()가 null일 수 있음
```

**핵심 한계**:
vavr은 합성은 되지만 중첩이 깊고, checked exception은 중첩은 없지만 합성이 안 되고,
Go 스타일은 단순하지만 에러 체크 누락을 강제할 수 없다.
이 프로젝트의 `Result<E, T>` + `bind()`는 합성과 명령형 스타일을 모두 지원한다.

### BusinessError에 throw가 아닌 Result를 쓰는 이유

예외(throw)는 "여기서 실패할 수 있다"는 정보가 시그니처에 드러나지 않는다.
호출자가 try-catch를 빠뜨려도 컴파일러가 잡아주지 않고, 어떤 예외가 올지 코드를 따라가봐야 안다.

`Result<BusinessError, T>`를 반환하면 실패 가능성이 타입에 명시되어,
호출자가 반드시 성공/실패를 처리해야 한다.

```kotlin
// 서비스에서 resultOf + ensure로 비즈니스 검증
resultOf {
    val customer = ensureNotNull(customerQueryPort.getCustomer(id)) { CustomerNotFoundError() }
    ensure(customer.isActive) { CustomerNotFoundError() }
    // 실패 시 Result.Failure로 반환, 호출자가 반드시 처리해야 함
}
```

### InfraException에 throw를 쓰는 이유

인프라 장애는 예상된 비즈니스 케이스가 아니므로 Result가 아닌 throw로 처리한다.
DB가 죽으면 서비스 코드에서 할 수 있는 게 없고, 공통 핸들러에서 500으로 처리하면 된다.

> **`PaymentExecutionPort`는 예외적으로 Result를 반환한다.**
> retry/recover 같은 Result 합성 기능을 보여주기 위한 데모 용도이다.
> 실무에서 인프라 재시도는 resilience4j 등으로 처리하고, 포트는 성공 또는 throw만 하는 것이 기준에 맞다.

외부 시스템 연동 시 주의할 점은 **어댑터에서 예외를 InfraException으로 분류하는 것**이다.
raw exception이 그대로 올라가면 UNKNOWN으로 처리되어 `module` 정보가 빠진다.
`GlobalExceptionHandler`는 `InfraException`과 `Exception`을 모두 잡으므로 빠뜨릴 일은 없지만,
어댑터에서 감싸지 않으면 장애 원인 추적이 어려워진다.

### resultOf + ensure 명령형 스타일

`resultOf { }` 블록 안에서 `ensure`/`ensureNotNull`로 검증하면
조건 실패 시 자동으로 `Result.Failure`를 반환한다.
명령형 스타일을 유지하면서 Result의 타입 안전성을 가져간다.

```kotlin
resultOf {
    val customer = ensureNotNull(customerQueryPort.getCustomer(id)) { CustomerNotFoundError() }
    ensure(customer.isActive) { CustomerNotFoundError() }

    val progress = orderStatusQueryPort.getProgress(orderId)
    ensure(progress.isOrderable) { OrderNotReadyError(progress.status.name) }

    val items = orderItemQueryPort.getItems(orderId)
    ensure(items.isNotEmpty()) { EmptyCartError() }
    // ...
}
```

### ensure / ensureNotNull로 조건 검증을 통일

`ensure(조건) { 에러 }`와 `ensureNotNull(값) { 에러 }`로 비즈니스 검증을 통일했다.
Query 포트는 plain 값을 반환하고, 검증은 서비스에서 일괄 처리한다.

```kotlin
val customer = ensureNotNull(getCustomer(id)) { CustomerNotFoundError() }  // null 검증 + smart cast
ensure(customer.isActive) { CustomerNotFoundError() }                       // 조건 검증
ensure(items.isNotEmpty()) { EmptyCartError() }                            // 조건 검증
```

### Swagger 에러 문서 자동 분류

커스텀 `@ApiErrors` 어노테이션에 에러 클래스만 나열하면 상속 관계로 400/500/503이 자동 분류된다.
springdoc의 `OperationCustomizer` / `GlobalOpenApiCustomizer` 확장 포인트로 구현했다.

```kotlin
// BusinessError 하위 → 400
// InfraException(retryable=false) → 500
// InfraException(retryable=true) → 503
// @ApiErrors 없는 엔드포인트에도 UNKNOWN 500은 글로벌로 자동 등록
@ApiErrors([
  CustomerNotFoundError::class,     // → 400
  PaymentGatewayException::class,   // → 503 (retryable)
  PaymentLogicException::class,     // → 500
  ...
])
@PostMapping("/place")
fun placeOrder(...): ResponseEntity<Any>
```

## TODO

- **sealed interface로 유스케이스별 에러 계약 정의** — `sealed interface PlaceOrderError : BusinessError`로 엔드포인트별 발생 가능한 에러를 exhaustive when으로 강제
- **bind() 개선** — invoke operator 등으로 `.bind()` 호출을 줄이는 방안 검토
- **도메인 에러 발생 시 트랜잭션 롤백 고려** — BusinessError는 Result로 반환되어 예외가 아니므로 Spring `@Transactional`의 자동 롤백이 동작하지 않는다. 트랜잭션 내에서 Result.Failure 반환 시 명시적 롤백 처리가 필요할 수 있다.
