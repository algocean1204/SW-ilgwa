## Service Conventions

### XXXService.java
- Class-level annotations: `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
- Interface is prohibited
- All **5 rules below are required on every method**

#### Rule 1: JavaDoc
Describe the method's purpose, detailed behavior, `@param`, and `@return`

#### Rule 2: Start log
```java
log.info("[XXXService] functionName() - START | param: {}", param);
```

#### Rule 3: End log
```java
log.info("[XXXService] functionName() - END | result: {}", result);
```

#### Rule 4: Inline comment format
Inside `/* */`, use `(1) step index` and `(2) step detail description` format

#### Rule 5: Exception handling
Always throw exceptions as `throw new CustomException(XXXErrorCode.XXX)`

---

### Example
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * User Entity 생성 공통 로직
     * - AccountCode, Nickname을 자동으로 생성해준다.
     * - email은 선택적으로 넣을 수 있다.
     * - User 생성 후, userRepository에 save()까지 수행한 결과물을 반환한다.
     * @param email : Guest-null, Google-Token에 있는 Google Email 설정
     * @return : 저장된 UserResponse
     */
    public UserResponse createUser(String email) {
        log.info("[UserService] createUser() - START | email: {}", email);

        /*
            1. 사용자 객체 생성
            - builder를 이용해서 User를 생성하기 위한 값을 추가
         */
        User user = User.builder()
                .email(email)
                .nickname(generateNickname())
                .accountCode(generateAccountCode())
                .build();

        /*
            2. 생성한 사용자 객체 저장
            - 생성한 사용자 객체를 저장한다.
        */
        User savedUser = userRepository.save(user);

        /*
            3. ResponseDto Mapping
            - 저장한 Entity를 Mapper를 사용해서 Dto로 매핑한다.
        */
        UserResponse result = userMapper.toResponse(savedUser);

        log.info("[UserService] createUser() - END | userId: {}", savedUser.getId());
        return result;
    }

    /**
     * userId로 사용자 단건 조회
     * - 존재하지 않는 userId인 경우 UserErrorCode.USER_NOT_FOUND 예외 발생
     * @param userId : 조회할 사용자 ID
     * @return : 조회된 UserResponse
     */
    public UserResponse getUser(Long userId) {
        log.info("[UserService] getUser() - START | userId: {}", userId);

        /*
            1. 사용자 단건 조회
            - userId로 사용자를 조회하고, 존재하지 않으면 USER_NOT_FOUND 예외를 발생시킨다.
        */
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        /*
            2. ResponseDto Mapping
            - 조회한 Entity를 Mapper를 사용해서 Dto로 매핑한다.
        */
        UserResponse result = userMapper.toResponse(user);

        log.info("[UserService] getUser() - END | userId: {}", userId);
        return result;
    }
}
```

---

### Pagination

#### Cursor-based (`CursorPageResponse`)
Used for infinite scroll and sequential traversal. Fetch `size + 1` to determine if a next page exists.

```java
/**
 * 커서 기반으로 사용자 목록 조회
 * @param cursor : 이전 응답의 nextCursor (첫 조회 시 null)
 * @param size   : 페이지 크기
 * @return : CursorPageResponse<UserResponse>
 */
public CursorPageResponse<UserResponse> getUserList(String cursor, Integer size) {
    log.info("[UserService] getUserList() - START | cursor: {}, size: {}", cursor, size);

    /*
        1. 커서 기반으로 사용자 목록 조회
        - size + 1개를 조회해 다음 페이지 존재 여부를 판단한다.
    */
    List<User> users = userRepository.findByCursor(cursor, size + 1);

    /*
        2. 다음 페이지 여부 확인 및 nextCursor 추출
        - size + 1개가 조회되면 다음 페이지가 존재. 초과분을 제거하고 마지막 요소의 ID를 커서로 사용한다.
    */
    boolean hasNext = users.size() > size;
    if (hasNext) users = users.subList(0, size);
    String nextCursor = hasNext ? String.valueOf(users.get(users.size() - 1).getId()) : null;

    /*
        3. ResponseDto Mapping 및 CursorPageResponse 래핑
    */
    List<UserResponse> content = users.stream().map(userMapper::toResponse).toList();
    CursorPageResponse<UserResponse> result = CursorPageResponse.of(content, nextCursor, hasNext, size);

    log.info("[UserService] getUserList() - END | resultSize: {}", content.size());
    return result;
}
```

#### Offset-based (`OffsetPageResponse`)
Used when arbitrary page navigation is needed (e.g. admin pages). Convert frontend 1-based `page` to internal 0-based index.

```java
/**
 * 오프셋 기반으로 사용자 목록 조회
 * @param page : 페이지 번호 (1 ~ N, 프론트 기준)
 * @param size : 페이지 크기
 * @return : OffsetPageResponse<UserResponse>
 */
public OffsetPageResponse<UserResponse> getUserPageList(Integer page, Integer size) {
    log.info("[UserService] getUserPageList() - START | page: {}, size: {}", page, size);

    /*
        1. Pageable 생성
        - 프론트에서 전달받은 page는 1부터 시작하므로, 0 기반 인덱스로 변환한다.
    */
    Pageable pageable = PageRequest.of(page - 1, size);

    /*
        2. 사용자 목록 페이지 조회
    */
    Page<User> pageResult = userRepository.findAll(pageable);

    /*
        3. ResponseDto Mapping 및 OffsetPageResponse 래핑
        - OffsetPageResponse.of()의 page 파라미터는 0-based로 전달한다.
    */
    List<UserResponse> content = pageResult.getContent().stream().map(userMapper::toResponse).toList();
    OffsetPageResponse<UserResponse> result =
            OffsetPageResponse.of(content, pageResult.getTotalElements(), page - 1, size);

    log.info("[UserService] getUserPageList() - END | totalElements: {}", pageResult.getTotalElements());
    return result;
}
```

### Comment Update Policy
Only update comments when a business logic change causes them to diverge from actual behavior. Do not update during refactoring, formatting, or performance optimization.