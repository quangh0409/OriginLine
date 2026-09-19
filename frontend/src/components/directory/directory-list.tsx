"use client";

import { Pagination, Skeleton } from "antd";
import { useTranslations } from "next-intl";
import { DirectoryEntryCard } from "./directory-entry-card";
import { DirectoryEmptyState } from "./directory-empty-state";
import type { DirectoryPage } from "@/lib/api/directory";

export interface DirectoryListProps {
  data: DirectoryPage | undefined;
  isLoading: boolean;
  isFetching: boolean;
  /** Người dùng có đang bật bộ lọc nào không — quyết định câu chữ khi rỗng. */
  filtered: boolean;
  page: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onClearFilters: () => void;
  selfPersonId?: string | null;
}

/**
 * Danh sách kết quả.
 *
 * Ba trạng thái không được gộp: đang tải · rỗng · có kết quả. Và trạng thái
 * rỗng còn chia đôi nữa (xem `directory-empty-state.tsx`), vì "chưa ai chọn
 * hiện" và "không ai khớp bộ lọc" dẫn tới hai hành động khác nhau của người
 * dùng.
 *
 * Cố ý KHÔNG có trạng thái "chờ gõ" như màn tìm kiếm: danh bạ là thứ người ta
 * mở ra để *duyệt*, nên nó phải có nội dung ngay từ giây đầu. Bắt gõ một chữ
 * mới cho xem là biến một danh bạ thành một ô tìm kiếm thứ hai.
 */
export function DirectoryList({
  data,
  isLoading,
  isFetching,
  filtered,
  page,
  pageSize,
  onPageChange,
  onClearFilters,
  selfPersonId,
}: DirectoryListProps) {
  const t = useTranslations("directory");

  if (isLoading) return <Skeleton active paragraph={{ rows: 6 }} />;

  const items = data?.items ?? [];
  const total = data?.page.totalElements ?? 0;

  if (items.length === 0) {
    return (
      <DirectoryEmptyState
        reason={filtered ? "filtered-out" : "no-one-shared"}
        onClearFilters={onClearFilters}
        selfPersonId={selfPersonId}
      />
    );
  }

  return (
    <div className="space-y-3" aria-busy={isFetching}>
      <p className="m-0 text-than text-text-muted" role="status" aria-live="polite">
        {t("resultCount", { count: total })}
      </p>

      <ul className="m-0 grid list-none grid-cols-1 gap-3 p-0">
        {items.map((entry) => (
          <DirectoryEntryCard key={entry.personId} entry={entry} />
        ))}
      </ul>

      {total > pageSize && (
        <div className="flex justify-center pt-2">
          {/*
            `simple` chứ không phải dải số trang đầy đủ, vì hai lý do độc lập
            và cả hai đều quan trọng:

            1. Trên điện thoại — thiết bị chính của cổng này — một dải mười ô
               số nhỏ xíu là mười vùng chạm dưới ngưỡng 44px nằm sát nhau.
            2. Dải đầy đủ vẽ nút nhảy trang bằng ký tự "•••", và "•••" là
               ĐÚNG mẫu mà bộ kiểm rò rỉ của sản phẩm truy tìm (nó là hình
               dạng kinh điển của một ô "có dữ liệu bạn không được xem"). Ở đây
               nó vô hại về nghĩa nhưng vẫn dạy mắt người dùng đúng cái dấu
               hiệu mà mọi màn hình khác cố tình không bao giờ dùng.
          */}
          <Pagination
            simple
            current={page + 1}
            pageSize={pageSize}
            total={total}
            onChange={(next) => onPageChange(next - 1)}
          />
        </div>
      )}
    </div>
  );
}
