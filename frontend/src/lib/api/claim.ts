import { ApiError, apiFetch } from "./http";
import type { DuplicateSignal, Gender } from "@/types/api";

/**
 * Lớp API cho **đơn tự nhận mình trong phả** — nửa *người gửi* của nhóm
 * `/api/v1/person-claims`. Nửa *người duyệt* ở
 * {@code src/lib/api/membership-admin.ts}.
 *
 * <h2>Nguồn của mọi hình dạng dưới đây</h2>
 * `contracts/openapi.yaml`, nhóm `person-claims` — schema `PersonClaim`,
 * `SubmitPersonClaimRequest`, `SubmitNewPersonClaimRequest`, `MyPersonClaims`,
 * `ClaimQuota`, `ClaimBranch`, `ClaimDuplicateSuspect`, `RelativeKind`.
 * Contract thắng mọi nguồn khác.
 *
 * <h2>Đường dẫn là `/person-claims`, KHÔNG phải `/claims`</h2>
 * Lý do backend đưa ra: `claims` một mình sẽ đụng khái niệm khác khi Giai đoạn
 * 3 mang quỹ họ và di sản vào. Đổi ở đúng một hằng số dưới đây.
 *
 * <h2>HAI lối gửi, không phải một lối có trường phân loại</h2>
 * `POST /person-claims/existing` và `POST /person-claims/new-person`. Hai loại
 * đơn dùng chung một bảng và một máy trạng thái, nhưng hệ quả của nút "Duyệt"
 * khác nhau đến mức chúng là hai lối ghi khác nhau — `EXISTING` chỉ *gắn* tài
 * khoản, `NEW_PERSON` **tạo** một nhân khẩu trong phả. {@link SubmitClaimBody}
 * vẫn là một liên hiệp có nhãn `kind` để màn hình khỏi phải biết chuyện này;
 * {@link claimApi.submit} là chỗ duy nhất rẽ nhánh.
 *
 * <h2>Thứ giao diện CỐ Ý KHÔNG xin: một lối hỏi trước "ô này nhận đơn không"</h2>
 * Một lối hỏi trả lời được "ô này có nhận đơn không" <b>chính là</b> công cụ dò
 * xem ai đã vào hệ thống — gõ lần lượt từng ô trên phả đồ và đọc câu trả lời.
 * Contract nói thẳng điều đó ở `x-code-notes.CLAIM_TARGET_UNAVAILABLE`, và vì
 * thế máy chủ gộp **ba** lý do (đã khuất · đã có tài khoản · đã xoá mềm) vào
 * <em>một</em> mã, chỉ lộ ra ở bước <b>gửi</b>.
 *
 * Hai ca biên vẫn chặn được <b>trước</b> khi người dùng gõ, bằng dữ liệu đã có
 * sẵn và không cần endpoint mới:
 * <ul>
 *   <li><b>người đã khuất</b> → {@code GET /persons/&#123;id&#125;} đã trả
 *       {@code isAlive}. Người đã khuất vốn công khai nên đọc trường ấy không
 *       lộ thêm gì;</li>
 *   <li><b>đã có đơn đang chờ · đã hết lượt</b> → {@code GET
 *       /person-claims/mine}. Cả hai là chuyện của <em>chính tài khoản đang
 *       gọi</em>, nên nói rõ được.</li>
 * </ul>
 */

// ============================================================================
// KIỂU CƠ BẢN
// ============================================================================

/**
 * `PersonClaim.kind` — `EXISTING` | `NEW_PERSON`.
 *
 * Hai giá trị này <b>không</b> phải một việc có thêm một cờ — hệ quả của nút
 * "Duyệt" khác nhau đến mức chúng phải là hai màn hình khác nhau:
 * {@code EXISTING} chỉ <em>gắn</em> tài khoản vào một nhân khẩu đã có;
 * {@code NEW_PERSON} <em>tạo</em> một nhân khẩu mới rồi mới gắn.
 */
export type ClaimKind = "EXISTING" | "NEW_PERSON";

/**
 * `PersonClaim.status` — cố ý trùng tập với `ChangeRequestStatus`, kể cả
 * {@code CANCELLED}.
 *
 * {@code CANCELLED} là người gửi <b>tự rút</b>. Giao diện phải mở lối ấy ra: mã
 * {@code CLAIM_ALREADY_OPEN} nói đúng chữ "rút đơn cũ rồi gửi lại", nên không
 * có nút rút thì lời khuyên của máy chủ dẫn vào ngõ cụt.
 *
 * Rút đơn <b>không tiêu một lượt</b>: {@link ClaimQuotaDto.rejected} đếm đơn
 * <em>bị từ chối</em>, không đếm đơn đã rút.
 */
export type ClaimStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

/**
 * `RelativeKind`: vai của <b>người thân đã có trong phả</b> đối với người khai.
 * {@code FATHER} nghĩa là "người này là bố tôi".
 *
 * Ba giá trị và chỉ ba — đó là ba quan hệ mà máy chủ suy ra đời thứ được: cha,
 * mẹ (đời + 1) và vợ/chồng (cùng đời, lối của dâu/rể mới về). "Con" không có
 * mặt vì nó chèn một đời <em>lên trên</em> và đánh số lại cả một nhánh.
 *
 * Đây là <b>vai cấu trúc</b>, không phải `danh xưng` — danh xưng vẫn là việc
 * của máy chủ và không bao giờ được suy ở client.
 */
export type RelativeKind = "FATHER" | "MOTHER" | "SPOUSE";

// ============================================================================
// NHÁNH GIAO DIỆN
// ============================================================================

/**
 * Vì sao <b>không gửi đơn được</b> — tập nhánh giao diện, không phải tập mã lỗi
 * của máy chủ. Ánh xạ nằm ở {@link claimFailureOf}.
 *
 * <h2>Hai lớp, và ranh giới giữa chúng là cả thiết kế riêng tư của luồng</h2>
 * Hai giá trị đầu nói về <em>ô được chọn</em>; ba giá trị sau nói về
 * <em>người đang gọi</em>. Ranh giới ấy quyết định câu chữ được phép cụ thể tới
 * đâu:
 * <ul>
 *   <li>{@code PERSON_DECEASED} — nói thẳng được, và nói thẳng là <b>đúng</b>:
 *       hồ sơ người đã khuất công khai với cả khách, nên chặn ở đây không lộ
 *       thêm gì; một câu mơ hồ chỉ làm người dùng bấm lại đúng ô ấy. Đây cũng
 *       là ca duy nhất giao diện phát hiện được <em>trước</em> khi gửi, từ
 *       {@code isAlive} của {@code GET /persons/&#123;id&#125;}.</li>
 *   <li>{@code PERSON_UNAVAILABLE} — <b>cố ý chung chung</b>, và đây là chỗ dễ
 *       bị "cải tiến" hỏng nhất trong cả tệp. Nó là bản dịch của
 *       {@code CLAIM_TARGET_UNAVAILABLE}, thứ contract dùng chung cho <b>ba</b>
 *       nguyên nhân — đã khuất · đã có tài khoản · đã xoá mềm. Tách ra thì màn
 *       này thành <b>công cụ dò xem ai đã vào hệ thống</b>. Câu chữ hiển thị
 *       cũng phải chung chung — xem `claim.block.personUnavailable`.</li>
 * </ul>
 *
 * Ba giá trị về người gọi thì ngược lại — nói rõ được, vì chúng không kể gì về
 * người khác, và người dùng <b>cần</b> biết để đi tiếp.
 *
 * <h2>Một đơn đang chờ của NGƯỜI KHÁC không nằm trong danh sách này</h2>
 * Có chủ ý, và máy chủ cũng vậy: `GET /person-claims/pending` cho hai đơn cùng
 * trỏ một nhân khẩu <b>đều xuất hiện</b>, không ưu tiên đơn gửi trước — trùng
 * tên trong dòng họ là chuyện thường và người gửi trước chưa chắc là người
 * đúng. Chặn người thứ hai vừa sai quyết định ấy, vừa để lộ rằng có người đã
 * nhận ô này.
 */
export type ClaimBlockReason =
  | "PERSON_DECEASED"
  | "PERSON_UNAVAILABLE"
  | "CLAIM_ALREADY_PENDING"
  | "CLAIM_LIMIT_REACHED"
  | "ACCOUNT_ALREADY_LINKED"
  /**
   * Riêng lối `NEW_PERSON`: người thân được chỉ ra không dùng được để nối vào
   * phả — thiếu, đã xoá mềm, hoặc <b>chưa được gắn vào chi nào</b>.
   *
   * Việc cần làm khác hẳn hai lý do ở trên: không phải "chọn ô khác của chính
   * mình" mà là "chọn một người thân khác, hoặc báo Trưởng chi" — và ca thứ
   * hai là thứ <em>Trưởng chi sửa được còn người dùng thì không</em>, nên câu
   * chữ phải nói ra. Contract tách nó khỏi {@code CLAIM_TARGET_UNAVAILABLE}
   * đúng vì hai luồng dẫn tới hai màn hình khác nhau.
   */
  | "RELATIVE_UNUSABLE";

/**
 * Bốn lỗi <b>không</b> thuộc luật nghiệp vụ mà thuộc đường truyền và phiên làm
 * việc. Tách khỏi {@link ClaimBlockReason} vì lối đi tiếp khác hẳn: thử lại /
 * đăng nhập, chứ không phải "chọn người khác".
 */
export type ClaimTransportFailure =
  /** Chưa đăng nhập, hoặc tài khoản chưa được khởi tạo. */
  | "NEEDS_ACCOUNT"
  | "RATE_LIMITED"
  /** Mọi thứ còn lại: 5xx, mất mạng, proxy trả HTML. */
  | "UNAVAILABLE";

export type ClaimFailure = ClaimBlockReason | ClaimTransportFailure;

/**
 * Lối gửi nào đang chạy.
 *
 * <b>Chỉ còn một việc duy nhất:</b> phân biệt nghĩa của {@code NOT_FOUND} —
 * "ô được nhận không tra ra" so với "người thân được chỉ ra không tra ra". Mọi
 * lý do nghiệp vụ khác nay có mã riêng trong contract và rẽ thẳng theo mã.
 */
export type ClaimFlow = "EXISTING" | "NEW_PERSON";

/**
 * `unknown` bắt được từ React Query → nhánh giao diện.
 *
 * <h2>Rẽ theo `code`, không theo `detail`, không theo mã HTTP</h2>
 * Contract §ProblemCode đã tách đủ các ca mà trước đây dồn vào
 * {@code VALIDATION_FAILED}: {@code CLAIM_TARGET_UNAVAILABLE},
 * {@code CLAIM_RELATIVE_UNUSABLE}, {@code CLAIM_ALREADY_OPEN},
 * {@code CLAIM_LIMIT_REACHED}, {@code CLAIM_CLOSED}. Bản trước phải
 * <em>suy theo lối</em> (`flow === "NEW_PERSON" ? … : …`) vì máy chủ chưa phân
 * biệt; phép suy ấy đã gỡ.
 *
 * <h2>`VALIDATION_FAILED` trơ nay KHÔNG còn nghĩa nghiệp vụ</h2>
 * Nó chỉ còn nghĩa "thân yêu cầu sai khuôn" (`phone` quá 32 ký tự,
 * `introduction` quá 2000, `birthYear` ngoài 1800–2200 …) — tức những thứ biểu
 * mẫu đã chặn bằng `maxLength`. Hàm này rơi nó về {@code UNAVAILABLE}
 * <b>có chủ ý</b>: xem đoạn dưới.
 *
 * <h2>Nhưng {@code UNAVAILABLE} không phải câu CUỐI CÙNG người dùng thấy</h2>
 * Hai màn gửi đơn ({@code claim-existing-form.tsx},
 * {@code claim-new-person-screen.tsx}) gọi {@link isClaimValidationError}
 * <b>trước</b> khi gọi hàm này, và nếu đúng thì hiện thẳng
 * {@link claimValidationDetail} — câu máy chủ nói về chính dữ liệu vừa gửi —
 * thay vì để nó rơi vào nhánh {@code UNAVAILABLE} và bảo người dùng "thử lại
 * sau" cho một thứ sẽ không bao giờ tự hết (một số điện thoại 40 chữ số không
 * ngắn lại vì đợi). Hàm này vẫn phải trả {@code UNAVAILABLE} cho ca ấy, vì nó
 * còn được gọi ở những nơi không có bước chặn trước đó (ví dụ lỗi của
 * {@code /person-claims/mine}).
 *
 * <h2>Mặc định là {@code UNAVAILABLE}, KHÔNG phải một lý do nghiệp vụ</h2>
 * Mặc định về "không gửi cho người này được" là cách nhanh nhất để một trục
 * trặc hạ tầng thành một lời từ chối vĩnh viễn với một người hoàn toàn hợp lệ —
 * và họ sẽ tin.
 */
export function claimFailureOf(error: unknown, flow: ClaimFlow = "EXISTING"): ClaimFailure {
  if (!(error instanceof ApiError)) return "UNAVAILABLE";

  switch (error.problem?.code) {
    case "ACCOUNT_ALREADY_LINKED":
      return "ACCOUNT_ALREADY_LINKED";
    case "CLAIM_LIMIT_REACHED":
      return "CLAIM_LIMIT_REACHED";
    // Người gửi đang có một đơn chờ. Contract nói lối đi tiếp rất cụ thể và
    // "giao diện làm hộ được": rút đơn cũ rồi gửi lại — đúng thứ màn
    // `claim.block.alreadyPending` dẫn tới.
    case "CLAIM_ALREADY_OPEN":
      return "CLAIM_ALREADY_PENDING";
    // MỘT mã cho BA lý do, và gộp lại là *điểm* của nó. Đừng cố suy ra lý do
    // thật từ `detail`.
    case "CLAIM_TARGET_UNAVAILABLE":
      return "PERSON_UNAVAILABLE";
    case "CLAIM_RELATIVE_UNUSABLE":
      return "RELATIVE_UNUSABLE";
    // Đơn đã ở trạng thái cuối (rút/duyệt/từ chối ở một tab khác). Với người
    // gửi, việc cần làm giống hệt ca "đang có đơn mở": mở màn đơn của mình ra
    // xem chuyện gì đã xảy ra.
    case "CLAIM_CLOSED":
      return "CLAIM_ALREADY_PENDING";
    case "NOT_FOUND":
      // Nhân khẩu không tra ra. Với LỐI NHẬN MÌNH đó là cùng một câu trả lời
      // với "đã xoá mềm" — và phải như vậy: phân biệt được thì người dò đọc
      // được mã nào từng tồn tại.
      return flow === "NEW_PERSON" ? "RELATIVE_UNUSABLE" : "PERSON_UNAVAILABLE";
    case "ACCOUNT_NOT_PROVISIONED":
    case "UNAUTHENTICATED":
      return "NEEDS_ACCOUNT";
    case "RATE_LIMITED":
      return "RATE_LIMITED";
    default:
      break;
  }

  if (error.status === 401) return "NEEDS_ACCOUNT";
  if (error.status === 429) return "RATE_LIMITED";
  return "UNAVAILABLE";
}

/** Lý do này nói về người được chọn (đổi người thì hết), hay về tài khoản? */
export function isTargetBlock(reason: ClaimFailure): boolean {
  return (
    reason === "PERSON_DECEASED" ||
    reason === "PERSON_UNAVAILABLE" ||
    reason === "RELATIVE_UNUSABLE"
  );
}

/**
 * `true` khi lỗi là một `VALIDATION_FAILED` <b>trần</b> — thân yêu cầu sai
 * hình dạng (`phone` quá 32 ký tự, `introduction` quá 2000 …), không phải một
 * trong các mã nghiệp vụ đã được tách riêng ở trên.
 *
 * Dùng để rẽ nhánh <b>trước</b> khi gọi {@link claimFailureOf}: hai biểu mẫu
 * gửi đơn phải hiện lý do thật ngay tại chỗ thay vì để nó rơi vào
 * {@code UNAVAILABLE} và bảo người dùng "thử lại sau" — tức chờ một thứ sẽ
 * không bao giờ tự hết, vì số điện thoại họ vừa gõ vẫn sẽ dài y như cũ.
 *
 * Cùng một cách rẽ mà {@code isClanInviteValidationError} trong
 * {@code clan-invite.ts} đã dùng cho `/clan-invites/register` — chép lại
 * nguyên si, không phát minh cách thứ hai.
 */
export function isClaimValidationError(error: unknown): boolean {
  return error instanceof ApiError && error.problem?.code === "VALIDATION_FAILED";
}

/**
 * Câu máy chủ nói về <b>chính thân yêu cầu</b> vừa gửi — `detail` của một
 * `VALIDATION_FAILED` trần ở `POST /person-claims/existing` hoặc
 * `/person-claims/new-person`.
 *
 * Cùng khuôn với {@code clanInviteValidationDetail} và
 * {@code passwordRejectionDetail}: trả {@code null} khi máy chủ không nói gì
 * (hoặc lỗi không phải dạng này) — lúc ấy màn hình dùng câu dự phòng
 * `block.unavailable.body` sẵn có, KHÔNG bịa ra một câu mới.
 */
export function claimValidationDetail(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  if (error.problem?.code !== "VALIDATION_FAILED") return null;
  const detail = error.problem?.detail?.trim();
  return detail && detail.length > 0 ? detail : null;
}

// ============================================================================
// DTO
// ============================================================================

/**
 * `ClaimBranch` — chi đích của một lá đơn, câu trả lời cho <b>"đang chờ ai"</b>.
 *
 * {@code clanTitle} là một <b>vai</b> ("Trưởng Chi Giáp", "Tộc trưởng"),
 * <b>không phải một con người</b>: tên riêng và số điện thoại của Trưởng chi cố
 * ý không đi ra màn này — người đọc là một tài khoản vừa đăng ký, chưa được
 * duyệt, chưa ở trong phả, và cho họ đọc danh bạ ban quản trị là một bề mặt
 * không ai xin.
 *
 * Mọi trường trừ `id` đều có thể vắng. Vắng `clanTitle` thì giao diện <b>để
 * trống dòng ấy</b> chứ đừng bịa; vắng cả khối thì lùi về câu "chờ Trưởng chi
 * của chi liên quan" — vẫn là một câu đúng.
 */
export interface ClaimBranchRefDto {
  id: string;
  name?: string | null;
  /** `CHI` / `NGANH` / … — mã loại chi, không phải nhãn hiển thị. */
  kind?: string | null;
  clanTitle?: string | null;
}

/**
 * `ClaimDuplicateSuspect` — một nhân khẩu bị nghi là chính người đang khai.
 *
 * <h2>Chỉ khoá, điểm và tín hiệu — không tên, không năm sinh, không chi</h2>
 * Bên bị nghi hoàn toàn có thể là một người <b>còn sống ở một chi khác</b> mà
 * người đọc đơn không được xem tên huý hay năm sinh. Giao diện cầm
 * {@code personId} rồi gọi {@code GET /persons/&#123;personId&#125;}, nơi bộ
 * lọc phân tầng riêng tư thật sự chạy.
 *
 * Điều đó đặc biệt quan trọng vì ảnh chụp này được <b>lưu vào đơn</b>: một giá
 * trị đọc từ phả lọt vào đó là lọt vĩnh viễn, và nó sẽ đi qua mọi lần đọc đơn
 * về sau mà không còn bộ lọc nào chạy trên nó.
 */
export interface ClaimDuplicateSuspectDto {
  personId: string;
  /** Điểm nghi ngờ, đã cộng trừ mọi tín hiệu. Cao hơn = đáng xem hơn. */
  score: number;
  /**
   * Tên các tín hiệu đã đóng góp — **chỉ để hiển thị**.
   *
   * Contract khai lỏng (`type: string`); khai chặt ở đây theo
   * {@link DuplicateSignal}, tập đóng mà bộ dò của `genealogy` sinh ra và là
   * tập mà `membership.duplicate.signal.*` đã có câu chữ cho. Một tín hiệu lạ
   * lọt qua sẽ hiện ra nguyên khoá — thấy được, chứ không im lặng.
   */
  signals?: DuplicateSignal[];
  /** Câu giải thích ngắn cho người đọc — **chỉ hiển thị, đừng phân tích chuỗi này**. */
  hint?: string | null;
}

/**
 * `PersonClaim` — một lá đơn, **cùng một schema cho cả người gửi lẫn người
 * duyệt**.
 *
 * <h2>Nó KHÔNG mang tên hay năm sinh của nhân khẩu được nhận</h2>
 * Chỉ {@code personId}. Nhân khẩu ấy có thể là một người <b>còn sống</b>, và ai
 * được xem gì về họ là câu hỏi của bộ lọc phân tầng riêng tư — thứ chạy ở
 * {@code GET /persons/&#123;id&#125;}, không phải ở đây. Giao diện cầm khoá rồi
 * gọi sang; chép sẵn một cái tên vào đây là dựng một lối đọc thứ hai không có
 * bộ lọc nào.
 *
 * Với đơn {@code NEW_PERSON} thì ngược lại: {@code declaredName} là tên
 * <b>người gửi tự khai</b>, chưa phải tên một nhân khẩu nào, nên nó đi thẳng
 * trong phản hồi mà không qua bộ lọc nào — vì không có gì để lọc.
 */
export interface ClaimView {
  id: string;
  kind: ClaimKind;
  status: ClaimStatus;
  /** `app_user` của người khai. */
  requestedBy: string;
  /**
   * Tên hiển thị của **tài khoản** gửi đơn (tự khai lúc đăng ký).
   * **Không** phải tên trong phả.
   */
  requesterDisplayName?: string | null;
  /**
   * Đơn thứ mấy của người này. Lần thứ nhất là chuyện thường; lần thứ tư trên
   * bốn nhân khẩu khác nhau là một tín hiệu hoàn toàn khác.
   */
  attemptNo?: number | null;
  /**
   * Chỉ đơn {@code EXISTING}: nhân khẩu được nhận.
   *
   * Đơn {@code NEW_PERSON} để trống <b>cho tới khi được duyệt</b> — nhân khẩu
   * chỉ ra đời lúc duyệt, xem {@link ClaimView.createdPersonId}. Giao diện phải
   * nói điều này ra thành lời, không coi là chi tiết kỹ thuật.
   */
  personId?: string | null;
  /** Chỉ đơn {@code NEW_PERSON}. */
  relativePersonId?: string | null;
  relativeKind?: RelativeKind | null;
  declaredName?: string | null;
  declaredBirthYear?: number | null;
  declaredGender?: Gender | null;
  /** Chi đích — căn cứ để so phạm vi `ltree` khi duyệt. */
  targetBranchId?: string | null;
  targetBranch?: ClaimBranchRefDto | null;
  /** `NOT NULL` ở contract: một đơn luôn có số để Trưởng chi gọi kiểm chứng. */
  phone: string;
  introduction?: string | null;
  /**
   * Ảnh chụp kết quả dò trùng, chụp **lúc gửi**.
   *
   * <b>Ba trạng thái, ba câu khác nhau trên màn hình</b> — và gộp hai trạng
   * thái đầu sẽ nói với người đọc rằng hệ thống đã kiểm trong khi nó chưa kiểm:
   * <ul>
   *   <li><b>vắng mặt</b> → chưa quét bao giờ (đơn `EXISTING` không cần quét);</li>
   *   <li><b>`[]`</b> → đã quét, không nghi ai — kết quả mong đợi của tuyệt đại
   *       đa số đơn;</li>
   *   <li><b>có phần tử</b> → đã quét, và đây.</li>
   * </ul>
   */
  duplicateSuspects?: ClaimDuplicateSuspectDto[];
  /**
   * Các đơn **khác đang chờ** cùng trỏ vào nhân khẩu này. Rỗng là thường.
   *
   * Chỉ trả <b>khoá</b> — đơn kia chở số điện thoại của một người thứ ba, và
   * người đọc có thể là người gửi đơn này chứ không phải Trưởng chi. Giao diện
   * cầm khoá rồi gọi {@code GET /person-claims/&#123;id&#125;}, nơi phép kiểm
   * quyền thật sự chạy.
   */
  competingClaimIds?: string[];
  reviewerId?: string | null;
  reviewNote?: string | null;
  reviewedAt?: string | null;
  /**
   * Nhân khẩu được tạo <b>lúc duyệt</b> một đơn {@code NEW_PERSON}.
   *
   * Rỗng ở mọi đơn chưa duyệt và mọi đơn bị từ chối — và đó là bằng chứng đọc
   * được rằng không có node ma nào trong phả.
   */
  createdPersonId?: string | null;
  createdAt: string;
}

/**
 * `ClaimQuota` — hạn mức gửi lại **của tài khoản đang gọi**.
 *
 * Ngưỡng là một giá trị <b>cấu hình của máy chủ</b>
 * ({@code giapha.membership.claim.max-rejected}) và một dòng họ hoàn toàn có
 * thể đặt khác, nên <b>client không được đoán</b>: gán cứng con số sẽ đúng hôm
 * nay và âm thầm sai ngày Hội đồng đổi cấu hình.
 *
 * Đếm theo <b>tài khoản</b>, không theo ô được chọn — đó là điểm mấu chốt: đếm
 * theo ô thì kẻ dò chỉ cần đổi sang người kế tiếp là bộ đếm lại về không.
 */
export interface ClaimQuotaDto {
  /** Số đơn của tài khoản này **đã bị từ chối**. Đơn tự rút không tính. */
  rejected: number;
  max: number;
  /** `max - rejected`, không bao giờ âm. */
  remaining: number;
}

/**
 * `MyPersonClaims` — phản hồi của `GET /person-claims/mine`.
 *
 * <h2>Phong bì, không phải mảng trần</h2>
 * {@code quota} không thuộc về đơn nào: nó là hạn mức của <b>tài khoản</b>.
 * Nhét vào từng phần tử là nhân bản một sự thật, và bản sao thứ hai là bản sẽ
 * lệch. Cả hai trường đều <b>bắt buộc</b> ở contract.
 */
export interface MyClaimsDto {
  claims: ClaimView[];
  quota: ClaimQuotaDto;
}

/**
 * Danh sách đơn, hoặc rỗng khi chưa tải xong.
 *
 * Còn lại ở đây (thay vì đọc thẳng `data.claims`) vì nó nuốt gọn ca
 * {@code undefined} của React Query ở một chỗ, và mọi màn đọc nhóm này đều phải
 * xử ca ấy.
 */
export function claimsOf(response: MyClaimsDto | undefined): ClaimView[] {
  return response?.claims ?? [];
}

/**
 * Bộ đếm, hoặc {@code null} khi chưa tải xong.
 *
 * {@code null} phải được đọc là "<b>chưa biết</b>", tuyệt đối không phải
 * "còn 0 lần": giao diện im lặng về số lần chứ không bịa ra một con số.
 */
export function quotaOf(response: MyClaimsDto | undefined): ClaimQuotaDto | null {
  return response?.quota ?? null;
}

// ============================================================================
// THÂN YÊU CẦU
// ============================================================================

/**
 * `SubmitPersonClaimRequest` — đơn "tôi là ô này".
 *
 * <h2>`introduction` là thứ quyết định; `phone` chỉ để gọi kiểm chứng</h2>
 * Contract nói thẳng điều này. Giao diện phải phản ánh đúng thứ bậc: phần tự
 * giới thiệu là một ô nhiều dòng có câu hỏi gợi ý thật, không phải ô "ghi chú
 * thêm" nép ở cuối. Một lá đơn chỉ có số điện thoại là một lá đơn Trưởng chi
 * không quyết được, và nó sẽ nằm mãi trong hàng chờ.
 *
 * `kind` là nhãn của liên hiệp **ở phía client** — nó chọn endpoint và
 * {@link claimApi.submit} gỡ nó ra trước khi gửi. Contract không có trường này
 * trong thân yêu cầu.
 */
export interface SubmitExistingClaimBody {
  kind: "EXISTING";
  personId: string;
  /** Gửi <b>nguyên văn</b>. Chuẩn hoá đầu số là luật của máy chủ. Tối đa 32 ký tự. */
  phone: string;
  /** Tối đa 2000 ký tự. */
  introduction: string;
}

/**
 * `SubmitNewPersonClaimRequest` — đơn "tôi chưa có trong phả".
 *
 * Gửi đơn này <b>không</b> tạo nhân khẩu nào: nhân khẩu chỉ ra đời khi Trưởng
 * chi duyệt. Xoá mềm là luật tuyệt đối của dự án, nên tạo trước rồi xoá sau sẽ
 * để lại một <b>node ma</b> trong phả cho mỗi đơn bị từ chối.
 *
 * Đơn chạy qua bộ dò trùng <b>ngay lúc gửi</b> và phản hồi mang
 * {@link ClaimView.duplicateSuspects}.
 */
export interface SubmitNewPersonClaimBody {
  kind: "NEW_PERSON";
  /** Tối đa 160 ký tự. */
  fullName: string;
  /**
   * <b>Không bắt buộc ở contract</b> (1800–2200 khi có), nhưng biểu mẫu vẫn hỏi
   * và vẫn đòi: nó là tín hiệu tốt nhất mà bộ dò trùng có, và bộ dò là thứ giữ
   * cho phả không sinh ra hai bản ghi của cùng một người. Đây là chỗ giao diện
   * <b>chặt hơn</b> API một cách có chủ ý — ghi ra để không ai đọc nhầm thành
   * lệch hợp đồng.
   */
  birthYear?: number | null;
  gender: Gender;
  /**
   * **Người thân đã có trong phả — bắt buộc.** Không có nó thì nhân khẩu mới
   * thành node mồ côi: không gắn vào cây, không tính được đời, không tra được
   * danh xưng. Người thân ấy cũng là thứ quyết định **ai duyệt**.
   *
   * Được phép <b>đã khuất</b>; chỉ nhân khẩu đã xoá mềm mới bị từ chối.
   */
  relativePersonId: string;
  relativeKind: RelativeKind;
  phone: string;
  introduction: string;
}

export type SubmitClaimBody = SubmitExistingClaimBody | SubmitNewPersonClaimBody;

// ============================================================================
// LỜI GỌI
// ============================================================================

const BASE = "/api/v1/person-claims";

export const claimApi = {
  /**
   * Đơn của chính người gọi, kèm hạn mức gửi lại.
   *
   * Không phân trang ở đây: một tài khoản có nhiều nhất vài lá đơn trong cả
   * đời (bộ đếm chặn ở vài lần bị từ chối), nên một trang là đủ và thêm điều
   * khiển phân trang chỉ làm rối một màn mà phần lớn người dùng mở đúng một lần.
   */
  mine: () => apiFetch<MyClaimsDto>(`${BASE}/mine`),

  /**
   * Gửi đơn — <b>hai endpoint, chọn theo `kind`</b>.
   *
   * Đây là <em>chỗ duy nhất</em> biết rằng có hai lối: màn hình vẫn dựng một
   * {@link SubmitClaimBody} có nhãn và không biết gì về đường dẫn. `kind` bị gỡ
   * khỏi thân yêu cầu vì contract không khai nó — gửi thừa một trường là mời
   * một `400` ở ngày máy chủ bật kiểm chặt.
   */
  submit: ({ kind, ...than }: SubmitClaimBody) =>
    apiFetch<ClaimView>(`${BASE}/${kind === "NEW_PERSON" ? "new-person" : "existing"}`, {
      method: "POST",
      body: than,
    }),

  /** Chi tiết một đơn — **chỉ người gửi hoặc người duyệt đúng phạm vi chi**. */
  byId: (claimId: string) => apiFetch<ClaimView>(`${BASE}/${claimId}`),

  /**
   * Tự rút đơn của mình.
   *
   * Rút <b>không tiêu một lượt</b> (bộ đếm đếm đơn bị từ chối), và nó là lối
   * duy nhất để gửi một đơn khác khi đang có đơn mở. Chính mã
   * {@code CLAIM_ALREADY_OPEN} bảo người dùng làm việc này, nên thiếu nút rút
   * là để lời khuyên của máy chủ dẫn vào ngõ cụt.
   */
  cancel: (claimId: string) =>
    apiFetch<ClaimView>(`${BASE}/${claimId}/cancel`, { method: "POST" }),
};
