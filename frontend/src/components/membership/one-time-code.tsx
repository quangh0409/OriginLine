"use client";

import { useState } from "react";
import { App, Button } from "antd";
import { CopyOutlined, WarningFilled } from "@ant-design/icons";
import { useTranslations } from "next-intl";
import { colorVars } from "@/styles/tokens";

export interface OneTimeCodeProps {
  /** Mã thô vừa nhận từ máy chủ. Không bao giờ đọc lại được. */
  code: string;
  /** Câu nói mã này dành cho ai — "Mã của ông Nguyễn Văn Bốn", hoặc nhãn mã họ. */
  caption?: string;
}

/**
 * **Mã chỉ hiện một lần** — khối dùng chung cho cả mã mời cá nhân và mã dòng họ.
 *
 * <h2>Vì sao lời cảnh báo đứng TRƯỚC mã, không phải dưới</h2>
 * Máy chủ lưu mã dạng băm, nên đóng màn là mất mã. Một dòng chú thích đặt dưới
 * một con số to sẽ không được đọc — người dùng đã lấy được thứ họ cần và đang
 * tìm nút đóng. Đặt trên thì nó nằm trên đường mắt đi tới mã.
 *
 * <h2>Chép bằng nút, đọc bằng mắt, và cả hai đều phải chạy</h2>
 * Trưởng chi làm một trong hai việc: **dán vào Zalo** (cần nút chép) hoặc
 * **đọc qua điện thoại** cho một cụ ở xa (cần mã dễ đọc). Nên mã được vẽ bằng
 * phông đều nét, cỡ lớn, giữ nguyên dấu gạch chia nhóm của máy chủ —
 * `K7M2Q-D9HFX` đọc qua điện thoại được, `K7M2QD9HFX` thì không.
 *
 * `navigator.clipboard` vắng mặt trên `http://` không phải localhost và trong
 * jsdom. Nút vẫn hiện và vẫn bấm được; hỏng thì nói thẳng "xin chép tay" thay
 * vì im lặng — im lặng ở đây nghĩa là Trưởng chi dán một chuỗi rỗng vào Zalo.
 */
export function OneTimeCode({ code, caption }: OneTimeCodeProps) {
  const t = useTranslations("membership");
  const { message } = App.useApp();
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      message.success(t("code.copied"));
    } catch {
      message.warning(t("code.copyFailed"));
    }
  };

  return (
    <div
      className="rounded-lg border p-3"
      style={{ borderColor: colorVars.accent, background: colorVars.warningBg }}
    >
      <p className="m-0 mb-2 flex items-start gap-2 text-than font-semibold text-text-main">
        <WarningFilled aria-hidden style={{ color: colorVars.accentText }} />
        <span>{t("code.onceWarning")}</span>
      </p>

      {caption && <p className="m-0 mb-1 text-than text-text-muted">{caption}</p>}

      <p
        data-testid="ma-mot-lan"
        className="m-0 select-all break-all font-mono text-[28px] font-bold leading-tight tracking-[0.12em] text-text-main"
      >
        {code}
      </p>

      <div className="mt-3 flex flex-wrap gap-2">
        <Button
          type="primary"
          size="large"
          icon={<CopyOutlined aria-hidden />}
          onClick={() => void copy()}
        >
          {copied ? t("code.copyAgain") : t("code.copy")}
        </Button>
      </div>

      <p className="m-0 mt-2 text-than text-text-muted">{t("code.reissueHint")}</p>
    </div>
  );
}
