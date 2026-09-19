#!/usr/bin/env node
/**
 * Cổng 1 — kiểm chứng rằng `mvn test` thật sự đã CHẠY bộ test tích hợp.
 *
 * Vì sao cần bước này: mọi lớp `*IT` đều gắn
 * `@EnabledIf("...AbstractIntegrationTest#dockerAvailable")`. Khi runner không
 * thấy Docker, JUnit BỎ QUA chúng trong im lặng và `mvn test` vẫn trả về 0 —
 * cổng xanh trong khi ~60 ca chạm PostgreSQL + Apache AGE thật chưa hề chạy.
 * Đó đúng là kiểu "xanh vì không kiểm gì" mà CI này sinh ra để chặn.
 *
 * Dùng: node .github/ci/check-surefire.mjs <thư-mục-surefire-reports>
 */
import fs from "node:fs";
import path from "node:path";

const thuMuc = process.argv[2];
if (!thuMuc) {
  console.error("Dùng: node check-surefire.mjs <thư-mục-surefire-reports>");
  process.exit(2);
}
if (!fs.existsSync(thuMuc)) {
  console.error(`Không thấy ${thuMuc} — surefire chưa sinh báo cáo nào. Coi như KHÔNG ĐẠT.`);
  process.exit(1);
}

const NGUONG_TONG_SO_CA = Number(process.env.BACKEND_MIN_TESTS ?? 2000);

const tep = fs.readdirSync(thuMuc).filter((f) => f.startsWith("TEST-") && f.endsWith(".xml"));
if (tep.length === 0) {
  console.error(`Không có TEST-*.xml trong ${thuMuc}. KHÔNG ĐẠT.`);
  process.exit(1);
}

const so = (xml, ten) => {
  const m = xml.match(new RegExp(ten + '="([0-9]+)"'));
  return m ? Number(m[1]) : 0;
};
const tenLop = (xml, tenTep) => {
  const m = xml.match(/<testsuite[^>]*\bname="([^"]+)"/);
  return m ? m[1] : tenTep;
};

let tong = 0;
let hong = 0;
let sai = 0;
let boQua = 0;
const lopIT = [];

for (const f of tep) {
  const xml = fs.readFileSync(path.join(thuMuc, f), "utf8");
  const t = so(xml, "tests");
  const fa = so(xml, "failures");
  const er = so(xml, "errors");
  const sk = so(xml, "skipped");
  tong += t;
  hong += fa;
  sai += er;
  boQua += sk;

  const lop = tenLop(xml, f);
  if (lop.endsWith("IT") || lop.endsWith("ApplicationTests")) {
    lopIT.push({ lop, t, sk, chay: t - sk });
  }
}

const caITDaChay = lopIT.reduce((a, x) => a + x.chay, 0);
const lopITBiBoQuaHoanToan = lopIT.filter((x) => x.t > 0 && x.chay === 0);

console.log("=== Cổng 1 · đối chiếu báo cáo surefire ===");
console.log(`Tổng số ca        : ${tong} (ngưỡng tối thiểu ${NGUONG_TONG_SO_CA})`);
console.log(`Hỏng / lỗi        : ${hong} / ${sai}`);
console.log(`Bỏ qua            : ${boQua}`);
console.log(`Ca tích hợp đã chạy: ${caITDaChay} trên ${lopIT.length} lớp *IT / *ApplicationTests`);

const loi = [];
if (hong > 0 || sai > 0) loi.push(`có ${hong} ca hỏng và ${sai} ca lỗi.`);
if (tong < NGUONG_TONG_SO_CA) {
  loi.push(`chỉ chạy ${tong} ca, dưới ngưỡng ${NGUONG_TONG_SO_CA} — bộ test đang thiếu.`);
}
if (lopITBiBoQuaHoanToan.length > 0) {
  loi.push(
    `${lopITBiBoQuaHoanToan.length} lớp tích hợp bị bỏ qua TOÀN BỘ — gần như chắc chắn runner không thấy ` +
      `Docker, nên PostgreSQL + Apache AGE thật chưa hề được chạm tới:` +
      lopITBiBoQuaHoanToan.map((x) => `\n    - ${x.lop} (${x.t} ca, bỏ qua hết)`).join("")
  );
}

if (loi.length > 0) {
  console.error("\n--- CỔNG 1 KHÔNG ĐẠT ---");
  for (const l of loi) console.error(`  * ${l}`);
  process.exit(1);
}

console.log("\nĐẠT: không có ca hỏng/lỗi, và bộ test tích hợp đã chạy thật trên Docker.");
