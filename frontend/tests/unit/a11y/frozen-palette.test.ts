import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { stripComments, toRepoPath, walkSource, FRONTEND_ROOT } from "./source-scan";

/**
 * LỖ THỦNG CỦA BÀI KIỂM MÀU, VÀ HAI HÀNG RÀO BỊT NÓ.
 *
 * <p>{@code no-hardcoded-colors.test.ts} quét chuỗi <b>hex</b>. Nó bắt được
 * {@code "#a3336b"}. Nó <b>không</b> bắt được {@code colorTokens.warningBg} — dù hai thứ ấy
 * giống hệt nhau về hậu quả: {@code colorTokens} là bảng màu <b>chế độ sáng</b>, giá trị bị đóng
 * băng vào lượt render, {@code prefers-color-scheme} không với tới. Chỉ {@code colorVars} mới đảo
 * theo chế độ, vì phép thay thế {@code var()} được giải ở thời điểm dùng.</p>
 *
 * <p>Cái lỗ ấy đủ lớn để lọt cả chế độ tối: phép quét ngày 2026-09-10 đếm được <b>28 chỗ</b> dùng
 * {@code colorTokens.*} trong {@code components/} + {@code app/} — nền thẻ, viền, chấm trạng thái,
 * màu chữ lỗi — tất cả vẽ bằng màu chế độ sáng trên nền tối, im lặng, không lỗi biên dịch.</p>
 *
 * <p>Ba biến {@code --tree-line-*} là ca nặng nhất trong nhóm đó và cũng là ca khó thấy nhất:
 * {@code to-flow-elements.ts} tham chiếu chúng KÈM GIÁ TRỊ DỰ PHÒNG, mà biến thì chưa từng được
 * khai ở đâu — nên giá trị dự phòng luôn thắng. Đường xuống con, thanh hôn phối và đường kế tự vẽ
 * bằng màu chế độ sáng trên nền tối, và không phép kiểm nào thấy vì mã ở đó không viết hex, nó gọi
 * TÊN TOKEN.</p>
 */

/** Chỗ được phép nhập bảng màu hex, kèm LÝ DO. Danh sách này không được dài thêm vì tiện tay. */
const DUOC_PHEP: ReadonlyMap<string, string> = new Map([
  [
    "src/styles/tokens.ts",
    "chính là bảng màu — nguồn sự thật duy nhất",
  ],
  [
    "src/styles/antd-theme.ts",
    "Ant Design chạy thuật toán sinh dải hover/active trên colorPrimary/colorError…; " +
      "thuật toán ấy phải PHÂN TÍCH ĐƯỢC màu, đưa vào chuỗi var(--x) thì tinycolor trả về " +
      "màu không hợp lệ và cả dải hỏng câm lặng. Chế độ tối ở tầng này đi bằng antdThemeDark.",
  ],
  [
    "src/app/[locale]/layout.tsx",
    "themeColor là SIÊU DỮ LIỆU của tài liệu HTML (thẻ <meta>), không phải CSS — nó không " +
      "nhận var(--…). Hai chế độ ở đây đi bằng hai mục `media` riêng, đúng cách.",
  ],
]);

interface ViPham {
  readonly file: string;
  readonly line: number;
  readonly snippet: string;
}

/** Mọi lần nhập `colorTokens` / `darkColorTokens` trong MÃ THẬT (không phải chú thích). */
export function timBangMauDongBang(source: string, file: string): ViPham[] {
  const kind: "ts" | "css" = file.endsWith(".css") ? "css" : "ts";
  const sach = stripComments(source, kind).split("\n");
  const goc = source.split("\n");
  const out: ViPham[] = [];
  sach.forEach((line, i) => {
    if (/\b(darkColorTokens|colorTokens)\b/.test(line)) {
      out.push({ file, line: i + 1, snippet: (goc[i] ?? "").trim() });
    }
  });
  return out;
}

function quet(): ViPham[] {
  const out: ViPham[] = [];
  for (const absolute of walkSource(/\.(ts|tsx)$/)) {
    const file = toRepoPath(absolute);
    if (DUOC_PHEP.has(file)) continue;
    out.push(...timBangMauDongBang(readFileSync(absolute, "utf8"), file));
  }
  return out;
}

describe("bảng màu · không mã nào ngoài danh sách trắng dùng bảng màu ĐÓNG BĂNG", () => {
  it("không tệp nào nhập colorTokens / darkColorTokens", () => {
    const viPham = quet();
    expect(
      viPham.map((v) => `${v.file}:${v.line} → ${v.snippet}`),
      `${viPham.length} chỗ dùng bảng màu chế độ SÁNG đóng băng. Đổi sang \`colorVars\` — ` +
        "cùng bộ tên, nhưng trỏ vào biến CSS nên tự đảo theo chế độ tối mà không phải sửa một " +
        "dòng component nào.\n" +
        `Ba chỗ được miễn, và mỗi chỗ có lý do đã ghi:\n` +
        [...DUOC_PHEP].map(([f, ly]) => `  · ${f} — ${ly}`).join("\n")
    ).toEqual([]);
  });
});

describe("chống xanh giả · phép quét bảng màu đóng băng", () => {
  it("bắt được một lần nhập thật", () => {
    const src = ['import { colorTokens } from "@/styles/tokens";'].join("\n");
    expect(timBangMauDongBang(src, "src/components/x.tsx")).toHaveLength(1);
  });

  it("bắt được cả darkColorTokens, không chỉ bản sáng", () => {
    const src = "const c = darkColorTokens.bgPage;";
    expect(timBangMauDongBang(src, "src/components/x.tsx")).toHaveLength(1);
  });

  it("KHÔNG bắt nhầm colorVars — đó chính là thứ đang khuyến khích dùng", () => {
    const src = [
      'import { colorVars } from "@/styles/tokens";',
      "const c = colorVars.primary;",
    ].join("\n");
    expect(timBangMauDongBang(src, "src/components/x.tsx")).toEqual([]);
  });

  it("KHÔNG bắt nhầm tên token nhắc trong chú thích — đó là cách ghi đối chiếu đúng", () => {
    const src = [
      "// colorTokens bị đóng băng vào chế độ sáng; dùng colorVars thay thế.",
      "/* darkColorTokens chỉ dùng ở tầng AntD */",
      "const c = colorVars.primary;",
    ].join("\n");
    expect(timBangMauDongBang(src, "src/components/x.tsx")).toEqual([]);
  });
});

/**
 * Ba biến đường vẽ phả đồ.
 *
 * <p>Ca này đọc CẢ HAI đầu của sợi dây: nơi tham chiếu ({@code to-flow-elements.ts}) và nơi khai
 * ({@code globals.css}). Chỉ kiểm một đầu là bỏ lọt đúng cái lỗi đã xảy ra — mã tham chiếu trông
 * hoàn toàn đúng, chú thích còn ghi rõ ý định, mà biến thì không tồn tại.</p>
 */
describe("phả đồ · ba biến --tree-line-* phải được KHAI, không chỉ được tham chiếu", () => {
  const CSS = readFileSync(resolve(FRONTEND_ROOT, "src/app/globals.css"), "utf8");
  const TS = readFileSync(resolve(FRONTEND_ROOT, "src/lib/tree/to-flow-elements.ts"), "utf8");
  const BIEN = ["--tree-line-descent", "--tree-line-marriage", "--tree-line-heir"] as const;

  it.each(BIEN)("%s được khai trong globals.css", (bien) => {
    expect(
      new RegExp(`${bien}\\s*:`).test(CSS),
      `${bien} được tham chiếu ở to-flow-elements.ts nhưng KHÔNG được khai ở đâu cả. ` +
        "Giá trị dự phòng sẽ luôn thắng, kể cả ở chế độ tối."
    ).toBe(true);
  });

  it.each(BIEN)("%s vẫn được tham chiếu ở to-flow-elements.ts", (bien) => {
    expect(TS).toContain(bien);
  });

  /**
   * Điều kiện thật sự phải đúng: giá trị của ba biến phải ĐI QUA một biến `--color-*`, vì chỉ
   * `--color-*` mới được suy ra từ kênh RGB và chỉ kênh RGB mới đảo theo chế độ. Khai chúng bằng
   * hex ở `:root` sẽ làm ca trên xanh mà lỗi vẫn còn nguyên.
   */
  it.each(BIEN)("%s trỏ vào một biến --color-*, không phải một giá trị đóng băng", (bien) => {
    const m = new RegExp(`${bien}\\s*:\\s*([^;]+);`).exec(CSS);
    expect(m, `không đọc được giá trị của ${bien}`).not.toBeNull();
    expect(
      m![1]!.trim(),
      `${bien} phải trỏ vào var(--color-…): chỉ nhóm biến ấy mới đảo theo chế độ tối. ` +
        "Một giá trị hex ở đây làm bài kiểm xanh mà đường vẽ vẫn kẹt ở bảng màu sáng."
    ).toMatch(/^var\(--color-[a-z-]+\)$/);
  });

  /**
   * Và điều kiện ngược: ba biến này KHÔNG được lặp lại trong khối `.dark`. Chúng đã tự đúng nhờ
   * `--color-*`; khai lại là dựng thêm một chỗ để lệch, đúng cái mà khối chú thích đầu globals.css
   * cấm ("khai đúng một lần ở :root … nhờ vậy đảo chế độ chỉ phải sửa MỘT bảng").
   */
  it("không biến nào bị khai lại trong khối .dark", () => {
    const dark = CSS.slice(CSS.indexOf("\n.dark {"));
    const lap = BIEN.filter((b) => dark.includes(`${b}:`));
    expect(
      lap,
      "ba biến này suy ra từ --color-*, vốn đã đảo theo chế độ. Khai lại trong khối tối chỉ " +
        "tạo thêm một bản sao để sớm muộn lệch nhau."
    ).toEqual([]);
  });
});
