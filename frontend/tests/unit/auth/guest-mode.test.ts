import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/http";
import { eventsApi, notificationsApi, personsApi, treeApi } from "@/lib/api";
import { setDevRole } from "@/lib/api/dev-role";
import { LIVING_MEMBER_FULL, LIVING_MINOR_FULL } from "@/mocks/data";

/**
 * F8 — chế độ khách. Luật quan trọng nhất của cả sản phẩm.
 *
 * BA v2 §10 / Nghị định 13/2023: **khách vãng lai không được thấy BẤT KỲ
 * người còn sống nào**. Người đã khuất là công khai; người sống bị ẩn mặc
 * định và chỉ lộ dần theo tầng T1/T2/T3 cho người đã đăng nhập.
 *
 * Hai điểm tinh tế mà bộ test này bảo vệ:
 *
 *  - Câu trả lời phải là **404, không phải 403**. Một 403 xác nhận rằng hồ sơ
 *    đó CÓ TỒN TẠI, tức là chính thứ mà việc phân tầng sinh ra để giấu. "Ẩn
 *    vì không đủ quyền" phải không phân biệt được với "không có trong gia phả".
 *  - Con số tổng (`page.totalElements`) phải đếm SAU khi lọc. Nếu backend trả
 *    "12 kết quả" rồi chỉ đưa 9 dòng, khách suy ra ngay là có 3 người sống bị
 *    giấu.
 *
 * Test gọi thẳng lớp API trên cùng bộ MSW handler mà ứng dụng chạy thật, nên
 * nó kiểm đúng hình dạng dữ liệu trên dây chứ không phải một bản giả riêng.
 */

const LIVING_ID = LIVING_MEMBER_FULL.id; // p-100, người lớn còn sống
const MINOR_ID = LIVING_MINOR_FULL.id; // p-101, trẻ vị thành niên còn sống
const DECEASED_ROOT_ID = "p-001"; // Thủy tổ, đã khuất -> luôn công khai

describe("khách xem hồ sơ một người còn sống", () => {
  it("nhận 404 chứ không phải 403 — lỗi không được xác nhận người đó có thật", async () => {
    setDevRole("guest");

    const error = (await personsApi.getById(LIVING_ID).catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(error.code).toBe("NOT_FOUND");
    expect(error.status).not.toBe(403);
    expect(error.code).not.toBe("FORBIDDEN");
  });

  it("cũng nhận 404 với trẻ vị thành niên — nhóm phải được giấu kỹ nhất", async () => {
    setDevRole("guest");

    const error = (await personsApi.getById(MINOR_ID).catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(404);
    expect(error.code).toBe("NOT_FOUND");
    // Không một mảnh dữ liệu nào được lọt ra qua thân lỗi.
    expect(JSON.stringify(error.problem ?? {})).not.toContain(
      LIVING_MINOR_FULL.displayName as string
    );
  });

  it("vẫn xem được người đã khuất với đầy đủ chi tiết — người đã khuất là công khai", async () => {
    setDevRole("guest");

    const { data: person } = await personsApi.getById(DECEASED_ROOT_ID);

    expect(person.isAlive).toBe(false);
    expect(person.displayName).toBe("Nguyễn Văn Thủy Tổ");
    expect(person.meta?.visibleTier).toBe("PUBLIC");
  });
});

describe("khách tìm kiếm", () => {
  it("không có một người sống nào trong kết quả, dù tìm đúng tên họ", async () => {
    setDevRole("guest");

    const page = await personsApi.search({ q: "Nguyễn", size: 100 });

    expect(page.items.length).toBeGreaterThan(0);
    expect(page.items.every((p) => p.isAlive === false)).toBe(true);
  });

  it("không trả về người sống ngay cả khi gõ chính xác tên đầy đủ của họ", async () => {
    setDevRole("guest");

    const page = await personsApi.search({ q: LIVING_MEMBER_FULL.displayName!, size: 100 });

    expect(page.items.some((p) => p.id === LIVING_ID)).toBe(false);
  });

  it("đếm tổng SAU khi lọc — con số không được tiết lộ số người bị giấu", async () => {
    // Lấy một trang đủ lớn để chứa hết kết quả, nhờ vậy so được tổng với số dòng thật.
    setDevRole("guest");
    const guestPage = await personsApi.search({ q: "Nguyễn", size: 5000 });

    setDevRole("member");
    const memberPage = await personsApi.search({ q: "Nguyễn", size: 5000 });

    // Tổng của khách bằng đúng số dòng khách nhận được…
    expect(guestPage.page.totalElements).toBe(guestPage.items.length);
    // …và ít hơn tổng của thành viên, vì thành viên còn thấy người sống.
    expect(guestPage.page.totalElements).toBeLessThan(memberPage.page.totalElements);
    expect(guestPage.items.every((p) => p.isAlive === false)).toBe(true);
  });

  it("bộ lọc isAlive=true trong tay khách vẫn ra rỗng, không phải lối vòng", async () => {
    setDevRole("guest");

    const page = await personsApi.search({ q: "Nguyễn", isAlive: true, size: 100 });

    expect(page.items).toHaveLength(0);
    expect(page.page.totalElements).toBe(0);
  });
});

/**
 * Khách đi **đường công khai** (`/api/v1/public/tree`), không phải đường thành
 * viên. Đó là thay đổi đáng kể nhất của bản vá này: trước đây bộ giả lập cho
 * khách đọc luôn `/api/v1/tree` (đã lọc), còn máy chủ thật trả `401` — nên
 * "khách xem được phả đồ" xanh trong test mà đỏ trên hệ thống thật.
 */
describe("khách mở phả đồ", () => {
  it("bản THÀNH VIÊN trả 401 cho khách — và đó là luật chạy đúng, không phải sự cố", async () => {
    setDevRole("guest");

    const error = (await treeApi
      .getTree({ rootId: DECEASED_ROOT_ID, depth: 2 })
      .catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
  });

  it("bản CÔNG KHAI mở được, và có người đã khuất để xem", async () => {
    setDevRole("guest");

    const projection = await treeApi.getPublicTree({ rootId: DECEASED_ROOT_ID, depth: 4 });

    expect(projection.nodes.length).toBeGreaterThan(0);
    expect(projection.nodes.every((n) => n.person.isAlive === false)).toBe(true);
  });

  it("không có nút nào trong phóng chiếu cây là người còn sống", async () => {
    setDevRole("guest");

    const projection = await treeApi.getPublicTree({ rootId: DECEASED_ROOT_ID, depth: 4 });

    expect(projection.nodes.length).toBeGreaterThan(0);
    expect(projection.nodes.every((n) => n.person.isAlive === false)).toBe(true);
  });

  it("không có cạnh nào trỏ tới một nút đã bị lọc bỏ (cạnh mồ côi cũng là rò rỉ)", async () => {
    setDevRole("guest");

    const projection = await treeApi.getPublicTree({ rootId: DECEASED_ROOT_ID, depth: 4 });
    const ids = new Set(projection.nodes.map((n) => n.person.id));

    for (const edge of projection.edges) {
      expect(ids.has(edge.source), `cạnh ${edge.id} trỏ tới nguồn đã bị ẩn`).toBe(true);
      expect(ids.has(edge.target), `cạnh ${edge.id} trỏ tới đích đã bị ẩn`).toBe(true);
    }
  });

  it("lấy cây gốc là một người sống thì nhận 404, không phải cây rỗng", async () => {
    setDevRole("guest");

    const error = (await treeApi
      .getPublicTree({ rootId: LIVING_ID, depth: 2 })
      .catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
  });

  it("bản công khai KHÔNG đổi câu trả lời theo vai người gọi", async () => {
    // Máy chủ thật ép ngữ cảnh Khách trước khi đọc dữ liệu, nên phản hồi giống
    // hệt nhau với mọi người gọi — chính điều đó mới cho phép `Cache-Control:
    // public`. Nếu một ngày nào đó bản công khai bắt đầu nhìn vai, header ấy
    // biến mọi proxy trung gian thành một chỗ rò rỉ.
    setDevRole("guest");
    const asGuest = await treeApi.getPublicTree({ rootId: DECEASED_ROOT_ID, depth: 2 });

    setDevRole("admin");
    const asAdmin = await treeApi.getPublicTree({ rootId: DECEASED_ROOT_ID, depth: 2 });

    expect(asAdmin.nodes.map((n) => n.id)).toEqual(asGuest.nodes.map((n) => n.id));
    expect(asAdmin.nodes.every((n) => n.person.isAlive === false)).toBe(true);
  });
});

describe("khách xem lịch giỗ và hộp thư", () => {
  it("không thấy sự kiện gắn với người còn sống (ví dụ mừng thọ)", async () => {
    setDevRole("guest");

    const page = await eventsApi.list({ upcomingDays: 366, size: 200 });

    expect(page.items.every((e) => (e.person?.isAlive ?? false) === false)).toBe(true);
    expect(page.items.some((e) => e.eventType === "MUNG_THO")).toBe(false);
    // Tổng cũng phải đếm sau lọc.
    expect(page.page.totalElements).toBe(page.items.length);
  });

  it("hộp thư của khách là rỗng — chưa đăng nhập thì không sở hữu hộp thư nào", async () => {
    setDevRole("guest");

    const inbox = await notificationsApi.list({ size: 50 });

    expect(inbox.items).toHaveLength(0);
    expect(inbox.unreadCount).toBe(0);
  });
});

describe("thành viên đã đăng nhập — để chứng minh phép so sánh là có thật", () => {
  it("thấy người còn sống ở tầng T1", async () => {
    setDevRole("member");

    const { data: person } = await personsApi.getById(LIVING_ID);

    expect(person.isAlive).toBe(true);
    expect(person.meta?.visibleTier).toBe("T1");
    expect(person.displayName).toBe(LIVING_MEMBER_FULL.displayName);
  });

  it("nhưng T1 vẫn không kèm dữ liệu tầng 3 (điện thoại, email, địa chỉ đầy đủ)", async () => {
    setDevRole("member");

    const { data: person } = await personsApi.getById(LIVING_ID);

    expect(person.contact).toBeUndefined();
    expect(person.currentPlaceFull).toBeUndefined();
    // Ngày sinh chính xác cũng là tầng 3; T1 không có cả năm sinh.
    expect(person.birth).toBeUndefined();
  });

  it("và người sống có mặt trong kết quả tìm kiếm của thành viên", async () => {
    setDevRole("member");

    const page = await personsApi.search({ q: LIVING_MEMBER_FULL.displayName!, size: 50 });

    expect(page.items.some((p) => p.id === LIVING_ID)).toBe(true);
  });
});
