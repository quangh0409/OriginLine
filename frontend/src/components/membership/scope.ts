import type { MeView } from "@/lib/api/change-requests";

/**
 * Phạm vi mà người đang đăng nhập được PHÁT/CẤP cho — hình chiếu phía client
 * của phép kiểm `ltree` ở backend (`InvitationService.issue`,
 * `PasswordResetGrantService.issue`). Không phải quyết định phân quyền: máy
 * chủ vẫn kiểm lại và trả `403 BRANCH_SCOPE_VIOLATION`. Hàm này chỉ để màn
 * hình KHÔNG ĐƯA RA những lựa chọn chắc chắn bị từ chối.
 *
 * `null` nghĩa là toàn họ (Hội đồng, Admin, hoặc `clanWide`).
 */
export function phamViCap(me: Pick<MeView, "role" | "clanWide" | "managedBranches">): string[] | null {
  if (me.role === "ADMIN" || me.role === "COUNCIL" || me.clanWide) return null;
  return me.managedBranches;
}

/**
 * `path` có nằm trong một trong các nhánh `phamVi` không — so theo NHÃN
 * `ltree`, không theo tiền tố chuỗi: `chi_binh` không được khớp nhầm
 * `chi_binh_moi`.
 *
 * Người không rõ chi (`path` rỗng) được GIỮ LẠI: ẩn họ đi là ẩn một người có
 * thật khỏi người duy nhất cấp được cho họ; để máy chủ phán thì tệ nhất cũng
 * chỉ là một câu từ chối nói rõ lý do.
 */
export function trongPhamVi(path: string | null | undefined, phamVi: string[] | null): boolean {
  if (phamVi === null) return true;
  if (!path) return true;
  return phamVi.some((goc) => path === goc || path.startsWith(goc + "."));
}

/**
 * Tên đường nhánh để HIỂN THỊ, bỏ gốc dòng họ: `Chi Bính · Ngành Thứ`.
 *
 * Vì sao cần: tên lá của chi/ngành trùng nhau khắp họ — bốn chi đều có một
 * "Ngành Trưởng". Ô gợi ý cũ chỉ in tên lá, nên Trưởng chi Chi Bính gõ
 * "Nguyễn Thế Tài" thấy hai dòng "Đời thứ 7 · Ngành Trưởng" giống hệt nhau
 * (một ở Chi Giáp) và không có cách nào biết dòng nào là người của mình — đo
 * trên hệ thật 24/09, lượt cấp lại mật khẩu đầu tiên vì thế trúng nhầm người
 * chi khác và nhận 403.
 */
export function tenDuongNhanh(
  path: string | null | undefined,
  tenTheoPath: ReadonlyMap<string, string>
): string | null {
  if (!path) return null;
  const nhan = path.split(".");
  const ten: string[] = [];
  // i = 1: bỏ nút gốc (cả dòng họ) — ai trong họ cũng thuộc nó, in ra chỉ tốn chỗ.
  for (let i = 1; i < nhan.length; i++) {
    const t = tenTheoPath.get(nhan.slice(0, i + 1).join("."));
    if (t) ten.push(t);
  }
  return ten.length > 0 ? ten.join(" · ") : null;
}
