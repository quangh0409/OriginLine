"use client";

import { Handle, Position, type Node, type NodeProps } from "@xyflow/react";
import { Tag } from "antd";
import { LoadingOutlined, MinusOutlined, PlusOutlined } from "@ant-design/icons";
import { useLocale, useTranslations } from "next-intl";
import { useTreeCanvasContext } from "./tree-canvas-context";
import { BADGE_META, displayBadges } from "@/lib/tree/badges";
import type { PersonNodeData } from "@/lib/tree/to-flow-elements";

export type PersonFlowNode = Node<PersonNodeData, "person">;

/**
 * Custom React Flow node for one nhân khẩu. Deliberately renders ONLY what
 * `TreeNode`/`PersonSummaryDto` already carries — no kinship title (that's
 * `useKinship`, Sprint 3's F5), no field invented to hint at hidden data.
 * The living/deceased visual split (dot + card tone) is the one thing the
 * F2 brief explicitly asks for; badges come straight from `TreeNode.badges`
 * (never inferred here — see contracts/README §7.6).
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
          : "border-border-dark bg-[#f2ede2]",
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
            data-testid="person-node-name" className="truncate font-serif text-[13px] font-semibold leading-tight text-text-main"
            title={person.displayName}
          >
            {person.displayName}
          </div>
          <div className="mt-0.5 truncate text-[10.5px] text-text-muted">
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

      {(person.birthYear || person.deathYear) && (
        <div className="mt-1 text-[10.5px] text-text-muted">
          {person.birthYear ?? "?"} – {person.isAlive ? "" : (person.deathYear ?? "?")}
        </div>
      )}

      {badges.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1">
          {badges.map((b) => (
            <Tag key={b} color={BADGE_META[b].color} bordered={false} className="!m-0 !px-1.5 !text-[9.5px] !leading-4">
              {locale === "vi" ? BADGE_META[b].vi : BADGE_META[b].en}
            </Tag>
          ))}
        </div>
      )}

      {offersToggle && (
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            toggle(id);
          }}
          className="absolute -bottom-3 left-1/2 flex h-6 w-6 -translate-x-1/2 items-center justify-center rounded-full border border-border-dark bg-bg-card text-[11px] text-primary shadow-sm hover:bg-primary-light focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
          aria-label={isExpanded ? t("collapseBranch") : t("expandBranch")}
          aria-expanded={isExpanded}
        >
          {isLoading ? <LoadingOutlined spin /> : isExpanded ? <MinusOutlined /> : <PlusOutlined />}
        </button>
      )}
    </div>
  );
}
