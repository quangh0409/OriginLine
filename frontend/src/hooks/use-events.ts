import { useQuery } from "@tanstack/react-query";
import { eventsApi, type ListEventsParams } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";

export function useEvents(params: ListEventsParams = {}) {
  return useQuery({
    queryKey: [...queryKeys.events(), params],
    queryFn: () => eventsApi.list(params),
  });
}

/**
 * Một sự kiện đơn, kèm `ETag` — nền của màn sửa (F7).
 *
 * Trả `{ event, etag }`, cùng khuôn với `usePerson` trả `{ person, etag }`.
 * `retry: false` vì một `404` (đã xoá mềm, hoặc khách/thành viên ngoài phạm
 * vi hỏi một sự kiện gắn người còn sống) là câu trả lời hợp lệ, không phải sự
 * cố mạng cần thử lại.
 */
export function useEvent(id: string) {
  return useQuery({
    queryKey: queryKeys.event(id),
    queryFn: async () => {
      const { data, etag } = await eventsApi.getById(id);
      return { event: data, etag };
    },
    retry: false,
  });
}
