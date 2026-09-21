import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { resolveMockRole } from "./role";
import type {
  InvitationAcceptedDto,
  InvitationPreviewDto,
} from "@/lib/api/invitation";
import type { Problem, ProblemCode } from "@/types/api";

/**
 * MSW cho **lời mời vào phả** — nhóm `invitations` của
 * `contracts/openapi.yaml`.
 *
 * <h2>Bộ giả lập này chép theo contract, không theo ý muốn của giao diện</h2>
 * Bốn chỗ dưới đây cố ý **khó hơn** một API dễ chiều màn hình, vì API thật khó
 * đúng như thế; một mock dễ hơn API thật là một lời hứa mà nghiệm thu sẽ đòi
 * lại:
 * <ul>
 *   <li>`/lookup` <b>không</b> trả `personId` (khoá nối bền vững cho người chưa
 *       xác thực) và <b>không</b> trả `relationToInviter` (danh xưng không tính
 *       được cho một khách không token ở Giai đoạn 1);</li>
 *   <li>`inviter.displayName` là tên <b>trần</b>, không kính ngữ;</li>
 *   <li>`/accept` <b>KHÔNG đòi token</b> (`security: []`), và khi người gọi
 *       không mang token thì <b>bắt buộc</b> có {@code loginId} — đúng luật
 *       {@code LoginIdentifier.of} phía máy chủ, kể cả thông điệp lỗi (chép
 *       nguyên văn, không dấu, như chính máy chủ Java trả về);</li>
 *   <li>bốn ca hỏng giữ bốn mã riêng, `410`/`409`/`404` chứ không gộp về `400`.</li>
 * </ul>
 *
 * <h2>Chỉ `/accept` đọc `x-mock-role`</h2>
 * `/lookup` và `/decline` phục vụ người **chưa có tài khoản**, nên vai của
 * người gọi không có nghĩa gì ở đó — mã mời là chứng chỉ duy nhất. `/accept`
 * thì ngược lại: nó là endpoint <b>duy nhất</b> của nhóm mà vai người gọi
 * quyết định hình dạng phản hồi — "khách" đóng vai "không mang token", và ở
 * ĐÓ (không phải ở một mã `401`) là nơi luật "phải khai `loginId`" chạy, đúng
 * như `InvitationService.acceptAsNewAccount` phía máy chủ.
 */

/**
 * Chuẩn hoá mã — **rộng tay**, theo `RedeemInvitationRequest` của contract.
 *
 * Phiếu giấy A6 in `K7M2Q-D9HFX`, tin nhắn mang `/moi/K7M2QD9HFX`, và một cụ
 * đọc từ tờ phiếu rồi gõ tay rất dễ ra `k7m2q d9hfx` — hoặc gõ chữ O thay cho
 * số 0 và chữ I thay cho số 1. Bảng chữ **Crockford Base32** đã loại hẳn
 * `I L O U` khỏi tập ký tự sinh mã, nên ba phép dịch dưới đây **không thể** làm
 * hai mã khác nhau va vào nhau: ký tự đích và ký tự nguồn không bao giờ cùng
 * xuất hiện trong một mã thật.
 *
 * Mỗi lần gõ sai là một cú điện thoại cho Trưởng chi. Luật này sống ở **máy
 * chủ** để mọi client cùng theo một luật.
 */
function normalizeCode(raw: unknown): string {
  return String(raw ?? "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .replace(/O/g, "0")
    .replace(/[IL]/g, "1");
}

function problem(
  status: number,
  code: ProblemCode,
  title: string,
  instance: string,
  detail?: string | Record<string, string>,
  headers?: Record<string, string>
): HttpResponse<Problem> {
  // `detail` chồng lấp với vị trí cũ của `headers` (mọi lời gọi trước đợt này
  // truyền một `Record<string,string>` ở đúng chỗ ấy để đặt `Retry-After`).
  // Phân biệt bằng KIỂU thay vì đổi vị trí mọi lời gọi cũ: chuỗi là `detail`,
  // object là `headers`.
  const detailText = typeof detail === "string" ? detail : undefined;
  const headersArg = typeof detail === "object" ? detail : headers;
  const body: Problem = {
    type: "about:blank",
    title,
    status,
    code,
    instance,
    ...(detailText !== undefined ? { detail: detailText } : {}),
  };
  return HttpResponse.json(body, { status, headers: headersArg });
}

/**
 * Năm mã cố định của bộ dữ liệu giả.
 *
 * Tất cả đều **hợp lệ theo Crockford Base32** (không có `I L O U`), vì một
 * fixture mà bộ sinh mã thật không bao giờ tạo ra được là một fixture nói dối
 * về hình dạng dữ liệu. `MOCK_INVITATION_CODE` theo đúng khuôn 10 ký tự chia
 * hai nhóm của contract.
 */
export const MOCK_INVITATION_CODE = "K7M2QD9HFX";

/** Mã đã quá hạn. */
export const MOCK_INVITATION_CODE_EXPIRED = "HETHAN";

/** Mã đã có người nhận lời mời bằng nó. */
export const MOCK_INVITATION_CODE_USED = "DADNG5";

/** Mã bị chính người nhận bấm "Không phải tôi". */
export const MOCK_INVITATION_CODE_REVOKED = "DAHY2";

/** Mã làm máy chủ trả `500` — để thử nhánh "trục trặc đường truyền". */
export const MOCK_INVITATION_CODE_SERVER_DOWN = "MAY500";

/** Mã làm máy chủ trả `429` kèm `Retry-After`. */
export const MOCK_INVITATION_CODE_RATE_LIMITED = "NHANH429";

/**
 * **Không phải một mã mời — một `loginId`.** Định danh này đã thuộc về một tài
 * khoản đang tồn tại trong realm, nên `POST /invitations/accept` **không kèm
 * token** trả `422 IDENTITY_ALREADY_REGISTERED`.
 *
 * <h2>Vì sao lối nhận lời mời cũng có phép chặn này</h2>
 * Cùng một lỗ hổng, cùng một bản vá: `/accept` không đòi đăng nhập, nên chuỗi
 * người gọi tự gõ **không chứng minh** họ sở hữu định danh ấy. Nếu định danh đã
 * có chủ thì mọi bước sau — đúc liên kết đặt mật khẩu, làm tươi hồ sơ, và ở lối
 * này còn là **ghép vào một nhân khẩu trong phả** — là thao tác trên tài sản
 * của người khác. Một tài khoản dựng qua đăng nhập Google **không có mật khẩu**,
 * nên "chưa có mật khẩu" không hề có nghĩa là "chưa có chủ"; đó chính là ca mà
 * lỗ hổng cũ khai thác.
 */
export const MOCK_INVITATION_LOGIN_ID_TAKEN = "da-co-tai-khoan@ho-nguyen.vn";

/**
 * Lời mời mẫu, chép theo hình 5 của bản thiết kế **và theo contract**.
 *
 * Người mời là `p-103` (Nguyễn Văn Cẩn) — chính là Trưởng chi giả lập của
 * `src/mocks/identity.ts`, nên câu chuyện trong bộ giả lập khớp nhau.
 *
 * Hai trường bản thiết kế muốn mà contract Giai đoạn 1 **không có**, và vì thế
 * cũng không có ở đây: `invitee.personId` và `invitee.relationToInviter`. Để
 * chúng trong mock là dựng màn hình trên một API không tồn tại — đúng cái bẫy
 * màn danh bạ đã sập một lần.
 */
const PREVIEW: InvitationPreviewDto = {
  clanName: "Dòng họ Nguyễn — Đại Lan",
  inviter: {
    // Tên TRẦN. Kính ngữ phụ thuộc quan hệ họ hàng, nên không ai — kể cả máy
    // chủ ở endpoint này — được thêm vào.
    displayName: "Nguyễn Văn Cẩn",
    // Chức danh dòng tộc, không phải vai kỹ thuật BRANCH_HEAD.
    clanTitle: "Trưởng Chi Nhất",
  },
  invitee: {
    displayName: "Trần Thị Lan",
    branch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
    generation: 5,
  },
  // Cố định thay vì `Date.now() + 7 ngày`: một mốc trôi theo đồng hồ làm ảnh
  // chụp màn hình và test khác nhau mỗi lần chạy mà chẳng kiểm thêm được gì.
  expiresAt: "2026-09-26T17:00:00Z",
};

/**
 * Phản hồi của `/accept`. **Không có `setPasswordUrl`** — backend hôm nay không
 * phát được token hành động của Keycloak, và trả sẵn một trường luôn rỗng còn
 * tệ hơn không có nó.
 */
const ACCEPTED: InvitationAcceptedDto = {
  appUserId: "u-moi-nhan",
  personId: "p-140",
  status: "ACTIVE",
};

/** Mã đã tiêu trong phiên chạy này — để `accept` hai lần cho ra `409` thật. */
const consumed = new Set<string>();

/** Mã đã bị "Không phải tôi" huỷ trong phiên chạy này. */
const revoked = new Set<string>();

/** Đưa bộ giả lập về trạng thái đầu. Dùng giữa các ca kiểm. */
export function resetInvitationMockState(): void {
  consumed.clear();
  revoked.clear();
}

/**
 * Tra một mã → `null` nếu mở được, hoặc phản hồi lỗi tương ứng.
 *
 * Thứ tự kiểm là **có nghĩa**: đã huỷ / đã dùng được trả lời trước hết hạn, vì
 * một mã đã bị huỷ thì việc nó còn hạn hay không không giúp gì người đọc.
 *
 * Mã **sai định dạng** trả lời y hệt mã đúng định dạng mà không tồn tại: khác
 * nhau ở đó thì kẻ dò đọc được độ dài và bảng chữ của mã mà không cần đoán
 * trúng lần nào.
 */
function rejectionFor(code: string, instance: string): HttpResponse<Problem> | null {
  if (revoked.has(code) || code === MOCK_INVITATION_CODE_REVOKED) {
    return problem(410, "INVITATION_REVOKED", "Lời mời đã bị thu hồi", instance);
  }
  if (consumed.has(code) || code === MOCK_INVITATION_CODE_USED) {
    return problem(409, "INVITATION_ALREADY_USED", "Lời mời đã được dùng", instance);
  }
  if (code === MOCK_INVITATION_CODE_EXPIRED) {
    // 410 Gone: tài nguyên ĐÃ tồn tại và thử lại không bao giờ đổi kết quả.
    return problem(410, "INVITATION_EXPIRED", "Lời mời đã hết hạn", instance);
  }
  if (code === MOCK_INVITATION_CODE_RATE_LIMITED) {
    return problem(429, "RATE_LIMITED", "Vượt hạn mức gọi", instance, {
      "Retry-After": "120",
    });
  }
  if (code === MOCK_INVITATION_CODE_SERVER_DOWN) {
    return problem(500, "INTERNAL_ERROR", "Lỗi máy chủ", instance);
  }
  if (code !== MOCK_INVITATION_CODE && code !== MOCK_INVITATION_CODE_NEW_ACCOUNT) {
    // Dùng lại `NOT_FOUND` đã có trong enum thay vì bịa `INVITATION_NOT_FOUND`.
    return problem(404, "NOT_FOUND", "Không tìm thấy lời mời", instance);
  }
  return null;
}

export const invitationHandlers = [
  http.post(`${API_BASE_URL}/api/v1/invitations/lookup`, async ({ request }) => {
    const instance = "/api/v1/invitations/lookup";
    const body = (await request.json().catch(() => ({}))) as { code?: unknown };
    const code = normalizeCode(body.code);
    if (code.length === 0) {
      return problem(400, "VALIDATION_FAILED", "Thiếu mã mời", instance);
    }
    return rejectionFor(code, instance) ?? HttpResponse.json(PREVIEW);
  }),

  http.post(`${API_BASE_URL}/api/v1/invitations/accept`, async ({ request }) => {
    const instance = "/api/v1/invitations/accept";
    const body = (await request.json().catch(() => ({}))) as {
      code?: unknown;
      loginId?: unknown;
      email?: unknown;
    };
    const code = normalizeCode(body.code);
    const role = resolveMockRole(request);

    const rejection = rejectionFor(code, instance);
    if (rejection) return rejection;

    // `POST /invitations/accept` KHÔNG đòi token — chính mã mời là chứng chỉ.
    // Đòi token ở đây là đóng cửa với đúng người mà cả luồng sinh ra để phục vụ:
    // người CHƯA có tài khoản nào để đăng nhập bằng.
    if (role === "guest") {
      // Không mang token ⇒ máy chủ đọc `loginId` (tên cũ `email`, đánh dấu bỏ
      // dần) để tìm-hoặc-tạo tài khoản Keycloak — `IdentityEnroller.readIdentifier`
      // / `LoginIdentifier.of`. Thiếu nó là `422 VALIDATION_FAILED`, và câu chữ
      // dưới đây CHÉP NGUYÊN VĂN thông điệp của lớp Java ấy (không dấu, đúng quy
      // ước ghi log nội bộ của nó) — đây chính là ca `isInvitationValidationError`
      // ở `invitation.ts` sinh ra để bắt.
      const loginId =
        typeof body.loginId === "string" && body.loginId.trim().length > 0
          ? body.loginId.trim()
          : typeof body.email === "string"
            ? body.email.trim()
            : "";
      if (loginId.length === 0) {
        return problem(
          422,
          "VALIDATION_FAILED",
          "Dữ liệu gửi lên không hợp lệ",
          instance,
          "Phai cho biet dia chi thu dien tu hoac so dien thoai de lap tai khoan"
        );
      }

      // ĐỊNH DANH ĐÃ CÓ CHỦ THÌ DỪNG LẠI — TRƯỚC khi tiêu mã, trước khi ghép
      // vào nhân khẩu nào. Xem {@link MOCK_INVITATION_LOGIN_ID_TAKEN}.
      //
      // `detail` CHÉP NGUYÊN VĂN câu của `InvitationService`, kể cả việc nó là
      // tiếng Việt không dấu: contract dặn giao diện in câu này ra, nên một bản
      // "đã bỏ dấu giúp" ở đây sẽ giấu mất thứ màn hình thật đang hiện.
      if (loginId === MOCK_INVITATION_LOGIN_ID_TAKEN) {
        return problem(
          422,
          "IDENTITY_ALREADY_REGISTERED",
          "Định danh này đã có tài khoản",
          instance,
          "Dia chi nay co the da duoc dung de dang nhap truoc day, ke ca bang Google."
            + " Hay dang nhap truoc roi nhap lai ma moi. Neu ban khong dang nhap duoc, hay dua"
            + " ma moi nay cho Truong chi de duoc ho tro."
        );
      }

      // `MOCK_INVITATION_CODE_NEW_ACCOUNT` dựng lại đúng ca người CHƯA có tài
      // khoản Keycloak nào — nhận được liên kết đặt mật khẩu, bất kể `loginId`
      // gõ vào là gì, để ca kiểm khỏi phải biết trước bộ sinh danh tính giả lập
      // nghĩ gì.
      //
      // MỌI MÃ KHÁC ⇒ 422. Định danh gõ vào trùng một tài khoản ĐÃ có từ trước,
      // và sau bản vá lỗ hổng chiếm tài khoản thì máy chủ thật **từ chối** ca ấy
      // — nó không còn trả `200` kèm một màn "đăng nhập như thường lệ" nữa.
      //
      // Bản đầu của tệp này để nhánh mặc định trả `200`, và đó chính là mẫu hình
      // đã cắn dự án này BỐN LẦN: bộ giả lập mô phỏng một API **dễ hơn** API
      // thật. Một màn hình xanh trên bộ giả lập rồi gãy khi gặp máy chủ là thứ
      // đắt hơn nhiều một bài kiểm đỏ hôm nay.
      if (code !== MOCK_INVITATION_CODE_NEW_ACCOUNT) {
        return problem(
          422,
          "IDENTITY_ALREADY_REGISTERED",
          "Định danh này đã có tài khoản",
          instance,
          "Dia chi nay co the da duoc dung de dang nhap truoc day, ke ca bang Google."
            + " Hay dang nhap truoc roi nhap lai ma moi. Neu ban khong dang nhap duoc, hay dua"
            + " ma moi nay cho Truong chi de duoc ho tro."
        );
      }

      consumed.add(code);

      {
        return HttpResponse.json({
          ...ACCEPTED,
          appUserId: "u-vua-lap",
          // Đường dẫn TƯƠNG ĐỐI trong chính ứng dụng này — khuôn mặc định của
          // backend. Giao diện phải đi tới URL MÁY CHỦ TRẢ VỀ chứ không tự ghép.
          setPasswordUrl: `/vi/dat-mat-khau?token=${MOCK_SET_PASSWORD_TOKEN}`,
          setPasswordExpiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
        });
      }

      return HttpResponse.json(ACCEPTED);
    }

    // Có token: danh tính lấy từ đó, `loginId` bị bỏ qua — không phát liên kết
    // đặt mật khẩu, vì họ vừa đăng nhập được thì hiển nhiên đã có cách đăng
    // nhập rồi.
    //
    // Tiêu mã NGAY: lần gọi thứ hai với cùng mã phải ra `409`, kể cả khi người
    // dùng bấm hai lần vì mạng chậm. Đó là bất biến "mã dùng một lần"; mock
    // không giữ nó thì giao diện sẽ được dựng với một giả định sai.
    consumed.add(code);
    return HttpResponse.json(ACCEPTED);
  }),

  http.post(`${API_BASE_URL}/api/v1/invitations/decline`, async ({ request }) => {
    const instance = "/api/v1/invitations/decline";
    const body = (await request.json().catch(() => ({}))) as { code?: unknown };
    const code = normalizeCode(body.code);
    const rejection = rejectionFor(code, instance);
    if (rejection) return rejection;
    revoked.add(code);
    return new HttpResponse(null, { status: 204 });
  }),
];

/* ══════════════════════════════════════════════════════════════════════════
   ĐẶT MẬT KHẨU QUA LIÊN KẾT MỘT LẦN — `POST /invitations/set-password`
   ══════════════════════════════════════════════════════════════════════════

   Lối gọi này KHÔNG đòi token đăng nhập (`security: []` trong contract) và
   KHÔNG đọc `x-mock-role`: người gọi chính là người chưa có mật khẩu nào để
   đăng nhập bằng. Chứng chỉ duy nhất là `token` trong thân yêu cầu.

   Ba chỗ bộ giả lập này cố ý khó đúng như API thật:

     1. **Mật khẩu do "realm" phán quyết, không do token.** `422` phát sinh từ
        giá trị `newPassword`, không từ một mã fixture riêng. Gắn `422` vào một
        token cố định sẽ dựng nên một giao diện tin rằng "liên kết này thì luôn
        hỏng", trong khi sự thật là "mật khẩu này thì chưa đạt" — và hai điều ấy
        dẫn tới hai màn hình khác hẳn nhau.

     2. **Tính một lần là thật.** Đặt xong thì token vào `dungRoi`, và lần gọi
        thứ hai trả `410` y như API thật — trong khi mật khẩu vừa đặt vẫn dùng
        được. Đó là bất biến chống "đổi mật khẩu của người khác"; một mock bỏ
        qua nó sẽ để giao diện được dựng trên một giả định sai.

     3. **Token lạ trả `410`, KHÔNG trả `404`.** Sai chữ ký, hết hạn và đã dùng
        là cùng một câu trả lời — khác nhau ở đó thì kẻ dò đọc được token nào
        từng tồn tại.

   `/accept` phía trên vẫn giữ nguyên hình dạng cũ (đòi token, không phát
   `setPasswordUrl`): nó thuộc mạch việc khác và có bộ kiểm riêng ghim đúng hành
   vi ấy. Để đi trọn luồng dưới `npm run dev:mock` mà không phải đụng vào nó,
   dùng MÃ MỜI RIÊNG `MOCK_INVITATION_CODE_NEW_ACCOUNT` ở dưới — mã ấy dựng lại
   đúng ca thật: người CHƯA có tài khoản, nên `/accept` không đòi token và trả
   về `setPasswordUrl`. */

/**
 * Hai mã lỗi của lối gọi này **đã có trong `contracts/openapi.yaml`** nhưng
 * `src/types/api.ts` chưa bắt kịp — tệp ấy thuộc mạch việc khác và không sửa
 * từ đây. Ép kiểu ở đúng hai dòng, ngay cạnh lý do, thay vì rải `as` khắp nơi;
 * khi enum bắt kịp contract thì xoá hai dòng này và **không** đổi gì khác.
 */
const MA_LIEN_KET_HONG: ProblemCode = "SET_PASSWORD_LINK_INVALID";
const MA_KEYCLOAK_IM_LANG: ProblemCode = "IDENTITY_PROVIDER_UNAVAILABLE";

/** Token của liên kết còn hạn — đặt được đúng một lần. */
export const MOCK_SET_PASSWORD_TOKEN = "sp-lien-ket-con-han";

/** Liên kết quá ba mươi phút, hoặc sai chữ ký. `410`. */
export const MOCK_SET_PASSWORD_TOKEN_EXPIRED = "sp-lien-ket-het-han";

/** Backend không nói chuyện được với Keycloak. `503`. */
export const MOCK_SET_PASSWORD_TOKEN_PROVIDER_DOWN = "sp-keycloak-im-lang";

/** Vượt hạn mức gọi. `429` kèm `Retry-After`. */
export const MOCK_SET_PASSWORD_TOKEN_RATE_LIMITED = "sp-go-qua-nhanh";

/**
 * Mã mời của một người **chưa có tài khoản Keycloak** — ca mà cả luồng sinh ra
 * để phục vụ. `/accept` với mã này không đòi token và trả về `setPasswordUrl`
 * trỏ tới `/dat-mat-khau`, đúng khuôn địa chỉ mặc định của backend.
 */
export const MOCK_INVITATION_CODE_NEW_ACCOUNT = "M01TA1KH0AN";

/** Token đã đặt mật khẩu xong trong phiên chạy này. */
const dungRoi = new Set<string>();

/** Đưa bộ giả lập đặt mật khẩu về trạng thái đầu. Dùng giữa các ca kiểm. */
export function resetSetPasswordMockState(): void {
  dungRoi.clear();
}

/**
 * Chính sách mật khẩu **của realm**, dựng lại ở đây chỉ đủ để bộ giả lập trả
 * lời đúng hình dạng. Đây KHÔNG phải nơi giao diện được phép đọc luật: client
 * không kiểm mật khẩu trước, và bản sao thứ hai của một chính sách là bản sẽ
 * lệch. Chỗ duy nhất phán quyết là Keycloak.
 *
 * `detail` phải nói **phải sửa gì** — contract đòi vậy ở `422`, và nó là khác
 * biệt giữa một cụ sửa được mật khẩu trong mười giây và một cú điện thoại.
 */
function realmTuChoi(matKhau: string, tiengAnh: boolean): string | null {
  if (matKhau.length < 8) {
    return tiengAnh
      ? `A password needs at least 8 characters. The one you typed has ${matKhau.length}.`
      : `Mật khẩu cần ít nhất 8 ký tự. Mật khẩu ông/bà vừa gõ có ${matKhau.length} ký tự.`;
  }
  if (/^(?:\d+|(?:12345678|matkhau|password|qwertyui))$/i.test(matKhau)) {
    return tiengAnh
      ? "This password is too easy to guess. Try a short phrase you will remember, with at least one letter that is not a digit."
      : "Mật khẩu này quá dễ đoán. Xin chọn một cụm từ ông/bà nhớ được, có ít nhất một chữ cái chứ không chỉ toàn số.";
  }
  return null;
}

invitationHandlers.push(
  http.post(`${API_BASE_URL}/api/v1/invitations/set-password`, async ({ request }) => {
    const instance = "/api/v1/invitations/set-password";
    const body = (await request.json().catch(() => ({}))) as {
      token?: unknown;
      newPassword?: unknown;
    };

    // Token đi NGUYÊN VĂN — không chuẩn hoá, không viết hoa. Nó là chuỗi đã ký
    // do máy chủ đúc, không phải mã một cụ gõ tay từ phiếu giấy.
    const token = typeof body.token === "string" ? body.token : "";
    const matKhau = typeof body.newPassword === "string" ? body.newPassword : "";
    const tiengAnh = (request.headers.get("Accept-Language") ?? "vi").startsWith("en");

    // `400` là thân yêu cầu sai khuôn — một lỗi lập trình, KHÔNG phải thứ người
    // dùng sửa được bằng cách gõ lại. Vì thế nó không bao giờ được đọc thành
    // "mật khẩu chưa đạt".
    if (token.length === 0 || matKhau.length === 0) {
      return problem(400, "VALIDATION_FAILED", "Thiếu token hoặc mật khẩu", instance);
    }

    if (token === MOCK_SET_PASSWORD_TOKEN_RATE_LIMITED) {
      return problem(429, "RATE_LIMITED", "Vượt hạn mức gọi", instance, {
        "Retry-After": "120",
      });
    }

    // Hạ tầng hỏng được trả lời TRƯỚC khi xét mật khẩu: Keycloak im lặng thì
    // không ai phán quyết được mật khẩu, nên nói "mật khẩu chưa đạt" ở đây là
    // nói sai — và người dùng sẽ đi đổi một mật khẩu vốn không có vấn đề gì.
    if (token === MOCK_SET_PASSWORD_TOKEN_PROVIDER_DOWN) {
      return problem(
        503,
        MA_KEYCLOAK_IM_LANG,
        "Chưa nối được với hệ thống tài khoản",
        instance
      );
    }

    if (dungRoi.has(token) || token !== MOCK_SET_PASSWORD_TOKEN) {
      return problem(
        410,
        MA_LIEN_KET_HONG,
        "Liên kết đặt mật khẩu không còn dùng được",
        instance
      );
    }

    const tuChoi = realmTuChoi(matKhau, tiengAnh);
    if (tuChoi) {
      const than: Problem = {
        type: "about:blank",
        title: tiengAnh ? "Password not accepted" : "Mật khẩu chưa dùng được",
        status: 422,
        code: "VALIDATION_FAILED",
        detail: tuChoi,
        instance,
      };
      // KHÔNG tiêu token: mật khẩu bị từ chối thì người dùng phải gõ lại được
      // ngay. Tiêu ở đây là biến một lỗi chính tả thành một cú điện thoại.
      return HttpResponse.json(than, { status: 422 });
    }

    dungRoi.add(token);
    return new HttpResponse(null, { status: 204 });
  })
);
