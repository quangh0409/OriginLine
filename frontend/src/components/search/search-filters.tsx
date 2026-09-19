"use client";

import { Button, Input, Select } from "antd";
import { ClearOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { branchDepth, useBranches } from "@/hooks/use-branches";

export interface SearchFilterValues {
  generation?: number;
  branchId?: string;
  nativePlace?: string;
}

export interface SearchFiltersProps {
  value: SearchFilterValues;
  onChange: (next: SearchFilterValues) => void;
}

/**
 * UI affordance only. The API accepts any `generation >= 1`; this cap just
 * bounds the dropdown so it stays tappable on a phone. Raise it the day a
 * clan books a 21st generation — nothing server-side depends on it.
 */
const MAX_GENERATION_OPTION = 20;

/**
 * Đời / chi-ngành / quê quán filters (plan §4 F6).
 *
 * Every filter is passed straight through to `/persons/search`; none of them
 * is applied client-side. That matters for privacy as much as for
 * correctness — filtering locally would mean fetching rows the caller is not
 * entitled to and hiding them in the browser, which is the exact inversion of
 * how the tiering is supposed to work (BA v2 §10).
 */
export function SearchFilters({ value, onChange }: SearchFiltersProps) {
  const t = useTranslations("search");
  const { data: branches, isLoading: branchesLoading } = useBranches();

  const hasAnyFilter =
    value.generation !== undefined ||
    value.branchId !== undefined ||
    (value.nativePlace ?? "").length > 0;

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <label className="block" htmlFor="search-filter-generation">
        <span className="mb-1 block text-than text-text-muted">{t("filters.generation")}</span>
        <Select<number>
          id="search-filter-generation"
          className="w-full"
          size="large"
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder={t("filters.anyGeneration")}
          value={value.generation}
          onChange={(next) => onChange({ ...value, generation: next ?? undefined })}
          options={Array.from({ length: MAX_GENERATION_OPTION }, (_, i) => ({
            value: i + 1,
            label: t("filters.generationOption", { n: i + 1 }),
          }))}
        />
      </label>

      <label className="block" htmlFor="search-filter-branch">
        <span className="mb-1 block text-than text-text-muted">{t("filters.branch")}</span>
        <Select<string>
          id="search-filter-branch"
          className="w-full"
          size="large"
          allowClear
          showSearch
          optionFilterProp="label"
          loading={branchesLoading}
          placeholder={t("filters.anyBranch")}
          value={value.branchId}
          onChange={(next) => onChange({ ...value, branchId: next ?? undefined })}
          options={(branches ?? []).map((branch) => ({
            value: branch.id,
            label: branch.name,
            // Indent by ltree depth so ngành/nhánh read as children of a chi.
            title: branch.path,
            depth: branchDepth(branch),
          }))}
          optionRender={(option) => (
            <span
              style={{ paddingLeft: `${(option.data as { depth: number }).depth * 12}px` }}
            >
              {option.label}
            </span>
          )}
        />
      </label>

      <label className="block" htmlFor="search-filter-native-place">
        <span className="mb-1 block text-than text-text-muted">
          {t("filters.nativePlace")}
        </span>
        <Input
          id="search-filter-native-place"
          size="large"
          allowClear
          autoCorrect="off"
          placeholder={t("filters.nativePlacePlaceholder")}
          value={value.nativePlace ?? ""}
          onChange={(e) =>
            onChange({ ...value, nativePlace: e.target.value || undefined })
          }
        />
      </label>

      {hasAnyFilter && (
        <div className="sm:col-span-3">
          <Button
            type="link"
            size="small"
            icon={<ClearOutlined />}
            className="!px-0"
            onClick={() => onChange({})}
          >
            {t("filters.clear")}
          </Button>
        </div>
      )}
    </div>
  );
}
