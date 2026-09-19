import type {
  ChangeRequestStatus,
  ChangeRequestType,
  ChangeRequestView,
} from "@/lib/api/change-requests";
import { resolvePersonMock } from "./person-detail";

/**
 * Kho yêu cầu **đính chính** trong bộ nhớ cho MSW.
 *
 * Đây là nửa giả lập của `membership.ChangeRequestService`. Nó cố ý dựng lại
 * đúng ba phép kiểm mà luồng thật dựa vào — và chỉ ba phép ấy, vì đó là những
 * chỗ giao diện phải xử lý cho đúng:
 *
 *  1. **phạm vi chi/ngành theo `ltree`** — Trưởng chi `root.chi_nhat` thấy và
 *     duyệt được `root.chi_nhat.*`, không thấy `root.chi_nhi`;
 *  2. **không tự duyệt** — người duyệt là người gửi thì `SELF_REVIEW_FORBIDDEN`;
 *  3. **trạng thái cuối là cuối** — đã duyệt/từ chối/rút thì `CHANGE_REQUEST_CLOSED`.
 *
 * Trạng thái sống trong module, reset khi tải lại trang — như mọi mảnh dữ liệu
 * giả khác.
 */

/**
 * `branch.id → ltree path`. Trong hệ thống thật đây là một cột trong bảng
 * `branch`; ở đây phải chép tay vì `generate-large-tree.ts` sinh path từ cùng
 * bộ hằng số ấy (`CHI_IDS` × `CHI_SLUGS`).
 */
const BRANCH_PATHS: Record<string, string> = {
  "b-root": "root",
  "b-chi1": "root.chi_nhat",
  "b-chi2": "root.chi_nhi",
  "b-chi3": "root.chi_tam",
  "b-chi4": "root.chi_tu",
};

export function branchPathOf(branchId: string | null | undefined): string | null {
  if (!branchId) return null;
  return BRANCH_PATHS[branchId] ?? null;
}

/** Chi chính của một nhân khẩu, dạng `ltree` — dùng để chốt chi đích lúc gửi. */
export function branchOfPerson(personId: string | null | undefined): {
  branchId: string | null;
  path: string | null;
} {
  if (!personId) return { branchId: null, path: null };
  const person = resolvePersonMock(personId);
  const branch = person?.primaryBranch ?? null;
  return { branchId: branch?.id ?? null, path: branch?.path ?? null };
}

interface SeedInput {
  id: string;
  type: ChangeRequestType;
  personId: string | null;
  /**
   * Chi đích, ghi thẳng thay vì suy từ `personId`.
   *
   * Suy ra lúc nạp module sẽ kéo theo việc dựng cả đồ thị ~4.000 node cho MỌI
   * tệp test, kể cả những tệp không đụng tới phả đồ — đủ để một worker Vitest
   * hết bộ nhớ và chết không kèm lỗi. Đây là dữ liệu mồi, chi đích đã biết
   * trước, nên viết thẳng ra là vừa đúng vừa rẻ.
   */
  targetBranchId: string | null;
  payload: Record<string, unknown>;
  reason: string;
  requestedBy: string;
  status?: ChangeRequestStatus;
  reviewerId?: string | null;
  reviewNote?: string | null;
  createdAt: string;
}

function build(seed: SeedInput): ChangeRequestView {
  const branchId = seed.targetBranchId;
  const status = seed.status ?? "PENDING";
  return {
    id: seed.id,
    type: seed.type,
    status,
    personId: seed.personId,
    targetBranchId: branchId,
    payload: seed.payload,
    payloadFields: Object.keys(seed.payload).sort(),
    reason: seed.reason,
    requestedBy: seed.requestedBy,
    reviewerId: seed.reviewerId ?? null,
    reviewNote: seed.reviewNote ?? null,
    reviewedAt: status === "PENDING" ? null : "2026-08-30T02:00:00+07:00",
    createdAt: seed.createdAt,
    version: 0,
  };
}

/**
 * Bốn yêu cầu mồi, mỗi cái phục vụ đúng một tình huống giao diện phải chịu được.
 * Đừng bỏ cái nào mà không thay bằng cái tương đương.
 */
function seedRequests(): ChangeRequestView[] {
  return [
    // (1) Thành viên báo sai ngày giỗ — ca chính, Trưởng chi chi_nhat duyệt được.
    build({
      id: "cr-001",
      type: "UPDATE_PERSON",
      personId: "p-010",
      targetBranchId: "b-chi1",
      payload: {
        death: {
          solar: "1878-01-20",
          lunar: { year: 1877, month: 12, day: 18, leap: false },
          precision: "DAY",
        },
      },
      reason:
        "Gia phả chép tay của nhà cháu ghi cụ mất ngày 18 tháng Chạp, không phải 15. "
        + "Nhà cháu vẫn làm giỗ cụ ngày 18 từ đời ông nội.",
      requestedBy: "u-member",
      createdAt: "2026-08-28T03:15:00+07:00",
    }),
    // (2) Do CHÍNH Trưởng chi gửi — ca "không được tự duyệt". Trưởng chi thấy
    //     nó trong hàng đợi nhưng chỉ Hội đồng/Quản trị mới quyết được.
    build({
      id: "cr-002",
      type: "UPDATE_PERSON",
      personId: "p-020",
      targetBranchId: "b-chi1",
      payload: { nativePlace: "Nam Định" },
      reason: "Quê quán của cụ bà ghi thiếu, xin bổ sung theo văn bia ngoài từ đường.",
      requestedBy: "u-branch-head",
      createdAt: "2026-08-29T08:40:00+07:00",
    }),
    // (3) Thuộc Chi Nhị — NGOÀI phạm vi Trưởng chi chi_nhat. Phải vô hình với
    //     người ấy, không phải "thấy nhưng bấm vào thì 403".
    build({
      id: "cr-003",
      type: "UPDATE_PERSON",
      personId: "p-011",
      targetBranchId: "b-chi2",
      payload: { occupation: "Thầy đồ" },
      reason: "Cụ dạy học ở làng, gia phả cũ có ghi là thầy đồ.",
      requestedBy: "u-member",
      createdAt: "2026-08-27T10:05:00+07:00",
    }),
    // (4) Đã duyệt xong — người gửi phải thấy kết quả, và mọi cố gắng duyệt lại
    //     phải trả CHANGE_REQUEST_CLOSED.
    build({
      id: "cr-004",
      type: "OTHER",
      personId: "p-001",
      targetBranchId: "b-root",
      payload: {},
      reason: "Xin bổ sung ảnh chụp bia đá ngoài lăng cụ Thủy tổ vào thư viện di sản.",
      requestedBy: "u-member",
      status: "APPROVED",
      reviewerId: "u-admin",
      reviewNote: "Đã nhận ảnh, chuyển sang mục di sản.",
      createdAt: "2026-08-20T06:00:00+07:00",
    }),
  ];
}

/**
 * Khởi tạo **lười**. Nạp module không được làm gì tốn kém: `src/mocks/server.ts`
 * kéo theo mọi handler vào mọi tệp test, kể cả những tệp chẳng liên quan.
 */
let store: ChangeRequestView[] | null = null;
let counter = 0;

function requests(): ChangeRequestView[] {
  if (store === null) store = seedRequests();
  return store;
}

export function allChangeRequestsMock(): ChangeRequestView[] {
  return requests();
}

export function findChangeRequestMock(id: string): ChangeRequestView | undefined {
  return requests().find((r) => r.id === id);
}

export function insertChangeRequestMock(request: ChangeRequestView): ChangeRequestView {
  store = [request, ...requests()];
  return request;
}

export function replaceChangeRequestMock(next: ChangeRequestView): ChangeRequestView {
  store = requests().map((r) => (r.id === next.id ? next : r));
  return next;
}

export function nextChangeRequestIdMock(): string {
  counter += 1;
  return `cr-new-${counter}-${Date.now().toString(36)}`;
}

/** Chỉ dùng trong test — trả kho về đúng bốn yêu cầu mồi. */
export function resetChangeRequestsMock(): void {
  store = seedRequests();
  counter = 0;
}

/**
 * Bản đã cắt `payload` (khớp `ChangeRequestView.redacted()`).
 *
 * Giữ `payloadFields` là có chủ ý: người ngoài cuộc vẫn biết yêu cầu động tới
 * trường nào — đủ để hiểu hàng đợi — mà không thấy giá trị, vì giá trị mới là
 * chỗ có thể chứa dữ liệu Tầng 3 của một người còn sống.
 */
export function redactPayload(request: ChangeRequestView): ChangeRequestView {
  return { ...request, payload: {} };
}
