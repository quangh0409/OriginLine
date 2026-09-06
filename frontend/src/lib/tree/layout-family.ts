import type {
  AuxiliaryLink,
  ChildDrop,
  FamilyJunction,
  FamilyLayout,
  FamilyUnit,
  HorizontalBar,
} from "./family-layout-types";
import type { NodePosition } from "./layout-hierarchical";
import {
  COUPLE_GAP,
  FAMILY_GAP,
  FAMILY_RANK_SEP,
  HIERARCHICAL_NODE_SEP,
  NODE_HEIGHT,
  NODE_WIDTH,
  SIBLING_BAR_STAGGER,
} from "./layout-constants";

/**
 * Xếp chỗ phả đồ theo <b>đơn vị gia đình</b> (giao kèo kiểu nằm ở `family-layout-types.ts`).
 *
 * <h2>Thuật toán đã chọn và vì sao</h2>
 *
 * <p>Đây là biến thể <b>Reingold–Tilford gọn</b> — đệ quy tính bề rộng trọn nhánh rồi căn giữa —
 * nhưng thao tác trên <i>khối gia đình</i> chứ không trên từng người. Một khối gồm: người "chủ
 * khối" đứng giữa, các bạn đời xếp hai bên, bên dưới là nhóm con của từng cuộc hôn phối. Hàm dựng
 * khối gọi đệ quy xuống từng người con, nhận về bề rộng trọn nhánh của con, xếp các nhánh cạnh
 * nhau, rồi căn hàng cha mẹ vào giữa.</p>
 *
 * <p>Vì sao KHÔNG dùng contour đầy đủ của Reingold–Tilford: contour sinh ra để "cài răng lược" hai
 * nhánh có hình răng cưa cho đỡ tốn bề ngang. Phả đồ giấy truyền thống <b>không cài răng lược</b> —
 * mỗi chi phải chiếm trọn một dải dọc riêng, cài vào nhau là mất ranh giới chi/ngành, thứ mà người
 * trong họ đọc phả đồ để tìm. Nên bề rộng nhánh ở đây là một khoảng liền, xếp cạnh nhau là xong:
 * đơn giản hơn, ổn định hơn giữa các lần dựng lại, và đúng với cách đọc gia phả.</p>
 *
 * <p>Vì sao không dùng thư viện cây có sẵn (d3-hierarchy / dagre): chúng giả định mỗi người là một
 * nút có đúng một cha. Gia phả Việt phá giả định đó ngay ở đời thứ hai — đa thê, tái hôn, con nuôi
 * từ chi khác. Ở đây phần <b>xương sống</b> vẫn là một rừng cây thật (mỗi người đúng một chỗ đứng),
 * còn mọi quan hệ không vừa khuôn được trả lại nguyên vẹn trong {@code auxiliaryLinks} kèm đường đi
 * đã tính sẵn: <b>không cạnh nào bị đánh rơi</b>.</p>
 *
 * <h2>Độ phức tạp</h2>
 *
 * <p>n = số nhân khẩu, u = số đơn vị gia đình, a = số đường phụ. Suy đời (BFS trên đồ thị ràng
 * buộc) O(n+u); chọn chủ khối và khối nhà O(n+u); dựng khối đệ quy — mỗi khối đúng một lần, công
 * việc mỗi khối tỉ lệ với số bạn đời cộng số con của nó — O(n+u), cộng O(n log n) cho các phép sắp
 * thứ tự sinh / thứ tự vợ; trải toạ độ O(n); vẽ đường phụ O(a). <b>Tổng O((n+u) log n) thời gian,
 * O(n+u) bộ nhớ</b>, một lượt duy nhất, không có vòng lặp nới-dần toàn cục. 1506 nhân khẩu chạy
 * gọn trong một khung hình, và mỗi lô tải thêm chỉ tốn đúng phần của lô đó.</p>
 *
 * <h2>Bất biến hình học — điều kiện nghiệm thu, có test máy kiểm</h2>
 *
 * <ol>
 *   <li>Không thẻ nào chồng lên thẻ nào.</li>
 *   <li>Mọi đoạn đường chỉ nằm ngang hoặc thẳng đứng, không có đoạn chéo.</li>
 *   <li>Không đoạn nào cắt qua lòng một tấm thẻ: đường rơi xuống con luôn đi trong <b>khe
 *       {@link COUPLE_GAP} giữa hai vợ chồng</b>, thanh anh em luôn nằm trong <b>hành lang giữa
 *       hai đời</b>, đường phụ đi theo hành lang hoặc theo làn ngoài rìa.</li>
 * </ol>
 */

/**
 * Nhân khẩu tối thiểu mà tầng bố cục cần biết. {@code TreeNode} của API thoả kiểu này, nên tầng vẽ
 * truyền thẳng {@code projection.nodes} vào được.
 */
export interface LayoutPerson {
  readonly id: string;
  /** Đời tương đối với gốc (âm = tổ tiên). Thiếu thì tự suy từ cấu trúc đơn vị gia đình. */
  readonly depth?: number;
}

/**
 * Cạnh thô của phép chiếu cây. {@code TreeEdge} của API thoả kiểu này.
 *
 * <p><b>Vì sao tầng bố cục cần cạnh thô chứ không chỉ {@link FamilyUnit}:</b> khuôn đơn vị gia đình
 * chỉ chứa được tối đa hai người bạn đời và các con của riêng cuộc hôn phối đó. Ba loại quan hệ
 * không lọt vào khuôn ấy — {@code HEIR} (kế tự/thừa tự), người con có từ ba cha/mẹ trở lên (cặp cha
 * mẹ đẻ cộng thêm cha nuôi ở chi khác), và hôn phối nối sang nhánh khác. Nếu chỉ nhận
 * {@code FamilyUnit[]} thì đúng những quan hệ khó nhất của gia phả Việt sẽ lặng lẽ biến mất khỏi
 * phả đồ. Ở đây cạnh thô được đối chiếu lại một lượt cuối: cạnh nào chưa được xương sống vẽ thì
 * thành {@code auxiliaryLinks}.</p>
 */
export interface LayoutEdge {
  readonly source: string;
  readonly target: string;
  /** {@code PARENT_BIO} | {@code PARENT_ADOPT} | {@code SPOUSE} | {@code HEIR}. */
  readonly relType: string;
}

/* -------------------------------------------------------------------------- */
/* Hằng số dẫn xuất — tất cả suy từ layout-constants, không có số cứng.        */
/* -------------------------------------------------------------------------- */

/** Bước ngang giữa hai thẻ liền nhau trong cùng một hàng vợ chồng. */
const COL_PITCH = NODE_WIDTH + COUPLE_GAP;

/** Bước dọc giữa hai đời. */
const ROW_PITCH = NODE_HEIGHT + FAMILY_RANK_SEP;

/** Lề tối thiểu giữa thanh anh em và mép hàng thẻ trên/dưới nó. */
const CORRIDOR_MARGIN = HIERARCHICAL_NODE_SEP / 2;

/** Độ sâu của làn dành cho đường phụ, đo từ mép hàng thẻ. */
const AUX_LANE = FAMILY_RANK_SEP / 2;

/** Đường phụ vượt từ hai đời trở lên phải vòng ra ngoài toàn bộ khung thẻ; đây là lề ra ngoài. */
const AUX_OUTSIDE_MARGIN = FAMILY_GAP;

/** Sai số so sánh toạ độ. */
const EPS = 1e-6;

/* -------------------------------------------------------------------------- */
/* Cấu trúc nội bộ                                                             */
/* -------------------------------------------------------------------------- */

/** Một thẻ trong hàng của khối; {@code dx} tính từ tâm thẻ chủ khối. */
interface RelCard {
  readonly id: string;
  readonly dx: number;
}

/**
 * Khối gia đình — đơn vị mà phép đệ quy thao tác.
 *
 * <p>Toạ độ x bên trong khối là <b>tương đối với tâm thẻ chủ khối</b>, nên khối dựng xong đem đặt ở
 * đâu cũng được mà không phải tính lại (nhờ vậy trải toạ độ chỉ tốn một lượt O(n) thay vì dịch
 * chuyển lồng nhau O(n·chiều sâu)). Toạ độ y đã tuyệt đối vì đời của mỗi người được chốt xong
 * trước khi dựng khối.</p>
 */
interface Block {
  readonly hostId: string;
  readonly gen: number;
  readonly cards: RelCard[];
  readonly junctions: FamilyJunction[];
  readonly subs: { block: Block; dx: number }[];
  /** Bao ngang trọn nhánh, tính từ tâm thẻ chủ khối. */
  left: number;
  right: number;
  /** Khối tự thành một "nhóm gia đình" (có bạn đời hoặc có con) ⇒ hàng xóm phải cách FAMILY_GAP. */
  readonly isFamily: boolean;
}

/** Đường phụ khi chưa tính đường đi. */
interface RawAux {
  readonly id: string;
  readonly sourceId: string;
  readonly targetId: string;
  readonly kind: AuxiliaryLink["kind"];
}

interface Ctx {
  readonly known: Set<string>;
  readonly gen: Map<string, number>;
  readonly unitById: Map<string, FamilyUnit>;
  readonly unitIndex: Map<string, number>;
  /** unitId → id người chủ khối của đơn vị đó. */
  readonly hostOfUnit: Map<string, string>;
  /** hostId → các đơn vị người đó làm chủ khối, đã sắp theo spouseOrder. */
  readonly unitsByHost: Map<string, FamilyUnit[]>;
  /** personId → id chủ khối mà thẻ của người đó nằm trong. */
  readonly homeHost: Map<string, string>;
  /** personId → unitId của cha mẹ "chính" — chỗ người đó treo vào xương sống. */
  readonly primaryParentUnit: Map<string, string>;
  /** unitId → các con thực sự treo dưới đơn vị đó, theo thứ tự sinh. */
  readonly backboneChildren: Map<string, string[]>;
  readonly aux: Map<string, RawAux>;
}

/* -------------------------------------------------------------------------- */
/* Hàm chính                                                                   */
/* -------------------------------------------------------------------------- */

export function layoutFamily(
  persons: readonly LayoutPerson[],
  units: readonly FamilyUnit[],
  edges: readonly LayoutEdge[] = []
): FamilyLayout {
  const ordered: LayoutPerson[] = [];
  const known = new Set<string>();
  for (const p of persons) {
    if (known.has(p.id)) continue;
    known.add(p.id);
    ordered.push(p);
  }

  if (ordered.length === 0) {
    return { positions: new Map(), junctions: [], auxiliaryLinks: [] };
  }

  const ctx = buildContext(ordered, units, known);

  /* ---- Dựng rừng khối ---------------------------------------------------- */

  const blockCache = new Map<string, Block>();
  const building = new Set<string>();
  const roots: Block[] = [];

  for (const p of ordered) {
    if (ctx.homeHost.get(p.id) !== p.id) continue;
    if (ctx.primaryParentUnit.has(p.id)) continue;
    roots.push(buildBlock(p.id, ctx, blockCache, building));
  }
  // Chốt chặn cho dữ liệu vòng (A là con của B mà B lại là con của A): những chủ khối không nằm
  // trong rừng nào vẫn phải có chỗ đứng, nhận làm gốc luôn.
  for (const p of ordered) {
    if (ctx.homeHost.get(p.id) !== p.id) continue;
    if (blockCache.has(p.id)) continue;
    roots.push(buildBlock(p.id, ctx, blockCache, building));
  }

  /* ---- Trải toạ độ tuyệt đối --------------------------------------------- */

  const positions = new Map<string, NodePosition>();
  const junctions: FamilyJunction[] = [];

  let cursor = 0;
  for (const block of roots) {
    emitBlock(block, cursor - block.left, positions, junctions);
    cursor += block.right - block.left + FAMILY_GAP;
  }

  // Người còn sót lại (bị lọc quyền riêng tư ở giữa chừng, hoặc dính vòng dữ liệu) vẫn phải có chỗ.
  for (const p of ordered) {
    if (positions.has(p.id)) continue;
    positions.set(p.id, { x: cursor, y: (ctx.gen.get(p.id) ?? 0) * ROW_PITCH });
    cursor += NODE_WIDTH + FAMILY_GAP;
  }

  /* ---- Đường phụ --------------------------------------------------------- */

  collectResidualEdges(ctx, junctions, edges);
  const auxiliaryLinks = routeAuxLinks([...ctx.aux.values()], positions);

  normalize(positions, junctions, auxiliaryLinks);
  return { positions, junctions, auxiliaryLinks };
}

/* -------------------------------------------------------------------------- */
/* Giai đoạn 1 — suy đời                                                       */
/* -------------------------------------------------------------------------- */

/**
 * Chốt số đời cho từng người trước khi xếp chỗ.
 *
 * <p>Không thể tin hoàn toàn vào {@code depth} của API: {@code depth} là khoảng cách tới gốc truy
 * vấn, còn thứ ta cần là <b>hàng ngang trên giấy</b> — người dâu phải cùng hàng với chồng dù không
 * có đường huyết thống nào nối cô ấy về gốc. Nên ở đây dựng một đồ thị ràng buộc (vợ chồng lệch 0,
 * cha mẹ→con lệch +1) rồi lan truyền BFS trong từng thành phần liên thông, lấy {@code depth} của
 * người đầu tiên có khai báo làm mốc cho cả thành phần.</p>
 *
 * <p>Dữ liệu mâu thuẫn (hôn phối lệch đời) không làm hỏng bố cục: BFS tới trước thắng, và quan hệ
 * mâu thuẫn bị đẩy sang đường phụ ở giai đoạn sau chứ không bị vẽ chéo qua thẻ.</p>
 */
function resolveGenerations(
  ordered: readonly LayoutPerson[],
  units: readonly FamilyUnit[],
  known: ReadonlySet<string>
): Map<string, number> {
  const adj = new Map<string, { to: string; delta: number }[]>();
  const link = (from: string, to: string, delta: number): void => {
    const list = adj.get(from);
    if (list) list.push({ to, delta });
    else adj.set(from, [{ to, delta }]);
  };

  for (const u of units) {
    const partners = u.partnerIds.filter((id) => known.has(id));
    for (let i = 0; i < partners.length; i += 1) {
      for (let j = i + 1; j < partners.length; j += 1) {
        const a = partners[i]!;
        const b = partners[j]!;
        link(a, b, 0);
        link(b, a, 0);
      }
    }
    for (const child of u.childIds) {
      if (!known.has(child)) continue;
      for (const parent of partners) {
        if (parent === child) continue;
        link(parent, child, 1);
        link(child, parent, -1);
      }
    }
  }

  const depthOf = new Map<string, number | undefined>(ordered.map((p) => [p.id, p.depth]));
  const gen = new Map<string, number>();
  const seen = new Set<string>();

  for (const start of ordered) {
    if (seen.has(start.id)) continue;

    // Gom thành phần liên thông trước, để chọn được mốc tốt nhất (người có depth khai báo).
    const component: string[] = [];
    const stack = [start.id];
    seen.add(start.id);
    while (stack.length > 0) {
      const cur = stack.pop()!;
      component.push(cur);
      for (const e of adj.get(cur) ?? []) {
        if (seen.has(e.to)) continue;
        seen.add(e.to);
        stack.push(e.to);
      }
    }

    let seedId = start.id;
    for (const id of component) {
      if (depthOf.get(id) !== undefined) {
        seedId = id;
        break;
      }
    }
    gen.set(seedId, depthOf.get(seedId) ?? 0);

    const queue = [seedId];
    for (let head = 0; head < queue.length; head += 1) {
      const cur = queue[head]!;
      const curGen = gen.get(cur)!;
      for (const e of adj.get(cur) ?? []) {
        if (gen.has(e.to)) continue;
        gen.set(e.to, curGen + e.delta);
        queue.push(e.to);
      }
    }
  }

  for (const p of ordered) if (!gen.has(p.id)) gen.set(p.id, p.depth ?? 0);
  return gen;
}

/* -------------------------------------------------------------------------- */
/* Giai đoạn 2 — chủ khối, khối nhà, con xương sống                            */
/* -------------------------------------------------------------------------- */

function buildContext(
  ordered: readonly LayoutPerson[],
  units: readonly FamilyUnit[],
  known: Set<string>
): Ctx {
  const gen = resolveGenerations(ordered, units, known);

  const unitById = new Map<string, FamilyUnit>();
  const unitIndex = new Map<string, number>();
  const usable: FamilyUnit[] = [];
  for (const u of units) {
    if (unitById.has(u.id)) continue;
    unitById.set(u.id, u);
    unitIndex.set(u.id, unitIndex.size);
    usable.push(u);
  }

  const knownPartners = (u: FamilyUnit): string[] => u.partnerIds.filter((id) => known.has(id));

  /* Đếm số cuộc hôn phối của mỗi người: người đa thê phải là trục, không được làm "dâu" của ai. */
  const partnerUnitCount = new Map<string, number>();
  for (const u of usable) {
    for (const p of knownPartners(u)) {
      partnerUnitCount.set(p, (partnerUnitCount.get(p) ?? 0) + 1);
    }
  }

  /* Ai có cha mẹ hiện diện trên canvas — dùng để giữ dòng máu làm xương sống. */
  const hasParentOnCanvas = new Set<string>();
  for (const u of usable) {
    const partners = knownPartners(u);
    if (partners.length === 0) continue;
    const unitGen = Math.min(...partners.map((p) => gen.get(p) ?? 0));
    for (const child of u.childIds) {
      if (!known.has(child)) continue;
      if ((gen.get(child) ?? 0) === unitGen + 1) hasParentOnCanvas.add(child);
    }
  }

  /**
   * Chọn chủ khối cho mỗi đơn vị. Thứ tự ưu tiên và lý do:
   *  1. Người có NHIỀU cuộc hôn phối hơn. Nếu để bà cả làm chủ khối thì ông chồng đa thê bị kéo
   *     sang khối của bà, và hai bà còn lại mất chỗ đứng cạnh ông.
   *  2. Người CÓ cha mẹ trên canvas: dòng máu làm xương sống, dâu/rể đứng ghé bên cạnh.
   *  3. {@code anchorId} do tầng suy đơn vị gia đình chỉ định.
   */
  const hostOfUnit = new Map<string, string>();
  for (const u of usable) {
    const partners = knownPartners(u);
    if (partners.length === 0) continue;
    if (partners.length === 1) {
      hostOfUnit.set(u.id, partners[0]!);
      continue;
    }
    const a = partners[0]!;
    const b = partners[1]!;
    const ca = partnerUnitCount.get(a) ?? 0;
    const cb = partnerUnitCount.get(b) ?? 0;
    if (ca !== cb) {
      hostOfUnit.set(u.id, ca > cb ? a : b);
      continue;
    }
    const pa = hasParentOnCanvas.has(a);
    const pb = hasParentOnCanvas.has(b);
    if (pa !== pb) {
      hostOfUnit.set(u.id, pa ? a : b);
      continue;
    }
    hostOfUnit.set(u.id, partners.includes(u.anchorId) ? u.anchorId : a);
  }

  const unitsByHost = new Map<string, FamilyUnit[]>();
  for (const u of usable) {
    const host = hostOfUnit.get(u.id);
    if (host === undefined) continue;
    const list = unitsByHost.get(host);
    if (list) list.push(u);
    else unitsByHost.set(host, [u]);
  }
  for (const list of unitsByHost.values()) {
    list.sort((x, y) => {
      const ox = x.spouseOrder ?? Number.MAX_SAFE_INTEGER;
      const oy = y.spouseOrder ?? Number.MAX_SAFE_INTEGER;
      if (ox !== oy) return ox - oy;
      return (unitIndex.get(x.id) ?? 0) - (unitIndex.get(y.id) ?? 0);
    });
  }

  /**
   * Khối nhà của mỗi người — chỗ đứng DUY NHẤT của họ trên canvas.
   *
   * <p>Đây chính là chỗ cây gia đình thuần va vào gia phả thật: người tái hôn thuộc hai đơn vị cùng
   * lúc nhưng chỉ có một tấm thẻ. Ai làm chủ khối thì ở khối của mình; ai không thì về khối của
   * cuộc hôn phối ĐẦU TIÊN (theo spouseOrder) — cuộc còn lại thành đường phụ, không mất.</p>
   */
  const homeHost = new Map<string, string>();
  for (const p of ordered) {
    if ((unitsByHost.get(p.id)?.length ?? 0) > 0) homeHost.set(p.id, p.id);
  }
  for (const u of usable) {
    const host = hostOfUnit.get(u.id);
    if (host === undefined) continue;
    for (const p of knownPartners(u)) {
      if (p === host || homeHost.has(p)) continue;
      // Lệch đời thì không thể đứng cạnh nhau trên cùng một hàng; để họ ở khối riêng.
      if ((gen.get(p) ?? 0) !== (gen.get(host) ?? 0)) continue;
      homeHost.set(p, host);
    }
  }
  for (const p of ordered) if (!homeHost.has(p.id)) homeHost.set(p.id, p.id);

  const ctx: Ctx = {
    known,
    gen,
    unitById,
    unitIndex,
    hostOfUnit,
    unitsByHost,
    homeHost,
    primaryParentUnit: new Map(),
    backboneChildren: new Map(),
    aux: new Map(),
  };

  /**
   * Chọn cha mẹ "chính" cho từng người con.
   *
   * <p>Một người có thể là con trong nhiều đơn vị (cha mẹ đẻ và cha mẹ nuôi). Chỉ MỘT trong số đó
   * được treo vào xương sống; phần còn lại thành đường phụ. Ưu tiên cha mẹ đẻ, vì đó là quan hệ
   * quyết định thứ bậc trong họ.</p>
   */
  const candidatesByChild = new Map<string, FamilyUnit[]>();
  for (const u of usable) {
    const host = hostOfUnit.get(u.id);
    if (host === undefined) continue;
    for (const child of u.childIds) {
      if (!known.has(child) || child === host) continue;
      const list = candidatesByChild.get(child);
      if (list) list.push(u);
      else candidatesByChild.set(child, [u]);
    }
  }

  for (const [child, cands] of candidatesByChild) {
    const oneRowBelowHost = (u: FamilyUnit): boolean =>
      (gen.get(child) ?? 0) === (gen.get(hostOfUnit.get(u.id)!) ?? 0) + 1;
    // Người đã đứng ghé trong khối của bạn đời thì không còn treo được dưới cha mẹ nữa — mọi quan
    // hệ cha mẹ của họ thành đường phụ (trường hợp hai chi cùng tải, anh em họ lấy nhau).
    const eligible = ctx.homeHost.get(child) === child ? cands.filter(oneRowBelowHost) : [];
    eligible.sort((x, y) => {
      const ax = x.adoptedChildIds.has(child) ? 1 : 0;
      const ay = y.adoptedChildIds.has(child) ? 1 : 0;
      if (ax !== ay) return ax - ay;
      return (unitIndex.get(x.id) ?? 0) - (unitIndex.get(y.id) ?? 0);
    });
    const primary = eligible[0];
    if (primary) {
      ctx.primaryParentUnit.set(child, primary.id);
      const list = ctx.backboneChildren.get(primary.id);
      if (list) list.push(child);
      else ctx.backboneChildren.set(primary.id, [child]);
    }
    // Mọi quan hệ cha mẹ–con KHÔNG treo được vào xương sống phải thành đường phụ. Bỏ qua ở đây là
    // vài tuần nữa có người mở phả ra và thấy con nuôi của cụ tổ biến mất khỏi cây.
    for (const u of cands) {
      if (primary && u.id === primary.id) continue;
      addAux(ctx, "CROSS_BRANCH_PARENT", hostOfUnit.get(u.id)!, child, u.id);
    }
  }

  // Trả lại đúng thứ tự sinh: vòng lặp trên gom theo thứ tự duyệt người con, không theo childIds.
  for (const [unitId, children] of ctx.backboneChildren) {
    const u = unitById.get(unitId)!;
    const set = new Set(children);
    ctx.backboneChildren.set(
      unitId,
      u.childIds.filter((c) => set.has(c))
    );
  }

  return ctx;
}

function addAux(
  ctx: Ctx,
  kind: AuxiliaryLink["kind"],
  sourceId: string,
  targetId: string,
  scope: string
): void {
  if (sourceId === targetId) return;
  const id = `aux:${kind}:${scope}:${sourceId}->${targetId}`;
  if (ctx.aux.has(id)) return;
  ctx.aux.set(id, { id, sourceId, targetId, kind });
}

/* -------------------------------------------------------------------------- */
/* Giai đoạn 3 — dựng khối (đệ quy)                                            */
/* -------------------------------------------------------------------------- */

/** Nhóm con của một cuộc hôn phối, đã xếp xong theo trục ngang tương đối. */
interface ChildGroup {
  readonly unit: FamilyUnit;
  /** x của điểm nối trong hệ toạ độ khối. */
  readonly junctionX: number;
  readonly junctionY: number;
  readonly marriageBar: HorizontalBar | null;
  readonly barY: number;
  readonly subs: { block: Block; dx: number }[];
  /** Bao ngang trọn nhóm, gồm cả nhánh sâu của từng đứa con. */
  extLeft: number;
  extRight: number;
  /** Nhịp giữa tâm thẻ đứa đầu và tâm thẻ đứa cuối — thanh anh em căn giữa theo nhịp này. */
  spanLeft: number;
  spanRight: number;
}

function buildBlock(
  hostId: string,
  ctx: Ctx,
  cache: Map<string, Block>,
  building: Set<string>
): Block {
  const cached = cache.get(hostId);
  if (cached) return cached;

  const gen = ctx.gen.get(hostId) ?? 0;
  const rowTop = gen * ROW_PITCH;

  // Chốt chặn dữ liệu vòng: trả về khối một thẻ thay vì đệ quy vô tận.
  if (building.has(hostId)) {
    return {
      hostId,
      gen,
      cards: [{ id: hostId, dx: 0 }],
      junctions: [],
      subs: [],
      left: -NODE_WIDTH / 2,
      right: NODE_WIDTH / 2,
      isFamily: false,
    };
  }
  building.add(hostId);

  const cards: RelCard[] = [{ id: hostId, dx: 0 }];
  const hostUnits = ctx.unitsByHost.get(hostId) ?? [];

  /* ---- 3a. Xếp bạn đời hai bên người trục -------------------------------- */

  interface Slot {
    readonly unit: FamilyUnit;
    readonly spouseId: string | null;
    /** null = không có thẻ bạn đời trong hàng (đơn thân, hoặc bạn đời sống ở khối khác). */
    readonly spouseDx: number | null;
    readonly distance: number;
  }

  const slots: Slot[] = [];
  let inlineIndex = 0;
  let vacantCount = 0;
  for (const u of hostUnits) {
    const spouseId = u.partnerIds.find((id) => id !== hostId && ctx.known.has(id)) ?? null;
    if (spouseId !== null && ctx.homeHost.get(spouseId) === hostId) {
      // spouseOrder 1 đứng sát người trục; các bà sau toả dần ra: phải, trái, phải ngoài, trái ngoài…
      const side = inlineIndex % 2 === 0 ? 1 : -1;
      const distance = Math.floor(inlineIndex / 2) + 1;
      inlineIndex += 1;
      const dx = side * distance * COL_PITCH;
      cards.push({ id: spouseId, dx });
      slots.push({ unit: u, spouseId, spouseDx: dx, distance });
    } else {
      vacantCount += 1;
      slots.push({ unit: u, spouseId, spouseDx: null, distance: 0 });
    }
  }

  /* ---- 3b. Điểm nối của từng cuộc hôn phối ------------------------------- */

  const unitsWithChildren = slots.filter(
    (s) => (ctx.backboneChildren.get(s.unit.id)?.length ?? 0) > 0
  ).length;

  let vacantSeen = 0;
  let barSeen = 0;
  const groups: ChildGroup[] = [];

  for (const slot of slots) {
    const children = ctx.backboneChildren.get(slot.unit.id) ?? [];

    let junctionX: number;
    let junctionY: number;
    let marriageBar: HorizontalBar | null = null;

    if (slot.spouseDx !== null) {
      const side = Math.sign(slot.spouseDx);
      const inwardDx = side * (slot.distance - 1) * COL_PITCH;
      const x1 = Math.min(slot.spouseDx, inwardDx) + NODE_WIDTH / 2;
      const x2 = Math.max(slot.spouseDx, inwardDx) - NODE_WIDTH / 2;
      junctionX = (x1 + x2) / 2;
      junctionY = rowTop + NODE_HEIGHT / 2;
      if (slot.distance === 1) {
        marriageBar = { x1, x2, y: junctionY };
      } else {
        // Bà thứ ba trở đi không đứng sát người trục được, nên thanh hôn phối sẽ phải bắc qua thẻ
        // của bà đứng giữa — vẽ thế là cắt qua thẻ. Thay bằng đường phụ đi vòng theo hành lang.
        addAux(ctx, "REMARRIAGE", hostId, slot.spouseId!, slot.unit.id);
      }
    } else {
      // Cha/mẹ đơn thân, hoặc bạn đời đã có chỗ đứng ở khối khác (tái hôn): điểm nối nằm ngay dưới
      // thẻ chủ khối. Nhiều đơn vị cùng cảnh thì trải đều trong bề ngang thẻ để các cuống không
      // chồng khít lên nhau.
      const spread = NODE_WIDTH / (vacantCount + 1);
      junctionX = -NODE_WIDTH / 2 + spread * (vacantSeen + 1);
      junctionY = rowTop + NODE_HEIGHT;
      vacantSeen += 1;
      if (slot.spouseId !== null) addAux(ctx, "REMARRIAGE", hostId, slot.spouseId, slot.unit.id);
    }

    if (children.length === 0) {
      groups.push({
        unit: slot.unit,
        junctionX,
        junctionY,
        marriageBar,
        barY: junctionY,
        subs: [],
        extLeft: junctionX,
        extRight: junctionX,
        spanLeft: junctionX,
        spanRight: junctionX,
      });
      continue;
    }

    const barY = siblingBarY(rowTop, barSeen, unitsWithChildren);
    barSeen += 1;

    // Xếp các nhánh con cạnh nhau theo bề rộng TRỌN nhánh — không cài răng lược, mỗi chi một dải.
    const subs: { block: Block; dx: number }[] = [];
    let cursor = 0;
    let prev: Block | null = null;
    for (const childId of children) {
      const childBlock = buildBlock(childId, ctx, cache, building);
      if (prev) {
        cursor += prev.isFamily || childBlock.isFamily ? FAMILY_GAP : HIERARCHICAL_NODE_SEP;
      }
      subs.push({ block: childBlock, dx: cursor - childBlock.left });
      cursor += childBlock.right - childBlock.left;
      prev = childBlock;
    }

    const rawSpanLeft = subs[0]!.dx;
    const rawSpanRight = subs[subs.length - 1]!.dx;
    // Căn giữa theo NHỊP TÂM THẺ CON, không theo bao ngang trọn nhánh: thanh anh em phải nằm chính
    // giữa dưới cặp vợ chồng — đó mới là thứ người đọc phả đồ nhìn vào.
    const shift = junctionX - (rawSpanLeft + rawSpanRight) / 2;
    for (const s of subs) s.dx += shift;

    groups.push({
      unit: slot.unit,
      junctionX,
      junctionY,
      marriageBar,
      barY,
      subs,
      extLeft: shift,
      extRight: shift + cursor,
      spanLeft: rawSpanLeft + shift,
      spanRight: rawSpanRight + shift,
    });
  }

  /* ---- 3c. Tách các nhóm con ra khỏi nhau -------------------------------- */

  separateGroups(groups);

  /* ---- 3d. Gom lại thành khối -------------------------------------------- */

  const junctions: FamilyJunction[] = [];
  const subs: { block: Block; dx: number }[] = [];
  let left = Math.min(...cards.map((c) => c.dx)) - NODE_WIDTH / 2;
  let right = Math.max(...cards.map((c) => c.dx)) + NODE_WIDTH / 2;

  for (const g of groups) {
    for (const s of g.subs) subs.push(s);
    const junction = makeJunction(g);
    junctions.push(junction);
    if (g.subs.length > 0) {
      left = Math.min(left, g.extLeft);
      right = Math.max(right, g.extRight);
    }
    if (junction.siblingBar) {
      left = Math.min(left, junction.siblingBar.x1);
      right = Math.max(right, junction.siblingBar.x2);
    }
  }

  const block: Block = {
    hostId,
    gen,
    cards,
    junctions,
    subs,
    left,
    right,
    isFamily: cards.length > 1 || subs.length > 0,
  };

  building.delete(hostId);
  cache.set(hostId, block);
  return block;
}

/**
 * Đẩy các nhóm con của cùng một người trục ra khỏi nhau.
 *
 * <p>Ông đa thê có nhiều nhóm con, mỗi nhóm muốn nằm chính giữa dưới điểm nối của mẹ nó — nhưng các
 * điểm nối chỉ cách nhau đúng một bước thẻ, còn nhóm con thì rộng bao nhiêu tuỳ số con. Ở đây quét
 * một lượt trái→phải, đẩy nhóm nào chạm nhóm trước, rồi kéo cả dải về cho cân quanh người trục.
 * Sau bước này điểm nối có thể lệch khỏi nhịp thẻ con; {@link makeJunction} kéo dài thanh anh em ra
 * tới điểm nối để cuống vẫn hạ đúng xuống thanh, không sinh đoạn chéo nào.</p>
 */
function separateGroups(groups: readonly ChildGroup[]): void {
  const withKids = groups.filter((g) => g.subs.length > 0);
  if (withKids.length < 2) return;

  withKids.sort((a, b) => a.junctionX - b.junctionX);
  const initialRight = withKids[withKids.length - 1]!.extRight;

  for (let i = 1; i < withKids.length; i += 1) {
    const prev = withKids[i - 1]!;
    const cur = withKids[i]!;
    const needed = prev.extRight + FAMILY_GAP - cur.extLeft;
    if (needed > 0) shiftGroup(cur, needed);
  }

  // Chỉ đẩy sang phải thì cả dải lệch phải; kéo lại một nửa cho dải con cân quanh người trục.
  const drift = withKids[withKids.length - 1]!.extRight - initialRight;
  if (drift > 0) for (const g of withKids) shiftGroup(g, -drift / 2);
}

function shiftGroup(g: ChildGroup, dx: number): void {
  if (dx === 0) return;
  for (const s of g.subs) s.dx += dx;
  g.extLeft += dx;
  g.extRight += dx;
  g.spanLeft += dx;
  g.spanRight += dx;
}

/**
 * Chiều cao của thanh anh em trong hành lang giữa hai đời.
 *
 * <p>Mỗi bà một thanh, đặt lệch tầng nhau để nhìn là biết con bà nào. Nhiều bà quá thì bước lệch tự
 * co lại thay vì tràn xuống đè lên hàng thẻ đời sau — hành lang chỉ có {@link FAMILY_RANK_SEP} để
 * chia, và {@link CORRIDOR_MARGIN} ở hai đầu phải giữ nguyên.</p>
 */
function siblingBarY(rowTop: number, index: number, count: number): number {
  const usable = FAMILY_RANK_SEP - 2 * CORRIDOR_MARGIN;
  const step = count > 1 ? Math.min(SIBLING_BAR_STAGGER, usable / (count - 1)) : 0;
  const span = step * (count - 1);
  const base = (FAMILY_RANK_SEP - span) / 2;
  return rowTop + NODE_HEIGHT + base + index * step;
}

function makeJunction(g: ChildGroup): FamilyJunction {
  const unit = g.unit;
  const childDrops: ChildDrop[] = g.subs.map((s) => ({
    childId: s.block.hostId,
    x: s.dx,
    yFrom: g.barY,
    yTo: s.block.gen * ROW_PITCH,
    dashed: unit.adoptedChildIds.has(s.block.hostId),
  }));

  if (childDrops.length === 0) {
    return {
      unitId: unit.id,
      x: g.junctionX,
      y: g.junctionY,
      marriageBar: g.marriageBar,
      siblingBar: null,
      stem: null,
      childDrops: [],
      ended: unit.ended,
    };
  }

  // Một con duy nhất, và điểm nối đã ở đúng ngay trên đầu nó ⇒ rơi thẳng, khỏi cần thanh anh em.
  const only = childDrops[0]!;
  if (childDrops.length === 1 && Math.abs(only.x - g.junctionX) < EPS) {
    return {
      unitId: unit.id,
      x: g.junctionX,
      y: g.junctionY,
      marriageBar: g.marriageBar,
      siblingBar: null,
      stem: null,
      childDrops: [{ ...only, yFrom: g.junctionY }],
      ended: unit.ended,
    };
  }

  // Thanh anh em phải phủ cả điểm nối; nếu không, cuống hạ xuống sẽ hụt khỏi thanh (đoạn chéo).
  return {
    unitId: unit.id,
    x: g.junctionX,
    y: g.junctionY,
    marriageBar: g.marriageBar,
    siblingBar: {
      x1: Math.min(g.spanLeft, g.junctionX),
      x2: Math.max(g.spanRight, g.junctionX),
      y: g.barY,
    },
    stem: { x: g.junctionX, yFrom: g.junctionY, yTo: g.barY },
    childDrops,
    ended: unit.ended,
  };
}

/* -------------------------------------------------------------------------- */
/* Giai đoạn 4 — trải toạ độ tuyệt đối                                         */
/* -------------------------------------------------------------------------- */

function emitBlock(
  block: Block,
  hostCenterX: number,
  positions: Map<string, NodePosition>,
  junctions: FamilyJunction[]
): void {
  const y = block.gen * ROW_PITCH;
  for (const card of block.cards) {
    if (positions.has(card.id)) continue;
    positions.set(card.id, { x: hostCenterX + card.dx - NODE_WIDTH / 2, y });
  }
  for (const j of block.junctions) junctions.push(shiftJunction(j, hostCenterX, 0));
  for (const sub of block.subs) emitBlock(sub.block, hostCenterX + sub.dx, positions, junctions);
}

/* -------------------------------------------------------------------------- */
/* Giai đoạn 5a — đối chiếu cạnh thô: không cạnh nào được phép biến mất        */
/* -------------------------------------------------------------------------- */

function pairKey(a: string, b: string): string {
  return a < b ? `${a}|${b}` : `${b}|${a}`;
}

/**
 * Lượt kiểm cuối: mọi cạnh trong phép chiếu phải được vẽ ra bằng cách nào đó.
 *
 * <p>Xương sống vẽ được cạnh nào thì thôi; cạnh nào không — vì khuôn đơn vị gia đình không chứa
 * nổi ({@code HEIR}, người con có ba cha/mẹ, hôn phối nối sang nhánh khác), hoặc vì hình học không
 * cho phép (bà thứ ba, người tái hôn) — thì thành đường phụ. Đây là chỗ chốt lời hứa
 * "<b>không đánh rơi cạnh nào</b>": bỏ sót ở đây là vài tuần nữa có người mở phả ra và thấy bà hai
 * của cụ tổ biến mất khỏi cây.</p>
 */
function collectResidualEdges(
  ctx: Ctx,
  junctions: readonly FamilyJunction[],
  edges: readonly LayoutEdge[]
): void {
  if (edges.length === 0) return;

  const junctionByUnit = new Map(junctions.map((j) => [j.unitId, j]));
  const drawnMarriage = new Set<string>();
  const drawnParent = new Set<string>();

  const auxByTarget = new Map<string, Set<string>>();
  const auxPairs = new Set<string>();
  for (const a of ctx.aux.values()) {
    auxPairs.add(pairKey(a.sourceId, a.targetId));
    const set = auxByTarget.get(a.targetId);
    if (set) set.add(a.sourceId);
    else auxByTarget.set(a.targetId, new Set([a.sourceId]));
  }

  for (const u of ctx.unitById.values()) {
    const partners = u.partnerIds.filter((id) => ctx.known.has(id));
    if (partners.length === 0) continue;
    const j = junctionByUnit.get(u.id);

    if (partners.length >= 2) {
      const key = pairKey(partners[0]!, partners[1]!);
      if (j?.marriageBar != null || auxPairs.has(key)) drawnMarriage.add(key);
    }

    for (const child of u.childIds) {
      if (!ctx.known.has(child)) continue;
      const hasDrop = j?.childDrops.some((d) => d.childId === child) ?? false;
      const hasAux = partners.some((p) => auxByTarget.get(child)?.has(p) ?? false);
      if (!hasDrop && !hasAux) continue;
      // Một đường phụ từ MỘT người trong cặp đã đủ nói lên cả cặp — đừng vẽ chồng hai đường
      // song song từ ông và từ bà xuống cùng một đứa con.
      for (const p of partners) drawnParent.add(`${p}>${child}`);
    }
  }

  for (const e of edges) {
    if (!ctx.known.has(e.source) || !ctx.known.has(e.target)) continue;
    if (e.source === e.target) continue;

    if (e.relType === "SPOUSE") {
      const key = pairKey(e.source, e.target);
      if (drawnMarriage.has(key)) continue;
      drawnMarriage.add(key);
      addAux(ctx, "REMARRIAGE", e.source, e.target, "edge");
      continue;
    }

    if (e.relType === "HEIR") {
      // Kế tự/thừa tự không bao giờ nằm trong một đơn vị gia đình — luôn là đường phụ.
      addAux(ctx, "HEIR", e.source, e.target, "edge");
      continue;
    }

    const key = `${e.source}>${e.target}`;
    if (drawnParent.has(key)) continue;
    drawnParent.add(key);
    addAux(ctx, "CROSS_BRANCH_PARENT", e.source, e.target, "edge");
  }
}

/* -------------------------------------------------------------------------- */
/* Giai đoạn 5b — vẽ đường phụ                                                 */
/* -------------------------------------------------------------------------- */

/**
 * Tính đường đi cho các cạnh không vừa khuôn cây.
 *
 * <p>Nguyên tắc: chỉ đi trong <b>hành lang giữa hai đời</b> — chỗ chắc chắn không có thẻ nào — hoặc
 * theo <b>làn ngoài rìa</b> toàn bộ khung thẻ. Cắt thẳng từ thẻ này sang thẻ kia đúng là kiểu vẽ đã
 * sinh ra lỗi "đường nối chui dưới thẻ" mà cả lần dựng lại này là để sửa.</p>
 */
function routeAuxLinks(
  raw: readonly RawAux[],
  positions: ReadonlyMap<string, NodePosition>
): AuxiliaryLink[] {
  if (raw.length === 0) return [];

  let minX = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  for (const p of positions.values()) {
    minX = Math.min(minX, p.x);
    maxX = Math.max(maxX, p.x + NODE_WIDTH);
  }

  const links: AuxiliaryLink[] = [];
  for (const r of raw) {
    const s = positions.get(r.sourceId);
    const t = positions.get(r.targetId);
    if (!s || !t) continue;

    const sx = s.x + NODE_WIDTH / 2;
    const tx = t.x + NODE_WIDTH / 2;
    const gap = Math.abs(s.y - t.y);
    let waypoints: { x: number; y: number }[];

    if (gap < EPS) {
      // Cùng đời (tái hôn, bà thứ ba): vòng lên hành lang phía trên hàng của họ.
      const lane = s.y - AUX_LANE;
      waypoints = [
        { x: sx, y: s.y },
        { x: sx, y: lane },
        { x: tx, y: lane },
        { x: tx, y: t.y },
      ];
    } else if (Math.abs(gap - ROW_PITCH) < EPS) {
      // Cách đúng một đời: hành lang giữa hai đời là đường duy nhất cần đi.
      const down = t.y > s.y;
      waypoints = [
        { x: sx, y: down ? s.y + NODE_HEIGHT : s.y },
        { x: sx, y: (down ? t.y : s.y) - AUX_LANE },
        { x: tx, y: (down ? t.y : s.y) - AUX_LANE },
        { x: tx, y: down ? t.y : t.y + NODE_HEIGHT },
      ];
    } else {
      // Cách từ hai đời trở lên: mọi đường dọc bên trong khung đều có nguy cơ xuyên qua hàng thẻ ở
      // giữa, nên đi hẳn ra làn ngoài rìa. Đường vòng dài, nhưng KHÔNG BAO GIỜ cắt qua thẻ.
      const down = t.y > s.y;
      const laneS = down ? s.y + NODE_HEIGHT + AUX_LANE : s.y - AUX_LANE;
      const laneT = down ? t.y - AUX_LANE : t.y + NODE_HEIGHT + AUX_LANE;
      const leftLane = minX - AUX_OUTSIDE_MARGIN;
      const rightLane = maxX + AUX_OUTSIDE_MARGIN;
      const mid = (sx + tx) / 2;
      const lane = mid - leftLane <= rightLane - mid ? leftLane : rightLane;
      waypoints = [
        { x: sx, y: down ? s.y + NODE_HEIGHT : s.y },
        { x: sx, y: laneS },
        { x: lane, y: laneS },
        { x: lane, y: laneT },
        { x: tx, y: laneT },
        { x: tx, y: down ? t.y : t.y + NODE_HEIGHT },
      ];
    }

    links.push({
      id: r.id,
      sourceId: r.sourceId,
      targetId: r.targetId,
      kind: r.kind,
      waypoints: dedupeWaypoints(waypoints),
    });
  }
  return links;
}

function dedupeWaypoints(
  points: readonly { x: number; y: number }[]
): { x: number; y: number }[] {
  const out: { x: number; y: number }[] = [];
  for (const p of points) {
    const last = out[out.length - 1];
    if (last && Math.abs(last.x - p.x) < EPS && Math.abs(last.y - p.y) < EPS) continue;
    out.push(p);
  }
  return out;
}

/* -------------------------------------------------------------------------- */
/* Giai đoạn 6 — dời về góc phần tư dương                                      */
/* -------------------------------------------------------------------------- */

/**
 * Dời cả bố cục sao cho mọi thứ vẽ ra — kể cả làn đường phụ chạy ngoài rìa — nằm trong góc phần tư
 * dương. Canvas vẫn chạy được với toạ độ âm, nhưng phép canh khung và các test hình học đọc dễ hơn
 * hẳn khi gốc là (0,0).
 */
function normalize(
  positions: Map<string, NodePosition>,
  junctions: FamilyJunction[],
  links: AuxiliaryLink[]
): void {
  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  const see = (x: number, y: number): void => {
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
  };

  for (const p of positions.values()) see(p.x, p.y);
  for (const j of junctions) {
    see(j.x, j.y);
    if (j.marriageBar) see(Math.min(j.marriageBar.x1, j.marriageBar.x2), j.marriageBar.y);
    if (j.siblingBar) see(Math.min(j.siblingBar.x1, j.siblingBar.x2), j.siblingBar.y);
    if (j.stem) see(j.stem.x, Math.min(j.stem.yFrom, j.stem.yTo));
    for (const d of j.childDrops) see(d.x, Math.min(d.yFrom, d.yTo));
  }
  for (const l of links) for (const w of l.waypoints) see(w.x, w.y);

  if (!Number.isFinite(minX) || !Number.isFinite(minY)) return;
  const dx = -minX;
  const dy = -minY;
  if (dx === 0 && dy === 0) return;

  for (const [id, p] of positions) positions.set(id, { x: p.x + dx, y: p.y + dy });
  for (let i = 0; i < junctions.length; i += 1) junctions[i] = shiftJunction(junctions[i]!, dx, dy);
  for (let i = 0; i < links.length; i += 1) {
    const l = links[i]!;
    links[i] = { ...l, waypoints: l.waypoints.map((w) => ({ x: w.x + dx, y: w.y + dy })) };
  }
}

function shiftJunction(j: FamilyJunction, dx: number, dy: number): FamilyJunction {
  return {
    unitId: j.unitId,
    x: j.x + dx,
    y: j.y + dy,
    marriageBar: j.marriageBar
      ? { x1: j.marriageBar.x1 + dx, x2: j.marriageBar.x2 + dx, y: j.marriageBar.y + dy }
      : null,
    siblingBar: j.siblingBar
      ? { x1: j.siblingBar.x1 + dx, x2: j.siblingBar.x2 + dx, y: j.siblingBar.y + dy }
      : null,
    stem: j.stem ? { x: j.stem.x + dx, yFrom: j.stem.yFrom + dy, yTo: j.stem.yTo + dy } : null,
    childDrops: j.childDrops.map((d) => ({
      ...d,
      x: d.x + dx,
      yFrom: d.yFrom + dy,
      yTo: d.yTo + dy,
    })),
    ended: j.ended,
  };
}
