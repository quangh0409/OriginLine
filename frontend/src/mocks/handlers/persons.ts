import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { isAliveMock, resolvePersonMock, toSummaryMock } from "@/mocks/person-detail";
import { projectPersonForRole } from "@/mocks/privacy";
import { unaccent } from "@/mocks/unaccent";
import { canSeeLivingPersons, resolveMockRole, type MockRole } from "./role";
import type {
  ConflictProblem,
  CreatePersonRequest,
  PersonDto,
  PersonSummaryDto,
  PersonSummaryPage,
  Problem,
  TabooConflict,
  UpdatePersonRequest,
} from "@/types/api";

function notFound(instance: string) {
  const problem: Problem = {
    type: "about:blank",
    title: "Không tìm thấy nhân khẩu",
    status: 404,
    code: "NOT_FOUND",
    instance,
  };
  return HttpResponse.json(problem, { status: 404 });
}

function forbidden(title: string, instance: string) {
  const problem: Problem = {
    type: "about:blank",
    title,
    status: 403,
    code: "FORBIDDEN",
    instance,
  };
  return HttpResponse.json(problem, { status: 403 });
}

function etagFor(person: PersonDto): string {
  return `"v${person.version ?? 1}"`;
}

/**
 * In-memory overlay for persons created/edited during a session. Without it,
 * F4 was a dead end in the mock: you could POST and get a 201 back, then
 * immediately 404 on the profile page you were redirected to. Resets on
 * reload, like every other piece of mock state.
 */
const overlay = new Map<string, PersonDto>();

function loadPerson(id: string): PersonDto | undefined {
  return overlay.get(id) ?? resolvePersonMock(id);
}

/**
 * Guest non-disclosure, applied before anything else: a living person must
 * read as "does not exist" (404), never as "exists but forbidden" (403) —
 * a 403 confirms the person is real, which is the leak the tiering exists to
 * prevent (contracts/README §3).
 */
function hiddenFromCaller(id: string, role: MockRole): boolean {
  const alive = overlay.get(id)?.isAlive ?? isAliveMock(id);
  return alive === true && !canSeeLivingPersons(role);
}

// ---------------------------------------------------------------------------
// Kỵ húy (FR-1.6)
// ---------------------------------------------------------------------------

/**
 * Scans every known ancestor for a collision with the submitted tên húy.
 * The real check runs in Postgres against the `name_unaccented` column across
 * all name layers and is bounded by a policy the clan council still has to
 * decide (contracts/README §6.5 — how many generations, whether unaccented
 * matches count). This mock scans the whole graph and reports both match
 * kinds so the FE can show them differently.
 */
function findTabooConflicts(huyName: string | undefined): TabooConflict[] {
  if (!huyName || huyName.trim().length === 0) return [];
  const graph = getMockGraph();
  const target = huyName.trim();
  const targetUnaccented = unaccent(target);

  const conflicts: TabooConflict[] = [];
  for (const person of graph.personsById.values()) {
    if (conflicts.length >= 4) break;
    const exact = person.displayName === target;
    const unaccented = !exact && unaccent(person.displayName) === targetUnaccented;
    if (!exact && !unaccented) continue;

    conflicts.push({
      ancestorPersonId: person.id,
      ancestorDisplayName: person.displayName,
      ancestorGeneration: person.generation,
      tabooName: person.displayName,
      matchedNameType: "HUY",
      matchKind: exact ? "EXACT" : "UNACCENTED",
      relationHint: person.primaryBranch?.name
        ? `Đời thứ ${person.generation} · ${person.primaryBranch.name}`
        : `Đời thứ ${person.generation}`,
    });
  }

  // Earliest generation first: the older the ancestor, the heavier the taboo.
  return conflicts.sort(
    (a, b) => (a.ancestorGeneration ?? 0) - (b.ancestorGeneration ?? 0)
  );
}

function tabooProblem(conflicts: TabooConflict[], instance: string, overrideField: string) {
  const problem: ConflictProblem = {
    type: "https://giapha.example.vn/problems/ky-huy-conflict",
    title: "Trùng tên húy bậc trên",
    status: 409,
    code: "KY_HUY_CONFLICT",
    detail: `Tên húy "${conflicts[0]?.tabooName}" trùng với ${conflicts[0]?.ancestorDisplayName}.`,
    instance,
    overridable: true,
    overrideField,
    conflicts,
  };
  return HttpResponse.json(problem, { status: 409 });
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

interface SearchFilters {
  generation?: number;
  branchId?: string;
  nativePlace?: string;
  isAlive?: boolean;
}

function searchGraph(q: string, role: MockRole, filters: SearchFilters): PersonSummaryDto[] {
  const graph = getMockGraph();
  const needle = unaccent(q);
  const results: PersonSummaryDto[] = [];

  for (const raw of graph.personsById.values()) {
    if (raw.isAlive && !canSeeLivingPersons(role)) continue;
    if (filters.isAlive !== undefined && raw.isAlive !== filters.isAlive) continue;
    if (filters.generation !== undefined && raw.generation !== filters.generation) continue;
    if (filters.branchId && raw.primaryBranch?.id !== filters.branchId) continue;
    if (filters.nativePlace && !unaccent(raw.nativePlace ?? "").includes(unaccent(filters.nativePlace))) {
      continue;
    }
    if (needle.length > 0 && !unaccent(raw.displayName).includes(needle)) continue;

    const person = loadPerson(raw.id);
    if (!person) continue;
    results.push({ ...toSummaryMock(person), matchedNameType: "HUY" });
  }

  // Overlay entries (created this session) are not in the graph yet.
  for (const person of overlay.values()) {
    if (graph.personsById.has(person.id)) continue;
    if (person.isAlive && !canSeeLivingPersons(role)) continue;
    const name = person.displayName ?? "";
    if (needle.length > 0 && !unaccent(name).includes(needle)) continue;
    results.push(toSummaryMock(person));
  }

  return results.sort(
    (a, b) => (a.generation ?? 0) - (b.generation ?? 0) || a.displayName.localeCompare(b.displayName, "vi")
  );
}

// ---------------------------------------------------------------------------

export const personHandlers = [
  http.post(`${API_BASE_URL}/api/v1/persons`, async ({ request }) => {
    const input = (await request.json()) as CreatePersonRequest;
    const role = resolveMockRole(request);

    // Phase 1: MEMBER never gets the 202 change-request flow (that's Phase 2).
    if (role === "member" || role === "guest") {
      return forbidden(
        "Chỉ Trưởng chi trở lên được thêm nhân khẩu ở Giai đoạn 1",
        "/api/v1/persons"
      );
    }

    const huyName = input.names.find((n) => n.nameType === "HUY")?.fullName;
    const conflicts = findTabooConflicts(huyName);
    if (conflicts.length > 0 && !input.confirmTabooOverride) {
      return tabooProblem(conflicts, "/api/v1/persons", "confirmTabooOverride");
    }

    const id = `p-new-${Date.now().toString(36)}`;
    const created: PersonDto = {
      id,
      isAlive: input.isAlive,
      gender: input.gender,
      generation: null, // backend derives this from the graph, never the client
      displayName: input.names.find((n) => n.isPrimary)?.fullName ?? input.names[0]?.fullName,
      names: input.names.map((n) => ({ ...n, isPrimary: n.isPrimary ?? false })),
      birth: input.birth ?? null,
      death: input.death ?? null,
      occupation: input.occupation ?? null,
      nativePlace: input.nativePlace ?? null,
      currentPlaceProvince: input.currentPlaceProvince ?? null,
      currentPlaceFull: input.currentPlaceFull ?? null,
      biography: input.biography ?? null,
      contact: input.contact ?? null,
      privacyLevel: input.privacyLevel ?? "DEFAULT",
      version: 1,
      createdAt: new Date().toISOString(),
      meta: {
        visibleTier: input.isAlive ? "T3" : "PUBLIC",
        canEdit: true,
        canDelete: true,
        canRequestCorrection: false,
      },
    };
    overlay.set(id, created);

    return HttpResponse.json(created, { status: 201, headers: { ETag: etagFor(created) } });
  }),

  http.get(`${API_BASE_URL}/api/v1/persons/search`, ({ request }) => {
    const url = new URL(request.url);
    const q = (url.searchParams.get("q") ?? "").trim();
    const role = resolveMockRole(request);
    const page = Number(url.searchParams.get("page") ?? "0");
    const size = Number(url.searchParams.get("size") ?? "20");
    const generationParam = url.searchParams.get("generation");
    const isAliveParam = url.searchParams.get("isAlive");

    const all = searchGraph(q, role, {
      generation: generationParam ? Number(generationParam) : undefined,
      branchId: url.searchParams.get("branchId") ?? undefined,
      nativePlace: url.searchParams.get("nativePlace") ?? undefined,
      isAlive: isAliveParam === null ? undefined : isAliveParam === "true",
    });

    const start = page * size;
    const items = all.slice(start, start + size);

    const response: PersonSummaryPage = {
      items,
      page: {
        page,
        size,
        // Already privacy-filtered: a guest's total never counts living people.
        totalElements: all.length,
        totalPages: Math.max(1, Math.ceil(all.length / size)),
        hasNext: start + size < all.length,
      },
    };
    return HttpResponse.json(response);
  }),

  http.get(`${API_BASE_URL}/api/v1/persons/:id`, ({ params, request }) => {
    const id = params.id as string;
    const role = resolveMockRole(request);

    if (hiddenFromCaller(id, role)) return notFound(`/api/v1/persons/${id}`);

    const person = loadPerson(id);
    if (!person) return notFound(`/api/v1/persons/${id}`);

    const projected = projectPersonForRole(person, role);
    return HttpResponse.json(projected, { headers: { ETag: etagFor(person) } });
  }),

  http.patch(`${API_BASE_URL}/api/v1/persons/:id`, async ({ params, request }) => {
    const id = params.id as string;
    const role = resolveMockRole(request);

    if (hiddenFromCaller(id, role)) return notFound(`/api/v1/persons/${id}`);
    const existing = loadPerson(id);
    if (!existing) return notFound(`/api/v1/persons/${id}`);

    if (role === "guest") {
      return forbidden("Cần đăng nhập để sửa hồ sơ", `/api/v1/persons/${id}`);
    }
    if (role === "member") {
      // Phase 1: correction requests are not wired yet, so a member editing
      // someone else's record is a hard 403 rather than a 202 (README §5.12).
      return forbidden(
        "Thành viên chỉ sửa được hồ sơ của chính mình; các thay đổi khác cần Trưởng chi duyệt",
        `/api/v1/persons/${id}`
      );
    }

    const ifMatch = request.headers.get("If-Match");
    if (!ifMatch) {
      const problem: Problem = {
        type: "about:blank",
        title: "Thiếu header If-Match",
        status: 412,
        code: "PRECONDITION_REQUIRED",
        instance: `/api/v1/persons/${id}`,
      };
      return HttpResponse.json(problem, { status: 412 });
    }
    if (ifMatch !== etagFor(existing)) {
      const problem: Problem = {
        type: "about:blank",
        title: "Bản ghi đã bị người khác sửa",
        status: 409,
        code: "OPTIMISTIC_LOCK_CONFLICT",
        instance: `/api/v1/persons/${id}`,
      };
      return HttpResponse.json(problem, { status: 409 });
    }

    const patch = (await request.json()) as UpdatePersonRequest;

    if (patch.names) {
      const huyName = patch.names.find((n) => n.nameType === "HUY")?.fullName;
      // Renaming to an ancestor's taboo name is the same two-call flow as
      // creating one — and must not be skipped just because it is a PATCH.
      const conflicts = findTabooConflicts(huyName).filter((c) => c.ancestorPersonId !== id);
      if (conflicts.length > 0 && !patch.confirmTabooOverride) {
        return tabooProblem(conflicts, `/api/v1/persons/${id}`, "confirmTabooOverride");
      }
    }

    const updated: PersonDto = {
      ...existing,
      ...patch,
      names: patch.names
        ? patch.names.map((n) => ({ ...n, isPrimary: n.isPrimary ?? false }))
        : existing.names,
      displayName: patch.names
        ? patch.names.find((n) => n.isPrimary)?.fullName ?? existing.displayName
        : existing.displayName,
      version: (existing.version ?? 1) + 1,
      updatedAt: new Date().toISOString(),
    };
    for (const field of patch.clearFields ?? []) {
      (updated as unknown as Record<string, unknown>)[field] = undefined;
    }
    overlay.set(id, updated);

    return HttpResponse.json(projectPersonForRole(updated, role), {
      headers: { ETag: etagFor(updated) },
    });
  }),

  http.delete(`${API_BASE_URL}/api/v1/persons/:id`, ({ params, request }) => {
    const id = params.id as string;
    const role = resolveMockRole(request);
    if (hiddenFromCaller(id, role)) return notFound(`/api/v1/persons/${id}`);

    const existing = loadPerson(id);
    if (!existing) return notFound(`/api/v1/persons/${id}`);
    if (role !== "admin" && role !== "branch-head") {
      return forbidden("Chỉ Trưởng chi trở lên được xoá nhân khẩu", `/api/v1/persons/${id}`);
    }

    // Soft delete only, per CLAUDE.md — the node stays in the graph.
    overlay.set(id, { ...existing, isDeleted: true, version: (existing.version ?? 1) + 1 });
    return new HttpResponse(null, { status: 204 });
  }),
];
