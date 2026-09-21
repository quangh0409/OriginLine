import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { findPersonMock, LOGGED_IN_BRANCH_HEAD_FULL } from "@/mocks/data";
import { canWriteInBranch, identityOf, roleCanReview, type MockIdentity } from "@/mocks/identity";
import { canSeeLivingPersons, resolveMockRole, type MockRole } from "./role";
import type {
  HonourDto,
  HonourPatchBody,
  HonourStatus,
  HonourWriteBody,
  ReviewHonourBody,
} from "@/lib/api/honours";
import type { Problem, ValidationProblem } from "@/types/api";

/**
 * MSW cho **vinh danh** — bản ghi gắn nhân khẩu, không phải bài viết. Đọc
 * javadoc đầu `src/lib/api/honours.ts` trước.
 *
 * <h2>Máy trạng thái CHUNG với bài viết (sửa lại — bản trước SAI)</h2>
 * `PENDING → PUBLISHED` (duyệt) hoặc `PENDING → WITHDRAWN` (từ chối). Không
 * còn `APPROVED`/`REJECTED` — đó là hai giá trị bịa của bản đầu tệp này.
 *
 * <h2>`status` LỌC NGAY TRONG QUERY, không gom ở client</h2>
 * `GET /honours?status=` được nhận thẳng — hàng chờ duyệt gọi
 * `{status:"PENDING"}`, danh sách công khai gọi `{status:"PUBLISHED"}`. Bỏ
 * tham số ⇒ trả mọi trạng thái mà `recordVisible` cho qua (dùng khi cần cả
 * hai, ví dụ `personId` để xem lịch sử vinh danh của một người).
 *
 * <h2>Không ai tự duyệt vinh danh của chính mình</h2>
 * `honour-5` (p-103 = chính Trưởng chi trong bộ giả lập) tồn tại chỉ để bài
 * test khoá được ca này: Trưởng chi KHÔNG duyệt được vinh danh về chính mình
 * dù đúng phạm vi chi, còn Hội đồng/Admin thì duyệt được.
 *
 * <h2>Hai điều bộ giả lập này cố tình mô phỏng, cả hai đều KHÔNG có trong DTO thật</h2>
 * <ul>
 *   <li><b>{@code nameOpenToClan}</b> — cờ riêng của bộ giả lập, đứng thế cho
 *       "nhóm trường riêng tư thứ sáu" mà CLAUDE.md nhắc tới nhưng chưa có tên
 *       chính thức trong contract. Khi tắt, {@code personDisplayName} vắng mặt
 *       với mọi người trừ chính chủ và người duyệt đúng phạm vi — CÁC TRƯỜNG
 *       KHÁC (loại, tiêu đề, năm) vẫn còn, đúng tinh thần "vẫn đếm được, chỉ
 *       không biết là ai". **Duyệt xong không tự bật cờ này** — một vinh danh
 *       vừa chuyển `PUBLISHED` mà chủ nhân chưa mở chia sẻ thì cả họ vẫn không
 *       thấy tên, đúng thiết kế.</li>
 *   <li><b>Khách không thấy vinh danh của người còn sống, dù đã duyệt</b> —
 *       hệ quả trực tiếp của luật "khách không thấy người còn sống nào" (BA v2
 *       §10); không phải một biến thể của {@code nameOpenToClan} — với khách,
 *       CẢ BẢN GHI biến mất, không chỉ mất tên.</li>
 * </ul>
 *
 * `branchIdInternal` và `PERSON_BRANCH_ID`/`BRANCH_PATH_BY_ID` chỉ phục vụ bộ
 * lọc `branchId` và việc xét quyền duyệt bên trong bộ giả lập — {@code
 * HonourDto} thật không có trường chi.
 */

const PERSON_ALIVE: Record<string, boolean> = {
  "p-001": false,
  "p-100": true,
  "p-102": true,
  "p-103": true,
};

const PERSON_BRANCH_ID: Record<string, string> = {
  "p-001": "b-root",
  "p-100": "b-chi1",
  "p-102": "b-chi1",
  "p-103": "b-chi1",
};

const BRANCH_PATH_BY_ID: Record<string, string> = {
  "b-root": "root",
  "b-chi1": "root.chi_nhat",
  "b-chi2": "root.chi_nhi",
};

/** Tài khoản người duyệt → nhân khẩu. Chỉ Trưởng chi có nhân khẩu gắn kèm; Admin/Hội đồng thì không. */
const REVIEWER_PERSON: Record<string, { displayName: string | undefined }> = {
  "u-branch-head": { displayName: LOGGED_IN_BRANCH_HEAD_FULL.displayName },
};

function reviewedByDisplayNameFor(reviewedBy: string | null | undefined, role: MockRole): string | null {
  if (!reviewedBy) return null;
  const reviewer = REVIEWER_PERSON[reviewedBy];
  if (!reviewer) return null;
  if (!canSeeLivingPersons(role)) return null;
  return reviewer.displayName ?? null;
}

interface StoredHonour extends HonourDto {
  /** Chỉ dùng nội bộ bộ giả lập — xem javadoc phía trên. Không đi ra JSON. */
  nameOpenToClan: boolean;
  branchIdInternal: string;
}

let seq = 100;
function nextId(): string {
  seq += 1;
  return `honour-${seq}`;
}

function etagFor(h: HonourDto): string {
  return `"v${h.version}"`;
}

function now(): string {
  return new Date().toISOString();
}

function problem(status: number, code: Problem["code"], title: string, detail?: string): Problem {
  return { type: "about:blank", title, status, code, detail, instance: "/api/v1/honours" };
}

function validationProblem(field: string, message: string): ValidationProblem {
  return {
    ...problem(422, "VALIDATION_FAILED", "Thiếu thông tin bắt buộc"),
    errors: [{ field, message }],
  };
}

const STORE: StoredHonour[] = [
  {
    id: "honour-1",
    personId: "p-001",
    personDisplayName: "Nguyễn Văn Thủy Tổ",
    kind: "DO_DAT",
    title: "Đỗ Hương cống khoa Kỷ Mão",
    year: 1819,
    issuer: "Triều Nguyễn",
    description: "Ghi trong gia phả chép tay, đối chiếu văn bia từ đường.",
    status: "PUBLISHED",
    reviewedBy: "u-admin",
    reviewedAt: "2026-01-10T02:00:00.000Z",
    createdAt: "2026-01-05T02:00:00.000Z",
    version: 1,
    nameOpenToClan: true,
    branchIdInternal: "b-root",
  },
  {
    id: "honour-2",
    personId: "p-100",
    personDisplayName: "Nguyễn Văn An",
    kind: "THANH_TICH",
    title: "Giải Nhất Toán học cấp Thành phố",
    year: 2015,
    issuer: "Sở Giáo dục Hà Nội",
    status: "PUBLISHED",
    reviewedBy: "u-branch-head",
    reviewedAt: "2026-02-01T02:00:00.000Z",
    createdAt: "2026-01-28T02:00:00.000Z",
    version: 1,
    nameOpenToClan: true,
    branchIdInternal: "b-chi1",
  },
  {
    id: "honour-3",
    personId: "p-102",
    personDisplayName: "Nguyễn Văn Bình",
    kind: "CHUC_TUOC",
    title: "Bí thư chi bộ thôn",
    year: 2020,
    status: "PENDING",
    createdAt: "2026-09-05T02:00:00.000Z",
    version: 1,
    nameOpenToClan: true,
    branchIdInternal: "b-chi1",
  },
  {
    id: "honour-4",
    personId: "p-103",
    personDisplayName: "Nguyễn Văn Cẩn",
    kind: "KHEN_THUONG",
    title: "Bằng khen Hội Chữ thập đỏ",
    year: 2022,
    issuer: "Hội Chữ thập đỏ tỉnh",
    status: "PUBLISHED",
    reviewedBy: "u-admin",
    reviewedAt: "2026-03-01T02:00:00.000Z",
    createdAt: "2026-02-25T02:00:00.000Z",
    version: 1,
    // Chính chủ đã KHÔNG mở nhóm trường thứ sáu cho cả họ — đây là bản ghi
    // dùng để kiểm "vinh danh của người còn sống thiếu trường thì màn không vỡ",
    // và để kiểm "duyệt xong không tự bật chia sẻ".
    nameOpenToClan: false,
    branchIdInternal: "b-chi1",
  },
  {
    id: "honour-5",
    personId: "p-103",
    personDisplayName: "Nguyễn Văn Cẩn",
    kind: "DO_DAT",
    title: "Bằng Lương y cấp tỉnh",
    year: 1998,
    status: "PENDING",
    createdAt: "2026-09-14T02:00:00.000Z",
    version: 1,
    nameOpenToClan: true,
    branchIdInternal: "b-chi1",
  },
];

function canReviewHonour(identity: MockIdentity, h: StoredHonour): boolean {
  if (!roleCanReview(identity)) return false;
  const path = BRANCH_PATH_BY_ID[h.branchIdInternal];
  return canWriteInBranch(identity, path);
}

/** Bản ghi có lọt vào tầm nhìn của người gọi không — CHƯA xét việc có tên hay không. */
function recordVisible(h: StoredHonour, identity: MockIdentity): boolean {
  const alive = PERSON_ALIVE[h.personId] ?? false;
  const isSelf = identity.personId === h.personId;

  if (h.status !== "PUBLISHED") {
    // Chưa duyệt / bị từ chối: chỉ chính chủ hoặc người duyệt đúng phạm vi thấy.
    return isSelf || canReviewHonour(identity, h);
  }
  if (alive && identity.appUserId === null) {
    // Khách: không thấy vinh danh của bất kỳ người còn sống nào, bất kể đã duyệt.
    return false;
  }
  return true;
}

/** Áp cờ `nameOpenToClan` + `reviewedByDisplayName` — trả về DTO công khai, KHÔNG có hai trường nội bộ. */
function project(h: StoredHonour, identity: MockIdentity, role: MockRole): HonourDto {
  const alive = PERSON_ALIVE[h.personId] ?? false;
  const isSelf = identity.personId === h.personId;
  const canSeeName = !alive || isSelf || h.nameOpenToClan || canReviewHonour(identity, h);

  const { nameOpenToClan: _nameOpenToClan, branchIdInternal: _branchIdInternal, ...dto } = h;
  void _nameOpenToClan;
  void _branchIdInternal;
  return {
    ...dto,
    personDisplayName: canSeeName ? dto.personDisplayName : null,
    reviewedByDisplayName: reviewedByDisplayNameFor(dto.reviewedBy, role),
  };
}

export const honourHandlers = [
  http.get(`${API_BASE_URL}/api/v1/honours`, ({ request }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const url = new URL(request.url);
    const personId = url.searchParams.get("personId") ?? undefined;
    const kind = url.searchParams.get("kind") ?? undefined;
    const branchId = url.searchParams.get("branchId") ?? undefined;
    const status = (url.searchParams.get("status") as HonourStatus | null) ?? undefined;
    const page = Number(url.searchParams.get("page") ?? "0");
    const size = Number(url.searchParams.get("size") ?? "20");

    const filtered = STORE.filter((h) => (personId ? h.personId === personId : true))
      .filter((h) => (kind ? h.kind === kind : true))
      .filter((h) => (branchId ? h.branchIdInternal === branchId : true))
      .filter((h) => (status ? h.status === status : true))
      .filter((h) => recordVisible(h, identity));

    const sorted = [...filtered].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    const start = page * size;
    const items = sorted.slice(start, start + size).map((h) => project(h, identity, role));

    return HttpResponse.json({
      items,
      page: {
        page,
        size,
        // Đếm TRƯỚC lọc riêng tư theo đúng thật — giao diện không được dùng số
        // này để vẽ trang, chỉ `hasNext` (xem javadoc `src/lib/api/honours.ts`).
        totalElements: STORE.length,
        totalPages: Math.max(1, Math.ceil(sorted.length / size)),
        hasNext: start + size < sorted.length,
      },
    });
  }),

  http.post(`${API_BASE_URL}/api/v1/honours`, async ({ request }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    if (identity.appUserId === null) {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Cần đăng nhập để đề nghị vinh danh"), {
        status: 403,
      });
    }
    const body = (await request.json()) as HonourWriteBody;
    if (!body.personId) {
      return HttpResponse.json(validationProblem("personId", "Thiếu nhân khẩu được vinh danh."), {
        status: 422,
      });
    }
    if (!body.title || !body.title.trim()) {
      return HttpResponse.json(validationProblem("title", "Xin nhập tiêu đề vinh danh."), {
        status: 422,
      });
    }
    const isSelf = identity.personId === body.personId;
    const branchIdInternal = PERSON_BRANCH_ID[body.personId] ?? "b-chi1";
    const scopeOk =
      isSelf || canWriteInBranch(identity, BRANCH_PATH_BY_ID[branchIdInternal]) || identity.clanWide;
    if (!scopeOk) {
      return HttpResponse.json(
        problem(403, "BRANCH_SCOPE_VIOLATION", "Ngoài phạm vi chi/ngành được giao"),
        { status: 403 }
      );
    }

    const record: StoredHonour = {
      id: nextId(),
      personId: body.personId,
      personDisplayName: findPersonMock(body.personId)?.displayName ?? null,
      kind: body.kind,
      title: body.title,
      year: body.year ?? null,
      issuer: body.issuer ?? null,
      description: body.description ?? null,
      status: "PENDING",
      createdAt: now(),
      version: 1,
      nameOpenToClan: true,
      branchIdInternal,
    };
    STORE.unshift(record);
    return HttpResponse.json(project(record, identity, role), {
      status: 201,
      headers: { ETag: etagFor(record) },
    });
  }),

  http.patch(`${API_BASE_URL}/api/v1/honours/:id`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const record = STORE.find((h) => h.id === params.id);
    if (!record) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy vinh danh"), {
        status: 404,
      });
    }
    const isSelf = identity.personId === record.personId;
    if (!isSelf && !canReviewHonour(identity, record)) {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Không sửa được vinh danh này"), {
        status: 403,
      });
    }

    const ifMatch = request.headers.get("If-Match");
    if (!ifMatch) {
      return HttpResponse.json(problem(412, "PRECONDITION_REQUIRED", "Thiếu header If-Match"), {
        status: 412,
      });
    }
    if (ifMatch !== etagFor(record)) {
      return HttpResponse.json(
        problem(409, "OPTIMISTIC_LOCK_CONFLICT", "Bản ghi đã bị sửa ở nơi khác"),
        { status: 409 }
      );
    }

    const patch = (await request.json()) as HonourPatchBody;
    if (patch.kind !== undefined) record.kind = patch.kind;
    if (patch.title !== undefined) record.title = patch.title;
    if (patch.year !== undefined) record.year = patch.year;
    if (patch.issuer !== undefined) record.issuer = patch.issuer;
    if (patch.description !== undefined) record.description = patch.description;
    record.version += 1;

    return HttpResponse.json(project(record, identity, role), { headers: { ETag: etagFor(record) } });
  }),

  http.post(`${API_BASE_URL}/api/v1/honours/:id/review`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const record = STORE.find((h) => h.id === params.id);
    if (!record) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy vinh danh"), {
        status: 404,
      });
    }
    if (record.status !== "PENDING" || !canReviewHonour(identity, record)) {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Không duyệt được vinh danh này"), {
        status: 403,
      });
    }
    // Cùng tiền lệ ChangeRequest: đúng phạm vi vẫn không được tự duyệt vinh danh về chính mình.
    if (identity.personId && identity.personId === record.personId) {
      return HttpResponse.json(
        problem(
          403,
          "SELF_REVIEW_FORBIDDEN",
          "Không tự duyệt được vinh danh của chính mình",
          "Nhờ một thành viên khác trong Hội đồng hoặc chi/ngành xem xét bản ghi này."
        ),
        { status: 403 }
      );
    }
    const body = (await request.json()) as ReviewHonourBody;
    if (!body.approve && !body.note?.trim()) {
      return HttpResponse.json(validationProblem("note", "Từ chối cần nêu lý do."), {
        status: 422,
      });
    }
    record.status = body.approve ? "PUBLISHED" : "WITHDRAWN";
    record.reviewedBy = identity.appUserId;
    record.reviewedAt = now();
    record.version += 1;
    return HttpResponse.json(project(record, identity, role), { headers: { ETag: etagFor(record) } });
  }),

  http.delete(`${API_BASE_URL}/api/v1/honours/:id`, ({ request, params }) => {
    const identity = identityOf(resolveMockRole(request));
    const idx = STORE.findIndex((h) => h.id === params.id);
    if (idx === -1) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy vinh danh"), {
        status: 404,
      });
    }
    const record = STORE[idx] as StoredHonour;
    const isSelf = identity.personId === record.personId;
    if (!isSelf && !canReviewHonour(identity, record)) {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Không xoá được vinh danh này"), {
        status: 403,
      });
    }
    STORE.splice(idx, 1);
    return new HttpResponse(null, { status: 204 });
  }),
];
