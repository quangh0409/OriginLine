"use client";

import { useState } from "react";
import { Input, Spin } from "antd";
import { SearchOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { useRootCandidates } from "@/hooks/use-root-candidates";
import { PUBLIC_SEARCH_MIN_QUERY_LENGTH } from "@/lib/api/public-portal";
import type { TreeAudience } from "@/lib/api/tree";
import { nodeDatesState } from "@/lib/tree/life-dates";

export interface TreeJumpSearchProps {
  audience: TreeAudience | null;
  /** Người dùng đã chọn một cái tên. Canvas có nhiệm vụ mở đường tới họ và đưa máy quay về đó. */
  onJump: (personId: string) => void;
}

/**
 * **Nhảy tới một người theo tên, ngay trên canvas.**
 *
 * <h2>Vì sao nó là việc chặn, chứ không phải tiện ích</h2>
 * Luồng đăng ký mới có một bước "tự nhận mình": người vừa có tài khoản phải tìm **chính họ** trong
 * 1.500 người, và bước ấy chạy trên đúng màn hình này. Không có ô tìm ở đây thì cách duy nhất là
 * cuộn một bức tranh vài nghìn pixel — tức bước ấy không dùng được.
 *
 * <h2>Hai endpoint, một hình dạng</h2>
 * Thành viên hỏi `/persons/search`; khách hỏi `/public/persons/search`, nơi `isAlive = false` bị
 * ghim cứng trong câu SQL của máy chủ. Phép rẽ nhánh ấy nằm trọn trong `useRootCandidates` — dùng
 * lại nguyên vẹn chứ không viết đường thứ hai, vì một bản sao là một chỗ để hai bề mặt lệch nhau
 * về quyền riêng tư.
 *
 * <h2>Vì sao là ô nhập + danh sách nút, không phải `<AutoComplete>` của Ant Design</h2>
 * Ba lý do, theo thứ tự quan trọng: (1) mỗi kết quả là một `<button>` thật nên nó **chắc chắn**
 * đạt vùng chạm 44px và Tab tới được — với `<AutoComplete>` thì kích thước mục do token của thư
 * viện quyết, và nó đổi sau lưng mình; (2) danh sách vẽ **tại chỗ** chứ không qua cổng nổi, nên
 * nó không thành thêm một lớp phủ nuốt thao tác chạm trên canvas — lỗi ấy đã xảy ra ba lần ở màn
 * này (Controls, MiniMap, chú giải); (3) `<AutoComplete>` điền **giá trị** của mục vào ô nhập,
 * mà giá trị ở đây là id nhân khẩu — ô tìm sẽ biến thành một dãy UUID ngay sau cú chọn đầu tiên.
 * Đây cũng đúng khuôn mẫu `<TreeRootPicker>` đang dùng.
 *
 * <h2>Không đếm, không hứa</h2>
 * Không in "tìm thấy N người". Với khách thì con số ấy là một phép đếm dân số dòng họ (chính vì
 * thế `PublicPersonSummaryPage` không có `totalElements`); với thành viên thì nó nói được điều gì
 * đó về số người đang bị lọc. Danh sách hiện ra đúng những ai hiện ra được, hết.
 */
export function TreeJumpSearch({ audience, onJump }: TreeJumpSearchProps) {
  const t = useTranslations("tree.jump");
  const [term, setTerm] = useState("");
  const [dismissed, setDismissed] = useState(false);
  const { data, isFetching, error } = useRootCandidates(term, audience);

  const trimmed = term.trim();
  const tooShort = trimmed.length > 0 && trimmed.length < PUBLIC_SEARCH_MIN_QUERY_LENGTH;
  const searched = trimmed.length >= PUBLIC_SEARCH_MIN_QUERY_LENGTH;
  const items = data?.items ?? [];
  const open = searched && !dismissed;

  const detailOf = (person: (typeof items)[number]): string => {
    const dates = nodeDatesState(person);
    return [
      person.generation != null ? t("generation", { n: person.generation }) : null,
      person.primaryBranch?.name ?? null,
      dates.kind === "known" ? dates.text : null,
    ]
      .filter(Boolean)
      .join(" · ");
  };

  return (
    <div className="relative w-full">
      <label className="block" htmlFor="tree-jump-search-input">
        <span className="sr-only">{t("label")}</span>
        <Input
          id="tree-jump-search-input"
          data-testid="tree-jump-search"
          allowClear
          // Biểu tượng kính lúp ĐI KÈM chữ, không thay chữ (định hướng 00 §2.3): nhãn đầy đủ nằm
          // ở `aria-label`, và chỗ giữ chỗ nói rõ gõ không dấu cũng được.
          prefix={<SearchOutlined aria-hidden />}
          suffix={isFetching ? <Spin size="small" /> : undefined}
          aria-label={t("label")}
          placeholder={t("placeholder")}
          value={term}
          onChange={(event) => {
            setTerm(event.target.value);
            setDismissed(false);
          }}
          // Escape đóng danh sách mà KHÔNG xoá chữ vừa gõ: người dùng bàn phím cần một lối thoát
          // khỏi lớp gợi ý để đi tiếp bằng Tab, và xoá luôn chữ thì họ phải gõ lại từ đầu.
          onKeyDown={(event) => {
            if (event.key === "Escape") setDismissed(true);
          }}
          className="!min-h-11 !text-than"
        />
      </label>

      {tooShort && (
        <p className="m-0 mt-1 text-than text-text-muted">
          {t("tooShort", { min: PUBLIC_SEARCH_MIN_QUERY_LENGTH })}
        </p>
      )}

      {open && (
        // `absolute` + `z-20`: danh sách phủ lên canvas chứ không đẩy thanh công cụ cao thêm —
        // trên Pixel 5 thanh này đã ngốn 217px trong 523px, và mỗi pixel lấy đi là một pixel phả đồ.
        <div
          data-testid="tree-jump-results"
          className="absolute left-0 right-0 top-full z-20 mt-1 max-h-72 overflow-y-auto rounded-lg border border-border bg-bg-card shadow-lg"
        >
          {error ? (
            <p className="m-0 px-3 py-3 text-than text-text-muted">{t("searchError")}</p>
          ) : items.length === 0 ? (
            // Chỉ nói "không có ai" khi ĐÃ hỏi xong. Câu ấy nhấp nháy giữa từng phím gõ thì đọc
            // ra như một lời nói dối.
            !isFetching && (
              <p className="m-0 px-3 py-3 text-than text-text-muted">{t("noMatch")}</p>
            )
          ) : (
            <ul className="m-0 list-none p-0">
              {items.map((person) => (
                <li key={person.id}>
                  <button
                    type="button"
                    data-testid="tree-jump-result"
                    data-person-id={person.id}
                    onClick={() => {
                      onJump(person.id);
                      // Giữ nguyên CÁI TÊN trong ô — không phải id. Đó là chỗ duy nhất trên màn
                      // hình nói "bạn đang xem vì bạn vừa tìm người này".
                      setTerm(person.displayName);
                      setDismissed(true);
                    }}
                    className="flex min-h-11 w-full flex-col justify-center px-3 py-2 text-left hover:bg-primary-light focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"
                  >
                    <span className="font-serif text-than font-semibold text-text-main">
                      {person.displayName}
                    </span>
                    {detailOf(person) !== "" && (
                      <span className="text-than text-text-muted">{detailOf(person)}</span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
