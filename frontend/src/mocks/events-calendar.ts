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
 *
 * Từ V10, `EventType` là **song ánh 12 giá trị**: `KHANH_THANH`, `HOP_HO`,
 * `CUOI_HOI` không còn gộp vào `KHAC`, và `SINH_NHAT` đã tách khỏi `MUNG_THO`.
 * Bốn mã mới đều có ít nhất một bản ghi mẫu ở đây, vì một nhãn không có dòng
 * dữ liệu nào mang nó là một nhãn chưa ai từng nhìn thấy — đúng tình cảnh của
 * `TIEU_TUONG`/`DAI_TUONG` trước khi V10 đưa chúng vào ràng buộc CSDL.
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

  // --- Giỗ đầu / giỗ hết -----------------------------------------------------
  //
  // `TIEU_TUONG` và `DAI_TUONG` có nhãn trong `messages/*.json` từ lâu nhưng
  // KHÔNG có giá trị nào trong `ck_event_type` cho tới V10 — hai nhãn cho hai
  // thứ không dòng dữ liệu nào mang được. Nay ràng buộc đã mở, nên bộ giả lập
  // phải sinh ra chúng, nếu không phía giao diện vẫn không ai từng nhìn thấy.
  const tangLe: ReadonlyArray<{ type: EventType; offset: number }> = [
    { type: "TIEU_TUONG", offset: 26 },
    { type: "DAI_TUONG", offset: 58 },
  ];
  candidates.slice(0, tangLe.length).forEach((raw, i) => {
    const moc = tangLe[i]!;
    const occurrence = addDays(today, moc.offset);
    const seed = hash(`${raw.id}#${moc.type}`);
    generated.push({
      id: `ev-${moc.type.toLowerCase()}-${raw.id}`,
      eventType: moc.type,
      title: `${moc.type === "TIEU_TUONG" ? "Giỗ đầu" : "Giỗ hết"} ${raw.displayName}`,
      person: summaryFor(raw.id) ?? null,
      lunarDate: fakeLunar(seed, occurrence.getUTCFullYear(), occurrence.getUTCMonth() + 1),
      nextOccurrenceSolar: isoDate(occurrence),
      nextOccurrenceLunarYear: occurrence.getUTCFullYear(),
      daysUntil: moc.offset,
      targetBranch: raw.primaryBranch ?? null,
      isClanLevel: false,
      reminderOffsets: REMINDER_OFFSETS,
    });
  });

  // --- Bốn mã V10: mỗi mã một bản ghi, để nhãn nào cũng có chỗ hiện ra -------
  //
  // `SINH_NHAT` gắn với NGƯỜI CÒN SỐNG và vì thế chịu phân tầng riêng tư chặt
  // hơn `MUNG_THO`: bản thân ngày diễn ra sự kiện CHÍNH LÀ ngày sinh, nên nó
  // chỉ hiện với người mà chủ thể đã mở nhóm `birthDetailAndPhoto`
  // (contracts/openapi.yaml → EventType). Phép lọc ấy nằm ở handlers/events.ts.
  if (livingSummary) {
    const occurrence = addDays(today, 34);
    generated.push({
      id: "ev-sinh-nhat-p100",
      eventType: "SINH_NHAT",
      title: `Sinh nhật ${livingSummary.displayName}`,
      person: livingSummary,
      lunarDate: { year: occurrence.getUTCFullYear(), month: 4, day: 7, leap: false },
      nextOccurrenceSolar: isoDate(occurrence),
      nextOccurrenceLunarYear: occurrence.getUTCFullYear(),
      daysUntil: 34,
      targetBranch: livingSummary.primaryBranch ?? null,
      isClanLevel: false,
      reminderOffsets: REMINDER_OFFSETS,
    });
  }

  // Ba việc họ không gắn với một cá nhân nào — trước V10 cả ba cùng đi ra dây
  // dưới mã `KHAC`, nên qua API một buổi họp họ không phân biệt được với một
  // đám cưới.
  const clanOccasions: ReadonlyArray<{ id: string; eventType: EventType; title: string; offset: number }> = [
    { id: "ev-hop-ho", eventType: "HOP_HO", title: "Họp họ đầu xuân", offset: 12 },
    { id: "ev-khanh-thanh", eventType: "KHANH_THANH", title: "Khánh thành tu bổ từ đường", offset: 47 },
    { id: "ev-cuoi-hoi", eventType: "CUOI_HOI", title: "Lễ cưới con cháu Chi Nhất", offset: 63 },
  ];
  for (const occasion of clanOccasions) {
    const occurrence = addDays(today, occasion.offset);
    const seed = hash(occasion.id);
    generated.push({
      id: occasion.id,
      eventType: occasion.eventType,
      title: occasion.title,
      person: null,
      lunarDate: fakeLunar(seed, occurrence.getUTCFullYear(), occurrence.getUTCMonth() + 1),
      nextOccurrenceSolar: isoDate(occurrence),
      nextOccurrenceLunarYear: occurrence.getUTCFullYear(),
      daysUntil: occasion.offset,
      targetBranch: null,
      isClanLevel: occasion.eventType !== "CUOI_HOI",
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
