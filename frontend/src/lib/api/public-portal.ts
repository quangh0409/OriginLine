import { apiFetch } from "./http";
import type {
  BranchRef,
  DateDual,
  Gender,
  HeirKind,
  NameType,
  PersonAccessMeta,
  PersonDto,
  PersonName,
  PersonSummaryDto,
  RelType,
  TreeDirection,
  TreeProjection,
} from "@/types/api";

/**
 * Cổng thông tin **công khai** — `/api/v1/public/**`.
 *
 * <h2>Vì sao có một lớp riêng thay vì thêm một cờ vào `tree.ts`/`persons.ts`</h2>
 * Đây không phải cùng một endpoint với quyền khác. Máy chủ ép ngữ cảnh về Khách
 * (`PublicGuestScope.asGuest`) **bất kể** người gọi có mang token hay không, trả
 * `Cache-Control: public`, và dùng một bộ DTO hẹp hơn hẳn — `PublicPersonSummaryDto`
 * **không có chỗ** để chứa dữ liệu Tầng 2/Tầng 3. Trộn hai bề mặt vào một hàm là
 * cách chắc chắn nhất để một ngày nào đó một trường Tầng 3 đi nhầm đường và
 * không ai phát hiện, vì kiểu dữ liệu đã cho phép nó tồn tại.
 *
 * <h2>Ba khác biệt phải nhớ khi gọi</h2>
 * <ol>
 *   <li><b>`rootId` là TUỲ CHỌN</b>. Thiếu nó, cổng công khai mở ra **thuỷ tổ**
 *       — nhân khẩu đời nhỏ nhất, đã khuất. Trước đây nó bắt buộc và điều đó
 *       làm trang phả đồ công khai, cửa vào của cả nửa *cổng thông tin dòng
 *       họ*, trả `400` cho mọi Khách. Gốc mặc định ở đây **không phụ thuộc
 *       token**: một thành viên lỡ mang `Authorization` tới vẫn nhận đúng cây
 *       của Khách, nếu không thì `Cache-Control: public` trên chính nó thành
 *       một lỗ rò. Xem `src/lib/tree/root-id.ts`.</li>
 *   <li><b>Trần thấp hơn hẳn bản thành viên</b>: `depth` tối đa 4 (thành viên 10),
 *       máy chủ tự cắt ở 200 node (thành viên 2000) và <b>không</b> nhận `maxNodes`.
 *       Gửi `depth` quá trần là `400`, không phải âm thầm cắt.</li>
 *   <li><b>Cây trả về có LỖ</b>: người còn sống bị bỏ cùng mọi cạnh chạm vào họ,
 *       nên `parentIds` rỗng là chuyện bình thường và phả đồ có thể gồm nhiều mảnh
 *       rời. Không bao giờ coi một cạnh thiếu là lỗi dữ liệu, và tuyệt đối không
 *       "vá" bằng node ẩn danh hay nối tắt — cả hai đều tiết lộ đúng thứ đang giấu.</li>
 * </ol>
 */

/** Trần `depth` của `/public/tree` (`giapha.public-portal.max-tree-depth`). */
export const PUBLIC_TREE_MAX_DEPTH = 4;

/** Từ khoá ngắn hơn ngần này là phép liệt kê trá hình — máy chủ trả `400`. */
export const PUBLIC_SEARCH_MIN_QUERY_LENGTH = 2;

/** Trang cuối cùng cổng công khai còn trả lời (0-based). */
export const PUBLIC_SEARCH_MAX_PAGE = 4;

/**
 * Bản gọn của một người **đã khuất**. `isAlive` luôn `false`.
 *
 * `avatarKey` là **khoá đối tượng MinIO**, không phải URL và không mở được tệp
 * nào ở Giai đoạn 1 — vì thế nó cố ý **không** được ánh xạ sang `avatarUrl` khi
 * đổi sang `PersonSummaryDto`: gán vào đó là dựng một thẻ `<img>` chắc chắn hỏng.
 */
export interface PublicPersonSummaryDto {
  id: string;
  displayName: string;
  nameHanNom?: string | null;
  gender?: Gender;
  generation?: number | null;
  isAlive: false;
  birthYear?: number | null;
  deathYear?: number | null;
  primaryBranch?: BranchRef | null;
  nativePlace?: string | null;
  avatarKey?: string | null;
  matchedNameType?: NameType | null;
}

/**
 * Phân trang công khai: **không có `totalElements`**, và điều đó là cố ý — một
 * con số tổng chính là phép đếm dân số dòng họ. `hasNext === false` cũng có
 * nghĩa "đã chạm trần trang công khai", không chỉ "hết kết quả".
 */
export interface PublicPageMeta {
  page: number;
  size: number;
  hasNext: boolean;
}

export interface PublicPersonSummaryPage {
  items: PublicPersonSummaryDto[];
  page: PublicPageMeta;
}

/**
 * Quan hệ một bậc trên bề mặt công khai — **chỉ những cạnh mà đầu kia cũng đã
 * khuất**. Nó mang id chứ không mang tóm tắt của người kia, nên **không đủ để
 * dựng mục "Quan hệ"**: vẽ ra một danh sách id là vừa vô nghĩa với người đọc
 * vừa là một phép đếm.
 */
export interface PublicRelationDto {
  id: string;
  fromPersonId: string;
  toPersonId: string;
  relType: RelType;
  heirKind?: HeirKind | null;
  spouseOrder?: number | null;
}

/**
 * Hồ sơ đầy đủ của một người **đã khuất**, bản công khai.
 *
 * Hẹp hơn `PersonDto` một cách có chủ ý: **không có** `contact`, `occupation`,
 * `currentPlaceProvince`, `currentPlaceFull`, `attributes`, `privacy`,
 * `version`, `access`. Số điện thoại ghi trong hồ sơ một cụ đã mất trên thực tế
 * là số của người thân **đang sống**.
 */
export interface PublicPersonDto {
  id: string;
  names: PersonName[];
  displayName: string;
  gender?: Gender;
  generation?: number | null;
  /** Luôn `false` trên bề mặt này. */
  isAlive: false;
  birth?: DateDual | null;
  death?: DateDual | null;
  nativePlace?: string | null;
  biography?: string | null;
  /** Khoá đối tượng MinIO — **không** phải URL, không mở được tệp nào ở Giai đoạn 1. */
  avatarKey?: string | null;
  primaryBranch?: BranchRef | null;
  relations?: PublicRelationDto[];
  /**
   * **Có thể chưa có.** Một agent backend đang thêm khối này vào bản công khai;
   * cho tới lúc nó lên, trường này vắng và giao diện phải chạy y nguyên — xem
   * `toPersonDetail`.
   */
  meta?: PersonAccessMeta;
}

/**
 * `meta` của một hồ sơ công khai khi máy chủ **chưa** gửi khối ấy.
 *
 * Không có nhánh nào ở đây được phép đoán rộng hơn sự thật: bề mặt công khai
 * chạy ở ngữ cảnh Khách, nên mọi quyền đều `false` và tầng là `PUBLIC` — đúng
 * tầng mà BA v2 §10 gán cho người đã khuất. Đặt mặc định **đóng** nghĩa là ngày
 * backend gửi `meta` thật, giao diện chỉ có thể mở thêm ra chứ không thể đang
 * hứa nhiều hơn rồi phải rút lại.
 */
const PUBLIC_PERSON_META: PersonAccessMeta = {
  visibleTier: "PUBLIC",
  canEdit: false,
  canDelete: false,
  canRequestCorrection: false,
  callerRole: "GUEST",
};

/**
 * `PublicPersonDto` → `PersonDto`, để các mục trình bày của hồ sơ (tên nhiều
 * lớp, ngày sinh/mất, quê quán, tiểu sử) dùng lại được **nguyên vẹn** thay vì
 * có một bản sao riêng cho Khách — một bản sao là một chỗ để hai bên lệch nhau.
 *
 * Ba điều cố ý:
 *  - **`meta` chịu được khi vắng** (xem `PUBLIC_PERSON_META`).
 *  - **`avatarKey` KHÔNG thành `avatarUrl`**: nó là khoá MinIO, gán vào đó là
 *    dựng một thẻ `<img>` chắc chắn hỏng.
 *  - **`relationships` để trống**, dù `relations` có dữ liệu: bản công khai
 *    không gửi tóm tắt của đầu kia, và mục Quan hệ chỉ dựng được từ tóm tắt ấy.
 *    Bịa ra "có 3 quan hệ, không rõ với ai" chính là phép đếm mà `404` của bề
 *    mặt này đang tránh.
 */
export function toPersonDetail(dto: PublicPersonDto): PersonDto {
  return {
    id: dto.id,
    names: dto.names ?? [],
    displayName: dto.displayName,
    gender: dto.gender,
    generation: dto.generation ?? null,
    isAlive: false,
    birth: dto.birth ?? null,
    death: dto.death ?? null,
    nativePlace: dto.nativePlace ?? null,
    biography: dto.biography ?? null,
    primaryBranch: dto.primaryBranch ?? null,
    meta: dto.meta ?? PUBLIC_PERSON_META,
  };
}

export interface PublicTreeNode {
  id: string;
  person: PublicPersonSummaryDto;
  /** `0` là gốc, dương là đời dưới, **âm là đời trên**. */
  depth: number;
  /** Cha/mẹ **có mặt trong chính projection này**. Rỗng ≠ mồ côi. */
  parentIds: string[];
  spouseIds: string[];
  /**
   * Node nằm ở rìa độ sâu đã xin. Suy ra **chỉ từ tham số yêu cầu**, không từ số
   * con thật (số ấy đếm cả người còn sống), nên có thể `true` ở một node thực ra
   * không còn con nào — bấm mở rộng chỉ tốn một lượt gọi trả về rỗng.
   */
  expandable: boolean;
}

export interface PublicTreeEdge {
  id: string;
  source: string;
  target: string;
  relType: RelType;
  heirKind?: HeirKind | null;
  spouseOrder?: number | null;
}

export interface PublicTreeMeta {
  depth: number;
  direction: TreeDirection;
  nodeCount: number;
  edgeCount: number;
  /** Đã chạm trần 200 node nên cây bị cắt. */
  truncated: boolean;
  /** Luôn `true`. Hằng số, **không phải phép đếm** — nó không nói gì về số người bị ẩn. */
  guestFiltered: boolean;
}

export interface PublicTreeProjection {
  rootId: string;
  nodes: PublicTreeNode[];
  edges: PublicTreeEdge[];
  meta: PublicTreeMeta;
}

export interface GetPublicTreeParams {
  /** **Tuỳ chọn** — vắng mặt thì cổng công khai mở ra thuỷ tổ. */
  rootId?: string | null;
  /** Tối đa `PUBLIC_TREE_MAX_DEPTH`; mặc định của máy chủ là 3. */
  depth?: number;
  direction?: TreeDirection;
  includeSpouses?: boolean;
}

export interface PublicSearchParams {
  q: string;
  /** Thủy tổ = 1. */
  generation?: number;
  page?: number;
}

export const publicPortalApi = {
  getTree: ({ rootId, depth, direction, includeSpouses }: GetPublicTreeParams) =>
    apiFetch<PublicTreeProjection>("/api/v1/public/tree", {
      // KHÔNG gửi `maxNodes`: endpoint công khai không có tham số ấy, trần nằm ở
      // cấu hình máy chủ. Gửi thừa chỉ làm URL (và khoá cache) sai lệch.
      // `rootId ?? undefined`: `buildUrl` chỉ bỏ qua `undefined`; một `null` lọt
      // xuống sẽ thành `?rootId=null` và đổi một phép chọn gốc thành `400`.
      query: { rootId: rootId ?? undefined, depth, direction, includeSpouses },
    }),

  /**
   * Hồ sơ một người **đã khuất** cho Khách vãng lai.
   *
   * Tồn tại để khách bấm vào một nút trên phả đồ công khai thì đọc được hồ sơ,
   * thay vì nhận `401` từ `/persons/{id}` rồi thấy một dải đỏ "không tải được".
   * Người còn sống và id bịa đặt đều là `404` **không phân biệt được** —
   * đừng thêm bất kỳ câu nào gợi ý sự khác nhau giữa hai ca ấy.
   */
  getPerson: (id: string) =>
    apiFetch<PublicPersonDto>(`/api/v1/public/persons/${encodeURIComponent(id)}`),

  searchPersons: ({ q, generation, page = 0 }: PublicSearchParams) =>
    apiFetch<PublicPersonSummaryPage>("/api/v1/public/persons/search", {
      query: { q, generation, page },
    }),
};

/**
 * `PublicTreeProjection` → `TreeProjection`, để canvas chỉ phải biết **một** hình
 * dạng dữ liệu.
 *
 * Hai ánh xạ đáng chú ý:
 *  - `expandable` → `hasMoreDescendants`. Ngữ nghĩa hơi khác (rìa độ sâu, không
 *    phải "còn con thật"), nhưng hành vi giao diện thì giống hệt: hiện nút mở
 *    rộng, và một lượt mở trả về rỗng là kết quả hợp lệ.
 *  - `childCount` **cố tình để trống**. Bản công khai không có con số ấy vì đếm
 *    con là đếm cả người còn sống; điền một giá trị suy đoán vào đây sẽ biến một
 *    quyết định bảo mật của máy chủ thành một con số bịa của trình duyệt.
 *
 * `badges` cũng vắng: nhãn dâu/rể/đích tôn do máy chủ tính, bản công khai không
 * gửi, và giao diện không được tự suy.
 */
export function toTreeProjection(dto: PublicTreeProjection): TreeProjection {
  return {
    rootId: dto.rootId,
    nodes: dto.nodes.map((node) => ({
      id: node.id,
      person: toPersonSummary(node.person),
      depth: node.depth,
      parentIds: node.parentIds,
      spouseIds: node.spouseIds,
      hasMoreDescendants: node.expandable,
    })),
    edges: dto.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      relType: edge.relType,
      heirKind: edge.heirKind ?? null,
      spouseOrder: edge.spouseOrder ?? null,
    })),
    meta: {
      depth: dto.meta.depth,
      direction: dto.meta.direction,
      nodeCount: dto.meta.nodeCount,
      edgeCount: dto.meta.edgeCount,
      truncated: dto.meta.truncated,
    },
  };
}

/** Bản gọn công khai → bản gọn dùng chung. `avatarKey` bị bỏ có chủ ý (xem trên). */
export function toPersonSummary(person: PublicPersonSummaryDto): PersonSummaryDto {
  return {
    id: person.id,
    displayName: person.displayName,
    nameHanNom: person.nameHanNom ?? null,
    gender: person.gender,
    generation: person.generation ?? null,
    isAlive: false,
    birthYear: person.birthYear ?? null,
    deathYear: person.deathYear ?? null,
    primaryBranch: person.primaryBranch ?? null,
    nativePlace: person.nativePlace ?? null,
    matchedNameType: person.matchedNameType ?? null,
  };
}
