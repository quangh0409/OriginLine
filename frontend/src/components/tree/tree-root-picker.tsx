"use client";

import { useState } from "react";
import { Alert, Button, Empty, Input, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { useRootCandidates } from "@/hooks/use-root-candidates";
import { PUBLIC_SEARCH_MIN_QUERY_LENGTH } from "@/lib/api/public-portal";
import type { TreeAudience } from "@/lib/api/tree";
import { nodeDatesState } from "@/lib/tree/life-dates";
import { colorVars } from "@/styles/tokens";

export interface TreeRootPickerProps {
  audience: TreeAudience | null;
  onSelect: (rootId: string) => void;
  /**
   * `true` khi người dùng ĐÃ mang tới một gốc nhưng máy chủ từ chối nó — một
   * liên kết chia sẻ đã cũ, hoặc một id sai định dạng. Chỉ đổi câu dẫn, không
   * đổi cách hoạt động.
   */
  afterRejectedRoot?: boolean;
  /**
   * "Mở phả đồ mặc định của dòng họ" — bỏ `?rootId=` và để máy chủ chọn gốc.
   *
   * Đây là lối ra khiến màn hình này **không còn là ngõ cụt**: người dùng tới
   * đây vì một liên kết hỏng, hoặc vì bấm nhầm, và trong cả hai ca họ không nợ
   * hệ thống một cái tên nào trước khi được xem cây.
   */
  onUseDefault?: () => void;
}

/**
 * "Phả đồ bắt đầu từ ai?" — bước **đổi gốc chủ động**.
 *
 * <h2>Nó đã thôi là bước bắt buộc</h2>
 * Màn hình này từng chắn giữa người dùng và lần xem cây đầu tiên, vì `rootId`
 * là tham số bắt buộc và máy chủ chưa có khái niệm gốc mặc định. Nay `rootId`
 * là tuỳ chọn và máy chủ chọn gốc **theo vai + phạm vi chi** của người gọi
 * (xem `src/lib/tree/root-id.ts`), nên mở `/tree` trần là ra cây ngay. Cái còn
 * lại ở đây là việc nó vẫn làm tốt nhất: cho người dùng **nhảy sang một nhánh
 * khác**, và cho họ một lối đi tiếp khi một liên kết chia sẻ không mở được.
 *
 * <h2>Với khách, ô tìm kiếm này không rò rỉ gì</h2>
 * Nó gọi `/public/persons/search`, nơi `isAlive = false` bị ghim cứng trong câu
 * SQL của máy chủ. Vì thế câu phụ đề nói thẳng "chỉ tìm được người đã khuất" —
 * đó là một tính chất của cổng công khai, không phải một lời than về quyền.
 * Không đếm, không nói có bao nhiêu người không hiện ra.
 */
export function TreeRootPicker({
  audience,
  onSelect,
  afterRejectedRoot = false,
  onUseDefault,
}: TreeRootPickerProps) {
  const t = useTranslations("tree.rootPicker");
  const [term, setTerm] = useState("");
  const { data, isFetching, error } = useRootCandidates(term, audience);

  const tooShort = term.trim().length > 0 && term.trim().length < PUBLIC_SEARCH_MIN_QUERY_LENGTH;
  const searched = term.trim().length >= PUBLIC_SEARCH_MIN_QUERY_LENGTH;

  return (
    <section
      data-tree-state="root-picker"
      data-testid="tree-root-picker"
      aria-labelledby="tree-root-picker-title"
      className="mx-auto w-full max-w-xl rounded-lg border px-4 py-6 sm:px-6"
      style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
    >
      <h2
        id="tree-root-picker-title"
        className="m-0 font-serif text-de font-semibold text-text-main"
      >
        {afterRejectedRoot ? t("titleAfterRejected") : t("title")}
      </h2>
      <p className="m-0 mt-2 text-than leading-relaxed text-text-muted">
        {audience === "public" ? t("bodyPublic") : t("bodyMember")}
      </p>

      {/* Ngay dưới câu dẫn, TRƯỚC ô tìm kiếm: người tới đây sau một liên kết hỏng
          cần thấy lối ra trước khi bị hỏi một câu họ không trả lời được. */}
      {onUseDefault && (
        <Button className="mt-3 !min-h-11" onClick={onUseDefault}>
          {t("useDefault")}
        </Button>
      )}

      <label className="mt-4 block" htmlFor="tree-root-picker-input">
        <span className="mb-1 block text-than font-medium text-text-main">{t("label")}</span>
        <Input.Search
          id="tree-root-picker-input"
          allowClear
          size="large"
          placeholder={t("placeholder")}
          onChange={(event) => setTerm(event.target.value)}
          loading={isFetching}
        />
      </label>

      {tooShort && (
        <p className="m-0 mt-2 text-than text-text-muted">
          {t("tooShort", { min: PUBLIC_SEARCH_MIN_QUERY_LENGTH })}
        </p>
      )}

      {error && (
        <Alert className="mt-3" type="error" showIcon message={t("searchError")} />
      )}

      {searched && !error && (
        <div className="mt-3">
          {isFetching && !data ? (
            <Skeleton active paragraph={{ rows: 3 }} title={false} />
          ) : data && data.items.length > 0 ? (
            <ul className="m-0 list-none space-y-2 p-0">
              {data.items.map((person) => {
                const dates = nodeDatesState(person);
                return (
                  <li key={person.id}>
                    <button
                      type="button"
                      onClick={() => onSelect(person.id)}
                      className="w-full rounded-lg border px-3 py-2 text-left transition-colors hover:border-primary"
                      style={{
                        borderColor: colorVars.border,
                        background: colorVars.bgCard,
                      }}
                    >
                      <span className="block font-serif text-de font-bold text-text-main">
                        {person.displayName}
                      </span>
                      <span className="block text-than text-text-muted">
                        {[
                          person.generation != null
                            ? t("generation", { n: person.generation })
                            : null,
                          dates.kind === "known" ? dates.text : null,
                          person.primaryBranch?.name ?? null,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={t("noMatch")}
            />
          )}
        </div>
      )}
    </section>
  );
}
