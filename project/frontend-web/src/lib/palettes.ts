// UI 팔레트. 컴포넌트는 palette 키 하나로 색상 세트를 가져온다.

export interface Palette {
  primary: string;
  primary2: string;
  wash: string;
  soft: string;
}

// 지원하는 팔레트 3종
export const PALETTES: Record<string, Palette> = {
  grass: { primary: "#207B4C", primary2: "#36B36F", wash: "#ECF8E6", soft: "#ADD87B" },
  coral: { primary: "#C2425B", primary2: "#FF7D87", wash: "#FFEDEF", soft: "#FFB5BB" },
  honey: { primary: "#8E6A10", primary2: "#D9B42E", wash: "#FFF7D1", soft: "#FEE87E" },
};
