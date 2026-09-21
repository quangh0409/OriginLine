import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import type { ClaimView, MyClaimsDto } from "@/lib/api/claim";
import { ALL_PERSONS } from "@/mocks/data";
import { identityOf } from "@/mocks/identity";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import type { Problem, ProblemCode } from "@/types/api";
import { resolveMockRole } from "./role";

/**
 * MSW cho **đơn tự nhận mình trong phả** — nửa *người gửi* của
 * `/api/v1/person-claims`.
 *
 * <h2>Bộ giả lập này chép theo CONTRACT, không theo ý muốn của màn hình</h2>
 * `contracts/openapi.yaml`, nhóm `person-claims`. Đọc javadoc đầu
 * `src/lib/api/claim.ts` trước.
 *
 * Bốn chỗ cố ý khó đúng như máy chủ thật khó:
 * <ul>
 *   <li><b>"Ô ấy đã có tài khoản" chỉ lộ ra ở bước GỬI, sau khi đã tiêu một
 *       lượt</b>, và gộp chung câu trả lời với "đã khuất" và "nhân khẩu đã xoá
 *       mềm" vào **một** mã `CLAIM_TARGET_UNAVAILABLE`. Không có lối hỏi trước
 *       nào — vì một lối hỏi trước chính là công cụ dò xem ai đã vào hệ thống.</li>
 *   <li><b>Ba lý do khác nhau cùng trả `CLAIM_TARGET_UNAVAILABLE`.</b> Mock trả
 *       mã riêng cho từng ca sẽ dựng nên một giao diện phân nhánh được theo lý
 *       do — tức chính công cụ liệt kê mà contract gộp mã để chặn.</li>
 *   <li><b>Bộ đếm đếm đơn BỊ TỪ CHỐI của tài khoản</b>, không đếm theo ô được
 *       chọn, và <b>đơn tự rút không tính</b>.</li>
 *   <li><b>Đơn `NEW_PERSON` trả {@code personId: null}</b> và không thêm node
 *       nào vào đồ thị giả lập: "không tạo nhân khẩu lúc gửi đơn" là ràng buộc
 *       1 của §1.5, và ràng buộc {@code ck_person_claim_created} của V16 canh
 *       nó ở CSDL.</li>
 * </ul>
 *
 * <h2>Người gọi: "đã đăng nhập, CHƯA ghép nhân khẩu"</h2>
 * Đúng nhân vật mà {@code requireUnlinkedMember()} phục vụ, và bộ chuyển vai
 * dev (`handlers/role.ts`) <b>không</b> biểu diễn được trạng thái ấy — nó chỉ
 * biết bốn vai, ba trong số đó đã ghép sẵn nhân khẩu. Thay vì sửa một tệp dùng
 * chung của mạch việc khác, nhóm này giữ một mẩu trạng thái riêng
 * ({@link seedClaimMockState}) mặc định là chưa ghép. Vai vẫn được đọc đúng một
 * việc: `guest` = không mang token → `401`.
 */

// ============================================================================
// FIXTURE
// ============================================================================

/** Người còn sống, chưa ai nhận — lối đi thuận. Có thật trong đồ thị phả đồ. */
export const MOCK_CLAIM_TARGET_OK = "p-100";

/**
 * Ô **không gửi đơn được**, và bộ giả lập cố ý **không nói vì sao** — y như
 * {@code KHONG_NHAN_DUOC}.
 *
 * Chọn một ô **có thật trong đồ thị** là cố ý: ca này phải đi được trọn vẹn từ
 * phả đồ dưới `npm run dev:mock`, không chỉ bằng cách gõ tay một địa chỉ.
 */
export const MOCK_CLAIM_TARGET_UNAVAILABLE = "p-101";

/** Người đã khuất — giao diện chặn trước, từ `isAlive` (checklist §1.4). */
export const MOCK_CLAIM_TARGET_DECEASED = "p-001";

/** Người thân **không dùng được**: chưa gắn chi nào, nên không biết ai duyệt. */
export const MOCK_CLAIM_RELATIVE_UNUSABLE = "p-102";

/** Người thân dùng được — đã khuất, và đó là ca THƯỜNG GẶP NHẤT của lối này. */
export const MOCK_CLAIM_RELATIVE_OK = "p-010";

/**
 * Hai ô nữa rơi vào câu trả lời chung chung, **suy ra** từ `mocks/identity.ts`
 * thay vì chép tay: p-102 và p-103 là nhân khẩu của hai tài khoản giả lập, tức
 * đúng ca "ô ấy đã có tài khoản". Suy ra thay vì chép là thứ giữ cho bộ giả lập
 * không tự mâu thuẫn khi ai đó đổi danh tính giả lập.
 */
const DA_CO_TAI_KHOAN = new Set(
  [identityOf("member").personId, identityOf("branch-head").personId].filter(
    (id): id is string => id !== null
  )
);

/** `giapha.membership.claim.max-rejected` — mặc định của máy chủ. */
const TRAN_BI_TU_CHOI = 3;

/**
 * `app_user` của người gửi giả lập.
 *
 * `PersonClaim.requestedBy` là **bắt buộc** ở contract, và nó là khoá của *tài
 * khoản*, không phải của nhân khẩu — nhân vật của luồng này theo định nghĩa là
 * người chưa ghép nhân khẩu nào.
 */
const MOCK_CLAIM_REQUESTER = "u-tu-nhan-01";

// ============================================================================
// TRẠNG THÁI PHIÊN CHẠY
// ============================================================================

interface ClaimMockState {
  /** Nhân khẩu mà tài khoản đang gọi đã ghép. `null` = chưa ghép (mặc định). */
  linkedPersonId: string | null;
  claims: ClaimView[];
}

let state: ClaimMockState = { linkedPersonId: null, claims: [] };

/** Đưa bộ giả lập về trạng thái đầu. Dùng giữa các ca kiểm. */
export function resetClaimMockState(): void {
  state = { linkedPersonId: null, claims: [] };
}

/**
 * Gieo sẵn một trạng thái.
 *
 * Bốn ca của màn "tôi là ai trong phả" chia làm hai nhóm: hai ca nói về **ô
 * được chọn** (đã khuất · không nhận đơn) gieo được bằng cách chọn đúng fixture
 * ở trên; hai ca còn lại nói về **tài khoản đang gọi** (đã gửi đơn rồi · đã bị
 * từ chối) là trạng thái tích luỹ, nên phải gieo qua đây.
 */
export function seedClaimMockState(seed: Partial<ClaimMockState>): void {
  state = {
    linkedPersonId: seed.linkedPersonId ?? state.linkedPersonId,
    claims: seed.claims ? [...seed.claims] : state.claims,
  };
}

/** Một lá đơn đã bị từ chối, để dựng ca "gửi lại, còn mấy lần". */
export function rejectedClaimFixture(overrides: Partial<ClaimView> = {}): ClaimView {
  return {
    id: "claim-da-tu-choi",
    kind: "EXISTING",
    status: "REJECTED",
    personId: MOCK_CLAIM_TARGET_OK,
    targetBranchId: "b-chi1",
    targetBranch: { id: "b-chi1", name: "Chi Nhất", kind: "CHI", clanTitle: "Trưởng Chi Nhất" },
    requestedBy: MOCK_CLAIM_REQUESTER,
    phone: "0912345678",
    reviewNote:
      "Cháu ghi là con ông Cẩn, nhưng ông Cẩn chỉ có hai người con và cả hai đều đã có trong phả. Xin cháu gọi cho bác để nói rõ thêm.",
    reviewedAt: "2026-09-12T03:30:00Z",
    attemptNo: 1,
    createdAt: "2026-09-10T02:00:00Z",
    ...overrides,
  };
}

/** Một lá đơn đang chờ duyệt. */
export function pendingClaimFixture(overrides: Partial<ClaimView> = {}): ClaimView {
  return {
    id: "claim-dang-cho",
    kind: "EXISTING",
    status: "PENDING",
    personId: MOCK_CLAIM_TARGET_OK,
    targetBranchId: "b-chi1",
    targetBranch: { id: "b-chi1", name: "Chi Nhất", kind: "CHI", clanTitle: "Trưởng Chi Nhất" },
    requestedBy: MOCK_CLAIM_REQUESTER,
    phone: "0912345678",
    attemptNo: 1,
    createdAt: "2026-09-18T02:00:00Z",
    ...overrides,
  };
}

// ============================================================================
// TIỆN ÍCH
// ============================================================================

function problem(
  status: number,
  code: ProblemCode,
  title: string,
  instance: string,
  detail?: string
): HttpResponse<Problem> {
  const body: Problem = {
    type: "about:blank",
    title,
    status,
    code,
    instance,
    ...(detail !== undefined ? { detail } : {}),
  };
  return HttpResponse.json(body, { status });
}

/**
 * Thân yêu cầu SAI HÌNH DẠNG — `phone` quá 32 ký tự, `introduction` quá 2000 —
 * đúng hai ràng buộc `@Size` của `SubmitPersonClaimRequest` /
 * `SubmitNewPersonClaimRequest`. Trả `400` với `detail` **cụ thể**, y hệt
 * {@code DomainException} thật: khác với hai lời từ chối nghiệp vụ ở dưới,
 * đây không phải một luật của dòng họ mà là một lỗi hình dạng, và người gửi
 * sửa được ngay bằng cách gõ ngắn lại — không phải chờ Trưởng chi.
 *
 * Trả `null` khi thân hợp lệ, để hai handler bên dưới gọi được TRƯỚC mọi phép
 * kiểm nghiệp vụ khác — đúng thứ tự máy chủ thật kiểm (Bean Validation chạy
 * trước khi vào tới application service).
 */
function loiHinhDangThanYeuCau(
  phone: string,
  introduction: string,
  instance: string
): HttpResponse<Problem> | null {
  if (phone.length > 32) {
    return problem(
      400,
      "VALIDATION_FAILED",
      "Dữ liệu gửi lên không hợp lệ",
      instance,
      `Số điện thoại dài ${phone.length} ký tự, vượt quá 32 ký tự cho phép.`
    );
  }
  if (introduction.length > 2000) {
    return problem(
      400,
      "VALIDATION_FAILED",
      "Dữ liệu gửi lên không hợp lệ",
      instance,
      `Phần tự giới thiệu dài ${introduction.length} ký tự, vượt quá 2000 ký tự cho phép.`
    );
  }
  return null;
}

/**
 * Gõ đúng tên này vào ô "Họ và tên" của lối `NEW_PERSON` để bộ giả lập trả về
 * hai hồ sơ nghi trùng — dựng lại đúng ca {@code ClaimDuplicateSuspectDto}
 * sinh ra để phục vụ: người khai rất có thể đã có trong phả dưới một tên khác.
 *
 * Chỉ **số lượng** đi ra màn hình của người gửi đơn; hai `personId` dưới đây
 * có thật trong đồ thị giả lập để ảnh chụp còn tra được từ màn Trưởng chi,
 * nhưng màn của người gửi không bao giờ được gọi `GET /persons/{id}` bằng nó.
 */
export const MOCK_NEW_PERSON_DUPLICATE_NAME = "Nguyễn Văn Trùng Tên";

function soLanBiTuChoi(): number {
  return state.claims.filter((c) => c.status === "REJECTED").length;
}

function quota() {
  const rejected = soLanBiTuChoi();
  return {
    rejected,
    max: TRAN_BI_TU_CHOI,
    remaining: Math.max(0, TRAN_BI_TU_CHOI - rejected),
  };
}

interface NguoiTrongPha {
  id: string;
  isAlive: boolean;
  branchId: string | null;
}

/**
 * Tra một ô trong **cả hai** nguồn dữ liệu giả.
 *
 * Đồ thị phả đồ (~3.500 người sinh tự động) được hỏi trước, vì đó là nơi người
 * dùng thật bấm vào. `ALL_PERSONS` là nguồn thứ hai: nó giữ p-102/p-103 — hai
 * nhân khẩu cố ý **không** ghép vào đồ thị (xem `mocks/data.ts`), nên thiếu
 * nguồn này thì ca "ô ấy đã có tài khoản" không tra được.
 */
function timNguoi(personId: string): NguoiTrongPha | null {
  const raw = getMockGraph().personsById.get(personId);
  if (raw) {
    return { id: raw.id, isAlive: raw.isAlive, branchId: raw.primaryBranch?.id ?? null };
  }
  const curated = ALL_PERSONS.find((p) => p.id === personId);
  if (!curated) return null;
  return {
    id: curated.id,
    isAlive: curated.isAlive,
    // p-102/p-103 CÓ chi trong `data.ts`, nhưng bộ giả lập coi p-102 là ca
    // "người thân chưa gắn chi nào" — ca mà chỉ Trưởng chi sửa được còn người
    // dùng thì không, và vì thế đáng có một lối đi riêng trên màn hình.
    branchId: curated.id === MOCK_CLAIM_RELATIVE_UNUSABLE ? null : (curated.primaryBranch?.id ?? null),
  };
}

/**
 * `requireUnlinkedMember()` của máy chủ, chép nguyên thứ tự ba phép kiểm.
 *
 * Thứ tự là có nghĩa: tài khoản đã ghép → đang có đơn mở → hết lượt. Đổi thứ tự
 * thì một người đã ghép nhân khẩu vẫn đọc được câu "ông/bà còn mấy lần", tức
 * một câu nói sai về chính trạng thái của họ.
 */
function chanTheoTaiKhoan(instance: string): HttpResponse<Problem> | null {
  if (state.linkedPersonId !== null) {
    return problem(
      422,
      "ACCOUNT_ALREADY_LINKED",
      "Tài khoản của bạn đã được ghép với một nhân khẩu trong phả",
      instance
    );
  }
  if (state.claims.some((c) => c.status === "PENDING")) {
    return problem(
      409,
      "CLAIM_ALREADY_OPEN",
      "Bạn đang có một đơn chờ duyệt. Hãy rút đơn ấy trước khi gửi đơn khác.",
      instance
    );
  }
  if (soLanBiTuChoi() >= TRAN_BI_TU_CHOI) {
    return problem(
      422,
      "CLAIM_LIMIT_REACHED",
      `Đơn của bạn đã bị từ chối ${soLanBiTuChoi()} lần. Hãy liên hệ Trưởng chi để được hỗ trợ trực tiếp.`,
      instance
    );
  }
  return null;
}

/** `401` cho khách: không có token thì không có đơn nào. */
function chanKhach(request: Request, instance: string): HttpResponse<Problem> | null {
  if (resolveMockRole(request) !== "guest") return null;
  return problem(401, "UNAUTHENTICATED", "Chưa đăng nhập", instance);
}

function chiCuaNguoi(personId: string | null): ClaimView["targetBranch"] {
  const nguoi = personId === null ? null : timNguoi(personId);
  if (!nguoi?.branchId) return null;
  const raw = getMockGraph().personsById.get(nguoi.id);
  const ten = raw?.primaryBranch?.name ?? "Chi Nhất";
  return {
    id: nguoi.branchId,
    name: ten,
    kind: "CHI",
    // Chức danh DÒNG TỘC, không phải vai kỹ thuật `BRANCH_HEAD`. `ClaimBranch`
    // cố ý KHÔNG mang `path`: người đọc là một tài khoản vừa đăng ký, và phép
    // so phạm vi `ltree` là việc của máy chủ.
    clanTitle: `Trưởng ${ten}`,
  };
}

/**
 * Những đoạn đường KHÔNG phải một khoá đơn. Xem javadoc của `GET /:id`.
 */
const DOAN_DUONG_DANH_RIENG = new Set(["mine", "pending", "existing", "new-person"]);

// ============================================================================
// HANDLER
// ============================================================================

export const claimHandlers = [
  http.get(`${API_BASE_URL}/api/v1/person-claims/mine`, ({ request }) => {
    const instance = "/api/v1/person-claims/mine";
    const chan = chanKhach(request, instance);
    if (chan) return chan;

    // PHONG BÌ, cả hai trường bắt buộc (`MyPersonClaims`). `quota` không thuộc
    // về đơn nào: ngưỡng sống trong cấu hình máy chủ và client không được đoán.
    const body: MyClaimsDto = { claims: [...state.claims], quota: quota() };
    return HttpResponse.json(body);
  }),

  /**
   * Chi tiết một đơn — lối mà giao diện đi khi nó cầm một khoá từ
   * `competingClaimIds`. Ở bộ giả lập, người gọi luôn là người gửi.
   *
   * <h2>Cái bẫy: `/:id` nuốt mất `/pending`</h2>
   * `GET /person-claims/pending` sống ở `membershipAdminHandlers`, tệp được
   * đăng ký **sau** tệp này. MSW khớp theo thứ tự đăng ký, nên nếu `/:id` nhận
   * bừa mọi đoạn đường thì hàng chờ duyệt sẽ nhận `404 NOT_FOUND` với
   * `id = "pending"` — một lỗi trông như "máy chủ không có dữ liệu" chứ không
   * như một lỗi định tuyến. Nhường đường tường minh ở đây (`undefined` = đi
   * tiếp) thay vì đảo thứ tự hai tệp, vì thứ tự ấy có lý do riêng của nó:
   * đường không cần token đứng trước đường đọc vai.
   */
  http.get(`${API_BASE_URL}/api/v1/person-claims/:id`, ({ params, request }) => {
    const id = String(params.id);
    if (DOAN_DUONG_DANH_RIENG.has(id)) return undefined;

    const instance = `/api/v1/person-claims/${id}`;
    const chan = chanKhach(request, instance);
    if (chan) return chan;

    const don = state.claims.find((c) => c.id === id);
    if (!don) return problem(404, "NOT_FOUND", "Không tìm thấy đơn", instance);
    return HttpResponse.json(don);
  }),

  http.post(`${API_BASE_URL}/api/v1/person-claims/:id/cancel`, ({ params, request }) => {
    const instance = `/api/v1/person-claims/${String(params.id)}/cancel`;
    const chan = chanKhach(request, instance);
    if (chan) return chan;

    const don = state.claims.find((c) => c.id === params.id);
    if (!don) return problem(404, "NOT_FOUND", "Không tìm thấy đơn", instance);
    if (don.status !== "PENDING") {
      return problem(409, "CLAIM_CLOSED", "Đơn đã đóng, không xử lý lại được", instance);
    }

    const daRut: ClaimView = { ...don, status: "CANCELLED" };
    state.claims = state.claims.map((c) => (c.id === don.id ? daRut : c));
    return HttpResponse.json(daRut);
  }),

  /**
   * `POST /person-claims/new-person` — **lối GHI VÀO PHẢ**, không phải một biểu
   * mẫu liên hệ. Gửi đơn **không** tạo nhân khẩu nào: nhân khẩu chỉ ra đời khi
   * Trưởng chi duyệt.
   *
   * Hai lối gửi là **hai đường dẫn**, không phải một đường có trường phân loại.
   */
  http.post(`${API_BASE_URL}/api/v1/person-claims/new-person`, async ({ request }) => {
    const instance = "/api/v1/person-claims/new-person";
    const chan = chanKhach(request, instance);
    if (chan) return chan;

    const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
    const phone = typeof body.phone === "string" ? body.phone.trim() : "";
    const introduction = typeof body.introduction === "string" ? body.introduction.trim() : "";

    const loiHinhDang = loiHinhDangThanYeuCau(phone, introduction, instance);
    if (loiHinhDang) return loiHinhDang;

    const chanTaiKhoan = chanTheoTaiKhoan(instance);
    if (chanTaiKhoan) return chanTaiKhoan;

    {
      const fullName = typeof body.fullName === "string" ? body.fullName.trim() : "";
      const relativePersonId =
        typeof body.relativePersonId === "string" ? body.relativePersonId : "";
      const relativeKind = typeof body.relativeKind === "string" ? body.relativeKind : "";

      if (fullName.length === 0 || relativePersonId.length === 0 || relativeKind.length === 0) {
        // Thân yêu cầu SAI KHUÔN. `VALIDATION_FAILED` ở đây khác hẳn
        // `CLAIM_RELATIVE_UNUSABLE` ở dưới: một bên là "thiếu trường", bên kia
        // là "người thân ấy có thật nhưng không nối được".
        return problem(
          422,
          "VALIDATION_FAILED",
          "Phải chỉ ra một người thân đã có trong phả (bố, mẹ, hoặc vợ/chồng)",
          instance
        );
      }

      const nguoiThan = timNguoi(relativePersonId);
      if (!nguoiThan) {
        return problem(404, "NOT_FOUND", "Không tìm thấy người thân trong phả", instance);
      }
      // Người thân ĐƯỢC PHÉP đã khuất — "bố tôi là cụ X, cụ mất năm 2019" là ca
      // thường gặp nhất của cả lối này. Chỉ chi rỗng mới chặn.
      if (nguoiThan.branchId === null) {
        return problem(
          422,
          "CLAIM_RELATIVE_UNUSABLE",
          "Người thân được chỉ ra chưa được gắn vào chi nào trong phả, nên chưa xác định được ai duyệt đơn.",
          instance
        );
      }

      const created: ClaimView = {
        id: `claim-moi-${state.claims.length + 1}`,
        kind: "NEW_PERSON",
        status: "PENDING",
        // KHÔNG tạo nhân khẩu. `null` cho tới khi Trưởng chi duyệt.
        personId: null,
        relativePersonId,
        relativeKind: relativeKind as ClaimView["relativeKind"],
        declaredName: fullName,
        declaredBirthYear: typeof body.birthYear === "number" ? body.birthYear : null,
        declaredGender: (body.gender as ClaimView["declaredGender"]) ?? "UNKNOWN",
        targetBranchId: nguoiThan.branchId,
        targetBranch: chiCuaNguoi(relativePersonId),
        requestedBy: MOCK_CLAIM_REQUESTER,
        phone,
        introduction,
        // `[]` = ĐÃ quét, không nghi ai — khác hẳn `undefined` ("chưa quét bao
        // giờ"). Gộp hai trạng thái ấy sẽ nói với Trưởng chi rằng hệ thống đã
        // kiểm trong khi nó chưa kiểm.
        //
        // Gõ đúng `MOCK_NEW_PERSON_DUPLICATE_NAME` để dựng ca "đã quét, nghi
        // hai người" — đúng ca bộ dò sinh ra để phục vụ. Ảnh chụp chỉ mang
        // KHOÁ + điểm + tín hiệu, không tên: màn của người gửi đơn không được
        // đọc gì hơn số lượng, xem javadoc `claim-new-person-screen.tsx`.
        duplicateSuspects:
          fullName === MOCK_NEW_PERSON_DUPLICATE_NAME
            ? [
                { personId: MOCK_CLAIM_TARGET_OK, score: 81, signals: ["TEN_TRUNG_KHONG_DAU"] },
                { personId: MOCK_CLAIM_RELATIVE_OK, score: 58, signals: ["CUNG_CHI"] },
              ]
            : [],
        competingClaimIds: [],
        attemptNo: state.claims.length + 1,
        createdAt: new Date().toISOString(),
      };
      state.claims = [created, ...state.claims];
      return HttpResponse.json(created, { status: 201 });
    }
  }),

  /** `POST /person-claims/existing` — "tôi là ô này". */
  http.post(`${API_BASE_URL}/api/v1/person-claims/existing`, async ({ request }) => {
    const instance = "/api/v1/person-claims/existing";
    const chan = chanKhach(request, instance);
    if (chan) return chan;

    const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
    const phone = typeof body.phone === "string" ? body.phone.trim() : "";
    const introduction = typeof body.introduction === "string" ? body.introduction.trim() : "";

    const loiHinhDang = loiHinhDangThanYeuCau(phone, introduction, instance);
    if (loiHinhDang) return loiHinhDang;

    const chanTaiKhoan = chanTheoTaiKhoan(instance);
    if (chanTaiKhoan) return chanTaiKhoan;

    const personId = typeof body.personId === "string" ? body.personId : "";
    if (personId.length === 0) {
      return problem(422, "VALIDATION_FAILED", "Phải chọn một nhân khẩu trong phả", instance);
    }

    const target = timNguoi(personId);
    if (!target) {
      return problem(404, "NOT_FOUND", "Không tìm thấy nhân khẩu", instance);
    }
    if (
      !target.isAlive ||
      DA_CO_TAI_KHOAN.has(personId) ||
      personId === MOCK_CLAIM_TARGET_UNAVAILABLE
    ) {
      // MỘT MÃ CHO BA LÝ DO — đã khuất · đã có tài khoản · đã xoá mềm. Đây là
      // chỗ bộ giả lập phải giữ đúng nhất: tách ra thành ba mã là dựng nên đúng
      // công cụ liệt kê mà contract gộp mã để chặn.
      //
      // Ca "đã khuất" thì giao diện gần như không bao giờ tới được — nó đã chặn
      // từ `isAlive` trước khi dựng biểu mẫu, và ở ĐÓ mới được nói thẳng, vì hồ
      // sơ người đã khuất vốn công khai với cả khách.
      return problem(
        422,
        "CLAIM_TARGET_UNAVAILABLE",
        "Không gửi đơn cho người này được. Hãy chọn một ô khác, hoặc hỏi Trưởng chi.",
        instance
      );
    }

    const created: ClaimView = {
      id: `claim-moi-${state.claims.length + 1}`,
      kind: "EXISTING",
      status: "PENDING",
      personId,
      targetBranchId: target.branchId,
      targetBranch: chiCuaNguoi(personId),
      requestedBy: MOCK_CLAIM_REQUESTER,
      phone,
      introduction,
      // Đơn `EXISTING` KHÔNG chạy bộ dò trùng, nên `duplicateSuspects` **vắng
      // mặt** — và vắng mặt là một trạng thái có nghĩa riêng: *chưa quét bao giờ*.
      competingClaimIds: [],
      attemptNo: state.claims.length + 1,
      createdAt: new Date().toISOString(),
    };
    state.claims = [created, ...state.claims];
    return HttpResponse.json(created, { status: 201 });
  }),
];
