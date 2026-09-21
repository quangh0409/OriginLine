"use client";

import { useState, type FormEvent } from "react";
import { Button } from "antd";
import { CheckCircleOutlined, LoginOutlined } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { RegisterField } from "./register-field";

export interface InvitationAccountFormProps {
  /** Gửi `{ loginId, displayName }` tới `invitationApi.accept(code, account)`. */
  readonly onSubmit: (values: { loginId: string; displayName: string }) => void;
  /** "Tôi đã có tài khoản rồi" — người vào bằng Google/Zalo trước, rồi quay lại mở lời mời này. */
  readonly onLoginInstead: () => void;
  readonly submitting?: boolean;
  /** Câu máy chủ nói về CHÍNH định danh vừa gửi (`detail` của một `VALIDATION_FAILED`). */
  readonly fieldError?: string | null;
}

/**
 * **Lập tài khoản ngay tại màn nhận lời mời** — cho người <b>chưa đăng nhập</b>.
 *
 * <h2>Vì sao màn này tồn tại, nói thẳng một lần</h2>
 * `POST /invitations/accept` không còn đòi token (xem javadoc đầu
 * {@code invitation.ts}): khi người gọi không mang token, máy chủ tự lập một
 * tài khoản Keycloak bằng {@code loginId} họ tự khai — <b>email hoặc số điện
 * thoại Việt Nam</b>. Trước khi có khối này, màn nhận lời mời gọi `accept`
 * không kèm gì, nhận lại {@code VALIDATION_FAILED} ("phải cho biết địa chỉ thư
 * hoặc số điện thoại"), và người dùng không có ô nào để sửa.
 *
 * <h2>Đúng nhóm mà luồng mời cá nhân sinh ra để phục vụ</h2>
 * Mã mời cá nhân dành cho <b>các cụ lớn tuổi</b> — Trưởng chi chọn người trong
 * phả, in phiếu, đưa tận tay. Đó là nhóm thường <i>không có</i> email, nên ô
 * định danh nhận cả số điện thoại — dùng lại nguyên văn
 * {@code auth.register.identifierLabel/identifierHint/identifierEmpty} của màn
 * đăng ký bằng mã dòng họ, KHÔNG viết câu thứ hai cho cùng một ô.
 *
 * <h2>KHÔNG có ô mật khẩu</h2>
 * `AcceptInvitationRequest` không có trường mật khẩu — cùng cơ chế với mã dòng
 * họ: máy chủ lập tài khoản rồi trả về {@code setPasswordUrl}, một liên kết
 * một lần để tự đặt mật khẩu ở bước sau. Dựng thêm một ô mật khẩu ở đây là dựng
 * một trường máy chủ không đọc.
 *
 * <h2>"Tôi đã có tài khoản rồi" vẫn phải có lối ra</h2>
 * Người vừa đăng nhập Google/Zalo nhưng chưa được ghép vào cây vẫn hợp lệ ở
 * `/accept` — token nói họ là ai, {@code loginId} bị bỏ qua. Nút thứ hai đưa
 * họ sang màn đăng nhập thay vì ép gõ một định danh mới.
 */
export function InvitationAccountForm({
  onSubmit,
  onLoginInstead,
  submitting,
  fieldError,
}: InvitationAccountFormProps) {
  const t = useTranslations("auth.invitation");
  const tRegister = useTranslations("auth.register");

  const [loginId, setLoginId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [trong, setTrong] = useState(false);

  const loiTrongO = trong ? tRegister("identifierEmpty") : (fieldError ?? null);

  function guiDi(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (loginId.trim().length === 0) {
      setTrong(true);
      return;
    }
    // KHÔNG chặn theo hình dạng ở đây — cùng kỷ luật với `register-account-form.tsx`:
    // email và số điện thoại đều gửi đi được, chỗ duy nhất phán quyết là máy chủ.
    setTrong(false);
    onSubmit({ loginId: loginId.trim(), displayName: displayName.trim() });
  }

  return (
    <div className="mt-5 space-y-4" data-invitation-account-form="">
      <p className="m-0 max-w-prose text-than leading-relaxed text-text-main">
        {t("needsAccountLead")}
      </p>

      <form onSubmit={guiDi} noValidate className="space-y-4">
        <RegisterField
          name="loi-moi-dinh-danh"
          label={tRegister("identifierLabel")}
          value={loginId}
          onChange={(v) => {
            setLoginId(v);
            if (trong) setTrong(false);
          }}
          hint={tRegister("identifierHint")}
          error={loiTrongO}
          autoComplete="username"
          maxLength={254}
          autoFocus
        />

        <RegisterField
          name="loi-moi-ten-tu-xung"
          label={tRegister("nameLabel")}
          value={displayName}
          onChange={setDisplayName}
          hint={tRegister("nameHint")}
          autoComplete="name"
          maxLength={160}
        />

        <Button
          type="primary"
          htmlType="submit"
          loading={submitting}
          icon={<CheckCircleOutlined />}
          data-invitation-action="accept"
          className="min-h-[44px] w-full text-than font-semibold sm:w-auto"
        >
          {submitting ? t("accepting") : t("needsAccountSubmit")}
        </Button>
      </form>

      <Button
        icon={<LoginOutlined />}
        onClick={onLoginInstead}
        className="min-h-[44px] text-than font-semibold"
      >
        {t("needsAccountHaveOne")}
      </Button>
    </div>
  );
}
