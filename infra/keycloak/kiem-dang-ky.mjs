#!/usr/bin/env node
/**
 * ═══════════════════════════════════════════════════════════════════════════
 * PHÉP KIỂM TĨNH CHO ĐĂNG KÝ · MÁY CHỦ THƯ · BỘ THÔNG ĐIỆP
 *
 * Chạy:  node infra/keycloak/kiem-dang-ky.mjs
 * Trả 0 khi đạt, 1 khi có chỗ trượt.
 *
 * KHÔNG cần Docker, KHÔNG cần Keycloak chạy, KHÔNG cần dựng frontend — cùng kỷ
 * luật với `kiem-mau-theme.mjs`: một phép kiểm chỉ chạy được khi cả ngăn xếp đã
 * lên là một phép kiểm không ai chạy. Gắn vào cổng "frontend static" của CI.
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Bảy điều nó giữ, và MỖI ĐIỀU ỨNG VỚI MỘT LỖI ĐÃ THẬT SỰ XẢY RA ở đây:
 *
 *  1. Bật đăng ký mà quên ô mã mời.
 *     `registrationAllowed: true` mà User Profile không có `maMoiDongHo` bắt
 *     buộc + có `pattern` nghĩa là cổng đang MỞ TOANG. Đây là chế độ hỏng tệ
 *     nhất của cả việc này, và nó trông y hệt lúc chạy đúng.
 *
 *  2. Bật "Quên mật khẩu" mà không có máy chủ thư.
 *     Đúng cái bẫy `design/06-dang-nhap` §1.2 #1 mô tả: liên kết hiện ra, người
 *     dùng bấm, nhận màn "đã gửi", rồi đợi một lá thư không tồn tại.
 *
 *  3. Thứ tự hành động bắt buộc bị đảo.
 *     Đã xảy ra: đặt VERIFY_EMAIL ưu tiên 50 trong khi UPDATE_PASSWORD là 30,
 *     nên Keycloak bắt đặt mật khẩu TRƯỚC khi xác minh thư — và lá thư xác minh
 *     không bao giờ được gửi. Số nhỏ chạy trước.
 *
 *  4. `requiredActions` khai thiếu.
 *     Danh sách ấy là TOÀN QUYỀN: khai thiếu một mục là gỡ mục ấy khỏi realm.
 *     README đã ghi một lần rằng `"requiredActions": []` làm UPDATE_PASSWORD
 *     biến mất khỏi realm.
 *
 *  5. Bản nhúng User Profile trôi khỏi nguồn đọc được.
 *     Xem `dong-goi-user-profile.mjs`.
 *
 *  6. Khoá thông điệp bị trỏ hụt.
 *     `user-profile-giapha.json` trỏ tới `${giaphaRegPhone}`; thiếu khoá ấy thì
 *     màn hình in ra nguyên văn "${giaphaRegPhone}" làm nhãn ô. Và thiếu ở MỘT
 *     ngôn ngữ thì chỉ người dùng ngôn ngữ ấy nhìn thấy.
 *
 *  7. Dấu nháy đơn không nhân đôi.
 *     Keycloak chạy mọi chuỗi theme qua `MessageFormat`, nơi một `'` đứng một
 *     mình là ký tự thoát và BỊ NUỐT IM LẶNG. Bản nháp đầu in ra "Each
 *     ancestors record" thay vì "Each ancestor's record".
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const thuMuc = path.dirname(fileURLToPath(import.meta.url));
const themeGoc = path.join(thuMuc, "themes", "giapha");
const loginGoc = path.join(themeGoc, "login");

const loi = [];
const nhac = [];
const doc = (p) => fs.readFileSync(p, "utf8");

// ═════════════════════════════════════════════════════════════════════════
// Đọc realm và cấu hình User Profile
// ═════════════════════════════════════════════════════════════════════════
const realm = JSON.parse(doc(path.join(thuMuc, "realm-giapha.json")));
const nguonHoSo = JSON.parse(doc(path.join(thuMuc, "user-profile-giapha.json")));

const LOAI_UP = "org.keycloak.userprofile.UserProfileProvider";
const nhungThoParsed = (() => {
  const s = realm.components?.[LOAI_UP]?.[0]?.config?.["kc.user.profile.config"]?.[0];
  if (!s) return null;
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
})();

// ── 5. Bản nhúng phải khớp nguồn ─────────────────────────────────────────
if (nhungThoParsed === null) {
  loi.push(
    "realm-giapha.json không có thành phần User Profile đọc được.\n" +
      "     Sinh lại: node infra/keycloak/dong-goi-user-profile.mjs"
  );
} else if (JSON.stringify(nhungThoParsed) !== JSON.stringify(nguonHoSo)) {
  loi.push(
    "Bản nhúng User Profile trong realm-giapha.json đã TRÔI khỏi\n" +
      "     user-profile-giapha.json. Sinh lại:\n" +
      "     node infra/keycloak/dong-goi-user-profile.mjs"
  );
}

// ── 1. Bật đăng ký thì phải có cổng mã mời ───────────────────────────────
const oMaMoi = nguonHoSo.attributes.find((a) => a.name === "maMoiDongHo");
if (realm.registrationAllowed === true) {
  if (!oMaMoi) {
    loi.push(
      'realm đặt "registrationAllowed": true nhưng User Profile KHÔNG có ô\n' +
        "     `maMoiDongHo`. Cổng đang mở toang: ai cũng tự tạo được tài khoản."
    );
  } else {
    if (!oMaMoi.required?.roles?.includes("user")) {
      loi.push(
        "Ô `maMoiDongHo` không bắt buộc (`required.roles` thiếu \"user\").\n" +
          "     Không bắt buộc nghĩa là bỏ trống vẫn đăng ký được."
      );
    }
    const mau = oMaMoi.validations?.pattern?.pattern;
    if (!mau) {
      loi.push(
        "Ô `maMoiDongHo` không có validator `pattern`. Không có mẫu thì mọi\n" +
          "     chuỗi đều qua — ô mã mời trở thành một ô ghi chú."
      );
    } else if (mau !== "(?!)") {
      // Mẫu trong kho PHẢI là mẫu đóng. Mã thật đi đường Admin API
      // (`phat-ma-moi.mjs`), không bao giờ đi đường tệp có trong git.
      loi.push(
        `Ô \`maMoiDongHo\` có mẫu "${mau}" nằm TRONG KHO.\n` +
          "     Mẫu trong kho phải luôn là `(?!)` — đóng sẵn. Mã thật phát bằng:\n" +
          "       GIAPHA_CLAN_INVITE_CODE='...' node infra/keycloak/phat-ma-moi.mjs --phat\n" +
          "     Một mã mời nằm trong git là một mã đã lộ."
      );
    }
    if (!oMaMoi.validations?.pattern?.["error-message"]) {
      nhac.push(
        "Ô `maMoiDongHo` không khai `error-message`, nên mã sai sẽ hiện câu\n" +
          "     chung `error-invalid-value` thay vì câu của dòng họ."
      );
    }
  }
}

// ── 2. Bật "Quên mật khẩu" thì phải có máy chủ thư ───────────────────────
const smtp = realm.smtpServer || {};
if (realm.resetPasswordAllowed === true || realm.verifyEmail === true) {
  const co = (k) => typeof smtp[k] === "string" && smtp[k].trim().length > 0;
  if (!co("host") || !co("from")) {
    loi.push(
      'realm bật "resetPasswordAllowed"/"verifyEmail" nhưng `smtpServer` thiếu\n' +
        "     `host` hoặc `from`. Liên kết hiện ra mà thư không bao giờ gửi thì tệ\n" +
        "     hơn là không có liên kết (design/06-dang-nhap §1.2 #1)."
    );
  }
  for (const k of ["user", "password"]) {
    if (co(k)) {
      loi.push(
        `\`smtpServer.${k}\` có giá trị nằm TRONG KHO. Bí mật máy chủ thư không\n` +
          "     bao giờ được commit — cấu hình ở admin console hoặc Admin API, lấy\n" +
          "     từ vault. Xem README mục \"Máy chủ thư\"."
      );
    }
  }
  if (/\$\{env\./.test(JSON.stringify(smtp))) {
    loi.push(
      "`smtpServer` còn dùng cú pháp `${env....}`. ĐÃ ĐO trên Keycloak 26.7.2:\n" +
        "     realm import KHÔNG đọc biến môi trường — `${env.X:mặc định}` luôn ra\n" +
        '     "mặc định", `${env.X}` giữ nguyên nguyên văn. Cú pháp ấy trông như\n' +
        "     đọc môi trường nhưng không bao giờ đọc."
    );
  }
}

// ── 3 + 4. Hành động bắt buộc ────────────────────────────────────────────
const hanhDong = new Map((realm.requiredActions || []).map((a) => [a.alias, a]));
const canCo = ["UPDATE_PASSWORD"];
if (realm.verifyEmail === true) canCo.push("VERIFY_EMAIL");
for (const alias of canCo) {
  const a = hanhDong.get(alias);
  if (!a) {
    loi.push(
      `\`requiredActions\` thiếu ${alias}. Danh sách ấy là TOÀN QUYỀN: khai\n` +
        "     thiếu một mục là gỡ mục ấy khỏi realm, và nó biến mất trong im lặng."
    );
  } else if (a.enabled !== true) {
    loi.push(`Hành động bắt buộc ${alias} đang \`enabled: false\`.`);
  }
}
const ve = hanhDong.get("VERIFY_EMAIL");
const up = hanhDong.get("UPDATE_PASSWORD");
if (realm.verifyEmail === true && ve && up && !(ve.priority < up.priority)) {
  loi.push(
    `Thứ tự hành động bắt buộc ĐẢO: VERIFY_EMAIL ưu tiên ${ve.priority}, ` +
      `UPDATE_PASSWORD ưu tiên ${up.priority}.\n` +
      "     Số NHỎ chạy TRƯỚC. Để ngược thì Keycloak bắt đặt mật khẩu trước khi\n" +
      "     xác minh thư, và lá thư xác minh không bao giờ được gửi. Đã xảy ra."
  );
}

// ── Theme mà realm trỏ tới phải tồn tại ──────────────────────────────────
for (const [khoa, thuMucCon] of [["loginTheme", "login"], ["emailTheme", "email"]]) {
  const ten = realm[khoa];
  if (!ten) {
    nhac.push(`realm không khai \`${khoa}\`; Keycloak sẽ lặng lẽ dùng theme gốc.`);
    continue;
  }
  const d = path.join(thuMuc, "themes", ten, thuMucCon);
  if (!fs.existsSync(d)) {
    loi.push(
      `realm khai \`${khoa}: "${ten}"\` nhưng không có thư mục ${path.relative(thuMuc, d)}.\n` +
        "     Realm trỏ vào một theme KHÔNG TỒN TẠI thì Keycloak rơi về giao diện\n" +
        "     gốc của nó — không có lỗi nào in ra."
    );
  }
}

// ═════════════════════════════════════════════════════════════════════════
// Bộ thông điệp
// ═════════════════════════════════════════════════════════════════════════

/** Đọc một tệp .properties thành Map, bỏ chú thích và dòng trống. */
function docThongDiep(duong) {
  const bang = new Map();
  const dong = doc(duong).split(/\r?\n/);
  for (const d of dong) {
    if (!d.trim() || d.trimStart().startsWith("#")) continue;
    const i = d.indexOf("=");
    if (i < 0) continue;
    bang.set(d.slice(0, i).trim(), d.slice(i + 1));
  }
  return bang;
}

const boThongDiep = [];
for (const khu of ["login", "email"]) {
  for (const ngu of ["vi", "en"]) {
    const duong = path.join(themeGoc, khu, "messages", `messages_${ngu}.properties`);
    if (!fs.existsSync(duong)) {
      loi.push(`Thiếu ${path.relative(thuMuc, duong)}.`);
      continue;
    }
    // ── Dòng đầu PHẢI là `# encoding: UTF-8` ─────────────────────────────
    const dongDau = doc(duong).split(/\r?\n/)[0].trim();
    if (dongDau !== "# encoding: UTF-8") {
      loi.push(
        `${khu}/messages_${ngu}.properties: dòng đầu là "${dongDau}", phải đúng\n` +
          "     `# encoding: UTF-8`. `java.util.Properties` mặc định đọc ISO-8859-1\n" +
          "     và Keycloak chỉ chuyển sang UTF-8 khi thấy đúng dòng ấy — thiếu nó\n" +
          "     thì mọi dấu tiếng Việt thành ký tự rác mà trang vẫn chạy."
      );
    }
    boThongDiep.push({ khu, ngu, duong, bang: docThongDiep(duong) });
  }
}

// ── 7. Dấu nháy đơn phải nhân đôi ────────────────────────────────────────
for (const { khu, ngu, duong } of boThongDiep) {
  const dong = doc(duong).split(/\r?\n/);
  dong.forEach((d, i) => {
    if (!d.trim() || d.trimStart().startsWith("#")) return;
    const j = d.indexOf("=");
    if (j < 0) return;
    const giaTri = d.slice(j + 1);
    // Bỏ mọi cặp `''` rồi xem còn sót dấu nháy đơn nào không.
    if (giaTri.replace(/''/g, "").includes("'")) {
      loi.push(
        `${khu}/messages_${ngu}.properties:${i + 1} có dấu nháy đơn KHÔNG nhân đôi.\n` +
          `     ${d.slice(0, 90)}\n` +
          "     `MessageFormat` nuốt im lặng một `'` đứng một mình. Gõ `''`."
      );
    }
  });
}

// ── 6. Mọi khoá được trỏ tới đều phải tồn tại, ở CẢ HAI ngôn ngữ ─────────
const login = Object.fromEntries(
  boThongDiep.filter((b) => b.khu === "login").map((b) => [b.ngu, b.bang])
);

const canCoKhoa = new Set();

// (a) khoá do user-profile-giapha.json trỏ tới: "${giaphaRegPhone}"
for (const m of JSON.stringify(nguonHoSo).matchAll(/\$\{([A-Za-z][\w.-]*)\}/g)) {
  canCoKhoa.add(m[1]);
}

// (b) khoá `giapha*` mà các tệp .ftl gọi bằng msg(...) / advancedMsg(...)
for (const tep of fs.readdirSync(loginGoc).filter((f) => f.endsWith(".ftl"))) {
  const nguon = doc(path.join(loginGoc, tep));
  for (const m of nguon.matchAll(/\b(?:advancedMsg|msg)\(\s*['"]([A-Za-z][\w.-]*)['"]/g)) {
    if (m[1].startsWith("giapha")) canCoKhoa.add(m[1]);
  }
}

for (const khoa of [...canCoKhoa].sort()) {
  for (const ngu of ["vi", "en"]) {
    if (!login[ngu]?.has(khoa)) {
      loi.push(
        `Khoá thông điệp "${khoa}" có chỗ trỏ tới nhưng KHÔNG có trong\n` +
          `     login/messages_${ngu}.properties. Màn hình sẽ in ra nguyên văn khoá.`
      );
    }
  }
}

// ── Hai ngôn ngữ phải phủ cùng một tập khoá `giapha*` ────────────────────
if (login.vi && login.en) {
  const chiVi = [...login.vi.keys()].filter((k) => k.startsWith("giapha") && !login.en.has(k));
  const chiEn = [...login.en.keys()].filter((k) => k.startsWith("giapha") && !login.vi.has(k));
  for (const k of chiVi) loi.push(`Khoá "${k}" chỉ có bản tiếng Việt, thiếu bản tiếng Anh.`);
  for (const k of chiEn) loi.push(`Khoá "${k}" chỉ có bản tiếng Anh, thiếu bản tiếng Việt.`);
}

// ═════════════════════════════════════════════════════════════════════════
// Kết quả
// ═════════════════════════════════════════════════════════════════════════
if (loi.length) {
  console.error("✗ Cấu hình đăng ký / máy chủ thư / bộ thông điệp có chỗ trượt:\n");
  for (const d of loi) console.error("   • " + d + "\n");
  process.exit(1);
}

console.log("✓ Đăng ký, máy chủ thư và bộ thông điệp nhất quán.");
console.log(
  `  registrationAllowed=${realm.registrationAllowed} · ` +
    `resetPasswordAllowed=${realm.resetPasswordAllowed} · ` +
    `verifyEmail=${realm.verifyEmail} · smtp=${smtp.host || "(không)"}:${smtp.port || ""}`
);
console.log(
  `  Cổng mã mời: mẫu trong kho là "${oMaMoi?.validations?.pattern?.pattern}" ` +
    "(đóng sẵn — mã thật phát bằng phat-ma-moi.mjs)."
);
console.log(
  `  Đã đối chiếu ${canCoKhoa.size} khoá thông điệp trên ${boThongDiep.length} tệp ` +
    "(vi + en, login + email)."
);
for (const d of nhac) console.log("  ⚠ " + d);
