"use client";

import { useTranslations } from "next-intl";
import { SafetyCertificateOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";

/**
 * "Chưa có gì được ghi vào phả."
 *
 * <h2>Vì sao câu này chiếm chỗ trên màn hình thay vì nằm trong một tooltip</h2>
 * Người nhập là Trưởng chi 45–65 tuổi, không phải người nhập liệu chuyên
 * nghiệp, và nỗi sợ lớn nhất của họ không phải là gõ chậm mà là **làm hỏng
 * phả**. Nỗi sợ ấy quyết định hành vi: người sợ thì bấm ít, đọc kỹ tới mức
 * không dám bấm gì, và cuối cùng gọi điện hỏi người khác thay vì tự làm.
 *
 * Bất biến của hệ thống là chưa ghi gì cho tới khi bấm duyệt (plan 02 §1) — một
 * bất biến rất mạnh, và hoàn toàn **vô hình** nếu không ai nói ra. Một dòng chữ
 * thường trực đổi hẳn cách người ta dùng màn hình này: họ dám tải lên lần thứ
 * ba, thứ tư, và đó chính là cách một tệp bẩn trở thành một tệp sạch.
 *
 * Vì vậy nó **không đóng được**. Một lời trấn an mà người dùng lỡ tay tắt mất ở
 * buổi đầu thì đến buổi thứ sáu họ không còn nhớ nữa.
 */
export function NotWrittenYetBanner() {
  const t = useTranslations("dataImport.reconcile.notWritten");

  return (
    <section
      className="rounded-lg border px-4 py-3"
      style={{
        borderColor: colorVars.successText,
        backgroundColor: colorVars.successBg,
      }}
      // `status` chứ không phải `alert`: đây là thông tin trấn an thường trực,
      // không phải một sự kiện cần cắt ngang người đang dùng trình đọc màn hình.
      role="status"
    >
      <h2
        className="m-0 flex items-center gap-2 font-serif text-de font-bold"
        style={{ color: colorVars.successText }}
      >
        <SafetyCertificateOutlined aria-hidden />
        {t("title")}
      </h2>
      <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
        {t("body")}
      </p>
    </section>
  );
}
