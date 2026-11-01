// NT-TC-09 (không tự duyệt bài của mình) → hoidong duyệt → NT-TV-13 (báo cáo ảnh trên bài NGƯỜI KHÁC,
// hai lần) → truongchi.at (chi khác) báo cáo ảnh → truongchi mở hàng chờ báo cáo ảnh.
// Chạy: node e2e/real-auth/nt-d-tc09-tv13.mjs [desktop|pixel5]
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-d-lib.mjs";
import fs from "node:fs";

const mode = process.argv[2] ?? "desktop";
const ctx = mode === "pixel5" ? PIXEL5 : DESKTOP;
const tag = `tc09-tv13-${mode}`;
const FX = ".playwright-mcp/nt-d/fixtures";
const stamp = new Date().toISOString().slice(11, 19);
const T = `NT-TC-09 bài của Trưởng chi (${mode} ${stamp})`;
const out = {};
const logs = [];

async function reportFirstImage(page, label) {
  await page.locator('[data-testid="post-media-grid"] button').first().click();
  const dialog = page.getByRole("dialog");
  await dialog.waitFor({ timeout: 15000 });
  await dialog.getByRole("button", { name: "Báo gỡ tệp này" }).click();
  await dialog.locator(".ant-select").first().click();
  await page.locator(".ant-select-dropdown").getByText("Không phù hợp với bài viết", { exact: true }).click();
  const resp = page.waitForResponse((r) => /\/api\/v1\/media\/[^/]+\/reports$/.test(r.url()), { timeout: 20000 });
  await dialog.getByRole("button", { name: "Gửi báo cáo" }).click();
  const r = await resp;
  await page.waitForTimeout(1500);
  out[`${label} status`] = r.status();
  out[`${label} màn hình`] = (await dialog.innerText()).replace(/\n+/g, " | ").slice(0, 400);
  await shot(page, `${tag}-${label}`);
  await page.keyboard.press("Escape");
  await page.waitForTimeout(800);
}

// ---------- 1) truongchi viết + tự thử duyệt ----------
let s = await open(`${tag}-tc`, ctx); logs.push(s.log);
let idT;
try {
  const page = s.page;
  await login(page, "truongchi");
  await page.goto(BASE + "/vi/bai-viet/moi");
  await page.locator("#post-title").waitFor({ timeout: 60000 });
  await page.locator("#post-title").fill(T);
  const created = page.waitForResponse((r) => r.url().endsWith("/api/v1/posts") && r.request().method() === "POST" && r.status() === 201, { timeout: 30000 });
  await page.locator("#post-body").click();
  await page.keyboard.type("Trưởng chi viết bài có ảnh, cần người khác duyệt.", { delay: 5 });
  await page.locator("#post-title").click();
  idT = (await (await created).json()).id;
  await page.locator('input[type="file"]').setInputFiles(`${FX}/anh-thu-hai.jpg`);
  await page.getByLabel("Chữ thay ảnh (alt)").first().waitFor({ timeout: 30000 });
  await page.getByLabel("Chữ thay ảnh (alt)").first().fill("Sân trước nhà thờ chi");
  await page.getByRole("button", { name: "Xong, gắn ảnh này vào bài" }).click();
  await page.waitForTimeout(2500);
  const submitted = page.waitForResponse((r) => r.url().includes(`/posts/${idT}/submit`), { timeout: 30000 });
  await page.getByRole("button", { name: "Gửi duyệt" }).click();
  out.submitT = (await submitted).status();
  await page.waitForTimeout(2000);
  await page.goto(BASE + "/vi/quan-ly/bai-viet");
  const card = page.locator("article", { hasText: T });
  await card.waitFor({ timeout: 30000 });
  out.tuDuyet_coNutDuyet = await card.getByRole("button", { name: "Duyệt", exact: true }).count();
  out.tuDuyet_ghiChu = (await card.innerText()).replace(/\n+/g, " | ");
  await card.scrollIntoViewIfNeeded();
  await shot(page, `${tag}-01-truongchi-bai-cua-minh`);
} catch (e) { console.log("ERR tc", e); await shot(s.page, `${tag}-zz-tc`); }
finally { summary(`${tag}-tc`, s.log); await s.browser.close(); }

// ---------- 2) hoidong duyệt bài của truongchi ----------
s = await open(`${tag}-hd`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "hoidong");
  await page.goto(BASE + "/vi/quan-ly/bai-viet");
  const card = page.locator("article", { hasText: T });
  await card.waitFor({ timeout: 30000 });
  const rv = page.waitForResponse((r) => r.url().includes(`/posts/${idT}/review`), { timeout: 30000 });
  await card.getByRole("button", { name: "Duyệt", exact: true }).click();
  out.hoidongDuyet = (await rv).status();
  await page.waitForTimeout(2000);
  await shot(page, `${tag}-02-hoidong-duyet`);
} catch (e) { console.log("ERR hd", e); await shot(s.page, `${tag}-zz-hd`); }
finally { summary(`${tag}-hd`, s.log); await s.browser.close(); }

// ---------- 3) thanhvien báo cáo ảnh trên bài của truongchi, hai lần ----------
s = await open(`${tag}-tv`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "thanhvien");
  await page.goto(BASE + `/vi/bai-viet/${idT}`);
  await page.locator('[data-testid="post-media-grid"]').waitFor({ timeout: 30000 });
  await shot(page, `${tag}-03-tv-doc-bai`);
  await reportFirstImage(page, "04-bao-lan-1");
  await reportFirstImage(page, "05-bao-lan-2");
} catch (e) { console.log("ERR tv", e); await shot(s.page, `${tag}-zz-tv`); }
finally { summary(`${tag}-tv`, s.log); await s.browser.close(); }

// ---------- 4) truongchi.at (chi khác) báo cáo cùng ảnh ----------
s = await open(`${tag}-at`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "truongchi.at");
  await page.goto(BASE + `/vi/bai-viet/${idT}`);
  await page.locator('[data-testid="post-media-grid"]').waitFor({ timeout: 30000 });
  await reportFirstImage(page, "06-truongchi-at-bao");
  await page.goto(BASE + "/vi/quan-ly/bao-cao-anh");
  await page.waitForTimeout(3000);
  out.hangChoCuaTruongChiAt = (await page.locator("main").innerText()).slice(0, 400);
  await shot(page, `${tag}-07-truongchi-at-hang-cho`);
} catch (e) { console.log("ERR at", e); await shot(s.page, `${tag}-zz-at`); }
finally { summary(`${tag}-at`, s.log); await s.browser.close(); }

// ---------- 5) truongchi mở hàng chờ báo cáo ảnh (không gỡ thật) ----------
s = await open(`${tag}-tc2`, ctx); logs.push(s.log);
try {
  const page = s.page;
  await login(page, "truongchi");
  await page.goto(BASE + "/vi/quan-ly/bao-cao-anh");
  await page.waitForTimeout(3000);
  await shot(page, `${tag}-08-hang-cho-bao-cao`);
  out.hangCho = (await page.locator("main").innerText()).slice(0, 1200);
  const first = page.locator("article").first();
  if (await first.count()) {
    out.theBaoCao = (await first.innerText()).replace(/\n+/g, " | ");
    out.theBaoCaoLinks = await first.locator("a").evaluateAll((as) => as.map((a) => [a.textContent, a.getAttribute("href")]));
    await first.getByRole("button", { name: "Gỡ vĩnh viễn" }).click();
    await page.waitForTimeout(800);
    out.canhBaoGo = (await first.innerText()).replace(/\n+/g, " | ");
    await shot(page, `${tag}-09-canh-bao-go`);
    await first.getByRole("button", { name: "Thôi" }).click();
  }
} catch (e) { console.log("ERR tc2", e); await shot(s.page, `${tag}-zz-tc2`); }
finally { summary(`${tag}-tc2`, s.log); await s.browser.close(); }

out.idT = idT;
console.log(JSON.stringify(out, null, 2));
fs.writeFileSync(`.playwright-mcp/nt-d/${tag}.json`, JSON.stringify({ out, logs }, null, 2));
