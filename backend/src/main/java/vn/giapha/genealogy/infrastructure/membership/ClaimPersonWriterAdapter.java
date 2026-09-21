package vn.giapha.genealogy.infrastructure.membership;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.genealogy.application.GenealogyBulkWriter;
import vn.giapha.genealogy.application.ImportedPersonDraft;
import vn.giapha.membership.application.ClaimPersonWriterPort;
import vn.giapha.shared.vo.Gender;

/**
 * Bộ <b>dịch kiểu</b> giữa một đơn "tôi chưa có trong phả" đã được duyệt và đường ghi thật của
 * {@code genealogy}. Không một dòng logic nghiệp vụ nào, và <b>không một câu SQL hay Cypher
 * nào</b>.
 *
 * <h2>Đường ra: {@code GenealogyBulkWriter}, named interface {@code "ghi-pha"}</h2>
 * Lớp ấy tồn tại đúng vì lý do này: bất biến "cạnh AGE và dòng {@code relationship} ghi trong cùng
 * một transaction" ({@code V2__core.sql} §2.4) được giữ ở đúng một chỗ, và một đường ghi thứ hai
 * dựng riêng cho luồng duyệt đơn là cách chắc chắn nhất để nó bị vi phạm sáu tháng sau. Ràng buộc 4
 * của design 07 §1.5 nói y hệt.
 *
 * <h2>{@code ImportedPersonDraft} được dùng lại, dù tên nó nói "import"</h2>
 * Cái tên phản ánh bên gọi <i>đầu tiên</i>, không phải giới hạn của kiểu: nó là "một hồ sơ đang
 * định ghi, kèm liên kết ban đầu", và đó đúng là thứ một đơn được duyệt mang theo. Dựng một kiểu
 * song song chỉ để đổi tên sẽ buộc {@code genealogy} mở thêm một kiểu nữa ra ngoài — mở rộng bề mặt
 * tiếp xúc để đổi lấy một cái tên đẹp hơn là một trao đổi tồi.
 *
 * <h2>Không {@code @Transactional}</h2>
 * Chạy trong transaction của {@code PersonClaimService#review}. Xem javadoc
 * {@link ClaimPersonWriterPort}.
 */
@Component
public class ClaimPersonWriterAdapter implements ClaimPersonWriterPort {

    private static final Logger log = LoggerFactory.getLogger(ClaimPersonWriterAdapter.class);

    private final GenealogyBulkWriter writer;

    public ClaimPersonWriterAdapter(GenealogyBulkWriter writer) {
        this.writer = writer;
    }

    @Override
    public UUID themNhanKhauTuDon(NewPersonFromClaim draft) {
        ImportedPersonDraft ho = new ImportedPersonDraft(
                draft.fullName(),
                // Khong ten huy, khong ten thuy, khong Han-Nom: nguoi khai la nguoi CON SONG va
                // dang tu khai qua mot bieu mau ba o. Nhung lop ten ay do Truong chi bo sung sau
                // khi doi chieu voi so giay, khong phai thu doan ra tu mot don.
                null, null, null,
                draft.gender() == null ? Gender.UNKNOWN : draft.gender(),
                // NGUOI MOI LA NGUOI CON SONG. Day cung la cho rang buoc 5 cua §1.5 tu dung: mo
                // hinh rieng tu V8 mac dinh KIN moi nhom truong, nen khong co co nao phai dat o
                // day — va dat thi lai la mot ban luat thu hai.
                true,
                draft.birthYear(),
                // Nguoi con song khong co ngay gio.
                null,
                null,
                draft.branchId(),
                draft.fatherId(),
                draft.motherId(),
                draft.spouseId(),
                draft.laChong(),
                // Bac hon phoi ("vo thu may") KHONG duoc doan tu mot don tu khai: doan sai la ghi
                // sai thu bac trong mot dong ho co da the, va sua lai thi phai danh so lai ca day
                // canh hon phoi. De trong, Truong chi dat sau.
                null,
                false,
                Map.of(),
                draft.ghiChu());
        UUID id = writer.them(ho);
        log.info("Duyet don tu nhan: tao nhan khau {} trong chi {}, noi vao cha={} me={} vo/chong={}",
                id, draft.branchId(), draft.fatherId(), draft.motherId(), draft.spouseId());
        return id;
    }

    /**
     * Uỷ thác nguyên vẹn cho {@link GenealogyBulkWriter#datSoDienThoaiNeuTrong} — luật "chỉ ghi khi
     * còn trống", việc bỏ qua nhóm riêng tư và việc giữ số ra khỏi {@code audit_log} đều nằm ở đó,
     * không có bản sao nào ở đây.
     */
    @Override
    public boolean datSoDienThoaiNeuTrong(UUID personId, String phone, String lyDo) {
        return writer.datSoDienThoaiNeuTrong(personId, phone, lyDo);
    }
}
