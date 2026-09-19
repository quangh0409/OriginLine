import type { PersonSummaryDto } from "@/types/api";

/**
 * Ô ngày trên thẻ nhân khẩu có BA trạng thái, không phải hai.
 *
 * <h2>Vì sao phải tách ra</h2>
 *
 * Sau mô hình đồng thuận V8, năm sinh của người còn sống nằm trong nhóm
 * `birthDetailAndPhoto` và nhóm ấy **mặc định đóng** — kể cả với người cùng
 * chi. Hệ quả trực tiếp: thẻ của người còn sống trống dòng ngày rất thường
 * xuyên. Trước đây nó trông y hệt thẻ của một cụ tổ chưa ai ghi năm sinh, và
 * hai chuyện ấy dẫn tới hai hành động hoàn toàn khác nhau của người xem:
 *
 *  - *chưa ai ghi* → một lời mời bổ sung, việc của dòng họ;
 *  - *chưa chia sẻ* → chuyện bình thường, không có gì để làm.
 *
 * Trộn hai thứ đó lại thì cả cuốn gia phả đọc ra như "thiếu dữ liệu".
 *
 * <h2>Vì sao phép tách này không rò rỉ</h2>
 *
 * Trạng thái chỉ phụ thuộc **hai đại lượng**: người này còn sống hay đã khuất,
 * và dòng ngày có rỗng hay không — cả hai đều đã nhìn thấy được trên thẻ. Nó
 * **không** hỏi dữ liệu có tồn tại ở máy chủ hay không (client không biết, và
 * đó là chủ ý), **không** đếm trường bị lọc, **không** nhắc tới mức chia sẻ
 * chủ thể đã đặt.
 *
 * Điểm mấu chốt: người CÒN SỐNG mà trống ngày thì **luôn** đọc ra `unshared`,
 * kể cả khi trong cơ sở dữ liệu thật sự chưa ai ghi ngày nào. Nếu để hai
 * trường hợp ấy ra hai câu khác nhau thì chính câu chữ trở thành kênh trả lời
 * câu hỏi "hồ sơ này có dữ liệu hay không" — đúng thứ phân tầng đang che. Đây
 * là cùng một bất biến mà `privacy-tier-notice.tsx` giữ: **hai hồ sơ khác nhau
 * ở cùng một tầng phải đọc ra CÙNG một câu.**
 *
 * Chiều ngược lại an toàn hiển nhiên: người đã khuất là công khai (BA v2 §10),
 * nên với họ "vắng mặt" chỉ còn đúng một nghĩa — chưa ai ghi.
 */
export type NodeDatesState =
  | { readonly kind: "known"; readonly text: string }
  | { readonly kind: "unrecorded" }
  | { readonly kind: "unshared" };

/** Người đã khuất: khuyết nửa nào thì đánh dấu nửa ấy — dữ liệu công khai, nói thẳng được. */
const UNKNOWN_HALF = "?";

/** Gạch ngang dài (–), không phải dấu trừ: đây là khoảng đời, không phải phép tính. */
const RANGE_DASH = "–";

export function nodeDatesState(
  person: Pick<PersonSummaryDto, "isAlive" | "birthYear" | "deathYear">
): NodeDatesState {
  const { isAlive, birthYear, deathYear } = person;
  const hasAnyYear = birthYear != null || deathYear != null;

  if (!hasAnyYear) return isAlive ? { kind: "unshared" } : { kind: "unrecorded" };

  if (isAlive) {
    // Người còn sống không có vế phải, và KHÔNG được điền "?" vào đó — dấu hỏi
    // ở vế mất là một câu hỏi đang chờ trả lời, còn ở đây thì không có gì để
    // trả lời cả.
    return { kind: "known", text: `${birthYear ?? UNKNOWN_HALF} ${RANGE_DASH}` };
  }

  return {
    kind: "known",
    text: `${birthYear ?? UNKNOWN_HALF} ${RANGE_DASH} ${deathYear ?? UNKNOWN_HALF}`,
  };
}
