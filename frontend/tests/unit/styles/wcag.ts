/**
 * WCAG 2.1 relative luminance + contrast ratio, hiện thực thẳng từ lời văn của
 * tiêu chuẩn (§ "relative luminance", § "contrast ratio").
 *
 * Cố ý KHÔNG dùng thư viện: cả đợt sửa này xoay quanh những con số tương phản,
 * và một tệp 15 dòng đọc được bằng mắt thì kiểm chứng được, còn một phụ thuộc
 * ngoài thì phải tin.
 */
function kenhTuyenTinh(c: number): number {
  const s = c / 255;
  return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
}

export function doSangTuongDoi(hex: string): number {
  const h = hex.replace("#", "");
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as [
    number,
    number,
    number,
  ];
  return (
    0.2126 * kenhTuyenTinh(r) +
    0.7152 * kenhTuyenTinh(g) +
    0.0722 * kenhTuyenTinh(b)
  );
}

/** Tỉ số tương phản giữa hai màu, luôn ≥ 1. Làm tròn 2 chữ số như tài liệu 02. */
export function tiSoTuongPhan(a: string, b: string): number {
  const la = doSangTuongDoi(a);
  const lb = doSangTuongDoi(b);
  const r = (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  return Math.round(r * 100) / 100;
}
