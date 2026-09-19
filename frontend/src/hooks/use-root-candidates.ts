"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { personsApi } from "@/lib/api/persons";
import {
  PUBLIC_SEARCH_MIN_QUERY_LENGTH,
  publicPortalApi,
  toPersonSummary,
} from "@/lib/api/public-portal";
import type { TreeAudience } from "@/lib/api/tree";
import type { PersonSummaryDto } from "@/types/api";

/** Đủ ngắn để không ai phải cuộn khi chọn gốc, đủ dài để thấy có nhiều lựa chọn. */
const CANDIDATE_PAGE_SIZE = 10;

export interface RootCandidates {
  items: PersonSummaryDto[];
  /** Còn kết quả nữa — với bản công khai, `false` cũng có thể là "chạm trần trang". */
  hasNext: boolean;
}

/**
 * Danh sách người có thể chọn làm **gốc phả đồ**.
 *
 * <h2>Hai endpoint, cùng một hình dạng trả về</h2>
 * Khách hỏi `/public/persons/search` (chỉ người đã khuất, không cần phiên);
 * thành viên hỏi `/persons/search` trong phạm vi của họ. Chúng được đưa về cùng
 * `PersonSummaryDto[]` ở đây chứ không phải ở component, để màn hình chọn gốc
 * không phải biết mình đang phục vụ ai.
 *
 * <h2>Vì sao không dùng luôn `usePersonSearch`</h2>
 * `usePersonSearch` phục vụ màn tìm kiếm và chỉ gọi bản thành viên — với khách
 * nó trả `401`. Đổi hành vi của nó ở đây sẽ kéo theo cả màn tìm kiếm, một thay
 * đổi rộng hơn hẳn việc đang sửa. (Chính nó cũng nên chuyển sang bản công khai
 * cho khách — đã ghi lại trong báo cáo, sửa riêng.)
 *
 * Truy vấn ngắn hơn `PUBLIC_SEARCH_MIN_QUERY_LENGTH` **không** được gửi đi: máy
 * chủ công khai trả `400` cho một ký tự, và một dải đỏ khi người dùng mới gõ chữ
 * đầu tiên là cách nhanh nhất khiến họ nghĩ hệ thống hỏng.
 */
export function useRootCandidates(query: string, audience: TreeAudience | null) {
  const term = query.trim();
  const enabled = audience !== null && term.length >= PUBLIC_SEARCH_MIN_QUERY_LENGTH;

  return useQuery<RootCandidates>({
    queryKey: ["tree-root-candidates", audience ?? "pending", term],
    queryFn: async () => {
      if (audience === "public") {
        const page = await publicPortalApi.searchPersons({ q: term });
        return { items: page.items.map(toPersonSummary), hasNext: page.page.hasNext };
      }
      const page = await personsApi.search({ q: term, size: CANDIDATE_PAGE_SIZE });
      return { items: page.items, hasNext: page.page.hasNext };
    },
    enabled,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    retry: false,
  });
}
