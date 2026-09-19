"use client";

import { Divider } from "antd";
import { useTranslations } from "next-intl";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import { KinshipFactsList } from "./kinship-facts-list";
import { KinshipPathDiagram } from "./kinship-path-diagram";
import { KinshipStatusNotice } from "./kinship-status-notice";
import type { KinshipResult, PersonSummaryDto } from "@/types/api";

export interface KinshipResultCardProps {
  result: KinshipResult;
  fromPerson?: PersonSummaryDto;
  toPerson?: PersonSummaryDto;
}

/**
 * The answer to "tôi gọi người này là gì".
 *
 * Three things, in the order a Vietnamese speaker actually needs them:
 *  1. **Gọi là** — `title`, how `from` addresses `to`.
 *  2. **Xưng là** — `reciprocalTitle`. In Vietnamese address you must supply
 *     both halves of the pair; knowing to say "bác" is useless without knowing
 *     to call yourself "cháu". The reciprocal doubles as the reverse-direction
 *     answer, which is why it is labelled both ways below.
 *  3. **Đường quan hệ** — the LCA path, as verifiable evidence.
 *
 * Titles are business data, always Vietnamese, and are printed exactly as the
 * rule engine returned them even in the English UI (contracts/README §3).
 * `titleEn` is offered only as a rough gloss beneath, never as a replacement.
 */
export function KinshipResultCard({ result, fromPerson, toPerson }: KinshipResultCardProps) {
  const t = useTranslations("kinship");

  if (result.status !== "RESOLVED") {
    return (
      <div className="space-y-3">
        <KinshipStatusNotice status={result.status} />
        {/* Facts survive a NO_MATCHING_RULE and are exactly what the council
            needs in order to write the missing rule — keep them on screen. */}
        {result.status === "NO_MATCHING_RULE" && (
          <div className="rounded-lg border border-border bg-bg-card px-4 py-3">
            <KinshipFactsList facts={result.facts} result={result} />
          </div>
        )}
        {result.status === "NO_MATCHING_RULE" && result.path && (
          <div className="rounded-lg border border-border bg-bg-card px-4 py-3">
            <h3 className="mb-3 mt-0 font-serif text-base text-primary">{t("pathTitle")}</h3>
            <KinshipPathDiagram path={result.path} lca={result.lca} />
          </div>
        )}
      </div>
    );
  }

  const fromName = fromPerson?.displayName;
  const toName = toPerson?.displayName;

  return (
    <div className="space-y-3">
      <section
        className="rounded-lg border px-4 py-5 text-center sm:px-6"
        style={{
          background: colorVars.primaryLight,
          borderColor: colorVars.borderDark,
          borderTop: `3px solid ${colorVars.primary}`,
        }}
      >
        {isPresent(fromName) && isPresent(toName) && (
          <p className="m-0 text-than text-text-muted">
            {t("sentence", { from: fromName, to: toName })}
          </p>
        )}

        <p className="m-0 mt-1 font-serif text-3xl font-bold leading-tight text-primary sm:text-4xl">
          {result.title}
        </p>

        {isPresent(result.reciprocalTitle) && (
          <p className="m-0 mt-2 text-than text-text-main">
            {t("selfAddress")}{" "}
            <strong className="font-serif text-lg text-primary-dark">
              {result.reciprocalTitle}
            </strong>
          </p>
        )}

        {isPresent(result.titleEn) && (
          <p className="m-0 mt-2 text-than italic text-text-muted">{result.titleEn}</p>
        )}
      </section>

      {isPresent(result.reciprocalTitle) && isPresent(fromName) && isPresent(toName) && (
        <section className="rounded-lg border border-border bg-bg-card px-4 py-3">
          <h3 className="mb-1 mt-0 font-serif text-base text-primary">{t("reverseTitle")}</h3>
          <p className="m-0 text-than text-text-main">
            {t("reverseSentence", {
              to: toName,
              from: fromName,
              title: result.reciprocalTitle,
            })}
          </p>
        </section>
      )}

      <section className="rounded-lg border border-border bg-bg-card px-4 py-3">
        <h3 className="mb-1 mt-0 font-serif text-base text-primary">{t("pathTitle")}</h3>
        {result.lca && (
          <p className="mb-3 mt-0 text-than leading-relaxed text-text-muted">
            {t("pathSummary", {
              lca: result.lca.displayName ?? "",
              up: result.lca.distanceFrom,
              down: result.lca.distanceTo,
            })}
          </p>
        )}
        <KinshipPathDiagram path={result.path} lca={result.lca} />
        <Divider className="!my-3" />
        <KinshipFactsList facts={result.facts} result={result} />
      </section>
    </div>
  );
}
