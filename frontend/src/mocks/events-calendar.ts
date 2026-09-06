import { getMockGraph } from "./tree-graph/build-graph";
import { resolvePersonMock, toSummaryMock } from "./person-detail";
import { EVENTS_MOCK } from "./data";
import type { EventDto, EventType, LunarDate, PersonSummaryDto } from "@/types/api";

/**
 * Generates a year's worth of giỗ so F7's lịch năm and "sắp tới" list have
 * something to be wrong about. Two hand-authored fixtures (EVENTS_MOCK) are
 * kept and merged in, so the curated story characters keep their exact dates.
 *
 * EVERYTHING HERE IS FAKE BACKEND WORK, and that distinction is the point:
 *
 *  - `nextOccurrenceSolar` and `daysUntil` are *computed* here because in
 *    production the backend computes them (Hồ Ngọc Đức, GMT+7, leap months).
 *    A mock is allowed to stand in for that service. The client is not
 *    allowed to do it at all — see src/lib/format/event.ts.
 *  - The `lunarDate` values are FABRICATED, not converted from the solar
 *    date. They are internally arbitrary; do not read anything into them, and
 *    never use this file as a reference for how conversion works.
 *
 * One event deliberately carries `leap: true` and one is a MUNG_THO for a
 * LIVING person, so both the leap-month marker and the guest privacy filter
 * (handlers/events.ts) stay exercisable by hand.
 */

const REMINDER_OFFSETS = [7, 3, 1];

/** Deterministic per-id hash — same graph, same calendar, every run. */
function hash(id: string): number {
  let h = 2166136261;
  for (let i = 0; i < id.length; i += 1) {
    h ^= id.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0) / 4294967296;
}

/** Today's solar date in Vietnam, as a UTC-midnight Date for safe day math. */
function vietnamToday(): Date {
  const iso = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  return new Date(`${iso}T00:00:00Z`);
}

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

function eventTypeFor(generation: number): EventType {
  if (generation <= 1) return "GIO_TO";
  if (generation === 2) return "GIO_CHI";
  return "GIO_THUONG";
}

function fakeLunar(seed: number, solarYear: number, solarMonth: number): LunarDate {
  return {
    // Roughly a month behind the solar date, which is the shape of the real
    // relationship — but a shape, not a conversion.
    year: solarYear,
    month: ((solarMonth + 10) % 12) + 1,
    day: 1 + (Math.floor(seed * 10000) % 29),
    leap: seed > 0.94,
  };
}

function summaryFor(personId: string): PersonSummaryDto | undefined {
  const person = resolvePersonMock(personId);
  return person ? toSummaryMock(person) : undefined;
}

let cached: EventDto[] | null = null;

export function getMockEvents(): EventDto[] {
  if (cached) return cached;

  const graph = getMockGraph();
  const today = vietnamToday();
  const generated: EventDto[] = [];

  // Ancestors only (generation <= 4): a giỗ is for the deceased, and the
  // early generations are the ones a clan actually keeps calendar-wide.
  const candidates = [...graph.personsById.values()]
    .filter((p) => !p.isAlive && p.generation <= 4)
    .sort((a, b) => a.id.localeCompare(b.id))
    .slice(0, 40);

  for (const raw of candidates) {
    const seed = hash(raw.id);
    const offset = Math.floor(seed * 366);
    const occurrence = addDays(today, offset);
    const solarMonth = occurrence.getUTCMonth() + 1;
    const isClanLevel = raw.generation <= 1;

    generated.push({
      id: `ev-gen-${raw.id}`,
      eventType: eventTypeFor(raw.generation),
      // The backend pre-renders titles per Accept-Language; the mock stands
      // in for that. The FE must never assemble this itself.
      title: `Giỗ ${raw.displayName}`,
      person: summaryFor(raw.id) ?? null,
      lunarDate: fakeLunar(seed, occurrence.getUTCFullYear(), solarMonth),
      nextOccurrenceSolar: isoDate(occurrence),
      nextOccurrenceLunarYear: occurrence.getUTCFullYear(),
      daysUntil: offset,
      targetBranch: isClanLevel ? null : (raw.primaryBranch ?? null),
      isClanLevel,
      reminderOffsets: REMINDER_OFFSETS,
    });
  }

  // A living person's event (mừng thọ). Guests must never see this one — it
  // is the fixture that proves handlers/events.ts filters by tier at all.
  const livingSummary = summaryFor("p-100");
  if (livingSummary) {
    const occurrence = addDays(today, 21);
    generated.push({
      id: "ev-mung-tho-p100",
      eventType: "MUNG_THO",
      title: `Mừng thọ ${livingSummary.displayName}`,
      person: livingSummary,
      lunarDate: { year: occurrence.getUTCFullYear(), month: 8, day: 12, leap: false },
      nextOccurrenceSolar: isoDate(occurrence),
      nextOccurrenceLunarYear: occurrence.getUTCFullYear(),
      daysUntil: 21,
      targetBranch: livingSummary.primaryBranch ?? null,
      isClanLevel: false,
      reminderOffsets: REMINDER_OFFSETS,
    });
  }

  // Curated fixtures win on id collision.
  const byId = new Map<string, EventDto>();
  for (const event of [...generated, ...EVENTS_MOCK]) byId.set(event.id, event);

  cached = [...byId.values()].sort((a, b) =>
    (a.nextOccurrenceSolar ?? "9999").localeCompare(b.nextOccurrenceSolar ?? "9999")
  );
  return cached;
}
