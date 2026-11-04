# Entity Conventions
- Class-level annotations: `@Entity`, `@Getter`, `@Builder`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, `@AllArgsConstructor`, `@Table(name = "table_name")`
- Use `@Id` and `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Required fields must explicitly declare `@Column(nullable = false)`
- Enum-type fields must apply `@Enumerated(EnumType.STRING)`

```java
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nickname;

    private String accountCode;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime deletedAt;
}
```

### Rules
- Class-level `@Setter` is prohibited. Field mutations must be expressed through meaningful method names.

### Comment Update Policy
Only update comments when a business logic change causes them to diverge from actual behavior. Do not update during refactoring, formatting, or performance optimization.

## Enum Conventions
- Domain-specific enums are placed in the `enums/` folder within the domain package.
- Global shared enums are placed in `global/enums/`.
- `GlobalResponseCode` lives in `global/enums/` and must not be modified.
- Every enum must include a `description` field.
- `description` should reflect the value exposed to users or the frontend.
- Every enum must declare `@Schema(description = "...")`.

```java
// {domain}/enums/UserRole.java
@Getter
@RequiredArgsConstructor
@Schema(description = "사용자 권한")
public enum UserRole {

    GUEST("비회원"),
    USER("일반 사용자"),
    ADMIN("관리자");

    private final String description;
}
```
