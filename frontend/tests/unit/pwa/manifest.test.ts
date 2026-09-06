import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * F9 — bản kê khai PWA.
 *
 * Kiều bào dùng điện thoại là người dùng chính (BA v2), và trên iOS thì Web
 * Push CHỈ hoạt động sau khi ứng dụng được thêm vào màn hình chính. Nghĩa là
 * manifest không phải thứ trang trí: nếu nó thiếu `display: standalone` hoặc
 * thiếu biểu tượng 512px, iOS sẽ không coi đây là ứng dụng cài được, và toàn
 * bộ tính năng nhắc giỗ đẩy tắt theo — im lặng, không lỗi nào báo lên.
 */

const PUBLIC_DIR = resolve(process.cwd(), "public");

interface WebManifest {
  name: string;
  short_name: string;
  description?: string;
  start_url: string;
  scope?: string;
  display: string;
  background_color?: string;
  theme_color?: string;
  lang?: string;
  icons: Array<{ src: string; sizes: string; type: string; purpose?: string }>;
}

const manifest = JSON.parse(
  readFileSync(resolve(PUBLIC_DIR, "manifest.json"), "utf8")
) as WebManifest;

describe("manifest.json", () => {
  it("mang tên dòng họ đầy đủ tiếng Việt có dấu", () => {
    expect(manifest.name).toBe("Cổng Thông Tin Gia Phả Dòng Họ");
    // Dấu tiếng Việt phải sống sót qua JSON — biểu tượng trên màn hình chính
    // mà mất dấu thì đọc thành một chữ khác hẳn.
    expect(manifest.name).toMatch(/[ổâảòợ]/u);
  });

  it("có short_name đủ ngắn để không bị cắt dưới biểu tượng", () => {
    expect(manifest.short_name.length).toBeLessThanOrEqual(12);
  });

  it("khai báo display standalone — điều kiện để iOS cho phép Web Push", () => {
    expect(manifest.display).toBe("standalone");
  });

  it("khai báo lang 'vi' để trình đọc màn hình phát âm đúng tiếng Việt", () => {
    expect(manifest.lang).toBe("vi");
  });

  it("mở từ gốc và giới hạn phạm vi trong ứng dụng", () => {
    expect(manifest.start_url).toBe("/");
    expect(manifest.scope).toBe("/");
  });

  it("dùng đúng bảng màu ấm truyền thống: đỏ sẫm trên nền kem", () => {
    expect(manifest.theme_color?.toLowerCase()).toBe("#8c2d19");
    expect(manifest.background_color?.toLowerCase()).toBe("#f8f6f2");
  });

  it("có đủ biểu tượng 192 và 512, kèm một bản maskable cho Android", () => {
    const sizes = manifest.icons.map((i) => i.sizes);
    expect(sizes).toContain("192x192");
    expect(sizes).toContain("512x512");
    expect(manifest.icons.some((i) => i.purpose === "maskable")).toBe(true);
  });

  it("mọi tệp biểu tượng đều thật sự tồn tại trong public/", () => {
    for (const icon of manifest.icons) {
      const path = resolve(PUBLIC_DIR, icon.src.replace(/^\//, ""));
      expect(existsSync(path), `thiếu tệp biểu tượng ${icon.src}`).toBe(true);
    }
  });

  it("có sẵn biểu tượng apple-touch-icon mà iOS đòi khi thêm vào màn hình chính", () => {
    expect(existsSync(resolve(PUBLIC_DIR, "icons/apple-touch-icon.png"))).toBe(true);
  });

  it("khai báo huy hiệu thông báo (badge) mà service worker sẽ dùng", () => {
    // worker/index.ts trỏ tới đúng tệp này; thiếu nó thì Android hiện một ô
    // xám trên thanh trạng thái.
    expect(existsSync(resolve(PUBLIC_DIR, "icons/badge-72.png"))).toBe(true);
  });
});
