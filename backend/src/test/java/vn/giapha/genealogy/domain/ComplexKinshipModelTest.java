package vn.giapha.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;

/**
 * Mô hình dữ liệu có biểu diễn được các tình huống phả hệ Việt thật hay không.
 *
 * <p>Đây là phần "mô hình", không phải phần "suy luận danh xưng" (việc đó ở context
 * {@code kinship}). Câu hỏi duy nhất của lớp này: <b>một gia phả thật có ghi vào được không</b> —
 * đa thê/đa phu, con nuôi, tái hôn và con riêng, dâu/rể, tuyệt tự/kế tự, đích tôn/thừa tự.
 */
class ComplexKinshipModelTest {

    private final UUID cha = UUID.randomUUID();
    private final UUID voCa = UUID.randomUUID();
    private final UUID voHai = UUID.randomUUID();
    private final UUID voBa = UUID.randomUUID();

    @Test
    @DisplayName("Đa thê: ba bà vợ được ghi bằng ba cạnh SPOUSE khác spouse_order")
    void daTheGhiBangSpouseOrder() {
        List<Relationship> honNhan = List.of(
                Relationship.spouse(UUID.randomUUID(), cha, voCa, 1, LocalDate.of(1940, 1, 1), null),
                Relationship.spouse(UUID.randomUUID(), cha, voHai, 2, LocalDate.of(1948, 1, 1), null),
                Relationship.spouse(UUID.randomUUID(), cha, voBa, 3, LocalDate.of(1955, 1, 1), null));

        assertThat(honNhan).extracting(Relationship::spouseOrder).containsExactly(1, 2, 3);
        assertThat(honNhan).allMatch(Relationship::isCurrent)
                .as("da the: ca ba cuoc hon nhan cung ton tai, khong cuoc nao bi dong lai");
    }

    @Test
    @DisplayName("Đa phu cũng dùng đúng cấu trúc đó — mô hình không giả định giới tính bên nào")
    void daPhuDungCungCauTruc() {
        UUID nguoiVo = UUID.randomUUID();
        UUID chongCa = UUID.randomUUID();
        UUID chongHai = UUID.randomUUID();

        List<Relationship> honNhan = List.of(
                Relationship.spouse(UUID.randomUUID(), nguoiVo, chongCa, 1, null, null),
                Relationship.spouse(UUID.randomUUID(), nguoiVo, chongHai, 2, null, null));

        assertThat(honNhan).extracting(Relationship::spouseOrder).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Con nuôi và con đẻ là HAI LOẠI CẠNH khác nhau, không phải một cờ trên cùng cạnh")
    void conNuoiVaConDeLaHaiLoaiCanh() {
        UUID conRuot = UUID.randomUUID();
        UUID conNuoi = UUID.randomUUID();

        Relationship ruot = Relationship.parent(UUID.randomUUID(), cha, conRuot, false);
        Relationship nuoi = Relationship.parent(UUID.randomUUID(), cha, conNuoi, true);

        assertThat(ruot.relType()).isEqualTo(RelType.PARENT_BIO);
        assertThat(nuoi.relType()).isEqualTo(RelType.PARENT_ADOPT);
        assertThat(nuoi.relType().edgeSubType()).isEqualTo("ADOPT");
        assertThat(nuoi.relType().isParentEdge())
                .as("con nuoi van dung nen cay pha do nhu con de")
                .isTrue();
    }

    @Test
    @DisplayName("Tái hôn: cuộc hôn nhân cũ đóng lại bằng validTo, cuộc mới mở ra song song")
    void taiHonDongCuocCuMoCuocMoi() {
        Relationship cuocDau = Relationship.spouse(UUID.randomUUID(), cha, voCa, 1,
                LocalDate.of(1990, 3, 1), LocalDate.of(2001, 8, 15)).endReason("ly hôn");
        Relationship cuocSau = Relationship.spouse(UUID.randomUUID(), cha, voHai, 2,
                LocalDate.of(2003, 5, 20), null);

        assertThat(cuocDau.isCurrent()).isFalse();
        assertThat(cuocDau.validTo()).isEqualTo(LocalDate.of(2001, 8, 15));
        assertThat(cuocSau.isCurrent()).isTrue();
    }

    @Test
    @DisplayName("Con riêng của vợ sau: nối vào mẹ ruột, không nối vào cha dượng bằng cạnh ruột")
    void conRiengNoiVaoMeRuotChuKhongPhaiChaDuong() {
        UUID conRieng = UUID.randomUUID();

        Relationship voiMeRuot = Relationship.parent(UUID.randomUUID(), voHai, conRieng, false);
        Relationship voiChaDuong = Relationship.parent(UUID.randomUUID(), cha, conRieng, true);

        assertThat(voiMeRuot.relType()).isEqualTo(RelType.PARENT_BIO);
        assertThat(voiChaDuong.relType())
                .as("cha duong nhan con rieng lam con nuoi thi ghi ADOPT; ghi BIO la bia huyet thong")
                .isEqualTo(RelType.PARENT_ADOPT);
    }

    @Test
    @DisplayName("Dâu và rể KHÔNG phải một loại cạnh — chúng được suy ra từ SPOUSE cộng huyết thống")
    void dauVaReKhongPhaiMotLoaiCanh() {
        assertThat(RelType.values())
                .as("tao canh rieng cho dau/re la lam hong suy luan danh xung cua context kinship")
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("PARENT_BIO", "PARENT_ADOPT", "SPOUSE", "HEIR");
    }

    @Test
    @DisplayName("Tuyệt tự và kế tự là trạng thái nối dõi; ai kế tự thì nằm ở cạnh HEIR")
    void tuyetTuVaKeTu() {
        Person chiTuyetTu = PersonFixtures.nam("Nguyễn Văn Đoạn");
        chiTuyetTu.changeLineageStatus(LineageStatus.TUYET_TU);

        UUID nguoiKeTu = UUID.randomUUID();
        Relationship canhKeTu = Relationship.heir(UUID.randomUUID(), chiTuyetTu.rawId(), nguoiKeTu,
                HeirKind.KE_TU);

        assertThat(chiTuyetTu.lineageStatus()).isEqualTo(LineageStatus.TUYET_TU);
        assertThat(canhKeTu.relType()).isEqualTo(RelType.HEIR);
        assertThat(canhKeTu.heirKind()).isEqualTo(HeirKind.KE_TU);
        assertThat(canhKeTu.fromPersonId())
                .as("from la nguoi de lai huong hoa, to la nguoi noi doi")
                .isEqualTo(chiTuyetTu.rawId());
    }

    @Test
    @DisplayName("Đích tôn và thừa tự đều ghi bằng cạnh HEIR, khác nhau ở heirKind")
    void dichTonVaThuaTu() {
        UUID dichTon = UUID.randomUUID();
        UUID thuaTu = UUID.randomUUID();

        Relationship canhDichTon = Relationship.heir(UUID.randomUUID(), cha, dichTon, HeirKind.DICH_TON);
        Relationship canhThuaTu = Relationship.heir(UUID.randomUUID(), cha, thuaTu, HeirKind.THUA_TU);

        assertThat(canhDichTon.heirKind()).isEqualTo(HeirKind.DICH_TON);
        assertThat(canhThuaTu.heirKind()).isEqualTo(HeirKind.THUA_TU);
        assertThat(HeirKind.values()).containsExactlyInAnyOrder(
                HeirKind.DICH_TON, HeirKind.THUA_TU, HeirKind.KE_TU);
    }

    @Test
    @DisplayName("Một người con có đủ hai cạnh cha và mẹ — bên ngoại được ghi ngang bên nội")
    void conCoDuCanhChaVaMe() {
        UUID con = UUID.randomUUID();

        List<Relationship> canh = new ArrayList<>();
        canh.add(Relationship.parent(UUID.randomUUID(), cha, con, false));
        canh.add(Relationship.parent(UUID.randomUUID(), voCa, con, false));

        assertThat(canh).hasSize(2);
        assertThat(canh).allSatisfy(rel -> assertThat(rel.toPersonId()).isEqualTo(con));
        assertThat(canh).extracting(Relationship::fromPersonId).containsExactly(cha, voCa);
    }

    @Test
    @DisplayName("Con gái được ghi nhận đầy đủ và bình đẳng với con trai (BA v2 §12)")
    void conGaiDuocGhiNhanBinhDangVoiConTrai() {
        Person conTrai = PersonFixtures.nam("Nguyễn Văn Cả");
        Person conGai = PersonFixtures.nu("Nguyễn Thị Cả");

        for (Person con : List.of(conTrai, conGai)) {
            con.placeInGeneration(5);
            con.placeInBirthOrder(1);
            con.changeLineageStatus(LineageStatus.NORMAL);
            con.addName(PersonName.of(NameType.HUY, "Húy " + con.displayName(), false));
        }

        assertThat(conGai.generation()).isEqualTo(conTrai.generation());
        assertThat(conGai.birthOrder()).isEqualTo(conTrai.birthOrder());
        assertThat(conGai.names()).hasSameSizeAs(conTrai.names());
        assertThat(conGai.tabooNames())
                .as("con gai cung co ten huy, cung kich hoat canh bao ky huy")
                .hasSize(1);
        assertThat(conGai.auditSnapshot().keySet()).isEqualTo(conTrai.auditSnapshot().keySet());
    }

    @Test
    @DisplayName("Con gái làm đích tôn / thừa tự vẫn ghi được — mô hình không chặn theo giới tính")
    void conGaiVanLamThuaTuDuoc() {
        Person conGai = PersonFixtures.nu("Nguyễn Thị Trưởng");

        Relationship canh = Relationship.heir(UUID.randomUUID(), cha, conGai.rawId(), HeirKind.THUA_TU);

        assertThat(canh.heirKind()).isEqualTo(HeirKind.THUA_TU);
        assertThat(conGai.gender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("Bên ngoại: một người có thể vừa là con của chi này vừa nối sang chi khác qua hôn nhân")
    void benNgoaiNoiSangChiKhacQuaHonNhan() {
        Person conGai = PersonFixtures.nu("Nguyễn Thị Lan");
        UUID chiNha = UUID.randomUUID();
        conGai.moveToBranch(chiNha);
        UUID chongNhaKhac = UUID.randomUUID();

        Relationship huyetThong = Relationship.parent(UUID.randomUUID(), cha, conGai.rawId(), false);
        Relationship honNhan = Relationship.spouse(UUID.randomUUID(), chongNhaKhac, conGai.rawId(),
                1, null, null);

        assertThat(conGai.primaryBranchId())
                .as("lay chong khong lam con gai bien mat khoi chi cua minh")
                .isEqualTo(chiNha);
        assertThat(huyetThong.toPersonId()).isEqualTo(conGai.rawId());
        assertThat(honNhan.otherEnd(conGai.rawId())).isEqualTo(chongNhaKhac);
    }
}
