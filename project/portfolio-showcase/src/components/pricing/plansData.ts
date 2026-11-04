// 구독 플랜 정의 데이터. PlanCards가 그린다.
import type { SubscriptionPlan } from '@/shared/types';

// 플랜 카드 하나의 정의
export interface PlanDef {
  id: SubscriptionPlan;
  name: string;
  price: number;
  yprice: number;
  tagline: string;
  featured: boolean;
  bullets: string[];
  // excluded가 undefined면 취소선 항목을 렌더링하지 않는다
  excluded: string[] | undefined;
  cta: string;
  // Pro 카드 안에 강조할 혜택 2줄. featured 카드에만 쓴다
  highlights: string[] | undefined;
}

// 플랜 정의. 순서는 Free, Pro, Premium이다.
// bullet은 기능명보다 그 기능으로 할 수 있는 일을 적는다
export const plans: PlanDef[] = [
  {
    id: "free",
    name: "Free",
    price: 0, yprice: 0,
    tagline: "맛보기",
    featured: false,
    bullets: [
      "과외 1개 생성 — 부담 없이 시작",
      "슬라이드 2개 미리보기 (전체 X)",
      "웹에서 튜터 Q&A",
      "기본 튜터 2명 (올빼미·냥)",
      "생성권 구매 가능 (무기한)",
    ],
    excluded: [
      "텔레그램 과제·채점 없음",
      "튜터 커스터마이징 없음",
      "모의고사 없음",
    ],
    cta: "지금 사용 중",
    highlights: undefined,
  },
  {
    id: "pro",
    name: "Pro",
    price: 14900, yprice: 149000,
    tagline: "가장 인기",
    featured: true,
    bullets: [
      "과외 2개 — 강의자료 전체 무제한 해제",
      "코인 5개 즉시 지급 (약 15,000원 상당)",
      "텔레그램 암기노트·과제·채점·총평",
      "나만의 튜터 커스터마이징",
      "학습 리포트로 약점 파악",
      "완강 시 모의고사 1회 무료 (과외별)",
    ],
    excluded: undefined as string[] | undefined,
    cta: "Pro 시작하기 →",
    // Pro 카드 혜택 2줄. "왜 Pro인가"가 가격보다 먼저 보이게 한다
    highlights: [
      "강의자료 전체 무제한 해제",
      "텔레그램 과제·채점·개인 과외 매일",
    ],
  },
  {
    id: "premium",
    name: "Premium",
    price: 49900, yprice: 499000,
    tagline: "집중 합격반",
    featured: false,
    bullets: [
      "과외 10개 — Pro의 모든 기능 포함",
      "코인 22개 즉시 지급 (약 50,000원 상당)",
      "모의고사 추가 2회 더 (총 3회)",
      "우선 지원 — 가장 빠른 응답",
    ],
    excluded: undefined as string[] | undefined,
    cta: "Premium 시작",
    highlights: undefined,
  },
];
