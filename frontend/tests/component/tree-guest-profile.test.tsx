import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { setDevRole } from "@/lib/api/dev-role";
import { publicPortalApi, toPersonDetail } from "@/lib/api/public-portal";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

/**
 * **KHÁCH BẤM VÀO MỘT NÚT TRÊN PHẢ ĐỒ.**
 *
 * Khách xem được phả đồ công khai, nhưng bấm vào một cụ thì giao diện gọi
 * `GET /persons/{id}` — endpoint nằm sau `authenticated()` — nên nhận `401` và
 * dịch nó thành một dải đỏ "không tải được hồ sơ". Nửa "cổng thông tin dòng họ"
 * của sản phẩm dừng lại ở đúng cú bấm đầu tiên.
 *
 * Hai điều bộ test này giữ:
 *  1. khách đọc được hồ sơ một cụ đã khuất, không phải một dải báo lỗi;
 *  2. `meta` của bản công khai **có thể chưa có** — một agent backend đang thêm
 *     khối ấy — và giao diện phải chạy y nguyên trong cả hai ca.
 */

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/tree",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/tree",
}));

const { TreePersonDrawer } = await import("@/components/tree/tree-person-drawer");

/** Thủy tổ của bộ dữ liệu giả lập — đã khuất, nên công khai với mọi người. */
const DECEASED_ID = "p-001";
/** Người còn sống trong bộ dữ liệu giả lập. */
const LIVING_ID = "p-100";

describe("đổi bản công khai về hình dạng hồ sơ dùng chung", () => {
  it("chịu được khi `meta` CHƯA có — điền mặc định đóng, không đoán rộng hơn", () => {
    const person = toPersonDetail({
      id: DECEASED_ID,
      names: [{ nameType: "HUY", fullName: "Nguyễn Phúc Thuỷ Tổ", isPrimary: true }],
      displayName: "Nguyễn Phúc Thuỷ Tổ",
      isAlive: false,
    });

    expect(person.meta.visibleTier).toBe("PUBLIC");
    expect(person.meta.canEdit).toBe(false);
    expect(person.meta.canDelete).toBe(false);
    expect(person.meta.canRequestCorrection).toBe(false);
  });

  it("dùng `meta` của máy chủ ngay khi nó xuất hiện", () => {
    const person = toPersonDetail({
      id: DECEASED_ID,
      names: [],
      displayName: "Nguyễn Phúc Thuỷ Tổ",
      isAlive: false,
      meta: {
        visibleTier: "PUBLIC",
        canEdit: false,
        canDelete: false,
        canRequestCorrection: false,
        callerRole: "GUEST",
      },
    });

    expect(person.meta.callerRole).toBe("GUEST");
  });

  it("KHÔNG dựng mục quan hệ từ bản công khai — nó chỉ có id, và đếm id là tiết lộ", () => {
    const person = toPersonDetail({
      id: DECEASED_ID,
      names: [],
      displayName: "Nguyễn Phúc Thuỷ Tổ",
      isAlive: false,
      relations: [
        { id: "r-1", fromPersonId: DECEASED_ID, toPersonId: "p-010", relType: "PARENT_BIO" },
      ],
    });

    expect(person.relationships).toBeUndefined();
  });

  it("KHÔNG biến `avatarKey` thành `avatarUrl` — đó là khoá MinIO, không phải URL", () => {
    const person = toPersonDetail({
      id: DECEASED_ID,
      names: [],
      displayName: "Nguyễn Phúc Thuỷ Tổ",
      isAlive: false,
      avatarKey: "portraits/p-001.jpg",
    });

    expect(person.avatarUrl).toBeUndefined();
  });
});

describe("cổng công khai giữ nguyên tính không tiết lộ", () => {
  it("hỏi một người còn sống nhận 404, không phân biệt được với id bịa đặt", async () => {
    setDevRole("guest");

    const living = await publicPortalApi.getPerson(LIVING_ID).catch((e) => e);
    const invented = await publicPortalApi.getPerson("khong-ton-tai").catch((e) => e);

    expect(living.status).toBe(404);
    expect(invented.status).toBe(404);
    expect(living.problem?.code).toBe(invented.problem?.code);
    expect(living.problem?.title).toBe(invented.problem?.title);
  });

  it("phản hồi giống hệt nhau dù người gọi mang vai gì — nếu không `Cache-Control: public` là lỗ rò", async () => {
    setDevRole("guest");
    const asGuest = await publicPortalApi.getPerson(DECEASED_ID);
    setDevRole("admin");
    const asAdmin = await publicPortalApi.getPerson(DECEASED_ID);

    expect(asAdmin).toEqual(asGuest);
  });
});

describe("ngăn hồ sơ mở từ phả đồ", () => {
  it("khách đọc được hồ sơ một cụ đã khuất, không phải một dải báo lỗi", async () => {
    setDevRole("guest");

    renderWithProviders(
      <TreePersonDrawer personId={DECEASED_ID} audience="public" onClose={() => undefined} />,
      { role: "guest" }
    );

    await waitFor(
      () => expect(screen.getByTestId("public-person-profile")).toBeInTheDocument(),
      { timeout: 10000 }
    );

    // Tên hiện ra thật — không phải một khung rỗng. Thủy tổ của bộ dữ liệu giả
    // lập là "Nguyễn Văn Thủy Tổ" (xem src/mocks/tree-graph/build-graph.ts).
    expect(screen.getAllByText(/Nguyễn Văn Thủy Tổ/).length).toBeGreaterThan(0);
    // Không có chữ nào gợi ý "có dữ liệu bạn không được xem": hồ sơ này ĐẦY ĐỦ
    // đúng như nó tồn tại trên bề mặt công khai.
    expect(document.body.textContent ?? "").not.toMatch(/không tải được|bị ẩn|không có quyền/i);
  });
});
