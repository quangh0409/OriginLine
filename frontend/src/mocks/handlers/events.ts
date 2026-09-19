import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockEvents } from "@/mocks/events-calendar";
import { canSeeBirthYearMock } from "@/mocks/privacy";
import { canSeeLivingPersons, resolveMockRole, type MockRole } from "./role";
import type { EventDto, EventPage, EventType } from "@/types/api";

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

export const eventHandlers = [
  http.get(`${API_BASE_URL}/api/v1/events`, ({ request }) => {
    const url = new URL(request.url);
    const role = resolveMockRole(request);
    const all = getMockEvents();

    let items = all.filter((e) => visibleToCaller(e, role));

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
];
