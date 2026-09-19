import { PRIVACY_GROUPS, type PersonDto, type PrivacySettings, type ShareScope } from "@/types/api";
import { pathIsWithin, type MockIdentity } from "./identity";

/**
 * Bản dựng giả của **bản đồng thuận riêng tư** (`PrivacySettings`) —
 * năm nhóm trường, năm mức độc lập (BA v2 §10, Nghị định 13/2023).
 *
 * <h2>Bám theo contract, không bám theo một API dễ hơn</h2>
 * Tên khoá, tên mức và ngữ nghĩa ghi ở đây chép thẳng từ
 * `contracts/openapi.yaml` → `PrivacySettings` / `ShareScope`. Trước khi
 * contract chốt, bộ giả lập này từng dựng một endpoint riêng
 * (`/persons/{id}/privacy-settings`) với tên nhóm kiểu hằng số
 * (`CURRENT_PLACE_PROVINCE`…). Cả hai đều SAI so với bản thật: khối `privacy`
 * nằm **trong** `PersonDto` và được ghi qua `PATCH /persons/{id}`. Đây đúng là
 * kiểu lệch mà bài học "bộ giả lập đang giả lập một API dễ hơn API thật" nói
 * tới, nên nó được sửa chứ không được bọc lại.
 *
 * <h2>Đây là mã giả lập, không phải phân quyền</h2>
 * Luật thật nằm ở backend. Không dòng nào ở đây được rò sang mã sản phẩm.
 */

export type AudienceMap = PrivacySettings;

/** Trường của `PersonDto` mà mỗi nhóm chi phối. */
export const PRIVACY_GROUP_FIELDS: Record<keyof PrivacySettings, string[]> = {
  occupation: ["occupation"],
  residenceProvince: ["currentPlaceProvince"],
  residenceFull: ["currentPlaceFull"],
  contact: ["contact.phone", "contact.email", "contact.zaloId"],
  birthDetailAndPhoto: ["birth", "avatarUrl"],
};

/** **Mặc định là KÍN.** Một nhóm chưa từng được đặt luôn đọc ra `PRIVATE`. */
export const DEFAULT_SCOPE: ShareScope = "PRIVATE";

export function allPrivate(): PrivacySettings {
  return {
    occupation: DEFAULT_SCOPE,
    residenceProvince: DEFAULT_SCOPE,
    residenceFull: DEFAULT_SCOPE,
    contact: DEFAULT_SCOPE,
    birthDetailAndPhoto: DEFAULT_SCOPE,
  };
}

/** Băm tất định theo id — cùng một người trông y hệt nhau ở mọi lần chạy. */
function hash(seedText: string): number {
  let h = 2166136261;
  for (let i = 0; i < seedText.length; i += 1) {
    h ^= seedText.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0) / 4294967296;
}

/**
 * Mức chia sẻ viết tay cho các nhân vật "có câu chuyện".
 *
 * `p-100` và `p-101` được ghim TOÀN `PRIVATE` một cách tường minh, và đó không
 * thừa: thiếu dòng ấy thì chúng rơi vào nhánh băm bên dưới, và `p-100` — hồ sơ
 * giữ vai "người bà con còn sống mà một thành viên KHÁC đang xem" — sẽ ngẫu
 * nhiên mở nghề nghiệp và số điện thoại ra. Bốn ca kiểm phân tầng đã đỏ vì
 * đúng chỗ này, và chúng đỏ ĐÚNG.
 *
 * Bài học chung: dữ liệu trình diễn sinh theo băm thì tiện, nhưng mọi hồ sơ
 * đang gánh một bất biến đều phải được ghim tay.
 */
const CURATED_SEEDS: Record<string, Partial<PrivacySettings>> = {
  "p-100": {},
  "p-101": {},
  // Thành viên đang đăng nhập — hồ sơ dùng để thử màn "chính chủ chỉnh mức".
  // Có đủ ba mức, để bảng "ai xem được gì" không phải một cột giống hệt nhau.
  "p-102": {
    occupation: "CLAN",
    residenceProvince: "CLAN",
    residenceFull: "PRIVATE",
    contact: "BRANCH",
    birthDetailAndPhoto: "PRIVATE",
  },
  // Trưởng chi: mở rộng hơn, đúng lẽ thường của người giữ việc họ.
  "p-103": {
    occupation: "CLAN",
    residenceProvince: "CLAN",
    contact: "CLAN",
  },
};

/**
 * Mức chia sẻ "đã có sẵn" của dữ liệu trình diễn.
 *
 * KHÔNG mâu thuẫn với luật "mặc định là kín": luật ấy nói về một **hồ sơ mới**
 * hoặc một **trường mới** — `allPrivate()` ở trên chính là nó. Dữ liệu trình
 * diễn mô phỏng một dòng họ đã dùng hệ thống một thời gian. Không có phần ấy
 * thì danh bạ rỗng trơn ở mọi lần chạy.
 *
 * Cả ba mức đều phải xuất hiện, vì mức giữa (`BRANCH`) là mức dễ dựng sai
 * nhất: nó là mức duy nhất khiến HAI người cùng vai "Thành viên" nhận hai câu
 * trả lời khác nhau.
 */
function demoSeed(personId: string): PrivacySettings {
  const curated = CURATED_SEEDS[personId];
  if (curated) return { ...allPrivate(), ...curated };

  const h = hash(personId);
  const map = allPrivate();

  if (h < 0.3) {
    map.occupation = "CLAN";
    map.residenceProvince = "CLAN";
  } else if (h < 0.42) {
    map.occupation = "BRANCH";
    map.residenceProvince = "BRANCH";
  }

  const h2 = hash(`${personId}#contact`);
  if (h2 < 0.12 && map.occupation === "CLAN") map.contact = "CLAN";
  else if (h2 < 0.3) map.contact = "BRANCH";

  if (hash(`${personId}#birth`) < 0.18) map.birthDetailAndPhoto = "BRANCH";

  return map;
}

// ---------------------------------------------------------------------------
// Kho ghi — PATCH viết vào đây. Reset khi tải lại trang, như mọi trạng thái giả.
// ---------------------------------------------------------------------------

const overrides = new Map<string, PrivacySettings>();

export function privacyOf(personId: string): PrivacySettings {
  return overrides.get(personId) ?? demoSeed(personId);
}

/**
 * **Hợp nhất, không thay thế** — đúng ngữ nghĩa `PATCH /persons/{id}`.
 *
 * Đây là chỗ khác hẳn `names`/`attributes` (vốn thay cả danh sách), và là chỗ
 * dễ dựng sai nhất của cả khối: nếu ghi đè toàn phần, một giao diện chỉ gửi
 * công tắc vừa gạt sẽ âm thầm ĐÓNG bốn nhóm còn lại.
 */
export function mergePrivacy(personId: string, patch: Partial<PrivacySettings>): PrivacySettings {
  const next = { ...privacyOf(personId), ...patch };
  overrides.set(personId, next);
  return next;
}

/** `clearFields: ["privacy"]` — đóng cả năm nhóm về `PRIVATE`. */
export function clearPrivacy(personId: string): PrivacySettings {
  const next = allPrivate();
  overrides.set(personId, next);
  return next;
}

/** Chỉ dùng trong ca kiểm: trả kho ghi về trạng thái ban đầu. */
export function resetPrivacySettingsStore(): void {
  overrides.clear();
}

// ---------------------------------------------------------------------------
// Phân hạng người xem và phép giải mức
// ---------------------------------------------------------------------------

/**
 * Hạng người xem đối với MỘT chủ thể cụ thể.
 *
 * Cố ý nhận vào đường dẫn chi của chủ thể chứ không chỉ vai: "cùng chi" là một
 * quan hệ giữa hai người, không phải thuộc tính của người xem. Lẫn hai trục đó
 * là chỗ phân quyền theo chi hỏng nhiều nhất.
 */
export type ViewerClass = "GUEST" | "CLAN_MEMBER" | "SAME_BRANCH" | "COUNCIL" | "SELF";

export function viewerClassFor(
  subject: Pick<PersonDto, "id"> & { primaryBranch?: PersonDto["primaryBranch"] },
  identity: MockIdentity
): ViewerClass {
  if (identity.appUserId === null) return "GUEST";
  if (identity.personId !== null && identity.personId === subject.id) return "SELF";
  // Hội đồng Tộc biểu / ADMIN — vai duy nhất đọc được cả nhóm `PRIVATE`.
  if (identity.clanWide) return "COUNCIL";

  const path = subject.primaryBranch?.path ?? null;
  const scopes = [...identity.managedBranches, identity.homeBranch].filter(
    (s): s is string => Boolean(s)
  );
  // Trưởng chi KHÔNG phải một hạng riêng: trong chi mình, họ là "người cùng
  // chi". Contract nói thẳng điều đó — "Trưởng chi được xem để liên hệ CHỈ KHI
  // privacy.contact ở mức BRANCH trở lên". Cho họ một nền Tầng 2 theo vai, như
  // bản dựng trước khi có contract, là tự ý mở dữ liệu mà chủ thể chưa đồng ý.
  if (scopes.some((scope) => pathIsWithin(scope, path) || pathIsWithin(path ?? "", scope))) {
    return "SAME_BRANCH";
  }
  return "CLAN_MEMBER";
}

/** Mức `scope` này có mở nhóm cho hạng người xem kia không. */
export function scopeAllows(scope: ShareScope, viewer: ViewerClass): boolean {
  // Khách không nằm trong mô hình: không mức nào mở dữ liệu người còn sống cho
  // khách, kể cả `CLAN`. Ranh giới pháp lý, không phải một lựa chọn bị ẩn đi.
  if (viewer === "GUEST") return false;
  if (viewer === "SELF" || viewer === "COUNCIL") return true;
  if (scope === "CLAN") return true;
  if (scope === "BRANCH") return viewer === "SAME_BRANCH";
  return false;
}

/**
 * Phép kiểm **thuần đồng thuận**, dùng riêng cho danh bạ.
 *
 * Khác `scopeAllows` ở HAI chỗ, và cả hai đều là bỏ một miễn trừ:
 *
 *  - **Hội đồng / `ADMIN` không được miễn trừ.** Danh bạ trả lời câu "ai đã tự
 *    chọn có mặt ở đây". Nếu quyền Hội đồng lọt vào câu trả lời ấy thì hai
 *    chuyện hỏng cùng lúc: danh bạ của Hội đồng đầy 100% trong khi của mọi
 *    người khác thưa — nên đúng người có trách nhiệm sửa lại là người duy nhất
 *    không thấy vấn đề; và tỉ lệ "218 / 627" mất nghĩa, vì nó phải nói cùng một
 *    điều với mọi người đọc nó.
 *  - **Chính chủ cũng không được miễn trừ.** Đây là chỗ đã dựng sai một lần:
 *    `scopeAllows` cho chính chủ thấy mọi thứ của mình (đúng, ở hồ sơ), nên
 *    danh bạ cũng liệt kê chính người đang đăng nhập kể cả khi họ chưa mở nhóm
 *    nào. Hệ quả: một người vừa đóng hết mức chia sẻ vẫn thấy tên mình trong
 *    danh bạ và kết luận rằng nút tắt không có tác dụng — đúng thứ mất lòng tin
 *    tệ nhất mà một màn hình riêng tư có thể gây ra. Danh bạ phải là **thứ dòng
 *    họ nhìn thấy**, kể cả khi người đang đọc là chủ thể.
 */
export function consentAllows(scope: ShareScope, viewer: ViewerClass): boolean {
  if (viewer === "GUEST") return false;
  if (scope === "CLAN") return true;
  if (scope === "BRANCH") return viewer === "SAME_BRANCH" || viewer === "SELF";
  return false;
}

/** Các nhóm mà chủ thể đã mở cho người gọi này. */
export function openedGroupsFor(
  subject: Pick<PersonDto, "id"> & { primaryBranch?: PersonDto["primaryBranch"] },
  identity: MockIdentity
): Set<keyof PrivacySettings> {
  const viewer = viewerClassFor(subject, identity);
  const settings = privacyOf(subject.id);
  const opened = new Set<keyof PrivacySettings>();
  for (const group of PRIVACY_GROUPS) {
    if (scopeAllows(settings[group], viewer)) opened.add(group);
  }
  return opened;
}

/**
 * Người chưa thành niên — ẩn tối đa, không mở được kể cả khi tự nguyện
 * (BA v2 §10, và `ShareScope` nói rõ luật này thắng mọi lựa chọn).
 *
 * Bản thật suy từ ngày sinh ở backend. Bộ giả lập suy từ năm sinh khi có, và
 * ngã về cờ `chuaThanhNienGiaLap` của bản ghi mẫu khi không có ngày sinh nào —
 * `p-101` là ca ấy, và đó chính là ca cần khoá để giao diện chứng minh được là
 * mình tôn trọng nó. Cờ ấy là của riêng bộ giả lập, KHÔNG phải trường hợp đồng.
 */
export function isMinorMock(person: PersonDto): boolean {
  if ((person as { chuaThanhNienGiaLap?: boolean }).chuaThanhNienGiaLap) return true;
  const year = person.birth?.solar ? Number(person.birth.solar.slice(0, 4)) : undefined;
  if (!year || Number.isNaN(year)) return false;
  return new Date().getFullYear() - year < 18;
}

/**
 * Người gọi này có được ĐỌC khối `privacy` của chủ thể không.
 *
 * Chỉ chính chủ và Hội đồng / `ADMIN`. Với mọi vai khác khối ấy phải vắng mặt
 * hoàn toàn khỏi JSON: biết người khác đang siết quyền riêng tư cũng là rò rỉ.
 */
export function canReadPrivacy(
  subject: Pick<PersonDto, "id"> & { primaryBranch?: PersonDto["primaryBranch"] },
  identity: MockIdentity
): boolean {
  const viewer = viewerClassFor(subject, identity);
  return viewer === "SELF" || viewer === "COUNCIL";
}
