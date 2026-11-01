"use client";

import { useEffect, useMemo, useState } from "react";
import { Select, Spin } from "antd";
import { useTranslations } from "next-intl";
import { useMe } from "@/hooks/use-me";
import { useBranches } from "@/hooks/use-branches";
import { usePersonSearch } from "@/hooks/use-person-search";
import { isPresent } from "@/lib/privacy/present";
import { colorVars } from "@/styles/tokens";
import type { PersonSummaryDto } from "@/types/api";
import { phamViCap, tenDuongNhanh, trongPhamVi } from "./scope";

const DEBOUNCE_MS = 250;
/** Lấy rộng hơn 20 của ô chọn chung: một phần kết quả sẽ bị lọc bỏ vì ngoài phạm vi. */
const SO_KET_QUA = 50;

export interface ScopedPersonPickerProps {
  value: string | undefined;
  onChange: (personId: string | undefined, person?: PersonSummaryDto) => void;
  label: string;
  placeholder: string;
  id: string;
  seed?: PersonSummaryDto | undefined;
  status?: "error" | "warning";
}

/**
 * Ô chọn **một người còn sống trong phạm vi mình được giao** — dùng cho hai việc
 * chỉ làm được trong phạm vi ấy: phát lời mời cá nhân và cấp lại liên kết đặt
 * mật khẩu.
 *
 * <h2>Vì sao không dùng thẳng `<PersonPicker>`</h2>
 * `<PersonPicker>` là ô tra CẢ HỌ và chỉ in tên lá của chi/ngành. Hai điều ấy
 * đúng cho biểu mẫu quan hệ, sai ở đây. Đo trên hệ thật 24/09 với
 * `truongchi` (Chi Bính): gõ "Nguyễn Thế Tài" ra ba dòng, hai dòng in y hệt
 * "Đời thứ 7 · Ngành Trưởng" — một ở Chi Giáp — và năm sinh bị giấu theo
 * quyền riêng tư nên không còn gì để phân biệt. Trưởng chi chọn dòng đầu,
 * máy chủ trả `403 BRANCH_SCOPE_VIOLATION`. Với một người 60 tuổi duyệt trên
 * điện thoại, đó là một ngõ cụt chứ không phải một câu báo lỗi.
 *
 * Nên ở đây: (1) chỉ tra người còn sống — người đã khuất không có tài khoản
 * để mời hay cấp lại, máy chủ trả 422; (2) bỏ người ngoài phạm vi khỏi danh
 * sách, và NÓI RA là đã bỏ bao nhiêu người, để "sao không thấy cụ Tài bên Chi
 * Giáp" có câu trả lời ngay trên màn; (3) in cả đường nhánh (`Chi Bính ·
 * Ngành Thứ`) thay vì chỉ tên lá, để Hội đồng — phạm vi cả họ, không lọc gì —
 * cũng phân biệt được bốn "Ngành Trưởng".
 *
 * Đây không phải phép kiểm quyền: máy chủ vẫn kiểm `ltree` và vẫn có thể từ
 * chối; màn gọi vẫn phải dịch `BRANCH_SCOPE_VIOLATION` ra câu người đọc được.
 */
export function ScopedPersonPicker({
  value,
  onChange,
  label,
  placeholder,
  id,
  seed,
  status,
}: ScopedPersonPickerProps) {
  const t = useTranslations("membership");
  const tPerson = useTranslations("person");
  const { data: me } = useMe();
  const { data: branches } = useBranches();

  const [rawQuery, setRawQuery] = useState("");
  const [query, setQuery] = useState("");
  useEffect(() => {
    const timer = setTimeout(() => setQuery(rawQuery), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [rawQuery]);

  const { data, isFetching } = usePersonSearch("member", {
    q: query,
    isAlive: true,
    size: SO_KET_QUA,
  });

  // Giữ tham chiếu ổn định giữa các lượt vẽ: `phamViCap` trả mảng mới mỗi lần gọi.
  const phamVi = useMemo(() => (me ? phamViCap(me) : []), [me]);
  const tenTheoPath = useMemo(
    () => new Map((branches ?? []).map((b) => [b.path, b.name] as const)),
    [branches]
  );

  const { hopLe, soBiAn } = useMemo(() => {
    const items = data?.items ?? [];
    // Máy chủ đã lọc `isAlive=true`; lọc lại ở đây là để một bản máy chủ cũ bỏ
    // qua tham số ấy cũng không đưa được người đã khuất lên danh sách.
    const song = items.filter((p) => p.isAlive !== false);
    const trong = song.filter((p) => trongPhamVi(p.primaryBranch?.path, phamVi));
    return { hopLe: trong, soBiAn: song.length - trong.length };
  }, [data?.items, phamVi]);

  /** Một dòng in lại đầy đủ người vừa chọn — ô Select chỉ còn giữ cái tên sau khi đóng. */
  const moTa = (person: PersonSummaryDto): string =>
    [
      isPresent(person.generation) ? tPerson("generationValue", { n: person.generation }) : null,
      tenDuongNhanh(person.primaryBranch?.path, tenTheoPath) ?? person.primaryBranch?.name,
      isPresent(person.birthYear) ? String(person.birthYear) : null,
    ]
      .filter(Boolean)
      .join(" · ");

  const options = useMemo(() => {
    const items = [...hopLe];
    if (seed && !items.some((p) => p.id === seed.id)) items.unshift(seed);
    return items.map((person) => ({ value: person.id, label: person.displayName, person }));
  }, [hopLe, seed]);

  const daChon = value ? options.find((o) => o.value === value)?.person : undefined;

  return (
    <div>
      <label className="block" htmlFor={id}>
        <span className="mb-1 block text-than font-medium text-text-main">{label}</span>
        <Select
          id={id}
          showSearch
          allowClear
          size="large"
          className="w-full"
          status={status}
          value={value}
          placeholder={placeholder}
          filterOption={false}
          onSearch={setRawQuery}
          onChange={(next) => {
            const picked = options.find((o) => o.value === next)?.person;
            onChange(next ?? undefined, picked);
          }}
          notFoundContent={
            isFetching ? <Spin size="small" /> : query.trim() ? t("scopedPicker.empty") : null
          }
          options={options}
          optionRender={(option) => {
            const person = option.data.person as PersonSummaryDto;
            return (
              <div className="py-0.5">
                <div className="flex items-center gap-1.5">
                  <span
                    aria-hidden
                    className="h-1.5 w-1.5 shrink-0 rounded-full"
                    style={{ background: colorVars.success }}
                  />
                  <span className="truncate text-text-main">{person.displayName}</span>
                </div>
                <div className="truncate pl-3 text-than text-text-muted">{moTa(person)}</div>
              </div>
            );
          }}
        />
      </label>
      {daChon && (
        <p className="m-0 mt-1 text-than text-text-main" data-testid="nguoi-da-chon">
          {t("scopedPicker.chosen", { name: daChon.displayName, detail: moTa(daChon) })}
        </p>
      )}
      {soBiAn > 0 && query.trim().length > 0 && (
        <p className="m-0 mt-1 text-than text-text-muted" data-testid="an-ngoai-pham-vi">
          {t("scopedPicker.hiddenOutside", { n: soBiAn })}
        </p>
      )}
    </div>
  );
}
