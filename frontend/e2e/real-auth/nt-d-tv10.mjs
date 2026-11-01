// NT-TV-10 (+ ảnh JPEG thật): thanhvien viết bài, nháp tự lưu, F5, mở lại từ "Bài của tôi", gắn ảnh,
// gửi duyệt. In ra từng kỳ vọng của kịch bản kèm ĐẠT/LỖI ở cuối.
// Chạy: node e2e/real-auth/nt-d-tv10.mjs [desktop|pixel5]
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-d-lib.mjs";
import fs from "node:fs";

const mode = process.argv[2] ?? "desktop";
const ctx = mode === "pixel5" ? PIXEL5 : DESKTOP;
const tag = `tv10-${mode}`;
const FX = ".playwright-mcp/nt-d/fixtures";
const stamp = new Date().toISOString().slice(11, 19);
const TITLE = `Kiểm nghiệm thu — họp họ cuối năm (${mode} ${stamp})`;
const BODY = "Họp họ cuối năm tổ chức tại nhà thờ họ. Xin các chi báo số người về dự.";
const { browser, page, log } = await open(tag, ctx);
const out = { title: TITLE };
const verdict = [];
const check = (name, ok, detail = "") => verdict.push(`${ok ? "ĐẠT" : "LỖI"}  ${name}${detail ? ` — ${detail}` : ""}`);

try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/bai-viet/moi");
  await page.locator("#post-title").waitFor({ timeout: 60000 });
  await shot(page, `${tag}-01-compose`);

  // 1. Gõ tiêu đề, rời ô (đúng lời gợi ý ở bộ chọn ảnh) — nháp phải lưu được ngay, không 400.
  const firstSave = page.waitForResponse((r) => r.url().endsWith("/api/v1/posts") && r.request().method() === "POST", { timeout: 30000 });
  await page.locator("#post-title").fill(TITLE);
  await page.locator("#post-title").blur();
  const fs1 = await firstSave;
  check("gõ tiêu đề rồi rời ô ⇒ nháp được tạo", fs1.status() === 201, `POST /posts ${fs1.status()}`);
  await page.waitForTimeout(1500);
  out.urlAfterFirstSave = page.url();
  check("URL đổi sang /bai-viet/{id}/sua sau lượt lưu đầu", /\/bai-viet\/[^/]+\/sua$/.test(page.url()), page.url());
  await shot(page, `${tag}-02-after-title-blur`);

  // 2. Gõ vài câu, đợi vài giây không thao tác.
  await page.locator("#post-body").click();
  await page.keyboard.type(BODY, { delay: 15 });
  await page.waitForTimeout(11000);
  await shot(page, `${tag}-03-autosaved`);
  out.statusLine = await page.getByText(/Đã lưu nháp lúc|Đang lưu nháp|Chưa lưu được/).first().innerText().catch(() => "(không thấy dòng trạng thái lưu)");
  check("thấy 'Đã lưu nháp lúc {giờ}'", /Đã lưu nháp lúc/.test(out.statusLine), out.statusLine);

  // 3. Ảnh JPEG thật: tải lên → alt → gắn; ảnh phải còn hiện trên màn soạn.
  await page.locator('input[type="file"]').setInputFiles(`${FX}/nha-tho-ho.jpg`);
  await page.getByLabel("Chữ thay ảnh (alt)").first().waitFor({ timeout: 30000 });
  await page.getByLabel("Chữ thay ảnh (alt)").first().fill("Mái ngói nhà thờ họ sau khi trùng tu");
  await page.getByRole("button", { name: "Xong, gắn ảnh này vào bài" }).click();
  await page.waitForTimeout(4000);
  await shot(page, `${tag}-04-attached`);
  const img = page.getByRole("img", { name: "Mái ngói nhà thờ họ sau khi trùng tu" }).first();
  out.attachedImg = await img.evaluate((el) => ({ src: el.src.slice(0, 90), complete: el.complete, w: el.naturalWidth })).catch(() => null);
  check("ảnh vừa gắn còn hiện trên màn soạn, mở được từ MinIO", Boolean(out.attachedImg && out.attachedImg.w > 0), JSON.stringify(out.attachedImg));

  // 4. F5 mà không bấm Gửi duyệt — chữ đã gõ không được mất.
  await page.reload();
  await page.locator("#post-title").waitFor({ timeout: 30000 });
  await page.waitForTimeout(3000);
  out.afterReloadTitle = await page.locator("#post-title").inputValue();
  out.afterReloadBody = await page.locator("#post-body").inputValue();
  check("F5 xong vẫn còn tiêu đề + nội dung", out.afterReloadTitle === TITLE && out.afterReloadBody === BODY, `${page.url()}`);
  await shot(page, `${tag}-05-after-reload`);

  // 5. "Bài của tôi" liệt kê đúng bài này ở "Nháp đang viết dở".
  await page.goto(BASE + "/vi/bai-viet/cua-toi");
  await page.getByText(TITLE, { exact: true }).first().waitFor({ timeout: 30000 });
  await shot(page, `${tag}-06-cua-toi`);
  const main = await page.locator("main").innerText();
  const iDraft = main.indexOf("Nháp đang viết dở");
  const iSent = main.indexOf("Đã gửi đi");
  const iMine = main.indexOf(TITLE);
  check("bài nằm ở mục 'Nháp đang viết dở'", iDraft >= 0 && iMine > iDraft && (iSent < 0 || iMine < iSent));

  // 6. Mở lại nháp, bấm Gửi duyệt — phải dẫn sang "Bài của tôi" kèm xác nhận, bài ở "Đã gửi đi".
  await page.getByText(TITLE, { exact: true }).first().click();
  await page.locator("#post-title").waitFor({ timeout: 30000 });
  await page.waitForTimeout(2500);
  out.reopenedBody = await page.locator("#post-body").inputValue();
  const submitted = page.waitForResponse((r) => r.url().endsWith("/submit"), { timeout: 30000 });
  await page.getByRole("button", { name: /Gửi duyệt/ }).click();
  const sr = await submitted.catch(() => null);
  check("bấm 'Gửi duyệt' trên nháp mở lại ⇒ POST /submit 200", sr?.status() === 200, sr ? `${sr.status()}` : "không có lượt gọi /submit nào");
  await page.waitForTimeout(4000);
  out.urlAfterSubmit = page.url();
  await shot(page, `${tag}-07-after-submit`);
  const notice = await page.getByText("Đã gửi bài đi duyệt").count();
  check("sau khi gửi: sang /bai-viet/cua-toi và thấy 'Đã gửi bài đi duyệt'", /\/bai-viet\/cua-toi/.test(page.url()) && notice > 0, page.url());
  const main2 = await page.locator("main").innerText();
  const j = main2.lastIndexOf(TITLE);
  check("bài chuyển sang 'Đã gửi đi' với trạng thái 'Đang chờ duyệt'",
    j > main2.indexOf("Đã gửi đi") && main2.slice(j, j + 200).includes("Đang chờ duyệt"));
} catch (e) {
  console.log("SCRIPT ERROR", e);
  await shot(page, `${tag}-zz-error`).catch(() => {});
  verdict.push(`LỖI  kịch bản dừng giữa chừng: ${String(e).slice(0, 200)}`);
} finally {
  summary(tag, log);
  const unexpected = log.bad.filter((b) => !b.startsWith("401 GET"));
  check("không có API 4xx/5xx ngoài dự kiến (bỏ qua 401 của trang chủ trước khi đăng nhập)", unexpected.length === 0, unexpected.join(" || ").slice(0, 400));
  check("không có pageerror", log.pageErrors.length === 0);
  console.log(JSON.stringify(out, null, 2));
  console.log("\n==== KẾT LUẬN " + tag + "\n" + verdict.join("\n"));
  fs.writeFileSync(`.playwright-mcp/nt-d/${tag}.json`, JSON.stringify({ out, verdict, log }, null, 2));
  await browser.close();
}
