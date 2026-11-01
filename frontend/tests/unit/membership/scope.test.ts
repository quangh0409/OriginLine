import { describe, expect, it } from "vitest";
import { phamViCap, tenDuongNhanh, trongPhamVi } from "@/components/membership/scope";

/**
 * Hình chiếu phía client của phạm vi `ltree` — thứ quyết định ô chọn người ở
 * màn phát lời mời / cấp lại mật khẩu ĐƯA RA những ai.
 *
 * Ca gốc (đo trên hệ thật 24/09): `truongchi` (Chi Bính) gõ "Nguyễn Thế Tài",
 * thấy hai dòng "Đời thứ 7 · Ngành Trưởng" y hệt nhau — một ở Chi Giáp — chọn
 * nhầm, nhận 403.
 */
describe("phamViCap", () => {
  it("Hội đồng, Admin và vai toàn họ không bị lọc", () => {
    expect(phamViCap({ role: "COUNCIL", clanWide: false, managedBranches: [] })).toBeNull();
    expect(phamViCap({ role: "ADMIN", clanWide: false, managedBranches: [] })).toBeNull();
    expect(phamViCap({ role: "BRANCH_HEAD", clanWide: true, managedBranches: [] })).toBeNull();
  });

  it("Trưởng chi bị giới hạn đúng các nhánh được giao", () => {
    expect(
      phamViCap({ role: "BRANCH_HEAD", clanWide: false, managedBranches: ["ho.chi_binh"] })
    ).toEqual(["ho.chi_binh"]);
  });
});

describe("trongPhamVi", () => {
  const binh = ["ho_nguyen_dinh.chi_binh"];

  it("người trong cây chi (kể cả ngành con) được giữ", () => {
    expect(trongPhamVi("ho_nguyen_dinh.chi_binh", binh)).toBe(true);
    expect(trongPhamVi("ho_nguyen_dinh.chi_binh.nganh_thu", binh)).toBe(true);
  });

  it("người chi khác bị bỏ — kể cả khi tên ngành trùng", () => {
    expect(trongPhamVi("ho_nguyen_dinh.chi_giap.nganh_truong", binh)).toBe(false);
  });

  it("so theo NHÃN ltree, không theo tiền tố chuỗi", () => {
    expect(trongPhamVi("ho_nguyen_dinh.chi_binh_moi", binh)).toBe(false);
  });

  it("toàn họ thì ai cũng trong phạm vi; người không rõ chi để máy chủ phán", () => {
    expect(trongPhamVi("ho_nguyen_dinh.chi_giap", null)).toBe(true);
    expect(trongPhamVi(undefined, binh)).toBe(true);
  });
});

describe("tenDuongNhanh", () => {
  const ten = new Map([
    ["ho_nguyen_dinh", "Họ Nguyễn Đình"],
    ["ho_nguyen_dinh.chi_binh", "Chi Bính"],
    ["ho_nguyen_dinh.chi_binh.nganh_thu", "Ngành Thứ"],
    ["ho_nguyen_dinh.chi_giap.nganh_truong", "Ngành Trưởng"],
    ["ho_nguyen_dinh.chi_giap", "Chi Giáp"],
  ]);

  it("in cả chi lẫn ngành, bỏ gốc dòng họ", () => {
    expect(tenDuongNhanh("ho_nguyen_dinh.chi_binh.nganh_thu", ten)).toBe("Chi Bính · Ngành Thứ");
    expect(tenDuongNhanh("ho_nguyen_dinh.chi_giap.nganh_truong", ten)).toBe(
      "Chi Giáp · Ngành Trưởng"
    );
  });

  it("không có đường dẫn thì trả null để màn rơi về tên lá", () => {
    expect(tenDuongNhanh(null, ten)).toBeNull();
    expect(tenDuongNhanh("khac.hoan_toan", ten)).toBeNull();
  });
});
