import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { PUBLIC_SEARCH_MIN_QUERY_LENGTH } from "@/lib/api/public-portal";
import { searchApi, type PersonSearchInput, type SearchAudience } from "@/lib/api/search";
import { queryKeys } from "@/lib/query/keys";

/**
 * Accent-insensitive person search (FR-4.4). Backs the F6 search screen.
 *
 * `audience` decides which endpoint answers — see `searchApi.persons` for why
 * a guest must NOT reach `/persons/search`. `null` means "not resolved yet"
 * (Keycloak's `check-sso` mid-flight); the query stays `enabled: false` for
 * that instant rather than guessing, exactly like `useRootCandidates`.
 *
 * A public query shorter than `PUBLIC_SEARCH_MIN_QUERY_LENGTH` is never sent
 * — the public endpoint answers a 1-character query with `400`, and a red
 * banner on someone's very first keystroke reads as "the site is broken".
 * Member search keeps the old, looser floor (any non-empty string).
 *
 * `keepPreviousData` is what turns this from a form submit into live
 * suggestions: while the next keystroke's request is in flight the previous
 * matches stay on screen instead of collapsing to a skeleton, so the list
 * never flickers under a fast typist. Callers debounce the query themselves.
 *
 * Results arrive already privacy-filtered — including any `totalElements`,
 * which is counted after filtering. Never post-filter them here.
 */
export function usePersonSearch(audience: SearchAudience | null, params: PersonSearchInput) {
  const term = params.q.trim();
  const minLength = audience === "public" ? PUBLIC_SEARCH_MIN_QUERY_LENGTH : 1;

  return useQuery({
    queryKey: queryKeys.personSearch(JSON.stringify({ audience, ...params, q: term })),
    queryFn: () => searchApi.persons(audience ?? "member", { ...params, q: term }),
    enabled: audience !== null && term.length >= minLength,
    placeholderData: keepPreviousData,
  });
}
