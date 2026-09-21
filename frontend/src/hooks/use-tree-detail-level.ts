"use client";

import { useStore } from "@xyflow/react";
import type { TreeDetailLevel } from "@/components/tree/tree-canvas-context";

/**
 * Ngưỡng chuyển giữa hai mức chi tiết của tấm thẻ nhân khẩu.
 *
 * <p>Đặt ngay dưới {@code MIN_INITIAL_ZOOM} (0.75) một nhịp: phả đồ **mở ra ở mức đầy đủ**, vì lúc
 * ấy người dùng đang đọc chứ chưa đi tìm. Chỉ khi họ chủ động thu nhỏ — kéo ra xa để quét cả một
 * đời, hoặc bấm "Thu toàn cây" — thì thẻ mới rút về chỉ còn cái tên, ở cỡ chữ lớn hơn.</p>
 *
 * <p>Không chọn đúng 0.75: canvas canh khung ra những mức như 0.748 trên màn hình lẻ, và một
 * ngưỡng trùng khít sàn sẽ làm thẻ nhấp nháy giữa hai mức ngay lúc mở trang.</p>
 */
export const COMPACT_DETAIL_ZOOM = 0.7;

/**
 * Mức chi tiết của thẻ nhân khẩu, suy từ **mức phóng của máy quay**.
 *
 * <h2>Vì sao đọc ở đây chứ không ở từng thẻ</h2>
 * `<PersonNode>` được React Flow dựng lại độc lập, hàng nghìn bản. Gọi `useStore` trong đó là
 * đăng ký hàng nghìn người nghe vào cùng một kho trạng thái, và mỗi nấc lăn chuột sẽ dựng lại tất.
 * Ở đây chỉ có **một** người nghe, và giá trị nó trả về là một trong hai chuỗi — nên zustand chỉ
 * báo thay đổi khi **vượt ngưỡng**, tức vài lần trong cả một phiên, chứ không phải mỗi khung hình.
 *
 * Phải gọi bên trong `<ReactFlowProvider>`.
 */
export function useTreeDetailLevel(): TreeDetailLevel {
  return useStore((state): TreeDetailLevel =>
    state.transform[2] < COMPACT_DETAIL_ZOOM ? "compact" : "full"
  );
}
