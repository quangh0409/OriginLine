"use client";

import { PhoneFilled } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import type { ClaimReviewView } from "@/lib/api/membership-admin";

export interface RequesterBlockProps {
  request: ClaimReviewView;
}

/**
 * **Người khai** — tên tự ghi, số điện thoại, vài dòng tự giới thiệu.
 *
 * <h2>Số điện thoại là công cụ, không phải một trường trong bảng</h2>
 * design 07 §1.3 nói rõ cách Trưởng chi đối chiếu: <i>"vài dòng tự giới thiệu —
 * con ông nào, bà nào, quê quán — vì đó mới là thứ Trưởng chi dùng để đối
 * chiếu; số điện thoại chỉ giúp gọi kiểm chứng."</i> Tức việc thật diễn ra
 * **ngoài màn hình**: một cuộc điện thoại.
 *
 * Nên số máy không phải một dòng chữ nhỏ trong danh sách mà là một **nút gọi**
 * — `tel:` mở thẳng ứng dụng điện thoại trên máy của một bác 60 tuổi, và chạm
 * được ở cỡ 44px. Chép tay mười chữ số từ màn hình sang bàn phím là chỗ người
 * ta bỏ cuộc và bấm Duyệt cho xong.
 *
 * <h2>Không có thì không vẽ ô trống</h2>
 * `phone` là `NOT NULL` ở CSDL nên nó luôn có; `introduction` thì không. Máy
 * chủ không gửi bài tự giới thiệu nghĩa là đơn không có — hiện một gạch ngang ở
 * đó vừa vô nghĩa vừa đúng khuôn "ô trống gợi ý có dữ liệu bị giấu" mà cả sản
 * phẩm đang tránh.
 */
export function RequesterBlock({ request }: RequesterBlockProps) {
  const t = useTranslations("membership");
  const phone = request.phone?.trim();
  const intro = request.introduction?.trim();
  const name = request.requesterDisplayName?.trim();

  return (
    <section
      className="mt-3 rounded border p-3"
      style={{ borderColor: colorVars.border, background: colorVars.bgPage }}
    >
      <h4 className="m-0 mb-1 text-than font-medium uppercase tracking-wide text-text-muted">
        {t("card.requester")}
      </h4>

      <p className="m-0 text-dan font-semibold text-text-main">
        {name || t("card.requesterUnnamed")}
      </p>

      {phone && (
        <p className="m-0 mt-2">
          <a
            href={`tel:${phone.replace(/[^\d+]/g, "")}`}
            data-testid="goi-nguoi-khai"
            className="inline-flex min-h-[44px] items-center gap-2 rounded border px-3 text-dan font-semibold no-underline"
            style={{
              borderColor: colorVars.primary,
              color: colorVars.primary,
              background: colorVars.bgCard,
            }}
          >
            <PhoneFilled aria-hidden />
            <span>{phone}</span>
          </a>
        </p>
      )}

      {intro && (
        <div className="mt-2">
          <p className="m-0 text-than font-medium uppercase tracking-wide text-text-muted">
            {t("card.introduction")}
          </p>
          <p className="m-0 whitespace-pre-line text-than leading-snug text-text-main">{intro}</p>
        </div>
      )}

      {typeof request.attemptNo === "number" && request.attemptNo > 1 && (
        <p className="m-0 mt-2 text-than" style={{ color: colorVars.accentText }}>
          {t("card.attemptNo", { n: request.attemptNo })}
        </p>
      )}
    </section>
  );
}
