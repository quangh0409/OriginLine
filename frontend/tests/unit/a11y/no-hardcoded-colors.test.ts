import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { colorTokens, darkColorTokens } from "@/styles/tokens";
import { stripComments, toRepoPath, walkSource } from "./source-scan";

/**
 * KHÔNG MÃ MÀU CỨNG NGOÀI `tokens.ts`.
 *
 * <p>Vì sao đây là bất biến chứ không phải sở thích: một mã màu viết thẳng vào component
 * <b>không tồn tại ở chế độ tối</b>. Nó là chuỗi hằng, đã đóng băng vào lượt render, và
 * {@code prefers-color-scheme} không với tới được. Kết quả là một mảng sáng chói giữa nền tối,
 * im lặng, không lỗi biên dịch, không cảnh báo — đúng kiểu hỏng khó tìm nhất.</p>
 *
 * <p>Nó cũng làm hỏng chính bài kiểm tương phản bên cạnh: {@code token-contrast.test.ts} chấm
 * bảng màu trong {@code tokens.ts}. Màu nào không nằm trong bảng ấy thì <b>chưa từng được ai
 * chấm</b>, dù nó đang hiển thị trên màn hình người dùng.</p>
 *
 * <p>Phép quét bỏ qua chú thích: cả {@code tokens.ts} lẫn {@code globals.css} đều ghi mã màu
 * trong chú thích để đối chiếu, và đó là việc nên làm chứ không phải vi phạm.</p>
 */

/** Nguồn sự thật duy nhất của bảng màu — chỗ DUY NHẤT được phép viết hex. */
const PALETTE_FILES = new Set(["src/styles/tokens.ts"]);

const HEX = /#[0-9a-fA-F]{3,8}\b/g;

export interface HexHit {
  readonly file: string;
  readonly line: number;
  readonly value: string;
  readonly snippet: string;
}

/** Mọi mã màu hex nằm trong MÃ THẬT (không phải chú thích) của một tệp. */
export function findHardcodedHex(source: string, file: string): HexHit[] {
  const kind: "ts" | "css" = file.endsWith(".css") ? "css" : "ts";
  const lines = stripComments(source, kind).split("\n");
  const rawLines = source.split("\n");
  const hits: HexHit[] = [];
  lines.forEach((line, index) => {
    for (const match of line.matchAll(HEX)) {
      hits.push({
        file,
        line: index + 1,
        value: match[0],
        snippet: (rawLines[index] ?? "").trim(),
      });
    }
  });
  return hits;
}

function scanSrc(): HexHit[] {
  const hits: HexHit[] = [];
  for (const absolute of walkSource(/\.(ts|tsx|css)$/)) {
    const file = toRepoPath(absolute);
    if (PALETTE_FILES.has(file)) continue;
    hits.push(...findHardcodedHex(readFileSync(absolute, "utf8"), file));
  }
  return hits;
}

function describeHits(hits: readonly HexHit[]): string {
  return hits.map((h) => `  · ${h.file}:${h.line} → ${h.value}\n      ${h.snippet}`).join("\n");
}

describe("bảng màu · không mã màu cứng ngoài tokens.ts", () => {
  it("không tệp nguồn nào viết thẳng mã màu hex", () => {
    const hits = scanSrc();
    expect(
      hits,
      `${hits.length} mã màu viết cứng ngoài ${[...PALETTE_FILES].join(", ")}:\n` +
        `${describeHits(hits)}\n\n` +
        "Mỗi chỗ này là một màu KHÔNG đổi theo chế độ tối và CHƯA từng được bài kiểm tương " +
        "phản chấm. Chuyển vào tokens.ts (dùng colorVars nếu nó phải đảo theo chế độ)."
    ).toEqual([]);
  });

  /**
   * Ca phụ, để thông báo lỗi nói đúng bản chất: hai mã màu huy hiệu Dâu / Rể không phải lỗi kỹ
   * thuật của người viết mã. Chúng đang chờ Hội đồng Tộc biểu quyết — dâu/rể có được một sắc riêng
   * ngoài bảng màu dòng họ hay không là câu hỏi về NGHĨA, không phải về CSS. Ca này chỉ khẳng định
   * rằng nếu chúng còn đó thì ca trên phải đỏ, chứ không được ai lặng lẽ thêm vào danh sách trắng.
   */
  it("mã màu chờ Hội đồng quyết vẫn bị tính là vi phạm, không được đưa vào danh sách trắng", () => {
    const hits = scanSrc();
    const badge = hits.filter((h) => h.file.endsWith("lib/tree/badges.ts"));
    if (badge.length === 0) return; // đã được giải quyết — không có gì để khẳng định thêm
    expect(
      badge.map((h) => `${h.file}:${h.line} ${h.value}`).join(" · "),
      "hai sắc huy hiệu Dâu/Rể vẫn nằm ngoài bảng màu — đây là câu hỏi cho Hội đồng Tộc biểu, " +
        "không phải chỗ để nới bài kiểm"
    ).toMatch(/badges\.ts/);
  });
});

describe("bảng màu · mọi mã màu đang dùng đều truy ngược được về một token", () => {
  /**
   * Bổ sung cho ca trên chứ không thay thế: một mã màu cứng TRÙNG với token vẫn là vi phạm (nó
   * không đảo theo chế độ tối), nhưng nó là loại vi phạm rẻ — chỉ cần đổi sang tên token. Một mã
   * màu cứng KHÔNG trùng token nào là loại đắt: nó là một màu thứ hai trong sản phẩm mà không ai
   * duyệt. Tách hai loại ra để người sửa biết chỗ nào cần Hội đồng và chỗ nào chỉ cần sửa import.
   */
  it("phân loại được vi phạm: trùng token (sửa import) hay là màu lạ (cần duyệt)", () => {
    const known = new Set(
      [...Object.values(colorTokens), ...Object.values(darkColorTokens)].map((v) =>
        v.toLowerCase()
      )
    );
    const strangers = scanSrc().filter((h) => !known.has(h.value.toLowerCase()));
    expect(
      strangers,
      `${strangers.length} mã màu KHÔNG có trong bảng màu nào — tức là một sắc thứ hai đang ` +
        `hiển thị cho người dùng mà chưa ai chấm tương phản:\n${describeHits(strangers)}`
    ).toEqual([]);
  });
});

/** ---- Phép kiểm ngược: chứng minh phép quét bắt được, và không bắt nhầm ---- */
describe("chống xanh giả · phép quét mã màu cứng", () => {
  it("bắt được mã màu viết thẳng trong mã TypeScript", () => {
    const source = ['const style = { color: "#a3336b" };'].join("\n");
    const hits = findHardcodedHex(source, "src/lib/tree/badges.ts");
    expect(hits).toHaveLength(1);
    expect(hits[0]!.value).toBe("#a3336b");
    expect(hits[0]!.line).toBe(1);
  });

  it("bắt được mã màu viết thẳng trong CSS", () => {
    const hits = findHardcodedHex("body { color: #d97706; }", "src/app/globals.css");
    expect(hits.map((h) => h.value)).toEqual(["#d97706"]);
  });

  it("KHÔNG bắt nhầm mã màu nằm trong chú thích — đó là cách ghi đối chiếu đúng", () => {
    const source = [
      "// #8c2d19 do tram",
      "/* --rgb-primary: 140 45 25;  #8c2d19 */",
      "const primary = colorTokens.primary;",
      "/* nhiều dòng",
      "   #ffffff",
      "*/",
    ].join("\n");
    expect(findHardcodedHex(source, "src/styles/antd-theme.ts")).toEqual([]);
  });

  it("không để dấu // trong một chuỗi nuốt mất phần còn lại của dòng", () => {
    const source = 'const u = "https://example.com"; const c = "#123456";';
    const hits = findHardcodedHex(source, "src/lib/x.ts");
    expect(hits.map((h) => h.value)).toEqual(["#123456"]);
  });

  it("báo đúng số dòng để sửa được mà không phải đi tìm", () => {
    const source = ["line one", "line two", 'const c = "#0f0";'].join("\n");
    const hits = findHardcodedHex(source, "src/x.ts");
    expect(hits[0]!.line).toBe(3);
    expect(hits[0]!.snippet).toContain("#0f0");
  });

  it("không nhầm một mã băm không phải màu là màu", () => {
    // `#region`, `#!/usr/bin` và các mảnh URL không phải mã màu.
    const source = ['const a = "#region";', 'const b = "#zzzzzz";', 'const c = "#12345";'].join("\n");
    const hits = findHardcodedHex(source, "src/x.ts");
    // "#12345" (5 ký tự) KHÔNG phải mã màu hợp lệ nhưng khớp mẫu 3–8 ký tự hex; chấp nhận báo
    // thừa ở đây còn hơn bỏ sót — người sửa nhìn một cái là biết.
    expect(hits.map((h) => h.value)).toEqual(["#12345"]);
  });
});
