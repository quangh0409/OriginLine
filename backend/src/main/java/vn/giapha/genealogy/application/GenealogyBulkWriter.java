package vn.giapha.genealogy.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.giapha.genealogy.application.command.AddPersonCommand;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.application.command.RelationshipLinkCommand;
import vn.giapha.genealogy.application.command.UpdatePersonCommand;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.Relationship;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.genealogy.domain.port.BranchRepository;
import vn.giapha.genealogy.domain.port.PersonRepository;
import vn.giapha.genealogy.domain.port.RelationshipRepository;
import vn.giapha.genealogy.domain.port.TreeCachePort;
import vn.giapha.genealogy.domain.port.TreeGraphPort;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Cửa duy nhất</b> cho một đường nhập liệu hàng loạt ghi vào phả — named interface
 * {@code "ghi-pha"}.
 *
 * <h2>Vì sao lớp này tồn tại, và vì sao nó mỏng đến thế</h2>
 * Bất biến nặng nhất của cả hệ thống nằm ở {@code V2__core.sql} mục 2.4: <b>cạnh trong đồ thị AGE
 * và dòng {@code relationship} phải được ghi trong cùng một transaction</b>. Nó được giữ đúng một
 * chỗ — {@link LinkRelationshipService#attach} — và được ghim bằng
 * {@code GraphRelationalConsistencyIT}. Một đường ghi <b>thứ hai</b> dựng riêng cho nhập liệu là
 * cách chắc chắn nhất để bất biến ấy bị vi phạm sáu tháng sau, bởi một người không đọc mục 2.4.
 * Vì thế lớp này <b>không</b> chèn một dòng nào, không gõ một câu Cypher nào: nó dịch kiểu, rồi
 * uỷ thác cho đúng các use case mà màn "Thêm nhân khẩu" vẫn dùng.
 *
 * <h2>Không có {@code @Transactional} ở đây, và đó là chủ ý</h2>
 * Mọi phương thức chạy trong transaction của <b>bên gọi</b>. Ghi một lô là
 * <i>tất-cả-hoặc-không</i>: 400 người vào trọn vẹn hoặc không một ai. Nếu lớp này tự mở
 * transaction cho từng người thì cây sẽ nửa vời khi hỏng giữa chừng — một số người có cha, một số
 * treo lơ lửng — và Trưởng chi không có cách nào biết cái gì đã vào. Các service được uỷ thác đều
 * mang {@code @Transactional} mặc định ({@code REQUIRED}) nên chúng <b>nhập</b> vào transaction
 * của lô chứ không mở transaction mới.
 *
 * <h2>Phân quyền vẫn nguyên</h2>
 * Không có đường vòng nào: từng lời gọi bên dưới vẫn đi qua {@link GenealogyAccessGuard} với
 * {@code ltree} của chi đích, nên Trưởng chi Ất không nhập được người vào chi Giáp dù tệp có ghi
 * gì. Kiểm thêm một lần ở đây chỉ tạo hai nguồn chân lý cho cùng một câu hỏi.
 */
@org.springframework.modulith.NamedInterface("ghi-pha")
@Service
public class GenealogyBulkWriter {

    private static final Logger log = LoggerFactory.getLogger(GenealogyBulkWriter.class);

    private final AddPersonService addPerson;
    private final UpdatePersonService updatePerson;
    private final LinkRelationshipService links;
    private final SoftDeletePersonService softDelete;
    private final PersonRepository persons;
    private final RelationshipRepository relationships;
    private final TreeGraphPort graph;
    private final AuditPort audit;
    private final TreeCachePort treeCache;
    private final BranchRepository branches;
    private final PrivacyTierService privacy;
    private final GenealogyAccessGuard guard;

    public GenealogyBulkWriter(AddPersonService addPerson, UpdatePersonService updatePerson,
                               LinkRelationshipService links, SoftDeletePersonService softDelete,
                               PersonRepository persons, RelationshipRepository relationships,
                               TreeGraphPort graph, AuditPort audit, TreeCachePort treeCache,
                               BranchRepository branches, PrivacyTierService privacy,
                               GenealogyAccessGuard guard) {
        this.addPerson = addPerson;
        this.updatePerson = updatePerson;
        this.links = links;
        this.softDelete = softDelete;
        this.persons = persons;
        this.relationships = relationships;
        this.graph = graph;
        this.audit = audit;
        this.treeCache = treeCache;
        this.branches = branches;
        this.privacy = privacy;
        this.guard = guard;
    }

    /**
     * Thêm một nhân khẩu, nối luôn vào cha/mẹ nếu đã biết.
     *
     * <p>Cạnh cha/mẹ đi kèm ngay lúc tạo chứ không nối sau: {@code AddPersonService} suy
     * <b>đời thứ</b>, <b>chi kế thừa</b> và <b>thứ tự sinh</b> từ chính các liên kết ban đầu. Nối
     * sau thì ba giá trị ấy không bao giờ được suy ra, và một phả đồ thiếu đời thứ thì không xếp
     * hàng được.</p>
     *
     * <p>Hai cờ ghi đè đều bật: kỵ húy và nghi trùng đã được bộ kiểm của đường nhập liệu báo
     * <b>trước khi</b> người nhập bấm duyệt, và họ đã tick "tôi đã xem". Bật lại ở đây thì lô dừng
     * giữa chừng vì một cảnh báo người dùng đã trả lời rồi.</p>
     */
    public UUID them(ImportedPersonDraft draft) {
        List<RelationshipLinkCommand> lienKet = new ArrayList<>(3);
        RelType loaiCha = draft.conNuoi() ? RelType.PARENT_ADOPT : RelType.PARENT_BIO;
        if (draft.chaId() != null) {
            lienKet.add(new RelationshipLinkCommand(loaiCha, draft.chaId(), true, null, null,
                    null, null, draft.ghiChuNguon()));
        }
        if (draft.meId() != null) {
            lienKet.add(new RelationshipLinkCommand(loaiCha, draft.meId(), true, null, null,
                    null, null, draft.ghiChuNguon()));
        }
        // Dau/re khong co cha me trong ho: doi thu chi suy duoc tu vo/chong. Canh hon phoi that
        // duoc noi rieng o buoc sau vi no con phai mang spouse_order, nhung neu khong dua mot lien
        // ket nao vao day thi nguoi ay khong co doi thu va khong bao gio hien len pha do.
        if (lienKet.isEmpty() && draft.vongChongId() != null) {
            // otherIsSource = !laChong: voi mot ba dau thi nguoi chong dung o dau `from`, va
            // spouse_order ("vo thu may") gan vao dung nguoi chong ay. Ve nguoc canh nay la gan
            // thu tu vo cho nguoi vo, roi ux_relationship_spouse_order canh nham nguoi.
            lienKet.add(new RelationshipLinkCommand(RelType.SPOUSE, draft.vongChongId(),
                    !draft.laChong(), null, draft.bacHonPhoi(), null, null, draft.ghiChuNguon()));
        }

        AddPersonCommand cmd = new AddPersonCommand(tenCuaDraft(draft),
                draft.gender() == null ? Gender.UNKNOWN : draft.gender(),
                draft.conSong(),
                namSinh(draft.namSinh()),
                ngayGio(draft),
                draft.nguyenQuan(), null, null, null, null,
                draft.chiId(), null,
                draft.thuocTinh().isEmpty() ? null : draft.thuocTinh(),
                null, lienKet, true, true, draft.ghiChuNguon());
        return addPerson.add(cmd).id();
    }

    /**
     * Cập nhật hồ sơ một nhân khẩu <b>đã có</b> — nhánh UPDATE của đường nhập liệu.
     *
     * <p>Chỉ đụng vào các trường mà mẫu Excel thật sự chở được. Mọi trường khác dùng
     * {@link FieldChange#keep()} chứ không phải {@code null}: "không gửi" và "gửi rỗng" là hai ý
     * định khác nhau, và nhầm hai thứ này biến một lần tải lại vô hại thành một lệnh xoá trắng
     * tiểu sử của 400 người.</p>
     *
     * <p><b>Cố ý không kiểm khoá lạc quan</b> ({@code expectedVersion = null}): lô nhập liệu không
     * đến từ một form có ETag. Người sửa song song được canh ở tầng trên bằng khoá tư vấn theo
     * chi.</p>
     */
    public void capNhat(UUID personId, ImportedPersonDraft draft) {
        LifeDate gio = ngayGio(draft);
        UpdatePersonCommand cmd = new UpdatePersonCommand(personId, null,
                FieldChange.set(tenCuaDraft(draft)),
                FieldChange.setIfNotNull(draft.gender()),
                FieldChange.set(draft.conSong()),
                FieldChange.keep(),
                FieldChange.setIfNotNull(namSinh(draft.namSinh())),
                gio == null ? FieldChange.keep() : FieldChange.set(gio),
                FieldChange.setIfNotNull(draft.nguyenQuan()),
                FieldChange.keep(), FieldChange.keep(), FieldChange.keep(), FieldChange.keep(),
                FieldChange.keep(),
                FieldChange.setIfNotNull(draft.chiId()),
                FieldChange.keep(),
                draft.thuocTinh().isEmpty() ? FieldChange.keep() : FieldChange.set(draft.thuocTinh()),
                FieldChange.keep(),
                true, draft.ghiChuNguon());
        updatePerson.update(cmd);
    }

    /** Nối một cạnh quan hệ. Ghi cả cạnh AGE lẫn dòng bản chiếu, trong transaction của bên gọi. */
    public UUID noiQuanHe(ImportedEdgeDraft edge) {
        RelType loai = RelType.valueOf(edge.loai());
        HeirKind keTu = edge.loaiKeTu() == null ? null : HeirKind.valueOf(edge.loaiKeTu());
        return links.link(new LinkRelationshipCommand(edge.tu(), edge.den(), loai, keTu,
                edge.bacHonPhoi(), null, null, edge.ghiChu())).id();
    }

    /**
     * Đã có cạnh loại này giữa hai người chưa.
     *
     * <p>Cần cho <b>lần tải lại thứ hai</b>: tệp đã sửa thường giữ nguyên phần lớn quan hệ, và nối
     * lại một cạnh đã có sẽ đâm vào {@code ux_relationship_parent} / {@code ux_relationship_spouse}
     * rồi cuộn lại cả lô 400 người vì một cạnh trùng vô hại. Hỏi trước rẻ hơn bắt ngoại lệ: bắt
     * ngoại lệ thì ta không phân biệt được "cạnh đã có" với một vi phạm ràng buộc thật.</p>
     */
    public boolean daCoCanh(UUID tu, UUID den, String loai) {
        RelType loaiCanh = RelType.valueOf(loai);
        for (Relationship rel : relationships.byPerson(tu)) {
            if (rel.isCurrent() && rel.relType() == loaiCanh
                    && rel.fromPersonId().equals(tu) && rel.toPersonId().equals(den)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Đặt đời thứ cho một người <b>chỉ khi</b> người ấy chưa có đời thứ nào.
     *
     * <h2>Vì sao lối này tồn tại, và vì sao nó hẹp đến thế</h2>
     * Đời thứ được <b>suy ra từ liên kết</b> chứ không nhận từ client — đó là lý do
     * {@code AddPersonCommand} cố ý không có trường {@code generation}. Nhưng thuỷ tổ của một lô
     * nhập liệu <b>không có liên kết nào</b>: không cha, không mẹ. Suy từ đâu cũng không ra, nên
     * người ấy nhận đời thứ {@code null}, và mọi hậu duệ của người ấy cũng vậy — cả cây nhập vào
     * không có đời thứ, và phả đồ không xếp được một hàng nào. Cột <b>Đời</b> trong tệp Excel là
     * nguồn duy nhất còn lại cho đúng những dòng gốc ấy.
     *
     * <p>Điều kiện "chỉ khi chưa có" là thứ giữ cho lối này không thành một cửa hậu: một khi đồ thị
     * đã suy ra được đời thứ thì con số trong tệp <b>không</b> được phép ghi đè lên nó. Đánh số
     * lại cả một cây con là nghiệp vụ khác hẳn, ảnh hưởng hàng trăm bản ghi và cần nhật ký riêng.</p>
     *
     * @return {@code true} nếu thực sự có đặt
     */
    public boolean datDoiNeuTrong(UUID personId, Integer doi) {
        if (doi == null || doi < 1) {
            return false;
        }
        Person person = load(personId);
        if (person.generation() != null) {
            return false;
        }
        Map<String, Object> truoc = person.auditSnapshot();
        person.placeInGeneration(doi);
        persons.save(person);
        graph.syncPersonNode(person.rawId(),
                person.gender() == null ? null : person.gender().name(), doi, person.isDeleted());
        audit.record("Person", personId.toString(), AuditPort.Action.UPDATE, truoc,
                person.auditSnapshot(), List.of("generation"),
                "Doi thu lay tu cot Doi cua tep nhap lieu vi khong co lien ket nao de suy ra");
        return true;
    }

    /** Xoá <b>mềm</b> một nhân khẩu. Không có và sẽ không bao giờ có đường xoá cứng. */
    public void xoaMem(UUID personId, String lyDo) {
        softDelete.softDelete(personId, lyDo);
    }

    /**
     * Gỡ một cạnh: xoá mềm dòng bản chiếu <b>và</b> gỡ cạnh AGE, trong cùng transaction.
     *
     * <h2>Vì sao phải gỡ cạnh AGE chứ không chỉ hạ cờ ở bảng</h2>
     * {@code V7__graph.sql} quy định node đã xoá mềm <b>vẫn được đi xuyên qua</b>, để không làm
     * đứt đường nối giữa các đời. Hệ quả: nếu chỉ xoá mềm người mà để nguyên cạnh thì người ấy
     * thành một <b>người cha ma</b> — vẫn nằm trên đường duyệt, vẫn ảnh hưởng danh xưng và LCA, và
     * không có gì báo. Đây đúng là chỗ mà việc rút lại một lô nhập sai sẽ hỏng nếu làm ẩu.
     *
     * @return số cạnh thực sự đã gỡ (0 khi cạnh đã biến mất từ trước — gỡ lại là vô hại)
     */
    public int goCanh(UUID tu, UUID den, String loai) {
        RelType loaiCanh = RelType.valueOf(loai);
        CallerContext caller = privacy.caller();
        Person a = load(tu);
        Person b = load(den);
        BranchDirectory dir = BranchDirectory.load(branches,
                Arrays.asList(a.primaryBranchId(), b.primaryBranchId()));
        guard.requireWriteAccess(caller, dir.pathOf(a.primaryBranchId()));
        guard.requireWriteAccess(caller, dir.pathOf(b.primaryBranchId()));

        int daGo = 0;
        for (Relationship rel : relationships.byPerson(tu)) {
            if (!rel.isCurrent() || rel.relType() != loaiCanh
                    || !rel.fromPersonId().equals(tu) || !rel.toPersonId().equals(den)) {
                continue;
            }
            rel.softDelete();
            relationships.save(rel);
            daGo++;
            audit.record("Relationship", rel.id().toString(), AuditPort.Action.UNLINK_RELATIONSHIP,
                    Map.of("relType", rel.relType().name(), "fromPersonId", tu.toString(),
                            "toPersonId", den.toString()),
                    null, List.of("isDeleted"), "Go canh khi rut lai mot lo nhap lieu");
        }
        if (daGo > 0) {
            // Do thi la nguon chan ly: bang va do thi phai roi nhau trong CUNG transaction nay.
            graph.unlink(tu, den, loaiCanh);
            treeCache.evictAll();
            log.info("Da go {} canh {} giua {} va {}", daGo, loaiCanh, tu, den);
        }
        return daGo;
    }

    private List<PersonName> tenCuaDraft(ImportedPersonDraft draft) {
        List<PersonName> ten = new ArrayList<>(3);
        if (coChu(draft.thuongGoi())) {
            ten.add(new PersonName(null, NameType.THUONG_GOI, draft.thuongGoi(), draft.hanNom(),
                    true, null));
        }
        if (coChu(draft.huy())) {
            ten.add(PersonName.of(NameType.HUY, draft.huy(), false));
        }
        if (coChu(draft.thuy())) {
            ten.add(PersonName.of(NameType.THUY, draft.thuy(), false));
        }
        return ten;
    }

    /**
     * Năm sinh trần trụi thành một mốc sinh có <b>mức chính xác YEAR</b>.
     *
     * <p>Không đánh dấu mức chính xác thì 01/01 trở thành một ngày sinh thật, và hệ thống sẽ chúc
     * mừng sinh nhật cả họ vào mùng một Tết dương lịch.</p>
     */
    private static LifeDate namSinh(Integer nam) {
        if (nam == null) {
            return null;
        }
        return LifeDate.of(LocalDate.of(nam, 1, 1), null, DatePrecision.YEAR);
    }

    private static LifeDate ngayGio(ImportedPersonDraft draft) {
        if (draft.conSong() || draft.ngayGio() == null) {
            return null;
        }
        return LifeDate.ofLunar(draft.ngayGio());
    }

    private static boolean coChu(String s) {
        return s != null && !s.isBlank();
    }

    private Person load(UUID id) {
        return persons.byId(PersonId.of(id)).orElseThrow(() -> new NotFoundException(
                GenealogyProblemCodes.NOT_FOUND, "Khong tim thay nhan khau voi dinh danh " + id));
    }
}
