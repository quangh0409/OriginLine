import type { PersonBadge } from "@/types/api";
import { colorVars } from "@/styles/tokens";

/**
 * Display metadata for the backend-computed `PersonBadge` enum. This is
 * PURELY presentational (label + color for a small tag on the canvas node) —
 * it must never grow into logic that infers a badge client-side. `DECEASED`
 * is handled separately by PersonNode (it drives the whole card's visual
 * treatment, not a little tag), so it's excluded here.
 */
export interface BadgeMeta {
  readonly vi: string;
  readonly en: string;
  /** Mảng tô của huy hiệu. Đi qua biến CSS ⇒ tự đảo theo chế độ tối. */
  readonly color: string;
  /**
   * MỰC đè lên mảng tô ấy. Bắt buộc đi cùng {@link color}, không phải tuỳ chọn.
   *
   * <p>{@code <Tag color={...}>} của Ant Design ép chữ về TRẮNG. Trắng trên hổ phách
   * chỉ đạt 3,19:1 ở chế độ sáng và 2,07:1 ở chế độ tối — dưới ngưỡng 4,5:1, mà ba
   * huy hiệu quan trọng nhất của phả hệ (Đích tôn · Thừa tự · Kế tự) nằm đúng trên
   * mảng đó. Dùng {@code bgPage} làm mực: ở chế độ sáng nó là nền kem trên mảng trầm,
   * ở chế độ tối nó là mực tối trên mảng sáng — một khai báo, đúng ở cả hai chiều.</p>
   *
   * <p>Bỏ trống = giữ nguyên mặc định trắng của Ant Design. Chỉ hai huy hiệu Dâu / Rể
   * ở trạng thái đó, vì mảng tô của chúng còn là mã màu cứng đang chờ Hội đồng Tộc
   * biểu quyết — xem chú thích bên dưới.</p>
   */
  readonly ink?: string;
}

export const BADGE_META: Record<Exclude<PersonBadge, "DECEASED">, BadgeMeta> = {
  DICH_TON: { vi: "Đích tôn", en: "Eldest grandson", color: colorVars.accentText, ink: colorVars.bgPage },
  THUA_TU: { vi: "Thừa tự", en: "Designated heir", color: colorVars.accentText, ink: colorVars.bgPage },
  KE_TU: { vi: "Kế tự", en: "Adoptive heir", color: colorVars.accentText, ink: colorVars.bgPage },
  CON_NUOI: { vi: "Con nuôi", en: "Adopted", color: colorVars.secondary, ink: colorVars.bgPage },
  DAU: { vi: "Dâu", en: "Daughter-in-law", color: "#a3336b" },
  RE: { vi: "Rể", en: "Son-in-law", color: "#2f6fa8" },
  TUYET_TU: { vi: "Tuyệt tự", en: "No descendants", color: colorVars.textMuted, ink: colorVars.bgPage },
  TRUONG_CHI: { vi: "Trưởng chi", en: "Branch head", color: colorVars.primary, ink: colorVars.bgPage },
};

/** Badge type that actually has display metadata (everything but DECEASED). */
export type DisplayBadge = Exclude<PersonBadge, "DECEASED">;

export function displayBadges(badges: PersonBadge[] | undefined): DisplayBadge[] {
  return (badges ?? []).filter((b): b is DisplayBadge => b !== "DECEASED");
}
