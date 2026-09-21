"use client";

import { useTranslations } from "next-intl";
import { ApartmentOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import { ClaimLink } from "./claim-chrome";
import type { ClaimTarget } from "./use-claims";

/**
 * Ô trên phả đồ mà người dùng vừa chọn — **để họ soi lại trước khi gõ**.
 *
 * <h2>Vì sao phải có, dù người dùng vừa tự tay bấm vào nó</h2>
 * Giữa lúc bấm và lúc gửi đơn có một lần chuyển trang. Trên điện thoại, một cú
 * chạm trượt sang ô bên cạnh là chuyện thường, và trong một dòng họ thì ô bên
 * cạnh rất hay <em>trùng tên</em>. Chiếu lại tên + đời + chi là cách rẻ nhất để
 * bắt cú trượt ấy trước khi nó thành một lá đơn vô nghĩa nằm trên bàn Trưởng
 * chi — cùng khuôn mẫu với hộp thoại xác nhận ngày mất mà tài liệu 00 §2.4 gọi
 * là mẫu chuẩn.
 *
 * <h2>Ba trường, và không thêm trường thứ tư</h2>
 * Tên · đời · chi. Đúng mức mà người chưa được duyệt được thấy trên phả đồ
 * (quyết định §8.2: "tên, đời, quan hệ"). Thêm năm sinh hay nghề nghiệp là mở
 * một cửa sổ Tầng 2 trên một màn mà ai cầm mã mời dòng họ cũng mở được — và
 * những trường ấy <b>vốn đã bị máy chủ lọc</b> với người gọi này, nên vẽ chỗ
 * cho chúng chỉ tạo ra ô trống gợi ý rằng có dữ liệu bị giấu.
 *
 * <h2>Tên người là DỮ LIỆU, không phải nhãn giao diện</h2>
 * Không viết hoa toàn bộ, không cắt bằng dấu ba chấm (tài liệu 00 §3). Dấu
 * tiếng Việt chồng tầng nên chữ phải đủ đậm và đủ to — ở đây là bộ chữ có chân,
 * 18px, in đậm.
 */
export interface ClaimTargetCardProps {
  readonly target: ClaimTarget;
  /** Ẩn lối "chọn ô khác" khi màn đã có lối ra riêng ngay bên dưới. */
  readonly hideBackLink?: boolean;
}

export function ClaimTargetCard({ target, hideBackLink }: ClaimTargetCardProps) {
  const t = useTranslations("claim");

  // Hai mảnh ghép rời nhau chứ không phải một chuỗi có chỗ trống: thiếu chi thì
  // câu phải đọc trôi chảy, không ra "· Đời 5".
  const manh = [
    target.generation != null ? t("target.generation", { n: target.generation }) : null,
    target.branchName,
  ].filter((m): m is string => m !== null && m.length > 0);

  return (
    <section
      data-claim-target={target.id}
      aria-labelledby="claim-target-heading"
      className="rounded-lg border px-4 py-5 sm:px-6"
      style={{ background: colorVars.bgCard, borderColor: colorVars.borderDark }}
    >
      <h2 id="claim-target-heading" className="m-0 text-than font-medium text-text-muted">
        {t("target.heading")}
      </h2>

      <p className="m-0 mt-2 font-serif text-de font-bold text-text-main">
        {target.displayName}
      </p>

      {manh.length > 0 && (
        <p className="m-0 mt-1 text-than text-text-muted">{manh.join(" · ")}</p>
      )}

      <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
        {t("target.confirm")}
      </p>

      {!hideBackLink && (
        <div className="mt-4">
          <ClaimLink href="/tree" icon={<ApartmentOutlined />}>
            {t("target.back")}
          </ClaimLink>
        </div>
      )}
    </section>
  );
}
