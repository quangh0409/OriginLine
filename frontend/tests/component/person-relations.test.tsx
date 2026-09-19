import { describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders, expectNoHiddenFieldPlaceholders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/p-001",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/p-001",
}));

const { PersonProfile } = await import("@/components/person/person-profile");

/**
 * Mục "Quan hệ" trên hồ sơ nhân khẩu, chạy trên đúng bộ MSW handler mà ứng
 * dụng dùng ở chế độ `dev:mock`, với vai người gọi lấy từ header `x-mock-role`.
 *
 * Nhân vật cố định trong đồ thị giả lập:
 *  - `p-001` Nguyễn Văn Thủy Tổ — đã khuất, có vợ và bốn con;
 *  - `p-021` Trần Văn Khoa — **rể**, đã khuất, KHÔNG có tổ tiên trong dòng họ
 *    này: hồ sơ của anh chỉ còn đúng một quan hệ, nên đây là phép thử gắt nhất
 *    cho câu hỏi "không có mục quan hệ thì hồ sơ này còn lại gì";
 *  - `g-16r` Nguyễn Văn Long — đã khuất, hai vợ (đa thê);
 *  - `g-iv` Nguyễn Văn Việt — đã khuất, có con nuôi.
 */

type Role = "guest" | "member" | "branch-head" | "admin";

async function renderProfile(personId: string, role: Role) {
  const view = renderWithProviders(<PersonProfile personId={personId} />, { role });
  await screen.findByRole("heading", { name: "Quan hệ trong dòng họ" }, { timeout: 5000 });
  // Đầu mục hiện ra trước dữ liệu (đang là khung chờ). Khẳng định bất cứ điều gì lúc
  // này đều đúng một cách vô nghĩa, nên phải đợi khung chờ biến mất đã.
  await waitFor(
    () => expect(relationsSection().querySelector(".ant-skeleton")).toBeNull(),
    { timeout: 5000 }
  );
  return view;
}

function relationsSection(): HTMLElement {
  return screen.getByRole("heading", { name: "Quan hệ trong dòng họ" }).closest("section")!;
}

async function findGroup(name: string): Promise<HTMLElement> {
  const section = relationsSection();
  const heading = await within(section).findByRole("heading", { name, level: 3 });
  return heading.parentElement as HTMLElement;
}

describe("hồ sơ rể — mục quan hệ là thứ duy nhất còn lại", () => {
  it("dựng được quan hệ vợ/chồng cho người không có tổ tiên trong dòng họ này", async () => {
    await renderProfile("p-021", "guest");

    const spouses = await findGroup("Vợ / Chồng");
    expect(within(spouses).getByText("Nguyễn Thị Ngọc")).toBeInTheDocument();
  });

  it("không dựng những nhóm không có ai — không có đầu mục trống", async () => {
    await renderProfile("p-021", "guest");
    const section = relationsSection();

    for (const group of ["Cha mẹ", "Con", "Anh chị em", "Nối dõi"]) {
      expect(
        within(section).queryByRole("heading", { name: group, level: 3 }),
        `nhóm "${group}" rỗng mà vẫn hiện đầu mục`
      ).not.toBeInTheDocument();
    }
  });
});

describe("hồ sơ thủy tổ — gom nhóm theo loại quan hệ", () => {
  it("xếp con vào nhóm Con và vợ vào nhóm Vợ / Chồng", async () => {
    await renderProfile("p-001", "guest");

    const children = await findGroup("Con");
    expect(within(children).getByText("Nguyễn Văn Hiển")).toBeInTheDocument();
    expect(within(children).getByText("Nguyễn Văn Hoà")).toBeInTheDocument();

    const spouses = await findGroup("Vợ / Chồng");
    expect(within(spouses).getByText("Trần Thị Hạnh")).toBeInTheDocument();
  });

  it("mỗi quan hệ nối được sang hồ sơ người kia", async () => {
    await renderProfile("p-001", "guest");
    const children = await findGroup("Con");

    const link = within(children).getByRole("link", { name: "Nguyễn Văn Hiển" });
    expect(link).toHaveAttribute("href", "/persons/p-010");
  });

  it("mỗi quan hệ mở được màn tra danh xưng với người kia là ĐÍCH (`to=`)", async () => {
    // Regression: `from=` biến câu hỏi thành "người kia gọi ai là gì" — hỏi
    // ngược 180° so với điều người dùng muốn biết.
    await renderProfile("p-001", "guest");
    const children = await findGroup("Con");

    const lookup = within(children).getByRole("link", {
      name: "Tra danh xưng với Nguyễn Văn Hiển",
    });
    expect(lookup).toHaveAttribute("href", "/kinship?to=p-010");
    expect(lookup.getAttribute("href")).not.toContain("from=");
  });

  it("hiện bối cảnh đời và chi/ngành cho từng người", async () => {
    await renderProfile("p-001", "guest");
    const children = await findGroup("Con");

    expect(within(children).getAllByText("Đời thứ 2").length).toBeGreaterThan(0);
    expect(within(children).getAllByText("Chi Nhất").length).toBeGreaterThan(0);
  });

  it("không sinh ra chữ xưng hô nào — danh xưng chỉ đến từ API kinship", async () => {
    await renderProfile("p-001", "guest");
    const text = relationsSection().textContent ?? "";

    // Đây là các danh xưng phụ thuộc vùng miền và bộ quy tắc dòng họ. Nếu một
    // trong số chúng xuất hiện, nghĩa là giao diện đã tự suy diễn danh xưng.
    for (const title of ["bác ", "chú ", "cậu ", "thím ", "cụ ông", "anh cả"]) {
      expect(text.toLowerCase(), `danh xưng "${title}" bị suy diễn ở client`).not.toContain(title);
    }
  });
});

describe("sắc thái đã có trong dữ liệu", () => {
  it("hiện thứ tự vợ khi đa thê, và chỉ khi đó", async () => {
    await renderProfile("g-16r", "member");
    const spouses = await findGroup("Vợ / Chồng");

    expect(within(spouses).getByText("Vợ cả")).toBeInTheDocument();
    expect(within(spouses).getByText("Vợ thứ 2")).toBeInTheDocument();
  });

  it("không dán nhãn 'vợ cả' cho hồ sơ chỉ có một vợ", async () => {
    // Nhãn thứ tự ở đây sẽ gợi ý sai rằng có người vợ thứ hai chưa hiện ra.
    await renderProfile("p-001", "guest");
    const spouses = await findGroup("Vợ / Chồng");

    expect(within(spouses).queryByText("Vợ cả")).not.toBeInTheDocument();
    expect(within(spouses).queryByText(/Vợ thứ/)).not.toBeInTheDocument();
  });

  it("đánh dấu con nuôi", async () => {
    await renderProfile("g-iv", "member");
    const children = await findGroup("Con");

    expect(within(children).getAllByText("Con nuôi").length).toBeGreaterThan(0);
  });
});

describe("khách chưa đăng nhập", () => {
  it("được cho biết vì sao danh sách chỉ có người đã khuất, và cách xem tiếp", async () => {
    await renderProfile("p-001", "guest");
    const note = relationsSection().querySelector('[data-privacy-notice="relations-guest"]')!;

    expect(note).toBeTruthy();
    expect(note.textContent).toContain("tư cách khách");
    expect(note.textContent).toContain("Đăng nhập");
  });

  it("câu đó không nhắc tới bất kỳ con số nào — đếm đã là tiết lộ", async () => {
    await renderProfile("p-001", "guest");
    const note = relationsSection().querySelector('[data-privacy-notice="relations-guest"]')!;

    expect(note.textContent ?? "").not.toMatch(/\d/);
  });

  it("thành viên đã đăng nhập không phải đọc câu đó", async () => {
    await renderProfile("p-001", "member");

    expect(
      relationsSection().querySelector('[data-privacy-notice="relations-guest"]')
    ).toBeNull();
  });
});

describe("mục quan hệ không rò rỉ quy mô phần bị ẩn", () => {
  it.each([
    ["p-001", "guest"],
    ["p-001", "member"],
    ["p-021", "guest"],
    ["p-100", "member"],
    ["p-100", "branch-head"],
  ] as const)("với %s / %s không đếm ra thứ gì bị giấu", async (id, role) => {
    await renderProfile(id, role);
    const text = relationsSection().textContent ?? "";

    // "còn 3 người nữa", "2 quan hệ bị giấu", "1 thông tin"… — mọi hình thức
    // đếm phần bị lọc đều tiết lộ đúng thứ phân tầng đang che.
    expect(text).not.toMatch(/\d+\s*(người|quan hệ|thông tin|trường|mục)\b/i);
    expect(text).not.toMatch(/bị ẩn|đã ẩn|không có quyền|ẩn bớt/i);
  });

  it("không để lại chỗ trống ngụ ý có dữ liệu bị ẩn", async () => {
    const { container } = await renderProfile("p-001", "guest");
    await waitFor(() => expect(relationsSection().textContent?.length ?? 0).toBeGreaterThan(0));

    expectNoHiddenFieldPlaceholders(container);
  });
});

/** Gọi thẳng bộ giả lập với một vai cụ thể — không qua React, không qua cache. */
function fetchAs(personId: string, role: Role): Promise<Response> {
  return fetch(`http://localhost:8080/api/v1/persons/${personId}`, {
    headers: { "x-mock-role": role },
  });
}

/**
 * `RelationshipDto.otherPerson` — hồ sơ tự mang theo đầu kia.
 *
 * Trước trường này, mục "Quan hệ" dựng một khung xương rồi chờ trọn một lượt
 * `/tree` chỉ để đổi một danh sách id lấy một danh sách tên. Nay bốn nhóm
 * trực tiếp vẽ được ngay từ phản hồi hồ sơ; lượt `/tree` còn lại đã rời khỏi
 * đường găng và chỉ bổ khuyết hai thứ không có nguồn nào khác — **anh chị em**
 * (quan hệ hai bậc) và **`badges`** (`PersonSummaryDto` không có trường ấy).
 */
describe("đầu kia đi kèm ngay trong hồ sơ", () => {
  it("bộ giả lập gửi `otherPerson` cho mọi cạnh, đã lọc theo người gọi", async () => {
    const response = await fetchAs("p-001", "member");
    const person = (await response.json()) as {
      relationships?: Array<{ relType: string; otherPerson?: { id: string; displayName: string } }>;
    };

    expect(person.relationships?.length ?? 0).toBeGreaterThan(0);
    for (const rel of person.relationships ?? []) {
      expect(rel.otherPerson, `cạnh ${rel.relType} thiếu otherPerson`).toBeTruthy();
      expect(rel.otherPerson?.displayName).toBeTruthy();
    }
  });

  it("chỉ có quan hệ MỘT BẬC — anh chị em không nằm trong `relationships`", async () => {
    // Hợp đồng nói thẳng: "quan hệ trực tiếp một bậc, KHÔNG phải cả cây". Nếu
    // bộ giả lập rộng tay hơn bản thật, giao diện sẽ được viết cho một thế giới
    // không tồn tại và hỏng đúng lúc nối vào backend.
    const person = (await (await fetchAs("p-010", "member")).json()) as {
      relationships?: Array<{ fromPersonId: string; toPersonId: string }>;
    };

    for (const rel of person.relationships ?? []) {
      expect(rel.fromPersonId === "p-010" || rel.toPersonId === "p-010").toBe(true);
    }
  });

  it("không có khung chờ nào trước khi bốn nhóm trực tiếp hiện ra", async () => {
    renderWithProviders(<PersonProfile personId="p-001" />, { role: "member" });

    // Tên người con phải đọc được mà KHÔNG cần chờ lượt `/tree` — đây chính là
    // khoản nợ đang trả: trước đây chỗ này là một `.ant-skeleton`.
    const link = await screen.findByRole("link", { name: "Nguyễn Văn Hiển" }, { timeout: 5000 });
    expect(link).toHaveAttribute("href", "/persons/p-010");
  });

  it("`otherPerson` vắng mặt thì vẫn vẽ đủ — đường lùi về chiếu `/tree` còn sống", async () => {
    // `p-011` là hồ sơ mà bộ giả lập CỐ Ý không gửi `otherPerson` (xem
    // src/mocks/person-relationships.ts). Trường này nằm ngoài `required` của
    // hợp đồng, nên một đường lùi không bao giờ chạy là một đường lùi đã hỏng
    // mà không ai biết.
    await renderProfile("p-011", "member");

    const children = await findGroup("Con");
    expect(within(children).getAllByRole("link").length).toBeGreaterThan(0);
  });

  it("tóm tắt đi kèm cũng chịu phân tầng: khách không nhận cạnh nào tới người còn sống", async () => {
    const person = (await (await fetchAs("p-001", "guest")).json()) as {
      relationships?: Array<{ otherPerson?: { isAlive: boolean } }>;
    };

    // Cạnh tới một người còn sống bị loại CẢ CẠNH với khách — không có tóm tắt
    // "đã che" nào, vì trạng thái ấy không tồn tại trên dây.
    for (const rel of person.relationships ?? []) {
      expect(rel.otherPerson?.isAlive ?? false).toBe(false);
    }
  });
});
