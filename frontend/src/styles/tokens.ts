/**
 * Single source of truth for the color palette.
 *
 * Values are copied verbatim from the project spec (BA v2 / TDD / plan-giai-doan-1
 * §4 F1) — do not invent new colors here. Both Tailwind (tailwind.config.ts) and
 * Ant Design (src/styles/antd-theme.ts) read from this file so the two UI systems
 * can never visually drift apart.
 */
export const colorTokens = {
  primary: "#8c2d19", // do tram - primary brand / CTA
  primaryDark: "#661e0f",
  primaryLight: "#fbeee9", // tint for subtle backgrounds/badges
  bgPage: "#f8f6f2", // nen kem
  bgCard: "#ffffff",
  // Aged-paper tone reserved for the deceased: the living/deceased split is a
  // required visual distinction (BA v2, plan §4 F2/F3), and it must read as
  // reverence rather than as a "disabled" state.
  bgDeceased: "#f2ede2",
  accent: "#d97706", // ho phach
  textMain: "#2b2825",
  textMuted: "#5e564d",
  secondary: "#2c3e50",
  border: "#e6dfd5",
  borderDark: "#cdbeaa",
  // Status colors kept close to AntD defaults but tuned warmer, used sparingly
  // (e.g. living/deceased distinction badges in later sprints).
  success: "#15803d",
  successBg: "#eaf6ee",
  danger: "#b91c1c",
  dangerBg: "#fbeae9",
  warningBg: "#fdf6e7",
} as const;

export type ColorTokenName = keyof typeof colorTokens;
