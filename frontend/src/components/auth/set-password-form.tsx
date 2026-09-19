"use client";

import { useState, type FormEvent } from "react";
import { Button } from "antd";
import { useTranslations } from "next-intl";
import type { SetPasswordFailure } from "@/lib/api/invitation";
import { colorVars } from "@/styles/tokens";
import { SetPasswordField } from "./set-password-field";

export interface SetPasswordFormProps {
  readonly onSubmit: (newPassword: string) => void;
  readonly submitting: boolean;
  /**
   * Nhánh hỏng của lần gửi gần nhất, **trừ** {@code LINK_INVALID} — ca ấy làm
   * cả biểu mẫu này vô nghĩa nên màn ngoài thay hẳn nội dung.
   */
  readonly failure?: SetPasswordFailure | null;
  /** `detail` của `422` — câu **máy chủ** nói về chính mật khẩu vừa gõ. */
  readonly rejectionDetail?: string | null;
}

/**
 * Biểu mẫu đặt mật khẩu lần đầu.
 *
 * <h2>KHÔNG có bản sao chính sách mật khẩu ở đây, và đó là cả một quyết định</h2>
 * Chính sách mật khẩu sống trong realm Keycloak: nó đổi được từ màn quản trị,
 * nó khác nhau giữa các bản triển khai, và nó là thứ <b>duy nhất</b> thật sự
 * phán quyết. Dựng một bản sao ở client tạo ra bản luật thứ hai — và bản thứ
 * hai sẽ lệch, âm thầm, theo đúng một trong hai chiều tồi:
 * <ul>
 *   <li>chặt hơn realm → chặn một mật khẩu mà máy chủ sẵn sàng nhận, và người
 *       dùng không có cách nào biết mình đang cãi nhau với ai;</li>
 *   <li>lỏng hơn realm → hứa "được rồi" rồi để máy chủ từ chối, tức đúng cái
 *       trải nghiệm mà việc kiểm trước sinh ra để tránh.</li>
 * </ul>
 * Vì thế nút gửi <b>không bao giờ bị vô hiệu vì mật khẩu yếu</b>, không có
 * thanh đo độ mạnh, không có danh sách dấu tích. Chỉ một câu <b>gợi ý</b>, chép
 * đúng chữ của theme Keycloak để hai nơi nói cùng một điều.
 *
 * <h2>Hai ô, và vì sao phép dò "gõ lệch" KHÔNG phải một luật thứ hai</h2>
 * Nó không xét mật khẩu là gì — nó xét hai lần gõ có giống nhau không. Một mật
 * khẩu realm chấp nhận không bao giờ bị nó chặn khi người dùng gõ đúng ý mình
 * hai lần. Cái nó chặn là ca <b>không sửa được</b>: đặt xong một mật khẩu mà
 * chính chủ không biết là gì, rồi phát hiện ra ở màn đăng nhập, lúc liên kết
 * một lần đã tiêu. Màn "Đặt mật khẩu mới" của Keycloak cũng có hai ô và hai nút
 * "Hiện" — người dùng gặp cả hai màn trong cùng một buổi, nên chúng phải giống
 * nhau.
 *
 * <h2>Mật khẩu bị từ chối thì Ở LẠI trên biểu mẫu</h2>
 * `422` là ca duy nhất người dùng sửa được ngay tại chỗ, nên nó hiện <b>trong
 * ô</b> và giữ nguyên chữ đã gõ. Ba ca còn lại ({@code PROVIDER_DOWN},
 * {@code RATE_LIMITED}, {@code UNAVAILABLE}) đều là "chưa gửi đi được" chứ
 * không phải "mật khẩu chưa đạt": mật khẩu vẫn tốt, liên kết vẫn còn, và việc
 * cần làm là bấm lại. Nói nhầm hai nhóm ấy vào nhau sẽ đẩy một người đi nghĩ ra
 * mật khẩu mới trong khi mạng của họ vừa rớt.
 */
export function SetPasswordForm({
  onSubmit,
  submitting,
  failure,
  rejectionDetail,
}: SetPasswordFormProps) {
  const t = useTranslations("auth.setPassword");

  const [matKhau, setMatKhau] = useState("");
  const [goLai, setGoLai] = useState("");
  /** Lỗi của PHÍA CLIENT: ô trống, hoặc hai lần gõ lệch. Không phải chính sách. */
  const [loiCucBo, setLoiCucBo] = useState<"EMPTY" | "MISMATCH" | null>(null);

  // Câu của máy chủ được ưu tiên: nó biết luật, tệp này thì không.
  const loiTrongO =
    failure === "PASSWORD_REJECTED"
      ? (rejectionDetail ?? t("rejectedFallback"))
      : loiCucBo === "EMPTY"
        ? t("errorEmpty")
        : null;

  const loiGoLai = loiCucBo === "MISMATCH" ? t("errorMismatch") : null;

  const banner =
    failure === "PROVIDER_DOWN" || failure === "RATE_LIMITED" || failure === "UNAVAILABLE"
      ? failure
      : null;

  function guiDi(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (matKhau.length === 0) {
      setLoiCucBo("EMPTY");
      return;
    }
    if (matKhau !== goLai) {
      setLoiCucBo("MISMATCH");
      return;
    }
    setLoiCucBo(null);
    onSubmit(matKhau);
  }

  return (
    <section data-set-password-state="FORM" className="space-y-4">
      <div>
        <h1 className="m-0 font-serif text-de font-bold text-text-main">{t("title")}</h1>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("intro")}
        </p>
        <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
          {t("oneTimeNote")}
        </p>
      </div>

      {banner && (
        <div
          data-set-password-banner={banner}
          // `status`, KHÔNG `alert`: người đang đọc không làm gì sai, và một
          // dải đỏ ở đây dạy họ rằng mật khẩu của mình có vấn đề.
          role="status"
          className="rounded-lg border px-4 py-4"
          style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
        >
          <h2 className="m-0 text-than font-semibold text-text-main">
            {t(`banner.${banner}.title`)}
          </h2>
          <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-main">
            {t(`banner.${banner}.body`)}
          </p>
          <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-main">
            {t(`banner.${banner}.next`)}
          </p>
        </div>
      )}

      {/* `noValidate`: phép kiểm của trình duyệt in ra một bong bóng tiếng Anh
          của hệ điều hành, nằm ngoài mọi thứ tệp này dịch được. */}
      <form onSubmit={guiDi} noValidate className="space-y-4">
        <SetPasswordField
          name="moi"
          label={t("passwordLabel")}
          value={matKhau}
          onChange={(v) => {
            setMatKhau(v);
            if (loiCucBo) setLoiCucBo(null);
          }}
          hint={t("hint")}
          error={loiTrongO}
          autoFocus
          disabled={submitting}
        />

        <SetPasswordField
          name="go-lai"
          label={t("confirmLabel")}
          value={goLai}
          onChange={(v) => {
            setGoLai(v);
            if (loiCucBo) setLoiCucBo(null);
          }}
          error={loiGoLai}
          disabled={submitting}
        />

        <Button
          type="primary"
          htmlType="submit"
          loading={submitting}
          // Nút KHÔNG bao giờ bị vô hiệu vì mật khẩu "chưa đủ mạnh" — xem
          // javadoc. Chỉ lúc đang gửi mới khoá, để không gửi hai lần.
          className="min-h-[44px] w-full text-than font-semibold"
        >
          {submitting ? t("submitting") : t("submit")}
        </Button>
      </form>
    </section>
  );
}
