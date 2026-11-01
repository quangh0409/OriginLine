import { open, login, shot, summary, BASE } from "./nt-c-lib.mjs";
const { browser, page, log } = await open("explore");
try {
  await login(page, "thanhvien");
  await page.goto(BASE + "/vi/tree");
  await page.getByTestId("person-node").first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(2000);
  await shot(page, "x-tree");
  const n = await page.getByTestId("person-node").count();
  console.log("nodes", n);
  const texts = await page.getByTestId("person-node").allInnerTexts();
  console.log(texts.slice(0, 40).map((t) => t.replace(/\n/g, " | ")).join("\n"));
  console.log("BODYTEXT", (await page.locator("main").innerText()).slice(0, 1500));
} finally { summary("explore", log); await browser.close(); }
