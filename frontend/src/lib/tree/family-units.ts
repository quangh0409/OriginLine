import type { TreeEdge, TreeNode } from "@/types/api";
import type { FamilyUnit } from "./family-layout-types";

/**
 * Suy ra <b>đơn vị gia đình</b> (một cuộc hôn phối + đúng những đứa con của riêng cuộc hôn phối đó)
 * từ dữ liệu phả đồ phẳng mà backend trả về.
 *
 * <p>Đây là bước B1 của phả đồ dựng lại: tầng bố cục và tầng vẽ chỉ làm việc với {@link FamilyUnit},
 * không đụng lại vào {@code TreeEdge} nữa. Tách thành hàm thuần vì đúng chỗ này là nơi gia phả Việt
 * phá vỡ mọi thư viện vẽ cây có sẵn — <b>đa thê</b>, <b>tái hôn</b>, <b>con nuôi</b>, <b>cha/mẹ đơn
 * thân</b> — nên nó phải test được độc lập, không cần trình duyệt.</p>
 *
 * <h2>Những quyết định đã chốt (đọc trước khi sửa)</h2>
 *
 * <ul>
 *   <li><b>Không suy diễn người còn thiếu.</b> Một đứa con chỉ ghi được cha (gia phả cổ rất hay như
 *   vậy, và backend cũng che bớt người còn sống theo tầng riêng tư) thì nó nằm ở đơn vị <i>cha đơn
 *   thân</i>, KHÔNG tự động gán sang bà vợ đang đứng cạnh. Gán bừa là bịa ra một quan hệ mẹ–con:
 *   sai với con của bà vợ khác, sai với con riêng, và làm rò rỉ suy đoán về người bị che.</li>
 *
 *   <li><b>Mỗi người con thuộc đúng MỘT đơn vị</b> (bố cục cần bất biến này để xếp chỗ thẻ), nhưng
 *   <b>một người bạn đời có thể thuộc NHIỀU đơn vị</b> — đó chính là ca tái hôn/đa thê, và không
 *   đơn vị nào được phép biến mất.</li>
 *
 *   <li>Cạnh {@code HEIR} (đích tôn/thừa tự/kế tự) <b>không</b> sinh ra đơn vị gia đình: nó là quan
 *   hệ thừa kế, không phải hôn phối hay sinh thành. Tầng bố cục vẽ nó thành đường phụ
 *   ({@code AuxiliaryLink.kind = "HEIR"}).</li>
 * </ul>
 */

/** Tiền tố id đơn vị, để không bao giờ đụng id nhân khẩu khi cả hai cùng làm khoá React Flow. */
const UNIT_ID_PREFIX = "fu:";
const ID_SEPARATOR = "+";

/**
 * So sánh id theo mã ký tự. <b>Cố tình không dùng</b> {@code localeCompare}: nó phụ thuộc locale của
 * máy chạy, nên cùng một cây có thể ra thứ tự khác nhau giữa máy dev và máy người dùng — đúng kiểu
 * lỗi làm id đơn vị đổi giữa hai lần dựng và khiến cả canvas nhấp nháy.
 */
function byCodeUnit(a: string, b: string): number {
  if (a < b) return -1;
  if (a > b) return 1;
  return 0;
}

/**
 * Id đơn vị gia đình — suy hoàn toàn từ id các thành viên nên <b>ổn định giữa các lần dựng lại</b>
 * (không đếm, không {@code Math.random}). Bố cục và React Flow dùng id này làm khoá.
 *
 * <p>Xuất ra ngoài để tầng bố cục/vẽ tra ngược được đơn vị của một cặp mà không phải đoán quy ước
 * đặt tên.</p>
 */
export function familyUnitId(partnerIds: readonly string[]): string {
  return UNIT_ID_PREFIX + [...partnerIds].sort(byCodeUnit).join(ID_SEPARATOR);
}

/** Một liên kết cha/mẹ → con đã gộp các cạnh trùng. */
interface ParentLink {
  readonly parentId: string;
  /** Chỉ đúng khi mọi cạnh nối cặp này đều là {@code PARENT_ADOPT}. */
  readonly adopted: boolean;
}

interface MutableParentLink {
  parentId: string;
  sawBio: boolean;
  sawAdopt: boolean;
}

interface MutableUnit {
  id: string;
  memberIds: string[];
  childIds: string[];
  adoptedChildIds: Set<string>;
}

/**
 * Gom {@code nodes} + {@code edges} thành danh sách đơn vị gia đình.
 *
 * <p>Chịu được dữ liệu khuyết: cây tải theo lô nên id trỏ ra ngoài {@code nodes} là chuyện thường —
 * những id đó bị bỏ qua chứ không ném lỗi. Người chưa có vợ/chồng/con thì không sinh đơn vị nào.</p>
 *
 * @param nodes nhân khẩu đang có trong projection (thứ tự mảng là thứ tự backend trả về)
 * @param edges quan hệ; chỉ những cạnh có <b>cả hai đầu</b> nằm trong {@code nodes} mới được dùng
 * @returns danh sách đơn vị, thứ tự tất định (theo thành viên xuất hiện sớm nhất, rồi theo id)
 */
export function buildFamilyUnits(
  nodes: readonly TreeNode[],
  edges: readonly TreeEdge[]
): FamilyUnit[] {
  const nodeById = new Map<string, TreeNode>();
  /** Thứ tự xuất hiện trong projection — mốc tất định dùng thay cho thứ tự duyệt Map. */
  const inputOrder = new Map<string, number>();
  for (const n of nodes ?? []) {
    if (!n || typeof n.id !== "string" || nodeById.has(n.id)) continue;
    inputOrder.set(n.id, nodeById.size);
    nodeById.set(n.id, n);
  }
  if (nodeById.size === 0) return [];

  const orderOf = (id: string): number => inputOrder.get(id) ?? Number.MAX_SAFE_INTEGER;
  const present = (id: unknown): id is string => typeof id === "string" && nodeById.has(id);

  const safeEdges = (edges ?? []).filter(
    (e): e is TreeEdge =>
      Boolean(e) && present(e.source) && present(e.target) && e.source !== e.target
  );

  // ---------------------------------------------------------------------------------------------
  // 1. Hôn phối
  // ---------------------------------------------------------------------------------------------
  /** khoá cặp (chính là id đơn vị của cặp đó) → các cạnh SPOUSE giữa hai người. */
  const marriageEdges = new Map<string, TreeEdge[]>();
  /** khoá cặp → hai id thành viên, đã sắp. */
  const marriagePartners = new Map<string, string[]>();

  const registerMarriage = (a: string, b: string, e: TreeEdge | null): void => {
    const key = familyUnitId([a, b]);
    if (!marriagePartners.has(key)) {
      marriagePartners.set(key, [a, b].sort(byCodeUnit));
      marriageEdges.set(key, []);
    }
    if (e) marriageEdges.get(key)!.push(e);
  };

  for (const e of safeEdges) {
    if (e.relType === "SPOUSE") registerMarriage(e.source, e.target, e);
  }
  // Lưới an toàn: hợp đồng nói hễ cả hai đầu cùng hiện thì phải có cạnh, nhưng nếu projection lỡ
  // thiếu cạnh mà `spouseIds` vẫn ghi nhận thì thà dựng một hôn phối không rõ thứ bậc còn hơn đánh
  // rơi cả một cuộc hôn phối khỏi phả đồ.
  for (const n of nodeById.values()) {
    for (const sid of n.spouseIds ?? []) {
      if (!present(sid) || sid === n.id) continue;
      registerMarriage(n.id, sid, null);
    }
  }

  /** Số cuộc hôn phối của mỗi người — dấu hiệu nhận ra người trục trong ca đa thê. */
  const marriageCount = new Map<string, number>();
  for (const partners of marriagePartners.values()) {
    for (const id of partners) marriageCount.set(id, (marriageCount.get(id) ?? 0) + 1);
  }

  // ---------------------------------------------------------------------------------------------
  // 2. Liên kết cha/mẹ → con
  // ---------------------------------------------------------------------------------------------
  const parentsOfChild = new Map<string, Map<string, MutableParentLink>>();
  const linkParent = (parentId: string, childId: string, adopted: boolean): void => {
    let byParent = parentsOfChild.get(childId);
    if (!byParent) {
      byParent = new Map<string, MutableParentLink>();
      parentsOfChild.set(childId, byParent);
    }
    const link = byParent.get(parentId) ?? { parentId, sawBio: false, sawAdopt: false };
    if (adopted) link.sawAdopt = true;
    else link.sawBio = true;
    byParent.set(parentId, link);
  };

  for (const e of safeEdges) {
    if (e.relType === "PARENT_BIO") linkParent(e.source, e.target, false);
    else if (e.relType === "PARENT_ADOPT") linkParent(e.source, e.target, true);
  }
  // Cùng lý do như `spouseIds`. Không có cạnh thì không biết con đẻ hay con nuôi, mà "con nuôi" là
  // một khẳng định về thân phận nên không được đoán — mặc định coi là con đẻ.
  for (const n of nodeById.values()) {
    for (const pid of n.parentIds ?? []) {
      if (!present(pid) || pid === n.id) continue;
      if (parentsOfChild.get(n.id)?.has(pid)) continue;
      linkParent(pid, n.id, false);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 3. Dựng đơn vị
  // ---------------------------------------------------------------------------------------------
  const units = new Map<string, MutableUnit>();
  const ensureUnit = (memberIds: readonly string[]): MutableUnit => {
    const id = familyUnitId(memberIds);
    let unit = units.get(id);
    if (!unit) {
      unit = {
        id,
        memberIds: [...memberIds].sort(byCodeUnit),
        childIds: [],
        adoptedChildIds: new Set<string>(),
      };
      units.set(id, unit);
    }
    return unit;
  };

  // Hôn phối chưa có con vẫn là một đơn vị: thiếu nó thì không có thanh hôn phối để vẽ.
  for (const partners of marriagePartners.values()) ensureUnit(partners);

  // Duyệt con theo thứ tự projection để kết quả không phụ thuộc thứ tự duyệt Map.
  const childIdsInOrder = [...parentsOfChild.keys()].sort(
    (a, b) => orderOf(a) - orderOf(b) || byCodeUnit(a, b)
  );
  for (const childId of childIdsInOrder) {
    const links: ParentLink[] = [...(parentsOfChild.get(childId)?.values() ?? [])]
      .map((l) => ({ parentId: l.parentId, adopted: l.sawAdopt && !l.sawBio }))
      .sort(
        (a, b) => orderOf(a.parentId) - orderOf(b.parentId) || byCodeUnit(a.parentId, b.parentId)
      );
    if (links.length === 0) continue;

    const group = choosePartnerGroup(links, marriagePartners, orderOf);
    const unit = ensureUnit(group.map((l) => l.parentId));
    unit.childIds.push(childId);
    // Con nuôi ở mức ĐƠN VỊ: chỉ cần một trong hai người là cha/mẹ nuôi thì đứa con đó không phải
    // con đẻ của cặp này (ca kinh điển: con riêng của chồng được mẹ kế nhận nuôi), nên đoạn rơi
    // xuống nó vẽ nét đứt. Nó VẪN nằm trong `childIds` — vẫn là con của gia đình đó.
    if (group.some((l) => l.adopted)) unit.adoptedChildIds.add(childId);
  }

  // ---------------------------------------------------------------------------------------------
  // 4. Kết xuất
  // ---------------------------------------------------------------------------------------------
  const result: FamilyUnit[] = [...units.values()].map((unit) => {
    const marriage = unit.memberIds.length === 2 ? marriageEdges.get(unit.id) ?? null : null;
    const primaryEdge = marriage && marriage.length > 0 ? pickPrimarySpouseEdge(marriage) : null;
    return {
      id: unit.id,
      partnerIds: unit.memberIds,
      anchorId: pickAnchor(unit.memberIds, marriageCount, parentsOfChild, nodeById, orderOf),
      spouseOrder: primaryEdge?.spouseOrder ?? null,
      childIds: sortChildren(unit.childIds, nodeById, orderOf),
      adoptedChildIds: unit.adoptedChildIds,
      // Hôn phối chỉ coi là đã kết thúc khi MỌI cạnh SPOUSE giữa hai người đều có `validTo` — cưới
      // lại đúng người cũ thì cuộc hôn phối hiện thời vẫn đang tiếp diễn. Dù đã kết thúc, đơn vị
      // vẫn ở lại trên cây: giấu đi là làm đứt liên kết của con cái cuộc hôn phối đó.
      ended: Boolean(marriage && marriage.length > 0 && marriage.every((e) => hasEnded(e))),
    } satisfies FamilyUnit;
  });

  return result.sort((a, b) => {
    const oa = Math.min(...a.partnerIds.map(orderOf));
    const ob = Math.min(...b.partnerIds.map(orderOf));
    return oa - ob || byCodeUnit(a.id, b.id);
  });
}

/** {@code validTo} rỗng/toàn khoảng trắng không tính là đã kết thúc. */
function hasEnded(e: TreeEdge): boolean {
  return typeof e.validTo === "string" && e.validTo.trim().length > 0;
}

/**
 * Chọn cạnh SPOUSE "chính" của một cặp khi có nhiều cạnh (ly hôn rồi tái hợp cùng một người).
 * Ưu tiên cuộc hôn phối còn hiệu lực, rồi tới {@code spouseOrder} nhỏ nhất, cuối cùng theo id cạnh
 * để kết quả tất định.
 */
function pickPrimarySpouseEdge(candidates: readonly TreeEdge[]): TreeEdge {
  return [...candidates].sort((a, b) => {
    const ea = hasEnded(a) ? 1 : 0;
    const eb = hasEnded(b) ? 1 : 0;
    if (ea !== eb) return ea - eb;
    const sa = a.spouseOrder ?? Number.MAX_SAFE_INTEGER;
    const sb = b.spouseOrder ?? Number.MAX_SAFE_INTEGER;
    if (sa !== sb) return sa - sb;
    return byCodeUnit(a.id ?? "", b.id ?? "");
  })[0]!;
}

/**
 * Quyết định đứa con này thuộc cặp cha–mẹ nào.
 *
 * <p>Thứ tự ưu tiên: (1) con đẻ trước — nếu có từ hai người là cha/mẹ đẻ thì chỉ xét những người
 * đó; (2) trong nhóm ứng viên, ưu tiên cặp thật sự có hôn phối; (3) không cặp nào có hôn phối thì
 * vẫn ghép hai người đầu thành một đơn vị "đồng sinh thành" — cha mẹ không có cạnh SPOUSE là ca hợp
 * lệ, không được đánh rơi đứa con; (4) chỉ biết một người thì thành đơn vị cha/mẹ đơn thân.</p>
 *
 * <p>Nếu đứa con còn liên kết với cha/mẹ thứ ba (con nuôi đến từ chi khác chẳng hạn), liên kết dôi
 * ra đó KHÔNG bị nhét vào đơn vị — tầng bố cục vẽ nó thành
 * {@code AuxiliaryLink.kind = "CROSS_BRANCH_PARENT"}. Bất biến "mỗi con đúng một đơn vị" quan trọng
 * hơn, vì thẻ nhân khẩu chỉ có một chỗ đứng.</p>
 */
function choosePartnerGroup(
  links: readonly ParentLink[],
  marriagePartners: ReadonlyMap<string, string[]>,
  orderOf: (id: string) => number
): ParentLink[] {
  const bio = links.filter((l) => !l.adopted);
  const candidates = bio.length >= 2 ? bio : links;

  let best: { pair: [ParentLink, ParentLink]; rank: number[] } | null = null;
  for (let i = 0; i < candidates.length; i++) {
    for (let j = i + 1; j < candidates.length; j++) {
      const a = candidates[i]!;
      const b = candidates[j]!;
      if (!marriagePartners.has(familyUnitId([a.parentId, b.parentId]))) continue;
      const rank = [
        a.adopted || b.adopted ? 1 : 0,
        orderOf(a.parentId),
        orderOf(b.parentId),
      ];
      if (!best || compareRank(rank, best.rank) < 0) best = { pair: [a, b], rank };
    }
  }
  if (best) return [best.pair[0], best.pair[1]];
  if (candidates.length >= 2) return [candidates[0]!, candidates[1]!];
  return [candidates[0]!];
}

function compareRank(a: readonly number[], b: readonly number[]): number {
  for (let i = 0; i < a.length; i++) {
    const d = (a[i] ?? 0) - (b[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
}

/**
 * Chọn người trục của đơn vị.
 *
 * <p>Tiêu chí, theo đúng thứ tự: (1) <b>ai có nhiều bạn đời hơn</b> — Hội đồng đã chốt trong ca đa
 * thê thì người chồng đứng giữa, các bà toả ra hai bên; (2) ai là <b>người trong họ</b> (có cha/mẹ
 * nằm trong phả đồ) thay vì dâu/rể, để trục của đơn vị nằm trên huyết thống của chi; (3) nam trước
 * — thuần tuý là quy ước vẽ của phả đồ truyền thống, chỉ dùng khi hai tiêu chí trên đã hoà, và
 * không đụng gì tới nguyên tắc con gái/bên ngoại được ghi nhận đầy đủ ngang con trai; (4) ai xuất
 * hiện sớm hơn trong projection; (5) id — để không bao giờ hoà.</p>
 */
function pickAnchor(
  memberIds: readonly string[],
  marriageCount: ReadonlyMap<string, number>,
  parentsOfChild: ReadonlyMap<string, Map<string, MutableParentLink>>,
  nodeById: ReadonlyMap<string, TreeNode>,
  orderOf: (id: string) => number
): string {
  if (memberIds.length === 1) return memberIds[0]!;
  const rankOf = (id: string): number[] => [
    -(marriageCount.get(id) ?? 0),
    (parentsOfChild.get(id)?.size ?? 0) > 0 ? 0 : 1,
    nodeById.get(id)?.person?.gender === "MALE" ? 0 : 1,
    orderOf(id),
  ];
  return [...memberIds].sort((a, b) => compareRank(rankOf(a), rankOf(b)) || byCodeUnit(a, b))[0]!;
}

/**
 * Sắp con theo thứ tự sinh khi biết được.
 *
 * <p>Chỉ dùng năm sinh khi <b>mọi</b> đứa con của đơn vị đều có năm sinh: trộn "biết năm" với
 * "không biết năm" chỉ tạo ra một thứ tự trông có vẻ đúng mà thật ra sai. Người còn sống bị che ở
 * tầng T1 thì không có {@code birthYear}, nên trường hợp này rất hay gặp — khi đó giữ nguyên thứ tự
 * backend trả về (xấp xỉ tốt nhất còn lại), rồi mới tới id. Cả hai nhánh đều tất định.</p>
 */
function sortChildren(
  childIds: readonly string[],
  nodeById: ReadonlyMap<string, TreeNode>,
  orderOf: (id: string) => number
): string[] {
  const yearOf = (id: string): number | null => {
    const y = nodeById.get(id)?.person?.birthYear;
    return typeof y === "number" && Number.isFinite(y) ? y : null;
  };
  const allYearsKnown = childIds.every((id) => yearOf(id) !== null);
  return [...childIds].sort((a, b) => {
    if (allYearsKnown) {
      const d = (yearOf(a) ?? 0) - (yearOf(b) ?? 0);
      if (d !== 0) return d;
    }
    return orderOf(a) - orderOf(b) || byCodeUnit(a, b);
  });
}
