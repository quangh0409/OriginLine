"use client";

import { Alert, Button, Space } from "antd";
import { useTranslations, useFormatter } from "next-intl";

export interface DraftRestoreBannerProps {
  savedAt: string;
  onRestore: () => void;
  onDiscard: () => void;
}

/**
 * "Lần trước bạn đang nhập dở" — lời mời khôi phục bản nháp cục bộ.
 *
 * <h2>Nháp phải hiện ra, không được âm thầm tự điền</h2>
 * Tự nạp lại nháp sẽ khiến người dùng nhìn một biểu mẫu đã có sẵn chữ mà không
 * biết chữ ấy ở đâu ra, và nếu bản nháp cũ hơn dữ liệu trên máy chủ thì họ vô
 * tình đè bản cũ lên bản mới. Hỏi trước, và nói rõ nháp lưu lúc nào.
 *
 * <h2>Nút "Xoá nháp" nằm ngay đây, không giấu trong cài đặt</h2>
 * Nháp có thể chứa dữ liệu Tầng 3 của một người còn sống. Người dùng phải
 * **thấy** rằng có dữ liệu đang nằm trên máy này và xoá được ngay, nhất là khi
 * đang ngồi ở máy dùng chung của nhà thờ họ. (Nháp đã nằm ở `sessionStorage`
 * nên đóng tab là mất — nhưng "đóng tab" không phải là một thao tác ai cũng
 * nghĩ tới.)
 */
export function DraftRestoreBanner({ savedAt, onRestore, onDiscard }: DraftRestoreBannerProps) {
  const t = useTranslations("personForm");
  const format = useFormatter();

  return (
    <Alert
      type="warning"
      showIcon
      message={t("draft.title")}
      description={
        <div className="flex flex-col gap-2">
          <span className="text-than">
            {t("draft.savedAt", {
              time: format.dateTime(new Date(savedAt), {
                dateStyle: "short",
                timeStyle: "short",
              }),
            })}
          </span>
          <span className="text-than text-text-muted">{t("draft.privacyHint")}</span>
          <Space>
            <Button size="small" type="primary" onClick={onRestore}>
              {t("draft.restore")}
            </Button>
            <Button size="small" onClick={onDiscard}>
              {t("draft.discard")}
            </Button>
          </Space>
        </div>
      }
    />
  );
}
