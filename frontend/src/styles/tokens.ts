/**
 * Single source of truth for the color palette.
 *
 * Values are copied verbatim from the project spec (BA v2 / TDD / plan-giai-doan-1
 * §4 F1) — do not invent new colors here. Both Tailwind (tailwind.config.ts) and
 * Ant Design (src/styles/antd-theme.ts) read from this file so the two UI systems
 * can never visually drift apart.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * `colorTokens` là bảng màu CHẾ ĐỘ SÁNG dưới dạng hex thật.
 *
 * Vì sao vẫn giữ hex thật thay vì `var(--...)`: Ant Design chạy thuật toán sinh
 * dải màu (`generateColorPalettes`) trên `colorPrimary`/`colorError`... để suy ra
 * các bậc hover/active. Thuật toán đó phải PHÂN TÍCH ĐƯỢC màu; đưa vào một chuỗi
 * `var(--x)` thì tinycolor trả về màu không hợp lệ và cả dải hover/active hỏng
 * câm lặng. Nên tầng AntD ăn hex, còn tầng CSS/Tailwind ăn biến CSS.
 *
 * Muốn một giá trị TỰ ĐỔI THEO CHẾ ĐỘ TỐI thì dùng `colorVars` (bên dưới) chứ
 * không dùng `colorTokens` — hex ở đây bị "đóng băng" vào chế độ sáng.
 */
export const colorTokens = {
  primary: "#8c2d19", // do tram - primary brand / CTA
  primaryDark: "#661e0f",
  primaryLight: "#fbeee9", // tint for subtle backgrounds/badges
  bgPage: "#f8f6f2", // nen kem
  bgCard: "#ffffff",
  // Aged-paper tone reserved for the deceased: the living/deceased split is a
  // required visual distinction (BA v2, plan §4 F2/F3), and it must read as
  // reverence rather than as a "disabled" state.
  bgDeceased: "#f2ede2",

  // ── Hổ phách: HAI token, không phải một ────────────────────────────────────
  // `accent` (#d97706) là màu MẢNG TÔ và NÉT DÀY: nền chip, vạch nhấn, thanh hôn
  // phối trên phả đồ. Ở vai trò đó ngưỡng WCAG là 3:1 phi văn bản, và nó vẫn đẹp.
  //
  // Nhưng làm CHỮ thì nó trượt 4,5:1 trên MỌI nền sáng của sản phẩm — cao nhất
  // chỉ 3,19:1 (trên nền trắng tuyệt đối), thấp nhất 2,73:1 (trên nền giấy cũ).
  // Ba badge quan trọng nhất của phả hệ — Đích tôn / Thừa tự / Kế tự — đang là ba
  // chữ khó đọc nhất màn hình. Vì vậy tách riêng `accentText`.
  //
  // Vì sao là #9c5a06 chứ không phải #a55a06 (đề xuất bản nháp đầu): #9c5a06 vượt
  // 4,5:1 ở CẢ BỐN nền sáng (bgCard 5,41 · bgPage 5,02 · warningBg 5,03 ·
  // bgDeceased 4,64 · primaryLight 4,77), còn #a55a06 hụt ở bgDeceased (4,42).
  // Cái giá đã biết và chấp nhận: #9c5a06 ngả nâu đất, mất bớt vẻ "rực" của hương
  // và sơn son thếp vàng mà BA v2 chọn hổ phách vì nó.
  accent: "#d97706", // ho phach - CHỈ dùng cho mảng tô / nét dày
  accentText: "#9c5a06", // hổ phách trầm - dùng khi hổ phách làm CHỮ

  textMain: "#2b2825",
  textMuted: "#5e564d",
  secondary: "#2c3e50",
  border: "#e6dfd5",
  borderDark: "#cdbeaa",
  // Viền Ô NHẬP LIỆU, tách khỏi `border` viền thẻ. WCAG 1.4.11 chỉ bắt buộc 3:1
  // khi viền là thứ DUY NHẤT nhận diện thành phần — đúng trường hợp ô nhập: không
  // có viền thì không biết đâu là chỗ gõ vào. `border` #e6dfd5 trên nền trắng chỉ
  // được 1,32:1, tức gần như vô hình. #8a7a66 cho 4,15:1 (trắng) / 3,85 (nền kem)
  // / 3,56 (giấy cũ). Viền thẻ KHÔNG dùng token này: thẻ đã tự nhận diện được bằng
  // nội dung và lệch nền, đậm hết lên sẽ biến biểu mẫu sáu tầng tên thành một lưới
  // ô vuông nặng nề đúng kiểu "biểu mẫu hành chính" mà tông ấm truyền thống tránh.
  borderInput: "#8a7a66",

  // Status colors kept close to AntD defaults but tuned warmer, used sparingly
  // (e.g. living/deceased distinction badges in later sprints).
  success: "#15803d", // mảng tô: chấm "còn sống", nền badge
  // Cùng lý do như accentText: #15803d làm CHỮ thì trượt trên hai nền nhạt của
  // sản phẩm — 4,30:1 trên giấy cũ và 4,42:1 trên hồng nhạt. #116533 kéo lên
  // 6,13 / 6,31 / 7,16 (nền thẻ).
  successText: "#116533",
  successBg: "#eaf6ee",
  danger: "#b91c1c",
  dangerBg: "#fbeae9",
  warningBg: "#fdf6e7",

  // ── Vòng tiêu điểm bàn phím: hai lớp trái sắc ──────────────────────────────
  // Vòng ĐƠN SẮC không thể đạt 3:1 với mọi nền cùng lúc: hổ phách được 2,95 trên
  // nền kem, 2,73 trên giấy cũ, 2,63 quanh nút đỏ trầm — trượt cả ba.
  // Lời giải là hai lớp trái sắc nhau, để LUÔN có một lớp đạt ≥3:1 bất kể nền:
  //   lõi #2b2825  → 13,58 trên nền kem · 12,55 trên giấy cũ · 1,75 trên nút đỏ
  //   quầng #ffffff → 1,08 trên nền kem · nhưng 8,38 trên nút đỏ
  // Trên nút đỏ lõi biến mất thì quầng gánh, trên nền sáng thì ngược lại.
  focusRing: "#2b2825",
  focusHalo: "#ffffff",
} as const;

/**
 * Bảng màu CHẾ ĐỘ TỐI. Chép nguyên từ khối `prefers-color-scheme: dark` trong
 * `design/assets/design-doc.css` — tức bộ màu tối mà chính tài liệu thiết kế
 * đang chạy — để giữ nguyên tắc "một nguồn duy nhất".
 *
 * Điểm đáng chú ý: ở chế độ tối hổ phách HẾT trượt (#e9a83f đạt 7,87–8,67:1),
 * nên `accentText` ánh xạ thẳng về `accent`, không cần token thứ ba. `successText`
 * cũng vậy. Vấn đề tương phản của hổ phách chỉ tồn tại ở chế độ sáng.
 *
 * Vòng tiêu điểm ĐẢO hai lớp: lõi sáng, quầng tối — cùng logic "luôn có một lớp
 * đạt 3:1", chỉ đổi chiều.
 */
export const darkColorTokens = {
  primary: "#e08a72",
  primaryDark: "#f0a68f", // sáng HƠN primary: hover phải đậm nét lên, mà ở nền tối "đậm" nghĩa là sáng
  primaryLight: "#33211c",
  bgPage: "#1a1614",
  bgCard: "#241f1c",
  bgDeceased: "#2b2521",
  accent: "#e9a83f",
  accentText: "#e9a83f",
  textMain: "#efe8df",
  textMuted: "#a99c8e",
  secondary: "#8ba9c4",
  border: "#3b312b",
  borderDark: "#56483f",
  // Không có trong design-doc.css (tài liệu chưa có ô nhập). Chọn để đạt cùng
  // ngưỡng 3:1 như bản sáng: 3,83 trên nền thẻ · 4,22 trên nền trang · 3,55 trên
  // giấy cũ.
  borderInput: "#8b7767",
  success: "#74c68f",
  successText: "#74c68f",
  successBg: "#1c2e22",
  danger: "#ec8a80",
  dangerBg: "#33201e",
  warningBg: "#2e2617",
  focusRing: "#efe8df",
  focusHalo: "#1a1614",
} as const satisfies Record<ColorTokenName, string>;

/**
 * Cùng bộ tên, nhưng trỏ vào biến CSS thay vì hex.
 *
 * DÙNG CÁI NÀY trong `style={{ ... }}` và trong mọi chỗ CSS. Giá trị tự đảo khi
 * chuyển chế độ tối mà không phải sửa một dòng component nào — vì phép thay thế
 * `var()` được giải ở thời điểm dùng, trên từng phần tử, chứ không phải lúc biên
 * dịch.
 *
 * `colorTokens.primary` thì ngược lại: nó là chuỗi "#8c2d19" đã đóng băng vào
 * lượt render, chế độ tối không với tới được. Mọi component đang viết
 * `style={{ color: colorTokens.primary }}` sẽ giữ nguyên màu sáng trên nền tối.
 */
export const colorVars = {
  primary: "var(--color-primary)",
  primaryDark: "var(--color-primary-dark)",
  primaryLight: "var(--color-primary-light)",
  bgPage: "var(--color-bg-page)",
  bgCard: "var(--color-bg-card)",
  bgDeceased: "var(--color-bg-deceased)",
  accent: "var(--color-accent)",
  accentText: "var(--color-accent-text)",
  textMain: "var(--color-text-main)",
  textMuted: "var(--color-text-muted)",
  secondary: "var(--color-secondary)",
  border: "var(--color-border)",
  borderDark: "var(--color-border-dark)",
  borderInput: "var(--color-border-input)",
  success: "var(--color-success)",
  successText: "var(--color-success-text)",
  successBg: "var(--color-success-bg)",
  danger: "var(--color-danger)",
  dangerBg: "var(--color-danger-bg)",
  warningBg: "var(--color-warning-bg)",
  focusRing: "var(--color-focus-ring)",
  focusHalo: "var(--color-focus-halo)",
} as const satisfies Record<ColorTokenName, string>;

export type ColorTokenName = keyof typeof colorTokens;

/**
 * Tên biến CSS kênh RGB tương ứng, ví dụ `--rgb-primary: 140 45 25`.
 *
 * Tailwind cần dạng kênh chứ không dạng hex để bổ trợ độ mờ (`bg-bg-page/95`,
 * `border-primary/40`, `border-primary/50` — cả ba đang có thật trong mã) hoạt
 * động: nó sinh ra `rgb(var(--rgb-x) / 0.95)`. Đưa thẳng `var(--color-x)` vào
 * Tailwind sẽ làm mọi hậu tố `/nn` im lặng không có tác dụng.
 */
export function rgbVar(name: ColorTokenName): string {
  // camelCase → kebab-case, khớp tên biến khai trong globals.css
  const kebab = name.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
  return `--rgb-${kebab}`;
}

/** `rgb(var(--rgb-x) / <alpha-value>)` — dạng Tailwind cần cho một scale màu. */
export function tailwindColor(name: ColorTokenName): string {
  return `rgb(var(${rgbVar(name)}) / <alpha-value>)`;
}

/** "#8c2d19" → "140 45 25". Dùng để sinh/đối chiếu khối biến trong globals.css. */
export function hexToRgbChannels(hex: string): string {
  const h = hex.replace("#", "");
  const full =
    h.length === 3
      ? h
          .split("")
          .map((c) => c + c)
          .join("")
      : h;
  return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16)).join(" ");
}
