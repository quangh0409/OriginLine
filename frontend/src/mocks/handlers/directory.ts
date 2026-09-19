import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockGraph } from "@/mocks/tree-graph/build-graph";
import { resolvePersonMock } from "@/mocks/person-detail";
import { ALL_PERSONS } from "@/mocks/data";
import { unaccent } from "@/mocks/unaccent";
import { identityOf } from "@/mocks/identity";
import { consentAllows, privacyOf, viewerClassFor } from "@/mocks/privacy-settings";
import type {
  DirectoryBranchFacet,
  DirectoryEntryDto,
  DirectoryFacetValue,
  DirectoryPage,
} from "@/lib/api/directory";
import { resolveMockRole } from "./role";
import type { PersonDto, Problem } from "@/types/api";

/**
 * MSW cho `GET /api/v1/directory` — danh bạ dòng họ.
 *
 * <h2>Danh bạ là bề mặt ĐỒNG THUẬN, không phải một phép lọc khác của tìm kiếm</h2>
 * Một người chỉ có mặt ở đây khi CHÍNH HỌ đã mở nghề nghiệp hoặc nơi ở cấp
 * tỉnh cho hạng người xem mà người gọi thuộc về. Không suy từ vai, không suy từ
 * "có dữ liệu thì hiện". Vì vậy danh bạ thưa là **kết quả đúng**, và toàn bộ
 * phần `coverage` tồn tại để nói ra điều đó bằng con số thay vì để người dùng
 * tự kết luận rằng hệ thống hỏng.
 *
 * <h2>Khách: 401, không phải danh sách rỗng</h2>
 * Xem doc ở `src/lib/api/directory.ts`. Trả về danh sách rỗng cho khách sẽ là
 * một lời nói dối mang hình dạng dữ liệu thật.
 *
 * <h2>Lọc ở máy chủ, luôn luôn</h2>
 * `province` / `occupation` / `branchId` được áp ở đây, trước phân trang, chứ
 * không gửi cả danh sách về rồi lọc trong trình duyệt. Lọc ở trình duyệt nghĩa
 * là đã tải về những dòng người gọi không có quyền xem rồi mới giấu đi — đúng
 * phép đảo ngược mà phân tầng sinh ra để chống (kiến trúc thông tin §4).
 */

function problem(status: number, code: Problem["code"], title: string) {
  const body: Problem = {
    type: "about:blank",
    title,
    status,
    code,
    instance: "/api/v1/directory",
  };
  return HttpResponse.json(body, { status });
}

/**
 * Mọi người còn sống mà bộ giả lập biết tới, không trùng lặp theo id.
 *
 * Dựng MỘT LẦN rồi giữ lại, theo đúng kiểu `getMockGraph()` bên cạnh. Không có
 * bộ nhớ đệm này thì mỗi lời gọi `/directory` phải tổng hợp đầy đủ `PersonDto`
 * cho khoảng bốn nghìn người trong đồ thị sinh tự động — đo được là hàng trăm
 * mili-giây cho một yêu cầu, và một màn hình có gõ-trễ + đồng bộ URL thì bắn
 * vài yêu cầu liền nhau. Đó là lý do một ca kiểm thành phần chạy 13 giây khi
 * đứng một mình và VƯỢT hạn 20 giây khi chạy cùng cả bộ.
 *
 * Giữ được vì danh sách NGƯỜI là tĩnh; thứ thay đổi theo phiên là **mức chia
 * sẻ**, và mức ấy được đọc lại ở mỗi yêu cầu qua `privacyOf`, nằm ngoài
 * bộ nhớ đệm này. Trộn hai thứ vào một chỗ đệm sẽ làm màn hình không thấy
 * người dùng vừa đổi mức của chính mình.
 */
let livingCache: PersonDto[] | null = null;

function livingPersons(): PersonDto[] {
  if (livingCache) return livingCache;

  const out: PersonDto[] = [];
  const seen = new Set<string>();

  for (const raw of getMockGraph().personsById.values()) {
    if (!raw.isAlive) continue;
    const person = resolvePersonMock(raw.id);
    if (!person) continue;
    seen.add(raw.id);
    out.push(person);
  }
  // p-102 / p-103 cố ý KHÔNG được ghép vào đồ thị (xem src/mocks/data.ts), nên
  // vòng lặp trên bỏ sót đúng hai hồ sơ ứng với người đang đăng nhập — tức là
  // đúng hai hồ sơ mà người dùng sẽ tìm mình trong danh bạ.
  for (const person of ALL_PERSONS) {
    if (person.isAlive && !seen.has(person.id)) out.push(person);
  }
  livingCache = out;
  return out;
}

function bump(counter: Map<string, number>, key: string | null | undefined) {
  if (!key) return;
  counter.set(key, (counter.get(key) ?? 0) + 1);
}

function toFacet(counter: Map<string, number>): DirectoryFacetValue[] {
  return [...counter.entries()]
    .map(([value, count]) => ({ value, count }))
    .sort((a, b) => b.count - a.count || a.value.localeCompare(b.value, "vi"));
}

export const directoryHandlers = [
  http.get(`${API_BASE_URL}/api/v1/directory`, ({ request }) => {
    const role = resolveMockRole(request);
    const identity = identityOf(role);

    if (identity.appUserId === null) {
      return problem(401, "UNAUTHENTICATED", "Cần đăng nhập để xem danh bạ dòng họ");
    }

    const url = new URL(request.url);
    const q = unaccent((url.searchParams.get("q") ?? "").trim());
    const province = url.searchParams.get("province") ?? undefined;
    const occupation = url.searchParams.get("occupation") ?? undefined;
    const branchId = url.searchParams.get("branchId") ?? undefined;
    const page = Number(url.searchParams.get("page") ?? "0");
    const size = Number(url.searchParams.get("size") ?? "20");
    const sort = url.searchParams.get("sort") ?? "name";

    const living = livingPersons();

    const provinceCounter = new Map<string, number>();
    const occupationCounter = new Map<string, number>();
    const branchCounter = new Map<string, DirectoryBranchFacet>();

    const shared: DirectoryEntryDto[] = [];

    for (const person of living) {
      const viewer = viewerClassFor(person, identity);
      const settings = privacyOf(person.id);

      const showsOccupation = consentAllows(settings.occupation, viewer);
      const showsProvince = consentAllows(settings.residenceProvince, viewer);
      // Điều kiện lọt vào danh bạ: đã mở ÍT NHẤT một trong hai nhóm làm nên một
      // dòng danh bạ. Mở mỗi số điện thoại mà giấu cả nghề lẫn nơi ở thì dòng ấy
      // không nói được gì, và hiện nó lên chỉ làm danh sách dài mà không hữu ích.
      if (!showsOccupation && !showsProvince) continue;

      const entry: DirectoryEntryDto = {
        personId: person.id,
        displayName:
          person.displayName ?? person.names.find((n) => n.isPrimary)?.fullName ?? "(?)",
        generation: person.generation ?? null,
        primaryBranch: person.primaryBranch ?? null,
        occupation: showsOccupation ? person.occupation ?? null : undefined,
        currentPlaceProvince: showsProvince ? person.currentPlaceProvince ?? null : undefined,
      };
      shared.push(entry);

      bump(provinceCounter, entry.currentPlaceProvince);
      bump(occupationCounter, entry.occupation);
      const branch = person.primaryBranch;
      if (branch) {
        const existing = branchCounter.get(branch.id);
        if (existing) existing.count += 1;
        else {
          branchCounter.set(branch.id, {
            id: branch.id,
            name: branch.name,
            path: branch.path,
            count: 1,
          });
        }
      }
    }

    const filtered = shared.filter((entry) => {
      if (province && entry.currentPlaceProvince !== province) return false;
      if (occupation && entry.occupation !== occupation) return false;
      if (branchId && entry.primaryBranch?.id !== branchId) return false;
      if (q.length > 0 && !unaccent(entry.displayName).includes(q)) return false;
      return true;
    });

    filtered.sort((a, b) =>
      sort === "generation"
        ? (a.generation ?? 0) - (b.generation ?? 0) ||
          a.displayName.localeCompare(b.displayName, "vi")
        : a.displayName.localeCompare(b.displayName, "vi")
    );

    const start = page * size;
    const items = filtered.slice(start, start + size);

    const body: DirectoryPage = {
      items,
      page: {
        page,
        size,
        totalElements: filtered.length,
        totalPages: Math.max(1, Math.ceil(filtered.length / size)),
        hasNext: start + size < filtered.length,
        sort,
      },
      coverage: {
        // Tử số đếm trên tập CHƯA lọc: câu "218 / 627 đã điền" nói về dòng họ,
        // không về bộ lọc đang bật. Gắn nó vào bộ lọc thì mỗi lần chọn một tỉnh
        // con số lại tụt, và người đọc sẽ hiểu thành "càng lọc càng ít người
        // chịu điền".
        sharedCount: shared.length,
        livingCount: living.length,
      },
      facets: {
        provinces: toFacet(provinceCounter),
        occupations: toFacet(occupationCounter),
        branches: [...branchCounter.values()].sort((a, b) => a.path.localeCompare(b.path)),
      },
    };

    return HttpResponse.json(body);
  }),
];
