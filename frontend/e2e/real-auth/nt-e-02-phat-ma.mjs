// NT-HD-01: Hội đồng phát mã mời dòng họ qua /quan-ly/phat-ma (nhãn, hạn 30 ngày, trần 20 lượt).
// Mã thô chỉ hiện một lần → ghi vào tệp trạng thái ở scratchpad (ngoài kho) cho các bước đăng ký sau.
import { open, login, shot, mainText, saveState, verdict, APP } from "./nt-e-lib.mjs";
const kind = process.env.NTE_KIND ?? "desktop";
const { browser, page, p, log } = await open(kind, "HD-01 " + kind);
try {
  await login(page, "hoidong");
  await page.goto(APP + "/vi/quan-ly", { waitUntil: "networkidle" });
  await page.getByRole("link", { name: /Phát mã và phát lời mời/ }).click();
  await page.waitForURL(/phat-ma/); await page.waitForLoadState("networkidle");
  await shot(page, `02-phat-ma-truoc-${kind}`);
  await page.getByLabel("Phát cho kênh nào?").fill("Nghiệm thu nhóm E " + kind);
  const ttl = page.getByLabel("Hạn dùng (ngày)").first();
  await ttl.fill("30");
  await page.getByLabel("Trần lượt dùng").fill("20");
  const resP = page.waitForResponse((r) => r.url().endsWith("/api/v1/clan-invites") && r.request().method() === "POST");
  await page.getByRole("button", { name: "Phát mã", exact: true }).click();
  const res = await resP; const j = await res.json();
  p(`POST /clan-invites -> ${res.status()} maxUses=${j.invite?.maxUses ?? j.maxUses} expiresAt=${j.invite?.expiresAt ?? j.expiresAt}`);
  await page.waitForTimeout(800);
  await shot(page, `02-phat-ma-ma-hien-${kind}`);
  const t = await mainText(page);
  p("thấy cảnh báo một lần: " + t.includes("Mã chỉ hiện một lần"));
  saveState({ clanCode: j.code, clanInviteId: j.invite?.id ?? j.id });
  await page.getByRole("button", { name: "Tôi đã chép xong" }).click();
  await page.waitForTimeout(1200);
  await shot(page, `02-phat-ma-danh-sach-${kind}`);
  const t2 = await mainText(page);
  const i = t2.indexOf("Nghiệm thu nhóm E " + kind);
  p("trong danh sách: " + (i >= 0 ? t2.slice(i, i + 300) : "KHÔNG THẤY"));
} catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, "02-loi"); }
finally { verdict(log, p); await browser.close(); }
