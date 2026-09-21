"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { eventsApi } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";
import type { EventCreateRequest, EventUpdateRequest } from "@/types/api";

/**
 * Tạo việc họ mới (F7 Đợt 2). Không có luồng kỵ húy/nghi trùng như
 * `usePersonSubmit` — hợp đồng của `/events` không định nghĩa cổng nào tương
 * tự, nên đây là một `useMutation` thẳng.
 */
export function useCreateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: EventCreateRequest) => eventsApi.create(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.events() });
    },
  });
}

export interface UpdateEventVariables {
  id: string;
  input: EventUpdateRequest;
  /** `ETag` lấy từ lượt `GET` trước đó — bắt buộc, xem `eventsApi.update`. */
  ifMatch: string;
}

export function useUpdateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, input, ifMatch }: UpdateEventVariables) => {
      const result = await eventsApi.update(id, input, ifMatch);
      return result.data;
    },
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.events() });
      void queryClient.invalidateQueries({ queryKey: queryKeys.event(variables.id) });
    },
  });
}

export function useDeleteEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => eventsApi.remove(id),
    onSuccess: (_data, id) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.events() });
      void queryClient.invalidateQueries({ queryKey: queryKeys.event(id) });
    },
  });
}
