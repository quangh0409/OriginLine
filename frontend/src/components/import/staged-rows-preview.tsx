"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Alert, Skeleton } from "antd";
import { colorVars } from "@/styles/tokens";
import { VungCuonNgang } from "@/components/common/vung-cuon-ngang";
import { useImportRows } from "./hooks/use-data-import";
import { useImportProblemMessage } from "./problem-message";

export interface StagedRowsPreviewProps {
  batchId: string;
  /** Con số máy chủ đếm, để tiêu đề khớp với phần còn lại của màn hình. */
  plannedCreateCount: number;
  plannedUpdateCount: number;
}

/**
 * Xem trước **các dòng đang chờ** — câu trả lời cho nỗi lo lớn nhất khi nộp lần
 * thứ hai: *tải lại có sinh ra người trùng không?*
 *
 * <h2>`plannedAction` là câu trả lời, và nó suy từ MÃ chứ không từ tên</h2>
 * `UPDATE` nghĩa là mã này đã có chủ trong phả và sẽ cập nhật đúng người ấy;
 * `CREATE` nghĩa là sẽ thêm mới. Con số suy từ `person_external_ref`. Đó là lý
 * do cột Mã được nhấn mạnh suốt từ màn mẫu Excel tới đây.
 *
 * <h2>Đóng sẵn, và chỉ hỏi máy chủ khi người dùng mở</h2>
 * 318 dòng không rẻ trên 3G ở sân nhà thờ họ. `<details>` giữ nó ngoài đường đi
 * chính mà vẫn có mặt khi cần — và nó là một điều khiển thật, mở được bằng bàn
 * phím, không phải một `div` giả làm nút.
 *
 * <h2>Không có dữ liệu nào của phả ở đây</h2>
 * Kể cả `resolvedPersonId` cũng chỉ là một khoá, nên nó **không** được hiển thị
 * thành tên: muốn biết người ấy là ai thì mở hồ sơ, nơi bộ lọc phân tầng riêng
 * tư quyết định trường nào được xem.
 */
export function StagedRowsPreview({
  batchId,
  plannedCreateCount,
  plannedUpdateCount,
}: StagedRowsPreviewProps) {
  const t = useTranslations("dataImport.rows");
  const describeProblem = useImportProblemMessage();
  const [opened, setOpened] = useState(false);
  const rowsQuery = useImportRows(batchId, { enabled: opened });

  return (
    <details
      className="rounded-lg border px-4 py-3"
      style={{ borderColor: colorVars.border, backgroundColor: colorVars.bgCard }}
      onToggle={(event) => setOpened((event.currentTarget as HTMLDetailsElement).open)}
    >
      <summary
        className="min-h-[44px] cursor-pointer text-than font-medium"
        style={{ color: colorVars.textMain }}
      >
        {t("summary", { create: plannedCreateCount, update: plannedUpdateCount })}
      </summary>

      <div className="mt-3">
        <p className="m-0 text-than" style={{ color: colorVars.textMuted }}>
          {t("why")}
        </p>

        {!opened ? null : rowsQuery.isPending ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : rowsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            className="mt-2"
            message={<span className="text-than">{describeProblem(rowsQuery.error)}</span>}
          />
        ) : (
          /* Bảng nằm TRONG vùng cuộn riêng của nó. Bốn cột — số dòng, mã, họ tên,
             việc sẽ làm — không co xuống vừa một màn 400px được, và một `<table>`
             đặt trần thì đẩy thẻ rộng ra rồi kéo cả trang cuộn ngang. */
          <VungCuonNgang nhan={t("caption")}>
            <table className="mt-2 w-full border-collapse text-than">
              <caption className="sr-only">{t("caption")}</caption>
              <thead>
                <tr>
                  {(["row", "code", "name", "action"] as const).map((key) => (
                    <th
                      key={key}
                      scope="col"
                      className="border-b px-2 py-1 text-left text-than font-semibold"
                      style={{ borderColor: colorVars.border, color: colorVars.textMuted }}
                    >
                      {t(`columns.${key}`)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {(rowsQuery.data ?? []).map((row) => (
                  <tr key={row.rowNo} data-testid={`staged-row-${row.rowNo}`}>
                    <td
                      className="border-b px-2 py-2 align-top"
                      style={{ borderColor: colorVars.border, color: colorVars.textMain }}
                    >
                      {row.rowNo}
                    </td>
                    <td
                      className="break-all border-b px-2 py-2 align-top font-mono"
                      style={{ borderColor: colorVars.border, color: colorVars.textMain }}
                    >
                      {row.externalCode ?? "—"}
                    </td>
                    <td
                      className="border-b px-2 py-2 align-top"
                      style={{ borderColor: colorVars.border, color: colorVars.textMain }}
                    >
                      {row.fullName ?? "—"}
                    </td>
                    <td
                      className="border-b px-2 py-2 align-top font-medium"
                      style={{
                        borderColor: colorVars.border,
                        // Màu VÀ chữ: `UPDATE` và `CREATE` không bao giờ được
                        // phân biệt chỉ bằng sắc độ.
                        color:
                          row.plannedAction === "UPDATE"
                            ? colorVars.successText
                            : colorVars.textMain,
                      }}
                    >
                      {t(`action.${row.plannedAction}`)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </VungCuonNgang>
        )}
      </div>
    </details>
  );
}
