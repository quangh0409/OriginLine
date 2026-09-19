package vn.giapha.genealogy.application;

import java.util.List;
import java.util.UUID;

/**
 * Hai phép <b>điểm danh</b> mà tầng application cần nhưng {@code PersonRepository} cố ý không có:
 * "ai đứng đầu một nhánh" và "ai còn sống".
 *
 * <h2>Vì sao cổng này nằm ở {@code application} chứ không ở {@code domain.port}</h2>
 * Đây <b>không</b> phải cổng của aggregate {@code Person}: nó không nạp, không ghi, không giữ bất
 * biến nào của domain. Nó trả về <b>danh sách khoá</b> cho hai read-model của tầng application —
 * gốc mặc định của phả đồ và danh bạ dòng họ. Đặt nó cạnh {@code PersonRepository} sẽ mời gọi
 * người sau thêm tiếp các truy vấn báo cáo vào một cổng vốn chỉ để nạp/ghi aggregate, và
 * {@code PersonRepository} mất đi tính "không có delete" mà javadoc của nó đang tuyên bố.
 *
 * <h2>Chỉ trả KHOÁ, không trả dữ liệu nhân khẩu — và đó là điều kiện, không phải tuỳ chọn</h2>
 * Cổng này <b>không biết người gọi là ai</b> và <b>không</b> lọc riêng tư. Nếu nó trả về nghề
 * nghiệp hay tỉnh thì nó đã thành một kênh đọc song song với {@code GET /persons/&#123;id&#125;},
 * chỉ khác là không có bộ lọc — đúng cái bẫy mà {@code GenealogyExceptionHandler} mô tả dài dòng ở
 * javadoc của nó. Vì vậy đầu ra chỉ là {@code UUID}: người gọi buộc phải nạp lại qua
 * {@code PersonRepository.byIds} rồi đẩy qua {@code PrivacyTierService}.
 *
 * <p><b>Đặc biệt: không có tham số nào lọc theo {@code privacy_consent}.</b> V8 §8.4 đã chốt —
 * không đánh chỉ mục trên cột ấy, vì "tìm người đã mở liên hệ cho cả họ" là đúng loại truy vấn hệ
 * thống này không nên làm cho dễ. Danh bạ vì thế lọc <b>trong tiến trình</b>, bằng chính
 * {@code PersonVisibility}, chứ không bằng một mệnh đề {@code WHERE} chép lại luật.</p>
 */
public interface GenealogyRosterPort {

    /**
     * Ứng viên làm <b>gốc</b> của một nhánh phả đồ, xếp theo thứ tự ưu tiên đã định.
     *
     * <p>Trả về nhiều ứng viên chứ không phải một, vì người gọi còn phải loại tiếp những ai họ
     * không được biết là tồn tại (Khách không thấy người còn sống). Chọn sẵn một người rồi để
     * người gọi nhận {@code 404} là cách chắc chắn nhất để trang phả đồ chết với đúng vai cần nó
     * nhất.</p>
     *
     * <p>Thứ tự: <b>đời thứ tăng dần</b> (thuỷ tổ trước) → <b>người đã khuất trước</b> (để Khách
     * và người ngoài chi vẫn có gốc nhìn thấy được) → năm sinh tăng dần → {@code created_at} →
     * {@code id}. Ba khoá cuối chỉ để phép chọn <b>tất định</b>: cùng một dòng họ phải luôn mở ra
     * cùng một gốc, nếu không người dùng sẽ thấy trang phả đồ "nhảy" giữa hai lần tải.</p>
     *
     * @param branchPathPrefix {@code ltree} của chi/ngành cần tìm gốc, lấy cả cây con
     *                         ({@code path <@ prefix}); {@code null} nghĩa là <b>cả dòng họ</b>
     * @param limit            trần số ứng viên
     */
    List<UUID> rootCandidates(String branchPathPrefix, int limit);

    /**
     * Khoá của mọi nhân khẩu <b>còn sống</b> chưa bị xoá mềm, thứ tự tất định.
     *
     * <p>Đây là <b>mẫu số</b> của câu "218 / 627 người còn sống đã điền" và cũng là tập nguồn của
     * danh bạ. Cả hai phải đi từ đúng một truy vấn: đếm bằng {@code COUNT(*)} riêng rồi liệt kê
     * bằng một câu khác là mở đường cho tử số và mẫu số nói về hai tập khác nhau.</p>
     *
     * @param limit trần cứng; chạm trần là dấu hiệu dòng họ đã vượt quy mô mà cách làm
     *              "nạp hết rồi lọc trong tiến trình" còn hợp lý
     */
    List<UUID> livingPersonIds(int limit);

    /**
     * Khoá của người <b>còn sống</b> có ít nhất một lớp tên khớp {@code q}, <b>không dấu cũng
     * khớp</b>.
     *
     * <h2>Vì sao phép so tên chạy trong CSDL chứ không trong Java</h2>
     * Định nghĩa "bỏ dấu" phải tồn tại đúng <b>một</b> nơi là hàm {@code vn_unaccent} của Postgres.
     * Một bản cài lại trong Java sẽ lệch ở đúng những ký tự hiếm, và triệu chứng là tìm kiếm lúc
     * ra lúc không mà không ai tái hiện được. Cột {@code person_name.name_unaccented} là
     * {@code GENERATED ALWAYS} từ {@code full_name} nên không bao giờ lệch, và chỉ mục GIN
     * {@code pg_trgm} phủ đúng phép {@code LIKE '%…%'} mà truy vấn này dùng.
     *
     * <p>Tìm trên <b>mọi lớp tên</b> (húy · tự · hiệu · thụy · thường gọi · pháp danh) như
     * {@code /persons/search}: người trong họ thường nhớ tên thường gọi chứ không nhớ tên khai
     * sinh. Việc một lớp tên có được <i>hiển thị</i> hay không là chuyện của bộ lọc riêng tư chạy
     * sau — khớp ở tên húy rồi hiện ra tên chính là hành vi đúng, giống hệt tìm kiếm.</p>
     */
    List<UUID> livingPersonIdsMatchingName(String q, int limit);
}
