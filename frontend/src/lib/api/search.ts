import { personsApi, type SearchPersonsParams } from "./persons";
import { publicPortalApi, toPersonSummary } from "./public-portal";
import type { PersonSummaryDto } from "@/types/api";

export type SearchAudience = "public" | "member";

export interface PersonSearchInput {
  q: string;
  generation?: number;
  branchId?: string;
  nativePlace?: string;
  isAlive?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Trang kết quả đã được QUY VỀ MỘT HÌNH DẠNG, bất kể lối tìm nào phục vụ nó.
 *
 * `totalElements: null` khi và chỉ khi lối công khai đã trả lời — đó là cố ý,
 * không phải một trường quên điền: bề mặt công khai không trả tổng số (một
 * tổng số chính là phép đếm dân số dòng họ, xem `PublicPageDto`), nên
 * `<SearchResultList>` phải phân biệt được "chưa biết tổng" với "tổng bằng 0".
 */
export interface PersonSearchResultPage {
  items: PersonSummaryDto[];
  page: {
    size: number;
    totalElements: number | null;
    hasNext: boolean;
  };
}

/**
 * Chọn đúng lối tìm theo audience — **lỗi thật** mà Đợt 2 sửa (F6).
 *
 * <h2>Lỗi là gì</h2>
 * Trước bản này, `PersonSearchScreen` gọi thẳng `personsApi.search` cho MỌI
 * người gọi, kể cả Khách. `/api/v1/persons/search` đòi phiên đăng nhập, nên
 * Khách bấm Tìm kiếm nhận `401` — đúng lúc trang chủ/màn đăng nhập đang hứa
 * điều ngược lại ("xem được phả đồ mà chưa cần tài khoản"). Máy chủ **đã có**
 * lối công khai chạy tốt, có thử nghiệm đầy đủ
 * (`/api/v1/public/persons/search` — `PublicSearchController`, gõ có dấu hay
 * không dấu đều ra); lỗi chỉ nằm ở tầng này chưa từng gọi tới nó.
 *
 * <h2>Đây là lần THỨ BA lặp lại đúng một mẫu hình</h2>
 * `src/hooks/use-root-candidates.ts` (chọn gốc phả đồ) và
 * `src/components/tree/tree-jump-search.tsx` (nhảy tới một người trên canvas)
 * đã rẽ nhánh public/member đúng kiểu này từ trước — javadoc của
 * `useRootCandidates` còn ghi rõ: "Chính [màn tìm kiếm] cũng nên chuyển sang
 * bản công khai cho khách — đã ghi lại trong báo cáo, sửa riêng." Đây là bản
 * sửa riêng ấy. Phép rẽ nhánh và cách quy `PublicPersonSummaryDto` về
 * `PersonSummaryDto` (qua `toPersonSummary`) lấy nguyên từ đó, không viết lại
 * một đường khác.
 *
 * <h2>Khách bị bớt tham số, không phải bị từ chối trong im lặng</h2>
 * `branchId`, `nativePlace`, `isAlive`, `sort` không tồn tại trên
 * `/public/persons/search` (chỉ `q`, `generation`, `page`, và `size` cố định
 * phía máy chủ). Với audience `"public"`, hàm này CỐ Ý không gửi ba tham số
 * đầu — gửi lên rồi để máy chủ lặng lẽ bỏ qua sẽ tạo ra đúng loại "hỏng thầm
 * lặng" mà cả dự án đang tránh. `<SearchFilters>` ẩn hẳn hai ô lọc không có
 * tác dụng ấy khi audience là `"public"`, thay vì hiện ra rồi không làm gì.
 */
export const searchApi = {
  persons: (audience: SearchAudience, input: PersonSearchInput): Promise<PersonSearchResultPage> => {
    if (audience === "public") {
      return publicPortalApi
        .searchPersons({ q: input.q, generation: input.generation, page: input.page })
        .then((page) => ({
          items: page.items.map(toPersonSummary),
          page: { size: page.page.size, totalElements: null, hasNext: page.page.hasNext },
        }));
    }

    const memberParams: SearchPersonsParams = {
      q: input.q,
      generation: input.generation,
      branchId: input.branchId,
      nativePlace: input.nativePlace,
      isAlive: input.isAlive,
      page: input.page,
      size: input.size,
      sort: input.sort,
    };
    return personsApi.search(memberParams).then((page) => ({
      items: page.items,
      page: {
        size: page.page.size,
        totalElements: page.page.totalElements,
        hasNext: page.page.hasNext,
      },
    }));
  },
};
