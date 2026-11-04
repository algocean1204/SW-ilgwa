// 일과 전용 stroke 기반 아이콘 세트
import React from 'react';

interface IconProps {
  name: string;
  size?: number;
  color?: string;
  strokeWidth?: number;
  style?: React.CSSProperties;
}

// 사용법: <Icon name="plus" size={16} color="currentColor" />
// 모든 아이콘은 24x24 viewBox, stroke 기반, 기본 strokeWidth 1.6
function Icon({ name, size = 18, color = "currentColor", strokeWidth = 1.6, style }: IconProps) {
  const s = size;
  const props = {
    width: s, height: s, viewBox: "0 0 24 24",
    fill: "none", stroke: color, strokeWidth,
    strokeLinecap: "round" as const, strokeLinejoin: "round" as const,
    style: { display: "inline-block", flexShrink: 0, verticalAlign: "middle" as const, ...(style || {}) },
  };
  const paths = ICONS[name];
  if (!paths) return <svg {...props} />;
  return <svg {...props}>{paths}</svg>;
}

const ICONS: Record<string, React.ReactNode> = {
  // 내비게이션
  home: (<><path d="M3 11.5L12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1v-8.5z"/></>),
  plus: (<><path d="M12 5v14M5 12h14"/></>),
  book: (<><path d="M4 4h10a4 4 0 0 1 4 4v12H8a4 4 0 0 1-4-4V4z"/><path d="M4 16a4 4 0 0 1 4-4h10"/></>),
  cards: (<><rect x="3" y="6" width="14" height="12" rx="2"/><path d="M7 3h12a2 2 0 0 1 2 2v12"/></>),
  chart: (<><path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/></>),
  owl: (<><circle cx="12" cy="13" r="8"/><circle cx="9" cy="11" r="1.8"/><circle cx="15" cy="11" r="1.8"/><path d="M12 14.5v1.5M8 5.5L10 8M16 5.5L14 8"/></>),
  sparkle: (<><path d="M12 3v6M12 15v6M3 12h6M15 12h6M6.5 6.5l3 3M14.5 14.5l3 3M17.5 6.5l-3 3M9.5 14.5l-3 3"/></>),
  settings: (<><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></>),

  // 액션
  check: (<><path d="M4 12l5 5L20 6"/></>),
  x: (<><path d="M6 6l12 12M18 6L6 18"/></>),
  arrowRight: (<><path d="M5 12h14M13 6l6 6-6 6"/></>),
  arrowUp: (<><path d="M12 19V5M6 11l6-6 6 6"/></>),
  arrowDown: (<><path d="M12 5v14M6 13l6 6 6-6"/></>),
  edit: (<><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></>),
  trash: (<><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M10 11v6M14 11v6M5 6l1 14a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2l1-14"/></>),
  send: (<><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/></>),
  search: (<><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.35-4.35"/></>),
  download: (<><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/></>),
  upload: (<><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/></>),

  // 콘텐츠
  file: (<><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z"/><path d="M14 2v6h6"/></>),
  note: (<><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z"/><path d="M14 2v6h6M8 13h8M8 17h6"/></>),
  mic: (<><rect x="9" y="3" width="6" height="12" rx="3"/><path d="M19 11a7 7 0 0 1-14 0M12 18v3M8 21h8"/></>),
  signout: (<><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/></>),
  brain: (<><path d="M12 5a3 3 0 0 0-3 3v.5A2.5 2.5 0 0 0 5 11a2.5 2.5 0 0 0 .5 4.5A2.5 2.5 0 0 0 8 19a3 3 0 0 0 4 1 3 3 0 0 0 4-1 2.5 2.5 0 0 0 2.5-3.5A2.5 2.5 0 0 0 19 11a2.5 2.5 0 0 0-4-2.5V8a3 3 0 0 0-3-3z"/><path d="M12 5v15M9 10h-.5M15 10h.5M9 14h-.5M15 14h.5"/></>),
  message: (<><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></>),
  telegram: (<><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/></>),
  map: (<><path d="M1 6v16l7-4 8 4 7-4V2l-7 4-8-4-7 4z"/><path d="M8 2v16M16 6v16"/></>),

  // 장식 / 소형
  star: (<><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></>),
  diamond: (<><path d="M12 2l10 10-10 10L2 12 12 2z"/></>),
  flag: (<><path d="M4 22V3M4 4h14l-3 5 3 5H4"/></>),
  trophy: (<><path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0V4zM7 7H4a2 2 0 0 0 0 4h3M17 7h3a2 2 0 0 1 0 4h-3"/></>),
  clock: (<><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>),
  lock: (<><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 1 1 8 0v4"/></>),
  bell: (<><path d="M18 16v-5a6 6 0 0 0-12 0v5l-2 3h16l-2-3zM10 21a2 2 0 0 0 4 0"/></>),
  user: (<><circle cx="12" cy="8" r="4"/><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"/></>),
  credit: (<><rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/></>),
  warning: (<><path d="M12 3L2 20h20L12 3zM12 10v5M12 18v.5"/></>),
  bolt: (<><path d="M13 2L3 14h7l-1 8 10-12h-7l1-8z"/></>),
  eye: (<><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8S1 12 1 12z"/><circle cx="12" cy="12" r="3"/></>),
  pdf: (<><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z"/><path d="M14 2v6h6"/><path d="M8 13v5M8 13h1.5a1.5 1.5 0 0 1 0 3H8M12 13v5h1.5a1.5 1.5 0 0 0 1.5-1.5v-2A1.5 1.5 0 0 0 13.5 13H12zM17 13h2M17 13v5M17 16h1.5"/></>),
  wand: (<><path d="M15 4V2M15 16v-2M8 9h2M20 9h2M17.8 11.8l1.4 1.4M17.8 6.2l1.4-1.4M3 21l9-9M12.2 6.2l-1.4-1.4"/></>),
  sliders: (<><path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"/></>),
  // 카메라. PhotoDropzone 등 사진 업로드 UI
  camera: (
    <>
      <path d="M4 7h3l2-2h6l2 2h3v12H4z" strokeLinecap="round" strokeLinejoin="round"/>
      <circle cx="12" cy="13" r="3.5"/>
    </>
  ),
  // 사이드바 토글: 메뉴(햄버거)와 왼쪽 화살표
  menu: (<><path d="M3 12h18M3 6h18M3 18h18"/></>),
  'chevron-left': (<><path d="M15 18l-6-6 6-6"/></>),
  'chevron-right': (<><path d="M9 18l6-6-6-6"/></>),
  // 캘린더. 학습 캘린더·스케줄 항목
  calendar: (<><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></>),
  // 재생. 이어서 학습하기·시작 항목
  play: (<><path d="M5 3l14 9-14 9V3z"/></>),
  // 과녁. 실전 모의고사 생성 등 목표 강조 UI
  target: (<><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2" fill="currentColor"/></>),
  // 휴대폰. 텔레그램 모바일 사용 안내 등
  phone: (<><rect x="6" y="2" width="12" height="20" rx="2"/><path d="M10 18h4"/></>),
  // 나침반. 커스터마이징 UI
  compass: (<><circle cx="12" cy="12" r="10"/><path d="M14.5 9.5l-2 5-5 2 2-5 5-2z"/></>),
  // 새로고침. 업/다운그레이드 등 전환 동작
  refresh: (<><path d="M21 12a9 9 0 1 1-2.64-6.36M21 4v5h-5"/></>),
  // 볼륨. 음성 미리듣기 등 오디오 UI
  volume: (<><path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M16 8a5 5 0 0 1 0 8M19 5a9 9 0 0 1 0 14"/></>),
  // 불꽃. 학습 스트릭 배지 등
  flame: (<><path d="M12 2c1.5 3 0 4.5-1 6-1.5 2-2 4-1 6a4 4 0 0 0 8 0c0-1.5-.5-2.5-1.5-3.5.5 2-.5 3.5-2 3.5s-2.5-1.5-2-3.5c.5-2 2-3 2-5-.5 1-2.5 0-2.5-3.5z"/></>),
};

export default Icon;
