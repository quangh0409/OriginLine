import { devices, type Page } from "@playwright/test";
import { expect, signInAs, test, waitForTreeReady } from "./fixtures";
import {
  CONTRAST_UI,
  contrastRatio,
  flattenOver,
  formatRatio,
  parseColor,
  roundRatio,
  textThreshold,
  type Rgb,
} from "../tests/helpers/contrast";

/**
 * BỘ KIỂM HỒI QUY TIẾP CẬN — ĐO TRÊN TRÌNH DUYỆT THẬT.
 *
 * <p>Tầng unit ({@code tests/unit/a11y/}) chấm bảng màu và cấu hình. Nhưng ba nhóm lỗi trong danh
 * sách này <b>không tồn tại ở tầng ấy</b>, vì chúng chỉ xuất hiện sau khi CSS đã xếp chồng xong và
 * máy quay của phả đồ đã nhân mức phóng vào:</p>
 *
 * <ul>
 *   <li><b>Vùng chạm</b> — bài học 0.21. Tài liệu chuẩn tiếp cận §4.3 nói thẳng: mọi bài kiểm cũ
 *       vẫn xanh khi nút bung nhánh co còn 5px, <b>vì chúng đo mức phóng chứ không đo pixel</b>.
 *       C-3.5 đòi đo kích thước thật trên màn hình. Chỗ này làm đúng thế.</li>
 *   <li><b>Cỡ chữ</b> — widget Ant Design lấy cỡ chữ từ CSS-in-JS chèn vào {@code <head>} lúc
 *       chạy. Không {@code getComputedStyle} thì không biết được nó đang là 14 hay 16.</li>
 *   <li><b>Chế độ tối và giảm chuyển động</b> — cả hai là truy vấn media. Chỉ trình duyệt trả lời
 *       được.</li>
 * </ul>
 *
 * <p><b>Chống xanh giả:</b> mỗi bộ thu thập đều có một ca dựng lại giá trị sai đã biết ngay trên
 * trang thật (nút 18px, chữ 14px, vòng tiêu điểm hổ phách 2,95:1) rồi khẳng định bộ thu thập
 * <b>kêu</b>. Một bất biến không có phép kiểm ngược chỉ chứng minh được rằng nó chưa bao giờ chạy.</p>
 *
 * <p>Chạy trên MSW (`npm run dev:mock`) như toàn bộ bộ e2e hiện có. Ảnh chụp lỗi đi vào
 * {@code .playwright-mcp/}, không bao giờ ra gốc kho.</p>
 */

/** Sàn vùng chạm của tài liệu 00 §2.2 — ngưỡng WCAG 2.2 SC 2.5.8 nâng lên mức AAA 44px. */
const TOUCH_FLOOR_PX = 44;
/** Sàn cỡ chữ thân bài của tài liệu 00 §2.2. */
const BODY_FONT_FLOOR_PX = 16;
/** Sàn tuyệt đối C-1.2: ngoài canvas phả đồ, không chữ nào được nhỏ hơn thế này. */
const ABSOLUTE_FONT_FLOOR_PX = 12;

interface Screen {
  readonly path: string;
  readonly name: string;
}

/** Các màn hình chính, đủ để phủ ba loại bề mặt: trang nội dung, biểu mẫu, và canvas. */
const MAIN_SCREENS: readonly Screen[] = [
  { path: "/", name: "trang chủ" },
  { path: "/tree", name: "phả đồ" },
  { path: "/persons/p-001", name: "hồ sơ nhân khẩu" },
  { path: "/search", name: "tìm kiếm" },
  { path: "/kinship?from=p-010&to=p-001", name: "tra danh xưng" },
  { path: "/events", name: "lịch giỗ" },
];

/**
 * Tập rút gọn cho những phép quét ĐẮT (đi hết bàn phím, chấm tương phản từng đoạn chữ). Ba màn này
 * phủ đủ ba loại bề mặt khác nhau về bản chất — trang nội dung tĩnh, hồ sơ nhiều dữ liệu, và
 * canvas — nên quét thêm màn thứ tư cùng loại chỉ tốn thời gian chứ không tăng độ phủ.
 */
const CORE_SCREENS: readonly Screen[] = [
  { path: "/", name: "trang chủ" },
  { path: "/persons/p-001", name: "hồ sơ nhân khẩu" },
  { path: "/tree", name: "phả đồ" },
];

/**
 * Chờ trang lặng đi, nhưng CÓ HẠN.
 *
 * <p>Không dùng {@code waitForLoadState("networkidle")} trần: {@code useNotifications} đặt
 * {@code refetchInterval: 60_000} và trình chạy dịch vụ của MSW giữ kết nối, nên "mạng lặng" là
 * trạng thái trang này có thể không bao giờ đạt tới. Khi đó lời chờ ăn hết ngân sách 90s của ca
 * kiểm và báo về một <b>timeout</b> — trông y hệt một lỗi tiếp cận thật, nhưng không phải. Chờ có
 * hạn rồi đi tiếp: phép đo bên dưới tự có lời chờ riêng cho thứ nó cần.</p>
 */
async function settle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  // Next dev biên dịch từng tuyến đường ở lần ghé đầu tiên, nên "đã có điều khiển trên trang" là
  // mốc đáng chờ; nó cũng là điều kiện tối thiểu để mọi phép đo dưới đây có gì mà đo.
  await page
    .waitForFunction(() => document.querySelectorAll("button, a[href]").length > 2, undefined, {
      timeout: 45_000,
    })
    .catch(() => {
      /* để ca kiểm tự báo "không quét được điều khiển nào" — thông báo ấy rõ hơn một timeout */
    });
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {
    /* trang này vốn không bao giờ lặng hẳn — không sao, đi tiếp */
  });
}

/* ══════════════════════════════════════════════════════════════════════════════
   BỘ THU THẬP — chạy trong trang, trả về dữ liệu thô để Node chấm
   ════════════════════════════════════════════════════════════════════════════ */

interface ControlSample {
  readonly label: string;
  readonly width: number;
  readonly height: number;
  readonly inCanvasViewport: boolean;
}

/**
 * Mọi điều khiển tương tác đang NHÌN THẤY ĐƯỢC, kèm kích thước thật trên màn hình.
 *
 * <p>Ba loại bị loại ra, và mỗi loại có lý do chứ không phải để cho dễ xanh:</p>
 *
 * <ul>
 *   <li>Phần tử không nhìn thấy (rộng/cao 0, {@code opacity: 0}, {@code visibility: hidden}) —
 *       Ant Design giấu một {@code <input type="radio">} thật dưới mỗi nhãn Segmented; đo cái vô
 *       hình là đo nhầm đối tượng.</li>
 *   <li>Liên kết NẰM TRONG một câu văn ({@code display: inline}, và câu ấy còn chữ khác ngoài
 *       liên kết). WCAG 2.5.8 có đúng ngoại lệ "inline" này: bắt một liên kết giữa dòng phải cao
 *       44px là ép giãn dòng chữ ra, làm hại chính người đang đọc.</li>
 *   <li>Phần tử nằm trong {@code .react-flow__viewport} — đó là nội dung phóng được của canvas,
 *       kiểm riêng ở ca C-3.5 vì kích thước của nó phụ thuộc mức phóng.</li>
 * </ul>
 */
async function collectControls(page: Page): Promise<ControlSample[]> {
  return page.evaluate(() => {
    const SELECTOR = [
      "a[href]",
      "button",
      "input:not([type=hidden])",
      "select",
      "textarea",
      "summary",
      '[role="button"]',
      '[role="link"]',
      '[role="tab"]',
      '[role="menuitem"]',
      '[role="switch"]',
      '[role="checkbox"]',
      '[role="radio"]',
      '[tabindex]:not([tabindex="-1"])',
    ].join(",");

    const describe = (el: Element): string => {
      const tag = el.tagName.toLowerCase();
      const label =
        el.getAttribute("aria-label") ??
        (el as HTMLElement).innerText?.trim().slice(0, 40) ??
        el.getAttribute("title") ??
        "";
      const cls = (el.getAttribute("class") ?? "").split(/\s+/).slice(0, 3).join(".");
      return `${tag}${cls ? "." + cls : ""}${label ? ` "${label.replace(/\s+/g, " ")}"` : ""}`;
    };

    const out: {
      label: string;
      width: number;
      height: number;
      inCanvasViewport: boolean;
    }[] = [];

    for (const el of document.querySelectorAll<HTMLElement>(SELECTOR)) {
      const style = getComputedStyle(el);
      if (style.visibility === "hidden" || style.display === "none") continue;
      if (Number.parseFloat(style.opacity) === 0) continue;
      const rect = el.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) continue;

      // Ngoại lệ "inline" của WCAG 2.5.8: liên kết nằm giữa một câu văn.
      if (style.display === "inline") {
        const parentText = (el.parentElement?.textContent ?? "").trim();
        const ownText = (el.textContent ?? "").trim();
        if (parentText.length > ownText.length) continue;
      }

      out.push({
        label: describe(el),
        width: rect.width,
        height: rect.height,
        inCanvasViewport: Boolean(el.closest(".react-flow__viewport")),
      });
    }
    return out;
  });
}

interface TextSample {
  readonly label: string;
  readonly text: string;
  readonly fontSizePx: number;
  readonly inCanvasViewport: boolean;
}

/**
 * Cỡ chữ THẬT của mọi đoạn chữ đang hiển thị. Chỉ lấy phần tử có nút văn bản con trực tiếp — nếu
 * lấy cả tổ tiên thì một chữ 11px sẽ bị một thẻ bọc 16px che mất.
 */
async function collectText(page: Page): Promise<TextSample[]> {
  return page.evaluate(() => {
    const out: {
      label: string;
      text: string;
      fontSizePx: number;
      inCanvasViewport: boolean;
    }[] = [];

    for (const el of document.querySelectorAll<HTMLElement>("body *")) {
      const ownText = [...el.childNodes]
        .filter((n) => n.nodeType === Node.TEXT_NODE)
        .map((n) => n.textContent ?? "")
        .join("")
        .trim();
      if (ownText.length === 0) continue;

      const style = getComputedStyle(el);
      if (style.visibility === "hidden" || style.display === "none") continue;
      if (Number.parseFloat(style.opacity) === 0) continue;
      // `.sr-only` là chữ CỐ Ý chỉ dành cho trình đọc màn hình — nó không có cỡ nhìn thấy được.
      const rect = el.getBoundingClientRect();
      if (rect.width <= 1 || rect.height <= 1) continue;

      const cls = (el.getAttribute("class") ?? "").split(/\s+/).slice(0, 3).join(".");
      out.push({
        label: `${el.tagName.toLowerCase()}${cls ? "." + cls : ""}`,
        text: ownText.slice(0, 40),
        fontSizePx: Number.parseFloat(style.fontSize),
        inCanvasViewport: Boolean(el.closest(".react-flow__viewport")),
      });
    }
    return out;
  });
}

interface FocusSample {
  readonly label: string;
  readonly outlineStyle: string;
  readonly outlineWidthPx: number;
  readonly outlineColor: string;
  readonly boxShadow: string;
  /** Nền ngay sau phần tử (leo lên tổ tiên tới màu đục đầu tiên). */
  readonly behindColor: string;
  /** Nền của chính phần tử — lớp quầng ôm sát nó nằm trên nền này. */
  readonly ownColor: string;
  readonly obscured: boolean;
}

/** Đọc dấu hiệu tiêu điểm của phần tử ĐANG được tiêu điểm. */
async function readFocusIndicator(page: Page): Promise<FocusSample | null> {
  return page.evaluate(() => {
    const el = document.activeElement as HTMLElement | null;
    if (!el || el === document.body || el === document.documentElement) return null;
    // Lớp phủ công cụ của Next dev (`<nextjs-portal>`) không thuộc sản phẩm — nó không có mặt ở
    // bản dựng thật, nên chấm nó là báo một lỗi không tồn tại với người dùng.
    if (el.tagName.toLowerCase() === "nextjs-portal" || el.closest("nextjs-portal")) return null;

    /**
     * Nhiều widget cố ý ĐẶT TIÊU ĐIỂM lên một ô nhập vô hình rồi vẽ vòng tiêu điểm lên phần tử
     * nhìn thấy được bọc ngoài — Ant Design làm đúng thế với <Select> và <Segmented>. Chấm chính
     * cái ô vô hình là chấm nhầm phần tử: nó luôn "không có vòng nào".
     *
     * Nên khi phần tử nhận tiêu điểm là thứ trong suốt, ta leo lên tổ tiên nhìn thấy được gần
     * nhất và chấm vòng tiêu điểm Ở ĐÓ. Không lỏng tay: nếu cả hai đều không có dấu hiệu nào thì
     * ca kiểm vẫn đỏ.
     */
    const hasRing = (s: CSSStyleDeclaration): boolean =>
      (s.outlineStyle !== "none" && (Number.parseFloat(s.outlineWidth) || 0) >= 2) ||
      (s.boxShadow !== "none" && s.boxShadow.trim().length > 0);

    /** Phần tử nhìn thấy được gần nhất — dùng để định vị và để báo cáo. */
    const visibleHost = ((): HTMLElement => {
      let node: HTMLElement = el;
      for (let i = 0; i < 4; i += 1) {
        const s = getComputedStyle(node);
        const r = node.getBoundingClientRect();
        if (Number.parseFloat(s.opacity) > 0 && r.width > 1 && r.height > 1) return node;
        if (!node.parentElement) return node;
        node = node.parentElement;
      }
      return node;
    })();

    /** Phần tử THẬT SỰ mang vòng tiêu điểm, nếu có ai trong bốn tầng đầu mang nó. */
    const ringSource = ((): HTMLElement | null => {
      let node: HTMLElement | null = el;
      for (let i = 0; i < 4 && node; i += 1) {
        if (hasRing(getComputedStyle(node))) return node;
        node = node.parentElement;
      }
      return null;
    })();

    const opaqueBackground = (start: Element | null): string => {
      let node: Element | null = start;
      while (node) {
        const bg = getComputedStyle(node).backgroundColor;
        const alpha = /rgba?\([^)]*?([\d.]+)\s*\)$/.exec(bg);
        const isTransparent = bg === "transparent" || (alpha != null && Number(alpha[1]) === 0);
        if (!isTransparent && bg) return bg;
        node = node.parentElement;
      }
      return "rgb(255, 255, 255)";
    };

    const source = ringSource ?? visibleHost;
    const style = getComputedStyle(source);

    const rect = visibleHost.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    const hit = document.elementFromPoint(cx, cy);
    const cls = (el.getAttribute("class") ?? "").split(/\s+/).slice(0, 2).join(".");

    return {
      label:
        `${el.tagName.toLowerCase()}${cls ? "." + cls : ""} ` +
        `"${(el.getAttribute("aria-label") ?? el.innerText ?? "").trim().slice(0, 30)}"` +
        (source === el ? "" : ` (vòng vẽ ở <${source.tagName.toLowerCase()}> bọc ngoài)`),
      outlineStyle: style.outlineStyle,
      outlineWidthPx: Number.parseFloat(style.outlineWidth) || 0,
      outlineColor: style.outlineColor,
      boxShadow: style.boxShadow,
      behindColor: opaqueBackground(source.parentElement),
      ownColor: opaqueBackground(source),
      // C-4.4: phần tử được tiêu điểm không được bị thanh công cụ hay lớp phủ che khuất.
      obscured: !(hit && (visibleHost.contains(hit) || hit.contains(visibleHost))),
    };
  });
}

/* ══════════════════════════════════════════════════════════════════════════════
   CHẤM ĐIỂM — chạy ở Node, dùng lại đúng bộ tính tương phản của tầng unit
   ════════════════════════════════════════════════════════════════════════════ */

function undersizedControls(controls: readonly ControlSample[]): string[] {
  return controls
    .filter((c) => !c.inCanvasViewport)
    .filter((c) => c.width < TOUCH_FLOOR_PX || c.height < TOUCH_FLOOR_PX)
    .map((c) => `${c.label} → ${Math.round(c.width)}×${Math.round(c.height)}px`);
}

function undersizedText(texts: readonly TextSample[], floor: number): string[] {
  return texts
    .filter((t) => !t.inCanvasViewport)
    .filter((t) => t.fontSizePx < floor)
    .map((t) => `${t.label} "${t.text}" → ${t.fontSizePx}px`);
}

/**
 * Vòng tiêu điểm có nhìn thấy được không.
 *
 * <p>Chấp nhận hai cách vẽ, vì cả hai đều là cách vẽ đúng: một {@code outline} ≥2px, hoặc một
 * {@code box-shadow} làm quầng. Với mẫu hai lớp mà sản phẩm đang dùng, chỉ cần <b>một</b> trong hai
 * lớp đạt 3:1 — lớp kia gánh ở những nền mà lớp này chìm (C-4.3).</p>
 */
function focusProblems(sample: FocusSample): string[] {
  const problems: string[] = [];
  const hasOutline = sample.outlineStyle !== "none" && sample.outlineWidthPx >= 2;
  const hasShadow = sample.boxShadow !== "none" && sample.boxShadow.trim().length > 0;

  if (!hasOutline && !hasShadow) {
    problems.push(
      `${sample.label}: không có dấu hiệu tiêu điểm nào thấy được ` +
        `(outline ${sample.outlineStyle} ${sample.outlineWidthPx}px, box-shadow ${sample.boxShadow})`
    );
    return problems;
  }

  const behind = parseColor(sample.behindColor);
  const own = parseColor(sample.ownColor);
  const ratios: { layer: string; ratio: number }[] = [];

  const push = (layer: string, colorText: string, surface: Rgb | null): void => {
    const color = parseColor(colorText);
    if (!color || !surface) return;
    const solid = color.a < 1 ? flattenOver(color, surface) : color;
    ratios.push({ layer, ratio: contrastRatio(solid, surface) });
  };

  if (hasOutline) {
    push("outline / nền sau", sample.outlineColor, behind);
    push("outline / nền phần tử", sample.outlineColor, own);
  }
  if (hasShadow) {
    // `rgb(...)`/`rgba(...)` đầu tiên trong chuỗi box-shadow là màu của quầng.
    const shadowColor = /rgba?\([^)]*\)/.exec(sample.boxShadow)?.[0];
    if (shadowColor) {
      push("quầng / nền sau", shadowColor, behind);
      push("quầng / nền phần tử", shadowColor, own);
    }
  }

  if (ratios.length === 0) {
    problems.push(`${sample.label}: không đọc được màu của dấu hiệu tiêu điểm để chấm tương phản`);
    return problems;
  }

  const best = ratios.sort((a, b) => b.ratio - a.ratio)[0]!;
  if (roundRatio(best.ratio) < CONTRAST_UI) {
    problems.push(
      `${sample.label}: dấu hiệu tiêu điểm chỉ đạt ${formatRatio(best.ratio)} ` +
        `(lớp khá nhất: ${best.layer}), cần ≥ 3:1 — C-4.3`
    );
  }
  if (sample.obscured) {
    problems.push(`${sample.label}: phần tử được tiêu điểm bị lớp khác che khuất — C-4.4`);
  }
  return problems;
}

/**
 * Ảnh chụp lúc ca kiểm đỏ — luôn nằm trong {@code .playwright-mcp/}, không bao giờ ra gốc kho.
 * Tên màn hình có dấu tiếng Việt và dấu cách nên phải bỏ dấu trước khi làm tên tệp.
 */
async function snapshot(page: Page, name: string): Promise<void> {
  const slug = name
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[đĐ]/g, "d")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .toLowerCase();
  await page.screenshot({ path: `.playwright-mcp/a11y-${slug}.png`, fullPage: false });
}

/* ══════════════════════════════════════════════════════════════════════════════
   1 · VÙNG CHẠM ≥ 44×44px
   ════════════════════════════════════════════════════════════════════════════ */

function touchTargetSuite(surface: string, screens: readonly Screen[]): void {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
  });

  for (const screen of screens) {
    test(`C-3.1 · ${surface} · mọi điều khiển trên ${screen.name} đạt vùng chạm 44×44px`, async ({
      page,
    }) => {
      await page.goto(screen.path);
      await settle(page);
      if (screen.path === "/tree") await waitForTreeReady(page);

      const controls = await collectControls(page);
      expect(
        controls.length,
        `không tìm thấy điều khiển nào trên ${screen.name} — phép quét hỏng, mọi khẳng định vô nghĩa`
      ).toBeGreaterThan(0);

      const tooSmall = undersizedControls(controls);
      if (tooSmall.length > 0) await snapshot(page, `cham-${surface}-${screen.name}`);
      expect(
        tooSmall,
        `${tooSmall.length}/${controls.length} điều khiển dưới sàn chạm ${TOUCH_FLOOR_PX}px trên ` +
          `${screen.name} (${surface}):\n${tooSmall.map((s) => `  · ${s}`).join("\n")}`
      ).toEqual([]);
    });
  }

  /**
   * C-3.5 — ĐO PIXEL THẬT, KHÔNG ĐO MỨC PHÓNG.
   *
   * <p>Đây là chỗ khó nhất trong cả tài liệu, và là chỗ bộ kiểm cũ đã trượt: nút bung nhánh vẽ
   * 24px, nhưng người dùng chạm vào <b>24px × mức phóng</b>. Ở sàn phóng hiện tại 0.75 con số thật
   * là 18px — dưới cả ngưỡng tối thiểu tuyệt đối 24px, chưa nói tới 44px.</p>
   *
   * <p>Ca này cố ý đo ở <b>mức phóng mặc định lúc mở màn hình</b>, không phải ở mức 1.0: mức 1.0
   * là mức người dùng phải tự tìm tới, còn mức mặc định là mức họ gặp.</p>
   */
  test(`C-3.5 · ${surface} · nút bung nhánh trên phả đồ đạt 44px ở MỨC PHÓNG MẶC ĐỊNH`, async ({
    page,
  }) => {
    await page.goto("/tree");
    await waitForTreeReady(page);
    await waitForViewportSettled(page);

    const measured = await page.evaluate(() => {
      const canvas = document.querySelector(".react-flow")?.getBoundingClientRect();
      const style = document.querySelector<HTMLElement>(".react-flow__viewport")?.style.transform;
      const scale = Number(/scale\(([\d.]+)\)/.exec(style ?? "")?.[1] ?? "1");
      const out: { id: string; width: number; height: number }[] = [];
      if (!canvas) return { scale, out };
      for (const button of document.querySelectorAll<HTMLElement>(".react-flow__node button")) {
        const r = button.getBoundingClientRect();
        const cx = r.left + r.width / 2;
        const cy = r.top + r.height / 2;
        // Chỉ những nút thật sự nằm trong canvas: React Flow giữ lại trong DOM cả những nút đã
        // trôi ra ngoài, và đo chúng là đo một thứ người dùng không nhìn thấy.
        if (cx < canvas.left || cx > canvas.right || cy < canvas.top || cy > canvas.bottom) continue;
        out.push({
          id: button.closest(".react-flow__node")?.getAttribute("data-id") ?? "?",
          width: r.width,
          height: r.height,
        });
      }
      return { scale, out };
    });

    expect(
      measured.out.length,
      "không nút bung nhánh nào nằm trong canvas để đo — phép kiểm chưa chạy"
    ).toBeGreaterThan(0);

    const tooSmall = measured.out.filter(
      (b) => b.width < TOUCH_FLOOR_PX || b.height < TOUCH_FLOOR_PX
    );
    if (tooSmall.length > 0) await snapshot(page, `cham-bung-nhanh-${surface}`);
    expect(
      tooSmall.map((b) => `${b.id} → ${b.width.toFixed(1)}×${b.height.toFixed(1)}px`),
      `phả đồ mở ở mức phóng ${measured.scale}; ${tooSmall.length}/${measured.out.length} nút bung ` +
        `nhánh dưới sàn chạm ${TOUCH_FLOOR_PX}px. Đây là phép nhân mà C-3.5 nói tới: 24px vẽ ra ` +
        `nhân ${measured.scale} = ${(24 * measured.scale).toFixed(1)}px người dùng thật sự chạm ` +
        "được. Vùng chạm ẩn được phép LỚN HƠN vòng tròn nhìn thấy — đó là cách sửa."
    ).toEqual([]);
  });
}

test.describe("vùng chạm · desktop", () => {
  touchTargetSuite("desktop", MAIN_SCREENS);
});

test.describe("vùng chạm · điện thoại", () => {
  // Dự án `mobile-chromium` trong playwright.config.ts chỉ nhận `mobile.spec.ts` và
  // `tree-legibility.spec.ts`, mà tệp cấu hình ấy không thuộc phạm vi sửa của việc này — nên khung
  // nhìn điện thoại được giả lập ngay tại đây. `defaultBrowserType` không đặt được trong describe
  // (Playwright bắt buộc nó ở cấp tệp/cấu hình vì nó đổi cả worker), nên chỉ lấy phần mô tả THIẾT
  // BỊ: khung nhìn, tỉ lệ điểm ảnh, user-agent, cảm ứng. Trình duyệt vẫn là chromium.
  test.use({
    viewport: devices["Pixel 5"].viewport,
    userAgent: devices["Pixel 5"].userAgent,
    deviceScaleFactor: devices["Pixel 5"].deviceScaleFactor,
    isMobile: devices["Pixel 5"].isMobile,
    hasTouch: devices["Pixel 5"].hasTouch,
  });
  touchTargetSuite("điện thoại", CORE_SCREENS);
});

/* ══════════════════════════════════════════════════════════════════════════════
   2 · CỠ CHỮ ≥ 16px, KỂ CẢ WIDGET ANT DESIGN
   ════════════════════════════════════════════════════════════════════════════ */

test.describe("cỡ chữ", () => {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
  });

  for (const screen of MAIN_SCREENS) {
    test(`C-1.1 · chữ trên ${screen.name} đạt sàn 16px`, async ({ page }) => {
      await page.goto(screen.path);
      await settle(page);
      if (screen.path === "/tree") await waitForTreeReady(page);

      const texts = await collectText(page);
      expect(texts.length, `không quét được đoạn chữ nào trên ${screen.name}`).toBeGreaterThan(3);

      const small = undersizedText(texts, BODY_FONT_FLOOR_PX);
      if (small.length > 0) await snapshot(page, `co-chu-${screen.name}`);
      expect(
        small,
        `${small.length}/${texts.length} đoạn chữ dưới sàn ${BODY_FONT_FLOOR_PX}px trên ` +
          `${screen.name}:\n${small.map((s) => `  · ${s}`).join("\n")}\n\n` +
          "Tài liệu 00 §2.2: thân bài tối thiểu 16px, KHÔNG CÓ NGOẠI LỆ ngoài canvas phả đồ (C-1.3)."
      ).toEqual([]);
    });
  }

  /**
   * Sàn tuyệt đối, tách riêng vì nó nghiêm trọng hơn hẳn: dưới 12px thì dấu tiếng Việt chồng tầng
   * (ữ, ỹ, ặ) bắt đầu dính vào nhau và chữ không còn đọc được, chứ không phải chỉ khó đọc.
   */
  test("C-1.2 · không đoạn chữ nào dưới 12px ở bất kỳ đâu ngoài canvas phả đồ", async ({ page }) => {
    const offenders: string[] = [];
    for (const screen of MAIN_SCREENS) {
      await page.goto(screen.path);
      await settle(page);
      if (screen.path === "/tree") await waitForTreeReady(page);
      offenders.push(
        ...undersizedText(await collectText(page), ABSOLUTE_FONT_FLOOR_PX).map(
          (s) => `${screen.name}: ${s}`
        )
      );
    }
    expect(offenders, offenders.map((s) => `  · ${s}`).join("\n")).toEqual([]);
  });

  /**
   * Ant Design không lấy cỡ chữ từ Tailwind: nó chèn CSS-in-JS vào {@code <head>} lúc chạy, từ
   * {@code antdTheme.token.fontSize}. Bỏ trống thì rơi về mặc định 14 — và đó là vi phạm rộng nhất
   * trong sản phẩm (mọi nút, ô nhập, nhãn biểu mẫu, bảng). Ca này đo đúng widget thật.
   */
  test("C-1.1 · widget Ant Design không rơi về mặc định 14px", async ({ page }) => {
    await page.goto("/search");
    await settle(page);

    const widgets = await page.evaluate(() => {
      const out: { selector: string; fontSizePx: number }[] = [];
      for (const selector of [
        ".ant-btn",
        ".ant-input",
        ".ant-select-selector",
        ".ant-form-item-label label",
        ".ant-segmented-item-label",
        ".ant-tag",
      ]) {
        for (const el of document.querySelectorAll<HTMLElement>(selector)) {
          const style = getComputedStyle(el);
          if (style.display === "none" || style.visibility === "hidden") continue;
          out.push({ selector, fontSizePx: Number.parseFloat(style.fontSize) });
        }
      }
      return out;
    });

    expect(
      widgets.length,
      "không tìm thấy widget Ant Design nào trên màn tìm kiếm — phép kiểm chưa chạy"
    ).toBeGreaterThan(0);
    const small = widgets.filter((w) => w.fontSizePx < BODY_FONT_FLOOR_PX);
    expect(
      small.map((w) => `${w.selector} → ${w.fontSizePx}px`),
      "widget Ant Design đang chạy dưới 16px — khai `fontSize: 16` trong " +
        "frontend/src/styles/antd-theme.ts (C-1.1)"
    ).toEqual([]);
  });
});

/* ══════════════════════════════════════════════════════════════════════════════
   3 · VÒNG TIÊU ĐIỂM BÀN PHÍM
   ════════════════════════════════════════════════════════════════════════════ */

test.describe("tiêu điểm bàn phím", () => {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
  });

  for (const screen of CORE_SCREENS) {
    test(`C-4.3 · đi bằng Tab qua ${screen.name}: mọi điểm dừng đều thấy được vòng tiêu điểm`, async ({
      page,
    }) => {
      await page.goto(screen.path);
      await settle(page);
      if (screen.path === "/tree") await waitForTreeReady(page);

      const problems: string[] = [];
      const seen = new Set<string>();
      let stops = 0;

      for (let i = 0; i < 25; i += 1) {
        await page.keyboard.press("Tab");
        const sample = await readFocusIndicator(page);
        if (!sample) continue;
        if (seen.has(sample.label)) continue;
        seen.add(sample.label);
        stops += 1;
        problems.push(...focusProblems(sample));
      }

      expect(
        stops,
        `Tab không dừng ở đâu trên ${screen.name} — hoặc trang không có điều khiển nào, hoặc ` +
          "tiêu điểm đang bị nuốt"
      ).toBeGreaterThan(2);
      if (problems.length > 0) await snapshot(page, `tieu-diem-${screen.name}`);
      expect(problems, problems.map((p) => `  · ${p}`).join("\n")).toEqual([]);
    });
  }

  /**
   * C-4.5 — liên kết "bỏ qua, tới nội dung chính". Rẻ, và là thứ đầu tiên người kiểm tiếp cận tìm.
   * Nó phải là điểm dừng ĐẦU TIÊN của Tab, nếu không thì nó không cứu được ai.
   */
  test("C-4.5 · điểm dừng Tab đầu tiên là liên kết bỏ qua tới nội dung chính", async ({ page }) => {
    await page.goto("/tree");
    await settle(page);
    await page.keyboard.press("Tab");

    const first = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      if (!el) return null;
      return {
        text: (el.innerText ?? el.getAttribute("aria-label") ?? "").trim(),
        href: el.getAttribute("href") ?? "",
      };
    });

    expect(first, "Tab lần đầu không dừng ở đâu cả").not.toBeNull();
    expect(
      `${first!.text} ${first!.href}`,
      "điểm dừng Tab đầu tiên không phải liên kết bỏ qua — C-4.5. Thêm một <a href=\"#main\"> " +
        "ở đầu mỗi trang."
    ).toMatch(/bỏ qua|skip|#main|#noi-dung/i);
  });
});

/* ══════════════════════════════════════════════════════════════════════════════
   4 · prefers-reduced-motion
   ════════════════════════════════════════════════════════════════════════════ */

/** Chờ máy quay của phả đồ ngừng đổi — dùng chung cho nhiều ca dưới đây. */
async function waitForViewportSettled(page: Page): Promise<void> {
  const read = (): Promise<string | null> =>
    page.locator(".react-flow__viewport").getAttribute("style");
  let previous = await read();
  await expect
    .poll(
      async () => {
        const current = await read();
        const settled = current === previous;
        previous = current;
        return settled;
      },
      { timeout: 15_000, intervals: [150, 150, 150, 300] }
    )
    .toBe(true);
}

/** Ma trận biến đổi của máy quay tại đúng thời điểm gọi. */
async function viewportTransform(page: Page): Promise<string> {
  return (await page.locator(".react-flow__viewport").getAttribute("style")) ?? "";
}

/**
 * Bấm "Thu toàn cây" rồi đo máy quay ngay khung hình kế tiếp và sau 500ms.
 *
 * <p>Có hoạt ảnh thì hai lần đo khác nhau (đang trên đường bay). Không hoạt ảnh thì máy quay nhảy
 * thẳng tới đích và hai lần đo trùng nhau. Đây là phép đo <b>hành vi</b>, không đọc giá trị cấu
 * hình — nên nó vẫn đúng dù người ta đổi cách cài đặt.</p>
 */
async function fitViewAnimates(page: Page): Promise<{ immediate: string; later: string }> {
  await page.getByTestId("tree-fit-whole").click();
  await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => r(null))));
  const immediate = await viewportTransform(page);
  await page.waitForTimeout(500);
  const later = await viewportTransform(page);
  return { immediate, later };
}

test.describe("giảm chuyển động · đã bật", () => {
  test.use({ contextOptions: { reducedMotion: "reduce" } });

  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
  });

  /**
   * C-6.1 — với người rối loạn tiền đình, một mặt phẳng lớn trượt và phóng gây chóng mặt và buồn
   * nôn thật. Phả đồ là thứ gây triệu chứng nặng nhất trong sản phẩm.
   */
  test("C-6.1 · hoạt ảnh canh khung của phả đồ không chạy", async ({ page }) => {
    await page.goto("/tree");
    await waitForTreeReady(page);
    await waitForViewportSettled(page);

    const { immediate, later } = await fitViewAnimates(page);
    expect(
      immediate,
      "máy quay phả đồ vẫn đang bay sau khi bấm Thu toàn cây, dù hệ điều hành đã xin giảm " +
        "chuyển động — C-6.1 đòi duration = 0 cho mọi lần canh khung"
    ).toBe(later);
  });

  test("C-6.1 · không chuyển tiếp nào còn làm dịch chuyển điểm ảnh", async ({ page }) => {
    await page.goto("/tree");
    await waitForTreeReady(page);

    const moving = await page.evaluate(() => {
      const bad: string[] = [];
      for (const el of document.querySelectorAll<HTMLElement>("body *")) {
        const style = getComputedStyle(el);
        const props = style.transitionProperty;
        const duration = style.transitionDuration
          .split(",")
          .map((d) => Number.parseFloat(d) * (d.includes("ms") ? 1 : 1000));
        const movesPixels = /transform|all|width|height|top|left|right|bottom|margin|inset/.test(
          props
        );
        if (movesPixels && duration.some((d) => d > 1)) {
          const cls = (el.getAttribute("class") ?? "").split(/\s+/).slice(0, 2).join(".");
          bad.push(`${el.tagName.toLowerCase()}${cls ? "." + cls : ""} → ${props} ${style.transitionDuration}`);
        }
      }
      return [...new Set(bad)].slice(0, 20);
    });

    expect(
      moving,
      "vẫn còn chuyển tiếp làm dịch chuyển điểm ảnh khi đã xin giảm chuyển động:\n" +
        moving.map((m) => `  · ${m}`).join("\n")
    ).toEqual([]);
  });
});

test.describe("giảm chuyển động · chưa bật (phép kiểm ngược)", () => {
  test.use({ contextOptions: { reducedMotion: "no-preference" } });

  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
  });

  /**
   * PHÉP KIỂM NGƯỢC cho ca ngay trên. Nếu phép đo không phân biệt được "có hoạt ảnh" với "không
   * hoạt ảnh" thì ca kia xanh vì phép đo mù, chứ không phải vì sản phẩm đúng. Ca này đòi phép đo
   * phải THẤY hoạt ảnh khi hoạt ảnh thật sự chạy.
   */
  test("phép đo hoạt ảnh THẤY được chuyển động khi không ai xin giảm nó", async ({ page }) => {
    await page.goto("/tree");
    await waitForTreeReady(page);
    await waitForViewportSettled(page);

    const { immediate, later } = await fitViewAnimates(page);
    expect(
      immediate,
      "phép đo không phân biệt được có hoạt ảnh với không hoạt ảnh — ca C-6.1 ở trên đang xanh " +
        "vì phép đo mù, không phải vì sản phẩm đúng"
    ).not.toBe(later);
  });
});

/* ══════════════════════════════════════════════════════════════════════════════
   5 · CHẾ ĐỘ TỐI
   ════════════════════════════════════════════════════════════════════════════ */

test.describe("chế độ tối", () => {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
  });

  /** Mọi biến màu khai trên <html>, đọc ở trạng thái hiện tại của truy vấn media. */
  async function readColorVars(page: Page): Promise<Record<string, string>> {
    return page.evaluate(() => {
      const style = getComputedStyle(document.documentElement);
      const out: Record<string, string> = {};
      for (let i = 0; i < style.length; i += 1) {
        const name = style.item(i);
        if (name.startsWith("--rgb-") || name.startsWith("--color-")) {
          out[name] = style.getPropertyValue(name).trim();
        }
      }
      return out;
    });
  }

  async function readBodyColors(page: Page): Promise<{ background: string; color: string }> {
    return page.evaluate(() => {
      const style = getComputedStyle(document.body);
      return { background: style.backgroundColor, color: style.color };
    });
  }

  /**
   * CẠM BẪY PHẢI BẮT (tài liệu 00 §3): "mọi màu phải khai ở :root trước; màu chỉ tồn tại trong
   * khối tối là lỗi kinh điển gây trang không đọc được".
   *
   * <p>Ở trạng thái mặc định "theo hệ thống" không có class nào trên {@code <html>}. Một biến chỉ
   * khai trong khối tối sẽ <b>không tồn tại</b> ở chế độ sáng — {@code color: } rỗng, chữ rơi về
   * đen mặc định trên một nền có thể cũng đen.</p>
   */
  test("mọi biến màu tồn tại ở CẢ HAI chế độ, không biến nào chỉ sống trong khối tối", async ({
    page,
  }) => {
    await page.goto("/");
    await settle(page);

    await page.emulateMedia({ colorScheme: "light" });
    const light = await readColorVars(page);
    await page.emulateMedia({ colorScheme: "dark" });
    const dark = await readColorVars(page);

    expect(Object.keys(light).length, "không đọc được biến màu nào trên <html>").toBeGreaterThan(5);

    const onlyInDark = Object.keys(dark).filter((k) => !light[k]);
    const onlyInLight = Object.keys(light).filter((k) => !dark[k]);
    expect(
      onlyInDark,
      "biến chỉ tồn tại ở chế độ tối — ở trạng thái 'theo hệ thống' của một máy đang để sáng thì " +
        "chúng rỗng, và trang không đọc được:\n" + onlyInDark.map((k) => `  · ${k}`).join("\n")
    ).toEqual([]);
    expect(
      onlyInLight,
      "biến chỉ tồn tại ở chế độ sáng — chế độ tối sẽ rơi về giá trị sáng, thành mảng chói giữa " +
        "nền tối:\n" + onlyInLight.map((k) => `  · ${k}`).join("\n")
    ).toEqual([]);
  });

  test("nền và chữ THẬT SỰ đổi khi hệ điều hành chuyển sang chế độ tối", async ({ page }) => {
    await page.goto("/");
    await settle(page);

    await page.emulateMedia({ colorScheme: "light" });
    const light = await readBodyColors(page);
    await page.emulateMedia({ colorScheme: "dark" });
    const dark = await readBodyColors(page);

    expect(
      dark.background,
      "nền trang không đổi khi chuyển sang chế độ tối — hoặc chưa có chế độ tối, hoặc khối tối " +
        "đang bị ghi đè"
    ).not.toBe(light.background);
    expect(dark.color, "màu chữ không đổi khi chuyển sang chế độ tối").not.toBe(light.color);

    const lightBg = parseColor(light.background);
    const darkBg = parseColor(dark.background);
    expect(lightBg && darkBg).toBeTruthy();
    // Nền tối phải TỐI HƠN nền sáng. Không có ca này thì hai bảng chỉ cần "khác nhau" là đủ.
    expect(
      contrastRatio(darkBg!, "#000000"),
      `nền chế độ tối (${dark.background}) không tối hơn nền chế độ sáng (${light.background})`
    ).toBeLessThan(contrastRatio(lightBg!, "#000000"));
  });

  for (const screen of CORE_SCREENS) {
    test(`chữ trên ${screen.name} vẫn đạt tương phản ở chế độ tối`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: "dark" });
      await page.goto(screen.path);
      await settle(page);
      if (screen.path === "/tree") await waitForTreeReady(page);

      const samples = await page.evaluate(() => {
        const opaqueBackground = (start: Element | null): string => {
          let node: Element | null = start;
          while (node) {
            const bg = getComputedStyle(node).backgroundColor;
            const alpha = /rgba?\([^)]*?([\d.]+)\s*\)$/.exec(bg);
            const transparent = bg === "transparent" || (alpha != null && Number(alpha[1]) === 0);
            if (!transparent && bg) return bg;
            node = node.parentElement;
          }
          return "rgb(255, 255, 255)";
        };

        const out: {
          label: string;
          text: string;
          color: string;
          background: string;
          fontSizePx: number;
          fontWeight: number;
        }[] = [];

        for (const el of document.querySelectorAll<HTMLElement>("body *")) {
          if (el.closest(".react-flow__viewport")) continue;
          // WCAG 1.4.3 loại trừ rõ ràng "thành phần giao diện đang bất hoạt". Nút đăng nhập ở
          // chế độ MSW luôn bị vô hiệu hoá (không có Keycloak nào để gọi) — chấm tương phản chữ
          // mờ của nó là báo một lỗi mà tiêu chuẩn không đòi.
          if (
            el.closest("[disabled], [aria-disabled='true'], .ant-btn-disabled, .ant-input-disabled")
          ) {
            continue;
          }
          const own = [...el.childNodes]
            .filter((n) => n.nodeType === Node.TEXT_NODE)
            .map((n) => n.textContent ?? "")
            .join("")
            .trim();
          if (own.length === 0) continue;
          const style = getComputedStyle(el);
          if (style.visibility === "hidden" || style.display === "none") continue;
          if (Number.parseFloat(style.opacity) === 0) continue;
          const rect = el.getBoundingClientRect();
          if (rect.width <= 1 || rect.height <= 1) continue;
          const cls = (el.getAttribute("class") ?? "").split(/\s+/).slice(0, 2).join(".");
          out.push({
            label: `${el.tagName.toLowerCase()}${cls ? "." + cls : ""}`,
            text: own.slice(0, 30),
            color: style.color,
            background: opaqueBackground(el),
            fontSizePx: Number.parseFloat(style.fontSize),
            fontWeight: Number.parseFloat(style.fontWeight) || 400,
          });
        }
        return out;
      });

      expect(samples.length, `không quét được chữ nào trên ${screen.name}`).toBeGreaterThan(3);

      const failures: string[] = [];
      for (const s of samples) {
        const fg = parseColor(s.color);
        const bg = parseColor(s.background);
        if (!fg || !bg || bg.a === 0) continue;
        const solid = fg.a < 1 ? flattenOver(fg, bg) : fg;
        const ratio = contrastRatio(solid, bg);
        const threshold = textThreshold(s.fontSizePx, s.fontWeight);
        if (roundRatio(ratio) < threshold) {
          failures.push(
            `${s.label} "${s.text}" → ${s.color} trên ${s.background} = ` +
              `${formatRatio(ratio)} (cần ≥ ${threshold}:1)`
          );
        }
      }
      if (failures.length > 0) await snapshot(page, `toi-${screen.name}`);
      expect(
        [...new Set(failures)],
        `${failures.length}/${samples.length} đoạn chữ dưới ngưỡng ở CHẾ ĐỘ TỐI trên ` +
          `${screen.name}:\n${[...new Set(failures)].map((f) => `  · ${f}`).join("\n")}`
      ).toEqual([]);
    });
  }
});

/* ══════════════════════════════════════════════════════════════════════════════
   6 · TÍN HIỆU DƯ THỪA — điều kiện để ngoại lệ "nền giấy cũ" còn hiệu lực
   ════════════════════════════════════════════════════════════════════════════ */

/**
 * Ca này là <b>điều kiện</b> của ngoại lệ ghi trong {@code tests/unit/a11y/token-contrast.test.ts}:
 * cặp nền giấy cũ / nền trắng chỉ 1,16:1 được phép trượt, VỚI ĐIỀU KIỆN nghĩa "đã khuất" còn được
 * một tín hiệu không phải màu gánh (C-8.1). C-8.2 nói thẳng: một chấm 4–8px KHÔNG tính là tín hiệu
 * — nó quá nhỏ để nhìn ở mức phóng &lt;1.0 và quá nhỏ để phân biệt màu ở mọi mức phóng.
 *
 * <p>Phép kiểm rẻ mà C-8.3 mô tả — in ra giấy đen trắng rồi hỏi "ai trong đây đã mất?" — dịch sang
 * máy chính là: <b>bỏ hết màu đi, thẻ người đã khuất còn phân biệt được không</b>.</p>
 */
test.describe("C-8.1 · phân biệt sống / đã khuất không được chỉ dựa vào màu", () => {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
  });

  test("thẻ người đã khuất vẫn phân biệt được khi bỏ hết màu", async ({ page }) => {
    await page.goto("/tree");
    await waitForTreeReady(page);
    await waitForViewportSettled(page);

    // Bỏ màu ngay trên trang thật: đúng bài "in ra giấy đen trắng" của C-8.3.
    await page.addStyleTag({ content: "html { filter: grayscale(1) !important; }" });
    await snapshot(page, "song-khuat-den-trang");

    const cards = await page.evaluate(() => {
      const out: { id: string; deceased: boolean; visibleText: string }[] = [];
      for (const node of document.querySelectorAll<HTMLElement>(".react-flow__node")) {
        // Trạng thái do chính thẻ công bố cho trình đọc màn hình — nguồn sự thật ở đây.
        const srOnly = [...node.querySelectorAll<HTMLElement>(".sr-only")]
          .map((el) => el.textContent ?? "")
          .join(" ");
        const visible = [...node.querySelectorAll<HTMLElement>("*")]
          .filter((el) => !el.classList.contains("sr-only") && !el.closest(".sr-only"))
          .map((el) =>
            [...el.childNodes]
              .filter((n) => n.nodeType === Node.TEXT_NODE)
              .map((n) => n.textContent ?? "")
              .join("")
          )
          .join(" ");
        if (srOnly.trim().length === 0) continue;
        out.push({
          id: node.getAttribute("data-id") ?? "?",
          deceased: /mất|khuất|deceased/i.test(srOnly) && !/còn sống|alive/i.test(srOnly),
          visibleText: visible.replace(/\s+/g, " ").trim(),
        });
      }
      return out;
    });

    expect(
      cards.filter((c) => c.deceased).length,
      "không thẻ người đã khuất nào trên màn hình để kiểm — phép kiểm chưa chạy"
    ).toBeGreaterThan(0);

    // Tín hiệu KHÔNG PHẢI MÀU được chấp nhận: một nhãn chữ, một dấu (†/✝), hoặc khoảng năm có năm
    // mất. Bất cứ thứ gì đọc được khi trang đã trắng đen.
    const CUE = /đã mất|đã khuất|mất năm|†|✝|\d{3,4}\s*[–—-]\s*\d{3,4}/i;
    const silent = cards.filter((c) => c.deceased && !CUE.test(c.visibleText));

    expect(
      silent.map((c) => `${c.id} → "${c.visibleText}"`),
      `${silent.length} thẻ người đã khuất không mang tín hiệu nào ngoài màu. Khi in đen trắng ` +
        "(C-8.3) không ai chỉ ra được ai đã mất. Chấm trạng thái 8px KHÔNG tính (C-8.2) — cần một " +
        "nhãn chữ, một dấu, hoặc khoảng năm có năm mất.\n\n" +
        "Ngoại lệ tương phản của nền giấy cũ trong tests/unit/a11y/token-contrast.test.ts đứng " +
        "trên đúng ca này; ca này đỏ thì ngoại lệ kia mất hiệu lực."
    ).toEqual([]);

    // Tín hiệu chỉ có nghĩa khi nó PHÂN BIỆT. Nếu thẻ người còn sống cũng mang đúng tín hiệu ấy
    // thì ca trên vẫn xanh trong khi người đọc vẫn không phân biệt được gì — đúng kiểu xanh giả.
    //
    // Đối chứng chỉ chạy khi trên màn hình CÓ người còn sống. Lát cắt mặc định của phả đồ là gốc
    // và mấy đời đầu, tức là toàn người đã khuất — bắt buộc phải có người sống ở đây là bắt bộ dữ
    // liệu giả lập chứ không phải bắt sản phẩm. Lời khẳng định chính ở trên vẫn cứng.
    const living = cards.filter((c) => !c.deceased);
    if (living.length > 0) {
      expect(
        living.filter((c) => CUE.test(c.visibleText)).map((c) => `${c.id} → "${c.visibleText}"`),
        "thẻ người CÒN SỐNG cũng mang tín hiệu 'đã khuất' — tín hiệu ấy không phân biệt được ai với ai"
      ).toEqual([]);
    }
  });
});

/* ══════════════════════════════════════════════════════════════════════════════
   7 · CHỐNG XANH GIẢ — dựng lại lỗi đã biết NGAY TRÊN TRANG THẬT
   ════════════════════════════════════════════════════════════════════════════ */

/**
 * Phần quan trọng nhất của tệp này.
 *
 * <p>Mọi ca ở trên đều dựa vào ba bộ thu thập chạy trong trình duyệt. Nếu một bộ thu thập bỏ sót —
 * chọn nhầm selector, lọc quá tay, đọc nhầm thuộc tính — thì mọi ca kia xanh, và cái xanh ấy chỉ
 * chứng minh rằng phép kiểm chưa bao giờ chạy. Nên ở đây ta <b>tiêm đúng những giá trị sai đã biết
 * vào một trang thật</b> rồi đòi bộ thu thập phải kêu.</p>
 */
test.describe("chống xanh giả · bộ thu thập trên trình duyệt phải bắt được lỗi", () => {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, "member");
    await page.goto("/");
    await settle(page);
  });

  test("bắt được một nút 18px — đúng kích thước nút bung nhánh ở sàn phóng 0.75", async ({
    page,
  }) => {
    await page.evaluate(() => {
      const button = document.createElement("button");
      button.type = "button";
      button.id = "nut-hoi-quy-18px";
      button.setAttribute("aria-label", "nút hồi quy 18px");
      button.style.cssText =
        "position:fixed;top:4px;left:4px;width:18px;height:18px;z-index:99999;padding:0";
      document.body.appendChild(button);
    });

    const tooSmall = undersizedControls(await collectControls(page));
    expect(
      tooSmall.join("\n"),
      "bộ thu thập vùng chạm KHÔNG thấy một nút 18px cố tình tiêm vào trang — mọi ca vùng chạm " +
        "ở trên đang xanh vì phép đo mù"
    ).toContain("nút hồi quy 18px");
  });

  test("bắt được chữ 14px — đúng cỡ mặc định của Ant Design khi thiếu khai fontSize", async ({
    page,
  }) => {
    await page.evaluate(() => {
      const p = document.createElement("p");
      p.textContent = "đoạn chữ hồi quy 14px";
      p.style.cssText = "position:fixed;bottom:4px;left:4px;font-size:14px;z-index:99999";
      document.body.appendChild(p);
    });

    const small = undersizedText(await collectText(page), BODY_FONT_FLOOR_PX);
    expect(
      small.join("\n"),
      "bộ thu thập cỡ chữ KHÔNG thấy một đoạn 14px cố tình tiêm vào trang"
    ).toContain("đoạn chữ hồi quy 14px");
  });

  test("KHÔNG bắt nhầm chữ 16px — phép kiểm phải phân biệt được đạt với không đạt", async ({
    page,
  }) => {
    await page.evaluate(() => {
      const p = document.createElement("p");
      p.textContent = "đoạn chữ hợp lệ 16px";
      p.style.cssText = "position:fixed;bottom:40px;left:4px;font-size:16px;z-index:99999";
      document.body.appendChild(p);
    });

    const small = undersizedText(await collectText(page), BODY_FONT_FLOOR_PX);
    expect(small.join("\n")).not.toContain("đoạn chữ hợp lệ 16px");
  });

  // Ca này chấm THUẬT TOÁN chấm điểm chứ không chấm trang, nên nó không cần `page` — nhưng nó
  // vẫn thuộc về đây, cạnh những ca cùng chứng minh một điều: các bộ đo có kêu hay không.
  test("bắt được vòng tiêu điểm hổ phách 2,95:1 — lỗi C-4.3 cũ", async () => {
    const problems = focusProblems({
      label: 'button "nút hồi quy hổ phách"',
      outlineStyle: "solid",
      outlineWidthPx: 2,
      outlineColor: "rgb(217, 119, 6)", // #d97706
      boxShadow: "none",
      behindColor: "rgb(248, 246, 242)", // #f8f6f2 nền kem
      ownColor: "rgb(248, 246, 242)",
      obscured: false,
    });
    expect(problems.join("\n")).toContain("2.95:1");

    // Và mẫu hai lớp đang dùng thì PHẢI qua — nếu nó cũng bị báo hỏng thì ngưỡng đang sai chứ
    // không phải sản phẩm sai.
    const twoLayer = focusProblems({
      label: 'button "nút hai lớp"',
      outlineStyle: "solid",
      outlineWidthPx: 2,
      outlineColor: "rgb(43, 40, 37)", // #2b2825 lõi
      boxShadow: "rgb(255, 255, 255) 0px 0px 0px 2px",
      behindColor: "rgb(248, 246, 242)",
      ownColor: "rgb(255, 255, 255)",
      obscured: false,
    });
    expect(twoLayer).toEqual([]);
  });

  /** Dựng một nút thật ở góc màn hình rồi đặt tiêu điểm vào nó. */
  async function nutThu(page: Page, style: string): Promise<void> {
    await page.evaluate((extra) => {
      document.getElementById("nut-khong-vong")?.remove();
      const button = document.createElement("button");
      button.type = "button";
      button.id = "nut-khong-vong";
      button.textContent = "nút không vòng tiêu điểm";
      button.style.cssText =
        "position:fixed;top:4px;right:4px;width:48px;height:48px;z-index:99999;" + extra;
      document.body.appendChild(button);
      button.focus();
    }, style);
  }

  /**
   * Bộ thu thập phải KÊU khi một điều khiển thật sự không có dấu hiệu tiêu điểm nào.
   *
   * <p><b>Vì sao mẫu thử phải mang {@code !important}, và đây không phải nới bài kiểm:</b> quy tắc
   * vòng tiêu điểm trong {@code globals.css} nay mang {@code !important}, vì Ant Design tiêm vào
   * {@code <head>} lúc chạy một quy tắc {@code :where(.css-…) a:focus { outline: 0 }} — cùng độ đặc
   * hiệu, nằm sau, nên nó gỡ vòng tiêu điểm của MỌI liên kết trong sản phẩm (đo được 1,00:1). Hệ
   * quả phụ là một {@code style="outline:none"} nội tuyến KHÔNG còn tạo ra được lỗi ấy nữa — nên
   * mẫu thử cũ không còn tái hiện trạng thái nào có thật, và một ca kiểm ngược không tái hiện được
   * lỗi thì không chứng minh gì cả.</p>
   *
   * <p>Lời khẳng định và ngưỡng giữ nguyên; chỉ mẫu thử được nâng cho đủ sức tạo ra đúng trạng
   * thái "không có vòng nào". Ca kiểm ngay dưới ghim lại phần được lợi.</p>
   */
  test("bắt được `outline: none` không có gì thay thế", async ({ page }) => {
    await nutThu(page, "outline:none !important;box-shadow:none !important");

    const sample = await readFocusIndicator(page);
    expect(sample, "không đọc được phần tử đang tiêu điểm").not.toBeNull();
    expect(
      focusProblems(sample!).join("\n"),
      "bộ thu thập tiêu điểm KHÔNG thấy một nút cố tình bỏ vòng tiêu điểm"
    ).toContain("không có dấu hiệu tiêu điểm nào thấy được");
  });

  /**
   * Mặt kia của cùng một đồng xu: một {@code outline: none} viết thường — kiểu người ta hay viết
   * để "cho gọn" — KHÔNG còn xoá được vòng tiêu điểm nữa. Đây là điều bài kiểm trên vừa mất khả
   * năng nói, nên phải nói ở đây, nếu không thì lớp bảo vệ này không có ai canh.
   */
  test("một `outline: none` thường KHÔNG xoá nổi vòng tiêu điểm của sản phẩm", async ({ page }) => {
    await nutThu(page, "outline:none;box-shadow:none");

    const sample = await readFocusIndicator(page);
    expect(sample, "không đọc được phần tử đang tiêu điểm").not.toBeNull();
    expect(
      focusProblems(sample!),
      "quy tắc vòng tiêu điểm chung đã mất `!important` — Ant Design sẽ lại gỡ vòng của mọi " +
        "liên kết bằng quy tắc `a:focus { outline: 0 }` nó tiêm lúc chạy"
    ).toEqual([]);
    expect(sample!.outlineWidthPx).toBeGreaterThanOrEqual(2);
  });
});
