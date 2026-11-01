"use client";

import { useQuery } from "@tanstack/react-query";
import { clanProfileApi, type ClanProfilePublicDto } from "@/lib/api/clan-profile";
import { queryKeys } from "@/lib/query/keys";

/**
 * Hồ sơ dòng họ, bản **công khai** — dùng để dựng TÊN THƯƠNG HIỆU ở những chỗ
 * hiện tên dòng họ (thanh đầu trang, tiêu đề trang chủ). Khách gọi được
 * (`GET /api/v1/public/clan-profile`), nên hook này an toàn để gọi ở MỌI nơi,
 * kể cả trước khi biết người xem là ai.
 *
 * <h2>Không được chặn NFR-1 (điều hướng → thẻ người đầu tiên &lt; 2000 ms)</h2>
 * `staleTime` dài (24 giờ — đổi tên dòng họ là chuyện hiếm, không phải chuyện
 * mỗi phiên) và `gcTime: Infinity` nghĩa là sau lượt tải đầu, mọi trang sau chỉ
 * đọc từ bộ nhớ đệm React Query, không gọi mạng lại. Ở lượt tải ĐẦU, nơi gọi
 * KHÔNG ĐƯỢC chờ `isPending` rồi mới vẽ — ghép hook này với chuỗi chung
 * ("Gia Phả Dòng Họ") làm giá trị hiện ngay, và chỉ thay bằng tên thật khi
 * `data` về. Không có khung xương, không có `Suspense` chặn cây render:
 * xem cách dùng ở `home-hero.tsx` và `header.tsx`.
 *
 * <h2>`initialized: false` là một câu trả lời đầy đủ, không phải dữ liệu thiếu</h2>
 * Cổng chưa qua bước 1 của `/quan-tri` thì mọi trường khác đều vắng — nơi đọc
 * PHẢI kiểm `data?.initialized` trước khi đọc `name`/`shortName`, không suy
 * diễn từ sự có/không của bản thân trường đó.
 */
export function useClanProfile(options: { enabled?: boolean } = {}) {
  return useQuery<ClanProfilePublicDto>({
    queryKey: queryKeys.clanProfilePublic(),
    queryFn: () => clanProfileApi.getPublic(),
    staleTime: 24 * 60 * 60 * 1000,
    gcTime: Infinity,
    // Khách vãng lai load rất nhiều trang mà không đăng nhập — một biến động
    // mạng thoáng qua không đáng để thử lại và trễ thêm nhịp nào ở đây; chuỗi
    // chung đã đứng sẵn làm giá trị an toàn.
    retry: 1,
    enabled: options.enabled ?? true,
  });
}

/**
 * Tên đầy đủ để hiện — tên dòng họ nếu đã khởi tạo, ngược lại `fallback`
 * (chuỗi thương hiệu chung của sản phẩm, do nơi gọi truyền vào từ bản dịch
 * đang dùng — hook này không tự quyết định ngôn ngữ).
 */
export function clanDisplayName(
  profile: ClanProfilePublicDto | undefined,
  fallback: string
): string {
  return profile?.initialized && profile.name ? profile.name : fallback;
}

/** Tên rút gọn — `shortName`, hoặc `name` nếu chưa khai tên rút gọn, hoặc `fallback`. */
export function clanDisplayShortName(
  profile: ClanProfilePublicDto | undefined,
  fallback: string
): string {
  if (!profile?.initialized) return fallback;
  return profile.shortName?.trim() || profile.name || fallback;
}
