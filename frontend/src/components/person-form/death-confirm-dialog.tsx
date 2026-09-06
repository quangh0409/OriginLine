"use client";

import { Alert, Button, Modal } from "antd";
import { ExclamationCircleFilled } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { DualDate } from "@/components/person/dual-date";
import { colorTokens } from "@/styles/tokens";
import type { DeathConfirmationRequest } from "@/hooks/use-person-submit";

export interface DeathConfirmDialogProps {
  /** `null` khi không có gì phải hỏi — hộp thoại đóng. */
  request: DeathConfirmationRequest | null;
  submitting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * "Bạn đang nhập thông tin người mất, bạn chắc chắn chứ" — chốt chặn cuối trước một thao tác
 * gần như không thu hồi được.
 *
 * <h2>Vì sao phải viết kỹ đến thế</h2>
 * Đánh dấu một người là đã mất KHÔNG chỉ đổi một ô đánh dấu. Theo BA v2 §10, hồ sơ người đã
 * khuất là thông tin công khai — xem được cả khi chưa đăng nhập — trong khi người đang sống mặc
 * định được che và chỉ lộ dần theo tầng T1/T2/T3. Cùng lúc đó, ngày mất âm lịch là căn cứ sinh
 * lịch nhắc giỗ gửi cho cả chi/nhánh. Một cú gõ nhầm ngày vào hồ sơ người đang sống vì thế vừa
 * phát tán thông tin cá nhân của họ ra ngoài, vừa báo tin dữ cho cả họ. Sửa lại thì được, nhưng
 * thứ đã lộ ra thì không thu về được — nên hộp thoại này nêu đủ HỆ QUẢ chứ không hỏi trống không,
 * và nêu đích danh người bị ảnh hưởng.
 *
 * <h2>Vì sao chiếu lại ngày vừa nhập</h2>
 * Rủi ro cần chặn là gõ nhầm ngày, mà một câu hỏi "bạn chắc chứ?" thì không giúp ai phát hiện ra
 * mình gõ nhầm. Ngày được chiếu lại bằng chính {@link DualDate} mà hồ sơ dùng, đủ cả âm lịch và
 * dấu tháng nhuận, để người dùng đọc lại đúng thứ sắp lưu.
 *
 * Không bắt gõ lý do như hộp thoại kỵ húy: kỵ húy phải ghi vào nhật ký vì đó là một ngoại lệ với
 * lệ họ, còn ghi nhận một người đã mất là việc bình thường của gia phả. Ở đây chỉ cần chắc chắn.
 */
export function DeathConfirmDialog({
  request,
  submitting,
  onCancel,
  onConfirm,
}: DeathConfirmDialogProps) {
  const t = useTranslations("personForm");
  const open = request !== null;

  return (
    <Modal
      open={open}
      onCancel={onCancel}
      maskClosable={!submitting}
      title={
        <span className="flex items-center gap-2">
          <ExclamationCircleFilled style={{ color: colorTokens.primary }} />
          {t("deathConfirm.title")}
        </span>
      }
      width={560}
      footer={[
        // Huỷ đứng trước và là nút thường; xác nhận là nút `danger` — thứ tự và sắc độ nói rằng
        // lối an toàn là quay lại xem kỹ, không phải bấm tiếp cho xong.
        <Button key="cancel" onClick={onCancel} disabled={submitting}>
          {t("deathConfirm.cancel")}
        </Button>,
        <Button
          key="confirm"
          danger
          type="primary"
          loading={submitting}
          onClick={onConfirm}
        >
          {t("deathConfirm.confirm")}
        </Button>,
      ]}
    >
      <Alert
        type="warning"
        showIcon={false}
        className="!mb-3"
        message={t("deathConfirm.lead", { name: request?.personName ?? "" })}
        description={t("deathConfirm.question")}
      />

      {request?.deathDate && (
        <div
          className="mb-3 rounded border px-3 py-2"
          style={{ borderColor: colorTokens.borderDark, background: colorTokens.warningBg }}
        >
          <div className="text-[13px] font-medium text-text-main">
            {t("deathConfirm.dateLabel")}
          </div>
          <div className="mt-0.5 font-serif text-[15px] text-text-main">
            <DualDate date={request.deathDate} />
          </div>
          <div className="mt-1 text-[12.5px] text-text-muted">
            {t("deathConfirm.dateHint")}
          </div>
        </div>
      )}

      <p className="m-0 mb-1 text-[13px] font-medium text-text-main">
        {t("deathConfirm.consequencesTitle")}
      </p>
      <ul className="m-0 mb-2 list-disc space-y-1 pl-5 text-[13px] leading-relaxed text-text-main">
        <li>{t("deathConfirm.consequencePublic", { name: request?.personName ?? "" })}</li>
        <li>{t("deathConfirm.consequenceGio")}</li>
        <li>{t("deathConfirm.consequenceIrreversible")}</li>
      </ul>
    </Modal>
  );
}
