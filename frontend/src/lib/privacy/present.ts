/**
 * The single rule that keeps privacy-tier rendering honest.
 *
 * The backend already filtered this payload (BA v2 §10 / Nghị định 13/2023).
 * A field the caller may not see is simply ABSENT from REST JSON (`null` over
 * GraphQL) — and "absent because hidden" is deliberately indistinguishable
 * from "absent because unknown" (contracts/README §3).
 *
 * Therefore the ONLY correct rendering of an absent field is: render nothing.
 * No label with an empty value, no "•••", no lock icon, no "restricted" hint —
 * every one of those tells a guest that data exists, which is exactly what the
 * filtering is there to hide. Components must route every optional API field
 * through `isPresent` (or <OptionalField>, which wraps it) rather than
 * hand-rolling `value ?? "—"`.
 */
export function isPresent<T>(value: T | null | undefined): value is T {
  if (value === null || value === undefined) return false;
  if (typeof value === "string") return value.trim().length > 0;
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === "object") {
    // An object whose every leaf is absent (e.g. a ContactInfo where phone,
    // email and zaloId were all filtered out) is itself absent.
    return Object.values(value as Record<string, unknown>).some(isPresent);
  }
  return true;
}

/** Convenience for `isPresent(x) ? x : undefined` in JSX expressions. */
export function presentOrUndefined<T>(value: T | null | undefined): T | undefined {
  return isPresent(value) ? value : undefined;
}

/** True when at least one of the supplied values survived privacy filtering. */
export function anyPresent(...values: unknown[]): boolean {
  return values.some((v) => isPresent(v));
}
