import { describe, expect, it } from "vitest";
import { rootIdFromQuery } from "@/lib/tree/root-id";

/**
 * "PHẢ ĐỒ BẮT ĐẦU TỪ AI?" — **máy chủ** trả lời.
 *
 * Bộ test này từng ghim một chuỗi bốn bước ở phía client: `?rootId=` →
 * `NEXT_PUBLIC_DEFAULT_ROOT_ID` → gốc đã xem lần trước (`localStorage`) → hỏi
 * người dùng. Chuỗi ấy ra đời vì `rootId` là tham số **bắt buộc** và máy chủ
 * chưa có khái niệm gốc mặc định.
 *
 * Nay `rootId` là **tuỳ chọn** và máy chủ chọn gốc theo **vai + phạm vi chi**
 * của người gọi — ba dữ kiện (vai, `ltree`, ai là thuỷ tổ) mà không nhánh nào
 * của trình duyệt biết. Ba bước giữa vì thế bị xoá, và cái còn lại ở tệp này
 * đúng một hàm: chuẩn hoá `?rootId=`.
 *
 * Tính chất phải giữ, và là lý do tệp này còn tồn tại: **không nhánh nào bịa ra
 * một id cho một dòng họ thật.** Trước kia nó được giữ bằng cách kiểm UUID;
 * nay nó được giữ bằng cách đơn giản hơn — giao diện không có chỗ nào để đặt
 * một id vào cả.
 */

describe("chuẩn hoá `?rootId=`", () => {
  it("giữ nguyên một id có thật — liên kết chia sẻ phải thắng tuyệt đối", () => {
    const shared = "11111111-1111-4111-8111-111111111111";
    expect(rootIdFromQuery(shared)).toBe(shared);
  });

  it("cắt khoảng trắng thừa quanh id dán từ nhóm chat", () => {
    expect(rootIdFromQuery("  p-001  ")).toBe("p-001");
  });

  it("vắng mặt, rỗng, hay toàn khoảng trắng đều là `null` — nghĩa là ĐỂ MÁY CHỦ CHỌN", () => {
    expect(rootIdFromQuery(undefined)).toBeNull();
    expect(rootIdFromQuery(null)).toBeNull();
    expect(rootIdFromQuery("")).toBeNull();
    expect(rootIdFromQuery("   ")).toBeNull();
  });

  it("KHÔNG tự loại một id sai định dạng — máy chủ mới là nơi biết id nào có thật", () => {
    // Trước đây có một phép kiểm UUID ở đây, và nó âm thầm làm hỏng đúng thứ nó
    // định bảo vệ: một liên kết chia sẻ mang id sai sẽ bị bỏ ở trình duyệt rồi
    // mở ra CÂY MẶC ĐỊNH, tức người nhận nhìn một nhánh khác hẳn nhánh được gửi
    // mà tưởng liên kết chạy đúng. Nay id đi thẳng lên máy chủ và `400`/`404`
    // của nó dẫn tới màn chọn gốc với đúng một câu trung thực.
    expect(rootIdFromQuery("khong-phai-uuid")).toBe("khong-phai-uuid");
  });
});

describe("những gì đã bị xoá khỏi tệp này", () => {
  it("không còn một nhánh nào đọc biến môi trường hay `localStorage`", async () => {
    const mod = await import("@/lib/tree/root-id");

    // `NEXT_PUBLIC_DEFAULT_ROOT_ID`: nếu ai đặt nó thì MỌI VAI đều mở cùng một
    // gốc, xoá sạch phần cá nhân hoá theo chi mà máy chủ vừa làm.
    // `localStorage`: một gốc nhớ từ phiên trước ghi đè lựa chọn theo vai, sống
    // sót qua đăng xuất, và không phân biệt được "tôi đã chọn" với "tôi vô tình
    // mở". Gốc người dùng CHỦ Ý chọn nay sống ở `?rootId=` trên URL — hiện ra,
    // chia sẻ được, xoá được.
    expect(Object.keys(mod)).toEqual(["rootIdFromQuery"]);
  });
});
