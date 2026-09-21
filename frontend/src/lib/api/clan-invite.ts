import { ApiError, apiFetch } from "./http";

/**
 * Lớp API cho **mã mời DÒNG HỌ** — nửa của *người cầm mã*:
 * `POST /api/v1/clan-invites/lookup` và `POST /api/v1/clan-invites/register`.
 *
 * <h2>Vì sao tệp này tồn tại tách khỏi `membership-admin.ts`</h2>
 * `clanInvitesApi` trong {@code src/lib/api/membership-admin.ts} là nửa của
 * **Hội đồng**: phát · liệt kê · thu hồi, và mọi lối ở đó **đòi token cùng
 * phạm vi toàn dòng họ**. Hai lối dưới đây thì ngược hẳn — chúng phục vụ người
 * <b>chưa có tài khoản</b>, nên chúng không đòi token, không đọc vai, và thẩm
 * quyền duy nhất là <b>việc sở hữu mã</b>. Gộp hai nửa vào một tệp là mời gọi
 * một lượt gọi "tiện tay" từ màn đăng ký sang một endpoint quản trị.
 *
 * <h2>Nguồn hình dạng</h2>
 * `contracts/openapi.yaml`, nhóm `clan-invites` — schema `ClanInvitePreview`,
 * `ClanRegistration`, `RedeemClanInviteRequest`, `RegisterWithClanInviteRequest`.
 * Contract thắng mọi nguồn khác.
 *
 * <h2>Kiểm mã TRƯỚC, tạo tài khoản SAU — và đó là cả một luật nghiệp vụ</h2>
 * Contract nói thẳng ở `POST /clan-invites/lookup`: realm đặt
 * {@code duplicateEmailsAllowed: false}, nên một lần gõ sai mã mà đã kịp tạo
 * tài khoản sẽ để lại một tài khoản Keycloak mồ côi — và chính lần thử lại của
 * người ấy sẽ hỏng vì định danh đã bị tài khoản rác chiếm. Không ai đi dọn thứ đó.
 *
 * <h2>Bộ đếm lượt dùng là chốt quan trọng nhất, và nó chỉ đếm ở ĐÂY</h2>
 * Design 07 §1.2: mã cấp cho **cả họ** nên bộ đếm là lớp bảo vệ duy nhất —
 * "mã đã dùng 400 lần trong khi dòng họ có 600 người" là tín hiệu để Hội đồng
 * thu hồi. Chỉ `POST /clan-invites/register` tiêu một lượt vào sổ ấy. Mọi lối
 * lập tài khoản khác (trang đăng ký của Keycloak chẳng hạn) đếm **thiếu trong
 * im lặng**, và một bộ đếm sai còn tệ hơn không có bộ đếm.
 */

// ============================================================================
// CHUẨN HOÁ MÃ
// ============================================================================

/**
 * Chuẩn hoá mã mời dòng họ — **cùng luật với máy chủ**, chép theo javadoc của
 * `RedeemClanInviteRequest` ("dấu gạch, khoảng trắng và chữ thường đều chấp
 * nhận được") và theo khối chú thích chuẩn hoá trong `src/lib/api/invitation.ts`.
 *
 * Bốn phép, theo đúng thứ tự:
 * <ol>
 *   <li>viết hoa;</li>
 *   <li>bỏ mọi thứ không phải chữ-số — gạch nối, khoảng trắng, chấm, gạch dưới;</li>
 *   <li>`O` → `0`;</li>
 *   <li>`I` và `L` → `1`.</li>
 * </ol>
 *
 * <h2>Vì sao hai phép dịch cuối KHÔNG làm hai mã thật va vào nhau</h2>
 * Bảng chữ **Crockford Base32** đã loại hẳn `I L O U` khỏi tập ký tự sinh mã,
 * nên ký tự nguồn (`O`, `I`, `L`) không bao giờ xuất hiện trong một mã thật —
 * gặp chúng thì chắc chắn là người gõ nhầm, không phải một mã khác.
 *
 * <h2>Client chuẩn hoá chỉ để ĐỠ MỘT VÒNG GỌI — máy chủ mới phán quyết</h2>
 * Phiếu in `K7M-2QD`, tin nhắn mang `K7M2QD`, một cụ bảy mươi gõ `k7m 2qd`.
 * Cả ba phải mở cùng một mã, và máy chủ đã làm đúng việc ấy. Làm lại ở đây là
 * để một lần gõ có dấu gạch không tốn một lượt mạng — <b>không</b> phải để
 * client tự quyết mã nào hợp lệ. Mọi phán quyết vẫn đi từ phản hồi của máy chủ;
 * tệp này không bao giờ từ chối một mã.
 */
export function normalizeClanInviteCode(raw: string): string {
  return raw
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .replace(/O/g, "0")
    .replace(/[IL]/g, "1");
}

// ============================================================================
// NHÁNH HỎNG — kiểu của GIAO DIỆN, không phải của đường truyền
// ============================================================================

/**
 * Mọi cách một mã dòng họ có thể không mở được, cộng hai ca không phải lỗi
 * của mã. **Mỗi nhánh có câu chữ riêng và một lối đi tiếp riêng** — gộp chúng
 * thành "mã không hợp lệ" là dựng một dải đỏ cụt cho bốn tình huống mà người
 * dùng phải làm bốn việc khác nhau.
 *
 * <ul>
 *   <li>{@code NOT_FOUND} — mã không khớp mã nào. Hay gặp nhất là đường dẫn bị
 *       cắt lúc chuyển tiếp tin nhắn, hoặc gõ thiếu một ký tự. <b>Cùng một câu
 *       trả lời với mã sai định dạng</b>, cố ý: phân biệt được thì người dò đọc
 *       ra độ dài và bảng chữ của mã mà không cần đoán trúng lần nào.</li>
 *   <li>{@code EXPIRED} — mã có thật, đã quá hạn. Hệ thống đang chạy <i>đúng</i>
 *       (chốt "có hạn dùng", design 07 §1.2).</li>
 *   <li>{@code REVOKED} — <b>khác hẳn hết hạn</b>: có người chủ động đóng nó.
 *       Đó là chốt "thu hồi được" vừa làm việc của nó, thường vì mã đã lan.</li>
 *   <li>{@code EXHAUSTED} — mã còn hạn, chưa thu hồi, nhưng đã đầy trần lượt
 *       Hội đồng đặt. Đây là ca <b>duy nhất</b> mà trạng thái đổi được mà không
 *       cần mã mới (Hội đồng nâng trần), nên nó phải có câu riêng.</li>
 *   <li>{@code RATE_LIMITED} — <b>không phải lỗi của họ</b>. Chốt "giới hạn tần
 *       suất" đang chặn việc dò mã bằng máy; việc cần làm là đợi.</li>
 *   <li>{@code IDENTITY_TAKEN} — định danh vừa khai <b>đã thuộc về một tài khoản
 *       đang tồn tại</b>. Không phải lỗi của mã: mã vẫn đúng, vẫn còn lượt.
 *       Đây là nhánh <b>duy nhất</b> mà lối đi tiếp là <i>đăng nhập</i> chứ không
 *       phải gõ lại hay chờ — xem {@code register-problem.tsx}.</li>
 *   <li>{@code PROVIDER_DOWN} — không lập được tài khoản ở Keycloak. Điểm quan
 *       trọng nhất: <b>mã CHƯA bị tiêu</b> (xem javadoc `register`), nên bấm
 *       lại là việc đúng — và câu chữ phải nói ra điều đó.</li>
 *   <li>{@code UNAVAILABLE} — mất mạng hoặc máy chủ 500.</li>
 * </ul>
 */
export type ClanInviteFailure =
  | "NOT_FOUND"
  | "EXPIRED"
  | "REVOKED"
  | "EXHAUSTED"
  | "RATE_LIMITED"
  | "IDENTITY_TAKEN"
  | "PROVIDER_DOWN"
  | "UNAVAILABLE";

/**
 * `unknown` bắt được từ React Query → nhánh giao diện.
 *
 * <h2>Mặc định là `UNAVAILABLE`, KHÔNG phải `NOT_FOUND`</h2>
 * Cùng luật với {@code invitationFailureOf}. Bảo một người rằng mã của họ sai,
 * trong khi thật ra mạng vừa rớt, là cách chắc chắn nhất để họ đi xin một mã
 * mới — và mã mới cũng sẽ không mở được.
 *
 * <h2>Phân nhánh theo `code`, không theo mã HTTP</h2>
 * Contract trả `410` cho hết hạn/thu hồi và `409` cho hết lượt; mã trạng thái ở
 * đó là để proxy và giám sát hiểu đúng, còn client thì rẽ theo {@code code}.
 * Hai nhánh HTTP ở cuối hàm chỉ là lưới hứng cho trường hợp máy chủ chưa gắn
 * `code` (một proxy trả HTML chẳng hạn) — và `410` thì **không** suy ra được là
 * hết hạn hay đã thu hồi, nên nó cố ý rơi về `UNAVAILABLE` thay vì đoán một
 * trong hai.
 *
 * <h2>Hai trong ba ca của mã dòng họ DÙNG LẠI mã lỗi của lời mời cá nhân</h2>
 * `INVITATION_EXPIRED` và `INVITATION_REVOKED` — quyết định của contract, với
 * lý do viết ra thành lời: *người dùng chỉ cầm một dãy mười ký tự và không phân
 * biệt được hai loại mã*, nên hai tập mã song song chỉ bắt giao diện viết hai
 * nhánh giống hệt nhau. Câu chữ đã viết cho hai ca ấy giữ nguyên.
 * `CLAN_INVITE_EXHAUSTED` là mã riêng **duy nhất** của nhóm này, vì nó là ca
 * duy nhất mà trạng thái đổi được mà không cần mã mới (Hội đồng nâng trần).
 *
 * <h2>`detail` của mã này KHÔNG được in ra màn hình — sản phẩm song ngữ</h2>
 * Cám dỗ có thật: câu của máy chủ mang hai tình tiết quý (địa chỉ ấy có thể đã
 * từng dùng để đăng nhập <b>bằng Google</b> — một lối không đặt mật khẩu nào cả
 * nên người ta không nhớ; và lối đi thứ hai cho người không đăng nhập nổi là
 * đưa mã mời cho Trưởng chi). Nhưng `detail` <b>chỉ có tiếng Việt</b>, nên in
 * nó ra là dán một đoạn tiếng Việt không dấu xuống dưới một đoạn tiếng Anh hoàn
 * chỉnh cho bà con ở nước ngoài. Đó không phải "thêm tình tiết", đó là một lỗi
 * song ngữ.
 *
 * <p>Và nó <b>thừa</b>: hai tình tiết ấy đã nằm trong `auth.register.problem`
 * / `auth.invitation.problem` ở cả hai ngôn ngữ. Đó chính là <i>lý do</i> hợp
 * đồng dùng một {@code ProblemCode} <b>đóng</b> cộng một bộ dịch phía client —
 * `code` là thứ máy đọc và dịch được, `detail` là thứ dành cho nhật ký và cho
 * người hỗ trợ đang đọc log. In `detail` ra màn là đi vòng qua chính thiết kế
 * ấy, và cái vòng ấy chỉ hở ra ở bản dịch thứ hai.</p>
 *
 * <h2>`IDENTITY_ALREADY_REGISTERED` — KHÔNG được thử lại, dù chỉ một lần</h2>
 * Mã này là chỗ vừa bịt một lỗ hổng chiếm tài khoản: trước bản vá, cùng lời gọi
 * ấy trả về một **liên kết đặt mật khẩu dùng được** cho tài khoản của người
 * khác. Nay nó từ chối — và **lần từ chối ấy được máy chủ tính vào giới hạn tần
 * suất như một lần thất bại**, cố ý, để phần tín hiệu còn sót ("địa chỉ này đã
 * là thành viên") không biến endpoint thành máy dò tài khoản.
 *
 * <p>Hệ quả cho client là một luật cứng: <b>không tự thử lại, không thử lại
 * ngầm, không nút "Thử lại"</b>. Mỗi lượt gọi lặp đốt thêm hạn mức của chính
 * người dùng ngay tình, và câu trả lời sẽ y hệt. Lối đi tiếp duy nhất đúng là
 * <b>đăng nhập</b>, rồi nhập lại mã dòng họ khi đã có phiên: nhánh "đã có token"
 * của cùng endpoint lấy danh tính từ Keycloak nên không đi qua phép chặn này.
 * {@code register-problem.tsx} giữ luật ấy bằng cách không bao giờ dựng nút thử
 * lại cho nhánh {@code IDENTITY_TAKEN}.</p>
 */
export function clanInviteFailureOf(error: unknown): ClanInviteFailure {
  if (!(error instanceof ApiError)) return "UNAVAILABLE";

  switch (error.problem?.code) {
    case "INVITATION_EXPIRED":
      return "EXPIRED";
    case "INVITATION_REVOKED":
      return "REVOKED";
    case "CLAN_INVITE_EXHAUSTED":
      return "EXHAUSTED";
    case "NOT_FOUND":
      return "NOT_FOUND";
    case "RATE_LIMITED":
      return "RATE_LIMITED";
    case "IDENTITY_PROVIDER_UNAVAILABLE":
      return "PROVIDER_DOWN";
    case "IDENTITY_ALREADY_REGISTERED":
      return "IDENTITY_TAKEN";
    default:
      break;
  }

  if (error.status === 404) return "NOT_FOUND";
  if (error.status === 429) return "RATE_LIMITED";
  if (error.status === 503) return "PROVIDER_DOWN";
  return "UNAVAILABLE";
}

/**
 * Số giây máy chủ bảo đợi ở `429` — thuộc tính mở rộng `retryAfterSeconds` của
 * RFC 7807, do {@code MembershipExceptionHandler.handleTooManyAttempts} gắn.
 *
 * Trả `null` khi máy chủ không nói, và lúc ấy giao diện nói "ít phút" thay vì
 * bịa một con số. Design 00 §5: **không bịa số**.
 */
export function retryAfterSecondsOf(error: unknown): number | null {
  if (!(error instanceof ApiError)) return null;
  const raw = (error.problem as unknown as Record<string, unknown> | undefined)
    ?.retryAfterSeconds;
  return typeof raw === "number" && Number.isFinite(raw) && raw > 0 ? raw : null;
}

/**
 * Câu máy chủ nói về **chính dữ liệu vừa gửi** — `detail` của một `400`/`422`
 * `VALIDATION_FAILED` ở `/register`.
 *
 * Ở luồng này nó gần như luôn nói về ô định danh: `loginId` đọc không ra thành
 * email lẫn số máy. Nên nó thuộc về **trong ô** chứ không phải một dải báo lỗi
 * thay cả màn hình. Trả `null` khi máy chủ không nói gì — lúc ấy giao diện dùng
 * câu dự phòng của mình.
 */
export function clanInviteValidationDetail(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  if (error.problem?.code !== "VALIDATION_FAILED") return null;
  const detail = error.problem?.detail?.trim();
  return detail && detail.length > 0 ? detail : null;
}

/** `true` khi lỗi là một `VALIDATION_FAILED` — tức thứ người dùng sửa được ngay. */
export function isClanInviteValidationError(error: unknown): boolean {
  return (
    error instanceof ApiError && error.problem?.code === "VALIDATION_FAILED"
  );
}

// ============================================================================
// DTO
// ============================================================================

/**
 * `ClanInvitePreviewDto` — **hai trường, và đó là toàn bộ**.
 *
 * Khác hẳn `InvitationPreviewDto`: mã dòng họ không trỏ vào ai nên không có tên
 * người nào để trả. Và bộ đếm lẫn nhãn mã **cố ý vắng mặt** — chúng là công cụ
 * giám sát của Hội đồng; nói "mã này còn 3 lượt" cho một người chưa đăng nhập
 * là nói cho kẻ dò biết mình đang ở đâu.
 *
 * `clanName` **có thể vắng** (`@JsonInclude(NON_NULL)` + `orElse(null)` ở
 * `ClanInviteService.preview`): một phả chưa có gốc chi thì không có tên để
 * lấy. Giao diện phải dựng được khi chỉ có `expiresAt`.
 */
export interface ClanInvitePreviewDto {
  clanName?: string;
  /** ISO-8601. Dùng để nói "mã còn dùng được tới …", không để tự tính hết hạn. */
  expiresAt: string;
}

/**
 * `ClanRegistrationDto` — tài khoản vừa lập.
 *
 * <h2>KHÔNG có `personId`, và `status` luôn là `PENDING`</h2>
 * Đó là điểm phân biệt căn bản với `InvitationAcceptedDto`, và nó là **trạng
 * thái đúng chứ không phải một bước dang dở**: mã dòng họ không trỏ vào ai, nên
 * không có gì để ghép. Suy đoán theo trùng tên là cách nhanh nhất để trao cho
 * một người quyền đọc dữ liệu Tầng 3 của người khác. Bước tiếp theo là xem phả
 * đồ rồi tự nhận mình.
 */
export interface ClanRegistrationDto {
  appUserId: string;
  status: "PENDING";
  clanInviteId: string;
  /**
   * Liên kết một lần để tự đặt mật khẩu.
   *
   * **VẮNG MẶT là một phép chặn có chủ ý, không phải một lỗi.** Nó vắng khi tài
   * khoản đã có mật khẩu từ trước, và khi người gọi đã mang sẵn token (lối
   * Google/Zalo). Nếu máy chủ vẫn phát trong hai ca ấy thì bất kỳ ai cầm mã
   * dòng họ — tức **cả họ** — cộng với việc đoán đúng thư điện tử của một thành
   * viên cũ sẽ đổi được mật khẩu của người ta. Vắng thì đưa người dùng tới màn
   * đăng nhập.
   */
  setPasswordUrl?: string;
  /** Hạn của `setPasswordUrl`; vắng cùng lúc với nó. */
  setPasswordExpiresAt?: string;
}

/** Thân `POST /clan-invites/register` — `RegisterWithClanInviteRequest`. */
export interface RegisterWithClanInviteBody {
  /** Tối đa 32 ký tự. Gửi bản **đã chuẩn hoá**; máy chủ chuẩn hoá lại lần nữa. */
  code: string;
  /**
   * **Địa chỉ thư điện tử HOẶC số điện thoại** — định danh đăng nhập ở Keycloak,
   * tối đa 254 ký tự.
   *
   * Ô đăng nhập của realm nhận cả hai (quyết định ấy có để phục vụ các cụ không
   * có email), nên ô lập tài khoản cũng nhận cả hai. Chỉ nhận email ở đây là
   * một **mâu thuẫn trong chính sản phẩm**, và nó rơi đúng vào nhóm người mà
   * luồng mời sinh ra để phục vụ.
   *
   * **Gửi nguyên văn.** Chuẩn hoá số Việt Nam về dạng `0…` (`+84912345678`,
   * `84912345678`, `0912 345 678`, `0912.345.678` đều là **một** số) là luật
   * của máy chủ — dựng bản sao thứ hai ở client là dựng một bản sẽ lệch, và
   * lệch ở đây nghĩa là cùng một người đăng ký hai lần ra hai tài khoản.
   *
   * Bắt buộc khi gọi **không kèm token**. Người gọi đã đăng nhập (vừa vào bằng
   * Google/Zalo nhưng chưa được ghép vào phả) thì danh tính lấy từ token và
   * trường này bị bỏ qua.
   *
   * Đọc không ra thành email lẫn số máy → `422 VALIDATION_FAILED`.
   */
  loginId?: string;
  /**
   * **Tên cũ của {@link RegisterWithClanInviteBody.loginId}** — contract đánh
   * dấu `deprecated`, giữ lại để bản giao diện đang chạy không gãy.
   *
   * Đừng dùng ở mã mới: tên ấy nói dối khi giá trị là một số điện thoại, và
   * chính lời nói dối ấy là thứ đã khiến màn đăng ký chặn số máy suốt một thời
   * gian.
   *
   * @deprecated dùng `loginId`.
   */
  email?: string;
  /**
   * Tên hiển thị tự khai, tối đa 160 ký tự.
   *
   * **KHÔNG phải tên trong phả**, và máy chủ **không** dùng nó để suy ra nhân
   * khẩu nào — việc ghép là của đơn tự nhận và của Trưởng chi.
   */
  displayName?: string;
}

// ============================================================================
// GỌI API
// ============================================================================

const BASE = "/api/v1/clan-invites";

export const clanInviteApi = {
  /**
   * "Mã này của dòng họ nào" — **không cần token, và không tiêu lượt nào**.
   *
   * `POST` chứ không `GET {code}`, kể cả ở một lối chỉ đọc: mã là bí mật, và
   * đặt nó vào đường dẫn là đặt nó vào access log của reverse proxy, lịch sử
   * trình duyệt, header `Referer` và mọi hệ thống giám sát đang gom URL. Thân
   * của một `POST` không đi vào chỗ nào trong số đó.
   */
  lookup: (code: string) =>
    apiFetch<ClanInvitePreviewDto>(`${BASE}/lookup`, {
      method: "POST",
      body: { code: normalizeClanInviteCode(code) },
    }),

  /**
   * Lập tài khoản bằng mã dòng họ — **không cần token**, và **tiêu một lượt**.
   *
   * `apiFetch` tự gắn `Authorization` khi có phiên, và gọi kèm token **cũng
   * hợp lệ**: đó là lối của người vừa đăng nhập Google/Zalo mà chưa được ghép
   * vào phả. Lúc ấy `loginId` trong thân bị bỏ qua và `setPasswordUrl` vắng mặt.
   *
   * Bấm hai lần **không** đếm thành hai lượt: một tài khoản đếm một lượt trên
   * một mã. Không có điều đó thì bộ đếm phồng lên vì những lần bấm lại vô hại,
   * và Hội đồng sẽ thu hồi một mã lành vì tưởng nó đã rò.
   */
  register: (body: RegisterWithClanInviteBody) =>
    apiFetch<ClanRegistrationDto>(`${BASE}/register`, {
      method: "POST",
      body: { ...body, code: normalizeClanInviteCode(body.code) },
    }),
};

// ============================================================================
// ĐỊNH DANH ĐĂNG NHẬP — thư điện tử hay số điện thoại
// ============================================================================

/**
 * Hình dạng của thứ người dùng vừa gõ vào ô định danh.
 *
 * <h2>Cả hai ĐỀU ĐĂNG KÝ ĐƯỢC — phân loại này KHÔNG còn là một phép chặn</h2>
 * `RegisterWithClanInviteRequest.loginId` và `AcceptInvitationRequest.loginId`
 * nhận **email hoặc số điện thoại Việt Nam**, và máy chủ chuẩn hoá `+84…` /
 * `84…` / dấu cách / chấm / gạch về dạng `0…`. Bản trước của tệp này chặn số
 * máy tại chỗ vì backend khi ấy mang `@Email`; **điều đó đã hết đúng**.
 *
 * <h2>Nó chỉ còn MỘT việc: chọn câu ghi chú nào hiện ra SAU khi đăng ký xong</h2>
 * Giới hạn có thật và giao diện nên nói ra — **tài khoản lập bằng số điện thoại
 * sẽ không nhận được thư đặt lại mật khẩu** khi hệ thống có SMTP. Nhưng đó là
 * giới hạn của việc **khôi phục**, không phải của việc **đăng ký**: nói nó ở ô
 * nhập là chặn nhầm một người máy chủ sẵn sàng nhận. Chỗ đúng là khối đặt mật
 * khẩu ở bước xong ({@code register-done.tsx}).
 *
 * Hàm {@link loginIdentifierKind} vì thế **không bao giờ nói "sai"** — nó chỉ
 * nói "đây trông như cái gì". Mọi phán quyết vẫn đi từ máy chủ.
 */
export type LoginIdentifierKind = "EMAIL" | "PHONE" | "UNKNOWN";

/**
 * Số điện thoại Việt Nam sau khi bỏ khoảng trắng, chấm và gạch: `0` + 9 chữ số,
 * hoặc `+84`/`84` + 9 chữ số.
 *
 * Cố ý **rộng**: nó chỉ quyết định *câu trả lời nào hiện ra*, không quyết định
 * ai được đăng ký. Một chuỗi toàn chữ số dài 9–11 ký tự thì gần như chắc chắn
 * là một số máy, và nói "đây là số điện thoại" với nó thì đúng hơn nhiều so với
 * nói "thư điện tử sai khuôn".
 */
const SO_DIEN_THOAI = /^(?:\+?84|0)\d{9,10}$/;

/**
 * Phân loại thứ vừa gõ. **Không phải một phép kiểm hợp lệ** — nó không bao giờ
 * nói "sai", nó chỉ nói "đây trông như cái gì" để màn hình chọn đúng câu.
 *
 * `UNKNOWN` (ô trống, hay một chuỗi chẳng giống gì) đi thẳng tới máy chủ: chỗ
 * duy nhất phán quyết là ở đó, và một ô trống đã có câu riêng ngay trong biểu
 * mẫu.
 */
export function loginIdentifierKind(raw: string): LoginIdentifierKind {
  const goi = raw.trim();
  if (goi.length === 0) return "UNKNOWN";
  if (SO_DIEN_THOAI.test(goi.replace(/[\s.\-()]/g, ""))) return "PHONE";
  // Khuôn tối thiểu, cố ý lỏng hơn `@Email` của máy chủ: tệp này KHÔNG dựng bản
  // sao thứ hai của luật thư điện tử — bản thứ hai sẽ lệch, và lệch theo chiều
  // "chặt hơn máy chủ" nghĩa là chặn một địa chỉ máy chủ sẵn sàng nhận.
  if (/^[^\s@]+@[^\s@]+$/.test(goi)) return "EMAIL";
  return "UNKNOWN";
}
