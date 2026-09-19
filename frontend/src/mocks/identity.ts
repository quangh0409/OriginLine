import type { Role } from "@/types/api";
import type { MockRole } from "./handlers/role";

/**
 * Danh tính của người gọi trong MSW — nửa còn thiếu của `handlers/role.ts`.
 *
 * <h2>Vì sao cần</h2>
 * Trước đây bộ giả lập chỉ biết *vai* của người gọi, không biết *họ là ai*. Với
 * phân tầng riêng tư thì thế là đủ, nhưng luồng đính chính thì không: nó phải
 * trả lời được ba câu mà vai suông không trả lời nổi —
 *
 *  1. hồ sơ này có phải của chính tôi không (`meta.isSelf`, quyền sửa hồ sơ mình);
 *  2. chi này có nằm trong phạm vi tôi được giao không (`ltree`, `meta.canEdit`);
 *  3. yêu cầu này có phải do chính tôi gửi không (`SELF_REVIEW_FORBIDDEN`).
 *
 * Câu thứ ba là câu quan trọng nhất: không có danh tính thì không có cách nào
 * dựng được ca "Trưởng chi không tự duyệt đề nghị của mình", mà đó chính là
 * chốt chặn khiến luồng duyệt có nghĩa.
 *
 * <h2>Đây là dữ liệu giả, không phải phân quyền</h2>
 * Bản chạy thật lấy những dữ kiện này từ `GET /api/v1/me`, mà backend suy từ
 * JWT + bảng `branch_assignment`. Không có gì ở đây được phép rò sang mã sản
 * phẩm.
 */
export interface MockIdentity {
  /** `null` với Khách: chưa có dòng `app_user` nào (`ACCOUNT_NOT_PROVISIONED`). */
  appUserId: string | null;
  /** Nhân khẩu tương ứng trong cây, `null` nếu tài khoản chưa được ghép. */
  personId: string | null;
  role: Role;
  clanWide: boolean;
  /** Các chi được giao, dạng `ltree`. **Rỗng nghĩa là không có phạm vi nào.** */
  managedBranches: string[];
  homeBranch: string | null;
}

/**
 * Trưởng chi giả lập chỉ quản `root.chi_nhat`.
 *
 * Cố ý **không** cho quản cả `root`: nếu Trưởng chi giả lập nào cũng thấy mọi
 * chi thì `BRANCH_SCOPE_VIOLATION` không bao giờ chạy qua, và cái nửa quyền
 * hạn dễ sai nhất của hệ thống sẽ không có chỗ nào thử được.
 */
const BRANCH_HEAD_SCOPE = "root.chi_nhat";

const IDENTITIES: Record<MockRole, MockIdentity> = {
  guest: {
    appUserId: null,
    personId: null,
    role: "GUEST",
    clanWide: false,
    managedBranches: [],
    homeBranch: null,
  },
  member: {
    appUserId: "u-member",
    // p-102 (Nguyễn Văn Bình) chứ không phải p-100: p-100 đóng vai "người bà
    // con còn sống mà thành viên KHÁC đang xem", và hồ sơ ấy phải tiếp tục
    // hiện ra ở Tầng 1 không có nút Sửa.
    personId: "p-102",
    role: "MEMBER",
    clanWide: false,
    managedBranches: [],
    homeBranch: "root.chi_nhat",
  },
  "branch-head": {
    appUserId: "u-branch-head",
    personId: "p-103",
    role: "BRANCH_HEAD",
    clanWide: false,
    managedBranches: [BRANCH_HEAD_SCOPE],
    homeBranch: BRANCH_HEAD_SCOPE,
  },
  admin: {
    appUserId: "u-admin",
    personId: null,
    role: "ADMIN",
    clanWide: true,
    managedBranches: [],
    homeBranch: null,
  },
};

export function identityOf(role: MockRole): MockIdentity {
  return IDENTITIES[role];
}

/**
 * Ngữ nghĩa `@>` của `ltree`: `root.chi_nhat` bao `root.chi_nhat.nganh_truong`
 * nhưng **không** bao `root.chi_nhat_khac`. Dấu chấm ở cuối tiền tố là thứ giữ
 * đúng ranh giới ấy — thiếu nó thì mọi chi có tên bắt đầu giống nhau đều lọt.
 */
export function pathIsWithin(ancestor: string, path: string | null | undefined): boolean {
  if (!path) return false;
  return path === ancestor || path.startsWith(`${ancestor}.`);
}

/** Người này có được **ghi thẳng** lên dữ liệu thuộc chi `path` không. */
export function canWriteInBranch(identity: MockIdentity, path: string | null | undefined): boolean {
  if (identity.appUserId === null) return false;
  if (identity.clanWide) return true;
  if (identity.role !== "BRANCH_HEAD") return false;
  // Chi rỗng KHÔNG phải chi công cộng: đối tượng chưa gắn chi thì chỉ vai toàn
  // dòng họ được đụng (BranchScopeGuard, "Chi rỗng không phải là chi công cộng").
  return identity.managedBranches.some((scope) => pathIsWithin(scope, path));
}

/** Vai này có được **duyệt** yêu cầu đính chính không (còn phải qua kiểm phạm vi). */
export function roleCanReview(identity: MockIdentity): boolean {
  return identity.role === "ADMIN" || identity.role === "COUNCIL" || identity.role === "BRANCH_HEAD";
}
