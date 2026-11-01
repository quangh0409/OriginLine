// Vai phụ cho NT-MOI-13: truongchi mở /quan-ly/don-gia-nhap, tìm đơn có số điện thoại đã khai,
// bấm "Từ chối", ghi lý do, xác nhận. Chỉ phần duyệt tối thiểu để kịch bản nhóm B đi tiếp.
//   PHONE=0912000131 node e2e/real-auth/nt-b-tc-tu-choi.mjs
import { open, login, shot, mainText, APP } from "./nt-b-lib.mjs";
const { browser, page, p } = await open("desktop", "truongchi-tu-choi");
await login(page, "truongchi", "giapha123", "/vi");
await page.goto(APP + "/vi/quan-ly/don-gia-nhap", { waitUntil: "networkidle" });
await page.waitForTimeout(2000);
const the = page.getByTestId("don-nhan-minh").filter({ hasText: process.env.PHONE });
p("số thẻ khớp: " + (await the.count()));
await the.first().getByRole("button", { name: "Từ chối" }).click();
const dlg = page.getByRole("dialog");
await dlg.waitFor();
await dlg.getByRole("textbox").first().fill("Lời tự giới thiệu chưa khớp với gia phả chi, xin gọi Trưởng chi để nói rõ (nghiệm thu nhóm B).");
const resP = page.waitForResponse((r) => r.url().includes("/person-claims/") && r.request().method() === "POST");
await dlg.getByRole("button", { name: /Từ chối/ }).last().click();
const r = await resP;
p("POST " + new URL(r.url()).pathname + " -> " + r.status());
await page.waitForTimeout(1200);
p("thông báo: " + (await page.locator(".ant-message").allInnerTexts()).join(" | "));
await browser.close();
