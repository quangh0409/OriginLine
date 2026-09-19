import { personHandlers } from "./persons";
import { treeHandlers } from "./tree";
import { kinshipHandlers } from "./kinship";
import { eventHandlers } from "./events";
import { notificationHandlers } from "./notifications";
import { pushHandlers } from "./push";
import { graphqlHandlers } from "./graphql";
import { changeRequestHandlers } from "./change-requests";
import { meHandlers } from "./me";
import { dataImportHandlers } from "./data-import";
import { directoryHandlers } from "./directory";
import { publicPortalHandlers } from "./public-portal";
import { invitationHandlers } from "./invitation";

export const handlers = [
  ...personHandlers,
  // Danh bạ dòng họ — bề mặt đồng thuận, tách hẳn khỏi `/persons/search`.
  ...directoryHandlers,
  // Cổng công khai (`/api/v1/public/**`). Đặt ngay cạnh `treeHandlers` để ai
  // đọc cũng thấy hai bề mặt song song: `/tree` cần phiên, `/public/tree` thì
  // không, và chúng KHÔNG phải một endpoint với hai mức quyền.
  ...publicPortalHandlers,
  ...treeHandlers,
  ...kinshipHandlers,
  ...eventHandlers,
  ...notificationHandlers,
  ...pushHandlers,
  ...graphqlHandlers,
  // Luồng đính chính (W6) + hồ sơ phiên làm việc. Đặt sau cùng vì hai nhóm
  // này không giành đường với nhóm nào ở trên.
  ...changeRequestHandlers,
  ...meHandlers,
  // Lời mời vào phả (`/api/v1/invitations/**`). Nhóm duy nhất phục vụ người
  // CHƯA có tài khoản, nên nó không giành đường với nhóm nào ở trên và cũng
  // không đọc `x-mock-role`.
  ...invitationHandlers,
  // Đường ống nhập liệu ban đầu (kế hoạch 02). Đặt cuối: nhóm `/api/v1/import/**`
  // và `/api/v1/admin/branches/:id/import-template.xlsx` không giành đường với
  // bất kỳ nhóm nào ở trên.
  ...dataImportHandlers,
];
