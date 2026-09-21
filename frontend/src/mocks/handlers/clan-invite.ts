import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { normalizeClanInviteCode } from "@/lib/api/clan-invite";
import type {
  ClanInvitePreviewDto,
  ClanRegistrationDto,
} from "@/lib/api/clan-invite";
import type { Problem, ProblemCode } from "@/types/api";

/**
 * MSW cho **nửa của người cầm mã** trong nhóm `clan-invites`:
 * `POST /lookup` và `POST /register`.
 *
 * Nửa **quản trị** của cùng nhóm (`GET /`, `POST /`, `DELETE /{id}`) nằm ở
 * {@code handlers/membership-admin.ts} và **không** được nhắc lại ở đây: hai
 * nửa có hai chế độ xác thực khác hẳn nhau, và hai bộ giả lập cho cùng một
 * đường là hai bản sẽ lệch. Tệp này chỉ thêm hai đường mới, không giành đường
 * nào.
 *
 * <h2>Bộ giả lập này cố ý KHÓ đúng như API thật</h2>
 * <ul>
 *   <li>`/lookup` trả **đúng hai trường** — không bộ đếm, không nhãn mã, không
 *       tên người nào. Một mock hào phóng hơn API thật sẽ đẻ ra một màn hình in
 *       "mã này còn 3 lượt" cho một người chưa đăng nhập;</li>
 *   <li>`/register` trả `status: "PENDING"` và **không** `personId` — trạng
 *       thái đúng, không phải một bước dang dở;</li>
 *   <li>sáu ca hỏng giữ sáu mã riêng ở `410`/`409`/`404`/`429`/`503`/`422`,
 *       không gộp về `400`;</li>
 *   <li>một định danh **đã có chủ** bị từ chối ở nhánh **không token**
 *       (`422 IDENTITY_ALREADY_REGISTERED`) chứ không được trả một `200` rỗng
 *       liên kết — xem {@link MOCK_CLAN_LOGIN_ID_TAKEN};</li>
 *   <li>`/lookup` **không tiêu lượt**, `/register` **có** — và mã đã đầy trần
 *       thì `/lookup` vẫn mở được cho tới khi thử đăng ký. Đó là hành vi thật:
 *       {@code ClanInviteService.preview} và `register` gọi cùng
 *       `resolveUsable`, nhưng chỉ `register` gọi `redeemer.redeem`.</li>
 * </ul>
 *
 * <h2>Không đọc `x-mock-role`</h2>
 * Cả hai đường phục vụ người **chưa có tài khoản**, nên vai của người gọi không
 * có nghĩa gì ở đây — mã mời là chứng chỉ duy nhất. Cùng lý do với
 * `invitationHandlers./lookup`.
 */

function problem(
  status: number,
  // `ProblemCode`, KHÔNG `string`: enum đóng của contract nay đã mang đủ mọi mã
  // bộ giả lập này phát ra, nên một mã gõ sai phải là lỗi biên dịch ở đây thay
  // vì một nhánh giao diện lặng lẽ không bao giờ chạy.
  code: ProblemCode,
  title: string,
  instance: string,
  extra?: Record<string, unknown>,
  headers?: Record<string, string>
): HttpResponse<Problem> {
  const body = {
    type: "about:blank",
    title,
    status,
    code,
    instance,
    ...extra,
  } as Problem;
  return HttpResponse.json(body, { status, headers });
}

/* ══════════════════════════════════════════════════════════════════════════
   MÃ CỐ ĐỊNH CỦA BỘ DỮ LIỆU GIẢ

   Tất cả đều hợp lệ theo **Crockford Base32** (không có `I L O U`): một fixture
   mà bộ sinh mã thật không bao giờ tạo ra được là một fixture nói dối về hình
   dạng dữ liệu — và ở đây nó còn nguy hiểm hơn, vì phép chuẩn hoá `O→0` /
   `I,L→1` sẽ biến một mã fixture chứa `O` thành một mã khác hẳn.
   ══════════════════════════════════════════════════════════════════════════ */

/** Mã dùng được. Khuôn 10 ký tự chia hai nhóm, như mã thật in trên phiếu. */
export const MOCK_CLAN_CODE = "H0NG7V2KDA";

/** Quá hạn — chốt "có hạn dùng" đang làm việc của nó. `410`. */
export const MOCK_CLAN_CODE_EXPIRED = "HETHAN9922";

/** Hội đồng đã thu hồi — chốt "thu hồi được". `410`. */
export const MOCK_CLAN_CODE_REVOKED = "TH0H0134XY";

/** Đầy trần lượt dùng — chốt "đếm lượt dùng". `409`. */
export const MOCK_CLAN_CODE_EXHAUSTED = "HETS0TDA55";

/** Vượt giới hạn tần suất — chốt thứ tư. `429` kèm `Retry-After`. */
export const MOCK_CLAN_CODE_RATE_LIMITED = "NHANH42999";

/** Keycloak im lặng: `/lookup` mở được, `/register` trả `503`. Mã CHƯA bị tiêu. */
export const MOCK_CLAN_CODE_PROVIDER_DOWN = "KCD0WN5033";

/** Máy chủ 500 — nhánh "trục trặc đường truyền", không phải "mã sai". */
export const MOCK_CLAN_CODE_SERVER_DOWN = "MAY500NG27";

/**
 * Thư điện tử **đã thuộc về một tài khoản đang tồn tại** trong realm.
 *
 * `/register` **không kèm token** với địa chỉ này trả `422
 * IDENTITY_ALREADY_REGISTERED`. Đây là chỗ vừa bịt một lỗ hổng chiếm tài khoản:
 * bản trước của máy chủ trả `200` kèm một **liên kết đặt mật khẩu dùng được**
 * cho tài khoản của người khác — ai cầm mã dòng họ (tức **cả họ**) mà đoán
 * trúng thư điện tử của một thành viên cũ là đổi được mật khẩu của người ta.
 *
 * <p>Bộ giả lập này từng dựng đúng cái hành vi cũ ấy dưới tên
 * {@code MOCK_CLAN_EMAIL_HAS_PASSWORD} — trả `200` không kèm liên kết. Nó đã
 * hết đúng: ở nhánh không token, máy chủ nay **dừng lại trước cả bước đúc liên
 * kết**, nên một `200` ở đây là một mock nói dối về API thật.</p>
 *
 * <p>Với lượt gọi **có** `Authorization`, cùng địa chỉ này vẫn trả `200` không
 * kèm liên kết — đó là nhánh "đã có token", nơi danh tính do Keycloak chứng
 * nhận chứ không do người gọi tự khai.</p>
 */
export const MOCK_CLAN_LOGIN_ID_TAKEN = "da-co-tai-khoan@ho-nguyen.vn";

/**
 * Xem trước. **Hai trường** — xem javadoc đầu tệp.
 *
 * `expiresAt` cố định thay vì `Date.now() + n ngày`: một mốc trôi theo đồng hồ
 * làm ảnh chụp màn hình và test khác nhau mỗi lần chạy mà chẳng kiểm thêm được
 * gì.
 */
const PREVIEW: ClanInvitePreviewDto = {
  clanName: "Dòng họ Nguyễn — Đại Lan",
  expiresAt: "2026-10-21T17:00:00Z",
};

/**
 * Trần lượt dùng của mã giả lập.
 *
 * **Lớn hơn 1, và đó là điểm quan trọng nhất của bộ giả lập này.** Mã dòng họ
 * *dùng nhiều lần* — đó là cả lý do nó tồn tại tách khỏi mã cá nhân (design 07
 * §1.1: "đừng trộn làm một"). Một mock tiêu mã sau lượt đầu sẽ dựng nên một
 * giao diện tin rằng đăng ký xong là mã chết, và giao diện ấy sẽ sai với dòng
 * họ thật ngay ở người thứ hai.
 */
const TRAN_LUOT = 3;

/** Bộ đếm lượt dùng trong phiên chạy này — chốt quan trọng nhất của §1.2. */
const luotDaDung = new Map<string, number>();

/** Đưa bộ giả lập về trạng thái đầu. Dùng giữa các ca kiểm. */
export function resetClanInviteMockState(): void {
  luotDaDung.clear();
}

/**
 * Tra một mã → `null` nếu mở được, hoặc phản hồi lỗi tương ứng.
 *
 * Thứ tự kiểm là **có nghĩa**: đã thu hồi được trả lời trước hết hạn, vì một mã
 * bị đóng chủ động thì việc nó còn hạn hay không không giúp gì người đọc.
 *
 * Mã **sai định dạng** trả lời y hệt mã đúng định dạng mà không tồn tại — khác
 * nhau ở đó thì kẻ dò đọc được độ dài và bảng chữ của mã mà không cần đoán
 * trúng lần nào.
 */
function rejectionFor(
  code: string,
  instance: string,
  dangDangKy: boolean
): HttpResponse<Problem> | null {
  if (code === MOCK_CLAN_CODE_RATE_LIMITED) {
    // 429 đứng TRƯỚC mọi phép tra: giới hạn tần suất chặn ở cổng, trước khi máy
    // chủ chịu tốn một lượt đọc CSDL cho một người đang dò.
    return problem(429, "RATE_LIMITED", "Thử quá nhiều lần", instance, {
      retryAfterSeconds: 120,
    }, { "Retry-After": "120" });
  }
  if (code === MOCK_CLAN_CODE_REVOKED) {
    // 410 Gone: mã từng tồn tại và nay mất vĩnh viễn; thử lại không đổi kết quả.
    return problem(410, "INVITATION_REVOKED", "Mã mời dòng họ đã bị thu hồi", instance);
  }
  if (code === MOCK_CLAN_CODE_EXPIRED) {
    return problem(410, "INVITATION_EXPIRED", "Mã mời dòng họ đã hết hạn", instance);
  }
  if (code === MOCK_CLAN_CODE_EXHAUSTED || (luotDaDung.get(code) ?? 0) >= TRAN_LUOT) {
    // 409 Conflict, KHÔNG 410: trạng thái này đổi được — Hội đồng nâng trần là
    // mã dùng lại được. Đó chính là khác biệt giữa hai mã trạng thái.
    return problem(409, "CLAN_INVITE_EXHAUSTED", "Mã mời dòng họ đã hết lượt", instance);
  }
  if (code === MOCK_CLAN_CODE_SERVER_DOWN) {
    return problem(500, "INTERNAL_ERROR", "Lỗi máy chủ", instance);
  }
  if (code === MOCK_CLAN_CODE_PROVIDER_DOWN) {
    // Chỉ hỏng ở BƯỚC LẬP TÀI KHOẢN. `/lookup` mở được — đúng hành vi thật, vì
    // cổng danh tính chỉ được gọi tới ở `register`.
    return dangDangKy
      ? problem(503, "IDENTITY_PROVIDER_UNAVAILABLE", "Chưa lập được tài khoản", instance)
      : null;
  }
  if (code !== MOCK_CLAN_CODE) {
    // Dùng lại `NOT_FOUND` đã có trong enum thay vì bịa một mã thứ sáu.
    return problem(404, "NOT_FOUND", "Không tìm thấy mã mời dòng họ", instance);
  }
  return null;
}

export const clanInviteHandlers = [
  http.post(`${API_BASE_URL}/api/v1/clan-invites/lookup`, async ({ request }) => {
    const instance = "/api/v1/clan-invites/lookup";
    const body = (await request.json().catch(() => ({}))) as { code?: unknown };
    const code = normalizeClanInviteCode(String(body.code ?? ""));
    if (code.length === 0) {
      return problem(400, "VALIDATION_FAILED", "Thiếu mã mời", instance);
    }
    // KHÔNG tiêu lượt ở đây: `preview` là chỉ đọc. Một mock tiêu lượt ở bước xem
    // trước sẽ dạy giao diện rằng kiểm mã là đắt, và nó sẽ bỏ bước kiểm.
    return rejectionFor(code, instance, false) ?? HttpResponse.json(PREVIEW);
  }),

  http.post(`${API_BASE_URL}/api/v1/clan-invites/register`, async ({ request }) => {
    const instance = "/api/v1/clan-invites/register";
    // Có token hay không là thứ QUYẾT ĐỊNH nhánh nào chạy, y như máy chủ thật:
    // `CurrentUserProvider.current()` rỗng thì danh tính là do người gọi tự
    // khai, và chỉ nhánh ấy mới có phép chặn "định danh đã có chủ".
    const coToken = (request.headers.get("Authorization") ?? "").length > 0;
    const body = (await request.json().catch(() => ({}))) as {
      code?: unknown;
      loginId?: unknown;
      email?: unknown;
      displayName?: unknown;
    };
    const code = normalizeClanInviteCode(String(body.code ?? ""));
    // `loginId` là tên hiện hành; `email` là tên cũ, contract vẫn nhận và đã
    // đánh dấu bỏ dần. Bộ giả lập nhận cả hai vì máy chủ nhận cả hai.
    const dinhDanh = String(body.loginId ?? body.email ?? "").trim();

    // Mã được tra TRƯỚC định danh, và thứ tự ấy có nghĩa: một mã hỏng phải trả
    // lời là mã hỏng, chứ không phải là "thiếu định danh".
    const rejection = rejectionFor(code, instance, true);
    if (rejection) return rejection;

    if (dinhDanh.length === 0) {
      return problem(400, "VALIDATION_FAILED", "Thiếu định danh đăng nhập", instance, {
        detail: "Phải cho biết thư điện tử hoặc số điện thoại để lập tài khoản.",
      });
    }
    // SỐ ĐIỆN THOẠI ĐĂNG KÝ ĐƯỢC. Chỉ chặn thứ đọc không ra thành email lẫn số
    // máy — đúng một câu của contract: "Đọc không ra thành email lẫn số máy →
    // `422 VALIDATION_FAILED`".
    const laSoVietNam = /^(?:\+?84|0)\d{9,10}$/.test(dinhDanh.replace(/[\s.\-()]/g, ""));
    const laThuDienTu = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(dinhDanh);
    if (!laSoVietNam && !laThuDienTu) {
      // Câu `detail` nói PHẢI SỬA GÌ, không nói "sai khuôn".
      return problem(422, "VALIDATION_FAILED", "Định danh không đọc được", instance, {
        detail:
          "Chưa đúng khuôn của thư điện tử lẫn số điện thoại. Ví dụ: ten.ban@gmail.com hoặc 0912345678",
      });
    }

    // ĐỊNH DANH ĐÃ CÓ CHỦ THÌ DỪNG LẠI — trước khi tiêu lượt, trước khi đúc bất
    // cứ liên kết nào. Chuỗi người gọi tự gõ không chứng minh họ sở hữu địa chỉ
    // ấy, nên mọi bước tiếp theo sẽ là thao tác trên tài sản của người khác.
    //
    // Máy chủ thật tính lần từ chối này vào giới hạn tần suất như một lần thất
    // bại; bộ giả lập không dựng lại bộ đếm ấy, nhưng giao diện vẫn phải cư xử
    // như thể nó có — tức KHÔNG tự thử lại.
    if (dinhDanh === MOCK_CLAN_LOGIN_ID_TAKEN) {
      return problem(422, "IDENTITY_ALREADY_REGISTERED",
        "Định danh này đã có tài khoản", instance, {
          // CHÉP NGUYÊN VĂN câu của `ClanInviteService#register`, kể cả việc nó
          // là tiếng Việt KHÔNG DẤU. Contract dặn giao diện in câu này ra, nên
          // bộ giả lập phải trình đúng thứ máy chủ trình — một bản "đã bỏ dấu
          // giúp" ở đây sẽ giấu mất việc màn hình thật đang hiện chữ không dấu.
          detail:
            "Dinh danh nay da co tai khoan trong he thong. Hay dang nhap roi nhap lai ma"
            + " dong ho; neu ban khong dang nhap duoc, hay bao Truong chi hoac Hoi dong Toc bieu.",
        });
    }

    // Tiêu MỘT lượt, và chỉ ở đây. Bộ đếm là chốt quan trọng nhất của §1.2;
    // một mock quên tiêu lượt sẽ để giao diện được dựng trên giả định rằng mã
    // dùng mãi được — đúng cái lỗ hổng mà màn này sinh ra để bịt.
    luotDaDung.set(code, (luotDaDung.get(code) ?? 0) + 1);

    const registration: ClanRegistrationDto = {
      appUserId: "u-vua-dang-ky",
      status: "PENDING",
      clanInviteId: "ci-dang-mo",
    };

    if (coToken) {
      // Nhánh "đã có token": danh tính lấy từ token, `loginId` bị bỏ qua, và
      // KHÔNG phát liên kết đặt mật khẩu — họ vừa đăng nhập được thì hiển nhiên
      // đã có cách đăng nhập.
      return HttpResponse.json(registration);
    }

    return HttpResponse.json({
      ...registration,
      // Đường dẫn TƯƠNG ĐỐI trong chính ứng dụng này — khuôn mặc định của
      // backend. Giao diện phải đi tới URL MÁY CHỦ TRẢ VỀ chứ không tự ghép.
      setPasswordUrl: "/vi/dat-mat-khau?token=sp-lien-ket-con-han",
      setPasswordExpiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    } satisfies ClanRegistrationDto);
  }),
];
