import type { NameType, PersonDto, PersonName } from "@/types/api";
import { isPresent } from "@/lib/privacy/present";

/**
 * Display ordering for the multi-layer Vietnamese naming system (BA v2 —
 * "a person has many names, never collapse them to one"). Ritual weight
 * first: tên húy (taboo) is the name the clan actually records, tên thụy
 * (posthumous) belongs with it, everyday names come last.
 *
 * Labels are NOT here — they live in messages/{vi,en}.json under
 * `person.nameType.*` so the UI stays bilingual. This module only knows about
 * order and selection.
 */
export const NAME_TYPE_ORDER: readonly NameType[] = [
  "HUY",
  "TU",
  "HIEU",
  "THUY",
  "THUONG_GOI",
  "PHAP_DANH",
];

const ORDER_INDEX = new Map<NameType, number>(
  NAME_TYPE_ORDER.map((t, i) => [t, i])
);

/** Stable sort by ritual weight; primary layer floats to the top of its type. */
export function sortNameLayers(names: readonly PersonName[]): PersonName[] {
  return [...names].sort((a, b) => {
    const byType =
      (ORDER_INDEX.get(a.nameType) ?? 99) - (ORDER_INDEX.get(b.nameType) ?? 99);
    if (byType !== 0) return byType;
    return Number(b.isPrimary) - Number(a.isPrimary);
  });
}

/**
 * The one name to show in a heading. Prefers the server-rendered
 * `displayName` (it already accounts for privacy tier); falls back to the
 * primary name layer, then to any layer at all. Returns undefined rather than
 * a placeholder when nothing survived filtering — callers must not invent one.
 */
export function headlineName(person: Pick<PersonDto, "displayName" | "names">): string | undefined {
  if (isPresent(person.displayName)) return person.displayName;
  const primary = person.names.find((n) => n.isPrimary && isPresent(n.fullName));
  if (primary) return primary.fullName;
  return person.names.find((n) => isPresent(n.fullName))?.fullName;
}

/** Hán-Nôm of the primary layer, when the tier actually returned one. */
export function headlineHanNom(person: Pick<PersonDto, "names">): string | undefined {
  const primary = person.names.find((n) => n.isPrimary && isPresent(n.nameHanNom));
  if (primary?.nameHanNom) return primary.nameHanNom;
  return person.names.find((n) => isPresent(n.nameHanNom))?.nameHanNom ?? undefined;
}

/** The taboo name (tên húy) — drives the kỵ húy check server-side (FR-1.6). */
export function tabooName(person: Pick<PersonDto, "names">): string | undefined {
  return person.names.find((n) => n.nameType === "HUY" && isPresent(n.fullName))?.fullName;
}
