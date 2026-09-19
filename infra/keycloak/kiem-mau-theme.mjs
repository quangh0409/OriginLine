#!/usr/bin/env node
/**
 * PHÉP KIỂM CHẶN TRÔI LỆCH giữa theme Keycloak và bảng màu của sản phẩm.
 *
 * `design/06-dang-nhap` §3 gọi đây là cái giá phải trả của "đường 3" (theme
 * Keycloak riêng + các màn phụ bằng Next.js): bảng màu và cỡ chữ phải CHÉP sang
 * CSS của theme, vì `frontend/src/styles/tokens.ts` không với tới trang Keycloak
 * được — hai tiến trình khác nhau, hai bộ tài nguyên tĩnh khác nhau.
 *
 * Nguyên văn tài liệu: "Không có phép kiểm này thì sau sáu tháng hai bên sẽ lệch
 * màu, và NGƯỜI DÙNG SẼ NHÌN THẤY SỰ LỆCH ẤY Ở ĐÚNG GIÂY HỌ CẦN TIN TƯỞNG NHẤT."
 *
 * Chạy:  node infra/keycloak/kiem-mau-theme.mjs
 * Trả 0 nếu khớp, 1 nếu lệch. Không cần cài gì — chỉ đọc hai tệp bằng regex,
 * KHÔNG dựng frontend, KHÔNG cần Docker, KHÔNG cần Keycloak đang chạy. Đó là lý
 * do nó viết bằng regex chứ không import tokens.ts: một phép kiểm chỉ chạy được
 * khi cả ngăn xếp đã lên là một phép kiểm không ai chạy.
 *
 * Gắn vào CI ở cổng "frontend static" của `.github/workflows/ci.yml`.
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const thuMuc = path.dirname(fileURLToPath(import.meta.url));
const goc = path.resolve(thuMuc, "..", "..");

const duongDanTokens = path.join(goc, "frontend", "src", "styles", "tokens.ts");
const duongDanCss = path.join(
  thuMuc,
  "themes",
  "giapha",
  "login",
  "resources",
  "css",
  "giapha.css"
);

/** Bốn giá trị tài liệu 06 §3 nêu đích danh, cộng phần còn lại của bảng màu. */
const BAT_BUOC = ["primary", "bgPage", "bgCard", "textMain"];

const hexSangKenh = (hex) => {
  const h = hex.replace("#", "");
  const day = h.length === 3 ? h.split("").map((c) => c + c).join("") : h;
  return [0, 2, 4].map((i) => parseInt(day.slice(i, i + 2), 16)).join(" ");
};

const camelSangKebab = (ten) => ten.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);

/** Đọc một khối `export const X = { ... } as const` trong tokens.ts. */
function docBangMau(nguon, tenBien) {
  const batDau = nguon.indexOf(`export const ${tenBien}`);
  if (batDau < 0) throw new Error(`Không thấy \`${tenBien}\` trong tokens.ts`);
  const moc = nguon.indexOf("{", batDau);
  const ket = nguon.indexOf("} as const", moc);
  const than = nguon.slice(moc, ket);
  const bang = {};
  for (const m of than.matchAll(/(\w+)\s*:\s*"(#[0-9a-fA-F]{3,8})"/g)) {
    bang[m[1]] = m[2].toLowerCase();
  }
  return bang;
}

/** Đọc các dòng `--rgb-x: a b c;` trong một khối CSS. */
function docKenhCss(khoi) {
  const bang = {};
  for (const m of khoi.matchAll(/--rgb-([a-z0-9-]+)\s*:\s*([\d\s]+?)\s*;/g)) {
    bang[m[1]] = m[2].replace(/\s+/g, " ").trim();
  }
  return bang;
}

const nguonTokens = fs.readFileSync(duongDanTokens, "utf8");
const nguonCss = fs.readFileSync(duongDanCss, "utf8");

const sang = docBangMau(nguonTokens, "colorTokens");
const toi = docBangMau(nguonTokens, "darkColorTokens");

// Khối `:root` đầu tiên là chế độ sáng; khối trong @media dark là chế độ tối.
//
// Bắt buộc neo đầu dòng (`^` + cờ `m`): chuỗi "@media (prefers-color-scheme:
// dark)" cũng xuất hiện trong CHÚ THÍCH ở đầu tệp, và một `indexOf` trần sẽ cắt
// nhầm ở đó — làm khối "sáng" rỗng và phép kiểm báo THIẾU cả bốn màu bắt buộc.
// Một phép kiểm chặn trôi lệch mà tự báo sai thì chẳng ai còn tin nó.
const khopMediaToi = /^@media \(prefers-color-scheme: dark\)/m.exec(nguonCss);
const viTriMediaToi = khopMediaToi ? khopMediaToi.index : -1;
if (viTriMediaToi < 0) {
  console.error("LỖI: giapha.css không có khối @media (prefers-color-scheme: dark).");
  console.error("     00 §3: chế độ tối là một trong ba trạng thái bắt buộc.");
  process.exit(1);
}
const cssSang = docKenhCss(nguonCss.slice(0, viTriMediaToi));
const cssToi = docKenhCss(nguonCss.slice(viTriMediaToi));

const loi = [];
const boQua = [];

for (const [nhan, bangTs, bangCss] of [
  ["sáng", sang, cssSang],
  ["tối", toi, cssToi],
]) {
  for (const [ten, hex] of Object.entries(bangTs)) {
    const khoa = camelSangKebab(ten);
    const mong = hexSangKenh(hex);
    const thuc = bangCss[khoa];

    if (thuc === undefined) {
      // Theme cố tình KHÔNG dùng hết bảng màu của sản phẩm (không có phả đồ,
      // không có badge). Thiếu một token không-bắt-buộc là chấp nhận được;
      // thiếu một trong bốn token BAT_BUOC thì không.
      if (BAT_BUOC.includes(ten)) {
        loi.push(`[${nhan}] THIẾU --rgb-${khoa} trong giapha.css (bắt buộc phải có)`);
      } else {
        boQua.push(`${nhan}/${ten}`);
      }
      continue;
    }

    if (thuc !== mong) {
      loi.push(
        `[${nhan}] --rgb-${khoa}: giapha.css có "${thuc}", tokens.ts nói "${mong}" (${hex})`
      );
    }
  }
}

// ── Sàn của 00 §2.2, đo trên chính tệp CSS ─────────────────────────────────
const sanChu = nguonCss.match(/body\s*\{[^}]*?font-size:\s*(\d+)px/s);
if (!sanChu || Number(sanChu[1]) < 16) {
  loi.push(
    `Sàn cỡ chữ: \`body { font-size }\` trong giapha.css là ${
      sanChu ? sanChu[1] + "px" : "KHÔNG KHAI"
    }, phải ≥ 16px (00 §2.2, "không có ngoại lệ")`
  );
}

const sanCham = nguonCss.match(/--gp-cham:\s*(\d+)px/);
if (!sanCham || Number(sanCham[1]) < 44) {
  loi.push(
    `Sàn vùng chạm: \`--gp-cham\` là ${
      sanCham ? sanCham[1] + "px" : "KHÔNG KHAI"
    }, phải ≥ 44px (WCAG 2.2, 00 §2.2)`
  );
}

const caoO = nguonCss.match(/--gp-cao-o:\s*(\d+)px/);
if (!caoO || Number(caoO[1]) < 44) {
  loi.push(
    `Chiều cao ô nhập / nút chính: \`--gp-cao-o\` là ${
      caoO ? caoO[1] + "px" : "KHÔNG KHAI"
    }, phải ≥ 44px`
  );
}

// ── Kết quả ────────────────────────────────────────────────────────────────
if (loi.length) {
  console.error("✗ Theme Keycloak đã TRÔI khỏi bảng màu của sản phẩm:\n");
  for (const d of loi) console.error("   " + d);
  console.error(
    "\n  Sửa `infra/keycloak/themes/giapha/login/resources/css/giapha.css`" +
      "\n  cho khớp `frontend/src/styles/tokens.ts`. tokens.ts LUÔN thắng —" +
      "\n  nó là nguồn màu của sản phẩm, theme chỉ là bản chép."
  );
  process.exit(1);
}

console.log("✓ Theme Keycloak khớp tokens.ts.");
console.log(
  `  Đã đối chiếu ${Object.keys(cssSang).length} kênh màu chế độ sáng và ` +
    `${Object.keys(cssToi).length} kênh chế độ tối.`
);
console.log(
  `  Sàn: chữ ${sanChu[1]}px · vùng chạm ${sanCham[1]}px · ô nhập ${caoO[1]}px.`
);
if (boQua.length) {
  console.log(
    `  Không dùng trong theme (bỏ qua có chủ đích): ${boQua.length} token — ` +
      `${[...new Set(boQua.map((s) => s.split("/")[1]))].join(", ")}.`
  );
}
