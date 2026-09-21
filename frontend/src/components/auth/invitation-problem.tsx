"use client";

import { Button } from "antd";
import { LoginOutlined, ReloadOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import type { InvitationFailure } from "@/lib/api/invitation";
import { useAuth } from "@/lib/auth/auth-context";
import { colorVars } from "@/styles/tokens";
import { ContactInviterNotice } from "./contact-inviter-notice";
import { GuestModeNotice } from "./guest-mode-notice";

export interface InvitationProblemProps {
  readonly failure: InvitationFailure;
  /** Chỉ dùng cho ca đường truyền — ba ca mã hỏng thì bấm lại vô ích. */
  readonly onRetry?: () => void;
  readonly retrying?: boolean;
}

/**
 * Ba ca mã mời hỏng — **hết hạn · đã dùng · sai mã** — cộng hai ca phụ (đã bị
 * huỷ, bị chặn tần suất) và một ca không phải lỗi của mã (mất đường truyền).
 *
 * <h2>Cả ba ca chính đều là nghiệp vụ BÌNH THƯỜNG</h2>
 * Một mã hết hạn sau bảy ngày là hệ thống <i>đang chạy đúng</i>, không phải
 * hỏng. Vì vậy khối này dùng nền hổ phách chứ không phải dải đỏ — 06 §7.2 luật
 * 4: "không dùng màu đỏ lỗi cho luật chạy đúng". Màu đỏ ở đây dạy sai người
 * dùng rằng họ vừa làm hỏng thứ gì đó, và người sợ làm hỏng nhất trong dòng họ
 * lại chính là người ta cần nhất (00 §2.4).
 *
 * <h2>Ba mảnh câu chữ, không phải một</h2>
 * {@code title} nói <b>chuyện gì</b>, {@code body} nói <b>vì sao</b> — luôn là
 * một lý do người thường hiểu được, không phải mã lỗi — và {@code next} nói
 * <b>làm gì tiếp</b>. Thiếu mảnh thứ ba thì đây là "một cái ngõ cụt có màu"
 * (06 §7.2 luật 5). Không mảnh nào in mã lỗi kỹ thuật.
 *
 * <h2>`UNAVAILABLE` tách hẳn ra, và đó là điểm dễ làm sai nhất</h2>
 * Mất mạng hay máy chủ 500 <b>không</b> được đọc thành "mã của ông/bà sai":
 * người vừa bị bảo là mã sai sẽ đi xin mã mới, và mã mới cũng sẽ không mở được.
 * Chỉ riêng ca ấy có nút "Thử lại"; với năm ca kia, bấm lại chỉ là lặp lại cùng
 * một câu trả lời.
 *
 * <h2>Ba ca của riêng bước "nhận lời mời"</h2>
 * {@code NEEDS_ACCOUNT} · {@code ACCOUNT_ALREADY_LINKED} ·
 * {@code PERSON_ALREADY_LINKED} chỉ xảy ra <b>sau</b> khi người dùng đã bấm
 * "Đúng là tôi", nên chúng đi qua đúng khuôn này chứ không qua một thành phần
 * thứ hai: cùng ba mảnh câu chữ, cùng một lối đi tiếp, cùng một bộ mốc kiểm.
 *
 * {@code NEEDS_ACCOUNT} là ca <b>không phải lỗi của ai cả</b> — hôm nay
 * {@code POST /invitations/accept} còn đòi token vì backend chưa phát được tài
 * khoản Keycloak. Nó là ca duy nhất trong nhóm có một nút bấm làm được việc
 * ngay ("Đăng nhập"); hai ca kia phải đi qua một con người.
 *
 * <h2>Chế độ khách chỉ hiện ở ca "mã sai"</h2>
 * Đúng cửa 3 của 06 §5.4: người lạ mò vào cần được chỉ đường tới phần công
 * khai thay vì bị chặn bằng một bức tường. Ở ca hết hạn / đã dùng thì người
 * đứng trước màn hình gần như chắc chắn là người được mời thật, và việc cần làm
 * của họ là gọi một cuộc điện thoại — chen thêm một khối "xem phần công khai"
 * vào đó là làm loãng đúng việc cần làm.
 */
export function InvitationProblem({ failure, onRetry, retrying }: InvitationProblemProps) {
  const t = useTranslations("auth.invitation");
  const tAuth = useTranslations("auth");
  const { isAuthenticated, login } = useAuth();
  const canRetry = failure === "UNAVAILABLE" && typeof onRetry === "function";
  // Chỉ mời đăng nhập khi người này thật sự chưa đăng nhập. Một người ĐÃ đăng
  // nhập mà vẫn nhận 401 đang gặp chuyện khác (phiên chết, đồng hồ lệch), và
  // một nút "Đăng nhập" ở đó là một vòng tròn.
  //
  // `IDENTITY_TAKEN` dùng CHUNG nút ấy, và đó là cả điểm của nhánh này: định
  // danh người dùng tự khai đã có chủ, nên lối đi tiếp duy nhất đúng là vào
  // bằng chính tài khoản ấy rồi mở lại liên kết mời — nhánh "đã có token" lấy
  // danh tính từ Keycloak nên không đi qua phép chặn. TUYỆT ĐỐI không thêm nút
  // "Thử lại" cho nhánh này: máy chủ tính mỗi lần từ chối vào giới hạn tần suất
  // của chính người dùng ngay tình.
  const canLogin =
    (failure === "NEEDS_ACCOUNT" || failure === "IDENTITY_TAKEN") && !isAuthenticated;

  return (
    <div className="space-y-4">
      <section
        // Mốc kiểm được cho cả vitest lẫn Playwright, và nó mang ĐÚNG nhánh —
        // để một ca kiểm không thể xanh nhờ nhận nhầm màn khác.
        data-invitation-state={failure}
        role="status"
        aria-labelledby="invitation-problem-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
      >
        <h1
          id="invitation-problem-title"
          className="m-0 font-serif text-de font-bold text-text-main"
        >
          {t(`problem.${failure}.title`)}
        </h1>
        <p
          data-problem-copy="body"
          className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main"
        >
          {t(`problem.${failure}.body`)}
        </p>
        <p
          data-problem-copy="next"
          className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main"
        >
          {t(`problem.${failure}.next`)}
        </p>

        {(canRetry || canLogin) && (
          <div className="mt-4 flex flex-wrap items-center gap-3">
            {canRetry && (
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                loading={retrying}
                onClick={onRetry}
              >
                {t("retry")}
              </Button>
            )}
            {canLogin && (
              <Button type="primary" icon={<LoginOutlined />} onClick={login}>
                {tAuth("login")}
              </Button>
            )}
          </div>
        )}
      </section>

      <ContactInviterNotice />

      {failure === "NOT_FOUND" && <GuestModeNotice />}
    </div>
  );
}
