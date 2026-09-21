"use client";

import type { ReactNode } from "react";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";

/**
 * Ba mảnh giao diện dùng lại trong cả ba màn của luồng "tôi là ai trong phả".
 *
 * <h2>Vì sao gom vào một tệp thay vì chép tay ba lần</h2>
 * Ba màn này chia nhau đúng một sàn tiếp cận — chữ 16px, vùng chạm 44×44px,
 * biểu tượng luôn kèm chữ — và một sàn sống bằng cách chép tay thì nó không
 * phải sàn: nó là ba bản sao sẽ lệch nhau, mà lệch rồi thì không có gì bắt
 * được. Sửa ở đây là sửa cho cả luồng.
 *
 * <h2>Màu qua `colorVars`, không bao giờ hex</h2>
 * `colorVars` trỏ vào biến CSS nên tự đảo khi người dùng chuyển chế độ tối.
 * `colorTokens` thì đã đóng băng thành chuỗi `"#…"` vào lượt dựng và chế độ tối
 * không với tới được — đó là cái bẫy mà `styles/tokens.ts` ghi riêng một đoạn
 * để cảnh báo.
 */

/**
 * Sắc thái của một khối thông báo.
 *
 * <b>Không có sắc đỏ cho một luật đang chạy đúng.</b> Nguyên tắc 4 của tài liệu
 * 00: người có thẩm quyền cao nhất cũng là người sợ làm hỏng nhất, và tô đỏ một
 * quy trình bình thường là cách nhanh nhất để họ tưởng mình vừa làm hỏng thứ
 * gì. "Ô này đã có người nhận", "ông/bà đã gửi đơn rồi", "người này đã khuất" —
 * cả ba là hệ thống đang làm đúng việc của nó, nên chúng mang sắc hổ phách.
 * Sắc đỏ để dành cho hỏng thật: mất đường truyền, máy chủ lỗi.
 */
export type SacThai = "trungtinh" | "hophach" | "hongthat" | "xong";

const NEN: Record<SacThai, string> = {
  trungtinh: colorVars.bgCard,
  hophach: colorVars.warningBg,
  hongthat: colorVars.dangerBg,
  xong: colorVars.successBg,
};

export interface ClaimNoticeProps {
  readonly tone?: SacThai;
  readonly title: string;
  /** Biểu tượng **đi kèm** tiêu đề chữ, không bao giờ thay cho nó (00 §2.3). */
  readonly icon?: ReactNode;
  readonly children?: ReactNode;
  /** `<h1>` cho khối chủ đề của trang, `<h2>` cho khối phụ. */
  readonly as?: "h1" | "h2";
  readonly titleId?: string;
  /**
   * Cho {@code role="status"} khi khối này là **câu trả lời cho một thao tác
   * vừa xảy ra** — trình đọc màn hình đọc nó lên mà không cướp tiêu điểm. Khối
   * chỉ để bày thông tin thì không cần, và gắn bừa sẽ làm mọi thứ trên trang
   * cùng đòi được đọc.
   */
  readonly live?: boolean;
  readonly dataState?: string;
}

export function ClaimNotice({
  tone = "trungtinh",
  title,
  icon,
  children,
  as = "h2",
  titleId,
  live,
  dataState,
}: ClaimNoticeProps) {
  const Heading = as;
  return (
    <section
      data-claim-state={dataState}
      {...(live ? { role: "status" as const } : {})}
      aria-labelledby={titleId}
      className="rounded-lg border px-4 py-5 sm:px-6"
      style={{ background: NEN[tone], borderColor: colorVars.borderDark }}
    >
      <Heading
        id={titleId}
        className="m-0 flex items-start gap-2 font-serif text-de font-bold text-text-main"
      >
        {icon ? (
          <span aria-hidden className="mt-0.5 shrink-0" style={{ color: colorVars.accentText }}>
            {icon}
          </span>
        ) : null}
        <span>{title}</span>
      </Heading>
      {children ? <div className="mt-3 space-y-3">{children}</div> : null}
    </section>
  );
}

/** Một đoạn văn trong khối — 17px, bề rộng đọc có trần, giãn dòng rộng. */
export function ClaimParagraph({ children }: { children: ReactNode }) {
  return (
    <p className="m-0 max-w-prose text-dan leading-relaxed text-text-main">{children}</p>
  );
}

/**
 * Lớp dùng chung của mọi điều khiển mang hành động trong luồng này.
 *
 * {@code min-h-[44px]} là **sàn WCAG 2.2**, không phải một lựa chọn thẩm mỹ, và
 * dự án đã có tiền lệ đau thương: phả đồ từng mở ở mức phóng 0.21 khiến nút
 * bung nhánh còn 5px. {@code px-4} giữ bề ngang cũng đủ rộng cho một ngón tay
 * chứ không chỉ chiều cao.
 */
const SAN_CHAM = "inline-flex min-h-[44px] items-center justify-center gap-2 rounded-lg px-4 text-than font-semibold";

function kieu(bac: "chinh" | "phu") {
  return bac === "chinh"
    ? { background: colorVars.primary, color: colorVars.bgCard, borderColor: colorVars.primary }
    : { background: colorVars.bgCard, color: colorVars.primary, borderColor: colorVars.primary };
}

export interface ClaimLinkProps {
  readonly href: string;
  readonly bac?: "chinh" | "phu";
  readonly icon?: ReactNode;
  readonly children: ReactNode;
}

/** Liên kết trông như nút. Biểu tượng chỉ để nhận diện nhanh, chữ luôn có. */
export function ClaimLink({ href, bac = "phu", icon, children }: ClaimLinkProps) {
  return (
    <Link href={href} className={`${SAN_CHAM} border no-underline`} style={kieu(bac)}>
      {icon ? (
        <span aria-hidden className="shrink-0">
          {icon}
        </span>
      ) : null}
      <span>{children}</span>
    </Link>
  );
}

export interface ClaimButtonProps {
  /** Bỏ trống khi {@code type="submit"} — lúc ấy biểu mẫu mới là nơi xử lý. */
  readonly onClick?: () => void;
  readonly bac?: "chinh" | "phu";
  readonly icon?: ReactNode;
  readonly disabled?: boolean;
  readonly type?: "button" | "submit";
  readonly children: ReactNode;
}

export function ClaimButton({
  onClick,
  bac = "chinh",
  icon,
  disabled,
  type = "button",
  children,
}: ClaimButtonProps) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`${SAN_CHAM} border disabled:opacity-60`}
      style={kieu(bac)}
    >
      {icon ? (
        <span aria-hidden className="shrink-0">
          {icon}
        </span>
      ) : null}
      <span>{children}</span>
    </button>
  );
}
