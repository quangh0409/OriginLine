import type { Config } from "tailwindcss";
import { tailwindColor } from "./src/styles/tokens";

// Tailwind theme is generated from the SAME token source as the Ant Design
// ConfigProvider theme (src/styles/tokens.ts) so the two design systems never
// drift apart. Do not hard-code colors here — add new tokens to tokens.ts.
//
// ─────────────────────────────────────────────────────────────────────────────
// Từ đợt sửa này, scale màu KHÔNG còn là hex mà là `rgb(var(--rgb-x) / <alpha>)`,
// trỏ vào biến CSS khai ở `src/app/globals.css`.
//
// Vì sao phải làm thế thay vì rắc `dark:` khắp nơi: mỗi lớp `bg-bg-card` đã viết
// sẽ TỰ hoạt động ở chế độ tối, không phải sửa một dòng component nào. Cách kia
// đòi đụng vào hàng trăm chỗ trong `components/` — nơi đang có nhiều người sửa
// song song — và bất kỳ chỗ nào quên sẽ thành một mảng sáng chói giữa nền tối,
// im lặng, không có gì bắt được.
//
// `<alpha-value>` là phần bắt buộc: có nó thì `bg-bg-page/95`,
// `hover:border-primary/40`, `border-primary/50` (cả ba đang có thật trong mã)
// mới còn chạy. Đưa thẳng `var(--color-x)` vào đây sẽ làm mọi hậu tố `/nn` im
// lặng mất tác dụng — không lỗi biên dịch, chỉ là độ mờ biến mất.
const config: Config = {
  // Ba trạng thái mà 00 §3 chốt: sáng · tối · theo hệ thống (mặc định).
  // `darkMode: "class"` cũ chỉ phục vụ được HAI trong ba — ở trạng thái "theo hệ
  // thống" không có class `dark` nào trên <html>, nên mọi tiện ích `dark:` sẽ im
  // dù hệ điều hành đang ở chế độ tối.
  //   không class → theo hệ thống   ·   .light → ép sáng   ·   .dark → ép tối
  darkMode: [
    "variant",
    [
      "@media (prefers-color-scheme: dark) { &:where(:not(.light *)) }",
      "&:where(.dark, .dark *)",
    ],
  ],
  content: [
    "./src/app/**/*.{ts,tsx}",
    "./src/components/**/*.{ts,tsx}",
    "./src/hooks/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: tailwindColor("primary"),
          dark: tailwindColor("primaryDark"),
          light: tailwindColor("primaryLight"),
        },
        // `accent` ở scale chung là màu MẢNG TÔ (#d97706 hổ phách). Đúng cho
        // `bg-accent`, `border-accent` — ngưỡng phi văn bản 3:1 là đủ, và đó là
        // sắc mà BA v2 chọn vì nó rực. Riêng `text-accent` và `outline-accent`
        // bị ánh xạ lại ở `textColor`/`outlineColor` bên dưới.
        accent: {
          DEFAULT: tailwindColor("accent"),
          text: tailwindColor("accentText"),
        },
        focus: {
          DEFAULT: tailwindColor("focusRing"),
          halo: tailwindColor("focusHalo"),
        },
        secondary: tailwindColor("secondary"),
        bg: {
          page: tailwindColor("bgPage"),
          card: tailwindColor("bgCard"),
        },
        text: {
          main: tailwindColor("textMain"),
          muted: tailwindColor("textMuted"),
        },
        border: {
          DEFAULT: tailwindColor("border"),
          dark: tailwindColor("borderDark"),
          // Viền Ô NHẬP, không phải viền thẻ. WCAG 1.4.11 bắt buộc 3:1 ở đây vì
          // viền là thứ DUY NHẤT cho biết "đây là chỗ gõ vào"; `border` thường
          // chỉ được 1,32:1 trên nền trắng, gần như vô hình.
          input: tailwindColor("borderInput"),
        },
        // Bảy token dưới đây từng thiếu, và cái giá không hề nhỏ: `person-node.tsx` dùng
        // `bg-success` cho chấm "người còn sống" và `border-border-dark` cho viền thẻ người đã
        // khuất, nhưng Tailwind chỉ sinh CSS cho tên màu đã khai ở đây. Không khai ⇒ lớp không
        // tồn tại ⇒ im lặng không có gì xảy ra, không cảnh báo, không lỗi biên dịch.
        //
        // Hệ quả người dùng thấy: chấm phân biệt SỐNG / ĐÃ KHUẤT trong suốt ở vế "còn sống", nên
        // trên phả đồ hai trạng thái này gần như không phân biệt được — trong khi chú giải vẫn vẽ
        // đúng hai chấm, vì chú giải tô màu bằng đường dẫn khác. Đó là lý do lỗi sống lâu.
        success: {
          DEFAULT: tailwindColor("success"),
          text: tailwindColor("successText"),
          bg: tailwindColor("successBg"),
        },
        danger: {
          DEFAULT: tailwindColor("danger"),
          bg: tailwindColor("dangerBg"),
        },
        warning: {
          bg: tailwindColor("warningBg"),
        },
        // Sắc giấy cũ dành riêng cho người đã khuất. Phải đọc như sự TÔN KÍNH, không phải như
        // trạng thái bị vô hiệu hoá — xem design/00-dinh-huong.html §3.
        deceased: tailwindColor("bgDeceased"),
      },

      // ── Ánh xạ lại `text-accent` và `text-success` ────────────────────────
      // Tài liệu 02 §1.3 cảnh báo đúng: quy tắc "hai token, một cho chữ một cho
      // mảng" chỉ đúng nếu cả đội kỷ luật chọn đúng token, và kinh nghiệm chung là
      // KHÔNG — vài tháng nữa sẽ có người viết `text-accent` vì tên ngắn hơn.
      //
      // Nên thay vì trông chờ kỷ luật (hoặc thêm một luật ESLint nữa để quên bật),
      // ta làm cho lựa chọn ngắn nhất cũng là lựa chọn đúng: `text-accent` TỰ trỏ
      // vào #9c5a06 (5,02–5,41:1 ✓), còn `bg-accent` giữ nguyên #d97706. Không
      // component nào phải sửa — quan trọng vì `components/` đang có người sửa
      // song song ngay lúc này.
      //
      // Vẫn còn lối thoát cho trường hợp cố ý: `text-accent-ruc` / `text-success-ruc`
      // giữ sắc gốc. Tên dài hơn và lạ hơn là CHỦ Ý — nó buộc người viết phải dừng
      // lại một nhịp và tự chịu trách nhiệm về tương phản ở chỗ đó.
      textColor: ({ theme }) => ({
        ...theme("colors"),
        accent: {
          DEFAULT: tailwindColor("accentText"),
          ruc: tailwindColor("accent"),
        },
        success: {
          DEFAULT: tailwindColor("successText"),
          ruc: tailwindColor("success"),
          bg: tailwindColor("successBg"),
        },
      }),

      // Cùng lý do, cho vòng tiêu điểm. `focus-visible:outline-accent` đang có ở
      // `tree/person-node.tsx` và `tree/tree-legend.tsx`, và hổ phách trượt ngưỡng
      // 3:1 ở CẢ BA nền quan trọng (2,95 nền kem · 2,73 giấy cũ · 2,63 quanh nút
      // đỏ trầm). Ánh xạ `outline-accent` về lõi vòng tiêu điểm #2b2825 (12,55–13,58:1)
      // chữa hai chỗ đó mà không cần chạm vào tệp của người khác.
      outlineColor: ({ theme }) => ({
        ...theme("colors"),
        accent: tailwindColor("focusRing"),
      }),
      ringColor: ({ theme }) => ({
        ...theme("colors"),
        accent: tailwindColor("focusRing"),
      }),

      // ── THANG CỠ CHỮ ─────────────────────────────────────────────────────
      // Vì sao là một thang có TÊN chứ không phải sửa từng chỗ: phép quét ngày
      // 2026-09-10 đếm được 151 chỗ đặt `text-[Npx]` rời rạc trên 55 tệp, trong
      // đó `text-[13px]` xuất hiện 45 lần và `text-[12px]` 38 lần. Đó không phải
      // lỗi của một màn hình mà là thói quen viết mã của cả sản phẩm, và giá trị
      // rời thì không giữ được kỷ luật — chữa từng chỗ chỉ tái phát ở chỗ thứ 152.
      //
      // Sàn của định hướng 00 §2.2 là 16px cho thân bài, "KHÔNG CÓ NGOẠI LỆ cho
      // 'chỗ này chật quá'". Ngoại lệ DUY NHẤT là canvas phả đồ (C-1.3), nơi chữ
      // co giãn theo mức phóng — bốn tên `the-*` bên dưới chỉ dùng ở đó.
      //
      // Cố ý KHÔNG kèm line-height vào thang: `leading-tight`, `leading-snug`,
      // `leading-4` đang có thật trong mã, và một thang mang sẵn line-height sẽ
      // đá nhau với chúng theo thứ tự CSS — im lặng, khó truy.
      fontSize: {
        /** Thân bài. Đây là SÀN, không phải mặc định để co xuống. */
        than: "16px",
        /** Nội dung chính — 00 §2.2 đặt 17–18px cho phần người dùng đến để đọc. */
        dan: "17px",
        /** Đề mục trong trang. */
        de: "18px",

        // ── Chỉ dùng TRONG canvas phả đồ (src/components/tree, src/lib/tree) ──
        /** Tên người trên thẻ nhân khẩu. */
        "the-ten": "13px",
        /** Biểu tượng +/− của nút bung nhánh. */
        "the-nut": "11px",
        /** Đời / chi / năm sinh–mất trên thẻ. */
        "the-phu": "10.5px",
        /** Nhãn huy hiệu trên thẻ. */
        "the-nhan": "9.5px",
      },
      fontFamily: {
        // Be Vietnam Pro has an explicit "vietnamese" subset covering stacked
        // diacritics (ữ, ỹ, ặ, ...). Noto Serif is the heading/traditional
        // face, also with full Vietnamese coverage.
        sans: [
          "var(--font-be-vietnam-pro)",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "Roboto",
          "Helvetica Neue",
          "Arial",
          "sans-serif",
        ],
        serif: [
          "var(--font-noto-serif)",
          "Georgia",
          "Times New Roman",
          "serif",
        ],
      },
      borderRadius: {
        DEFAULT: "8px",
      },
      transitionDuration: {
        // Trỏ vào biến CSS để `prefers-reduced-motion` ép chúng về 0 ở một chỗ
        // duy nhất (globals.css) thay vì rải điều kiện khắp component.
        mau: "var(--thoi-luong-mau)",
        khoi: "var(--thoi-luong-khoi)",
        "lop-phu": "var(--thoi-luong-lop-phu)",
      },
    },
  },
  plugins: [],
};

export default config;
