# Exception Handling / Response Format Conventions

## Exception Structure
```
global/exception/
├── model/BaseErrorCode.java       ← Interface (do not modify)
├── CustomException.java           ← Custom exception class (do not modify)
└── GlobalExceptionHandler.java    ← Global exception handler (do not modify)

{도메인}/exception/
└── XXXErrorCode.java              ← Domain-specific error codes (edit here)
```

---

### Do Not Modify
- **BaseErrorCode**: Interface declaring `getHttpStatus()`, `getCode()`, `getMessage()`.
- **CustomException**: `RuntimeException` that accepts a `BaseErrorCode` and propagates its message.
- **GlobalExceptionHandler**: Registered globally via `@RestControllerAdvice`. Maps `CustomException` → `ApiResponse.error()` using the error code's status and message. Handles all standard Spring exceptions (400, 404, 405, 500, etc.). Only add a new `@ExceptionHandler` method when handling a new standard exception type.

---

### Domain Error Code (Edit Target)

`{domain}/exception/XXXErrorCode.java`
- Implements `BaseErrorCode`
- Requires `@Getter`, `@RequiredArgsConstructor`
- `enum` format; use English messages
- The `code` field must match the enum constant name exactly

```java
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "이미 존재하는 사용자입니다."),
    USER_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "USER_UNAUTHORIZED", "접근 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
```

### Usage in Service

```java
// Simple throw
throw new CustomException(UserErrorCode.USER_NOT_FOUND);

// With Optional
User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
```

---

## ApiResponse (Do Not Modify)

- All Controller responses must be wrapped in `ApiResponse<T>`.
- Fields: `success → data → message → error → timestamp`
- `error` is only serialized on failure (`@JsonInclude(NON_NULL)`)

#### Success
```json
{
  "success": true,
  "data": { ... },
  "message": "커스텀 메시지 (선택)",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

#### Failure
```json
{
  "success": false,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "사용자를 찾을 수 없습니다.",
    "details": { "field": "오류 설명" }
  },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

- `details` is only included when `@Valid` validation fails; otherwise null.

### Usage in Controller

```java
// Single fetch
return ResponseEntity.ok(ApiResponse.success(userService.getUser(userId)));

// With success message
return ResponseEntity.ok(ApiResponse.success(userService.getUser(userId), "조회 성공"));

// Create (201)
return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(userService.createUser(request)));
```

- Failure responses are handled automatically by `GlobalExceptionHandler`. Never call `ApiResponse.error()` directly in a Controller.

### Pagination Response Wrappers
- `CursorPageResponse<T>`: Cursor-based pagination. Wrap as `ApiResponse<CursorPageResponse<T>>`.
- `OffsetPageResponse<T>`: Offset-based pagination. Wrap as `ApiResponse<OffsetPageResponse<T>>`.
- See `service.md` for creation patterns and Service-layer usage.
