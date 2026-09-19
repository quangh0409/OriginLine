"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Alert, App, Button, Skeleton } from "antd";
import { Link } from "@/i18n/navigation";
import { colorVars } from "@/styles/tokens";
import { dataImportApi } from "@/lib/api/data-import";
import { useImportBranches } from "./hooks/use-data-import";
import { saveDownloadedFile } from "./download";
import { useImportProblemMessage } from "./problem-message";
import { NoBranchNotice } from "./no-branch-notice";

/**
 * Màn 2 — **mẫu Excel riêng cho từng chi**, kèm hướng dẫn ngắn.
 *
 * <h2>Vì sao mẫu phải riêng từng chi chứ không phải một tệp dùng chung</h2>
 * Từ lần nhập thứ hai trở đi, mẫu mang sẵn *những người đã có trong phả* của
 * chi ấy. Đó là toàn bộ cơ chế giữ cho lần nhập sau không sinh người trùng:
 * người nhập sửa ngay trên dòng đã có mã, thay vì gõ lại từ đầu rồi nhận về 300
 * dòng `CREATE`.
 *
 * <h2>Quyền tải mẫu = quyền GHI trên chính chi đó</h2>
 * Đường dẫn nằm dưới `/api/v1/admin/**` nhưng **không** phải "chỉ System
 * Admin": khác `/admin/reminders/**`, endpoint này phục vụ đúng Trưởng chi —
 * người sẽ điền vào mẫu. Vì vậy nút tải mở theo `canImport`, đúng cờ mà
 * `POST /import/batches` sẽ kiểm lại.
 *
 * <h2>Không có "tiền tố mã của chi", và đó là chủ ý</h2>
 * Bảng `branch` không có cột `code_prefix`. Màn này từng in ra một tiền tố
 * (`NA`, `NI`…) và dạy người dùng một quy tắc **không tồn tại**: mọi quy tắc
 * tiền tố cứng sẽ đá nhau với cách đánh số thật của từng chi trong sổ giấy của
 * họ. Cột `Mã` vẫn là khoá bất biến của cả đường ống — nhưng hình dạng của nó
 * do chi tự quyết, và mẫu Excel là nơi nói điều đó, không phải trang web này.
 *
 * <h2>Hướng dẫn ở đây ngắn có chủ ý</h2>
 * Bản đầy đủ nằm ở trang đầu tiên trong chính tệp Excel — nơi người nhập thật
 * sự đang nhìn khi họ điền. Bốn ô trên màn này chỉ giữ bốn điều mà biết muộn
 * thì phải làm lại từ đầu.
 */
export function ImportTemplateScreen() {
  const t = useTranslations("dataImport.template");
  const tc = useTranslations("dataImport.common");
  const { message } = App.useApp();
  const describeProblem = useImportProblemMessage();
  const branchesQuery = useImportBranches();
  const [busyBranchId, setBusyBranchId] = useState<string | null>(null);

  if (branchesQuery.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (branchesQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message={<span className="text-dan">{tc("error")}</span>}
        description={<span className="text-than">{describeProblem(branchesQuery.error)}</span>}
      />
    );
  }

  const branches = branchesQuery.data ?? [];
  const writable = branches.filter((b) => b.canImport);

  const download = async (branchId: string, branchName: string) => {
    setBusyBranchId(branchId);
    try {
      saveDownloadedFile(
        await dataImportApi.template(
          branchId,
          `Mau-nhap-lieu-${branchName.replace(/\s+/g, "-")}.xlsx`
        )
      );
    } catch (error) {
      message.error(describeProblem(error));
    } finally {
      setBusyBranchId(null);
    }
  };

  return (
    <div className="space-y-4">
      <header>
        <h1 className="m-0 font-serif text-2xl font-bold" style={{ color: colorVars.textMain }}>
          {t("title")}
        </h1>
        <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
          {t("lead")}
        </p>
      </header>

      {writable.length === 0 ? (
        <NoBranchNotice />
      ) : (
        <ul className="m-0 list-none space-y-3 p-0">
          {writable.map((branch) => (
            <li
              key={branch.id}
              className="rounded-lg border px-4 py-3"
              style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
            >
              <h2
                className="m-0 font-serif text-de font-bold"
                style={{ color: colorVars.textMain }}
              >
                {branch.name}
              </h2>
              <p className="m-0 mt-1 text-than" style={{ color: colorVars.textMuted }}>
                {t(`kind.${branch.kind}`)}
                {" · "}
                <span className="break-all font-mono" style={{ color: colorVars.textMain }}>
                  {branch.path}
                </span>
              </p>
              <Button
                type="primary"
                size="large"
                className="mt-2 min-h-[44px] min-w-[44px]"
                loading={busyBranchId === branch.id}
                onClick={() => void download(branch.id, branch.name)}
              >
                {busyBranchId === branch.id
                  ? t("downloading")
                  : t("download", { branch: branch.name })}
              </Button>
            </li>
          ))}
        </ul>
      )}

      <section
        className="rounded-lg border px-4 py-3"
        style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
      >
        <h2 className="m-0 font-serif text-de font-bold" style={{ color: colorVars.textMain }}>
          {t("guide.title")}
        </h2>
        <dl className="m-0 mt-2 space-y-3">
          {(["sheets", "code", "lunar", "living"] as const).map((key) => (
            <div key={key}>
              <dt className="text-than font-semibold" style={{ color: colorVars.textMain }}>
                {t(`guide.${key}.title`)}
              </dt>
              <dd className="m-0 text-than" style={{ color: colorVars.textMuted }}>
                {t(`guide.${key}.body`)}
              </dd>
            </div>
          ))}
        </dl>
      </section>

      <Alert
        type="warning"
        showIcon
        message={<span className="text-dan font-semibold">{t("example.title")}</span>}
        description={<span className="text-than">{t("example.body")}</span>}
      />

      <Link
        href="/nhap-lieu/tai-len"
        className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md px-4 text-than font-medium"
        style={{ backgroundColor: colorVars.primary, color: colorVars.bgCard }}
      >
        {t("next")}
      </Link>
    </div>
  );
}
