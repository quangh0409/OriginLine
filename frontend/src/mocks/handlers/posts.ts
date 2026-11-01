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
import type {
  MediaAsset,
  MediaKind,
  MediaPolicy,
  MediaReportDto,
  MediaReportReason,
  PostMediaItem,
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
 * `withdraw` hợp lệ từ MỌI trạng thái trừ `WITHDRAWN` — khớp `Post.withdraw` phía
 * backend: gỡ bài đã đăng, VÀ là lối duy nhất để tác giả bỏ một bản nháp/bài đang chờ
 * (hợp đồng không có lệnh xoá bài). Bản trước của mock chỉ cho gỡ từ `PUBLISHED`.
 *
 * Nháp được phép THÂN RỖNG (chỉ tiêu đề bắt buộc ở `POST /posts`); `/submit` mới đòi thân
 * (`422 VALIDATION_FAILED`). `PATCH` chỉ tác giả + chỉ `DRAFT` — người duyệt KHÔNG sửa được bài
 * đang chờ (xem ghi chú ở handler `PATCH`).
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
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600"><rect width="100%" height="100%" fill="${fill}"/><text x="50%" y="50%" font-family="sans-serif" font-size="32" text-anchor="middle" fill="saddlebrown">${label}</text></svg>`;
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
        contentType: "image/jpeg",
        sizeBytes: 512_000,
        durationMs: null,
        url: sampleImage("Mái nhà thờ mới", "%23e9dcc8"),
        alt: "Mái ngói nhà thờ họ sau khi trùng tu, nhìn từ sân trước",
        position: 0,
      },
      {
        id: "media-seed-2",
        kind: "IMAGE",
        contentType: "image/jpeg",
        sizeBytes: 480_000,
        durationMs: null,
        url: sampleImage("Sân trước", "%23f2e6d3"),
        alt: "Sân trước nhà thờ họ đã lát lại gạch",
        position: 1,
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
/**
 * Khách hỏi bất kỳ lối đọc `/posts` nào đều nhận `401`, y như máy thật.
 *
 * Hợp đồng ghi thẳng "Khách vãng lai không dùng được endpoint này (`401`): toàn
 * bộ `/posts` đòi token", và máy chủ đo ngày 24/09 trả đúng như vậy. Bản trước
 * của mock phục vụ khách `200` với các bài đã đăng — "dễ hơn thật" — nên trang
 * chủ và bộ kiểm của nó tin rằng khách đọc được bài, trong khi trên máy thật
 * khách thấy "Chưa có bài viết nào được đăng." (đọc nhầm `401` thành rỗng).
 * Bài viết đóng với khách là đúng luật: mỗi bài mang tên tác giả, một người
 * đang sống (BA v2 §10).
 */
function guestReadRefused(role: MockRole) {
  if (role !== "guest") return null;
  return HttpResponse.json(
    {
      type: "https://giapha.vn/problems/unauthorized",
      title: "Chưa đăng nhập",
      status: 401,
      detail: "Yêu cầu cần một JWT hợp lệ do Keycloak cấp",
      instance: "/api/v1/posts",
      code: "UNAUTHENTICATED",
    } satisfies Problem,
    { status: 401, headers: { "Content-Type": "application/problem+json" } }
  );
}

const corePostHandlers = [
  http.get(`${API_BASE_URL}/api/v1/posts`, ({ request }) => {
    const role = resolveMockRole(request);
    const refused = guestReadRefused(role);
    if (refused) return refused;
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
    const refused = guestReadRefused(role);
    if (refused) return refused;
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get("limit") ?? "6");
    const published = STORE.filter((p) => p.status === "PUBLISHED").sort((a, b) =>
      (b.publishedAt ?? "").localeCompare(a.publishedAt ?? "")
    );
    return HttpResponse.json(published.slice(0, limit).map((p) => project(p, role)));
  }),

  http.get(`${API_BASE_URL}/api/v1/posts/:id`, ({ request, params }) => {
    const role = resolveMockRole(request);
    const refused = guestReadRefused(role);
    if (refused) return refused;
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
    // Khớp backend thật: chỉ `title` bắt buộc (bean validation ⇒ 400, không phải 422). Thân rỗng
    // là HỢP LỆ ở bản nháp — màn soạn tạo nháp ngay khi người viết rời ô tiêu đề (đổi 24/09/2026,
    // xem `CreatePostRequest` trong hợp đồng). Luật "phải có thân" nằm ở `/submit`.
    if (!body.title || !body.title.trim()) {
      return HttpResponse.json(
        { ...validationProblem("title", "Tieu de bai viet khong duoc rong"), status: 400 },
        { status: 400 }
      );
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

    // Khớp backend thật (`PostService.update`): CHỈ tác giả — người duyệt trong phạm vi KHÔNG
    // sửa được bài đang chờ duyệt. Bản trước của mock cho người duyệt sửa bài PENDING, nên nút
    // "Sửa nhẹ rồi đăng" xanh trên MSW mà luôn nhận 403 "Chi tac gia moi duoc sua bai viet" trên
    // máy chủ thật (đo bằng trình duyệt 24/09/2026) — lần thứ bảy "mock dễ hơn thật".
    // Thứ tự kiểm cũng khớp: tác giả (403) → If-Match (412/409) → trạng thái (409 CONTENT_CLOSED).
    const isAuthor = identity.personId === post.authorPersonId;
    if (!isAuthor) {
      return HttpResponse.json(
        problem(403, "FORBIDDEN", "Không đủ thẩm quyền", "Chi tac gia moi duoc sua bai viet"),
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

    if (post.status !== "DRAFT") {
      return HttpResponse.json(
        problem(409, "CONTENT_CLOSED", "Nội dung đã thay đổi", "Chi sua duoc bai o trang thai DRAFT"),
        { status: 409 }
      );
    }

    const patch = (await request.json()) as PostPatchBody;
    if (patch.title !== undefined && !patch.title.trim()) {
      return HttpResponse.json(validationProblem("title", "Tieu de bai viet khong duoc rong"), {
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
    // Khớp backend thật: gửi lại một bài đã PENDING (hay bất kỳ trạng thái nào khác DRAFT) là
    // 409 CONTENT_CLOSED — bản trước của mock trả 200 "cho qua", dễ hơn máy chủ thật.
    if (post.status !== "DRAFT") {
      return HttpResponse.json(
        problem(409, "CONTENT_CLOSED", "Nội dung đã thay đổi", "Chi gui duyet duoc bai o trang thai DRAFT"),
        { status: 409 }
      );
    }
    // Cửa đòi thân bài — nháp được rỗng thân, bài gửi đi thì không (422, bài ở lại DRAFT).
    if (!post.body.trim()) {
      return HttpResponse.json(
        {
          ...problem(
            422,
            "VALIDATION_FAILED",
            "Vi phạm quy tắc nghiệp vụ",
            "Than bai viet con trong — viet vai dong roi hay gui duyet"
          ),
        },
        { status: 422 }
      );
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
    if (!canReviewPost(identity, post)) {
      return HttpResponse.json(
        problem(403, "FORBIDDEN", "Không duyệt được bài này", "Sai phạm vi chi/ngành."),
        { status: 403 }
      );
    }
    // Khớp backend thật: đúng phạm vi mà bài không còn chờ duyệt là 409 CONTENT_CLOSED
    // (hai người duyệt cùng bấm), không phải 403.
    if (post.status !== "PENDING") {
      return HttpResponse.json(
        problem(409, "CONTENT_CLOSED", "Nội dung đã thay đổi", "Chi duyet duoc bai o trang thai PENDING"),
        { status: 409 }
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
    // Khớp backend thật (`Post.withdraw`): tác giả gỡ được cả nháp/bài chờ duyệt (bỏ nháp), không
    // chỉ bài đã đăng; chỉ bài đã gỡ rồi mới là 409 CONTENT_CLOSED.
    const canWithdraw = identity.personId === post.authorPersonId || canReviewPost(identity, post);
    if (!canWithdraw) {
      return HttpResponse.json(problem(403, "FORBIDDEN", "Không gỡ được bài này"), { status: 403 });
    }
    if (post.status === "WITHDRAWN") {
      return HttpResponse.json(
        problem(409, "CONTENT_CLOSED", "Nội dung đã thay đổi", "Bai viet da o trang thai WITHDRAWN"),
        { status: 409 }
      );
    }
    post.status = "WITHDRAWN";
    post.version += 1;
    post.updatedAt = now();
    return HttpResponse.json(project(post, role), { headers: { ETag: etagFor(post) } });
  }),
];

/* ══════════════════════════════════════════════════════════════════════════
 * Media — ảnh/video đính kèm (xem javadoc `src/lib/api/media.ts`)
 *
 * <h2>Đồng bộ đợt ba — hình dạng THẬT, không còn suy đoán</h2>
 * Bản trước của khối này tưởng tượng một hợp đồng LỒNG (`MediaPolicy.image.
 * maxBytes`) và bốn điểm cuối dưới `/posts/{id}/media/**` (`uploads`,
 * `uploads/{id}/confirm`, `order`, `/{mediaId}`) — đây là lần THỨ SÁU cái bẫy
 * "mock giả lập một API dễ hơn/khác hình dạng hơn API thật" lặp lại ở dự án
 * này (`design 07`/`Danh_Gia_Gop_Y_Spec_Gia_Pha.html` gọi tên nó). Hình dạng
 * thật, đối chiếu với `contracts/openapi.yaml` VÀ mã nguồn backend
 * (`MediaController`, `MediaDtos`, `PostController.setMedia`):
 * <ul>
 *   <li><b>`MediaPolicyDto` PHẲNG</b> — không có `image`/`video` lồng nhau;</li>
 *   <li><b>`POST /media/upload-tickets`</b> chỉ nhận `{kind, sizeBytes}` —
 *       KHÔNG `fileName`/`contentType`/`postId`. Một tệp tồn tại độc lập với
 *       bài; server không biết tên tệp gốc ở bất kỳ bước nào;</li>
 *   <li><b>`POST /media/{mediaId}/confirm`</b> trả một tệp TRẦN TRỤI
 *       (`MediaAssetDto`), chưa gắn vào bài nào (`position=0`, `url=null`);</li>
 *   <li><b>`PUT /posts/{id}/media` {@code {mediaIds}}</b> (của `content`, không
 *       phải `media`) là điểm DUY NHẤT ghi danh sách tệp của một bài — "gắn
 *       mới"/"sắp lại"/"gỡ" đều là CÙNG lệnh này với một danh sách khác nhau.
 *       Không còn `PATCH .../media/order` hay `PATCH .../media/{mediaId}`
 *       (sửa `alt` sau khi gắn) — hợp đồng thật không có chỗ cho nó.</li>
 * </ul>
 *
 * <h2>Bộ giả lập vẫn phải khó bằng thật, không dễ hơn</h2>
 * <ul>
 *   <li>xin phiếu kiểm `sizeBytes` so với trần TRƯỚC KHI cấp URL;</li>
 *   <li>`PUT` lên "kho" giả có độ trễ thật (không phải 0ms) để thanh tiến
 *       trình có ý nghĩa;</li>
 *   <li>xác nhận đòi tệp ĐÃ THẬT SỰ được `PUT` lên (`STORED_OBJECTS`), rồi tự
 *       DÒ CHỮ KÝ BYTE (không tin `sizeBytes`/loại client khai) — đúng tinh
 *       thần "chữ ký byte là phép kiểm thật, MIME chỉ là gợi ý" mà
 *       `MediaKind`/`MediaSignature` phía backend ghi rõ;</li>
 *   <li>gắn vào bài đòi tệp đã `CONFIRMED` VÀ đúng chủ — hai điều kiện độc
 *       lập, hai mã lỗi khác nhau (`409 MEDIA_NOT_UPLOADED` /
 *       `403 MEDIA_NOT_OWNED`), khớp đúng bảng lỗi trong
 *       `contracts/openapi.yaml` (~5904).</li>
 * </ul>
 * ══════════════════════════════════════════════════════════════════════════ */

/**
 * Chính sách media — CÙNG con số `MediaLimits` phía backend thật
 * (`MediaController.policy()`), hình dạng PHẲNG đúng `MediaPolicyDto`.
 * `GET /media/policy` trả nguyên object này; các handler dưới đây dùng
 * CHÍNH nó để kiểm, không phải một bộ số khác.
 */
const MEDIA_POLICY: MediaPolicy = {
  maxImageBytes: 8 * 1024 * 1024, // 8 MiB
  maxVideoBytes: 100 * 1024 * 1024, // 100 MiB — ngân sách TẢI VỀ, không phải tải lên
  maxVideoDurationSeconds: 120,
  maxMediaPerPost: 12,
  maxAltLength: 300,
  imageContentTypes: ["image/jpeg", "image/png", "image/webp"],
  videoContentTypes: ["video/mp4", "video/webm"],
  uploadUrlTtlSeconds: 15 * 60,
  viewUrlTtlSeconds: 10 * 60,
};

/**
 * Một phiếu/tệp trên "kho" giả — tồn tại ĐỘC LẬP với bài, đúng như backend
 * thật (`media_asset` không mang khoá bài nào; việc gắn là một bảng nối
 * riêng). `status` đi từ `PENDING` (đã xin phiếu, chưa xác nhận) →
 * `CONFIRMED` (đã dò chữ ký byte, có `alt` nếu là ảnh) — không có bước lùi.
 */
interface MockMediaAsset {
  id: string;
  kind: MediaKind;
  /** Chỉ có sau khi xác nhận — dò được từ chữ ký byte, không phải MIME client khai lúc xin phiếu (không hề có trường đó). */
  contentType: string | null;
  sizeBytes: number;
  durationMs: number | null;
  alt: string | null;
  key: string;
  ownerAppUserId: string;
  status: "PENDING" | "CONFIRMED";
}

const ASSETS = new Map<string, MockMediaAsset>();
/** Đối tượng đã thật sự `PUT` lên "kho" — byte + `Content-Type` trình duyệt tự gắn, để dò chữ ký byte lúc xác nhận. */
const STORED_OBJECTS = new Map<string, { bytes: ArrayBuffer; contentType: string }>();

let mediaSeq = 0;
function nextMediaId(): string {
  mediaSeq += 1;
  return `media-${mediaSeq}`;
}

function storageUrl(key: string): string {
  return `${API_BASE_URL}/__mock-storage__/${encodeURIComponent(key)}`;
}

/**
 * Dò CHỮ KÝ BYTE — cùng tinh thần `MediaSignature` phía backend thật: MIME
 * client khai không phải phép kiểm, những byte đầu tệp mới là. Năm định dạng
 * được nhận (JPEG/PNG/WebP/MP4/WebM); HEIC/GIF/MOV/MKV và bất kỳ thứ gì khác
 * đều rơi vào `null` → `MEDIA_BAD_SIGNATURE` ở nơi gọi.
 */
function detectSignature(bytes: ArrayBuffer): { contentType: string; kind: MediaKind } | null {
  const head = new Uint8Array(bytes.slice(0, 32));
  const ascii = (offset: number, len: number) =>
    String.fromCharCode(...head.slice(offset, offset + len));

  if (head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff) {
    return { contentType: "image/jpeg", kind: "IMAGE" };
  }
  if (head[0] === 0x89 && head[1] === 0x50 && head[2] === 0x4e && head[3] === 0x47) {
    return { contentType: "image/png", kind: "IMAGE" };
  }
  if (ascii(0, 4) === "RIFF" && ascii(8, 4) === "WEBP") {
    return { contentType: "image/webp", kind: "IMAGE" };
  }
  if (ascii(4, 4) === "ftyp") {
    return { contentType: "video/mp4", kind: "VIDEO" };
  }
  if (head[0] === 0x1a && head[1] === 0x45 && head[2] === 0xdf && head[3] === 0xa3) {
    return { contentType: "video/webm", kind: "VIDEO" };
  }
  return null;
}

/** Cùng cổng quyền với sửa thân bài (chỉ tác giả, chỉ khi còn `DRAFT`) — "ảnh đi theo quyền của bài". */
function mediaEditAllowed(identity: MockIdentity, post: PostDto): boolean {
  return post.status === "DRAFT" && identity.personId === post.authorPersonId;
}

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
  /** Đúng enum `MediaReportStatus` của hợp đồng — bản trước dùng `PENDING`/`TAKEN_DOWN` tự đặt. */
  status: "OPEN" | "ACTIONED" | "DISMISSED";
  createdAt: string;
  decidedBy?: string;
  decidedAt?: string;
  decisionNote?: string | null;
  version: number;
  /** URL đọc lúc tệp còn — `mediaUrl` là `null` sau khi đã gỡ. */
  mediaUrl: string | null;
  mediaKind: MediaKind;
}

/**
 * Hình dạng THẬT của `MediaReportDto` (hợp đồng + `MediaDtos.MediaReportDto`). Bản trước của mock
 * trả `postId`/`postTitle`/`reporterDisplayName` — ba trường máy chủ không bao giờ gửi — và màn
 * duyệt báo cáo dựng lên trên chúng, nên trên hệ thống thật hiện một đường dẫn rỗng.
 */
function toReportDto(report: MediaReportRecord): MediaReportDto {
  return {
    id: report.id,
    mediaId: report.mediaId,
    mediaKind: report.mediaKind,
    mediaUrl: report.status === "ACTIONED" ? null : report.mediaUrl,
    reason: report.reason,
    note: report.note,
    reportedBy: report.reporterId,
    status: report.status,
    reviewedBy: report.decidedBy ?? null,
    reviewedAt: report.decidedAt ?? null,
    resolutionNote: report.decisionNote ?? null,
    createdAt: report.createdAt,
    version: report.version,
  };
}

const REPORTS: MediaReportRecord[] = [];
let reportSeq = 0;
function nextReportId(): string {
  reportSeq += 1;
  return `report-${reportSeq}`;
}

function mediaForbidden(post: PostDto): Problem {
  return problem(
    403,
    "FORBIDDEN",
    "Không sửa được ảnh/video của bài này",
    post.status === "DRAFT"
      ? "Chỉ tác giả mới gắn/sắp lại/gỡ được tệp đính kèm."
      : "Bài không còn ở dạng nháp — tệp đính kèm không sửa trực tiếp được nữa."
  );
}

/**
 * Hai mồi sẵn (`media-seed-1`/`media-seed-2`, gắn với `post-3` của
 * `LOGGED_IN_BRANCH_HEAD_FULL`) cần một `MockMediaAsset` tương ứng để
 * `PUT /posts/:id/media` (gắn/sắp lại/gỡ) hoạt động được trên chúng, giống
 * hệt một tệp đã trải qua đúng ba bước thật — không có lối tắt "bỏ qua
 * `ASSETS` cho riêng dữ liệu mồi".
 */
for (const seed of [
  { id: "media-seed-1", sizeBytes: 512_000, alt: "Mái ngói nhà thờ họ sau khi trùng tu, nhìn từ sân trước" },
  { id: "media-seed-2", sizeBytes: 480_000, alt: "Sân trước nhà thờ họ đã lát lại gạch" },
]) {
  ASSETS.set(seed.id, {
    id: seed.id,
    kind: "IMAGE",
    contentType: "image/jpeg",
    sizeBytes: seed.sizeBytes,
    durationMs: null,
    alt: seed.alt,
    key: `seed/${seed.id}`,
    ownerAppUserId: "u-branch-head",
    status: "CONFIRMED",
  });
}

const mediaHandlers = [
  /**
   * Bước 1/3 — `POST /media/upload-tickets`. KHÔNG cần `postId` (một tệp
   * tồn tại độc lập với bài). Kiểm `sizeBytes` so với trần TRƯỚC KHI cấp
   * URL — đúng tinh thần `413 MEDIA_TOO_LARGE` "từ chối trước khi tốn băng
   * thông".
   */
  http.post(`${API_BASE_URL}/api/v1/media/upload-tickets`, async ({ request }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    if (!identity.appUserId) {
      return HttpResponse.json(problem(401, "UNAUTHENTICATED", "Cần đăng nhập để tải tệp"), { status: 401 });
    }

    const body = (await request.json()) as { kind?: string; sizeBytes?: number };
    const kind = body.kind;
    if (kind !== "IMAGE" && kind !== "VIDEO") {
      return HttpResponse.json(validationProblem("kind", "Phải nói rõ IMAGE hay VIDEO."), { status: 422 });
    }
    if (typeof body.sizeBytes !== "number" || body.sizeBytes <= 0) {
      return HttpResponse.json(validationProblem("sizeBytes", "Phải khai kích thước tệp."), { status: 422 });
    }
    const maxBytes = kind === "IMAGE" ? MEDIA_POLICY.maxImageBytes : MEDIA_POLICY.maxVideoBytes;
    if (body.sizeBytes > maxBytes) {
      return HttpResponse.json(
        {
          ...problem(413, "MEDIA_TOO_LARGE", "Tệp vượt quá dung lượng cho phép"),
          detail: `Trần cho ${kind === "IMAGE" ? "ảnh" : "video"} là ${Math.round(maxBytes / (1024 * 1024))} MiB.`,
        },
        { status: 413 }
      );
    }

    const mediaId = nextMediaId();
    const key = `media/${mediaId}`;
    ASSETS.set(mediaId, {
      id: mediaId,
      kind,
      contentType: null,
      sizeBytes: body.sizeBytes,
      durationMs: null,
      alt: null,
      key,
      ownerAppUserId: identity.appUserId,
      status: "PENDING",
    });

    return HttpResponse.json(
      {
        uploadUrl: storageUrl(key),
        mediaId,
        mediaKey: key,
        expiresAt: new Date(Date.now() + MEDIA_POLICY.uploadUrlTtlSeconds * 1000).toISOString(),
        maxBytes,
        maxDurationSeconds: kind === "VIDEO" ? MEDIA_POLICY.maxVideoDurationSeconds : null,
        acceptedContentTypes: kind === "IMAGE" ? MEDIA_POLICY.imageContentTypes : MEDIA_POLICY.videoContentTypes,
      },
      { status: 200, headers: { "Cache-Control": "no-store, private" } }
    );
  }),

  // "Kho" giả — KHÔNG đi qua backend thật, đúng hình dạng ba bước. Không
  // kiểm `Content-Type` ở đây: hợp đồng thật không đưa header nào vào chữ ký
  // của URL đã ký (xem `MinioObjectStorageAdapter.presign`), nên client chỉ
  // gửi thân tệp — loại thật được dò lại bằng chữ ký byte lúc XÁC NHẬN.
  http.put(`${API_BASE_URL}/__mock-storage__/:key`, async ({ request, params }) => {
    const key = decodeURIComponent(String(params.key));
    const asset = [...ASSETS.values()].find((a) => a.key === key);
    if (!asset) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "URL tải lên không hợp lệ hoặc đã hết hạn"), {
        status: 404,
      });
    }
    const bytes = await request.arrayBuffer();
    const contentType = request.headers.get("content-type") ?? "application/octet-stream";
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

  /**
   * Bước 3/3 — `POST /media/{mediaId}/confirm`. Trả một tệp TRẦN TRỤI
   * (`position=0`, `url=null`) — KHÔNG gắn vào bài nào ở đây, khác hẳn bản
   * trước (từng trả cả bài đã cập nhật). Dò CHỮ KÝ BYTE thật sự
   * ({@link detectSignature}), không tin `kind` client khai lúc xin phiếu.
   */
  http.post(`${API_BASE_URL}/api/v1/media/:mediaId/confirm`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    if (!identity.appUserId) {
      return HttpResponse.json(problem(401, "UNAUTHENTICATED", "Cần đăng nhập để xác nhận tải lên"), {
        status: 401,
      });
    }
    const mediaId = String(params.mediaId);
    const asset = ASSETS.get(mediaId);
    if (!asset) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy phiếu tải lên này"), { status: 404 });
    }
    if (asset.ownerAppUserId !== identity.appUserId) {
      return HttpResponse.json(problem(403, "MEDIA_NOT_OWNED", "Phiếu này không phải của bạn"), { status: 403 });
    }
    if (asset.status === "CONFIRMED") {
      return HttpResponse.json(
        problem(409, "MEDIA_TICKET_CLOSED", "Phiếu đã xử lý rồi", "Gọi lại xác nhận cho cùng một mediaId không sửa được alt đã đặt."),
        { status: 409 }
      );
    }
    const stored = STORED_OBJECTS.get(asset.key);
    if (!stored) {
      return HttpResponse.json(
        {
          ...problem(409, "MEDIA_NOT_UPLOADED", "Tệp chưa thật sự được tải lên kho"),
          detail: "Xin thử tải lại tệp này.",
        },
        { status: 409 }
      );
    }

    const signature = detectSignature(stored.bytes);
    if (!signature || signature.kind !== asset.kind) {
      // Ba mã đầu (chữ ký sai/quá trần) XOÁ tệp khỏi kho ngay — khớp
      // `contracts/openapi.yaml` (~5652).
      STORED_OBJECTS.delete(asset.key);
      ASSETS.delete(mediaId);
      return HttpResponse.json(
        {
          ...problem(422, "MEDIA_BAD_SIGNATURE", "Định dạng tệp này chưa hỗ trợ"),
          detail:
            "Những byte đầu của tệp không khớp một định dạng được nhận (hoặc khớp một định dạng bị loại: HEIC, GIF, MOV, MKV), hoặc khác loại đã khai lúc xin phiếu.",
        },
        { status: 422 }
      );
    }
    const maxBytes = asset.kind === "IMAGE" ? MEDIA_POLICY.maxImageBytes : MEDIA_POLICY.maxVideoBytes;
    if (stored.bytes.byteLength > maxBytes) {
      STORED_OBJECTS.delete(asset.key);
      ASSETS.delete(mediaId);
      return HttpResponse.json(
        {
          ...problem(413, "MEDIA_TOO_LARGE", "Tệp thực nhận lớn hơn dung lượng cho phép"),
          detail: `Trần là ${Math.round(maxBytes / (1024 * 1024))} MiB.`,
        },
        { status: 413 }
      );
    }

    const body = (await request.json().catch(() => ({}))) as { alt?: string };
    // `VALIDATION_FAILED` (thiếu `alt`) KHÔNG xoá tệp — người dùng chỉ cần
    // gõ thêm rồi gọi lại đúng điểm cuối này, không phải tải lại từ đầu.
    if (asset.kind === "IMAGE" && !body.alt?.trim()) {
      return HttpResponse.json(
        validationProblem("alt", "Ảnh cần có chữ mô tả (alt) trước khi xác nhận."),
        { status: 422 }
      );
    }
    if (body.alt && body.alt.length > MEDIA_POLICY.maxAltLength) {
      return HttpResponse.json(
        validationProblem("alt", `Chữ thay ảnh tối đa ${MEDIA_POLICY.maxAltLength} ký tự.`),
        { status: 422 }
      );
    }

    asset.status = "CONFIRMED";
    asset.contentType = signature.contentType;
    asset.alt = asset.kind === "IMAGE" ? (body.alt ?? "").trim() : null;
    // Video thật đọc thời lượng từ header container; bộ giả lập không phân
    // tích byte video thật (không bài kiểm nào ở đây tải một video thật lên),
    // nên gán 0 thay vì bịa một con số không kiểm chứng được.
    asset.durationMs = asset.kind === "VIDEO" ? 0 : null;

    const dto: MediaAsset = {
      id: asset.id,
      kind: asset.kind,
      contentType: asset.contentType,
      sizeBytes: asset.sizeBytes,
      durationMs: asset.durationMs,
      alt: asset.alt,
      position: 0,
      url: null,
    };
    return HttpResponse.json(dto, { status: 200, headers: { "Cache-Control": "no-store, private" } });
  }),

  /**
   * `PUT /posts/{id}/media` — của `content`, không `media`, nhưng vẫn sống
   * ở đây vì nó dùng chung `mediaEditAllowed`/`ASSETS` với phần còn lại của
   * khối này. Đặt CẢ danh sách, đúng thứ tự — gắn tệp mới/sắp lại/gỡ đều là
   * CÙNG lệnh này với một `mediaIds` khác nhau (không có lệnh riêng nào).
   */
  http.put(`${API_BASE_URL}/api/v1/posts/:id/media`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const post = STORE.find((p) => p.id === params.id);
    if (!post) return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy bài viết"), { status: 404 });
    if (!mediaEditAllowed(identity, post)) return HttpResponse.json(mediaForbidden(post), { status: 403 });

    const body = (await request.json()) as { mediaIds?: unknown };
    if (!Array.isArray(body.mediaIds)) {
      // Lỗi kiểm thân yêu cầu (thiếu trường bắt buộc) là 400, không 422 —
      // khớp `GlobalExceptionHandler.handleMethodArgumentNotValid` phía
      // backend thật (bean validation → 400 VALIDATION_FAILED).
      return HttpResponse.json(
        {
          ...problem(400, "VALIDATION_FAILED", "Dữ liệu gửi lên không hợp lệ"),
          errors: [{ field: "mediaIds", message: "Phải gửi danh sách tệp, dù là danh sách rỗng." }],
        },
        { status: 400 }
      );
    }
    const mediaIds = body.mediaIds as string[];
    if (mediaIds.length > MEDIA_POLICY.maxMediaPerPost) {
      return HttpResponse.json(
        problem(422, "MEDIA_TOO_MANY", "Quá nhiều tệp", `Một bài gắn tối đa ${MEDIA_POLICY.maxMediaPerPost} tệp.`),
        { status: 422 }
      );
    }

    const items: PostMediaItem[] = [];
    for (const mediaId of mediaIds) {
      const asset = ASSETS.get(mediaId);
      if (!asset || asset.status !== "CONFIRMED") {
        return HttpResponse.json(
          problem(409, "MEDIA_NOT_UPLOADED", "Một tệp trong danh sách chưa được xác nhận hoặc đã bị gỡ"),
          { status: 409 }
        );
      }
      if (asset.ownerAppUserId !== identity.appUserId) {
        return HttpResponse.json(
          problem(403, "MEDIA_NOT_OWNED", "Không gắn được tệp này", "Tệp không do bạn tải lên."),
          { status: 403 }
        );
      }
      items.push({
        id: asset.id,
        kind: asset.kind,
        contentType: asset.contentType ?? "application/octet-stream",
        sizeBytes: asset.sizeBytes,
        durationMs: asset.durationMs,
        alt: asset.alt,
        position: items.length,
        url: storageUrl(asset.key),
      });
    }

    post.media = items;
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

    // Một người chỉ mở được MỘT báo cáo đang treo cho mỗi tệp — mã thật là REPORT_DUPLICATE.
    const alreadyOpen = REPORTS.some(
      (r) => r.mediaId === mediaId && r.reporterId === identity.appUserId && r.status === "OPEN"
    );
    if (alreadyOpen) {
      return HttpResponse.json(
        problem(409, "REPORT_DUPLICATE", "Nội dung đã thay đổi", "Ban da bao tep nay va don van dang mo"),
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
      status: "OPEN",
      createdAt: now(),
      version: 0,
      mediaUrl: owner.item.url,
      mediaKind: owner.item.kind,
    };
    REPORTS.push(report);
    return HttpResponse.json(toReportDto(report), { status: 201 });
  }),

  http.get(`${API_BASE_URL}/api/v1/media/reports`, ({ request }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get("limit") ?? "50");

    const inScope = REPORTS.filter((r) => r.status === "OPEN")
      .map((r) => ({ report: r, owner: findMediaOwner(r.mediaId) }))
      .filter((x): x is { report: MediaReportRecord; owner: NonNullable<ReturnType<typeof findMediaOwner>> } =>
        Boolean(x.owner) && canReviewPost(identity, x.owner!.post)
      )
      .sort((a, b) => b.report.createdAt.localeCompare(a.report.createdAt))
      .slice(0, limit);

    const dtos: MediaReportDto[] = inScope.map(({ report, owner }) =>
      toReportDto({ ...report, mediaUrl: owner.item.url })
    );
    return HttpResponse.json(dtos);
  }),

  http.post(`${API_BASE_URL}/api/v1/media/reports/:id/takedown`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const report = REPORTS.find((r) => r.id === params.id);
    if (!report) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy báo cáo này"), { status: 404 });
    }
    // Khớp máy chủ thật: đơn đã xử lý là 409 REPORT_CLOSED, không phải 404.
    if (report.status !== "OPEN") {
      return HttpResponse.json(problem(409, "REPORT_CLOSED", "Nội dung đã thay đổi", "Don da duoc xu ly"), {
        status: 409,
      });
    }
    const owner = findMediaOwner(report.mediaId);
    if (!owner || !canReviewPost(identity, owner.post)) {
      return HttpResponse.json(problem(403, "BRANCH_SCOPE_VIOLATION", "Không có quyền duyệt báo cáo này"), { status: 403 });
    }
    if (report.reporterId === identity.appUserId) {
      return HttpResponse.json(
        problem(403, "SELF_REVIEW_FORBIDDEN", "Không đủ thẩm quyền", "Nguoi bao khong tu xu don cua minh"),
        { status: 403 }
      );
    }

    const body = (await request.json().catch(() => ({}))) as { note?: string };
    // Xoá byte NGAY, cùng một "transaction" (đồng bộ trong tay xử lý này) —
    // không thời gian ân hạn, không nút hoàn tác.
    const index = owner.post.media.findIndex((m) => m.id === report.mediaId);
    if (index !== -1) {
      owner.post.media.splice(index, 1);
      owner.post.media = owner.post.media.map((m, i) => ({ ...m, position: i }));
      owner.post.version += 1;
      owner.post.updatedAt = now();
    }
    report.status = "ACTIONED";
    report.decidedBy = identity.appUserId ?? undefined;
    report.decidedAt = now();
    report.decisionNote = body.note?.trim() || null;
    report.version += 1;

    return HttpResponse.json(toReportDto(report));
  }),

  http.post(`${API_BASE_URL}/api/v1/media/reports/:id/dismiss`, async ({ request, params }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);
    const report = REPORTS.find((r) => r.id === params.id);
    if (!report) {
      return HttpResponse.json(problem(404, "NOT_FOUND", "Không tìm thấy báo cáo này"), { status: 404 });
    }
    if (report.status !== "OPEN") {
      return HttpResponse.json(problem(409, "REPORT_CLOSED", "Nội dung đã thay đổi", "Don da duoc xu ly"), {
        status: 409,
      });
    }
    const owner = findMediaOwner(report.mediaId);
    if (!owner || !canReviewPost(identity, owner.post)) {
      return HttpResponse.json(problem(403, "BRANCH_SCOPE_VIOLATION", "Không có quyền duyệt báo cáo này"), { status: 403 });
    }

    const body = (await request.json().catch(() => ({}))) as { note?: string };
    report.status = "DISMISSED";
    report.decidedBy = identity.appUserId ?? undefined;
    report.decidedAt = now();
    report.decisionNote = body.note?.trim() || null;
    report.version += 1;

    return HttpResponse.json(toReportDto(report));
  }),
];

export const postHandlers = [...corePostHandlers, ...mediaHandlers];
