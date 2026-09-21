#!/usr/bin/env node
/**
 * ═══════════════════════════════════════════════════════════════════════════
 * PHÁT · THU HỒI · ĐẾM LƯỢT DÙNG — MÃ MỜI DÒNG HỌ
 * design/07-checklist §1.2 (bốn chốt chặn) · §1.3 ("Bật tự đăng ký, có kiểm mã")
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ── VÌ SAO LÀ MỘT SCRIPT CHỨ KHÔNG PHẢI MỘT DÒNG TRONG realm-giapha.json ────
 *
 * Bản đầu đặt mã vào realm file bằng `${env.GIAPHA_CLAN_INVITE_PATTERN:(?!)}`,
 * tin rằng Keycloak thay chuỗi ấy bằng biến môi trường lúc nhập realm. ĐÃ ĐO
 * TRÊN KEYCLOAK 26.7.2 VÀ ĐIỀU ĐÓ KHÔNG XẢY RA:
 *
 *     ${env.X:mặc định}   →  LUÔN ra "mặc định", kể cả khi X có thật trong
 *                            container (kiểm bằng `docker exec ... env`)
 *     ${env.X}            →  giữ nguyên nguyên văn chuỗi "${env.X}"
 *
 * Đúng với cả biến có tiền tố `KC_`. Nên cú pháp ấy là một cái bẫy: trông như
 * đọc biến môi trường, không bao giờ đọc, và trả về đúng giá trị đã commit —
 * tức một "bí mật lấy từ môi trường" hoá ra là một hằng số nằm trong kho.
 *
 * Đường đi đúng là Admin API, và nó còn TỐT HƠN cho nghiệp vụ: thu hồi mã
 * không phải dựng lại container, nên "đóng cửa ngay khi biết mã đã lan"
 * (checklist §1.2) làm được trong vài giây thay vì vài phút mất phiên của mọi
 * người đang đăng nhập.
 *
 * ── CÁI NÀY KIỂM ĐƯỢC GÌ, VÀ KHÔNG KIỂM ĐƯỢC GÌ ────────────────────────────
 *
 * Mã được so bằng một MẪU regex trong cấu hình User Profile của realm, và
 * Keycloak chạy phép so ấy TRƯỚC khi tạo tài khoản. Đã đo: gửi biểu mẫu với mã
 * sai thì số tài khoản trong realm giữ nguyên.
 *
 *   ✓ chốt "thu hồi được"   — `--thu-hoi` đóng cửa tức thì
 *   ✓ chốt "đếm lượt dùng"  — `--dem`, đếm bằng chính thuộc tính lưu trên tài
 *                             khoản; kèm luôn "ai đã dùng mã nào"
 *   ~ chốt "có hạn dùng"    — KHÔNG tự động. Keycloak không biết ngày tháng.
 *                             Hạn dùng hôm nay là một việc của người vận hành:
 *                             hẹn lịch chạy `--thu-hoi`. Nói thẳng là một nửa.
 *   ✗ chốt "giới hạn tần suất" — Keycloak KHÔNG áp chống dò mật khẩu lên trang
 *                             đăng ký. Chốt này phải là reCAPTCHA (có sẵn, cần
 *                             khoá Google) hoặc giới hạn ở tầng proxy.
 *
 * Bốn chốt đầy đủ chỉ có khi máy chủ giữ mã (băm, hạn, bộ đếm, nhật ký) và
 * Keycloak hỏi máy chủ — mà hỏi được thì phải viết một Java SPI, hoặc chuyển
 * hẳn việc tạo tài khoản về cho máy chủ. README mục "Đăng ký có kiểm mã mời"
 * so hai đường ấy kèm cái giá.
 *
 * ── DÙNG ───────────────────────────────────────────────────────────────────
 *
 *   # phát mã mới (mã lấy từ biến môi trường, KHÔNG truyền qua tham số dòng
 *   # lệnh — tham số nằm lại trong lịch sử shell và trong `ps`)
 *   GIAPHA_CLAN_INVITE_CODE='K7M-2QD' node infra/keycloak/phat-ma-moi.mjs --phat
 *
 *   node infra/keycloak/phat-ma-moi.mjs --xem       # mã nào đang mở? (không in mã)
 *   node infra/keycloak/phat-ma-moi.mjs --dem       # đã dùng bao nhiêu lượt, ai dùng
 *   node infra/keycloak/phat-ma-moi.mjs --thu-hoi   # đóng cửa ngay
 *
 * Tài khoản quản trị lấy từ `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`
 * (mặc định admin/admin của máy phát triển), máy chủ từ `KEYCLOAK_URL`.
 *
 * ── SCRIPT NÀY KHÔNG BAO GIỜ IN MÃ RA MÀN HÌNH ─────────────────────────────
 * Kể cả ở `--xem`. Màn hình bị chụp, nhật ký CI bị lưu. Nó chỉ in DẤU VÂN của
 * mã (8 ký tự đầu của SHA-256) — đủ để hai người xác nhận đang nói về cùng một
 * mã, không đủ để dựng lại mã.
 */

import crypto from "node:crypto";

// `127.0.0.1`, KHÔNG `localhost`. Trên Windows, Node phân giải `localhost`
// thành `::1` trước, và cổng Keycloak do Docker Desktop mở chỉ nghe IPv4 —
// lỗi trả về là `UND_ERR_SOCKET: other side closed`, đọc như "máy chủ chết"
// chứ không như "sai địa chỉ". Mất mười phút một lần, nên ghim thẳng vào đây.
const GOC = process.env.KEYCLOAK_URL || "http://127.0.0.1:8081";
const REALM = process.env.KEYCLOAK_REALM || "giapha";
const ADMIN = process.env.KEYCLOAK_ADMIN || "admin";
const MAT_KHAU = process.env.KEYCLOAK_ADMIN_PASSWORD || "admin";

const THUOC_TINH = "maMoiDongHo";
/** Regex không bao giờ khớp. Đây là trạng thái "cửa đóng". */
const DONG = "(?!)";

const viec = process.argv.find((a) => a.startsWith("--")) || "--xem";

const vanTay = (s) => crypto.createHash("sha256").update(s).digest("hex").slice(0, 8);

/**
 * Gọi lại vài lượt trước khi bỏ cuộc.
 *
 * Keycloak vừa khởi động xong có một khoảng vài giây MỞ CỔNG nhưng đóng ngay
 * kết nối: `/.well-known/...` đã trả 200 mà lượt gọi kế tiếp vẫn hỏng với
 * `UND_ERR_SOCKET: other side closed`. Lỗi ấy đọc như "máy chủ chết" nên người
 * gặp sẽ đi tìm nhầm chỗ. Ba lượt cách nhau 2 giây là đủ.
 */
async function goiLai(fn, soLuot = 5) {
  for (let i = 1; ; i++) {
    try {
      return await fn();
    } catch (e) {
      if (i >= soLuot) throw e;
      await new Promise((r) => setTimeout(r, 2000));
    }
  }
}

async function token() {
  const r = await goiLai(() =>
    fetch(`${GOC}/realms/master/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: "admin-cli",
        username: ADMIN,
        password: MAT_KHAU,
        grant_type: "password",
      }),
    })
  );
  const j = await r.json().catch(() => ({}));
  if (!j.access_token) {
    console.error(`✗ Không đăng nhập được vào ${GOC} với tài khoản "${ADMIN}".`);
    console.error("  Keycloak đã chạy chưa?  cd infra && docker compose up -d keycloak");
    process.exit(1);
  }
  return j.access_token;
}

async function api(duong, opts = {}) {
  const t = await token();
  const r = await goiLai(() =>
    fetch(`${GOC}${duong}`, {
      ...opts,
      headers: { authorization: `Bearer ${t}`, "content-type": "application/json", ...(opts.headers || {}) },
    })
  );
  const txt = await r.text();
  if (r.status >= 400) {
    console.error(`✗ ${opts.method || "GET"} ${duong} → ${r.status}`);
    console.error("  " + txt.slice(0, 400));
    process.exit(1);
  }
  return txt ? JSON.parse(txt) : null;
}

const docHoSo = () => api(`/admin/realms/${REALM}/users/profile`);

async function ghiHoSo(hoSo) {
  await api(`/admin/realms/${REALM}/users/profile`, {
    method: "PUT",
    body: JSON.stringify(hoSo),
  });
}

function oMaMoi(hoSo) {
  const o = hoSo.attributes.find((a) => a.name === THUOC_TINH);
  if (!o) {
    console.error(`✗ Realm không có thuộc tính "${THUOC_TINH}" trong User Profile.`);
    console.error("  Realm đã nhập lại sau khi sửa chưa?");
    console.error("    node infra/keycloak/dong-goi-user-profile.mjs");
    console.error("    cd infra && docker compose up -d --force-recreate keycloak");
    process.exit(1);
  }
  return o;
}

/**
 * Bọc mã thành một mẫu khớp ĐÚNG mã ấy, không phân biệt hoa thường.
 *
 * `(?i)` bật cờ không phân biệt hoa thường — người gõ trên điện thoại không
 * nên trượt vì bàn phím tự viết hoa. `\\Q...\\E` bọc mã thành chuỗi nguyên văn,
 * nên một mã có `.` hay `-` không biến thành ký tự đặc biệt của regex.
 * `^...$` để mã phải khớp TOÀN BỘ chứ không phải "có chứa".
 */
const bocThanhMau = (ma) => `(?i)^\\Q${ma}\\E$`;

const dangDong = (mau) => !mau || mau === DONG;

async function xem() {
  const o = oMaMoi(await docHoSo());
  const mau = o.validations?.pattern?.pattern;
  if (dangDong(mau)) {
    console.log("● Cửa ĐANG ĐÓNG — chưa phát mã nào, không ai đăng ký được.");
    console.log("  Phát mã:  GIAPHA_CLAN_INVITE_CODE='...' node infra/keycloak/phat-ma-moi.mjs --phat");
    return;
  }
  console.log(`● Cửa ĐANG MỞ — có một mã mời đang dùng được.`);
  console.log(`  Dấu vân mẫu: ${vanTay(mau)}  (không in mã; dùng để đối chiếu)`);
  console.log(`  Thu hồi:  node infra/keycloak/phat-ma-moi.mjs --thu-hoi`);
}

async function phat() {
  const ma = process.env.GIAPHA_CLAN_INVITE_CODE;
  if (!ma || ma.trim().length < 4) {
    console.error("✗ Chưa có mã. Đặt biến môi trường GIAPHA_CLAN_INVITE_CODE (ít nhất 4 ký tự).");
    console.error("  Cố ý KHÔNG nhận mã qua tham số dòng lệnh: tham số nằm lại trong");
    console.error("  lịch sử shell và hiện ra trong `ps` cho mọi tiến trình khác đọc.");
    process.exit(1);
  }
  const hoSo = await docHoSo();
  const o = oMaMoi(hoSo);
  const cu = o.validations?.pattern?.pattern;
  o.validations = o.validations || {};
  o.validations.pattern = {
    pattern: bocThanhMau(ma.trim()),
    "error-message": "giaphaInviteCodeWrong",
  };
  await ghiHoSo(hoSo);
  console.log(`✓ Đã phát mã mời. Dấu vân: ${vanTay(o.validations.pattern.pattern)}`);
  if (!dangDong(cu)) {
    console.log(`  Mã cũ (vân ${vanTay(cu)}) HẾT HIỆU LỰC ngay lập tức.`);
    console.log("  Người đã đăng ký bằng mã cũ KHÔNG bị ảnh hưởng — mã chỉ gác cửa lúc đăng ký.");
  }
  console.log("  Có hiệu lực ngay, không phải dựng lại container.");
}

async function thuHoi() {
  const hoSo = await docHoSo();
  const o = oMaMoi(hoSo);
  const cu = o.validations?.pattern?.pattern;
  if (dangDong(cu)) {
    console.log("● Cửa vốn đã đóng. Không có gì để thu hồi.");
    return;
  }
  o.validations.pattern = { pattern: DONG, "error-message": "giaphaInviteCodeWrong" };
  await ghiHoSo(hoSo);
  console.log(`✓ Đã thu hồi mã (vân ${vanTay(cu)}). Từ giây này không ai đăng ký được nữa.`);
  console.log("  Tài khoản đã tạo bằng mã ấy vẫn dùng bình thường — thu hồi mã không đuổi ai ra.");
}

async function dem() {
  // `briefRepresentation=false` để Keycloak trả kèm `attributes`, nơi mã mời
  // đã dùng được lưu lại trên chính tài khoản. Đây là chốt "đếm lượt dùng" và
  // chốt "ghi lại ai đã dùng mã nào" của checklist §1.2 — không cần bảng mới.
  const nguoiDung = await api(
    `/admin/realms/${REALM}/users?briefRepresentation=false&max=2000`
  );
  const theoMa = new Map();
  for (const u of nguoiDung) {
    const ma = u.attributes?.[THUOC_TINH]?.[0];
    if (!ma) continue;
    const khoa = ma.toUpperCase();
    if (!theoMa.has(khoa)) theoMa.set(khoa, []);
    theoMa.get(khoa).push(u);
  }
  if (theoMa.size === 0) {
    console.log("● Chưa tài khoản nào được tạo bằng mã mời dòng họ.");
    console.log(`  (Tổng ${nguoiDung.length} tài khoản trong realm — số còn lại do`);
    console.log("   Hội đồng tạo tay hoặc do lời mời cá nhân.)");
    return;
  }
  console.log(`● ${theoMa.size} mã đã có người dùng:\n`);
  for (const [ma, ds] of theoMa) {
    console.log(`  mã vân ${vanTay(`(?i)^\\Q${ma}\\E$`)} · ${ds.length} lượt`);
    for (const u of ds) {
      const ngay = u.createdTimestamp ? new Date(u.createdTimestamp).toISOString().slice(0, 10) : "?";
      console.log(`      ${ngay}  ${u.username}  ${u.emailVerified ? "(đã xác minh thư)" : "(CHƯA xác minh thư)"}`);
    }
    console.log("");
  }
  console.log("  Hội đồng thấy con số này vượt quá số người trong họ thì thu hồi:");
  console.log("    node infra/keycloak/phat-ma-moi.mjs --thu-hoi");
}

const bang = { "--xem": xem, "--phat": phat, "--thu-hoi": thuHoi, "--dem": dem };

if (!bang[viec]) {
  console.error(`✗ Không hiểu "${viec}". Dùng: --xem | --phat | --thu-hoi | --dem`);
  process.exit(1);
}

await bang[viec]();
