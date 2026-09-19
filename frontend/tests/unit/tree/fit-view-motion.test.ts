import { afterEach, describe, expect, it, vi } from "vitest";
import {
  INITIAL_FIT_VIEW_OPTIONS,
  WHOLE_TREE_FIT_VIEW_OPTIONS,
  prefersReducedMotion,
  withMotionPreference,
} from "@/lib/tree/fit-view";

/**
 * Canh khung phả đồ phải nghe theo `prefers-reduced-motion`.
 *
 * <p>Hai phép canh khung của phả đồ chạy bằng JAVASCRIPT — React Flow tự nội suy ma trận máy quay
 * qua tham số {@code duration}, không qua CSS transition. Nên bản vá `prefers-reduced-motion` ở
 * tầng biểu định kiểu (globals.css) cắt sạch mọi chuyển tiếp CSS mà vẫn để nguyên cả mặt phẳng phả
 * đồ trượt và phóng trong 300–400ms. Đó là hoạt ảnh nặng nhất còn lại trong sản phẩm, và với người
 * rối loạn tiền đình thì nó gây chóng mặt và buồn nôn thật.</p>
 *
 * <p>e2e/accessibility.spec.ts (C-6.1) đo HÀNH VI trên trình duyệt thật — bấm "Thu toàn cây" rồi
 * xem máy quay có đang bay không. Tệp này ghim phần quyết định: hỏi ai, hỏi lúc nào, và trả về gì.</p>
 */

const originalMatchMedia = window.matchMedia;
const originalGetComputedStyle = window.getComputedStyle;

function pinEnvironment({
  reduce,
  fitDuration,
}: {
  reduce: boolean;
  fitDuration: string;
}): void {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: reduce && query.includes("prefers-reduced-motion"),
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })) as unknown as typeof window.matchMedia;

  window.getComputedStyle = vi.fn().mockReturnValue({
    getPropertyValue: (name: string) => (name === "--thoi-luong-canh-khung" ? fitDuration : ""),
  }) as unknown as typeof window.getComputedStyle;
}

afterEach(() => {
  window.matchMedia = originalMatchMedia;
  window.getComputedStyle = originalGetComputedStyle;
  vi.restoreAllMocks();
});

describe("prefersReducedMotion", () => {
  it("nghe theo hệ điều hành", () => {
    pinEnvironment({ reduce: true, fitDuration: "0ms" });
    expect(prefersReducedMotion()).toBe(true);
  });

  it("không tự ý tắt hoạt ảnh khi không ai xin", () => {
    pinEnvironment({ reduce: false, fitDuration: "400ms" });
    expect(prefersReducedMotion()).toBe(false);
  });

  it("cũng nghe biến CSS bị ép về 0, kể cả khi hệ điều hành không nói gì", () => {
    // Đây là cái van thứ hai: nếu sau này có ai tắt hoạt ảnh vì lý do khác (máy yếu, chế độ trình
    // chiếu ngày giỗ Tổ) thì chỉ cần ép `--thoi-luong-canh-khung` về 0, không phải sửa TypeScript.
    pinEnvironment({ reduce: false, fitDuration: "0ms" });
    expect(prefersReducedMotion()).toBe(true);
  });

  it("đọc được cả đơn vị giây", () => {
    pinEnvironment({ reduce: false, fitDuration: "0s" });
    expect(prefersReducedMotion()).toBe(true);
    pinEnvironment({ reduce: false, fitDuration: "0.4s" });
    expect(prefersReducedMotion()).toBe(false);
  });

  it("biến CSS chưa khai thì KHÔNG suy ra là muốn giảm chuyển động", () => {
    // "" đọc ra 0 nếu ép kiểu ẩu — và thế là tắt hoạt ảnh cho tất cả mọi người.
    pinEnvironment({ reduce: false, fitDuration: "" });
    expect(prefersReducedMotion()).toBe(false);
  });
});

describe("withMotionPreference", () => {
  it("ép cả hai phép canh khung về 0ms khi người dùng xin giảm chuyển động", () => {
    pinEnvironment({ reduce: true, fitDuration: "0ms" });

    expect(withMotionPreference(INITIAL_FIT_VIEW_OPTIONS).duration).toBe(0);
    expect(withMotionPreference(WHOLE_TREE_FIT_VIEW_OPTIONS).duration).toBe(0);
  });

  it("giữ nguyên mọi tham số khác — sàn phóng KHÔNG được đụng tới", () => {
    pinEnvironment({ reduce: true, fitDuration: "0ms" });

    const initial = withMotionPreference(INITIAL_FIT_VIEW_OPTIONS);
    expect(initial.minZoom).toBe(INITIAL_FIT_VIEW_OPTIONS.minZoom);
    expect(initial.padding).toBe(INITIAL_FIT_VIEW_OPTIONS.padding);

    const whole = withMotionPreference(WHOLE_TREE_FIT_VIEW_OPTIONS);
    expect(whole.minZoom).toBe(WHOLE_TREE_FIT_VIEW_OPTIONS.minZoom);
    expect(whole.padding).toBe(WHOLE_TREE_FIT_VIEW_OPTIONS.padding);
  });

  it("trả về ĐÚNG đối tượng cũ khi không giảm chuyển động", () => {
    // Hai hằng số này đi vào useCallback/useEffect; sinh tham chiếu mới mỗi lần dựng lại là dựng
    // lại cả canvas một cách vô cớ.
    pinEnvironment({ reduce: false, fitDuration: "400ms" });

    expect(withMotionPreference(INITIAL_FIT_VIEW_OPTIONS)).toBe(INITIAL_FIT_VIEW_OPTIONS);
    expect(withMotionPreference(WHOLE_TREE_FIT_VIEW_OPTIONS)).toBe(WHOLE_TREE_FIT_VIEW_OPTIONS);
  });
});
