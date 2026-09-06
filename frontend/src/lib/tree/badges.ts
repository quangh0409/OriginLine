import type { PersonBadge } from "@/types/api";
import { colorTokens } from "@/styles/tokens";

/**
 * Display metadata for the backend-computed `PersonBadge` enum. This is
 * PURELY presentational (label + color for a small tag on the canvas node) —
 * it must never grow into logic that infers a badge client-side. `DECEASED`
 * is handled separately by PersonNode (it drives the whole card's visual
 * treatment, not a little tag), so it's excluded here.
 */
export const BADGE_META: Record<
  Exclude<PersonBadge, "DECEASED">,
  { vi: string; en: string; color: string }
> = {
  DICH_TON: { vi: "Đích tôn", en: "Eldest grandson", color: colorTokens.accent },
  THUA_TU: { vi: "Thừa tự", en: "Designated heir", color: colorTokens.accent },
  KE_TU: { vi: "Kế tự", en: "Adoptive heir", color: colorTokens.accent },
  CON_NUOI: { vi: "Con nuôi", en: "Adopted", color: colorTokens.secondary },
  DAU: { vi: "Dâu", en: "Daughter-in-law", color: "#a3336b" },
  RE: { vi: "Rể", en: "Son-in-law", color: "#2f6fa8" },
  TUYET_TU: { vi: "Tuyệt tự", en: "No descendants", color: colorTokens.textMuted },
  TRUONG_CHI: { vi: "Trưởng chi", en: "Branch head", color: colorTokens.primary },
};

/** Badge type that actually has display metadata (everything but DECEASED). */
export type DisplayBadge = Exclude<PersonBadge, "DECEASED">;

export function displayBadges(badges: PersonBadge[] | undefined): DisplayBadge[] {
  return (badges ?? []).filter((b): b is DisplayBadge => b !== "DECEASED");
}
