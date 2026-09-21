"use client";

import { useState, type FormEvent } from "react";
import { Button } from "antd";
import { ArrowLeftOutlined, UserAddOutlined } from "@ant-design/icons";
import { useFormatter, useTranslations } from "next-intl";
import type { ClanInvitePreviewDto } from "@/lib/api/clan-invite";
import { RegisterField } from "./register-field";

export interface RegisterAccountFormProps {
  readonly preview: ClanInvitePreviewDto;
  readonly onSubmit: (values: { loginId: string; displayName: string }) => void;
  readonly onEditCode: () => void;
  readonly submitting: boolean;
  /** Câu máy chủ nói về chính dữ liệu vừa gửi (`detail` của `VALIDATION_FAILED`). */
  readonly serverFieldError?: string | null;
}

/**
 * **Bước 2 — lập tài khoản, sau khi máy chủ đã nói mã dùng được.**
 *
 * <h2>Ô định danh nhận CẢ số điện thoại lẫn thư điện tử — và cả hai ĐĂNG KÝ ĐƯỢC</h2>
 * Ô đăng nhập của Keycloak nhận cả hai (realm đặt {@code username} với nhãn
 * {@code ${giaphaRegPhone}}), và phiếu mời giấy in "ĐĂNG NHẬP BẰNG SỐ ĐIỆN
 * THOẠI" ngay dưới mã. Người dùng gặp cả hai màn trong <b>cùng một buổi</b>;
 * một màn từ chối thẳng thứ màn kia nhận sẽ khiến họ kết luận rằng mình nhớ
 * sai, chứ không phải rằng hai màn khác nhau.
 *
 * `RegisterWithClanInviteRequest.loginId` nay nhận <b>email hoặc số điện thoại
 * Việt Nam</b>, và máy chủ chuẩn hoá `+84…` / `84…` / dấu cách / chấm / gạch về
 * dạng `0…`. Khối giải thích nền hổ phách từng đứng ở đây — nói rằng số máy
 * đăng nhập được nhưng chưa đăng ký được — <b>đã gỡ</b>: nó đúng khi viết, và
 * nay sai.
 *
 * <h2>Câu ĐÚNG trong khối ấy không mất, nó chuyển chỗ</h2>
 * Tài khoản lập bằng số điện thoại sẽ <i>không</i> nhận được thư đặt lại mật
 * khẩu khi hệ thống có SMTP. Nhưng đó là giới hạn của việc <b>khôi phục</b>,
 * không phải của việc <b>đăng ký</b> — nói nó ở ô nhập là chặn nhầm một người
 * mà máy chủ sẵn sàng nhận. Nó nằm ở khối đặt mật khẩu của
 * {@code register-done.tsx}, đúng chỗ nó có nghĩa.
 *
 * <h2>Đây KHÔNG phải một bản sao luật kiểm của máy chủ</h2>
 * Cùng kỷ luật với {@code set-password-form.tsx}: nút gửi không bao giờ bị vô
 * hiệu, và mọi thứ <b>không</b> nhận ra là số điện thoại đều được gửi đi để máy
 * chủ phán quyết — kể cả một địa chỉ trông lạ. Phép phân loại ở đây chỉ chọn
 * <i>câu trả lời nào hiện ra</i>, không chọn ai được đăng ký.
 *
 * <h2>Tên tự khai là TUỲ CHỌN, và nó không phải tên trong phả</h2>
 * {@code displayName} tuỳ chọn ở contract, nên nó tuỳ chọn ở đây — thêm một ô
 * bắt buộc mà máy chủ không đòi là dựng một luật thứ hai. Và câu gợi ý phải nói
 * thẳng rằng nó <b>không</b> dùng để ghép vào phả: ghép theo trùng tên là cách
 * nhanh nhất để trao cho một người quyền đọc dữ liệu Tầng 3 của người khác.
 */
export function RegisterAccountForm({
  preview,
  onSubmit,
  onEditCode,
  submitting,
  serverFieldError,
}: RegisterAccountFormProps) {
  const t = useTranslations("auth.register");
  const format = useFormatter();

  const [dinhDanh, setDinhDanh] = useState("");
  const [tenTuXung, setTenTuXung] = useState("");
  const [trong, setTrong] = useState(false);

  const loiTrongO = trong ? t("identifierEmpty") : (serverFieldError ?? null);

  function guiDi(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (dinhDanh.trim().length === 0) {
      setTrong(true);
      return;
    }
    // KHÔNG chặn theo hình dạng. Email và số điện thoại đều gửi đi được, và
    // thứ không nhận ra là cả hai cũng vậy — chỗ duy nhất phán quyết là máy
    // chủ. Gửi NGUYÊN VĂN: chuẩn hoá đầu số là luật của nó, và một bản sao
    // thứ hai ở đây là một bản sẽ lệch.
    setTrong(false);
    onSubmit({ loginId: dinhDanh.trim(), displayName: tenTuXung.trim() });
  }

  return (
    <section data-register-state="ACCOUNT" className="space-y-4">
      <div>
        <h1 className="m-0 font-serif text-de font-bold text-text-main">
          {t("accountTitle")}
        </h1>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {/* `clanName` CÓ THỂ VẮNG (một phả chưa có gốc chi thì không có tên để
              lấy), nên phải có câu dự phòng — không in một chỗ trống. */}
          {preview.clanName
            ? t("accountForClan", { clan: preview.clanName })
            : t("accountForClanUnknown")}
        </p>
        <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
          {t("accountExpiresOn", {
            date: format.dateTime(new Date(preview.expiresAt), {
              day: "numeric",
              month: "numeric",
              year: "numeric",
            }),
          })}
        </p>
      </div>

      <form onSubmit={guiDi} noValidate className="space-y-4">
        <RegisterField
          name="dinh-danh"
          label={t("identifierLabel")}
          value={dinhDanh}
          onChange={(v) => {
            setDinhDanh(v);
            if (trong) setTrong(false);
          }}
          hint={t("identifierHint")}
          error={loiTrongO}
          // `username`, không `email`: ô này nhận cả số điện thoại, và bảo trình
          // duyệt rằng đây là ô email sẽ khiến nó gợi ý sai cho một nửa số người.
          autoComplete="username"
          maxLength={254}
          autoFocus
        />

        <RegisterField
          name="ten-tu-xung"
          label={t("nameLabel")}
          value={tenTuXung}
          onChange={setTenTuXung}
          hint={t("nameHint")}
          autoComplete="name"
          maxLength={160}
        />

        <Button
          type="primary"
          htmlType="submit"
          loading={submitting}
          icon={<UserAddOutlined />}
          // Nút KHÔNG bị vô hiệu vì ô trống hay vì định dạng — xem javadoc. Chỉ
          // lúc đang gửi mới khoá, để không tiêu hai lượt của mã.
          className="min-h-[44px] w-full text-than font-semibold"
        >
          {submitting ? t("accountSubmitting") : t("accountSubmit")}
        </Button>
      </form>

      <Button
        icon={<ArrowLeftOutlined />}
        onClick={onEditCode}
        className="min-h-[44px] text-than font-semibold"
      >
        {t("editCode")}
      </Button>
    </section>
  );
}
