// Bước DỰNG DỮ LIỆU cho NT-TC-02..07 / NT-HD-04: một tài khoản mới tự đăng ký bằng mã dòng họ (qua
// /dang-ky, đặt mật khẩu qua /dat-mat-khau), đăng nhập, rồi gửi đơn trên màn nhận diện của nhóm B.
// Màn claim chỉ được DÙNG ở đây, không kiểm — đó là kịch bản của nhóm B.
// Env: NTE_ID, NTE_NAME, NTE_MODE=existing|new, NTE_PERSON (existing), NTE_FULLNAME/NTE_YEAR/NTE_GENDER/
//      NTE_REL_QUERY/NTE_REL_MATCH/NTE_RELATION (new), NTE_PHONE, NTE_KIND
import { open, login, shot, mainText, loadState, saveState, registerAccount, pickPerson, verdict, APP } from "./nt-e-lib.mjs";
const E = process.env;
const kind = E.NTE_KIND ?? "desktop";
const tag = E.NTE_ID.replace(/[@.]/g, "_");
const { browser, page, p, log } = await open(kind, "dựng " + E.NTE_ID);
try {
  const st = loadState();
  const pw = await registerAccount(page, st.clanCode, E.NTE_ID, E.NTE_NAME);
  p("đăng ký + đặt mật khẩu xong: " + (await mainText(page)).slice(0, 120));
  await login(page, E.NTE_ID, pw);
  const intro = E.NTE_INTRO ?? "Tôi là con thứ hai trong nhà, quê Đại Lan, Nam Định. Kịch bản nghiệm thu nhóm E, xin gọi số này để xác nhận.";
  if (E.NTE_MODE === "existing") {
    await page.goto(APP + "/vi/nhan-dien?nguoi=" + E.NTE_PERSON, { waitUntil: "networkidle" });
    await page.getByLabel("Số điện thoại").fill(E.NTE_PHONE);
    await page.getByLabel("Vài dòng tự giới thiệu").fill(intro);
  } else {
    await page.goto(APP + "/vi/nhan-dien/chua-co", { waitUntil: "networkidle" });
    await page.getByLabel("Họ và tên").fill(E.NTE_FULLNAME);
    await page.getByLabel("Năm sinh").fill(E.NTE_YEAR);
    await page.getByText(E.NTE_GENDER ?? "Nữ", { exact: true }).click();
    await page.getByLabel("Số điện thoại").fill(E.NTE_PHONE);
    await pickPerson(page, page.getByRole("combobox", { name: /Người thân ấy là ai/ }), E.NTE_REL_QUERY, E.NTE_REL_MATCH);
    await page.getByText(E.NTE_RELATION ?? "Vợ / chồng của tôi", { exact: true }).click();
    await page.getByLabel("Vài dòng tự giới thiệu").fill(intro);
  }
  const resP = page.waitForResponse((r) => /\/person-claims\/(existing|new-person)$/.test(r.url()));
  await page.getByRole("button", { name: "Gửi đơn cho Trưởng chi" }).click();
  const r = await resP; const j = await r.json().catch(() => ({}));
  p(`POST ${new URL(r.url()).pathname} -> ${r.status()} id=${j.id ?? j.claim?.id}`);
  await page.waitForTimeout(1500);
  await shot(page, `03-don-${tag}`);
  const accounts = st.accounts ?? {};
  accounts[E.NTE_ID] = { password: pw, claimId: j.id ?? j.claim?.id, mode: E.NTE_MODE, person: E.NTE_PERSON, fullName: E.NTE_FULLNAME };
  saveState({ accounts });
} catch (e) { p("LỖI SCRIPT: " + e.message); await shot(page, `03-loi-${tag}`); p((await mainText(page)).slice(0, 600)); }
finally { verdict(log, p); await browser.close(); }
