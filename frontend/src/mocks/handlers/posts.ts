import { http, HttpResponse, delay } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import {
  LOGGED_IN_BRANCH_HEAD_FULL,
  LOGGED_IN_MEMBER_FULL,
} from "@/mocks/data";
import { canWriteInBranch, identityOf, roleCanReview, type MockIdentity } from "@/mocks/identity";
import { canSeeLivingPersons, resolveMockRole, type MockRole } from "./role";
import type {
  PostDto,
  PostPatchBody,
  PostStatus,
  PostWriteBody,
  ReviewPostBody,
} from "@/lib/api/posts";
import {
  namedRejectionKey,
  type MediaKind,
  type MediaPolicy,
  type MediaReportDto,
  type MediaReportReason,
  type PostMediaItem,
} from "@/lib/api/media";
import type { Problem, ValidationProblem } from "@/types/api";

/**
 * MSW cho **bài viết dòng họ**. Tình trạng hợp đồng và luật trạng thái đã ghi
 * trong javadoc của `src/lib/api/posts.ts` — đọc ở đó trước khi sửa handler
 * này để hai bên không trôi khỏi nhau.
 *
 * <h2>Máy trạng thái đã chọn, và vì sao</h2>
 * `DRAFT → PENDING → PUBLISHED → WITHDRAWN` (checklist §2), cộng một lối
 * quay lại: `review({approve:false})` đưa `PENDING` **về `DRAFT`** kèm
 * `rejectReason`, vì hợp đồng không có endpoint "huỷ nộp" riêng và
 * `PostStatus` không có giá trị `REJECTED` — "trả lại" đúng nghĩa đen là trả
 * bài về tay người viết để sửa, không phải một ngõ cụt.
 *
 * `withdraw` chỉ hợp lệ từ `PUBLISHED` (gỡ một bài đã lên trang chủ) — khớp
 * đúng trình tự bốn trạng thái mà checklist liệt kê, **không** phải một lối
 * "rút đơn" cho bài đang `PENDING` (hợp đồng không cho endpoint đó).
 *
 * <h2>Phạm vi duyệt dùng lại `mocks/identity.ts`</h2>
 * `roleCanReview` + `canWriteInBranch` — cùng cặp hàm màn hàng-chờ-duyệt-đơn-
 * gia-nhập dùng. Chi của một bài viết được ghi bằng `branchId` dạng
 * `BranchRef.id` (vd. `"b-chi1"`) để khớp cách phần còn lại của sản phẩm hiển
 * thị chi; quy phạm vi RBAC lại cần `ltree`, nên `BRANCH_PATH_BY_ID` bên dưới
 * là bảng tra CHỈ DÙNG TRONG BỘ GIẢ LẬP.
 *
 * <h2>Không ai tự duyệt bài của chính mình</h2>
 * Cùng tiền lệ `ChangeRequestService` (`SELF_REVIEW_FORBIDDEN`): một Trưởng
 * chi vẫn có thể VIẾT bài như một thành viên bình thường, và bài ấy vẫn hiện
 * trong hàng chờ của chính họ (chi/ngành khác không có ai khác đọc), nhưng
 * chính họ không được là người bấm Duyệt/Trả lại cho bài của mình — người
 * khác trong Hội đồng phải làm việc đó.
 *
 * <h2>`reviewedByDisplayName` tôn trọng đúng luật ẩn người còn sống</h2>
 * Người duyệt trong bộ giả lập luôn là một tài khoản còn sống (Trưởng chi)
 * hoặc không gắn nhân khẩu nào (System Admin kỹ thuật, `u-admin`). Tên chỉ lộ
 * ra với người gọi ĐÃ đăng nhập (`canSeeLivingPersons`) — khách xem một bài đã
 * đăng vẫn không biết Trưởng chi nào đã duyệt nó, đúng luật "khách không thấy
 * người còn sống nào" dù bài đã công khai.
 */

const BRANCH_PATH_BY_ID: Record<string, string> = {
  "b-root": "root",
  "b-chi1": "root.chi_nhat",
  "b-chi2": "root.chi_nhi",
};

/** Tài khoản người duyệt → nhân khẩu, CHỈ những tài khoản có nhân khẩu gắn kèm. `u-admin` cố tình vắng mặt. */
const REVIEWER_PERSON: Record<string, { displayName: string | undefined }> = {
  "u-member": { displayName: LOGGED_IN_MEMBER_FULL.displayName },
  "u-branch-head": { displayName: LOGGED_IN_BRANCH_HEAD_FULL.displayName },
};

/**
 * `null` khi: chưa có ai duyệt, người duyệt không gắn nhân khẩu nào (không có
 * gì để lộ), hoặc người gọi không được thấy người còn sống ấy. Không bao giờ
 * trả một chuỗi tự bịa.
 */
function reviewedByDisplayNameFor(reviewedBy: string | null | undefined, role: MockRole): string | null {
  if (!reviewedBy) return null;
  const reviewer = REVIEWER_PERSON[reviewedBy];
  if (!reviewer) return null;
  if (!canSeeLivingPersons(role)) return null;
  return reviewer.displayName ?? null;
}

function project(post: PostDto, role: MockRole): PostDto {
  return { ...post, reviewedByDisplayName: reviewedByDisplayNameFor(post.reviewedBy, role) };
}

interface AuthorInfo {
  personId: string;
  displayName: string;
  branchId: string | null;
  branchName: string | null;
}

/** Chỉ hai tài khoản trong bộ giả lập có nhân khẩu gắn kèm — xem `mocks/identity.ts`. */
function authorFor(identity: MockIdentity): AuthorInfo | null {
  if (identity.personId === LOGGED_IN_MEMBER_FULL.id) {
    return {
      personId: LOGGED_IN_MEMBER_FULL.id,
      displayName: LOGGED_IN_MEMBER_FULL.displayName ?? "",
      branchId: LOGGED_IN_MEMBER_FULL.primaryBranch?.id ?? null,
      branchName: LOGGED_IN_MEMBER_FULL.primaryBranch?.name ?? null,
    };
  }
  if (identity.personId === LOGGED_IN_BRANCH_HEAD_FULL.id) {
    return {
      personId: LOGGED_IN_BRANCH_HEAD_FULL.id,
      displayName: LOGGED_IN_BRANCH_HEAD_FULL.displayName ?? "",
      branchId: LOGGED_IN_BRANCH_HEAD_FULL.primaryBranch?.id ?? null,
      branchName: LOGGED_IN_BRANCH_HEAD_FULL.primaryBranch?.name ?? null,
    };
  }
  return null;
}

function canReviewPost(identity: MockIdentity, post: PostDto): boolean {
  if (!roleCanReview(identity)) return false;
  const path = post.branchId ? BRANCH_PATH_BY_ID[post.branchId] : null;
  return canWriteInBranch(identity, path ?? undefined);
}

let seq = 100;
function nextId(): string {
  seq += 1;
  return `post-${seq}`;
}

function etagFor(post: PostDto): string {
  return `"v${post.version}"`;
}

function now(): string {
  return new Date().toISOString();
}

function problem(status: number, code: Problem["code"], title: string, detail?: string): Problem {
  return { type: "about:blank", title, status, code, detail, instance: "/api/v1/posts" };
}

function validationProblem(field: string, message: string): ValidationProblem {
  return {
    ...problem(422, "VALIDATION_FAILED", "Thiếu thông tin bắt buộc"),
    errors: [{ field, message }],
  };
}

/** Ảnh mẫu vẽ bằng SVG nội tuyến — không phụ thuộc mạng ngoài, chạy được cả khi máy không có Internet. */
function sampleImage(label: string, fill: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600"><rect width="100%" height="100%" fill="${fill}"/><text x="50%" y="50%" font-family="sans-serif" font-size="32" text-anchor="middle" fill="#3a2a1a">${label}</text></svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

const STORE: PostDto[] = [
  {
    id: "post-1",
    title: "Sửa lại đường vào từ đường trước giỗ Tổ",
    body: "Con đường đất vào từ đường đã lún sau mấy trận mưa vừa rồi, đang bàn để sửa trước ngày giỗ Tổ tháng tới…",
    status: "DRAFT",
    authorPersonId: LOGGED_IN_MEMBER_FULL.id,
    authorDisplayName: LOGGED_IN_MEMBER_FULL.displayName ?? "",
    branchId: "b-chi1",
    branchName: "Chi Nhất",
    media: [],
    createdAt: "2026-08-01T02:00:00.000Z",
    updatedAt: "2026-08-01T02:00:00.000Z",
    version: 1,
  },
  {
    id: "post-2",
    title: "Xin ý kiến cả họ về ngày họp mặt cuối năm",
    body: "Năm nay con cháu ở xa về đông, xin đề xuất họp mặt vào 25 tháng Chạp thay vì 30 như mọi năm để ai cũng kịp về quê ăn Tết.",
    status: "PENDING",
    authorPersonId: LOGGED_IN_MEMBER_FULL.id,
    authorDisplayName: LOGGED_IN_MEMBER_FULL.displayName ?? "",
    branchId: "b-chi1",
    branchName: "Chi Nhất",
    media: [],
    createdAt: "2026-09-01T02:00:00.000Z",
    updatedAt: "2026-09-10T02:00:00.000Z",
    version: 2,
  },
  {
    id: "post-3",
    title: "Đã hoàn thành trùng tu nhà thờ họ",
    body: "Sau ba tháng, nhà thờ họ đã trùng tu xong phần mái và sân trước. Xin cảm ơn các nhà đã đóng góp công đức.",
    status: "PUBLISHED",
    authorPersonId: LOGGED_IN_BRANCH_HEAD_FULL.id,
    authorDisplayName: LOGGED_IN_BRANCH_HEAD_FULL.displayName ?? "",
    branchId: "b-chi1",
    branchName: "Chi Nhất",
    media: [
      {
        id: "media-seed-1",
        kind: "IMAGE",
        url: sampleImage("Mái nhà thờ mới", "%23e9dcc8"),
        alt: "Mái ngói nhà thờ họ sau khi trùng tu, nhìn từ sân trước",
        order: 0,
        width: 800,
        height: 600,
        createdAt: "2026-07-15T03:00:00.000Z",
      },
      {
        id: "media-seed-2",
        kind: "IMAGE",
        url: sampleImage("Sân trước", "%23f2e6d3"),
        alt: "Sân trước nhà thờ họ đã lát lại gạch",
        order: 1,
        width: 800,
        height: 600,
        createdAt: "2026-07-15T03:00:00.000Z",
      },
    ],
    publishedAt: "2026-07-15T03:00:00.000Z",
    reviewedBy: "u-admin",
    reviewedAt: "2026-07-15T03:00:00.000Z",
    createdAt: "2026-07-10T02:00:00.000Z",
    updatedAt: "2026-07-15T03:00:00.000Z",
    version: 3,
  },
  {
    id: "post-4",
    title: "Ra mắt cổng thông tin dòng họ",
    body: "Từ hôm nay, cả họ có thể xem phả đồ, tra danh xưng và nhận nhắc giỗ ngay trên điện thoại. Mời các bác, các cô chú, anh chị em cùng vào xem thử.",
    status: "PUBLISHED",
    authorPersonId: LOGGED_IN_MEMBER_FULL.id,
    authorDisplayName: LOGGED_IN_MEMBER_FULL.displayName ?? "",
    branchId: null,
    branchName: null,
    media: [],
    publishedAt: "2026-06-01T02:00:00.000Z",
    reviewedBy: "u-branch-head",
    reviewedAt: "2026-06-01T02:00:00.000Z",
    createdAt: "2026-05-28T02:00:00.000Z",
    updatedAt: "2026-06-01T02:00:00.000Z",
    version: 2,
  },
  {
    id: "post-5",
    title: "Xin ý kiến sửa cổng chi",
    body: "Cổng vào nhà thờ chi đã cũ, xin ý kiến cả chi trước khi báo Hội đồng.",
    status: "PENDING",
    authorPersonId: LOGGED_IN_BRANCH_HEAD_FULL.id,
    authorDisplayName: LOGGED_IN_BRANCH_HEAD_FULL.displayName ?? "",
    branchId: "b-chi1",
    branchName: "Chi Nhất",
    media: [],
    createdAt: "2026-09-12T02:00:00.000Z",
    updatedAt: "2026-09-12T02:00:00.000Z",
    version: 1,
  },
];

/** Ai gọi cũng nhìn được các bài PUBLISHED; các trạng thái khác chỉ tác giả hoặc người duyệt đúng phạm vi. */
function visibleTo(post: PostDto, identity: MockIdentity): boolean {
  if (post.status === "PUBLISHED") return true;
  if (identity.personId && identity.personId === post.authorPersonId) return true;
  if (post.status === "PENDING" && canReviewPost(identity, post)) return true;
  return false;
}

/**
 * Bản thân bài — media handlers được định nghĩa sau (cần `STORE`/`project`/
 * `problem` đã có ở trên), rồi hai mảng được ghép lại ở cuối tệp thành
 * `postHandlers`. Không ghép trực tiếp bằng `...mediaHandlers` ngay trong
 * mảng này: `mediaHandlers` là một `const` khai báo BÊN DƯỚI, tham chiếu tới
 * nó ngay tại đây sẽ vỡ vì temporal dead zone lúc module chạy.
 */
const corePostHandlers = [
  http.get(`${API_BASE_URL}/api/v1/posts`, ({ request }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const url = new URL(request.url);
    const status = url.searchParams.get("status") as PostStatus | null;
    const mine = url.searchParams.get("mine") === "true";
    const page = Number(url.searchParams.get("page") ?? "0");
    const size = Number(url.searchParams.get("size") ?? "20");

    // `mine=true` lọc TRONG câu truy vấn (ở đây: trước khi cắt trang) — đúng
    // sửa lỗi mà bốn-lượt-gọi-song-song-rồi-lọc-ở-client từng mắc: cắt trang
    // TRƯỚC KHI lọc làm một người viết nhiều mất bài cũ mà không có gì báo.
    // `identity.personId` rỗng (khách/tài khoản chưa gắn nhân khẩu) ⇒ không
    // ai là "của tôi" — trả rỗng, không phải mọi bài.
    const filtered = STORE.filter((p) => (status ? p.status === status : mine || p.status === "PUBLISHED"))
      .filter((p) => (mine ? Boolean(identity.personId) && p.authorPersonId === identity.personId : true))
      .filter((p) => visibleTo(p, identity));
    const sorted = [...filtered].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    const start = page * size;
    const items = sorted.slice(start, start + size).map((p) => project(p, role));

    return HttpResponse.json({
      items,
      page: {
        page,
        size,
        totalElements: sorted.length,
        totalPages: Math.max(1, Math.ceil(sorted.length / size)),
        hasNext: start + size < sorted.length,
      },
    });
  }),

  // Đăng ký TRƯỚC `/:id` — nếu không, `/posts/feed` sẽ khớp nhầm vào route `/posts/:id`.
  http.get(`${API_BASE_URL}/api/v1/posts/feed`, ({ request }) => {
    const role = resolveMockRole(request);
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get("limit") ?? "6");
    const published = STORE.filter((p) => p.status === "PUBLISHED").sort((a, b) =>
      (b.publishedAt ?? "").localeCompare(a.publishedAt ?? "")
    );
    return HttpResponse.json(published.slice(0, limit).map((p) => project(p, role)));
  }),

  http.get(`${API_BASE_URL}/api/v1/posts/:id`, ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post || !visibleTo(post, identity)) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), {
        status: 404,
      });
    }
    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),

  http.post(`${API_BASE_URL}/api/v1/posts`, async ({ request }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const author = authorFor(identity);
    if (!author) {
      return HttpResponse.json(
        problem(
          403,
          "FORBIDDEN",
          "Chưa được duyệt vào phả",
          "Viết bài là tiếng nói của người trong họ — hãy hoàn tất nhận diện mình trong phả trước."
        ),
        { status: 403 }
      );
    }
    const body = (await request.json()) as PostWriteBody;
    if (!body.title || !body.title.trim()) {
      return HttpResponse.json(validationProblem("title", "Xin nhập tiêu đề."), { status: 422 });
    }

    const post: PostDto = {
      id: nextId(),
      title: body.title,
      body: body.body ?? "",
      status: "DRAFT",
      authorPersonId: author.personId,
      authorDisplayName: author.displayName,
      branchId: author.branchId,
      branchName: author.branchName,
      media: [],
      createdAt: now(),
      updatedAt: now(),
      version: 1,
    };
    STORE.unshift(post);
    return HttpResponse.json(project(post, role), { status: 201, headers: { ETag: etagFor(post) } });
  }),

  http.patch(`${API_BASE_URL}/api/v1/posts/:id`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), {
        status: 404,
      });
    }

    const isAuthor = identity.personId === post.authorPersonId;
    const isReviewerInScope = canReviewPost(identity, post);
    const allowed =
      (post.status === "DRAFT" && isAuthor) || (post.status === "PENDING" && isReviewerInScope);
    if (!allowed) {
      return HttpResponse.json(
        problem(
          403,
          "FORBIDDEN",
          "Không sửa được bài này",
          post.status === "PUBLISHED" || post.status === "WITHDRAWN"
            ? "Bài đã đăng hoặc đã gỡ không sửa trực tiếp được."
            : "Bạn không có quyền sửa bài này ở trạng thái hiện tại."
        ),
        { status: 403 }
      );
    }

    const ifMatch = request.headers.get("If-Match");
    if (!ifMatch) {
      return HttpResponse.json(problem(412, "PRECONDITION_REQUIRED", "Thiếu header If-Match"), {
        status: 412,
      });
    }
    if (ifMatch !== etagFor(post)) {
      return HttpResponse.json(
        problem(409, "OPTIMISTIC_LOCK_CONFLICT", "Bài viết đã bị sửa ở nơi khác"),
        { status: 409 }
      );
    }

    const patch = (await request.json()) as PostPatchBody;
    if (patch.title !== undefined && !patch.title.trim()) {
      return HttpResponse.json(validationProblem("title", "Tiêu đề không được để trống."), {
        status: 422,
      });
    }

    if (patch.title !== undefined) post.title = patch.title;
    if (patch.body !== undefined) post.body = patch.body;
    post.version += 1;
    post.updatedAt = now();

    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),

  http.post(`${API_BASE_URL}/api/v1/posts/:id/submit`, ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), {
        status: 404,
      });
    }
    if (identity.personId !== post.authorPersonId) {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Chỉ tác giả mới gửi duyệt được"), {
        status: 403,
      });
    }
    if (post.status === "PENDING") return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
    if (post.status !== "DRAFT") {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Chỉ nháp mới gửi duyệt được"), {
        status: 403,
      });
    }
    if (!post.title.trim() || !post.body.trim()) {
      return HttpResponse.json(validationProblem("body", "Bài viết cần có tiêu đề và nội dung trước khi gửi duyệt."), {
        status: 422,
      });
    }

    post.status = "PENDING";
    post.rejectReason = null;
    post.reviewedBy = null;
    post.reviewedAt = null;
    post.version += 1;
    post.updatedAt = now();
    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),

  http.post(`${API_BASE_URL}/api/v1/posts/:id/review`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), {
        status: 404,
      });
    }
    if (post.status !== "PENDING" || !canReviewPost(identity, post)) {
      return HttpResponse.json(
        problem(403, "FORBIDDEN", "Không duyệt được bài này", "Sai phạm vi chi/ngành hoặc bài không còn chờ duyệt."),
        { status: 403 }
      );
    }
    // Cùng tiền lệ ChangeRequest: đúng phạm vi vẫn không được tự duyệt bài của chính mình.
    if (identity.personId && identity.personId === post.authorPersonId) {
      return HttpResponse.json(
        problem(
          403,
          "SELF_REVIEW_FORBIDDEN",
          "Không tự duyệt được bài viết của chính mình",
          "Nhờ một thành viên khác trong Hội đồng hoặc chi/ngành xem xét bài này."
        ),
        { status: 403 }
      );
    }
    const body = (await request.json()) as ReviewPostBody;
    if (!body.approve && !body.note?.trim()) {
      return HttpResponse.json(validationProblem("note", "Trả lại bài viết cần nêu lý do."), {
        status: 422,
      });
    }

    post.reviewedBy = identity.appUserId;
    post.reviewedAt = now();
    if (body.approve) {
      post.status = "PUBLISHED";
      post.publishedAt = now();
      post.rejectReason = null;
    } else {
      post.status = "DRAFT";
      post.rejectReason = body.note ?? null;
    }
    post.version += 1;
    post.updatedAt = now();
    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),

  http.post(`${API_BASE_URL}/api/v1/posts/:id/withdraw`, ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), {
        status: 404,
      });
    }
    const canWithdraw = identity.personId === post.authorPersonId || canReviewPost(identity, post);
    if (post.status !== "PUBLISHED" || !canWithdraw) {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Không gỡ được bài này"), { status: 403 });
    }
    post.status = "WITHDRAWN";
    post.version += 1;
    post.updatedAt = now();
    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),
];

/* ══════════════════════════════════════════════════════════════════════════
 * Media — ảnh/video đính kèm (Việc 1–2, xem javadoc `src/lib/api/media.ts`)
 *
 * <h2>Bộ giả lập khó bằng thật, không dễ hơn</h2>
 * Đây là cái bẫy đã lặp bốn lần ở dự án này: một bộ giả lập dễ hơn API thật
 * làm giao diện trông chạy được rồi vỡ trước máy chủ thật. Nên ở đây:
 * <ul>
 *   <li>xin URL đã ký kiểm lại `kind`/`contentType`/`sizeBytes` — CHÍNH nó,
 *       không tin phép kiểm phía client;</li>
 *   <li>`PUT` lên "kho" giả kiểm lại `Content-Type` khớp lúc xin URL, và có
 *       độ trễ thật (không phải 0ms) để thanh tiến trình có ý nghĩa;</li>
 *   <li>xác nhận đòi tệp ĐÃ THẬT SỰ được `PUT` lên (`UPLOADED_KEYS`) — gọi xác
 *       nhận trước khi PUT xong bị từ chối, không phải "coi như đã tải".</li>
 * </ul>
 * ══════════════════════════════════════════════════════════════════════════ */

/**
 * Chính sách media — cùng con số backend media thật đã chốt (design 07
 * đồng bộ đợt hai). `GET /media/policy` trả nguyên object này; các handler
 * dưới đây dùng CHÍNH nó để kiểm, không phải một bộ số khác — hai nơi (chính
 * sách công bố / chính sách áp dụng) lệch nhau là đúng loại lỗi "mock dễ hơn
 * thật" đã lặp lại ở dự án này.
 */
const MEDIA_POLICY: MediaPolicy = {
  image: {
    maxBytes: 8 * 1024 * 1024, // 8 MiB
    mimeTypes: ["image/jpeg", "image/png", "image/webp"],
  },
  video: {
    maxBytes: 100 * 1024 * 1024, // 100 MiB — ngân sách TẢI VỀ, không phải tải lên
    mimeTypes: ["video/mp4", "video/webm"],
    maxDurationSeconds: 120,
  },
  maxAttachmentsPerPost: 12,
  uploadUrlExpiresInSeconds: 15 * 60,
  readUrlExpiresInSeconds: 60 * 60,
};

interface PendingUpload {
  uploadId: string;
  postId: string;
  kind: MediaKind;
  contentType: string;
  sizeBytes: number;
  key: string;
  fileName: string;
}

const PENDING_UPLOADS = new Map<string, PendingUpload>();
/** Đối tượng đã thật sự `PUT` lên "kho" — byte + kiểu, để lượt `GET` sau trả lại đúng thứ đã gửi. */
const STORED_OBJECTS = new Map<string, { bytes: ArrayBuffer; contentType: string }>();

let uploadSeq = 0;
let mediaSeq = 0;
function nextUploadId(): string {
  uploadSeq += 1;
  return `up-${uploadSeq}`;
}
function nextMediaId(): string {
  mediaSeq += 1;
  return `media-${mediaSeq}`;
}

function storageUrl(key: string): string {
  return `${API_BASE_URL}/__mock-storage__/${encodeURIComponent(key)}`;
}

/** Cùng cổng quyền với sửa thân bài (chỉ tác giả, chỉ khi còn `DRAFT`) — "ảnh đi theo quyền của bài". */
function mediaEditAllowed(identity: MockIdentity, post: PostDto): boolean {
  return post.status === "DRAFT" && identity.personId === post.authorPersonId;
}

/**
 * Câu từ chối theo TÊN tệp — mỗi định dạng một cách sửa cụ thể, khớp đúng
 * khoá dịch `posts.media.rejected.named.*` phía client (cùng nội dung, hai
 * nơi — máy chủ trả câu tiếng Việt hoàn chỉnh trong `detail`, và
 * `MediaPicker` cũng tự dịch được cùng ý cho ca client chặn trước khi gọi
 * mạng, để hai đường luôn nói cùng một điều).
 */
const NAMED_FORMAT_DETAIL: Record<string, string> = {
  heic: "Đây là ảnh định dạng HEIC (mặc định của iPhone). Vào Cài đặt > Máy ảnh > Định dạng, chọn \"Tương thích nhất\" để lưu ảnh dạng JPEG — hoặc mở ảnh này, bấm Chia sẻ > Lưu ảnh gốc dạng tương thích, rồi tải lại.",
  gif: "Ảnh động GIF chưa hỗ trợ. Lưu lại thành ảnh tĩnh (JPEG/PNG) nếu chỉ cần một khung hình, hoặc chuyển thành video MP4 ngắn nếu cần giữ chuyển động.",
  mov: "Video định dạng MOV (mặc định của iPhone) chưa hỗ trợ. Chuyển sang MP4 trước khi tải lên — trên iPhone: mở Ảnh, chọn video, bấm Chia sẻ > Lưu dưới dạng video đã nén để đổi sang MP4.",
  mkv: "Video định dạng MKV chưa hỗ trợ. Chuyển sang MP4 bằng một công cụ chuyển đổi định dạng video trước khi tải lên.",
};

/** Tìm bài đang giữ một `mediaId` — báo cáo gắn với TỆP, không phải bài, nên phải quét ngược lại. */
function findMediaOwner(mediaId: string): { post: PostDto; item: PostMediaItem } | undefined {
  for (const post of STORE) {
    const item = post.media.find((m) => m.id === mediaId);
    if (item) return { post, item };
  }
  return undefined;
}

interface MediaReportRecord {
  id: string;
  mediaId: string;
  postId: string;
  reporterId: string;
  reason: MediaReportReason;
  note: string | null;
  status: "PENDING" | "TAKEN_DOWN" | "DISMISSED";
  createdAt: string;
  decidedBy?: string;
  decidedAt?: string;
  decisionNote?: string | null;
}

const REPORTS: MediaReportRecord[] = [];
let reportSeq = 0;
function nextReportId(): string {
  reportSeq += 1;
  return `report-${reportSeq}`;
}

/** Cùng luật riêng tư với `reviewedByDisplayNameFor` — khách/không thấy người còn sống thì không thấy tên. */
function reporterDisplayNameFor(reporterId: string, role: MockRole): string | null {
  const reporter = REVIEWER_PERSON[reporterId];
  if (!reporter) return null;
  if (!canSeeLivingPersons(role)) return null;
  return reporter.displayName ?? null;
}

function namedFormatProblem(namedKey: string): Problem {
  return {
    ...problem(422, "VALIDATION_FAILED", "Định dạng tệp này chưa hỗ trợ"),
    detail: NAMED_FORMAT_DETAIL[namedKey] ?? "Định dạng tệp này chưa hỗ trợ.",
  };
}

function mediaForbidden(post: PostDto): Problem {
  return problem(
    403,
    "FORBIDDEN",
    "Không sửa được ảnh/video của bài này",
    post.status === "DRAFT"
      ? "Chỉ tác giả mới thêm/sửa/gỡ được tệp đính kèm."
      : "Bài không còn ở dạng nháp — tệp đính kèm không sửa trực tiếp được nữa."
  );
}

const mediaHandlers = [
  http.post(`${API_BASE_URL}/api/v1/posts/:id/media/uploads`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), { status: 404 });
    if (!mediaEditAllowed(identity, post)) return HttpResponse.json(mediaForbidden(post), { status: 403 });

    const body = (await request.json()) as {
      kind?: string;
      fileName?: string;
      contentType?: string;
      sizeBytes?: number;
    };
    const fileName = body.fileName ?? "";

    // Từ chối theo TÊN trước — HEIC/GIF/MOV/MKV kèm cách sửa, ưu tiên hơn một
    // câu "định dạng không hỗ trợ" chung chung. Máy chủ không tin client đã
    // chặn được — kiểm lại CHÍNH nó, đúng tinh thần "mock khó bằng thật".
    const namedKey = namedRejectionKey(fileName);
    if (namedKey) {
      return HttpResponse.json(namedFormatProblem(namedKey), { status: 422 });
    }

    const kind = body.kind;
    if (kind !== "IMAGE" && kind !== "VIDEO") {
      return HttpResponse.json(validationProblem("kind", "Loại tệp không hợp lệ."), { status: 422 });
    }
    const limits = MEDIA_POLICY[kind === "IMAGE" ? "image" : "video"];
    if (!body.contentType || !limits.mimeTypes.includes(body.contentType)) {
      return HttpResponse.json(
        validationProblem("contentType", `Định dạng không được hỗ trợ. Chỉ nhận: ${limits.mimeTypes.join(", ")}.`),
        { status: 422 }
      );
    }
    if (typeof body.sizeBytes !== "number" || body.sizeBytes <= 0) {
      return HttpResponse.json(validationProblem("sizeBytes", "Thiếu dung lượng tệp."), { status: 422 });
    }
    if (body.sizeBytes > limits.maxBytes) {
      return HttpResponse.json(
        {
          ...problem(413, "VALIDATION_FAILED", "Tệp vượt quá dung lượng cho phép"),
          detail: `Trần cho ${kind === "IMAGE" ? "ảnh" : "video"} là ${Math.round(limits.maxBytes / (1024 * 1024))} MiB.`,
        },
        { status: 413 }
      );
    }
    const attachedAndPending =
      post.media.length + [...PENDING_UPLOADS.values()].filter((u) => u.postId === post.id).length;
    if (attachedAndPending >= MEDIA_POLICY.maxAttachmentsPerPost) {
      return HttpResponse.json(
        validationProblem("kind", `Một bài chỉ mang tối đa ${MEDIA_POLICY.maxAttachmentsPerPost} tệp.`),
        { status: 422 }
      );
    }

    const uploadId = nextUploadId();
    const key = `posts/${post.id}/${uploadId}-${fileName || "tep"}`;
    PENDING_UPLOADS.set(uploadId, {
      uploadId,
      postId: post.id,
      kind,
      contentType: body.contentType,
      sizeBytes: body.sizeBytes,
      key,
      fileName,
    });

    return HttpResponse.json(
      {
        uploadId,
        key,
        uploadUrl: storageUrl(key),
        uploadHeaders: { "Content-Type": body.contentType },
        expiresAt: new Date(Date.now() + MEDIA_POLICY.uploadUrlExpiresInSeconds * 1000).toISOString(),
      },
      { status: 201 }
    );
  }),

  // "Kho" giả — KHÔNG đi qua backend thật, đúng hình dạng ba bước. Đăng ký
  // cả hai method để một lượt PUT thiếu Content-Type cũng có chỗ mà rơi vào.
  http.put(`${API_BASE_URL}/__mock-storage__/:key`, async ({ request, params }) => {
    const key = decodeURIComponent(String(params.key));
    const pending = [...PENDING_UPLOADS.values()].find((u) => u.key === key);
    if (!pending) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "URL tải lên không hợp lệ hoặc đã hết hạn"), {
        status: 404,
      });
    }
    const contentType = request.headers.get("content-type") ?? "";
    if (contentType !== pending.contentType) {
      return HttpResponse.json(
        problem(400, "VALIDATION_FAILED", "Content-Type không khớp lúc xin URL tải lên"),
        { status: 400 }
      );
    }
    const bytes = await request.arrayBuffer();
    if (bytes.byteLength > pending.sizeBytes * 1.05) {
      // 5% dung sai — tránh vỡ vì làm tròn giữa client và kho, nhưng vẫn chặn một tệp bị tráo giữa chừng.
      return HttpResponse.json(problem(413, "VALIDATION_FAILED", "Tệp thực nhận lớn hơn dung lượng đã khai"), {
        status: 413,
      });
    }
    // Độ trễ thật, KHÔNG phải 0ms — để thanh tiến trình có ý nghĩa khi xem bằng `npm run dev:mock`.
    await delay(300);
    STORED_OBJECTS.set(key, { bytes, contentType });
    return new HttpResponse(null, { status: 200 });
  }),

  http.get(`${API_BASE_URL}/__mock-storage__/:key`, ({ params }) => {
    const key = decodeURIComponent(String(params.key));
    const stored = STORED_OBJECTS.get(key);
    if (!stored) return new HttpResponse(null, { status: 404 });
    return new HttpResponse(stored.bytes, { status: 200, headers: { "Content-Type": stored.contentType } });
  }),

  http.post(`${API_BASE_URL}/api/v1/posts/:id/media/uploads/:uploadId/confirm`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), { status: 404 });
    if (!mediaEditAllowed(identity, post)) return HttpResponse.json(mediaForbidden(post), { status: 403 });

    const pending = PENDING_UPLOADS.get(String(params.uploadId));
    if (!pending || pending.postId !== post.id) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy lượt tải lên này"), { status: 404 });
    }
    if (!STORED_OBJECTS.has(pending.key)) {
      return HttpResponse.json(
        problem(409, "VALIDATION_FAILED", "Tệp chưa thật sự được tải lên kho", "Xin thử tải lại tệp này."),
        { status: 409 }
      );
    }

    const body = (await request.json()) as {
      alt?: string;
      width?: number;
      height?: number;
      durationSeconds?: number;
    };
    if (pending.kind === "IMAGE" && (!body.alt || !body.alt.trim())) {
      return HttpResponse.json(
        validationProblem("alt", "Ảnh cần có chữ mô tả (alt) trước khi gắn vào bài."),
        { status: 422 }
      );
    }
    // Video: máy chủ "đọc thời lượng thật từ phần đầu tệp" — trong bộ giả
    // lập, đó là chính giá trị client gửi kèm. VẮNG MẶT nghĩa là không đọc
    // được (đúng hành vi thật đã chốt: từ chối, không coi như hợp lệ).
    if (pending.kind === "VIDEO") {
      if (body.durationSeconds === undefined) {
        return HttpResponse.json(
          {
            ...problem(422, "VALIDATION_FAILED", "Không đọc được thời lượng video"),
            detail: "Tệp có thể bị hỏng phần đầu, hoặc không phải video thật. Xin thử một tệp khác.",
          },
          { status: 422 }
        );
      }
      if (body.durationSeconds > MEDIA_POLICY.video.maxDurationSeconds) {
        return HttpResponse.json(
          {
            ...problem(422, "VALIDATION_FAILED", "Video vượt trần thời lượng"),
            detail: `Trần thời lượng video là ${MEDIA_POLICY.video.maxDurationSeconds} giây.`,
          },
          { status: 422 }
        );
      }
    }

    const item: PostMediaItem = {
      id: nextMediaId(),
      kind: pending.kind,
      url: storageUrl(pending.key),
      alt: pending.kind === "IMAGE" ? (body.alt ?? "").trim() : null,
      order: post.media.length,
      width: body.width,
      height: body.height,
      createdAt: now(),
    };
    post.media.push(item);
    post.version += 1;
    post.updatedAt = now();
    PENDING_UPLOADS.delete(pending.uploadId);

    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),

  http.patch(`${API_BASE_URL}/api/v1/posts/:id/media/order`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), { status: 404 });
    if (!mediaEditAllowed(identity, post)) return HttpResponse.json(mediaForbidden(post), { status: 403 });

    const body = (await request.json()) as { order?: string[] };
    const order = body.order ?? [];
    const currentIds = new Set(post.media.map((m) => m.id));
    const sameSet = order.length === post.media.length && order.every((id) => currentIds.has(id));
    if (!sameSet) {
      return HttpResponse.json(
        validationProblem("order", "Danh sách thứ tự không khớp các tệp đang gắn vào bài."),
        { status: 400 }
      );
    }

    const byId = new Map(post.media.map((m) => [m.id, m]));
    post.media = order.map((id, index) => ({ ...byId.get(id)!, order: index }));
    post.version += 1;
    post.updatedAt = now();
    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),

  http.patch(`${API_BASE_URL}/api/v1/posts/:id/media/:mediaId`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), { status: 404 });
    if (!mediaEditAllowed(identity, post)) return HttpResponse.json(mediaForbidden(post), { status: 403 });

    const item = post.media.find((m) => m.id === params.mediaId);
    if (!item) return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy tệp này"), { status: 404 });
    if (item.kind === "VIDEO") {
      return HttpResponse.json(validationProblem("alt", "Video không có chữ thay thế."), { status: 422 });
    }

    const body = (await request.json()) as { alt?: string };
    if (!body.alt || !body.alt.trim()) {
      return HttpResponse.json(validationProblem("alt", "Chữ mô tả không được để trống."), { status: 422 });
    }
    item.alt = body.alt.trim();
    post.version += 1;
    post.updatedAt = now();
    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),

  http.delete(`${API_BASE_URL}/api/v1/posts/:id/media/:mediaId`, ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), { status: 404 });
    if (!mediaEditAllowed(identity, post)) return HttpResponse.json(mediaForbidden(post), { status: 403 });

    const index = post.media.findIndex((m) => m.id === params.mediaId);
    if (index === -1) return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy tệp này"), { status: 404 });
    post.media.splice(index, 1);
    post.media = post.media.map((m, i) => ({ ...m, order: i }));
    post.version += 1;
    post.updatedAt = now();
    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),

  http.get(`${API_BASE_URL}/api/v1/media/policy`, () => HttpResponse.json(MEDIA_POLICY)),

  http.post(`${API_BASE_URL}/api/v1/media/:mediaId/reports`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    if (!identity.appUserId) {
      return HttpResponse.json(problem(401, "UNAUTHENTICATED", "Cần đăng nhập để báo gỡ một tệp"), {
        status: 401,
      });
    }
    const mediaId = String(params.mediaId);
    const owner = findMediaOwner(mediaId);
    if (!owner || !visibleTo(owner.post, identity)) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy tệp này"), { status: 404 });
    }

    const body = (await request.json()) as { reason?: MediaReportReason; note?: string };
    const reason = body.reason;
    const validReasons: MediaReportReason[] = ["RIENG_TU", "SAI_NGUOI", "KHONG_PHU_HOP", "BAN_QUYEN", "KHAC"];
    if (!reason || !validReasons.includes(reason)) {
      return HttpResponse.json(validationProblem("reason", "Xin chọn một lý do."), { status: 422 });
    }
    if (reason === "KHAC" && !body.note?.trim()) {
      return HttpResponse.json(
        validationProblem("note", "Chọn \"Khác\" thì xin nói rõ vì sao tệp này cần được xem lại."),
        { status: 422 }
      );
    }

    // Một người chỉ mở được MỘT báo cáo đang treo cho mỗi tệp.
    const alreadyOpen = REPORTS.some(
      (r) => r.mediaId === mediaId && r.reporterId === identity.appUserId && r.status === "PENDING"
    );
    if (alreadyOpen) {
      return HttpResponse.json(
        problem(409, "VALIDATION_FAILED", "Đã có một báo cáo đang chờ xử lý cho tệp này"),
        { status: 409 }
      );
    }

    const report: MediaReportRecord = {
      id: nextReportId(),
      mediaId,
      postId: owner.post.id,
      reporterId: identity.appUserId,
      reason,
      note: body.note?.trim() || null,
      status: "PENDING",
      createdAt: now(),
    };
    REPORTS.push(report);
    return HttpResponse.json({ id: report.id }, { status: 201 });
  }),

  http.get(`${API_BASE_URL}/api/v1/media/reports`, ({ request }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get("limit") ?? "50");

    const inScope = REPORTS.filter((r) => r.status === "PENDING")
      .map((r) => ({ report: r, owner: findMediaOwner(r.mediaId) }))
      .filter((x): x is { report: MediaReportRecord; owner: NonNullable<ReturnType<typeof findMediaOwner>> } =>
        Boolean(x.owner) && canReviewPost(identity, x.owner!.post)
      )
      .sort((a, b) => b.report.createdAt.localeCompare(a.report.createdAt))
      .slice(0, limit);

    const dtos: MediaReportDto[] = inScope.map(({ report, owner }) => ({
      id: report.id,
      mediaId: report.mediaId,
      mediaUrl: owner.item.url,
      mediaKind: owner.item.kind,
      postId: owner.post.id,
      postTitle: owner.post.title,
      reason: report.reason,
      note: report.note,
      reporterDisplayName: reporterDisplayNameFor(report.reporterId, role),
      createdAt: report.createdAt,
    }));
    return HttpResponse.json(dtos);
  }),

  http.post(`${API_BASE_URL}/api/v1/media/reports/:id/takedown`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const report = REPORTS.find((r) => r.id === params.id);
    if (!report || report.status !== "PENDING") {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy báo cáo đang chờ này"), { status: 404 });
    }
    const owner = findMediaOwner(report.mediaId);
    if (!owner || !canReviewPost(identity, owner.post)) {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Không có quyền duyệt báo cáo này"), { status: 403 });
    }

    const body = (await request.json().catch(() => ({}))) as { note?: string };
    // Xoá byte NGAY, cùng một "transaction" (đồng bộ trong tay xử lý này) —
    // không thời gian ân hạn, không nút hoàn tác.
    const index = owner.post.media.findIndex((m) => m.id === report.mediaId);
    if (index !== -1) {
      owner.post.media.splice(index, 1);
      owner.post.media = owner.post.media.map((m, i) => ({ ...m, order: i }));
      owner.post.version += 1;
      owner.post.updatedAt = now();
    }
    report.status = "TAKEN_DOWN";
    report.decidedBy = identity.appUserId ?? undefined;
    report.decidedAt = now();
    report.decisionNote = body.note?.trim() || null;

    return HttpResponse.json({ id: report.id });
  }),

  http.post(`${API_BASE_URL}/api/v1/media/reports/:id/dismiss`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const report = REPORTS.find((r) => r.id === params.id);
    if (!report || report.status !== "PENDING") {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy báo cáo đang chờ này"), { status: 404 });
    }
    const owner = findMediaOwner(report.mediaId);
    if (!owner || !canReviewPost(identity, owner.post)) {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Không có quyền duyệt báo cáo này"), { status: 403 });
    }

    const body = (await request.json().catch(() => ({}))) as { note?: string };
    report.status = "DISMISSED";
    report.decidedBy = identity.appUserId ?? undefined;
    report.decidedAt = now();
    report.decisionNote = body.note?.trim() || null;

    return HttpResponse.json({ id: report.id });
  }),
];

export const postHandlers = [...corePostHandlers, ...mediaHandlers];
