"use client";

import { Button, Segmented, Space, Tag } from "antd";
import {
  AimOutlined,
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
  /**
   * Số nhân khẩu ĐANG HIỆN trên canvas — kết quả của `computeVisibleSubgraph`, tức đúng những tấm
   * thẻ người dùng đếm được bằng mắt nếu thu hết cả cây vào khung.
   *
   * Đây là con số ĐƯỢC IN RA, và nó phải khớp với nhãn. Trước đây chỗ này nhận số đã TẢI: thu một
   * nhánh lại thì màn hình vơi đi mà con số đứng im, nên nhãn "đang hiển thị" nói sai.
   */
  visibleCount: number;
  /**
   * Số nhân khẩu đã tải về từ máy chủ — luôn ≥ {@link visibleCount}, vì nạp trước một vòng và vì
   * nhánh thu gọn vẫn giữ nguyên dữ liệu trong bộ nhớ đệm.
   *
   * KHÔNG in ra thành chữ (người trong họ không quan tâm phả đồ đã tải bao nhiêu), nhưng vẫn phải
   * lộ ra: đây là bề mặt duy nhất chứng minh giao kèo "không bao giờ tải cả cây" — được đọc qua
   * `data-loaded-count` ở e2e/tree-performance.spec.ts, và giải thích cho người dùng qua `title`.
   */
  loadedCount: number;
  /**
   * "Thu toàn cây" — canh khung KHÔNG áp sàn phóng, xem
   * `WHOLE_TREE_FIT_VIEW_OPTIONS`. Nhận qua prop chứ không tự gọi `useReactFlow()`
   * ở đây, để thanh công cụ vẫn là component thuần và test được mà không cần
   * dựng cả `<ReactFlowProvider>`.
   */
  onFitWholeTree?: () => void;
  /**
   * "Chọn người khác làm gốc" — mở `<TreeRootPicker>`.
   *
   * Nút này tồn tại vì màn chọn gốc **không còn** là bước bắt buộc trước khi
   * xem cây: máy chủ tự chọn gốc theo vai, nên đổi gốc trở thành một hành động
   * chủ động và phải có một chỗ nhìn thấy được để gọi ra. Trước đây nó chỉ tới
   * được qua màn "không tìm thấy gốc", tức chỉ khi có gì đó hỏng.
   */
  onChangeRoot?: () => void;
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
  visibleCount,
  loadedCount,
  onFitWholeTree,
  onChangeRoot,
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
            Cỡ nút: `min-h-11` (44px) là thứ bảo đảm ngưỡng chạm, KHÔNG phải `size`. Trước đây
            phải `size="large"` vì `controlHeight` của bộ chủ đề còn 32px; nay nó đã là 44px nên
            `size="large"` chỉ tổ đẩy nút lên 55px và làm thanh công cụ cao thêm một nấc — trên
            Pixel 5 thanh này đã ngốn 217px trong 523px, mỗi pixel lấy đi là một pixel phả đồ. */}
        {onFitWholeTree && (
          // Cố ý KHÔNG bọc <Tooltip>: trên điện thoại, chạm vào nút sẽ bung thêm một bong bóng
          // chú thích nổi đè lên canvas — đúng loại lớp phủ đã từng nuốt thao tác chạm ở màn này.
          // Nút đã có nhãn chữ rõ ràng; phần giải thích để ở `title` cho người dùng chuột.
          <Button
            icon={<CompressOutlined />}
            onClick={onFitWholeTree}
            data-testid="tree-fit-whole"
            title={t("fitWholeTreeHint")}
            className="!min-h-11"
          >
            {t("fitWholeTree")}
          </Button>
        )}
        {onChangeRoot && (
          // Cùng lý do với nút trên: không bọc <Tooltip>.
          //
          // Nhãn chữ ẩn đi dưới `sm` và nút thành icon-only: trên Pixel 5 thanh
          // công cụ đã ngốn 217px trong 523px, nên một nút có chữ ở đây sẽ đẩy
          // hàng nút xuống một dòng nữa và mỗi pixel lấy đi là một pixel phả đồ.
          // `aria-label` vẫn mang đủ câu nên tên gọi cho trình đọc màn hình
          // không đổi theo bề rộng màn hình — thứ nhìn thấy co lại, thứ nghe
          // thấy thì không.
          <Button
            icon={<AimOutlined />}
            onClick={onChangeRoot}
            data-testid="tree-change-root"
            aria-label={t("rootPicker.changeRoot")}
            className="!min-h-11 !min-w-11"
          >
            <span className="hidden sm:inline">{t("rootPicker.changeRoot")}</span>
          </Button>
        )}
        {/* Nói ĐÚNG thứ đang có trên màn hình. Số đã tải đi kèm dưới dạng thuộc tính máy đọc
            được + lời giải thích ở `title`, chứ không tranh chỗ với con số người dùng kiểm chứng
            được bằng mắt. */}
        <Tag
          data-testid="tree-node-count"
          data-visible-count={visibleCount}
          data-loaded-count={loadedCount}
          title={t("loadedCount", { count: loadedCount })}
          // `!text-base` (16px): <Tag> của Ant Design tự đặt cỡ chữ 12–14px, dưới sàn thân bài
          // 16px mà bộ chuẩn hiển thị của sản phẩm quy định. Đây là con số duy nhất trên thanh
          // công cụ nói về NỘI DUNG phả đồ, nó không được là chữ nhỏ nhất màn hình.
          className="!m-0 !text-base"
          color="default"
        >
          {t("visibleCount", { count: visibleCount })}
        </Tag>
      </Space>
    </div>
  );
}
