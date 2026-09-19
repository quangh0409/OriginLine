/**
 * Bộ tính tương phản WCAG 2.x — dùng chung cho tầng unit (Vitest) và tầng trình duyệt
 * (Playwright, phía Node sau khi đã đọc màu ra khỏi trang).
 *
 * <p>Vì sao phải tự viết thay vì gọi một thư viện: yêu cầu C-2.4 trong
 * {@code design/01-nguoi-dung/01-nguoi-dung-va-chuan-tiep-can.html} nói rõ bài kiểm phải
 * <b>dừng build</b> và phải tính <b>trực tiếp từ {@code colorTokens}</b>. Một phụ thuộc thêm chỉ để
 * làm bốn phép nhân là chi phí không cần thiết, và quan trọng hơn: công thức nằm ngay đây thì bài
 * tự kiểm ở {@code tests/unit/a11y/contrast-formula.test.ts} mới chứng minh được rằng con số dùng
 * để chấm cả bảng màu là con số đúng.</p>
 *
 * <p>Công thức (WCAG 2.1, mục "relative luminance" và "contrast ratio"): ngưỡng kênh 0,04045;
 * luỹ thừa 2,4; hệ số 0,2126 / 0,7152 / 0,0722; tỉ số {@code (L1 + 0,05) / (L2 + 0,05)}. Đây đúng
 * là bộ số mà tài liệu thiết kế đã dùng để tính bảng tỉ số của dự án, nên số của bài kiểm và số
 * của tài liệu so sánh được với nhau.</p>
 */

export interface Rgb {
  readonly r: number;
  readonly g: number;
  readonly b: number;
  /** 0–1. Mặc định 1 (đục hoàn toàn). */
  readonly a: number;
}

/** Ngưỡng WCAG 1.4.3 cho chữ thường. */
export const CONTRAST_TEXT = 4.5;
/** Ngưỡng WCAG 1.4.3 cho chữ lớn (≥24px, hoặc ≥18,66px và đậm). */
export const CONTRAST_LARGE_TEXT = 3;
/** Ngưỡng WCAG 1.4.11 cho viền/thành phần giao diện — kể cả vòng tiêu điểm bàn phím. */
export const CONTRAST_UI = 3;

const HEX = /^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i;
const RGB_FN = /^rgba?\(([^)]+)\)$/i;

/**
 * Đọc một mã màu CSS thành RGBA. Chấp nhận hex 3/4/6/8 ký tự và {@code rgb()} / {@code rgba()}
 * (kể cả cú pháp dấu cách của CSS Color 4, vì đó là dạng {@code getComputedStyle} trả về).
 *
 * <p>Trả về {@code null} thay vì ném lỗi cho những giá trị <b>không phải màu đặc</b> —
 * {@code transparent}, {@code currentColor}, gradient. Chỗ gọi phải tự quyết định: với nền thì
 * "trong suốt" nghĩa là phải nhìn lên lớp cha, không phải là một lỗi.</p>
 */
export function parseColor(input: string): Rgb | null {
  const value = input.trim().toLowerCase();
  if (value === "transparent") return { r: 0, g: 0, b: 0, a: 0 };
  if (value === "white") return { r: 255, g: 255, b: 255, a: 1 };
  if (value === "black") return { r: 0, g: 0, b: 0, a: 1 };

  if (HEX.test(value)) {
    const hex = value.slice(1);
    const wide = hex.length <= 4;
    const part = (i: number): number => {
      const chunk = wide ? hex[i]!.repeat(2) : hex.slice(i * 2, i * 2 + 2);
      return Number.parseInt(chunk, 16);
    };
    const alphaIndex = wide ? 3 : 3;
    const hasAlpha = hex.length === 4 || hex.length === 8;
    return {
      r: part(0),
      g: part(1),
      b: part(2),
      a: hasAlpha ? part(alphaIndex) / 255 : 1,
    };
  }

  const fn = RGB_FN.exec(value);
  if (fn) {
    const parts = fn[1]!
      .replace(/\//g, " ")
      .split(/[\s,]+/)
      .filter(Boolean)
      .map((p) => Number.parseFloat(p));
    const [r, g, b, a] = parts;
    if (r == null || g == null || b == null) return null;
    return { r, g, b, a: a == null ? 1 : a > 1 ? a / 100 : a };
  }

  return null;
}

/** Như {@link parseColor} nhưng ném lỗi — dùng cho hằng số trong test, nơi màu sai là lỗi của test. */
export function requireColor(input: string): Rgb {
  const parsed = parseColor(input);
  if (!parsed) throw new Error(`không đọc được mã màu: ${JSON.stringify(input)}`);
  return parsed;
}

/**
 * Chồng một màu có độ trong suốt lên một nền đục ("alpha compositing"), trả về màu đục nhìn thấy
 * được. Cần thật, không phải cho đẹp: viền tiêu điểm hay dùng {@code rgba(...)} hoặc lớp mờ của
 * Tailwind ({@code border-primary/50}), và chấm tương phản trên màu chưa chồng nền là chấm một
 * màu người dùng chưa bao giờ nhìn thấy.
 */
export function flattenOver(fg: Rgb, bg: Rgb): Rgb {
  if (fg.a >= 1) return fg;
  const mix = (f: number, b: number): number => f * fg.a + b * (1 - fg.a);
  return { r: mix(fg.r, bg.r), g: mix(fg.g, bg.g), b: mix(fg.b, bg.b), a: 1 };
}

/** Độ chói tương đối theo WCAG. */
export function relativeLuminance(color: Rgb): number {
  const channel = (raw: number): number => {
    const c = raw / 255;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return (
    0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b)
  );
}

/**
 * Tỉ số tương phản giữa hai màu. Màu có alpha < 1 được chồng lên màu còn lại trước khi tính —
 * nếu cả hai đều trong suốt thì không có nền nào để chồng, và hàm ném lỗi thay vì trả về một con
 * số vô nghĩa.
 */
export function contrastRatio(a: Rgb | string, b: Rgb | string): number {
  const fg0 = typeof a === "string" ? requireColor(a) : a;
  const bg0 = typeof b === "string" ? requireColor(b) : b;
  if (fg0.a < 1 && bg0.a < 1) {
    throw new Error("cả hai màu đều trong suốt — không xác định được tương phản");
  }
  const bg = bg0.a < 1 ? flattenOver(bg0, fg0) : bg0;
  const fg = fg0.a < 1 ? flattenOver(fg0, bg) : fg0;

  const l1 = relativeLuminance(fg);
  const l2 = relativeLuminance(bg);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

/** Làm tròn xuống 2 chữ số thập phân — làm tròn LÊN sẽ đẩy 4,497 thành "4,50" và cho qua oan. */
export function roundRatio(ratio: number): number {
  return Math.floor(ratio * 100) / 100;
}

/** Chuỗi để đọc trong thông báo lỗi: "2.95:1". */
export function formatRatio(ratio: number): string {
  return `${roundRatio(ratio).toFixed(2)}:1`;
}

/**
 * "Chữ lớn" theo WCAG 1.4.3: ≥18,66px và đậm (weight ≥ 700), hoặc ≥24px ở bất kỳ độ đậm nào.
 * Đây là ngưỡng tài liệu 00 §2.2 nhắc lại, và là lý do một tiêu đề 24px được phép ở 3:1.
 */
export function isLargeText(fontSizePx: number, fontWeight: number): boolean {
  return fontSizePx >= 24 || (fontSizePx >= 18.66 && fontWeight >= 700);
}

/** Ngưỡng áp cho một đoạn chữ cụ thể. */
export function textThreshold(fontSizePx: number, fontWeight: number): number {
  return isLargeText(fontSizePx, fontWeight) ? CONTRAST_LARGE_TEXT : CONTRAST_TEXT;
}

export interface ContrastPair {
  /** Nhãn đọc được, xuất hiện nguyên văn trong thông báo lỗi. */
  readonly label: string;
  readonly foreground: string;
  readonly background: string;
  readonly threshold: number;
}

export interface ContrastFailure extends ContrastPair {
  readonly ratio: number;
}

/**
 * Chấm một danh sách cặp màu, trả về <b>danh sách vi phạm</b> chứ không ném lỗi ở cặp đầu tiên.
 * Chủ ý: khi bảng màu đổi, người sửa cần thấy hết mọi chỗ hỏng trong một lần chạy, không phải
 * sửa một cặp rồi chạy lại để lộ ra cặp tiếp theo.
 */
export function findContrastFailures(pairs: readonly ContrastPair[]): ContrastFailure[] {
  const failures: ContrastFailure[] = [];
  for (const pair of pairs) {
    const ratio = contrastRatio(pair.foreground, pair.background);
    if (roundRatio(ratio) < pair.threshold) failures.push({ ...pair, ratio });
  }
  return failures;
}

/** Một dòng mô tả vi phạm, đủ để sửa mà không phải mở lại tài liệu. */
export function describeFailure(failure: ContrastFailure): string {
  return (
    `${failure.label}: ${failure.foreground} trên ${failure.background} = ` +
    `${formatRatio(failure.ratio)} (cần ≥ ${failure.threshold}:1)`
  );
}

export function describeFailures(failures: readonly ContrastFailure[]): string {
  return failures.map((f) => `  · ${describeFailure(f)}`).join("\n");
}
