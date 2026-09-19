import type {
  PersonAccessMeta,
  PersonDto,
  PrivacySettings,
  Role,
  VisibleTier,
} from "@/types/api";
import type { MockRole } from "./handlers/role";
import { canWriteInBranch, identityOf } from "./identity";
import {
  canReadPrivacy,
  isMinorMock,
  openedGroupsFor,
  privacyOf,
  viewerClassFor,
} from "./privacy-settings";

/**
 * Illustrative stand-in for the backend's `PrivacyTierFilter` (W6), simplified
 * for mock purposes. The REAL rule is a backend concern — do not port this
 * logic anywhere near production code. This exists only so the screens have
 * every tier to develop against without a live backend.
 *
 * Ánh xạ vai giả → vai thật. **Không** còn ánh xạ vai → tầng: từ khi
 * `PrivacySettings` vào contract, tầng là thứ được SUY RA sau khi lọc
 * (`summarizeTier`), không phải thứ quyết định phép lọc.
 */
function mockRoleToRole(role: MockRole): Role {
  switch (role) {
    case "admin":
      return "ADMIN";
    case "branch-head":
      return "BRANCH_HEAD";
    case "member":
      return "MEMBER";
    default:
      return "GUEST";
  }
}

/**
 * Ba cờ quyền của `meta`, tính từ **danh tính** người gọi chứ không từ vai suông.
 *
 * <h2>Vì sao `canRequestCorrection` không thể là hằng số</h2>
 * Trước đây cả ba tệp dữ liệu giả đều ghi cứng `canRequestCorrection: false`,
 * và hệ quả là luồng đính chính không có điểm bắt đầu nào trên toàn bộ giao
 * diện. Nhưng thành viên thường mới là người **phát hiện sai sót nhiều nhất** —
 * họ biết rõ nhà mình — trong khi họ lại là người **không được ghi thẳng**. Yêu
 * cầu đính chính chính là cây cầu giữa hai điều đó, nên cờ này phải là:
 *
 *   đã có tài khoản  ∧  KHÔNG được sửa trực tiếp
 *
 * Nói cách khác: hễ ai đó thấy một chỗ sai mà không tự sửa được, họ phải đề
 * nghị được. Khách không nằm trong đó (khách còn không thấy người sống nào).
 * Quản trị và Trưởng chi trong phạm vi của mình cũng không — họ sửa thẳng,
 * bắt họ đi vòng qua hàng đợi của chính mình là vô nghĩa.
 *
 * <h2>`canEdit` soi phạm vi, không soi vai</h2>
 * Trưởng chi `root.chi_nhat` sửa được người trong chi mình và **không** sửa
 * được `root.chi_nhi` — đúng ngữ nghĩa `@>` của `ltree` mà `BranchScopeGuard`
 * dùng. Thành viên sửa được hồ sơ **của chính mình** (BA v2 §10: Tầng 3 mở cho
 * chính chủ), ngoài ra thì không.
 */
function accessMetaFor(full: PersonDto, role: MockRole, tier: VisibleTier): PersonAccessMeta {
  const identity = identityOf(role);
  const hasAccount = identity.appUserId !== null;
  const isSelf = identity.personId !== null && identity.personId === full.id;
  const branchPath = full.primaryBranch?.path ?? null;

  const canWriteHere = canWriteInBranch(identity, branchPath);
  const canEdit = hasAccount && (isSelf || canWriteHere);

  return {
    visibleTier: tier,
    canEdit,
    // Xoá mềm là việc của người quản chi, không phải của chính chủ: không ai
    // tự gỡ mình khỏi gia phả.
    canDelete: canWriteHere,
    canRequestCorrection: hasAccount && !canEdit,
    isSelf,
    callerRole: mockRoleToRole(role),
  };
}

/**
 * `meta` mặc định của các bản ghi "đầy đủ" trong `data.ts` / `person-detail.ts`.
 *
 * Chúng là dữ liệu **trước khi lọc**, chưa gắn với người gọi nào, nên chưa có
 * câu trả lời nào cho ba cờ quyền. Trước đây mỗi bản ghi tự ghi `false` cho cả
 * ba, và ba con số `false` ấy trông y hệt một câu trả lời — đó là cách
 * `canRequestCorrection` bị chốt chết ở `false` trên toàn hệ thống. Hằng số này
 * tồn tại để chỗ ấy chỉ có **một** và nó nói rõ mình là chỗ giữ chỗ:
 * `projectPersonForRole` luôn dựng lại `meta` từ đầu trước khi trả về dây.
 */
export function preProjectionMeta(visibleTier: VisibleTier): PersonAccessMeta {
  return { visibleTier, canEdit: false, canDelete: false, canRequestCorrection: false };
}

/**
 * Project a "full fidelity" mock PersonDto down to what a given mock role
 * would actually receive. Fields dropped are set to `undefined`, which
 * `JSON.stringify` (used by MSW's `HttpResponse.json`) omits from the wire
 * payload — the same "absent, not null" semantics as the real REST contract.
 *
 * <h2>Ai quyết định: bản đồng thuận, không phải vai</h2>
 * Từ khi `PrivacySettings` vào contract, **nguồn quyết định là năm công tắc
 * của chủ thể**, không còn là tầng suy từ vai. Trưởng chi không còn một nền
 * Tầng 2 mặc định: trong chi mình họ là "người cùng chi", nên họ thấy đúng
 * những nhóm mà chủ thể đã mở tới mức `BRANCH`. Contract nói thẳng điều đó ở
 * `ContactInfo`: *"Trưởng chi được xem để liên hệ CHỈ KHI `privacy.contact` ở
 * mức `BRANCH` trở lên"*.
 *
 * Chỉ còn HAI thứ nằm ngoài tay chủ thể, và cả hai đều theo hướng ĐÓNG chứ
 * không mở: khách không thấy người còn sống nào, và Hội đồng / `ADMIN` đọc
 * được tất cả (kể cả nhóm `PRIVATE` — đó là định nghĩa của mức ấy).
 *
 * <h2>`meta.visibleTier` là tóm tắt, không phải đầu vào</h2>
 * Nó được suy ra SAU khi lọc xong, để giao diện biết mình đang ở đâu. Không
 * dòng nào dưới đây rẽ nhánh theo nó, và giao diện cũng không được — `T2`
 * không hứa `occupation` tồn tại.
 */
export function projectPersonForRole(full: PersonDto, role: MockRole): PersonDto {
  const identity = identityOf(role);
  const viewer = viewerClassFor(full, identity);

  if (!full.isAlive || viewer === "SELF" || viewer === "COUNCIL") {
    const tier: VisibleTier = full.isAlive ? "T3" : "PUBLIC";
    return {
      ...full,
      // Khối `privacy` chỉ đi kèm khi người gọi được đọc nó. Người đã khuất
      // không mang bản đồng thuận nào (contract: người đã khuất không bị áp mô
      // hình đồng thuận), nên nó cũng vắng ở đó.
      privacy: full.isAlive && canReadPrivacy(full, identity) ? privacyOf(full.id) : undefined,
      meta: accessMetaFor(full, role, tier),
    };
  }

  const opened = openedGroupsFor(full, identity);
  const minor = isMinorMock(full);

  const projected: PersonDto = {
    id: full.id,
    isAlive: full.isAlive,
    generation: full.generation,
    gender: full.gender,
    displayName: full.displayName,
    primaryBranch: full.primaryBranch,
    // Chỉ lớp tên chính — húy/tự/hiệu/thụy là dữ liệu nhạy cảm về lễ nghi và
    // không nằm trong năm nhóm, nên chúng theo luật cố định chặt nhất.
    names: full.names.filter((n) => n.isPrimary),
    meta: preProjectionMeta("T1"),
  };

  // Trẻ vị thành niên ẩn tối đa BẤT KỂ chọn gì — luật này thắng mọi công tắc.
  if (!minor) {
    if (opened.has("occupation")) projected.occupation = full.occupation;
    if (opened.has("residenceProvince")) projected.currentPlaceProvince = full.currentPlaceProvince;
    if (opened.has("residenceFull")) projected.currentPlaceFull = full.currentPlaceFull;
    if (opened.has("contact")) projected.contact = full.contact;
    if (opened.has("birthDetailAndPhoto")) {
      // Mở nhóm này thì là NGÀY ĐẦY ĐỦ, không phải bản cắt còn năm: cắt bớt
      // thứ chủ thể đã cố ý mở là tự ý siết hộ người dùng.
      projected.birth = full.birth;
      projected.avatarUrl = full.avatarUrl;
    }
    // KHÔNG có nhánh `else`: nhóm `birthDetailAndPhoto` đóng thì **không có
    // ngày sinh nào** đi ra, kể cả bản cắt còn năm, kể cả với người cùng chi.
    //
    // ĐIỂM LỆCH ĐÃ BIẾT — cần backend/Hội đồng chốt, đã báo lên điều phối:
    // `contracts/openapi.yaml` mô tả `birthDetailAndPhoto` có một câu phụ nói
    // rằng đóng nhóm này thì `birth` "vẫn có thể xuất hiện ở mức năm với người
    // cùng chi, vì năm sinh là chất liệu của chính cây phả hệ". Bản dựng ở đây
    // theo phía CHẶT HƠN, vì hai lý do:
    //
    //   1. chỉ dẫn của điều phối viên nói thẳng "người còn sống không còn hiện
    //      năm sinh cho thành viên thường — kể cả người cùng chi";
    //   2. với một trường riêng tư, cách đoán sai an toàn là đoán về phía kín:
    //      mở nhầm thì không thu về được, giấu nhầm thì mở lại được.
    //
    // Nếu chốt ngược lại thì đây là chỗ duy nhất phải sửa — thêm lại một dòng
    // `if (viewer === "SAME_BRANCH") projected.birth = truncateToYear(...)`.
  }

  projected.meta = accessMetaFor(full, role, summarizeTier(opened, minor));
  return projected;
}

/**
 * Người gọi này có được thấy **năm sinh** của chủ thể không.
 *
 * Tách riêng khỏi `projectPersonForRole` vì câu hỏi ấy được hỏi ở ba chỗ khác
 * nhau và chỉ một trong ba dựng nổi một `PersonDto` đầy đủ: hồ sơ, **nút trên
 * phả đồ** (`PersonSummaryDto`, vài trăm nút một lượt), và **sự kiện sinh
 * nhật**. Trước khi có hàm này, hai chỗ sau trả năm sinh của mọi người còn
 * sống cho mọi thành viên — tức bộ giả lập đang giả lập một API **dễ hơn** API
 * thật, đúng kiểu lệch khiến giao diện được dựng cho một thế giới không tồn tại.
 *
 * Luật, theo đúng mô hình đồng thuận V8:
 *
 *  - người đã khuất là công khai — không có gì để hỏi;
 *  - người còn sống: năm sinh thuộc nhóm `birthDetailAndPhoto`, và nhóm ấy
 *    **không có mức trung gian "chỉ năm"**. Mở thì ra ngày đầy đủ, đóng thì
 *    không có gì — kể cả với người cùng chi;
 *  - trẻ vị thành niên: ẩn tối đa, chỉ chính chủ và Hội đồng/`ADMIN`.
 */
export function canSeeBirthYearMock(
  subject: {
    id: string;
    isAlive: boolean;
    birthYear?: number | null;
    primaryBranch?: PersonDto["primaryBranch"];
  },
  role: MockRole
): boolean {
  if (!subject.isAlive) return true;

  const identity = identityOf(role);
  const viewer = viewerClassFor(subject, identity);
  if (viewer === "SELF" || viewer === "COUNCIL") return true;

  // Vị thành niên: không công tắc nào mở được. Suy từ năm sinh vì đây là tất cả
  // những gì một tóm tắt mang theo; không có năm sinh thì cũng không có gì để
  // giấu ở đây.
  const year = subject.birthYear ?? null;
  if (year !== null && new Date().getFullYear() - year < 18) return false;

  return openedGroupsFor(subject, identity).has("birthDetailAndPhoto");
}

/**
 * `meta.visibleTier` — **suy ra từ kết quả lọc thật**, không phải đầu vào của
 * phép lọc (contracts/openapi.yaml → `VisibleTier`).
 *
 * Thứ tự tính phải khớp contract: `T3` khi đã mở ít nhất một nhóm nhạy cảm,
 * `T2` khi đã mở một trong hai nhóm nhẹ, còn lại là `T1`.
 */
function summarizeTier(opened: Set<keyof PrivacySettings>, minor: boolean): VisibleTier {
  if (minor) return "T1";
  if (opened.has("residenceFull") || opened.has("contact") || opened.has("birthDetailAndPhoto")) {
    return "T3";
  }
  if (opened.has("occupation") || opened.has("residenceProvince")) return "T2";
  return "T1";
}
