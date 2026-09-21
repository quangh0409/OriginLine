import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/http";
import {
  claimFailureOf,
  claimsOf,
  isTargetBlock,
  quotaOf,
  type ClaimView,
} from "@/lib/api/claim";
import type { Problem, ProblemCode } from "@/types/api";

/**
 * ÁNH XẠ LỖI CỦA NHÓM `person-claims` — phần mỏng nhất và dễ sai nhất của cả
 * luồng.
 *
 * <h2>Bản trước SUY THEO LỐI; nay rẽ thẳng theo mã, và đó là cả điểm của đợt này</h2>
 * Khi máy chủ còn ném `VALIDATION_FAILED` cho bốn tình huống có bốn lối đi tiếp
 * khác nhau, giao diện phải đoán bằng lối đang chạy:
 *
 * <pre>
 *   case "VALIDATION_FAILED":
 *     return flow === "NEW_PERSON" ? "RELATIVE_UNUSABLE" : "PERSON_UNAVAILABLE";
 * </pre>
 *
 * Đó là một **suy luận**, đúng hôm ấy và sẽ sai vào ngày backend thêm một phép
 * kiểm thứ năm cùng mã. Contract nay có `CLAIM_TARGET_UNAVAILABLE`,
 * `CLAIM_RELATIVE_UNUSABLE` và `CLAIM_ALREADY_OPEN`, nên phép suy đã gỡ. Những
 * ca dưới đây ghim **cách rẽ mới**, và ghim luôn hai bất biến không được mất
 * theo: một mã gộp thì không được tách ra, và một trục trặc hạ tầng thì không
 * bao giờ được đọc thành một lời từ chối.
 */

function loi(status: number, code?: ProblemCode): ApiError {
  const problem: Problem | undefined =
    code === undefined
      ? undefined
      : { type: "about:blank", title: "", status, code };
  return new ApiError(status, problem);
}

describe("rẽ thẳng theo mã — không còn suy theo lối", () => {
  it("`CLAIM_TARGET_UNAVAILABLE` → 'ô này không nhận đơn', ở CẢ HAI lối", () => {
    // Mã này gộp BA lý do — đã khuất · đã có tài khoản · đã xoá mềm — và gộp
    // lại là *điểm* của nó. Nếu một ngày ánh xạ này tách ra theo `detail` thì
    // màn tự nhận thành công cụ liệt kê ai đã vào hệ thống.
    expect(claimFailureOf(loi(422, "CLAIM_TARGET_UNAVAILABLE"), "EXISTING")).toBe(
      "PERSON_UNAVAILABLE"
    );
    expect(claimFailureOf(loi(422, "CLAIM_TARGET_UNAVAILABLE"), "NEW_PERSON")).toBe(
      "PERSON_UNAVAILABLE"
    );
  });

  it("`CLAIM_RELATIVE_UNUSABLE` → 'người thân không dùng được', KHÔNG phụ thuộc lối", () => {
    // Việc cần làm khác hẳn: "chọn một người thân khác", không phải "chọn ô
    // khác của chính mình".
    expect(claimFailureOf(loi(422, "CLAIM_RELATIVE_UNUSABLE"), "NEW_PERSON")).toBe(
      "RELATIVE_UNUSABLE"
    );
    expect(claimFailureOf(loi(422, "CLAIM_RELATIVE_UNUSABLE"), "EXISTING")).toBe(
      "RELATIVE_UNUSABLE"
    );
  });

  it("`CLAIM_ALREADY_OPEN` → màn 'bạn đang có một đơn chờ', nơi có nút RÚT", () => {
    // Lối đi tiếp rất cụ thể và giao diện làm hộ được: rút đơn cũ rồi gửi lại.
    // Rút không tính vào giới hạn gửi lại.
    expect(claimFailureOf(loi(409, "CLAIM_ALREADY_OPEN"))).toBe("CLAIM_ALREADY_PENDING");
  });

  it("`CLAIM_CLOSED` cũng dẫn về màn đơn của mình — đơn vừa đổi trạng thái ở nơi khác", () => {
    expect(claimFailureOf(loi(409, "CLAIM_CLOSED"))).toBe("CLAIM_ALREADY_PENDING");
  });

  it("`VALIDATION_FAILED` TRƠ không còn mang nghĩa nghiệp vụ nào", () => {
    // Nó chỉ còn nghĩa "thân yêu cầu sai khuôn". Điều PHẢI giữ: nó không được
    // đọc thành "ô của bạn không nhận đơn" — đó là một lời từ chối vĩnh viễn
    // nói với một người hoàn toàn hợp lệ.
    expect(claimFailureOf(loi(422, "VALIDATION_FAILED"), "EXISTING")).not.toBe(
      "PERSON_UNAVAILABLE"
    );
    expect(claimFailureOf(loi(422, "VALIDATION_FAILED"), "NEW_PERSON")).not.toBe(
      "RELATIVE_UNUSABLE"
    );
    expect(claimFailureOf(loi(422, "VALIDATION_FAILED"))).toBe("UNAVAILABLE");
  });

  it("`NOT_FOUND` là chỗ DUY NHẤT `flow` còn việc để làm", () => {
    // Và câu trả lời của nó cố ý trùng với ca "không nhận đơn": phân biệt được
    // "không tồn tại" với "không nhận đơn" thì người dò đọc được mã nhân khẩu
    // nào từng tồn tại.
    expect(claimFailureOf(loi(404, "NOT_FOUND"), "EXISTING")).toBe("PERSON_UNAVAILABLE");
    expect(claimFailureOf(loi(404, "NOT_FOUND"), "NEW_PERSON")).toBe("RELATIVE_UNUSABLE");
    // Mặc định là lối nhận mình — lối thường gặp hơn hẳn.
    expect(claimFailureOf(loi(404, "NOT_FOUND"))).toBe("PERSON_UNAVAILABLE");
  });
});

describe("những mã nói rõ được, vì chúng nói về CHÍNH người gọi", () => {
  it("tài khoản đã ghép nhân khẩu", () => {
    expect(claimFailureOf(loi(422, "ACCOUNT_ALREADY_LINKED"))).toBe(
      "ACCOUNT_ALREADY_LINKED"
    );
  });

  it("hết lượt gửi lại", () => {
    expect(claimFailureOf(loi(422, "CLAIM_LIMIT_REACHED"))).toBe("CLAIM_LIMIT_REACHED");
  });

  it("chưa có tài khoản, dù backend báo bằng mã nào", () => {
    expect(claimFailureOf(loi(401, "UNAUTHENTICATED"))).toBe("NEEDS_ACCOUNT");
    expect(claimFailureOf(loi(404, "ACCOUNT_NOT_PROVISIONED"))).toBe("NEEDS_ACCOUNT");
    // Máy chủ cũ không gắn `code`: mã HTTP vẫn đủ nghĩa ở đúng nhóm endpoint này.
    expect(claimFailureOf(loi(401))).toBe("NEEDS_ACCOUNT");
  });
});

describe("mặc định KHÔNG BAO GIỜ là một lý do nghiệp vụ", () => {
  it("lỗi máy chủ, lỗi mạng, proxy trả HTML — tất cả là 'chưa gửi được lúc này'", () => {
    expect(claimFailureOf(loi(500, "INTERNAL_ERROR"))).toBe("UNAVAILABLE");
    expect(claimFailureOf(loi(502))).toBe("UNAVAILABLE");
    expect(claimFailureOf(new TypeError("Failed to fetch"))).toBe("UNAVAILABLE");
    expect(claimFailureOf(undefined)).toBe("UNAVAILABLE");
  });

  it("một trục trặc hạ tầng KHÔNG được đọc thành lời từ chối vĩnh viễn", () => {
    // Đây là cái bẫy: mặc định về `PERSON_UNAVAILABLE` sẽ nói với một người
    // hoàn toàn hợp lệ rằng ô của chính họ không nhận đơn — và họ sẽ tin.
    expect(claimFailureOf(loi(503))).not.toBe("PERSON_UNAVAILABLE");
  });

  it("vượt hạn mức gọi là ca riêng, vì lối đi tiếp là CHỜ chứ không phải đổi người", () => {
    expect(claimFailureOf(loi(429, "RATE_LIMITED"))).toBe("RATE_LIMITED");
    expect(claimFailureOf(loi(429))).toBe("RATE_LIMITED");
  });
});

describe("isTargetBlock — 'đổi người thì hết' hay 'chuyện của tài khoản'", () => {
  it("ba lý do về người được chỉ ra", () => {
    expect(isTargetBlock("PERSON_DECEASED")).toBe(true);
    expect(isTargetBlock("PERSON_UNAVAILABLE")).toBe(true);
    expect(isTargetBlock("RELATIVE_UNUSABLE")).toBe(true);
  });

  it("còn lại là chuyện của tài khoản hoặc của đường truyền", () => {
    expect(isTargetBlock("CLAIM_ALREADY_PENDING")).toBe(false);
    expect(isTargetBlock("CLAIM_LIMIT_REACHED")).toBe(false);
    expect(isTargetBlock("ACCOUNT_ALREADY_LINKED")).toBe(false);
    expect(isTargetBlock("UNAVAILABLE")).toBe(false);
  });
});

/**
 * `GET /person-claims/mine` trả một **phong bì** `{ claims, quota }`, cả hai
 * trường bắt buộc — đúng thứ bên gửi đã xin, nay có thật. Đường dự phòng đọc
 * mảng trần đã gỡ: nó chỉ có nghĩa khi hai phía còn lệch nhau.
 *
 * Hai hàm dưới đây ở lại vì một việc khác, vẫn thật: nuốt gọn ca `undefined`
 * của React Query ở một chỗ thay vì rải `?.` khắp các màn.
 */
describe("phong bì của `/person-claims/mine`", () => {
  const don: ClaimView = {
    id: "c1",
    kind: "EXISTING",
    status: "PENDING",
    requestedBy: "u-1",
    phone: "0912345678",
    createdAt: "2026-09-18T02:00:00Z",
  };

  it("đọc ra danh sách đơn và bộ đếm", () => {
    const envelope = { claims: [don], quota: { rejected: 1, max: 3, remaining: 2 } };
    expect(claimsOf(envelope)).toEqual([don]);
    expect(quotaOf(envelope)).toEqual({ rejected: 1, max: 3, remaining: 2 });
  });

  it("chưa tải xong — rỗng và `null`, không nổ", () => {
    expect(claimsOf(undefined)).toEqual([]);
    // `null` phải đọc là "CHƯA BIẾT", tuyệt đối không phải "còn 0 lần": giao
    // diện im lặng về số lần chứ không bịa ra một con số.
    expect(quotaOf(undefined)).toBeNull();
  });

  it("KHÔNG gán cứng ngưỡng — nó sống trong cấu hình máy chủ", () => {
    // `giapha.membership.claim.max-rejected`. Một dòng họ đặt khác thì màn hình
    // phải nói theo máy chủ, không theo một hằng số trong bó JavaScript.
    const khac = { claims: [], quota: { rejected: 4, max: 10, remaining: 6 } };
    expect(quotaOf(khac)?.max).toBe(10);
  });
});
