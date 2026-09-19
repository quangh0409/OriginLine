import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { FRONTEND_ROOT, SRC_DIR, stripComments, toRepoPath, walkSource } from "./source-scan";

/**
 * SÀN CHỨA — HÀNG RÀO CHỐNG TÁI PHÁT CHO LỖI "THẺ CON RỘNG HƠN THẺ CHA".
 *
 * <p>Chủ dự án báo một triệu chứng duy nhất, trên <b>nhiều màn</b>: thẻ bên trong dài hơn thẻ bao
 * ngoài, tràn ra ngoài viền. Một triệu chứng lặp trên nhiều màn thì nguyên nhân <b>không nằm ở màn
 * nào cả</b>. Nó nằm ở chỗ CSS mặc định cho phép một hộp con từ chối co lại, và sản phẩm chưa có
 * quy ước nào chặn việc đó. Bản vá đi kèm tệp này sửa ở tầng hệ thống — {@code globals.css} cộng
 * hai thành phần dùng chung — còn tệp này là phần thứ hai của cùng một việc: <b>không có nó, chỗ
 * thứ mười một sẽ xuất hiện trong lần sửa tới và không ai biết.</b></p>
 *
 * <h2>Bốn cách một hộp con từ chối co lại, và cách nào cũng là một luật ở đây</h2>
 * <ol>
 *   <li><b>{@code <table>} đặt trần.</b> {@code table-layout: auto} lấy bề rộng bằng tổng
 *       min-content của các cột; {@code w-full} chỉ là sàn, không phải trần. Bảng phải nằm trong
 *       một {@code <VungCuonNgang>}.</li>
 *   <li><b>Con của flex thiếu {@code min-w-0}.</b> {@code flex: 1 1 0%} không thắng được
 *       {@code min-width: auto} — giá trị mặc định nghĩa là "không bao giờ hẹp hơn nội dung".</li>
 *   <li><b>Rãnh lưới {@code 1fr} viết trần</b> trong giá trị tuỳ ý. Tiện ích
 *       {@code grid-cols-N} của Tailwind sinh {@code minmax(0,1fr)} — an toàn — nhưng
 *       {@code grid-cols-[1fr_auto_1fr]} thì đúng nghĩa {@code minmax(auto,1fr)}, tức lại là
 *       "không hẹp hơn nội dung".</li>
 *   <li><b>Chuỗi máy không ngắt được</b> — UUID, đường {@code ltree}, mã ngoại. Sàn
 *       {@code overflow-wrap} ở {@code globals.css} lo phần chữ chảy thường; nhưng ở ô bảng và ô
 *       lưới thì phép tính kích thước NỘI TẠI mới quyết định, và chỉ {@code break-all} mới ăn vào
 *       phép tính ấy.</li>
 * </ol>
 *
 * <h2>Vì sao quét mã nguồn ở đây mà không đo hình học</h2>
 * Tầng này chạy trên jsdom — <b>không có bộ dựng bố cục</b>, mọi
 * {@code getBoundingClientRect()} đều trả 0. Phép đo hình học thật nằm ở
 * {@code e2e/layout-containment.spec.ts}, đo trên trình duyệt thật, ở cả hai chế độ màu và cả khung
 * điện thoại. Hai tầng trả lời hai câu khác nhau: tệp này canh <b>quy ước</b> (rẻ, chạy mọi lượt
 * commit), tệp kia canh <b>kết quả</b> (đắt, chạy ở cổng E2E).
 *
 * <p><b>Chống xanh giả:</b> mỗi bộ dò đều có một ca kiểm ngược ở cuối tệp, dựng lại đúng đoạn mã
 * đã gây lỗi rồi khẳng định bộ dò <b>kêu</b>. Một bất biến không có phép kiểm ngược chỉ chứng minh
 * được rằng nó chưa bao giờ chạy.</p>
 */

/** Canvas phả đồ có hình học riêng (toạ độ tuyệt đối, máy quay, mức phóng) và luật riêng — C-1.3. */
const VUNG_CANVAS = [/^src\/components\/tree\//, /^src\/lib\/tree\//];

/** Chính hai thành phần dùng chung thì được phép "vi phạm" — chúng là nơi luật được cài đặt. */
const THANH_PHAN_NEN = [
  /^src\/components\/common\/vung-cuon-ngang\.tsx$/,
  /^src\/components\/common\/khung-trang\.tsx$/,
];

export interface ViPham {
  readonly file: string;
  readonly line: number;
  readonly vi: string;
  readonly snippet: string;
}

const moTa = (v: ViPham): string => `${v.file}:${v.line} — ${v.vi}\n      ${v.snippet}`;

/* ══════════════════════════════════════════════════════════════════════════════
   BỘ DÒ — hàm thuần trên VĂN BẢN NGUỒN, để ca kiểm ngược nạp được mã giả lập
   ════════════════════════════════════════════════════════════════════════════ */

/** Bám ranh giới từ: `grow-0` không phải `grow`, nhưng `sm:grow` thì phải. */
function coLop(line: string, lop: string): boolean {
  return new RegExp(`(^|[\\s"'\`:])${lop}([\\s"'\`]|$)`).test(line);
}

/**
 * L1 · Mọi {@code <table>} phải nằm trong một {@code <VungCuonNgang>}.
 *
 * <p>Xác định "nằm trong" bằng cách đếm thẻ mở trừ thẻ đóng từ đầu tệp tới đúng vị trí
 * {@code <table}: đếm được số dương nghĩa là đang ở trong một vùng cuộn còn mở. Cách này không phụ
 * thuộc khoảng cách dòng, nên chèn thêm một khối chú thích giữa hai thẻ không làm phép dò sai.</p>
 */
export function timBangKhongCuon(source: string, file: string): ViPham[] {
  const sach = stripComments(source, "ts");
  const out: ViPham[] = [];
  const goc = source.split("\n");

  for (const m of sach.matchAll(/<table\b/g)) {
    const truoc = sach.slice(0, m.index);
    const mo = (truoc.match(/<VungCuonNgang\b/g) ?? []).length;
    const dong = (truoc.match(/<\/VungCuonNgang>/g) ?? []).length;
    if (mo > dong) continue;
    const line = truoc.split("\n").length;
    out.push({
      file,
      line,
      vi: "<table> không nằm trong <VungCuonNgang> — bảng sẽ đẩy thẻ cha tràn khỏi viền và kéo cả trang cuộn ngang",
      snippet: (goc[line - 1] ?? "").trim(),
    });
  }
  return out;
}

/** L2 · Con flex co giãn phải khai {@code min-w-0}. */
export function timConFlexKhongCoDuoc(source: string, file: string): ViPham[] {
  const CO_GIAN = ["flex-1", "flex-auto", "grow", "basis-0"];
  const out: ViPham[] = [];
  const sach = stripComments(source, "ts").split("\n");
  const goc = source.split("\n");

  sach.forEach((line, i) => {
    if (/min-w-0/.test(line)) return;
    const lop = CO_GIAN.find((c) => coLop(line, c));
    if (!lop) return;
    out.push({
      file,
      line: i + 1,
      vi: `\`${lop}\` mà không có \`min-w-0\` — \`min-width: auto\` giữ hộp này không bao giờ hẹp hơn nội dung của nó`,
      snippet: (goc[i] ?? "").trim(),
    });
  });
  return out;
}

/** L3 · Rãnh lưới viết trần {@code 1fr} trong giá trị tuỳ ý. */
export function timRanhLuoi1fr(source: string, file: string): ViPham[] {
  const out: ViPham[] = [];
  const sach = stripComments(source, "ts").split("\n");
  const goc = source.split("\n");

  sach.forEach((line, i) => {
    for (const m of line.matchAll(/grid-(?:cols|rows)-\[([^\]]+)\]/g)) {
      const bieuThuc = m[1] ?? "";
      // Tách theo dấu gạch dưới NGOÀI ngoặc, để `minmax(0,1fr)` không bị xé làm đôi.
      const ranh = bieuThuc.split(/_(?![^(]*\))/);
      for (const r of ranh) {
        if (!/^!?\d*\.?\d*fr$/.test(r)) continue;
        out.push({
          file,
          line: i + 1,
          vi: `rãnh \`${r}\` trong \`${bieuThuc}\` nghĩa là \`minmax(auto, ${r})\` — dùng \`minmax(0,${r})\``,
          snippet: (goc[i] ?? "").trim(),
        });
      }
    }
  });
  return out;
}

/** L4 · Chuỗi máy in bằng phông đơn cách phải ngắt được. */
export function timChuoiMayKhongNgatDuoc(source: string, file: string): ViPham[] {
  const NGAT = /break-all|break-words|whitespace-pre-wrap|truncate/;
  const out: ViPham[] = [];
  const sach = stripComments(source, "ts").split("\n");
  const goc = source.split("\n");

  sach.forEach((line, i) => {
    if (!coLop(line, "font-mono")) return;
    if (NGAT.test(line)) return;
    out.push({
      file,
      line: i + 1,
      vi: "`font-mono` (mã ngoại, đường `ltree`, UUID) mà không có luật ngắt — một 'từ' 36 ký tự sẽ đẩy hộp chứa rộng ra",
      snippet: (goc[i] ?? "").trim(),
    });
  });
  return out;
}

/* ══════════════════════════════════════════════════════════════════════════════
   QUÉT TOÀN KHO
   ════════════════════════════════════════════════════════════════════════════ */

function quet(bo: (source: string, file: string) => ViPham[]): ViPham[] {
  const out: ViPham[] = [];
  for (const absolute of walkSource(/\.tsx$/)) {
    const file = toRepoPath(absolute);
    if (VUNG_CANVAS.some((re) => re.test(file))) continue;
    if (THANH_PHAN_NEN.some((re) => re.test(file))) continue;
    out.push(...bo(readFileSync(absolute, "utf8"), file));
  }
  return out;
}

const GLOBALS_CSS = readFileSync(join(SRC_DIR, "app", "globals.css"), "utf8");

/** Mọi tệp trang trong App Router. */
function tepTrang(): string[] {
  return walkSource(/^page\.tsx$/, join(SRC_DIR, "app")).map(toRepoPath);
}

/* ══════════════════════════════════════════════════════════════════════════════
   BẤT BIẾN
   ════════════════════════════════════════════════════════════════════════════ */

describe("bố cục · không thẻ con nào được rộng hơn thẻ cha", () => {
  it("mọi <table> đều nằm trong một <VungCuonNgang> riêng", () => {
    const viPham = quet(timBangKhongCuon);
    expect(
      viPham.map(moTa),
      "Bảng là hộp DUY NHẤT trong HTML lấy bề rộng bằng tổng min-content của các cột, nên nó không " +
        "co xuống vừa một màn 400px. Bọc nó bằng <VungCuonNgang> — ràng buộc của sản phẩm là " +
        '"thân trang không bao giờ cuộn ngang; chỉ bảng, sơ đồ và khối mã được cuộn, mỗi thứ trong ' +
        'vùng cuộn riêng".'
    ).toEqual([]);
  });

  it("mọi con flex co giãn đều khai `min-w-0`", () => {
    const viPham = quet(timConFlexKhongCoDuoc);
    expect(
      viPham.map(moTa),
      "`flex-1` đặt flex-basis về 0 nhưng KHÔNG đụng tới `min-width: auto`, mà chính `min-width: " +
        "auto` mới là thứ nói 'không bao giờ hẹp hơn nội dung'. Thiếu `min-w-0`, cột con giữ " +
        "nguyên bề rộng nội tại và tràn ra ngoài viền thẻ cha."
    ).toEqual([]);
  });

  it("không rãnh lưới tuỳ ý nào viết `1fr` trần", () => {
    const viPham = quet(timRanhLuoi1fr);
    expect(
      viPham.map(moTa),
      "Tiện ích `grid-cols-2` của Tailwind sinh `repeat(2, minmax(0,1fr))` — an toàn sẵn. Nhưng " +
        "giá trị tuỳ ý thì viết gì được nấy, và `1fr` trần nghĩa là `minmax(auto,1fr)`: đúng cái " +
        "bẫy `min-width: auto` một lần nữa, chỉ đổi từ flex sang grid."
    ).toEqual([]);
  });

  it("mọi chuỗi máy in bằng phông đơn cách đều ngắt được", () => {
    const viPham = quet(timChuoiMayKhongNgatDuoc);
    expect(
      viPham.map(moTa),
      "Bộ giả lập dùng mã ngắn ('P-001') nên lỗi này KHÔNG lộ ra khi chạy `dev:mock` — máy chủ " +
        "thật trả UUID 36 ký tự và đường `ltree` nhiều đốt. Thêm `break-all` ở đúng ô in mã; " +
        "không rắc nó lên chữ tiếng Việt, ở đó nó sẽ cắt giữa từ."
    ).toEqual([]);
  });
});

describe("bố cục · sàn chứa khai ở globals.css, không rải trong component", () => {
  it("`body` khai `overflow-wrap` để từ dài tự xuống dòng", () => {
    // Thuộc tính KẾ THỪA, khai một lần ở `body` là phủ toàn sản phẩm — kể cả widget Ant Design
    // dựng lúc chạy, thứ không component nào của ta với tới được.
    expect(
      /body\s*\{[^}]*overflow-wrap:\s*(break-word|anywhere)/s.test(GLOBALS_CSS),
      "globals.css không còn khai `overflow-wrap` ở `body`. Bỏ dòng đó đi là trả lại nguyên lỗi " +
        "gốc: UUID của `app_user`, đường `ltree` của chi, tên tệp .xlsx có dấu và URL người dùng " +
        "dán vào lý do xin đính chính đều thành một 'từ' không được phép xuống dòng."
    ).toBe(true);
  });

  it("`fieldset` được trả về `min-inline-size: 0`", () => {
    // Preflight của Tailwind gỡ margin/padding/border của <fieldset> nhưng KHÔNG gỡ
    // `min-inline-size: min-content` trong stylesheet mặc định của trình duyệt.
    expect(
      /fieldset\s*\{[^}]*min-inline-size:\s*0/s.test(GLOBALS_CSS),
      "globals.css không còn đặt lại `min-inline-size` cho <fieldset>. Thiếu dòng này, <fieldset> " +
        "là hộp duy nhất trong HTML không bao giờ chịu hẹp hơn nội dung — đúng định nghĩa 'thẻ con " +
        "dài hơn thẻ cha'. Khối ngày âm–dương của biểu mẫu nhân khẩu và hộp xin đính chính đều " +
        "nằm trong một <fieldset>."
    ).toBe(true);
  });

  it("hai quy tắc trên nằm NGOÀI mọi bộ chọn theo lớp, nên Tailwind không loại chúng", () => {
    // Tailwind v3 loại mọi quy tắc có bộ chọn theo LỚP khỏi `@layer base` — bẫy đã ghi ở cuối
    // globals.css. `body` và `fieldset` là bộ chọn theo THẺ nên an toàn; ca này chốt lại điều đó
    // để không ai "dọn dẹp" bằng cách đổi chúng thành `.dark body` hay `.form fieldset`.
    const khoi = /(^|\n)\s*(body|fieldset)\s*\{/g;
    const tenBoChon = [...GLOBALS_CSS.matchAll(khoi)].map((m) => m[2]);
    expect(tenBoChon).toContain("body");
    expect(tenBoChon).toContain("fieldset");
  });
});

describe("bố cục · lề trang khai một chỗ, không chép tay 17 lần", () => {
  const TRANG_TU_DUNG_KHUNG = new Set([
    // Trang chủ có khối chào riêng, bề rộng khác và lề 16px sẵn.
    "src/app/[locale]/page.tsx",
    // Phả đồ là canvas tràn viền — nó KHÔNG được có lề, đó là chủ ý.
    "src/app/[locale]/tree/page.tsx",
  ]);

  it("không tệp trang nào tự chép lại chuỗi lề/bề-rộng", () => {
    const pham = tepTrang()
      .filter((file) => !TRANG_TU_DUNG_KHUNG.has(file))
      .filter((file) => /mx-auto w-full max-w-/.test(readFileSync(join(FRONTEND_ROOT, file), "utf8")));

    expect(
      pham,
      "Chuỗi `mx-auto w-full max-w-… px-… py-…` từng nằm y hệt ở 17 tệp trang. Một quy ước bố cục " +
        "sống bằng cách chép tay thì nó không phải quy ước — nó là 17 bản sao sẽ lệch nhau. Dùng " +
        "<KhungTrang> (src/components/common/khung-trang.tsx)."
    ).toEqual([]);
  });

  it("<KhungTrang> giữ lề ngang 16px ở MỌI bề rộng", () => {
    const src = readFileSync(join(SRC_DIR, "components", "common", "khung-trang.tsx"), "utf8");
    expect(src).toContain("px-4");
    expect(
      /px-3|px-2|px-1\b/.test(stripComments(src, "ts")),
      "Lề ngang của trang nội dung là 16px, không có biến thể hẹp hơn theo bề rộng. Bản cũ mở đầu " +
        "bằng `px-3` (12px) rồi mới lên 16px từ `sm:` — tức đặt lề mỏng nhất đúng vào điện thoại, " +
        "dạng máy chính của cổng này."
    ).toBe(false);
  });
});

/* ══════════════════════════════════════════════════════════════════════════════
   CHỐNG XANH GIẢ — mỗi bộ dò phải KÊU khi lỗi cũ quay lại
   ════════════════════════════════════════════════════════════════════════════ */

describe("chống xanh giả · bộ dò phải bắt được đúng đoạn mã đã gây lỗi", () => {
  it("bắt được một <table> đặt trần trong thẻ", () => {
    const mau = `
      <article className="rounded-lg border px-4 py-3">
        <table className="mt-2 w-full border-collapse text-than">
          <tbody />
        </table>
      </article>`;
    expect(timBangKhongCuon(mau, "gia-lap.tsx")).toHaveLength(1);
  });

  it("KHÔNG bắt nhầm một <table> đã nằm trong vùng cuộn", () => {
    const mau = `
      <VungCuonNgang nhan={t("caption")}>
        <table className="mt-2 w-full border-collapse text-than">
          <tbody />
        </table>
      </VungCuonNgang>`;
    expect(timBangKhongCuon(mau, "gia-lap.tsx")).toEqual([]);
  });

  it("bắt được `flex-1` thiếu `min-w-0` — đúng đoạn ở cột đối chiếu nghi trùng", () => {
    const mau = `<div className="flex-1 rounded-lg border px-3 py-2">`;
    expect(timConFlexKhongCoDuoc(mau, "gia-lap.tsx")).toHaveLength(1);
    expect(
      timConFlexKhongCoDuoc(`<div className="min-w-0 flex-1 rounded-lg border px-3 py-2">`, "x.tsx")
    ).toEqual([]);
  });

  it("phân biệt được `grow` với `grow-0` / `flex-grow`", () => {
    expect(timConFlexKhongCoDuoc(`<div className="grow">`, "x.tsx")).toHaveLength(1);
    expect(timConFlexKhongCoDuoc(`<div className="grow-0">`, "x.tsx")).toEqual([]);
  });

  it("bắt được rãnh `1fr` trần nhưng tha `minmax(0,1fr)`", () => {
    expect(
      timRanhLuoi1fr(`<div className="grid sm:grid-cols-[1fr_auto_1fr]">`, "x.tsx")
    ).toHaveLength(2);
    expect(
      timRanhLuoi1fr(`<div className="grid sm:grid-cols-[minmax(0,10rem)_1fr]">`, "x.tsx")
    ).toHaveLength(1);
    expect(
      timRanhLuoi1fr(
        `<div className="grid sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]">`,
        "x.tsx"
      )
    ).toEqual([]);
  });

  it("bắt được `font-mono` không ngắt được, và tha khi đã có luật ngắt", () => {
    expect(
      timChuoiMayKhongNgatDuoc(`<span className="font-mono">{branch.path}</span>`, "x.tsx")
    ).toHaveLength(1);
    expect(
      timChuoiMayKhongNgatDuoc(
        `<span className="break-all font-mono">{branch.path}</span>`,
        "x.tsx"
      )
    ).toEqual([]);
  });

  it("bỏ qua chú thích tiếng Việt, không đọc chúng thành lớp CSS", () => {
    // Bài học đã ghi trong source-scan.ts: dấu huyền quanh một tên thuộc tính CSS trông y hệt một
    // chuỗi mẫu. Ở đây là câu tiếng Việt nhắc tên lớp — nó không phải một lớp.
    const mau = `
      // Cột này từng thiếu \`min-w-0\` nên \`flex-1\` không co được.
      <div className="min-w-0 flex-1" />`;
    expect(timConFlexKhongCoDuoc(mau, "x.tsx")).toEqual([]);
  });
});
