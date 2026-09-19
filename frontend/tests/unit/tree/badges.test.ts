import { describe, expect, it } from "vitest";
import { BADGE_META, displayBadges } from "@/lib/tree/badges";
import type { PersonBadge } from "@/types/api";

/**
 * Badges are backend-authored (contracts/README §7.6) — dâu/rể in particular
 * are derived from SPOUSE edges plus bloodline on the server and must never
 * be inferred here. These tests guard the display layer only.
 */
const ALL_BADGES: PersonBadge[] = [
  "DICH_TON",
  "THUA_TU",
  "KE_TU",
  "CON_NUOI",
  "DAU",
  "RE",
  "TUYET_TU",
  "TRUONG_CHI",
  "DECEASED",
];

describe("BADGE_META", () => {
  it("has display metadata for every badge except DECEASED", () => {
    // A badge arriving from the backend with no entry here would render as
    // `undefined.color` and crash PersonNode mid-canvas.
    const expected = ALL_BADGES.filter((b) => b !== "DECEASED").sort();
    expect(Object.keys(BADGE_META).sort()).toEqual(expected);
  });

  it("carries both a Vietnamese and an English label for every badge", () => {
    for (const [badge, meta] of Object.entries(BADGE_META)) {
      expect(meta.vi, `vi label for ${badge}`).toBeTruthy();
      expect(meta.en, `en label for ${badge}`).toBeTruthy();
    }
  });

  /**
   * Mảng tô của huy hiệu phải ĐI QUA biến CSS, không phải một mã màu đóng băng.
   *
   * <p>Ca này trước đây khẳng định ngược lại — mọi màu phải khớp `/^#[0-9a-fA-F]{6}$/` —
   * và chính lời khẳng định ấy là thứ ghim cả bảng huy hiệu vào chế độ SÁNG: một
   * chuỗi hex đã cố định vào lượt render, `prefers-color-scheme` không với tới.</p>
   *
   * <p>Hai ngoại lệ CÓ TÊN, và chỉ hai: Dâu và Rể. Mã màu của chúng đang chờ Hội đồng
   * Tộc biểu quyết (dâu/rể có được một sắc riêng ngoài bảng màu dòng họ hay không là
   * câu hỏi về NGHĨA, không phải về CSS), nên chúng cố ý còn là hex và cố ý còn làm
   * `no-hardcoded-colors.test.ts` đỏ. Liệt kê đích danh ở đây để khi Hội đồng quyết
   * xong, ca này đỏ và nhắc người sửa quay lại — thay vì im lặng cho qua mãi mãi.</p>
   */
  it("mảng tô đi qua biến CSS, trừ hai sắc đang chờ Hội đồng quyết", () => {
    const CHO_HOI_DONG = new Set(["DAU", "RE"]);
    for (const [badge, meta] of Object.entries(BADGE_META)) {
      if (CHO_HOI_DONG.has(badge)) {
        expect(meta.color, `${badge} vẫn đang chờ Hội đồng — xem no-hardcoded-colors`).toMatch(
          /^#[0-9a-fA-F]{6}$/
        );
        continue;
      }
      expect(
        meta.color,
        `${badge}: mã màu cứng ở đây KHÔNG đảo theo chế độ tối. Dùng colorVars.*`
      ).toMatch(/^var\(--color-[a-z-]+\)$/);
    }
  });

  /**
   * MẢNG TÔ VÀ MỰC ĐI THÀNH CẶP.
   *
   * <p>`<Tag color={…}>` của Ant Design ép chữ về TRẮNG. Trắng trên hổ phách chỉ đạt
   * 3,19:1 ở chế độ sáng và <b>2,07:1</b> ở chế độ tối — đo được trên phả đồ — trong
   * khi ba huy hiệu quan trọng nhất của phả hệ (Đích tôn · Thừa tự · Kế tự) nằm đúng
   * trên mảng đó. `token-contrast.test.ts` đã khẳng định bảng màu LUÔN cung cấp một
   * màu mực đọc được cho mọi mảng tô; ca này khẳng định rằng nơi vẽ THẬT SỰ dùng nó.</p>
   */
  it("mọi huy hiệu dùng biến CSS đều mang theo một màu mực", () => {
    const thieuMuc = Object.entries(BADGE_META)
      .filter(([, meta]) => meta.color.startsWith("var(") && !meta.ink)
      .map(([badge]) => badge);
    expect(
      thieuMuc,
      "thiếu `ink` thì Ant Design vẽ chữ TRẮNG lên mảng tô — 2,07:1 ở chế độ tối"
    ).toEqual([]);
  });

  it("keeps the Vietnamese domain wording rather than translating it away", () => {
    expect(BADGE_META.DICH_TON.vi).toBe("Đích tôn");
    expect(BADGE_META.CON_NUOI.vi).toBe("Con nuôi");
    expect(BADGE_META.DAU.vi).toBe("Dâu");
    expect(BADGE_META.RE.vi).toBe("Rể");
    expect(BADGE_META.TUYET_TU.vi).toBe("Tuyệt tự");
  });

  it("gives dâu and rể visually distinct colours", () => {
    expect(BADGE_META.DAU.color).not.toBe(BADGE_META.RE.color);
  });
});

describe("displayBadges", () => {
  it("drops DECEASED, which drives the whole card's treatment instead of a tag", () => {
    expect(displayBadges(["DECEASED", "DICH_TON"])).toEqual(["DICH_TON"]);
  });

  it("returns an empty list for a person with no badges at all", () => {
    expect(displayBadges(undefined)).toEqual([]);
    expect(displayBadges([])).toEqual([]);
  });

  it("preserves the backend's badge order", () => {
    expect(displayBadges(["RE", "DECEASED", "TRUONG_CHI", "CON_NUOI"])).toEqual([
      "RE",
      "TRUONG_CHI",
      "CON_NUOI",
    ]);
  });

  it("keeps every non-DECEASED badge when a person carries several", () => {
    // Polygamy/adoption/heirship can genuinely stack: an adopted son can also
    // be the designated heir of a branch.
    expect(displayBadges(["CON_NUOI", "KE_TU", "TRUONG_CHI"])).toHaveLength(3);
  });
});
