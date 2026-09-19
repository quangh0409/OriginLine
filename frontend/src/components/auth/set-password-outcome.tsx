"use client";

import { Button } from "antd";
import { CheckCircleOutlined, ClockCircleOutlined, LoginOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";
import { ContactInviterNotice } from "./contact-inviter-notice";

export interface SetPasswordDoneProps {
  /** Rời sang màn đăng nhập, quay lại phả đồ sau khi xong. */
  readonly onGoToLogin: () => void;
}

/**
 * **Xong rồi** — và một lối đi tiếp, không phải một màn "Thành công" cụt.
 *
 * <h2>Vì sao không đưa thẳng vào phả đồ</h2>
 * Người vừa đặt mật khẩu <b>chưa có phiên đăng nhập</b>: họ có một mật khẩu,
 * chưa có một token. Đẩy họ sang `/tree` lúc này là đẩy họ vào bản phả đồ dành
 * cho khách — nơi <b>không một người đang sống nào</b> hiện ra, theo đúng BA v2
 * §10. Họ sẽ kết luận rằng dòng họ mình chỉ có người đã khuất, ở đúng phút đầu
 * tiên gặp hệ thống.
 *
 * Nên bước tiếp là đăng nhập, và màn này làm hai việc để bước ấy không giống
 * một thủ tục nữa: nó <b>nói trước có gì ở bên kia</b>, và nó đặt đích đến sau
 * đăng nhập là <b>phả đồ</b> chứ không phải chính trang này. Quay lại trang đặt
 * mật khẩu sau khi đăng nhập là một vòng tròn — liên kết đã tiêu rồi.
 */
export function SetPasswordDone({ onGoToLogin }: SetPasswordDoneProps) {
  const t = useTranslations("auth.setPassword");

  return (
    <section
      data-set-password-state="DONE"
      role="status"
      aria-labelledby="set-password-done-title"
      className="rounded-lg border px-4 py-5 sm:px-6"
      style={{ background: colorVars.successBg, borderColor: colorVars.borderDark }}
    >
      <h1
        id="set-password-done-title"
        className="m-0 flex items-center gap-2 font-serif text-de font-bold text-text-main"
      >
        <CheckCircleOutlined aria-hidden style={{ color: colorVars.successText }} />
        {t("doneTitle")}
      </h1>
      <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
        {t("doneBody")}
      </p>

      <p className="m-0 mt-4 max-w-prose text-than font-semibold text-text-main">
        {t("doneWhatsThere")}
      </p>
      <ul className="m-0 mt-2 list-disc space-y-1 pl-5 text-than leading-relaxed text-text-main">
        <li>{t("doneItem1")}</li>
        <li>{t("doneItem2")}</li>
        <li>{t("doneItem3")}</li>
      </ul>

      <div className="mt-5">
        <Button
          type="primary"
          // Biểu tượng LUÔN kèm chữ — 00 §2.3. Một mũi tên trơ trọi ở bước cuối
          // của luồng mời là chỗ tệ nhất để bắt người dùng đoán.
          icon={<LoginOutlined />}
          onClick={onGoToLogin}
          className="min-h-[44px] w-full text-than font-semibold sm:w-auto"
        >
          {t("doneCta")}
        </Button>
      </div>
    </section>
  );
}

export interface SetPasswordLinkExpiredProps {
  /** Người này có thể đã có mật khẩu rồi (gọi lần hai) — cho họ lối đăng nhập. */
  readonly onGoToLogin: () => void;
}

/**
 * **Liên kết đặt mật khẩu không còn dùng được** — `410 SET_PASSWORD_LINK_INVALID`.
 *
 * <h2>Đây là nghiệp vụ chạy ĐÚNG, không phải sự cố</h2>
 * Hạn ba mươi phút, và một cụ hoàn toàn có thể để tin nhắn tới hôm sau mới mở.
 * Nền hổ phách chứ không phải dải đỏ: màu đỏ ở đây dạy sai người dùng rằng họ
 * vừa làm hỏng thứ gì đó, và người sợ làm hỏng nhất trong dòng họ lại chính là
 * người ta cần nhất.
 *
 * <h2>Điều quan trọng nhất phải nói: TÀI KHOẢN KHÔNG MẤT</h2>
 * Lời mời đã được nhận trước đó rồi — tài khoản đã lập, đã nối với hồ sơ trong
 * phả, không còn bước chờ duyệt nào. Thứ hết hạn chỉ là cái liên kết. Không nói
 * ra điều này thì một người vừa bấm "Đúng là tôi" hôm qua sẽ tin rằng mình phải
 * làm lại từ đầu, và sẽ đi xin một <b>mã mời</b> mới — thứ họ không cần.
 *
 * <h2>Lối đi tiếp là một CON NGƯỜI, và nó dẫn tới một quy trình có thật</h2>
 * Trưởng chi đặt một mật khẩu tạm rồi đọc qua điện thoại; lần đăng nhập đầu,
 * Keycloak bắt đổi ngay bằng hành động `UPDATE_PASSWORD` — màn
 * `login-update-password.ftl` của theme dựng sẵn cho đúng nghi thức ấy. Đây
 * không phải một lời an ủi; đó là bước kế tiếp có thật.
 *
 * <h2>Và một lối thứ hai, cho ca dễ quên nhất</h2>
 * `410` cũng là câu trả lời khi liên kết <b>đã dùng rồi</b> — tức mật khẩu đã
 * đặt xong ở một lần bấm trước, và nó <b>vẫn đăng nhập được</b>. Người bấm lại
 * liên kết trong tin nhắn cũ rơi đúng vào đây. Với họ, việc cần làm không phải
 * gọi điện mà chỉ là đăng nhập, nên nút ấy phải có mặt.
 */
export function SetPasswordLinkExpired({ onGoToLogin }: SetPasswordLinkExpiredProps) {
  const t = useTranslations("auth.setPassword");

  return (
    <div className="space-y-4">
      <section
        data-set-password-state="LINK_INVALID"
        role="status"
        aria-labelledby="set-password-expired-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
      >
        <h1
          id="set-password-expired-title"
          className="m-0 flex items-center gap-2 font-serif text-de font-bold text-text-main"
        >
          <ClockCircleOutlined aria-hidden style={{ color: colorVars.accentText }} />
          {t("expiredTitle")}
        </h1>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("expiredAccountSafe")}
        </p>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("expiredBody")}
        </p>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("expiredNext")}
        </p>

        <div className="mt-4">
          <Button
            icon={<LoginOutlined />}
            onClick={onGoToLogin}
            className="min-h-[44px] w-full text-than font-semibold sm:w-auto"
          >
            {t("expiredAlreadySetCta")}
          </Button>
          <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
            {t("expiredAlreadySetHint")}
          </p>
        </div>
      </section>

      {/* Cùng khối "gọi người đã mời" mà ba màn lời mời hỏng đang dùng. Dùng
          lại chứ không chép: người nhận thật đã có số máy ấy trong tay — ở cuối
          tin nhắn, hoặc dưới phiếu mời giấy — và phản hồi lỗi của máy chủ cố ý
          KHÔNG chở tên hay số điện thoại của một người thật. */}
      <ContactInviterNotice />
    </div>
  );
}

export interface SetPasswordNoLinkProps {
  readonly onGoToLogin: () => void;
}

/**
 * Mở trang này mà **không có liên kết** — không có `token` trong đường dẫn.
 *
 * <h2>Đây là một PHÉP CHẶN CÓ CHỦ Ý ở phía máy chủ, không phải một thiếu sót</h2>
 * Backend <b>không phát</b> `setPasswordUrl` cho một tài khoản đã có mật khẩu.
 * Nếu nó phát, thì ai cầm một mã mời cộng với việc đoán đúng địa chỉ thư của
 * một thành viên cũ sẽ <b>đổi được mật khẩu của người ta</b>. Vắng liên kết vì
 * thế là hệ thống <i>đang bảo vệ</i> người dùng, và màn này phải đọc ra đúng
 * như vậy.
 *
 * <h2>Không một chữ "lỗi" nào, và không màu đỏ</h2>
 * Ca hay gặp nhất ở đây là một người gõ tay địa chỉ trang, hoặc mở lại một thẻ
 * cũ sau khi đã đặt mật khẩu xong. Việc cần làm của họ là đăng nhập — nên màn
 * này <b>là</b> đường tới đăng nhập, chứ không phải một dải đỏ đứng chắn trước
 * nó. Nút đăng nhập ở đây an toàn theo đúng nghĩa đen: không có `token` trong
 * đường dẫn thì không có gì để rò sang `redirect_uri` của Keycloak.
 */
export function SetPasswordNoLink({ onGoToLogin }: SetPasswordNoLinkProps) {
  const t = useTranslations("auth.setPassword");

  return (
    <section
      data-set-password-state="NO_LINK"
      role="status"
      aria-labelledby="set-password-nolink-title"
      className="rounded-lg border px-4 py-5 sm:px-6"
      style={{ background: colorVars.bgCard, borderColor: colorVars.border }}
    >
      <h1
        id="set-password-nolink-title"
        className="m-0 font-serif text-de font-bold text-text-main"
      >
        {t("noLinkTitle")}
      </h1>
      <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
        {t("noLinkBody")}
      </p>
      <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
        {t("noLinkNext")}
      </p>

      <div className="mt-4">
        <Button
          type="primary"
          icon={<LoginOutlined />}
          onClick={onGoToLogin}
          className="min-h-[44px] w-full text-than font-semibold sm:w-auto"
        >
          {t("noLinkCta")}
        </Button>
      </div>
    </section>
  );
}
