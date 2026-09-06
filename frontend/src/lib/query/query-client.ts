import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/http";

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (failureCount, error) => {
          // Don't retry 4xx (bad request, forbidden, not found) — only
          // transient/server errors are worth retrying.
          if (error instanceof ApiError && error.status < 500) return false;
          return failureCount < 2;
        },
        refetchOnWindowFocus: false,
      },
      mutations: {
        retry: false,
      },
    },
  });
}
