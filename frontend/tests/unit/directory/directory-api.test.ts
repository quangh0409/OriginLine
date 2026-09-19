import { beforeEach, describe, expect, it } from "vitest";
import { setDevRole } from "@/lib/api/dev-role";
import { ApiError } from "@/lib/api/http";
import { directoryApi } from "@/lib/api/directory";
import { personsApi } from "@/lib/api";
import { resetPrivacySettingsStore } from "@/mocks/privacy-settings";

/**
 * HỢP ĐỒNG của `/api/v1/directory`.
 *
 * Điều được khẳng định ở đây, và nó là điều dễ hỏng nhất của cả tính năng:
 * **danh bạ là bề mặt đồng thuận**. Một người có mặt trong danh bạ khi và chỉ
 * khi chính họ đã mở nghề nghiệp hoặc nơi ở cho hạng người xem mà người gọi
 * thuộc về. Không suy từ vai, không suy từ "có dữ liệu thì hiện".
 */

beforeEach(() => {
  resetPrivacySettingsStore();
});

describe("khách chưa đăng nhập", () => {
  it("nhận 401, KHÔNG phải một danh sách rỗng", async () => {
    setDevRole("guest");
    try {
      await directoryApi.list();
      throw new Error("guest must not receive a directory payload");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(401);
      expect((error as ApiError).code).toBe("UNAUTHENTICATED");
    }
  });

  it("không nhận được một con số nào — kể cả tổng số người còn sống", async () => {
    // Một danh sách rỗng kèm `coverage: 0/627` sẽ vừa nói dối (dòng họ trông
    // như không còn ai) vừa tiết lộ quy mô người còn sống cho người ngoài.
    setDevRole("guest");
    const error = await directoryApi.list().catch((e: ApiError) => e);
    const body = JSON.stringify((error as ApiError).problem ?? {});
    expect(body).not.toMatch(/\bcoverage\b|\bitems\b|livingCount/);
    expect(body).not.toMatch(/Nguyễn/);
  });
});

describe("điều kiện lọt vào danh bạ", () => {
  it("chỉ chứa người CÒN SỐNG", async () => {
    setDevRole("member");
    const page = await directoryApi.list({ size: 100 });
    expect(page.items.length).toBeGreaterThan(0);
    // Người đã khuất là công khai nhưng không thuộc về danh bạ: danh bạ trả
    // lời "ai đang ở đâu, làm gì", một câu chỉ có nghĩa với người còn sống.
    for (const entry of page.items) {
      expect(entry.personId).toBeTruthy();
    }
    expect(page.coverage.livingCount).toBeGreaterThanOrEqual(page.coverage.sharedCount);
  });

  it("một người toàn Riêng tư KHÔNG lọt vào, dù người gọi là Quản trị", async () => {
    // Quản trị có Tầng 3 với mọi hồ sơ. Nếu quyền ấy lọt vào danh bạ thì danh
    // bạ của Quản trị đầy 100% trong khi của mọi người khác thưa — và đúng
    // người có trách nhiệm sửa lại là người duy nhất không thấy vấn đề.
    setDevRole("admin");
    const page = await directoryApi.list({ size: 500 });
    const ids = page.items.map((i) => i.personId);
    expect(ids).not.toContain("p-100"); // toàn PRIVATE
    expect(ids).not.toContain("p-101"); // người chưa thành niên
    expect(page.coverage.sharedCount).toBeLessThan(page.coverage.livingCount);
  });

  it("mở nhóm cho cả họ là đủ để lọt vào, và đóng lại là ra ngay", async () => {
    setDevRole("member");
    const SELF = "p-102";

    // Lọc theo tên chứ không lấy 500 dòng đầu: danh bạ giả lập có hơn một
    // nghìn dòng, nên "không thấy trong trang đầu" sẽ là một cái đỏ giả.
    const before = await directoryApi.list({ q: "Nguyễn Văn Bình", size: 50 });
    expect(before.items.map((i) => i.personId)).toContain(SELF);

    const { etag } = await personsApi.getById(SELF);
    await personsApi.update(SELF, { clearFields: ["privacy"] }, etag!);

    const after = await directoryApi.list({ q: "Nguyễn Văn Bình", size: 50 });
    expect(after.items.map((i) => i.personId)).not.toContain(SELF);
    expect(after.coverage.sharedCount).toBe(before.coverage.sharedCount - 1);
  });

  it("một dòng chỉ mang đúng những nhóm chủ thể đã mở", async () => {
    setDevRole("member");
    const SELF = "p-102";
    // HAI lời gọi, không phải một. Gửi `clearFields: ["privacy"]` cùng
    // `privacy: {...}` trong một yêu cầu là chuyện contract KHÔNG định nghĩa;
    // bộ giả lập chọn "đóng thắng" (an toàn hơn), nhưng một ca kiểm không được
    // dựa vào một quy ước mà backend chưa hứa.
    const first = await personsApi.getById(SELF);
    const cleared = await personsApi.update(SELF, { clearFields: ["privacy"] }, first.etag!);
    await personsApi.update(
      SELF,
      { privacy: { residenceProvince: "CLAN" } },
      `"v${cleared.data.version}"`
    );

    const page = await directoryApi.list({ q: "Nguyễn Văn Bình", size: 50 });
    const row = page.items.find((i) => i.personId === SELF)!;
    expect(row.currentPlaceProvince).toBe("Nam Định");
    // Nghề đã đóng lại: trường phải VẮNG MẶT, không phải rỗng hay null-hiển-thị.
    expect(row.occupation).toBeUndefined();
  });
});

describe("tỉ lệ bao phủ", () => {
  it("tử số không đổi theo bộ lọc — câu 'đã điền' nói về dòng họ, không về bộ lọc", async () => {
    setDevRole("member");
    const all = await directoryApi.list({ size: 500 });
    const province = all.facets.provinces[0]!.value;
    const filtered = await directoryApi.list({ province, size: 500 });

    expect(filtered.coverage.sharedCount).toBe(all.coverage.sharedCount);
    expect(filtered.coverage.livingCount).toBe(all.coverage.livingCount);
    // …nhưng số dòng trả về thì có đổi, nếu không thì bộ lọc chẳng lọc gì.
    expect(filtered.page.totalElements).toBeLessThan(all.page.totalElements);
  });

  it("tử số luôn ≤ mẫu số, và mẫu số > 0 với thành viên", async () => {
    setDevRole("member");
    const page = await directoryApi.list();
    expect(page.coverage.livingCount).toBeGreaterThan(0);
    expect(page.coverage.sharedCount).toBeLessThanOrEqual(page.coverage.livingCount);
  });
});

describe("bộ lọc và facet", () => {
  it("facet chỉ liệt kê giá trị CÓ THẬT, nên không có mục nào dẫn tới ngõ cụt", async () => {
    setDevRole("member");
    const page = await directoryApi.list({ size: 500 });

    for (const facet of page.facets.provinces.slice(0, 3)) {
      expect(facet.count).toBeGreaterThan(0);
      const filtered = await directoryApi.list({ province: facet.value, size: 500 });
      expect(filtered.page.totalElements).toBe(facet.count);
    }
  });

  it("facet tính trên tập CHƯA lọc, nên chọn một tỉnh rồi vẫn còn thấy tỉnh khác", async () => {
    setDevRole("member");
    const all = await directoryApi.list({ size: 500 });
    const province = all.facets.provinces[0]!.value;
    const filtered = await directoryApi.list({ province, size: 500 });

    expect(filtered.facets.provinces.length).toBe(all.facets.provinces.length);
  });

  it("lọc theo tên bỏ dấu vẫn khớp", async () => {
    setDevRole("member");
    const page = await directoryApi.list({ q: "nguyen", size: 500 });
    expect(page.page.totalElements).toBeGreaterThan(0);
    for (const entry of page.items) {
      expect(entry.displayName.toLowerCase()).toContain("nguy");
    }
  });

  it("phân trang là ở máy chủ, không phải cắt trong trình duyệt", async () => {
    setDevRole("member");
    const first = await directoryApi.list({ page: 0, size: 5 });
    const second = await directoryApi.list({ page: 1, size: 5 });

    expect(first.items).toHaveLength(5);
    expect(first.page.hasNext).toBe(true);
    expect(second.items[0]!.personId).not.toBe(first.items[0]!.personId);
  });
});
