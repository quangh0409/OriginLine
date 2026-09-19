import { describe, expect, it } from "vitest";
import { nodeDatesState } from "@/lib/tree/life-dates";
import type { PersonSummaryDto } from "@/types/api";

/**
 * Ô ngày trên thẻ phả đồ — phép phân biệt "chưa ai ghi" với "chưa chia sẻ".
 *
 * Bộ kiểm này canh đúng hai bờ vực:
 *  - **nói quá ít** → mọi ô trống trông như nhau, và cả cuốn gia phả đọc ra
 *    như "thiếu dữ liệu" trong khi phần lớn ô trống chỉ là người còn sống chưa
 *    bật chia sẻ;
 *  - **nói quá nhiều** → chính câu chữ trở thành kênh trả lời câu hỏi "hồ sơ
 *    này có dữ liệu hay không", tức là để lộ đúng thứ phân tầng đang che.
 *
 * Bất biến cốt lõi, giống hệt `privacy-tier-notice`: **kết quả chỉ phụ thuộc
 * `isAlive` và việc dòng ngày có rỗng hay không**, tuyệt đối không phụ thuộc
 * việc dữ liệu có tồn tại ở máy chủ.
 */

const person = (o: Partial<PersonSummaryDto>): PersonSummaryDto => ({
  ...o,
  id: o.id ?? "p",
  displayName: o.displayName ?? "Nguyễn Văn A",
  isAlive: o.isAlive ?? false,
});

describe("người đã khuất — dữ liệu công khai, nói thẳng được", () => {
  it("có đủ hai mốc thì hiện khoảng đời", () => {
    const state = nodeDatesState(person({ birthYear: 1780, deathYear: 1852 }));
    expect(state).toEqual({ kind: "known", text: "1780 – 1852" });
  });

  it("khuyết năm sinh thì đánh dấu đúng nửa khuyết, không giấu cả dòng", () => {
    expect(nodeDatesState(person({ deathYear: 1852 }))).toEqual({
      kind: "known",
      text: "? – 1852",
    });
  });

  it("không có mốc nào thì đó là lời mời bổ sung, không phải chuyện riêng tư", () => {
    expect(nodeDatesState(person({}))).toEqual({ kind: "unrecorded" });
  });
});

describe("người còn sống — trống là trạng thái bình thường", () => {
  it("đã chia sẻ thì hiện năm sinh, và KHÔNG bịa dấu hỏi cho vế còn lại", () => {
    // "1985 – ?" đọc ra là "chưa ai biết người này mất năm nào" — một câu hỏi
    // về một người đang sống.
    expect(nodeDatesState(person({ isAlive: true, birthYear: 1985 }))).toEqual({
      kind: "known",
      text: "1985 –",
    });
  });

  it("chưa chia sẻ thì đọc ra 'chưa chia sẻ', không phải 'chưa ai ghi'", () => {
    expect(nodeDatesState(person({ isAlive: true }))).toEqual({ kind: "unshared" });
  });
});

describe("phép phân biệt không rò rỉ gì về chính hồ sơ", () => {
  it("hai người còn sống khác nhau, cùng trống ngày, đọc ra Y HỆT nhau", () => {
    // Đây là bất biến quan trọng nhất. Nếu người có dữ liệu (nhưng đóng chia sẻ)
    // và người thật sự chưa ai ghi ra hai kết quả khác nhau, thì kết quả ấy tự
    // nó đã trả lời câu "hồ sơ này có dữ liệu hay không".
    const coDuLieuNhungDong = nodeDatesState(person({ id: "a", isAlive: true }));
    const thatSuChuaAiGhi = nodeDatesState(person({ id: "b", isAlive: true }));

    expect(coDuLieuNhungDong).toEqual(thatSuChuaAiGhi);
    expect(coDuLieuNhungDong.kind).toBe("unshared");
  });

  it("chỉ đọc đúng ba trường, không đọc gì khác của hồ sơ", () => {
    // Chống hồi quy theo hướng "thêm một nhánh rẽ theo `meta.visibleTier`" hay
    // "rẽ theo chi/ngành": mọi trường khác đổi giá trị mà kết quả vẫn nguyên.
    const a = nodeDatesState(
      person({ id: "x", displayName: "A", isAlive: true, generation: 5, gender: "MALE" })
    );
    const b = nodeDatesState(
      person({
        id: "y",
        displayName: "B",
        isAlive: true,
        generation: 9,
        gender: "FEMALE",
        nativePlace: "Nam Định",
        primaryBranch: { id: "b1", name: "Chi Nhất", path: "root.chi_nhat" },
      })
    );

    expect(a).toEqual(b);
  });

  it("không câu nào trong phép dựng mang một con số đếm", () => {
    // "ẩn 3 trường" là con đường ngắn nhất tới rò rỉ; ở đây con số duy nhất
    // được phép xuất hiện là chính năm sinh/năm mất.
    const unshared = nodeDatesState(person({ isAlive: true }));
    const unrecorded = nodeDatesState(person({}));
    expect(JSON.stringify(unshared)).not.toMatch(/\d/);
    expect(JSON.stringify(unrecorded)).not.toMatch(/\d/);
  });
});
