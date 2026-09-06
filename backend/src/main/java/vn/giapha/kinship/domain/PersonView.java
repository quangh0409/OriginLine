package vn.giapha.kinship.domain;

import java.time.LocalDate;
import java.util.UUID;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * Ảnh chụp <b>chỉ-đọc</b> của một nhân khẩu, đủ dùng cho suy luận danh xưng và không hơn.
 *
 * <p>Context {@code kinship} cố tình <b>không</b> import {@code Person} của context
 * {@code genealogy}: hai context chỉ nói chuyện qua application service công khai hoặc domain
 * event. Bản chiếu này do {@code PersonLookupPort} cung cấp.</p>
 *
 * @param id          định danh nhân khẩu
 * @param gender      giới tính — chiều so khớp {@code kinship_rule.gender}
 * @param generation  đời trong gia phả; chỉ để hiển thị. Suy luận danh xưng luôn dùng khoảng cách
 *                    tới LCA, vì số đời có thể lệch giữa các chi
 * @param birthOrder  thứ tự sinh trong nhà — nguồn gốc của {@code is_elder} (bác hay chú).
 *                    Rất hay thiếu ở gia phả giấy chép lại, khi đó rơi vào luật "chưa rõ vai"
 * @param birthSolar  ngày sinh dương lịch, căn cứ dự phòng khi thiếu {@code birthOrder}
 * @param deleted     đã xoá mềm — vẫn nằm trên đường đi để nối các đời nhưng KHÔNG được làm LCA
 * @param displayName tên hiển thị; tầng trên có thể thay bằng nhãn chung theo phân tầng riêng tư
 * @param branchId    chi/ngành chính, dùng để chọn bộ luật hiệu lực
 * @param branchPath  đường {@code ltree} của chi, dùng để leo lên tìm bộ luật của chi cha
 */
public record PersonView(
        PersonId id,
        Gender gender,
        Integer generation,
        Integer birthOrder,
        LocalDate birthSolar,
        boolean deleted,
        String displayName,
        UUID branchId,
        String branchPath) {

    public PersonView {
        if (id == null) {
            throw new IllegalArgumentException("PersonView.id khong duoc null");
        }
        if (gender == null) {
            gender = Gender.UNKNOWN;
        }
    }

    /** Bản tối giản dùng cho test và cho các chặng trên đường đi chỉ cần id + giới tính. */
    public static PersonView minimal(PersonId id, Gender gender) {
        return new PersonView(id, gender, null, null, null, false, null, null, null);
    }
}
