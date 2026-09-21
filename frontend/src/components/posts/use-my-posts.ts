"use client";

import { useState } from "react";
import { useMe } from "@/hooks/use-me";
import type { PostDto } from "@/lib/api/posts";
import { usePostsList } from "./queries";

const PAGE_SIZE = 20;

export interface MyPostsResult {
  isPending: boolean;
  isError: boolean;
  drafts: PostDto[];
  others: PostDto[];
  page: number;
  setPage: (page: number) => void;
  hasNext: boolean;
  totalPages: number;
}

/**
 * "Bài của tôi / nháp của tôi" — checklist §2, khoảng trống thứ hai: hôm nay
 * một bản nháp đang viết dở chỉ mở lại được nếu người dùng còn giữ đường dẫn.
 *
 * <h2>Một lượt gọi, lọc trong SQL — KHÔNG còn bốn lượt gọi song song</h2>
 * Bản đầu của hook này gọi `GET /posts?status=X` bốn lần (một cho mỗi trạng
 * thái) rồi lọc lại theo `authorPersonId` ở client, vì hợp đồng lúc đó không
 * có cách lọc theo tác giả ở tầng máy chủ. Backend nay có
 * `GET /posts?mine=true`, lọc **trong SQL** và ghép được với phân trang —
 * đúng nghĩa đen sửa lỗi mà cách cũ mắc: cắt trang RỒI MỚI lọc ở client làm
 * một người viết nhiều mất bài cũ ở trang sau mà không có gì báo (backend đã
 * viết một bài kiểm ghim đúng ca này). `mine=true` không có vấn đề đó vì việc
 * lọc xảy ra TRƯỚC khi cắt trang, ngay trong SQL.
 *
 * <h2>Vì sao vẫn nhóm "nháp"/"đã gửi" ở CLIENT</h2>
 * Đây chỉ là cách trình bày (hai khối trên một trang), không phải một điều
 * kiện truy cập — dữ liệu của TRANG HIỆN TẠI đã đúng và đủ nhờ `mine=true`,
 * nên nhóm lại trong bộ nhớ không có rủi ro "mất dữ liệu ở trang khác" như
 * cách cũ có.
 */
export function useMyPosts(): MyPostsResult {
  const me = useMe();
  const [page, setPage] = useState(0);

  const query = usePostsList({ mine: true, page, size: PAGE_SIZE });

  if (!me.data?.personId) {
    return {
      isPending: me.isPending,
      isError: me.isError,
      drafts: [],
      others: [],
      page,
      setPage,
      hasNext: false,
      totalPages: 0,
    };
  }

  const items = query.data?.items ?? [];
  return {
    isPending: query.isPending,
    isError: query.isError,
    drafts: items.filter((p) => p.status === "DRAFT"),
    others: items.filter((p) => p.status !== "DRAFT"),
    page,
    setPage,
    hasNext: query.data?.page.hasNext ?? false,
    totalPages: query.data?.page.totalPages ?? 0,
  };
}
