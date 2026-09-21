#!/usr/bin/env node
/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ĐO THẬT TRÊN TRÌNH DUYỆT — không phải đếm px trên khung dây
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * `design/06-dang-nhap` tự khai ở §0 rằng MỌI con số px trong nó là "đề xuất
 * VẼ RA, không phải số đo từ trình duyệt". Tệp này biến chúng thành số đo thật.
 *
 * Bốn thứ được đo, và cả bốn đều là ràng buộc đã viết ra trước:
 *
 *   1. NGÂN SÁCH CHIỀU CAO (06 §9) — đáy nút chính cách đáy ô mật khẩu
 *      ≤ 120px. Quá ngưỡng thì trên điện thoại, sau khi trình duyệt cuộn ô mật
 *      khẩu vào tầm nhìn, nút chính rơi xuống dưới mép bàn phím ảo và người
 *      dùng gõ xong mật khẩu rồi không thấy nút đâu. Lỗi IM LẶNG.
 *   2. SÀN CỠ CHỮ (00 §2.2) — mọi chữ người dùng đọc ≥ 16px, không ngoại lệ.
 *   3. SÀN VÙNG CHẠM (00 §2.2, WCAG 2.2) — mọi thứ bấm được ≥ 44×44px, đo
 *      HỘP BAO chứ không đo chữ.
 *   4. TƯƠNG PHẢN (WCAG AA) — ≥ 4.5:1 cho chữ thường, ≥ 3:1 cho chữ lớn
 *      (≥ 24px, hoặc ≥ 18.66px khi đậm).
 *
 * Đo ở khung 400px, CẢ CHẾ ĐỘ SÁNG LẪN TỐI. Chế độ tối không phải trang trí:
 * `prefers-color-scheme` do hệ điều hành quyết, người dùng không bật nó trong
 * sản phẩm này, nên một lỗi tương phản ở chế độ tối là một lỗi mà nửa số người
 * dùng gặp và không ai báo cáo được.
 *
 * ── CHẠY ───────────────────────────────────────────────────────────────────
 *   cd infra && docker compose up -d keycloak
 *   node infra/keycloak/do-chieu-cao.mjs
 *
 * Trả 0 khi mọi ràng buộc đạt, 1 khi có cái trượt.
 *
 * Playwright lấy từ `frontend/node_modules` — cố ý KHÔNG thêm một
 * `package.json` thứ hai vào `infra/` chỉ để có một thư viện. Chưa cài frontend
 * thì script nói thẳng và thoát 0 (bỏ qua), chứ không đỏ vì một lý do không
 * liên quan tới theme.
 */

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { pathToFileURL } from "node:url";

const thuMuc = path.dirname(fileURLToPath(import.meta.url));
const goc = path.resolve(thuMuc, "..", "..");
const duongPlaywright = path.join(goc, "frontend", "node_modules", "playwright", "index.mjs");

if (!fs.existsSync(duongPlaywright)) {
  console.log("⊘ Bỏ qua: chưa có Playwright ở frontend/node_modules.");
  console.log("  cd frontend && npm install");
  process.exit(0);
}

const { chromium } = await import(pathToFileURL(duongPlaywright).href);

const GOC = process.env.KEYCLOAK_URL || "http://127.0.0.1:8081";
const REALM = process.env.KEYCLOAK_REALM || "giapha";
const KHUNG = { width: 400, height: 800 };
/** 06 §9: đáy nút chính cách đáy ô mật khẩu không quá ngần này. */
const TRAN_NGAN_SACH = 120;

/** Dựng URL uỷ quyền đầy đủ; client `giapha-frontend` bắt buộc PKCE S256. */
function duongDan(loai) {
  const verifier = crypto.randomBytes(32).toString("base64url");
  const challenge = crypto.createHash("sha256").update(verifier).digest("base64url");
  const q = new URLSearchParams({
    client_id: "giapha-frontend",
    response_type: "code",
    scope: "openid",
    redirect_uri: "http://localhost:3000/",
    code_challenge: challenge,
    code_challenge_method: "S256",
    state: "do-chieu-cao",
  });
  return `${GOC}/realms/${REALM}/protocol/openid-connect/${loai}?${q}`;
}

/**
 * Hàm chạy TRONG trang. Tự chứa, không tham chiếu gì bên ngoài — Playwright
 * tuần tự hoá nó sang trình duyệt.
 */
function doTrongTrang() {
  const kenh = (mau) => {
    const m = mau.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const p = m[1].split(",").map((x) => parseFloat(x));
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  };

  /** Nền THẬT SỰ nhìn thấy: leo lên cho tới khi gặp một lớp không trong suốt. */
  const nenThat = (el) => {
    let n = el;
    while (n && n !== document.documentElement) {
      const c = kenh(getComputedStyle(n).backgroundColor);
      if (c && c.a > 0.95) return c;
      n = n.parentElement;
    }
    const c = kenh(getComputedStyle(document.body).backgroundColor);
    return c && c.a > 0.95 ? c : { r: 255, g: 255, b: 255, a: 1 };
  };

  const sang = (c) => {
    const f = (v) => {
      const s = v / 255;
      return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b);
  };

  const tuongPhan = (a, b) => {
    const l1 = sang(a);
    const l2 = sang(b);
    return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
  };

  const nhinThay = (el) => {
    const s = getComputedStyle(el);
    if (s.display === "none" || s.visibility === "hidden" || s.opacity === "0") return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };

  const chuTrucTiep = (el) =>
    Array.from(el.childNodes).some((n) => n.nodeType === 3 && n.textContent.trim().length > 0);

  // ── 1. Ngân sách chiều cao ──────────────────────────────────────────────
  const oMatKhau = document.querySelector("#password") || document.querySelector("#password-new");
  const nutChinh = document.querySelector(".gp-nut--chinh");
  let nganSach = null;
  if (oMatKhau && nutChinh) {
    nganSach = Math.round(
      nutChinh.getBoundingClientRect().bottom - oMatKhau.getBoundingClientRect().bottom
    );
  }

  // ── 2. Cỡ chữ ───────────────────────────────────────────────────────────
  const chuNho = [];
  for (const el of document.querySelectorAll(".gp-the *, .gp-khach *, .gp-goi *, .gp-chan *, .gp-thanh *")) {
    if (!nhinThay(el) || !chuTrucTiep(el)) continue;
    const co = parseFloat(getComputedStyle(el).fontSize);
    if (co < 16) {
      chuNho.push({
        the: el.tagName.toLowerCase() + (el.className ? "." + String(el.className).split(" ")[0] : ""),
        co: Math.round(co * 10) / 10,
        chu: el.textContent.trim().slice(0, 40),
      });
    }
  }

  // ── 3. Vùng chạm ────────────────────────────────────────────────────────
  const chamNho = [];
  for (const el of document.querySelectorAll("a[href], button, input:not([type=hidden]), label.gp-chon, select")) {
    if (!nhinThay(el)) continue;
    const r = el.getBoundingClientRect();
    // Ô đánh dấu nằm TRONG một nhãn ≥44px thì vùng chạm thật là cái nhãn.
    if (el.type === "checkbox" && el.closest("label")) {
      const rn = el.closest("label").getBoundingClientRect();
      if (rn.height >= 44 && rn.width >= 44) continue;
    }
    if (r.height < 44 || r.width < 44) {
      chamNho.push({
        the: el.tagName.toLowerCase() + (el.id ? "#" + el.id : "") + (el.className ? "." + String(el.className).split(" ")[0] : ""),
        rong: Math.round(r.width),
        cao: Math.round(r.height),
        chu: (el.textContent || el.value || "").trim().slice(0, 30),
      });
    }
  }

  // ── 4. Tương phản ───────────────────────────────────────────────────────
  const tpTruot = [];
  for (const el of document.querySelectorAll(".gp-the *, .gp-khach *, .gp-goi *, .gp-chan *, .gp-thanh *")) {
    if (!nhinThay(el) || !chuTrucTiep(el)) continue;
    const s = getComputedStyle(el);
    const chu = kenh(s.color);
    if (!chu || chu.a < 0.95) continue;
    const co = parseFloat(s.fontSize);
    const dam = parseInt(s.fontWeight, 10) >= 600;
    const chuLon = co >= 24 || (co >= 18.66 && dam);
    const can = chuLon ? 3 : 4.5;
    const ti = tuongPhan(chu, nenThat(el));
    if (ti < can) {
      tpTruot.push({
        the: el.tagName.toLowerCase() + (el.className ? "." + String(el.className).split(" ")[0] : ""),
        ti: Math.round(ti * 100) / 100,
        can,
        co: Math.round(co * 10) / 10,
        chu: el.textContent.trim().slice(0, 40),
      });
    }
  }

  return { nganSach, chuNho, chamNho, tpTruot };
}

const trang = [
  { ten: "đăng nhập", url: duongDan("auth"), doNganSach: true },
  { ten: "đăng ký", url: duongDan("registrations"), doNganSach: false },
];

const trinhDuyet = await chromium.launch();
let coLoi = false;

for (const cheDo of ["light", "dark"]) {
  const ctx = await trinhDuyet.newContext({ viewport: KHUNG, colorScheme: cheDo });
  for (const t of trang) {
    const pg = await ctx.newPage();
    try {
      await pg.goto(t.url, { waitUntil: "networkidle" });
    } catch {
      console.error(`✗ Không mở được trang ${t.ten}. Keycloak đã chạy chưa? (${GOC})`);
      process.exit(1);
    }
    const kq = await pg.evaluate(doTrongTrang);
    const nhan = `[${KHUNG.width}px · ${cheDo === "light" ? "sáng" : "tối"} · ${t.ten}]`;

    if (t.doNganSach) {
      if (kq.nganSach === null) {
        console.error(`${nhan} ✗ không thấy ô mật khẩu hoặc nút chính để đo`);
        coLoi = true;
      } else {
        const dat = kq.nganSach <= TRAN_NGAN_SACH;
        console.log(
          `${nhan} ngân sách chiều cao: ${kq.nganSach}px / trần ${TRAN_NGAN_SACH}px ` +
            (dat ? `→ còn dư ${TRAN_NGAN_SACH - kq.nganSach}px ✓` : "→ VƯỢT TRẦN ✗")
        );
        if (!dat) coLoi = true;
      }
    }

    const in4 = (ten, ds, viet) => {
      if (ds.length === 0) {
        console.log(`${nhan} ${ten}: đạt ✓`);
        return;
      }
      console.error(`${nhan} ${ten}: ${ds.length} chỗ TRƯỢT ✗`);
      for (const d of ds.slice(0, 6)) console.error("        " + viet(d));
      coLoi = true;
    };

    in4("cỡ chữ ≥ 16px", kq.chuNho, (d) => `${d.the} ${d.co}px — "${d.chu}"`);
    in4("vùng chạm ≥ 44×44", kq.chamNho, (d) => `${d.the} ${d.rong}×${d.cao} — "${d.chu}"`);
    in4("tương phản AA", kq.tpTruot, (d) => `${d.the} ${d.ti}:1 (cần ${d.can}) ${d.co}px — "${d.chu}"`);

    await pg.close();
  }
  await ctx.close();
}

await trinhDuyet.close();

if (coLoi) {
  console.error("\n✗ Có ràng buộc bị trượt. Xem từng dòng ở trên.");
  process.exit(1);
}
console.log("\n✓ Cả bốn ràng buộc đạt, ở khung 400px, cả chế độ sáng lẫn tối.");
