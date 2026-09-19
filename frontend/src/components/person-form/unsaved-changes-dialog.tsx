"use client";

import { Button, Modal, Space } from "antd";
import { ExclamationCircleFilled } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";

export interface UnsavedChangesDialogProps {
  open: boolean;
  onStay: () => void;
  onLeave: () => void;
}

/**
 * "Bạn có thay đổi chưa lưu" — hộp thoại chặn giữa người dùng và việc mất công.
 *
 * <h2>Ở lại là nút chính, và mọi cử chỉ mơ hồ đều dẫn về ở lại</h2>
 * Trên điện thoại, hộp thoại này thường bật lên ngay sau một cử chỉ vuốt lùi —
 * nghĩa là ngón tay đang ở giữa màn hình và có thể chạm tiếp bất cứ lúc nào.
 * Đặt hành động phá huỷ làm nút chính là mời người ta mất dữ liệu lần thứ hai
 * trong ba giây. Vì thế: bấm ra ngoài, bấm dấu ×, bấm Esc — tất cả đều là "ở
 * lại". Chỉ đúng một nút, có màu cảnh báo, mới rời đi.
 *
 * <h2>Nói rõ mất cái gì</h2>
 * "Bạn có chắc không?" thì không ai chắc cả. Câu ở đây nói thẳng: những gì vừa
 * nhập sẽ không được lưu vào gia phả.
 */
export function UnsavedChangesDialog({ open, onStay, onLeave }: UnsavedChangesDialogProps) {
  const t = useTranslations("personForm");

  return (
    <Modal
      open={open}
      onCancel={onStay}
      centered
      // Tháo hẳn khỏi cây khi đóng: antd mặc định giữ nội dung lại và ẩn bằng
      // CSS, khiến câu "Bạn có thay đổi chưa lưu" vẫn nằm trong DOM sau khi
      // người dùng đã chọn ở lại.
      destroyOnHidden
      title={
        <span className="flex items-center gap-2">
          <ExclamationCircleFilled style={{ color: colorVars.accentText }} aria-hidden />
          {t("unsavedDialog.title")}
        </span>
      }
      footer={
        <Space>
          <Button danger onClick={onLeave}>
            {t("unsavedDialog.leave")}
          </Button>
          <Button type="primary" autoFocus onClick={onStay}>
            {t("unsavedDialog.stay")}
          </Button>
        </Space>
      }
    >
      <p className="m-0 text-than leading-snug text-text-main">{t("unsavedDialog.body")}</p>
      <p className="m-0 mt-2 text-than text-text-muted">{t("unsavedDialog.draftHint")}</p>
    </Modal>
  );
}
