// 리포트 페이지 하위 컴포넌트들이 같이 쓰는 타입
import type { Palette } from '@/lib';

// 요일별 학습 데이터. 차트, KPI, 사이드 요약이 같이 쓴다.
export interface WeekDay {
  d: string;
  m: number;
  q: number;
  r: number;
}

// KPI 카드 단일 항목 props
export interface KpiProps {
  label: string;
  value: React.ReactNode;
  delta: string;
  palette: Palette;
}

// 차트 범례 아이템 props
export interface LegendProps {
  c: string;
  t: string;
}

// 개념 카드 내부 항목
export interface ConceptItem {
  k: string;
  v: number;
  desc: string;
}

// 개념 카드 전체 props
export interface ConceptCardProps {
  title: string;
  subtitle: string;
  tone: 'warn' | 'ok';
  items: ConceptItem[];
  palette: Palette;
}
