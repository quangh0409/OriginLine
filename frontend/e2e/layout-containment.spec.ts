import type { Page } from "@playwright/test";
import { expect, signInAs, test } from "./fixtures";

/**
 * BỐ CỤC — ĐO TRÊN TRÌNH DUYỆT THẬT: KHÔNG THẺ CON NÀO RỘNG HƠN THẺ CHA.
 *
 * <h2>Vì sao phép đo này phải sống ở đây chứ không ở tầng unit</h2>
 * Tầng unit chạy trên `jsdom`, thứ <b>không có bộ dựng bố cục</b>: mọi
 * `getBoundingClientRect()` đều trả 0 và mọi lời khẳng định về bề rộng đều xanh
 * vô nghĩa. `tests/unit/a11y/layout-containment.test.ts` vì thế canh <b>quy
 * ước</b> (bảng phải nằm trong vùng cuộn, con flex phải khai `min-w-0`, …). Tệp
 * này canh <b>kết quả</b>, và chỉ trình duyệt trả lời được.
 *
 * Cùng một cặp tầng, cùng một lý do như bộ kiểm tiếp cận: `tests/unit/a11y/`
 * chấm bảng màu và cấu hình, `e2e/accessibility.spec.ts` đo vùng chạm 44px và
 * cỡ chữ 16px trên trang thật.
 *
 * <h2>Bất biến được đo, theo đúng lời chủ dự án</h2>
 * <ol>
 *   <li><b>Không phần tử nào tràn khỏi VIỀN của hộp cha nó.</b> Đây là câu
 *       "thẻ bên trong dài hơn thẻ bao ngoài" dịch sang hình học. So với hộp
 *       viền (`getBoundingClientRect`) chứ không so với hộp nội dung: cái người
 *       dùng nhìn thấy là chữ/nền chạy qua nét viền.</li>
 *   <li><b>Thân trang không bao giờ cuộn ngang.</b> Chỉ bảng, sơ đồ và khối mã
 *       được cuộn — mỗi thứ trong vùng cuộn riêng, đánh dấu
 *       `[data-vung-cuon]`.</li>
 *   <li><b>Lề hai bên ≥ 16px ở mọi bề rộng.</b></li>
 * </ol>
 *
 * <h2>Cái bẫy làm phép đo này xanh giả — và cách né</h2>
 * Bộ giả lập MSW dùng mã ngắn (`P-001`, `root.chi_nhat`), trong khi máy chủ
 * thật trả UUID 36 ký tự và đường `ltree` nhiều đốt. Chạy trên dữ liệu giả rồi
 * tuyên bố "không tràn" là đúng <b>với dữ liệu giả</b> và nói đúng số không về
 * sản phẩm thật — đó chính là lý do lỗi này sống tới tận buổi nghiệm thu.
 * Vì vậy nhóm ca cuối tệp <b>tiêm dữ liệu xấu nhất có thật</b> vào DOM đang
 * chạy (UUID, `ltree` bảy đốt, URL dán vào ô lý do) rồi đo lại.
 *
 * <p><b>Chống xanh giả, phần hai:</b> có một ca dựng lại đúng lỗi cũ ngay trên
 * trang thật (một hộp khai `overflow-wrap: normal` ôm một chuỗi 60 ký tự) và
 * khẳng định bộ thu thập <b>kêu</b>. Một bất biến không có phép kiểm ngược chỉ
 * chứng minh được rằng nó chưa bao giờ chạy.</p>
 *
 * Chạy trên CẢ HAI dự án Playwright (máy tính 1440px và Pixel 5 393px) — xem
 * `testMatch` trong `playwright.config.ts`. Khung điện thoại mới là nơi lỗi
 * xuất hiện trước, và đa số người trong họ ở xa dùng điện thoại.
 */

/** Lề tối thiểu hai bên thân trang, mọi bề rộng. */
const LE_TOI_THIEU_PX = 16;

/**
 * Dung sai của phép so hình chữ nhật.
 *
 * <p>2px chứ không phải 0: Ant Design chồng viền bằng `margin-left: -1px` ở vài
 * widget hợp thành, và trình duyệt làm tròn vị trí phụ-pixel khi có `transform`
 * hoặc `zoom`. 2px đủ tha cho hai thứ đó mà vẫn bắt được lỗi thật — lỗi thật ở
 * đây tính bằng hàng chục tới hàng trăm pixel, không bao giờ bằng 3.</p>
 */
const DUNG_SAI_PX = 2;

interface ManHinh {
  readonly path: string;
  readonly name: string;
  /** Vai cần đăng nhập để màn có nội dung thật. */
  readonly vai?: "guest" | "member" | "branch-head" | "admin";
}

/**
 * Các màn có THẺ nội dung. Phả đồ cố ý vắng mặt: canvas là mặt phẳng tràn viền,
 * nút con nằm ở toạ độ tuyệt đối trong một `transform`, nên "tràn khỏi cha" ở
 * đó là trạng thái bình thường và có bộ kiểm hình học riêng
 * (`e2e/tree-legibility.spec.ts`).
 */
const MAN_HINH: readonly ManHinh[] = [
  { path: "/", name: "trang chủ" },
  { path: "/persons/p-001", name: "hồ sơ nhân khẩu", vai: "member" },
  { path: "/persons/p-001/edit", name: "sửa hồ sơ", vai: "admin" },
  { path: "/persons/p-001/correction", name: "xin đính chính", vai: "member" },
  { path: "/search", name: "tìm kiếm", vai: "member" },
  { path: "/kinship?from=p-010&to=p-001", name: "tra danh xưng", vai: "member" },
  { path: "/events", name: "lịch giỗ", vai: "member" },
  { path: "/notifications", name: "hộp thư nhắc giỗ", vai: "member" },
  { path: "/correction-requests", name: "yêu cầu đính chính", vai: "branch-head" },
  { path: "/settings", name: "cài đặt", vai: "member" },
  { path: "/nhap-lieu", name: "nhập liệu · dẫn nhập", vai: "branch-head" },
  { path: "/nhap-lieu/mau", name: "nhập liệu · mẫu Excel", vai: "branch-head" },
  { path: "/nhap-lieu/tai-len", name: "nhập liệu · tải lên", vai: "branch-head" },
  { path: "/nhap-lieu/tien-do", name: "nhập liệu · tiến độ theo chi", vai: "branch-head" },
];

/** Tập lõi cho phép quét ĐẮT (lặp ở cả hai chế độ màu). */
const MAN_HINH_LOI: readonly ManHinh[] = MAN_HINH.filter((m) =>
  ["/persons/p-001", "/nhap-lieu/tien-do", "/correction-requests"].includes(m.path)
);

/* ══════════════════════════════════════════════════════════════════════════════
   BỘ THU THẬP — chạy trong trang
   ════════════════════════════════════════════════════════════════════════════ */

/**
 * Chờ trang lặng đi, CÓ HẠN.
 *
 * <p>Không dùng `networkidle` trần: `useNotifications` đặt `refetchInterval:
 * 60_000` và trình chạy dịch vụ của MSW giữ kết nối, nên "mạng lặng" là trạng
 * thái trang này có thể không bao giờ đạt tới — lời chờ sẽ ăn hết ngân sách của
 * ca kiểm rồi báo về một timeout trông y hệt một lỗi bố cục thật.</p>
 */
async function lang(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page
    .waitForFunction(
      () => {
        const main = document.querySelector("#noi-dung-chinh");
        return Boolean(main && (main as HTMLElement).innerText.trim().length > 20);
      },
      undefined,
      { timeout: 60_000 }
    )
    .catch(() => {
      /* để lời khẳng định bên dưới tự báo "không quét được phần tử nào" — rõ hơn một timeout */
    });
  // Ant Design tiêm CSS-in-JS vào <head> LÚC CHẠY; đo trước lượt tiêm ấy là đo một trang
  // chưa có nửa số quy tắc của nó.
  await page.waitForTimeout(400);

  // ...và chờ mọi con quay TẮT. Ô "Chi/Ngành" ở màn tìm kiếm bật `loading` trong lúc
  // `useBranches()` còn bay; trong khoảnh khắc ấy Ant Design đặt một glyph 22px vào cái khe
  // mũi tên 16px của CHÍNH NÓ, và bộ thu thập ghi lại 3px tràn. Đó không phải lỗi bố cục của
  // sản phẩm: khe ấy là nội thất Ant Design, nó `absolute` nên không đẩy gì ra, không sinh
  // cuộn ngang, và nó biến mất sau chưa tới ba giây. Đo một khung hình như thế là đo một
  // trạng thái người dùng gần như không thấy — và tệ hơn, kết quả phụ thuộc vào việc bộ giả
  // lập trả lời nhanh hay chậm, tức là một cái đỏ nhấp nháy.
  await page
    .waitForFunction(
      () =>
        document.querySelectorAll(
          "#noi-dung-chinh .anticon-loading, #noi-dung-chinh .ant-spin-spinning"
        ).length === 0,
      undefined,
      { timeout: 10_000 }
    )
    .catch(() => {
      /* Còn quay sau 10s là trang thật sự kẹt tải — để phép đo bên dưới nói ra điều đó bằng
         thứ nó thấy, thay vì chết ở đây bằng một timeout không nói gì về bố cục. */
    });
}

export interface TranVien {
  readonly mo: string;
  readonly thua: number;
  readonly phia: "trái" | "phải";
}

/**
 * Mọi phần tử trong thân trang tràn ra ngoài VIỀN của cha nó.
 *
 * <p>Những gì cố ý bỏ qua, và vì sao từng thứ:</p>
 * <ul>
 *   <li><b>Cây con nằm trong một vùng cuộn</b> (`overflow-x` khác `visible`,
 *       hoặc `[data-vung-cuon]`). Ở đó rộng hơn cha là <i>mục đích</i> — bảng
 *       cuộn được chính là lời giải, không phải lỗi.</li>
 *   <li><b>Phần tử định vị</b> (`absolute` / `fixed` / `sticky`). Hộp chứa của
 *       chúng không phải phần tử cha, nên so với cha là so nhầm hộp: huy hiệu
 *       đếm của Ant Design, ô `sr-only` 1×1px và lớp phủ đều rơi vào đây.</li>
 *   <li><b>Lớp phủ trong cổng React</b> (`.ant-modal-root`, `.ant-drawer`,
 *       `.ant-popover`, `.ant-select-dropdown`, `.ant-tooltip`) — chúng được
 *       gắn ra ngoài `<main>`, và bản thân phép quét đã bắt đầu từ `<main>`.</li>
 *   <li><b>Ruột `<svg>`.</b> Hình vẽ trong một `viewBox` không theo luật hộp
 *       CSS; một biểu tượng có nét tràn ra ngoài khung là chuyện của trình vẽ,
 *       không phải của bố cục.</li>
 *   <li><b>Canvas phả đồ</b> (`.react-flow`). Mặt phẳng vô hạn, nút con ở toạ
 *       độ tuyệt đối trong một `transform` — có bộ kiểm hình học riêng.</li>
 * </ul>
 */
async function timTranVien(page: Page): Promise<TranVien[]> {
  return page.evaluate((dungSai) => {
    const goc = document.querySelector<HTMLElement>("#noi-dung-chinh");
    if (!goc) return [];

    const LOP_PHU = ".ant-modal-root, .ant-drawer, .ant-popover, .ant-select-dropdown, .ant-tooltip";

    const ten = (el: Element): string => {
      const tag = el.tagName.toLowerCase();
      const cls = (el.getAttribute("class") ?? "")
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 4)
        .join(".");
      const chu = (el as HTMLElement).innerText?.trim().replace(/\s+/g, " ").slice(0, 48) ?? "";
      const testId = el.getAttribute("data-testid");
      return (
        `${tag}${cls ? "." + cls : ""}` +
        `${testId ? `[data-testid=${testId}]` : ""}` +
        `${chu ? ` "${chu}"` : ""}`
      );
    };

    const out: { mo: string; thua: number; phia: "trái" | "phải" }[] = [];

    const di = (el: HTMLElement, trongVungCuon: boolean): void => {
      for (const con of Array.from(el.children)) {
        if (!(con instanceof HTMLElement)) continue; // ruột <svg> là SVGElement — bỏ qua
        if (con.matches(LOP_PHU)) continue;
        if (con.classList.contains("react-flow")) continue;

        const cs = getComputedStyle(con);
        if (cs.display === "none" || cs.visibility === "hidden") continue;

        const dinhVi = cs.position === "absolute" || cs.position === "fixed" || cs.position === "sticky";
        const hopCon = con.getBoundingClientRect();
        const hopCha = el.getBoundingClientRect();

        if (!trongVungCuon && !dinhVi && hopCon.width > 0 && hopCha.width > 0) {
          const thuaPhai = hopCon.right - hopCha.right;
          const thuaTrai = hopCha.left - hopCon.left;
          if (thuaPhai > dungSai) {
            out.push({
              mo: `${ten(con)}\n        nằm trong ${ten(el)}`,
              thua: Math.round(thuaPhai),
              phia: "phải",
            });
          } else if (thuaTrai > dungSai) {
            out.push({
              mo: `${ten(con)}\n        nằm trong ${ten(el)}`,
              thua: Math.round(thuaTrai),
              phia: "trái",
            });
          }
        }

        const tuCuon =
          con.hasAttribute("data-vung-cuon") ||
          cs.overflowX === "auto" ||
          cs.overflowX === "scroll" ||
          cs.overflowX === "hidden" ||
          cs.overflowX === "clip";

        di(con, trongVungCuon || tuCuon);
      }
    };

    const csGoc = getComputedStyle(goc);
    di(goc, csGoc.overflowX === "auto" || csGoc.overflowX === "scroll");
    return out;
  }, DUNG_SAI_PX);
}

/** Trang có cuộn ngang được bao nhiêu pixel. Trên 1px là lỗi. */
async function cuonNgang(page: Page): Promise<number> {
  // Đọc lại vài lần: `kinship` và `search` đồng bộ trạng thái vào URL bằng
  // `router.replace` ngay sau khi gắn, và một `evaluate` rơi đúng lượt điều hướng
  // ấy chết vì "Execution context was destroyed" — một cái đỏ trông y hệt lỗi bố cục.
  const doc = () =>
    page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth
    );
  for (let lan = 0; lan < 5; lan += 1) {
    try {
      return await doc();
    } catch (loi) {
      if (lan === 4) throw loi;
      await page.waitForTimeout(300);
    }
  }
  return doc();
}

/**
 * Lề trái/phải của **nội dung** so với mép khung nhìn. `null` khi màn không có khung trang.
 *
 * <h2>Phải cộng cả `padding` của khung, không chỉ đo hộp viền</h2>
 * `KhungTrang` cài lề bằng `px-4` — tức **padding**, không phải margin. Trên máy rộng thì
 * `max-w-3xl` chặn lại và `mx-auto` đẩy hộp vào giữa, nên hộp viền có mép trái dương và một
 * phép đo chỉ nhìn `getBoundingClientRect().left` vẫn ra số đúng. Trên điện thoại thì
 * `max-w-*` không ràng buộc gì: hộp viền trải hết bề rộng, `left` bằng **0**, và phép đo ấy
 * báo "lề trái 0px" trên **mọi** màn — trong khi người dùng đang nhìn đúng 16px khoảng thở.
 *
 * <p>Đó là một cái đỏ tệ hơn cả một cái xanh sai: nó đỏ ở 13 màn cùng lúc, nên người đọc kết
 * luận "vỏ ứng dụng hỏng" và đi sửa vỏ, trong khi cả 13 màn đều đúng. Thứ cần đo là khoảng
 * cách từ mép màn hình tới **hộp nội dung**, và đó là `left + paddingLeft`.</p>
 */
async function leTrang(page: Page): Promise<{ trai: number; phai: number } | null> {
  return page.evaluate(() => {
    const khung = document.querySelector("[data-khung-trang]");
    if (!khung) return null;
    const r = khung.getBoundingClientRect();
    const cs = getComputedStyle(khung);
    return {
      trai: Math.round(r.left + parseFloat(cs.paddingLeft || "0")),
      phai: Math.round(
        document.documentElement.clientWidth - r.right + parseFloat(cs.paddingRight || "0")
      ),
    };
  });
}

function moTa(list: readonly TranVien[]): string {
  return list
    .slice(0, 12)
    .map((v) => `  · tràn ${v.thua}px sang ${v.phia}: ${v.mo}`)
    .join("\n");
}

async function moMan(page: Page, man: ManHinh): Promise<void> {
  await signInAs(page, man.vai ?? "member");
  await page.goto(man.path);
  await lang(page);
}

/* ══════════════════════════════════════════════════════════════════════════════
   1 · KHÔNG THẺ CON NÀO RỘNG HƠN THẺ CHA
   ════════════════════════════════════════════════════════════════════════════ */

test.describe("bố cục · thẻ con không được tràn khỏi viền thẻ cha", () => {
  for (const man of MAN_HINH) {
    // MỘT lượt mở trang cho cả ba phép đo, cố ý: mỗi `page.goto` bắt `next dev` biên dịch lại
    // một tuyến đường, và bộ e2e này đã phải chia nhỏ theo tệp vì bộ nhớ (README §7).
    test(`${man.name} · không tràn viền, không cuộn ngang, lề ≥ ${LE_TOI_THIEU_PX}px`, async ({
      page,
    }) => {
      await moMan(page, man);

      const tran = await timTranVien(page);
      expect(
        tran.map((v) => `tràn ${v.thua}px sang ${v.phia}: ${v.mo}`),
        `${tran.length} phần tử rộng hơn hộp cha trên ${man.name}:\n${moTa(tran)}\n\n` +
          "Bốn nguyên nhân đã biết, theo thứ tự hay gặp: (1) chuỗi máy không ngắt được — thêm " +
          "`break-all`; (2) con flex thiếu `min-w-0`; (3) rãnh lưới `1fr` trần — dùng " +
          "`minmax(0,1fr)`; (4) `<table>` chưa nằm trong <VungCuonNgang>."
      ).toEqual([]);

      expect(
        await cuonNgang(page),
        `${man.path} cuộn ngang được — chỉ bảng, sơ đồ và khối mã mới được cuộn, và mỗi thứ ` +
          "trong vùng cuộn riêng của nó"
      ).toBeLessThanOrEqual(1);

      const le = await leTrang(page);
      if (le) {
        expect(le.trai, `${man.name}: lề trái ${le.trai}px`).toBeGreaterThanOrEqual(
          LE_TOI_THIEU_PX
        );
        expect(le.phai, `${man.name}: lề phải ${le.phai}px`).toBeGreaterThanOrEqual(
          LE_TOI_THIEU_PX
        );
      }
    });
  }
});

/* ══════════════════════════════════════════════════════════════════════════════
   2 · CHẾ ĐỘ TỐI — bố cục không được đổi theo bảng màu, và phải chứng minh
   ════════════════════════════════════════════════════════════════════════════ */

test.describe("bố cục · chế độ tối", () => {
  for (const man of MAN_HINH_LOI) {
    test(`${man.name} · vẫn không tràn viền khi hệ điều hành ở chế độ tối`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: "dark" });
      await moMan(page, man);

      // Chốt rằng ca này THẬT SỰ đang đo chế độ tối. Thiếu nó, một lỗi khiến chế độ tối
      // không áp được sẽ biến cả nhóm ca này thành bản sao của nhóm trên — xanh, và vô nghĩa.
      const nen = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
      expect(nen, "trang không ở chế độ tối, nên ca này không đo thêm được gì").not.toBe(
        "rgb(248, 246, 242)"
      );

      const tran = await timTranVien(page);
      expect(
        tran.map((v) => `tràn ${v.thua}px sang ${v.phia}: ${v.mo}`),
        `chế độ tối · ${tran.length} phần tử rộng hơn hộp cha trên ${man.name}:\n${moTa(tran)}`
      ).toEqual([]);
      expect(await cuonNgang(page)).toBeLessThanOrEqual(1);
    });
  }
});

/* ══════════════════════════════════════════════════════════════════════════════
   3 · DỮ LIỆU THẬT XẤU NHẤT — nơi bộ giả lập nói dối
   ════════════════════════════════════════════════════════════════════════════ */

test.describe("bố cục · chuỗi máy dài, thứ bộ giả lập không có", () => {
  /** Khoá `app_user` in ra ở dòng "ai quyết, lúc nào" của màn đối chiếu nghi trùng. */
  const UUID_NGUOI_DUNG = "7a1d4c60-9f3b-4e21-b8d4-0c5e1a2f7b93";
  /** Đường `ltree` của một chi ở đời thứ bảy. */
  const DUONG_LTREE = "dong_ho.chi_truong.nganh_hai.canh_ba.nhanh_tu.phan_nam.doan_sau";
  /** URL người dùng dán vào ô "lý do" của một yêu cầu đính chính. */
  const URL_TAI_LIEU =
    "https://thuvienlichsu.example.gov.vn/tai-lieu/gia-pha/1897/ban-scan-trang-142.pdf";

  /** Ba chuỗi máy CÓ THẬT trong sản phẩm chạy với backend thật. */
  const CHUOI_XAU: readonly string[] = [UUID_NGUOI_DUNG, DUONG_LTREE, URL_TAI_LIEU];

  test("chuỗi máy dài nhất vẫn nằm trong thẻ — hồ sơ nhân khẩu", async ({ page }) => {
    await moMan(page, { path: "/persons/p-001", name: "hồ sơ nhân khẩu", vai: "member" });

    // Tiêm vào MỌI đoạn chữ trong thân trang, chứ không dựng một hộp mới: mục đích là đo
    // đúng những thẻ sản phẩm đang có, với dữ liệu có hình dạng của máy chủ thật.
    const soCho = await page.evaluate((chuoi) => {
      // Quét cả `dt`, `li` và **con cháu** `span` của `dd`, không chỉ `p, dd`. Mẫu hẹp hơn
      // thì đủ thứ đoạn chữ thật nằm ngoài tầm với: trên hồ sơ `p-001` chỉ có 5 thẻ `dd`, và
      // hai trong số đó bọc nội dung trong thẻ con (cặp ngày dương/âm) nên hết là "lá" — mẫu
      // tụt xuống 3, tức phép kiểm gần như rỗng mà vẫn xanh. Điều kiện "lá" được giữ: tiêm
      // vào một thẻ còn con là xoá mất cây con ấy, và khi đó ta đo một trang đã bị chính
      // phép kiểm phá.
      const doan = Array.from(
        document.querySelectorAll<HTMLElement>(
          "#noi-dung-chinh p, #noi-dung-chinh dd, #noi-dung-chinh dt," +
            " #noi-dung-chinh li, #noi-dung-chinh dd span"
        )
      ).filter((p) => p.children.length === 0 && (p.innerText ?? "").trim().length > 0);
      doan.forEach((p, i) => {
        p.textContent = `${p.textContent} ${chuoi[i % chuoi.length] ?? ""}`;
      });
      return doan.length;
    }, CHUOI_XAU);

    expect(soCho, "không tiêm được vào đoạn chữ nào — phép kiểm rỗng").toBeGreaterThan(3);

    const tran = await timTranVien(page);
    expect(
      tran.map((v) => `tràn ${v.thua}px sang ${v.phia}: ${v.mo}`),
      "một UUID/`ltree`/URL vẫn đẩy thẻ tràn khỏi viền. Sàn `overflow-wrap: break-word` ở " +
        `\`body\` (globals.css) là thứ chặn việc này — kiểm xem nó còn không:\n${moTa(tran)}`
    ).toEqual([]);
    expect(await cuonNgang(page)).toBeLessThanOrEqual(1);
  });

  test("chuỗi máy dài nhất vẫn nằm trong thẻ — màn tiến độ theo chi", async ({ page }) => {
    await moMan(page, {
      path: "/nhap-lieu/tien-do",
      name: "nhập liệu · tiến độ theo chi",
      vai: "branch-head",
    });

    // Đường `ltree` thật dài hơn `root.chi_nhat` của bộ giả lập nhiều lần, và nó in bằng
    // phông đơn cách — tức rộng hơn nữa.
    const soCho = await page.evaluate((duong) => {
      const o = Array.from(document.querySelectorAll<HTMLElement>("#noi-dung-chinh .font-mono"));
      o.forEach((el) => {
        el.textContent = duong;
      });
      return o.length;
    }, DUONG_LTREE);

    expect(soCho, "không tìm thấy ô in mã máy nào trên màn này").toBeGreaterThan(0);

    const tran = await timTranVien(page);
    expect(
      tran.map((v) => `tràn ${v.thua}px sang ${v.phia}: ${v.mo}`),
      `đường \`ltree\` dài đẩy thẻ chi tràn khỏi viền:\n${moTa(tran)}`
    ).toEqual([]);
    expect(await cuonNgang(page)).toBeLessThanOrEqual(1);
  });
});

/* ══════════════════════════════════════════════════════════════════════════════
   4 · BẢNG NẰM TRONG VÙNG CUỘN RIÊNG, VÀ VÙNG ẤY BẤM ĐƯỢC BẰNG BÀN PHÍM
   ════════════════════════════════════════════════════════════════════════════ */

test.describe("bố cục · bảng cuộn riêng, trang thì không", () => {
  test("bảng dòng đang chờ và bảng bằng chứng đều nằm trong một vùng cuộn có nhãn", async ({
    page,
  }) => {
    await signInAs(page, "branch-head");
    await page.goto("/nhap-lieu/tai-len");
    await expect(page.getByRole("heading", { name: /Tải tệp lên/i })).toBeVisible({
      timeout: 60_000,
    });
    await page.getByRole("radio", { name: /Chi Nhất/i }).check();
    await page.locator("#import-file-input").setInputFiles({
      name: "chi-nhat.xlsx",
      mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      buffer: Buffer.concat([
        Buffer.from([0x50, 0x4b, 0x03, 0x04]),
        Buffer.from("giapha-e2e:bo-cuc", "utf8"),
      ]),
    });
    await page.getByRole("button", { name: /^Tải lên và đối soát$/ }).click();
    await page.waitForURL(/\/nhap-lieu\/2026-\d+$/, { timeout: 60_000 });
    await lang(page);

    // Mở khối "các dòng đang chờ" — bảng bốn cột nằm trong đó.
    const tomTat = page.locator("details summary").first();
    if (await tomTat.isVisible()) await tomTat.click();
    await page.waitForTimeout(500);

    const bangKhongCuon = await page.evaluate(() =>
      Array.from(document.querySelectorAll("#noi-dung-chinh table")).filter(
        (t) => !t.closest("[data-vung-cuon]")
      ).length
    );
    expect(
      bangKhongCuon,
      "có <table> nằm trần trong một thẻ. Bảng là hộp duy nhất trong HTML lấy bề rộng bằng tổng " +
        "min-content của các cột, nên nó sẽ đẩy thẻ tràn viền rồi kéo cả trang cuộn ngang."
    ).toBe(0);

    // Một vùng cuộn được mà không lấy được tiêu điểm thì người chỉ dùng bàn phím
    // không có cách nào cuộn nó (WCAG 2.1.1).
    const vungKhongBamDuoc = await page.evaluate(() =>
      Array.from(document.querySelectorAll("[data-vung-cuon]")).filter(
        (v) => v.getAttribute("tabindex") !== "0" || !v.getAttribute("aria-label")
      ).length
    );
    expect(
      vungKhongBamDuoc,
      "một vùng cuộn thiếu `tabindex=0` hoặc thiếu nhãn — người dùng bàn phím không cuộn được nó"
    ).toBe(0);

    expect(await cuonNgang(page)).toBeLessThanOrEqual(1);

    const tran = await timTranVien(page);
    expect(
      tran.map((v) => `tràn ${v.thua}px sang ${v.phia}: ${v.mo}`),
      `màn đối soát:\n${moTa(tran)}`
    ).toEqual([]);
  });
});

/* ══════════════════════════════════════════════════════════════════════════════
   5 · CHỐNG XANH GIẢ — bộ thu thập phải BẮT ĐƯỢC lỗi cũ
   ════════════════════════════════════════════════════════════════════════════ */

test.describe("chống xanh giả · bộ thu thập trên trình duyệt phải kêu", () => {
  /**
   * Mồi nhử: một "từ" **dài hơn mọi hộp chứa hợp lý**, dựng tại chỗ theo bề rộng thật.
   *
   * <p>Bản đầu của hai ca dưới đây dùng một chuỗi 73 ký tự cố định. Nó từng tràn, rồi thôi:
   * thẻ `<article>` của hồ sơ rộng <b>736px</b> còn chuỗi ấy vẽ ra khoảng 620px, nên con
   * chim hoàng yến im lặng — và một con chim hoàng yến im lặng vì mồi hết tác dụng trông y
   * hệt một con chim hoàng yến im lặng vì mỏ than sạch. Đó đúng là kiểu xanh giả mà cả mục
   * này sinh ra để chặn, chỉ là nó xảy ra với chính nó.</p>
   *
   * <p>Nên mồi nay được tính từ `clientWidth` của hộp nhận: lặp UUID cho tới khi bề rộng
   * ước lượng vượt hộp một quãng an toàn. Đổi bố cục rộng hẹp thế nào mồi vẫn đủ dài, và
   * ca "KHÔNG bắt nhầm" dùng <b>đúng chuỗi ấy</b> nên hai vế vẫn đối xứng — chỉ khác một
   * thứ duy nhất: `overflow-wrap`.</p>
   */
  // MỒI KHÔNG CÓ DẤU GẠCH NGANG, và đó là nửa còn lại của lý do con chim hoàng yến đã câm.
  // Bản đầu dùng `7a1d4c60-9f3b-4e21-…` — nhưng dấu gạch ngang là một CHỖ NGẮT DÒNG hợp lệ
  // trong CSS, và `overflow-wrap: normal` không tắt nó đi: thuộc tính ấy chỉ nói có được ngắt
  // GIỮA MỘT TỪ hay không, còn ở đây trình duyệt vẫn có sẵn bảy chỗ ngắt tử tế. Nên "chuỗi
  // không ngắt được" ấy ngắt đẹp như thường và chẳng tràn đi đâu cả. Bỏ hết gạch ngang thì
  // nó mới thật sự là MỘT từ.
  //
  // Phép tính nằm TRONG `page.evaluate` ở cả hai ca chứ không gom thành hàm dùng chung:
  // `evaluate` chạy trong trình duyệt và không đóng bao được một hàm phía Node. Hai bản sao
  // ba dòng, giống nhau từng ký tự, đổi một chỗ là phải đổi cả hai — và ca "KHÔNG bắt nhầm"
  // sẽ đỏ ngay nếu chúng lệch nhau, nên sự trùng lặp này có người canh.
  // (~8.5px mỗi ký tự ở 16px Be Vietnam Pro; lấy 7 cho chắc rồi nhân đôi quãng an toàn.)

  /**
   * Lỗi cũ có <b>hai</b> triệu chứng, và hai bộ đo khác nhau bắt chúng.
   *
   * <p>Một chuỗi không ngắt được nằm trong một khối thường <b>không</b> làm hộp của nó to ra
   * — hộp vẫn 736px, chỉ có chữ chạy tràn ra ngoài. Bộ thu thập {@link timTranVien} so
   * <i>hộp con với hộp cha</i> nên nó không thấy gì cả; thứ bắt được ca ấy là
   * {@link cuonNgang}, vì chữ chạy quá mép khung nhìn thì tài liệu cuộn ngang được.</p>
   *
   * <p>Hộp <b>có</b> to ra khi đoạn chữ ấy là con của một hàng {@code flex} thiếu
   * {@code min-w-0}: bề rộng nội tại của "từ" đẩy chính hộp con rộng hơn hộp cha. Đó là
   * nguyên nhân số (2) trong danh sách gợi ý của chính bộ thu thập, và là ca duy nhất trong
   * hai ca mà nó có cơ chế để nhìn thấy.</p>
   *
   * <p>Ghim cả hai, vì khẳng định một mình vế nào cũng để lọt nửa còn lại — và bản đầu của
   * ca này khẳng định đúng vế mà bộ thu thập <b>không</b> nhìn được.</p>
   */
  test("bắt được đúng lỗi cũ: một hộp từ chối ngắt chuỗi dài", async ({ page }) => {
    await moMan(page, { path: "/persons/p-001", name: "hồ sơ nhân khẩu", vai: "member" });

    // Dựng lại NGUYÊN trạng thái trước bản vá: `overflow-wrap: normal` là giá trị mặc
    // định của CSS, và sàn ở `globals.css` chính là thứ đang ghi đè nó.
    const daTran = await page.evaluate(() => {
      const the = (document.querySelector("#noi-dung-chinh section, #noi-dung-chinh article") ??
        document.querySelector("#noi-dung-chinh")) as HTMLElement;
      const UUID = "7a1d4c609f3b4e21b8d40c5e1a2f7b93";
      const moi = UUID.repeat(
        Math.max(2, Math.ceil((Math.ceil(the.clientWidth / 7) * 2) / UUID.length))
      );

      // Vế 1 — khối thường: CHỮ tràn, hộp thì không.
      const chu = document.createElement("p");
      chu.style.overflowWrap = "normal";
      chu.style.wordBreak = "normal";
      chu.textContent = moi;
      the.appendChild(chu);

      // Vế 2 — hàng flex thiếu `min-w-0`: chính HỘP CON bị đẩy rộng hơn hộp cha.
      const hang = document.createElement("div");
      hang.style.display = "flex";
      const o = document.createElement("p");
      o.style.overflowWrap = "normal";
      o.style.wordBreak = "normal";
      o.textContent = moi;
      hang.appendChild(o);
      the.appendChild(hang);

      return { rongHop: the.clientWidth, rongChu: chu.scrollWidth };
    });

    // Mồi phải THẬT SỰ tràn trước đã. Không có dòng này thì hai lời khẳng định bên dưới chỉ
    // chứng minh được rằng mồi vô hại, chứ không chứng minh được gì về hai bộ đo.
    expect(
      daTran.rongChu,
      `mồi nhử (${daTran.rongChu}px) không rộng hơn hộp nhận (${daTran.rongHop}px) — ` +
        "phép kiểm chống-xanh-giả tự nó đã thành xanh giả"
    ).toBeGreaterThan(daTran.rongHop);

    const tran = await timTranVien(page);
    expect(
      tran.length,
      "bộ thu thập KHÔNG thấy một hộp con rộng hơn hộp cha trong hàng flex thiếu `min-w-0` " +
        "— tức là mọi lời khẳng định 'không tràn viền' ở tệp này đều vô nghĩa"
    ).toBeGreaterThan(0);

    expect(
      await cuonNgang(page),
      "chữ chạy quá mép khung nhìn mà tài liệu vẫn không cuộn ngang được — `cuonNgang` " +
        "không còn phân biệt được đạt với không đạt"
    ).toBeGreaterThan(1);
  });

  test("KHÔNG bắt nhầm cùng đoạn chữ ấy khi nó theo sàn của sản phẩm", async ({ page }) => {
    await moMan(page, { path: "/persons/p-001", name: "hồ sơ nhân khẩu", vai: "member" });

    // ĐÚNG hai cấu trúc của ca trên, khác một thứ duy nhất: không đặt `overflow-wrap`, tức là
    // chúng thừa hưởng sàn `break-word` ở `body` — và hàng flex có `min-w-0` như sản phẩm
    // vẫn viết. Nếu ca này cũng đỏ thì phép đo không phân biệt được đạt với không đạt, và cả
    // tệp mất nghĩa.
    await page.evaluate(() => {
      const the = (document.querySelector("#noi-dung-chinh section, #noi-dung-chinh article") ??
        document.querySelector("#noi-dung-chinh")) as HTMLElement;
      const UUID = "7a1d4c609f3b4e21b8d40c5e1a2f7b93";
      const moi = UUID.repeat(
        Math.max(2, Math.ceil((Math.ceil(the.clientWidth / 7) * 2) / UUID.length))
      );

      const chu = document.createElement("p");
      chu.textContent = moi;
      the.appendChild(chu);

      const hang = document.createElement("div");
      hang.style.display = "flex";
      const o = document.createElement("p");
      o.style.minWidth = "0";
      o.textContent = moi;
      hang.appendChild(o);
      the.appendChild(hang);
    });

    const tran = await timTranVien(page);
    expect(
      tran.map((v) => `tràn ${v.thua}px sang ${v.phia}: ${v.mo}`),
      "cùng một chuỗi, chỉ khác là nó thừa hưởng `overflow-wrap` từ `body` — nếu chỗ này cũng đỏ " +
        `thì phép đo không phân biệt được đạt với không đạt:\n${moTa(tran)}`
    ).toEqual([]);
  });

  test("bộ thu thập KHÔNG kêu vì một bảng đang cuộn trong vùng cuộn của nó", async ({ page }) => {
    await moMan(page, { path: "/persons/p-001", name: "hồ sơ nhân khẩu", vai: "member" });

    await page.evaluate(() => {
      const vung = document.createElement("div");
      vung.setAttribute("data-vung-cuon", "ngang");
      vung.style.overflowX = "auto";
      vung.style.maxWidth = "100%";
      const rong = document.createElement("div");
      rong.style.width = "2000px";
      rong.style.height = "8px";
      vung.appendChild(rong);
      document.querySelector("#noi-dung-chinh")!.appendChild(vung);
    });

    const tran = await timTranVien(page);
    expect(
      tran,
      "một khối 2000px NẰM TRONG vùng cuộn bị báo là tràn viền — đó là mục đích của vùng cuộn, " +
        "không phải lỗi, và phép đo phải phân biệt được hai thứ"
    ).toEqual([]);
    expect(await cuonNgang(page)).toBeLessThanOrEqual(1);
  });
});
