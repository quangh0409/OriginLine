import { describe, expect, it } from "vitest";
import { branchQualifiedName } from "@/hooks/use-branches";
import type { BranchOption } from "@/lib/graphql/queries";

/**
 * Nhãn chi/ngành đầy đủ theo dòng — đo trên hệ thật 24/09 (NT-TV-18): bốn chi đều có "Ngành Út",
 * nên ô lọc theo tên trần gõ "Bính" chỉ còn "Chi Bính", và chọn xong thì không biết Ngành Út nào.
 */
const b = (path: string, name: string): BranchOption =>
  ({ id: path, path, name, memberCount: 0 }) as unknown as BranchOption;

const HO = b("ho", "Họ Nguyễn Đình");
const CHI_AT = b("ho.chi_at", "Chi Ất");
const AT_UT = b("ho.chi_at.nganh_ut", "Ngành Út");
const CHI_BINH = b("ho.chi_binh", "Chi Bính");
const BINH_UT = b("ho.chi_binh.nganh_ut", "Ngành Út");
const ALL = [HO, CHI_AT, AT_UT, CHI_BINH, BINH_UT];

describe("branchQualifiedName", () => {
  it("ngành mang tên chi cha — hai 'Ngành Út' khác nhau có hai nhãn khác nhau", () => {
    expect(branchQualifiedName(AT_UT, ALL)).toBe("Chi Ất › Ngành Út");
    expect(branchQualifiedName(BINH_UT, ALL)).toBe("Chi Bính › Ngành Út");
  });

  it("gõ 'Bính' khớp cả chi lẫn các ngành con của nó", () => {
    const hits = ALL.filter((x) => branchQualifiedName(x, ALL).includes("Bính"));
    expect(hits.map((x) => x.path)).toEqual(["ho.chi_binh", "ho.chi_binh.nganh_ut"]);
  });

  it("chi và gốc dòng họ giữ nguyên tên — không kéo tên cả họ vào mọi nhãn", () => {
    expect(branchQualifiedName(CHI_AT, ALL)).toBe("Chi Ất");
    expect(branchQualifiedName(HO, ALL)).toBe("Họ Nguyễn Đình");
  });

  it("chi cha không có trong danh sách (bị rootPath cắt) ⇒ bỏ qua, không in đường dẫn ltree", () => {
    const only = [BINH_UT];
    expect(branchQualifiedName(BINH_UT, only)).toBe("Ngành Út");
  });
});
