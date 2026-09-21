import { readFileSync } from "node:fs";
import postcss from "postcss";
import tailwindcss from "tailwindcss";
import type { Config } from "tailwindcss";
import { beforeAll, describe, expect, it } from "vitest";
import baseConfig from "../../../tailwind.config";
import { stripComments, toRepoPath, walkSource } from "./source-scan";

/**
 * MỌI LỚP TIỆN ÍCH MÀU ĐANG VIẾT TRONG `src/` PHẢI THẬT SỰ SINH RA CSS.
 *
 * <p>Đây chính là bất biến mà lỗi phả đồ vừa rồi thiếu. {@code person-node.tsx} viết
 * {@code bg-success} cho chấm "người còn sống", nhưng {@code success} chưa được khai trong
 * {@code tailwind.config.ts}. Tailwind <b>không báo gì cả</b> — không cảnh báo, không lỗi biên
 * dịch, không lỗi lúc chạy. Lớp ấy đơn giản là không tồn tại, chấm màu trong suốt, và trên phả đồ
 * người sống với người đã khuất gần như không phân biệt được. Chú giải thì vẫn vẽ đúng hai chấm,
 * vì nó tô màu bằng đường khác — nên nhìn bằng mắt còn tưởng là đúng.</p>
 *
 * <p>Cách kiểm: <b>chạy Tailwind thật</b> trên đúng cấu hình của dự án, rồi hỏi từng lớp một xem
 * nó có mặt trong CSS đầu ra không. Cố ý không so danh sách tên token với danh sách tên trong
 * config bằng chuỗi — làm thế là kiểm lại giả định của chính mình, còn ở đây câu hỏi thật là
 * "lớp này có sinh ra CSS không", và chỉ Tailwind mới trả lời được.</p>
 */


/**
 * Tiền tố của những nhóm tiện ích CÓ THỂ mang màu. Quét hẹp lại như vậy để tránh nhặt phải mọi
 * chuỗi trong mã; những nhóm không mang màu ({@code text-sm}, {@code border-2}) vẫn được đưa qua
 * Tailwind và vẫn sinh CSS, nên chúng tự động xanh chứ không cần một danh sách trắng nào.
 */
const COLOR_PREFIXES = [
  "bg",
  "text",
  "border",
  "ring",
  "outline",
  "divide",
  "fill",
  "stroke",
  "decoration",
  "placeholder",
  "caret",
  "accent",
  "shadow",
  "from",
  "via",
  "to",
];

const CLASS_CANDIDATE = new RegExp(
  `(?:^|\\s)((?:[a-z0-9@:[\\]().%!/-]+:)*!?(?:${COLOR_PREFIXES.join("|")})-[^\\s"'\`{}]+)`,
  "g"
);

/**
 * Một lớp Tailwind **không bao giờ chứa dấu `=`** — dấu ấy nói rằng thứ vừa bắt
 * được là một **thuộc tính đánh dấu**, không phải một lớp.
 *
 * <p>Ca thật đã dựng ra phép lọc này: `src/mocks/handlers/posts.ts` vẽ ảnh mẫu
 * bằng một chuỗi SVG nội tuyến, trong đó có `text-anchor="middle"`. Bộ quét đọc
 * mọi chuỗi trong tệp (vì `className` hay được ghép từ mảng và từ biểu thức ba
 * ngôi, nên không thể chỉ đọc đúng thuộc tính `className`), và `text-` lại đúng
 * là một tiền tố màu — nên nó báo `text-anchor=` là một lớp chết.</p>
 *
 * <p>Đây là nới phép quét, nên phải nói rõ nó KHÔNG nới cái gì: một lớp viết sai
 * thật, như `bg-bg-deceased`, không có dấu `=` nào và vẫn bị bắt như cũ. Phép
 * lọc này chỉ loại đúng thứ mà cú pháp đã chứng minh là không phải lớp.</p>
 */
function laLopThat(className: string): boolean {
  return !className.includes("=");
}

interface Usage {
  readonly className: string;
  readonly file: string;
  readonly line: number;
}

/**
 * Rút mọi lớp tiện ích màu từ các CHUỖI trong mã nguồn.
 *
 * <p>Chỉ đọc trong dấu nháy: `className` được ghép từ mảng, từ biểu thức ba ngôi, từ
 * {@code [...].join(" ")} — mọi kiểu đều rơi về những chuỗi ký tự, nên đọc chuỗi bắt được hết mà
 * không phải phân tích cú pháp JSX.</p>
 *
 * <p>Chú thích bị bỏ TRƯỚC khi quét. Đây là một cái đỏ giả đã xảy ra thật: chú thích tiếng Việt
 * trong {@code person-node.tsx} viết <i>"`outline-offset` ÂM kéo vòng vào trong…"</i>, và cặp dấu
 * huyền quanh tên thuộc tính CSS trông y hệt một chuỗi mẫu. Phép quét nhặt nó lên rồi báo "lớp
 * này không sinh CSS" — trong khi nó chỉ là một câu văn.</p>
 */
function collectUsages(): Usage[] {
  const usages: Usage[] = [];
  const stringLiteral = /(["'`])((?:\\.|(?!\1)[^\\])*)\1/g;
  for (const absolute of walkSource(/\.(ts|tsx)$/)) {
    const file = toRepoPath(absolute);
    const source = stripComments(readFileSync(absolute, "utf8"), "ts");
    const lines = source.split("\n");
    lines.forEach((line, index) => {
      for (const literal of line.matchAll(stringLiteral)) {
        const body = literal[2] ?? "";
        for (const match of ` ${body} `.matchAll(CLASS_CANDIDATE)) {
          const className = match[1]!;
          if (!laLopThat(className)) continue;
          usages.push({ className, file, line: index + 1 });
        }
      }
    });
  }
  return usages;
}

/** Sinh CSS cho một tập lớp cụ thể, dùng đúng cấu hình Tailwind của dự án. */
async function buildCss(classNames: readonly string[], config: Config): Promise<string> {
  const scoped: Config = {
    ...config,
    content: [{ raw: classNames.join(" "), extension: "html" }],
  };
  const result = await postcss([tailwindcss(scoped)]).process(
    "@tailwind base;@tailwind components;@tailwind utilities;",
    { from: undefined }
  );
  // Tailwind thoát ký tự đặc biệt bằng dấu chéo ngược (`.bg-bg-page\/95`). Bỏ hết dấu chéo ngược
  // đi thì tên lớp trong CSS trùng đúng tên lớp người viết — so sánh được thẳng.
  return result.css.replace(/\\/g, "");
}

/** Lớp có mặt trong CSS đầu ra hay không. */
function isGenerated(css: string, className: string): boolean {
  const escaped = className.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  // Theo sau tên lớp phải là một ký tự KHÔNG thuộc tên lớp, để `.bg-primary` không khớp nhầm
  // vào `.bg-primary-light`.
  return new RegExp(`\\.${escaped}(?![\\w-])`).test(css);
}

let usages: Usage[] = [];
let css = "";

beforeAll(async () => {
  usages = collectUsages();
  const unique = [...new Set(usages.map((u) => u.className))];
  css = await buildCss(unique, baseConfig);
}, 120_000);

describe("Tailwind · mọi lớp tiện ích màu đang dùng đều sinh ra CSS thật", () => {
  it("quét được một lượng lớp hợp lý — phép quét rỗng thì mọi khẳng định dưới đây vô nghĩa", () => {
    expect(usages.length, "không quét được lớp nào trong src/ — mẫu nhận dạng đã hỏng").toBeGreaterThan(100);
    expect(css.length, "Tailwind không sinh ra CSS nào").toBeGreaterThan(1000);
  });

  it("không lớp nào im lặng không tồn tại", () => {
    const dead = new Map<string, Usage[]>();
    for (const usage of usages) {
      if (isGenerated(css, usage.className)) continue;
      const list = dead.get(usage.className) ?? [];
      list.push(usage);
      dead.set(usage.className, list);
    }
    const report = [...dead.entries()]
      .map(
        ([className, where]) =>
          `  · ${className}\n${where.map((w) => `      ${w.file}:${w.line}`).join("\n")}`
      )
      .join("\n");
    expect(
      [...dead.keys()],
      `${dead.size} lớp Tailwind KHÔNG sinh ra CSS nào — chúng im lặng không có tác dụng, ` +
        `đúng kiểu lỗi đã làm phả đồ không phân biệt được người sống với người đã khuất:\n${report}\n\n` +
        "Cách sửa: khai token thiếu trong tailwind.config.ts (lấy giá trị từ src/styles/tokens.ts), " +
        "hoặc sửa tên lớp viết sai."
    ).toEqual([]);
  });
});

/** ---- Phép kiểm ngược: dựng lại đúng lỗi cũ và đòi phép kiểm phải KÊU ---- */
describe("chống xanh giả · phép kiểm lớp Tailwind", () => {
  it("dựng lại lỗi cũ: bỏ token `success` khỏi config thì `bg-success` phải bị báo chết", async () => {
    const colors = { ...(baseConfig.theme?.extend?.colors as Record<string, unknown>) };
    delete colors.success;
    // Ép kiểu ở đây là cố ý: ta đang dựng một cấu hình CỐ TÌNH THIẾU một token, thứ mà kiểu của
    // Tailwind không mô tả được. Bản thân việc nó không hợp lệ chính là điều đang được kiểm.
    const crippled = {
      ...baseConfig,
      theme: {
        ...baseConfig.theme,
        extend: { ...baseConfig.theme?.extend, colors },
      },
    } as unknown as Config;
    const before = await buildCss(["bg-success"], baseConfig);
    const after = await buildCss(["bg-success"], crippled);

    expect(
      isGenerated(before, "bg-success"),
      "với cấu hình hiện tại `bg-success` phải sinh ra CSS"
    ).toBe(true);
    expect(
      isGenerated(after, "bg-success"),
      "bỏ token `success` đi mà phép kiểm vẫn báo `bg-success` còn sống — phép kiểm hỏng, " +
        "và lỗi phả đồ sẽ quay lại được"
    ).toBe(false);
  }, 120_000);

  /**
   * ĐỎ GIẢ đã xảy ra thật, ghim lại để nó không tái diễn: chú thích tiếng Việt trong
   * {@code person-node.tsx} viết <i>"`outline-offset` ÂM kéo vòng vào trong…"</i>. Cặp dấu huyền
   * quanh tên thuộc tính CSS trông y hệt một chuỗi mẫu, và phép quét đã báo "lớp này không sinh
   * CSS" cho một câu văn. Một bộ kiểm hay báo nhầm sẽ bị người ta tắt đi — nên đây cũng là một
   * kiểu hỏng, không nhẹ hơn bỏ sót.
   */
  it("không nhặt nhầm một tên lớp nằm trong chú thích", () => {
    const source = [
      "// `outline-offset` ÂM kéo vòng vào trong đúng (80 − 24) / 2 = 28px",
      "/* dùng `bg-khong-ton-tai` ở đây thì hỏng */",
      'const cls = "bg-primary";',
    ].join("\n");
    const stripped = stripComments(source, "ts");
    const found = [...stripped.matchAll(/(["'`])((?:\\.|(?!\1)[^\\])*)\1/g)]
      .flatMap((m) => [...` ${m[2] ?? ""} `.matchAll(CLASS_CANDIDATE)])
      .filter((m) => laLopThat(m[1] ?? ""))
      .map((m) => m[1]);
    expect(found).toEqual(["bg-primary"]);
  });

  it("một tên màu bịa ra không bao giờ sinh CSS", async () => {
    const generated = await buildCss(["bg-mau-khong-ton-tai", "text-mau-khong-ton-tai"], baseConfig);
    expect(isGenerated(generated, "bg-mau-khong-ton-tai")).toBe(false);
    expect(isGenerated(generated, "text-mau-khong-ton-tai")).toBe(false);
  }, 120_000);

  it("không khớp nhầm lớp con: `.bg-primary` không được tính là có mặt nhờ `.bg-primary-light`", async () => {
    const generated = await buildCss(["bg-primary-light"], baseConfig);
    expect(isGenerated(generated, "bg-primary-light")).toBe(true);
    expect(
      isGenerated(generated, "bg-primary"),
      "phép so khớp quá lỏng — một lớp chết sẽ được báo là sống nhờ trùng tiền tố"
    ).toBe(false);
  }, 120_000);

  it("lớp có biến thể và độ mờ vẫn nhận diện đúng", async () => {
    const generated = await buildCss(
      ["hover:bg-primary-light", "border-primary/50", "dark:bg-bg-card"],
      baseConfig
    );
    expect(isGenerated(generated, "hover:bg-primary-light")).toBe(true);
    expect(isGenerated(generated, "border-primary/50")).toBe(true);
    expect(isGenerated(generated, "dark:bg-bg-card")).toBe(true);
  }, 120_000);
});
