import { describe, expect, it } from "vitest";
import { colorTokens, darkColorTokens, type ColorTokenName } from "@/styles/tokens";
import { antdTheme } from "@/styles/antd-theme";
import {
  CONTRAST_TEXT,
  CONTRAST_UI,
  contrastRatio,
  describeFailures,
  findContrastFailures,
  formatRatio,
  parseColor,
  roundRatio,
  type ContrastPair,
} from "../../helpers/contrast";

/**
 * C-2.4 — QUÉT TƯƠNG PHẢN TOÀN BỘ BẢNG MÀU, dừng build khi có cặp tụt dưới ngưỡng.
 *
 * <p>Lý do bài kiểm này tồn tại, chép từ tài liệu chuẩn tiếp cận §4.2: hai cặp trong bảng màu đang
 * ở mức "đạt cực sát" 4,52:1 — biên độ 0,02. Không có bài kiểm này thì lần chỉnh mã màu tiếp theo
 * làm hỏng chúng mà không ai biết. Và "lỗi trớ trêu nhất" đã tìm được chỉ là một dòng cấu hình:
 * {@code colorLinkHover} = hổ phách, tức là di chuột lên liên kết làm nó khó đọc đi 2,6 lần —
 * không một bài kiểm nào từng bắt được.</p>
 *
 * <p><b>Bài kiểm viết theo TIÊU CHUẨN, không theo cài đặt hiện tại.</b> Bảng màu chưa sửa xong thì
 * bài kiểm này đỏ, và đỏ là kết quả đúng.</p>
 *
 * <p>Ngưỡng: 4,5:1 cho chữ thường (WCAG 1.4.3); 3:1 cho chữ lớn và cho viền / thành phần giao diện
 * (WCAG 1.4.11) — kể cả vòng tiêu điểm bàn phím (C-4.3).</p>
 */

/** Đường dẫn ngắn để thông báo lỗi chỉ thẳng vào chỗ phải sửa. */
const TOKENS_FILE = "frontend/src/styles/tokens.ts";

/** Bảng màu của một chế độ, cùng bộ tên token. */
type Palette = Record<ColorTokenName, string>;

/**
 * NGOẠI LỆ CỐ Ý — DUY NHẤT MỘT CÁI, ĐƯỢC GHI RA ĐÂY CHỨ KHÔNG BỎ QUA TRONG IM LẶNG.
 *
 * <p>Cặp nền "giấy cũ" {@code bgDeceased} cạnh nền thẻ {@code bgCard} chỉ đạt <b>1,17:1</b> ở chế
 * độ sáng, dưới xa ngưỡng 3:1 của một thành phần giao diện. Nó vẫn được giữ, vì ba lý do đã cân
 * nhắc:</p>
 *
 * <ol>
 *   <li>Nó không mang chữ. Đây là hai <b>mặt nền</b> cạnh nhau, không phải chữ trên nền — ngưỡng
 *       1.4.3 không áp vào đây.</li>
 *   <li>Việc nó phân biệt <b>người đã khuất</b> với người còn sống là chuyện có thật, nhưng đẩy nó
 *       lên 3:1 buộc nền phải tối đi đủ nhiều để tấm thẻ đọc ra như một ô <b>bị vô hiệu hoá</b> —
 *       đúng thứ {@code design/00-dinh-huong.html} §3 cấm: sắc giấy cũ phải đọc như sự TÔN KÍNH,
 *       không phải như một trường bị khoá.</li>
 *   <li>Vì thế nghĩa "đã khuất" <b>không được phép chỉ nằm ở màu này</b>. C-8.1 đòi ít nhất một
 *       tín hiệu KHÔNG PHẢI MÀU đi kèm, và C-8.2 nói thẳng rằng một chấm 4–8px không tính là tín
 *       hiệu. Bất biến ấy được ghim ở tầng trình duyệt — {@code e2e/accessibility.spec.ts}.</li>
 * </ol>
 *
 * <p>Nói cách khác: ngoại lệ này <b>không miễn phí</b>. Nó chỉ hợp lệ chừng nào bài kiểm tín hiệu
 * dư thừa còn xanh. Bỏ tín hiệu kia đi thì ngoại lệ này mất hiệu lực.</p>
 */
const DECEASED_SURFACE_EXCEPTION = {
  label: "nền giấy cũ của người đã khuất cạnh nền thẻ người sống",
  foreground: colorTokens.bgDeceased,
  background: colorTokens.bgCard,
  /** Tỉ số thật ở chế độ sáng, ghim lại để ngoại lệ không âm thầm nới thành "màu nào cũng được". */
  measuredRatio: 1.16,
  redundantSignalTest: "e2e/accessibility.spec.ts — ca tín hiệu dư thừa khi bỏ hết màu",
} as const;

/**
 * VIỀN TRANG TRÍ — được miễn ngưỡng 3:1, với điều kiện.
 *
 * <p>WCAG 1.4.11 chỉ bắt buộc 3:1 khi thông tin thị giác ấy là thứ <b>cần để nhận ra một thành
 * phần giao diện</b>. Tài liệu chuẩn tiếp cận §4.2 nói đúng ranh giới đó bằng một câu:
 * <i>"Chấp nhận được nếu chỉ trang trí; không chấp nhận được nếu nó là ranh giới của một điều
 * khiển."</i></p>
 *
 * <p>{@code border} và {@code borderDark} là đường kẻ của <b>thẻ</b> — thẻ tự nhận diện bằng nội
 * dung của nó, không nhờ đường viền. Nên chúng được miễn. Điều kiện đi kèm được ghim thành hai ca
 * kiểm thật ở dưới:</p>
 *
 * <ol>
 *   <li>{@code antdTheme.token.colorBorder} <b>không được</b> là một trong hai token này —
 *       {@code colorBorder} tô viền cho MỌI ô nhập, và ở đó viền chính là thứ duy nhất nói
 *       "chỗ này gõ vào được".</li>
 *   <li>Nút bung nhánh trên phả đồ viền {@code borderDark}, nên danh tính nhìn thấy được của nó
 *       phải đến từ chỗ khác: biểu tượng +/− tô {@code primary} trên nền thẻ, và cặp đó phải đạt
 *       3:1 thật.</li>
 * </ol>
 */
const DECORATIVE_BORDER_TOKENS: readonly ColorTokenName[] = ["border", "borderDark"];

/**
 * Các cặp chữ-trên-nền THỰC SỰ ĐƯỢC DÙNG. Cố ý không lấy tích Descartes của cả bảng màu: chấm
 * những cặp không ai dùng chỉ tạo tiếng ồn, và tiếng ồn là thứ dẫn tới việc nới ngưỡng cho yên
 * chuyện. Danh sách bám theo cột "Dùng ở đâu" của bảng §4.2 cộng các token mới tách ra
 * ({@code accentText}, {@code successText}, {@code borderInput}) theo C-2.2 / C-2.3.
 */
function textPairs(p: Palette, mode: string): ContrastPair[] {
  const t = (label: string, foreground: string, background: string): ContrastPair => ({
    label: `[${mode}] ${label}`,
    foreground,
    background,
    threshold: CONTRAST_TEXT,
  });
  return [
    t("chữ chính trong thẻ", p.textMain, p.bgCard),
    t("chữ chính trên trang", p.textMain, p.bgPage),
    t("chữ trên hồ sơ người đã khuất", p.textMain, p.bgDeceased),
    t("chữ trong khối cảnh báo", p.textMain, p.warningBg),
    t("chữ trong khối thành công", p.textMain, p.successBg),
    t("chữ trong khối lỗi", p.textMain, p.dangerBg),
    t("chữ trong chip nhấn", p.textMain, p.primaryLight),
    t("chữ phụ trong thẻ", p.textMuted, p.bgCard),
    t("chữ phụ trên trang", p.textMuted, p.bgPage),
    t("chữ phụ trên nền giấy cũ", p.textMuted, p.bgDeceased),
    t("liên kết / tiêu đề nhấn trong thẻ", p.primary, p.bgCard),
    t("liên kết trên nền trang", p.primary, p.bgPage),
    t("liên kết trên hồ sơ người đã khuất", p.primary, p.bgDeceased),
    t("chữ trên chip / khối nhấn", p.primary, p.primaryLight),
    t("màu thông tin trên trang", p.secondary, p.bgPage),
    t("màu thông tin trong thẻ", p.secondary, p.bgCard),
    t("thông báo lỗi trên trang", p.danger, p.bgPage),
    t("thông báo lỗi trong thẻ", p.danger, p.bgCard),
    t("chữ đỏ trong khối lỗi", p.danger, p.dangerBg),
    // `accentText` / `successText` sinh ra ĐÚNG để làm chữ. Nếu chúng vẫn trượt thì việc tách
    // token đã không giải quyết được gì và phải chọn lại sắc, chứ không phải hạ ngưỡng.
    t("chữ hổ phách trên trang", p.accentText, p.bgPage),
    t("chữ hổ phách trong thẻ", p.accentText, p.bgCard),
    t("chữ hổ phách trong khối cảnh báo", p.accentText, p.warningBg),
    t("chữ hổ phách trên nền giấy cũ", p.accentText, p.bgDeceased),
    t("chữ hổ phách trên chip nhấn", p.accentText, p.primaryLight),
    t("chữ trạng thái còn sống trên trang", p.successText, p.bgPage),
    t("chữ trạng thái còn sống trong thẻ", p.successText, p.bgCard),
    t("chữ trong khối thành công", p.successText, p.successBg),
    t("chữ trạng thái trên nền giấy cũ", p.successText, p.bgDeceased),

    // ── MỰC TRÊN MẢNG TÔ — cặp mà sản phẩm THẬT SỰ vẽ ──────────────────────────
    // `fillInkFailures()` bên dưới chỉ hỏi "bảng màu CÓ một màu mực đọc được không".
    // Câu hỏi ấy trả lời xong rồi, nhưng nó không nói được gì về việc nơi vẽ có DÙNG
    // màu mực ấy hay không — và trong một thời gian dài thì không: `<Tag color={…}>`
    // của Ant Design ép chữ về TRẮNG, nên huy hiệu Đích tôn / Thừa tự / Kế tự chạy ở
    // 3,19:1 khi sáng và 2,07:1 khi tối. Bốn cặp dưới đây ghim đúng cặp đang chạy
    // trên màn hình: mảng tô lấy từ token, mực luôn là `bgPage`.
    //
    // Vì sao `bgPage` đúng ở CẢ HAI chế độ mà không cần rẽ nhánh: ở chế độ sáng nó là
    // nền kem trên một mảng trầm, ở chế độ tối nó là mực tối trên một mảng sáng. Một
    // khai báo, đúng hai chiều — đây chính là loại việc mà bảng màu hai chế độ sinh
    // ra để giải.
    t("mực huy hiệu trên mảng hổ phách trầm", p.bgPage, p.accentText),
    t("mực huy hiệu trên mảng đỏ trầm", p.bgPage, p.primary),
    t("mực huy hiệu trên mảng xanh xám", p.bgPage, p.secondary),
    t("mực huy hiệu trên mảng mực nhạt", p.bgPage, p.textMuted),
  ];
}

/**
 * Thành phần giao diện và viền — ngưỡng 3:1 (WCAG 1.4.11). Chỉ những thứ mà <b>hình ảnh chính là
 * thông tin</b>: ranh giới ô nhập, chấm trạng thái sống/khuất, mảng hổ phách mang nghĩa trên phả
 * đồ (thanh hôn phối), nền nút hành động chính.
 */
function uiPairs(p: Palette, mode: string): ContrastPair[] {
  const u = (label: string, foreground: string, background: string): ContrastPair => ({
    label: `[${mode}] ${label}`,
    foreground,
    background,
    threshold: CONTRAST_UI,
  });
  return [
    u("viền ô nhập trên nền thẻ", p.borderInput, p.bgCard),
    u("viền ô nhập trên nền trang", p.borderInput, p.bgPage),
    u("viền ô nhập trên nền giấy cũ", p.borderInput, p.bgDeceased),
    u("chấm trạng thái còn sống trên nền thẻ", p.success, p.bgCard),
    u("chấm trạng thái còn sống trên nền trang", p.success, p.bgPage),
    // Hổ phách làm MẢNG TÔ mang nghĩa (thanh hôn phối, vạch nhấn) — không phải trang trí thuần,
    // nên 1.4.11 áp vào. Đây là cách dùng mà token `accent` được giữ lại để phục vụ.
    u("mảng hổ phách trên nền trang", p.accent, p.bgPage),
    u("mảng hổ phách trên nền thẻ", p.accent, p.bgCard),
    u("nút hành động chính trên nền trang", p.primary, p.bgPage),
    u("nút hành động chính trên nền thẻ", p.primary, p.bgCard),
    // Biểu tượng +/− của nút bung nhánh: đây mới là thứ nhận diện nút, vì viền nó là
    // `borderDark` trang trí. Xem DECORATIVE_BORDER_TOKENS.
    u("biểu tượng nút bung nhánh trên nền thẻ", p.primary, p.bgCard),
  ];
}

/**
 * MẢNG TÔ CÓ CHỮ ĐÈ LÊN — nút, chip, huy hiệu.
 *
 * <p>Cố ý không ghim cứng "mực nào trên mảng nào": ở chế độ sáng chữ trên mảng hổ phách phải là
 * mực tối, ở chế độ tối thì ngược lại — mảng hổ phách sáng lên nên chữ phải là màu nền tối. Ghim
 * cứng một cặp sẽ báo sai ở một trong hai chế độ, và một bài kiểm báo sai là bài kiểm sẽ bị người
 * ta tắt đi.</p>
 *
 * <p>Điều thật sự phải đúng, ở cả hai chế độ, là: <b>bảng màu phải cung cấp ít nhất một màu mực
 * đọc được trên mảng ấy</b>. Không có mực nào đạt 4,5:1 thì mảng đó không dùng làm nền chữ được,
 * chấm hết — và ba huy hiệu quan trọng nhất của phả hệ (Đích tôn · Thừa tự · Kế tự) đang nằm đúng
 * trên một mảng như thế.</p>
 */
function fillInkFailures(p: Palette, mode: string): string[] {
  const fills: readonly [string, string][] = [
    ["nút hành động chính", p.primary],
    ["nút hành động chính lúc nhấn", p.primaryDark],
    ["huy hiệu / chip hổ phách", p.accent],
    ["huy hiệu hổ phách trầm", p.accentText],
    ["chip trạng thái còn sống", p.success],
    ["chip lỗi", p.danger],
    ["chip thông tin", p.secondary],
  ];
  const inks: readonly [string, string][] = [
    ["mực chính", p.textMain],
    ["nền trang", p.bgPage],
    ["nền thẻ", p.bgCard],
  ];
  const failures: string[] = [];
  for (const [fillName, fill] of fills) {
    const best = inks
      .map(([inkName, ink]) => ({ inkName, ratio: contrastRatio(ink, fill) }))
      .sort((a, b) => b.ratio - a.ratio)[0]!;
    if (roundRatio(best.ratio) < CONTRAST_TEXT) {
      failures.push(
        `[${mode}] không màu mực nào đọc được trên ${fillName} (${fill}): ` +
          `khá nhất là ${best.inkName} ${formatRatio(best.ratio)} (cần ≥ 4,5:1)`
      );
    }
  }
  return failures;
}

/**
 * VÒNG TIÊU ĐIỂM HAI LỚP (C-4.3).
 *
 * <p>Một vòng ĐƠN SẮC không thể đạt 3:1 với mọi nền cùng lúc khi nền của sản phẩm trải từ trắng
 * tới đỏ trầm. Lời giải đang dùng là hai lớp trái sắc — lõi {@code focusRing} và quầng
 * {@code focusHalo} — với lời hứa "bất kể nền nào, luôn có ít nhất MỘT lớp đạt ≥3:1".</p>
 *
 * <p>Ca kiểm này chính là lời hứa ấy, viết thành máy chấm được. Nó cũng đòi hai lớp phải phân biệt
 * được VỚI NHAU: hai lớp cùng sắc thì chỉ là một vòng dày hơn, không phải hai lớp.</p>
 */
function focusRingFailures(p: Palette, mode: string): string[] {
  const surfaces: readonly [string, string][] = [
    ["nền trang", p.bgPage],
    ["nền thẻ", p.bgCard],
    ["nền giấy cũ", p.bgDeceased],
    ["nút hành động chính", p.primary],
    ["nút hành động chính lúc nhấn", p.primaryDark],
    ["mảng hổ phách", p.accent],
    ["chip nhấn", p.primaryLight],
    ["khối thành công", p.successBg],
    ["khối lỗi", p.dangerBg],
    ["khối cảnh báo", p.warningBg],
  ];
  const failures: string[] = [];
  for (const [name, surface] of surfaces) {
    const ring = contrastRatio(p.focusRing, surface);
    const halo = contrastRatio(p.focusHalo, surface);
    if (roundRatio(Math.max(ring, halo)) < CONTRAST_UI) {
      failures.push(
        `[${mode}] không lớp nào của vòng tiêu điểm thấy được trên ${name}: ` +
          `lõi ${formatRatio(ring)}, quầng ${formatRatio(halo)} (cần một lớp ≥ 3:1)`
      );
    }
  }
  const layers = contrastRatio(p.focusRing, p.focusHalo);
  if (roundRatio(layers) < CONTRAST_UI) {
    failures.push(
      `[${mode}] lõi và quầng không phân biệt được với nhau (${formatRatio(layers)}) — ` +
        "hai lớp cùng sắc chỉ là một vòng dày hơn, không phải hai lớp"
    );
  }
  return failures;
}

const LIGHT: Palette = colorTokens;
const DARK: Palette = darkColorTokens;

describe.each([
  ["sáng", LIGHT],
  ["tối", DARK],
] as const)("C-2.4 · tương phản bảng màu — chế độ %s", (mode, palette) => {
  it("mọi cặp chữ trên nền đạt 4,5:1", () => {
    const failures = findContrastFailures(textPairs(palette, mode));
    expect(
      failures,
      `${failures.length} cặp chữ/nền dưới ngưỡng 4,5:1 (sửa ở ${TOKENS_FILE}):\n` +
        describeFailures(failures)
    ).toEqual([]);
  });

  it("mọi viền và thành phần giao diện đạt 3:1", () => {
    const failures = findContrastFailures(uiPairs(palette, mode));
    expect(
      failures,
      `${failures.length} viền/thành phần dưới ngưỡng 3:1 (sửa ở ${TOKENS_FILE}):\n` +
        describeFailures(failures)
    ).toEqual([]);
  });

  it("mọi mảng tô có chữ đè lên đều có ít nhất một màu mực đọc được", () => {
    const failures = fillInkFailures(palette, mode);
    expect(failures, failures.join("\n")).toEqual([]);
  });

  it("C-4.3 · vòng tiêu điểm luôn có ít nhất một lớp thấy được trên mọi nền", () => {
    const failures = focusRingFailures(palette, mode);
    expect(failures, failures.join("\n")).toEqual([]);
  });

  /**
   * Chống bỏ sót: thêm một token vào bảng màu mà không thêm cặp nào cho nó thì mọi ca trên vẫn
   * xanh — và token mới đi thẳng vào sản phẩm không qua phép kiểm nào. Ca này chặn đúng chuyện đó.
   */
  it("không token nào trong tokens.ts thoát khỏi phép quét", () => {
    const used = new Set<string>();
    for (const pair of [...textPairs(palette, mode), ...uiPairs(palette, mode)]) {
      used.add(pair.foreground);
      used.add(pair.background);
    }
    used.add(palette.focusRing);
    used.add(palette.focusHalo);
    // `primaryDark` chỉ xuất hiện với tư cách MẢNG TÔ (nút lúc nhấn), nên nó được chấm ở
    // fillInkFailures() chứ không ở hai danh sách cặp trên.
    used.add(palette.primaryDark);
    for (const name of DECORATIVE_BORDER_TOKENS) used.add(palette[name]);

    const unchecked = (Object.keys(palette) as ColorTokenName[]).filter(
      (name) => !used.has(palette[name])
    );
    expect(
      unchecked,
      `token chưa có cặp nào được chấm tương phản: ${unchecked.join(", ")}. ` +
        "Thêm cặp THẬT mà nó được dùng vào textPairs() / uiPairs(), hoặc — nếu nó chỉ là đường " +
        "kẻ trang trí — vào DECORATIVE_BORDER_TOKENS kèm lý do."
    ).toEqual([]);
  });
});

describe("C-2.4 · ngoại lệ cố ý — sắc giấy cũ của người đã khuất", () => {
  /**
   * Ngoại lệ được VIẾT RA và được ĐO, không phải bị bỏ qua. Nếu ai đó chỉnh {@code bgDeceased}
   * đậm lên tới mức đạt 3:1, ca này đỏ — và đó là tín hiệu đúng: lúc ấy tấm thẻ đã trượt sang vẻ
   * "bị vô hiệu hoá" mà định hướng cấm, còn ngoại lệ thì không còn lý do tồn tại.
   */
  it("vẫn đúng là 1,17:1 — dưới ngưỡng, và được chấp nhận với lý do đã ghi", () => {
    const ratio = contrastRatio(
      DECEASED_SURFACE_EXCEPTION.foreground,
      DECEASED_SURFACE_EXCEPTION.background
    );
    // 1,16 chứ không phải 1,17: tài liệu thiết kế làm tròn lên ở chữ số thứ hai, còn ở đây tỉ số
    // luôn được làm tròn XUỐNG để không cho qua oan một cặp sát ngưỡng. Cùng một con số.
    expect(roundRatio(ratio)).toBe(DECEASED_SURFACE_EXCEPTION.measuredRatio);
    expect(
      roundRatio(ratio),
      "sắc giấy cũ đã đủ tương phản — ngoại lệ này không còn lý do tồn tại, hãy bỏ nó đi"
    ).toBeLessThan(CONTRAST_UI);
  });

  it("ngoại lệ chỉ hợp lệ nhờ một tín hiệu dư thừa KHÔNG PHẢI MÀU, và tín hiệu ấy có địa chỉ", () => {
    // Ca này không đo màu; nó ghim rằng ngoại lệ ĐI KÈM ĐIỀU KIỆN, và điều kiện ấy được kiểm ở
    // một chỗ cụ thể. C-8.1 + C-8.2: mọi nghĩa truyền bằng màu phải có tín hiệu chữ / hình dạng /
    // vị trí đi kèm, và một chấm 4–8px KHÔNG được tính là tín hiệu.
    expect(DECEASED_SURFACE_EXCEPTION.redundantSignalTest).toMatch(/e2e\/accessibility\.spec\.ts/);
  });

  it("ngoại lệ không được nới ra cho bất kỳ cặp nào khác", () => {
    const checked = [
      ...textPairs(LIGHT, "sáng"),
      ...uiPairs(LIGHT, "sáng"),
      ...textPairs(DARK, "tối"),
      ...uiPairs(DARK, "tối"),
    ];
    const sub3 = checked.filter(
      (p) => roundRatio(contrastRatio(p.foreground, p.background)) < CONTRAST_UI
    );
    expect(
      sub3.map((p) => `${p.label} (${formatRatio(contrastRatio(p.foreground, p.background))})`),
      "chỉ có ĐÚNG MỘT ngoại lệ được phép (nền giấy cũ). Những cặp dưới đây phải sửa, " +
        "không phải thêm vào một danh sách miễn trừ thứ hai"
    ).toEqual([]);
  });
});

describe("C-2.4 · viền trang trí chỉ được miễn ngưỡng khi nó THẬT SỰ chỉ trang trí", () => {
  it("token viền trang trí không được đem đi tô ranh giới ô nhập của Ant Design", () => {
    const colorBorder = antdTheme.token?.colorBorder;
    const decorative: string[] = DECORATIVE_BORDER_TOKENS.map((n) => colorTokens[n]);
    expect(
      decorative.includes(colorBorder ?? ""),
      `antdTheme.token.colorBorder = ${colorBorder} đang là một token viền TRANG TRÍ ` +
        `(${decorative.join(", ")}). colorBorder tô viền cho MỌI ô nhập, mà ở ô nhập viền là thứ ` +
        "duy nhất nói 'chỗ này gõ vào được' — nên nó phải là borderInput và phải đạt 3:1 (C-2.3)."
    ).toBe(false);
  });

  it("nút bung nhánh nhận diện được bằng biểu tượng, không phải bằng viền trang trí", () => {
    // Viền `borderDark` của nút chỉ 1,69:1 và được miễn với tư cách đường kẻ trang trí. Điều kiện
    // đi kèm: cái nhận ra được nút phải là biểu tượng +/−, và nó phải đạt 3:1 thật.
    const glyph = contrastRatio(colorTokens.primary, colorTokens.bgCard);
    expect(roundRatio(glyph)).toBeGreaterThanOrEqual(CONTRAST_UI);
  });
});

/**
 * Cặp màu suy ra TỰ ĐỘNG từ bộ chủ đề Ant Design. Cố ý không chép tay: bộ chủ đề chính là chỗ khai
 * "màu chữ nào nằm trên mặt nền nào", nên đọc thẳng từ đó thì bài kiểm tự đi theo mọi lần sửa
 * {@code antd-theme.ts} mà không cần ai nhớ cập nhật một danh sách song song.
 */
function antdDerivedPairs(): ContrastPair[] {
  const token = antdTheme.token ?? {};
  const container = token.colorBgContainer ?? colorTokens.bgCard;
  const layout = token.colorBgLayout ?? colorTokens.bgPage;
  const surfaces: readonly { name: string; value: string }[] = [
    { name: "nền thẻ (colorBgContainer)", value: container },
    { name: "nền trang (colorBgLayout)", value: layout },
  ];
  const inks: readonly { name: string; value: string | undefined; threshold: number }[] = [
    { name: "colorText", value: token.colorText, threshold: CONTRAST_TEXT },
    { name: "colorTextSecondary", value: token.colorTextSecondary, threshold: CONTRAST_TEXT },
    { name: "colorLink", value: token.colorLink, threshold: CONTRAST_TEXT },
    // Trạng thái phản hồi phải làm RÕ HƠN, không được làm mờ đi. "Lỗi trớ trêu nhất" ở §4.2.
    { name: "colorLinkHover", value: token.colorLinkHover, threshold: CONTRAST_TEXT },
    { name: "colorError", value: token.colorError, threshold: CONTRAST_TEXT },
    { name: "colorSuccess", value: token.colorSuccess, threshold: CONTRAST_TEXT },
    { name: "colorInfo", value: token.colorInfo, threshold: CONTRAST_TEXT },
    // colorWarning tô chữ và biểu tượng của <Alert type="warning"> — chữ thật, ngưỡng chữ thật.
    { name: "colorWarning", value: token.colorWarning, threshold: CONTRAST_TEXT },
    { name: "colorBorder", value: token.colorBorder, threshold: CONTRAST_UI },
    { name: "colorPrimary", value: token.colorPrimary, threshold: CONTRAST_UI },
  ];

  const pairs: ContrastPair[] = [];
  for (const ink of inks) {
    if (!ink.value || !parseColor(ink.value)) continue;
    for (const surface of surfaces) {
      pairs.push({
        label: `antd ${ink.name} trên ${surface.name}`,
        foreground: ink.value,
        background: surface.value,
        threshold: ink.threshold,
      });
    }
  }
  return pairs;
}

describe("C-2.4 · bộ chủ đề Ant Design", () => {
  it("mọi màu chữ/viền khai trong antdTheme đạt ngưỡng trên cả hai mặt nền của nó", () => {
    const failures = findContrastFailures(antdDerivedPairs());
    expect(
      failures,
      `${failures.length} token antd dưới ngưỡng (sửa ở frontend/src/styles/antd-theme.ts):\n` +
        describeFailures(failures)
    ).toEqual([]);
  });

  /**
   * C-1.1 — sàn 16px. {@code antd-theme.ts} không khai {@code fontSize} thì Ant Design rơi về mặc
   * định 14, và 14px là vi phạm rộng nhất trong sản phẩm: mọi nút, ô nhập, nhãn biểu mẫu, bảng.
   */
  it("C-1.1 · khai fontSize ≥ 16 để widget Ant Design không rơi về mặc định 14", () => {
    const fontSize = antdTheme.token?.fontSize;
    expect(
      fontSize,
      "antdTheme.token.fontSize chưa được khai — Ant Design sẽ dùng mặc định 14px cho toàn " +
        "bộ nút / ô nhập / nhãn / bảng (C-1.1)"
    ).toBeDefined();
    expect(fontSize ?? 0).toBeGreaterThanOrEqual(16);
  });

  /**
   * C-3.1 — sàn chạm 44px. Không khai {@code controlHeight} thì mọi nút, ô nhập, ô chọn và
   * Segmented cỡ mặc định cao 32px.
   */
  it("C-3.1 · khai controlHeight ≥ 44 để mọi điều khiển đạt sàn chạm", () => {
    const controlHeight = antdTheme.token?.controlHeight;
    expect(
      controlHeight,
      "antdTheme.token.controlHeight chưa được khai — mọi điều khiển Ant Design cỡ mặc định " +
        "đang cao 32px, dưới sàn 44px của tài liệu 00 §2.2 (C-3.1)"
    ).toBeDefined();
    expect(controlHeight ?? 0).toBeGreaterThanOrEqual(44);
  });
});

describe("C-2.4 · hai bảng màu phải cùng bộ tên", () => {
  /**
   * Bảng tối thiếu một token thì ở chế độ tối token ấy rơi về giá trị sáng — một mảng chói giữa
   * nền tối, im lặng, không lỗi biên dịch. `satisfies Record<ColorTokenName, string>` trong
   * tokens.ts đã chặn ở tầng kiểu; ca này chặn thêm ở tầng chạy, phòng khi ai đó nới kiểu ra.
   */
  it("bảng màu tối phủ đúng mọi token của bảng sáng", () => {
    const light = Object.keys(colorTokens).sort();
    const dark = Object.keys(darkColorTokens).sort();
    expect(dark).toEqual(light);
  });

  it("chế độ tối THẬT SỰ đảo tông, không phải chép lại bảng sáng", () => {
    // Nếu ai đó dựng bảng tối bằng cách chép bảng sáng rồi quên đổi, ca trên vẫn xanh.
    expect(darkColorTokens.bgPage).not.toBe(colorTokens.bgPage);
    expect(darkColorTokens.textMain).not.toBe(colorTokens.textMain);
    const lightIsBright =
      contrastRatio(colorTokens.bgPage, "#000000") > contrastRatio(colorTokens.bgPage, "#ffffff");
    const darkIsDim =
      contrastRatio(darkColorTokens.bgPage, "#ffffff") >
      contrastRatio(darkColorTokens.bgPage, "#000000");
    expect(lightIsBright, "nền chế độ sáng không sáng").toBe(true);
    expect(darkIsDim, "nền chế độ tối không tối").toBe(true);
  });
});

/** ---- Phép kiểm ngược: dựng lại đúng giá trị sai đã biết, rồi đòi phép quét phải KÊU ---- */
describe("chống xanh giả · phép quét bảng màu phải bắt được lỗi đã biết", () => {
  it("bắt hổ phách #d97706 nếu nó quay lại làm màu chữ (2,95 < 4,5)", () => {
    const regressed = textPairs({ ...LIGHT, accentText: "#d97706" }, "hồi quy");
    const failures = findContrastFailures(regressed);
    const amber = failures.find((f) => f.foreground === "#d97706");
    expect(
      amber,
      "phép quét KHÔNG bắt được hổ phách làm chữ — bộ kiểm này vô giá trị"
    ).toBeDefined();
    expect(roundRatio(contrastRatio("#d97706", LIGHT.bgPage))).toBe(2.95);
  });

  it("bắt viền #e6dfd5 nếu nó quay lại làm ranh giới ô nhập (1,32 < 3)", () => {
    const regressed = uiPairs({ ...LIGHT, borderInput: "#e6dfd5" }, "hồi quy");
    const failures = findContrastFailures(regressed);
    expect(failures.length).toBeGreaterThanOrEqual(2);
    expect(roundRatio(failures[0]!.ratio)).toBe(1.32);
  });

  it("bắt mảng tô mà không màu mực nào đọc được trên đó", () => {
    // Một sắc xám trung tính là ca kinh điển: nó cách đều mực tối lẫn nền sáng, nên đặt chữ gì
    // lên cũng không đạt 4,5:1. Huy hiệu Đích tôn / Thừa tự / Kế tự đang nằm trên mảng hổ phách,
    // đúng loại rủi ro này.
    const failures = fillInkFailures({ ...LIGHT, accent: "#8a8a8a" }, "hồi quy");
    expect(failures.join("\n")).toContain("huy hiệu / chip hổ phách");
  });

  it("bắt vòng tiêu điểm đơn sắc hổ phách — lỗi C-4.3 cũ", () => {
    const failures = focusRingFailures(
      { ...LIGHT, focusRing: "#d97706", focusHalo: "#d97706" },
      "hồi quy"
    );
    // Trượt trên nền kem (2,95), trên giấy cũ (2,73), quanh nút đỏ trầm (2,63), và hai lớp không
    // phân biệt được với nhau.
    expect(failures.length).toBeGreaterThanOrEqual(4);
    expect(failures.join("\n")).toContain("không phân biệt được với nhau");
  });

  it("bắt colorLinkHover mờ hơn colorLink — trạng thái phản hồi làm chữ khó đọc đi", () => {
    // Dựng lại đúng cấu hình cũ: link = đỏ trầm (7,77), hover = hổ phách (2,95).
    const link = contrastRatio("#8c2d19", colorTokens.bgPage);
    const hover = contrastRatio("#d97706", colorTokens.bgPage);
    expect(hover).toBeLessThan(link);
    expect(
      findContrastFailures([
        {
          label: "colorLinkHover cũ",
          foreground: "#d97706",
          background: colorTokens.bgPage,
          threshold: CONTRAST_TEXT,
        },
      ])
    ).toHaveLength(1);
  });

  it("bắt được token mới thêm mà chưa ai chấm tương phản cho nó", () => {
    // Giả lập: bảng màu mọc thêm một token, danh sách cặp thì không đổi.
    const palette = { ...LIGHT, mauMoiChuaKiem: "#c0ffee" };
    const used = new Set(
      [...textPairs(LIGHT, "x"), ...uiPairs(LIGHT, "x")].flatMap((p) => [
        p.foreground,
        p.background,
      ])
    );
    const unchecked = Object.keys(palette).filter(
      (name) => !used.has(palette[name as keyof typeof palette])
    );
    expect(unchecked).toContain("mauMoiChuaKiem");
  });

  it("bắt được bảng màu tối giả — chữ gần trắng trên nền trắng", () => {
    const fakeDark: Palette = {
      ...DARK,
      bgPage: "#ffffff",
      bgCard: "#ffffff",
      textMain: "#f5f5f5",
      textMuted: "#eeeeee",
    };
    const failures = findContrastFailures(textPairs(fakeDark, "tối giả"));
    expect(failures.length).toBeGreaterThan(3);
  });
});
