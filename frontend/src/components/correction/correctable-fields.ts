import type { DateDual, Gender, PersonDto } from "@/types/api";

/**
 * Danh mục các trường một thành viên được **đề nghị đính chính**.
 *
 * <h2>Vì sao danh sách hẹp, và hẹp có chủ ý</h2>
 * Người dùng điển hình là thành viên 30–50 tuổi, dùng thưa, mở ứng dụng vì
 * vừa nhận ra một chỗ ghi sai. Một danh sách ba mươi trường sẽ khiến họ đóng
 * lại. Sáu trường dưới đây phủ gần hết những gì người trong nhà thật sự phát
 * hiện được — dẫn đầu là **ngày mất**, vì đó là ngày giỗ, và ghi sai ngày giỗ
 * là sai lệch nặng nhất mà một cuốn gia phả có thể mắc.
 *
 * Phần còn lại (tên húy/tự/hiệu, quan hệ cha–con, chuyển chi) cố ý **không** ở
 * đây: chúng thay đổi cấu trúc cây hoặc thay cả danh sách lớp tên, và một biểu
 * mẫu một-trường không diễn đạt nổi. Những việc ấy đi qua lựa chọn `OTHER` —
 * mô tả bằng lời cho Trưởng chi đọc — cho tới khi có màn hình riêng.
 *
 * <h2>Khoá trùng tên trường của `UpdatePersonRequest`</h2>
 * `payload` gửi lên là `{ "<khoá>": <giá trị> }` với khoá đúng bằng tên trường
 * của `UpdatePersonRequest`. Đó là quy ước duy nhất khiến bước ghép W2×W6 sau
 * này áp dụng được đề nghị mà không phải dịch khoá — backend hiện chưa có
 * context nào nhận `ChangeRequestApprovedEvent`, nên quy ước này là thứ giữ chỗ
 * cho lúc ấy.
 *
 * <h2>Không suy diễn về dữ liệu bị ẩn</h2>
 * `read()` trả `undefined` khi hồ sơ **không mang** trường ấy — và theo hợp
 * đồng REST, "không có dữ liệu" với "không được phép thấy" là hai thứ không
 * phân biệt được, cố ý. Giao diện vì thế không được in "chưa có" hay một gạch
 * ngang vào cột "đang ghi": im lặng mới là cách hiển thị đúng.
 */

export type CorrectionFieldKey =
  | "death"
  | "birth"
  | "nativePlace"
  | "occupation"
  | "currentPlaceProvince"
  | "gender";

/** Lựa chọn "điều khác" — không gắn với trường nào, chỉ có lời mô tả. */
export const OTHER_TOPIC = "OTHER" as const;

export type CorrectionTopic = CorrectionFieldKey | typeof OTHER_TOPIC;

export type CorrectionFieldKind = "text" | "date" | "gender";

export interface CorrectionFieldSpec {
  key: CorrectionFieldKey;
  kind: CorrectionFieldKind;
  /** Giá trị đang ghi, hoặc `undefined` nếu hồ sơ không mang trường này. */
  read: (person: PersonDto) => string | DateDual | Gender | undefined;
}

function text(value: string | null | undefined): string | undefined {
  return value == null || value === "" ? undefined : value;
}

/**
 * Thứ tự cố ý: ngày mất trước ngày sinh. Ngày mất là ngày giỗ — nó chi phối
 * lịch nhắc của cả chi/nhánh — nên nó phải là thứ đập vào mắt đầu tiên.
 */
export const CORRECTABLE_FIELDS: readonly CorrectionFieldSpec[] = [
  { key: "death", kind: "date", read: (p) => p.death ?? undefined },
  { key: "birth", kind: "date", read: (p) => p.birth ?? undefined },
  { key: "nativePlace", kind: "text", read: (p) => text(p.nativePlace) },
  { key: "occupation", kind: "text", read: (p) => text(p.occupation) },
  { key: "currentPlaceProvince", kind: "text", read: (p) => text(p.currentPlaceProvince) },
  { key: "gender", kind: "gender", read: (p) => p.gender },
];

export function fieldSpec(key: string): CorrectionFieldSpec | undefined {
  return CORRECTABLE_FIELDS.find((f) => f.key === key);
}

/** `true` nếu khoá payload nằm trong danh mục — dùng để chọn cách hiển thị. */
export function isCorrectableField(key: string): key is CorrectionFieldKey {
  return fieldSpec(key) !== undefined;
}
