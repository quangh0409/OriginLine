"use client";

import { Tag } from "antd";
import { useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import type { KinshipResult, RelationFacts } from "@/types/api";

export interface KinshipFactsListProps {
  facts: RelationFacts | null | undefined;
  result: KinshipResult;
}

/**
 * Plain-language rendering of the normalised `RelationFacts` the rule engine
 * matched on, plus which rule set answered.
 *
 * Two reasons this is worth the screen space rather than being debug output:
 *  1. `collateralDegree` is the single distinction between `bác ruột` and
 *     `bác họ` (contracts/README §5.10). Showing it is how an elder verifies
 *     the answer instead of taking it on faith.
 *  2. `ruleSetScope` tells the clan council whether this branch is running an
 *     override or the seeded default — which is the first thing they need to
 *     know when they think a danh xưng is wrong.
 *
 * `genDelta`, `side` and the degrees are all server-computed. Nothing here is
 * re-derived; this component only translates codes into words.
 */
export function KinshipFactsList({ facts, result }: KinshipFactsListProps) {
  const t = useTranslations("kinship");
  if (!facts) return null;

  const degreeKey =
    facts.collateralDegree == null
      ? null
      : facts.collateralDegree <= 2
        ? `collateral.${facts.collateralDegree}`
        : "collateral.far";

  return (
    <dl className="m-0 grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-2">
      <Fact label={t("facts.genDelta")}>
        {facts.genDelta === 0
          ? t("genDelta.same")
          : facts.genDelta < 0
            ? t("genDelta.above", { n: Math.abs(facts.genDelta) })
            : t("genDelta.below", { n: facts.genDelta })}
      </Fact>

      <Fact label={t("facts.side")}>{t(`side.${facts.side}`)}</Fact>

      {degreeKey && (
        <Fact label={t("facts.collateral")}>
          {t(degreeKey, { n: facts.collateralDegree ?? 0 })}
        </Fact>
      )}

      {facts.isElder != null && (
        <Fact label={t("facts.birthOrder")}>
          {facts.isElder ? t("birthOrder.elder") : t("birthOrder.younger")}
        </Fact>
      )}

      {(facts.throughMarriage || facts.throughAdoption) && (
        <Fact label={t("facts.via")}>
          <span className="flex flex-wrap gap-1">
            {facts.throughMarriage && (
              <Tag bordered={false} className="!m-0">
                {t("via.marriage")}
              </Tag>
            )}
            {facts.throughAdoption && (
              <Tag bordered={false} className="!m-0">
                {t("via.adoption")}
              </Tag>
            )}
          </span>
        </Fact>
      )}

      {isPresent(result.ruleSetScope) && (
        <Fact label={t("facts.ruleSet")}>
          {t(`ruleScope.${result.ruleSetScope}`)}
          {result.cached && <span className="ml-1.5 text-[12px] text-text-muted">· {t("cached")}</span>}
        </Fact>
      )}
    </dl>
  );
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col">
      <dt className="text-[12px] uppercase tracking-wide text-text-muted">{label}</dt>
      <dd className="m-0 text-[15px] text-text-main">{children}</dd>
    </div>
  );
}
