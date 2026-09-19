package vn.giapha.genealogy.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Cổng tra <b>ứng viên nghi trùng</b>: những nhân khẩu đã có trong gia phả mà tên (so không dấu)
 * trùng với tên đang định nhập.
 *
 * <h2>Cổng chỉ lọc, không chấm điểm</h2>
 * Cổng này cố ý <b>chỉ làm cửa lọc theo tên</b> rồi trả về hồ sơ rút gọn; toàn bộ việc chấm điểm
 * nghi ngờ nằm ở {@code DuplicateScorer} bên tầng application. Lý do: ngưỡng và trọng số là quy tắc
 * nghiệp vụ của dòng họ, sẽ còn được chỉnh; nhét chúng vào SQL thì mỗi lần chỉnh là một migration,
 * và không test được nếu không có CSDL.
 *
 * <h2>Vì sao vẫn phải nhờ CSDL bỏ dấu</h2>
 * Định nghĩa "bỏ dấu" phải tồn tại đúng <b>một</b> nơi là hàm {@code vn_unaccent} của Postgres
 * (V6). Nếu Java tự cài một bản bỏ dấu thứ hai thì hai bản sẽ lệch nhau ở đúng những ký tự hiếm
 * (đ/Đ, ê có dấu nặng...), và hậu quả là cảnh báo lúc có lúc không mà không ai truy ra được vì sao.
 * Vì vậy có {@link #unaccent(List)}: một vòng gọi CSDL cho cả lô, không phải cho từng người.
 *
 * <h2>Gọi được theo lô</h2>
 * Đường ống nhập liệu hàng loạt (400 người một lượt) dùng lại đúng cổng này: gom toàn bộ tên
 * không dấu của cả lô rồi gọi {@link #findByAnyName(Collection, int)} <b>một lần</b>, sau đó ghép
 * cặp trong bộ nhớ. Không có phương thức "tra một người" để không ai vô tình viết vòng lặp N+1.
 */
public interface DuplicateCandidatePort {

    /**
     * Bỏ dấu một lô tên bằng chính {@code vn_unaccent} của CSDL.
     *
     * @return danh sách cùng kích thước và cùng thứ tự với đầu vào; phần tử null giữ nguyên null
     */
    List<String> unaccent(List<String> rawNames);

    /**
     * Mọi nhân khẩu <b>chưa xoá mềm</b> có ít nhất một lớp tên khớp không dấu với danh sách truyền
     * vào. Tra chéo mọi lớp tên (húy / tự / hiệu / thụy / thường gọi / pháp danh) vì một người được
     * nhập lại lần hai rất hay bị ghi tên vào lớp khác với lần đầu.
     *
     * @param unaccentedNames tên đã bỏ dấu (kết quả của {@link #unaccent(List)})
     * @param limit trần số nhân khẩu trả về — cửa lọc theo tên có thể quét trúng cả trăm người
     *        trùng tên trong một dòng họ lớn
     */
    List<PersonSignature> findByAnyName(Collection<String> unaccentedNames, int limit);

    /**
     * Một lớp tên đã kèm sẵn bản không dấu do CSDL sinh.
     *
     * @param type lớp tên
     * @param raw tên có dấu, nguyên văn
     * @param unaccented bản không dấu — cột generated {@code person_name.name_unaccented}
     */
    record NameKey(NameType type, String raw, String unaccented) {
    }

    /**
     * Hồ sơ rút gọn vừa đủ để chấm điểm nghi trùng — cố ý <b>không</b> mang dữ liệu Tầng 3
     * (điện thoại, email, địa chỉ đầy đủ): phép dò trùng chạy trước cả khi biết người gọi có được
     * xem những trường đó hay không.
     *
     * @param personId nhân khẩu đã có trong gia phả; {@code null} khi đây là một dòng trong lô đang
     *        nhập, chưa được ghi
     * @param displayName tên hiển thị, để dựng hộp thoại
     * @param names mọi lớp tên
     * @param gender giới tính
     * @param generation đời thứ (Thuỷ tổ = 1); {@code null} khi chưa nối vào cây
     * @param branchId chi/ngành chính
     * @param birthYear năm sinh suy từ dương lịch, thiếu thì lấy từ âm lịch
     * @param deathYear năm mất
     * @param gio ngày giỗ âm lịch — <b>nguồn chân lý</b>, mạnh hơn năm sinh nhiều
     * @param nativePlaceUnaccented nguyên quán đã bỏ dấu
     */
    record PersonSignature(UUID personId,
                           String displayName,
                           List<NameKey> names,
                           Gender gender,
                           Integer generation,
                           UUID branchId,
                           Integer birthYear,
                           Integer deathYear,
                           LunarDate gio,
                           String nativePlaceUnaccented) {

        public PersonSignature {
            names = names == null ? List.of() : List.copyOf(names);
        }
    }
}
