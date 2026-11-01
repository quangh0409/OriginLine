import { describe, expect, it } from "vitest";
import viMsg from "../../../messages/vi.json";

/**
 * Câu chữ màn `/nhap-lieu/mau` phải khớp tệp mẫu THẬT mà backend sinh ra
 * (`ImportTemplateGenerator`: Hướng dẫn · Nhân khẩu · Hôn phối · Ví dụ · Danh
 * mục-ẩn). Đối chiếu với tệp tải về ngày 24/09: màn cũ kể một trang "Đã có"
 * không tồn tại, và bảo xoá "dòng ví dụ trên trang Nhân khẩu" trong khi dòng mẫu
 * nằm ở trang Ví dụ riêng — lời khuyên ấy, làm theo, là xoá người thật đã mang
 * mã của chi.
 */
describe("màn mẫu Excel nói đúng về tệp", () => {
  const g = viMsg.dataImport.template;

  it("liệt kê đúng năm trang của tệp sinh ra", () => {
    for (const trang of ["Hướng dẫn", "Nhân khẩu", "Hôn phối", "Ví dụ", "Danh mục"]) {
      expect(g.guide.sheets.body).toContain(trang);
    }
    expect(g.guide.sheets.body).not.toContain("Đã có");
  });

  it("không bảo người dùng xoá dòng trên trang Nhân khẩu", () => {
    expect(g.example.title).not.toMatch(/^Xoá/);
    expect(g.example.body).toMatch(/[Tt]rang Ví dụ/);
    expect(g.example.body).not.toMatch(/Xoá hết/);
  });
});
