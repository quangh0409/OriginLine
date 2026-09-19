import { resolve } from "node:path";
import { readFileSync } from "node:fs";
import postcss from "postcss";
import tailwind from "tailwindcss";
import { beforeAll, describe, expect, it } from "vitest";
import tailwindConfig from "../../../tailwind.config";

/**
 * Dựng CSS THẬT rồi soi đầu ra, thay vì đọc tệp nguồn.
 *
 * Lý do tệp này tồn tại là một lỗi có thật gặp trong đợt sửa: khối `.dark { ... }`
 * viết đúng trong `src/app/globals.css`, đọc mã thấy đúng, test đọc tệp nguồn
 * cũng thấy đúng — nhưng Tailwind v3 LOẠI BỎ mọi quy tắc có bộ chọn theo lớp ra
 * khỏi `@layer base` khi biên dịch. Không cảnh báo, không lỗi. Hậu quả trên màn
 * hình: chế độ "theo hệ thống" chạy (vì `@media` không bị loại) còn công tắc
 * "Tối" của người dùng thì không làm gì cả — hỏng đúng MỘT trong ba trạng thái,
 * chỗ dễ bỏ sót nhất khi thử tay.
 *
 * Chỉ có kiểm tra ĐẦU RA mới bắt được loại lỗi này.
 */
const NGUON = resolve(__dirname, "../../../src/app/globals.css");

// Chuỗi lớp dùng làm "nội dung" giả để Tailwind sinh đúng những tiện ích cần soi.
const NOI_DUNG = `
  bg-bg-page bg-bg-card bg-deceased bg-accent bg-success bg-primary
  text-text-main text-text-muted text-accent text-accent-ruc text-success text-primary
  border-border border-border-dark border-border-input
  outline-accent outline-focus bg-bg-page/95 border-primary/40 dark:bg-bg-card
`;

let css = "";

beforeAll(async () => {
  const ket_qua = await postcss([
    tailwind({
      ...tailwindConfig,
      content: [{ raw: NOI_DUNG, extension: "html" }],
    }),
  ]).process(readFileSync(NGUON, "utf8"), { from: NGUON });
  // Bỏ chú thích: globals.css có bình luận nhắc lại chuỗi `.dark { --c: 3 }`
  // như ví dụ, và indexOf sẽ vớ phải nó thay vì quy tắc thật.
  css = ket_qua.css.replace(/\/\*[\s\S]*?\*\//g, "");
}, 60_000);

/** Lấy phần khai báo của một quy tắc trong CSS đầu ra. */
function than(bo_chon: string): string {
  const i = css.indexOf(bo_chon);
  expect(i, `bộ chọn "${bo_chon}" KHÔNG có trong CSS đầu ra`).toBeGreaterThan(-1);
  return css.slice(i, css.indexOf("}", i));
}

describe("CSS đầu ra — chế độ tối sống sót qua bước dựng", () => {
  it("khối .dark có mặt (đây là thứ từng bị Tailwind xoá mất)", () => {
    const t = than(".dark {");
    expect(t).toContain("--rgb-bg-page: 26 22 20");
    expect(t).toContain("--rgb-text-main: 239 232 223");
    expect(t).toContain("color-scheme: dark");
  });

  it("khối 'theo hệ thống' có mặt và có loại trừ .light", () => {
    expect(css).toContain("@media (prefers-color-scheme: dark)");
    const t = than(":root:not(.light) {");
    expect(t).toContain("--rgb-bg-page: 26 22 20");
  });

  it("bảng màu sáng ở :root có mặt", () => {
    expect(css).toContain("--rgb-bg-page: 248 246 242");
    expect(css).toContain("--color-bg-page: rgb(var(--rgb-bg-page))");
  });

  it("hai khối tối phải nằm NGOÀI @layer base, nếu không sẽ bị xoá lại", () => {
    // Tailwind gộp @layer base vào phần đầu tệp; kiểm gián tiếp bằng việc quy tắc
    // vẫn còn sau khi dựng chính là bằng chứng. Thêm một chốt nữa cho chắc: đầu
    // vào phải KHÔNG có `.dark` bên trong khối `@layer base`.
    const nguon = readFileSync(NGUON, "utf8").replace(/\/\*[\s\S]*?\*\//g, "");
    const dau_layer = nguon.indexOf("@layer base {");
    const dau_dark = nguon.indexOf("\n.dark {");
    expect(dau_dark).toBeGreaterThan(dau_layer);
    // `.dark` phải đứng sau dấu đóng của @layer base, tức không thụt lề.
    expect(nguon).toMatch(/\n\.dark \{/);
    expect(nguon).not.toMatch(/\n {2}\.dark \{/);
  });
});

describe("CSS đầu ra — ánh xạ lại các tiện ích trượt chuẩn", () => {
  it("text-accent trỏ vào #9c5a06, KHÔNG trỏ vào #d97706", () => {
    expect(than(".text-accent {")).toContain("var(--rgb-accent-text)");
  });

  it("bg-accent vẫn giữ hổ phách rực — mảng tô không cần đổi", () => {
    expect(than(".bg-accent {")).toContain("var(--rgb-accent)");
  });

  it("outline-accent trỏ vào lõi vòng tiêu điểm, không phải hổ phách", () => {
    expect(than(".outline-accent {")).toContain("var(--rgb-focus-ring)");
  });

  it("text-success trỏ vào #116533", () => {
    expect(than(".text-success {")).toContain("var(--rgb-success-text)");
  });

  it("còn lối thoát cố ý: text-accent-ruc giữ sắc gốc", () => {
    expect(than(".text-accent-ruc {")).toContain("var(--rgb-accent)");
  });
});

describe("CSS đầu ra — ba biến đường vẽ phả đồ sống sót qua bước dựng", () => {
  /**
   * Ba biến `--tree-line-*` khai ở `:root` bên trong `@layer base`. `:root` là bộ chọn
   * GIẢ LỚP gốc, không phải bộ chọn theo LỚP, nên nó sống sót — y như bảng màu nằm ngay
   * cạnh. Nhưng "y như bảng màu" là một suy luận, và cả tệp này tồn tại vì suy luận về
   * Tailwind v3 đã sai một lần rồi. Nên: đo đầu ra.
   */
  it.each(["--tree-line-descent", "--tree-line-marriage", "--tree-line-heir"])(
    "%s có mặt trong CSS đầu ra",
    (bien) => {
      expect(css, `${bien} bị mất trong bước dựng — giá trị dự phòng sẽ thắng`).toContain(bien);
    }
  );

  it("và chúng trỏ vào --color-*, tức đảo theo chế độ tối", () => {
    expect(than(":root {")).toContain("--tree-line-marriage: var(--color-accent-text)");
  });
});

describe("CSS đầu ra — vòng tiêu điểm của widget AntD KHÔNG bị @layer base nuốt mất", () => {
  /**
   * Cái bẫy đắt nhất của tệp này, và nó đã cắn lần thứ hai trong đợt sửa tiếp cận: quy
   * tắc vòng tiêu điểm cho Ant Design nhắm vào `.ant-select-focused`,
   * `.ant-picker-focused`, `.ant-input-affix-wrapper` — toàn bộ chọn theo LỚP. Đặt trong
   * `@layer base` thì Tailwind v3 xoá sạch chúng khỏi đầu ra, không cảnh báo. Đọc mã
   * nguồn vẫn thấy đúng; chỉ có đầu ra mới nói thật.
   */
  it.each([
    ".ant-select-focused .ant-select-selector",
    ".ant-picker-focused",
    ".ant-select .ant-select-selection-search-input:focus-visible",
    ".ant-input-affix-wrapper > input.ant-input",
  ])("%s còn trong CSS đầu ra", (bo_chon) => {
    expect(css, `"${bo_chon}" bị Tailwind loại khỏi @layer base — chuyển nó ra ngoài`).toContain(
      bo_chon
    );
  });
});

describe("CSS đầu ra — hậu tố độ mờ vẫn chạy", () => {
  it("bg-bg-page/95 sinh ra alpha thật, không im lặng mất tác dụng", () => {
    expect(css).toContain("rgb(var(--rgb-bg-page) / 0.95)");
  });

  it("border-primary/40 cũng vậy", () => {
    expect(css).toContain("rgb(var(--rgb-primary) / 0.4)");
  });
});

describe("CSS đầu ra — biến thể dark: phủ cả ba trạng thái", () => {
  it("sinh CẢ hai đường: @media cho 'theo hệ thống' và class cho chọn tay", () => {
    expect(css).toMatch(/\.dark\\:bg-bg-card:where\(:not\(\.light \*\)\)/);
    expect(css).toMatch(/\.dark\\:bg-bg-card:where\(\.dark, \.dark \*\)/);
  });
});

describe("CSS đầu ra — giảm chuyển động và vòng tiêu điểm", () => {
  it("khối prefers-reduced-motion còn nguyên sau khi dựng", () => {
    expect(css).toContain("@media (prefers-reduced-motion: reduce)");
    expect(css).toMatch(/--thoi-luong-canh-khung:\s*0ms/);
  });

  it("vòng tiêu điểm hai lớp còn nguyên", () => {
    expect(css).toContain("outline: 2px solid var(--color-focus-ring)");
    expect(css).toContain("box-shadow: 0 0 0 2px var(--color-focus-halo)");
  });
});

/**
 * SÀN CHỨA — ba quy tắc, và cả ba chỉ có giá trị nếu chúng SỐNG SÓT qua bước dựng.
 *
 * <p>Cùng một lý do như khối chế độ tối ở đầu tệp: một quy tắc viết đúng trong `globals.css`
 * nhưng bị Tailwind loại khỏi đầu ra là lỗi <b>không có thông báo</b>, và đọc mã nguồn thì thấy
 * hoàn toàn đúng. `overflow-wrap` và `min-inline-size` dùng bộ chọn theo THẺ nên an toàn — ca
 * kiểm này chốt lại điều đó, để một lần "dọn dẹp" đổi chúng thành bộ chọn theo lớp không âm thầm
 * xoá cả sàn.</p>
 */
describe("CSS đầu ra — sàn chứa, thứ chặn 'thẻ con rộng hơn thẻ cha'", () => {
  it("Preflight vẫn đặt box-sizing: border-box cho MỌI phần tử", () => {
    // Đây là thứ làm cho `w-full` cộng `padding` KHÔNG tràn — nguyên nhân kinh điển số một của
    // lỗi thẻ tràn viền, và nó được chặn ở đây chứ không ở component nào. Tắt Preflight (hoặc
    // đặt `corePlugins: { preflight: false }`) là mở lại cả lớp lỗi ấy cho toàn sản phẩm.
    expect(css).toMatch(/\*,\s*::before,\s*::after\s*\{[^}]*box-sizing:\s*border-box/s);
  });

  it("`body` mang overflow-wrap, nên chuỗi máy dài tự xuống dòng ở khắp nơi", () => {
    // Thuộc tính KẾ THỪA: khai một lần ở `body` là phủ cả widget Ant Design dựng lúc chạy.
    expect(css).toMatch(/body\s*\{[^}]*overflow-wrap:\s*break-word/s);
  });

  it("`fieldset` được trả về min-inline-size: 0", () => {
    // Preflight gỡ margin/padding/border của <fieldset> nhưng KHÔNG gỡ
    // `min-inline-size: min-content` của stylesheet mặc định — hộp duy nhất trong HTML không bao
    // giờ chịu hẹp hơn nội dung. Khối ngày âm–dương của biểu mẫu nhân khẩu nằm trong một cái.
    expect(css).toMatch(/fieldset\s*\{[^}]*min-inline-size:\s*0/s);
  });
});
