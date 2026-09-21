package vn.giapha.genealogy.application;

import java.util.UUID;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Hồ sơ một nhân khẩu <b>đã lọc xong theo người gọi</b> — thứ duy nhất context khác nhận được từ
 * {@link PersonDisclosureService}.
 *
 * <h2>Đối xứng với {@link ImportedPersonDraft}</h2>
 * {@code ImportedPersonDraft} là thứ đi <b>vào</b> phả; kiểu này là thứ đi <b>ra</b>. Hai bản ghi
 * cố tình mang gần đúng cùng bộ trường và cùng cách gọi tên, để người đọc thấy ngay chiều nào là
 * chiều nào. Khác biệt nằm ở một điểm và nó là toàn bộ ý nghĩa của lớp: mọi trường ở đây có thể
 * vắng vì <b>người gọi không được xem</b>, chứ không chỉ vì dữ liệu không có.
 *
 * <h2>{@code null} mang hai nghĩa, và KHÔNG phân biệt được — đó là chủ ý</h2>
 * "Bị giấu" và "không có dữ liệu" đều là {@code null}. Thêm bất kỳ cờ nào cho phép phân biệt hai
 * nguyên nhân ấy là phá bỏ chính điều BA v2 §10 bảo vệ: biết chắc "cụ này CÓ số điện thoại nhưng
 * anh không được xem" đã là một mẩu thông tin rò rỉ. Xem javadoc
 * {@code PersonView} — kiểu này thừa kế nguyên tắc đó.
 *
 * <h2>Không có trường nào thuộc Tầng 2/Tầng 3 "nặng"</h2>
 * Không điện thoại, không email, không địa chỉ đầy đủ, không nghề nghiệp, không ảnh. Chúng
 * <b>không</b> bị bỏ quên: kiểu này sinh ra để phục vụ việc xuất phả (mẫu Excel, sổ in, kết xuất
 * cho chi), và những khối ấy không có chỗ trong một cuốn gia phả. Cần chúng thì đường đúng là
 * {@code PersonQueryService} với đầy đủ ngữ cảnh HTTP, chứ không phải nới kiểu này rộng ra — một
 * tệp tải về là dữ liệu <b>rời khỏi hệ thống</b>, không có bộ lọc thứ hai và không có đường thu hồi.
 *
 * @param personId    khoá để bên gọi ghép kết quả về đúng dòng dữ liệu của mình
 * @param thuongGoi   tên hiển thị (tên chính) — luôn có mặt, vì hồ sơ nào không được phép hiện tên
 *                    thì đã không có mặt trong kết quả
 * @param huy         tên huý; {@code null} khi người gọi không được xem các lớp tên phụ
 * @param thuy        tên thụy; cùng luật với {@link #huy}
 * @param hanNom      chữ Hán-Nôm của <b>tên chính</b>. Nó đi theo tên chính chứ không theo các lớp
 *                    tên phụ — đúng như {@code PersonSummaryView} vẫn đưa nó lên node phả đồ
 * @param gender      giới tính
 * @param doi         đời thứ
 * @param conSong     còn sống. Người đã khuất là dữ liệu công khai (BA v2 §10); người còn sống bị
 *                    che theo đồng thuận của chính họ
 * @param namSinh     <b>chỉ năm</b> sinh — mức mà gia phả cần; ngày đầy đủ không bao giờ ra khỏi
 *                    kiểu này
 * @param ngayGio     ngày mất <b>âm lịch</b>, nguồn chân lý để tính giỗ; {@code null} khi sổ không
 *                    chép, vì {@link LunarDate} bắt buộc có năm
 * @param nguyenQuan  nguyên quán
 * @param duLieuNgoaiNhomHienDuoc <b>một quyết định, không phải một danh sách trường.</b> Bằng
 *        {@code PersonVisibility#ungroupedFieldsVisible()}: người gọi có được xem khối dữ liệu phả
 *        hệ "không thuộc nhóm đồng thuận nào" của hồ sơ này không (nguyên quán, các lớp tên phụ,
 *        tiểu sử). Dùng nó cho những cột mà aggregate của {@code genealogy} <b>không chở</b> nhưng
 *        thuộc cùng khối — ví dụ cột {@code native_place_code} do {@code dataimport} tự thêm ở V9.
 *        Đây là cách duy nhất được phép để một context khác "hỏi thêm": nhận lại <i>kết luận</i>
 *        của bộ lọc, chứ không chép lại <i>luật</i> của nó
 * @param vinhDanhHienDuoc <b>cùng loại với trường trên: một kết luận, không phải một danh sách
 *        trường.</b> Bằng {@code PersonVisibility#allows(PrivacyFieldGroup.HONOUR)}: người gọi có
 *        được xem các bản ghi <i>vinh danh</i> của hồ sơ này không (V17, nhóm trường riêng tư thứ
 *        sáu). Bảng {@code honour} thuộc context {@code content}, nên aggregate của
 *        {@code genealogy} không chở nội dung ấy — nhưng <b>luật</b> quyết định ai xem được thì
 *        phải ở lại đây. Cách duy nhất khác là {@code content} tự đọc {@code privacy_consent} và
 *        tự diễn giải, tức bản luật riêng tư thứ hai, đúng thứ {@link PersonDisclosureService}
 *        sinh ra để xoá
 */
@org.springframework.modulith.NamedInterface("loc-rieng-tu")
public record DisclosedPerson(UUID personId,
                              String thuongGoi,
                              String huy,
                              String thuy,
                              String hanNom,
                              Gender gender,
                              Integer doi,
                              boolean conSong,
                              Integer namSinh,
                              LunarDate ngayGio,
                              String nguyenQuan,
                              boolean duLieuNgoaiNhomHienDuoc,
                              boolean vinhDanhHienDuoc) {
}
