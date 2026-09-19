import { readdirSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";

/**
 * Tiện ích quét mã nguồn dùng chung cho hai phép kiểm bảng màu ở thư mục này.
 *
 * <p>Không phải là tệp {@code *.test.ts} nên Vitest không thu nó vào danh sách ca kiểm
 * ({@code include: ["tests/**\/*.test.ts"]}) — nó chỉ là mô-đun dùng chung.</p>
 *
 * <p>Vì sao phải bỏ chú thích trước khi quét, và đây là bài học từ một cái đỏ giả đã xảy ra thật:
 * {@code person-node.tsx} có dòng chú thích <i>"`outline-offset` ÂM kéo vòng vào trong…"</i>. Dấu
 * huyền quanh {@code outline-offset} trông y hệt một chuỗi mẫu, nên phép quét lớp Tailwind nhặt nó
 * lên và báo "lớp này không sinh ra CSS". Nó không phải một lớp — nó là một câu tiếng Việt.</p>
 */

export const FRONTEND_ROOT = join(__dirname, "..", "..", "..");
export const SRC_DIR = join(FRONTEND_ROOT, "src");

/**
 * Thay mọi chú thích bằng khoảng trắng, GIỮ NGUYÊN số dòng và số cột.
 *
 * <p>Có xử lý chuỗi (' " `) để một dấu {@code //} nằm trong chuỗi — ví dụ {@code "https://…"} —
 * không nuốt mất phần còn lại của dòng. Đây không phải trình phân tích cú pháp đầy đủ; nó chỉ cần
 * đủ đúng để không bỏ sót thứ thật, và cả hai tệp kiểm dùng nó đều có ca kiểm ngược chứng minh
 * điều đó.</p>
 */
export function stripComments(source: string, kind: "ts" | "css"): string {
  const out: string[] = [];
  const n = source.length;
  let i = 0;
  const blank = (c: string): string => (c === "\n" ? "\n" : " ");

  while (i < n) {
    const c = source[i]!;
    const next = i + 1 < n ? source[i + 1]! : "";

    if (c === "/" && next === "*") {
      out.push("  ");
      i += 2;
      while (i < n && !(source[i] === "*" && source[i + 1] === "/")) {
        out.push(blank(source[i]!));
        i += 1;
      }
      out.push("  ");
      i += 2;
      continue;
    }

    if (kind === "ts" && c === "/" && next === "/") {
      while (i < n && source[i] !== "\n") {
        out.push(" ");
        i += 1;
      }
      continue;
    }

    if (c === '"' || c === "'" || c === "`") {
      out.push(c);
      i += 1;
      while (i < n) {
        const ch = source[i]!;
        if (ch === "\\") {
          out.push(ch, i + 1 < n ? source[i + 1]! : "");
          i += 2;
          continue;
        }
        out.push(ch);
        i += 1;
        if (ch === c) break;
      }
      continue;
    }

    out.push(c);
    i += 1;
  }
  return out.join("");
}

/** Mọi tệp trong `src/` khớp phần mở rộng cho trước, đường dẫn tuyệt đối. */
export function walkSource(extensions: RegExp, dir: string = SRC_DIR): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) found.push(...walkSource(extensions, full));
    else if (extensions.test(entry)) found.push(full);
  }
  return found;
}

/** Đường dẫn tương đối, dùng dấu gạch chéo xuôi, để thông báo lỗi chỉ đúng chỗ trên mọi hệ. */
export function toRepoPath(absolute: string): string {
  return relative(FRONTEND_ROOT, absolute).split(sep).join("/");
}
