// course-detail 자식 컴포넌트들이 같이 쓰는 타입과 초기값

export interface Chapter {
  id: string;
  /** 챕터 제목 */
  t: string;
  /** 슬라이드 수 */
  s: number;
  /** 예상 분수 */
  m: number;
  status: string;
}

// AI가 생성하는 커리큘럼 초안 기본값
export const INITIAL_CHAPTERS: Chapter[] = [
  { id:"1",  t:"요구사항 확인",         s:1, m:12, status:"done"   },
  { id:"2",  t:"데이터 입출력 구현",    s:2, m:20, status:"active" },
  { id:"3",  t:"통합 구현",             s:1, m:10, status:""       },
  { id:"4",  t:"서버 프로그램 구현",    s:2, m:22, status:""       },
  { id:"5",  t:"인터페이스 구현",       s:1, m:11, status:""       },
  { id:"6",  t:"화면 설계",             s:1, m:12, status:""       },
  { id:"7",  t:"애플리케이션 테스트",   s:1, m:13, status:""       },
  { id:"8",  t:"SQL 응용",              s:1, m:14, status:""       },
  { id:"9",  t:"소프트웨어 개발 보안",  s:1, m:12, status:""       },
  { id:"10", t:"프로그래밍 언어 활용",  s:1, m:14, status:""       },
];
