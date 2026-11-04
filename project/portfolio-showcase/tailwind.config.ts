// PALETTES(grass/coral/honey)와 기존 CSS 변수 토큰을 Tailwind theme에 옮긴다.
// shadcn/ui v2가 요구하는 tailwindcss-animate 플러그인을 포함한다.

import type { Config } from "tailwindcss";
import tailwindcssAnimate from "tailwindcss-animate";

const config: Config = {
  // shadcn 표준인 class 기반 다크모드
  darkMode: ["class"],

  content: [
    "./index.html",
    "./src/**/*.{js,jsx,ts,tsx}",
  ],

  theme: {
    extend: {
      // 팔레트 색상 (lib PALETTES와 같은 값)
      colors: {
        // grass 팔레트 (primary 색상계)
        grass: {
          primary:  "#207B4C",
          primary2: "#36B36F",
          wash:     "#ECF8E6",  // 매우 연한 배경
          soft:     "#ADD87B",  // 중간 accent
        },
        // coral 팔레트 (경고/강조 색상계)
        coral: {
          primary:  "#C2425B",
          primary2: "#FF7D87",
          wash:     "#FFEDEF",
          soft:     "#FFB5BB",
        },
        // honey 팔레트 (골드/경고 색상계)
        honey: {
          primary:  "#8E6A10",
          primary2: "#D9B42E",
          wash:     "#FFF7D1",
          soft:     "#FEE87E",
        },

        // CSS 변수 기반 시맨틱 토큰. 이름은 shadcn의 CSS 변수 규칙을 따른다.
        background:  "var(--bg)",
        paper:       "var(--paper)",
        ink: {
          DEFAULT: "var(--ink)",
          "2":     "var(--ink-2)",
          "3":     "var(--ink-3)",
          muted:   "var(--muted)",
        },
        sand: {
          DEFAULT: "var(--sand)",
          "2":     "var(--sand-2)",
        },
        line: {
          DEFAULT: "var(--line)",
          "2":     "var(--line-2)",
        },
        // shadcn 내부 토큰 (components.json과 연동)
        border:      "var(--border)",
        input:       "var(--input)",
        ring:        "var(--ring)",
        foreground:  "var(--foreground)",
        primary: {
          DEFAULT:    "var(--primary)",
          foreground: "var(--primary-foreground)",
        },
        secondary: {
          DEFAULT:    "var(--secondary)",
          foreground: "var(--secondary-foreground)",
        },
        destructive: {
          DEFAULT:    "var(--destructive)",
          foreground: "var(--destructive-foreground)",
        },
        muted: {
          DEFAULT:    "var(--muted)",
          foreground: "var(--muted-foreground)",
        },
        accent: {
          DEFAULT:    "var(--accent)",
          foreground: "var(--accent-foreground)",
        },
        popover: {
          DEFAULT:    "var(--popover)",
          foreground: "var(--popover-foreground)",
        },
        card: {
          DEFAULT:    "var(--card)",
          foreground: "var(--card-foreground)",
        },
      },

      // 서체
      fontFamily: {
        // 기본 한국어 UI 폰트
        sans: [
          "Pretendard Variable",
          "Pretendard",
          "Inter",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "sans-serif",
        ],
        // 코드/모노 영역
        mono: [
          "JetBrains Mono",
          "ui-monospace",
          "monospace",
        ],
        // 손글씨 accent (암기노트 등)
        pen: [
          "Nanum Pen Script",
          "cursive",
        ],
      },

      // 모서리 반경
      borderRadius: {
        xs:  "4px",
        sm:  "6px",
        md:  "10px",
        lg:  "14px",
        xl:  "20px",
        "2xl": "28px",
        // shadcn 표준 --radius CSS 변수
        DEFAULT: "var(--radius)",
      },

      // 그림자
      boxShadow: {
        sm: "var(--shadow-sm)",
        md: "var(--shadow-md)",
        lg: "var(--shadow-lg)",
      },

      // shadcn 컴포넌트 애니메이션 키프레임
      keyframes: {
        "accordion-down": {
          from: { height: "0" },
          to:   { height: "var(--radix-accordion-content-height)" },
        },
        "accordion-up": {
          from: { height: "var(--radix-accordion-content-height)" },
          to:   { height: "0" },
        },
      },
      animation: {
        "accordion-down": "accordion-down 0.2s ease-out",
        "accordion-up":   "accordion-up 0.2s ease-out",
      },
    },
  },

  plugins: [
    tailwindcssAnimate,
  ],
};

export default config;
