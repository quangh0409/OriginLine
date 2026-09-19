"use client";

import { useCallback, useMemo, useState } from "react";
import { useQueries } from "@tanstack/react-query";
import { treeApi, type TreeAudience } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { queryKeys } from "@/lib/query/keys";
import { mergeProjections } from "@/lib/tree/merge-projections";
import { computeVisibleSubgraph } from "@/lib/tree/visible-subgraph";
import { useTreeAudience } from "./use-tree-audience";
import type { TreeDirection } from "@/types/api";

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
  const [resetKey, setResetKey] = useState(`${requestedRootId ?? ""}:${direction}`);

  const nextResetKey = `${requestedRootId ?? ""}:${direction}`;
  if (nextResetKey !== resetKey) {
    // Root or direction changed underneath us (toolbar toggle) — this is a
    // different query space, not an incremental extension of what's loaded,
    // so start over rather than mixing DESCENDANTS/ANCESTORS graphs.
    setResetKey(nextResetKey);
    setExpandedIds(new Set(requestedRootId ? [requestedRootId] : []));
    setFetchedIds([requestedRootId]);
  }

  const results = useQueries({
    queries: fetchedIds.map((branchRootId) => {
      const depth = branchRootId === requestedRootId ? initialDepth : expandDepth;
      return {
        queryKey: queryKeys.treeBranch(
          branchRootId ?? SERVER_DEFAULT_ROOT_KEY,
          depth,
          direction,
          maxNodesPerFetch,
          audience ?? "pending"
        ),
        queryFn: () =>
          treeApi.getTreeFor(audience ?? "public", {
            rootId: branchRootId,
            depth,
            direction,
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
      const id = fetchedIds[i] ?? effectiveRootId;
      if (r.isFetching && id) ids.add(id);
    });
    return ids;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results, fetchedIds, effectiveRootId]);

  const merged = useMemo(
    () => mergeProjections(results.map((r) => r.data)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [results]
  );

  /**
   * Gốc **luôn** ở trạng thái mở, kể cả khi ta mới vừa biết nó là ai.
   *
   * Không gộp thẳng vào `expandedIds` bằng một `useEffect`: gốc do máy chủ chọn
   * đến sau lượt dựng đầu, nên một effect sẽ vẽ **một nhịp cây trống** trước
   * khi mở ra — đúng cái nhấp nháy mà việc bỏ màn chọn gốc định loại đi.
   */
  const expandedWithRoot = useMemo(() => {
    if (!effectiveRootId || expandedIds.has(effectiveRootId)) return expandedIds;
    return new Set(expandedIds).add(effectiveRootId);
  }, [expandedIds, effectiveRootId]);

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
    error:
      notFound || unauthorized || rootRejected
        ? null
        : (results.find((r) => r.error)?.error ?? null),
    /** Guest-hidden-root (or a truly missing id) — render "not found", never a generic error. */
    rootNotFound: notFound,
    unauthorized,
    rootRejected,
    truncated: merged.truncated,
    truncatedNodeIds: merged.truncatedNodeIds,
    loadedCount: merged.nodesById.size,
  };
}
