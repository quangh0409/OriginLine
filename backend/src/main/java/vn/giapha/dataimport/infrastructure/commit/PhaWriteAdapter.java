package vn.giapha.dataimport.infrastructure.commit;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.port.PhaWritePort;
import vn.giapha.genealogy.application.GenealogyBulkWriter;
import vn.giapha.genealogy.application.ImportedEdgeDraft;
import vn.giapha.genealogy.application.ImportedPersonDraft;
import vn.giapha.shared.vo.LunarDate;

/**
 * Hiện thực {@link PhaWritePort} bằng cách gọi <b>đúng đường ghi đã có</b> của {@code genealogy},
 * qua facade {@link GenealogyBulkWriter} (named interface {@code "ghi-pha"}).
 *
 * <h2>Lớp này cố ý không chứa một dòng logic nghiệp vụ nào</h2>
 * Nó dịch kiểu, và chỉ dịch kiểu — y hệt {@code DuplicateScanAdapter} và {@code TabooScanAdapter}
 * bên cạnh. Không một câu SQL, không một câu Cypher. Đó không phải sở thích kiến trúc mà là cách
 * duy nhất giữ được bất biến lớn nhất của dự án: <b>cạnh AGE và dòng {@code relationship} ghi
 * trong cùng một transaction</b>. Bất biến ấy sống ở một chỗ duy nhất trong hệ thống
 * ({@code LinkRelationshipService.attach}) và được ghim bằng
 * {@code GraphRelationalConsistencyIT}; mở một đường ghi riêng cho nhập liệu — dù chỉ "cho nhanh",
 * dù chỉ một lần — là cách nó bị vi phạm sáu tháng sau bởi một người không đọc
 * {@code V2__core.sql} mục 2.4.
 *
 * <h2>Vì sao nằm ở gói riêng chứ không nằm chung với hai adapter dò</h2>
 * {@code infrastructure.genealogy} là nơi nhốt các lời gọi <b>đọc</b> (dò trùng, kỵ húy) và đang
 * do một luồng khác giữ. Gói này nhốt lời gọi <b>ghi</b>. Luật thì không đổi và vẫn là luật của
 * {@code dataimport/package-info}: cả {@code domain} lẫn {@code application} của context này
 * <b>không biết gì</b> về {@code genealogy}; mọi lời gọi sang đó gói gọn trong đúng ba tệp adapter.
 *
 * <h2>Bề mặt tiếp xúc — giữ cho nó nhỏ</h2>
 * Lớp này chạm đúng ba kiểu của {@code genealogy}, tất cả đều ở tầng {@code application} và đều
 * mang nhãn {@code "ghi-pha"}: {@link GenealogyBulkWriter}, {@link ImportedPersonDraft},
 * {@link ImportedEdgeDraft}. Không kiểu {@code domain} nào đi qua đây — {@code Person},
 * {@code PersonName}, {@code LifeDate}, {@code RelType} đều ở lại bên kia ranh giới. Nếu chữ ký
 * của facade sau này nhận thêm một kiểu mới thì kiểu ấy phải được gắn nhãn ở phía
 * {@code genealogy}, nếu không {@code ModularityTests} sẽ đỏ — và đó là hành vi mong muốn.
 */
@Component
public class PhaWriteAdapter implements PhaWritePort {

    private final GenealogyBulkWriter writer;

    public PhaWriteAdapter(GenealogyBulkWriter writer) {
        this.writer = writer;
    }

    @Override
    public UUID them(NhanKhauMoi nk) {
        return writer.them(toDraft(nk));
    }

    @Override
    public void capNhat(UUID personId, NhanKhauMoi nk) {
        writer.capNhat(personId, toDraft(nk));
    }

    @Override
    public UUID noiQuanHe(CanhMoi canh) {
        return writer.noiQuanHe(new ImportedEdgeDraft(canh.tu(), canh.den(), canh.loai(),
                canh.bac(), canh.loaiKeTu(), canh.ghiChu()));
    }

    @Override
    public boolean daCoCanh(UUID tu, UUID den, String loai) {
        return writer.daCoCanh(tu, den, loai);
    }

    @Override
    public boolean datDoiNeuTrong(UUID personId, Integer doi) {
        return writer.datDoiNeuTrong(personId, doi);
    }

    @Override
    public void xoaMem(UUID personId, String lyDo) {
        writer.xoaMem(personId, lyDo);
    }

    @Override
    public int goCanh(UUID tu, UUID den, String loai) {
        return writer.goCanh(tu, den, loai);
    }

    private static ImportedPersonDraft toDraft(NhanKhauMoi nk) {
        return new ImportedPersonDraft(nk.thuongGoi(), nk.huy(), nk.thuy(), nk.hanNom(),
                nk.gender(), nk.conSong(), nk.namSinh(), gio(nk.gio()), nk.nguyenQuan(),
                nk.chiId(), nk.chaId(), nk.meId(), nk.vongChongId(), nk.laChong(),
                nk.bacHonPhoi(), nk.conNuoi(),
                nk.thuocTinh(), nk.ghiChuNguon());
    }

    /**
     * Ngày giỗ — chỉ đi qua được khi <b>biết năm</b>.
     *
     * <p>{@link LunarDate} của shared kernel bắt buộc có năm, còn {@link LunarDeathDate} thì không
     * — vì "mất ngày 15 tháng 8, không rõ năm" là chuyện rất thường trong sổ cũ và vẫn đủ để cúng
     * giỗ. Ca không rõ năm <b>không bị bỏ rơi</b>: nó được vá thẳng vào cột {@code death_lunar}
     * trong cùng transaction, qua {@code CommitLedgerPort.vaCotNgoai} — lược đồ cho phép
     * ({@code ck_person_death_lunar} chỉ đòi {@code day} + {@code month}), chỉ có value object là
     * không chở nổi. Bỏ qua ca này nghĩa là đúng nhóm hồ sơ cổ nhất của dòng họ không bao giờ được
     * nhắc giỗ.</p>
     */
    private static LunarDate gio(LunarDeathDate d) {
        if (d == null || !d.coNam()) {
            return null;
        }
        return new LunarDate(d.year(), d.month(), d.day(), d.leap());
    }
}
