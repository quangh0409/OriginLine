// NT-TV-18 — ô lọc Chi/Ngành ở /search (GraphQL `branches`) + đối chứng memberCount thanhvien vs hoidong.
// Đồng thời kiểm lời gọi GraphQL THẬT mang cùng lúc Content-Type và Authorization.
import { open, login, shot, summary, BASE, DESKTOP, PIXEL5 } from "./nt-c-lib.mjs";
const dev = process.argv[2] ?? "desktop";
const out = {};
for (const user of ["thanhvien", "hoidong"]) {
  const L = `tv18-${user}-${dev}`;
  const { browser, page, log } = await open(L, dev === "pixel5" ? PIXEL5 : DESKTOP);
  const gql = [];
  page.on("request", (r) => {
    if (r.url().includes("/api/v1/graphql") && r.method() === "POST") {
      const h = r.headers();
      gql.push({ ct: h["content-type"], auth: (h["authorization"] ?? "").startsWith("Bearer ") ? "Bearer …" : "(none)", body: r.postData()?.slice(0, 120) });
    }
  });
  let token = null;
  page.on("request", (r) => { const a = r.headers()["authorization"]; if (a && r.url().includes(":8088")) token = a; });
  try {
    await login(page, user);
    await page.goto(BASE + "/vi/search");
    const sel = page.locator("#search-filter-branch");
    await sel.waitFor({ timeout: 30000 });
    await page.waitForTimeout(2500);
    await sel.click();
    await page.waitForTimeout(800);
    const opts = await page.locator(".ant-select-dropdown:visible .ant-select-item-option").allInnerTexts();
    console.log(`[${user}] branch options (${opts.length}):`, opts.join(" / "));
    await sel.pressSequentially("Bính", { delay: 40 });
    await page.waitForTimeout(800);
    const filtered = await page.locator(".ant-select-dropdown:visible .ant-select-item-option").evaluateAll((els) => els.map((e) => `${e.textContent}@pad${e.querySelector("span[style]")?.style.paddingLeft ?? "?"}`));
    console.log(`[${user}] after "Bính":`, filtered.join(" / "));
    await shot(page, `${L}-01-dropdown`);
    await page.locator(".ant-select-dropdown:visible .ant-select-item-option", { hasText: /^Chi Bính$/ }).first().click();
    await page.waitForTimeout(3000);
    await shot(page, `${L}-02-filtered`);
    console.log(`[${user}] URL`, page.url());
    const res = await page.locator("main").innerText();
    console.log(`[${user}] results:`, res.slice(0, 900).replace(/\n+/g, " | "));
    console.log(`[${user}] GQL requests:`, JSON.stringify(gql));
    // Phần 2: đọc memberCount bằng đúng token của phiên này (chính lời gọi mà ứng dụng làm, gọi lại từ trang).
    const counts = await page.evaluate(async (auth) => {
      const r = await fetch("http://localhost:8088/api/v1/graphql", { method: "POST", headers: { "Content-Type": "application/json", Authorization: auth }, body: JSON.stringify({ query: "query { branches { path name memberCount } }" }) });
      return { status: r.status, json: await r.json() };
    }, token);
    const at = counts.json?.data?.branches?.find((b) => b.path === "ho_nguyen_dinh.chi_at");
    out[user] = at?.memberCount;
    console.log(`[${user}] graphql status ${counts.status}, chi_at memberCount = ${at?.memberCount}`);
  } finally { summary(L, log); await browser.close(); }
}
console.log("MEMBERCOUNT chi_at:", JSON.stringify(out), out.hoidong > out.thanhvien ? "hoidong > thanhvien ĐẠT" : "KHÔNG ĐẠT");
