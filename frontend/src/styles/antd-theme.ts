import type { ThemeConfig } from "antd";
import { colorTokens } from "./tokens";

/**
 * Ant Design design tokens, derived from the same palette as Tailwind
 * (src/styles/tokens.ts). Passed to <ConfigProvider theme={antdTheme}> in
 * src/app/providers.tsx.
 */
export const antdTheme: ThemeConfig = {
  token: {
    colorPrimary: colorTokens.primary,
    colorLink: colorTokens.primary,
    colorLinkHover: colorTokens.accent,
    colorInfo: colorTokens.secondary,
    colorSuccess: colorTokens.success,
    colorError: colorTokens.danger,
    colorWarning: colorTokens.accent,
    colorBgLayout: colorTokens.bgPage,
    colorBgContainer: colorTokens.bgCard,
    colorBorder: colorTokens.border,
    colorBorderSecondary: colorTokens.border,
    colorText: colorTokens.textMain,
    colorTextSecondary: colorTokens.textMuted,
    fontFamily:
      'var(--font-be-vietnam-pro), -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif',
    borderRadius: 8,
    wireframe: false,
  },
  components: {
    Layout: {
      headerBg: colorTokens.bgCard,
      bodyBg: colorTokens.bgPage,
      footerBg: colorTokens.bgCard,
    },
    Button: {
      colorPrimary: colorTokens.primary,
      algorithm: true,
    },
    Menu: {
      itemSelectedColor: colorTokens.primary,
      itemSelectedBg: colorTokens.primaryLight,
    },
    Badge: {
      colorError: colorTokens.accent,
    },
  },
};
