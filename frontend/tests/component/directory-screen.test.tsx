import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { resetPrivacySettingsStore } from "@/mocks/privacy-settings";

let searchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
  usePathname: () => "/danh-ba",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/danh-ba",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/danh-ba",
}));

const { DirectoryScreen } = await import("@/components/directory/directory-screen");

/**
 * DANH BẠ DÒNG HỌ.
 *
 * Hai điều màn hình này phải nói thẳng ra, và chúng là lý do tồn tại của phần
 * lớn ca kiểm dưới đây:
 *
 *  1. **Danh bạ chỉ hiện người đã tự chọn cho cả họ xem** — nên nó thưa, và
 *     tỉ lệ "x / y" phải in ra để người dùng hiểu vì sao.
 *  2. **Rỗng hoặc thưa không được trông như lỗi.** Không dải đỏ, không chữ
 *     "không tải được", không ổ khoá.
 *
 * Cộng thêm ranh giới pháp lý: khách chưa đăng nhập không được nhận một mảnh
 * dữ liệu nào của người còn sống, kể cả một con số tổng.
 */

function renderDirectory(
  query = "",
  role: "guest" | "member" | "branch-head" | "admin" = "member",
  locale: "vi" | "en" = "vi"
) {
  searchParams = new URLSearchParams(query);
  resetRouterMock();
  return renderWithProviders(<DirectoryScreen />, { role, locale });
}

beforeEach(() => {
  resetPrivacySettingsStore();
});

/**
 * Chờ danh sách vẽ xong, rồi trả về các liên kết tra danh xưng.
 *
 * Cố ý dùng `querySelectorAll` chứ không `findAllByRole("link", { name })`:
 * phép tìm theo VAI + TÊN bắt testing-library tính lại tên khả truy cập cho
 * mọi liên kết trong cây, ở mỗi lần thử lại — đo được gần 20 giây trên màn
 * này, tức sát hạn 20 giây của Vitest, và đó đúng là một ca đỏ nhấp nháy đang
 * chờ xảy ra. Nhãn khả truy cập vẫn được khẳng định, chỉ là đọc thẳng ra khỏi
 * thuộc tính thay vì bắt thư viện suy lại.
 */
async function kinshipLinks(container: HTMLElement): Promise<HTMLAnchorElement[]> {
  await waitFor(
    () =>
      expect(
        container.querySelectorAll('a[href^="/kinship"]').length
      ).toBeGreaterThan(0),
    { timeout: 10000 }
  );
  return [...container.querySelectorAll<HTMLAnchorElement>('a[href^="/kinship"]')];
}

describe("khách chưa đăng nhập", () => {
  it("nhận đúng một câu mời đăng nhập, không phải một danh sách rỗng", async () => {
    const { container } = renderDirectory("", "guest");

    expect(
      await screen.findByRole("heading", { name: "Danh bạ dành cho thành viên đã đăng nhập" })
    ).toBeInTheDocument();
    expect(container.querySelector('[data-directory-state="guest"]')).not.toBeNull();
    // Không phải trạng thái rỗng: hai chuyện khác hẳn nhau và dẫn tới hai hành
    // động khác nhau.
    expect(container.querySelector("[data-directory-empty]")).toBeNull();
  });

  it("không thấy tên, nghề, tỉnh hay bất kỳ con số nào", async () => {
    const { container } = renderDirectory("", "guest");
    await screen.findByRole("heading", { name: "Danh bạ dành cho thành viên đã đăng nhập" });

    const text = container.textContent ?? "";
    expect(text).not.toMatch(/Nguyễn|Trần|Lê Thị/);
    expect(text).not.toMatch(/Kỹ sư|Giáo viên|Lương y/);
    expect(text).not.toMatch(/Nam Định|Hà Nội|Thái Bình/);
    // Kể cả tổng số người còn sống: đó là quy mô dòng họ, không phải thứ người
    // ngoài được biết.
    expect(container.querySelector("[data-directory-coverage]")).toBeNull();
    expect(text).not.toMatch(/\d/);
  });

  it("không dùng ổ khoá — dấu hiệu ấy dạy người đọc cách suy ra dữ liệu bị giữ", async () => {
    const { container } = renderDirectory("", "guest");
    await screen.findByRole("heading", { name: "Danh bạ dành cho thành viên đã đăng nhập" });

    expect(container.querySelector('[data-icon="lock"]')).toBeNull();
    expect(container.querySelector('[data-icon="eye-invisible"]')).toBeNull();
  });

  it("nói danh bạ LÀ GÌ, rồi dẫn tới chỗ đăng nhập và tới phả đồ công khai", async () => {
    // Ba việc một câu từ chối phải làm: giải thích thứ bị từ chối, giải thích
    // vì sao, và để lại một việc làm được. Câu cũ ("Chưa tải được danh bạ.
    // Vui lòng thử lại.") không làm được việc nào.
    const { container } = renderDirectory("", "guest");
    await screen.findByRole("heading", { name: "Danh bạ dành cho thành viên đã đăng nhập" });

    const text = container.textContent ?? "";
    expect(text).toMatch(/nghề nghiệp và nơi ở/);
    expect(text).not.toMatch(/Chưa tải được danh bạ|Vui lòng thử lại/);
    expect(screen.getByRole("button", { name: /Đăng nhập/ })).toBeInTheDocument();
    expect(container.querySelector('a[href="/tree"]')).not.toBeNull();
  });
});

/**
 * MÁY CHỦ CHƯA CÓ DANH BẠ.
 *
 * `GET /api/v1/directory` là hợp đồng do giao diện đề xuất; phía backend chưa
 * có controller nào phục vụ đường dẫn ấy, nên **mọi vai đã đăng nhập** đều nhận
 * `404`. Trạng thái này khác hẳn `401` của khách và khác hẳn một sự cố mạng, và
 * chỉ có một câu nói đúng được nó.
 */
describe("máy chủ chưa phục vụ danh bạ", () => {
  function serveNotFound() {
    server.use(
      http.get(`${API_BASE_URL}/api/v1/directory`, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Không tìm thấy",
            status: 404,
            code: "NOT_FOUND",
            instance: "/api/v1/directory",
          },
          { status: 404 }
        )
      )
    );
  }

  it("nói thẳng là máy chủ chưa mở phần này, KHÔNG mời thử lại", async () => {
    serveNotFound();
    const { container } = renderDirectory("", "member");

    expect(
      await screen.findByRole("heading", { name: "Máy chủ chưa mở danh bạ" })
    ).toBeInTheDocument();
    expect(container.querySelector('[data-directory-state="unavailable"]')).not.toBeNull();

    const text = container.textContent ?? "";
    // Câu cũ — "Chưa tải được danh bạ. Vui lòng thử lại." — mời người dùng làm
    // một việc không bao giờ đổi kết quả.
    expect(text).not.toMatch(/Chưa tải được danh bạ/);
    expect(text).not.toMatch(/Vui lòng thử lại/);
  });

  it("chỉ ra người bật được nó, và một việc làm được ngay bây giờ", async () => {
    serveNotFound();
    const { container } = renderDirectory("", "member");
    await screen.findByRole("heading", { name: "Máy chủ chưa mở danh bạ" });

    expect(container.textContent).toMatch(/Hội đồng Tộc biểu|quản trị hệ thống/);
    expect(container.querySelector('a[href="/tree"]')).not.toBeNull();
  });

  it("không đổ lỗi cho quyền hạn — đó là hướng dẫn sai người đi xin quyền", async () => {
    serveNotFound();
    const { container } = renderDirectory("", "branch-head");
    await screen.findByRole("heading", { name: "Máy chủ chưa mở danh bạ" });

    const text = container.textContent ?? "";
    expect(text).not.toMatch(/không có quyền|bị từ chối|403/i);
    expect(container.querySelector('[data-directory-state="guest"]')).toBeNull();
  });

  it("một sự cố THẬT (5xx) thì vẫn là 'tải lỗi, thử lại' — hai ca không bị gộp", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api/v1/directory`, () => HttpResponse.json({}, { status: 503 }))
    );
    const { container } = renderDirectory("", "member");

    await waitFor(
      () => expect(container.textContent).toMatch(/Chưa tải được danh bạ/),
      { timeout: 8000 }
    );
    expect(container.querySelector('[data-directory-state="unavailable"]')).toBeNull();
  });
});

describe("thành viên đã đăng nhập", () => {
  it("thấy tỉ lệ bao phủ, và tỉ lệ ấy giải thích vì sao danh bạ thưa", async () => {
    const { container } = renderDirectory("", "member");

    await waitFor(
      () => expect(container.querySelector("[data-directory-coverage]")).not.toBeNull(),
      { timeout: 8000 }
    );

    const note = container.querySelector("[data-directory-coverage]") as HTMLElement;
    // Dạng "x / y", không phải một con số trần.
    expect(note.textContent).toMatch(/\d+\s*\/\s*\d+/);
    expect(note.textContent).toContain("người còn sống đã điền và chọn hiện ở đây");
    expect(note.textContent).toContain("không phải thiếu dữ liệu");
  });

  it("mỗi dòng có nút tra danh xưng trỏ bằng ?to=, không phải ?from=", async () => {
    const { container } = renderDirectory("", "member");

    const links = await kinshipLinks(container);
    expect(links.length).toBeGreaterThan(0);
    for (const link of links) {
      const href = link.getAttribute("href") ?? "";
      expect(href).toMatch(/^\/kinship\?to=/);
      expect(href).not.toContain("from=");
    }
  });

  it("nút tra danh xưng có CHỮ bên cạnh biểu tượng, và nhãn khả truy cập nêu TÊN", async () => {
    const { container } = renderDirectory("", "member");
    const links = await kinshipLinks(container);

    // Chữ nhìn thấy: ngắn, vì nó lặp lại trên mọi dòng.
    expect(links[0]!.textContent).toContain("Tôi gọi là?");
    // Nhãn khả truy cập: đầy đủ, vì người dùng trình đọc màn hình duyệt theo
    // danh sách liên kết và chỉ nghe thấy cái nhãn.
    expect(links[0]!.getAttribute("aria-label")).toMatch(/^Tôi gọi .+ là gì\?$/);
  });

  it("không dòng nào dựng ô trống thay cho một trường chủ thể chưa mở", async () => {
    const { container } = renderDirectory("", "member");
    await kinshipLinks(container);

    const text = container.textContent ?? "";
    expect(text).not.toMatch(/•\s*•\s*•/);
    expect(text).not.toMatch(/\bbị ẩn\b/i);
    expect(text).not.toMatch(/\bkhông có quyền\b/i);
    expect(text).not.toContain("MISSING_MESSAGE");
    // Không có dấu phân cách lủng lẳng kiểu "Đời thứ 5 · · Hà Nội".
    expect(text).not.toMatch(/·\s*·/);
  });
});

describe("trạng thái rỗng không trông như lỗi", () => {
  it("lọc ra không ai thì nói rõ là do bộ lọc, kèm lối ra", async () => {
    // Một tỉnh chắc chắn không có ai trong dữ liệu giả.
    renderDirectory("province=Cà%20Mau", "member");

    expect(
      await screen.findByRole("heading", { name: "Không có ai khớp bộ lọc này" }, { timeout: 8000 })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Bỏ bộ lọc và xem lại danh bạ" })
    ).toBeInTheDocument();
    // Không dải lỗi, không màu báo hỏng.
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByText(/Chưa tải được danh bạ/)).toBeNull();
  });

  it("câu chữ khi rỗng vì bộ lọc KHÁC câu khi chưa ai chọn hiện", async () => {
    const { container, unmount } = renderDirectory("province=Cà%20Mau", "member");
    await screen.findByRole("heading", { name: "Không có ai khớp bộ lọc này" }, { timeout: 8000 });
    expect(container.querySelector('[data-directory-empty="filtered-out"]')).not.toBeNull();
    expect(container.querySelector('[data-directory-empty="no-one-shared"]')).toBeNull();
    unmount();
  });
});

describe("bộ lọc", () => {
  it("nơi ở và nghề là DANH SÁCH CHỌN, không phải ô gõ tự do", async () => {
    // Vì sao điều này đáng một ca kiểm riêng: gõ "Hà Nội" vào một ô tự do mà
    // không ra ai là cách nhanh nhất khiến người dùng kết luận phần mềm hỏng,
    // trong khi sự thật chỉ là chưa ai ở đó chọn hiện. Danh sách chọn lấy từ
    // `facets` thì không có mục nào dẫn tới ngõ cụt — phần số đếm kèm theo
    // được ghim ở tầng hợp đồng (tests/unit/directory/directory-api.test.ts).
    //
    // Ô tìm theo TÊN thì ngược lại, vẫn phải là ô gõ tự do: ở đó người dùng
    // biết chính xác mình tìm ai, và "không tìm thấy" là câu trả lời có nghĩa.
    const { container } = renderDirectory("", "member");
    await waitFor(
      () => expect(container.querySelector("[data-directory-coverage]")).not.toBeNull(),
      { timeout: 8000 }
    );

    for (const id of ["#directory-filter-province", "#directory-filter-occupation"]) {
      const box = container.querySelector(id)!;
      expect(box, `${id} phải tồn tại`).not.toBeNull();
      expect(box.getAttribute("role")).toBe("combobox");
    }
    expect(
      container.querySelector("#directory-filter-name")!.getAttribute("role")
    ).not.toBe("combobox");
  });

  it("giữ bộ lọc trong URL để chia sẻ được, và URL không mang tên ai", async () => {
    renderDirectory("province=Nam%20Định", "member");
    await waitFor(() => expect(routerMock.replace).toHaveBeenCalled(), { timeout: 8000 });

    const url = routerMock.replace.mock.calls.at(-1)![0] as string;
    expect(url).toContain("province=");
    expect(url).not.toMatch(/Nguyễn|Trần/);
  });
});

describe("song ngữ", () => {
  it("đọc được bản tiếng Anh mà không lộ khoá i18n", async () => {
    const { container } = renderDirectory("", "member", "en");
    await waitFor(
      () => expect(container.querySelector("[data-directory-coverage]")).not.toBeNull(),
      { timeout: 8000 }
    );

    expect(within(container).getByRole("heading", { name: "Clan directory" })).toBeInTheDocument();
    expect(container.textContent).not.toContain("MISSING_MESSAGE");
  });
});
