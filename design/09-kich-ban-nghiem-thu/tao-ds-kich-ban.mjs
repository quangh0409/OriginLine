#!/usr/bin/env node
// Nhúng danh sách kịch bản nghiệm thu (mã NT-…, tên, nhóm, ca biên) từ index.html vào ghi-loi-kiem-thu.html,
// để trang ghi lỗi có danh sách chọn mà vẫn mở được bằng file:// (trình duyệt chặn fetch tệp bên cạnh).
//
// Chạy:  node design/09-kich-ban-nghiem-thu/tao-ds-kich-ban.mjs           — ghi lại danh sách
//        node design/09-kich-ban-nghiem-thu/tao-ds-kich-ban.mjs --kiem    — chỉ kiểm, thoát mã 1 nếu đã cũ
//
// Chạy lại mỗi khi thêm, bỏ hay đổi tên một kịch bản trong index.html.
import { readFileSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const GOC = dirname(fileURLToPath(import.meta.url));
const NGUON = join(GOC, "index.html"), DICH = join(GOC, "ghi-loi-kiem-thu.html");
const giaiMa = s => s.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&");
const boThe = s => giaiMa(s.replace(/<[^>]+>/g, "")).replace(/\s+/g, " ").trim();

const html = readFileSync(NGUON, "utf8");
const ds = [];
let nhom = "";
const re = /<h2 id="[^"]+"[^>]*>([\s\S]*?)<\/h2>|<h3><span class="ma-so">(NT-[A-Z0-9-]+)<\/span>\s*—\s*([\s\S]*?)<\/h3>/g;
for (const m of html.matchAll(re)) {
  if (m[1] !== undefined) { nhom = boThe(m[1]); continue; }
  const caBien = /chip-cabien/.test(m[3]);
  ds.push({ ma: m[2], ten: boThe(m[3].replace(/<span class="chip[\s\S]*?<\/span>/g, "")), nhom, caBien });
}
if (!ds.length) { console.error("Không tìm thấy kịch bản NT-… nào trong index.html — cấu trúc trang đã đổi?"); process.exit(1); }
const trung = ds.map(k => k.ma).filter((m, i, a) => a.indexOf(m) !== i);
if (trung.length) { console.error("Mã kịch bản trùng: " + [...new Set(trung)].join(", ")); process.exit(1); }

const dich = readFileSync(DICH, "utf8");
const moi = dich.replace(/\/\*KICH_BAN_BAT_DAU\*\/[\s\S]*?\/\*KICH_BAN_KET_THUC\*\//,
  `/*KICH_BAN_BAT_DAU*/${JSON.stringify(ds)}/*KICH_BAN_KET_THUC*/`);
if (moi === dich && !dich.includes("/*KICH_BAN_BAT_DAU*/")) { console.error("ghi-loi-kiem-thu.html thiếu dấu /*KICH_BAN_BAT_DAU*/"); process.exit(1); }
if (process.argv.includes("--kiem")) {
  if (moi !== dich) { console.error("Danh sách kịch bản trong ghi-loi-kiem-thu.html đã cũ — chạy: node design/09-kich-ban-nghiem-thu/tao-ds-kich-ban.mjs"); process.exit(1); }
  console.log(`OK — ${ds.length} kịch bản, khớp.`);
} else {
  writeFileSync(DICH, moi, "utf8");
  console.log(`Đã nhúng ${ds.length} kịch bản (${[...new Set(ds.map(k => k.nhom))].length} nhóm) vào ghi-loi-kiem-thu.html.`);
}
