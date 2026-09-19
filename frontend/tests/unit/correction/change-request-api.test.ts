import { beforeEach, describe, expect, it } from "vitest";
import { changeRequestsApi, meApi } from "@/lib/api/change-requests";
import { personsApi } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { setDevRole } from "@/lib/api/dev-role";
import { findChangeRequestMock, resetChangeRequestsMock } from "@/mocks/change-requests";

/**
 * Hợp đồng của luồng đính chính ở mức HTTP, không đi qua React.
 *
 * Bài này canh những chỗ mà giao diện **phải** phân nhánh theo `code` (RFC
 * 7807), chứ không theo `title`/`detail` vốn là chữ cho người đọc và đổi theo
 * `Accept-Language`. Năm mã của W6 —`CHANGE_REQUEST_CLOSED`,
 * `SELF_REVIEW_FORBIDDEN`, `ACCOUNT_NOT_PROVISIONED`, `ACCOUNT_NOT_ACTIVE`,
 * `INVALID_ROLE_ASSIGNMENT` — là mã mới, và nếu bộ giả lập trả sai mã thì
 * không có lỗi biên dịch nào bắt được.
 */

async function expectApiError(
  promise: Promise<unknown>,
  code: string,
  status: number
): Promise<void> {
  await expect(promise).rejects.toBeInstanceOf(ApiError);
  try {
    await promise;
  } catch (caught) {
    const error = caught as ApiError;
    expect(error.code).toBe(code);
    expect(error.status).toBe(status);
  }
}

beforeEach(() => {
  resetChangeRequestsMock();
});

describe("GET /api/v1/me", () => {
  it("trả 200 kèm appUserId null cho Khách — chưa có tài khoản là câu trả lời, không phải lỗi", async () => {
    setDevRole("guest");
    const me = await meApi.get();
    expect(me.appUserId).toBeNull();
    expect(me.role).toBe("GUEST");
    expect(me.managedBranches).toEqual([]);
  });

  it("Trưởng chi nhận PHẠM VI chứ không chỉ vai — giao diện cần cả hai", async () => {
    setDevRole("branch-head");
    const me = await meApi.get();
    expect(me.role).toBe("BRANCH_HEAD");
    expect(me.clanWide).toBe(false);
    expect(me.managedBranches).toEqual(["root.chi_nhat"]);
  });
});

describe("gửi đề nghị", () => {
  it("Khách bị chặn bằng ACCOUNT_NOT_PROVISIONED, không phải FORBIDDEN chung chung", async () => {
    setDevRole("guest");
    await expectApiError(
      changeRequestsApi.submit({
        requestType: "UPDATE_PERSON",
        personId: "p-010",
        payload: { occupation: "Thầy đồ" },
        reason: "Thử",
      }),
      "ACCOUNT_NOT_PROVISIONED",
      403
    );
  });

  it("chốt chi đích ngay lúc gửi, suy từ chi của nhân khẩu", async () => {
    setDevRole("member");
    const created = await changeRequestsApi.submit({
      requestType: "UPDATE_PERSON",
      personId: "p-010",
      payload: { occupation: "Thầy đồ" },
      reason: "Gia phả cũ có ghi.",
    });
    // p-010 thuộc Chi Nhất; lưu khoá chi giúp hàng đợi lọc bằng một câu ltree.
    expect(created.targetBranchId).toBe("b-chi1");
    expect(created.status).toBe("PENDING");
    expect(created.payloadFields).toEqual(["occupation"]);
  });

  it("không kiểm phạm vi lúc gửi — người ở chi khác vẫn phải báo được", async () => {
    setDevRole("member");
    // p-011 thuộc Chi Nhị, ngoài chi của thành viên này.
    const created = await changeRequestsApi.submit({
      requestType: "UPDATE_PERSON",
      personId: "p-011",
      payload: { nativePlace: "Nam Định" },
      reason: "Văn bia ghi vậy.",
    });
    expect(created.status).toBe("PENDING");
  });
});

describe("duyệt", () => {
  it("Trưởng chi duyệt được yêu cầu trong chi mình", async () => {
    setDevRole("branch-head");
    const reviewed = await changeRequestsApi.review("cr-001", { approve: true, note: null });
    expect(reviewed.status).toBe("APPROVED");
    expect(reviewed.reviewerId).toBe("u-branch-head");
  });

  it("đúng vai sai chi trả BRANCH_SCOPE_VIOLATION, tách bạch với FORBIDDEN", async () => {
    setDevRole("branch-head");
    // cr-003 nhắm p-011 thuộc `root.chi_nhi`.
    await expectApiError(
      changeRequestsApi.review("cr-003", { approve: true }),
      "BRANCH_SCOPE_VIOLATION",
      403
    );
    expect(findChangeRequestMock("cr-003")?.status).toBe("PENDING");
  });

  it("KHÔNG tự duyệt đề nghị của chính mình", async () => {
    setDevRole("branch-head");
    // cr-002 do chính u-branch-head gửi.
    await expectApiError(
      changeRequestsApi.review("cr-002", { approve: true }),
      "SELF_REVIEW_FORBIDDEN",
      403
    );
    expect(findChangeRequestMock("cr-002")?.status).toBe("PENDING");
  });

  it("thành viên thường không xem được hàng đợi duyệt", async () => {
    setDevRole("member");
    await expectApiError(changeRequestsApi.pending(), "FORBIDDEN", 403);
  });

  it("yêu cầu đã chốt thì không quyết lại — CHANGE_REQUEST_CLOSED là 422", async () => {
    setDevRole("admin");
    await expectApiError(
      changeRequestsApi.review("cr-004", { approve: false, note: "Đổi ý" }),
      "CHANGE_REQUEST_CLOSED",
      422
    );
  });

  it("từ chối mà không nêu lý do bị máy chủ chặn — chốt không chỉ nằm ở giao diện", async () => {
    setDevRole("branch-head");
    await expectApiError(
      changeRequestsApi.review("cr-001", { approve: false, note: "  " }),
      "VALIDATION_FAILED",
      400
    );
  });
});

describe("hàng đợi lọc theo ltree", () => {
  it("Trưởng chi chỉ thấy chi được giao", async () => {
    setDevRole("branch-head");
    const queue = await changeRequestsApi.pending();
    const ids = queue.map((r) => r.id).sort();
    expect(ids).toEqual(["cr-001", "cr-002"]);
  });

  it("vai toàn dòng họ thấy tất cả yêu cầu đang chờ", async () => {
    setDevRole("admin");
    const queue = await changeRequestsApi.pending();
    expect(queue.map((r) => r.id).sort()).toEqual(["cr-001", "cr-002", "cr-003"]);
  });

  it("badge trả 0 cho người không có quyền thay vì làm hỏng thanh đầu trang", async () => {
    setDevRole("guest");
    expect((await changeRequestsApi.pendingCount()).count).toBe(0);
    setDevRole("member");
    expect((await changeRequestsApi.pendingCount()).count).toBe(0);
    setDevRole("branch-head");
    expect((await changeRequestsApi.pendingCount()).count).toBe(2);
  });
});

describe("rút lại", () => {
  it("chuyển sang CANCELLED chứ không xoá — lịch sử duyệt vẫn đọc được", async () => {
    setDevRole("member");
    const cancelled = await changeRequestsApi.cancel("cr-001");
    expect(cancelled.status).toBe("CANCELLED");
    expect(findChangeRequestMock("cr-001")).toBeDefined();
  });

  it("không rút được đề nghị của người khác", async () => {
    setDevRole("branch-head");
    await expectApiError(changeRequestsApi.cancel("cr-001"), "FORBIDDEN", 403);
  });
});

describe("meta.canRequestCorrection phản ánh quyền thật, không còn ghi cứng false", () => {
  it("Khách: không có tài khoản thì không có gì để đề nghị", async () => {
    setDevRole("guest");
    const { data } = await personsApi.getById("p-010");
    expect(data.meta.canRequestCorrection).toBe(false);
    expect(data.meta.canEdit).toBe(false);
  });

  it("Thành viên: không sửa được nên PHẢI đề nghị được — đây là cây cầu duy nhất", async () => {
    setDevRole("member");
    const { data } = await personsApi.getById("p-010");
    expect(data.meta.canEdit).toBe(false);
    expect(data.meta.canRequestCorrection).toBe(true);
  });

  it("Thành viên sửa thẳng được hồ sơ CỦA CHÍNH MÌNH, và khi ấy không cần đề nghị", async () => {
    setDevRole("member");
    const { data } = await personsApi.getById("p-102");
    expect(data.meta.isSelf).toBe(true);
    expect(data.meta.canEdit).toBe(true);
    expect(data.meta.canRequestCorrection).toBe(false);
  });

  it("Trưởng chi: sửa thẳng trong chi mình, chỉ đề nghị được ở chi khác", async () => {
    setDevRole("branch-head");
    const inScope = await personsApi.getById("p-010"); // root.chi_nhat
    expect(inScope.data.meta.canEdit).toBe(true);
    expect(inScope.data.meta.canRequestCorrection).toBe(false);

    const outOfScope = await personsApi.getById("p-011"); // root.chi_nhi
    expect(outOfScope.data.meta.canEdit).toBe(false);
    expect(outOfScope.data.meta.canRequestCorrection).toBe(true);
  });

  it("PATCH ngoài phạm vi trả BRANCH_SCOPE_VIOLATION và chỉ đường sang luồng đính chính", async () => {
    setDevRole("branch-head");
    const { etag } = await personsApi.getById("p-011");
    await expectApiError(
      personsApi.update("p-011", { occupation: "Thầy đồ" }, etag ?? '"v1"'),
      "BRANCH_SCOPE_VIOLATION",
      403
    );
  });
});
