"use client";

import { Button, Segmented, Space, Tag } from "antd";
import {
  ApartmentOutlined,
  BorderOuterOutlined,
  CompressOutlined,
  RadarChartOutlined,
} from "@ant-design/icons";
import { useTranslations } from "next-intl";
import type { TreeDirection } from "@/types/api";
import type { TreeViewMode } from "@/types/tree-ui";

export interface TreeToolbarProps {
  viewMode: TreeViewMode;
  onViewModeChange: (mode: TreeViewMode) => void;
  direction: TreeDirection;
  onDirectionChange: (direction: TreeDirection) => void;
  nodeCount: number;
  /**
   * "Thu toàn cây" — canh khung KHÔNG áp sàn phóng, xem
   * `WHOLE_TREE_FIT_VIEW_OPTIONS`. Nhận qua prop chứ không tự gọi `useReactFlow()`
   * ở đây, để thanh công cụ vẫn là component thuần và test được mà không cần
   * dựng cả `<ReactFlowProvider>`.
   */
  onFitWholeTree?: () => void;
}

/**
 * View-mode switch never resets `expandedIds`/loaded data (see
 * useTreeCanvas) — only the layout algorithm changes, which is what makes
 * "chuyển đổi mượt, giữ nguyên vùng đang xem" possible at all.
 */
export function TreeToolbar({
  viewMode,
  onViewModeChange,
  direction,
  onDirectionChange,
  nodeCount,
  onFitWholeTree,
}: TreeToolbarProps) {
  const t = useTranslations("tree");

  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-bg-card px-3 py-2">
      <Space size="middle" wrap>
        <Segmented
          value={viewMode}
          onChange={(v) => onViewModeChange(v as TreeViewMode)}
          options={[
            { value: "hierarchical", label: t("viewMode.hierarchical"), icon: <ApartmentOutlined /> },
            { value: "radial", label: t("viewMode.radial"), icon: <RadarChartOutlined /> },
            { value: "matrix", label: t("viewMode.matrix"), icon: <BorderOuterOutlined /> },
          ]}
        />
        <Segmented
          value={direction}
          onChange={(v) => onDirectionChange(v as TreeDirection)}
          options={[
            { value: "DESCENDANTS", label: t("direction.descendants") },
            { value: "ANCESTORS", label: t("direction.ancestors") },
            { value: "BOTH", label: t("direction.both") },
          ]}
        />
      </Space>
      <Space size="small" wrap>
        {/* Nút nằm TRONG thanh công cụ, không phải một lớp phủ trên canvas: ba góc canvas đã có
            <Controls> (trên-phải), <MiniMap> (dưới-phải) và <TreeLegend> (dưới-trái), thêm một
            lớp phủ nữa là lại tái diễn đúng lỗi cũ — lớp phủ nuốt thao tác chạm của người dùng
            điện thoại. Ở đây thì không thể đè lên thứ gì.
            `size="large"` cho vùng chạm 40px, cộng `min-h-11` (44px) để đạt ngưỡng chạm trên
            điện thoại chứ không chỉ vừa đủ WCAG 2.5.8. */}
        {onFitWholeTree && (
          // Cố ý KHÔNG bọc <Tooltip>: trên điện thoại, chạm vào nút sẽ bung thêm một bong bóng
          // chú thích nổi đè lên canvas — đúng loại lớp phủ đã từng nuốt thao tác chạm ở màn này.
          // Nút đã có nhãn chữ rõ ràng; phần giải thích để ở `title` cho người dùng chuột.
          <Button
            size="large"
            icon={<CompressOutlined />}
            onClick={onFitWholeTree}
            data-testid="tree-fit-whole"
            title={t("fitWholeTreeHint")}
            className="!min-h-11"
          >
            {t("fitWholeTree")}
          </Button>
        )}
        <Tag data-testid="tree-loaded-count" className="!m-0" color="default">
          {t("loadedCount", { count: nodeCount })}
        </Tag>
      </Space>
    </div>
  );
}
