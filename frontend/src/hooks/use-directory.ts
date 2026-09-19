"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { directoryApi, type DirectoryPage, type DirectoryQuery } from "@/lib/api/directory";
import { queryKeys } from "@/lib/query/keys";

/**
 * Danh bạ dòng họ.
 *
 * `keepPreviousData` cũng vì lý do như tìm kiếm: đổi bộ lọc trên điện thoại mà
 * danh sách sập về khung xương rồi mọc lại sẽ đẩy ngón tay người dùng ra khỏi
 * chỗ họ đang nhìn.
 *
 * Kết quả đã được máy chủ lọc theo **đồng thuận của từng chủ thể**. Không lọc
 * thêm ở đây, và tuyệt đối không "bù" thêm dòng nào từ `/persons/search`: hai
 * endpoint trả lời hai câu hỏi khác nhau, và trộn chúng lại chính là cách một
 * người chưa đồng ý lọt vào danh bạ.
 */
export function useDirectory(query: DirectoryQuery) {
  return useQuery<DirectoryPage>({
    queryKey: queryKeys.directory(JSON.stringify(query)),
    queryFn: () => directoryApi.list(query),
    placeholderData: keepPreviousData,
    retry: false,
  });
}
