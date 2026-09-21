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
import { clanInviteHandlers } from "./clan-invite";
import { claimHandlers } from "./claim";
import { membershipAdminHandlers } from "./membership-admin";
import { postHandlers } from "./posts";
import { honourHandlers } from "./honours";

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
  // Mã mời DÒNG HỌ, nửa của người cầm mã (`/clan-invites/lookup`,
  // `/clan-invites/register`). Đặt ngay cạnh `invitationHandlers` vì cùng phục
  // vụ người CHƯA có tài khoản và cùng không đọc `x-mock-role` — nhưng là một
  // cơ chế RIÊNG, không phải biến thể của lời mời cá nhân (design 07 §1.1).
  // Phải đứng TRƯỚC `membershipAdminHandlers`, nơi giữ nửa quản trị của cùng
  // nhóm: hai nửa không giành đường nhau, nhưng thứ tự này giữ đúng nguyên tắc
  // "đường không cần token đứng trước đường đọc vai".
  ...clanInviteHandlers,
  // Đơn tự nhận mình trong phả (`/api/v1/person-claims/**`). Nhóm duy nhất phục vụ
  // người ĐÃ có tài khoản mà CHƯA gắn nhân khẩu — trạng thái mà bộ chuyển vai
  // dev không biểu diễn được, nên nhóm này giữ trạng thái riêng thay vì đọc
  // `x-mock-role` (chỉ đọc đúng một việc: khách thì `401`).
  ...claimHandlers,
  // Nửa QUẢN TRỊ của tư cách thành viên: hàng chờ duyệt đơn
  // (`/person-claims/pending`, `/person-claims/{id}/review`), mã mời dòng họ
  // (`/clan-invites/**`) và nửa quản trị của lời mời cá nhân
  // (`GET`/`POST`/`DELETE /invitations`). Đặt SAU `claimHandlers` vì nó phải
  // đọc `x-mock-role`, khác hẳn hai nhóm trên.
  //
  // ⚠️ Thứ tự này KHÔNG vô hại: `claimHandlers` có `GET /person-claims/:id`, và
  // nó sẽ nuốt `GET /person-claims/pending` nếu không nhường đường. Chỗ nhường
  // nằm trong `handlers/claim.ts` (`DOAN_DUONG_DANH_RIENG`) — đọc javadoc ở đó
  // trước khi thêm một đường `/person-claims/<chữ>` mới vào tệp này.
  ...membershipAdminHandlers,
  // Đường ống nhập liệu ban đầu (kế hoạch 02). Đặt cuối: nhóm `/api/v1/import/**`
  // và `/api/v1/admin/branches/:id/import-template.xlsx` không giành đường với
  // bất kỳ nhóm nào ở trên.
  ...dataImportHandlers,
  // Bài viết dòng họ và vinh danh (Đợt 2 — trang chủ thật). Hai nhóm đường
  // riêng (`/api/v1/posts/**`, `/api/v1/honours/**`), không giành đường với gì
  // ở trên. `postHandlers` tự nhường đường nội bộ: `GET /posts/feed` đăng ký
  // TRƯỚC `GET /posts/:id` để không bị nuốt thành một `id` tên "feed".
  ...postHandlers,
  ...honourHandlers,
];
