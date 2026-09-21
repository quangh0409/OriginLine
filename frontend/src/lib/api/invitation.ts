import { ApiError, apiFetch } from "./http";
import type { BranchRef } from "@/types/api";

/**
 * Lớp API cho **lời mời vào phả** — nhóm `invitations` của
 * `contracts/openapi.yaml`.
 *
 * <h2>Nguồn sự thật là contract, không phải tệp này</h2>
 * Bản đầu của tệp này là một **đề xuất** viết trước khi backend tồn tại. Nhóm
 * endpoint nay đã dựng xong và đã vào `contracts/openapi.yaml`; mọi kiểu dưới
 * đây được chép lại theo nó. Chỗ nào lệch, contract thắng.
 *
 * <h2>Bốn chỗ contract KHÁC bản đề xuất — đọc trước khi sửa lại</h2>
 * <ol>
 *   <li><b>`accept` KHÔNG còn đòi token — điểm này đã ĐỔI LẦN THỨ HAI, đọc kỹ
 *       trước khi tin dòng nào nói khác.</b> Bản đề xuất giả định `accept` cần
 *       token đăng nhập sẵn. Đợt trước đã sửa một phần: `InvitationController`
 *       gắn {@code @SecurityRequirements} và `SecurityConfig` liệt kê
 *       `/invitations/accept` trong danh sách `permitAll()` — endpoint **không
 *       đòi token nữa**, còn `AcceptInvitationRequest` thì thêm hẳn hai trường
 *       {@code loginId}/{@code email} để người **chưa có tài khoản** tự khai.
 *       Máy chủ tìm-hoặc-tạo tài khoản Keycloak bằng định danh ấy rồi trả về
 *       {@code setPasswordUrl} — một liên kết một lần để tự đặt mật khẩu.
 *       <b>Không có trường mật khẩu nào ở `/accept`</b>: mật khẩu luôn đặt ở
 *       bước sau, qua {@code setPasswordUrl}, giống hệt cơ chế mã dòng họ ở
 *       {@code clan-invite.ts}. Giao diện phải thu {@link AcceptInvitationAccount}
 *       từ người dùng <b>trước</b> khi gọi `accept` khi họ chưa đăng nhập, chứ
 *       không còn gọi suông rồi đợi một `401` — xem
 *       {@code invitation-account-form.tsx}.</li>
 *   <li><b>`inviter.displayName` là tên TRẦN, không kính ngữ.</b> Bản đề xuất
 *       đoán ngược. Kết luận thì không đổi và nay có chỗ dựa trong contract:
 *       giao diện <b>không được</b> tự thêm "ông"/"bà", vì kính ngữ phụ thuộc
 *       quan hệ họ hàng. Sự tôn kính do {@code clanTitle} gánh, và đó là
 *       <b>chức danh dòng tộc</b> suy từ {@code branch.head_person_id}, không
 *       phải vai kỹ thuật {@code BRANCH_HEAD}.</li>
 *   <li><b>`relationToInviter` KHÔNG có ở Giai đoạn 1.</b> Ba lý do độc lập,
 *       cái nào cũng đủ: gói danh xưng chưa phải named interface; bộ luật danh
 *       xưng <b>che tên theo người gọi</b> mà người gọi ở đây là khách không
 *       token; và nó là một truy vấn LCA bằng Cypher đặt trên một endpoint
 *       không cần đăng nhập. Trường giữ nguyên dạng tuỳ chọn để ngày nó xuất
 *       hiện thì không phải sửa gì — nhưng <b>bộ giả lập không gửi nó</b>, để
 *       không màn hình nào được dựng dựa trên một trường chưa tồn tại.</li>
 *   <li><b>`/lookup` KHÔNG trả `personId`.</b> Lý do đúng: đó là khoá tra cứu ở
 *       mọi endpoint khác, tức một khoá nối bền vững trao cho người
 *       <b>chưa xác thực</b>, trong khi màn hình chẳng dùng tới. `accept` mới
 *       trả `personId`, lúc ấy đã có token. Backend ghim điều này bằng test
 *       phản chiếu — đừng đọc trường ấy ở màn xem trước.</li>
 * </ol>
 *
 * <h2>Ba quyết định của bản đề xuất được contract giữ nguyên</h2>
 * `POST .../lookup` thay vì `GET {code}` (mã là bí mật: path đi vào access log,
 * `GET` thì cacheable) · chuẩn hoá mã ở máy chủ, nay rộng tay hơn nữa (bỏ
 * `- . _` và khoảng trắng, viết hoa, và dịch `O → 0`, `I`/`L → 1` vì bảng chữ
 * Crockford Base32 đã loại `I L O U`) · thân lỗi <b>không</b> mang tên hay số
 * điện thoại người mời.
 *
 * <h2>Ba mã lỗi riêng, không một mã chung kèm `reason`</h2>
 * FE rẽ nhánh theo {@code code} của RFC 7807 — không theo mã HTTP, không theo
 * {@code detail}, và không theo một trường nào khác trong thân lỗi.
 *
 * <h2>Quy ước đọc: trường trống bị LOẠI khỏi JSON</h2>
 * Không có `null` trong các DTO dưới đây. Mọi thứ tuỳ chọn đều có thể vắng mặt
 * hẳn, kể cả {@code clanName} và cả khối {@code inviter} — giao diện phải dựng
 * được khi chỉ có {@code invitee} và {@code expiresAt}, hai trường bắt buộc duy
 * nhất của {@code InvitationPreview}.
 */

// ============================================================================
// NHÁNH HỎNG — kiểu của GIAO DIỆN, không phải của đường truyền
// ============================================================================

/**
 * Mọi cách một lời mời có thể không mở được, gom thành đúng số nhánh mà bản
 * thiết kế 06 §5 và §7 đòi. Mỗi nhánh có câu chữ riêng **và** một lối đi tiếp.
 *
 * Bốn nhánh đầu đến từ `/lookup`; ba nhánh sau chỉ xảy ra ở `/accept`, tức sau
 * khi người dùng đã xác nhận "đúng là tôi".
 *
 * `UNAVAILABLE` tách hẳn ra và đó là điểm quan trọng nhất của kiểu này: mất
 * mạng hay máy chủ 500 **không được** đọc thành "mã của ông/bà sai". Một người
 * vừa bị bảo là mã sai sẽ gọi đi xin mã mới, và mã mới cũng sẽ không mở được.
 */
export type InvitationFailure =
  | "EXPIRED"
  | "ALREADY_USED"
  | "REVOKED"
  | "NOT_FOUND"
  | "RATE_LIMITED"
  | "UNAVAILABLE"
  /**
   * `401 UNAUTHENTICATED` ở `/accept`. **Không còn xảy ra với máy chủ hôm
   * nay** — `/accept` đã bỏ yêu cầu token (xem javadoc đầu tệp) và một người
   * chưa đăng nhập giờ được phục vụ bằng {@link InvitationAccountForm}
   * ({@code invitation-account-form.tsx}), không phải bằng một câu từ chối.
   * Nhánh này ở lại làm <b>lưới an toàn cho một bản máy chủ cũ hơn</b> — mã
   * mời vẫn tốt, chỉ là chưa đăng nhập, và điều PHẢI giữ là không đọc nó
   * thành "mã sai". Ca kiểm tương ứng dựng `401` bằng tay
   * ({@code server.use(...)}) thay vì tin bộ giả lập nói dối về API thật.
   */
  | "NEEDS_ACCOUNT"
  /** `422 ACCOUNT_ALREADY_LINKED` — tài khoản đang gọi đã gắn người khác. */
  | "ACCOUNT_ALREADY_LINKED"
  /** `422 PERSON_ALREADY_LINKED` — nhân khẩu đã bị một tài khoản khác nhận. */
  | "PERSON_ALREADY_LINKED"
  /**
   * `422 IDENTITY_ALREADY_REGISTERED` — định danh tự khai ở `/accept` **đã
   * thuộc về một tài khoản đang tồn tại**.
   *
   * <h2>Cùng một mã, cùng một cách xử, ở CẢ HAI lối vào không cần đăng nhập</h2>
   * `POST /invitations/accept` và `POST /clan-invites/register` đều nhận một
   * chuỗi người gọi **tự gõ** mà không chứng minh được quyền sở hữu. Nếu định
   * danh ấy đã có chủ thì mọi bước sau — đúc liên kết đặt mật khẩu, làm tươi hồ
   * sơ, và ở lối này còn là <b>ghép vào một nhân khẩu trong phả</b> — là thao
   * tác trên tài sản của người khác. Chủ dự án đã chốt bịt cả hai.
   *
   * <p><b>Lưu ý dễ hiểu sai:</b> một tài khoản dựng qua đăng nhập Google/Zalo
   * <b>không có mật khẩu</b>, nên "chưa có mật khẩu" không hề có nghĩa là "chưa
   * có chủ". Đó chính là ca mà lỗ hổng cũ khai thác.</p>
   *
   * <p><b>Không tự thử lại.</b> Mỗi lần từ chối được máy chủ tính vào giới hạn
   * tần suất như một lần thất bại — cố ý, để tín hiệu còn sót không thành máy
   * dò tài khoản. Lối đi tiếp là <b>đăng nhập rồi mở lại liên kết mời</b>.</p>
   *
   * <p><b>`detail` của mã này KHÔNG in ra màn hình</b>, cùng luật với lối mã
   * dòng họ. Câu của máy chủ chỉ có tiếng Việt, nên in nó ra là dán một đoạn
   * tiếng Việt không dấu xuống dưới một đoạn tiếng Anh hoàn chỉnh. Hai tình
   * tiết đáng giá của nó (đã từng đăng nhập bằng Google · đưa mã cho Trưởng
   * chi) đã nằm sẵn trong `auth.invitation.problem.IDENTITY_TAKEN` ở cả hai
   * ngôn ngữ — đó chính là lý do hợp đồng dùng một mã <b>đóng</b> cộng bộ dịch
   * phía client. `detail` dành cho nhật ký và cho người hỗ trợ đọc log.</p>
   */
  | "IDENTITY_TAKEN";

/**
 * `unknown` bắt được từ React Query → nhánh giao diện.
 *
 * Mặc định là `UNAVAILABLE`, không phải `NOT_FOUND`. Mặc định-về-"mã sai" là
 * cách nhanh nhất để một trục trặc hạ tầng biến thành một cuộc gọi hoảng hốt
 * tới trưởng chi.
 */
export function invitationFailureOf(error: unknown): InvitationFailure {
  if (!(error instanceof ApiError)) return "UNAVAILABLE";

  switch (error.code) {
    case "INVITATION_EXPIRED":
      return "EXPIRED";
    case "INVITATION_ALREADY_USED":
      return "ALREADY_USED";
    case "INVITATION_REVOKED":
      return "REVOKED";
    case "NOT_FOUND":
      return "NOT_FOUND";
    case "RATE_LIMITED":
      return "RATE_LIMITED";
    case "UNAUTHENTICATED":
      return "NEEDS_ACCOUNT";
    case "ACCOUNT_ALREADY_LINKED":
      return "ACCOUNT_ALREADY_LINKED";
    case "PERSON_ALREADY_LINKED":
      return "PERSON_ALREADY_LINKED";
    case "IDENTITY_ALREADY_REGISTERED":
      return "IDENTITY_TAKEN";
    default:
      break;
  }

  // Máy chủ chưa gắn `code` (bản cũ, hoặc một proxy trả HTML): ở ĐÚNG bốn
  // endpoint lời mời, các mã HTTP dưới đây không mang nghĩa nào khác.
  if (error.status === 404) return "NOT_FOUND";
  if (error.status === 429) return "RATE_LIMITED";
  if (error.status === 401) return "NEEDS_ACCOUNT";
  return "UNAVAILABLE";
}

/**
 * `true` khi lỗi là một `VALIDATION_FAILED` trần ở `/accept` — thân yêu cầu
 * sai hình dạng, gần như luôn là {@code loginId} đọc không ra thành email lẫn
 * số điện thoại (xem {@code LoginIdentifier.of} phía máy chủ).
 *
 * Dùng để rẽ nhánh <b>trước</b> khi gọi {@link invitationFailureOf}: đây là lỗi
 * người dùng SỬA ĐƯỢC NGAY tại ô định danh, không phải một trong chín nhánh
 * ở trên — thay cả màn cho ca này sẽ ném đi tên hiển thị họ vừa gõ.
 *
 * Cùng cách rẽ mà {@code isClanInviteValidationError} đã dùng cho
 * `/clan-invites/register` — chép lại nguyên si.
 */
export function isInvitationValidationError(error: unknown): boolean {
  return error instanceof ApiError && error.code === "VALIDATION_FAILED";
}

/**
 * Câu máy chủ nói về CHÍNH định danh vừa gửi — `detail` của một
 * `VALIDATION_FAILED` trần ở `/accept`.
 *
 * Cùng khuôn với {@code clanInviteValidationDetail}: trả {@code null} khi máy
 * chủ không nói gì — lúc ấy màn hình dùng câu dự phòng
 * {@code auth.register.identifierEmpty} sẵn có, KHÔNG bịa ra một câu mới.
 */
export function invitationValidationDetail(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  if (error.code !== "VALIDATION_FAILED") return null;
  const detail = error.problem?.detail?.trim();
  return detail && detail.length > 0 ? detail : null;
}

// ============================================================================
// DTO — PHÍA NGƯỜI ĐƯỢC MỜI
// ============================================================================

/**
 * Người phát lời mời. Một **con người trong họ**, không phải hệ thống — đó là
 * lý do tiêu đề màn đọc "Lời mời từ …" chứ không phải "Kích hoạt tài khoản".
 *
 * Cả khối này **có thể vắng mặt**; giao diện phải có tiêu đề dự phòng.
 */
export interface InvitationInviterDto {
  /**
   * Tên **trần, KHÔNG kính ngữ** — `"Nguyễn Văn Bốn"`, không phải
   * `"ông Nguyễn Văn Bốn"`. Giao diện không được tự thêm: đoán sai một chữ
   * "ông" cho một người phụ nữ là đúng loại lỗi mà luật "quan hệ là việc của
   * máy chủ" sinh ra để chặn.
   */
  displayName?: string;
  /**
   * Chức danh **dòng tộc** (`"Trưởng Chi Giáp"`), suy từ `branch.head_person_id`
   * — **không** phải vai kỹ thuật `BRANCH_HEAD`. Không ai trong họ tự giới
   * thiệu mình bằng một mã vai trò. Vắng mặt với phần lớn thành viên, và đó là
   * câu trả lời đúng.
   *
   * Trên màn này nó là thứ **gánh sự tôn kính** thay cho kính ngữ.
   */
  clanTitle?: string;
}

/**
 * Người được mời — **mức tối thiểu để tự nhận ra mình**, không hơn.
 *
 * <h2>Ngoại lệ riêng tư có chủ ý</h2>
 * `displayName` là dữ liệu Tầng 1 của một người **đang sống**, hiện ra cho bất
 * kỳ ai cầm mã. Ngoại lệ được chọn vì không hiện tên thì nút "Không phải tôi"
 * mất nghĩa, mà nút ấy là lớp phòng vệ cuối khi lời mời đi nhầm số. Cái giá trả
 * bằng ba lớp: mã dùng một lần · hạn bảy ngày · giới hạn tần suất.
 *
 * Hệ quả bắt buộc: **không nới thêm một trường nào**. Không năm sinh, không số
 * điện thoại, không ảnh, không nghề nghiệp. Và **không `personId`** — xem
 * javadoc đầu tệp.
 */
export interface InvitationInviteeDto {
  displayName: string;
  /** Thuỷ tổ = 1. */
  generation?: number;
  branch?: BranchRef;
  /**
   * Danh xưng với người mời ("Con dâu ông Nguyễn Văn Bốn").
   *
   * **Giai đoạn 1 không gửi trường này** — ba lý do ở javadoc đầu tệp. Khai sẵn
   * ở đây để ngày nó xuất hiện thì không phải sửa kiểu; bộ giả lập cố ý **không**
   * gửi nó, nên không màn hình nào được dựng dựa trên một trường chưa tồn tại.
   * Nếu có, nó **do máy chủ tính** — giao diện không bao giờ suy quan hệ từ dữ
   * liệu cây.
   */
  relationToInviter?: string;
}

/**
 * `InvitationPreview`. Chỉ `invitee` và `expiresAt` là **bắt buộc**.
 *
 * Bề mặt rò rỉ duy nhất của cả luồng, mở cho người chưa đăng nhập: mỗi trường ở
 * đây là một mẩu dữ liệu mà người nhặt được tờ phiếu giấy cũng đọc được. Đừng
 * thêm trường thứ n+1 mà không nêu được lý do.
 */
export interface InvitationPreviewDto {
  clanName?: string;
  inviter?: InvitationInviterDto;
  invitee: InvitationInviteeDto;
  /** ISO-8601. Dùng để nói "mã còn dùng được tới …", không để tự tính hết hạn. */
  expiresAt: string;
}

/**
 * Hai trường chỉ cần khi backend phải **tạo tài khoản Keycloak** cho người được
 * mời — `AcceptInvitationRequest` ngoài `code`.
 *
 * <h2>`loginId` nhận EMAIL HOẶC SỐ ĐIỆN THOẠI</h2>
 * Luồng này dành cho **các cụ lớn tuổi** (Trưởng chi chọn người trong phả, in
 * phiếu, đưa tận tay), tức đúng nhóm thường *không có* email — nên đây là lối
 * cần nhận số điện thoại **nhất**, không phải ít nhất. Số Việt Nam được máy chủ
 * chuẩn hoá về dạng `0…`; gửi nguyên văn.
 *
 * Bắt buộc khi người gọi **chưa có** tài khoản. Khi lời gọi mang token của một
 * tài khoản đã có, các trường này bị bỏ qua.
 *
 * Contract còn nhận tên cũ `email`, đã đánh dấu **bỏ dần** — đừng dùng ở mã mới.
 */
export interface AcceptInvitationAccount {
  loginId?: string;
  /** Tên hiển thị của tài khoản. Vắng thì lấy tên nhân khẩu trong lời mời. */
  displayName?: string;
}

/**
 * `AcceptedInvitation`.
 *
 * `status: "ACTIVE"` cùng `personId` khác rỗng **ngay trong phản hồi này** là
 * bằng chứng đọc được từ phía client rằng **không có bước chờ duyệt nào**:
 * không `ChangeRequest` nào được tạo, không ai phải bấm Duyệt. Trưởng chi đã
 * chỉ đích danh người mình mời; hệ thống không hỏi lại một câu đã có đáp án.
 */
export interface InvitationAcceptedDto {
  appUserId: string;
  /** Nhân khẩu đã được nối — chỉ có ở đây, không có ở `/lookup`. */
  personId: string;
  status: "ACTIVE";
  /**
   * URL một lần của Keycloak để tự đặt mật khẩu.
   *
   * **Hôm nay LUÔN vắng mặt.** Backend chưa phát được token hành động (xem
   * javadoc đầu tệp), nên `/accept` đòi token thay vì cấp tài khoản. Giao diện
   * phải làm đúng việc ở cả hai trường hợp: có thì rời sang Keycloak, không thì
   * ở lại và nói rõ người dùng đã vào phả. Trả sẵn một trường luôn rỗng còn tệ
   * hơn không có nó — giao diện sẽ chuyển hướng tới `undefined`.
   */
  setPasswordUrl?: string;
}

// ============================================================================
// DTO — PHÍA NGƯỜI MỜI (Trưởng chi / Hội đồng). Chưa có màn hình nào dùng.
// ============================================================================

/** Trạng thái **lưu trữ**. Cố ý không có `EXPIRED` — xem `usability`. */
export type InvitationStatus = "PENDING" | "ACCEPTED" | "REVOKED";

/**
 * Trạng thái **đã xét đồng hồ**. Danh sách quản trị phải hiển thị trường này
 * chứ **không** phải `status`: `PENDING` của một mã đã quá hạn không nói lên
 * điều gì cho người đang nhìn.
 */
export type InvitationUsability = "USABLE" | "EXPIRED" | "ALREADY_USED" | "REVOKED";

/**
 * `InviteeSummary` — người được mời, nhìn từ **danh sách quản trị**.
 *
 * <h2>Không phải cùng một kiểu với {@link InvitationInviteeDto}</h2>
 * Hai schema riêng trong contract, và khác nhau ở đúng một chỗ có nghĩa:
 * {@code InvitationInvitee} (màn nhận lời mời) chở nguyên một `BranchRef`, vì
 * `PublicPersonDto` của cổng công khai vốn đã chở `BranchRef` cho cả Khách. Còn
 * khối này chỉ chở **tên chi dưới dạng chuỗi** — danh sách quản trị chỉ cần đọc
 * ra, không cần một khoá để đi tiếp. Gộp hai kiểu lại là tự cấp cho mình thêm
 * một trường ở mỗi bên.
 *
 * Ba trường, và đúng ba: không năm sinh, không nghề nghiệp, không nơi ở, không
 * điện thoại, không ảnh.
 */
export interface InviteeSummaryDto {
  displayName?: string;
  /** Thuỷ tổ = 1. */
  generation?: number;
  /** **Chuỗi**, không phải `BranchRef` — xem javadoc của kiểu. */
  branchName?: string;
}

/**
 * Một lời mời nhìn từ phía người phát. **Không có mã, và cũng không có băm của
 * mã** — băm là bí mật dẫn xuất: một bản băm lọt ra ngoài cho phép kiểm chứng
 * offline xem một mã đoán được có đúng không, tức bẻ mất lớp giới hạn tần suất.
 */
export interface InvitationDto {
  id: string;
  personId: string;
  /**
   * `InviteeSummary` — **đúng đủ để nhận ra ai** trong một danh sách.
   *
   * Không năm sinh, không nghề nghiệp, không nơi ở, không điện thoại, không
   * ảnh: cùng ranh giới mà màn nhận lời mời đã đặt, vì đây là cùng một dữ liệu
   * đi ra ngoài.
   *
   * <h2>Vì sao đưa ra ở đây là an toàn</h2>
   * `POST /invitations/lookup` vốn đã hiện đúng cái tên ấy cho **bất kỳ ai cầm
   * mã**, kể cả người chưa đăng nhập. Người đọc danh sách này thì chặt hơn
   * nhiều: đã đăng nhập, đã qua kiểm phạm vi chi, và thường chính là người vừa
   * phát lời mời.
   *
   * Không có khối này thì màn "lời mời đã phát" là một cột UUID, và giao diện
   * buộc phải gọi `GET /persons/&#123;id&#125;` một lượt cho mỗi dòng — một bài
   * toán N+1 sinh ra chỉ vì thiếu ba trường. {@code invitation-row.tsx} từng
   * làm đúng thế; nay không còn.
   *
   * Cả khối **có thể vắng**; giao diện phải dựng được khi chỉ có `personId`.
   */
  invitee?: InviteeSummaryDto;
  branchId?: string;
  invitedBy: string;
  status: InvitationStatus;
  usability: InvitationUsability;
  expiresAt: string;
  acceptedBy?: string;
  acceptedAt?: string;
  revokedAt?: string;
  revokedReason?: string;
  note?: string;
  createdAt?: string;
}

/**
 * **Lần duy nhất mã thô rời máy chủ.** `code` không đọc lại được ở bất kỳ
 * endpoint nào — CSDL chỉ lưu băm. Màn hình phát mã phải cho Trưởng chi chép
 * hoặc in ngay và nói rõ rằng đóng màn là mất mã; mất mã thì phát lại, và phát
 * lại tự thu hồi mã cũ.
 */
export interface IssuedInvitationDto {
  /** Mã thô, đã chia nhóm cho dễ đọc trên phiếu giấy: `K7M2Q-D9HFX`. */
  code: string;
  invitation: InvitationDto;
}

export interface IssueInvitationBody {
  personId: string;
  /** 1–30, mặc định 7. */
  ttlDays?: number;
  /** Tối đa 500 ký tự; chỉ hiện trong danh sách quản trị. */
  note?: string;
}

// ============================================================================
// GỌI API
// ============================================================================

const BASE = "/api/v1/invitations";

export const invitationApi = {
  /**
   * Mở lời mời để người nhận xác nhận "đúng là tôi". **Không cần token**, và
   * không tiêu mã.
   */
  preview: (code: string) =>
    apiFetch<InvitationPreviewDto>(`${BASE}/lookup`, {
      method: "POST",
      body: { code },
    }),

  /**
   * Nhận lời mời: gắn tài khoản với nhân khẩu, tiêu mã.
   *
   * **Hôm nay đòi token** — xem javadoc đầu tệp. `apiFetch` tự gắn
   * `Authorization` khi có phiên; không có phiên thì máy chủ trả `401
   * UNAUTHENTICATED`, và đó là một nhánh giao diện riêng (`NEEDS_ACCOUNT`),
   * không phải "mã sai".
   */
  accept: (code: string, account?: AcceptInvitationAccount) =>
    apiFetch<InvitationAcceptedDto>(`${BASE}/accept`, {
      method: "POST",
      body: { code, ...account },
    }),

  /**
   * Đặt mật khẩu qua liên kết một lần. Xem {@link setInvitationPassword} —
   * hàm được khai ở cuối tệp cùng bốn nhánh hỏng của nó, và gắn vào đây để mọi
   * lối gọi của nhóm `invitations` đi qua đúng một cửa.
   */
  setPassword: setInvitationPassword,

  /**
   * "Không phải tôi": huỷ mã và để lại vết trong `audit_log`. **Không cần
   * token** — đòi token ở đây là vô hiệu hoá nút, vì người bấm chính là người
   * *không* có tài khoản.
   *
   * Thao tác **không thu hồi được**, nên giao diện phải hỏi lại và nói rõ hệ
   * quả trước khi gọi (00 §2.4). `204`, không thân phản hồi.
   */
  decline: (code: string) =>
    apiFetch<void>(`${BASE}/decline`, {
      method: "POST",
      body: { code },
    }),

  /**
   * Lời mời trong phạm vi của người gọi.
   *
   * **Lệch quy ước phân trang:** trả một **mảng phẳng**, không bọc
   * `{ items, page }` — giống `/change-requests/mine`. Đừng bọc lại ở đây.
   */
  list: (params: { page?: number; size?: number } = {}) =>
    apiFetch<InvitationDto[]>(BASE, { query: params }),

  issue: (body: IssueInvitationBody) =>
    apiFetch<IssuedInvitationDto>(BASE, { method: "POST", body }),

  /** Lời mời **đã được nhận** thì không thu hồi được (`422`). */
  revoke: (id: string, reason?: string) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", query: { reason } }),
};

// ============================================================================
// ĐẶT MẬT KHẨU QUA LIÊN KẾT MỘT LẦN — `POST /invitations/set-password`
// ============================================================================

/**
 * Thân yêu cầu của `POST /api/v1/invitations/set-password`.
 *
 * <h2>`token` là CHỨNG CHỈ, không phải khoá tra cứu</h2>
 * Nó được ký và mang sẵn hạn dùng, nên lối gọi này khai `security: []` trong
 * contract — <b>không cần token đăng nhập</b>. Đòi đăng nhập ở đây là đóng cửa
 * với đúng người mà nó sinh ra để phục vụ: người vừa được tạo tài khoản và
 * chưa có mật khẩu nào để đăng nhập bằng.
 *
 * <h2>Giá trị lấy NGUYÊN VĂN từ `setPasswordUrl`</h2>
 * Không cắt, không chuẩn hoá, không viết hoa — khác hẳn mã mời (mã mời là thứ
 * một cụ gõ tay từ phiếu giấy; cái này là thứ máy đọc từ đường dẫn). Một phép
 * "dọn dẹp" ở client sẽ làm hỏng chữ ký và cho ra `410` mà không ai hiểu vì sao.
 */
export interface SetPasswordBody {
  token: string;
  /**
   * Mật khẩu người dùng chọn. **Máy chủ không lưu nó** — nó đi thẳng tới
   * Keycloak, nơi chính sách realm phán quyết và nơi nó được băm.
   */
  newPassword: string;
}

/**
 * Bốn cách lối gọi đặt mật khẩu có thể không thành, cộng một ca đường truyền.
 *
 * <h2>Vì sao tách `PASSWORD_REJECTED` khỏi bốn ca kia</h2>
 * Nó là ca <b>duy nhất</b> mà người dùng sửa được ngay tại chỗ, nên nó phải ở
 * lại trên biểu mẫu (lỗi trong ô, mật khẩu vừa gõ còn nguyên) thay vì thay cả
 * màn hình. Bốn ca kia thì biểu mẫu hoặc vô nghĩa (`LINK_INVALID`) hoặc đúng
 * nhưng chưa gửi được (`PROVIDER_DOWN` / `RATE_LIMITED` / `UNAVAILABLE`), và
 * cả ba ca sau đều <b>không tiêu liên kết</b> — thử lại là việc đúng.
 *
 * <h2>`LINK_INVALID` là nghiệp vụ BÌNH THƯỜNG</h2>
 * Hạn ba mươi phút, mà một cụ hoàn toàn có thể để tin nhắn tới hôm sau mới mở.
 * Nó cũng là câu trả lời cho lần gọi <b>thứ hai</b> bằng cùng một liên kết —
 * trong khi mật khẩu đặt ở lần thứ nhất vẫn đăng nhập được. Vì vậy giao diện
 * không được dựng một dải đỏ ở đây, mà phải dựng một lối đi tiếp.
 */
export type SetPasswordFailure =
  | "LINK_INVALID"
  | "PASSWORD_REJECTED"
  | "RATE_LIMITED"
  | "PROVIDER_DOWN"
  | "UNAVAILABLE";

/**
 * `unknown` bắt được từ React Query → nhánh giao diện.
 *
 * <h2>Mặc định là `UNAVAILABLE`, không phải `LINK_INVALID`</h2>
 * Cùng lý do với {@code invitationFailureOf}: bảo một người rằng liên kết của
 * họ hỏng, trong khi thật ra mạng vừa rớt, là cách chắc chắn nhất để họ gọi đi
 * xin một liên kết mới — và liên kết mới cũng sẽ không chạy.
 */
export function setPasswordFailureOf(error: unknown): SetPasswordFailure {
  if (!(error instanceof ApiError)) return "UNAVAILABLE";

  const code = error.code;

  if (code === "SET_PASSWORD_LINK_INVALID") return "LINK_INVALID";
  if (code === "IDENTITY_PROVIDER_UNAVAILABLE") return "PROVIDER_DOWN";
  if (code === "RATE_LIMITED") return "RATE_LIMITED";

  // `VALIDATION_FAILED` ở `422` là realm từ chối MẬT KHẨU; cùng mã ấy ở `400`
  // là thân yêu cầu sai khuôn — tức một lỗi lập trình, không phải thứ người
  // dùng sửa được bằng cách gõ lại. Hai ca khác nhau nên phải phân biệt bằng
  // mã HTTP, và đây là chỗ DUY NHẤT trong tệp này làm vậy.
  if (error.status === 422 && code === "VALIDATION_FAILED") return "PASSWORD_REJECTED";

  // Máy chủ chưa gắn `code` (bản cũ, hoặc một proxy trả HTML).
  if (error.status === 410) return "LINK_INVALID";
  if (error.status === 429) return "RATE_LIMITED";
  if (error.status === 503) return "PROVIDER_DOWN";
  return "UNAVAILABLE";
}

/**
 * Câu máy chủ nói về **mật khẩu vừa bị từ chối** — `detail` của RFC 7807.
 *
 * Contract buộc `detail` ở `422` phải nói <b>phải sửa gì</b> ("quá ngắn, quá dễ
 * đoán, trùng mật khẩu cũ"), và chính sách mật khẩu sống trong realm Keycloak
 * chứ không trong mã này. Vì vậy câu của máy chủ <b>được ưu tiên</b>: dựng một
 * bản sao luật ở client là tạo bản luật thứ hai sẽ lệch, và người dùng sẽ đọc
 * một lời khuyên sai về một mật khẩu bị từ chối vì lý do khác.
 *
 * Trả `null` khi máy chủ không nói gì — lúc ấy giao diện dùng câu dự phòng của
 * mình. Phân nhánh vẫn đi theo {@code code}; chỉ phần CHỮ HIỂN THỊ mới lấy từ
 * `detail`.
 */
export function passwordRejectionDetail(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  const detail = error.problem?.detail?.trim();
  return detail && detail.length > 0 ? detail : null;
}

/**
 * Đặt mật khẩu qua liên kết một lần. `204`, không thân phản hồi.
 *
 * **Không gắn thêm gì vào truy vấn** — `token` đi trong thân POST. Nó đã nằm
 * trong đường dẫn trình duyệt (không tránh được: đó là thứ máy chủ gửi qua tin
 * nhắn), và việc của lớp này là không nhân thêm một bản sao nào nữa.
 */
export async function setInvitationPassword(body: SetPasswordBody): Promise<void> {
  await apiFetch<void>(`${BASE}/set-password`, { method: "POST", body });
}
