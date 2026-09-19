import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import type {
  ChangeRequestType,
  ChangeRequestView,
  ReviewChangeRequestBody,
  SubmitChangeRequestBody,
} from "@/lib/api/change-requests";
import {
  allChangeRequestsMock,
  branchOfPerson,
  branchPathOf,
  findChangeRequestMock,
  insertChangeRequestMock,
  nextChangeRequestIdMock,
  redactPayload,
  replaceChangeRequestMock,
} from "@/mocks/change-requests";
import {
  canWriteInBranch,
  identityOf,
  pathIsWithin,
  roleCanReview,
  type MockIdentity,
} from "@/mocks/identity";
import { resolveMockRole } from "./role";
import type { Problem, ProblemCode } from "@/types/api";

/**
 * MSW cho `/api/v1/change-requests` — luồng **đính chính**.
 *
 * Chép lại `membership.ChangeRequestService` ở mức đủ để giao diện gặp đúng
 * những phản hồi thật, kể cả những phản hồi khó chịu: `SELF_REVIEW_FORBIDDEN`,
 * `BRANCH_SCOPE_VIOLATION`, `CHANGE_REQUEST_CLOSED`, `ACCOUNT_NOT_PROVISIONED`.
 * Mã trạng thái theo `GlobalExceptionHandler`: `ForbiddenException` → 403,
 * `DomainException` → 422, `NotFoundException` → 404.
 *
 * <h2>Duyệt KHÔNG tự áp dụng thay đổi — cố ý</h2>
 * Backend phát `ChangeRequestApprovedEvent` nhưng **chưa có context nào nhận**
 * (javadoc của chính lớp ấy: "Ở W6 chưa có người nhận"). Bộ giả lập này bám
 * đúng hành vi đó: duyệt xong, hồ sơ nhân khẩu **không** đổi. Cho mock tự sửa
 * hồ sơ sẽ dạy cả đội một điều sai về hệ thống thật, và cái sai ấy chỉ lộ ra ở
 * môi trường thật — nơi trả giá đắt nhất.
 */

function problem(
  status: number,
  code: ProblemCode,
  title: string,
  detail: string,
  instance: string
) {
  const body: Problem = { type: "about:blank", title, status, code, detail, instance };
  return HttpResponse.json(body, { status });
}

const BASE = `${API_BASE_URL}/api/v1/change-requests`;

/** `guard.requireProvisionedAccount` — khách chưa có dòng `app_user` nào. */
function requireAccount(identity: MockIdentity, instance: string) {
  if (identity.appUserId === null) {
    return problem(
      403,
      "ACCOUNT_NOT_PROVISIONED",
      "Không đủ thẩm quyền",
      "Tài khoản chưa được khởi tạo trong hệ thống, xin đăng nhập lại.",
      instance
    );
  }
  return null;
}

/** Chi đích của một yêu cầu, dạng `ltree` — căn cứ duy nhất để so phạm vi. */
function targetPathOf(request: ChangeRequestView): string | null {
  if (request.targetBranchId) return branchPathOf(request.targetBranchId);
  if (request.personId) return branchOfPerson(request.personId).path;
  return null;
}

/**
 * `BranchScopeGuard.requireReviewAccess`: đúng vai **và** đúng phạm vi.
 *
 * Mã lỗi tách làm hai vì hai tình huống dẫn tới hai hành động khác nhau của
 * người dùng — "bạn không có quyền này" so với "bạn có quyền này nhưng không
 * phải ở chi đó".
 */
function reviewDenial(identity: MockIdentity, request: ChangeRequestView, instance: string) {
  if (!roleCanReview(identity)) {
    return problem(
      403,
      "FORBIDDEN",
      "Không đủ thẩm quyền",
      "Chỉ Trưởng chi, Hội đồng Tộc biểu hoặc Quản trị hệ thống được duyệt yêu cầu đính chính.",
      instance
    );
  }
  if (!canWriteInBranch(identity, targetPathOf(request))) {
    return problem(
      403,
      "BRANCH_SCOPE_VIOLATION",
      "Không đủ thẩm quyền",
      "Yêu cầu này thuộc chi/ngành ngoài phạm vi bạn được giao.",
      instance
    );
  }
  return null;
}

/** Yêu cầu nào lọt vào hàng đợi của người này. */
function visibleInQueue(identity: MockIdentity, request: ChangeRequestView): boolean {
  if (request.status !== "PENDING") return false;
  if (identity.clanWide) return true;
  const path = targetPathOf(request);
  return identity.managedBranches.some((scope) => pathIsWithin(scope, path));
}

function paginate<T>(items: T[], url: URL): T[] {
  const page = Math.max(Number(url.searchParams.get("page") ?? "0"), 0);
  const rawSize = Number(url.searchParams.get("size") ?? "20");
  const size = rawSize <= 0 ? 20 : Math.min(rawSize, 100);
  return items.slice(page * size, page * size + size);
}

const byNewestFirst = (a: ChangeRequestView, b: ChangeRequestView) =>
  (b.createdAt ?? "").localeCompare(a.createdAt ?? "");

export const changeRequestHandlers = [
  // -- Gửi đề nghị ---------------------------------------------------------
  // Không kiểm phạm vi ở bước này, đúng như backend: người ở xa vẫn phải báo
  // được rằng ngày mất của cụ ghi sai. Cửa kiểm chặt nằm ở bước duyệt.
  http.post(BASE, async ({ request }) => {
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, "/api/v1/change-requests");
    if (denied) return denied;

    const body = (await request.json()) as SubmitChangeRequestBody;
    const type: ChangeRequestType = body.requestType;

    if (type !== "CREATE_PERSON" && !body.personId) {
      return problem(
        400,
        "VALIDATION_FAILED",
        "Dữ liệu gửi lên không hợp lệ",
        `Yêu cầu loại ${type} phải trỏ tới một nhân khẩu có sẵn.`,
        "/api/v1/change-requests"
      );
    }

    const payload = body.payload ?? {};
    const targetBranchId =
      body.targetBranchId ?? branchOfPerson(body.personId ?? null).branchId;

    const created: ChangeRequestView = {
      id: nextChangeRequestIdMock(),
      type,
      status: "PENDING",
      personId: body.personId ?? null,
      targetBranchId,
      payload,
      payloadFields: Object.keys(payload).sort(),
      reason: body.reason ?? null,
      requestedBy: identity.appUserId as string,
      reviewerId: null,
      reviewNote: null,
      reviewedAt: null,
      createdAt: new Date().toISOString(),
      version: 0,
    };
    insertChangeRequestMock(created);
    return HttpResponse.json(created, {
      status: 201,
      headers: { Location: `/api/v1/change-requests/${created.id}` },
    });
  }),

  // -- Đề nghị của chính tôi ----------------------------------------------
  http.get(`${BASE}/mine`, ({ request }) => {
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, "/api/v1/change-requests/mine");
    if (denied) return denied;

    const mine = allChangeRequestsMock()
      .filter((r) => r.requestedBy === identity.appUserId)
      .sort(byNewestFirst);
    return HttpResponse.json(paginate(mine, new URL(request.url)));
  }),

  // -- Hàng đợi chờ duyệt --------------------------------------------------
  http.get(`${BASE}/pending`, ({ request }) => {
    const identity = identityOf(resolveMockRole(request));
    const denied = requireAccount(identity, "/api/v1/change-requests/pending");
    if (denied) return denied;

    if (!roleCanReview(identity)) {
      return problem(
        403,
        "FORBIDDEN",
        "Không đủ thẩm quyền",
        "Chỉ Trưởng chi, Hội đồng Tộc biểu hoặc Quản trị hệ thống xem được hàng đợi duyệt.",
        "/api/v1/change-requests/pending"
      );
    }

    const queue = allChangeRequestsMock()
      .filter((r) => visibleInQueue(identity, r))
      .sort(byNewestFirst);
    return HttpResponse.json(paginate(queue, new URL(request.url)));
  }),

  http.get(`${BASE}/pending/count`, ({ request }) => {
    const identity = identityOf(resolveMockRole(request));
    // Không ném lỗi: badge trên thanh đầu trang không được hỏng chỉ vì người
    // đang xem là Khách. Backend cũng trả 0 chứ không trả 403.
    if (identity.appUserId === null || !roleCanReview(identity)) {
      return HttpResponse.json({ count: 0 });
    }
    const count = allChangeRequestsMock().filter((r) => visibleInQueue(identity, r)).length;
    return HttpResponse.json({ count });
  }),

  // -- Một yêu cầu cụ thể --------------------------------------------------
  http.get(`${BASE}/:id`, ({ params, request }) => {
    const identity = identityOf(resolveMockRole(request));
    const id = params.id as string;
    const instance = `/api/v1/change-requests/${id}`;
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const found = findChangeRequestMock(id);
    if (!found) {
      return problem(404, "NOT_FOUND", "Không tìm thấy dữ liệu",
        `Không tìm thấy yêu cầu đính chính với định danh ${id}.`, instance);
    }

    const isRequester = found.requestedBy === identity.appUserId;
    const canSeePayload =
      isRequester || identity.clanWide || canWriteInBranch(identity, targetPathOf(found));
    return HttpResponse.json(canSeePayload ? found : redactPayload(found));
  }),

  // -- Duyệt / từ chối -----------------------------------------------------
  http.post(`${BASE}/:id/review`, async ({ params, request }) => {
    const identity = identityOf(resolveMockRole(request));
    const id = params.id as string;
    const instance = `/api/v1/change-requests/${id}/review`;
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const found = findChangeRequestMock(id);
    if (!found) {
      return problem(404, "NOT_FOUND", "Không tìm thấy dữ liệu",
        `Không tìm thấy yêu cầu đính chính với định danh ${id}.`, instance);
    }

    // Thứ tự các phép kiểm bám đúng ChangeRequestService.review():
    // trạng thái mở → quyền duyệt → tự duyệt.
    if (found.status !== "PENDING") {
      return problem(
        422,
        "CHANGE_REQUEST_CLOSED",
        "Vi phạm quy tắc nghiệp vụ",
        `Yêu cầu đính chính đã ở trạng thái ${found.status}, không xử lý lại được.`,
        instance
      );
    }

    const scopeDenial = reviewDenial(identity, found, instance);
    if (scopeDenial) return scopeDenial;

    if (found.requestedBy === identity.appUserId) {
      return problem(
        403,
        "SELF_REVIEW_FORBIDDEN",
        "Không đủ thẩm quyền",
        "Người duyệt không được là người gửi yêu cầu.",
        instance
      );
    }

    const body = (await request.json()) as ReviewChangeRequestBody;
    const note = body.note?.trim() ?? "";
    if (!body.approve && note.length === 0) {
      return problem(
        400,
        "VALIDATION_FAILED",
        "Dữ liệu gửi lên không hợp lệ",
        "Từ chối một yêu cầu thì bắt buộc phải nêu lý do.",
        instance
      );
    }

    const reviewed: ChangeRequestView = {
      ...found,
      status: body.approve ? "APPROVED" : "REJECTED",
      reviewerId: identity.appUserId,
      reviewNote: note.length > 0 ? note : null,
      reviewedAt: new Date().toISOString(),
      version: found.version + 1,
    };
    replaceChangeRequestMock(reviewed);
    return HttpResponse.json(reviewed);
  }),

  // -- Người gửi tự rút lại ------------------------------------------------
  http.delete(`${BASE}/:id`, ({ params, request }) => {
    const identity = identityOf(resolveMockRole(request));
    const id = params.id as string;
    const instance = `/api/v1/change-requests/${id}`;
    const denied = requireAccount(identity, instance);
    if (denied) return denied;

    const found = findChangeRequestMock(id);
    if (!found) {
      return problem(404, "NOT_FOUND", "Không tìm thấy dữ liệu",
        `Không tìm thấy yêu cầu đính chính với định danh ${id}.`, instance);
    }
    if (found.requestedBy !== identity.appUserId) {
      return problem(403, "FORBIDDEN", "Không đủ thẩm quyền",
        "Chỉ người gửi mới rút lại được yêu cầu của mình.", instance);
    }
    if (found.status !== "PENDING") {
      return problem(422, "CHANGE_REQUEST_CLOSED", "Vi phạm quy tắc nghiệp vụ",
        `Yêu cầu đính chính đã ở trạng thái ${found.status}, không xử lý lại được.`, instance);
    }

    // Rút lại KHÔNG phải xoá: bản ghi chuyển sang CANCELLED để lịch sử còn đọc được.
    const cancelled: ChangeRequestView = {
      ...found,
      status: "CANCELLED",
      reviewedAt: new Date().toISOString(),
      version: found.version + 1,
    };
    replaceChangeRequestMock(cancelled);
    return HttpResponse.json(cancelled);
  }),
];
