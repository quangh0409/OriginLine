"use client";

import { Handle, Position, type Node, type NodeProps } from "@xyflow/react";
import { Tag } from "antd";
import { LoadingOutlined, MinusOutlined, PlusOutlined } from "@ant-design/icons";
import { useLocale, useTranslations } from "next-intl";
import { useTreeCanvasContext } from "./tree-canvas-context";
import { BADGE_META, displayBadges } from "@/lib/tree/badges";
import { nodeDatesState } from "@/lib/tree/life-dates";
import {
  TOGGLE_HIT_OVERHANG,
  TOGGLE_HIT_SIZE,
  TOGGLE_KNOB_INSET,
  TOGGLE_KNOB_SIZE,
} from "@/lib/tree/layout-constants";
import type { PersonNodeData } from "@/lib/tree/to-flow-elements";

export type PersonFlowNode = Node<PersonNodeData, "person">;

/**
 * Custom React Flow node for one nhân khẩu. Deliberately renders ONLY what
 * `TreeNode`/`PersonSummaryDto` already carries — no kinship title (that's
 * `useKinship`, Sprint 3's F5), no field invented to hint at hidden data.
 * The living/deceased visual split (dot + card tone) is the one thing the
 * F2 brief explicitly asks for; badges come straight from `TreeNode.badges`
 * (never inferred here — see contracts/README §7.6).
 *
 * Dòng ngày là chỗ duy nhất trên thẻ có một trạng thái "trống" mang nghĩa, và
 * nghĩa ấy khác nhau giữa người còn sống và người đã khuất — phép phân biệt
 * nằm trọn trong `nodeDatesState` (src/lib/tree/life-dates.ts), đọc chỗ đó
 * trước khi sửa bất cứ điều gì ở đây.
 */
export function PersonNode({ id, data }: NodeProps<PersonFlowNode>) {
  const t = useTranslations("tree");
  const locale = useLocale();
  const { rootId, expandedIds, loadingIds, toggle } = useTreeCanvasContext();
  const { treeNode, hasLoadableChildren } = data;
  const person = treeNode.person;
  const isExpanded = expandedIds.has(id);
  const isLoading = loadingIds.has(id);
  const badges = displayBadges(treeNode.badges);
  const dates = nodeDatesState(person);
  // Nút gốc luôn ở trạng thái mở và không thu gọn được (useTreeCanvas.collapse bỏ qua nó, vì thu
  // gọn gốc thì canvas trống trơn). Vẫn vẽ nút bấm cho nó là dựng lên một điều khiển bấm vào
  // không xảy ra gì — người dùng đọc ra là "hỏng", chứ không đọc ra là "cố ý".
  const offersToggle = hasLoadableChildren && id !== rootId;

  return (
    <div
      className={[
        "relative rounded-md border px-3 py-2 shadow-sm",
        person.isAlive
          ? "border-primary/50 bg-bg-card"
          : "border-border-dark bg-deceased",
      ].join(" ")}
      style={{ width: 208, minHeight: 96 }}
    >
      <Handle type="target" position={Position.Top} id="top" className="!h-2 !w-2 !border-0 !bg-border-dark" />
      <Handle type="source" position={Position.Bottom} id="bottom" className="!h-2 !w-2 !border-0 !bg-border-dark" />
      <Handle type="source" position={Position.Left} id="left" className="!h-2 !w-2 !border-0 !opacity-0" />
      <Handle type="target" position={Position.Right} id="right" className="!h-2 !w-2 !border-0 !opacity-0" />

      <div className="flex items-start justify-between gap-1.5">
        <div className="min-w-0">
          <div
            data-testid="person-node-name" className="truncate font-serif text-the-ten font-semibold leading-tight text-text-main"
            title={person.displayName}
          >
            {person.displayName}
          </div>
          <div className="mt-0.5 truncate text-the-phu text-text-muted">
            {person.generation != null ? t("generationShort", { n: person.generation }) : null}
            {person.primaryBranch ? ` · ${person.primaryBranch.name}` : ""}
          </div>
        </div>
        <span
          aria-hidden
          className={[
            "mt-1 h-2 w-2 shrink-0 rounded-full",
            person.isAlive ? "bg-success" : "bg-text-muted",
          ].join(" ")}
        />
        <span className="sr-only">{person.isAlive ? t("statusAlive") : t("statusDeceased")}</span>
      </div>

      {/* Ô ngày. Ba trạng thái, không phải hai — xem `nodeDatesState` để biết vì sao
          "chưa ai ghi" và "chưa chia sẻ" phải đọc ra khác nhau, và vì sao phép phân
          biệt ấy không rò rỉ gì. Cả ba đều chiếm đúng một dòng, nên chiều cao thẻ
          không đổi theo dữ liệu: một thẻ cao hơn thẻ bên cạnh tự nó đã là một tín
          hiệu về nội dung. */}
      {dates.kind === "known" && (
        <div className="mt-1 text-the-phu text-text-muted">{dates.text}</div>
      )}

      {dates.kind === "unrecorded" && (
        // Ô trống CÓ CHỦ ĐÍCH: nét đứt đọc ra là "chỗ này còn chờ được điền", đúng
        // nghĩa với người đã khuất — họ là dữ liệu công khai, nên vắng mặt chỉ có
        // một nghĩa duy nhất là chưa ai ghi.
        <div
          data-testid="person-node-dates-unrecorded"
          title={t("datesUnrecordedHint")}
          className="mt-1 w-fit border-b border-dashed border-border-dark text-the-phu text-text-muted"
        >
          {t("datesUnrecorded")}
        </div>
      )}

      {dates.kind === "unshared" && (
        // Không nét đứt, không biểu tượng ổ khoá, không dấu ba chấm: đây KHÔNG phải
        // một chỗ trống chờ điền mà là một trạng thái bình thường và trọn vẹn. Câu
        // chữ giống hệt nhau trên MỌI thẻ người còn sống đang trống ngày, nên nó
        // không nói được điều gì riêng về ai.
        <div
          data-testid="person-node-dates-unshared"
          title={t("datesUnsharedHint")}
          className="mt-1 w-fit text-the-phu text-text-muted"
        >
          {t("datesUnshared")}
        </div>
      )}

      {badges.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1">
          {badges.map((b) => (
            <Tag
              key={b}
              color={BADGE_META[b].color}
              style={{ color: BADGE_META[b].ink }}
              bordered={false}
              className="!m-0 !px-1.5 !text-the-nhan !leading-4"
            >
              {locale === "vi" ? BADGE_META[b].vi : BADGE_META[b].en}
            </Tag>
          ))}
        </div>
      )}

      {offersToggle && (
        // Nút = ô chạm TRONG SUỐT 80×80 (hệ toạ độ cây), đặt cân đối quanh vòng tròn 24px —
        // phần duy nhất nhìn thấy được, và nó vẫn nằm đúng chỗ cũ, giữa mép đáy thẻ. Ở mức phóng
        // mặc định 0.75 thì 80 × 0.75 = 60px THẬT, vượt sàn chạm 44px; trước đây là 24 × 0.75 =
        // 18px. Xem layout-constants.ts để biết vì sao là 80, vì sao đặt cân đối, và vì sao
        // KHÔNG phản-tỉ-lệ theo mức phóng.
        //
        // Vẫn đúng MỘT <button> cho mỗi thẻ: `.react-flow__node button` là bộ chọn mà cả e2e lẫn
        // phép đo vùng chạm đang dùng, thêm nút thứ hai là làm chúng nhập nhằng.
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            toggle(id);
          }}
          data-testid="tree-node-toggle"
          className="group/toggle absolute left-1/2 flex -translate-x-1/2 justify-center rounded-full border-0 bg-transparent p-0 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
          style={{
            width: TOGGLE_HIT_SIZE,
            height: TOGGLE_HIT_SIZE,
            bottom: -TOGGLE_HIT_OVERHANG,
            paddingTop: TOGGLE_KNOB_INSET,
            alignItems: "flex-start",
            // Vòng tiêu điểm phải nằm trên CHÍNH phần tử nhận tiêu điểm — bộ kiểm bàn phím đọc
            // `getComputedStyle(document.activeElement)`, nên đẩy vòng sang thẻ con là người dùng
            // bàn phím mất dấu và phép kiểm C-4.3 báo "không có dấu hiệu tiêu điểm nào".
            //
            // Nhưng ô chạm rộng 80px và trong suốt: vẽ vòng quanh mép nó là một khung to đùng
            // quanh khoảng không. `outline-offset` ÂM kéo vòng vào trong đúng (80 − 24) / 2 = 28px,
            // tức nó ôm khít vòng tròn nhìn thấy — và vì nút đã `rounded-full` (bán kính 40), vòng
            // co lại còn bán kính 12, đúng hình tròn 24px.
            outlineOffset: -(TOGGLE_HIT_SIZE - TOGGLE_KNOB_SIZE) / 2,
          }}
          aria-label={isExpanded ? t("collapseBranch") : t("expandBranch")}
          aria-expanded={isExpanded}
        >
          <span
            data-testid="tree-node-toggle-knob"
            style={{ width: TOGGLE_KNOB_SIZE, height: TOGGLE_KNOB_SIZE }}
            className="flex shrink-0 items-center justify-center rounded-full border border-border-dark bg-bg-card text-the-nut text-primary shadow-sm transition-colors group-hover/toggle:bg-primary-light"
          >
            {isLoading ? <LoadingOutlined spin /> : isExpanded ? <MinusOutlined /> : <PlusOutlined />}
          </span>
        </button>
      )}
    </div>
  );
}
