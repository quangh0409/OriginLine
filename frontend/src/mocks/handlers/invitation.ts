import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
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
 *   <li>`/accept` <b>đòi token</b> và <b>không</b> trả `setPasswordUrl` — đúng
 *       khoảng trống thật của backend hôm nay;</li>
 *   <li>bốn ca hỏng giữ bốn mã riêng, `410`/`409`/`404` chứ không gộp về `400`.</li>
 * </ul>
 *
 * <h2>Chỉ `/accept` đọc `x-mock-role`</h2>
 * `/lookup` và `/decline` phục vụ người **chưa có tài khoản**, nên vai của
 * người gọi không có nghĩa gì ở đó — mã mời là chứng chỉ duy nhất. `/accept`
 * thì ngược lại: nó là endpoint duy nhất của nhóm đòi token, nên ở đây "khách"
 * đóng vai "không mang token" và phải nhận `401`.
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
  headers?: Record<string, string>
): HttpResponse<Problem> {
  const body: Problem = { type: "about:blank", title, status, code, instance };
  return HttpResponse.json(body, { status, headers });
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
    const body = (await request.json().catch(() => ({}))) as { code?: unknown };
    const code = normalizeCode(body.code);

    // `POST /invitations/accept` KHÔNG đòi token — chính mã mời là chứng chỉ.
    // Đòi token ở đây là đóng cửa với đúng người mà cả luồng sinh ra để phục vụ:
    // người CHƯA có tài khoản nào để đăng nhập bằng.
    //
    // Hai ca, phân biệt bằng việc CÓ hay KHÔNG có `setPasswordUrl`:
    //
    //   · người chưa có tài khoản (`MOCK_INVITATION_CODE_NEW_ACCOUNT`) — máy chủ
    //     tạo tài khoản Keycloak rồi phát liên kết đặt mật khẩu;
    //   · người đã có tài khoản — máy chủ **cố ý không phát** liên kết, vì nếu
    //     phát thì ai cầm được một mã mời cộng với đoán đúng email của một thành
    //     viên cũ sẽ đổi được mật khẩu của người ta. Giao diện đưa họ tới màn
    //     đăng nhập.
    const nguoiChuaCoTaiKhoan = code === MOCK_INVITATION_CODE_NEW_ACCOUNT;

    const rejection = rejectionFor(code, instance);
    if (rejection) return rejection;

    if (nguoiChuaCoTaiKhoan) {
      consumed.add(code);
      return HttpResponse.json({
        ...ACCEPTED,
        appUserId: "u-vua-lap",
        // Đường dẫn TƯƠNG ĐỐI trong chính ứng dụng này — khuôn mặc định của
        // backend. Giao diện phải đi tới URL MÁY CHỦ TRẢ VỀ chứ không tự ghép.
        setPasswordUrl: `/vi/dat-mat-khau?token=${MOCK_SET_PASSWORD_TOKEN}`,
        setPasswordExpiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
      });
    }

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
