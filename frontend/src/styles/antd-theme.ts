import { theme, type ThemeConfig } from "antd";
import { colorTokens, darkColorTokens } from "./tokens";

/**
 * Ant Design design tokens, derived from the same palette as Tailwind
 * (src/styles/tokens.ts). Passed to <ConfigProvider theme={antdTheme}> in
 * src/app/providers.tsx.
 *
 * Lưu ý tầng này ăn HEX chứ không ăn `var(--...)` như Tailwind: Ant Design chạy
 * thuật toán sinh dải màu trên `colorPrimary`/`colorError`... để suy ra các bậc
 * hover/active. Thuật toán đó phải phân tích được màu; đưa vào chuỗi `var(--x)`
 * thì màu trở thành không hợp lệ và cả dải hover/active hỏng câm lặng. Vì vậy
 * chế độ tối ở tầng AntD phải đi bằng một ThemeConfig riêng (`antdThemeDark`),
 * không đi bằng biến CSS.
 */

/**
 * Sàn kích thước của 00 §2.2, áp cho toàn bộ lớp widget.
 *
 * ĐO THẬT trước khi sửa (getComputedStyle trên nút thật, jsdom + antd 5.29.3,
 * xem tests/unit/styles/antd-metrics.test.tsx):
 *   Button mặc định  14px / cao 32px   → 73% ngưỡng chạm 44px
 *   Button size=small 14px / cao 24px  → 55%
 *   Button size=large 16px / cao 40px  → 91%
 *   Input            14px / line-height 1,5714 / padding 4px 11px
 * Theme dự án TRƯỚC đợt sửa này cho ra số y hệt — đúng như dự đoán, vì nó không
 * khai fontSize cũng không khai controlHeight nên chạy theo hạt giống mặc định
 * của thư viện.
 *
 * `controlHeightSM: 44` là cố ý, KHÔNG để AntD tự suy. Mọi `size="small"` đang
 * có trong mã (ví dụ nút Sửa ở person-profile-header.tsx) sẽ ra 24px — dưới cả
 * ngưỡng tối thiểu 24px của WCAG 2.5.8, chứ chưa nói 44px.
 *
 * CÁI GIÁ, đã biết trước: mọi màn hình dày đặc sẽ giãn ra một lượt (nút cao thêm
 * 12px, nút nhỏ cao thêm 20px). Đó là điều chỉnh bố cục ở tầng component, không
 * phải lỗi của tệp này — xem phần báo cáo.
 */
const SAN_CHU = 16;
const SAN_CHAM = 44;

const tokenChung = {
  fontFamily:
    'var(--font-be-vietnam-pro), -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif',
  fontSize: SAN_CHU,
  // AntD suy `fontSizeSM = fontSize − 2` nếu không khai. Đo trên trình duyệt:
  // <Tag> "Sắp ra mắt" 14px, số trên huy hiệu chuông 14px — cả hai dưới sàn 16px
  // của 00 §2.2, và cả hai đều là chữ MANG NGHĨA chứ không phải trang trí. Đặt
  // bằng sàn: thang cỡ chữ của sản phẩm không có nấc nào dưới 16px ngoài canvas.
  fontSizeSM: SAN_CHU,
  // 1,5 thay cho mặc định 1,5714: ở 16px cho ra đúng 24px chẵn, và vẫn ≥1,45 —
  // ngưỡng dưới của dấu tiếng Việt chồng tầng (ữ, ỹ, ặ) để dấu không chạm dòng trên.
  lineHeight: 1.5,
  controlHeight: SAN_CHAM,
  controlHeightSM: SAN_CHAM,
  borderRadius: 8,
  wireframe: false,
} as const;

export const antdTheme: ThemeConfig = {
  token: {
    ...tokenChung,
    colorPrimary: colorTokens.primary,
    colorLink: colorTokens.primary,
    // TRƯỚC: colorLinkHover = accent (#d97706) = 2,95:1 trên nền kem.
    // Liên kết ở trạng thái nghỉ đạt 7,77:1 (AAA), vừa đưa chuột vào là tụt
    // xuống dưới cả ngưỡng AA — khó đọc đi 2,63 lần. Trạng thái hover hiếm khi
    // được kiểm thử tiếp cận nên loại lỗi này sống rất lâu, và nó hại đúng nhóm
    // người dùng duy nhất gặp hover: người lão thị dùng chuột trên máy tính bảng
    // hoặc màn hình lớn (00 §1).
    // SAU: primaryDark = 11,07:1 nền kem / 11,95:1 nền thẻ. Di chuột làm liên kết
    // ĐẬM LÊN, đúng hướng trực giác. Cái giá: mất hiệu ứng "ấm lên thành hổ phách"
    // khi rê chuột — một mất mát thẩm mỹ có thật.
    colorLinkHover: colorTokens.primaryDark,
    colorInfo: colorTokens.secondary,
    colorSuccess: colorTokens.success,
    colorError: colorTokens.danger,
    // colorWarning điều khiển CHỮ của Alert/Tag/Message cảnh báo, không chỉ mảng
    // tô — nên phải là bản trầm (5,02–5,41:1), không phải hổ phách rực (2,73–3,19).
    colorWarning: colorTokens.accentText,
    colorBgLayout: colorTokens.bgPage,
    colorBgContainer: colorTokens.bgCard,
    // Hai token viền của AntD KHÔNG cùng vai trò, và việc gán cả hai bằng một
    // giá trị (như trước) chính là thứ xoá mất sự phân biệt đó. Đọc từ mã nguồn
    // antd 5.29.3: Input và Button dùng `colorBorder`; Card và Table dùng
    // `colorBorderSecondary`.
    //
    // `colorBorder` → viền ĐIỀU KHIỂN. WCAG 1.4.11 bắt buộc 3:1 ở đây vì viền là
    //   thứ DUY NHẤT cho biết "chỗ này gõ vào được" / "đây là một cái nút".
    //   #e6dfd5 trên nền trắng chỉ 1,32:1 — gần như vô hình. #8a7a66 cho 4,15:1.
    // `colorBorderSecondary` → viền THẺ và đường phân cách. Được miễn ngưỡng:
    //   thẻ đã tự nhận diện bằng nội dung và lệch nền. Giữ #e6dfd5, vì đậm hết
    //   lên sẽ biến biểu mẫu sáu tầng tên (HUY/TU/HIEU/THUY/THUONG_GOI/PHAP_DANH)
    //   thành một lưới ô vuông nặng nề — đúng cái "trông như biểu mẫu hành chính"
    //   mà tông ấm truyền thống muốn tránh.
    colorBorder: colorTokens.borderInput,
    colorBorderSecondary: colorTokens.border,
    colorText: colorTokens.textMain,
    colorTextSecondary: colorTokens.textMuted,
  },
  components: {
    Layout: {
      headerBg: colorTokens.bgCard,
      bodyBg: colorTokens.bgPage,
      footerBg: colorTokens.bgCard,
    },
    // KHÔNG khai `Button: { colorPrimary, algorithm: true }` nữa.
    //
    // Khối đó là vô nghĩa về màu — `colorPrimary` của nó trùng đúng token toàn
    // cục — nhưng `algorithm: true` thì KHÔNG vô hại: nó bắt AntD chạy LẠI thuật
    // toán phái sinh riêng cho Button, và lượt chạy lại đó suy `controlHeightSM`
    // = controlHeight × 0,75 = 33px, đè mất giá trị 44 ta khai ở token. Đo thật:
    // token toàn cục báo controlHeightSM = 44, nhưng nút size="small" vẫn ra
    // 33px. Một lớp ghi đè không màu nào thay đổi mà lại lặng lẽ phá sàn vùng
    // chạm — xem tests/unit/styles/antd-metrics.test.tsx.
    Menu: {
      itemSelectedColor: colorTokens.primary,
      itemSelectedBg: colorTokens.primaryLight,
    },
    Badge: {
      // Badge là chữ trắng trên nền đặc: trên hổ phách rực chỉ 3,19:1, trên bản
      // trầm 5,41:1.
      colorError: colorTokens.accentText,
      // MỰC của con số. AntD lấy nó từ `colorTextLightSolid`, mặc định là TRẮNG
      // cứng — không đảo theo chế độ. Ở chế độ tối hổ phách sáng lên (#e9a83f) và
      // chữ trắng tụt xuống 2,07:1, đo được trên phả đồ. Dùng `bgPage` để một khai
      // báo đúng ở cả hai chiều: nền kem trên mảng trầm khi sáng, mực tối trên mảng
      // sáng khi tối.
      colorTextLightSolid: colorTokens.bgPage,
    },
    // Divider dùng `colorSplit` (suy từ colorBorderSecondary) nên không phải
    // kéo về bằng tay; Card/Table cũng vậy. Xem ghi chú ở token.colorBorder.
  },
};

/**
 * Cấu hình chế độ tối cho Ant Design.
 *
 * Cần `theme.darkAlgorithm` chứ không chỉ đảo bảng màu: thuật toán tối là thứ
 * quyết định cách AntD suy ra hàng chục màu phái sinh nó không cho ghi đè trực
 * tiếp (nền mờ, viền chia, màu chữ bị vô hiệu hoá, bóng đổ). Chỉ đảo mấy token
 * mặt ngoài sẽ để lại một loạt màu sáng lọt giữa nền tối.
 *
 * TỆP NÀY KHÔNG TỰ ÁP DỤNG ĐƯỢC. `providers.tsx` phải chọn giữa `antdTheme` và
 * `antdThemeDark` theo chế độ đang hiệu lực — xem phần báo cáo. Chừng nào chưa
 * nối vào đó, tầng Tailwind/CSS đã tối còn widget AntD vẫn sáng.
 */
export const antdThemeDark: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    ...tokenChung,
    colorPrimary: darkColorTokens.primary,
    colorLink: darkColorTokens.primary,
    // Ở nền tối, "đậm lên" nghĩa là SÁNG lên: #f0a68f cho 9,03:1 trên nền trang
    // so với 6,89:1 của trạng thái nghỉ. Vẫn đúng hướng "hover làm rõ hơn".
    colorLinkHover: darkColorTokens.primaryDark,
    colorInfo: darkColorTokens.secondary,
    colorSuccess: darkColorTokens.success,
    colorError: darkColorTokens.danger,
    // Ở chế độ tối hổ phách hết trượt (7,87–8,67:1) nên dùng thẳng accent.
    colorWarning: darkColorTokens.accentText,
    colorBgLayout: darkColorTokens.bgPage,
    colorBgContainer: darkColorTokens.bgCard,
    colorBorder: darkColorTokens.borderInput,
    colorBorderSecondary: darkColorTokens.border,
    colorText: darkColorTokens.textMain,
    colorTextSecondary: darkColorTokens.textMuted,
  },
  components: {
    Layout: {
      headerBg: darkColorTokens.bgCard,
      bodyBg: darkColorTokens.bgPage,
      footerBg: darkColorTokens.bgCard,
    },
    Menu: {
      itemSelectedColor: darkColorTokens.primary,
      itemSelectedBg: darkColorTokens.primaryLight,
    },
    Badge: {
      colorError: darkColorTokens.accentText,
      colorTextLightSolid: darkColorTokens.bgPage,
    },
  },
};
