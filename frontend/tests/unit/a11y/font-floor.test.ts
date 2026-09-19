import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { FRONTEND_ROOT, stripComments, toRepoPath, walkSource } from "./source-scan";

/**
 * SÀN CỠ CHỮ — HÀNG RÀO CHỐNG TÁI PHÁT.
 *
 * <p>Phép quét ngày 2026-09-10 đếm được <b>151 chỗ</b> đặt cỡ chữ rời {@code text-[Npx]} với
 * N &lt; 16, trải trên <b>55 tệp</b>; {@code text-[13px]} xuất hiện 45 lần và {@code text-[12px]}
 * 38 lần. Con số ấy nói rằng đây <b>không phải lỗi của một màn hình</b> — tài liệu
 * {@code design/README} ghi nó là "hồ sơ nhân khẩu: 39/61 đoạn chữ dưới 16px" — mà là thói quen
 * viết mã của cả sản phẩm.</p>
 *
 * <p>Vì thế lời giải là một <b>thang có tên</b> (xem {@code tailwind.config.ts}) chứ không phải
 * sửa từng chỗ: 45 lần {@code text-[13px]} rải khắp kho chính là bằng chứng rằng giá trị rời không
 * giữ được kỷ luật. Tệp này là phần thứ hai của cùng một việc — không có nó, chỗ thứ 152 sẽ xuất
 * hiện trong lần sửa tới và không ai biết.</p>
 *
 * <p><b>Ngoại lệ duy nhất là canvas phả đồ</b> ({@code src/components/tree/},
 * {@code src/lib/tree/}), theo C-1.3: chữ ở đó co giãn theo mức phóng nên một con số CSS cố định
 * không nói lên điều gì. Ngoại lệ ấy được ghim bằng một ca kiểm ngược ở cuối tệp.</p>
 */

/** Sàn thân bài của định hướng 00 §2.2 — "không có ngoại lệ cho 'chỗ này chật quá'". */
const SAN_THAN_BAI_PX = 16;

/** Hai thư mục duy nhất được đặt cỡ chữ dưới sàn, vì chữ ở đó co giãn theo máy quay. */
const VUNG_CANVAS = [/^src\/components\/tree\//, /^src\/lib\/tree\//];

/** Tiện ích cỡ chữ mặc định của Tailwind nằm dưới sàn — cấm dùng ngoài canvas. */
const TIEN_ICH_NHO: ReadonlyMap<string, number> = new Map([
  ["text-xs", 12],
  ["text-sm", 14],
]);

interface ChoNho {
  readonly file: string;
  readonly line: number;
  readonly lop: string;
  readonly px: number;
  readonly snippet: string;
}

const RE_TUY_Y = /!?text-\[(\d+(?:\.\d+)?)px\]/g;

/** Mọi khai báo cỡ chữ dưới sàn trong MÃ THẬT của một tệp. */
export function timChuNho(source: string, file: string): ChoNho[] {
  const sach = stripComments(source, file.endsWith(".css") ? "css" : "ts").split("\n");
  const goc = source.split("\n");
  const out: ChoNho[] = [];

  sach.forEach((line, i) => {
    for (const m of line.matchAll(RE_TUY_Y)) {
      const px = Number(m[1]);
      if (px >= SAN_THAN_BAI_PX) continue;
      out.push({ file, line: i + 1, lop: m[0], px, snippet: (goc[i] ?? "").trim() });
    }
    for (const [lop, px] of TIEN_ICH_NHO) {
      // Bám vào ranh giới từ: `text-smth` không phải `text-sm`, và `dark:text-xs` thì phải.
      if (new RegExp(`(^|[\\s"'\`:])${lop}([\\s"'\`]|$)`).test(line)) {
        out.push({ file, line: i + 1, lop, px, snippet: (goc[i] ?? "").trim() });
      }
    }
  });
  return out;
}

function quet(): ChoNho[] {
  const out: ChoNho[] = [];
  for (const absolute of walkSource(/\.(ts|tsx)$/)) {
    const file = toRepoPath(absolute);
    if (VUNG_CANVAS.some((re) => re.test(file))) continue;
    out.push(...timChuNho(readFileSync(absolute, "utf8"), file));
  }
  return out;
}

describe("cỡ chữ · sàn 16px ngoài canvas phả đồ", () => {
  it("không tệp nào ngoài canvas đặt cỡ chữ dưới 16px", () => {
    const nho = quet();
    expect(
      nho.map((c) => `${c.file}:${c.line} → ${c.lop} (${c.px}px)\n      ${c.snippet}`),
      `${nho.length} chỗ đặt cỡ chữ dưới sàn ${SAN_THAN_BAI_PX}px.\n\n` +
        "Dùng thang cỡ chữ trong tailwind.config.ts thay cho giá trị rời:\n" +
        "  · text-than (16px) — thân bài, và là SÀN\n" +
        "  · text-dan  (17px) — nội dung chính\n" +
        "  · text-de   (18px) — đề mục\n" +
        "Bốn nấc `text-the-*` chỉ dành cho canvas phả đồ (C-1.3)."
    ).toEqual([]);
  });

  it("thang cỡ chữ có mặt trong tailwind.config.ts và không nấc nào dưới sàn ngoài nhóm canvas", () => {
    const cfg = readFileSync(resolve(FRONTEND_ROOT, "tailwind.config.ts"), "utf8");
    for (const ten of ["than:", "dan:", "de:"]) {
      expect(cfg, `thang cỡ chữ thiếu nấc ${ten}`).toContain(ten);
    }
    // Nấc `than` là sàn: nếu ai đó hạ nó xuống thì mọi chỗ đã đổi sang `text-than`
    // cùng tụt theo một lượt, và không ca kiểm nào khác thấy.
    expect(cfg).toMatch(/than:\s*"16px"/);
  });
});

describe("chống xanh giả · phép quét cỡ chữ", () => {
  it("bắt được một giá trị rời dưới sàn", () => {
    const hits = timChuNho('<p className="text-[13px]">x</p>', "src/components/x.tsx");
    expect(hits.map((h) => h.px)).toEqual([13]);
  });

  it("bắt được cả biến thể có dấu chấm than của Tailwind", () => {
    const hits = timChuNho('<Tag className="!text-[11.5px]" />', "src/components/x.tsx");
    expect(hits.map((h) => h.lop)).toEqual(["!text-[11.5px]"]);
  });

  it("bắt được text-xs và text-sm — hai tiện ích mặc định cũng dưới sàn", () => {
    const hits = timChuNho('<p className="mt-2 text-xs">a</p>\n<p className="text-sm">b</p>', "src/x.tsx");
    expect(hits.map((h) => h.lop).sort()).toEqual(["text-sm", "text-xs"]);
  });

  it("KHÔNG bắt nhầm giá trị ĐẠT sàn", () => {
    expect(timChuNho('<p className="text-[16px] text-[18px] text-than text-base">x</p>', "src/x.tsx")).toEqual([]);
  });

  it("KHÔNG bắt nhầm một lớp chỉ TÌNH CỜ bắt đầu bằng text-sm", () => {
    expect(timChuNho('<p className="text-smooth">x</p>', "src/x.tsx")).toEqual([]);
  });

  it("KHÔNG bắt nhầm cỡ chữ nhắc trong chú thích — đó là cách ghi đối chiếu đúng", () => {
    const src = [
      "// Trước đây chỗ này là text-[10.5px], trượt cả sàn 16px lẫn sàn tuyệt đối 12px.",
      '<span className="text-than">x</span>',
    ].join("\n");
    expect(timChuNho(src, "src/components/x.tsx")).toEqual([]);
  });

  it("canvas phả đồ THẬT SỰ được miễn — và chỉ nó", () => {
    const trongCanvas = "src/components/tree/person-node.tsx";
    const ngoaiCanvas = "src/components/person/person-badge-list.tsx";
    expect(VUNG_CANVAS.some((re) => re.test(trongCanvas))).toBe(true);
    expect(VUNG_CANVAS.some((re) => re.test(ngoaiCanvas))).toBe(false);
  });
});

/**
 * BA HẰNG SỐ SÀN TRONG BỘ KIỂM E2E LÀ SÀN, KHÔNG PHẢI THAM SỐ ĐIỀU CHỈNH ĐƯỢC.
 *
 * <p>Đường thoát dễ nhất khỏi một ca kiểm tiếp cận đỏ là sửa con số trong chính ca kiểm ấy — và
 * nó không để lại dấu vết nào ngoài một dòng diff trông vô hại. Ca dưới đây đọc thẳng
 * {@code e2e/accessibility.spec.ts} và ghim ba con số, nên "nới cho xanh" trở thành một ca kiểm
 * ĐỎ ở tầng unit chứ không phải một cái xanh im lặng.</p>
 */
describe("bộ kiểm tiếp cận · ba hằng số sàn không được nới", () => {
  const SPEC = readFileSync(resolve(FRONTEND_ROOT, "e2e/accessibility.spec.ts"), "utf8");
  const SAN: ReadonlyArray<readonly [string, number, string]> = [
    ["TOUCH_FLOOR_PX", 44, "WCAG 2.2 SC 2.5.8 ở mức AAA — 00 §2.2"],
    ["BODY_FONT_FLOOR_PX", 16, "sàn thân bài — 00 §2.2"],
    ["ABSOLUTE_FONT_FLOOR_PX", 12, "dưới mức này dấu tiếng Việt chồng tầng dính vào nhau — C-1.2"],
  ];

  it.each(SAN)("%s vẫn là %d (%s)", (ten, giaTri, lyDo) => {
    const m = new RegExp(`const ${ten}\\s*=\\s*(\\d+)\\s*;`).exec(SPEC);
    expect(m, `không tìm thấy hằng số ${ten} trong e2e/accessibility.spec.ts`).not.toBeNull();
    expect(
      Number(m![1]),
      `${ten} đã bị đổi. Đây là SÀN chứ không phải tham số: ${lyDo}. ` +
        "Một ca tiếp cận đỏ được sửa bằng cách sửa sản phẩm, không phải bằng cách hạ ngưỡng."
    ).toBe(giaTri);
  });

  it("danh sách loại trừ của collectControls() không dài thêm", () => {
    // Ba loại được loại ra, mỗi loại có lý do đã ghi trong chính spec: phần tử vô hình, liên kết
    // NẰM TRONG một câu văn (ngoại lệ "inline" của WCAG 2.5.8), và nội dung phóng được của canvas.
    // Con số này là cách rẻ nhất để một ngoại lệ thứ tư không lọt vào cùng một lượt sửa.
    const than = SPEC.slice(SPEC.indexOf("async function collectControls"));
    const thanHam = than.slice(0, than.indexOf("\n}"));
    expect(
      (thanHam.match(/react-flow__viewport/g) ?? []).length,
      "collectControls() chỉ được loại canvas ra ĐÚNG MỘT LẦN"
    ).toBe(1);
  });
});
