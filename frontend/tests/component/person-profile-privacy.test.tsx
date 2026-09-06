import { describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders, expectNoHiddenFieldPlaceholders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/persons/p-100",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/persons/p-100",
}));

const { PersonProfile } = await import("@/components/person/person-profile");

/**
 * F3 — hồ sơ nhân khẩu, rendered against the SAME MSW handlers the app runs
 * on, with the caller role driven by the dev `x-mock-role` header.
 *
 * The rule under test is BA v2 §10 / Nghị định 13/2023 as restated in the F3
 * brief: "không hiển thị ô trống gợi ý có dữ liệu bị ẩn". A hidden field must
 * be indistinguishable from a field that was never recorded — so it must
 * vanish label and all, with no dash, no bullets, no lock, no tier badge.
 *
 * Fixtures: p-001 deceased (always PUBLIC) · p-100 living adult ·
 * p-101 living minor with privacyLevel RESTRICTED.
 */

async function renderProfile(personId: string, role: "guest" | "member" | "branch-head" | "admin") {
  const view = renderWithProviders(<PersonProfile personId={personId} />, { role });
  await waitFor(() => expect(screen.queryByRole("progressbar")).not.toBeInTheDocument(), {
    timeout: 5000,
  });
  return view;
}

describe("guest looking at a living person", () => {
  it("is told the person is not found, never that access was denied", async () => {
    // The backend answers 404 (not 403) so that the ERROR ITSELF does not
    // confirm the person exists. The UI must not undo that by hinting at
    // permissions.
    await renderProfile("p-100", "guest");

    expect(await screen.findByText("Không tìm thấy nhân khẩu này.")).toBeInTheDocument();
    const body = document.body;
    expect(body.textContent).not.toMatch(/quyền/i);
    expect(body.textContent).not.toMatch(/đăng nhập/i);
    expect(body.textContent).not.toMatch(/403|forbidden/i);
  });

  it("leaks nothing about a living minor either", async () => {
    await renderProfile("p-101", "guest");

    expect(await screen.findByText("Không tìm thấy nhân khẩu này.")).toBeInTheDocument();
    expect(screen.queryByText(/Nguyễn Thị Bé/)).not.toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/Chi Nhất/);
  });
});

describe("guest looking at a deceased ancestor", () => {
  it("sees the full public record — deceased persons are public", async () => {
    const { container } = await renderProfile("p-001", "guest");

    expect(await screen.findByRole("heading", { name: /Nguyễn Văn Thủy Tổ/ })).toBeInTheDocument();
    expect(screen.getByText("Đã khuất")).toBeInTheDocument();
    expect(screen.getByText("Nam Định")).toBeInTheDocument();
    expectNoHiddenFieldPlaceholders(container);
  });

  it("shows every recorded name layer, including tên húy and tên thụy", async () => {
    await renderProfile("p-001", "guest");

    await screen.findByRole("heading", { name: /Nguyễn Văn Thủy Tổ/ });
    const namesSection = screen.getByRole("heading", { name: "Các lớp tên" }).closest("section")!;
    expect(within(namesSection).getByText("Tên húy")).toBeInTheDocument();
    expect(within(namesSection).getByText("Nguyễn Văn Tổ")).toBeInTheDocument();
    expect(within(namesSection).getByText("Tên thụy")).toBeInTheDocument();
    expect(within(namesSection).getByText("Trung Hậu Công")).toBeInTheDocument();
  });

  it("shows the death date in both calendars, lunar marked as the giỗ anchor", async () => {
    await renderProfile("p-001", "guest");

    await screen.findByRole("heading", { name: /Nguyễn Văn Thủy Tổ/ });
    const dates = screen.getByRole("heading", { name: "Ngày sinh – ngày mất" }).closest("section")!;
    expect(within(dates).getByText("03/11/1852")).toBeInTheDocument();
    expect(within(dates).getByText(/Ngày 22 tháng 9 âm lịch/)).toBeInTheDocument();
    expect(
      within(dates).getByText("Ngày giỗ tính theo ngày âm lịch ở trên.")
    ).toBeInTheDocument();
  });
});

describe("member (Tier 1) looking at a living relative", () => {
  it("sees name, generation and branch and NOTHING else — no empty rows anywhere", async () => {
    const { container } = await renderProfile("p-100", "member");

    expect(await screen.findByRole("heading", { name: /Nguyễn Văn An/ })).toBeInTheDocument();
    // Rendered twice on purpose: once in the identity band, once as a fact row.
    expect(screen.getAllByText("Đời thứ 5").length).toBeGreaterThan(0);
    expectNoHiddenFieldPlaceholders(container);
  });

  it("hides the Tier-2 and Tier-3 sections entirely, headings included", async () => {
    await renderProfile("p-100", "member");
    await screen.findByRole("heading", { name: /Nguyễn Văn An/ });

    // Section headings must be gone, not empty.
    expect(screen.queryByRole("heading", { name: "Liên hệ" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Tiểu sử & ghi chép" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Ngày sinh – ngày mất" })).not.toBeInTheDocument();

    // ...and so must every individual label inside them.
    for (const label of ["Điện thoại", "Thư điện tử", "Zalo", "Nghề nghiệp", "Ngày sinh"]) {
      expect(screen.queryByText(label), `label "${label}" leaked at Tier 1`).not.toBeInTheDocument();
    }
  });

  it("never renders the withheld values themselves", async () => {
    await renderProfile("p-100", "member");
    await screen.findByRole("heading", { name: /Nguyễn Văn An/ });

    const text = document.body.textContent ?? "";
    expect(text).not.toContain("+84 912 345 678");
    expect(text).not.toContain("an.nguyen@example.com");
    expect(text).not.toContain("Kỹ sư phần mềm");
    expect(text).not.toContain("1990");
  });

  it("offers no edit affordance, because the server said canEdit is false", async () => {
    await renderProfile("p-100", "member");
    await screen.findByRole("heading", { name: /Nguyễn Văn An/ });
    expect(screen.queryByRole("button", { name: /Sửa hồ sơ/ })).not.toBeInTheDocument();
  });

  it("shows a living person as living, without colour being the only cue", async () => {
    await renderProfile("p-100", "member");
    await screen.findByRole("heading", { name: /Nguyễn Văn An/ });
    expect(screen.getByText("Còn sống")).toBeInTheDocument();
    expect(screen.queryByText("Đã khuất")).not.toBeInTheDocument();
  });
});

describe("member looking at a living MINOR (maximally hidden)", () => {
  it("sees only the Tier-1 identity, with no placeholder marking the rest", async () => {
    const { container } = await renderProfile("p-101", "member");

    expect(await screen.findByRole("heading", { name: /Nguyễn Thị Bé/ })).toBeInTheDocument();
    expectNoHiddenFieldPlaceholders(container);
    expect(screen.queryByRole("heading", { name: "Liên hệ" })).not.toBeInTheDocument();
  });

  it("never discloses the privacy level itself — even that is sensitive", async () => {
    await renderProfile("p-101", "member");
    await screen.findByRole("heading", { name: /Nguyễn Thị Bé/ });
    const text = document.body.textContent ?? "";
    expect(text).not.toMatch(/RESTRICTED/i);
    expect(text).not.toMatch(/Hạn chế tối đa/);
  });
});

describe("branch head (Tier 2) looking at a living relative", () => {
  it("gains occupation, province and a YEAR-ONLY birth date", async () => {
    const { container } = await renderProfile("p-100", "branch-head");

    await screen.findByRole("heading", { name: /Nguyễn Văn An/ });
    expect(screen.getByText("Kỹ sư phần mềm")).toBeInTheDocument();
    expect(screen.getByText("Hà Nội")).toBeInTheDocument();
    // The day is Tier 3: the wire carries a 1990-01-01 filler that must never
    // reach the screen as a real day.
    expect(screen.getByText("1990")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("01/01/1990");
    expect(document.body.textContent).not.toContain("20/07/1990");
    expectNoHiddenFieldPlaceholders(container);
  });

  it("still gets no contact details at all", async () => {
    await renderProfile("p-100", "branch-head");
    await screen.findByRole("heading", { name: /Nguyễn Văn An/ });
    expect(screen.queryByRole("heading", { name: "Liên hệ" })).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain("+84 912 345 678");
  });
});

describe("admin (Tier 3) looking at a living relative", () => {
  it("sees the contact block, and only then", async () => {
    const { container } = await renderProfile("p-100", "admin");

    await screen.findByRole("heading", { name: /Nguyễn Văn An/ });
    expect(screen.getByRole("heading", { name: "Liên hệ" })).toBeInTheDocument();
    expect(screen.getByText("+84 912 345 678")).toBeInTheDocument();
    expect(screen.getByText("an.nguyen@example.com")).toBeInTheDocument();
    expectNoHiddenFieldPlaceholders(container);
  });

  it("gets an edit affordance because the server granted canEdit", async () => {
    await renderProfile("p-100", "admin");
    await screen.findByRole("heading", { name: /Nguyễn Văn An/ });
    expect(screen.getByRole("link", { name: /Sửa hồ sơ/ })).toBeInTheDocument();
  });
});

describe("the profile never invents UI that hints at hidden data", () => {
  it.each([
    ["p-001", "guest"],
    ["p-100", "member"],
    ["p-100", "branch-head"],
    ["p-100", "admin"],
    ["p-101", "member"],
  ] as const)("renders %s for %s with no placeholder, lock or tier badge", async (id, role) => {
    const { container } = await renderProfile(id, role);
    await waitFor(() => expect(container.textContent?.length ?? 0).toBeGreaterThan(0));

    expectNoHiddenFieldPlaceholders(container);
    // No lock/eye iconography anywhere in the tree.
    expect(container.querySelector('[aria-label*="lock" i]')).toBeNull();
    expect(container.querySelector('[data-icon="lock"]')).toBeNull();
    expect(container.querySelector('[data-icon="eye-invisible"]')).toBeNull();
    // No untranslated key ever reaches the screen.
    expect(container.textContent).not.toContain("MISSING_MESSAGE");
  });
});
