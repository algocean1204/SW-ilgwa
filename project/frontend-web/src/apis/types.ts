// 백엔드 공통 응답 타입. 모든 API 함수의 반환 타입이 여기서 나온다.

export interface ErrorResponse {
  code: string;
  message: string;
  traceId: string | null;
  details: Record<string, string> | null;
}

// data: 성공 시 T, ApiResponse.error() 패턴이면 null
// error: @JsonInclude(NON_NULL) 설정으로 성공 응답에서 생략될 수 있으므로 optional
export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  error?: ErrorResponse | null;
  timestamp: string;
}

// OffsetPageable 기반 커스텀 오프셋 페이지네이션
export interface OffsetPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

// Spring Data Page<T> 직렬화 필드 전체 (pageable/sort 포함)
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
  numberOfElements: number;
  pageable: {
    pageNumber: number;
    pageSize: number;
    sort: { sorted: boolean; empty: boolean; unsorted: boolean };
    offset: number;
    paged: boolean;
    unpaged: boolean;
  };
  sort: { sorted: boolean; empty: boolean; unsorted: boolean };
}

// API 호출 실패 시 throw하는 에러. status 0은 네트워크 오류다.
export class ApiError extends Error {
  readonly status: number;
  readonly errorResponse: ErrorResponse | null;

  constructor(status: number, errorResponse: ErrorResponse | null) {
    super(errorResponse?.message ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.errorResponse = errorResponse;
  }
}
