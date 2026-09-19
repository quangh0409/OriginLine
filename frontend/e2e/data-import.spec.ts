import type { Page } from "@playwright/test";
import { expect, signInAs, test } from "./fixtures";

/**
 * ĐƯỜNG ỐNG NHẬP LIỆU BAN ĐẦU — năm màn, một mạch.
 *
 * <h2>Vì sao ca kiểm này phải là E2E chứ không phải test thành phần</h2>
 * Hai thứ chỉ tồn tại trong một trình duyệt thật:
 * <ul>
 *   <li><b>multipart thật.</b> Dưới `jsdom`, `FormData` là bản của jsdom còn
 *       `fetch` là bản của undici, nên `Content-Type: multipart/form-data`
 *       không bao giờ được đặt và `request.formData()` phía máy chủ giả ném
 *       ngay. Ở đây `setInputFiles` đưa một tệp thật vào một `<input>` thật.</li>
 *   <li><b>"đóng trình duyệt rồi quay lại".</b> Nó chỉ có nghĩa khi có một ngữ
 *       cảnh trình duyệt thật để đóng.</li>
 * </ul>
 *
 * <h2>Tệp `.xlsx` ở đây là tệp giả, và bộ giả lập chọn kịch bản theo TÊN tệp</h2>
 * Bốn byte đầu là chữ ký ZIP thật (`PK\x03\x04`) — đúng thứ backend dùng để
 * nhận diện định dạng. Nội dung còn lại là rác: bộ giả lập không đọc Excel, nó
 * đọc tên tệp. Backend thật thì đọc nội dung bằng Apache POI.
 *
 *   `*sach*`    → 0 lỗi chặn, 0 cảnh báo, 0 cặp nghi trùng (ca DUY NHẤT duyệt được hôm nay)
 *   `*da-sua*`  → 0 lỗi chặn, còn cảnh báo và cặp nghi trùng
 *   khác        → 6 lỗi chặn + 11 cảnh báo + 3 cặp nghi trùng
 */

const XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

/**
 * Khoá của ba cặp nghi trùng giả — **UUID cố định**, sao y `src/mocks/data-import.ts`.
 *
 * Chép tay ở đây thay vì `import`: spec E2E chạy ngoài `tsconfig` của ứng dụng
 * nên không có alias `@/`, và một khoá đổi mỗi lần chạy sẽ biến ca kiểm thành
 * ca kiểm dò theo thứ tự hiển thị — đúng thứ vỡ ngay khi máy chủ đổi cách xếp.
 */
const PAIR_TREE_VISIBLE = "7a1d4c60-0000-4000-8000-000000000012";
const PAIR_TREE_HIDDEN = "7a1d4c60-0000-4000-8000-000000000019";
const PAIR_IN_FILE = "7a1d4c60-0000-4000-8000-000000000026";

/** Bốn byte chữ ký ZIP + một chuỗi rác — đủ để đi hết đường multipart. */
function fakeWorkbook(name: string) {
  return {
    name,
    mimeType: XLSX_MIME,
    buffer: Buffer.concat([
      Buffer.from([0x50, 0x4b, 0x03, 0x04]),
      Buffer.from(`giapha-e2e:${name}`, "utf8"),
    ]),
  };
}

async function pickAndSubmit(page: Page, fileName: string): Promise<void> {
  await page.getByRole("radio", { name: /Chi Nhất/i }).check();
  await page.locator("#import-file-input").setInputFiles(fakeWorkbook(fileName));
  await expect(page.getByText(new RegExp(`Đã chọn: ${fileName}`, "i"))).toBeVisible();
  await page.getByRole("button", { name: /^Tải lên và đối soát$/ }).click();
}

async function uploadWorkbook(page: Page, fileName: string): Promise<void> {
  await page.goto("/nhap-lieu/tai-len");
  await expect(page.getByRole("heading", { name: /Tải tệp lên/i })).toBeVisible({
    timeout: 60_000,
  });
  await pickAndSubmit(page, fileName);
  await page.waitForURL(/\/nhap-lieu\/2026-\d+$/, { timeout: 60_000 });
}

test.beforeEach(async ({ page }) => {
  await signInAs(page, "branch-head");
});

test("màn dẫn nhập cho biết quy trình năm bước và chưa mời làm tiếp khi chưa có lô nào", async ({
  page,
  consoleErrors,
}) => {
  await page.goto("/nhap-lieu");

  await expect(page.getByRole("heading", { name: /Nhập liệu ban đầu/i })).toBeVisible({
    timeout: 60_000,
  });
  await expect(page.getByText(/Không có thao tác nào ở đây làm hỏng phả/i)).toBeVisible();
  await expect(page.getByRole("navigation", { name: /Quy trình năm bước/i })).toBeVisible();
  // Mảng rỗng ở đây có đúng một nghĩa và nghĩa ấy an toàn: người gọi thật sự
  // chưa có lô nào. Chi ngoài phạm vi không rơi vào đây — nó ra `403`.
  await expect(page.getByTestId("resume-batch-link")).toHaveCount(0);

  expect(consoleErrors.filter((e) => e.includes("MISSING_MESSAGE"))).toEqual([]);
});

test("mẫu Excel tải về được, và tệp về máy đúng là một tệp riêng của chi", async ({ page }) => {
  await page.goto("/nhap-lieu/mau");
  await expect(page.getByRole("heading", { name: /Mẫu Excel cho từng chi/i })).toBeVisible({
    timeout: 60_000,
  });
  // Không dạy một quy tắc tiền tố mã không tồn tại; cái hiện ra là `ltree`.
  await expect(page.getByText(/Tiền tố mã/i)).toHaveCount(0);
  await expect(page.getByText("root.chi_nhat")).toBeVisible();

  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("button", { name: /Tải mẫu của Chi Nhất/i }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/Chi-Nh/i);
});

test("tải một tệp bẩn lên: hai nhóm lỗi tách bạch, chưa ghi gì vào phả, nút duyệt khoá", async ({
  page,
}) => {
  await uploadWorkbook(page, "chi-nhat.xlsx");

  await expect(page.getByText(/Chưa có gì được ghi vào phả/i)).toBeVisible();

  const blocking = page.getByRole("region", { name: /lỗi phải sửa/i });
  const warning = page.getByRole("region", { name: /cần xem lại/i });
  await expect(blocking.getByText(/6 lỗi phải sửa/i)).toBeVisible();
  await expect(warning.getByRole("heading", { name: /11 mục cần xem lại/i })).toBeVisible();
  // Không bao giờ gộp thành một con số làm người nhập tưởng hỏng cả tệp.
  await expect(page.getByText(/17 vấn đề/i)).toHaveCount(0);

  // Mỗi dòng chỉ đúng TRANG và DÒNG trong tệp Excel.
  await expect(blocking.getByText(/Nhân khẩu · dòng 37/i)).toBeVisible();
  // Lỗi của cả lô không in ra một số dòng không tồn tại.
  await expect(blocking.getByText("Cả lô")).toBeVisible();
  await expect(blocking.getByText(/undefined/i)).toHaveCount(0);
  // Gợi ý mã gần giống: chi tiết nhỏ biến một lỗi bí hiểm thành một cú sửa.
  await expect(blocking.getByText("AT-04-003")).toBeVisible();

  await expect(page.getByRole("button", { name: /Duyệt và ghi .* vào phả/i })).toBeDisabled();
  await expect(page.getByTestId("commit-blockers").getByText(/6 lỗi/i)).toBeVisible();
});

test("mã HTTP đúng chỗ: tệp quá lớn ra một câu khác hẳn tệp sai cấu trúc", async ({ page }) => {
  await page.goto("/nhap-lieu/tai-len");
  await expect(page.getByRole("heading", { name: /Tải tệp lên/i })).toBeVisible({
    timeout: 60_000,
  });

  // 413 — "tệp quá lớn": việc cần làm là bỏ ảnh nhúng ra.
  await pickAndSubmit(page, "chi-nhat-qua-lon.xlsx");
  await expect(page.getByText(/Giới hạn là 10 MB/i)).toBeVisible({ timeout: 30_000 });

  // 422 — "tệp đọc được nhưng cấu trúc sai": việc cần làm là dùng đúng mẫu.
  // Khác 400 ở chỗ đó, và hai câu phải khác nhau.
  await pickAndSubmit(page, "chi-nhat-thieu-trang.xlsx");
  await expect(page.getByText(/thiếu trang Nhân khẩu/i)).toBeVisible({ timeout: 30_000 });

  // Và không lô nào được tạo: vẫn đứng nguyên ở màn tải lên.
  expect(page.url()).toMatch(/\/nhap-lieu\/tai-len$/);
});

test("sửa tệp rồi tải lại: lô mới sạch lỗi, lô cũ nói rõ nó đã bị thay", async ({ page }) => {
  await uploadWorkbook(page, "chi-nhat.xlsx");
  const dirtyUrl = page.url();

  // Đúng lối đi thật: bấm "Tải lại tệp đã sửa" ngay trong khối lỗi chặn.
  await page.getByRole("button", { name: /Tải lại tệp đã sửa/i }).click();
  await page.waitForURL(/\/nhap-lieu\/tai-len$/, { timeout: 60_000 });

  await pickAndSubmit(page, "chi-nhat-da-sua.xlsx");
  await page.waitForURL(/\/nhap-lieu\/2026-\d+$/, { timeout: 60_000 });

  expect(page.url()).not.toBe(dirtyUrl);
  await expect(page.getByText(/Không còn lỗi phải sửa/i)).toBeVisible();
  await expect(page.getByText(/Chưa có gì được ghi vào phả/i)).toBeVisible();

  // Lô cũ không biến mất; nó tự giải thích tình trạng của mình.
  await page.goto(dirtyUrl);
  await expect(page.getByText(/đã được thay bằng lần tải lên mới hơn/i)).toBeVisible({
    timeout: 60_000,
  });
});

test("đối chiếu nghi trùng: bên phả chỉ có một khoá, và ca không được xem là ca bình thường", async ({
  page,
}) => {
  await uploadWorkbook(page, "chi-nhat-da-sua.xlsx");

  await page.getByRole("link", { name: /Xem 3 cặp nghi trùng/i }).click();
  await page.waitForURL(/\/trung$/, { timeout: 60_000 });
  await expect(
    page.getByText(/Hệ thống chỉ xếp thứ tự xem xét. Quyết định là của bạn/i)
  ).toBeVisible();
  // Ngưỡng do MÁY CHỦ công bố, không ghi cứng trong giao diện.
  await expect(page.getByText(/từ 70 điểm trở lên/i)).toBeVisible();

  // Cặp có bên phả KHÔNG được xem: đọc ra "bạn không có quyền", không phải
  // "không tìm thấy", và không rò rỉ việc bản ghi có tồn tại hay không.
  const blocked = page.locator(`[data-testid="duplicate-pair-${PAIR_TREE_HIDDEN}"]`);
  await expect(blocked.getByTestId("tree-party-not-visible")).toBeVisible();
  await expect(blocked.getByText(/không có quyền xem người này/i)).toBeVisible();
  await expect(blocked.getByText(/Hội đồng Tộc biểu/i)).toBeVisible();

  // Cặp TREE không có bảng bằng chứng — bất biến riêng tư, nói ra thay vì để trống.
  await expect(blocked.getByTestId("evidence-withheld")).toBeVisible();

  // Cặp hai dòng trong CÙNG một tệp thì có đủ bảng, và ngày giỗ đứng đầu.
  const inFile = page.locator(`[data-testid="duplicate-pair-${PAIR_IN_FILE}"]`);
  const rows = await inFile.locator('[data-testid^="evidence-"]').all();
  const order = await Promise.all(rows.map((row) => row.getAttribute("data-testid")));
  expect(order[0]).toBe("evidence-DEATH_LUNAR");
  expect(order[order.length - 1]).toBe("evidence-BIRTH_YEAR");

  // Cặp TREE vẫn nói được VÌ SAO NGHI — qua `signals`, ô duy nhất nó được phép
  // có. Đọc `hint` ở đây thì ô ấy trống, đúng ở loại cặp mà bảng bằng chứng
  // cũng rỗng theo bất biến riêng tư.
  await expect(blocked.getByTestId("pair-signals")).toContainText(/Trùng tên huý/i);

  // Ba lựa chọn bấm được — endpoint quyết định đã có thật.
  await expect(page.getByTestId("pending-duplicateDecision")).toHaveCount(0);
  await expect(inFile.getByRole("button", { name: /Chưa rõ — để lại sau/i })).toBeEnabled();
});

test("quyết một cặp: hai con số kế hoạch đổi, và máy chủ là nơi tính lại", async ({ page }) => {
  await uploadWorkbook(page, "chi-nhat-da-sua.xlsx");
  await page.getByRole("link", { name: /Xem 3 cặp nghi trùng/i }).click();
  await page.waitForURL(/\/trung$/, { timeout: 60_000 });

  const plan = page.getByTestId("duplicate-plan");
  await expect(plan).toBeVisible({ timeout: 30_000 });
  const createBefore = (await plan.getByTestId("plan-create").textContent()) ?? "";
  const updateBefore = (await plan.getByTestId("plan-update").textContent()) ?? "";

  // Gộp một cặp có bên kia ĐÃ CÓ TRONG PHẢ: dòng ấy chuyển từ "thêm mới" sang
  // "cập nhật", nên cả hai con số đổi cùng lúc. Người đối chiếu phải thấy được
  // hệ quả của việc mình vừa làm.
  const pair = page.locator(`[data-testid="duplicate-pair-${PAIR_TREE_VISIBLE}"]`);
  await pair.getByRole("button", { name: /hợp nhất/i }).click();
  await expect(pair).toHaveAttribute("data-status", "MERGED", { timeout: 30_000 });

  await expect(plan.getByTestId("plan-create")).not.toHaveText(createBefore);
  await expect(plan.getByTestId("plan-update")).not.toHaveText(updateBefore);
});

test('"hoãn" KHÔNG mở khoá nút duyệt, kể cả khi hoãn hết mọi cặp', async ({ page }) => {
  await uploadWorkbook(page, "chi-nhat-da-sua.xlsx");
  await page.getByRole("link", { name: /Xem 3 cặp nghi trùng/i }).click();
  await page.waitForURL(/\/trung$/, { timeout: 60_000 });
  await expect(page.getByTestId("duplicate-gate")).toBeVisible({ timeout: 30_000 });

  // Hoãn từng cặp một — KHÔNG có nút "hoãn tất cả", và đó là chủ ý.
  for (const id of [PAIR_TREE_VISIBLE, PAIR_TREE_HIDDEN, PAIR_IN_FILE]) {
    const pair = page.locator(`[data-testid="duplicate-pair-${id}"]`);
    await pair.getByRole("button", { name: /Chưa rõ — để lại sau/i }).click();
    await expect(pair).toHaveAttribute("data-status", "DEFERRED", { timeout: 30_000 });
  }

  // Cả ba đã quyết, mà cửa duyệt VẪN đóng: `DEFERRED` nằm trong số chưa quyết.
  await expect(page.getByTestId("plan-undecided")).toHaveText(/3 cặp chưa quyết/i);
  await expect(page.getByTestId("duplicate-gate")).toContainText(/chưa duyệt được/i);

  // Và màn đối soát nói đúng như vậy — cùng một `canApprove` của máy chủ.
  await page.getByRole("link", { name: /Về màn đối soát/i }).click();
  await expect(page.getByRole("button", { name: /Duyệt và ghi .* vào phả/i })).toBeDisabled({
    timeout: 30_000,
  });
  await expect(page.getByTestId("commit-blockers")).toContainText(/3 cặp nghi trùng chưa quyết/i);
});

test("kiểm lại KHÔNG xoá quyết định cũ — không ai phải quyết lại 40 cặp", async ({ page }) => {
  // Tệp CÒN LỖI CHẶN, có chủ ý: nút "Kiểm lại lô này" chỉ có mặt trong khối lỗi
  // chặn, và đó đúng là tình huống thật — người ta bấm kiểm lại khi còn lỗi,
  // chứ không phải khi đã sạch.
  await uploadWorkbook(page, "chi-nhat.xlsx");
  await page.getByRole("link", { name: /Xem 3 cặp nghi trùng/i }).click();
  await page.waitForURL(/\/trung$/, { timeout: 60_000 });

  const pair = page.locator(`[data-testid="duplicate-pair-${PAIR_IN_FILE}"]`);
  await pair.getByRole("textbox").fill("đối chiếu với sổ chi Giáp bản 1998");
  await pair.getByRole("button", { name: /hai người khác nhau/i }).click();
  await expect(pair).toHaveAttribute("data-status", "DISTINCT", { timeout: 30_000 });
  await expect(page.getByTestId("plan-undecided")).toHaveText(/2 cặp chưa quyết/i);

  // Chạy lại bộ kiểm từ màn đối soát — đúng đường người dùng thật đi.
  await page.getByRole("link", { name: /Về màn đối soát/i }).click();
  const revalidate = page.getByRole("button", { name: /Kiểm lại lô này/i });
  await expect(revalidate).toBeVisible({ timeout: 30_000 });
  await revalidate.click();
  await expect(revalidate).toBeEnabled({ timeout: 30_000 });

  // Quay lại: quyết định cũ CÒN NGUYÊN, kèm ghi chú và mốc thời gian. Bắt
  // người ta quyết lại 40 cặp vì lần kiểm sau tìm thêm cặp thứ 41 là cách
  // huấn luyện họ bấm bừa.
  await page.getByRole("link", { name: /Xem 3 cặp nghi trùng/i }).click();
  await page.waitForURL(/\/trung$/, { timeout: 60_000 });
  const again = page.locator(`[data-testid="duplicate-pair-${PAIR_IN_FILE}"]`);
  await expect(again).toHaveAttribute("data-status", "DISTINCT", { timeout: 30_000 });
  await expect(again.getByTestId("pair-decision")).toContainText(/sổ chi Giáp bản 1998/i);
  await expect(again.getByTestId("pair-decision")).toContainText(/đã quyết lúc/i);

  // Cặp chưa ai động tới vẫn PENDING và vẫn chặn lô — không có gì bị "tha".
  await expect(page.getByTestId("plan-undecided")).toHaveText(/2 cặp chưa quyết/i);
});

test("tải danh sách lỗi ra Excel: nút ngay cạnh bảng, tên tệp giữ nguyên dấu", async ({ page }) => {
  await uploadWorkbook(page, "chi-nhat.xlsx");

  // NGAY CẠNH BẢNG LỖI, không giấu trong một menu phụ: chỗ Trưởng chi sửa được
  // là chính tệp Excel, và một đường thoát không ai tìm thấy thì không tồn tại.
  const blocking = page.getByRole("region", { name: /lỗi phải sửa/i });
  const download = blocking.getByRole("button", { name: /Tải danh sách lỗi ra Excel/i });
  await expect(download).toBeVisible({ timeout: 60_000 });
  // Một nút, không phải một nút cho mỗi nhóm lỗi.
  await expect(page.getByTestId("issues-workbook")).toHaveCount(1);

  const [file] = await Promise.all([page.waitForEvent("download"), download.click()]);

  // `Content-Disposition` mang CẢ HAI dạng tên tệp. Đọc `filename=` trước thì
  // lượt tải vẫn "thành công" và người dùng nhận về một tên đã rụng hết dấu —
  // hỏng im lặng, kiểu tệ nhất.
  expect(file.suggestedFilename()).toContain("Danh sách cần sửa");
  expect(file.suggestedFilename()).toContain("Chi Nhất");
});

test("một tệp sạch: duyệt, ghi vào phả, rồi gỡ lại được cả lô", async ({ page }) => {
  await uploadWorkbook(page, "chi-nhat-sach.xlsx");

  // Sạch hoàn toàn thì `canApprove` của máy chủ mở nút — không có lý do khoá nào.
  await expect(page.getByTestId("commit-blockers")).toHaveCount(0);
  const commit = page.getByRole("button", { name: /Duyệt và ghi 318 người vào phả/i });
  await expect(commit).toBeEnabled();
  await commit.click();

  // Hỏi lại một lần, và câu hỏi KHÔNG hứa một hạn 30 ngày không tồn tại.
  await expect(page.getByText(/chừng nào chưa ai trong họ sửa/i)).toBeVisible();
  await expect(page.getByText(/30 ngày/i)).toHaveCount(0);
  await page.getByRole("button", { name: /^Ghi vào phả$/ }).click();

  await expect(page.getByText(/318 người vào phả/i)).toBeVisible({ timeout: 30_000 });
  // Lời trấn an biến mất sau khi ghi — lúc đó nó không còn đúng.
  await expect(page.getByText(/Chưa có gì được ghi vào phả/i)).toHaveCount(0);

  // Nút gỡ lô hỏi preflight TRƯỚC khi mở hộp xác nhận.
  await page.getByRole("button", { name: /Gỡ toàn bộ lô này/i }).click();
  const confirm = page.getByRole("button", { name: /^Gỡ lô$/ });
  await expect(confirm).toBeEnabled({ timeout: 30_000 });
  // Lý do là TUỲ CHỌN: không gõ gì vẫn gỡ được.
  await confirm.click();

  // Lô đã gỡ VẪN mang trạng thái "đã vào phả" — nó đã từng được ghi.
  await expect(page.getByText(/Trạng thái vẫn là/i)).toBeVisible({ timeout: 30_000 });
});

test("đóng trình duyệt giữa chừng rồi quay lại: lô đang dở vẫn ở đó", async ({
  page,
  context,
}) => {
  await uploadWorkbook(page, "chi-nhat.xlsx");
  const batchId = page.url().split("/").pop()!;
  await expect(page.getByRole("region", { name: /lỗi phải sửa/i })).toBeVisible();

  /**
   * "Đóng trình duyệt": đóng hẳn trang, rồi mở một trang MỚI trong cùng ngữ
   * cảnh. Trang mới không mang theo gì của trang cũ — không bộ nhớ React Query,
   * không state thành phần, không `sessionStorage` (nơi `use-form-draft` giữ
   * nháp, và là nơi một cách làm sai sẽ giấu trạng thái vào).
   *
   * Cùng ngữ cảnh là cố ý: `localStorage` ở đây đóng vai **cơ sở dữ liệu của
   * máy chủ giả**, nên giữ nó lại chính là giữ cho máy chủ "vẫn đang chạy"
   * trong khi trình duyệt đã đóng. Bản chạy thật không cần mẹo này: trạng thái
   * nằm ở bảng `import_*`.
   */
  await page.close();

  const nextMorning = await context.newPage();
  await signInAs(nextMorning, "branch-head");
  await nextMorning.addInitScript(() => window.sessionStorage.clear());

  // Về qua MÀN DẪN NHẬP, không phải qua một đường dẫn nhớ sẵn: đó chính là
  // điều `GET /import/batches` làm cho khả thi.
  await nextMorning.goto("/nhap-lieu");
  const resume = nextMorning.getByTestId("resume-batch-link");
  await expect(resume).toBeVisible({ timeout: 60_000 });
  await expect(resume).toHaveAttribute("href", new RegExp(`/nhap-lieu/${batchId}$`));

  await resume.click();
  await nextMorning.waitForURL(new RegExp(`/nhap-lieu/${batchId}$`), { timeout: 60_000 });
  await expect(
    nextMorning.getByRole("region", { name: /lỗi phải sửa/i }).getByText(/6 lỗi phải sửa/i)
  ).toBeVisible();
  await nextMorning.close();
});

test("màn tải lên cảnh báo TRƯỚC khi nộp lô thứ hai cho cùng một chi", async ({ page }) => {
  await uploadWorkbook(page, "chi-nhat.xlsx");
  const batchId = page.url().split("/").pop()!;

  await page.goto("/nhap-lieu/tai-len");
  await expect(page.getByRole("heading", { name: /Tải tệp lên/i })).toBeVisible({
    timeout: 60_000,
  });

  // Không chặn — nộp lại là hành vi hợp lệ — nhưng nói ra cái đang đánh đổi,
  // kèm một đường dẫn về đúng chỗ cũ.
  const warning = page.getByText(/Chi này còn một lô đang đối soát/i);
  await expect(warning).toBeVisible();
  await expect(page.getByText(new RegExp(`Lô ${batchId}`, "i"))).toBeVisible();
  await expect(page.getByRole("link", { name: /Mở lô đang dở/i })).toHaveAttribute(
    "href",
    new RegExp(`/nhap-lieu/${batchId}$`)
  );
});

test("tiến độ theo chi không xếp hạng, và cắt trường với chi ngoài phạm vi", async ({ page }) => {
  await page.goto("/nhap-lieu/tien-do");
  await expect(page.getByRole("heading", { name: /Tiến độ theo chi/i })).toBeVisible({
    timeout: 60_000,
  });

  await expect(page.getByText(/Đây không phải bảng xếp hạng/i)).toBeVisible();
  await expect(page.locator('[role="progressbar"]')).toHaveCount(0);

  // Người đăng nhập ở đây là Trưởng chi Nhất, nên BA chi kia đều ngoài phạm vi
  // và khối "người phụ trách" bị cắt ở cả ba — kể cả chi Tứ, chi chưa ai nhận.
  // Ca "chưa có người nhận" chỉ hiện với người nhìn được cả bốn chi, và nó nằm
  // ở test thành phần với vai Quản trị.
  await expect(page.getByText(/Chưa có người nhận chi này/i)).toHaveCount(0);

  // Chi ngoài phạm vi: không vẽ ô trống ở chỗ năm trường bị cắt.
  const other = page.getByTestId("branch-progress-b-chi2");
  await expect(other.getByText(/Bạn không nhập liệu cho chi này/i)).toBeVisible();
  await expect(other.getByText(/Người phụ trách/i)).toHaveCount(0);

  // `expectedPersons` vắng nghĩa là chưa ai đếm, KHÔNG phải 0.
  await expect(
    page.getByTestId("branch-progress-b-chi3").getByText(/Chưa ai đếm bản phả giấy/i)
  ).toBeVisible();
});

test("bản tiếng Anh không rơi khoá dịch nào trên cả năm màn", async ({ page, consoleErrors }) => {
  for (const path of [
    "/en/nhap-lieu",
    "/en/nhap-lieu/mau",
    "/en/nhap-lieu/tai-len",
    "/en/nhap-lieu/tien-do",
  ]) {
    await page.goto(path);
    await expect
      .poll(async () => (await page.locator("body").innerText()).trim().length, {
        timeout: 60_000,
      })
      .toBeGreaterThan(20);
    const body = await page.locator("body").innerText();
    expect(body, `MISSING_MESSAGE on ${path}`).not.toContain("MISSING_MESSAGE");
  }
  expect(
    consoleErrors.filter((e) => e.includes("MISSING_MESSAGE") || e.includes("IntlError"))
  ).toEqual([]);
});

/* ══════════════════════════════════════════════════════════════════════════
   SÀN TIẾP CẬN TRÊN CHÍNH NĂM MÀN NÀY
   ══════════════════════════════════════════════════════════════════════════ */

/**
 * `e2e/accessibility.spec.ts` quét một danh sách màn cố định (`MAIN_SCREENS`)
 * và **không** gồm nhánh nhập liệu. Nên phép đo được lặp lại ở đây, trên đúng
 * các màn này — nếu không thì "đạt sàn tiếp cận" chỉ là một lời khẳng định.
 *
 * Hai sàn của tài liệu 00 §2.2, cả hai đều không đàm phán:
 * 44×44px vùng chạm (WCAG 2.2 SC 2.5.8 mức AAA) và 16px thân bài.
 */
const TOUCH_FLOOR_PX = 44;
const FONT_FLOOR_PX = 16;

interface ControlScan {
  /** Tổng số điều khiển ĐÃ ĐO. Bằng 0 nghĩa là phép quét hỏng, không phải màn hình sạch. */
  scanned: number;
  tooSmall: string[];
}

async function undersizedControls(page: Page): Promise<ControlScan> {
  return page.evaluate((floor) => {
    const SELECTOR = [
      "a[href]",
      "button",
      "input:not([type=hidden])",
      "select",
      "textarea",
      "summary",
      '[role="button"]',
      '[role="checkbox"]',
      '[role="radio"]',
    ].join(",");

    const out: string[] = [];
    let scanned = 0;
    for (const el of document.querySelectorAll<HTMLElement>(SELECTOR)) {
      const style = getComputedStyle(el);
      if (style.visibility === "hidden" || style.display === "none") continue;
      if (Number.parseFloat(style.opacity) === 0) continue;
      // Ngoại lệ "inline" của WCAG 2.5.8: liên kết nằm giữa một câu văn.
      if (style.display === "inline") continue;

      // Ô nhập tệp thật được giấu bằng `sr-only` và người dùng chạm vào <label>
      // của nó — đo cái bị giấu là đo một thứ không ai chạm tới.
      if (el.classList.contains("sr-only")) continue;
      // Ant Design giấu input thật của Checkbox/Radio sau một lớp vẽ; vùng chạm
      // thật là nhãn bao ngoài, và nhãn ấy đã nằm trong phép quét này.
      if (el.closest(".ant-checkbox, .ant-radio")) continue;

      const rect = el.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) continue;
      scanned += 1;
      if (rect.width >= floor && rect.height >= floor) continue;

      const label = (el.innerText || el.getAttribute("aria-label") || "").trim().slice(0, 40);
      out.push(
        `${el.tagName.toLowerCase()} "${label}" → ` +
          `${rect.width.toFixed(1)}×${rect.height.toFixed(1)}px`
      );
    }
    return { scanned, tooSmall: out };
  }, TOUCH_FLOOR_PX);
}

async function undersizedText(page: Page): Promise<string[]> {
  return page.evaluate((floor) => {
    const out: string[] = [];
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    const seen = new Set<Element>();
    while (walker.nextNode()) {
      const text = walker.currentNode.textContent?.trim() ?? "";
      if (text.length < 3) continue;
      const el = walker.currentNode.parentElement;
      if (!el || seen.has(el)) continue;
      seen.add(el);
      const style = getComputedStyle(el);
      if (style.visibility === "hidden" || style.display === "none") continue;
      if (el.closest(".sr-only")) continue;
      const size = Number.parseFloat(style.fontSize);
      if (size >= floor) continue;
      out.push(`${size}px — "${text.slice(0, 40)}"`);
    }
    return out;
  }, FONT_FLOOR_PX);
}

const IMPORT_SCREENS = [
  { path: "/nhap-lieu", name: "quy trình năm bước" },
  { path: "/nhap-lieu/mau", name: "mẫu Excel" },
  { path: "/nhap-lieu/tai-len", name: "tải lên" },
  { path: "/nhap-lieu/tien-do", name: "tiến độ theo chi" },
] as const;

for (const screen of IMPORT_SCREENS) {
  test(`sàn tiếp cận · ${screen.name}: vùng chạm 44px và chữ 16px`, async ({ page }) => {
    await page.goto(screen.path);
    await page.waitForLoadState("domcontentloaded");
    await page
      .waitForFunction(() => document.querySelectorAll("button, a[href]").length > 2, undefined, {
        timeout: 60_000,
      })
      .catch(() => undefined);

    const controls = await undersizedControls(page);
    // Chống xanh giả: một phép quét không thấy điều khiển nào thì mọi khẳng
    // định sau nó đều vô nghĩa, và nó im lặng đúng như một màn hình hoàn hảo.
    expect(controls.scanned, `không đo được điều khiển nào trên ${screen.name}`).toBeGreaterThan(2);
    expect(controls.tooSmall.join(" · "), "điều khiển dưới sàn chạm 44px").toBe("");

    const tiny = await undersizedText(page);
    expect(tiny.join(" · "), `${tiny.length} đoạn chữ dưới sàn 16px`).toBe("");
  });
}

test("sàn tiếp cận · màn đối soát, màn dày điều khiển nhất của cả nhánh", async ({ page }) => {
  await uploadWorkbook(page, "chi-nhat.xlsx");
  await expect(page.getByRole("region", { name: /lỗi phải sửa/i })).toBeVisible();

  const controls = await undersizedControls(page);
  expect(controls.scanned, "không đo được điều khiển nào trên màn đối soát").toBeGreaterThan(5);
  expect(controls.tooSmall.join(" · "), "điều khiển dưới sàn chạm 44px").toBe("");

  const tiny = await undersizedText(page);
  expect(tiny.join(" · "), `${tiny.length} đoạn chữ dưới sàn 16px`).toBe("");
});

test("sàn tiếp cận · chế độ tối không làm rơi màu nào của nhánh nhập liệu", async ({ page }) => {
  await page.emulateMedia({ colorScheme: "dark" });
  await page.goto("/nhap-lieu/tien-do");
  await expect(page.getByRole("heading", { name: /Tiến độ theo chi/i })).toBeVisible({
    timeout: 60_000,
  });

  /**
   * Mọi biến màu được nhánh này dùng phải GIẢI ĐƯỢC ở chế độ tối. Một biến chưa
   * khai không báo lỗi — nó trả về chuỗi rỗng, trình duyệt bỏ qua khai báo, và
   * kết quả là một mảng màu chế độ sáng nằm trên nền tối, im lặng.
   */
  const unresolved = await page.evaluate(() => {
    const style = getComputedStyle(document.documentElement);
    const names = [
      "--color-primary",
      "--color-primary-light",
      "--color-bg-card",
      "--color-bg-page",
      "--color-bg-deceased",
      "--color-accent",
      "--color-accent-text",
      "--color-text-main",
      "--color-text-muted",
      "--color-border",
      "--color-border-dark",
      "--color-border-input",
      "--color-success",
      "--color-success-text",
      "--color-success-bg",
      "--color-danger",
      "--color-danger-bg",
      "--color-warning-bg",
    ];
    return names.filter((name) => style.getPropertyValue(name).trim() === "");
  });
  expect(unresolved, `biến màu chưa khai ở chế độ tối: ${unresolved.join(", ")}`).toEqual([]);
});
