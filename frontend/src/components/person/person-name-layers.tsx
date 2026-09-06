"use client";

import { Tag } from "antd";
import { useTranslations } from "next-intl";
import { sortNameLayers } from "@/lib/format/name-layers";
import { isPresent } from "@/lib/privacy/present";
import { PersonSection } from "./person-section";
import type { PersonDto } from "@/types/api";

export interface PersonNameLayersProps {
  person: PersonDto;
}

/**
 * The multi-layer Vietnamese naming system, rendered as layers rather than
 * collapsed into one line: tên húy · tên tự · tên hiệu · tên thụy · tên
 * thường gọi · pháp danh, each with its optional Hán-Nôm.
 *
 * Note this list SHRINKS with the caller's tier, and that is correct: at Tier
 * 1 the backend returns only the primary layer, because tên húy and tên thụy
 * are ritually sensitive. The heading only appears when at least one layer
 * came back, so nobody can count the missing rows.
 */
export function PersonNameLayers({ person }: PersonNameLayersProps) {
  const t = useTranslations("person");
  const layers = sortNameLayers(person.names).filter((n) => isPresent(n.fullName));

  return (
    <PersonSection title={t("names")} hasContent={layers.length > 0}>
      <ul className="m-0 list-none divide-y divide-border p-0">
        {layers.map((layer, index) => (
          <li
            key={`${layer.nameType}-${layer.id ?? index}`}
            className="flex flex-col gap-0.5 py-2 sm:flex-row sm:items-baseline sm:gap-4"
          >
            <span className="flex shrink-0 items-center gap-2 text-[13px] text-text-muted sm:w-40">
              {t(`nameType.${layer.nameType}`)}
              {layer.isPrimary && (
                <Tag bordered={false} className="!m-0 !px-1.5 !text-[10px]">
                  {t("primaryName")}
                </Tag>
              )}
            </span>
            <span className="min-w-0">
              <span className="text-[15px] text-text-main">{layer.fullName}</span>
              {isPresent(layer.nameHanNom) && (
                <span className="ml-2 font-serif text-[15px] text-text-muted" lang="zh-Hant">
                  {layer.nameHanNom}
                </span>
              )}
              {isPresent(layer.note) && (
                <span className="mt-0.5 block text-[12px] text-text-muted">{layer.note}</span>
              )}
            </span>
          </li>
        ))}
      </ul>
    </PersonSection>
  );
}
