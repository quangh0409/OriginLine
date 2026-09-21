"use client";

import { useCallback, useMemo, useState } from "react";
import { useQueries } from "@tanstack/react-query";
import { treeApi, type TreeAudience } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { queryKeys } from "@/lib/query/keys";
import { pathFromRootTo } from "@/lib/tree/focus-path";
import { mergeProjections } from "@/lib/tree/merge-projections";
import { computeVisibleSubgraph } from "@/lib/tree/visible-subgraph";
import { useTreeAudience } from "./use-tree-audience";
import type { TreeDirection, TreeNode } from "@/types/api";

/**
 * Khoá React Query cho nhánh "**chưa nói gốc, để máy chủ chọn**".
 *
 * Phải là một chuỗi không thể trùng một id thật, vì hai yêu cầu `/tree` — một
 * có `rootId`, một không — là hai câu hỏi khác nhau và phải có hai ô nhớ đệm
 * khác nhau. Phần `audience` đã nằm sẵn trong khoá; vai cụ thể thì không, nên
 * dưới MSW việc đổi vai vẫn phải kèm tải lại trang (bộ chuyển vai dev vẫn làm
 * đúng thế) — ghi ra đây để lần sau không ai phải tìm lại.
 */
const SERVER_DEFAULT_ROOT_KEY = "__server_default__";

/**
 * Độ sâu của lượt hỏi **tổ tiên** khi nhảy tới một người.
 *
 * Bằng đúng trần của hợp đồng (`depth` kẹp trong [1, 10]). Đây là số bước cha/mẹ tối đa mà một
 * cú nhảy có thể nối ngược về gốc đang mở; xa hơn thế thì `<TreeCanvas>` đổi gốc sang chính người
 * ấy thay vì vẽ một cái cây đứt đoạn.
 */
const FOCUS_ANCESTOR_DEPTH = 10;

/**
 * Độ sâu của lượt hỏi **hậu duệ** khi nhảy tới một người: đúng MỘT đời.
 *
 * Checklist mục 5 nói "đường thẳng từ gốc tới người đang xem **cộng con cái trực tiếp**" — một
 * đời, không hơn. Hỏi sâu hơn là kéo về đúng bức tranh mà việc này đang dẹp đi.
 */
const FOCUS_DESCENDANT_DEPTH = 1;

/** Tập rỗng dùng chung — giữ tham chiếu ổn định để `useMemo` phía dưới không sinh giá trị mới. */
const EMPTY_IDS: ReadonlySet<string> = new Set<string>();

/** Một lượt gọi `/tree` trong kế hoạch nạp của canvas. */
interface BranchPlan {
  /** `null` = "để máy chủ chọn gốc" — chỉ có ở phần tử đầu tiên. */
  readonly id: string | null;
  readonly depth: number;
  readonly direction: TreeDirection;
  /**
   * Lượt gọi này phục vụ **cú nhảy tới một người**, không phải phả đồ đang vẽ.
   *
   * Phân biệt để làm đúng một việc, và việc ấy quan trọng: **hỏng một cú nhảy không được làm
   * hỏng cả phả đồ**. Người được nhắm tới có thể không có trong phả (máy chủ trả `404` — đúng
   * hợp đồng, không phải sự cố), và nếu lỗi ấy rơi vào `error` chung thì màn hình chính đổi
   * thành dải đỏ "Không tải được cây phả đồ" trong khi cây vẫn tải tốt. Đã đo được: bộ giả lập
   * cố ý để hồ sơ của chính người đăng nhập nằm NGOÀI đồ thị, nên mọi thành viên mở `/tree` đều
   * rơi vào ca này.
   */
  readonly forFocus: boolean;
}

export interface UseTreeCanvasOptions {
  /**
   * Gốc do người dùng chỉ định (`?rootId=` trên URL, hoặc một lựa chọn ở
   * `<TreeRootPicker>`). **Vắng mặt là trạng thái bình thường**: lúc ấy hook
   * không gửi `rootId` và máy chủ chọn gốc theo vai + phạm vi chi của người
   * gọi. Gốc thật sự đang mở đọc ở `rootId` trong giá trị trả về, không phải ở
   * tham số này.
   */
  rootId?: string | null;
  /** Depth fetched from `rootId` on first load. Kept small + a lazy prefetch
   * of one extra ring (see module doc) rather than the contract max (10). */
  initialDepth?: number;
  /** Depth fetched when the user expands a node that ran out of loaded data. */
  expandDepth?: number;
  maxNodesPerFetch?: number;
  direction?: TreeDirection;
  includeSpouses?: boolean;
  /**
   * Bản nào của phả đồ. Bỏ trống thì suy từ phiên hiện tại (`useTreeAudience`);
   * chỉ truyền tay trong test.
   */
  audience?: TreeAudience | null;
  /**
   * **Người đang xem** — hồ sơ mà phả đồ phải mở đường tới.
   *
   * Ba nguồn, cùng một cơ chế: hồ sơ của chính người đăng nhập lúc mở trang, một cái tên vừa chọn
   * ở ô tìm trên canvas, và nút "Về chỗ tôi". Khi có nó, hook nạp thêm **chuỗi tổ tiên** của người
   * ấy rồi tự mở đúng những người nằm trên đường từ gốc xuống — không mở gì khác. Mọi nhánh còn
   * lại vẫn gập.
   *
   * Vắng mặt (`null`) là trạng thái bình thường: khách chưa đăng nhập, và thành viên chưa được
   * ghép vào phả, đều rơi vào đây và vẫn xem được cây.
   */
  focusId?: string | null;
  /**
   * Có được **gọi mạng** để tìm người ấy không, hay chỉ mở đường trong phần đã tải.
   *
   * <h2>Vì sao phải là một công tắc, không phải mặc định bật</h2>
   * Phả đồ tự nhắm vào hồ sơ của chính người đăng nhập mỗi lần mở trang. Nếu lượt nhắm ngầm ấy
   * cũng bắn hai yêu cầu mạng thì **mọi thành viên, mọi lần tải trang** đều trả giá cho một cú
   * nhảy họ chưa yêu cầu — và khi người ấy không có trong phần phả đang mở, cái giá là hai lượt
   * `404` trong nhật ký lẫn trong bảng điều khiển trình duyệt. NFR-1 đo đúng đường này.
   *
   * Nên: nhắm **ngầm** thì chỉ mở đường trong dữ liệu đã có (miễn phí, và đúng ở ca thường gặp —
   * người dùng nằm trong chính nhánh mà máy chủ vừa mở). Nhắm **chủ ý** — gõ một cái tên, bấm
   * "Về chỗ tôi", mở một liên kết `?focus=` — thì mới đi hỏi máy chủ.
   */
  fetchFocus?: boolean;
}

/**
 * Owns every stateful piece of the canvas that ISN'T "how to draw pixels":
 * which branches have been fetched (React Query, one query per expanded
 * root — never one query for the whole tree), which are currently expanded
 * in the UI, and the resulting visible node/edge set after collapsing.
 *
 * Lazy-loading strategy: the initial fetch requests `initialDepth` (default
 * 2) generations from `rootId`, but only `rootId` itself starts "expanded" —
 * so the second of those two fetched generations is already sitting in the
 * React Query cache, ready to render the instant a user expands into it,
 * with no network round-trip. Expanding a node that has run out of
 * pre-fetched data (its `hasMoreDescendants` is still true beyond what's
 * loaded) issues exactly one more `/tree?rootId=<thatNode>` call — the tree
 * is NEVER fetched in full, matching the F2 brief's #1 requirement.
 */
export function useTreeCanvas({
  rootId,
  initialDepth = 2,
  expandDepth = 2,
  maxNodesPerFetch = 300,
  direction = "DESCENDANTS",
  includeSpouses = true,
  audience: audienceOverride,
  focusId: requestedFocusId = null,
  fetchFocus = true,
}: UseTreeCanvasOptions) {
  const detectedAudience = useTreeAudience();
  const audience = audienceOverride === undefined ? detectedAudience : audienceOverride;

  /** Gốc do người gọi chỉ định; `null` = "để máy chủ chọn". */
  const requestedRootId = typeof rootId === "string" && rootId.trim() ? rootId.trim() : null;

  // `null` ở phần tử đầu = nhánh "để máy chủ chọn gốc". Mọi phần tử sau luôn là
  // một id thật, vì chúng chỉ sinh ra từ một node đã có mặt trên canvas.
  const [expandedIds, setExpandedIds] = useState<Set<string>>(
    () => new Set(requestedRootId ? [requestedRootId] : [])
  );
  const [fetchedIds, setFetchedIds] = useState<(string | null)[]>(() => [requestedRootId]);
  /** Người đang xem mà đường tới họ ĐÃ được gộp vào `expandedIds`. Xem chỗ gộp, phía dưới. */
  const [focusApplied, setFocusApplied] = useState<string | null>(null);
  const [resetKey, setResetKey] = useState(`${requestedRootId ?? ""}:${direction}`);

  const nextResetKey = `${requestedRootId ?? ""}:${direction}`;
  if (nextResetKey !== resetKey) {
    // Root or direction changed underneath us (toolbar toggle) — this is a
    // different query space, not an incremental extension of what's loaded,
    // so start over rather than mixing DESCENDANTS/ANCESTORS graphs.
    setResetKey(nextResetKey);
    setExpandedIds(new Set(requestedRootId ? [requestedRootId] : []));
    setFetchedIds([requestedRootId]);
    // Gốc mới ⇒ đường từ gốc tới người đang xem là một đường KHÁC. Quên đường cũ đi để nó được
    // tính lại, nếu không thì đổi gốc xong người ấy không còn lối nào hiện ra.
    setFocusApplied(null);
  }

  const focusId = typeof requestedFocusId === "string" && requestedFocusId.trim()
    ? requestedFocusId.trim()
    : null;

  /**
   * Kế hoạch nạp: các nhánh người dùng đã bung, cộng **tối đa hai lượt gọi cho người đang xem**.
   *
   * <h2>Vì sao hai lượt chứ không một lượt `BOTH`</h2>
   * `BOTH` duyệt cả hai chiều trong CÙNG một ngân sách node (`maxNodes`). Ở một dòng họ thật, hậu
   * duệ nở theo cấp số nhân còn tổ tiên chỉ là một sợi dây — nên `BOTH` tiêu hết ngân sách vào con
   * cháu và **cắt mất chuỗi tổ tiên trước khi chạm gốc**, đúng thứ duy nhất mà cú nhảy cần. Tách
   * làm hai lượt thì mỗi lượt có ngân sách riêng và lượt tổ tiên không bao giờ bị con cháu chen chỗ.
   *
   * Lượt hậu duệ chỉ sâu một đời: "cộng con cái trực tiếp", không hơn.
   */
  const plans = useMemo<BranchPlan[]>(() => {
    const list: BranchPlan[] = fetchedIds.map((branchRootId) => ({
      id: branchRootId,
      depth: branchRootId === requestedRootId ? initialDepth : expandDepth,
      direction,
      forFocus: false,
    }));
    if (fetchFocus && focusId !== null && !fetchedIds.includes(focusId)) {
      list.push({
        id: focusId,
        depth: FOCUS_ANCESTOR_DEPTH,
        direction: "ANCESTORS",
        forFocus: true,
      });
      list.push({
        id: focusId,
        depth: FOCUS_DESCENDANT_DEPTH,
        direction: "DESCENDANTS",
        forFocus: true,
      });
    }
    return list;
  }, [fetchedIds, requestedRootId, initialDepth, expandDepth, direction, focusId, fetchFocus]);

  const results = useQueries({
    queries: plans.map(({ id: branchRootId, depth, direction: planDirection }) => {
      return {
        queryKey: queryKeys.treeBranch(
          branchRootId ?? SERVER_DEFAULT_ROOT_KEY,
          depth,
          planDirection,
          maxNodesPerFetch,
          audience ?? "pending"
        ),
        queryFn: () =>
          treeApi.getTreeFor(audience ?? "public", {
            rootId: branchRootId,
            depth,
            direction: planDirection,
            includeSpouses,
            // Bản công khai không nhận `maxNodes` và tự bỏ qua (xem treeApi).
            maxNodes: maxNodesPerFetch,
          }),
        // Phiên chưa ngã ngũ thì KHÔNG hỏi: xem `useTreeAudience`. Hỏi sớm là
        // tự chuốc một lượt gọi công khai thừa cho mọi thành viên.
        enabled: audience !== null,
        staleTime: 60_000,
        retry: (failureCount: number, error: unknown) => {
          // 400/401/403/404 đều là câu trả lời CUỐI CÙNG của máy chủ, không
          // phải sự cố tạm thời: gốc sai định dạng, chưa đăng nhập, ngoài phạm
          // vi, hoặc không được biết là có tồn tại. Thử lại chỉ làm người dùng
          // chờ lâu hơn để nhận đúng câu trả lời ấy.
          if (error instanceof ApiError && error.status < 500) return false;
          return failureCount < 2;
        },
      };
    }),
  });

  const rootQuery = results[0];

  /**
   * **Gốc thật sự đang mở.**
   *
   * Khi người dùng chỉ định thì đó là id của họ. Khi không, nó chỉ được biết
   * **sau** lượt gọi đầu tiên, qua trường `rootId` của chính phản hồi — hợp
   * đồng nói rõ "gốc đã chọn nằm ở `rootId` của phản hồi". Chuỗi rỗng là trạng
   * thái "chưa biết", và `computeVisibleSubgraph` trả về cây rỗng cho nó, đúng
   * như khi dữ liệu chưa về.
   */
  const effectiveRootId = requestedRootId ?? rootQuery?.data?.rootId ?? "";

  const loadingIds = useMemo(() => {
    const ids = new Set<string>();
    results.forEach((r, i) => {
      const id = plans[i]?.id ?? effectiveRootId;
      if (r.isFetching && id) ids.add(id);
    });
    return ids;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results, plans, effectiveRootId]);

  const merged = useMemo(
    () => mergeProjections(results.map((r) => r.data)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [results]
  );

  /**
   * Những người nằm trên **đường thẳng từ gốc tới người đang xem** — tập phải ở trạng thái mở để
   * người ấy lộ ra. Rỗng khi chưa có ai được chỉ định, khi dữ liệu chưa về, hoặc khi người ấy
   * không nối được về gốc đang mở (xem `focus-path.ts`).
   */
  const focusPath = useMemo<readonly string[] | null>(() => {
    if (focusId === null || !effectiveRootId) return null;
    return pathFromRootTo(merged.nodesById, merged.edgesById.values(), effectiveRootId, focusId);
  }, [merged, effectiveRootId, focusId]);

  const focusExpansion = useMemo<ReadonlySet<string>>(
    () => (focusPath === null ? EMPTY_IDS : new Set(focusPath)),
    [focusPath]
  );

  /**
   * Gộp đường ấy vào `expandedIds` **đúng một lần cho mỗi người được nhắm tới**, chứ không suy ra
   * ở mỗi lần dựng.
   *
   * Vì sao quan trọng: nếu để nó là giá trị suy ra thì người dùng **thu gọn một người trên đường
   * ấy** sẽ thấy nhánh bung lại ngay lập tức — một cái nút bấm vào không ăn thua. Gộp một lần rồi
   * thôi thì thao tác tay luôn thắng, đúng nguyên tắc "người dùng tự mở phần mình cần".
   *
   * Đặt trong thân hàm dựng (không phải `useEffect`) theo đúng khuôn mẫu "trạng thái dẫn xuất từ
   * props" mà `resetKey` phía trên đang dùng: React dựng lại ngay, không vẽ ra một nhịp cây sai.
   */
  if (focusId !== focusApplied) {
    if (focusId === null) {
      setFocusApplied(null);
    } else if (focusExpansion.size > 0) {
      setFocusApplied(focusId);
      setExpandedIds((prev) => {
        const next = new Set(prev);
        for (const id of focusExpansion) next.add(id);
        return next;
      });
    }
  }

  /**
   * Gốc **luôn** ở trạng thái mở, kể cả khi ta mới vừa biết nó là ai.
   *
   * Không gộp thẳng vào `expandedIds` bằng một `useEffect`: gốc do máy chủ chọn
   * đến sau lượt dựng đầu, nên một effect sẽ vẽ **một nhịp cây trống** trước
   * khi mở ra — đúng cái nhấp nháy mà việc bỏ màn chọn gốc định loại đi.
   *
   * Đường tới người đang xem đi kèm ở đây cho **nhịp dựng đầu tiên sau khi dữ liệu về**: lúc ấy
   * `setExpandedIds` ngay trên kia chưa kịp có hiệu lực trong chính lần dựng này.
   */
  const expandedWithRoot = useMemo(() => {
    const needsRoot = Boolean(effectiveRootId) && !expandedIds.has(effectiveRootId);
    const needsFocus = [...focusExpansion].some((id) => !expandedIds.has(id));
    if (!needsRoot && !needsFocus) return expandedIds;
    const next = new Set(expandedIds);
    if (effectiveRootId) next.add(effectiveRootId);
    for (const id of focusExpansion) next.add(id);
    return next;
  }, [expandedIds, effectiveRootId, focusExpansion]);

  const visible = useMemo(
    () =>
      computeVisibleSubgraph(
        merged.nodesById,
        merged.edgesById,
        effectiveRootId,
        expandedWithRoot
      ),
    [merged, expandedWithRoot, effectiveRootId]
  );

  const expand = useCallback(
    (nodeId: string) => {
      setExpandedIds((prev) => (prev.has(nodeId) ? prev : new Set(prev).add(nodeId)));
      setFetchedIds((prev) => {
        if (prev.includes(nodeId)) return prev;
        const node = merged.nodesById.get(nodeId);
        // Only issue a new fetch if this node's descendants genuinely
        // aren't fully loaded yet — many expand clicks are "free" because
        // initialDepth already prefetched one ring further than what's shown.
        if (!node?.hasMoreDescendants) return prev;
        return [...prev, nodeId];
      });
    },
    [merged]
  );

  const collapse = useCallback(
    (nodeId: string) => {
      if (nodeId === effectiveRootId) return;
      setExpandedIds((prev) => {
        if (!prev.has(nodeId)) return prev;
        const next = new Set(prev);
        next.delete(nodeId);
        return next;
      });
    },
    [effectiveRootId]
  );

  const toggle = useCallback(
    (nodeId: string) => {
      if (expandedWithRoot.has(nodeId)) collapse(nodeId);
      else expand(nodeId);
    },
    [expandedWithRoot, expand, collapse]
  );

  const rootError = rootQuery?.error;
  const notFound = rootError instanceof ApiError && rootError.status === 404;
  /** Khách gọi bản thành viên, hoặc phiên đã chết. Không phải sự cố. */
  const unauthorized =
    rootError instanceof ApiError && (rootError.status === 401 || rootError.status === 403);
  /**
   * Máy chủ từ chối chính `rootId` mà người dùng mang tới — id sai định dạng,
   * hoặc một liên kết chia sẻ đã cũ. Chỉ xảy ra khi CÓ `requestedRootId`: bỏ
   * trống tham số thì máy chủ tự chọn và không còn gì để từ chối.
   *
   * Với người dùng, đây không phải sự cố mà là "điểm bắt đầu này không mở
   * được", nên nó dẫn tới bước chọn gốc — nơi còn một lối ra nữa là mở thẳng
   * cây mặc định (xem `<TreeRootPicker>`).
   */
  const rootRejected = rootError instanceof ApiError && rootError.status === 400;

  /**
   * Con cái đã tải về của một người — **đọc từ đồ thị đã nạp, không gọi thêm mạng**.
   *
   * Kiểu xem *Danh sách theo đời* dùng nó để đi xuống từng đời một. Nó cố ý trả về mảng rỗng khi
   * chưa tải: người gọi biết phải gọi `expand()` trước, và không có cách nào để một phép đọc vô
   * tình kéo về cả cây.
   */
  const childrenOf = useCallback(
    (personId: string): TreeNode[] => {
      const out: TreeNode[] = [];
      for (const edge of merged.edgesById.values()) {
        if (edge.relType !== "PARENT_BIO" && edge.relType !== "PARENT_ADOPT") continue;
        if (edge.source !== personId) continue;
        const child = merged.nodesById.get(edge.target);
        if (child && !out.some((n) => n.id === child.id)) out.push(child);
      }
      return out.sort((a, b) =>
        a.person.displayName.localeCompare(b.person.displayName, "vi")
      );
    },
    [merged]
  );

  /**
   * Cú nhảy **đã hỏi xong mà vẫn không nối được** người ấy về gốc đang mở.
   *
   * Ba nguyên nhân có thật: họ thuộc một chi khác, chuỗi tổ tiên của họ dài hơn
   * {@link FOCUS_ANCESTOR_DEPTH}, hoặc một mắt xích trên đường ấy bị lọc riêng tư. Cả ba đều là
   * hệ thống chạy đúng, nên đây KHÔNG phải lỗi — `<TreeCanvas>` chuyển sang mở cây từ chính người
   * ấy, tức cú nhảy vẫn tới đích, chỉ khác điểm xuất phát.
   *
   * <h2>Phải chờ MỌI lượt gọi đã chạy xong ít nhất một lần</h2>
   * Dùng `isFetched`, không dùng `!isFetching`. Khác biệt là một lỗi có thật: giữa lúc `/me` trả
   * lời (sinh ra `focusId`) và lúc hai lượt gọi cho người ấy **bắt đầu**, mọi truy vấn đang có
   * đều `isFetching === false` — nên `!isFetching` kết luận "không nối được" trong đúng một nhịp
   * và màn hình tự đổi gốc oan. Triệu chứng đã đo được: thanh công cụ biến mất giữa chừng vì
   * canvas rơi lại về trạng thái đang tải. `isFetched` chỉ bật sau khi một lượt gọi đã **xong**,
   * nên một lượt vừa được thêm vào kế hoạch luôn kéo kết luận này về `false`.
   */
  const allQueriesSettled = results.length > 0 && results.every((r) => r.isFetched);
  /** Người ấy có mặt trong dữ liệu đã tải hay không — hai ca hỏng khác hẳn nhau. */
  const focusLoaded = focusId !== null && merged.nodesById.has(focusId);
  const focusSettled =
    focusId !== null && Boolean(effectiveRootId) && focusId !== effectiveRootId && allQueriesSettled;

  /** Có mặt, nhưng không nối được về gốc đang mở ⇒ mở cây từ chính họ là cách tới đích. */
  const focusUnreachable = focusSettled && focusLoaded && focusExpansion.size === 0;

  /**
   * **Không có mặt.** Máy chủ trả `404`, hoặc bộ lọc riêng tư không cho họ vào projection này.
   *
   * KHÔNG đổi gốc trong ca này: đổi gốc sang một id mà máy chủ vừa từ chối thì màn hình rơi
   * thẳng vào "Không tìm thấy nhân khẩu gốc của cây" — tức một cú nhảy hỏng kéo sập cả phả đồ.
   * Người gọi chỉ nên **nói ra một câu trung tính**, và chỉ khi cú nhảy là do người dùng chủ ý.
   */
  const focusMissing = focusSettled && !focusLoaded;

  return {
    audience,
    /**
     * Gốc đang mở **thật sự**. Khác `rootId` truyền vào ở đúng một ca, ca quan
     * trọng nhất: người dùng không chỉ định gì và máy chủ đã chọn hộ.
     */
    rootId: effectiveRootId,
    nodes: visible.nodes,
    edges: visible.edges,
    expandedIds: expandedWithRoot,
    loadingIds,
    expand,
    collapse,
    toggle,
    // `audience === null` (đang chờ Keycloak) cũng là "đang tải": truy vấn bị
    // hoãn nên `isLoading` của React Query là `false`, mà màn hình thì chưa có
    // gì để vẽ.
    isLoadingInitial: audience === null || (rootQuery?.isLoading ?? false),
    isFetchingAny: results.some((r) => r.isFetching),
    // Chỉ lỗi của những lượt gọi DỰNG NÊN phả đồ mới là lỗi của phả đồ. Xem `BranchPlan.forFocus`.
    error:
      notFound || unauthorized || rootRejected
        ? null
        : (results.find((r, i) => r.error && !plans[i]?.forFocus)?.error ?? null),
    /** Guest-hidden-root (or a truly missing id) — render "not found", never a generic error. */
    rootNotFound: notFound,
    unauthorized,
    rootRejected,
    truncated: merged.truncated,
    truncatedNodeIds: merged.truncatedNodeIds,
    loadedCount: merged.nodesById.size,
    /** Người đang xem, sau khi đã chuẩn hoá (chuỗi rỗng ⇒ `null`). */
    focusId,
    /**
     * Đường từ gốc xuống người ấy, **theo thứ tự** `[gốc, …, người ấy]`. `null` khi chưa có ai
     * được nhắm tới, hoặc khi chưa nối được — xem {@link focusUnreachable}.
     */
    focusPath,
    focusUnreachable,
    focusMissing,
    /** Toàn bộ nhân khẩu ĐÃ TẢI, kể cả phần đang gập. Dành cho kiểu xem danh sách theo đời. */
    loadedNodesById: merged.nodesById as ReadonlyMap<string, TreeNode>,
    childrenOf,
  };
}
