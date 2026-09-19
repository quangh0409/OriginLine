/**
 * Luồng **đính chính** (`membership.ChangeRequest`) — hai phía của cùng một
 * cây cầu:
 *
 *  - **người gửi**: `CorrectionRequestEntry` → `CorrectionRequestDialog`;
 *  - **người duyệt**: `CorrectionScreen` → `ReviewRequestCard`.
 *
 * `CorrectionRequestEntry` được đóng gói để mọi màn hình có một `PersonDto`
 * đều thả xuống được một dòng — hồ sơ nhân khẩu, ngăn kéo trên phả đồ, màn
 * hình sửa. Nó tự ẩn khi `meta.canRequestCorrection` là `false`, nên nơi gọi
 * không cần biết gì về phân quyền.
 */
export { CorrectionRequestEntry } from "./correction-request-entry";
export { CorrectionRequestDialog } from "./correction-request-dialog";
export { CorrectionScreen } from "./correction-screen";
export { CorrectionQueueLink } from "./correction-queue-link";
export { ChangeRequestStatusTag } from "./change-request-status-tag";
export { CorrectionDiff } from "./correction-diff";
export { CORRECTABLE_FIELDS, OTHER_TOPIC } from "./correctable-fields";
export type { CorrectionFieldKey, CorrectionTopic } from "./correctable-fields";
