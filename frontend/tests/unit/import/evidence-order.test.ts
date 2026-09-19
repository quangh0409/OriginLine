import { describe, expect, it } from "vitest";
import {
  isDivergence,
  orderByTrust,
  trustRank,
} from "@/components/import/evidence-order";
import type {
  ImportDuplicateEvidence,
  ImportEvidenceField,
} from "@/lib/api/data-import";

/**
 * THỨ TỰ BẰNG CHỨNG KHI ĐỐI CHIẾU NGƯỜI NGHI TRÙNG.
 *
 * <p>Đây không phải một bài kiểm về cách sắp mảng. Nó ghim một hiểu biết về gia
 * phả Việt vào mã nguồn, nơi hiểu biết ấy dễ bị người sửa sau vô tình gỡ mất —
 * vì hợp đồng KHÔNG gửi điểm cho từng dấu hiệu, nên "thôi thì sắp theo thứ tự
 * máy chủ trả về" nghe rất hợp lý và là đúng thứ phá hỏng màn hình này.</p>
 *
 * <p>Chúng không bằng nhau trong đời thật: ngày giỗ được cả họ cúng hằng năm,
 * còn năm sinh trong sổ cũ thường chép theo trí nhớ. Người đối chiếu có vài
 * giây cho mỗi cặp và đọc từ trên xuống; đặt năm sinh lên trước là dẫn họ tới
 * kết luận "hai người" ngay trước khi họ kịp thấy một ngày giỗ trùng khít.</p>
 */

/**
 * Không có tham số `points`: hợp đồng để `ImportDuplicateEvidence.points` là
 * trường **tuỳ chọn** và backend hôm nay không gửi nó. Một tham số điểm ở đây
 * sẽ mời gọi đúng cái sai mà cả tệp này tồn tại để chặn — sắp theo điểm.
 */
function evidence(
  field: ImportEvidenceField,
  match: ImportDuplicateEvidence["match"] = "SAME"
): ImportDuplicateEvidence {
  return { field, match, existingValue: "x", incomingValue: "y" };
}

describe("đối chiếu nghi trùng · thứ tự bằng chứng theo độ tin", () => {
  it("ngày giỗ luôn đứng TRƯỚC năm sinh, dù máy chủ không gửi điểm nào", () => {
    const ordered = orderByTrust([
      // Cố tình đưa năm sinh vào trước. Không có `points` để mà sắp theo — và
      // đó chính là lý do thứ tự này phải là một quyết định có chủ ý.
      evidence("BIRTH_YEAR", "SAME"),
      evidence("DEATH_LUNAR", "SAME"),
    ]);
    expect(ordered.map((e) => e.field)).toEqual(["DEATH_LUNAR", "BIRTH_YEAR"]);
  });

  it("năm sinh xếp cuối cùng trong bảy dấu hiệu", () => {
    const all: ImportEvidenceField[] = [
      "BIRTH_YEAR",
      "ORIGIN_PLACE",
      "GENERATION",
      "FULL_NAME",
      "TABOO_NAME",
      "FATHER",
      "DEATH_LUNAR",
    ];
    const ordered = orderByTrust(all.map((f) => evidence(f)));
    expect(ordered[0]!.field).toBe("DEATH_LUNAR");
    expect(ordered[ordered.length - 1]!.field).toBe("BIRTH_YEAR");
  });

  it("tên huý xếp trên họ tên thường — tên huý ít trùng ngẫu nhiên hơn hẳn", () => {
    expect(trustRank("TABOO_NAME")).toBeLessThan(trustRank("FULL_NAME"));
  });

  it("dấu hiệu lạ (backend thêm mới) xuống cuối chứ không làm hỏng thứ tự", () => {
    const ordered = orderByTrust([
      { ...evidence("DEATH_LUNAR"), field: "SOMETHING_NEW" as ImportEvidenceField },
      evidence("DEATH_LUNAR"),
    ]);
    expect(ordered[0]!.field).toBe("DEATH_LUNAR");
  });

  it("không đổi nội dung và KHÔNG tự sinh điểm — chỉ đổi thứ tự", () => {
    const input = [evidence("BIRTH_YEAR", "DIFFERENT"), evidence("DEATH_LUNAR", "SAME")];
    const ordered = orderByTrust(input);
    expect(ordered).toHaveLength(2);
    // Thang điểm là dữ liệu hiệu chỉnh của bộ dò; client tuyệt đối không cộng bù.
    expect(ordered.every((e) => e.points === undefined)).toBe(true);
    // Mảng gốc giữ nguyên: người gọi có thể còn dùng nó cho việc khác.
    expect(input[0]!.field).toBe("BIRTH_YEAR");
  });
});

describe("đối chiếu nghi trùng · chỗ nào được tô", () => {
  it("tô chỗ hai bên khác nhau", () => {
    expect(isDivergence(evidence("BIRTH_YEAR", "DIFFERENT"))).toBe(true);
  });

  it("tô chỗ TỆP bỏ trống mà phả có — đây là chỗ làm mất lớp tên khi hợp nhất ẩu", () => {
    expect(isDivergence(evidence("TABOO_NAME", "MISSING_IN_FILE"))).toBe(true);
  });

  it("KHÔNG tô chỗ hai bên giống nhau — tô hết thì không còn gì được tô", () => {
    expect(isDivergence(evidence("FULL_NAME", "SAME"))).toBe(false);
  });

  it("KHÔNG tô chỗ phả thiếu mà tệp có: đó là dữ liệu được BỔ SUNG, không phải rủi ro", () => {
    expect(isDivergence(evidence("ORIGIN_PLACE", "MISSING_IN_TREE"))).toBe(false);
  });
});
