import { apiFetch, apiFetchWithMeta, type ApiResult } from "./http";
import type { EventCreateRequest, EventDto, EventPage, EventType, EventUpdateRequest } from "@/types/api";

export interface ListEventsParams {
  from?: string; // solar date, YYYY-MM-DD
  to?: string;
  upcomingDays?: number; // shortcut for from/to, max 400
  eventType?: EventType[];
  branchId?: string;
  personId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * `POST`/`PATCH`/`DELETE /events` — Đợt 2 (F7 form tạo/sửa việc họ).
 *
 * <h2>Hợp đồng chưa lên lúc viết những dòng này</h2>
 * `contracts/openapi.yaml` mới chỉ có `GET /events` và `GET /events/{id}`. Tám
 * trường thân yêu cầu ({@link EventCreateRequest}) và bốn động từ dưới đây là
 * đặc tả PO đã chốt cho một agent backend khác đang xây đúng nó — KHÔNG phải
 * suy đoán. Khi `contracts/openapi.yaml` lên `EventCreateRequest`/
 * `EventUpdateRequest` thật, đối chiếu lại đúng một chỗ này; đừng tự ý thêm
 * trường nào ở đây trước đó (ví dụ `personId` gắn sự kiện với một người,
 * `graveId`, hay `reminderOffsets` tuỳ biến — cả ba đều có mặt trên
 * `EventDto` đọc về nhưng KHÔNG có trong tám trường ghi lên, nên form không có
 * ô nào cho chúng; xem báo cáo bàn giao).
 *
 * `update`/`getById` trả cả `ETag` (`apiFetchWithMeta`) — `PATCH` đòi
 * `If-Match`, giống hệt khuôn `personsApi.update`.
 */
export const eventsApi = {
  list: (params: ListEventsParams = {}) =>
    apiFetch<EventPage>("/api/v1/events", { query: { ...params } }),

  /** Trả kèm `ETag` — giữ lại cho `If-Match` của lượt `update` kế tiếp. */
  getById: (id: string): Promise<ApiResult<EventDto>> =>
    apiFetchWithMeta<EventDto>(`/api/v1/events/${id}`),

  create: (input: EventCreateRequest) =>
    apiFetch<EventDto>("/api/v1/events", { method: "POST", body: input }),

  /** `ifMatch` bắt buộc — máy chủ từ chối `PATCH` không mang `If-Match` (412). */
  update: (id: string, input: EventUpdateRequest, ifMatch: string) =>
    apiFetchWithMeta<EventDto>(`/api/v1/events/${id}`, {
      method: "PATCH",
      body: input,
      ifMatch,
    }),

  /** Xoá mềm — không có lối xoá cứng cho sự kiện, cùng nguyên tắc với nhân khẩu. */
  remove: (id: string) => apiFetch<void>(`/api/v1/events/${id}`, { method: "DELETE" }),
};
