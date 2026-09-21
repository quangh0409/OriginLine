"use client";

import { useState, type FormEvent } from "react";
import { Button } from "antd";
import { KeyOutlined, LoginOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { useAuth } from "@/lib/auth/auth-context";
import { colorVars } from "@/styles/tokens";
import { RegisterField } from "./register-field";
import { GuestModeNotice } from "./guest-mode-notice";

export interface RegisterCodeFormProps {
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly onSubmit: () => void;
  readonly checking: boolean;
}

/**
 * **Bước 1 — ô mã mời dòng họ.**
 *
 * <h2>Mã được kiểm TRƯỚC, tài khoản lập SAU</h2>
 * Đây là luật nghiệp vụ, không phải một lựa chọn về bố cục. Realm đặt
 * {@code duplicateEmailsAllowed: false}, nên nếu tài khoản được tạo trước rồi
 * mới hỏi mã thì <b>một lần gõ sai mã cũng để lại một tài khoản Keycloak mồ
 * côi</b> — và chính lần thử lại của người ấy sẽ hỏng, vì địa chỉ thư của họ đã
 * bị cái tài khoản rác kia chiếm. Không ai đi dọn thứ đó.
 *
 * Vì vậy màn này là hai bước chứ không phải một biểu mẫu dài: ô mã đứng riêng,
 * và ô thư điện tử chỉ hiện ra sau khi máy chủ đã nói mã dùng được.
 *
 * <h2>Người không có mã cũng phải có lối đi</h2>
 * 06 §5.4 gọi đây là chỗ dễ viết sai nhất trong cả tài liệu: phản xạ mặc định
 * là "Bạn không có quyền truy cập", và câu ấy sai <b>cả về nghiệp vụ lẫn về sự
 * thật</b> — người lạ <i>có</i> quyền xem phần công khai, và phần công khai là
 * một nửa lý do sản phẩm tồn tại. Nên dưới ô mã luôn có hai lối: xem phần công
 * khai, và đăng nhập nếu đã có tài khoản.
 */
export function RegisterCodeForm({
  value,
  onChange,
  onSubmit,
  checking,
}: RegisterCodeFormProps) {
  const t = useTranslations("auth.register");
  const { isAuthenticated, login } = useAuth();

  const [trong, setTrong] = useState(false);

  function guiDi(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (value.trim().length === 0) {
      setTrong(true);
      return;
    }
    setTrong(false);
    onSubmit();
  }

  return (
    <div className="space-y-4">
      <section data-register-state="CODE" className="space-y-4">
        <div>
          <h1 className="m-0 font-serif text-de font-bold text-text-main">{t("codeTitle")}</h1>
          <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
            {t("codeIntro")}
          </p>
        </div>

        {/* `noValidate`: phép kiểm của trình duyệt in ra một bong bóng tiếng Anh
            của hệ điều hành, nằm ngoài mọi thứ tệp này dịch được. */}
        <form onSubmit={guiDi} noValidate className="space-y-4">
          <RegisterField
            name="ma-moi"
            label={t("codeLabel")}
            value={value}
            onChange={(v) => {
              onChange(v);
              if (trong) setTrong(false);
            }}
            hint={t("codeHint")}
            error={trong ? t("codeEmpty") : null}
            placeholder={t("codePlaceholder")}
            autoComplete="off"
            // 32 là trần của `RedeemClanInviteRequest.code`. Cắt ở client thì một
            // mã dán nhầm cả câu văn không bị máy chủ trả về `400` khó hiểu.
            maxLength={32}
            monospace
            autoFocus
          />

          <Button
            type="primary"
            htmlType="submit"
            loading={checking}
            icon={<KeyOutlined />}
            className="min-h-[44px] w-full text-than font-semibold"
          >
            {checking ? t("codeChecking") : t("codeSubmit")}
          </Button>
        </form>

        <p
          className="m-0 max-w-prose rounded-lg border px-4 py-3 text-than leading-relaxed text-text-main"
          style={{ background: colorVars.bgDeceased, borderColor: colorVars.border }}
        >
          {t("codeWhyFirst")}
        </p>
      </section>

      <section
        aria-labelledby="register-no-code-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.bgCard, borderColor: colorVars.border }}
      >
        <h2
          id="register-no-code-title"
          className="m-0 font-serif text-de font-semibold text-text-main"
        >
          {t("noCodeTitle")}
        </h2>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("noCodeBody")}
        </p>

        {/* Người ĐÃ đăng nhập mà đứng ở đây đang gặp chuyện khác (họ có tài
            khoản nhưng chưa được ghép vào phả), và một nút "Đăng nhập" ở đó là
            một vòng tròn. Cùng phép chặn với `invitation-problem.tsx`. */}
        {!isAuthenticated && (
          <Button
            icon={<LoginOutlined />}
            onClick={() => login()}
            className="mt-4 min-h-[44px] text-than font-semibold"
          >
            {t("haveAccount")}
          </Button>
        )}
        {isAuthenticated && (
          <p className="m-0 mt-3 max-w-prose text-than leading-relaxed text-text-muted">
            {t("alreadySignedIn")}
          </p>
        )}
      </section>

      <GuestModeNotice />
    </div>
  );
}
