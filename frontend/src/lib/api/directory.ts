import { apiFetch } from "./http";
import type { BranchRef, PageMeta } from "@/types/api";

/**
 * Lớp API cho **danh bạ dòng họ** — danh sách người còn sống đã tự chọn cho cả
 * họ xem, lọc theo nơi ở / nghề / chi.
 *
 * <h2>Trạng thái hợp đồng: đã có thật ở cả hai phía</h2>
 * Tệp này khởi đầu là một hợp đồng do giao diện đề xuất (cùng quy ước với
 * `privacy-settings.ts` bên cạnh), và trong một thời gian **không có controller
 * nào** phục vụ `/api/v1/directory` — mọi vai đã đăng nhập đều nhận `404`, còn
 * màn hình thì dịch `404` ấy thành "Chưa tải được danh bạ. Vui lòng thử lại.".
 * Nay endpoint đã được dựng và `contracts/openapi.yaml` đã có mục `/directory`;
 * hình dạng dưới đây khớp với phản hồi thật (`items` · `page` · `coverage` ·
 * `facets`).
 *
 * `<DirectoryUnavailableNotice>` vẫn giữ nhánh `404`/`501`: một bản triển khai
 * cũ hơn vẫn có thể chưa có endpoint này, và khi đó câu đúng là "máy chủ chưa
 * mở phần này", không phải "thử lại".
 *
 * <h2>Vì sao là một endpoint riêng chứ không phải `/persons/search` có thêm bộ lọc</h2>
 * Ba lý do, và cả ba đều là lý do nghiệp vụ chứ không phải tiện tay:
 * <ol>
 *   <li><b>Điều kiện lọt vào danh sách khác hẳn.</b> Tìm kiếm trả về mọi người
 *       mà người gọi được biết tới (gồm cả người đã khuất, và người còn sống ở
 *       Tầng 1 chưa mở gì). Danh bạ chỉ trả về người còn sống **đã tự mở** ít
 *       nhất một nhóm trường cho người gọi. Nhét hai ngữ nghĩa vào một endpoint
 *       là cách chắc chắn nhất để một ngày nào đó tìm kiếm rò ra một người chưa
 *       hề đồng ý.</li>
 *   <li><b>Danh bạ cần `coverage`</b> — tỉ lệ "đã điền / tổng người còn sống".
 *       Đó không phải siêu dữ liệu phân trang; nó là thứ giải thích vì sao danh
 *       sách thưa, và nó phải do máy chủ đếm trên phạm vi của người gọi.</li>
 *   <li><b>Danh bạ cần `facets`</b> — các tỉnh/nghề/chi CÓ THẬT trong tập kết
 *       quả. Thả một ô nhập tự do rồi để người dùng gõ "Hà Nội" cho ra không kết
 *       quả là cách nhanh nhất khiến họ kết luận hệ thống hỏng.</li>
 * </ol>
 *
 * <h2>Khách: `401`, không phải danh sách rỗng</h2>
 * Người còn sống không tồn tại đối với khách chưa đăng nhập. Trả về một danh
 * sách rỗng kèm `coverage` bằng 0 sẽ là một lời nói dối có hình dạng của dữ
 * liệu thật — và tệ hơn, nó bảo người dùng rằng dòng họ không có ai còn sống.
 * `401 UNAUTHENTICATED` để giao diện nói đúng một câu: cần đăng nhập.
 * Khác với `GET /persons/{id}`: ở đó `404` là bắt buộc vì câu hỏi là về MỘT
 * người cụ thể và bản thân sự tồn tại của người ấy là bí mật. Ở đây câu hỏi
 * không nhắc tới ai cả, nên không có gì để giấu ngoài chính dữ liệu.
 */

export interface DirectoryEntryDto {
  personId: string;
  displayName: string;
  generation?: number | null;
  primaryBranch?: BranchRef | null;
  /**
   * Chỉ có mặt khi chủ thể đã mở nhóm tương ứng **cho đúng người gọi này**.
   * Vắng mặt là câu trả lời cuối cùng — không có chỗ nào trong giao diện được
   * vẽ ô trống thay cho nó (xem `src/lib/privacy/present.ts`).
   */
  occupation?: string | null;
  currentPlaceProvince?: string | null;
  avatarUrl?: string | null;
}

/**
 * Tử số / mẫu số của câu "218 / 627 người còn sống đã điền".
 *
 * Cả hai đều tính **trong phạm vi của người gọi**, nên hai người ở hai chi có
 * thể thấy hai cặp số khác nhau và cả hai đều đúng. Mẫu số không phải điều bí
 * mật với thành viên: Tầng 1 vốn đã cho họ thấy tên và đời của mọi người còn
 * sống, nên số lượng là thứ họ đếm được bằng tay. Với khách thì không có câu
 * hỏi nào ở đây cả — khách nhận `401`.
 */
export interface DirectoryCoverage {
  /** Số người còn sống hiện ra trong danh bạ của người gọi (bỏ qua bộ lọc). */
  sharedCount: number;
  /** Tổng số người còn sống trong phạm vi người gọi. */
  livingCount: number;
}

export interface DirectoryFacetValue {
  value: string;
  count: number;
}

export interface DirectoryBranchFacet {
  id: string;
  name: string;
  /** ltree, dùng để xếp thứ tự/thụt đầu dòng — không bao giờ hiển thị. */
  path: string;
  count: number;
}

export interface DirectoryFacets {
  provinces: DirectoryFacetValue[];
  occupations: DirectoryFacetValue[];
  branches: DirectoryBranchFacet[];
}

export interface DirectoryPage {
  items: DirectoryEntryDto[];
  page: PageMeta;
  coverage: DirectoryCoverage;
  /** Tính trên tập KHÔNG lọc, nên chọn một tỉnh xong vẫn còn thấy các tỉnh khác. */
  facets: DirectoryFacets;
}

export interface DirectoryQuery {
  /** Tìm theo tên, không dấu cũng khớp (Postgres FTS + `unaccent`). */
  q?: string;
  province?: string;
  occupation?: string;
  branchId?: string;
  page?: number; // zero-based
  size?: number;
  /** `name` (mặc định) | `generation` — cùng từ vựng sắp xếp với `/persons/search`. */
  sort?: string;
}

export const directoryApi = {
  list: (query: DirectoryQuery = {}) =>
    apiFetch<DirectoryPage>("/api/v1/directory", {
      query: {
        page: query.page ?? 0,
        size: query.size ?? 20,
        q: query.q || undefined,
        province: query.province || undefined,
        occupation: query.occupation || undefined,
        branchId: query.branchId || undefined,
        sort: query.sort || undefined,
      },
    }),
};
