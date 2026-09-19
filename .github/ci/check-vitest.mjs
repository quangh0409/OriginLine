#!/usr/bin/env node
/**
 * Cổng 2 — đối chiếu kết quả vitest với DANH SÁCH ĐỎ CÓ CHỦ Ý.
 *
 * Vì sao không để CI đỏ vĩnh viễn, và cũng không `it.skip` cho xong:
 *   - Để đỏ mãi thì sau vài tuần không ai nhìn màu nữa, và ca đỏ thứ năm — ca
 *     thật — lọt qua.
 *   - `skip` thì ca kiểm biến mất khỏi báo cáo; khi Hội đồng Tộc biểu quyết
 *     xong sẽ không còn gì nhắc người sửa quay lại.
 *
 * Nên cổng này đòi kết quả khớp CHÍNH XÁC danh sách trong
 * `.github/ci/vitest-expected-failures.json`:
 *   - đỏ ngoài danh sách        -> hỏng cổng (hồi quy thật)
 *   - trong danh sách mà đã XANH -> hỏng cổng (đã được quyết, hãy xoá khỏi danh sách)
 *   - trong danh sách mà KHÔNG TÌM THẤY -> hỏng cổng (ca bị đổi tên/xoá, lời nhắc mất)
 *   - tổng số ca tụt dưới ngưỡng -> hỏng cổng (chống "xanh vì chẳng chạy gì")
 *
 * Dùng: node .github/ci/check-vitest.mjs <results.json> <allowlist.json>
 */
import fs from "node:fs";
import path from "node:path";

const [, , resultsArg, allowlistArg] = process.argv;
if (!resultsArg || !allowlistArg) {
  console.error("Dùng: node check-vitest.mjs <results.json> <allowlist.json>");
  process.exit(2);
}

const doc = (p) => JSON.parse(fs.readFileSync(path.resolve(p), "utf8"));
const results = doc(resultsArg);
const allowlist = doc(allowlistArg);

/** Đường dẫn tuyệt đối của vitest -> đường dẫn tương đối gốc repo, luôn dùng "/". */
function chuanHoa(tenTep) {
  const slash = tenTep.split(String.fromCharCode(92)).join("/");
  const i = slash.lastIndexOf("/frontend/");
  return i >= 0 ? slash.slice(i + 1) : slash;
}

/** Khoá định danh một ca kiểm: tệp + tên đầy đủ (describe + it). */
const khoa = (tep, ten) => `${tep} >> ${ten}`;

const moiCa = new Map();
for (const tep of results.testResults ?? []) {
  const duongDan = chuanHoa(tep.name ?? "");
  for (const ca of tep.assertionResults ?? []) {
    moiCa.set(khoa(duongDan, ca.fullName), ca.status);
  }
}

const tongSo = results.numTotalTests ?? moiCa.size;
const soDo = results.numFailedTests ?? 0;

const duocPhepDo = new Map();
for (const muc of allowlist.caDoCoChuY ?? []) {
  duocPhepDo.set(khoa(muc.tep, muc.tenDayDu), muc);
}

const loi = [];
const canhBao = [];

// 1) Đỏ ngoài danh sách = hồi quy thật.
const doNgoaiDanhSach = [];
for (const [k, trangThai] of moiCa) {
  if (trangThai === "failed" && !duocPhepDo.has(k)) doNgoaiDanhSach.push(k);
}
if (doNgoaiDanhSach.length > 0) {
  loi.push(
    `${doNgoaiDanhSach.length} ca đỏ KHÔNG nằm trong danh sách có chủ ý — đây là hồi quy, phải sửa mã nguồn:` +
      doNgoaiDanhSach.map((k) => `\n    - ${k}`).join("")
  );
}

// 2) Trong danh sách mà đã xanh = lời nhắc đã hết hạn.
const daXanh = [];
// 3) Trong danh sách mà không tìm thấy = ca bị đổi tên hoặc xoá.
const khongThay = [];
for (const [k, muc] of duocPhepDo) {
  const trangThai = moiCa.get(k);
  if (trangThai === undefined) khongThay.push(`${k}  (lý do đang treo: ${muc.lyDo})`);
  else if (trangThai === "passed") daXanh.push(k);
  else if (trangThai !== "failed") canhBao.push(`${k} có trạng thái lạ: ${trangThai}`);
}
if (daXanh.length > 0) {
  loi.push(
    `${daXanh.length} ca trong danh sách đã XANH trở lại — Hội đồng đã quyết xong? Hãy xoá mục tương ứng khỏi ` +
      `.github/ci/vitest-expected-failures.json:` +
      daXanh.map((k) => `\n    - ${k}`).join("")
  );
}
if (khongThay.length > 0) {
  loi.push(
    `${khongThay.length} ca trong danh sách KHÔNG còn tồn tại (đổi tên hay bị xoá) — lời nhắc đang mất hiệu lực:` +
      khongThay.map((k) => `\n    - ${k}`).join("")
  );
}

// 4) Chống "xanh vì chẳng chạy gì".
const nguong = allowlist.soCaToiThieu ?? 0;
if (tongSo < nguong) {
  loi.push(
    `Chỉ chạy ${tongSo} ca, dưới ngưỡng tối thiểu ${nguong}. Bộ test đang thiếu — cổng này không nhận.`
  );
}

console.log("=== Cổng 2 · đối chiếu vitest ===");
console.log(`Tổng số ca      : ${tongSo} (ngưỡng tối thiểu ${nguong})`);
console.log(`Số ca đỏ        : ${soDo}`);
console.log(`Đỏ có chủ ý     : ${duocPhepDo.size} (khai báo trong vitest-expected-failures.json)`);
for (const c of canhBao) console.log(`CẢNH BÁO: ${c}`);

if (loi.length > 0) {
  console.error("\n--- CỔNG 2 KHÔNG ĐẠT ---");
  for (const l of loi) console.error(`  * ${l}`);
  process.exit(1);
}

console.log("\nĐẠT: tập ca đỏ khớp chính xác danh sách có chủ ý, không hơn không kém.");
