## Controller Conventions

### XXXController.java
- Interface dedicated to Swagger documentation
- All Swagger annotations go here: `@Tag`, `@RequestMapping`, `@Operation`, `@ApiResponses`, `@SecurityRequirement`, etc.
- Describe each parameter with `@Parameter`
- No business logic

#### Basic example (simple fetch)
```java
@Tag(name = "User", description = "사용자 관련 API")
@RequestMapping("/api/users")
public interface UserController {

    @Operation(summary = "사용자 조회", description = "ID로 사용자를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다.",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{userId}")
    ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long userId);
}
```

#### Full example (search + filter + pagination + detailed description)
```java
@Tag(name = "Admin User", description = "관리자 - 회원 관리 API")
@RequestMapping("/api/admin/users")
public interface AdminUserController {

    @Operation(summary = "회원 리스트 검색", description = """
            키워드 & 필터 검색을 통해 회원들을 리스트로 검색하는 API입니다.
            
            [**Query Parameter**]
            - page: 페이지 번호 (1 ~ n)
            - size: 페이지 크기
            - keyword: 검색창에 작성한 검색 키워드
                (검색할 수 있는 요소= `Account Code` & `Email` & `Nickname`)
            - status: 검색할 사용자의 상태 필터
                (필터링할 수 있는 요소)
                    1. `ACTIVE` (활동 상태)
                    2. `DORMANT` (휴면 상태)
                    3. `WITHDRAW_PENDING` (탈퇴 대기 상태)
                    4. `SUSPENDED` (정지 상태)
            """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "회원 리스트 검색 성공",
                    content = @Content(schema = @Schema(implementation = OffsetPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 상태 필터 값을 보냈습니다.",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인이 필요합니다.",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "관리자 권한이 필요합니다.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "해당 키워드 및 필터에 맞는 사용자 정보들을 찾지 못했습니다.",
                    content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("")
    ResponseEntity<ApiResponse<OffsetPageResponse<SearchUserResponse>>> searchUserList(
            @Parameter(description = "검색 키워드 (AccountCode | Email | Nickname)", required = true)
            @RequestParam("keyword") String keyword,

            @Parameter(description = "사용자 상태 필터 (ACTIVE | DORMANT | WITHDRAW_PENDING | SUSPENDED)")
            @RequestParam(value = "status", required = false) UserStatus status,

            @Parameter(description = "Page 번호 (1 ~ N)", required = true)
            @RequestParam(value = "page", defaultValue = "1") Integer page,

            @Parameter(description = "Page 크기", required = true)
            @RequestParam(value = "size", defaultValue = "5") Integer size
    );
}
```

#### Swagger rules summary

|Item|Rule|
|---|---|
|`@Operation`|`summary` is a one-line overview; `description` includes multi-line detail with parameter notes|
|`@ApiResponses`|List all possible response codes. Use `content = @Content` when there is no response body|
|`@Parameter`|Required on every `@RequestParam` and `@PathVariable`. Specify `required`|
|`@SecurityRequirement`|Add `name = "bearerAuth"` to any API that requires authentication|
|`content = @Content(schema = @Schema(implementation = XXX.class))`|Specify the actual return DTO class on 200 responses|

---

### XXXControllerImpl.java
- Implements `XXXController`
- Use `@Override` only. Never duplicate Swagger annotations
- No logic; only delegates to Service and returns the response
- Class-level annotations: `@RestController`, `@RequiredArgsConstructor`

```java
@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements UserController {

    private final UserService userService;

    @Override
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUser(userId)));
    }

    @Override
    public ResponseEntity<ApiResponse<OffsetPageResponse<SearchUserResponse>>> searchUserList(
            String keyword, UserStatus status, Integer page, Integer size) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.searchUserList(keyword, status, page, size)));
    }
}
```