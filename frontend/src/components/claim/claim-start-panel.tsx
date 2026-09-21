"use client";

import { useTranslations } from "next-intl";
import { ApartmentOutlined, ClockCircleOutlined, UserAddOutlined } from "@ant-design/icons";
import type { ClaimView } from "@/lib/api/claim";
import { colorVars } from "@/styles/tokens";
import { ClaimLink, ClaimNotice, ClaimParagraph } from "./claim-chrome";
import { claimRoutes } from "./routes";

/**
 * Màn mở đầu của `/nhan-dien` — **chưa chọn ai**.
 *
 * <h2>Nó tồn tại vì bước "tự nhận mình" chạy trên MỘT màn khác</h2>
 * Người dùng phải tìm chính mình trong 1.500 người, và chỗ làm được việc ấy là
 * phả đồ chứ không phải trang này. Nên trang này không cố thay thế phả đồ; nó
 * làm ba việc mà phả đồ không làm được: nói <b>vì sao</b> phải chọn một ô,
 * nói <b>ba bước</b> sẽ diễn ra, và mở <b>lối thứ hai</b> cho người không có
 * mặt trong phả.
 *
 * <h2>Hai nút, ngang hàng nhau</h2>
 * Tài liệu 00 §1b dặn: cách hỏi lúc nhập môn phải <b>song song</b>, tránh mọi
 * cách diễn đạt dựng lên khung "người trong họ" đối lập "người ngoài". "Tôi
 * chưa có trong phả" vì thế không phải một liên kết chữ nhỏ ở cuối trang mà là
 * một nút thật, đặt cạnh nút kia, kèm một câu nói rõ nó dành cho ai — con dâu
 * con rể mới về, cháu mới sinh, nhánh ở xa. Ba người ấy là người dùng bình
 * thường của hệ thống, không phải ca ngoại lệ.
 *
 * <h2>Người đã gửi đơn rồi thì được nói ngay, ở trên cùng</h2>
 * Không có câu ấy thì họ gửi lại lần hai, lần ba — đúng lý do checklist §1.3
 * đòi phải có màn "đang chờ duyệt".
 */
export interface ClaimStartPanelProps {
  readonly claims: ClaimView[];
  /**
   * Chưa biết thì **chưa nói gì**.
   *
   * Mảng rỗng lúc đang tải trông y hệt mảng rỗng của người chưa gửi đơn bao
   * giờ, nên thiếu cờ này thì khối "ông/bà đã gửi đơn rồi" sẽ nhấp nháy hiện ra
   * sau một nhịp — đúng kiểu giật mà người đọc hiểu thành "màn hình vừa đổi ý".
   */
  readonly loading?: boolean;
}

export function ClaimStartPanel({ claims, loading }: ClaimStartPanelProps) {
  const t = useTranslations("claim");
  const dangCho = !loading && claims.some((c) => c.status === "PENDING");

  return (
    <div className="space-y-4">
      {dangCho && (
        <ClaimNotice
          tone="hophach"
          dataState="HAS_PENDING"
          titleId="claim-start-pending"
          icon={<ClockCircleOutlined />}
          title={t("start.pendingTitle")}
        >
          <ClaimParagraph>{t("block.alreadyPending.body")}</ClaimParagraph>
          <div>
            <ClaimLink href={claimRoutes.pending} bac="chinh" icon={<ClockCircleOutlined />}>
              {t("start.pendingLink")}
            </ClaimLink>
          </div>
        </ClaimNotice>
      )}

      <section
        aria-labelledby="claim-start-title"
        className="rounded-lg border px-4 py-5 sm:px-6"
        style={{ background: colorVars.bgCard, borderColor: colorVars.borderDark }}
      >
        <h1
          id="claim-start-title"
          className="m-0 font-serif text-de font-bold text-text-main"
        >
          {t("start.title")}
        </h1>
        <p className="m-0 mt-3 max-w-prose text-dan leading-relaxed text-text-main">
          {t("start.lead")}
        </p>

        <h2 className="m-0 mt-5 text-than font-semibold text-text-muted">
          {t("start.stepsTitle")}
        </h2>
        {/* Danh sách CÓ THỨ TỰ, không phải ba dòng có chấm tròn: thứ tự ở đây là
            nội dung. Trình đọc màn hình đọc "1 trên 3" và người dùng biết mình
            đang ở đâu trong một việc gồm ba chặng. */}
        <ol className="m-0 mt-2 list-decimal space-y-2 pl-6 text-dan leading-relaxed text-text-main">
          <li>{t("start.step1")}</li>
          <li>{t("start.step2")}</li>
          <li>{t("start.step3")}</li>
        </ol>
      </section>

      {/* Hai lối đi, ngang hàng. Trên điện thoại chúng xếp dọc — 400px không đủ
          cho hai nút 44px cạnh nhau mà nhãn vẫn đọc được nguyên chữ. */}
      <div className="grid gap-4 sm:grid-cols-2">
        <LoiDi
          title={t("start.openTree")}
          hint={t("start.openTreeHint")}
          href="/tree"
          bac="chinh"
          icon={<ApartmentOutlined />}
        />
        <LoiDi
          title={t("start.notInTree")}
          hint={t("start.notInTreeHint")}
          href={claimRoutes.newPerson}
          icon={<UserAddOutlined />}
        />
      </div>
    </div>
  );
}

function LoiDi({
  title,
  hint,
  href,
  bac,
  icon,
}: {
  title: string;
  hint: string;
  href: string;
  bac?: "chinh" | "phu";
  icon: React.ReactNode;
}) {
  return (
    <div
      className="flex flex-col gap-3 rounded-lg border px-4 py-4"
      style={{ background: colorVars.bgCard, borderColor: colorVars.borderDark }}
    >
      <ClaimLink href={href} bac={bac} icon={icon}>
        {title}
      </ClaimLink>
      <p className="m-0 text-than leading-snug text-text-muted">{hint}</p>
    </div>
  );
}
