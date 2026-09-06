import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { personsApi, type SearchPersonsParams } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";

/**
 * Accent-insensitive person search (FR-4.4). Backs both the <PersonPicker>
 * dropdown and the F6 search screen.
 *
 * `keepPreviousData` is what turns this from a form submit into live
 * suggestions: while the next keystroke's request is in flight the previous
 * matches stay on screen instead of collapsing to a skeleton, so the list
 * never flickers under a fast typist. Callers debounce the query themselves.
 *
 * Results arrive already privacy-filtered — including `page.totalElements`,
 * which is counted after filtering. Never post-filter them here.
 */
export function usePersonSearch(params: SearchPersonsParams) {
  return useQuery({
    queryKey: queryKeys.personSearch(JSON.stringify(params)),
    queryFn: () => personsApi.search(params),
    enabled: params.q.trim().length > 0,
    placeholderData: keepPreviousData,
  });
}
