"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { App, Button } from "antd";
import { FileExcelOutlined } from "@ant-design/icons";
import { colorVars } from "@/styles/tokens";
import { dataImportApi } from "@/lib/api/data-import";
import { saveDownloadedFile } from "./download";
import { useImportProblemMessage } from "./problem-message";

export interface IssuesWorkbookButtonProps {
  batchId: string;
}

/**
 * "Tải danh sách lỗi ra Excel" — **đường thoát**, không phải một tiện ích.
 *
 * <h2>Vì sao nó phải đứng ngay cạnh bảng lỗi</h2>
 * Người dùng thật của màn này là Trưởng chi 45–65 tuổi, và chỗ họ sửa được là
 * **chính tệp Excel**, nơi họ đã quen tay ba mươi năm — không phải một bảng
 * trên web bắt sửa từng dòng rồi tải lại. Hợp đồng nói thẳng điều đó ở mô tả
 * của `GET /issues`: nút tải phải nằm ngay cạnh bảng lỗi, vì giấu nó trong một
 * menu phụ là để đường thoát tồn tại mà **không ai tìm thấy**.
 *
 * <h2>Một nút, không phải hai</h2>
 * Tệp có ba trang — `Tổng quan` · `Lỗi phải sửa` · `Nên xem lại` — và endpoint
 * **không nhận tham số `severity`**: bộ sinh tự tách hai nhóm. Dựng thêm một
 * nút "tải riêng phần cảnh báo" là dựng một tệp mang tên "Danh sách cần sửa" mà
 * thiếu đúng phần phải sửa. Vì vậy toàn màn đối soát chỉ có **một** nút này, và
 * nó được đặt cạnh bảng nào đang là việc chính.
 *
 * <h2>Tải bằng `fetch`, không bằng một thẻ `<a href>`</h2>
 * Đây là tệp nhị phân **kèm token**: `Authorization` với bản chạy thật,
 * `x-mock-role` dưới MSW. Trình duyệt không gắn header vào một lần điều hướng
 * thường, nên một liên kết trần nhận `401` ở môi trường thật trong khi vẫn chạy
 * ngon dưới bộ giả lập. Xem {@link saveDownloadedFile}.
 *
 * <h2>Tên tệp có dấu</h2>
 * `Content-Disposition` mang **cả hai** dạng; tầng API đọc `filename*=UTF-8''`
 * **trước** `filename=`. Đọc ngược lại thì lượt tải vẫn "thành công" và người
 * dùng nhận về một tệp tên đã rụng hết dấu — hỏng im lặng, kiểu tệ nhất.
 */
export function IssuesWorkbookButton({ batchId }: IssuesWorkbookButtonProps) {
  const t = useTranslations("dataImport.workbook");
  const { message } = App.useApp();
  const describeProblem = useImportProblemMessage();
  const [busy, setBusy] = useState(false);

  const download = async () => {
    setBusy(true);
    try {
      // Tên dự phòng chỉ dùng khi server quên `Content-Disposition`. Nó cố ý
      // không dấu: nó là *dự phòng*, và một tên dự phòng có dấu che mất việc
      // header thật đang thiếu.
      saveDownloadedFile(await dataImportApi.issuesWorkbook(batchId, t("fallbackName", { batchId })));
    } catch (error) {
      message.error(`${t("failed")}: ${describeProblem(error)}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div data-testid="issues-workbook">
      <Button
        size="large"
        className="min-h-[44px] min-w-[44px]"
        loading={busy}
        onClick={() => void download()}
      >
        {/* Biểu tượng LUÔN kèm chữ — sàn tiếp cận, không đàm phán. */}
        <FileExcelOutlined aria-hidden />
        {busy ? t("downloading") : t("download")}
      </Button>
      <p className="m-0 mt-2 text-than" style={{ color: colorVars.textMuted }}>
        {t("hint")}
      </p>
    </div>
  );
}
