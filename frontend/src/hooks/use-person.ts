import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { personsApi } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";
import type { UpdatePersonRequest } from "@/types/api";

/**
 * Returns `{ person, etag }` — keep `etag` around for the `If-Match` header
 * on a subsequent update (optimistic locking, contracts/openapi.yaml).
 */
export function usePerson(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.person(id ?? ""),
    queryFn: async () => {
      const { data, etag } = await personsApi.getById(id as string);
      return { person: data, etag };
    },
    enabled: Boolean(id),
  });
}

export function useUpdatePerson(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input, ifMatch }: { input: UpdatePersonRequest; ifMatch: string }) =>
      personsApi.update(id, input, ifMatch),
    onSuccess: ({ data, etag }) => {
      queryClient.setQueryData(queryKeys.person(id), { person: data, etag });
    },
  });
}
