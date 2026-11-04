# DTO Conventions

### Rules
- All DTOs must be written as `record` types.
- Response DTOs: `@Builder` is required.
- Request DTOs: `@Builder` is not needed.
- Naming: `XXXResponse` (response), `XXXRequest` (request).
- Folder: requests under `request/`, responses under `response/`.
- Use `@Schema` for Swagger documentation on both the class and all fields.
- Request DTOs must include validation annotations; `@Valid` is required at the Controller level.

```java
// Request DTO
@Schema(description = "사용자 생성 요청 DTO")
public record UserRequest(

        @NotBlank
        @Email
        @Schema(description = "이메일", example = "test@example.com")
        String email,

        @NotBlank
        @Schema(description = "닉네임", example = "홍길동")
        String nickname
) {}

// Response DTO
@Schema(description = "사용자 응답 DTO")
@Builder
public record UserResponse(

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "이메일", example = "test@example.com")
        String email
) {}
```

### Comment Update Policy
Only update comments when a business logic change causes them to diverge from actual behavior. Do not update during refactoring, formatting, or performance optimization.
