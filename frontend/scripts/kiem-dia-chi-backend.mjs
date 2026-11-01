#!/usr/bin/env node
/**
 * Cổng gác trước `next build` / `next start` khi chạy với BACKEND THẬT.
 *
 * VÌ SAO TỒN TẠI — một lỗi đã xảy ra thật (23/09/2026): backend chạy ở 8088
 * (vì 8080 trên máy dev bị httpd.exe giữ), nhưng frontend được dựng lại mà
 * quên NEXT_PUBLIC_API_BASE_URL, nên http.ts rơi về mặc định
 * http://localhost:8080. Next NƯỚNG CỨNG giá trị ấy vào bundle lúc build —
 * đặt biến lúc `next start` là vô ích. Hậu quả nhìn từ phía người dùng không
 * hề giống "sai cổng": mọi lời gọi API nhận 404 từ một tiến trình lạ,
 * useMe() lỗi, và cổng viết bài bảo một người VỪA ĐĂNG NHẬP là "cần đăng
 * nhập". Mất gần một buổi vì triệu chứng nằm cách nguyên nhân hai tầng.
 *
 * Script này biến chuyện ấy thành một dòng lỗi nói thẳng cách sửa:
 *   1. bắt buộc có NEXT_PUBLIC_API_BASE_URL (không cho rơi về mặc định ngầm);
 *   2. địa chỉ ấy phải trả lời /actuator/health = UP (backend thật đang chạy);
 *   3. (chỉ trước `next start`) bundle trong .next/ phải chứa đúng origin ấy
 *      — bằng chứng rằng bản dựng đang được phục vụ đã dựng với cùng biến.
 *
 * Dùng: node scripts/kiem-dia-chi-backend.mjs --truoc-build | --truoc-start
 */
import { readdirSync, readFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const cheDo = process.argv.includes("--truoc-start") ? "start" : "build";
const base = process.env.NEXT_PUBLIC_API_BASE_URL;

function thatBai(...dong) {
  console.error("\n[kiem-dia-chi-backend] KHÔNG ĐẠT");
  for (const d of dong) console.error("  " + d);
  console.error("");
  process.exit(1);
}

if (!base) {
  thatBai(
    "Thiếu NEXT_PUBLIC_API_BASE_URL. Không có nó, http.ts rơi về http://localhost:8080 — trên máy dev này",
    "8080 là của httpd.exe, KHÔNG phải backend. Chạy:",
    "  NEXT_PUBLIC_API_BASE_URL=http://localhost:8088 npm run " + (cheDo === "start" ? "start:nghiem-thu" : "build:nghiem-thu"),
  );
}

let origin = "";
try {
  origin = new URL(base).origin;
} catch {
  thatBai("NEXT_PUBLIC_API_BASE_URL không phải URL hợp lệ: " + base);
}

// 2. backend thật phải sống ở đúng địa chỉ ấy
try {
  const r = await fetch(origin + "/actuator/health", { signal: AbortSignal.timeout(5000) });
  const body = await r.json().catch(() => ({}));
  if (!r.ok || body.status !== "UP") {
    thatBai(origin + "/actuator/health trả " + r.status + " / status=" + (body.status ?? "?") + " — backend chưa lên, hoặc lên ở cổng khác.");
  }
} catch (e) {
  thatBai(
    "Không nối được " + origin + "/actuator/health (" + (e?.cause?.code ?? e?.message) + ").",
    "Backend đã chạy chưa? Đúng cổng chưa? (SERVER_PORT=8088 trên máy này)",
  );
}

// 3. trước `next start`: bundle phải được dựng với CÙNG origin
if (cheDo === "start") {
  const dir = join(process.cwd(), ".next", "static", "chunks");
  if (!existsSync(dir)) thatBai("Chưa có .next/ — chạy build:nghiem-thu trước.");
  const khop = readdirSync(dir)
    .filter((f) => f.endsWith(".js"))
    .some((f) => readFileSync(join(dir, f), "utf8").includes(origin));
  if (!khop) {
    thatBai(
      "Bundle trong .next/ KHÔNG chứa " + origin + " — bản dựng này được dựng với một địa chỉ backend khác.",
      "Biến NEXT_PUBLIC_* được nướng lúc build, đặt lúc start là vô ích. Dựng lại:",
      "  rm -rf .next && NEXT_PUBLIC_API_BASE_URL=" + origin + " npm run build:nghiem-thu",
    );
  }
}

console.log("[kiem-dia-chi-backend] ĐẠT — backend " + origin + " UP" + (cheDo === "start" ? ", bundle khớp." : "."));
