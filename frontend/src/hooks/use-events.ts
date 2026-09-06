import { useQuery } from "@tanstack/react-query";
import { eventsApi, type ListEventsParams } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";

export function useEvents(params: ListEventsParams = {}) {
  return useQuery({
    queryKey: [...queryKeys.events(), params],
    queryFn: () => eventsApi.list(params),
  });
}
