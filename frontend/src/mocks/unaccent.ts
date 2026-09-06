/**
 * Mock stand-in for Postgres `unaccent` + the generated `name_unaccented`
 * column the real search uses (FR-4.4). Lives in src/mocks/ on purpose: the
 * CLIENT must never fold diacritics itself, because the backend's FTS
 * behaviour (unaccent + pg_trgm + per-name-layer indexing) is what search
 * results must match. This only exists so the mocked `/persons/search`
 * behaves like the real one for "tim kiem khong dau".
 */
export function unaccent(input: string): string {
  return input
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "") // strip combining tone/vowel marks
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

/** Naive substring match on the unaccented forms, like the mock FTS needs. */
export function unaccentedIncludes(haystack: string, needle: string): boolean {
  return unaccent(haystack).includes(unaccent(needle));
}
