"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import {
  ApartmentOutlined,
  ClockCircleOutlined,
  DisconnectOutlined,
  InfoCircleOutlined,
  UserAddOutlined,
} from "@ant-design/icons";
import type { ClaimFailure } from "@/lib/api/claim";
import { ClaimButton, ClaimLink, ClaimNotice, ClaimParagraph, type SacThai } from "./claim-chrome";
import { claimRoutes } from "./routes";

/**
 * Bảng tra **một lý do chặn → một màn hình**.
 *
 * <h2>Vì sao phải là một bảng, không phải một chuỗi `if`</h2>
 * Chín lý do, mỗi lý do có câu chữ riêng, sắc thái riêng và <b>lối đi tiếp
 * riêng</b>. Viết rải ra thì chắc chắn sẽ có một lý do rơi vào nhánh "mặc định"
 * và người dùng nhận một màn hình nói sai chuyện gì vừa xảy ra — đúng cái lỗi
 * mà {@code AccountStateNotice} sinh ra để chữa. Bảng này khai đủ chín, và
 * TypeScript đỏ nếu thiếu một.
 *
 * <h2>Chỗ quan trọng nhất trong cả tệp: `PERSON_UNAVAILABLE`</h2>
 * Câu chữ của nó nói <b>chung chung</b> — "ô này hiện không nhận thêm đơn" — và
 * <b>không bao giờ</b> được nói "người này đã có tài khoản". Đây không phải một
 * lựa chọn của giao diện mà là bản dịch của {@code KHONG_NHAN_DUOC}: máy chủ cố
 * ý dùng <em>một</em> câu cho ít nhất hai nguyên nhân (ô ấy đã có tài khoản ·
 * nhân khẩu đã xoá mềm), vì nói thẳng thì màn này thành công cụ dò xem ai đã
 * vào hệ thống — gõ lần lượt từng ô trên phả đồ và đọc câu trả lời. Ca kiểm
 * tương ứng <b>khẳng định chuỗi "tài khoản" không xuất hiện</b> trên màn. Đừng
 * "cải thiện" câu ấy cho rõ hơn.
 *
 * <h2>Không sắc đỏ cho một luật đang chạy đúng</h2>
 * Tám trong chín lý do mang sắc hổ phách. Sắc đỏ chỉ dành cho
 * {@code UNAVAILABLE} — chỗ duy nhất trong bảng mà hệ thống thật sự hỏng. Xem
 * {@link SacThai}.
 */
interface CachXu {
  /** Nhánh khoá trong `claim.block.*`. */
  khoa: string;
  tone: SacThai;
  /** Lối đi tiếp, nếu có một lối đi tiếp thật. */
  loiDi: "tree" | "pending" | "retry" | "newPerson" | null;
}

const BANG: Readonly<Record<ClaimFailure, CachXu>> = {
  // ── Về ô được chọn ─────────────────────────────────────────────────────
  // Nói thẳng được: người đã khuất vốn công khai với cả khách, nên chặn ở đây
  // không lộ thêm gì (checklist §1.4). Đây cũng là ca duy nhất giao diện phát
  // hiện được TRƯỚC khi gửi, từ `isAlive`.
  PERSON_DECEASED: { khoa: "deceased", tone: "hophach", loiDi: "tree" },
  // CỐ Ý chung chung. Xem javadoc ở trên.
  PERSON_UNAVAILABLE: { khoa: "personUnavailable", tone: "hophach", loiDi: "tree" },
  // Riêng lối "tôi chưa có trong phả": việc cần làm khác hẳn — chọn một NGƯỜI
  // THÂN khác, không phải chọn lại ô của chính mình.
  RELATIVE_UNUSABLE: { khoa: "relativeUnusable", tone: "hophach", loiDi: "newPerson" },

  // ── Về tài khoản đang gọi — nói rõ được, vì không kể gì về ai khác ──────
  CLAIM_ALREADY_PENDING: { khoa: "alreadyPending", tone: "hophach", loiDi: "pending" },
  // Không có lối đi tiếp trong ứng dụng, và **cố ý không bịa ra một cái**: việc
  // cần làm là gọi điện cho một con người. Một nút dẫn về phả đồ ở đây chỉ mời
  // họ thử tiếp một việc chắc chắn hỏng.
  CLAIM_LIMIT_REACHED: { khoa: "limitReached", tone: "hophach", loiDi: null },
  ACCOUNT_ALREADY_LINKED: { khoa: "accountAlreadyLinked", tone: "hophach", loiDi: "tree" },

  // ── Đường truyền và phiên ───────────────────────────────────────────────
  NEEDS_ACCOUNT: { khoa: "needsAccount", tone: "hophach", loiDi: null },
  RATE_LIMITED: { khoa: "rateLimited", tone: "hophach", loiDi: "retry" },
  UNAVAILABLE: { khoa: "unavailable", tone: "hongthat", loiDi: "retry" },
};

const BIEU_TUONG: Readonly<Record<ClaimFailure, ReactNode>> = {
  PERSON_DECEASED: <InfoCircleOutlined />,
  PERSON_UNAVAILABLE: <InfoCircleOutlined />,
  RELATIVE_UNUSABLE: <InfoCircleOutlined />,
  CLAIM_ALREADY_PENDING: <ClockCircleOutlined />,
  CLAIM_LIMIT_REACHED: <InfoCircleOutlined />,
  ACCOUNT_ALREADY_LINKED: <ApartmentOutlined />,
  NEEDS_ACCOUNT: <InfoCircleOutlined />,
  RATE_LIMITED: <ClockCircleOutlined />,
  UNAVAILABLE: <DisconnectOutlined />,
};

export interface ClaimBlockNoticeProps {
  readonly failure: ClaimFailure;
  /**
   * Tên của ô được chọn, chỉ dùng cho ca "đã khuất".
   *
   * Nêu đích danh ở đúng ca ấy là cố ý: "ô này là của một người đã khuất" nghe
   * như một lỗi hệ thống, còn "cụ Nguyễn Văn Thủy Tổ đã khuất" thì người đọc
   * hiểu ngay mình vừa bấm nhầm ai. Dữ liệu người đã khuất là công khai nên nêu
   * tên không mở thêm cửa nào.
   */
  readonly targetName?: string | null;
  readonly onRetry?: () => void;
  readonly as?: "h1" | "h2";
}

export function ClaimBlockNotice({
  failure,
  targetName,
  onRetry,
  as = "h2",
}: ClaimBlockNoticeProps) {
  const t = useTranslations("claim");
  const { khoa, tone, loiDi } = BANG[failure];
  const titleId = `claim-block-${khoa}`;

  return (
    <ClaimNotice
      as={as}
      tone={tone}
      live
      dataState={failure}
      titleId={titleId}
      icon={BIEU_TUONG[failure]}
      title={t(`block.${khoa}.title`)}
    >
      <ClaimParagraph>
        {/* Chỉ nhánh "đã khuất" nhận tham số `name`. Truyền có chọn lọc thì đọc
            mã xong là biết ngay ca nào nêu đích danh ai. */}
        {failure === "PERSON_DECEASED"
          ? t("block.deceased.body", { name: targetName ?? "" })
          : t(`block.${khoa}.body`)}
      </ClaimParagraph>

      {loiDi === "tree" && (
        <div className="flex flex-wrap gap-3">
          <ClaimLink href="/tree" bac="chinh" icon={<ApartmentOutlined />}>
            {t(`block.${khoa}.action`)}
          </ClaimLink>
          {/* Lối thứ hai, và nó là lối THẬT: rất nhiều người bấm nhầm một ô gần
              đúng chính vì họ không có trong phả. Checklist §1.5 nói thẳng rằng
              không có lối đi thì họ sẽ chọn bừa một người gần đúng. */}
          <ClaimLink href={claimRoutes.newPerson} icon={<UserAddOutlined />}>
            {t("start.notInTree")}
          </ClaimLink>
        </div>
      )}

      {loiDi === "newPerson" && (
        <div className="flex flex-wrap gap-3">
          <ClaimLink href={claimRoutes.newPerson} bac="chinh" icon={<UserAddOutlined />}>
            {t("block.relativeUnusable.action")}
          </ClaimLink>
        </div>
      )}

      {loiDi === "pending" && (
        <div className="flex flex-wrap gap-3">
          <ClaimLink href={claimRoutes.pending} bac="chinh" icon={<ClockCircleOutlined />}>
            {t("block.alreadyPending.action")}
          </ClaimLink>
        </div>
      )}

      {loiDi === "retry" && onRetry && (
        <div className="flex flex-wrap gap-3">
          <ClaimButton onClick={onRetry}>{t("retry")}</ClaimButton>
        </div>
      )}
    </ClaimNotice>
  );
}
