import type {
  HeirKind,
  PersonBadge,
  PersonSummaryDto,
  RelType,
  RelationshipDto,
  TreeEdge,
  TreeProjection,
} from "@/types/api";

/**
 * Gom các cạnh quan hệ quanh MỘT nhân khẩu thành bốn nhóm gia phả quen thuộc
 * (cha mẹ · vợ/chồng · con · anh chị em) cộng một nhóm tràn cho người nối dõi.
 *
 * Ba ràng buộc chi phối toàn bộ tệp này:
 *
 * 1. **Không suy diễn danh xưng ở client.** Hàm này chỉ đọc `relType`, giới
 *    tính và các thuộc tính cạnh do máy chủ ghi. Nó KHÔNG sinh ra "anh/chị/em",
 *    "bác/chú/cậu" hay bất kỳ chữ xưng hô nào — đó là việc của bộ quy tắc
 *    danh xưng (`/api/v1/kinship`), vốn thay đổi theo vùng Bắc/Trung/Nam và
 *    theo từng dòng họ. Tên nhóm ở giao diện là *phân loại*, không phải danh xưng.
 *
 * 2. **Đầu kia không hiển thị được thì không có gì để vẽ.** Máy chủ chỉ trả cạnh
 *    mà cả hai đầu đều được phép hiển thị (openapi: `PersonDto.relationships`;
 *    `TreeProjection.edges`). Cạnh trỏ tới một người bị lọc đơn giản là không
 *    tồn tại trong phản hồi, nên ở đây không có (và không được có) chỗ nào đếm
 *    "còn bao nhiêu người bị giấu" — con số đó chính là thứ phân tầng đang che.
 *
 * 3. **Anh chị em cần độ sâu 2.** Anh chị em không phải cạnh trực tiếp: phải
 *    đi lên cha/mẹ rồi xuống các con khác. Vì vậy `usePersonRelations` vẫn gọi
 *    `/tree` với `depth=2, direction=BOTH` thay vì `depth=1`.
 *
 * ## Hai nguồn cho cùng một tập cạnh — và thứ tự ưu tiên giữa chúng
 *
 * Từ khi `RelationshipDto` mang `otherPerson`, `PersonDto.relationships` **tự
 * nó đã đủ** để vẽ bốn nhóm quan hệ trực tiếp (cha mẹ · vợ/chồng · con · nối
 * dõi): tên, đời và chi của người ở đầu kia đi kèm ngay trong phản hồi hồ sơ,
 * đã qua đúng bộ lọc phân tầng của người gọi. Trước đó màn quan hệ phải chờ
 * trọn một lượt `/tree` chỉ để đổi một danh sách id lấy một danh sách tên.
 *
 * Chiếu `/tree` vì thế không còn là nguồn chính, mà là phần **bổ khuyết** cho
 * đúng hai thứ mà hợp đồng không đặt ở chỗ nào khác:
 *
 *  - **anh chị em** — quan hệ hai bậc, `relationships` theo định nghĩa chỉ có
 *    một bậc (openapi: "quan hệ trực tiếp một bậc, **không** phải cả cây");
 *  - **`badges`** — `PersonSummaryDto` không có trường ấy, chỉ `TreeNode` có,
 *    và dâu/rể/đích tôn thì tuyệt đối không được suy ở client.
 *
 * Khi cả hai nguồn cùng nói về một người thì **chiếu `/tree` thắng**, vì nó là
 * bản duy nhất mang `badges`. Khi chỉ có `otherPerson` thì dòng quan hệ vẫn vẽ
 * được, chỉ thiếu nhãn — và thiếu nhãn thì im lặng, không có chỗ trống nào
 * được dựng lên để báo rằng ở đây lẽ ra có nhãn.
 *
 * ## `otherPerson` không bao giờ là một tóm tắt "đã che"
 *
 * Máy chủ loại **cả cạnh** khi đầu kia không hiển thị được. Nên ở đây không có
 * và không được có nhánh nào xử lý "có quan hệ nhưng không rõ với ai": trạng
 * thái ấy không tồn tại trên dây, và dựng nó lên là đếm hộ người xem số quan
 * hệ đang bị giấu.
 */

export type RelationGroupKey = "parents" | "spouses" | "children" | "siblings" | "heirs";

/** Thứ tự hiển thị trên hồ sơ — theo lối đọc gia phả: trên xuống, trong ra ngoài. */
export const RELATION_GROUP_ORDER: readonly RelationGroupKey[] = [
  "parents",
  "spouses",
  "children",
  "siblings",
  "heirs",
] as const;

export interface RelationEntry {
  /** Tóm tắt Tầng 1 của người ở đầu kia, y như máy chủ trả về. */
  person: PersonSummaryDto;
  /** Nhãn do máy chủ tính (dâu · rể · con nuôi · đích tôn · trưởng chi…). */
  badges: PersonBadge[];
  /**
   * Cạnh `PARENT_ADOPT` — con nuôi / cha mẹ nuôi. Con nuôi và con ruột là hai
   * loại cạnh khác nhau chứ không phải một cờ trên cùng cạnh.
   */
  adoptive: boolean;
  /** Chỉ có nghĩa với vợ/chồng: `1` = vợ cả/chồng cả, `2`, `3`… cho đa thê/đa phu. */
  spouseOrder: number | null;
  /** `true` khi cạnh hôn phối đã có `validTo` — ly hôn hoặc một bên đã mất. */
  ended: boolean;
  /** Ngày kết thúc hôn phối, đúng chuỗi máy chủ gửi (có thể chỉ có năm). */
  endedOn: string | null;
  /** Đích tôn / thừa tự / kế tự — do máy chủ ghi trên cạnh `HEIR`. */
  heirKind: HeirKind | null;
}

export type RelationGroups = Record<RelationGroupKey, RelationEntry[]>;

function emptyGroups(): RelationGroups {
  return { parents: [], spouses: [], children: [], siblings: [], heirs: [] };
}

export function isEmptyRelationGroups(groups: RelationGroups): boolean {
  return RELATION_GROUP_ORDER.every((key) => groups[key].length === 0);
}

export function countRelations(groups: RelationGroups): number {
  return RELATION_GROUP_ORDER.reduce((sum, key) => sum + groups[key].length, 0);
}

const isParentEdge = (e: TreeEdge) =>
  e.relType === "PARENT_BIO" || e.relType === "PARENT_ADOPT";

function endsKey(source: string, target: string, relType: RelType): string {
  return `${source}->${target}:${relType}`;
}

/**
 * `PersonDto.relationships` và `TreeProjection.edges` mô tả cùng một tập cạnh
 * qua hai transport khác nhau, nhưng `RelationshipDto` mang thêm `note` và
 * `validFrom` mà `TreeEdge` không có. Lập chỉ mục để bù thuộc tính cạnh, khớp
 * theo `id` trước, không có thì khớp theo cặp đầu–cuối.
 */
function indexRelationships(relationships: readonly RelationshipDto[] | null | undefined) {
  const byId = new Map<string, RelationshipDto>();
  const byEnds = new Map<string, RelationshipDto>();
  for (const rel of relationships ?? []) {
    byId.set(rel.id, rel);
    byEnds.set(endsKey(rel.fromPersonId, rel.toPersonId, rel.relType), rel);
  }
  return { byId, byEnds };
}

/**
 * `RelationshipDto` → hình dạng cạnh chung. Hai transport mô tả cùng một tập
 * cạnh, và `TreeEdge` là bản hẹp hơn, nên quy về nó là quy về mẫu số chung —
 * `note`/`validFrom` chỉ có ở `RelationshipDto` vẫn lấy lại được qua
 * `indexRelationships`.
 */
function relationshipToEdge(rel: RelationshipDto): TreeEdge {
  return {
    id: rel.id,
    source: rel.fromPersonId,
    target: rel.toPersonId,
    relType: rel.relType,
    heirKind: rel.heirKind ?? null,
    spouseOrder: rel.spouseOrder ?? null,
    validTo: rel.validTo ?? null,
  };
}

/**
 * Hợp nhất cạnh từ hai nguồn, khử trùng theo `id` rồi theo cặp đầu–cuối.
 *
 * Khử theo cặp đầu–cuối là bắt buộc chứ không phải phòng xa: hai transport
 * không hứa dùng chung `id` cạnh, và một cạnh lọt hai lần sẽ sinh hai dòng
 * trùng tên trong cùng một nhóm.
 */
function mergeEdges(
  projection: TreeProjection | null | undefined,
  relationships: readonly RelationshipDto[] | null | undefined
): TreeEdge[] {
  const merged: TreeEdge[] = [];
  const seenIds = new Set<string>();
  const seenEnds = new Set<string>();

  const push = (edge: TreeEdge) => {
    const ends = endsKey(edge.source, edge.target, edge.relType);
    if (seenIds.has(edge.id) || seenEnds.has(ends)) return;
    seenIds.add(edge.id);
    seenEnds.add(ends);
    merged.push(edge);
  };

  // Chiếu `/tree` đi trước: bản của nó là bản gắn được với `badges`.
  for (const edge of projection?.edges ?? []) push(edge);
  for (const rel of relationships ?? []) push(relationshipToEdge(rel));
  return merged;
}

interface ResolvedPerson {
  readonly person: PersonSummaryDto;
  readonly badges: PersonBadge[];
}

/**
 * Ai là ai, gộp từ hai nguồn. Nút của `/tree` ghi đè tóm tắt nhúng vì chỉ nó
 * mang `badges`; người chỉ có trong `relationships` vẫn vào được chỉ mục, với
 * danh sách nhãn rỗng.
 */
function buildPersonIndex(
  projection: TreeProjection | null | undefined,
  relationships: readonly RelationshipDto[] | null | undefined
): Map<string, ResolvedPerson> {
  const index = new Map<string, ResolvedPerson>();
  for (const rel of relationships ?? []) {
    const other = rel.otherPerson;
    if (other) index.set(other.id, { person: other, badges: [] });
  }
  for (const node of projection?.nodes ?? []) {
    index.set(node.id, { person: node.person, badges: node.badges ?? [] });
  }
  return index;
}

/**
 * Có thể vẽ bốn nhóm quan hệ trực tiếp mà **không cần** lượt `/tree` nào không?
 *
 * Trả `true` chỉ khi mọi cạnh đều mang `otherPerson`. Một cạnh thiếu là đủ để
 * rơi về đường cũ: vẽ thiếu một người cha là một lỗi dữ liệu nhìn thấy được,
 * còn chờ thêm một lượt mạng thì chỉ là chậm.
 */
export function hasEmbeddedOtherPersons(
  relationships: readonly RelationshipDto[] | null | undefined
): boolean {
  if (!relationships || relationships.length === 0) return false;
  return relationships.every((rel) => Boolean(rel.otherPerson));
}

/** Cha đứng trước mẹ theo lối ghi gia phả; giới tính không rõ xếp cuối. */
function genderRank(entry: RelationEntry): number {
  if (entry.person.gender === "MALE") return 0;
  if (entry.person.gender === "FEMALE") return 1;
  return 2;
}

function compareName(a: RelationEntry, b: RelationEntry): number {
  return a.person.displayName.localeCompare(b.person.displayName, "vi");
}

/** Thứ tự sinh khi biết được; ai không có năm sinh xếp sau, rồi so tên. */
function compareBirthOrder(a: RelationEntry, b: RelationEntry): number {
  const ya = a.person.birthYear;
  const yb = b.person.birthYear;
  if (ya != null && yb != null && ya !== yb) return ya - yb;
  if (ya != null && yb == null) return -1;
  if (ya == null && yb != null) return 1;
  return compareName(a, b);
}

export interface BuildRelationGroupsInput {
  personId: string;
  /** Chiếu `/tree` quanh nhân khẩu này (`depth=2, direction=BOTH`). */
  projection: TreeProjection | null | undefined;
  /** `PersonDto.relationships` nếu có — chỉ dùng để bù thuộc tính cạnh. */
  relationships?: readonly RelationshipDto[] | null;
}

export function buildRelationGroups({
  personId,
  projection,
  relationships,
}: BuildRelationGroupsInput): RelationGroups {
  const groups = emptyGroups();

  const personById = buildPersonIndex(projection, relationships);
  const allEdges = mergeEdges(projection, relationships);
  if (allEdges.length === 0) return groups;

  const index = indexRelationships(relationships);

  /**
   * Thêm một người vào nhóm. Bỏ qua nếu người đó không có mặt trong `nodes`:
   * đó là đầu kia đã bị lọc, và nó phải im lặng biến mất chứ không thành một
   * dòng "không rõ" — một dòng như thế là lời thông báo "có người ở đây".
   */
  function add(list: RelationEntry[], otherId: string, edge: TreeEdge): void {
    const resolved = personById.get(otherId);
    if (!resolved) return;
    if (list.some((entry) => entry.person.id === otherId)) return;

    const rel =
      index.byId.get(edge.id) ?? index.byEnds.get(endsKey(edge.source, edge.target, edge.relType));

    list.push({
      person: resolved.person,
      badges: resolved.badges,
      adoptive: edge.relType === "PARENT_ADOPT",
      spouseOrder: edge.spouseOrder ?? rel?.spouseOrder ?? null,
      ended: Boolean(edge.validTo ?? rel?.validTo),
      endedOn: edge.validTo ?? rel?.validTo ?? null,
      heirKind: edge.heirKind ?? rel?.heirKind ?? null,
    });
  }

  const parentEdges = allEdges.filter(isParentEdge);

  // --- Cha mẹ & con: cạnh PARENT_* chạm thẳng vào nhân khẩu này -------------
  for (const edge of parentEdges) {
    if (edge.target === personId) add(groups.parents, edge.source, edge);
    else if (edge.source === personId) add(groups.children, edge.target, edge);
  }

  // --- Vợ/chồng: cạnh SPOUSE không định hướng về mặt ý nghĩa ----------------
  for (const edge of allEdges) {
    if (edge.relType !== "SPOUSE") continue;
    const other = otherEnd(edge, personId);
    if (other) add(groups.spouses, other, edge);
  }

  // --- Anh chị em: con của cha/mẹ mình, trừ chính mình ----------------------
  // Gồm cả anh chị em cùng cha khác mẹ (nửa dòng máu) vì chỉ cần CHUNG MỘT
  // cha hoặc mẹ là đủ; gia phả Việt ghi nhận họ như anh chị em thật.
  const parentIds = new Set(groups.parents.map((entry) => entry.person.id));
  for (const edge of parentEdges) {
    if (!parentIds.has(edge.source)) continue;
    if (edge.target === personId) continue;
    add(groups.siblings, edge.target, edge);
  }

  // --- Nối dõi: cạnh HEIR, gắn heirKind vào dòng đã có nếu trùng người ------
  for (const edge of allEdges) {
    if (edge.relType !== "HEIR") continue;
    const other = otherEnd(edge, personId);
    if (!other) continue;

    const rel =
      index.byId.get(edge.id) ?? index.byEnds.get(endsKey(edge.source, edge.target, edge.relType));
    const heirKind = edge.heirKind ?? rel?.heirKind ?? null;

    const existing = findEntry(groups, other);
    if (existing) {
      existing.heirKind = heirKind;
      continue;
    }
    // Kế tự lấy từ chi khác thường KHÔNG có cạnh cha–con, nên phải có chỗ
    // riêng, nếu không người nối dõi sẽ biến mất khỏi hồ sơ.
    add(groups.heirs, other, edge);
    const added = groups.heirs.find((entry) => entry.person.id === other);
    if (added) added.heirKind = heirKind;
  }

  groups.parents.sort((a, b) => genderRank(a) - genderRank(b) || compareName(a, b));
  groups.spouses.sort(
    (a, b) => (a.spouseOrder ?? Number.MAX_SAFE_INTEGER) - (b.spouseOrder ?? Number.MAX_SAFE_INTEGER) || compareName(a, b)
  );
  groups.children.sort(compareBirthOrder);
  groups.siblings.sort(compareBirthOrder);
  groups.heirs.sort(compareName);

  return groups;
}

function otherEnd(edge: TreeEdge, personId: string): string | null {
  if (edge.source === personId) return edge.target;
  if (edge.target === personId) return edge.source;
  return null;
}

function findEntry(groups: RelationGroups, personId: string): RelationEntry | undefined {
  for (const key of ["parents", "spouses", "children", "siblings"] as const) {
    const hit = groups[key].find((entry) => entry.person.id === personId);
    if (hit) return hit;
  }
  return undefined;
}

/**
 * Có nên hiện nhãn thứ tự vợ/chồng không.
 *
 * Với một vợ/một chồng duy nhất mang `spouseOrder = 1` thì nhãn "vợ cả" là
 * thừa và còn gợi ý sai rằng có người thứ hai. Chỉ hiện khi thực sự đa thê/đa
 * phu (nhiều cạnh) hoặc khi thứ tự tự nó đã từ 2 trở lên.
 */
export function shouldShowSpouseOrder(entry: RelationEntry, spouseCount: number): boolean {
  if (entry.spouseOrder == null) return false;
  return entry.spouseOrder >= 2 || spouseCount > 1;
}
