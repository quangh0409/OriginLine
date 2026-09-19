"use client";

import { PhoneOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";

/**
 * **Chỗ để đi tiếp** khi một mã mời không mở được.
 *
 * <h2>Vì sao ở đây KHÔNG in tên và số máy của người mời</h2>
 * Bản thiết kế 06 §7 vẽ ba màn lỗi có in đích danh "Ông Nguyễn Văn Bốn · Trưởng
 * chi Giáp · 0903 111 222", và ghi rõ cái giá: giao diện phải biết "trưởng chi
 * phụ trách của tôi là ai", một dữ kiện <b>chưa có trong hợp đồng API</b>.
 *
 * Ở màn lời mời thì ngoài cái giá ấy còn một cái giá thứ hai, nặng hơn: mã mời
 * hỏng vẫn <b>có thể đang nằm trong tay người lạ</b> — đó chính là ba ca mã hết
 * hạn / đã dùng / sai. Gắn tên và số điện thoại của một người thật vào phản hồi
 * lỗi là biến bộ dò mã thành một máy thu danh bạ trưởng chi: thử mã bừa, đọc số
 * máy. Vì vậy phản hồi lỗi của {@code /api/v1/invitations/**} cố ý <b>không
 * chở</b> thông tin người mời, và khối này nói bằng con đường đã có sẵn trong
 * tay người nhận thật: số máy in ở cuối tin nhắn và dưới phiếu mời giấy.
 *
 * Đây là lối đi, không phải lời xin lỗi — 06 §7.2 luật 5: "mọi màn lỗi có ít
 * nhất một nút đi tiếp được".
 */
export function ContactInviterNotice() {
  const t = useTranslations("auth.invitation");

  return (
    <section
      data-invitation-contact="fallback"
      aria-labelledby="invitation-contact-title"
      className="rounded-lg border px-4 py-4 sm:px-5"
      style={{ background: colorVars.bgCard, borderColor: colorVars.border }}
    >
      <h3
        id="invitation-contact-title"
        className="m-0 flex items-center gap-2 text-than font-semibold text-text-main"
      >
        <PhoneOutlined aria-hidden style={{ color: colorVars.accentText }} />
        {t("contactFallbackTitle")}
      </h3>
      <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
        {t("contactFallbackBody")}
      </p>
    </section>
  );
}
