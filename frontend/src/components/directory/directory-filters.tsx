"use client";

import { Button, Input, Select } from "antd";
import { ClearOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import type { DirectoryFacets } from "@/lib/api/directory";

export interface DirectoryFilterValues {
  q?: string;
  province?: string;
  occupation?: string;
  branchId?: string;
}

export interface DirectoryFiltersProps {
  value: DirectoryFilterValues;
  onChange: (next: DirectoryFilterValues) => void;
  /** Các giá trị CÓ THẬT trong danh bạ của người gọi, kèm số lượng. */
  facets: DirectoryFacets | undefined;
  loading?: boolean;
}

/**
 * Lọc theo **nơi ở · nghề · chi**.
 *
 * <h2>Vì sao là danh sách chọn lấy từ `facets` chứ không phải ô gõ tự do</h2>
 * Một ô gõ tự do trên một danh bạ thưa là cái bẫy hoàn hảo: người dùng gõ "Hà
 * Nội", không ra ai, và kết luận rằng phần mềm hỏng — trong khi sự thật là chưa
 * ai ở Hà Nội chọn hiện. Danh sách chọn lấy từ chính tập kết quả thì không có
 * ngõ cụt nào: mọi mục trong danh sách đều chắc chắn ra ít nhất một người, và
 * số đếm bên cạnh nói trước sẽ ra bao nhiêu.
 *
 * Ô tìm theo tên vẫn là ô gõ tự do, vì ở đó người dùng biết chính xác mình tìm
 * ai và "không tìm thấy" là một câu trả lời có nghĩa.
 *
 * <h2>Lọc ở máy chủ</h2>
 * Mọi giá trị ở đây đi thẳng vào `/api/v1/directory`. Lọc trong trình duyệt sẽ
 * có nghĩa là đã tải về những dòng người gọi không có quyền xem rồi mới giấu
 * đi — đúng phép đảo ngược mà phân tầng sinh ra để chống.
 */
export function DirectoryFilters({
  value,
  onChange,
  facets,
  loading = false,
}: DirectoryFiltersProps) {
  const t = useTranslations("directory");

  const hasAnyFilter = Boolean(
    value.province || value.occupation || value.branchId || (value.q ?? "").length > 0
  );

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <label className="block" htmlFor="directory-filter-name">
        <span className="mb-1 block text-than text-text-muted">{t("filters.name")}</span>
        <Input
          id="directory-filter-name"
          size="large"
          allowClear
          autoCorrect="off"
          placeholder={t("filters.namePlaceholder")}
          value={value.q ?? ""}
          onChange={(e) => onChange({ ...value, q: e.target.value || undefined })}
        />
      </label>

      <label className="block" htmlFor="directory-filter-province">
        <span className="mb-1 block text-than text-text-muted">{t("filters.province")}</span>
        <Select<string>
          id="directory-filter-province"
          className="w-full"
          size="large"
          allowClear
          showSearch
          optionFilterProp="label"
          loading={loading}
          placeholder={t("filters.anyProvince")}
          value={value.province}
          onChange={(next) => onChange({ ...value, province: next ?? undefined })}
          options={(facets?.provinces ?? []).map((facet) => ({
            value: facet.value,
            label: t("filters.facetOption", { value: facet.value, count: facet.count }),
          }))}
        />
      </label>

      <label className="block" htmlFor="directory-filter-occupation">
        <span className="mb-1 block text-than text-text-muted">
          {t("filters.occupation")}
        </span>
        <Select<string>
          id="directory-filter-occupation"
          className="w-full"
          size="large"
          allowClear
          showSearch
          optionFilterProp="label"
          loading={loading}
          placeholder={t("filters.anyOccupation")}
          value={value.occupation}
          onChange={(next) => onChange({ ...value, occupation: next ?? undefined })}
          options={(facets?.occupations ?? []).map((facet) => ({
            value: facet.value,
            label: t("filters.facetOption", { value: facet.value, count: facet.count }),
          }))}
        />
      </label>

      <label className="block" htmlFor="directory-filter-branch">
        <span className="mb-1 block text-than text-text-muted">{t("filters.branch")}</span>
        <Select<string>
          id="directory-filter-branch"
          className="w-full"
          size="large"
          allowClear
          showSearch
          optionFilterProp="label"
          loading={loading}
          placeholder={t("filters.anyBranch")}
          value={value.branchId}
          onChange={(next) => onChange({ ...value, branchId: next ?? undefined })}
          options={(facets?.branches ?? []).map((facet) => ({
            value: facet.id,
            label: t("filters.facetOption", { value: facet.name, count: facet.count }),
          }))}
        />
      </label>

      {hasAnyFilter && (
        <div className="lg:col-span-4">
          <Button
            type="link"
            className="!px-0"
            icon={<ClearOutlined />}
            onClick={() => onChange({})}
          >
            {t("filters.clear")}
          </Button>
        </div>
      )}
    </div>
  );
}
