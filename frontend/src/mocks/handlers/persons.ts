import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { isAliveMock, resolvePersonMock, toSummaryMock } from "@/mocks/person-detail";
import { projectPersonForRole } from "@/mocks/privacy";
import { relationshipsForMock } from "@/mocks/person-relationships";
import {
  allPrivate,
  clearPrivacy,
  mergePrivacy,
  privacyOf,
} from "@/mocks/privacy-settings";
import { unaccent } from "@/mocks/unaccent";
import { canSeeLivingPersons, resolveMockRole, type MockRole } from "./role";
import { canWriteInBranch, identityOf } from "@/mocks/identity";
import type {
  ConflictProblem,
  CreatePersonRequest,
  DuplicateCandidate,
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

function forbidden(
  title: string,
  instance: string,
  options: { code?: Problem["code"]; detail?: string } = {}
) {
  const problem: Problem = {
    type: "about:blank",
    title,
    status: 403,
    // Đúng vai nhưng sai chi là BRANCH_SCOPE_VIOLATION, không phải FORBIDDEN:
    // hai tình huống dẫn tới hai hành động khác nhau của người dùng.
    code: options.code ?? "FORBIDDEN",
    detail: options.detail,
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

/**
 * Gắn `relationships` (quan hệ một bậc, kèm `otherPerson`) vào hồ sơ đã lọc.
 *
 * Đặt SAU `projectPersonForRole` chứ không phải bên trong nó, vì hai phép lọc
 * trả lời hai câu khác nhau: phép kia hỏi "trường nào của CHÍNH người này đi
 * ra", phép này hỏi "cạnh nào có ĐẦU KIA hiển thị được". Trộn lại thì một
 * ngày nào đó sẽ có người thêm nhánh "ẩn bớt thuộc tính của cạnh" — mà cạnh
 * thì không có trạng thái ấy: hoặc cả cạnh đi ra, hoặc nó biến mất.
 *
 * Nhân khẩu chưa vào đồ thị (vừa tạo trong phiên) thì **không có khoá
 * `relationships`** — vắng mặt, không phải mảng rỗng.
 */
function withRelationships(person: PersonDto, role: MockRole): PersonDto {
  const relationships = relationshipsForMock(person.id, role);
  return relationships ? { ...person, relationships } : person;
}

// ---------------------------------------------------------------------------
// Kỵ húy (FR-1.6)
// ---------------------------------------------------------------------------

/**
 * Bậc trên "còn sống ở một chi khác" — khoá **cố ý không tra được**.
 *
 * Không có dòng nào mang id này trong `data.ts` hay trong đồ thị, nên
 * `GET /persons/p-781` rơi đúng vào nhánh `notFound()` bên dưới. Đó chính là
 * điều cần dựng: hợp đồng trả **cùng một `404`** cho "không có" và cho "có
 * nhưng bạn không được biết", vì phân biệt hai thứ ấy đã là rò rỉ. Bộ giả lập
 * không cần — và không được — biết mình đang dựng ca nào.
 *
 * Cùng lối với `p-778` của bộ dữ liệu nhập liệu (`src/mocks/data-import.ts`).
 */
const HIDDEN_ANCESTOR_ID = "p-781";

/** Bậc trên đã khuất, tra được đầy đủ: cụ Thuỷ Tổ (`data.ts`). */
const VISIBLE_ANCESTOR_ID = "p-001";

/** Gõ tên này vào ô tên húy để dựng ca bậc trên KHÔNG tra được. */
const TABOO_TRIGGER_HIDDEN = "nguyen van bac tren an";

/**
 * Scans every known ancestor for a collision with the submitted tên húy.
 * The real check runs in Postgres against the `name_unaccented` column across
 * all name layers and is bounded by a policy the clan council still has to
 * decide (contracts/README §6.5 — how many generations, whether unaccented
 * matches count). This mock scans the whole graph and reports both match
 * kinds so the FE can show them differently.
 *
 * <h2>Thân lỗi chỉ mang KHOÁ</h2>
 * Bốn trường cũ — `ancestorDisplayName`, `ancestorGeneration`, `tabooName`,
 * `relationHint` — đã bị bỏ khỏi hợp đồng và **không được dựng lại ở đây**.
 * Truy vấn dò kỵ húy chọn bậc trên theo *đời thứ*, không theo sống/mất và
 * không theo chi, nên "bậc trên" có thể là người còn sống ở một chi khác; thân
 * lỗi 409 đi thẳng ra HTTP, không qua bộ lọc phân tầng riêng tư.
 *
 * Đây đúng là chỗ bộ giả lập dễ "dễ tính hơn API thật" nhất: cứ trả thêm tên
 * cho tiện nhìn thì `npm run dev:mock` chạy trên một hợp đồng không còn tồn
 * tại, và che mất đúng cái lỗi cần thấy.
 */
function findTabooConflicts(huyName: string | undefined): TabooConflict[] {
  if (!huyName || huyName.trim().length === 0) return [];
  const target = huyName.trim();
  const targetUnaccented = unaccent(target);

  if (targetUnaccented === TABOO_TRIGGER_HIDDEN) {
    return [
      {
        ancestorPersonId: HIDDEN_ANCESTOR_ID,
        matchedNameType: "HUY",
        matchKind: "EXACT",
      },
    ];
  }

  const graph = getMockGraph();
  const matched: Array<{ generation: number | null; conflict: TabooConflict }> = [];
  for (const person of graph.personsById.values()) {
    if (matched.length >= 4) break;
    const exact = person.displayName === target;
    const unaccented = !exact && unaccent(person.displayName) === targetUnaccented;
    if (!exact && !unaccented) continue;

    matched.push({
      // Đời thứ dùng để XẾP THỨ TỰ, và ở lại bên trong bộ giả lập — máy chủ
      // thật cũng xếp trước khi cắt trường. Nó không lên dây.
      generation: person.generation,
      conflict: {
        ancestorPersonId: person.id,
        matchedNameType: "HUY",
        matchKind: exact ? "EXACT" : "UNACCENTED",
      },
    });
  }

  // Earliest generation first: the older the ancestor, the heavier the taboo.
  return matched
    .sort((a, b) => (a.generation ?? 0) - (b.generation ?? 0))
    .map((m) => m.conflict);
}

function tabooProblem(conflicts: TabooConflict[], instance: string, overrideField: string) {
  const problem: ConflictProblem = {
    type: "https://giapha.example.vn/problems/ky-huy-conflict",
    title: "Trùng tên húy bậc trên",
    status: 409,
    code: "KY_HUY_CONFLICT",
    // `detail` cũng không được nối tên vào: nó đi ra cùng một thân lỗi, qua
    // cùng một đường không có bộ lọc. Nói SỐ LƯỢNG và nói chỗ để tra tiếp.
    detail:
      `Tên húy vừa nhập trùng tên húy của ${conflicts.length} bậc trên. ` +
      "Vì lý do riêng tư, thân lỗi không nêu danh tính bậc trên: mở hồ sơ theo " +
      "ancestorPersonId để xem phần mình được phép xem. Xác nhận để vẫn ghi.",
    instance,
    overridable: true,
    overrideField,
    conflicts,
  };
  return HttpResponse.json(problem, { status: 409 });
}

// ---------------------------------------------------------------------------
// Nghi trùng nhân khẩu (DUPLICATE_PERSON_SUSPECTED)
// ---------------------------------------------------------------------------

/**
 * Ba hình dạng ứng viên, gieo theo tên chính vừa nhập.
 *
 * Cố ý là một bảng tra tên **rõ ràng** chứ không phải một phép quét cả đồ thị:
 * bộ giả lập chỉ cần dựng lại đủ **ba hình dạng của phản hồi**, còn bộ dò thật
 * chạy trong Postgres trên `name_unaccented` cộng năm sinh, ngày giỗ và chi.
 * Một phép quét "thông minh" ở đây sẽ vô tình nổ vào các ca kỵ húy đang có và
 * làm hai luồng dính vào nhau.
 *
 * Ba hình dạng, và cả ba đều phải chạy được trong `npm run dev:mock`:
 *
 *  1. **`personId` tra được** (`p-001`, cụ Thuỷ Tổ đã khuất) — hộp thoại gọi
 *     `GET /persons/{id}` và dựng được phần danh tính. Đây là ca **thường
 *     gặp**: bậc trên và người bị nghi gần như luôn là người đã khuất, mà
 *     người đã khuất công khai.
 *  2. **`personId` KHÔNG tra được** (`p-781`) — `GET` trả `404`. Ca bình
 *     thường, không phải lỗi.
 *  3. **`personId === null`** — một dòng chưa ghi trong cùng lô nhập liệu.
 *     Định danh duy nhất là `ref`, và **không có gì để `GET`**.
 */
const DUPLICATE_FIXTURES: Record<string, DuplicateCandidate[]> = {
  // Cả ba hình dạng trong một hộp thoại — cửa trước cho người phát triển.
  "nguyen van nghi trung": [
    {
      personId: VISIBLE_ANCESTOR_ID,
      ref: null,
      score: 88,
      signals: ["TEN_TRUNG_CO_DAU", "NAM_SINH_KHOP", "CUNG_CHI"],
      hint: "trùng họ tên đủ dấu, trùng năm sinh, cùng chi",
    },
    {
      personId: HIDDEN_ANCESTOR_ID,
      ref: null,
      score: 74,
      signals: ["TEN_TRUNG_KHONG_DAU", "NAM_SINH_LECH_IT", "CUNG_DOI"],
      hint: "trùng họ tên khi bỏ dấu, năm sinh lệch 1 năm, cùng đời",
    },
    {
      personId: null,
      ref: "Dòng 42 · chi-nhat-2026.xlsx",
      score: 61,
      signals: ["TEN_TRUNG_CO_DAU", "CUNG_NGUYEN_QUAN"],
      hint: "trùng họ tên đủ dấu, cùng nguyên quán",
    },
  ],
  // Chỉ ca `404`, để đối chiếu được rằng hộp thoại không hiện một cái tên nào.
  "nguyen van nghi trung an": [
    {
      personId: HIDDEN_ANCESTOR_ID,
      ref: null,
      score: 80,
      signals: ["TEN_TRUNG_CO_DAU", "GIO_TRUNG_KHIT"],
      hint: "trùng họ tên đủ dấu, ngày giỗ trùng khít",
    },
  ],
  // Chỉ ca `personId === null`.
  "nguyen van nghi trung lo": [
    {
      personId: null,
      ref: "Dòng 7 · chi-nhi-2026.xlsx",
      score: 55,
      signals: ["TEN_TRUNG_KHONG_DAU", "KHAC_GIOI"],
      hint: "trùng họ tên khi bỏ dấu, nhưng khác giới tính",
    },
  ],
};

function findDuplicateCandidates(input: CreatePersonRequest): DuplicateCandidate[] {
  const primary = input.names.find((n) => n.isPrimary) ?? input.names[0];
  if (!primary?.fullName) return [];
  const found = DUPLICATE_FIXTURES[unaccent(primary.fullName.trim())];
  // Hợp đồng: mảng giữ thứ tự giảm dần theo `score`.
  return found ? [...found].sort((a, b) => b.score - a.score) : [];
}

function duplicateProblem(candidates: DuplicateCandidate[], instance: string) {
  const top = candidates[0];
  const problem: ConflictProblem = {
    type: "https://giapha.example.vn/problems/conflict",
    title: "Nghi trùng nhân khẩu",
    status: 409,
    code: "DUPLICATE_PERSON_SUSPECTED",
    // Không một trường nhân khẩu nào đọc từ phả — kể cả ở đây. `hint` được
    // phép vì nó chỉ nói ô nào của CHÍNH người gọi đã khớp.
    detail:
      `Nghi trùng với ${candidates.length} hồ sơ đã có trong gia phả — điểm cao nhất ` +
      `${top?.score ?? 0}${top?.hint ? `, khớp ở: ${top.hint}` : ""}. ` +
      "Vì lý do riêng tư, thân lỗi không nêu danh tính: mở từng hồ sơ theo personId " +
      "để xem phần mình được phép xem. Xác nhận nếu đây là người khác.",
    instance,
    overridable: true,
    overrideField: "confirmDuplicateOverride",
    conflicts: candidates,
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

    // Thành viên không ghi thẳng vào cây — nhưng nay có lối đi thật: gửi một
    // yêu cầu đính chính loại CREATE_PERSON để Trưởng chi duyệt.
    if (role === "member" || role === "guest") {
      return forbidden(
        "Chỉ Trưởng chi trở lên được thêm thẳng nhân khẩu vào gia phả",
        "/api/v1/persons",
        {
          detail:
            "Hãy gửi yêu cầu đính chính để Trưởng chi hoặc Hội đồng Tộc biểu xem xét.",
        }
      );
    }

    const huyName = input.names.find((n) => n.nameType === "HUY")?.fullName;
    const conflicts = findTabooConflicts(huyName);
    if (conflicts.length > 0 && !input.confirmTabooOverride) {
      return tabooProblem(conflicts, "/api/v1/persons", "confirmTabooOverride");
    }

    // Kỵ húy trước, nghi trùng sau — hai cổng nối tiếp, không loại trừ nhau.
    // Bỏ qua cảnh báo kỵ húy không có nghĩa là bỏ qua luôn phép chống trùng.
    const duplicates = findDuplicateCandidates(input);
    if (duplicates.length > 0 && !input.confirmDuplicateOverride) {
      return duplicateProblem(duplicates, "/api/v1/persons");
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
      // `POST` — nhóm vắng mặt ⇒ `PRIVATE`. Hệ thống không tự mở hộ ai bao giờ,
      // nên bắt đầu từ trạng thái kín rồi mới áp phần gửi lên.
      privacy: { ...allPrivate(), ...(input.privacy ?? {}) },
      version: 1,
      createdAt: new Date().toISOString(),
      meta: {
        visibleTier: input.isAlive ? "T3" : "PUBLIC",
        // Người vừa tạo hồ sơ thì đương nhiên sửa được nó; và vì sửa được nên
        // không có gì để đề nghị đính chính.
        canEdit: true,
        canDelete: true,
        canRequestCorrection: false,
        isSelf: false,
        callerRole: role === "admin" ? "ADMIN" : "BRANCH_HEAD",
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

    const projected = withRelationships(projectPersonForRole(person, role), role);
    return HttpResponse.json(projected, { headers: { ETag: etagFor(person) } });
  }),

  http.patch(`${API_BASE_URL}/api/v1/persons/:id`, async ({ params, request }) => {
    const id = params.id as string;
    const role = resolveMockRole(request);

    if (hiddenFromCaller(id, role)) return notFound(`/api/v1/persons/${id}`);
    const existing = loadPerson(id);
    if (!existing) return notFound(`/api/v1/persons/${id}`);

    /**
     * Quyền ghi soi **danh tính và phạm vi**, không soi vai.
     *
     * Ba lối được ghi thẳng: chính chủ sửa hồ sơ mình (BA v2 §10 — Tầng 3 mở
     * cho chính chủ), Trưởng chi trong chi được giao, và vai toàn dòng họ.
     * Ngoài ba lối đó thì phải đi qua **yêu cầu đính chính** — và câu 403 dưới
     * đây nay trỏ tới một luồng CÓ THẬT (`POST /api/v1/change-requests`), chứ
     * không còn mô tả một quy trình chưa có chỗ nào để bắt đầu.
     */
    const identity = identityOf(role);
    if (identity.appUserId === null) {
      return forbidden("Cần đăng nhập để sửa hồ sơ", `/api/v1/persons/${id}`, {
        detail: "Khách chưa đăng nhập không sửa được hồ sơ nào.",
      });
    }

    const isSelf = identity.personId === id;
    const branchPath = existing.primaryBranch?.path ?? null;
    const withinScope = canWriteInBranch(identity, branchPath);

    if (!isSelf && !withinScope) {
      const rightRoleWrongBranch = identity.role === "BRANCH_HEAD";
      return forbidden(
        rightRoleWrongBranch
          ? "Hồ sơ này thuộc chi/ngành ngoài phạm vi bạn được giao"
          : "Bạn chỉ sửa trực tiếp được hồ sơ của chính mình",
        `/api/v1/persons/${id}`,
        {
          code: rightRoleWrongBranch ? "BRANCH_SCOPE_VIOLATION" : "FORBIDDEN",
          detail:
            "Hãy gửi yêu cầu đính chính để Trưởng chi hoặc Hội đồng Tộc biểu xem xét.",
        }
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

    /**
     * `privacy` HỢP NHẤT, không thay thế — ngoại lệ so với `names`/`attributes`
     * ngay bên dưới, và là chỗ dễ dựng sai nhất của cả khối. Giao diện năm công
     * tắc chỉ gửi công tắc vừa gạt; nếu chỗ này ghi đè toàn phần thì bốn nhóm
     * còn lại sẽ âm thầm ĐÓNG lại sau mỗi lần lưu.
     */
    const mergedPrivacy = patch.privacy
      ? mergePrivacy(id, patch.privacy)
      : privacyOf(id);

    const updated: PersonDto = {
      ...existing,
      ...patch,
      privacy: mergedPrivacy,
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
      if (field === "privacy") {
        // `clearFields: ["privacy"]` đóng cả năm nhóm về `PRIVATE` — KHÔNG phải
        // xoá khối đi. Xoá khối sẽ làm chính chủ mất luôn bảng điều khiển của
        // mình, và "không có lựa chọn nào" đọc ra giống hệt "chưa từng chọn".
        //
        // Gửi kèm `privacy: {...}` trong CÙNG một yêu cầu là chuyện contract
        // chưa định nghĩa. Bộ giả lập chọn **đóng thắng** — vòng lặp này chạy
        // sau phép hợp nhất — vì với một trường riêng tư, cách đoán sai an toàn
        // là đoán về phía kín. Nếu backend chốt ngược lại thì sửa đúng một chỗ.
        updated.privacy = clearPrivacy(id);
        continue;
      }
      (updated as unknown as Record<string, unknown>)[field] = undefined;
    }
    overlay.set(id, updated);

    return HttpResponse.json(withRelationships(projectPersonForRole(updated, role), role), {
      headers: { ETag: etagFor(updated) },
    });
  }),

  http.delete(`${API_BASE_URL}/api/v1/persons/:id`, ({ params, request }) => {
    const id = params.id as string;
    const role = resolveMockRole(request);
    if (hiddenFromCaller(id, role)) return notFound(`/api/v1/persons/${id}`);

    const existing = loadPerson(id);
    if (!existing) return notFound(`/api/v1/persons/${id}`);
    // Xoá mềm cũng soi phạm vi: một Trưởng chi không gỡ được người của chi khác.
    if (!canWriteInBranch(identityOf(role), existing.primaryBranch?.path ?? null)) {
      return forbidden(
        "Chỉ Trưởng chi (trong phạm vi được giao) trở lên được xoá nhân khẩu",
        `/api/v1/persons/${id}`
      );
    }

    // Soft delete only, per CLAUDE.md — the node stays in the graph.
    overlay.set(id, { ...existing, isDeleted: true, version: (existing.version ?? 1) + 1 });
    return new HttpResponse(null, { status: 204 });
  }),
];
