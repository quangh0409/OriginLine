import { describe, expect, it } from "vitest";
import { colorTokens, darkColorTokens } from "@/styles/tokens";
import { tiSoTuongPhan } from "./wcag";

/**
 * Hàng rào tương phản cho bảng màu.
 *
 * Bài học của đợt sửa này: mười một cặp màu trượt chuẩn AA sống được rất lâu vì
 * không có gì ĐO chúng — tài liệu thiết kế có bảng số, nhưng bảng số trong tài
 * liệu không chạy khi ai đó sửa một hex trong tokens.ts. Tệp này là cái chạy.
 *
 * Ngưỡng dùng đúng theo VAI TRÒ của cặp, không dùng một ngưỡng chung:
 *   4,5:1 — chữ thường (WCAG 1.4.3 AA)
 *   3,0:1 — nét/viền là thứ DUY NHẤT nhận diện thành phần (WCAG 1.4.11)
 */
const CHU = 4.5;
const NET = 3;

describe("tương phản — chế độ sáng", () => {
  const T = colorTokens;

  describe("hổ phách làm CHỮ phải dùng accentText, không dùng accent", () => {
    const nenSang = [
      ["bgCard", T.bgCard],
      ["bgPage", T.bgPage],
      ["warningBg", T.warningBg],
      ["bgDeceased", T.bgDeceased],
      ["primaryLight", T.primaryLight],
    ] as const;

    it.each(nenSang)("accent #d97706 vẫn TRƯỢT trên %s (đây là lý do tách token)", (_ten, nen) => {
      expect(tiSoTuongPhan(T.accent, nen)).toBeLessThan(CHU);
    });

    it.each(nenSang)("accentText đạt ≥4,5:1 trên %s", (_ten, nen) => {
      expect(tiSoTuongPhan(T.accentText, nen)).toBeGreaterThanOrEqual(CHU);
    });

    it("chữ trắng trên nền accentText đạt AA (nút/badge nền đặc)", () => {
      expect(tiSoTuongPhan("#ffffff", T.accentText)).toBeGreaterThanOrEqual(CHU);
    });
  });

  describe("xanh 'còn sống' làm chữ phải dùng successText", () => {
    it.each([
      ["bgDeceased", T.bgDeceased],
      ["primaryLight", T.primaryLight],
      ["bgCard", T.bgCard],
    ] as const)("successText đạt ≥4,5:1 trên %s", (_ten, nen) => {
      expect(tiSoTuongPhan(T.successText, nen)).toBeGreaterThanOrEqual(CHU);
    });

    it("success gốc trượt trên giấy cũ — lý do tồn tại của successText", () => {
      expect(tiSoTuongPhan(T.success, T.bgDeceased)).toBeLessThan(CHU);
    });
  });

  describe("liên kết: di chuột phải làm rõ HƠN, không mờ đi", () => {
    it.each([
      ["bgPage", T.bgPage],
      ["bgCard", T.bgCard],
    ] as const)("colorLinkHover (primaryDark) ≥ colorLink (primary) trên %s", (_ten, nen) => {
      const nghi = tiSoTuongPhan(T.primary, nen);
      const diChuot = tiSoTuongPhan(T.primaryDark, nen);
      expect(nghi).toBeGreaterThanOrEqual(CHU);
      expect(diChuot).toBeGreaterThanOrEqual(CHU);
      // Đây là phần bắt được lỗi cũ: accent cho 2,95 trên nền kem, tức TỤT.
      expect(diChuot).toBeGreaterThan(nghi);
    });
  });

  describe("vòng tiêu điểm hai lớp: luôn có MỘT lớp đạt ≥3:1", () => {
    it.each([
      ["nền kem", T.bgPage],
      ["nền thẻ", T.bgCard],
      ["giấy cũ", T.bgDeceased],
      ["nút đỏ trầm", T.primary],
      ["nút hổ phách", T.accent],
      ["hồng nhạt", T.primaryLight],
    ] as const)("trên %s", (_ten, nen) => {
      const loi = tiSoTuongPhan(T.focusRing, nen);
      const quang = tiSoTuongPhan(T.focusHalo, nen);
      expect(Math.max(loi, quang)).toBeGreaterThanOrEqual(NET);
    });

    it("hổ phách đơn sắc KHÔNG làm nổi việc này (lý do đổi)", () => {
      for (const nen of [T.bgPage, T.bgDeceased, T.primary]) {
        expect(tiSoTuongPhan(T.accent, nen)).toBeLessThan(NET);
      }
    });

    it("hai lớp phải trái sắc nhau, nếu không thì vô nghĩa", () => {
      expect(tiSoTuongPhan(T.focusRing, T.focusHalo)).toBeGreaterThanOrEqual(7);
    });
  });

  describe("viền ô nhập — WCAG 1.4.11", () => {
    it.each([
      ["bgCard", T.bgCard],
      ["bgPage", T.bgPage],
      ["bgDeceased", T.bgDeceased],
    ] as const)("borderInput đạt ≥3:1 trên %s", (_ten, nen) => {
      expect(tiSoTuongPhan(T.borderInput, nen)).toBeGreaterThanOrEqual(NET);
    });

    it("border thường vẫn 1,32:1 trên nền trắng — cố ý, nó chỉ là viền THẺ", () => {
      expect(tiSoTuongPhan(T.border, T.bgCard)).toBeLessThan(2);
    });
  });

  describe("chữ thân bài và phụ chú trên cả ba nền", () => {
    it.each([
      ["bgPage", T.bgPage],
      ["bgCard", T.bgCard],
      ["bgDeceased", T.bgDeceased],
    ] as const)("textMain và textMuted đạt AA trên %s", (_ten, nen) => {
      expect(tiSoTuongPhan(T.textMain, nen)).toBeGreaterThanOrEqual(CHU);
      expect(tiSoTuongPhan(T.textMuted, nen)).toBeGreaterThanOrEqual(CHU);
    });
  });

  /**
   * NGOẠI LỆ CỐ Ý — không sửa.
   *
   * Nền giấy cũ #f2ede2 cạnh nền thẻ trắng chỉ 1,17:1. Giữ nguyên, vì:
   * (1) WCAG không đặt ngưỡng cho cặp NỀN–NỀN; 1.4.11 áp cho thành phần giao
   *     diện và đồ hoạ mang thông tin. Thông tin "đã khuất" ở đây do vạch đỏ
   *     trầm (7,18:1), thẻ chữ "đã khuất" và văn bản cho trình đọc màn hình
   *     mang, không phải do nước nền.
   * (2) Muốn 3:1 so với nền trắng thì giấy cũ phải tụt xuống quãng #9a948a —
   *     tối đi rõ rệt. Một khối nội dung tối hơn hẳn phần còn lại của trang
   *     CHÍNH LÀ định nghĩa thị giác của "bị vô hiệu hoá", đúng thứ mà
   *     00 §3 cấm: nền này phải đọc như SỰ TÔN KÍNH.
   * Sửa theo hướng đó là đạt được con số và mất đúng thứ con số sinh ra để bảo vệ.
   */
  it("giấy cũ so với nền thẻ giữ ở 1,17:1 — ngoại lệ cố ý", () => {
    expect(tiSoTuongPhan(T.bgDeceased, T.bgCard)).toBeCloseTo(1.17, 2);
    // Bù bằng dấu hiệu dư thừa: vạch đỏ trầm trên đỉnh thẻ phải thật sự nhìn thấy.
    expect(tiSoTuongPhan(T.primary, T.bgDeceased)).toBeGreaterThanOrEqual(NET);
    // ...và tên người trên giấy cũ vẫn đạt cả AAA.
    expect(tiSoTuongPhan(T.textMain, T.bgDeceased)).toBeGreaterThanOrEqual(7);
  });
});

describe("tương phản — chế độ tối", () => {
  const D = darkColorTokens;

  it.each([
    ["textMain / bgPage", D.textMain, D.bgPage],
    ["textMain / bgCard", D.textMain, D.bgCard],
    ["textMain / bgDeceased", D.textMain, D.bgDeceased],
    ["textMuted / bgCard", D.textMuted, D.bgCard],
    ["textMuted / bgDeceased", D.textMuted, D.bgDeceased],
    ["primary / bgCard", D.primary, D.bgCard],
    ["primary / bgPage", D.primary, D.bgPage],
    ["primaryDark(hover) / bgPage", D.primaryDark, D.bgPage],
    ["accentText / bgCard", D.accentText, D.bgCard],
    ["accentText / bgDeceased", D.accentText, D.bgDeceased],
    ["accentText / warningBg", D.accentText, D.warningBg],
    ["successText / bgDeceased", D.successText, D.bgDeceased],
    ["danger / bgCard", D.danger, D.bgCard],
    ["danger / dangerBg", D.danger, D.dangerBg],
    ["secondary / bgCard", D.secondary, D.bgCard],
  ] as const)("%s đạt AA", (_ten, a, b) => {
    expect(tiSoTuongPhan(a, b)).toBeGreaterThanOrEqual(CHU);
  });

  it.each([
    ["bgCard", D.bgCard],
    ["bgPage", D.bgPage],
    ["bgDeceased", D.bgDeceased],
  ] as const)("borderInput đạt ≥3:1 trên %s", (_ten, nen) => {
    expect(tiSoTuongPhan(D.borderInput, nen)).toBeGreaterThanOrEqual(NET);
  });

  it("vòng tiêu điểm đảo lớp vẫn luôn có một lớp ≥3:1", () => {
    for (const nen of [D.bgPage, D.bgCard, D.bgDeceased, D.primary, D.accent]) {
      const loi = tiSoTuongPhan(D.focusRing, nen);
      const quang = tiSoTuongPhan(D.focusHalo, nen);
      expect(Math.max(loi, quang)).toBeGreaterThanOrEqual(NET);
    }
  });

  it("di chuột vẫn làm liên kết rõ HƠN ở nền tối", () => {
    expect(tiSoTuongPhan(D.primaryDark, D.bgPage)).toBeGreaterThan(
      tiSoTuongPhan(D.primary, D.bgPage),
    );
  });

  it("hổ phách hết trượt ở nền tối — nên accentText ánh xạ thẳng về accent", () => {
    expect(D.accentText).toBe(D.accent);
    expect(tiSoTuongPhan(D.accent, D.bgCard)).toBeGreaterThanOrEqual(CHU);
  });
});
