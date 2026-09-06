"use client";

import { useCallback, useState } from "react";
import { ReactFlowProvider, useReactFlow } from "@xyflow/react";
import { Alert, Button, Empty, Spin } from "antd";
import { useTranslations } from "next-intl";
import { useTreeCanvas } from "@/hooks/use-tree-canvas";
import { ApiError } from "@/lib/api/http";
import { useAuth } from "@/lib/auth/auth-context";
import { WHOLE_TREE_FIT_VIEW_OPTIONS } from "@/lib/tree/fit-view";
import { TreeCanvasContext } from "./tree-canvas-context";
import { TreeCanvasInner } from "./tree-canvas-inner";
import { TreeToolbar, type TreeToolbarProps } from "./tree-toolbar";
import { TreeLegend } from "./tree-legend";
import { TreeTruncatedBanner } from "./tree-truncated-banner";
import { PersonProfileDrawer } from "@/components/person/person-profile-drawer";
import type { TreeDirection } from "@/types/api";
import type { TreeViewMode } from "@/types/tree-ui";

export interface TreeCanvasProps {
  rootId: string;
}

/**
 * Top-level phả đồ canvas. Composition only — all the actual state lives in
 * useTreeCanvas (data/lazy-load) and TreeCanvasInner (React Flow + layout).
 * See src/mocks/tree-graph/ for what backs this in dev (a ~3,500-node
 * generated graph, not the ~5-node Sprint 1 fixture) and the frontend
 * README for the Sprint-2 performance measurements against it.
 */
export function TreeCanvas({ rootId }: TreeCanvasProps) {
  const t = useTranslations("tree");
  const tAuth = useTranslations("auth");
  const { isAuthenticated, login } = useAuth();
  const [viewMode, setViewMode] = useState<TreeViewMode>("hierarchical");
  // F3 profile panel. Kept as a drawer rather than a route change so the
  // canvas keeps its viewport and its lazily-loaded subgraph.
  const [selectedPersonId, setSelectedPersonId] = useState<string | null>(null);
  const [direction, setDirection] = useState<TreeDirection>("DESCENDANTS");

  const {
    nodes,
    edges,
    expandedIds,
    loadingIds,
    toggle,
    isLoadingInitial,
    error,
    rootNotFound,
    truncated,
    truncatedNodeIds,
    loadedCount,
  } = useTreeCanvas({ rootId, direction });

  if (rootNotFound) {
    // Same non-disclosure as GET /persons/{id} 404 for a guest: this reads
    // identically whether the id is unknown or a hidden living person.
    return (
      <div className="flex h-[70vh] items-center justify-center">
        <Empty description={t("rootNotFound")} />
      </div>
    );
  }

  if (isLoadingInitial) {
    return (
      <div className="flex h-[70vh] items-center justify-center">
        <Spin size="large" tip={t("loading")}>
          <div className="h-32 w-32" />
        </Spin>
      </div>
    );
  }

  // 401 với người CHƯA đăng nhập không phải sự cố — đó là luật riêng tư đang
  // chạy đúng (BA v2 §10). Hiện lối vào đăng nhập chứ không phải "Không tải
  // được": một thông báo lỗi đỏ ở đây khiến khách tưởng hệ thống hỏng.
  if (error instanceof ApiError && error.status === 401 && !isAuthenticated) {
    return (
      <div className="p-6">
        <Alert
          // "warning" (hổ phách) chứ không phải "info": token `colorInfo` của
          // bộ chủ đề là xanh xám #2c3e50, đổ ra một mảng xám lạnh giữa nền
          // kem — lạc hẳn khỏi bảng màu đỏ sẫm / kem / hổ phách của spec, và
          // trông giống sự cố hơn là một lời mời.
          type="warning"
          showIcon
          message={t("authRequired")}
          description={t("authRequiredHint")}
          action={
            <Button type="primary" size="small" onClick={login}>
              {tAuth("login")}
            </Button>
          }
        />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6">
        <Alert type="error" showIcon message={t("loadError")} />
      </div>
    );
  }

  return (
    // Trên điện thoại phải trừ đi chiều cao thanh điều hướng dưới cùng (<MobileNav>, `fixed
    // bottom-0`, 3.5rem). Không trừ thì dải đáy canvas nằm vĩnh viễn dưới thanh đó: thẻ nhân khẩu
    // rơi vào đấy không chạm được, mà `fitView` lại canh nội dung theo TOÀN BỘ khung canvas nên
    // phả đồ mở ra là đã có thẻ nằm khuất sẵn.
    <div className="flex h-[calc(80vh-3.5rem)] min-h-[420px] flex-col border border-border md:h-[80vh] md:min-h-[520px]">
      {/* <ReactFlowProvider> bọc CẢ thanh công cụ, không chỉ canvas: nút "Thu toàn cây" phải gọi
          được `fitView` của cùng một phiên bản canvas. Provider chỉ là kho trạng thái, bọc rộng
          hơn không làm canvas dựng lại. */}
      <ReactFlowProvider>
        <TreeToolbarWithCamera
          viewMode={viewMode}
          onViewModeChange={setViewMode}
          direction={direction}
          onDirectionChange={setDirection}
          nodeCount={loadedCount}
        />
        {truncated && <TreeTruncatedBanner truncatedCount={truncatedNodeIds.size} />}
        <div className="relative min-h-0 flex-1 bg-bg-page">
          <TreeCanvasContext.Provider value={{ rootId, expandedIds, loadingIds, toggle }}>
            <TreeCanvasInner
              rootId={rootId}
              nodes={nodes}
              edges={edges}
              viewMode={viewMode}
              onSelectPerson={setSelectedPersonId}
            />
          </TreeCanvasContext.Provider>
          <TreeLegend />
        </div>
      </ReactFlowProvider>
      <PersonProfileDrawer
        personId={selectedPersonId}
        onClose={() => setSelectedPersonId(null)}
      />
    </div>
  );
}

/**
 * Nối thanh công cụ với máy quay của canvas.
 *
 * Tách riêng để <TreeToolbar> vẫn là component thuần: `useReactFlow()` chỉ chạy được bên trong
 * <ReactFlowProvider>, mà chính <TreeCanvas> là nơi dựng provider nên không tự gọi được.
 */
function TreeToolbarWithCamera(props: Omit<TreeToolbarProps, "onFitWholeTree">) {
  const { fitView } = useReactFlow();

  // Không áp sàn MIN_INITIAL_ZOOM: đây là lúc người dùng CHỦ Ý xin nhìn toàn cảnh cả họ.
  const fitWholeTree = useCallback(() => {
    void fitView(WHOLE_TREE_FIT_VIEW_OPTIONS);
  }, [fitView]);

  return <TreeToolbar {...props} onFitWholeTree={fitWholeTree} />;
}
