// NT-TC-08 (+ NT-TV-10 nhánh "bài mới gửi thẳng", + ảnh thật đi trọn vòng):
//  1) thanhvien soạn 2 bài mới có ảnh JPEG thật, gửi duyệt ngay từ /bai-viet/moi
//  2) truongchi duyệt bài A, thử "Sửa nhẹ rồi đăng" rồi "Trả lại" bài B
//  3) thanhvien đọc lại bài A đã đăng — ảnh phải mở được từ MinIO; bài B về lại "Nháp" kèm lý do
// Chạy: node e2e/real-auth/nt-d-tc08.mjs [desktop|pixel5]
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-d-lib.mjs";
import fs from "node:fs";

const mode = process.argv[2] ?? "desktop";
const ctx = mode === "pixel5" ? PIXEL5 : DESKTOP;
const tag = `tc08-${mode}`;
const FX = ".playwright-mcp/nt-d/fixtures";
const stamp = new Date().toISOString().slice(11, 19);
const A = `NT-TC-08 bài duyệt (${mode} ${stamp})`;
const B = `NT-TC-08 bài trả lại (${mode} ${stamp})`;
const out = {};
const logs = [];

async function writeAndSubmit(page, title, withImage) {
  await page.goto(BASE + "/vi/bai-viet/moi");
  await page.locator("#post-title").waitFor({ timeout: 60000 });
  await page.locator("#post-title").fill(title);
  const created = page.waitForResponse((r) => r.url().endsWith("/api/v1/posts") && r.request().method() === "POST" && r.status() === 201, { timeout: 30000 });
  await page.locator("#post-body").click();
  await page.keyboard.type(`Thân bài của "${title}". Xin cả họ đọc và góp ý.`, { delay: 5 });
  await page.locator("#post-title").click(); // rời ô nội dung ⇒ lưu ngay
  const id = (await (await created).json()).id;
  if (withImage) {
    await page.locator('input[type="file"]').setInputFiles(`${FX}/nha-tho-ho.jpg`);
    await page.getByLabel("Chữ thay ảnh (alt)").first().waitFor({ timeout: 30000 });
    await page.getByLabel("Chữ thay ảnh (alt)").first().fill("Mái ngói nhà thờ họ sau khi trùng tu");
    const attached = page.waitForResponse((r) => r.url().includes(`/posts/${id}/media`) && r.request().method() === "PUT", { timeout: 30000 });
    await page.getByRole("button", { name: "Xong, gắn ảnh này vào bài" }).click();
    out[`${title} attach`] = (await attached).status();
    await page.waitForTimeout(1500);
    out[`${title} ảnh còn hiện trên màn soạn`] = await page.locator('[data-testid="media-grid"] img').count();
  }
  const submitted = page.waitForResponse((r) => r.url().includes(`/posts/${id}/submit`), { timeout: 30000 });
  await page.getByRole("button", { name: "Gửi duyệt" }).click();
  out[`${title} submit`] = (await submitted).status();
  await page.waitForTimeout(3000);
  out[`${title} url sau gửi`] = page.url();
  out[`${title} màn sau gửi`] = (await page.locator("main").innerText()).slice(0, 500);
  return id;
}

// ---------- 1) thanhvien viết ----------
let s = await open(`${tag}-tv`, ctx);
logs.push(s.log);
let idA, idB;
try {
  await login(s.page, "thanhvien");
  idA = await writeAndSubmit(s.page, A, true);
  await shot(s.page, `${tag}-01-sau-gui-A`);
  idB = await writeAndSubmit(s.page, B, false);
  await shot(s.page, `${tag}-02-sau-gui-B`);
} catch (e) { console.log("ERR tv", e); await shot(s.page, `${tag}-zz-tv`); }
finally { summary(`${tag}-tv`, s.log); await s.browser.close(); }

// ---------- 2) truongchi duyệt ----------
s = await open(`${tag}-tc`, ctx);
logs.push(s.log);
try {
  await login(s.page, "truongchi");
  await s.page.goto(BASE + "/vi/quan-ly/bai-viet");
  const cardA = s.page.locator("article", { hasText: A });
  await cardA.waitFor({ timeout: 30000 });
  await shot(s.page, `${tag}-03-hang-cho`);
  const reviewA = s.page.waitForResponse((r) => r.url().includes(`/posts/${idA}/review`), { timeout: 30000 });
  await cardA.getByRole("button", { name: "Duyệt", exact: true }).click();
  out.reviewA = (await reviewA).status();
  await s.page.waitForTimeout(2500);
  out.cardAVanConTrongHangCho = await cardA.count();
  await shot(s.page, `${tag}-04-sau-duyet-A`);

  // B: "Sửa nhẹ rồi đăng" — máy chủ thật không có lối cho người duyệt sửa bài đang chờ (PATCH ⇒
  // 403 "Chi tac gia moi duoc sua bai viet"); sau bản vá 24/09 nút này bị ẩn chờ `ba` phân xử.
  const cardB = s.page.locator("article", { hasText: B });
  out.coNutSuaNhe = await cardB.getByRole("button", { name: "Sửa nhẹ rồi đăng" }).count();
  if (out.coNutSuaNhe) {
    await cardB.getByRole("button", { name: "Sửa nhẹ rồi đăng" }).click();
    await cardB.locator("textarea").first().fill(`Thân bài của "${B}" — Trưởng chi sửa một chữ.`);
    await cardB.getByRole("button", { name: /Lưu & Duyệt/ }).click();
    await s.page.waitForTimeout(3000);
    out.suaNheRoiDang = (await cardB.innerText()).slice(0, 600);
    await shot(s.page, `${tag}-05-sua-nhe-B`);
    if (await cardB.getByRole("button", { name: "Huỷ" }).isVisible().catch(() => false)) {
      await cardB.getByRole("button", { name: "Huỷ" }).click();
    }
  }
  // B: trả lại kèm lý do
  if (await cardB.count()) {
    await cardB.getByRole("button", { name: "Trả lại", exact: true }).click();
    await cardB.locator("textarea").first().fill("Xin ghi rõ ngày họp cụ thể trước khi đăng.");
    const reviewB = s.page.waitForResponse((r) => r.url().includes(`/posts/${idB}/review`), { timeout: 30000 });
    await cardB.getByRole("button", { name: "Xác nhận trả lại" }).click();
    out.reviewB = (await reviewB).status();
    await s.page.waitForTimeout(2000);
    await shot(s.page, `${tag}-06-sau-tra-lai-B`);
  }

  // Bài A trên /bai-viet và trang chủ
  await s.page.goto(BASE + "/vi/bai-viet");
  await s.page.waitForTimeout(3000);
  out.listCoA = (await s.page.locator("main").innerText()).includes(A);
  await shot(s.page, `${tag}-07-bai-viet-list`);
  await s.page.goto(BASE + "/vi");
  await s.page.waitForTimeout(3000);
  out.trangChuCoA = (await s.page.locator("main").innerText()).includes(A);
  await shot(s.page, `${tag}-08-trang-chu`);
} catch (e) { console.log("ERR tc", e); await shot(s.page, `${tag}-zz-tc`); }
finally { summary(`${tag}-tc`, s.log); await s.browser.close(); }

// ---------- 3) thanhvien đọc lại ----------
s = await open(`${tag}-tv2`, ctx);
logs.push(s.log);
try {
  await login(s.page, "thanhvien");
  await s.page.goto(BASE + `/vi/bai-viet/${idA}`);
  await s.page.locator("h1").waitFor({ timeout: 30000 });
  await s.page.waitForTimeout(3000);
  await shot(s.page, `${tag}-09-doc-lai-A`);
  out.chiTietA = (await s.page.locator("main").innerText()).slice(0, 800);
  const img = s.page.locator('[data-testid="post-media-grid"] img').first();
  out.anhA = await img.evaluate((el) => ({ src: el.src.slice(0, 120), complete: el.complete, naturalWidth: el.naturalWidth })).catch((e) => String(e));
  await s.page.goto(BASE + "/vi/bai-viet/cua-toi");
  await s.page.waitForTimeout(3000);
  await shot(s.page, `${tag}-10-cua-toi`);
  const cardB = s.page.getByText(B, { exact: true }).first();
  await cardB.click();
  await s.page.waitForTimeout(3000);
  await shot(s.page, `${tag}-11-B-tra-lai`);
  out.BTraLai = (await s.page.locator("main").innerText()).slice(0, 600);
} catch (e) { console.log("ERR tv2", e); await shot(s.page, `${tag}-zz-tv2`); }
finally { summary(`${tag}-tv2`, s.log); await s.browser.close(); }

out.idA = idA; out.idB = idB;
const v = [];
const check = (name, ok, d = "") => v.push(`${ok ? "ĐẠT" : "LỖI"}  ${name}${d ? ` — ${d}` : ""}`);
check("thanhvien: ảnh vừa gắn còn hiện trên màn soạn bài mới", out[`${A} ảnh còn hiện trên màn soạn`] > 0);
check("thanhvien: gửi duyệt bài mới ⇒ sang 'Bài của tôi' kèm xác nhận", /bai-viet\/cua-toi/.test(out[`${A} url sau gửi`] ?? "") && /Đã gửi bài đi duyệt/.test(out[`${A} màn sau gửi`] ?? ""), out[`${A} url sau gửi`]);
check("truongchi duyệt A ⇒ 200 và A rời hàng chờ", out.reviewA === 200 && out.cardAVanConTrongHangCho === 0);
check("không còn nút 'Sửa nhẹ rồi đăng' chắc chắn 403", !out.coNutSuaNhe);
check("trả lại B kèm lý do ⇒ 200", out.reviewB === 200);
check("A lên /bai-viet và trang chủ", out.listCoA && out.trangChuCoA);
check("A có dòng 'Duyệt bởi … lúc …'", /Duyệt bởi .+ lúc/.test(out.chiTietA ?? ""));
check("ảnh của A mở được (URL ký từ MinIO)", out.anhA?.naturalWidth > 0, JSON.stringify(out.anhA));
check("B quay về nháp, hiện lý do trả lại", /Xin ghi rõ ngày họp cụ thể/.test(out.BTraLai ?? ""));
const bad = logs.flatMap((l) => l.bad).filter((b) => !b.startsWith("401 GET"));
check("không có API 4xx/5xx ngoài dự kiến", bad.length === 0, bad.join(" || ").slice(0, 500));
check("không có pageerror", logs.every((l) => l.pageErrors.length === 0));
out.verdict = v;
console.log(JSON.stringify(out, null, 2));
console.log("\n==== KẾT LUẬN " + tag + "\n" + v.join("\n"));
fs.writeFileSync(`.playwright-mcp/nt-d/${tag}.json`, JSON.stringify({ out, logs }, null, 2));
