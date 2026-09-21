import { describe, expect, it } from "vitest";
import { groupClaimQueue } from "@/components/membership/grouping";
import type { ClaimReviewView } from "@/lib/api/membership-admin";

/**
 * **Phép gom cụm tranh chấp** — ca biên "hai người cùng nhận một nhân khẩu"
 * (design 07 §1.4), tách khỏi màn hình để kiểm được từng luật một.
 *
 * Luật mà hàm này sống chết theo: nó **không** được suy ra quan hệ tranh chấp
 * từ `person.id`. Máy chủ biết toàn bộ hàng đợi; client chỉ cầm một trang. Nếu
 * hàm tự nhóm theo nhân khẩu thì hai lá đơn ở hai trang khác nhau sẽ không bao
 * giờ gặp nhau, và Trưởng chi duyệt lá duy nhất mình thấy — vốn chưa chắc là lá
 * đúng.
 */

function don(id: string, competing: string[], personId = "p-100"): ClaimReviewView {
  return {
    id,
    kind: "EXISTING",
    status: "PENDING",
    requestedBy: `u-${id}`,
    phone: "0900 000 000",
    // CHỈ khoá — `PersonClaim` không chở tên nhân khẩu, xem javadoc của
    // `ClaimReviewView`.
    personId,
    competingClaimIds: competing,
    createdAt: "2026-09-10T08:00:00Z",
  };
}

describe("gom cụm hàng chờ duyệt", () => {
  it("để hai đơn tranh nhau vào CÙNG một cụm", () => {
    const groups = groupClaimQueue([don("a", ["b"]), don("b", ["a"])]);

    expect(groups).toHaveLength(1);
    expect(groups[0]?.requests.map((r) => r.id)).toEqual(["a", "b"]);
    expect(groups[0]?.missingCompeting).toBe(0);
  });

  it("KHÔNG gom hai đơn chỉ vì trùng nhân khẩu mà máy chủ không khai tranh chấp", () => {
    // Đây là chỗ dễ 'sửa cho tiện' nhất, và cũng là chỗ sai nguy hiểm nhất:
    // trùng `person.id` trong một trang KHÔNG phải bằng chứng tranh chấp, còn
    // lời khai của máy chủ thì là. Suy hộ máy chủ ở đây sẽ che mất việc máy chủ
    // quên khai.
    const groups = groupClaimQueue([don("a", []), don("b", [])]);
    expect(groups).toHaveLength(2);
  });

  it("gom ba đơn thành MỘT cụm chứ không ba cụm đôi chồng nhau", () => {
    const groups = groupClaimQueue([don("a", ["b"]), don("b", ["c"]), don("c", [])]);

    expect(groups).toHaveLength(1);
    expect(groups[0]?.requests.map((r) => r.id)).toEqual(["a", "b", "c"]);
  });

  it("coi quan hệ tranh chấp là ĐỐI XỨNG khi một bên khai thiếu", () => {
    // `b` quên nhắc tới `a`. Bỏ sót một lá đơn vì lỗi dữ liệu một chiều tệ hơn
    // hẳn việc hiện thừa một lá.
    const groups = groupClaimQueue([don("a", ["b"]), don("b", [])]);

    expect(groups).toHaveLength(1);
    expect(groups[0]?.requests.map((r) => r.id)).toEqual(["a", "b"]);
  });

  it("đếm và NÓI RA số đơn tranh chấp không có trong trang này", () => {
    // Hợp đồng đòi máy chủ trả mọi đơn tranh chấp trong cùng một trang. "Lẽ ra"
    // không phải một phép kiểm — nếu nó hỏng thì giao diện phải kêu, không được
    // lặng lẽ hiện một trong hai.
    const groups = groupClaimQueue([don("a", ["b", "c"])]);

    expect(groups).toHaveLength(1);
    expect(groups[0]?.requests.map((r) => r.id)).toEqual(["a"]);
    expect(groups[0]?.missingCompeting).toBe(2);
  });

  it("giữ nguyên thứ tự máy chủ trả về, KHÔNG sắp lại theo thời gian gửi", () => {
    // §1.4 chốt: người gửi trước không được ưu tiên. Một danh sách sắp theo
    // `createdAt` chính là một cách ưu tiên, vì mắt người đọc từ trên xuống.
    const sau = { ...don("sau", ["truoc"]), createdAt: "2026-09-12T00:00:00Z" };
    const truoc = { ...don("truoc", ["sau"]), createdAt: "2026-09-01T00:00:00Z" };

    const groups = groupClaimQueue([sau, truoc]);
    expect(groups[0]?.requests.map((r) => r.id)).toEqual(["sau", "truoc"]);
  });

  it("chịu được `competingClaimIds` vắng mặt", () => {
    // Trường này được khai tuỳ chọn trong hợp đồng đề xuất; một bản backend
    // chưa gửi nó không được làm hàng đợi trắng màn hình.
    const khuyet = { ...don("a", []), competingClaimIds: undefined };
    const groups = groupClaimQueue([khuyet]);

    expect(groups).toHaveLength(1);
    expect(groups[0]?.missingCompeting).toBe(0);
  });
});
