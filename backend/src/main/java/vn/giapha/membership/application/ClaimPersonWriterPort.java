package vn.giapha.membership.application;

import java.util.UUID;
import vn.giapha.shared.vo.Gender;

/**
 * Ghi một nhân khẩu mới vào phả khi Trưởng chi <b>duyệt</b> một đơn "tôi chưa có trong phả".
 *
 * <h2>Ràng buộc 4 của design 07 §1.5 — cạnh đồ thị và dòng quan hệ trong CÙNG một transaction</h2>
 * Bất biến nặng nhất của cả hệ thống ({@code V2__core.sql} §2.4): cạnh AGE và dòng
 * {@code relationship} được ghi cùng lúc, hỏng thì cùng quay lại. Nó được giữ đúng một chỗ —
 * {@code LinkRelationshipService#attach} bên {@code genealogy} — và được ghim bởi
 * {@code GraphRelationalConsistencyIT}.
 *
 * <p>Vì thế hiện thực của cổng này <b>không chèn một dòng nào và không gõ một câu Cypher nào</b>:
 * nó uỷ thác cho {@code GenealogyBulkWriter} (named interface {@code "ghi-pha"}), vốn tồn tại đúng
 * để tránh việc dựng một đường ghi thứ hai. Một đường ghi riêng cho luồng duyệt đơn là cách chắc
 * chắn nhất để bất biến ấy bị vi phạm sáu tháng sau, bởi một người không đọc §2.4.</p>
 *
 * <h2>Vì sao cổng này nằm ở {@code application}</h2>
 * Cùng lý do với {@link ClaimScreeningPort}: {@code genealogy} đã phụ thuộc vào
 * {@code membership}, nên chiều ngược lại tạo chu trình. Phụ thuộc phải được <b>đảo</b>, và bên
 * hiện thực cần nhìn thấy cổng — mà chỉ {@code membership.application} mang
 * {@code @NamedInterface}.
 *
 * <h2>Hiện thực KHÔNG được mang {@code @Transactional}</h2>
 * Nó chạy trong transaction của {@code PersonClaimService#review}, nên "tạo người + nối quan hệ +
 * ghép tài khoản + đóng đơn" là <b>tất-cả-hoặc-không</b>. Tự mở transaction riêng thì một lần duyệt
 * hỏng giữa chừng sẽ để lại đúng cái node ma mà ràng buộc 1 sinh ra để tránh — chỉ là muộn hơn một
 * bước, và lần này thì không ai biết để đi dọn.
 *
 * <h2>Ràng buộc 5 rơi ra từ cách lưu, không phải từ đây</h2>
 * Người mới là người <b>còn sống</b>, và mô hình riêng tư V8 mặc định <b>kín</b> mọi nhóm trường.
 * Cổng này không đặt một cờ riêng tư nào — không cần, và đặt thì lại là một bản luật thứ hai.
 */
public interface ClaimPersonWriterPort {

    /**
     * Tạo nhân khẩu và <b>nối luôn</b> vào người thân được chỉ ra.
     *
     * <p>Cạnh quan hệ đi kèm ngay lúc tạo chứ không nối sau: {@code AddPersonService} suy <b>đời
     * thứ</b>, <b>chi kế thừa</b> và <b>thứ tự sinh</b> từ chính các liên kết ban đầu. Nối sau thì
     * ba giá trị ấy không bao giờ được suy ra, và một nhân khẩu thiếu đời thứ thì phả đồ không xếp
     * được vào hàng nào — tức đúng cái node mồ côi mà ràng buộc 2 sinh ra để tránh.</p>
     *
     * @return khoá nhân khẩu vừa tạo
     */
    UUID themNhanKhauTuDon(NewPersonFromClaim draft);

    /**
     * Ghi <b>số điện thoại người khai tự khai</b> vào hồ sơ nhân khẩu — <b>chỉ khi</b> ô liên hệ
     * của hồ sơ ấy đang trống.
     *
     * <h2>Vì sao việc này đi qua một cổng chứ không làm thẳng ở {@code membership}</h2>
     * {@code person.contact} là dữ liệu <b>Tầng 3</b> theo Nghị định 13/2023 và thuộc mô hình
     * riêng tư theo nhóm trường của V8. Một câu {@code UPDATE} từ {@code membership} sẽ đi vòng qua
     * cả bộ lọc ấy lẫn nhật ký sửa đổi của {@code genealogy} — và vòng qua một lần là bộ lọc mất
     * tư cách là nguồn chân lý duy nhất.
     *
     * <h2>"Chỉ khi còn trống" là một phần của hợp đồng, không phải chi tiết hiện thực</h2>
     * Số đã có trong hồ sơ là số Trưởng chi <b>đã</b> đặt sau khi đối chiếu. Ghi đè lặng lẽ bằng
     * số trên một lá đơn tự khai là cách mất một dữ liệu đã kiểm mà không ai thấy, và triệu chứng
     * chỉ hiện ra nhiều tháng sau khi một lời nhắc giỗ gửi vào số không còn dùng.
     *
     * <h2>KHÔNG mở mức chia sẻ</h2>
     * Nhóm {@code contact} mặc định kín và ở nguyên như thế. Ghi số vào hồ sơ không phải là công bố
     * nó; chủ hồ sơ tự mở nếu muốn.
     *
     * @return {@code true} nếu thực sự có ghi; {@code false} khi hồ sơ đã có số hoặc đơn không khai
     */
    boolean datSoDienThoaiNeuTrong(UUID personId, String phone, String lyDo);

    /**
     * Khai báo của một người chưa có trong phả, đã kèm người thân để nối vào.
     *
     * <p><b>Đúng một trong ba khoá người thân được đặt</b> — cha, mẹ, hoặc vợ/chồng.
     * {@code PersonClaim} và {@code ck_person_claim_shape} của V16 canh điều đó từ hai phía trước
     * khi tới đây.</p>
     *
     * @param laChong người mới đứng ở đầu {@code from} của cạnh hôn phối (tức là người chồng). Chỉ
     *                có nghĩa khi {@code spouseId != null}: {@code spouse_order} ("vợ thứ mấy") gắn
     *                vào người chồng, nên đảo vế là gán thứ tự vợ cho người vợ và
     *                {@code ux_relationship_spouse_order} canh nhầm người
     * @param ghiChu  nguồn gốc dữ liệu, ghi vào {@code audit_log} và vào ghi chú quan hệ — phải nói
     *                được đơn nào đã sinh ra nhân khẩu này
     */
    record NewPersonFromClaim(String fullName, Gender gender, Integer birthYear, UUID branchId,
                              UUID fatherId, UUID motherId, UUID spouseId, boolean laChong,
                              String ghiChu) {
    }
}
