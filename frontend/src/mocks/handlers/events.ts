import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import {
  addMockEvent,
  findMockEvent,
  getMockEvents,
  isMockEventDeleted,
  mockEventVersion,
  nextMockEventId,
  softDeleteMockEvent,
  updateMockEvent,
} from "@/mocks/events-calendar";
import { canSeeBirthYearMock } from "@/mocks/privacy";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { resolvePersonMock, toSummaryMock } from "@/mocks/person-detail";
import { canWriteInBranch, identityOf } from "@/mocks/identity";
import { canSeeLivingPersons, resolveMockRole, type MockRole } from "./role";
import type {
  BranchRef,
  EventCreateRequest,
  EventDto,
  EventPage,
  EventType,
  EventUpdateRequest,
  Problem,
} from "@/types/api";

/**
 * 13 mã hợp đồng GHI ĐƯỢC — đúng `EventType`. Khác chiều ĐỌC: một mã lạ khi
 * ghi bị TỪ CHỐI (`400`), không lặng lẽ rơi về `KHAC` như một hàng cũ trong
 * CSDL mà chiều đọc gặp phải.
 */
const WRITABLE_EVENT_TYPES = new Set<string>([
  "GIO_TO",
  "GIO_HO",
  "GIO_CHI",
  "GIO_THUONG",
  "TIEU_TUONG",
  "DAI_TUONG",
  "CHAP_MA",
  "MUNG_THO",
  "SINH_NHAT",
  "KHANH_THANH",
  "HOP_HO",
  "CUOI_HOI",
  "KHAC",
]);

/** `ck_event_gio_has_person` — một giỗ thường luôn là giỗ của một người. */
const EVENT_TYPES_REQUIRING_PERSON = new Set<EventType>(["GIO_THUONG"]);

/** Mirrors the contract's `upcomingDays` ceiling. */
const MAX_UPCOMING_DAYS = 400;

function todayInVietnamIso(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function addDaysIso(iso: string, days: number): string {
  const date = new Date(`${iso}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/**
 * `branchId` includes the branch's whole ltree subtree, and clan-level events
 * always match regardless of filter (contracts/openapi.yaml `/events`).
 * Resolved through the event data itself because the mock has no separate
 * branch table.
 */
function branchPathFor(events: EventDto[], branchId: string): string | undefined {
  return events.find((e) => e.targetBranch?.id === branchId)?.targetBranch?.path;
}

/**
 * Chi/ngành hoàn chỉnh (id + name + path) từ id — dùng cho `POST`/`PATCH`, nơi
 * thân yêu cầu chỉ gửi `scopeBranchId` chứ không gửi cả `BranchRef`. Suy từ
 * `primaryBranch` của các nhân khẩu trong đồ thị giả lập, giống hệt cách
 * `handlers/graphql.ts` dựng danh sách chi cho ô lọc F6.
 */
function branchRefById(branchId: string): BranchRef | undefined {
  for (const person of getMockGraph().personsById.values()) {
    if (person.primaryBranch?.id === branchId) return person.primaryBranch;
  }
  return undefined;
}

/**
 * Sự kiện này có đi ra cho người gọi không.
 *
 * Hai tầng, và tầng thứ hai là tầng V10 mới thêm:
 *
 * 1. **Sự kiện gắn với người còn sống theo đúng phân tầng của người ấy.** Khách
 *    không thấy người còn sống nào, nên cũng không thấy lễ của họ — nếu không
 *    thì một dòng "Mừng thọ ông X" tự nó đã tiết lộ rằng ông X tồn tại và đang
 *    còn sống.
 * 2. **`SINH_NHAT` chặt hơn `MUNG_THO`.** Ngày diễn ra sinh nhật *chính là*
 *    ngày sinh, mà ngày sinh nằm trong nhóm `birthDetailAndPhoto` do chủ thể tự
 *    bật. Nên một thành viên cùng chi vẫn có thể **không** thấy sinh nhật của
 *    người bên cạnh mình — đúng như họ không thấy năm sinh trên thẻ phả đồ.
 *    Mừng thọ thì không chịu ràng buộc ấy: đó là việc của cả họ, và mốc
 *    60/70/80/90 không tiết lộ ngày sinh.
 *
 * Cả hai tầng đều lọc **trước** khi phân trang và đếm, để `totalElements` không
 * đếm hộ người xem số dòng đã bị giữ lại.
 */
function visibleToCaller(event: EventDto, role: MockRole): boolean {
  const person = event.person;
  if ((person?.isAlive ?? false) && !canSeeLivingPersons(role)) return false;

  if (event.eventType === "SINH_NHAT") {
    if (!person) return false;
    return canSeeBirthYearMock(
      {
        id: person.id,
        isAlive: person.isAlive,
        birthYear: person.birthYear ?? null,
        primaryBranch: person.primaryBranch ?? null,
      },
      role
    );
  }

  return true;
}

function notFound(instance: string) {
  const problem: Problem = {
    type: "about:blank",
    title: "Không tìm thấy việc họ",
    status: 404,
    code: "NOT_FOUND",
    instance,
  };
  return HttpResponse.json(problem, { status: 404 });
}

function forbidden(instance: string, detail: string) {
  const problem: Problem = {
    type: "about:blank",
    title: "Không đủ quyền",
    status: 403,
    code: "FORBIDDEN",
    instance,
    detail,
  };
  return HttpResponse.json(problem, { status: 403 });
}

function validationFailed(instance: string, detail: string) {
  const problem: Problem = {
    type: "about:blank",
    title: "Yêu cầu không hợp lệ",
    status: 400,
    code: "VALIDATION_FAILED",
    instance,
    detail,
  };
  return HttpResponse.json(problem, { status: 400 });
}

/** `409 GIO_DATE_FROM_PERSON` — xem javadoc `assertGioDateMatchesPerson`. */
function gioDateConflict(instance: string) {
  const problem: Problem = {
    type: "about:blank",
    title: "Ngày giỗ không khớp hồ sơ nhân khẩu",
    status: 409,
    code: "GIO_DATE_FROM_PERSON",
    instance,
    detail: "Ngày giỗ lấy từ hồ sơ nhân khẩu, muốn đổi thì đổi ở hồ sơ.",
  };
  return HttpResponse.json(problem, { status: 409 });
}

function etagFor(id: string): string {
  return `"v${mockEventVersion(id)}"`;
}

/** Băm ổn định — cùng kỹ thuật với `events-calendar.ts`, chỉ để lần nào cũng ra cùng một kết quả. */
function hash(seed: string): number {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i += 1) {
    h ^= seed.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0) / 4294967296;
}

/**
 * Quy đổi GIẢ cho một việc mới tạo/sửa qua mock — KHÔNG phải Hồ Ngọc Đức thật.
 *
 * Một việc MỘT LẦN có `solarDate` thì dùng NGUYÊN VĂN mốc ấy — không có gì để
 * "quy đổi", người dùng đã gõ đúng ngày dương của chính nó. Một việc LẶP LẠI
 * HẰNG NĂM thì không có mốc dương nào để dùng lại (đó chính là lý do máy chủ
 * thật phải tính), nên mock chọn một ngày giả trong 366 ngày tới bằng cùng kỹ
 * thuật băm mà `events-calendar.ts` dùng cho toàn bộ dữ liệu mẫu khác — một
 * HÌNH DẠNG hợp lý để màn hình có gì đó vẽ ra, không phải một phép quy đổi.
 */
function fakeNextOccurrence(
  id: string,
  input: EventCreateRequest
): { nextOccurrenceSolar: string; nextOccurrenceLunarYear: number; daysUntil: number } {
  if (!input.recurringAnnually && input.solarDate) {
    const today = todayInVietnamIso();
    const days = Math.round(
      (new Date(`${input.solarDate}T00:00:00Z`).getTime() - new Date(`${today}T00:00:00Z`).getTime()) /
        86_400_000
    );
    return {
      nextOccurrenceSolar: input.solarDate,
      nextOccurrenceLunarYear: Number(input.solarDate.slice(0, 4)),
      daysUntil: days,
    };
  }
  const today = todayInVietnamIso();
  const offset = Math.floor(hash(id) * 366);
  const occurrence = addDaysIso(today, offset);
  return {
    nextOccurrenceSolar: occurrence,
    nextOccurrenceLunarYear: Number(occurrence.slice(0, 4)),
    daysUntil: offset,
  };
}

/**
 * `GIO_THUONG` mà ngày âm gửi lên khác `person.death_lunar` bị từ chối
 * `409 GIO_DATE_FROM_PERSON` — máy chủ không lặng lẽ nhận một ngày giỗ lệch
 * khỏi hồ sơ rồi không có tác dụng gì. Chỉ so `day`/`month`/`leap`: đó là
 * phần lặp lại mỗi năm; `year` (nếu người dùng gõ) không phải phần máy chủ
 * đối chiếu ở đây.
 *
 * Best-effort: nếu không tra được ngày mất của người này (dữ liệu mẫu chưa
 * có `death.lunar`), BỎ QUA phép so — một bản demo thiếu góc dữ liệu ấy
 * không nên biến thành một `409` không giải thích được.
 */
function gioDateMatchesPerson(personId: string, lunarDate: EventCreateRequest["lunarDate"]): boolean {
  const person = resolvePersonMock(personId);
  const deathLunar = person?.death?.lunar;
  if (!deathLunar) return true;
  return (
    deathLunar.day === lunarDate.day &&
    deathLunar.month === lunarDate.month &&
    Boolean(deathLunar.leap) === Boolean(lunarDate.leap)
  );
}

/**
 * Ghi chú GIẢ về việc "ngày đã bị dời" — không phải lịch Hồ Ngọc Đức thật.
 * Chỉ để chứng minh `adjustmentNote` có đường tới giao diện: ngày 30 âm được
 * coi là rơi vào một "tháng thiếu" giả định và lùi về 29, đúng nguyên tắc
 * "chỉ lùi sớm, không bao giờ đẩy muộn". Câu chữ cố định tiếng Việt — mock
 * không chạy trong React nên không gọi được `useTranslations`, và
 * `adjustmentNote` vốn là văn bản MÁY CHỦ soạn sẵn (như `title`), không phải
 * khoá dịch phía client.
 */
function fakeAdjustmentNote(day: number, month: number): string | null {
  if (day !== 30) return null;
  return `Tháng ${month} năm nay là tháng thiếu — ngày 30 đã lùi về 29.`;
}

function toEventDto(id: string, input: EventCreateRequest): EventDto {
  const targetBranch =
    !input.clanWide && input.scopeBranchId ? (branchRefById(input.scopeBranchId) ?? null) : null;
  const occurrence = fakeNextOccurrence(id, input);
  const person = input.personId ? (resolvePersonMock(input.personId) ?? null) : null;
  return {
    id,
    eventType: input.eventType,
    title: input.title,
    person: person ? toSummaryMock(person) : null,
    lunarDate: {
      year: input.lunarDate.year ?? occurrence.nextOccurrenceLunarYear,
      day: input.lunarDate.day,
      month: input.lunarDate.month,
      leap: Boolean(input.lunarDate.leap),
    },
    ...occurrence,
    targetBranch,
    isClanLevel: input.clanWide,
    reminderOffsets: [7, 3, 1],
    note: input.description ?? null,
    location: input.location ?? null,
    solarDate: !input.recurringAnnually ? (input.solarDate ?? null) : null,
    lunarBased: true,
    recurringAnnually: input.recurringAnnually,
    adjustmentNote: fakeAdjustmentNote(input.lunarDate.day, input.lunarDate.month),
  };
}

/** Vai nào được ghi lên `/events` — cùng nguyên tắc branch-scoped RBAC với `/persons`. */
function assertCanWrite(role: MockRole, input: EventCreateRequest, instance: string) {
  const identity = identityOf(role);
  if (identity.appUserId === null || identity.role === "MEMBER") {
    return forbidden(instance, "Chỉ Trưởng cành/chi/họ mới tạo/sửa được việc họ");
  }

  if (!WRITABLE_EVENT_TYPES.has(input.eventType)) {
    return validationFailed(instance, "eventType không hợp lệ");
  }
  if (EVENT_TYPES_REQUIRING_PERSON.has(input.eventType) && !input.personId) {
    return validationFailed(instance, "Thiếu personId cho GIO_THUONG (ck_event_gio_has_person)");
  }
  if (!input.recurringAnnually && !input.lunarDate?.year) {
    return validationFailed(
      instance,
      "Thiếu lunarDate.year cho một việc chỉ diễn ra một lần (ck_event_oneoff_lunar_year)"
    );
  }
  if (
    input.eventType === "GIO_THUONG" &&
    input.personId &&
    !gioDateMatchesPerson(input.personId, input.lunarDate)
  ) {
    return gioDateConflict(instance);
  }

  if (input.clanWide) {
    if (!identity.clanWide) {
      return forbidden(
        instance,
        "Chỉ Hội đồng Tộc biểu / Tộc trưởng mới phát được lời nhắc cho cả dòng họ"
      );
    }
    return null;
  }
  if (!input.scopeBranchId) {
    return validationFailed(instance, "Thiếu phạm vi: cần clanWide=true hoặc scopeBranchId");
  }
  const path = branchRefById(input.scopeBranchId)?.path;
  if (!canWriteInBranch(identity, path)) {
    return forbidden(instance, "Chi/ngành này nằm ngoài phạm vi bạn được giao");
  }
  return null;
}

export const eventHandlers = [
  http.get(`${API_BASE_URL}/api/v1/events`, ({ request }) => {
    const url = new URL(request.url);
    const role = resolveMockRole(request);
    const all = getMockEvents();

    let items = all.filter((e) => !isMockEventDeleted(e.id) && visibleToCaller(e, role));

    const upcomingDaysParam = url.searchParams.get("upcomingDays");
    let from = url.searchParams.get("from") ?? undefined;
    let to = url.searchParams.get("to") ?? undefined;

    // `from`/`to` win over `upcomingDays` when both are sent, per the contract.
    if (!from && !to && upcomingDaysParam) {
      const days = Math.min(Math.max(Number(upcomingDaysParam) || 0, 1), MAX_UPCOMING_DAYS);
      from = todayInVietnamIso();
      to = addDaysIso(from, days);
    }

    if (from) items = items.filter((e) => (e.nextOccurrenceSolar ?? "") >= from);
    if (to) items = items.filter((e) => (e.nextOccurrenceSolar ?? "") <= to);

    const eventTypes = url.searchParams.getAll("eventType") as EventType[];
    if (eventTypes.length > 0) {
      items = items.filter((e) => eventTypes.includes(e.eventType));
    }

    const branchId = url.searchParams.get("branchId");
    if (branchId) {
      const path = branchPathFor(all, branchId);
      items = items.filter((e) => {
        if (e.isClanLevel) return true;
        const target = e.targetBranch;
        if (!target) return false;
        if (target.id === branchId) return true;
        return path ? target.path.startsWith(`${path}.`) : false;
      });
    }

    const personId = url.searchParams.get("personId");
    if (personId) items = items.filter((e) => e.person?.id === personId);

    // Default sort is `nextOccurrenceSolar,asc`; `eventType` is the only other
    // field the contract allows.
    const [sortField, sortDir] = (url.searchParams.get("sort") ?? "nextOccurrenceSolar,asc").split(",");
    const dir = sortDir === "desc" ? -1 : 1;
    items = [...items].sort((a, b) => {
      const left = sortField === "eventType" ? a.eventType : (a.nextOccurrenceSolar ?? "9999");
      const right = sortField === "eventType" ? b.eventType : (b.nextOccurrenceSolar ?? "9999");
      return left.localeCompare(right) * dir;
    });

    const page = Number(url.searchParams.get("page") ?? "0");
    const size = Number(url.searchParams.get("size") ?? "20");
    const start = page * size;

    const response: EventPage = {
      items: items.slice(start, start + size),
      page: {
        page,
        size,
        totalElements: items.length,
        totalPages: Math.max(1, Math.ceil(items.length / size)),
        hasNext: start + size < items.length,
        sort: `${sortField},${dir === -1 ? "desc" : "asc"}`,
      },
    };
    return HttpResponse.json(response);
  }),

  http.get(`${API_BASE_URL}/api/v1/events/:id`, ({ params, request }) => {
    const id = params.id as string;
    const role = resolveMockRole(request);
    const event = findMockEvent(id);
    if (!event || !visibleToCaller(event, role)) return notFound(`/api/v1/events/${id}`);
    return HttpResponse.json(
      { ...event, version: mockEventVersion(id) },
      { headers: { ETag: etagFor(id) } }
    );
  }),

  /**
   * `POST /events` — Đợt 2. Hợp đồng cho endpoint này chưa lên
   * `contracts/openapi.yaml` lúc mock được viết; thân yêu cầu dưới đây là
   * đặc tả PO đã chốt cho form, cập nhật một lần sau khi backend xây xong
   * (xem javadoc `EventCreateRequest`).
   */
  http.post(`${API_BASE_URL}/api/v1/events`, async ({ request }) => {
    const role = resolveMockRole(request);
    const input = (await request.json()) as EventCreateRequest;
    const instance = "/api/v1/events";

    if (!input.title?.trim()) return validationFailed(instance, "Thiếu title");
    if (
      !Number.isInteger(input.lunarDate?.day) ||
      input.lunarDate.day < 1 ||
      input.lunarDate.day > 30
    ) {
      return validationFailed(instance, "lunarDate.day phải từ 1 đến 30");
    }
    if (
      !Number.isInteger(input.lunarDate?.month) ||
      input.lunarDate.month < 1 ||
      input.lunarDate.month > 12
    ) {
      return validationFailed(instance, "lunarDate.month phải từ 1 đến 12");
    }

    const denied = assertCanWrite(role, input, instance);
    if (denied) return denied;

    const created = addMockEvent(toEventDto(nextMockEventId(), input));
    const body = { ...created, version: mockEventVersion(created.id) };
    return HttpResponse.json(body, { status: 201, headers: { ETag: etagFor(created.id) } });
  }),

  http.patch(`${API_BASE_URL}/api/v1/events/:id`, async ({ params, request }) => {
    const id = params.id as string;
    const instance = `/api/v1/events/${id}`;
    const role = resolveMockRole(request);
    const existing = findMockEvent(id);
    if (!existing) return notFound(instance);

    const ifMatch = request.headers.get("If-Match");
    if (!ifMatch) {
      const problem: Problem = {
        type: "about:blank",
        title: "Thiếu header If-Match",
        status: 412,
        code: "PRECONDITION_REQUIRED",
        instance,
      };
      return HttpResponse.json(problem, { status: 412 });
    }
    if (ifMatch !== etagFor(id)) {
      const problem: Problem = {
        type: "about:blank",
        title: "Việc họ này vừa bị người khác sửa",
        status: 409,
        code: "OPTIMISTIC_LOCK_CONFLICT",
        instance,
      };
      return HttpResponse.json(problem, { status: 409 });
    }

    const input = (await request.json()) as EventUpdateRequest;
    const denied = assertCanWrite(role, input, instance);
    if (denied) return denied;

    const patch = toEventDto(id, input);
    const updated = updateMockEvent(id, patch);
    if (!updated) return notFound(instance);
    const body = { ...updated, version: mockEventVersion(id) };
    return HttpResponse.json(body, { headers: { ETag: etagFor(id) } });
  }),

  http.delete(`${API_BASE_URL}/api/v1/events/:id`, ({ params, request }) => {
    const id = params.id as string;
    const instance = `/api/v1/events/${id}`;
    const role = resolveMockRole(request);
    const existing = findMockEvent(id);
    if (!existing) return notFound(instance);

    const identity = identityOf(role);
    const scopePath = existing.targetBranch?.path ?? null;
    const canDelete = existing.isClanLevel ? identity.clanWide : canWriteInBranch(identity, scopePath);
    if (!canDelete) {
      return forbidden(instance, "Chỉ Trưởng cành/chi/họ trong đúng phạm vi mới xoá được việc họ này");
    }

    softDeleteMockEvent(id);
    return new HttpResponse(null, { status: 204 });
  }),
];
