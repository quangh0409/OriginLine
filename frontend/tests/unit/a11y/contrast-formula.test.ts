import { describe, expect, it } from "vitest";
import {
  CONTRAST_TEXT,
  CONTRAST_UI,
  contrastRatio,
  describeFailures,
  findContrastFailures,
  flattenOver,
  formatRatio,
  isLargeText,
  parseColor,
  relativeLuminance,
  requireColor,
  roundRatio,
  textThreshold,
} from "../../helpers/contrast";

/**
 * TỰ KIỂM BỘ ĐO trước khi đem nó đi chấm bảng màu.
 *
 * <p>Cả bộ kiểm tiếp cận ở thư mục này đứng trên đúng một hàm: {@code contrastRatio}. Nếu hàm ấy
 * sai lệch, mọi bài kiểm còn lại vẫn xanh và vẫn vô giá trị — đó chính là kiểu "xanh giả" tệ nhất,
 * vì nó tạo ra niềm tin. Nên trước hết phải chứng minh công thức đúng bằng những cặp có đáp số
 * công bố sẵn, rồi chứng minh nó biết KÊU bằng những cặp đã biết là hỏng.</p>
 *
 * <p>Số đối chiếu lấy từ hai nguồn độc lập với mã nguồn này: định nghĩa của WCAG 2.1 (đen/trắng =
 * 21:1, hai màu giống nhau = 1:1) và bảng tỉ số đã tính sẵn trong
 * {@code design/01-nguoi-dung/01-nguoi-dung-va-chuan-tiep-can.html} §4.2.</p>
 */
describe("bộ tính tương phản WCAG", () => {
  it("cho 21:1 với đen trên trắng và 1:1 với hai màu trùng nhau", () => {
    expect(roundRatio(contrastRatio("#000000", "#ffffff"))).toBe(21);
    expect(roundRatio(contrastRatio("#ffffff", "#000000"))).toBe(21);
    expect(roundRatio(contrastRatio("#8c2d19", "#8c2d19"))).toBe(1);
  });

  it("không phụ thuộc thứ tự hai màu", () => {
    const forward = contrastRatio("#d97706", "#f8f6f2");
    const backward = contrastRatio("#f8f6f2", "#d97706");
    expect(roundRatio(forward)).toBe(roundRatio(backward));
  });

  it("cho độ chói 0 với đen và 1 với trắng", () => {
    expect(relativeLuminance(requireColor("#000000"))).toBe(0);
    expect(relativeLuminance(requireColor("#ffffff"))).toBeCloseTo(1, 10);
  });

  /**
   * Đối chiếu với bảng số của tài liệu thiết kế. Sai số cho phép 0,02 — tài liệu ghi hai chữ số
   * thập phân, không phải vì công thức khác.
   */
  it.each([
    ["mực trên nền thẻ trắng", "#2b2825", "#ffffff", 14.66],
    ["mực trên nền kem", "#2b2825", "#f8f6f2", 13.58],
    ["mực trên nền giấy cũ", "#2b2825", "#f2ede2", 12.55],
    ["lam thông tin trên nền kem", "#2c3e50", "#f8f6f2", 10.18],
    ["đỏ trầm trên nền thẻ trắng", "#8c2d19", "#ffffff", 8.38],
    ["đỏ trầm trên nền kem", "#8c2d19", "#f8f6f2", 7.77],
    ["mực nhạt trên nền thẻ trắng", "#5e564d", "#ffffff", 7.21],
    ["mực trên hổ phách", "#2b2825", "#d97706", 4.6],
    ["xanh sống trên nền kem", "#15803d", "#f8f6f2", 4.65],
    ["xanh sống trên nền xanh nhạt", "#15803d", "#eaf6ee", 4.52],
    // Các cặp tài liệu ghi là HỎNG — bộ đo phải tái lập đúng con số hỏng ấy.
    ["hổ phách làm chữ trên nền kem", "#d97706", "#f8f6f2", 2.95],
    ["hổ phách trên nền vàng nhạt", "#d97706", "#fdf6e7", 2.96],
    ["trắng trên nền hổ phách", "#ffffff", "#d97706", 3.19],
    ["viền ô nhập trên nền thẻ", "#e6dfd5", "#ffffff", 1.32],
    ["viền đậm trên nền kem", "#cdbeaa", "#f8f6f2", 1.69],
    ["giấy cũ cạnh nền trắng", "#f2ede2", "#ffffff", 1.17],
    // Chế độ tối.
    ["chữ chính chế độ tối", "#efe8df", "#1a1614", 14.78],
    ["hổ phách chế độ tối", "#e9a83f", "#1a1614", 8.67],
    ["đỏ trầm chế độ tối", "#e08a72", "#1a1614", 6.89],
    ["mực nhạt chế độ tối", "#a99c8e", "#1a1614", 6.7],
  ])("khớp bảng tỉ số của tài liệu thiết kế — %s", (_label, fg, bg, expected) => {
    expect(contrastRatio(fg, bg)).toBeCloseTo(expected, 1);
  });

  it("đọc được hex 3, 4, 6, 8 ký tự và cả rgb()/rgba() mà trình duyệt trả về", () => {
    expect(parseColor("#fff")).toEqual({ r: 255, g: 255, b: 255, a: 1 });
    expect(parseColor("#000f")).toEqual({ r: 0, g: 0, b: 0, a: 1 });
    expect(parseColor("#8c2d19")).toEqual({ r: 140, g: 45, b: 25, a: 1 });
    expect(parseColor("#8c2d1980")).toEqual({ r: 140, g: 45, b: 25, a: 128 / 255 });
    expect(parseColor("rgb(43, 40, 37)")).toEqual({ r: 43, g: 40, b: 37, a: 1 });
    expect(parseColor("rgba(43, 40, 37, 0.5)")).toEqual({ r: 43, g: 40, b: 37, a: 0.5 });
    expect(parseColor("rgb(43 40 37 / 0.5)")).toEqual({ r: 43, g: 40, b: 37, a: 0.5 });
  });

  it("trả về null cho thứ không phải màu đặc, thay vì đoán bừa", () => {
    expect(parseColor("currentColor")).toBeNull();
    expect(parseColor("linear-gradient(#fff, #000)")).toBeNull();
    expect(parseColor("")).toBeNull();
    // `transparent` là màu hợp lệ có alpha 0 — nó có nghĩa "nhìn lên lớp cha", không phải "hỏng".
    expect(parseColor("transparent")).toEqual({ r: 0, g: 0, b: 0, a: 0 });
  });

  /**
   * Vòng tiêu điểm và viền hay được vẽ bằng màu có alpha. Chấm tương phản trên màu CHƯA chồng nền
   * là chấm một màu người dùng chưa bao giờ nhìn thấy — và nó luôn cho con số đẹp hơn sự thật.
   */
  it("chồng màu trong suốt lên nền trước khi tính", () => {
    const halfBlackOnWhite = flattenOver(requireColor("#00000080"), requireColor("#ffffff"));
    // alpha 0x80 = 128/255 = 0,50196 → 255 × (1 − 0,50196) ≈ 127, không phải 128 chẵn.
    expect(Math.round(halfBlackOnWhite.r)).toBe(127);
    // Đen 50% trên trắng nhìn ra màu xám, tương phản với nền trắng chỉ còn ~3,9 — không phải 21.
    const ratio = contrastRatio("#00000080", "#ffffff");
    expect(ratio).toBeLessThan(5);
    expect(ratio).toBeGreaterThan(3);
  });

  it("phân loại chữ lớn đúng theo ngưỡng 18,66px đậm / 24px", () => {
    expect(isLargeText(24, 400)).toBe(true);
    expect(isLargeText(18.66, 700)).toBe(true);
    expect(isLargeText(18.66, 600)).toBe(false);
    expect(isLargeText(18, 700)).toBe(false);
    expect(textThreshold(16, 400)).toBe(CONTRAST_TEXT);
    expect(textThreshold(30, 400)).toBe(3);
  });

  it("làm tròn XUỐNG, để 4,497 không được cho qua thành 4,50", () => {
    expect(roundRatio(4.4999)).toBe(4.49);
    expect(formatRatio(4.4999)).toBe("4.49:1");
    expect(roundRatio(21)).toBe(21);
  });

  /** ---- Phép kiểm ngược: bộ đo phải KÊU ---- */

  describe("chống xanh giả", () => {
    it("bắt được hổ phách #d97706 làm chữ ở chế độ sáng (2,95 < 4,5)", () => {
      const failures = findContrastFailures([
        {
          label: "hổ phách làm chữ cảnh báo",
          foreground: "#d97706",
          background: "#f8f6f2",
          threshold: CONTRAST_TEXT,
        },
      ]);
      expect(failures).toHaveLength(1);
      expect(roundRatio(failures[0]!.ratio)).toBe(2.95);
      expect(describeFailures(failures)).toContain("2.95:1");
    });

    it("bắt được hổ phách làm vòng tiêu điểm (2,95 < 3,0 — ngưỡng thành phần giao diện)", () => {
      const failures = findContrastFailures([
        {
          label: "vòng tiêu điểm hổ phách",
          foreground: "#d97706",
          background: "#f8f6f2",
          threshold: CONTRAST_UI,
        },
      ]);
      expect(failures).toHaveLength(1);
    });

    it("bắt được cặp 'đạt cực sát' khi mã màu bị chỉnh một nấc", () => {
      // #15803d trên #eaf6ee đang là 4,52 — biên độ 0,02. Đẩy xanh sáng lên một nấc là rơi.
      const before = findContrastFailures([
        { label: "chip xong", foreground: "#15803d", background: "#eaf6ee", threshold: CONTRAST_TEXT },
      ]);
      expect(before).toEqual([]);
      const after = findContrastFailures([
        { label: "chip xong", foreground: "#188a43", background: "#eaf6ee", threshold: CONTRAST_TEXT },
      ]);
      expect(after).toHaveLength(1);
    });

    it("không im lặng khi cả hai màu đều trong suốt", () => {
      expect(() => contrastRatio("#00000000", "#ffffff00")).toThrow(/trong suốt/);
    });

    it("không im lặng khi mã màu không đọc được", () => {
      expect(() => requireColor("khong-phai-mau")).toThrow(/không đọc được mã màu/);
    });
  });
});
