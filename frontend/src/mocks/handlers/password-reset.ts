import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { canWriteInBranch, identityOf } from "@/mocks/identity";
import { resolvePersonMock } from "@/mocks/person-detail";
import { resolveMockRole } from "./role";
import type { Problem, ProblemCode } from "@/types/api";
import type { IssuedPasswordResetDto, PasswordResetGrantDto } from "@/lib/api/password-reset";

/**
 * MSW cho **cấp lại liên kết đặt mật khẩu** — nhóm `password-resets` của
 * `contracts/openapi.yaml`.
 *
 * <h2>Bộ giả lập này phải khó bằng API thật, không dễ hơn</h2>
 * Dự án này đã bị cắn năm lần vì một mock dễ chiều màn hình hơn máy chủ thật.
 * Bốn phép chặn dưới đây chạy trên CÙNG dữ liệu danh tính/đồ thị mà mọi màn
 * khác đang dùng (`identityOf`, `canWriteInBranch`, `resolvePersonMock`) —
 * không một cờ fixture riêng nào bịa ra cho "màn này dễ xanh".
 *
 * <h2>Mô hình "ai có tài khoản" của bộ giả lập</h2>
 * Trong thế giới thật chỉ có hai nhân khẩu có `app_user` khớp với hai vai dev
 * (`p-102` thành viên, `p-103` Trưởng chi) — cả hai đều KHÔNG lọt vào đồ thị
 * sinh tự động (`build-graph.ts`), nên không chọn được qua ô tra. Nhóm mock
 * này vì thế thêm hai nhân khẩu "có tài khoản" tìm được qua `/persons/search`:
 *
 *  - `p-100` (Nguyễn Văn An, `root.chi_nhat`) — có địa chỉ thư trong
 *    `data.ts` → ca `PASSWORD_RESET_NOT_ALLOWED` (đã có tài khoản, dùng "Quên
 *    mật khẩu" được);
 *  - `p-101` (Nguyễn Thị Bé, `root.chi_nhat`) — không có địa chỉ thư trong
 *    `data.ts` → ca cấp lại THÀNH CÔNG.
 *
 * Mọi nhân khẩu SỐNG khác (toàn bộ đồ thị sinh tự động, luôn có `email: null`
 * — xem `synthesizePersonDto`) được coi là CHƯA có tài khoản: đây là hình
 * dạng thật của một dòng họ 1.500 người, nơi tuyệt đại đa số chưa từng đăng
 * ký. Nhánh phạm vi chi (`BRANCH_SCOPE_VIOLATION`) và tự cấp cho mình
 * (`SELF_PASSWORD_RESET_FORBIDDEN`) được kiểm TRƯỚC bước "có tài khoản
 * không", đúng thứ tự của `PasswordResetGrantService.issue()`.
 */

function problem(
  status: number,
  code: ProblemCode,
  title: string,
  instance: string,
  detail?: string
): HttpResponse<Problem> {
  const body: Problem = {
    type: "about:blank",
    title,
    status,
    code,
    instance,
    ...(detail !== undefined ? { detail } : {}),
  };
  return HttpResponse.json(body, { status });
}

/** Hai nhân khẩu "có tài khoản" — xem javadoc đầu tệp. */
const CO_TAI_KHOAN_CO_EMAIL = new Set(["p-100"]);
const CO_TAI_KHOAN_KHONG_EMAIL = new Set(["p-101"]);

let daCap: PasswordResetGrantDto | null = null;
let dem = 0;

/** Đưa bộ giả lập về trạng thái đầu. Dùng giữa các ca kiểm. */
export function resetPasswordResetMockState(): void {
  daCap = null;
  dem = 0;
}

export const passwordResetHandlers = [
  http.post(`${API_BASE_URL}/api/v1/password-resets`, async ({ request }) => {
    const instance = "/api/v1/password-resets";
    const role = resolveMockRole(request);
    const identity = identityOf(role);

    if (!identity.appUserId) {
      return problem(401, "UNAUTHENTICATED", "Cần đăng nhập", instance);
    }

    const body = (await request.json().catch(() => ({}))) as {
      personId?: unknown;
      note?: unknown;
    };
    const personId = typeof body.personId === "string" ? body.personId : "";
    const note = typeof body.note === "string" ? body.note.trim() : undefined;

    const target = resolvePersonMock(personId);
    if (!target) {
      return problem(404, "NOT_FOUND", "Không tìm thấy nhân khẩu", instance);
    }

    // (1) PHẠM VI ltree — TRƯỚC mọi thứ khác, đúng thứ tự dịch vụ thật.
    if (!canWriteInBranch(identity, target.primaryBranch?.path)) {
      return problem(
        403,
        "BRANCH_SCOPE_VIOLATION",
        "Ngoài phạm vi được giao",
        instance,
        "Nhan khau nay thuoc mot chi khac, ngoai pham vi ong/ba duoc giao"
      );
    }

    // (2) KHÔNG AI TỰ CẤP CHO CHÍNH MÌNH.
    if (identity.personId === personId) {
      return problem(
        403,
        "SELF_PASSWORD_RESET_FORBIDDEN",
        "Không tự cấp cho chính mình được",
        instance,
        "Khong tu cap lien ket dat mat khau cho chinh minh duoc; day la luong quen mat khau,"
          + " hay dung duong khac"
      );
    }

    // (3)+(4) TÀI KHOẢN ĐÍCH: phải có tài khoản, ACTIVE, và KHÔNG có địa chỉ thư.
    if (CO_TAI_KHOAN_CO_EMAIL.has(personId)) {
      return problem(
        422,
        "PASSWORD_RESET_NOT_ALLOWED",
        "Tài khoản này tự lấy lại được",
        instance,
        "Tai khoan nay co dia chi thu nen tu lay lai mat khau duoc: bam 'Quen mat khau'"
          + " o man dang nhap de nhan thu"
      );
    }
    if (!CO_TAI_KHOAN_KHONG_EMAIL.has(personId)) {
      // Mọi nhân khẩu khác trong đồ thị: chưa có tài khoản. Đây là ca PHỔ BIẾN
      // NHẤT trong một dòng họ thật, không phải một ca biên.
      return problem(
        422,
        "PASSWORD_RESET_NOT_ALLOWED",
        "Chưa cấp lại được cho người này",
        instance,
        "Nhan khau nay chua co tai khoan — hay phat loi moi thay vi cap lai mat khau"
      );
    }

    dem += 1;
    if (dem > 10) {
      return problem(429, "RATE_LIMITED", "Vượt hạn mức gọi", instance);
    }

    const now = new Date();
    const expiresAt = new Date(now.getTime() + 30 * 60_000).toISOString();
    const grant: PasswordResetGrantDto = {
      id: `pr-${personId}-${dem}`,
      targetPersonId: personId,
      target: {
        displayName: target.displayName ?? undefined,
        generation: target.generation ?? undefined,
        branchName: target.primaryBranch?.name,
      },
      branchId: target.primaryBranch?.id,
      issuedBy: identity.appUserId,
      issuedAt: now.toISOString(),
      expiresAt,
      note,
    };
    daCap = grant;

    const response: IssuedPasswordResetDto = {
      url: `/vi/dat-mat-khau?token=pr-tok-${personId}-${dem}`,
      expiresAt,
      grant,
    };
    return HttpResponse.json(response, { status: 201 });
  }),
];

/** Lượt cấp gần nhất — chỉ để bài kiểm đối chiếu, không có endpoint đọc lại thật. */
export function lastIssuedPasswordResetMock(): PasswordResetGrantDto | null {
  return daCap;
}
