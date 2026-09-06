import type { Config } from "tailwindcss";
import { colorTokens } from "./src/styles/tokens";

// Tailwind theme is generated from the SAME token source as the Ant Design
// ConfigProvider theme (src/styles/tokens.ts) so the two design systems never
// drift apart. Do not hard-code colors here — add new tokens to tokens.ts.
const config: Config = {
  darkMode: "class",
  content: [
    "./src/app/**/*.{ts,tsx}",
    "./src/components/**/*.{ts,tsx}",
    "./src/hooks/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: colorTokens.primary,
          dark: colorTokens.primaryDark,
          light: colorTokens.primaryLight,
        },
        accent: colorTokens.accent,
        secondary: colorTokens.secondary,
        bg: {
          page: colorTokens.bgPage,
          card: colorTokens.bgCard,
        },
        text: {
          main: colorTokens.textMain,
          muted: colorTokens.textMuted,
        },
        border: {
          DEFAULT: colorTokens.border,
        },
      },
      fontFamily: {
        // Be Vietnam Pro has an explicit "vietnamese" subset covering stacked
        // diacritics (ữ, ỹ, ặ, ...). Noto Serif is the heading/traditional
        // face, also with full Vietnamese coverage.
        sans: [
          "var(--font-be-vietnam-pro)",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "Roboto",
          "Helvetica Neue",
          "Arial",
          "sans-serif",
        ],
        serif: [
          "var(--font-noto-serif)",
          "Georgia",
          "Times New Roman",
          "serif",
        ],
      },
      borderRadius: {
        DEFAULT: "8px",
      },
    },
  },
  plugins: [],
};

export default config;
