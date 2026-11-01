import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import type { MeView } from "@/lib/api/change-requests";
import type { Problem } from "@/types/api";
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
 * <h2>Khách nhận `401`, đúng như máy chủ thật</h2>
 * Bản trước trả `200` kèm `appUserId: null` cho khách và ghi rằng "bản thật
 * cũng vậy". Không phải: `/api/v1/me` rơi vào `anyRequest().authenticated()`
 * của `SecurityConfig`, và máy chủ thật trả `401 UNAUTHENTICATED` (đo ngày
 * 24/09 trên cổng 8088). Mock "dễ hơn thật" đã che đi việc mọi trang bắn một
 * `401` thừa cho mỗi vị khách. Giao diện nay không gọi `/me` khi chưa đăng nhập
 * (xem `useMe`); nếu có chỗ nào quên, mock này làm nó đỏ y như máy thật.
 *
 * Tài khoản CÓ token nhưng chưa có `app_user` thì khác: máy thật tự khởi tạo
 * dòng `app_user` ở lần gọi đầu và trả `200` — ca ấy không đi qua nhánh khách.
 */
export const meHandlers = [
  http.get(`${API_BASE_URL}/api/v1/me`, ({ request }) => {
    const role = resolveMockRole(request);
    if (role === "guest") {
      const body: Problem = {
        type: "https://giapha.vn/problems/unauthorized",
        title: "Chưa đăng nhập",
        status: 401,
        detail: "Yêu cầu cần một JWT hợp lệ do Keycloak cấp",
        instance: "/api/v1/me",
        code: "UNAUTHENTICATED",
      };
      return HttpResponse.json(body, {
        status: 401,
        headers: { "Content-Type": "application/problem+json" },
      });
    }
    const identity = identityOf(role);
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
