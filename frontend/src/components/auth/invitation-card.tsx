"use client";

import { useState } from "react";
import { Button, Modal } from "antd";
import { CheckCircleOutlined, CloseCircleOutlined } from "@ant-design/icons";
import { useFormatter, useTranslations } from "next-intl";
import type { InvitationPreviewDto } from "@/lib/api/invitation";
import { colorVars } from "@/styles/tokens";

export interface InvitationCardProps {
  readonly preview: InvitationPreviewDto;
  readonly onAccept: () => void;
  readonly onDecline: () => void;
  readonly accepting?: boolean;
  readonly declining?: boolean;
  /** Câu lỗi tại chỗ khi một trong hai thao tác không đi được. */
  readonly actionError?: string | null;
}

/**
 * Lời mời còn hiệu lực — khung 1 của hình 5, thiết kế 06 §5.2.
 *
 * <h2>Màn này hiện tên một người ĐANG SỐNG, và đó là đánh đổi đã chốt</h2>
 * 06 §5.3 nêu đủ ba phương án và chọn (a) — hiện tên — với lập luận: không có
 * tên thì nút "Không phải tôi" vô nghĩa, mà nút ấy là lớp phòng vệ cuối khi lời
 * mời đi nhầm số máy. Quyết định cuối thuộc Hội đồng Tộc biểu.
 *
 * Hệ quả ràng buộc chính thành phần này: <b>mức tối thiểu để nhận ra mình, và
 * không một trường nào hơn</b>. Bốn thứ được hiện — tên, danh xưng với người
 * mời, chi/ngành, đời — đủ để một người tự nhận ra mình và không đủ để dựng hồ
 * sơ về họ. Không năm sinh, không số điện thoại, không ảnh, không nghề nghiệp.
 * Thêm một dòng ở đây là nới một quyết định của Hội đồng bằng một lượt commit.
 *
 * <h2>Sự tôn kính do `clanTitle` gánh, không do kính ngữ</h2>
 * {@code inviter.displayName} là tên <b>trần</b> — máy chủ cố ý không kèm
 * "ông"/"bà", vì kính ngữ phụ thuộc quan hệ họ hàng giữa người mời và người
 * đọc, tức thuộc bộ luật danh xưng. Thành phần này <b>không được</b> tự thêm:
 * đoán sai một chữ "ông" cho một người phụ nữ là đúng loại lỗi mà luật "quan hệ
 * là việc của máy chủ" sinh ra để chặn. Vì vậy {@code clanTitle}
 * ("Trưởng Chi Giáp") được vẽ đậm và cùng màu chữ chính chứ không mờ đi — trên
 * màn này nó là thứ duy nhất mang sắc thái kính trọng.
 *
 * <h2>Ba thứ có thể VẮNG, và màn hình vẫn phải đọc trôi chảy</h2>
 * Contract chỉ bắt buộc {@code invitee} và {@code expiresAt}. Cả khối
 * {@code inviter} lẫn {@code clanName} đều có thể không có; và
 * {@code relationToInviter} ("Con dâu ông Nguyễn Văn Bốn") <b>không tồn tại ở
 * Giai đoạn 1</b> — danh xưng thuộc context `kinship`, bộ tính danh xưng che
 * tên theo người gọi, và người gọi ở đây là khách không token. Mỗi trường vắng
 * thì dòng ấy <b>biến mất hẳn</b>, không thay bằng dấu gạch: một ô trống có
 * nhãn là đúng cái "chỗ này có dữ liệu bạn không xem được" mà cả sản phẩm cấm.
 *
 * <h2>"Không phải tôi" là thao tác KHÔNG THU HỒI được</h2>
 * Nên nó đi qua hộp thoại xác nhận nêu đích danh người và liệt kê <b>ba hệ
 * quả</b>, theo đúng khuôn mẫu hộp thoại ngày mất mà 00 §2.4 gọi là mẫu chuẩn.
 * Không hỏi suông "bạn chắc chứ?".
 */
export function InvitationCard({
  preview,
  onAccept,
  onDecline,
  accepting,
  declining,
  actionError,
}: InvitationCardProps) {
  const t = useTranslations("auth.invitation");
  const format = useFormatter();
  const [confirmOpen, setConfirmOpen] = useState(false);

  const { clanName, inviter, invitee, expiresAt } = preview;
  const inviterName = inviter?.displayName ?? null;
  const branchName = invitee.branch?.name ?? null;
  const generation = invitee.generation ?? null;

  // Ba tổ hợp, không phải một chuỗi có chỗ trống: thiếu chi thì câu phải đọc
  // trôi chảy, chứ không ra "· đời 5".
  const branchLine =
    branchName && generation != null
      ? t("branchAndGeneration", { branch: branchName, generation })
      : branchName
        ? t("branchOnly", { branch: branchName })
        : generation != null
          ? t("generationOnly", { generation })
          : null;

  return (
    <div className="space-y-4" data-invitation-state="VALID">
      <section
        aria-labelledby="invitation-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.bgCard, borderColor: colorVars.borderDark }}
      >
        {/* Tên dòng họ đứng trên tiêu đề: người nhận phải biết đây là phả nhà
            nào TRƯỚC khi đọc tới tên mình. Tuỳ chọn theo contract. */}
        {clanName && <p className="m-0 font-serif text-than text-text-muted">{clanName}</p>}
        <h1 id="invitation-title" className="m-0 mt-1 font-serif text-de font-bold text-text-main">
          {/* "Lời mời từ <một con người>", không phải "Kích hoạt tài khoản" —
              06 §5.5. Lời mời đến từ người trong họ, không từ hệ thống.
              Không có người mời thì lùi về tiêu đề trung tính, KHÔNG dựng một
              câu quanh một chỗ trống ("Lời mời từ " cụt đuôi). */}
          {inviterName ? t("fromInviter", { inviter: inviterName }) : t("pageTitle")}
        </h1>
        {inviter?.clanTitle && (
          // Đậm và cùng màu chữ chính: đây là thứ gánh sự tôn kính thay cho
          // kính ngữ mà máy chủ cố ý không gửi. Làm mờ nó đi là bỏ mất đúng
          // chi tiết phân biệt một lời mời thật với một tin nhắn lừa đảo.
          <p className="m-0 mt-1 text-than font-semibold text-text-main">{inviter.clanTitle}</p>
        )}

        <div
          className="mt-4 rounded-lg border px-4 py-4"
          style={{ background: colorVars.warningBg, borderColor: colorVars.borderDark }}
        >
          <h2 className="m-0 text-than font-semibold uppercase" style={{ color: colorVars.textMuted }}>
            {t("forWhomLabel")}
          </h2>
          {/* Tên người là DỮ LIỆU, không phải nhãn giao diện: không viết hoa
              toàn bộ, không cắt bằng dấu ba chấm — 00 §3. */}
          <p
            className="m-0 mt-1 font-serif text-dan font-bold"
            data-invitation-field="invitee-name"
            style={{ color: colorVars.textMain }}
          >
            {invitee.displayName}
          </p>
          {invitee.relationToInviter && (
            <p className="m-0 mt-1 text-than text-text-main">{invitee.relationToInviter}</p>
          )}
          {branchLine && <p className="m-0 mt-1 text-than text-text-muted">{branchLine}</p>}
        </div>

        <h2 className="m-0 mt-4 text-than font-semibold text-text-main">{t("benefitsTitle")}</h2>
        <ul className="m-0 mt-2 list-disc space-y-1 pl-5 text-than leading-relaxed text-text-main">
          <li>{t("benefit1")}</li>
          <li>{t("benefit2")}</li>
          <li>{t("benefit3")}</li>
          <li>{t("benefit4")}</li>
        </ul>

        <p
          className="m-0 mt-4 max-w-prose text-than leading-relaxed"
          style={{ color: colorVars.successText }}
        >
          {t("noWaiting")}
        </p>
        <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
          {t("nextStep")}
        </p>
        <p className="m-0 mt-2 max-w-prose text-than leading-relaxed text-text-muted">
          {t("expiresOn", {
            date: format.dateTime(new Date(expiresAt), {
              day: "numeric",
              month: "numeric",
              year: "numeric",
            }),
          })}
        </p>

        {actionError && (
          <p
            role="alert"
            data-invitation-error="action"
            className="m-0 mt-4 max-w-prose text-than leading-relaxed"
            style={{ color: colorVars.danger }}
          >
            {actionError}
          </p>
        )}

        <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center">
          <Button
            type="primary"
            icon={<CheckCircleOutlined />}
            loading={accepting}
            onClick={onAccept}
            data-invitation-action="accept"
          >
            {accepting ? t("accepting") : t("accept")}
          </Button>
          <Button
            icon={<CloseCircleOutlined />}
            danger
            loading={declining}
            onClick={() => setConfirmOpen(true)}
            data-invitation-action="decline"
          >
            {t("decline")}
          </Button>
        </div>

        <p className="m-0 mt-3 max-w-prose text-than leading-relaxed text-text-muted">
          {t("declineHint")}
        </p>
      </section>

      <Modal
        open={confirmOpen}
        title={t("declineConfirmTitle")}
        okText={t("declineConfirmOk")}
        cancelText={t("declineConfirmCancel")}
        okButtonProps={{ danger: true }}
        confirmLoading={declining}
        onCancel={() => setConfirmOpen(false)}
        onOk={() => {
          setConfirmOpen(false);
          onDecline();
        }}
      >
        <p className="m-0 text-than leading-relaxed text-text-main">
          {t("declineConfirmBody", { name: invitee.displayName })}
        </p>
      </Modal>
    </div>
  );
}
