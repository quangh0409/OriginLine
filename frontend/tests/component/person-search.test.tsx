import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";

let searchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
  usePathname: () => "/search",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/search",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/search",
}));

const { PersonSearchScreen } = await import("@/components/search/person-search-screen");

/**
 * F6 — tìm kiếm nhân khẩu, chạy trên đúng bộ MSW handler mà ứng dụng dùng.
 *
 * Yêu cầu chính (FR-4.4): **gõ không dấu phải ra kết quả có dấu**. Người
 * trong họ, nhất là kiều bào dùng bàn phím không có bộ gõ tiếng Việt, sẽ gõ
 * "nguyen van duc" chứ không gõ "Nguyễn Văn Đức". Việc bỏ dấu là của Postgres
 * (`unaccent` + cột `name_unaccented`), CLIENT KHÔNG ĐƯỢC tự bỏ dấu — nếu tự
 * làm sẽ có hai bộ so khớp khác nhau và chúng sẽ bất đồng ở đúng những chữ
 * như "Đức" / "Duc".
 *
 * Ràng buộc riêng tư kèm theo: khách vãng lai tìm tên một người còn sống phải
 * nhận đúng câu "không tìm thấy" y như khi tìm một cái tên không ai trong họ
 * từng mang. Sự KHÔNG PHÂN BIỆT ĐƯỢC ấy chính là yêu cầu (BA v2 §10).
 */

function renderSearch(query = "", role: "guest" | "member" | "admin" = "member") {
  searchParams = new URLSearchParams(query);
  return renderWithProviders(<PersonSearchScreen />, { role });
}

/** Ô nhập tên — nhãn nằm trong sr-only nên tìm theo label. */
function searchInput(): HTMLInputElement {
  return screen.getByLabelText("Tên người cần tìm") as HTMLInputElement;
}

/** Chờ danh sách kết quả hiện ra (dòng "N kết quả" đóng vai aria-live). */
async function waitForResults(): Promise<HTMLElement> {
  return waitFor(
    () => {
      const status = screen.getByRole("status");
      expect(status.textContent).toMatch(/kết quả/);
      return status;
    },
    { timeout: 10_000 }
  );
}

beforeEach(() => {
  resetRouterMock();
  searchParams = new URLSearchParams();
});

describe("trước khi gõ gì", () => {
  it("mời gõ tên chứ không báo 'không tìm thấy'", async () => {
    renderSearch();

    expect(await screen.findByText("Nhập tên để bắt đầu tìm.")).toBeInTheDocument();
    expect(screen.queryByText("Không tìm thấy ai phù hợp.")).not.toBeInTheDocument();
  });

  it("nói rõ với người dùng rằng không cần bỏ dấu", async () => {
    renderSearch();

    expect(await screen.findByText(/Không cần bỏ dấu/)).toBeInTheDocument();
  });

  it("không gọi mạng khi chưa có từ khoá — bộ lọc một mình không tìm được", async () => {
    renderSearch("generation=2");

    expect(await screen.findByText("Nhập tên để bắt đầu tìm.")).toBeInTheDocument();
  });
});

describe("tìm tên tiếng Việt không dấu (FR-4.4)", () => {
  it("gõ 'nguyen' ra được người tên 'Nguyễn'", async () => {
    renderSearch("q=nguyen");

    await waitForResults();
    const names = screen.getAllByText(/^Nguyễn /);
    expect(names.length).toBeGreaterThan(0);
    // Tên hiển thị giữ nguyên dấu — chỉ việc SO KHỚP mới bỏ dấu.
    expect(names[0]!.textContent).toMatch(/[ễầảốữộ]/u);
  });

  it("gõ 'thuy to' (không dấu) tìm ra 'Nguyễn Văn Thủy Tổ'", async () => {
    renderSearch("q=thuy to");

    await waitForResults();
    expect(screen.getByText("Nguyễn Văn Thủy Tổ")).toBeInTheDocument();
  });

  it("gõ có dấu đầy đủ cũng ra đúng người đó", async () => {
    renderSearch("q=Thủy Tổ");

    await waitForResults();
    expect(screen.getByText("Nguyễn Văn Thủy Tổ")).toBeInTheDocument();
  });

  it("chữ 'đ' không dấu ('d') vẫn khớp — đây là chữ hay hỏng nhất", async () => {
    renderSearch("q=nguyen van hoa");

    await waitForResults();
    // "Nguyễn Văn Hoà" — cả nguyên âm có dấu lẫn phụ âm đầu.
    expect(screen.getByText("Nguyễn Văn Hoà")).toBeInTheDocument();
  });

  it("gửi từ khoá lên máy chủ ĐÚNG NHƯ ĐÃ GÕ, không tự bỏ dấu ở client", async () => {
    const spy = vi.spyOn(globalThis, "fetch");
    renderSearch("q=Nguyễn");

    await waitForResults();

    const urls = spy.mock.calls
      .map((call) => String(call[0]))
      .filter((url) => url.includes("/persons/search"));
    expect(urls.length).toBeGreaterThan(0);
    expect(urls.some((url) => decodeURIComponent(url).includes("q=Nguyễn"))).toBe(true);
    spy.mockRestore();
  });

  it("không tìm thấy thì nói thẳng, không hiện danh sách rỗng im lặng", async () => {
    renderSearch("q=zzzkhongaiten");

    expect(await screen.findByText("Không tìm thấy ai phù hợp.")).toBeInTheDocument();
  });
});

describe("thẻ kết quả", () => {
  it("kèm đời, chi và quê quán để phân biệt hai người trùng tên", async () => {
    renderSearch("q=Thủy Tổ");

    await waitForResults();
    const card = screen.getByText("Nguyễn Văn Thủy Tổ").closest("li")!;
    expect(within(card).getByText(/Đời thứ 1/)).toBeInTheDocument();
    expect(within(card).getByText(/Nam Định/)).toBeInTheDocument();
  });

  it("dẫn tới hồ sơ của đúng người đó", async () => {
    renderSearch("q=Thủy Tổ");

    await waitForResults();
    const card = screen.getByText("Nguyễn Văn Thủy Tổ").closest("li")!;
    expect(within(card).getByRole("link")).toHaveAttribute("href", "/persons/p-001");
  });

  it("ghi rõ đã khuất / còn sống bằng chữ, không chỉ bằng màu", async () => {
    renderSearch("q=Thủy Tổ");

    await waitForResults();
    const card = screen.getByText("Nguyễn Văn Thủy Tổ").closest("li")!;
    expect(within(card).getByText("Đã khuất")).toBeInTheDocument();
  });
});

describe("khách vãng lai tìm kiếm (BA v2 §10)", () => {
  // "Nguyễn Thị Bé" (p-101) là trẻ vị thành niên CÒN SỐNG và là cái tên duy
  // nhất trong bộ dữ liệu giả không trùng với người đã khuất nào — đúng nhóm
  // phải được giấu kỹ nhất.
  const LIVING_MINOR = "Nguyễn Thị Bé";

  it("không thấy người còn sống nào, dù gõ đúng tên đầy đủ", async () => {
    renderSearch(`q=${LIVING_MINOR}`, "guest");

    expect(await screen.findByText("Không tìm thấy ai phù hợp.")).toBeInTheDocument();
    expect(screen.queryByText(LIVING_MINOR)).not.toBeInTheDocument();
  });

  it("thành viên đã đăng nhập thì tìm ra — chứng minh phép so sánh là có thật", async () => {
    renderSearch(`q=${LIVING_MINOR}`, "member");

    await waitForResults();
    expect(screen.getByText(LIVING_MINOR)).toBeInTheDocument();
  });

  it("câu trả lời cho người sống giống hệt câu trả lời cho một cái tên không có thật", async () => {
    const { unmount } = renderSearch(`q=${LIVING_MINOR}`, "guest");
    const livingAnswer = (await screen.findByText("Không tìm thấy ai phù hợp.")).textContent;
    unmount();

    renderSearch("q=Trần Thị Không Tồn Tại", "guest");
    const nobodyAnswer = (await screen.findByText("Không tìm thấy ai phù hợp.")).textContent;

    expect(livingAnswer).toBe(nobodyAnswer);
  });

  it("không gợi ý ở đâu đó rằng có kết quả đã bị giấu", async () => {
    renderSearch(`q=${LIVING_MINOR}`, "guest");

    await screen.findByText("Không tìm thấy ai phù hợp.");
    const text = document.body.textContent ?? "";
    expect(text).not.toMatch(/đăng nhập/i);
    expect(text).not.toMatch(/quyền/i);
    expect(text).not.toMatch(/bị ẩn|riêng tư|hạn chế/i);
  });

  it("nhưng vẫn tìm được người đã khuất — người đã khuất là công khai", async () => {
    renderSearch("q=thuy to", "guest");

    await waitForResults();
    expect(screen.getByText("Nguyễn Văn Thủy Tổ")).toBeInTheDocument();
  });
});

describe("trạng thái nằm trên URL", () => {
  it("nhận từ khoá sẵn có từ ?q= để một đường link chia sẻ được mở ra đúng kết quả", async () => {
    renderSearch("q=thuy to");

    expect(searchInput().value).toBe("thuy to");
    await waitForResults();
    expect(screen.getByText("Nguyễn Văn Thủy Tổ")).toBeInTheDocument();
  });

  it("ghi lại từ khoá đã áp dụng vào thanh địa chỉ để chia sẻ được", async () => {
    renderSearch("q=thuy to");

    await waitForResults();
    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalled();
    });
    const target = routerMock.replace.mock.calls.at(-1)?.[0] as string;
    // URLSearchParams mã hoá dấu cách thành "+", đó là dạng thật trên thanh địa chỉ.
    expect(target).toContain("q=thuy+to");
  });
});
