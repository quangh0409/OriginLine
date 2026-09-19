package vn.giapha.dataimport.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.dataimport.domain.CellCodec;
import vn.giapha.dataimport.domain.CommitEntry;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.port.CommitLedgerPort;
import vn.giapha.dataimport.domain.port.ExternalRefPort;
import vn.giapha.dataimport.domain.port.ImportBatchRepository;
import vn.giapha.dataimport.domain.port.PhaWritePort;

/**
 * <b>Đúng một transaction</b>: toàn bộ lô vào phả, hoặc không một ai.
 *
 * <h2>Vì sao tất-cả-hoặc-không, chứ không chia khối cho chạy tiếp được</h2>
 * Lý do không nằm ở hiệu năng mà ở <b>khả năng phục hồi của con người</b>. Sau một lần ghi hỏng,
 * câu hỏi duy nhất Trưởng chi cần trả lời được là <i>"vào chưa?"</i>. Với một transaction, câu trả
 * lời là <b>rồi</b> hoặc <b>chưa</b>. Với lối chia khối 50 dòng, câu trả lời là "vào 137 người,
 * mời anh dò xem thiếu ai" — và đó là một buổi chiều mất trắng, lặp lại mỗi lần thử. Tệ hơn nữa:
 * cây nửa vời nghĩa là một số người có cha, một số treo lơ lửng, vợ chồng nối một nửa.
 *
 * <p>Cái giá, nói thẳng: một transaction ôm ~400 lệnh chèn {@code person}, ~400 đỉnh AGE và ~700
 * cạnh (mỗi cạnh là hai lệnh ghi — cạnh AGE và dòng bản chiếu). Nó giữ khoá lâu và làm phình bảng.
 * Chấp nhận được vì việc này xảy ra chừng <b>bốn lần cho cả dòng họ</b>, không phải bốn lần một
 * phút.
 *
 * <h2>Sáu bước, đúng thứ tự này</h2>
 * <ol>
 *   <li><b>Khoá tư vấn theo chi.</b> Hai lô của cùng một chi không bao giờ ghi chồng nhau.</li>
 *   <li><b>Tạo/cập nhật toàn bộ nhân khẩu theo thứ tự đời</b> — cha mẹ trước, con sau. Cạnh cha/mẹ
 *       đi kèm ngay lúc tạo, vì đời thứ, chi kế thừa và thứ tự sinh đều suy ra từ liên kết.</li>
 *   <li><b>Ghi khoá bất biến</b> {@code person_external_ref} cho từng người mới.</li>
 *   <li><b>Nối cạnh hôn phối</b>, mang theo bậc (vợ cả / vợ lẽ).</li>
 *   <li><b>Nối cạnh kế tự</b> ({@code HEIR}) — đích tôn, thừa tự, kế tự.</li>
 *   <li><b>Vá hai cột ngoài aggregate</b>, ghi sổ cái, đánh dấu lô đã vào phả.</li>
 * </ol>
 *
 * <h2>Timeout đặt tường minh</h2>
 * 300 giây. Một tệp 400 dòng chạy dưới 30 giây; con số 300 là biên để một lô rất lớn hoặc một
 * máy chậm không chết vì đồng hồ giữa chừng, rồi lặp lại đúng như thế mỗi lần thử.
 */
@Service
public class ImportCommitWriter {

    private static final Logger log = LoggerFactory.getLogger(ImportCommitWriter.class);

    private final PhaWritePort pha;
    private final ExternalRefPort externalRefs;
    private final CommitLedgerPort ledger;
    private final ImportBatchRepository batches;

    public ImportCommitWriter(PhaWritePort pha, ExternalRefPort externalRefs,
                              CommitLedgerPort ledger, ImportBatchRepository batches) {
        this.pha = pha;
        this.externalRefs = externalRefs;
        this.ledger = ledger;
        this.batches = batches;
    }

    /** Số người tạo mới, số người cập nhật, số cạnh đã nối. */
    public record KetQua(int daTao, int daCapNhat, int soCanh) {
    }

    @Transactional(timeout = 300)
    public KetQua ghi(ImportBatch batch, List<PersonRow> thuTu, List<MarriageRow> marriages,
                      UUID actorUserId) {
        batches.khoaChi(batch.branchId());

        Map<String, UUID> idTheoMa = new LinkedHashMap<>(
                externalRefs.resolve(batch.branchId(), maCuaLo(thuTu, marriages)));

        // --- Cac dong duoc NGUOI quyet la "gop voi mot ho so da co trong pha" ---
        // Ma cua chung CHUA co trong person_external_ref — do la ly do bo do kêu len ngay tu dau.
        // Gieo chung vao ban do TRUOC vong lap chinh de dong ay ra UPDATE thay vi CREATE, va giu
        // lai danh sach de con ghi khoa bat bien: khong ghi thi lan tai lai sau lai thay mot ma la,
        // lai sinh cap nghi trung, va ca thao tac gop tro thanh viec phai lam lai moi lan.
        Set<String> gopVaoHoSoDaCo = new LinkedHashSet<>();
        for (PersonRow row : thuTu) {
            UUID daQuyet = row.resolvedPersonId();
            if (daQuyet != null && row.externalCode() != null
                    && !idTheoMa.containsKey(row.externalCode())) {
                idTheoMa.put(row.externalCode(), daQuyet);
                gopVaoHoSoDaCo.add(row.externalCode());
            }
        }

        Map<String, MarriageRow> honPhoiCuaMa = honPhoiTheoMa(thuTu, marriages);

        List<CommitEntry> soCai = new ArrayList<>();
        Set<String> canhHonPhoiDaNoi = new LinkedHashSet<>();
        int daTao = 0;
        int soCanh = 0;

        // --- Buoc 2 + 3: nhan khau theo thu tu doi ---
        for (PersonRow row : thuTu) {
            String nguon = nguon(batch, row);
            UUID chaId = idTheoMa.get(chuanHoa(row.fatherCode()));
            UUID meId = idTheoMa.get(chuanHoa(row.motherCode()));
            MarriageRow honPhoi = chaId == null && meId == null
                    ? honPhoiCuaMa.get(row.externalCode()) : null;
            boolean laChong = honPhoi != null && row.externalCode().equals(honPhoi.husbandCode());
            UUID vongChongId = honPhoi == null ? null
                    : idTheoMa.get(laChong ? honPhoi.wifeCode() : honPhoi.husbandCode());

            PhaWritePort.NhanKhauMoi nk = new PhaWritePort.NhanKhauMoi(row.fullName(),
                    row.tabooName(), row.posthumousName(), row.hanNomName(), row.gender(),
                    Boolean.FALSE.equals(row.daMat()), row.birthYear(), row.death(),
                    row.nativePlace(), row.nativePlaceCode(), batch.branchId(), chaId, meId,
                    vongChongId, laChong, honPhoi == null ? null : honPhoi.spouseOrder(),
                    row.parentRel() == CellCodec.ParentRel.ADOPT, Map.of(), nguon);

            UUID daCo = idTheoMa.get(row.externalCode());
            UUID personId;
            if (daCo == null) {
                personId = pha.them(nk);
                externalRefs.ghi(batch.branchId(), row.externalCode(), personId, batch.id());
                idTheoMa.put(row.externalCode(), personId);
                soCai.add(CommitEntry.nguoi(row.rowNo(), row.externalCode(), personId, true, null));
                daTao++;
                // Canh cha/me (va canh hon phoi cua dau/re) da duoc tao BEN TRONG them(); ghi so
                // cai o day de viec go lo biet chinh xac phai go nhung canh nao.
                soCanh += ghiSoCaiCanhBanDau(soCai, row, personId, chaId, meId, vongChongId,
                        laChong, canhHonPhoiDaNoi, honPhoi);
            } else {
                personId = daCo;
                pha.capNhat(personId, nk);
                if (gopVaoHoSoDaCo.contains(row.externalCode())) {
                    // GOP: ma trong tep tro sang mot person DA TON TAI. Khong tao nguoi moi, va
                    // person_created = FALSE ngay duoi — go lo se go dong khoa nay chu tuyet doi
                    // khong duoc xoa mem nguoi da song trong pha tu truoc lo.
                    externalRefs.ghi(batch.branchId(), row.externalCode(), personId, batch.id());
                }
                soCai.add(CommitEntry.nguoi(row.rowNo(), row.externalCode(), personId, false, null));
                // Tai lai mot tep da sua co the BO SUNG ma cha cho mot nguoi von thieu. Noi bu o
                // day, va chi khi canh chua ton tai — noi lai mot canh da co se dam vao
                // ux_relationship_parent roi cuon lai ca lo vi mot canh trung vo hai.
                soCanh += noiNeuThieu(soCai, row, chaId, personId, row.parentRel());
                soCanh += noiNeuThieu(soCai, row, meId, personId, row.parentRel());
            }
            // Thuy to cua lo khong co lien ket nao nen he thong khong suy duoc doi thu; cot Doi
            // trong tep la nguon duy nhat con lai cho dung nhung dong goc ay.
            pha.datDoiNeuTrong(personId, row.generation());
        }

        // --- Buoc 4: hon phoi, mang theo bac (vo ca / vo le) ---
        for (MarriageRow m : marriages) {
            if (!m.duCap()) {
                continue;
            }
            UUID chongId = idTheoMa.get(m.husbandCode());
            UUID voId = idTheoMa.get(m.wifeCode());
            if (chongId == null || voId == null || canhHonPhoiDaNoi.contains(capMa(m))) {
                continue;
            }
            if (pha.daCoCanh(chongId, voId, "SPOUSE")) {
                continue;
            }
            String nguon = nguonHonPhoi(batch, m);
            UUID relId = pha.noiQuanHe(
                    PhaWritePort.CanhMoi.honPhoi(chongId, voId, m.spouseOrder(), nguon));
            soCai.add(CommitEntry.canh(m.rowNo(), chongId, voId, "SPOUSE", relId));
            canhHonPhoiDaNoi.add(capMa(m));
            soCanh++;
        }

        // --- Buoc 5: ke tu / dich ton ---
        for (PersonRow row : thuTu) {
            if (row.heirOfCode() == null || row.heirOfCode().isBlank() || row.heirType() == null) {
                continue;
            }
            UUID deLai = idTheoMa.get(chuanHoa(row.heirOfCode()));
            UUID noiDoi = idTheoMa.get(row.externalCode());
            if (deLai == null || noiDoi == null || deLai.equals(noiDoi)
                    || pha.daCoCanh(deLai, noiDoi, "HEIR")) {
                continue;
            }
            UUID relId = pha.noiQuanHe(PhaWritePort.CanhMoi.keTu(deLai, noiDoi, row.heirType(),
                    nguon(batch, row)));
            soCai.add(CommitEntry.canh(row.rowNo(), deLai, noiDoi, "HEIR", relId));
            soCanh++;
        }

        // --- Buoc 6: va hai cot ma aggregate Person khong cho noi, roi chot so cai ---
        // Phai o CUOI: luc nay dong person da ton tai that trong CSDL va se khong bi mot lan flush
        // nao cua ORM ghi de len nua. Hai cot deu la cot vo huong cua bang person — khong cot nao
        // cham toi relationship hay do thi AGE, nen bat bien lon nhat cua du an khong bi dung toi.
        for (PersonRow row : thuTu) {
            UUID personId = idTheoMa.get(row.externalCode());
            if (personId == null) {
                continue;
            }
            ledger.vaCotNgoai(personId, row.nativePlaceCode(), gioThieuNam(row));
        }

        List<CommitEntry> chot = chupPhienBan(soCai);
        ledger.ghi(batch.id(), chot);
        batches.markCommitted(batch.id(), actorUserId);

        int daCapNhat = thuTu.size() - daTao;
        log.info("Da ghi lo {} vao pha: {} nguoi moi, {} nguoi cap nhat, {} canh",
                batch.id(), daTao, daCapNhat, soCanh);
        return new KetQua(daTao, daCapNhat, soCanh);
    }

    /**
     * Chụp {@code person.version} <b>sau khi mọi lệnh ghi đã chạy</b>.
     *
     * <p>Nối một cạnh cha–con còn đặt lại đời thứ cho người con, tức là còn tăng version một nhịp
     * nữa. Chụp version quá sớm thì mọi lần gỡ lô đều bị từ chối với lý do "đã có người sửa hồ sơ"
     * trong khi không ai sửa cả — và người dùng sẽ thôi tin vào cả phép kiểm ấy.</p>
     */
    private List<CommitEntry> chupPhienBan(List<CommitEntry> soCai) {
        List<UUID> nguoi = soCai.stream()
                .filter(e -> e.kind() == CommitEntry.Kind.PERSON)
                .map(CommitEntry::personId)
                .toList();
        Map<UUID, Long> phienBan = ledger.phienBanCua(nguoi);
        List<CommitEntry> chot = new ArrayList<>(soCai.size());
        for (CommitEntry e : soCai) {
            chot.add(e.kind() == CommitEntry.Kind.PERSON
                    ? new CommitEntry(e.kind(), e.rowNo(), e.externalCode(), e.personId(),
                            e.personCreated(), phienBan.get(e.personId()), null, null, null, null)
                    : e);
        }
        return chot;
    }

    private int ghiSoCaiCanhBanDau(List<CommitEntry> soCai, PersonRow row, UUID personId,
                                   UUID chaId, UUID meId, UUID vongChongId, boolean laChong,
                                   Set<String> daNoi, MarriageRow honPhoi) {
        String loaiCha = row.parentRel() == CellCodec.ParentRel.ADOPT ? "PARENT_ADOPT" : "PARENT_BIO";
        int n = 0;
        if (chaId != null) {
            soCai.add(CommitEntry.canh(row.rowNo(), chaId, personId, loaiCha, null));
            n++;
        }
        if (meId != null) {
            soCai.add(CommitEntry.canh(row.rowNo(), meId, personId, loaiCha, null));
            n++;
        }
        if (vongChongId != null) {
            UUID tu = laChong ? personId : vongChongId;
            UUID den = laChong ? vongChongId : personId;
            soCai.add(CommitEntry.canh(row.rowNo(), tu, den, "SPOUSE", null));
            daNoi.add(capMa(honPhoi));
            n++;
        }
        return n;
    }

    private int noiNeuThieu(List<CommitEntry> soCai, PersonRow row, UUID chaMeId, UUID conId,
                            CellCodec.ParentRel quanHe) {
        if (chaMeId == null || chaMeId.equals(conId)) {
            return 0;
        }
        String loai = quanHe == CellCodec.ParentRel.ADOPT ? "PARENT_ADOPT" : "PARENT_BIO";
        if (pha.daCoCanh(chaMeId, conId, loai)) {
            return 0;
        }
        UUID relId = pha.noiQuanHe(new PhaWritePort.CanhMoi(chaMeId, conId, loai, null, null, null));
        soCai.add(CommitEntry.canh(row.rowNo(), chaMeId, conId, loai, relId));
        return 1;
    }

    /**
     * Hôn phối của những dòng <b>không có cha mẹ trong họ</b> — tức dâu/rể.
     *
     * <p>Chỉ những dòng ấy mới cần cạnh hôn phối ngay lúc tạo, và chỉ lấy <b>dòng hôn phối đầu
     * tiên</b> của mỗi người: một ông có ba bà thì đời thứ của ông suy từ bà nào cũng như nhau, còn
     * hai cạnh hôn phối tạo cùng lúc sẽ đâm vào nhau.</p>
     */
    private static Map<String, MarriageRow> honPhoiTheoMa(List<PersonRow> rows,
                                                          List<MarriageRow> marriages) {
        Set<String> moCoi = new LinkedHashSet<>();
        for (PersonRow row : rows) {
            if (!row.coCha() && !row.coMe() && row.externalCode() != null) {
                moCoi.add(row.externalCode());
            }
        }
        Map<String, MarriageRow> ketQua = new LinkedHashMap<>();
        for (MarriageRow m : marriages) {
            if (!m.duCap()) {
                continue;
            }
            if (moCoi.contains(m.husbandCode())) {
                ketQua.putIfAbsent(m.husbandCode(), m);
            }
            if (moCoi.contains(m.wifeCode())) {
                ketQua.putIfAbsent(m.wifeCode(), m);
            }
        }
        return ketQua;
    }

    private static Set<String> maCuaLo(List<PersonRow> rows, List<MarriageRow> marriages) {
        Set<String> ma = new LinkedHashSet<>();
        for (PersonRow row : rows) {
            themNeuCo(ma, row.externalCode());
            themNeuCo(ma, row.fatherCode());
            themNeuCo(ma, row.motherCode());
            themNeuCo(ma, row.heirOfCode());
        }
        for (MarriageRow m : marriages) {
            themNeuCo(ma, m.husbandCode());
            themNeuCo(ma, m.wifeCode());
        }
        return ma;
    }

    private static void themNeuCo(Set<String> dich, String ma) {
        if (ma != null && !ma.isBlank()) {
            dich.add(ma.trim());
        }
    }

    private static String capMa(MarriageRow m) {
        return m.husbandCode() + "|" + m.wifeCode();
    }

    private static String chuanHoa(String ma) {
        return ma == null ? "" : ma.trim();
    }

    /**
     * Ngày giỗ <b>thiếu năm</b> — ca mà value object của shared kernel không chở nổi.
     *
     * <p>"Mất ngày 15 tháng 8, không rõ năm" là chuyện rất thường trong sổ cũ, và nó vẫn đủ để cúng
     * giỗ: cả họ giỗ theo ngày và tháng âm. Lược đồ cho phép ({@code ck_person_death_lunar} chỉ đòi
     * {@code day} + {@code month}) nên dữ liệu không mất — nó được vá thẳng vào cột trong cùng
     * transaction. Bỏ qua ca này nghĩa là đúng nhóm hồ sơ cổ nhất của dòng họ không bao giờ được
     * nhắc giỗ, mà nhắc giỗ là lý do dòng họ mở ứng dụng.</p>
     */
    private static LunarDeathDate gioThieuNam(PersonRow row) {
        LunarDeathDate d = row.death();
        return d != null && !d.coNam() && Boolean.TRUE.equals(row.daMat()) ? d : null;
    }

    /**
     * Ghi chú nguồn, điền tự động vào từng nhân khẩu.
     *
     * <p>Rẻ đến mức gần như không tốn gì, và đắt gấp bội khi cần mà không có: ba tháng sau, mở hồ
     * sơ một cụ là biết ngay <b>dòng nào của tệp nào</b> đã sinh ra nó — phân biệt được "họ chép
     * sai sổ" với "ta phân tích sai tệp", hai chuyện có cách chữa hoàn toàn khác nhau.</p>
     */
    private static String nguon(ImportBatch batch, PersonRow row) {
        return "Nhập từ lô " + batch.id() + " · dòng " + row.rowNo() + " · "
                + batch.originalFilename();
    }

    private static String nguonHonPhoi(ImportBatch batch, MarriageRow row) {
        return "Nhập từ lô " + batch.id() + " · Hôn phối dòng " + row.rowNo() + " · "
                + batch.originalFilename();
    }
}
