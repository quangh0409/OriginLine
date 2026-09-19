import { apiFetch } from "./http";
import {
  publicPortalApi,
  toTreeProjection,
  PUBLIC_TREE_MAX_DEPTH,
} from "./public-portal";
import type { TreeDirection, TreeProjection } from "@/types/api";

export interface GetTreeParams {
  /**
   * **Tuỳ chọn.** Vắng mặt (`undefined`/`null`) nghĩa là *"để máy chủ chọn
   * gốc"*, không phải thiếu sót: `/tree` chọn theo **vai + phạm vi chi** của
   * người gọi, `/public/tree` mở ra **thuỷ tổ**. Gốc đã chọn về trong trường
   * `rootId` của phản hồi — đó là thứ duy nhất nói cho giao diện biết cây này
   * thực ra bắt đầu từ ai. Xem `src/lib/tree/root-id.ts`.
   */
  rootId?: string | null;
  depth?: number; // default 3, max 10
  direction?: TreeDirection; // default DESCENDANTS
  includeSpouses?: boolean; // default true
  includeDeleted?: boolean; // ADMIN/COUNCIL only
  maxNodes?: number; // default 500, max 2000
}

/**
 * Bản phả đồ nào đang được hỏi.
 *
 * - `member` → `GET /api/v1/tree`, cần phiên đăng nhập. Khách gọi vào đây nhận
 *   **401**, và 401 ấy là luật chạy đúng chứ không phải sự cố.
 * - `public`  → `GET /api/v1/public/tree`, không cần phiên, **chỉ người đã khuất**
 *   (BA v2 §10: người đã khuất là công khai).
 *
 * Đây là hai endpoint khác nhau với hai bộ DTO khác nhau, không phải một endpoint
 * với hai mức quyền — xem `public-portal.ts`.
 */
export type TreeAudience = "member" | "public";

export const treeApi = {
  /**
   * Flat REST projection (`nodes[]` + `edges[]`), cached server-side with an
   * ETag. Always check `meta.truncated` — a truncated response is NOT a
   * complete tree, and the guest-visible tree can have legitimate holes
   * where a living person was filtered out (never treat a missing edge as a
   * data bug). Deep client-shaped nested queries should prefer the GraphQL
   * client (src/lib/graphql) instead of chaining many of these calls.
   *
   * `rootId` **tuỳ chọn**: thiếu nó thì máy chủ mở ra gốc của phạm vi mà người
   * gọi chịu trách nhiệm (thuỷ tổ / ông tổ chi được giao / ông tổ chi nhà
   * mình). `null` được đổi về `undefined` trước khi dựng URL — `buildUrl` chỉ
   * bỏ qua `undefined`, còn `null` sẽ thành chuỗi `"null"` và biến một phép
   * chọn gốc thành một `400`.
   */
  getTree: ({ rootId, depth = 3, ...rest }: GetTreeParams) =>
    apiFetch<TreeProjection>("/api/v1/tree", {
      query: { rootId: rootId ?? undefined, depth, ...rest },
    }),

  /**
   * Bản công khai, đã đổi về cùng hình dạng `TreeProjection` để canvas không
   * phải biết mình đang vẽ bản nào.
   *
   * `depth` bị **kẹp** xuống trần công khai thay vì để máy chủ trả `400`: giao
   * diện dùng chung một tham số cho cả hai bản, và một khách bấm "mở sâu thêm"
   * xứng đáng nhận một cây nông hơn chứ không phải một dải báo lỗi.
   * `maxNodes`/`includeDeleted` bị bỏ — endpoint công khai không có chúng.
   */
  getPublicTree: ({ rootId, depth = 3, direction, includeSpouses }: GetTreeParams) =>
    publicPortalApi
      .getTree({
        rootId,
        depth: Math.min(depth, PUBLIC_TREE_MAX_DEPTH),
        direction,
        includeSpouses,
      })
      .then(toTreeProjection),

  /** Chọn bản theo người xem. Một chỗ duy nhất quyết định, để không nơi nào đoán lại. */
  getTreeFor: (audience: TreeAudience, params: GetTreeParams): Promise<TreeProjection> =>
    audience === "public" ? treeApi.getPublicTree(params) : treeApi.getTree(params),
};
