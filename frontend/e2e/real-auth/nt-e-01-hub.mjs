// NT-TC-01 / NT-ADMIN-01 (+ hoidong): vào /quan-ly bằng lối vào thật (không gõ đường dẫn), đếm các ô,
// so huy hiệu với số đơn thật. Chạy cả desktop 1280×900 và Pixel 5.
import { open, login, shot, mainText, api, tokenOf, verdict } from "./nt-e-lib.mjs";
const users = (process.env.NTE_USERS ?? "truongchi,hoidong,admin.giapha,thanhvien").split(",");
const kinds = (process.env.NTE_KINDS ?? "desktop,mobile").split(",");
for (const kind of kinds) for (const u of users) await run(kind, u);
async function run(kind, u) {
  const { browser, page, p, log } = await open(kind, `hub ${u} ${kind}`);
  try {
    await login(page, u);
    await page.waitForTimeout(1500);
    let entry;
    if (kind === "desktop") {
      entry = page.getByTestId("management-queue-link").filter({ visible: true });
      p(`lối vào thanh đầu trang: ${await entry.count()} — aria="${(await entry.count()) ? await entry.first().getAttribute("aria-label") : "-"}"`);
    } else {
      await page.getByTestId("more-menu-trigger").click();
      await page.waitForTimeout(600);
      await shot(page, `01-${u}-${kind}-ngan-them`);
      entry = page.getByRole("link", { name: /Quản lý thành viên/ }).filter({ visible: true });
      p(`mục trong ngăn Thêm: ${await entry.count()}`);
    }
    if (!(await entry.count())) { p("KHÔNG CÓ LỐI VÀO /quan-ly"); return; }
    await entry.first().click();
    await page.waitForURL(/\/quan-ly$/, { timeout: 20000 });
    await page.waitForLoadState("networkidle");
    const items = await page.locator("main ul li a").allInnerTexts();
    p(`ô trên /quan-ly (${items.length}): ` + items.map((s) => s.split("\n")[0]).join(" | "));
    await shot(page, `01-${u}-${kind}-quan-ly`);
    if (u !== "thanhvien") {
      const tok = await tokenOf(page);
      const r = await api("GET", "/api/v1/person-claims/pending/count", { token: tok });
      p(`GET /person-claims/pending/count -> ${r.status} ${r.text.slice(0,200)}`);
    }
  } catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, `01-${u}-${kind}-loi`); }
  finally { verdict(log, p); await browser.close(); }
}
