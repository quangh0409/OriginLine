"use client";

import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  EnvironmentOutlined,
  SwapOutlined,
} from "@ant-design/icons";
import { Tag } from "antd";
import { useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { KinshipPathStep, LcaInfo } from "@/types/api";

export interface KinshipPathDiagramProps {
  path: KinshipPathStep[] | undefined;
  lca: LcaInfo | null | undefined;
}

/**
 * The evidence trail: `from` walks UP to the tổ chung (LCA), then DOWN to
 * `to`. contracts/openapi.yaml is explicit that this is the point of the
 * feature — "FE vẽ để người dùng tự kiểm chứng — đây là thứ tạo niềm tin cho
 * tính năng, đừng ẩn đi" — so it is shown by default, not folded behind a
 * "details" toggle.
 *
 * Laid out as a vertical ladder rather than a graph: it reads top-to-bottom on
 * a phone, needs no panning, and each rung is a full-size touch/read target,
 * which matters because the people most likely to double-check a danh xưng are
 * the oldest members of the clan.
 *
 * A step whose person is a hidden living relative arrives with a generic
 * `displayName` from the backend and its step intact — the step is NOT
 * dropped, because a shorter ladder would misstate the distance. Whatever
 * label the server chose is rendered verbatim; the UI adds no annotation
 * explaining why a name looks generic.
 */
export function KinshipPathDiagram({ path, lca }: KinshipPathDiagramProps) {
  const t = useTranslations("kinship");
  if (!path || path.length === 0) return null;

  return (
    <ol className="m-0 list-none p-0">
      {path.map((step, index) => {
        const isLca = isPresent(lca?.personId) && step.personId === lca?.personId;
        const isLast = index === path.length - 1;

        return (
          <li key={`${step.personId}-${index}`} className="relative pl-9">
            {/* Connector line between rungs. */}
            {!isLast && (
              <span
                aria-hidden
                className="absolute left-[13px] top-7 h-[calc(100%-1.25rem)] w-px"
                style={{ background: colorVars.borderDark }}
              />
            )}

            <span
              aria-hidden
              className="absolute left-0 top-1 flex h-[26px] w-[26px] items-center justify-center rounded-full border text-than"
              style={{
                background: isLca ? colorVars.primary : colorVars.bgCard,
                borderColor: isLca ? colorVars.primary : colorVars.borderDark,
                color: isLca ? colorVars.bgCard : colorVars.textMuted,
              }}
            >
              <StepIcon direction={step.direction} />
            </span>

            <div className="flex flex-wrap items-center gap-x-2 gap-y-1 pb-4 pt-1">
              <span className="text-than font-medium text-text-main">
                {step.displayName}
              </span>
              {isPresent(step.generation) && (
                <span className="text-than text-text-muted">
                  {t("generationValue", { n: step.generation })}
                </span>
              )}
              {isLca && (
                <Tag
                  bordered={false}
                  color={colorVars.accentText}
                  style={{ color: colorVars.bgPage }}
                  className="!m-0"
                >
                  {t("lcaTag")}
                </Tag>
              )}
              <span className="basis-full text-than text-text-muted">
                {t(`direction.${step.direction}`)}
              </span>
            </div>
          </li>
        );
      })}
    </ol>
  );
}

function StepIcon({ direction }: { direction: KinshipPathStep["direction"] }) {
  switch (direction) {
    case "UP":
      return <ArrowUpOutlined />;
    case "DOWN":
      return <ArrowDownOutlined />;
    case "ACROSS":
      return <SwapOutlined />;
    default:
      return <EnvironmentOutlined />;
  }
}
