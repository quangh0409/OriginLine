"use client";

import { useTranslations } from "next-intl";
import { Alert } from "antd";

/**
 * "Bạn chưa được giao chi nào để nhập liệu."
 *
 * <h2>Câu trả lời này đến từ `canImport`, không từ một danh sách rỗng</h2>
 * `GET /import/branches` trả **cả cây** chi cho mọi tài khoản đã khởi tạo, nên
 * một mảng rỗng ở đây không còn nghĩa là "bạn không có quyền" — nó chỉ xảy ra
 * khi dòng họ chưa khai chi nào. Điều kiện thật của màn này là **không chi nào
 * có `canImport`**.
 *
 * Phân biệt ấy quan trọng: một mảng rỗng vừa có thể là "không có gì" vừa có thể
 * là "bạn không có quyền", và hai câu ấy dẫn người dùng đi hai hướng hoàn toàn
 * khác nhau.
 *
 * Câu trả lời vì thế phải chỉ đúng chỗ đi tiếp: việc giao chi là của Hội đồng
 * Tộc biểu, không phải của đội kỹ thuật và cũng không phải một nút nào trên màn
 * hình này. Một câu "không có quyền truy cập" trơ trọi sẽ sinh ra một cuộc gọi
 * cho người viết phần mềm, và người viết phần mềm không giải quyết được.
 */
export function NoBranchNotice() {
  const t = useTranslations("dataImport.common");

  return (
    <Alert
      type="info"
      showIcon
      message={<span className="text-dan font-semibold">{t("noBranchTitle")}</span>}
      description={<span className="text-than">{t("noBranchBody")}</span>}
    />
  );
}
