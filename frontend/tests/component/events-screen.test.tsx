import { beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import type { EventDto } from "@/types/api";

let searchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
  usePathname: () => "/events",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/events",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/events",
}));

const { EventCard } = await import("@/components/events/event-card");
const { EventDualDate } = await import("@/components/events/event-dual-date");
const { EventsScreen } = await import("@/components/events/events-screen");

/**
 * F7 — sự kiện & nhắc giỗ.
 *
 * Ba điều màn hình này bắt buộc phải đúng, vì sai là cả họ đi giỗ nhầm ngày:
 *
 *  1. **Ngày âm đứng trước.** `death_lunar` là nguồn sự thật của một đám giỗ
 *     (CLAUDE.md); ngày dương chỉ là tiện ích cho người sống theo lịch làm
 *     việc Gregory. Thứ tự hiển thị nói lên cái nào là thật.
 *  2. **Tháng nhuận luôn phải hiện.** Tháng 9 nhuận không phải tháng 9. Bỏ
 *     quên chữ "nhuận" là dời đám giỗ đi trọn một tháng mà không có lỗi nào
 *     báo lên.
 *  3. **Mốc nhắc 7 / 3 / 1 ngày (FR-2.2)** hiện đúng như máy chủ sẽ gửi.
 *
 * Ngày dương lấy nguyên từ `nextOccurrenceSolar` do máy chủ tính (Hồ Ngọc
 * Đức, GMT+7). Không có một phép quy đổi nào ở phía client.
 */

function giỗ(overrides: Partial<EventDto> = {}): EventDto {
  return {
    id: "ev-test",
    eventType: "GIO_TO",
    title: "Giỗ Thủy tổ Nguyễn Văn Thủy Tổ",
    lunarDate: { year: 2026, month: 9, day: 22, leap: false },
    nextOccurrenceSolar: "2026-11-01",
    nextOccurrenceLunarYear: 2026,
    daysUntil: 62,
    isClanLevel: true,
    reminderOffsets: [7, 3, 1],
    ...overrides,
  };
}

beforeEach(() => {
  resetRouterMock();
  searchParams = new URLSearchParams();
});

// jsdom không cài `scrollIntoView` — gap môi trường có thật, không phải lỗi
// của màn hình. Trước Đợt 2 không ca nào từng đi qua nhánh cuộn-tới-thẻ
// (`?event=`) nên khoảng trống này chưa lộ ra; nay ca "mở thẳng vào danh
// sách" chạy đúng nhánh đó.
Element.prototype.scrollIntoView ??= vi.fn();

describe("ngày âm và ngày dương trên một thẻ giỗ", () => {
  it("hiện ngày âm bằng chữ đầy đủ 'Ngày 22 tháng 9 âm lịch'", () => {
    renderWithProviders(<EventDualDate event={giỗ()} />, { role: "member" });

    expect(screen.getByText(/Ngày 22 tháng 9 âm lịch/)).toBeInTheDocument();
  });

  it("hiện kèm ngày dương do máy chủ quy đổi, định dạng ngày/tháng/năm", () => {
    renderWithProviders(<EventDualDate event={giỗ()} />, { role: "member" });

    expect(screen.getByText("Dương lịch: 01/11/2026")).toBeInTheDocument();
  });

  it("đặt ngày ÂM trước ngày dương — âm lịch mới là nguồn sự thật của giỗ", () => {
    const { container } = renderWithProviders(<EventDualDate event={giỗ()} />, {
      role: "member",
    });

    const text = container.textContent ?? "";
    expect(text.indexOf("âm lịch")).toBeGreaterThanOrEqual(0);
    expect(text.indexOf("âm lịch")).toBeLessThan(text.indexOf("Dương lịch"));
  });

  it("ĐÁNH DẤU THÁNG NHUẬN — tháng 9 nhuận không phải tháng 9", () => {
    renderWithProviders(
      <EventDualDate event={giỗ({ lunarDate: { year: 2026, month: 9, day: 22, leap: true } })} />,
      { role: "member" }
    );

    expect(screen.getByText("(tháng nhuận)")).toBeInTheDocument();
  });

  it("không gắn chữ 'nhuận' cho tháng thường", () => {
    renderWithProviders(<EventDualDate event={giỗ()} />, { role: "member" });

    expect(screen.queryByText("(tháng nhuận)")).not.toBeInTheDocument();
  });

  it("hiện can chi khi máy chủ có gửi", () => {
    renderWithProviders(
      <EventDualDate
        event={giỗ({
          lunarDate: { year: 2026, month: 9, day: 22, leap: false, canChi: "Bính Ngọ" },
        })}
      />,
      { role: "member" }
    );

    expect(screen.getByText("Bính Ngọ")).toBeInTheDocument();
  });

  it("không bịa ngày dương khi máy chủ chưa quy đổi được", () => {
    const { container } = renderWithProviders(
      <EventDualDate event={giỗ({ nextOccurrenceSolar: null })} />,
      { role: "member" }
    );

    expect(container.textContent).toContain("Ngày 22 tháng 9 âm lịch");
    expect(container.textContent).not.toContain("Dương lịch");
  });
});

describe("mốc nhắc 7 / 3 / 1 ngày (FR-2.2)", () => {
  it("ghi rõ sẽ nhắc trước 7, 3 và 1 ngày", () => {
    renderWithProviders(<EventCard event={giỗ()} />, { role: "member" });

    expect(screen.getByText("Nhắc trước 7, 3, 1 ngày")).toBeInTheDocument();
  });

  it("đếm ngược đúng số ngày còn lại", () => {
    renderWithProviders(<EventCard event={giỗ({ daysUntil: 3 })} />, { role: "member" });

    expect(screen.getByText("Còn 3 ngày")).toBeInTheDocument();
  });

  it("gọi đúng là 'Hôm nay' khi đến ngày", () => {
    renderWithProviders(<EventCard event={giỗ({ daysUntil: 0 })} />, { role: "member" });

    expect(screen.getByText("Hôm nay")).toBeInTheDocument();
  });

  it("nói rõ đã qua bao nhiêu ngày thay vì đếm ngược số âm", () => {
    renderWithProviders(<EventCard event={giỗ({ daysUntil: -2 })} />, { role: "member" });

    expect(screen.getByText("Đã qua 2 ngày")).toBeInTheDocument();
  });

  it("không hiện thẻ đếm ngày khi máy chủ không gửi daysUntil", () => {
    renderWithProviders(<EventCard event={giỗ({ daysUntil: null })} />, { role: "member" });

    expect(screen.queryByText(/Còn \d+ ngày$/)).not.toBeInTheDocument();
    expect(screen.queryByText("Hôm nay")).not.toBeInTheDocument();
  });
});

describe("thẻ giỗ — phạm vi dòng họ / chi", () => {
  it("gắn nhãn 'Cả dòng họ' cho giỗ cấp dòng họ", () => {
    renderWithProviders(<EventCard event={giỗ()} />, { role: "member" });

    expect(screen.getAllByText("Cả dòng họ").length).toBeGreaterThan(0);
    expect(screen.getByText("Giỗ Tổ")).toBeInTheDocument();
  });

  it("gắn tên chi cho giỗ cấp chi", () => {
    renderWithProviders(
      <EventCard
        event={giỗ({
          eventType: "GIO_CHI",
          isClanLevel: false,
          targetBranch: { id: "b-chi1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
        })}
      />,
      { role: "member" }
    );

    expect(screen.getByText("Chi Nhất")).toBeInTheDocument();
    expect(screen.getByText("Giỗ chi")).toBeInTheDocument();
  });

  it("dùng nguyên tiêu đề máy chủ soạn, không tự ghép 'Giỗ ' + tên người", () => {
    // Tự ghép sẽ in ra tên của một người mà người xem có thể không được phép
    // thấy — máy chủ soạn sẵn tiêu đề chính là để tránh việc đó.
    renderWithProviders(<EventCard event={giỗ({ title: "Giỗ cụ Tổ đời thứ nhất" })} />, {
      role: "member",
    });

    expect(
      screen.getByRole("heading", { name: "Giỗ cụ Tổ đời thứ nhất" })
    ).toBeInTheDocument();
  });

  it("thiếu tiêu đề thì lùi về tên loại lễ, không ghép tên người", () => {
    renderWithProviders(<EventCard event={giỗ({ title: null })} />, { role: "member" });

    expect(screen.getByRole("heading", { name: "Giỗ Tổ" })).toBeInTheDocument();
  });
});

/**
 * Chuyển kiểu xem qua `<EventsViewSwitcher>` (role="radiogroup"/"radio").
 * Dùng `fireEvent` chứ không `userEvent`: các nút này là `<button>` trần,
 * không có gì đặc biệt để `userEvent` phải mô phỏng, và `fireEvent` nhanh hơn
 * đáng kể trên một bộ dữ liệu giả gần trăm sự kiện.
 */
function switchView(name: "Lịch tháng" | "Lịch năm" | "Danh sách") {
  fireEvent.click(screen.getByRole("radio", { name }));
}

describe("màn hình sự kiện — kiểu xem mặc định (Đợt 2)", () => {
  it("mặc định là LỊCH THÁNG, không phải danh sách hay lịch năm", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    // Bộ chuyển kiểu xem phải có mặt và đúng lựa chọn ban đầu là "Lịch tháng".
    const monthRadio = await screen.findByRole("radio", { name: "Lịch tháng" }, { timeout: 15_000 });
    expect(monthRadio).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("radio", { name: "Lịch năm" })).toHaveAttribute("aria-checked", "false");
    expect(screen.getByRole("radio", { name: "Danh sách" })).toHaveAttribute("aria-checked", "false");

    // Lịch tháng thật sự được vẽ ra, chứ không chỉ nút bấm nói vậy. Thẻ
    // `<section aria-label="Lịch tháng">` có tên khả truy cập nên mang vai
    // "region" — `findBy`, không `getBy`, vì `<EventMonthCalendar>` tự có
    // thêm một nhịp `useEffect` (đọc "hôm nay") trước khi vẽ xong lưới thật.
    expect(await screen.findByRole("region", { name: "Lịch tháng" })).toBeInTheDocument();
    // Hai kiểu xem kia không cùng có mặt — chúng loại trừ nhau.
    expect(screen.queryByRole("heading", { name: "Lịch mười hai tháng tới" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Sắp tới" })).not.toBeInTheDocument();
  });

  it("bấm 'Lịch năm' thì chuyển hẳn sang lịch năm, lịch tháng biến mất", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    await screen.findByRole("radio", { name: "Lịch năm" }, { timeout: 15_000 });
    switchView("Lịch năm");

    expect(await screen.findByRole("heading", { name: "Lịch mười hai tháng tới" })).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Lịch tháng" })).not.toBeInTheDocument();
  });

  it("bấm 'Danh sách' thì chuyển hẳn sang danh sách 'Sắp tới'", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    await screen.findByRole("radio", { name: "Danh sách" }, { timeout: 15_000 });
    switchView("Danh sách");

    expect(await screen.findByRole("heading", { name: "Sắp tới" })).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Lịch tháng" })).not.toBeInTheDocument();
  });

  it("có `?event=` từ một lời nhắc thì mở thẳng vào DANH SÁCH — nơi duy nhất đã biết cuộn tới đúng thẻ", async () => {
    searchParams = new URLSearchParams("event=ev-hop-ho");
    renderWithProviders(<EventsScreen />, { role: "member" });

    expect(
      await screen.findByRole("heading", { name: "Sắp tới" }, { timeout: 15_000 })
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Danh sách" })).toHaveAttribute("aria-checked", "true");
  });
});

describe("song lịch âm–dương ở CẢ BA kiểu xem (Đợt 2)", () => {
  // Lịch tháng chỉ vẽ chi tiết (cả hai lịch) cho NGÀY ĐANG CHỌN, mặc định là
  // hôm nay — mà "hôm nay có việc họ hay không" phụ thuộc đồng hồ thật lúc
  // test chạy. Ca xác định — ghim đồng hồ hệ thống rồi kiểm cả hai lịch cùng
  // lúc trên một sự kiện đã biết trước — nằm ở
  // `tests/component/event-month-calendar.test.tsx`, tách khỏi bộ dữ liệu giả
  // ngẫu nhiên-theo-ngày ở đây.
  it("lịch năm: mỗi dòng việc họ mang cả ngày âm lẫn ngày dương", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    await screen.findByRole("radio", { name: "Lịch năm" }, { timeout: 15_000 });
    switchView("Lịch năm");

    const calendar = await screen.findByLabelText("Lịch mười hai tháng tới");
    expect(within(calendar).getAllByText(/Ngày \d+ tháng \d+ âm lịch/).length).toBeGreaterThan(0);
  });

  it("danh sách: mỗi thẻ việc họ mang cả ngày âm lẫn ngày dương", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    await screen.findByRole("radio", { name: "Danh sách" }, { timeout: 15_000 });
    switchView("Danh sách");

    const list = await screen.findByRole("heading", { name: "Sắp tới" });
    const section = list.closest("section")!;
    const lunar = within(section).getAllByText(/Ngày \d+ tháng \d+ âm lịch/);
    expect(lunar.length).toBeGreaterThan(0);
    expect(within(section).getAllByText(/^Dương lịch: /).length).toBeGreaterThan(0);
  });
});

describe("nút 'Tạo việc họ' — chỉ Trưởng cành/chi/họ thấy được (Đợt 2)", () => {
  it.each([["guest"], ["member"]] as const)(
    "vai '%s' không thấy nút tạo sự kiện",
    async (role) => {
      renderWithProviders(<EventsScreen />, { role });

      await screen.findByRole("radio", { name: "Lịch tháng" }, { timeout: 15_000 });
      expect(screen.queryByRole("link", { name: "Tạo việc họ" })).not.toBeInTheDocument();
    }
  );

  it.each([["branch-head"], ["admin"]] as const)(
    "vai '%s' thấy nút tạo sự kiện, dẫn tới /events/new",
    async (role) => {
      renderWithProviders(<EventsScreen />, { role });

      const link = await screen.findByRole("link", { name: "Tạo việc họ" }, { timeout: 15_000 });
      expect(link).toHaveAttribute("href", "/events/new");
    }
  );
});

describe("màn hình sự kiện", () => {
  it("khách vãng lai không thấy sự kiện của người còn sống (mừng thọ)", async () => {
    renderWithProviders(<EventsScreen />, { role: "guest" });

    await screen.findByRole("radio", { name: "Danh sách" }, { timeout: 15_000 });
    switchView("Danh sách");
    await screen.findByRole("heading", { name: "Sắp tới" });
    await waitFor(() => {
      expect(screen.queryByText(/Mừng thọ/)).not.toBeInTheDocument();
    });
  });
});

/**
 * V10 — `EventType` thành **song ánh 12 giá trị**.
 *
 * Trước đó `KHANH_THANH`, `HOP_HO`, `CUOI_HOI` cùng đi ra dây dưới mã `KHAC`,
 * nên qua API một buổi họp họ không phân biệt được với một đám cưới; và
 * `SINH_NHAT` bị hoá thành `MUNG_THO`, tức sinh nhật một đứa trẻ và lễ mừng
 * thọ một cụ 90 tuổi là cùng một thứ.
 *
 * Hai mã cũ vì thế **đổi nghĩa**, không chỉ là có thêm hàng xóm: `MUNG_THO`
 * nay chỉ còn là mừng thọ bậc cao niên, `KHAC` nay chỉ còn là loại khác.
 */
describe("mười hai loại lễ đều có nhãn đọc được", () => {
  const nhan: ReadonlyArray<[EventDto["eventType"], string]> = [
    ["GIO_TO", "Giỗ Tổ"],
    ["GIO_HO", "Giỗ họ"],
    ["GIO_CHI", "Giỗ chi"],
    ["GIO_THUONG", "Giỗ thường"],
    ["TIEU_TUONG", "Tiểu tường"],
    ["DAI_TUONG", "Đại tường"],
    ["CHAP_MA", "Chạp mả"],
    ["MUNG_THO", "Mừng thọ"],
    ["SINH_NHAT", "Sinh nhật"],
    ["KHANH_THANH", "Khánh thành"],
    ["HOP_HO", "Họp họ"],
    ["CUOI_HOI", "Cưới hỏi"],
    ["KHAC", "Việc họ khác"],
  ];

  it.each(nhan)("%s hiện ra là “%s”, không phải MISSING_MESSAGE", (eventType, label) => {
    const { container } = renderWithProviders(
      <EventCard event={giỗ({ eventType, title: null })} />,
      { role: "member" }
    );

    expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    expect(container.textContent).not.toContain("MISSING_MESSAGE");
  });

  it("có đủ nhãn ở cả hai ngôn ngữ", () => {
    // `TIEU_TUONG`/`DAI_TUONG` từng có nhãn ở đây mà KHÔNG có giá trị nào trong
    // ràng buộc CSDL — hai nhãn cho hai thứ không tồn tại. V10 đã đưa chúng vào
    // `ck_event_type`, nên phép đối chiếu này nay có nghĩa ở cả hai phía.
    for (const [eventType] of nhan) {
      renderWithProviders(<EventCard event={giỗ({ eventType, title: null })} />, {
        role: "member",
        locale: "en",
      });
      expect(screen.queryByText(/MISSING_MESSAGE/)).not.toBeInTheDocument();
      cleanup();
    }
  });
});

describe("SINH_NHAT chịu phân tầng chặt hơn MUNG_THO", () => {
  it("khách không thấy sinh nhật của người còn sống", async () => {
    renderWithProviders(<EventsScreen />, { role: "guest" });

    await screen.findByRole("radio", { name: "Danh sách" }, { timeout: 15_000 });
    switchView("Danh sách");
    await screen.findByRole("heading", { name: "Sắp tới" });
    await waitFor(() => {
      expect(screen.queryByText(/Sinh nhật/)).not.toBeInTheDocument();
    });
  });

  it("thành viên thường cũng không thấy, vì ngày diễn ra CHÍNH LÀ ngày sinh", async () => {
    // Đây là điểm khác `MUNG_THO`: mừng thọ là việc của cả họ theo mốc
    // 60/70/80/90 và không nói ra ngày sinh; sinh nhật thì nói. Ngày sinh nằm
    // trong nhóm `birthDetailAndPhoto` do chính chủ bật, và `p-100` đóng nhóm
    // ấy — nên một thành viên khác không được nhận sự kiện này.
    renderWithProviders(<EventsScreen />, { role: "member" });

    await screen.findByRole("radio", { name: "Danh sách" }, { timeout: 15_000 });
    switchView("Danh sách");
    await screen.findByRole("heading", { name: "Sắp tới" });
    await waitFor(() => {
      expect(screen.queryByText(/Sinh nhật Nguyễn Văn An/)).not.toBeInTheDocument();
    });
  });

  it("mừng thọ thì thành viên vẫn thấy — hai mã, hai luật", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    await screen.findByRole("radio", { name: "Danh sách" }, { timeout: 15_000 });
    switchView("Danh sách");

    expect(
      await screen.findByText(/Mừng thọ Nguyễn Văn An/, undefined, { timeout: 15_000 })
    ).toBeInTheDocument();
  });
});

describe("việc họ không gắn với cá nhân nào", () => {
  it("họp họ, khánh thành và cưới hỏi đều tới được màn hình", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    await screen.findByRole("radio", { name: "Danh sách" }, { timeout: 15_000 });
    switchView("Danh sách");
    await screen.findByRole("heading", { name: "Sắp tới" });
    expect((await screen.findAllByText("Họp họ đầu xuân")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Khánh thành tu bổ từ đường").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Lễ cưới con cháu Chi Nhất").length).toBeGreaterThan(0);
  });
});
