"use client";

import { Button } from "antd";
import { EditOutlined, LoginOutlined, ReloadOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import type { ClanInviteFailure } from "@/lib/api/clan-invite";
import { useAuth } from "@/lib/auth/auth-context";
import { colorVars } from "@/styles/tokens";
import { GuestModeNotice } from "./guest-mode-notice";

export interface RegisterProblemProps {
  readonly failure: ClanInviteFailure;
  /** Quay về ô mã để gõ lại hoặc thử một mã khác. Luôn có. */
  readonly onEditCode: () => void;
  /** Chỉ có ở hai ca "chưa gửi đi được" — xem javadoc. */
  readonly onRetry?: () => void;
  readonly retrying?: boolean;
  /** `retryAfterSeconds` của `429`. Vắng thì nói "ít phút", không bịa số. */
  readonly retryAfterSeconds?: number | null;
}

/**
 * **Tám câu trả lời cho tám tình huống, không một dải đỏ cho tất cả.**
 *
 * <h2>Bốn ca của mã, và vì sao gộp chúng lại là một lỗi nghiệp vụ</h2>
 * Người dùng phải làm bốn việc khác nhau, nên "mã không hợp lệ" là một câu vô
 * dụng ở cả bốn:
 * <ul>
 *   <li>{@code NOT_FOUND} → <b>gõ lại</b>. Hay gặp nhất là đường dẫn bị cắt lúc
 *       chuyển tiếp tin nhắn, hoặc thiếu một ký tự — `O`/`0` và `I`/`1` nhìn
 *       gần như nhau trên giấy in;</li>
 *   <li>{@code EXPIRED} → <b>xin mã mới</b>. Mã từng đúng, chỉ là cũ; gõ lại
 *       một nghìn lần cũng vậy;</li>
 *   <li>{@code REVOKED} → cũng xin mã mới, nhưng <b>lý do khác hẳn</b>: có
 *       người chủ động đóng nó. Nói "hết hạn" ở đây là nói sai một sự việc mà
 *       người trong họ có thể cần biết;</li>
 *   <li>{@code EXHAUSTED} → <b>nhắn cho người đưa mã</b>. Mã còn hạn, chưa thu
 *       hồi, chỉ là trần lượt đã đầy — và trần thì Hội đồng nâng được, tức đây
 *       là ca duy nhất mở lại được mà không cần mã mới.</li>
 * </ul>
 *
 * <h2>Ba ca không phải lỗi của mã, và chúng tuyệt đối không được đọc thành "mã sai"</h2>
 * {@code RATE_LIMITED} là chốt chống dò mã đang làm đúng việc của nó;
 * {@code PROVIDER_DOWN} là Keycloak im lặng — và điểm quan trọng nhất của ca ấy
 * là <b>mã CHƯA bị tiêu</b> (thao tác Keycloak nằm trước lần ghi CSDL), nên câu
 * chữ phải nói "bấm lại", không phải "xin mã mới"; {@code UNAVAILABLE} là mất
 * mạng. Một người vừa bị bảo là mã sai sẽ đi xin mã mới, và mã mới cũng sẽ
 * không mở được.
 *
 * <h2>{@code IDENTITY_TAKEN} — ca duy nhất mà lối đi tiếp là ĐĂNG NHẬP</h2>
 * Mã mời vẫn đúng và vẫn còn lượt; thứ đã có chủ là <b>định danh</b> người dùng
 * vừa khai. Vì lối đăng ký bằng mã dòng họ không đòi đăng nhập, chuỗi họ tự gõ
 * không chứng minh được họ sở hữu địa chỉ ấy — nên máy chủ dừng lại thay vì thao
 * tác trên một tài khoản có thể là của người khác.
 *
 * <p><b>Hai luật cứng của ca này:</b></p>
 * <ul>
 *   <li><b>Không có nút "Thử lại", và không tự gọi lại.</b> Máy chủ tính mỗi lần
 *       từ chối vào giới hạn tần suất như một lần thất bại — cố ý, để tín hiệu
 *       "địa chỉ này đã là thành viên" không biến endpoint thành máy dò tài
 *       khoản. Một vòng thử lại vì thế đốt hạn mức của chính người dùng ngay
 *       tình, để nhận lại đúng câu trả lời cũ.</li>
 *   <li><b>Câu chữ nói việc cần làm, và KHÔNG xác nhận dứt khoát.</b> "Đã có lỗi
 *       xảy ra" là vô dụng — người dùng không biết phải làm gì. Nhưng "tài khoản
 *       này đã tồn tại" thì đem đi dò được: đọc màn hình là đọc ra một câu trả
 *       lời có/không về một địa chỉ bất kỳ. Câu đã viết nói <i>hãy đăng nhập rồi
 *       nhập lại mã</i> mà không khẳng định điều gì về địa chỉ ấy.</li>
 * </ul>
 *
 * <p>Nút "Đăng nhập" đưa họ qua Keycloak rồi quay lại đúng trang này. Trang
 * {@code /dang-ky} cố ý <b>không</b> nhận mã qua tham số truy vấn, nên
 * {@code window.location.href} mà {@code login()} gửi làm {@code redirectUri}
 * không mang theo bí mật nào. Sau khi có phiên, người dùng nhập lại mã và lượt
 * gọi mang token — nhánh ấy không đi qua phép chặn này.</p>
 *
 * <h2>Nền hổ phách, không phải dải đỏ</h2>
 * Cùng luật với {@code invitation-problem.tsx} (06 §7.2 luật 4): một mã hết hạn
 * là hệ thống <i>đang chạy đúng</i>. Màu đỏ ở đây dạy người dùng rằng họ vừa
 * làm hỏng thứ gì đó, và người sợ làm hỏng nhất trong dòng họ lại chính là
 * người ta cần nhất.
 *
 * <h2>Mỗi ca có một chỗ để đi tiếp — kể cả ca phải chờ</h2>
 * Nút "Gõ lại mã" có mặt ở <b>mọi</b> ca, vì quay về ô mã luôn là một việc làm
 * được. Nút "Thử lại" thì chỉ ở hai ca "chưa gửi đi được": ở bốn ca của mã, bấm
 * lại chỉ lặp lại cùng một câu trả lời — và ở {@code RATE_LIMITED} nó còn đẩy
 * thêm một lượt vào đúng bộ đếm đang chặn họ.
 */
export function RegisterProblem({
  failure,
  onEditCode,
  onRetry,
  retrying,
  retryAfterSeconds,
}: RegisterProblemProps) {
  const t = useTranslations("auth.register");
  const { isAuthenticated, login } = useAuth();

  // Chỉ hai ca này thử lại được. Xem javadoc.
  //
  // `IDENTITY_TAKEN` KHÔNG nằm trong danh sách này, và không bao giờ được thêm
  // vào: máy chủ tính mỗi lần từ chối ấy vào giới hạn tần suất của chính người
  // dùng. Một nút "Thử lại" ở đó chỉ đốt hạn mức để nhận lại câu trả lời cũ.
  const canRetry =
    (failure === "UNAVAILABLE" || failure === "PROVIDER_DOWN") &&
    typeof onRetry === "function";

  // Lối đi tiếp thật của ca "định danh đã có chủ": vào bằng tài khoản ấy, rồi
  // nhập lại mã — lượt gọi kèm token lấy danh tính từ Keycloak. Ẩn nút khi đã
  // có phiên (cùng luật với `register-done.tsx`): bảo một người đang đăng nhập
  // đi đăng nhập là một ngõ cụt.
  const showSignIn = failure === "IDENTITY_TAKEN" && !isAuthenticated;

  // "Ít phút" khi máy chủ không nói số. 00 §5: không bịa số.
  const next =
    failure === "RATE_LIMITED" && typeof retryAfterSeconds === "number"
      ? t("problem.RATE_LIMITED.nextAfter", {
          minutes: Math.max(1, Math.ceil(retryAfterSeconds / 60)),
        })
      : t(`problem.${failure}.next`);

  return (
    <div className="space-y-4">
      <section
        // Mốc kiểm mang ĐÚNG nhánh, để một ca kiểm không thể xanh nhờ nhận nhầm
        // một màn khác.
        data-register-state={failure}
        role="status"
        aria-labelledby="register-problem-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
      >
        <h1
          id="register-problem-title"
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
          {next}
        </p>

        <div className="mt-4 flex flex-wrap items-center gap-3">
          {canRetry && (
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              loading={retrying}
              onClick={onRetry}
              className="min-h-[44px] text-than font-semibold"
            >
              {t("retry")}
            </Button>
          )}
          {showSignIn && (
            <Button
              type="primary"
              icon={<LoginOutlined />}
              onClick={() => login()}
              className="min-h-[44px] text-than font-semibold"
            >
              {t("signInThenCode")}
            </Button>
          )}
          {/* Biểu tượng LUÔN kèm chữ — 00 §2.3. */}
          <Button
            type={canRetry || showSignIn ? "default" : "primary"}
            icon={<EditOutlined />}
            onClick={onEditCode}
            className="min-h-[44px] text-than font-semibold"
          >
            {t("editCode")}
          </Button>
        </div>
      </section>

      {/*
        Chế độ khách chỉ hiện ở hai ca, và đó là một lựa chọn chứ không phải
        thiếu sót:
          · `NOT_FOUND` — đúng cửa 3 của 06 §5.4. Người đứng đây có thể là người
            lạ vừa mò vào, và câu trả lời đúng cho họ không phải một bức tường
            mà là "phần công khai mở cho ai cũng xem được";
          · `RATE_LIMITED` — họ buộc phải chờ, nên cho họ một việc làm trong lúc
            chờ thay vì một màn hình đứng yên.
        Ở ba ca còn lại (hết hạn · thu hồi · hết lượt) thì người đứng trước màn
        hình gần như chắc chắn là người trong họ cầm một mã thật, và việc cần làm
        của họ là một cuộc gọi — chen thêm một khối "xem phần công khai" vào đó
        chỉ làm loãng đúng việc cần làm.

        `IDENTITY_TAKEN` cũng không hiện khối này, và ở đó còn một lý do thứ hai
        rất cụ thể: `GuestModeNotice` mang sẵn một nút "Đăng nhập" của riêng nó.
        Hai nút cùng tên trên một màn hình, một nút dẫn đúng lối đi tiếp và một
        nút chỉ để xem phần công khai, là cách chắc chắn để người dùng bấm nhầm
        đúng cái nút không đưa họ tới đâu.
      */}
      {(failure === "NOT_FOUND" || failure === "RATE_LIMITED") && <GuestModeNotice />}
    </div>
  );
}
