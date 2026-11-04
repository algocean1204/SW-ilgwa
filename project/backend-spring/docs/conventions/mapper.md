## Mapper Conventions

### Rules
- Entity ↔ DTO conversion must go through a Mapper class exclusively.
- Annotate with `@Component`. Package: `{domain}/mapper/`. Class name: `XXXMapper`.
- JavaDoc is required on every method.
- Mappers also handle cases involving multiple entities or additional computed data.
- Method naming follows `to{ReturnType}`. Since the class name already contains the domain, do not repeat it in method names.
    - Request DTO → Entity: `toEntity` (accepts additional Entity params if needed)
    - Entity → default response: `toResponse` / list: `toResponseList`
    - Entity → specialized response: prefix with a qualifier, e.g. `toSummaryResponse`, `toDetailResponse`

### Prohibited
- Static `from()` methods inside DTOs or direct `new XXXResponse(...)` construction are forbidden.
- Direct DTO conversion inside Service is forbidden. Always use a Mapper.
- Do not repeat the domain name in method names: use `toResponse`, not `toUserResponse`.

```java
@Component
public class ApplicationFormMapper {

    /**
     * @param semester : 연결할 Semester Entity
     * @param request  : 변환할 ApplicationFormUpsertRequest
     * @return : 변환된 ApplicationForm Entity
     */
    public ApplicationForm toEntity(Semester semester, ApplicationFormUpsertRequest request) {
        return ApplicationForm.builder()
                .semester(semester)
                .openAt(request.getOpenAt())
                .closeAt(request.getCloseAt())
                .applicationResultAt(request.getApplicationResultAt())
                .interviewScheduleConfirmedAt(request.getInterviewScheduleConfirmedAt())
                .finalResultAt(request.getFinalResultAt())
                .build();
    }

    /** @param applicationForm : 변환할 ApplicationForm Entity */
    public ApplicationFormResponse toResponse(ApplicationForm applicationForm) { ... }

    /** @param applicationForms : 변환할 ApplicationForm Entity 목록 */
    public List<ApplicationFormResponse> toResponseList(List<ApplicationForm> applicationForms) {
        return applicationForms.stream().map(this::toResponse).toList();
    }

    /** 특수 응답 구분자 prefix 패턴 */
    public ApplicationFormSummaryResponse toSummaryResponse(ApplicationForm applicationForm) {
        Long semester = applicationForm.getSemester().getSemester();
        return ApplicationFormSummaryResponse.builder()
                .semester(semester)
                .title(semester + "기 아기사자 모집 지원서")
                .closeAt(applicationForm.getCloseAt())
                .build();
    }

    /** @param applicationForms : 변환할 ApplicationForm Entity 목록 */
    public List<ApplicationFormSummaryResponse> toSummaryResponseList(List<ApplicationForm> applicationForms) {
        return applicationForms.stream().map(this::toSummaryResponse).toList();
    }
}
```

### Pagination Response Conversion
Mappers only handle element-level conversion. Page wrapping is done in the Service:

```java
// content = list.stream().map(mapper::toResponse).toList()
CursorPageResponse.of(content, nextCursor, hasNext, size)
OffsetPageResponse.of(content, totalElements, page, size)
```

### Comment Update Policy
Only update comments when a business logic change causes them to diverge from actual behavior. Do not update during refactoring, formatting, or performance optimization.
