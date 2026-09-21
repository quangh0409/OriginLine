#!/usr/bin/env node
/**
 * NHÚNG `user-profile-giapha.json` VÀO `realm-giapha.json` — và canh cho hai
 * bên không trôi khỏi nhau.
 *
 * ───────────────────────────────────────────────────────────────────────────
 * VÌ SAO PHẢI CÓ TỆP NÀY
 *
 * Keycloak lưu cấu hình User Profile dưới dạng **một chuỗi JSON nằm bên trong
 * một chuỗi JSON khác**:
 *
 *   components["org.keycloak.userprofile.UserProfileProvider"][0]
 *     .config["kc.user.profile.config"][0]   ← cả cấu hình nằm gọn trong đây
 *
 * Tức là trong `realm-giapha.json` nó hiện ra thành một dòng dài vài nghìn ký
 * tự với `\"` ở khắp nơi. Đó là định dạng Keycloak xuất ra và nhập vào được,
 * nên ta KHÔNG đổi được — nhưng một dòng như thế thì không ai sửa tay nổi, và
 * không ai đọc nổi trong một bản diff.
 *
 * Nên: nguồn sự thật là `user-profile-giapha.json` (xuống dòng, đọc được, diff
 * được), còn `realm-giapha.json` chỉ giữ BẢN NHÚNG do script này sinh ra. Cùng
 * khuôn mẫu với biểu mẫu Excel của `dataimport`: một nguồn, một bản sinh, và
 * một phép kiểm để chúng không thể lệch nhau trong im lặng.
 *
 * ───────────────────────────────────────────────────────────────────────────
 * CÁCH DÙNG
 *
 *   node infra/keycloak/dong-goi-user-profile.mjs          # sinh lại bản nhúng
 *   node infra/keycloak/dong-goi-user-profile.mjs --kiem   # chỉ kiểm, không ghi
 *
 * Chế độ `--kiem` trả 1 khi bản nhúng đã lệch khỏi nguồn. `kiem-dang-ky.mjs`
 * gọi lại chế độ ấy, nên CI bắt được chỗ lệch mà không cần Docker.
 *
 * ───────────────────────────────────────────────────────────────────────────
 * `${env....}` TRONG CHUỖI NHÚNG LÀ CỐ Ý
 *
 * Mẫu regex kiểm mã mời đến từ biến môi trường `GIAPHA_CLAN_INVITE_PATTERN`.
 * Keycloak thay `${env.X:mặc định}` trên TOÀN BỘ tệp realm lúc nhập, nên chuỗi
 * ấy đi xuyên qua lớp nhúng mà không cần script này biết gì về nó.
 *
 * Mặc định là `(?!)` — một regex KHÔNG BAO GIỜ khớp. Nghĩa là một bản sao kho
 * chưa cấu hình gì thì **không ai đăng ký được**, thay vì ai cũng đăng ký được.
 * Đóng sẵn là mặc định đúng; xem README mục "Đăng ký có kiểm mã mời".
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const thuMuc = path.dirname(fileURLToPath(import.meta.url));
const duongDanNguon = path.join(thuMuc, "user-profile-giapha.json");
const duongDanRealm = path.join(thuMuc, "realm-giapha.json");

const LOAI = "org.keycloak.userprofile.UserProfileProvider";
const KHOA = "kc.user.profile.config";

const chiKiem = process.argv.includes("--kiem");

/** Chuẩn hoá để so sánh: cùng nội dung thì cùng chuỗi, bất kể xuống dòng. */
const chuanHoa = (obj) => JSON.stringify(obj);

const nguon = JSON.parse(fs.readFileSync(duongDanNguon, "utf8"));
const realm = JSON.parse(fs.readFileSync(duongDanRealm, "utf8"));

const thanhPhan = realm.components?.[LOAI]?.[0];
const dangCo = thanhPhan?.config?.[KHOA]?.[0];

let dangCoJson = null;
if (dangCo) {
  try {
    dangCoJson = JSON.parse(dangCo);
  } catch {
    dangCoJson = null;
  }
}

const khop = dangCoJson !== null && chuanHoa(dangCoJson) === chuanHoa(nguon);

if (chiKiem) {
  if (khop) {
    const soThuocTinh = nguon.attributes.length;
    console.log(
      `✓ Bản nhúng User Profile trong realm-giapha.json khớp ` +
        `user-profile-giapha.json (${soThuocTinh} thuộc tính: ` +
        `${nguon.attributes.map((a) => a.name).join(", ")}).`
    );
    process.exit(0);
  }
  console.error("✗ Bản nhúng User Profile đã TRÔI khỏi nguồn.\n");
  if (dangCoJson === null) {
    console.error(
      "   realm-giapha.json chưa có (hoặc không đọc được) thành phần\n" +
        `   ${LOAI}.`
    );
  } else {
    const tenNguon = nguon.attributes.map((a) => a.name);
    const tenRealm = (dangCoJson.attributes || []).map((a) => a.name);
    console.error(`   nguồn : ${tenNguon.join(", ")}`);
    console.error(`   realm : ${tenRealm.join(", ")}`);
  }
  console.error("\n   Sinh lại:  node infra/keycloak/dong-goi-user-profile.mjs");
  process.exit(1);
}

if (khop) {
  console.log("✓ Không có gì để sinh lại — bản nhúng đã khớp nguồn.");
  process.exit(0);
}

realm.components = realm.components || {};
realm.components[LOAI] = [
  {
    name: "declarative-user-profile",
    providerId: "declarative-user-profile",
    subComponents: {},
    config: { [KHOA]: [JSON.stringify(nguon)] },
  },
];

fs.writeFileSync(duongDanRealm, JSON.stringify(realm, null, 2) + "\n", "utf8");
console.log(
  `✓ Đã nhúng ${nguon.attributes.length} thuộc tính vào realm-giapha.json: ` +
    `${nguon.attributes.map((a) => a.name).join(", ")}.`
);
console.log(
  "  Realm chỉ nhập lại khi container Keycloak được DỰNG LẠI:\n" +
    "    cd infra && docker compose up -d --force-recreate keycloak"
);
