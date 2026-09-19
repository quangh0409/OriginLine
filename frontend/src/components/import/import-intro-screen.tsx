"use client";

import { useTranslations, useFormatter } from "next-intl";
import { Alert, Skeleton } from "antd";
import { SafetyCertificateOutlined } from "@ant-design/icons";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { useImportBatches } from "./hooks/use-data-import";
import { ImportSteps } from "./import-steps";

/**
 * Màn 1 — **quy trình năm bước**, và câu trả lời cho "tôi đang ở đâu".
 *
 * <h2>Vì sao trang dẫn nhập này không phải là trang thừa</h2>
 * Một người phụ trách nhập chi mình sẽ mở phần mềm này khoảng mười lần, cách
 * nhau nhiều ngày. Mỗi lần mở lại, câu hỏi đầu tiên trong đầu họ không phải
 * "nút nào" mà là "lần trước tôi làm đến đâu rồi". Không trả lời được câu ấy
 * thì họ mở lại tất cả các màn để dò — và mỗi lần dò là một lần có nguy cơ bấm
 * nhầm vào chỗ không nên bấm.
 *
 * <h2>Khối "làm tiếp lô đang dở" đọc từ MÁY CHỦ</h2>
 * Không có `localStorage` nào ở đây, và sẽ không có: việc đang dở phải hiện ra
 * đúng như nhau dù người dùng đổi máy, đổi trình duyệt, hay đóng trình duyệt
 * suốt một tuần. Đó là cách duy nhất đúng cho một luồng kéo dài 6–10 buổi, và
 * `GET /import/batches` là endpoint làm cho nó chạy được.
 *
 * Mảng rỗng ở đây có đúng một nghĩa và nghĩa ấy an toàn: người gọi thật sự
 * không có lô nào. Chi ngoài phạm vi không bao giờ rơi vào đây — hỏi một chi
 * không phải của mình thì máy chủ trả `403`, không trả một danh sách rỗng.
 */
export function ImportIntroScreen() {
  const t = useTranslations("dataImport.intro");
  const tRoot = useTranslations("dataImport");
  const format = useFormatter();
  // Chỉ hỏi những lô CHƯA CHỐT, và chỉ vài lô đầu: màn này cần đúng một câu trả
  // lời ("còn gì dở không"), không cần một bản kết xuất.
  const batchesQuery = useImportBatches();

  // Lô đang dở = lô chưa chốt. Máy chủ sắp giảm dần theo thời điểm tải lên, và
  // một chi chỉ có tối đa một lô đang dở.
  const openBatch = (batchesQuery.data ?? []).find(
    (batch) =>
      batch.status === "DRAFT" ||
      batch.status === "PARSED" ||
      batch.status === "VALIDATING" ||
      batch.status === "VALIDATED" ||
      batch.status === "FAILED" ||
      batch.status === "COMMITTING"
  );

  const currentStep = openBatch ? 4 : 3;

  return (
    <div className="space-y-4">
      <header>
        <h1 className="m-0 font-serif text-2xl font-bold" style={{ color: colorVars.textMain }}>
          {tRoot("title")}
        </h1>
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
          {tRoot("subtitle")}
        </p>
        <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMain }}>
          {t("lead")}
        </p>
      </header>

      <section
        className="rounded-lg border px-4 py-3"
        style={{ borderColor: colorVars.successText, backgroundColor: colorVars.successBg }}
      >
        <h2
          className="m-0 flex items-center gap-2 font-serif text-de font-bold"
          style={{ color: colorVars.successText }}
        >
          <SafetyCertificateOutlined aria-hidden />
          {t("reassure.title")}
        </h2>
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMain }}>
          {t("reassure.body")}
        </p>
      </section>

      {batchesQuery.isPending ? (
        <Skeleton active paragraph={{ rows: 2 }} />
      ) : (
        openBatch && (
          <Alert
            type="info"
            showIcon
            message={<span className="text-dan font-semibold">{t("resume.title")}</span>}
            description={
              <div className="text-than">
                <p className="m-0">
                  {t("resume.body", {
                    batchId: openBatch.id,
                    branch: openBatch.branchName ?? openBatch.branchId,
                    when: format.dateTime(new Date(openBatch.uploadedAt), {
                      dateStyle: "medium",
                      timeStyle: "short",
                    }),
                  })}
                </p>
                <Link
                  href={`/nhap-lieu/${openBatch.id}`}
                  data-testid="resume-batch-link"
                  className="mt-2 inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md px-4 text-than font-medium"
                  style={{ backgroundColor: colorVars.primary, color: colorVars.bgCard }}
                >
                  {t("resume.action")}
                </Link>
              </div>
            }
          />
        )
      )}

      <ImportSteps current={currentStep} />

      <nav aria-label={tRoot("title")} className="flex flex-col gap-2 sm:flex-row">
        {(
          [
            ["/nhap-lieu/mau", t("actions.template")],
            ["/nhap-lieu/tai-len", t("actions.upload")],
            ["/nhap-lieu/tien-do", t("actions.progress")],
          ] as const
        ).map(([href, label]) => (
          <Link
            key={href}
            href={href}
            className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md border px-4 text-than font-medium"
            style={{
              borderColor: colorVars.borderDark,
              backgroundColor: colorVars.bgCard,
              color: colorVars.textMain,
            }}
          >
            {label}
          </Link>
        ))}
      </nav>

      <p className="m-0 text-than" style={{ color: colorVars.textMuted }}>
        {t("sessionNote")}
      </p>
    </div>
  );
}
