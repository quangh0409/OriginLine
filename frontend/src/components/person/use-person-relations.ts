"use client";

import { useQuery } from "@tanstack/react-query";
import { treeApi } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";
import { useTreeAudience } from "@/hooks/use-tree-audience";
import type { TreeProjection } from "@/types/api";

/**
 * Nguồn dữ liệu cho mục "Quan hệ" trên hồ sơ.
 *
 * KHOẢNG TRỐNG HỢP ĐỒNG ĐÃ THU HẸP — đọc kỹ phần còn lại.
 *
 * `RelationshipDto` nay mang `otherPerson`, nên tên · đời · chi của người ở
 * đầu kia đã đi kèm ngay trong `/persons/{id}`. Lượt `/tree` này vì thế
 * **không còn nằm trên đường găng**: `PersonRelations` vẽ bốn nhóm trực tiếp
 * trước, rồi mới gắn thêm những gì lượt này mang về.
 *
 * Vì sao nó vẫn tồn tại — hai lý do, cả hai đều là giới hạn của hợp đồng chứ
 * không phải thói quen cũ:
 *
 *  1. **Anh chị em.** `relationships` là "quan hệ trực tiếp một bậc, không
 *     phải cả cây" (openapi). Anh chị em là hai bậc — lên cha/mẹ rồi xuống các
 *     con khác — nên không có cạnh nào trong `relationships` mô tả họ. Đó
 *     cũng là lý do `depth = 2` và `direction = BOTH`.
 *  2. **`badges`.** Dâu · rể · con nuôi · đích tôn · trưởng chi chỉ có trên
 *     `TreeNode.badges`; `PersonSummaryDto` không có trường ấy, và các nhãn ấy
 *     tuyệt đối không được suy ở client (contracts/README §7.6).
 *
 * Bỏ hẳn lượt gọi này thì phải đánh đổi bằng một trong hai thứ trên. Muốn bỏ
 * mà không mất gì thì hợp đồng phải thêm `badges` vào `PersonDto` và một lối
 * lấy anh chị em một bậc — cả hai đều là việc của backend, đã ghi lại ở đây để
 * không ai phải suy lại.
 */
export const RELATIONS_DEPTH = 2;

/**
 * Trần nút cho một lượt đọc quan hệ. Cha mẹ + ông bà + vợ/chồng + con + cháu +
 * anh chị em của một người hiếm khi vượt vài chục; 200 là dư mà vẫn giữ hồ sơ
 * nhẹ trên mạng di động. `meta.truncated` được xử lý ở tầng hiển thị.
 */
export const RELATIONS_MAX_NODES = 200;

export function usePersonRelations(personId: string | undefined) {
  const audience = useTreeAudience();

  return useQuery<TreeProjection>({
    // Trùng khoá với lượt tải nhánh của canvas khi tham số y hệt — đó là điều
    // mong muốn: cùng một request thì dùng chung cache.
    queryKey: queryKeys.treeBranch(
      personId ?? "",
      RELATIONS_DEPTH,
      "BOTH",
      RELATIONS_MAX_NODES,
      audience ?? "pending"
    ),
    queryFn: () =>
      // Cùng một lựa chọn bản với canvas: khách đọc quan hệ qua
      // `/api/v1/public/tree` (chỉ người đã khuất), thành viên qua `/api/v1/tree`.
      // Gọi thẳng bản thành viên như trước là `401` với mọi khách trên máy chủ thật.
      treeApi.getTreeFor(audience ?? "public", {
        rootId: personId as string,
        depth: RELATIONS_DEPTH,
        direction: "BOTH",
        includeSpouses: true,
        maxNodes: RELATIONS_MAX_NODES,
      }),
    enabled: audience !== null && Boolean(personId),
    // Lượt bổ khuyết thì không được tự ý làm phiền: tải lại khi cửa sổ lấy lại
    // tiêu điểm chỉ khiến nhóm anh chị em nhấp nháy trên một màn hình đứng yên.
    refetchOnWindowFocus: false,
    // Khách hỏi cây của một người còn sống bị ẩn sẽ nhận 404 giống hệt
    // `/persons/{id}`; thử lại chỉ làm chậm màn hình chứ không đổi kết quả.
    retry: false,
    staleTime: 60_000,
  });
}
