import { ApiError, apiFetch } from "./http";
import type { ClaimView } from "./claim";

/**
 * Lớp API cho **phần quản trị tư cách thành viên** — hai bề mặt mà Trưởng chi
 * và Hội đồng Tộc biểu dùng, và không ai khác thấy:
 *
 * <ol>
 *   <li><b>Hàng chờ duyệt đơn tự nhận</b> — nửa *người duyệt* của nhóm
 *       `/api/v1/person-claims`, đối xứng với nửa *người gửi* ở
 *       {@code src/lib/api/claim.ts};</li>
 *   <li><b>Mã mời dòng họ</b> (`/api/v1/clan-invites`) — cơ chế riêng,
 *       <b>không</b> phải biến thể của lời mời cá nhân.</li>
 * </ol>
 *
 * Nửa **lời mời cá nhân** của màn phát mã KHÔNG nằm ở đây: nó đã có đủ kiểu và
 * hàm gọi trong {@code src/lib/api/invitation.ts} (`invitationApi.list` /
 * `issue` / `revoke`). Viết một lớp thứ hai cho cùng nhóm endpoint là cách chắc
 * chắn để hai bản lệch nhau.
 *
 * <h2>Nguồn của các hình dạng dưới đây</h2>
 * `contracts/openapi.yaml` — nhóm `person-claims` và `clan-invites`. Contract
 * thắng mọi nguồn khác.
 */

// ============================================================================
// ĐƠN TỰ NHẬN — nửa NGƯỜI DUYỆT
// ============================================================================

/**
 * <h2>MỘT schema cho cả hai nửa, và đó là contract nói</h2>
 * `PersonClaim` là thứ **cùng** trả về ở `GET /person-claims/mine`,
 * `/pending`, `/{id}`, `POST /{id}/review` và `/{id}/cancel`. Trước đây tệp này
 * khai một hình dạng thứ hai, giàu hơn — có khối `person` lồng (tên, đời, chi,
 * cha mẹ), có `screening.matches`, có `relative.person`. **Không trường nào
 * trong số đó tồn tại.**
 *
 * Và chúng không chỉ thừa: khối `person` lồng đi ngược đúng điều contract viết
 * ra thành lời — *"Không mang tên hay năm sinh của nhân khẩu được nhận — chỉ
 * `personId`. Nhân khẩu ấy có thể là một người còn sống, và ai được xem gì về
 * họ là câu hỏi của bộ lọc phân tầng riêng tư, thứ chạy ở `GET /persons/&#123;id&#125;`
 * chứ không phải ở đây."* Một cái tên đi kèm lá đơn là một cái tên **không qua
 * bộ lọc nào** — nó lọt qua quyền của *người gửi đơn*, không phải của người
 * duyệt, và nó nằm lại trong đơn vĩnh viễn.
 *
 * Nên nửa người duyệt dùng lại đúng kiểu của nửa người gửi. Bí danh giữ nguyên
 * tên gọi cũ để các màn hình khỏi phải đổi chữ; nó **không** là một kiểu thứ hai.
 */
export type ClaimReviewView = ClaimView;

export type {
  ClaimBranchRefDto,
  ClaimDuplicateSuspectDto,
  ClaimKind,
  ClaimQuotaDto,
  ClaimStatus,
  ClaimView,
  RelativeKind,
} from "./claim";

/** Thân `POST /api/v1/person-claims/{id}/review` — `ReviewPersonClaimRequest`. */
export interface ReviewClaimBody {
  approve: boolean;
  /**
   * **Bắt buộc khi từ chối**, và lý do ấy **đến tay người gửi**.
   *
   * Người khai đã bỏ công tìm mình giữa 1.500 người, và họ chỉ còn một số lần
   * gửi lại có hạn. Trả về một chữ "không" là cách chắc chắn để lần gửi tiếp
   * theo cũng sai y như lần này. Kiểm ở client là phép lịch sự — nó giữ cho
   * người duyệt không mất công gõ rồi nhận `422`.
   *
   * Tối đa 1000 ký tự.
   */
  note?: string | null;
}

/**
 * Mọi cách một lượt duyệt có thể không thành. Mỗi nhánh một câu chữ và một lối
 * đi tiếp khác nhau — gộp lại thì người duyệt không biết phải làm gì.
 */
export type ClaimReviewFailure =
  | "BRANCH_SCOPE"
  | "CLOSED"
  | "SELF_REVIEW"
  | "PERSON_ALREADY_LINKED"
  | "ACCOUNT_ALREADY_LINKED"
  | "STALE"
  | "NOTE_REQUIRED"
  | "FORBIDDEN"
  | "UNAVAILABLE";

/**
 * `unknown` bắt được từ React Query → nhánh giao diện.
 *
 * Mặc định là `UNAVAILABLE`, **không** phải `FORBIDDEN`: mất mạng không được
 * đọc thành "ông/bà không có quyền", vì câu ấy làm một Trưởng chi đi xin lại
 * quyền mình vốn đã có.
 *
 * `error.code` nay đọc **đúng kiểu** `ProblemCode`: `CLAIM_CLOSED` và
 * `CLAIM_LIMIT_REACHED` đã vào enum đóng của `src/types/api.ts` theo contract,
 * nên phép nới kiểu `const code: string = …` đã gỡ. Gõ sai một mã ở đây bây giờ
 * là một lỗi biên dịch.
 */
export function claimReviewFailureOf(error: unknown): ClaimReviewFailure {
  if (!(error instanceof ApiError)) return "UNAVAILABLE";

  switch (error.code) {
    case "BRANCH_SCOPE_VIOLATION":
      return "BRANCH_SCOPE";
    case "CLAIM_CLOSED":
      return "CLOSED";
    case "SELF_REVIEW_FORBIDDEN":
      return "SELF_REVIEW";
    case "PERSON_ALREADY_LINKED":
      return "PERSON_ALREADY_LINKED";
    case "ACCOUNT_ALREADY_LINKED":
      return "ACCOUNT_ALREADY_LINKED";
    case "OPTIMISTIC_LOCK_CONFLICT":
      return "STALE";
    case "VALIDATION_FAILED":
      return "NOTE_REQUIRED";
    case "FORBIDDEN":
    case "UNAUTHENTICATED":
    case "ACCOUNT_NOT_PROVISIONED":
    case "ACCOUNT_NOT_ACTIVE":
      return "FORBIDDEN";
    default:
      return "UNAVAILABLE";
  }
}

export interface MembershipPageParams {
  page?: number;
  size?: number;
}

const CLAIMS_BASE = "/api/v1/person-claims";

export const claimReviewApi = {
  /**
   * Hàng chờ duyệt **trong phạm vi được giao**.
   *
   * <b>Mảng phẳng</b>, không bọc `{items, page}` — contract ghi rõ "lệch quy
   * ước phân trang", cùng quy ước với `/change-requests/pending` và
   * `GET /invitations`. (Nửa người gửi `/person-claims/mine` thì có phong bì, vì
   * nó chở thêm `quota`; ở đây không có gì thuộc về *tài khoản người duyệt* để
   * mà chở.)
   *
   * Sắp **cũ nhất trước**, và hai đơn cùng trỏ một nhân khẩu **đều xuất hiện** —
   * cố ý không ưu tiên đơn gửi trước: trùng tên trong dòng họ là chuyện thường,
   * và người gửi trước chưa chắc là người đúng.
   *
   * Phạm vi do **máy chủ** cắt theo `ltree` ngay trong SQL: Trưởng chi Ất không
   * thấy đơn trỏ vào người chi Bính. Giao diện hiện đúng thứ máy chủ trả về và
   * **không lọc lại** — lọc ở client nghĩa là dữ liệu ngoài phạm vi đã đi qua
   * dây mạng rồi.
   */
  pending: ({ page = 0, size = 50 }: MembershipPageParams = {}) =>
    apiFetch<ClaimReviewView[]>(`${CLAIMS_BASE}/pending`, { query: { page, size } }),

  /**
   * Số đơn chờ duyệt trong phạm vi — cho huy hiệu trên thanh điều hướng.
   *
   * Trả `0` cho người không có quyền duyệt, **không** `403`: huy hiệu nằm trên
   * mọi màn hình, và nếu nó `403` thì giao diện phải biết vai của người dùng
   * *trước khi* gọi — tức phải chép luật phân quyền sang client.
   */
  pendingCount: () => apiFetch<{ count: number }>(`${CLAIMS_BASE}/pending/count`),

  /** Chi tiết một đơn — **chỉ người gửi hoặc người duyệt đúng phạm vi chi**. */
  byId: (id: string) => apiFetch<ClaimReviewView>(`${CLAIMS_BASE}/${id}`),

  /**
   * Duyệt hoặc từ chối — **một transaction làm bốn việc hoặc không làm gì cả**.
   *
   * Với `EXISTING`, duyệt chỉ *gắn* tài khoản vào nhân khẩu đã có.
   *
   * Với `NEW_PERSON`, duyệt tạo nhân khẩu **cùng lúc** với cạnh quan hệ vào
   * người thân, gắn tài khoản, đóng đơn và ghi nhật ký. Hỏng ở bất kỳ bước nào
   * thì cả bốn cùng cuộn lại — đặc biệt là **không có nhân khẩu nào ở lại trong
   * phả**, điều mà một node đã tạo thì xoá mềm không gỡ được.
   *
   * Duyệt xong, các đơn còn lại cùng trỏ một nhân khẩu bị **đóng tường minh**
   * kèm lý do và chuyển sang `REJECTED` — để chúng nằm lại `PENDING` là để lại
   * một đơn vĩnh viễn không duyệt được làm nghẽn hàng chờ.
   */
  review: (id: string, body: ReviewClaimBody) =>
    apiFetch<ClaimReviewView>(`${CLAIMS_BASE}/${id}/review`, { method: "POST", body }),
};

// ============================================================================
// MÃ MỜI DÒNG HỌ — cơ chế riêng, không phải biến thể của lời mời cá nhân
// ============================================================================

/**
 * `ClanInvite.status` — trạng thái **lưu trữ**.
 *
 * **Cố ý chỉ hai giá trị.** "Hết hạn" và "hết lượt" không nằm ở đây: cả hai là
 * phép so sánh (với đồng hồ, với bộ đếm), không phải một dòng dữ liệu. Nếu
 * chúng là trạng thái lưu trữ thì phải có một job đi lật cờ, và cho tới khi job
 * ấy chạy thì mọi mã quá hạn vẫn dùng được — một lỗ hổng có lịch chạy.
 */
export type ClanInviteStatus = "ACTIVE" | "REVOKED";

/**
 * `ClanInvite.usability` — trạng thái **đã xét đồng hồ và bộ đếm**.
 *
 * **Hiển thị trường này, không phải `status`**: `ACTIVE` của một mã đã quá hạn
 * hoặc đã hết lượt không nói lên điều gì cho người đang nhìn danh sách.
 */
export type ClanInviteUsability = "USABLE" | "EXPIRED" | "REVOKED" | "EXHAUSTED";

/**
 * `ClanInvite` — một mã mời dòng họ nhìn từ phía Hội đồng.
 *
 * **Không có mã, và cũng không có băm của mã.** Băm không mở được cửa nào,
 * nhưng một trường không màn hình nào dùng thì không có lý do để đi qua dây.
 */
export interface ClanInviteView {
  id: string;
  /**
   * Nhãn để Hội đồng nhận ra mình phát mã nào cho kênh nào ("Nhóm Zalo họ
   * Nguyễn 2026").
   *
   * Nó là thứ biến bộ đếm thành thông tin dùng được: "mã dán nhóm Zalo đã dùng
   * 400 lần" nói được điều gì đó, "mã thứ ba đã dùng 400 lần" thì không.
   */
  label?: string | null;
  issuedBy: string;
  status: ClanInviteStatus;
  usability: ClanInviteUsability;
  /** `NOT NULL`. Không có mã vĩnh viễn — một tờ giấy bỏ quên không được mở phả sau bốn năm. */
  expiresAt: string;

  /**
   * **Bộ đếm lượt dùng. Con số quan trọng nhất của cả màn hình.**
   *
   * Hội đồng thấy mã đã dùng 400 lần trong khi dòng họ có 600 người thì *biết*
   * mà thu hồi; không có bộ đếm thì mã rò ra và mọi thứ trông vẫn bình thường.
   * Mẫu số của phép so ấy là {@link ClanInviteListDto.clanLivingPersonCount}.
   *
   * Bấm hai lần bởi cùng một tài khoản **không** đếm thành hai.
   */
  useCount: number;
  /** Trần lượt dùng. Vắng = không đặt trần — hợp lệ nhưng đáng cảnh báo. */
  maxUses?: number | null;
  /** Số lượt còn lại. Vắng **cùng lúc** với `maxUses`. */
  remainingUses?: number | null;

  revokedAt?: string | null;
  revokedReason?: string | null;
  /** Ghi chú nội bộ, tách khỏi `label`. */
  note?: string | null;
  createdAt?: string | null;
}

/**
 * `ClanInviteList` — phản hồi của `GET /api/v1/clan-invites`.
 *
 * <h2>Phản hồi BỌC danh sách, và đó là chỗ duy nhất trong nhóm này làm thế</h2>
 * {@link ClanInviteListDto.clanLivingPersonCount} **không thuộc về mã nào**
 * trong danh sách. Lập luận của quyết định "cấp mã cho cả họ" là *"mã đã dùng
 * 400 lần trong khi dòng họ có 600 người"* — không có vế thứ hai thì vế thứ
 * nhất không nói lên điều gì, và chốt được gọi là quan trọng nhất trở thành một
 * con số trang trí.
 *
 * Gắn nó vào từng phần tử là lặp một giá trị toàn cục lên n dòng rồi để chúng
 * có cơ hội lệch nhau — đúng thứ bản trước của tệp này đã làm khi khai
 * `clanLivingPersonCount` như một trường **của mã**. Tách ra một endpoint thứ
 * hai thì bắt màn hình gọi hai lượt cho một câu trả lời.
 */
export interface ClanInviteListDto {
  invites: ClanInviteView[];
  /**
   * Số người **đang sống** trong cả dòng họ — **mẫu số** để đọc `useCount`.
   *
   * Đây là một phép đếm, không phải một báo cáo dân số: không phân theo chi,
   * không phân theo đời, không tiết lộ ai cả.
   *
   * **Bắt buộc ở contract.** Không còn nhánh "vắng thì ẩn dòng đối chiếu".
   */
  clanLivingPersonCount: number;
}

/**
 * `IssuedClanInvite` — **lần duy nhất mã thô rời máy chủ**.
 *
 * CSDL chỉ lưu băm SHA-256, nên `code` không đọc lại được ở bất kỳ endpoint
 * nào. Màn hình phải nói rõ điều đó **trước khi** người dùng rời màn, và cho
 * một nút chép.
 *
 * Khác luồng mã cá nhân ở một điểm: phát mã dòng họ mới **không** tự thu hồi mã
 * cũ. Nhiều mã song song chính là cách Hội đồng biết mã nào đã rò — một mã cho
 * nhóm Zalo, một mã phát tại lễ giỗ tổ, và bộ đếm của từng mã nói cho họ biết
 * kênh nào đang chảy.
 */
export interface IssuedClanInvite {
  /**
   * Mã thô, đã chia nhóm cho dễ đọc qua điện thoại: `K7M2Q-D9HFX`. Bảng chữ
   * Crockford Base32 (bỏ hẳn `I`, `L`, `O`, `U`).
   */
  code: string;
  invite: ClanInviteView;
}

/** Thân `POST /api/v1/clan-invites` — `IssueClanInviteRequest`. Mọi trường tuỳ chọn. */
export interface IssueClanInviteBody {
  /** Tối đa 160 ký tự. Nên có: nó là thứ **biến bộ đếm thành thông tin dùng được**. */
  label?: string;
  /**
   * Số ngày hiệu lực, 1–90, mặc định 30.
   *
   * Trần 90 dài hơn mã cá nhân (30) vì mã dòng họ phát ra ở một dịp có thật (lễ
   * giỗ tổ, họp họ) và người ở xa cần vài tuần mới ngồi xuống đăng ký.
   */
  ttlDays?: number;
  /**
   * Trần lượt dùng, tối thiểu 1. Bỏ trống = không giới hạn *số lượt* (vẫn có
   * hạn thời gian, vẫn thu hồi được, vẫn có bộ đếm).
   *
   * Giao diện **điền sẵn** một con số chứ không để trống: một mã không trần
   * cộng một bộ đếm không ai nhìn là đúng tình huống mà chốt này sinh ra để chặn.
   */
  maxUses?: number;
  /** Ghi chú nội bộ, tối đa 500 ký tự. */
  note?: string;
}

const CLAN_INVITES_BASE = "/api/v1/clan-invites";

export const clanInvitesApi = {
  /**
   * Mọi mã dòng họ, kể cả đã thu hồi và đã hết hạn, **kèm mẫu số của bộ đếm**.
   *
   * Cố ý **không** lọc sẵn: bộ đếm của một mã đã thu hồi chính là bằng chứng
   * rằng việc thu hồi là đúng.
   *
   * **Quyền**: `COUNCIL` · `ADMIN`. Mã dòng họ không có chi để lọc theo, nên
   * không có "phạm vi" nào để cắt danh sách này cho Trưởng chi.
   */
  list: ({ page = 0, size = 50 }: MembershipPageParams = {}) =>
    apiFetch<ClanInviteListDto>(CLAN_INVITES_BASE, { query: { page, size } }),

  /** Chỉ Hội đồng Tộc biểu và Admin. `201` kèm mã thô — lần duy nhất. */
  issue: (body: IssueClanInviteBody) =>
    apiFetch<IssuedClanInvite>(CLAN_INVITES_BASE, { method: "POST", body }),

  /**
   * Thu hồi. **Không ảnh hưởng người đã vào bằng mã ấy**: họ đã có tài khoản, và
   * tài khoản không treo vào mã.
   */
  revoke: (id: string, reason?: string) =>
    apiFetch<void>(`${CLAN_INVITES_BASE}/${id}`, { method: "DELETE", query: { reason } }),
};

/** Nhánh hỏng của việc phát/thu hồi/đọc mã dòng họ. */
export type ClanInviteFailure =
  | "FORBIDDEN"
  | "ALREADY_REVOKED"
  | "RATE_LIMITED"
  | "UNAVAILABLE";

/**
 * `unknown` → nhánh giao diện, phía **Hội đồng**.
 *
 * `error.code` đọc đúng kiểu `ProblemCode`; phép nới `const code: string = …`
 * đã gỡ khi `CLAN_INVITE_EXHAUSTED` vào enum đóng theo contract.
 */
export function clanInviteFailureOf(error: unknown): ClanInviteFailure {
  if (!(error instanceof ApiError)) return "UNAVAILABLE";
  const code = error.code;
  if (code === "FORBIDDEN" || code === "UNAUTHENTICATED") return "FORBIDDEN";
  if (code === "RATE_LIMITED") return "RATE_LIMITED";
  if (error.status === 401 || error.status === 403) return "FORBIDDEN";
  // `409`/`422` ở `DELETE` chỉ có một nghĩa: mã đã ở trạng thái cuối rồi.
  if (error.status === 409 || error.status === 422) return "ALREADY_REVOKED";
  return "UNAVAILABLE";
}
