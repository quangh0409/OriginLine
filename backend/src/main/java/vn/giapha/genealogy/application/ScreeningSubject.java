package vn.giapha.genealogy.application;

import java.util.UUID;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Một hồ sơ <b>đang định ghi</b>, đưa qua {@link PersonScreeningService} để quét trước khi ghi.
 *
 * <h2>Vì sao có kiểu này bên cạnh {@link DuplicateProbe}</h2>
 * {@code DuplicateProbe} nói bằng value object của <b>domain</b> ({@code PersonName},
 * {@code LifeDate}), nên nó chỉ dùng được <i>bên trong</i> context {@code genealogy}. Value object
 * của domain không được đi qua ranh giới context: đưa chúng ra ngoài là buộc mọi context khác phải
 * biết mô hình tên nhiều lớp và mô hình song lịch của phả hệ, và — đúng như
 * {@code DomainPurityTest} đòi hỏi — sẽ kéo theo việc phải gắn siêu dữ liệu framework lên chính
 * các lớp domain.
 *
 * <p>Vì vậy kiểu này cố ý chỉ mang <b>kiểu nguyên thuỷ và VO của shared kernel</b>
 * ({@link Gender}, {@link LunarDate}) — những thứ mọi context đều được dùng.</p>
 *
 * <h2>Chống nhân đôi khái niệm</h2>
 * Đây <b>không</b> phải bản sao của mô hình nhân khẩu, mà là <b>danh sách tham số</b> của phép
 * quét. Chỉ có <b>đúng một chỗ</b> dịch từ đây sang kiểu domain:
 * {@link PersonScreeningService#toProbe(ScreeningSubject)}. Không adapter nào của context khác
 * được tự dựng {@code PersonName} hay {@code LifeDate} — chúng không nhìn thấy hai kiểu ấy.
 * {@code PersonScreeningServiceTest} ghim rằng hai bên không lệch: mọi thành phần của
 * {@code DuplicateProbe} phải được kiểu này nuôi, và kết quả đi qua facade phải trùng khít kết quả
 * gọi thẳng bộ dò.
 *
 * @param ref            mã tham chiếu do bên gọi đặt, để ghép kết quả về đúng dòng đầu vào
 * @param fullName       tên thường gọi — lớp tên {@code THUONG_GOI}, và là tên hiển thị mặc định
 * @param tabooName      tên huý — lớp tên {@code HUY}. Lớp tên <b>duy nhất</b> kích hoạt cảnh báo
 *                       kỵ húy (FR-1.6), và cũng là tín hiệu so trùng ít trùng ngẫu nhiên nhất
 * @param posthumousName tên thụy — lớp tên {@code THUY}, đặt sau khi mất
 * @param generation     đời thứ <b>đã suy ra</b> từ liên kết cha/mẹ, không phải giá trị tự khai:
 *                       chấm điểm bằng đời thứ tự khai là tự bỏ đi phản chứng đáng tin nhất
 * @param birthYear      chỉ năm sinh — mức chính xác mà gia phả giấy thường còn giữ được
 * @param gio            ngày giỗ, tức phần âm lịch của ngày mất — <b>nguồn chân lý</b>, và là tín
 *                       hiệu nặng điểm nhất của bộ dò trùng; {@code null} khi sổ không chép năm,
 *                       vì {@link LunarDate} bắt buộc có năm
 * @param excludePersonId bỏ qua chính nhân khẩu này (khi đang sửa hồ sơ, tránh tự báo trùng với
 *                       chính mình); {@code null} khi thêm mới
 */
@org.springframework.modulith.NamedInterface("do-trung")
public record ScreeningSubject(String ref,
                               String fullName,
                               String tabooName,
                               String posthumousName,
                               Gender gender,
                               Integer generation,
                               UUID branchId,
                               Integer birthYear,
                               LunarDate gio,
                               String nativePlace,
                               UUID excludePersonId) {

    /**
     * Dạng rút gọn cho phép quét <b>kỵ húy</b>, vốn chỉ cần đúng tên huý và đời thứ.
     *
     * <p>Các trường còn lại để trống là đúng, không phải thiếu sót: kỵ húy không chấm điểm theo
     * giới tính, chi hay ngày giỗ — nó chỉ hỏi "tên huý này có trùng tên huý bậc trên không".</p>
     */
    public static ScreeningSubject kyHuy(String ref, String tabooName, Integer generation,
                                         UUID excludePersonId) {
        return new ScreeningSubject(ref, null, tabooName, null, null, generation, null, null, null,
                null, excludePersonId);
    }
}
