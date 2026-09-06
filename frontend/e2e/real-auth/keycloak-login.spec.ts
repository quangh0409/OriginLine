import { expect, test, type Page } from "@playwright/test";

/**
 * F8 — MỘT lượt đăng nhập THẬT, đi hết vòng: giao diện → Keycloak → backend →
 * PostgreSQL, rồi quay về màn hình.
 *
 * Đây là bài kiểm chặn nghiệm thu Giai đoạn 1. Mọi bộ E2E khác chạy trên MSW,
 * nghĩa là chúng chứng minh giao diện đúng chứ KHÔNG chứng minh hai nửa hệ
 * thống nói được chuyện với nhau. Bài này là bài duy nhất làm việc đó, và vì
 * thế nó cố tình phụ thuộc vào hạ tầng sống (xem README §Tests).
 *
 * Không dùng `signInAs()` của `e2e/fixtures.ts`: hàm ấy ghi `localStorage` cho
 * cơ chế giả lập vai của MSW, thứ KHÔNG được phép tồn tại ở bản chạy này.
 * Vai trò ở đây tới từ claim `realm_access.roles` của token thật.
 */

const USERNAME = process.env.E2E_USERNAME ?? "admin.giapha";
const PASSWORD = process.env.E2E_PASSWORD ?? "giapha123";

/** Tên thủy tổ trong bộ dữ liệu demo (`demo/`, 1506 nhân khẩu, 7 đời). */
const ROOT_PERSON_NAME = "Nguyễn Đình Bách";

const SHOTS = ".playwright-mcp/real-auth";

/**
 * Điền form đăng nhập của Keycloak.
 *
 * Nhắm theo `#username`/`#password` — id của theme mặc định Keycloak — chứ
 * không theo nhãn: trang ấy đổi ngôn ngữ theo `Accept-Language` của trình
 * duyệt, mà context này chạy `vi-VN`.
 */
async function signInThroughKeycloak(page: Page): Promise<void> {
  await page.waitForURL(/:8081\/realms\/giapha\/protocol\/openid-connect\/auth/, {
    timeout: 60_000,
  });
  await page.locator("#username").fill(USERNAME);
  await page.locator("#password").fill(PASSWORD);
  await page.screenshot({ path: `${SHOTS}/02-keycloak-form.png`, fullPage: true });
  await page.locator("#kc-login, input[type=submit]").first().click();
  // Quay lại ứng dụng; `redirectUri` là chính trang đang đứng lúc bấm.
  await page.waitForURL(/localhost:3000/, { timeout: 60_000 });
}

test("khách mở được ứng dụng, đăng nhập thật, thấy nhân khẩu thật, rồi đăng xuất sạch", async ({
  page,
}) => {
  const failedRequests: string[] = [];
  page.on("response", (response) => {
    if (response.url().includes("/api/v1/") && response.status() >= 500) {
      failedRequests.push(`${response.status()} ${response.url()}`);
    }
  });

  // Cơ chế giả lập vai KHÔNG được phép lọt vào một bản chạy nói chuyện với
  // backend thật. `dev-role.ts` tự dặn như vậy; đây là chỗ chứng minh, chứ
  // không phải chỗ tin lời.
  const mockRoleLeaks: string[] = [];
  /** Các lời gọi API có mang `Authorization` — dùng để chắc chắn token đã đi trên dây. */
  const authorizedCalls: string[] = [];
  page.on("request", (request) => {
    if (!request.url().includes("/api/v1/")) return;
    const headers = request.headers();
    if (headers["x-mock-role"] !== undefined) {
      mockRoleLeaks.push(`${request.url()} -> x-mock-role: ${headers["x-mock-role"]}`);
    }
    if (headers["authorization"]?.startsWith("Bearer ")) {
      authorizedCalls.push(request.url());
    }
  });

  // ---------------------------------------------------------------- KHÁCH ---
  // BA v2 §10: khách chưa đăng nhập PHẢI mở được cổng thông tin. Không trắng
  // trang, không vòng lặp chuyển hướng sang Keycloak (đó là lý do `init()`
  // dùng `check-sso` chứ không phải `login-required`).
  await page.goto("/vi/tree");

  await expect(page.getByRole("banner")).toBeVisible();
  await expect(page.getByTestId("login-button")).toBeVisible();
  // Vẫn ở trên ứng dụng, không bị đá sang trang đăng nhập của Keycloak.
  expect(page.url()).toContain("localhost:3000");

  // Backend trả 401 cho khách (mọi endpoint nhân khẩu đều đòi token). Màn
  // hình phải là LỜI MỜI ĐĂNG NHẬP chứ không phải "Không tải được": với
  // khách, 401 là luật riêng tư đang chạy đúng, không phải sự cố.
  await expect(page.getByText("Xin đăng nhập để xem phả đồ")).toBeVisible({
    timeout: 60_000,
  });
  await expect(page.getByText("Không tải được cây phả đồ")).toHaveCount(0);
  await page.screenshot({ path: `${SHOTS}/01-guest.png`, fullPage: true });

  // ------------------------------------------------------------ ĐĂNG NHẬP ---
  await page.getByTestId("login-button").click();
  await signInThroughKeycloak(page);

  // Tên người đang đăng nhập hiện trên thanh đầu trang.
  await expect(page.getByTestId("account-button")).toBeVisible({ timeout: 60_000 });

  // --------------------------------------------- NHÂN KHẨU THẬT TỪ CSDL ---
  // Thủy tổ của bộ demo, kèm dấu tiếng Việt đầy đủ. Không một fixture nào của
  // frontend chứa tên này — nó chỉ có thể tới từ PostgreSQL.
  const rootNode = page
    .getByTestId("person-node-name")
    .filter({ hasText: ROOT_PERSON_NAME });
  await expect(rootNode.first()).toBeVisible({ timeout: 60_000 });

  // Và cây phải có nhiều hơn một người: một nút lẻ có thể là trạng thái lỗi.
  const nodeNames = page.getByTestId("person-node-name");
  await expect.poll(() => nodeNames.count(), { timeout: 60_000 }).toBeGreaterThan(1);

  await page.screenshot({ path: `${SHOTS}/03-tree-real-data.png`, fullPage: true });

  // ------------------------------------ ĐIỂM CUỐI CẦN QUYỀN: 200, KHÔNG 401 ---
  // Không tự chế lời gọi: quan sát chính lời gọi mà ứng dụng phát ra, để thứ
  // được kiểm là header do `src/lib/api/http.ts` gắn chứ không phải header do
  // bài kiểm gắn. `/api/v1/notifications` là hộp thư của riêng người đang
  // đăng nhập — cố ý không có tham số userId, nên nó BẮT BUỘC cần token.
  const notificationStatuses: number[] = [];
  page.on("response", (response) => {
    if (response.url().includes("/api/v1/notifications")) {
      notificationStatuses.push(response.status());
    }
  });

  await page.goto("/vi/notifications");
  await expect
    .poll(() => notificationStatuses.length, { timeout: 60_000 })
    .toBeGreaterThan(0);

  expect(
    notificationStatuses,
    "hộp thư phải trả 200 với token thật, không phải 401"
  ).toContain(200);
  expect(notificationStatuses).not.toContain(401);

  // Quay lại phả đồ để phần đăng xuất chạy trên đúng màn hình đã chụp ảnh.
  await page.goto("/vi/tree");
  await expect(page.getByTestId("account-button")).toBeVisible({ timeout: 60_000 });

  // ---------------------------------------------------------- ĐĂNG XUẤT ---
  await page.getByTestId("account-button").click();
  await page.getByText("Đăng xuất", { exact: true }).click();

  await expect(page.getByTestId("login-button")).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId("account-button")).toHaveCount(0);
  await page.screenshot({ path: `${SHOTS}/04-after-logout.png`, fullPage: true });

  expect(failedRequests, "không endpoint nào được trả 5xx").toEqual([]);
  expect(
    mockRoleLeaks,
    "header giả lập vai x-mock-role lọt vào bản chạy backend thật"
  ).toEqual([]);
  expect(
    authorizedCalls.length,
    "phải có lời gọi API mang Authorization: Bearer sau khi đăng nhập"
  ).toBeGreaterThan(0);
});
