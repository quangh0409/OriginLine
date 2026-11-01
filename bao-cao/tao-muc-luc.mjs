#!/usr/bin/env node
// Sinh lại bao-cao/index.html từ các thư mục ngày YYYY-MM-DD.
//
// Chạy:  node bao-cao/tao-muc-luc.mjs          — ghi index.html
//        node bao-cao/tao-muc-luc.mjs --kiem   — chỉ kiểm, thoát mã 1 nếu có lỗi
//                                               hoặc index.html đã cũ (dùng trước commit)
//
// Không phụ thuộc gói npm nào. Mỗi báo cáo tự khai báo siêu dữ liệu trong <head>:
//   <title>…</title>
//   <meta name="bao-cao:loai"   content="hien-trang">   (bắt buộc, xem LOAI bên dưới)
//   <meta name="bao-cao:tom-tat" content="…">          (bắt buộc, một câu)
//   <meta name="bao-cao:thay-the" content="2026-10-01/hien-trang-chuc-nang.html">  (tuỳ chọn)

import { readdirSync, readFileSync, writeFileSync, statSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const GOC = dirname(fileURLToPath(import.meta.url));
const CHE_DO_KIEM = process.argv.includes("--kiem");

export const LOAI = {
  "hien-trang": "Hiện trạng",
  "nghiem-thu": "Nghiệm thu",
  "danh-gia": "Đánh giá / review",
  "su-co": "Sự cố",
  "tien-do": "Tiến độ",
  "kiem-thu": "Kết quả kiểm thử",
};

const NGAY = /^\d{4}-\d{2}-\d{2}$/;
const TEN_TEP = /^[a-z0-9]+(-[a-z0-9]+)*\.html$/;

const thoat = (s) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const giaiMa = (s) =>
  s.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, "&");

function docMeta(html, ten) {
  const m = html.match(new RegExp(`<meta\\s+name="bao-cao:${ten}"\\s+content="([^"]*)"`, "i"));
  return m ? giaiMa(m[1]).trim() : "";
}

const loi = [];
const baoCao = [];

for (const ngay of readdirSync(GOC).sort().reverse()) {
  const thuMuc = join(GOC, ngay);
  if (!statSync(thuMuc).isDirectory() || ngay.startsWith("_")) continue;
  if (!NGAY.test(ngay) || Number.isNaN(Date.parse(ngay))) {
    loi.push(`${ngay}/: tên thư mục phải là ngày YYYY-MM-DD`);
    continue;
  }
  const cacTep = readdirSync(thuMuc).sort();
  // Bản xuất kèm (Excel, PDF…) phải trùng tên với một báo cáo .html — HTML là bản gốc
  // mang siêu dữ liệu, bản xuất chỉ là định dạng khác của cùng báo cáo ấy.
  for (const tep of cacTep) {
    const m = tep.match(/^(.*)\.(xlsx|pdf|docx)$/);
    if (m && !cacTep.includes(`${m[1]}.html`))
      loi.push(`${ngay}/${tep}: bản xuất phải đi kèm báo cáo cùng tên ${m[1]}.html`);
  }
  for (const tep of cacTep) {
    if (!tep.endsWith(".html")) continue; // ảnh, tệp đính kèm đi cùng báo cáo
    const duongDan = `${ngay}/${tep}`;
    if (!TEN_TEP.test(tep)) loi.push(`${duongDan}: tên tệp phải là chữ thường không dấu, nối bằng '-'`);
    const html = readFileSync(join(thuMuc, tep), "utf8");
    const tieuDe = giaiMa((html.match(/<title>([^<]*)<\/title>/i) || [, ""])[1].trim());
    const loai = docMeta(html, "loai");
    const tomTat = docMeta(html, "tom-tat");
    const thayThe = docMeta(html, "thay-the");
    if (!tieuDe) loi.push(`${duongDan}: thiếu <title>`);
    if (!LOAI[loai]) loi.push(`${duongDan}: bao-cao:loai="${loai}" không thuộc ${Object.keys(LOAI).join(", ")}`);
    if (!tomTat) loi.push(`${duongDan}: thiếu bao-cao:tom-tat`);
    if (thayThe && !existsSync(join(GOC, thayThe))) loi.push(`${duongDan}: bao-cao:thay-the trỏ tới tệp không tồn tại: ${thayThe}`);
    const goc = tep.slice(0, -".html".length);
    const banXuat = cacTep
      .filter((t) => new RegExp(`^${goc.replace(/[-]/g, "\\-")}\\.(xlsx|pdf|docx)$`).test(t))
      .map((t) => ({ duongDan: `${ngay}/${t}`, dinhDang: t.split(".").pop().toUpperCase() }));
    baoCao.push({ ngay, duongDan, tieuDe, loai, tomTat, thayThe, banXuat });
  }
}

// Báo cáo nào bị một báo cáo mới hơn thay thế thì đánh dấu "đã cũ".
const biThayThe = new Map(baoCao.filter((b) => b.thayThe).map((b) => [b.thayThe, b.duongDan]));

const theoThang = new Map();
for (const b of baoCao) {
  const thang = b.ngay.slice(0, 7);
  if (!theoThang.has(thang)) theoThang.set(thang, new Map());
  const ngayMap = theoThang.get(thang);
  if (!ngayMap.has(b.ngay)) ngayMap.set(b.ngay, []);
  ngayMap.get(b.ngay).push(b);
}

const dinhDangNgay = (iso) => {
  const [y, m, d] = iso.split("-");
  return `${d}/${m}/${y}`;
};

const dong = (b) => {
  const moi = biThayThe.get(b.duongDan);
  return `      <li class="${moi ? "cu" : ""}">
        <span class="loai loai-${b.loai}">${thoat(LOAI[b.loai] || b.loai)}</span>
        <a href="${thoat(b.duongDan)}">${thoat(b.tieuDe)}</a>${b.banXuat
          .map((x) => ` <a class="xuat" href="${thoat(x.duongDan)}" download>${x.dinhDang}</a>`)
          .join("")}${moi ?` <span class="nhan">đã cũ — xem <a href="${thoat(moi)}">bản mới</a></span>` : ""}
        <p>${thoat(b.tomTat)}</p>
      </li>`;
};

const thanThang = [...theoThang]
  .map(
    ([thang, ngayMap]) => `  <section>
    <h2>Tháng ${thang.slice(5)}/${thang.slice(0, 4)}</h2>
${[...ngayMap]
  .map(
    ([ngay, ds]) => `    <h3 id="${ngay}">${dinhDangNgay(ngay)}</h3>
    <ul>
${ds.map(dong).join("\n")}
    </ul>`,
  )
  .join("\n")}
  </section>`,
  )
  .join("\n");

const html = `<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Mục lục báo cáo — Gia Phả Dòng Họ</title>
<!-- TỆP SINH TỰ ĐỘNG bởi tao-muc-luc.mjs — đừng sửa tay, sửa báo cáo rồi chạy lại script. -->
<style>
  body{margin:0;background:#fbf7f0;color:#2b2118;font:15px/1.55 "Segoe UI",system-ui,sans-serif}
  main{max-width:880px;margin:0 auto;padding:32px 20px 64px}
  h1{color:#8a2b1d;margin:0 0 4px;font-size:26px}
  h2{color:#8a2b1d;font-size:19px;border-bottom:2px solid #e6dccd;padding-bottom:6px;margin-top:32px}
  h3{font-size:15px;color:#6b5d4f;margin:18px 0 6px}
  ul{list-style:none;padding:0;margin:0}
  li{background:#fff;border:1px solid #e6dccd;border-radius:8px;padding:10px 14px;margin:8px 0}
  li.cu{opacity:.6}
  li a{font-weight:600;color:#8a2b1d;text-decoration:none} li a:hover{text-decoration:underline}
  li p{margin:4px 0 0;color:#6b5d4f;font-size:14px}
  .meta{color:#6b5d4f;font-size:13px}
  .loai{display:inline-block;font-size:11.5px;font-weight:600;padding:1px 8px;border-radius:10px;margin-right:6px;background:#efe8dc;color:#6b5d4f}
  .loai-hien-trang{background:#e3ecf7;color:#22507f} .loai-nghiem-thu{background:#e7f2e8;color:#2f6b3a}
  .loai-danh-gia{background:#f1e6f5;color:#6b2f7f} .loai-su-co{background:#f8e3e1;color:#9b2c2c}
  .loai-tien-do{background:#fbf0d9;color:#8a5a00} .loai-kiem-thu{background:#e2f3f1;color:#1f6b63}
  .nhan{font-size:12px;color:#9b2c2c}
  a.xuat{font-size:11.5px;font-weight:600;color:#2f6b3a;border:1px solid #b9d6bd;border-radius:4px;padding:0 6px;margin-left:4px}
</style>
</head>
<body>
<main>
  <h1>Mục lục báo cáo</h1>
  <div class="meta">${baoCao.length} báo cáo · mới nhất trước · quy ước xem <code>README.md</code></div>
${thanThang}
</main>
</body>
</html>
`;

if (loi.length) {
  console.error("Báo cáo sai quy ước:\n  - " + loi.join("\n  - "));
  process.exit(1);
}

const tepMucLuc = join(GOC, "index.html");
if (CHE_DO_KIEM) {
  const cu = existsSync(tepMucLuc) ? readFileSync(tepMucLuc, "utf8").replace(/\r\n/g, "\n") : "";
  if (cu !== html) {
    console.error("index.html đã cũ — chạy: node bao-cao/tao-muc-luc.mjs");
    process.exit(1);
  }
  console.log(`OK — ${baoCao.length} báo cáo, mục lục khớp.`);
} else {
  writeFileSync(tepMucLuc, html, "utf8");
  console.log(`Đã ghi index.html — ${baoCao.length} báo cáo.`);
}
