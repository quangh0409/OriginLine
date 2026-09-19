import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import type { MeView } from "@/lib/api/change-requests";
import { identityOf } from "@/mocks/identity";
import { resolveMockRole } from "./role";

/**
 * MSW cho `GET /api/v1/me` — hồ sơ phiên làm việc.
 *
 * Giao diện dùng `managedBranches` để quyết định hiện hay ẩn cụm "Duyệt đính
 * chính". Đây thuần tuý là gợi ý vẽ giao diện: người dùng sửa được phản hồi
 * này trong DevTools, sửa xong vẫn không duyệt được gì, vì phép kiểm thật nằm
 * ở `ChangeRequestService.review()` → `BranchScopeGuard` → so `ltree`.
 *
 * Khách vẫn nhận `200` chứ không phải `403`: bản thật cũng vậy — endpoint này
 * là nơi tài khoản được khởi tạo lần đầu, nên nó phải trả lời được cả khi chưa
 * có `app_user` nào. `appUserId: null` là câu trả lời, không phải lỗi.
 */
export const meHandlers = [
  http.get(`${API_BASE_URL}/api/v1/me`, ({ request }) => {
    const identity = identityOf(resolveMockRole(request));
    const body: MeView = {
      appUserId: identity.appUserId,
      personId: identity.personId,
      role: identity.role,
      clanWide: identity.clanWide,
      managedBranches: identity.managedBranches,
      homeBranch: identity.homeBranch,
      linkedToTree: identity.personId !== null,
    };
    return HttpResponse.json(body);
  }),
];
