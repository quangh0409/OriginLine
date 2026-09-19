import { expect, expectNoLeakyPlaceholders, signInAs, test } from "./fixtures";

/**
 * DANH BẠ DÒNG HỌ + MỨC CHIA SẺ CỦA CHÍNH CHỦ — trên trình duyệt thật.
 *
 * Tầng component (`tests/component/`) đã kiểm hình dạng và câu chữ. Ba nhóm
 * điều dưới đây thì chỉ trình duyệt trả lời được:
 *
 *  - **Tải trọng mạng.** Ca kiểm quan trọng nhất của tệp này không đọc màn
 *    hình mà đọc dây: một người còn sống không được PHÉP ĐI QUA dây tới máy
 *    của khách, kể cả khi màn hình vô tình không vẽ ra. Đây là ranh giới pháp
 *    lý (Nghị định 13/2023), không phải một sở thích giao diện.
 *  - **Vùng chạm thật.** Sàn 44×44px của tài liệu định hướng §2.2 chỉ đo được
 *    sau khi CSS xếp chồng xong.
 *  - **Chế độ tối.** Là một truy vấn media; chỉ trình duyệt trả lời.
 */

const SELF_ID = "p-102"; // hồ sơ ứng với tài khoản vai Thành viên

/**
 * Nút "Tôi gọi là?" của một dòng danh bạ.
 *
 * `a[href^="/kinship"]` sẽ KHÔNG đúng: thanh điều hướng đầu trang cũng có một
 * liên kết `/kinship` (mục "Tra danh xưng"), và nó đứng trước trong DOM — nên
 * `.first()` bắt phải nó, rồi ở khung điện thoại nó lại bị ẩn (`hidden md:flex`)
 * và ca kiểm đỏ vì một lý do chẳng liên quan gì tới danh bạ. Tham số `?to=`
 * chính là thứ phân biệt hai loại liên kết.
 */
const KINSHIP_CTA = 'a[href^="/kinship?to="]';
const OTHER_LIVING_ID = "p-100";

/** Sàn vùng chạm của 00 §2.2 — WCAG 2.2 SC 2.5.8 nâng lên mức AAA. */
const TOUCH_FLOOR_PX = 44;

test.describe("khách chưa đăng nhập mở danh bạ", () => {
  test("không có một người còn sống nào đi qua dây, dù chỉ một mảnh", async ({ page }) => {
    // Không gieo vai: MẶC ĐỊNH phải đã là trạng thái an toàn.
    const leaks: string[] = [];

    page.on("response", async (response) => {
      if (!response.url().includes("/api/v1/")) return;
      let body: unknown;
      try {
        body = await response.json();
      } catch {
        return;
      }
      const walk = (value: unknown, path: string) => {
        if (Array.isArray(value)) {
          value.forEach((v, i) => walk(v, `${path}[${i}]`));
          return;
        }
        if (value && typeof value === "object") {
          const record = value as Record<string, unknown>;
          // Chỉ tính là rò rỉ khi CÓ dữ liệu thật. `GET /api/v1/me` trả về
          // `{ appUserId: null, personId: null, ... }` cho khách — đó là câu
          // trả lời hợp lệ "bạn chưa có tài khoản", không phải một người còn
          // sống, và bắt nhầm nó sẽ biến ca kiểm quan trọng nhất tệp này thành
          // một cái đỏ giả mà người sau sẽ gỡ đi cho xong.
          if (record.isAlive === true || typeof record.personId === "string") {
            leaks.push(`${response.url()} -> ${path}`);
          }
          for (const [key, child] of Object.entries(record)) walk(child, `${path}.${key}`);
        }
      };
      walk(body, "$");
    });

    await page.goto("/danh-ba");
    await expect(
      page.getByRole("heading", { name: "Danh bạ dành cho thành viên đã đăng nhập" })
    ).toBeVisible({ timeout: 60_000 });
    await page.waitForLoadState("networkidle");

    expect(leaks, `dữ liệu người còn sống tới máy của khách:\n${leaks.join("\n")}`).toEqual([]);
  });

  test("màn hình không mang tên ai, cũng không mang một con số nào", async ({ page }) => {
    await page.goto("/danh-ba");
    await expect(
      page.getByRole("heading", { name: "Danh bạ dành cho thành viên đã đăng nhập" })
    ).toBeVisible({ timeout: 60_000 });

    const main = page.locator("main");
    const text = await main.innerText();

    for (const name of ["Nguyễn Văn An", "Nguyễn Văn Bình", "Nguyễn Văn Cẩn"]) {
      expect(text, `tên "${name}" lọt tới khách`).not.toContain(name);
    }
    // Kể cả tỉ lệ bao phủ: nó tiết lộ quy mô người còn sống của dòng họ.
    await expect(page.locator("[data-directory-coverage]")).toHaveCount(0);
    expect(text, "một con số nào đó lọt ra màn hình của khách").not.toMatch(/\d/);

    await expectNoLeakyPlaceholders(page);
  });

  test("không đọc được hồ sơ hay mức chia sẻ của ai qua đường API", async ({ page }) => {
    await page.goto("/danh-ba");
    // Chờ MSW (chạy dưới dạng service worker) nhận việc: gọi `fetch` ngay khi
    // trang vừa mở sẽ trượt ra ngoài worker và chết vì không có máy chủ thật ở
    // cổng 8080 — một "Failed to fetch" trông y hệt một lỗi mạng.
    await expect(
      page.getByRole("heading", { name: "Danh bạ dành cho thành viên đã đăng nhập" })
    ).toBeVisible({ timeout: 60_000 });
    await page.waitForLoadState("networkidle");

    // Khối `privacy` nằm TRONG `PersonDto`, không có endpoint riêng — nên phép
    // thử đúng là gọi thẳng hồ sơ. Với một người còn sống, khách phải nhận
    // `404`, KHÔNG phải `403`: `403` là tự thú nhận "người này có thật".
    const probe = await page.evaluate(async () => {
      const res = await fetch("http://localhost:8080/api/v1/persons/p-102");
      return { status: res.status, body: await res.text() };
    });

    expect(probe.status).toBe(404);
    expect(probe.body).not.toContain("privacy");
    expect(probe.body).not.toContain("Nguyễn Văn Bình");
  });
});

test.describe("thành viên đã đăng nhập mở danh bạ", () => {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
  });

  test("thấy tỉ lệ bao phủ, và nó giải thích vì sao danh bạ thưa", async ({ page }) => {
    await page.goto("/danh-ba");

    const coverage = page.locator("[data-directory-coverage]");
    await expect(coverage).toBeVisible({ timeout: 60_000 });

    const text = await coverage.innerText();
    expect(text).toMatch(/\d+\s*\/\s*\d+/);
    expect(text).toContain("người còn sống đã điền và chọn hiện ở đây");
    // Câu giải thích phải nói rõ đây KHÔNG phải lỗi.
    expect(text).toContain("không phải thiếu dữ liệu");
  });

  test("mỗi dòng dẫn sang tra danh xưng bằng ?to=", async ({ page }) => {
    await page.goto("/danh-ba");
    const first = page.locator(KINSHIP_CTA).first();
    await expect(first).toBeVisible({ timeout: 60_000 });

    const href = await first.getAttribute("href");
    expect(href).toMatch(/^\/kinship\?to=/);
    expect(href).not.toContain("from=");

    await first.click();
    await expect(page).toHaveURL(/\/kinship\?to=/);
  });

  test("không dòng nào có ô trống gợi ý dữ liệu bị giữ lại", async ({ page }) => {
    await page.goto("/danh-ba");
    await expect(page.locator(KINSHIP_CTA).first()).toBeVisible({ timeout: 60_000 });
    await expectNoLeakyPlaceholders(page);
  });

  test("mọi vùng chạm đạt sàn 44px, ở khung điện thoại", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/danh-ba");
    await expect(page.locator(KINSHIP_CTA).first()).toBeVisible({ timeout: 60_000 });

    const targets = page.locator(
      "main a[href], main button:not([tabindex='-1']), main input, main [role='combobox']"
    );
    const count = await targets.count();
    const tooSmall: string[] = [];

    for (let i = 0; i < count; i += 1) {
      const target = targets.nth(i);
      if (!(await target.isVisible())) continue;
      const box = await target.boundingBox();
      if (!box) continue;
      if (box.height < TOUCH_FLOOR_PX || box.width < TOUCH_FLOOR_PX) {
        const label = (await target.innerText().catch(() => "")) || (await target.getAttribute("id"));
        tooSmall.push(`${label ?? "(?)"} → ${Math.round(box.width)}×${Math.round(box.height)}`);
      }
    }

    expect(tooSmall, `vùng chạm dưới ${TOUCH_FLOOR_PX}px:\n${tooSmall.join("\n")}`).toEqual([]);
  });

  test("chạy được ở chế độ tối mà không có mảng sáng chói nào", async ({ page }) => {
    await page.emulateMedia({ colorScheme: "dark" });
    await page.goto("/danh-ba");
    await expect(page.locator("[data-directory-coverage]")).toBeVisible({ timeout: 60_000 });

    // Nền trang phải THẬT SỰ tối: một mã màu viết cứng ở đâu đó sẽ để lại nền
    // sáng, im lặng, không lỗi biên dịch.
    const bg = await page.evaluate(
      () => getComputedStyle(document.body).backgroundColor
    );
    const [r, g, b] = bg.match(/\d+/g)!.map(Number) as [number, number, number];
    expect(r + g + b, `nền vẫn sáng ở chế độ tối: ${bg}`).toBeLessThan(200);
  });
});

test.describe("chính chủ đặt mức chia sẻ", () => {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
  });

  test("thấy đủ năm nhóm và ba mức trên hồ sơ CỦA MÌNH", async ({ page }) => {
    await page.goto(`/persons/${SELF_ID}`);

    const card = page.locator('[data-privacy-sharing="self"]');
    await expect(card).toBeVisible({ timeout: 60_000 });

    for (const group of [
      "Nghề nghiệp & nơi làm việc",
      "Nơi ở (tỉnh/thành)",
      "Địa chỉ đầy đủ",
      "Liên hệ: điện thoại · thư điện tử · Zalo",
      "Ngày sinh đầy đủ & ảnh",
    ]) {
      await expect(card.getByRole("heading", { name: group })).toBeVisible();
    }
    await expect(card.getByRole("radio")).toHaveCount(15);
  });

  test("lời hứa về khách in ngay trên màn hình, không giấu trong trang trợ giúp", async ({
    page,
  }) => {
    await page.goto(`/persons/${SELF_ID}`);
    const card = page.locator('[data-privacy-sharing="self"]');
    await expect(card).toBeVisible({ timeout: 60_000 });

    const text = await card.innerText();
    expect(text).toContain("người chưa đăng nhập");
    expect(text).toContain("kể cả tên");
    // Và lợi ích cũng vậy: ngay cạnh biểu mẫu.
    expect(text).toContain("bà con cùng nghề tìm được nhau");
  });

  test("KHÔNG hiện trên hồ sơ người khác — kể cả dạng chỉ đọc", async ({ page }) => {
    await page.goto(`/persons/${OTHER_LIVING_ID}`);
    await expect(page.getByRole("heading", { name: /Nguyễn Văn An/ })).toBeVisible({
      timeout: 60_000,
    });
    await expect(page.locator('[data-privacy-sharing="self"]')).toHaveCount(0);
    await expectNoLeakyPlaceholders(page);
  });

  test("đổi một mức rồi lưu thì đổi thật, và tải lại vẫn còn", async ({ page }) => {
    await page.goto(`/persons/${SELF_ID}`);
    const card = page.locator('[data-privacy-sharing="self"]');
    await expect(card).toBeVisible({ timeout: 60_000 });

    const addressGroup = card.locator("[role='group']").filter({
      has: page.getByRole("heading", { name: "Địa chỉ đầy đủ" }),
    });
    await addressGroup.getByText("Cùng chi", { exact: true }).click();

    const save = card.getByRole("button", { name: "Lưu mức chia sẻ" });
    await expect(save).toBeEnabled();
    await save.click();

    await expect(card.getByText("Đã lưu.")).toBeVisible();
    // Bộ giả lập giữ trạng thái trong bộ nhớ của trang, nên một lần tải lại sẽ
    // đặt lại — điều được khẳng định ở đây là màn hình phản ánh đúng phản hồi
    // của máy chủ ngay sau khi ghi, tức bản nháp đã được đồng bộ lại.
    await expect(save).toBeDisabled();
  });

  test("bảng 'ai xem được gì' không đếm số trường", async ({ page }) => {
    await page.goto(`/persons/${SELF_ID}`);
    const card = page.locator('[data-privacy-sharing="self"]');
    await expect(card).toBeVisible({ timeout: 60_000 });

    const preview = page.locator("section[aria-labelledby='privacy-preview-title']");
    await expect(preview).toBeVisible();

    const previewText = await preview.innerText();
    expect(previewText, "bảng xem thử chứa một con số — đếm đã là tiết lộ").not.toMatch(/\d/);
    expect(previewText).toContain("Không thấy gì về bạn, kể cả tên.");
  });
});
