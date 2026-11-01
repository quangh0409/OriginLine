// NT-TV-11 (HEIC) + NT-TV-12 (ảnh quá khổ, video quá 120 giây, tệp thứ 13) trên một nháp thật.
// Tệp mẫu ở .playwright-mcp/nt-d/fixtures (sinh bằng Python/PIL — JPEG thật, HEIC có chữ ký
// `ftypheic` thật, MP4 có hộp mvhd khai 200 giây).
// Chạy: node e2e/real-auth/nt-d-tv11-12.mjs [desktop|pixel5]
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-d-lib.mjs";
import fs from "node:fs";

const mode = process.argv[2] ?? "desktop";
const ctx = mode === "pixel5" ? PIXEL5 : DESKTOP;
const tag = `tv11-12-${mode}`;
const FX = ".playwright-mcp/nt-d/fixtures";
const out = {};
const { browser, page, log } = await open(tag, ctx);

async function pick(files) {
  await page.locator('input[type="file"]').setInputFiles(files);
  await page.waitForTimeout(2500);
}
async function lastCardText() {
  return (await page.locator('[data-testid="media-grid"] li').last().innerText().catch(() => "(không có thẻ)")).replace(/\n+/g, " | ");
}

try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/bai-viet/moi");
  await page.locator("#post-title").waitFor({ timeout: 60000 });
  await page.locator("#post-title").fill(`NT-TV-11/12 tệp bị từ chối (${mode})`);
  const created = page.waitForResponse((r) => r.url().endsWith("/api/v1/posts") && r.request().method() === "POST" && r.status() === 201, { timeout: 30000 });
  await page.locator("#post-body").click();
  await page.keyboard.type("Thử các tệp bị từ chối.", { delay: 5 });
  await page.locator("#post-title").click();
  out.postId = (await (await created).json()).id;

  // NT-TV-11: .heic
  await pick(`${FX}/IMG_0001.heic`);
  out.heic = await lastCardText();
  await shot(page, `${tag}-01-heic`);

  // NT-TV-11 biến thể: nội dung HEIC nhưng đuôi .jpg — chỉ máy chủ (chữ ký byte) bắt được
  await pick({ name: "anh-iphone-doi-ten.jpg", mimeType: "image/jpeg", buffer: fs.readFileSync(`${FX}/anh-iphone-doi-ten.jpg`) });
  const altBox = page.getByLabel("Chữ thay ảnh (alt)").last();
  if (await altBox.isVisible().catch(() => false)) {
    await altBox.fill("Ảnh iPhone đổi tên");
    await page.getByRole("button", { name: "Xong, gắn ảnh này vào bài" }).last().click();
    await page.waitForTimeout(3000);
  }
  out.heicDoiTen = await lastCardText();
  await shot(page, `${tag}-02-heic-doi-ten`);

  // NT-TV-12: ảnh > 8 MiB
  await pick(`${FX}/anh-qua-kho-9mb.jpg`);
  out.anhQuaKho = await lastCardText();
  await shot(page, `${tag}-03-anh-qua-kho`);

  // NT-TV-12: video khai 200 giây
  await pick({ name: "video-200-giay.mp4", mimeType: "video/mp4", buffer: fs.readFileSync(`${FX}/video-200-giay.mp4`) });
  await page.waitForTimeout(4000);
  out.videoDai = await lastCardText();
  await shot(page, `${tag}-04-video-dai`);

  // NT-TV-12: tệp thứ 13 — chọn 13 bản của một JPEG nhỏ cùng lúc
  const jpg = fs.readFileSync(`${FX}/anh-thu-hai.jpg`);
  const many = Array.from({ length: 13 }, (_, i) => ({ name: `anh-${i + 1}.jpg`, mimeType: "image/jpeg", buffer: jpg }));
  await pick(many);
  await page.waitForTimeout(4000);
  out.tep13 = await lastCardText();
  out.atCap = await page.getByText(/Bài đã đủ 12 tệp/).allInnerTexts();
  await shot(page, `${tag}-05-tep-13`);
  await page.screenshot({ path: `.playwright-mcp/nt-d/${tag}-05b-full.png`, fullPage: true });
} catch (e) {
  console.log("SCRIPT ERROR", e);
  await shot(page, `${tag}-zz-error`).catch(() => {});
} finally {
  summary(tag, log);
  console.log(JSON.stringify(out, null, 2));
  fs.writeFileSync(`.playwright-mcp/nt-d/${tag}.json`, JSON.stringify({ out, log }, null, 2));
  await browser.close();
}
