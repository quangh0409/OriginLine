import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { resolveMockRole, type MockRole } from "./role";
import { identityOf, pathIsWithin } from "../identity";
import type {
  ClaimReviewView,
  ClanInviteListDto,
  ClanInviteView,
  IssuedClanInvite,
} from "@/lib/api/membership-admin";
import type { InvitationDto, IssuedInvitationDto } from "@/lib/api/invitation";
import type { Problem, ProblemCode } from "@/types/api";

/**
 * MSW cho **phần quản trị tư cách thành viên**: hàng chờ duyệt đơn tự nhận
 * (`/api/v1/person-claims/pending`, `/{id}/review`), mã mời dòng họ
 * (`/api/v1/clan-invites`), và **nửa quản trị** của lời mời cá nhân
 * (`/api/v1/invitations` — `GET`/`POST`/`DELETE`).
 *
 * <h2>Nửa quản trị của `invitations` nằm ở ĐÂY, không ở `handlers/invitation.ts`</h2>
 * Tệp kia phục vụ người **chưa có tài khoản** và cố ý không đọc `x-mock-role`.
 * Ba lối gọi quản trị thì ngược hẳn: chúng đòi token và đi qua
 * `BranchScopeGuard`. Trộn hai chế độ xác thực vào một tệp là cách để lần sửa
 * sau vô tình cho một khách gọi được `POST /invitations`.
 *
 * <h2>Bộ giả lập này cố ý KHÓ đúng như API thật</h2>
 * Bốn chỗ dưới đây tốn công hơn một bản dễ chiều màn hình, và cả bốn đều là
 * ràng buộc có thật trong `V16__membership_clan_invite_and_claim.sql` hoặc
 * trong miền backend đã viết:
 *
 * <ul>
 *   <li><b>Phạm vi `ltree` cắt ở máy chủ.</b> Trưởng chi `root.chi_nhat` không
 *       nhận về đơn của `root.chi_nhi` — không phải "nhận rồi client lọc".</li>
 *   <li><b>Từ chối không kèm lý do trả `422`</b>, đúng như
 *       {@code PersonClaim#reject} ném lỗi. Giao diện chặn trước là phép lịch
 *       sự, không phải chốt chặn.</li>
 *   <li><b>Lá đơn chỉ mang KHOÁ nhân khẩu.</b> `PersonClaim` không có khối
 *       `person` lồng, không tên, không năm sinh — nhân khẩu ấy có thể là người
 *       còn sống ở chi khác, và bộ lọc phân tầng riêng tư chỉ chạy ở
 *       `GET /persons/{id}`. Cùng luật cho `duplicateSuspects`, thứ còn được
 *       **lưu vào đơn**: một giá trị đọc từ phả lọt vào đó là lọt vĩnh viễn.</li>
 *   <li><b>Mã thô chỉ trả về một lần.</b> Không endpoint nào đọc lại được, kể
 *       cả trong bộ giả lập.</li>
 * </ul>
 */

function problem(
  status: number,
  code: ProblemCode,
  title: string,
  instance: string
): HttpResponse<Problem> {
  const body: Problem = { type: "about:blank", title, status, code, instance };
  return HttpResponse.json(body, { status });
}

/** Ai được duyệt đơn và phát lời mời cá nhân. Khách và thành viên thường thì không. */
function coQuyenDuyet(role: MockRole): boolean {
  return role === "branch-head" || role === "admin";
}

/** Chỉ Hội đồng/Admin phát được mã dòng họ. Trưởng chi thì không. */
function coQuyenPhatMaHo(role: MockRole): boolean {
  return role === "admin";
}

/** `branch.id → ltree`, chép tay như `mocks/change-requests.ts` và vì cùng lý do. */
/**
 * Mẫu số của bộ đếm mã dòng họ — số người **đang sống** trong cả họ.
 *
 * Contract trả nó **một lần** trên phong bì của `GET /clan-invites`, không gắn
 * vào từng mã: lặp một giá trị toàn cục lên n dòng là để chúng có cơ hội lệch
 * nhau. Nó là một phép đếm, không phải một báo cáo dân số.
 */
const SO_NGUOI_DANG_SONG = 600;

const BRANCH_PATHS: Record<string, string> = {
  "b-root": "root",
  "b-chi1": "root.chi_nhat",
  "b-chi2": "root.chi_nhi",
  "b-chi3": "root.chi_tam",
  "b-chi4": "root.chi_tu",
};

/**
 * Chi đích của một lá đơn — `ClaimBranch`, và nó cố ý **không mang `path`**:
 * phép so phạm vi `ltree` là việc của máy chủ, còn người đọc lá đơn có thể là
 * một tài khoản vừa đăng ký. Bộ giả lập tra `ltree` qua {@link BRANCH_PATHS}.
 */
const CHI_NHAT = { id: "b-chi1", name: "Chi Nhất", kind: "CHI" } as const;
const CHI_NHI = { id: "b-chi2", name: "Chi Nhì", kind: "CHI" } as const;

// ============================================================================
// DỮ LIỆU MỒI — ĐƠN TỰ NHẬN
// ============================================================================

/**
 * Sáu lá đơn dựng lại đúng những ca mà màn hình phải xử đúng.
 *
 * <pre>
 *   pc-001 + pc-002  hai người CÙNG nhận p-100 (chi_nhat) → cụm tranh chấp
 *   pc-003           đơn NEW_PERSON, dò trùng CÓ ứng viên (chi_nhat)
 *   pc-004           đơn NEW_PERSON, đã dò mà KHÔNG thấy ai (chi_nhat)
 *   pc-005           đơn EXISTING của chi_nhi → Trưởng chi chi_nhat KHÔNG thấy
 *   pc-006           đơn NEW_PERSON CHƯA dò trùng (chi_nhat)
 * </pre>
 *
 * `pc-001` và `pc-002` là lá đơn quan trọng nhất của cả bộ: ca biên "hai người
 * cùng nhận một nhân khẩu" (design 07 §1.4). Chúng **không** được sắp theo thời
 * gian gửi ở giao diện, và bộ giả lập trả `pc-002` (gửi *sau*) trước `pc-001`
 * để chứng minh rằng màn hình không âm thầm dựa vào thứ tự thời gian.
 */
function seedClaims(): ClaimReviewView[] {
  return [
    {
      id: "pc-002",
      kind: "EXISTING",
      status: "PENDING",
      requestedBy: "u-khai-2",
      requesterDisplayName: "Nguyễn Văn An",
      phone: "0912 000 222",
      introduction:
        "Cháu là con thứ hai của ông Nguyễn Văn Tư, quê ở Đại Lan. Cháu sinh năm 1979, hiện ở Bình Dương.",
      // CHỈ KHOÁ. Tên, đời và chi của p-100 đến từ `GET /persons/p-100`, chạy
      // qua phiên của NGƯỜI DUYỆT — không phải của người gửi đơn.
      personId: "p-100",
      targetBranchId: CHI_NHAT.id,
      targetBranch: CHI_NHAT,
      competingClaimIds: ["pc-001"],
      attemptNo: 1,
      createdAt: "2026-09-12T02:10:00Z",
    },
    {
      id: "pc-001",
      kind: "EXISTING",
      status: "PENDING",
      requestedBy: "u-khai-1",
      requesterDisplayName: "Nguyễn Văn An",
      phone: "0912 000 111",
      introduction:
        "Tôi là Nguyễn Văn An, con cụ Nguyễn Văn Tư, sinh năm 1981, hiện sống tại Hà Nội.",
      personId: "p-100",
      targetBranchId: CHI_NHAT.id,
      targetBranch: CHI_NHAT,
      competingClaimIds: ["pc-002"],
      attemptNo: 2,
      createdAt: "2026-09-10T08:00:00Z",
    },
    {
      id: "pc-003",
      kind: "NEW_PERSON",
      status: "PENDING",
      requestedBy: "u-khai-3",
      requesterDisplayName: "Nguyễn Văn Ân",
      phone: "0987 333 444",
      introduction:
        "Cháu là con cụ Nguyễn Văn Cẩn, sinh năm 1988. Cháu mở phả đồ ra mà không tìm thấy tên cháu.",
      declaredName: "Nguyễn Văn Ân",
      declaredBirthYear: 1988,
      declaredGender: "MALE",
      relativePersonId: "p-103",
      relativeKind: "FATHER",
      targetBranchId: CHI_NHAT.id,
      targetBranch: CHI_NHAT,
      // Ảnh chụp: CHỈ khoá + điểm + tín hiệu — KHÔNG tên, không năm sinh. Giao
      // diện tự gọi `GET /persons/p-100` bằng phiên của NGƯỜI DUYỆT.
      //
      // Đây đúng ca mà bộ dò sinh ra để phục vụ: "Nguyễn Văn Ân" khai mình chưa
      // có trong phả, trong khi "Nguyễn Văn An" đã có sẵn ở cùng chi. Bỏ khối
      // này đi thì Trưởng chi tạo ra một bản trùng, và bản trùng ấy không xoá
      // được — xoá mềm là luật tuyệt đối.
      duplicateSuspects: [
        {
          personId: "p-100",
          score: 84,
          signals: ["TEN_TRUNG_KHONG_DAU", "CUNG_CHI", "NAM_SINH_LECH_IT"],
        },
      ],
      competingClaimIds: [],
      attemptNo: 1,
      createdAt: "2026-09-14T01:00:00Z",
    },
    {
      id: "pc-004",
      kind: "NEW_PERSON",
      status: "PENDING",
      requestedBy: "u-khai-4",
      requesterDisplayName: "Trần Thị Lan",
      phone: "0903 555 666",
      introduction:
        "Cháu mới về làm dâu nhà anh Nguyễn Văn Bình tháng 7 vừa rồi, chưa thấy tên cháu trong phả.",
      declaredName: "Trần Thị Lan",
      declaredBirthYear: 1996,
      declaredGender: "FEMALE",
      relativePersonId: "p-102",
      relativeKind: "SPOUSE",
      targetBranchId: CHI_NHAT.id,
      targetBranch: CHI_NHAT,
      // ĐÃ dò, không thấy ai — `[]`, KHÁC hẳn "chưa dò" (vắng mặt) ở `pc-006`.
      // Hai câu ấy dẫn tới hai quyết định khác nhau của người sắp bấm Duyệt.
      duplicateSuspects: [],
      competingClaimIds: [],
      attemptNo: 1,
      createdAt: "2026-09-15T03:00:00Z",
    },
    {
      id: "pc-005",
      kind: "EXISTING",
      status: "PENDING",
      requestedBy: "u-khai-5",
      requesterDisplayName: "Nguyễn Thị Hoa",
      phone: "0966 777 888",
      introduction: "Tôi là con gái cụ Nguyễn Văn Năm bên chi Nhì.",
      personId: "p-200",
      targetBranchId: CHI_NHI.id,
      targetBranch: CHI_NHI,
      competingClaimIds: [],
      attemptNo: 1,
      createdAt: "2026-09-16T05:00:00Z",
    },
    {
      id: "pc-006",
      kind: "NEW_PERSON",
      status: "PENDING",
      requestedBy: "u-khai-6",
      requesterDisplayName: "Nguyễn Văn Hải",
      phone: "0944 111 999",
      introduction:
        "Nhánh nhà tôi sang Pháp từ đời ông nội, phả giấy của chi không ghi tiếp nữa.",
      declaredName: "Nguyễn Văn Hải",
      declaredBirthYear: 1968,
      declaredGender: "MALE",
      relativePersonId: "p-050",
      relativeKind: "FATHER",
      targetBranchId: CHI_NHAT.id,
      targetBranch: CHI_NHAT,
      // `duplicateSuspects` VẮNG MẶT — bộ dò CHƯA chạy. Giao diện phải nói ra,
      // không im lặng: gộp nó với `[]` sẽ nói rằng hệ thống đã kiểm.
      competingClaimIds: [],
      attemptNo: 1,
      createdAt: "2026-09-17T06:00:00Z",
    },
  ];
}

let claims: ClaimReviewView[] = seedClaims();

// ============================================================================
// DỮ LIỆU MỒI — MÃ MỜI DÒNG HỌ
// ============================================================================

/**
 * Ba mã dựng lại ba câu chuyện khác nhau về bộ đếm.
 *
 * `ci-001` là mã của chính ví dụ trong design 07 §1.2: **400 lượt trên một dòng
 * họ 600 người**. Con số ấy có mặt trong fixture để màn hình phải hiện được
 * đúng cái mà Hội đồng nhìn vào rồi quyết thu hồi.
 */
function seedClanInvites(): ClanInviteView[] {
  return [
    {
      id: "ci-001",
      label: "Nhóm Zalo họ Nguyễn 2026",
      issuedBy: "u-admin",
      status: "ACTIVE",
      usability: "USABLE",
      expiresAt: "2026-10-20T17:00:00Z",
      useCount: 400,
      maxUses: 600,
      remainingUses: 200,
      note: null,
      createdAt: "2026-09-01T02:00:00Z",
    },
    {
      id: "ci-002",
      label: "Phiếu phát tại lễ giỗ tổ",
      issuedBy: "u-admin",
      status: "ACTIVE",
      usability: "USABLE",
      expiresAt: "2026-10-05T17:00:00Z",
      useCount: 12,
      maxUses: null,
      remainingUses: null,
      note: "Phát cho các cụ ở xa về dự giỗ.",
      createdAt: "2026-09-05T02:00:00Z",
    },
    {
      id: "ci-003",
      label: "Mã cũ — đã thu hồi",
      issuedBy: "u-admin",
      status: "REVOKED",
      usability: "REVOKED",
      expiresAt: "2026-09-30T17:00:00Z",
      useCount: 87,
      maxUses: 100,
      remainingUses: 13,
      revokedAt: "2026-09-08T09:00:00Z",
      revokedReason: "Mã bị chuyển tiếp ra ngoài dòng họ.",
      note: null,
      createdAt: "2026-08-20T02:00:00Z",
    },
  ];
}

let clanInvites: ClanInviteView[] = seedClanInvites();
let clanInviteSeq = 0;

// ============================================================================
// DỮ LIỆU MỒI — LỜI MỜI CÁ NHÂN (nửa quản trị)
// ============================================================================

function seedInvitations(): InvitationDto[] {
  return [
    {
      id: "inv-001",
      personId: "p-101",
      // `InviteeSummary` — BA trường, và đúng ba. Không năm sinh, không nghề
      // nghiệp, không nơi ở, không điện thoại, không ảnh: cùng ranh giới mà màn
      // nhận lời mời đã đặt, vì đây là cùng một dữ liệu đi ra ngoài.
      invitee: { displayName: "Nguyễn Thị Lan", generation: 7, branchName: CHI_NHAT.name },
      branchId: "b-chi1",
      invitedBy: "u-branch-head",
      status: "PENDING",
      usability: "USABLE",
      expiresAt: "2026-09-27T17:00:00Z",
      note: "Cụ bà không dùng điện thoại thông minh — đọc mã qua điện thoại.",
      createdAt: "2026-09-18T02:00:00Z",
    },
    {
      id: "inv-002",
      personId: "p-102",
      // Cố ý KHÔNG có `invitee`: khối ấy tuỳ chọn ở contract, và hàng phải dựng
      // được khi chỉ có `personId` — `personId` mới là thứ nút thu hồi cần.
      branchId: "b-chi1",
      invitedBy: "u-branch-head",
      status: "ACCEPTED",
      usability: "ALREADY_USED",
      expiresAt: "2026-09-20T17:00:00Z",
      acceptedBy: "u-member",
      acceptedAt: "2026-09-14T10:00:00Z",
      createdAt: "2026-09-13T02:00:00Z",
    },
  ];
}

let invitations: InvitationDto[] = seedInvitations();
let invitationSeq = 0;

/** Đưa cả ba kho về trạng thái đầu. Dùng giữa các ca kiểm. */
export function resetMembershipAdminMockState(): void {
  claims = seedClaims();
  clanInvites = seedClanInvites();
  invitations = seedInvitations();
  clanInviteSeq = 0;
  invitationSeq = 0;
}

/** Tra một lá đơn theo khoá — để bộ kiểm đối chiếu trạng thái sau khi duyệt. */
export function findClaimMock(id: string): ClaimReviewView | undefined {
  return claims.find((c) => c.id === id);
}

export function findClanInviteMock(id: string): ClanInviteView | undefined {
  return clanInvites.find((c) => c.id === id);
}

/**
 * Cắt hàng đợi theo phạm vi `ltree` của người gọi — **ở máy chủ**.
 *
 * Ngữ nghĩa `@>`: `root.chi_nhat` bao `root.chi_nhat.nganh_truong` nhưng **không**
 * bao `root.chi_nhat_khac`. Dùng lại `pathIsWithin` của `mocks/identity.ts` để
 * hai bộ giả lập không có hai phiên bản của cùng một luật.
 */
function inScope(role: MockRole, claim: ClaimReviewView): boolean {
  const identity = identityOf(role);
  if (identity.clanWide) return true;
  // `ClaimBranch` không chở `ltree` — tra qua bảng của bộ giả lập, đúng như
  // máy chủ thật tra trong SQL.
  const path = BRANCH_PATHS[claim.targetBranchId ?? claim.targetBranch?.id ?? ""] ?? null;
  return identity.managedBranches.some((scope) => pathIsWithin(scope, path));
}

/**
 * Mã thô giả lập, theo đúng khuôn Crockford Base32 của mã thật (không có
 * `I L O U`) và chia hai nhóm năm ký tự — `K7M2Q-D9HFX`.
 *
 * Tất định theo số thứ tự chứ không ngẫu nhiên: một mã đổi mỗi lần chạy làm ảnh
 * chụp màn hình và bộ kiểm khác nhau mà chẳng kiểm thêm được gì.
 */
function fakeCode(prefix: string, seq: number): string {
  const alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  let body = "";
  for (let i = 0; i < 10; i += 1) {
    body += alphabet[(seq * 7 + i * 11 + prefix.charCodeAt(0)) % alphabet.length];
  }
  return `${body.slice(0, 5)}-${body.slice(5)}`;
}

export const membershipAdminHandlers = [
  // ── Hàng chờ duyệt ────────────────────────────────────────────────────────
  http.get(`${API_BASE_URL}/api/v1/person-claims/pending`, ({ request }) => {
    const role = resolveMockRole(request);
    if (!coQuyenDuyet(role)) {
      return problem(
        403,
        "FORBIDDEN",
        "Không có quyền duyệt đơn",
        "/api/v1/person-claims/pending"
      );
    }
    const open = claims.filter((c) => c.status === "PENDING" && inScope(role, c));
    return HttpResponse.json(open);
  }),

  http.get(`${API_BASE_URL}/api/v1/person-claims/pending/count`, ({ request }) => {
    const role = resolveMockRole(request);
    // `0` chứ không `403`: badge ở thanh đầu trang gọi được mà không cần biết vai.
    if (!coQuyenDuyet(role)) return HttpResponse.json({ count: 0 });
    const n = claims.filter((c) => c.status === "PENDING" && inScope(role, c)).length;
    return HttpResponse.json({ count: n });
  }),

  http.post(`${API_BASE_URL}/api/v1/person-claims/:id/review`, async ({ request, params }) => {
    const id = String(params.id);
    const instance = `/api/v1/person-claims/${id}/review`;
    const role = resolveMockRole(request);
    const identity = identityOf(role);

    if (!coQuyenDuyet(role)) {
      return problem(403, "FORBIDDEN", "Không có quyền duyệt đơn", instance);
    }

    const claim = claims.find((c) => c.id === id);
    // `404` chứ không `403` cho đơn ngoài phạm vi: khác nhau ở đó thì một
    // Trưởng chi dò được sự tồn tại của đơn ở chi khác.
    if (!claim || !inScope(role, claim)) {
      return problem(404, "NOT_FOUND", "Không tìm thấy đơn", instance);
    }
    if (claim.status !== "PENDING") {
      return problem(409, "CLAIM_CLOSED", "Đơn đã được xử lý", instance);
    }
    if (claim.requestedBy === identity.appUserId) {
      return problem(403, "SELF_REVIEW_FORBIDDEN", "Không tự duyệt đơn của mình", instance);
    }

    const body = (await request.json().catch(() => ({}))) as {
      approve?: unknown;
      note?: unknown;
    };
    const approve = body.approve === true;
    const note = typeof body.note === "string" ? body.note.trim() : "";

    // TỪ CHỐI BẮT BUỘC KÈM LÝ DO — `PersonClaim#reject` ném lỗi khi thiếu.
    // Bộ giả lập giữ luật ấy để giao diện không được dựng trên một API dễ hơn.
    if (!approve && note.length === 0) {
      return problem(422, "VALIDATION_FAILED", "Từ chối đơn phải kèm lý do", instance);
    }

    claim.status = approve ? "APPROVED" : "REJECTED";
    claim.reviewerId = identity.appUserId;
    claim.reviewNote = note.length > 0 ? note : null;
    claim.reviewedAt = new Date().toISOString();
    if (approve && claim.kind === "NEW_PERSON") {
      claim.createdPersonId = `p-moi-${claim.id}`;
    }

    // Duyệt một đơn thì các đơn TRANH CHẤP cùng trỏ nhân khẩu ấy phải đóng lại:
    // một nhân khẩu chỉ gắn được một tài khoản (`ux_app_user_person`).
    if (approve) {
      for (const otherId of claim.competingClaimIds ?? []) {
        const other = claims.find((c) => c.id === otherId);
        if (other && other.status === "PENDING") {
          other.status = "REJECTED";
          other.reviewerId = identity.appUserId;
          other.reviewNote = "Nhân khẩu này đã được gắn cho một tài khoản khác.";
          other.reviewedAt = new Date().toISOString();
        }
      }
    }

    return HttpResponse.json(claim);
  }),

  // ── Mã mời dòng họ ────────────────────────────────────────────────────────
  http.get(`${API_BASE_URL}/api/v1/clan-invites`, ({ request }) => {
    const role = resolveMockRole(request);
    if (!coQuyenPhatMaHo(role)) {
      return problem(403, "FORBIDDEN", "Chỉ Hội đồng phát mã dòng họ", "/api/v1/clan-invites");
    }
    // PHONG BÌ — `clanLivingPersonCount` là **mẫu số** của `useCount` và không
    // thuộc về mã nào. Không có nó thì "đã dùng 400 lượt" không nói lên điều gì.
    const body: ClanInviteListDto = {
      invites: clanInvites,
      clanLivingPersonCount: SO_NGUOI_DANG_SONG,
    };
    return HttpResponse.json(body);
  }),

  http.post(`${API_BASE_URL}/api/v1/clan-invites`, async ({ request }) => {
    const instance = "/api/v1/clan-invites";
    const role = resolveMockRole(request);
    if (!coQuyenPhatMaHo(role)) {
      return problem(403, "FORBIDDEN", "Chỉ Hội đồng phát mã dòng họ", instance);
    }

    const body = (await request.json().catch(() => ({}))) as {
      label?: unknown;
      ttlDays?: unknown;
      maxUses?: unknown;
      note?: unknown;
    };
    const ttlDays = typeof body.ttlDays === "number" ? body.ttlDays : 30;
    const maxUses = typeof body.maxUses === "number" ? body.maxUses : null;

    clanInviteSeq += 1;
    const invite: ClanInviteView = {
      id: `ci-moi-${clanInviteSeq}`,
      label: typeof body.label === "string" && body.label.trim() ? body.label.trim() : null,
      issuedBy: identityOf(role).appUserId ?? "u-admin",
      status: "ACTIVE",
      usability: "USABLE",
      expiresAt: new Date(Date.now() + ttlDays * 86_400_000).toISOString(),
      useCount: 0,
      maxUses,
      remainingUses: maxUses,
      note: typeof body.note === "string" && body.note.trim() ? body.note.trim() : null,
      createdAt: new Date().toISOString(),
    };
    clanInvites = [invite, ...clanInvites];

    // LẦN DUY NHẤT mã thô rời máy chủ. Không endpoint nào đọc lại được — kể cả
    // ở đây: mã không được lưu vào `clanInvites`.
    const issued: IssuedClanInvite = { code: fakeCode("H", clanInviteSeq), invite };
    return HttpResponse.json(issued, { status: 201 });
  }),

  http.delete(`${API_BASE_URL}/api/v1/clan-invites/:id`, ({ request, params }) => {
    const id = String(params.id);
    const instance = `/api/v1/clan-invites/${id}`;
    const role = resolveMockRole(request);
    if (!coQuyenPhatMaHo(role)) {
      return problem(403, "FORBIDDEN", "Chỉ Hội đồng thu hồi mã dòng họ", instance);
    }
    const invite = clanInvites.find((c) => c.id === id);
    if (!invite) return problem(404, "NOT_FOUND", "Không tìm thấy mã", instance);
    if (invite.status === "REVOKED") {
      return problem(409, "VALIDATION_FAILED", "Mã đã được thu hồi", instance);
    }

    const url = new URL(request.url);
    invite.status = "REVOKED";
    invite.usability = "REVOKED";
    invite.revokedAt = new Date().toISOString();
    invite.revokedReason = url.searchParams.get("reason");
    return new HttpResponse(null, { status: 204 });
  }),

  // ── Lời mời cá nhân — NỬA QUẢN TRỊ (đòi token, có phạm vi) ────────────────
  http.get(`${API_BASE_URL}/api/v1/invitations`, ({ request }) => {
    const role = resolveMockRole(request);
    if (!coQuyenDuyet(role)) {
      return problem(403, "FORBIDDEN", "Không có quyền phát lời mời", "/api/v1/invitations");
    }
    return HttpResponse.json(invitations);
  }),

  http.post(`${API_BASE_URL}/api/v1/invitations`, async ({ request }) => {
    const instance = "/api/v1/invitations";
    const role = resolveMockRole(request);
    if (!coQuyenDuyet(role)) {
      return problem(403, "FORBIDDEN", "Không có quyền phát lời mời", instance);
    }

    const body = (await request.json().catch(() => ({}))) as {
      personId?: unknown;
      ttlDays?: unknown;
      note?: unknown;
    };
    const personId = typeof body.personId === "string" ? body.personId : "";
    if (!personId) {
      return problem(400, "VALIDATION_FAILED", "Thiếu nhân khẩu được mời", instance);
    }
    // Phát hiện "nhân khẩu đã có tài khoản" lúc PHÁT, không lúc nhận: Trưởng chi
    // phải biết ngay khi bấm, chứ không sau khi đã in phiếu và gửi tin nhắn.
    if (invitations.some((i) => i.personId === personId && i.status === "ACCEPTED")) {
      return problem(422, "PERSON_ALREADY_LINKED", "Nhân khẩu đã có tài khoản", instance);
    }

    const ttlDays = typeof body.ttlDays === "number" ? body.ttlDays : 7;
    invitationSeq += 1;
    const invitation: InvitationDto = {
      id: `inv-moi-${invitationSeq}`,
      personId,
      invitee: { displayName: `Nhân khẩu ${personId}`, branchName: CHI_NHAT.name },
      branchId: "b-chi1",
      invitedBy: identityOf(role).appUserId ?? "u-branch-head",
      status: "PENDING",
      usability: "USABLE",
      expiresAt: new Date(Date.now() + ttlDays * 86_400_000).toISOString(),
      note: typeof body.note === "string" && body.note.trim() ? body.note.trim() : undefined,
      createdAt: new Date().toISOString(),
    };
    invitations = [invitation, ...invitations];

    const issued: IssuedInvitationDto = {
      code: fakeCode("K", invitationSeq),
      invitation,
    };
    return HttpResponse.json(issued, { status: 201 });
  }),

  http.delete(`${API_BASE_URL}/api/v1/invitations/:id`, ({ request, params }) => {
    const id = String(params.id);
    const instance = `/api/v1/invitations/${id}`;
    const role = resolveMockRole(request);
    if (!coQuyenDuyet(role)) {
      return problem(403, "FORBIDDEN", "Không có quyền thu hồi lời mời", instance);
    }
    const invitation = invitations.find((i) => i.id === id);
    if (!invitation) return problem(404, "NOT_FOUND", "Không tìm thấy lời mời", instance);
    // Lời mời ĐÃ ĐƯỢC NHẬN thì không thu hồi được — `422`, như contract đã ghi.
    if (invitation.status === "ACCEPTED") {
      return problem(422, "VALIDATION_FAILED", "Lời mời đã được nhận", instance);
    }

    const url = new URL(request.url);
    invitation.status = "REVOKED";
    invitation.usability = "REVOKED";
    invitation.revokedAt = new Date().toISOString();
    invitation.revokedReason = url.searchParams.get("reason") ?? undefined;
    return new HttpResponse(null, { status: 204 });
  }),
];
