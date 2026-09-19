import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  colorTokens,
  darkColorTokens,
  hexToRgbChannels,
  rgbVar,
  type ColorTokenName,
} from "@/styles/tokens";

/**
 * globals.css khai lại bảng màu bằng tay dưới dạng kênh RGB — CSS không import
 * được TypeScript. Đó là một chỗ trùng lặp thật, và chỗ trùng lặp nào cũng lệch
 * sớm muộn. Tệp này là thứ giữ hai bên khớp nhau.
 *
 * Nó cũng canh cái bẫy mà 00 §3 gọi tên: "màu chỉ tồn tại trong khối tối là lỗi
 * kinh điển gây trang không đọc được". Ở trạng thái "theo hệ thống" trình duyệt
 * chỉ thấy khối :root.
 */
// Bỏ chú thích trước khi tìm: bản thân globals.css có bình luận nhắc lại chuỗi
// `.dark { --c: 3 }` như một ví dụ, và indexOf sẽ vớ phải nó thay vì quy tắc thật.
const CSS = readFileSync(
  resolve(__dirname, "../../../src/app/globals.css"),
  "utf8",
).replace(/\/\*[\s\S]*?\*\//g, "");

const TEN_TOKEN = Object.keys(colorTokens) as ColorTokenName[];

/** Cắt ra thân của một khối CSS bắt đầu tại `selector`, đếm ngoặc để khỏi dính khối lồng. */
function thanKhoi(selector: string): string {
  const dau = CSS.indexOf(selector);
  expect(dau, `không tìm thấy khối "${selector}" trong globals.css`).toBeGreaterThan(-1);
  let i = CSS.indexOf("{", dau);
  let sau = 1;
  const batDau = i + 1;
  while (sau > 0) {
    i += 1;
    if (CSS[i] === "{") sau += 1;
    else if (CSS[i] === "}") sau -= 1;
  }
  return CSS.slice(batDau, i);
}

function docBien(than: string, bien: string): string | undefined {
  const m = than.match(new RegExp(`${bien}\\s*:\\s*([^;]+);`));
  return m?.[1]?.trim();
}

const rootBody = thanKhoi(":root {");
const darkBody = thanKhoi(".dark {");
const mediaBody = thanKhoi(":root:not(.light) {");

describe("globals.css :root — mọi màu phải khai ở đây trước", () => {
  it.each(TEN_TOKEN)("%s có kênh RGB ở :root", (ten) => {
    expect(docBien(rootBody, rgbVar(ten))).toBeDefined();
  });

  it.each(TEN_TOKEN)("%s có tên màu --color-* suy ra từ kênh", (ten) => {
    const kebab = ten.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
    expect(docBien(rootBody, `--color-${kebab}`)).toBe(`rgb(var(${rgbVar(ten)}))`);
  });

  it.each(TEN_TOKEN)("kênh của %s khớp hex trong tokens.ts", (ten) => {
    expect(docBien(rootBody, rgbVar(ten))).toBe(hexToRgbChannels(colorTokens[ten]));
  });

  it("không có màu nào chỉ tồn tại trong khối tối", () => {
    const chiCoOToi = Object.keys(darkColorTokens).filter(
      (ten) => docBien(rootBody, rgbVar(ten as ColorTokenName)) === undefined,
    );
    expect(chiCoOToi).toEqual([]);
  });
});

describe("globals.css — hai đường vào chế độ tối phải nói cùng một điều", () => {
  it.each(TEN_TOKEN)("khối .dark khai %s khớp darkColorTokens", (ten) => {
    expect(docBien(darkBody, rgbVar(ten))).toBe(hexToRgbChannels(darkColorTokens[ten]));
  });

  it.each(TEN_TOKEN)(
    "khối @media prefers-color-scheme khai %s giống hệt khối .dark",
    (ten) => {
      expect(docBien(mediaBody, rgbVar(ten))).toBe(
        docBien(darkBody, rgbVar(ten)),
      );
    },
  );

  it("khối tối KHÔNG khai lại --color-*: chỉ đảo kênh, để đảo một bảng là đủ", () => {
    expect(darkBody).not.toMatch(/--color-[a-z-]+\s*:/);
    expect(mediaBody).not.toMatch(/--color-[a-z-]+\s*:/);
  });

  it("cả hai khối tối đều đặt color-scheme: dark cho điều khiển của hệ điều hành", () => {
    expect(docBien(darkBody, "color-scheme")).toBe("dark");
    expect(docBien(mediaBody, "color-scheme")).toBe("dark");
  });

  it("đường 'theo hệ thống' phải nhường khi người dùng đã chọn tay Sáng", () => {
    // `:root:not(.light)` — thiếu `:not(.light)` thì công tắc "Sáng" vô tác dụng
    // trên máy đang để hệ điều hành ở chế độ tối.
    expect(CSS).toContain(":root:not(.light)");
  });
});

describe("globals.css — giảm chuyển động", () => {
  const rm = thanKhoi("@media (prefers-reduced-motion: reduce)");

  it("có xử lý ở tầng chung, không phải sửa từng chỗ", () => {
    expect(rm).toContain("transition-property");
    expect(rm).toContain("animation-duration");
  });

  it("ép ba biến thời lượng CHUYỂN ĐỘNG về 0", () => {
    for (const bien of [
      "--thoi-luong-khoi",
      "--thoi-luong-lop-phu",
      "--thoi-luong-canh-khung",
    ]) {
      expect(rm).toMatch(new RegExp(`${bien}\\s*:\\s*0ms`));
    }
  });

  it("GIỮ chuyển màu: bỏ luôn cả nó là mất phản hồi thị giác", () => {
    // Danh sách trắng chỉ gồm thuộc tính không dời pixel nào.
    const dsTrang = rm.match(/transition-property:([^;]+)/)?.[1] ?? "";
    expect(dsTrang).toContain("color");
    expect(dsTrang).not.toContain("transform");
    expect(dsTrang).not.toContain("all");
    expect(rm).not.toMatch(/--thoi-luong-mau\s*:\s*0ms/);
  });
});

describe("globals.css — vòng tiêu điểm và sàn chữ", () => {
  it("dùng vòng hai lớp, không dùng hổ phách đơn sắc", () => {
    expect(CSS).toContain("outline: 2px solid var(--color-focus-ring)");
    expect(CSS).toContain("box-shadow: 0 0 0 2px var(--color-focus-halo)");
    expect(CSS).not.toContain("outline: 2px solid var(--color-accent)");
  });

  /**
   * Bất biến này ĐÃ ĐỔI, và đây là ca kiểm ghi lại vì sao.
   *
   * <p>Bản trước khẳng định quy tắc vòng tiêu điểm phải CHỪA widget Ant Design ra
   * (`:not([class*="ant-"])`), với lý do "vòng mặc định của AntD đã tạm chấp nhận
   * được". Đo trên trình duyệt thì không: ô &lt;Select&gt; khi nhận tiêu điểm cho
   * <b>1,00:1</b> — tức không có dấu hiệu nào — và trong kho có 13 ô Select /
   * DatePicker, nằm đúng ở biểu mẫu sửa người và hộp thoại xin đính chính.</p>
   *
   * <p>Nhưng phần còn lại của lý do cũ thì đúng: chồng vòng lên vòng cho ra hai
   * vòng lồng nhau, và widget hợp thành của AntD đặt tiêu điểm lên một ô nhập
   * TRONG SUỐT. Nên bất biến mới gồm hai vế, và cả hai đều phải có mặt: vẽ vòng
   * lên phần vỏ NHÌN THẤY ĐƯỢC, và TẮT vòng ở ô nhập ẩn bên trong. Thiếu vế thứ
   * hai thì phép kiểm tiếp cận đọc được một vòng trên phần tử vô hình và báo
   * xanh — trong khi người dùng vẫn không thấy gì.</p>
   */
  it("vẽ vòng lên phần vỏ nhìn thấy được của widget AntD", () => {
    expect(CSS).not.toContain(':focus-visible:not([class*="ant-"])');
    expect(CSS).toContain(".ant-select-focused .ant-select-selector");
    expect(CSS).toContain(".ant-picker-focused");
  });

  it("TẮT vòng ở ô nhập trong suốt bên trong, để không thành hai vòng lồng nhau", () => {
    expect(CSS).toContain(".ant-select .ant-select-selection-search-input:focus-visible");
    expect(CSS).toContain(".ant-segmented .ant-segmented-item-input:focus-visible");
  });

  it("thân bài đặt sàn 16px của 00 §2.2", () => {
    expect(thanKhoi("body {")).toMatch(/font-size:\s*16px/);
  });
});
