## Repository Conventions

### XXXRepository.java
- Extend `JpaRepository<ClassName, Long>`.
- JavaDoc is required on every custom method.

```java
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Checks whether a nickname already exists.
     * @param nickname : Nickname to check for duplication
     * @return : true if already exists, false if available
     */
    boolean existsByNickname(String nickname);

    /**
     * Finds a single user by email.
     * @param email : Email to look up
     * @return : Optional<User>
     */
    Optional<User> findByEmail(String email);
}
```

### Comment Update Policy
Only update comments when a business logic change causes them to diverge from actual behavior. Do not update during refactoring, formatting, or performance optimization.
