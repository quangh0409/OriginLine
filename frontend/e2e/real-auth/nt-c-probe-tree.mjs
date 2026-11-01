// Dò: máy chủ trả gì cho lượt ANCESTORS của một dâu (thanhvien) — để biết vì sao "Về chỗ tôi" đổi gốc.
import { open, login, summary, BASE } from "./nt-c-lib.mjs";
const { browser, page, log } = await open("probe");
const dumps = [];
page.on("response", async (r) => {
  if (r.url().includes("/api/v1/tree")) {
    try { const j = await r.json(); dumps.push({ url: r.url(), nodes: j.nodes?.map(n => `${n.id.slice(0,8)} ${n.fullName ?? n.displayName ?? ""} g${n.generation}`), edges: j.edges?.map(e => `${e.relType} ${e.source.slice(0,8)}->${e.target.slice(0,8)}`), meta: j.meta, rootId: j.rootId }); } catch {}
  }
});
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/tree");
  await page.getByTestId("person-node").first().waitFor({ timeout: 60000 });
  await page.getByTestId("tree-go-to-self").click();
  await page.waitForTimeout(4000);
  for (const d of dumps) console.log(JSON.stringify(d, null, 0).slice(0, 3000));
} finally { summary("probe", log); await browser.close(); }
