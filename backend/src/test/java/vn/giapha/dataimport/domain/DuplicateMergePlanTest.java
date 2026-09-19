package vn.giapha.dataimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;

/**
 * <b>Không sinh người mồ côi.</b> Đó là câu hỏi duy nhất mà tệp test này trả lời, ở mọi hình dạng
 * mà phép gộp có thể lấy.
 *
 * <p>Bỏ một dòng vì nó trùng với dòng khác là thao tác dễ làm hỏng phả nhất trong cả đường ống, và
 * nó hỏng <b>trong im lặng</b>: không ràng buộc nào của CSDL biết rằng đáng lẽ phải có một cạnh
 * cha–con ở chỗ vừa bị cắt. Vì vậy mỗi ca dưới đây đều khẳng định <b>tập cạnh sau khi gộp</b>, chứ
 * không chỉ khẳng định "dòng kia đã biến mất".</p>
 */
@DisplayName("Kế hoạch gộp: trỏ lại trọn vẹn, không bỏ sót ai")
class DuplicateMergePlanTest {

    private static final UUID LO = UUID.randomUUID();

    // =====================================================================================
    // 1. Gộp hai dòng trong cùng tệp
    // =====================================================================================

    @Nested
    @DisplayName("Gộp hai dòng trong cùng tệp")
    class TrongCungTep {

        @Test
        @DisplayName("Dòng bị bỏ biến mất, và MỌI mã cha/mẹ/kế tự/hôn phối trỏ tới nó được trỏ lại")
        void moiThamChieuDuocTroLai() {
            List<PersonRow> rows = List.of(
                    nguoi(2, "AT-01-001", "Nguyễn Văn Cẩn", null, null),
                    // Cung mot nguoi, chep hai lan: trang doi cha ghi AT-02-001, trang doi con ghi
                    // AT-02-900. Chuyen rat thuong khi mot nguoi dan ong xuat hien o ca hai trang.
                    nguoi(3, "AT-02-001", "Nguyễn Văn Hai", "AT-01-001", null),
                    nguoi(4, "AT-02-900", "Nguyễn Văn Hai", null, null),
                    // Ba con, va CHUNG TRO VAO DONG SE BI BO.
                    nguoi(5, "AT-03-001", "Con Cả", "AT-02-900", "AT-02-101"),
                    nguoi(6, "AT-03-002", "Con Thứ", "AT-02-900", "AT-02-101"),
                    nguoi(7, "AT-02-101", "Lê Thị Dâu", null, null)
                            .withThamChieu(null, null, "AT-02-900"));
            List<MarriageRow> honPhoi = List.of(honPhoi(2, "AT-02-900", "AT-02-101", 1));

            DuplicateMergePlan plan = DuplicateMergePlan.cua(
                    List.of(gop("AT-02-900", DuplicatePair.Kind.FILE, null, "AT-02-001")), rows);
            DuplicateMergePlan.SauGop sau = plan.apDung(rows, honPhoi);

            // --- Dong bi bo that su bien mat ---
            assertThat(ma(sau.personRows()))
                    .containsExactly("AT-01-001", "AT-02-001", "AT-03-001", "AT-03-002", "AT-02-101")
                    .doesNotContain("AT-02-900");

            // --- KHONG MOT NGUOI MO COI NAO: hai dua con nay tro sang dong o lai ---
            assertThat(theoMa(sau).get("AT-03-001").fatherCode()).isEqualTo("AT-02-001");
            assertThat(theoMa(sau).get("AT-03-002").fatherCode()).isEqualTo("AT-02-001");
            // Ma ke tu cung phai tro lai: quan he ke tu la thu dong ho coi trong bac nhat.
            assertThat(theoMa(sau).get("AT-02-101").heirOfCode()).isEqualTo("AT-02-001");
            // Hon phoi cung vay, neu khong thi mot cap vo chong bien mat khoi pha ma khong ai biet.
            assertThat(sau.marriageRows()).hasSize(1);
            assertThat(sau.marriageRows().get(0).husbandCode()).isEqualTo("AT-02-001");

            // --- Va khong con mot ma chet nao trong ca tep ---
            assertThat(maChet(sau)).isEmpty();
        }

        @Test
        @DisplayName("Dòng ở lại HÚT mã cha của dòng bị bỏ — nếu không, chính nó thành mồ côi")
        void dongOLaiHutMaCha() {
            // Dong o lai (so dong nho hon) KHONG co ma cha; dong bi bo thi co. Khong hut sang thi
            // chinh nguoi vua duoc gop bi cat khoi cha minh.
            List<PersonRow> rows = List.of(
                    nguoi(2, "AT-01-001", "Nguyễn Văn Cẩn", null, null),
                    nguoi(3, "AT-02-001", "Nguyễn Văn Hai", null, null),
                    nguoi(4, "AT-02-900", "Nguyễn Văn Hai", "AT-01-001", null));

            DuplicateMergePlan.SauGop sau = DuplicateMergePlan
                    .cua(List.of(gop("AT-02-900", DuplicatePair.Kind.FILE, null, "AT-02-001")), rows)
                    .apDung(rows, List.of());

            assertThat(theoMa(sau).get("AT-02-001").fatherCode()).isEqualTo("AT-01-001");
            assertThat(maChet(sau)).isEmpty();
        }

        @Test
        @DisplayName("Chuỗi gộp A≡B, B≡C: cả ba về một mã, không mã nào trỏ sang một mã đã bị bỏ")
        void chuoiGopVeMotMa() {
            List<PersonRow> rows = List.of(
                    nguoi(2, "AT-02-001", "Nguyễn Văn Hai", null, null),
                    nguoi(3, "AT-02-800", "Nguyễn Văn Hai", null, null),
                    nguoi(4, "AT-02-900", "Nguyễn Văn Hai", null, null),
                    nguoi(5, "AT-03-001", "Con", "AT-02-900", null),
                    nguoi(6, "AT-03-002", "Con Nữa", "AT-02-800", null));

            DuplicateMergePlan.SauGop sau = DuplicateMergePlan.cua(List.of(
                            gop("AT-02-800", DuplicatePair.Kind.FILE, null, "AT-02-001"),
                            gop("AT-02-900", DuplicatePair.Kind.FILE, null, "AT-02-800")), rows)
                    .apDung(rows, List.of());

            assertThat(ma(sau.personRows()))
                    .containsExactly("AT-02-001", "AT-03-001", "AT-03-002");
            assertThat(theoMa(sau).get("AT-03-001").fatherCode()).isEqualTo("AT-02-001");
            assertThat(theoMa(sau).get("AT-03-002").fatherCode()).isEqualTo("AT-02-001");
            assertThat(maChet(sau)).isEmpty();
        }

        @Test
        @DisplayName("Vòng gộp A≡B, B≡C, C≡A: dừng được, không lặp vô hạn, vẫn còn đúng một dòng")
        void vongGopVanDung() {
            List<PersonRow> rows = List.of(
                    nguoi(2, "AT-02-001", "Nguyễn Văn Hai", null, null),
                    nguoi(3, "AT-02-800", "Nguyễn Văn Hai", null, null),
                    nguoi(4, "AT-02-900", "Nguyễn Văn Hai", null, null));

            DuplicateMergePlan.SauGop sau = DuplicateMergePlan.cua(List.of(
                            gop("AT-02-800", DuplicatePair.Kind.FILE, null, "AT-02-001"),
                            gop("AT-02-900", DuplicatePair.Kind.FILE, null, "AT-02-800"),
                            gop("AT-02-001", DuplicatePair.Kind.FILE, null, "AT-02-900")), rows)
                    .apDung(rows, List.of());

            assertThat(sau.personRows()).hasSize(1);
        }

        @Test
        @DisplayName("Hai dòng hôn phối hoá thành một sau khi trỏ lại → chỉ giữ một, không cạnh trùng")
        void honPhoiTrungNhauSauKhiTroLai() {
            List<PersonRow> rows = List.of(
                    nguoi(2, "AT-02-001", "Chồng", null, null),
                    nguoi(3, "AT-02-101", "Vợ", null, null),
                    nguoi(4, "AT-02-901", "Vợ", null, null));
            List<MarriageRow> honPhoi = List.of(
                    honPhoi(2, "AT-02-001", "AT-02-101", 1),
                    honPhoi(3, "AT-02-001", "AT-02-901", 2));

            DuplicateMergePlan.SauGop sau = DuplicateMergePlan
                    .cua(List.of(gop("AT-02-901", DuplicatePair.Kind.FILE, null, "AT-02-101")), rows)
                    .apDung(rows, honPhoi);

            // Hai canh SPOUSE trung nhau se dam vao ux_relationship_spouse_order va cuon lai ca lo
            // o buoc cuoi cung — sau khi da chay xong phan viec nang nhat.
            assertThat(sau.marriageRows()).hasSize(1);
            assertThat(sau.marriageRows().get(0).spouseOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("Gộp làm một dòng thành cha của chính nó → bỏ hẳn mã cha, không sinh vòng lặp")
        void tuLamChaChinhMinhThiBoMa() {
            List<PersonRow> rows = List.of(
                    nguoi(2, "AT-02-001", "Nguyễn Văn Hai", null, null),
                    nguoi(3, "AT-03-001", "Con", "AT-02-001", null));

            DuplicateMergePlan.SauGop sau = DuplicateMergePlan
                    .cua(List.of(gop("AT-03-001", DuplicatePair.Kind.FILE, null, "AT-02-001")), rows)
                    .apDung(rows, List.of());

            assertThat(sau.personRows()).hasSize(1);
            // Neu giu lai ma cha thi CommitOrder gap mot vong lap va khong bao gio xep xong.
            assertThat(sau.personRows().get(0).fatherCode()).isNull();
        }
    }

    // =====================================================================================
    // 2. Gộp với một hồ sơ đã có trong phả
    // =====================================================================================

    @Nested
    @DisplayName("Gộp với hồ sơ đã có trong phả")
    class VoiHoSoDaCo {

        @Test
        @DisplayName("CREATE đổi thành UPDATE và trỏ đúng person — đây là hệ quả bắt buộc của 'gộp'")
        void createDoiThanhUpdate() {
            UUID cuTo = UUID.randomUUID();
            List<PersonRow> rows = List.of(nguoi(2, "AT-01-001", "Nguyễn Văn Cẩn", null, null));

            DuplicateMergePlan plan = DuplicateMergePlan.cua(
                    List.of(gop("AT-01-001", DuplicatePair.Kind.TREE, cuTo, null)), rows);
            List<PersonRow> sau = plan.apLenDoiSoat(rows);

            assertThat(plan.gopVaoHoSoDaCo()).containsEntry("AT-01-001", cuTo);
            assertThat(sau.get(0).plannedAction()).isEqualTo(PlannedAction.UPDATE);
            assertThat(sau.get(0).resolvedPersonId()).isEqualTo(cuTo);
        }

        @Test
        @DisplayName("Hai dòng cùng gộp vào MỘT người: chỉ một dòng sống, dòng kia trỏ lại")
        void haiDongCungMotNguoiThiGopLuon() {
            // Neu de ca hai dong cung UPDATE mot person thi so cai sinh hai dong PERSON cho cung
            // mot nguoi, dam vao ux_commit_entry_person, va ca lo cuon lai o buoc cuoi cung.
            UUID cuTo = UUID.randomUUID();
            List<PersonRow> rows = List.of(
                    nguoi(2, "AT-01-001", "Nguyễn Văn Cẩn", null, null),
                    nguoi(3, "AT-01-900", "Nguyễn Văn Cẩn", null, null),
                    nguoi(4, "AT-02-001", "Con", "AT-01-900", null));

            DuplicateMergePlan plan = DuplicateMergePlan.cua(List.of(
                    gop("AT-01-001", DuplicatePair.Kind.TREE, cuTo, null),
                    gop("AT-01-900", DuplicatePair.Kind.TREE, cuTo, null)), rows);
            DuplicateMergePlan.SauGop sau = plan.apDung(rows, List.of());

            assertThat(ma(sau.personRows())).containsExactly("AT-01-001", "AT-02-001");
            assertThat(theoMa(sau).get("AT-02-001").fatherCode()).isEqualTo("AT-01-001");
            assertThat(theoMa(sau).get("AT-01-001").resolvedPersonId()).isEqualTo(cuTo);
            assertThat(maChet(sau)).isEmpty();
        }

        @Test
        @DisplayName("Dòng đã có chủ trong phả thì DÒNG ẤY ở lại, dù số dòng lớn hơn")
        void dongDaCoChuThiOLai() {
            UUID daCo = UUID.randomUUID();
            List<PersonRow> rows = List.of(
                    nguoi(2, "AT-02-001", "Nguyễn Văn Hai", null, null),
                    nguoi(3, "AT-02-900", "Nguyễn Văn Hai", null, null)
                            .withResolution(daCo, PlannedAction.UPDATE));

            DuplicateMergePlan.SauGop sau = DuplicateMergePlan
                    .cua(List.of(gop("AT-02-900", DuplicatePair.Kind.FILE, null, "AT-02-001")), rows)
                    .apDung(rows, List.of());

            // Bo dong da co chu di nghia la bo luon phep cap nhat mot ho so CO THAT trong pha.
            assertThat(ma(sau.personRows())).containsExactly("AT-02-900");
            assertThat(sau.personRows().get(0).resolvedPersonId()).isEqualTo(daCo);
        }

        @Test
        @DisplayName("Một nhóm dính tới HAI người khác nhau trong phả → TỪ CHỐI, không tự chọn")
        void dinhHaiNguoiThiTuChoi() {
            List<PersonRow> rows = List.of(
                    nguoi(2, "AT-02-001", "Nguyễn Văn Hai", null, null),
                    nguoi(3, "AT-02-900", "Nguyễn Văn Hai", null, null));

            DuplicateMergePlan plan = DuplicateMergePlan.cua(List.of(
                    gop("AT-02-900", DuplicatePair.Kind.FILE, null, "AT-02-001"),
                    gop("AT-02-001", DuplicatePair.Kind.TREE, UUID.randomUUID(), null),
                    gop("AT-02-900", DuplicatePair.Kind.TREE, UUID.randomUUID(), null)), rows);

            assertThat(plan.coVuongMac()).isTrue();
            assertThat(plan.vuongMac().get(0)).contains("hai");
            // TU CHOI phai la TU CHOI: khong ap dung mot nua roi di tiep.
            assertThat(plan.maThayThe()).isEmpty();
            assertThat(plan.gopVaoHoSoDaCo()).isEmpty();
        }
    }

    // =====================================================================================
    // 3. "Để riêng" và "hoãn" không sinh gì cả
    // =====================================================================================

    @Test
    @DisplayName("DISTINCT / DEFERRED / PENDING không sinh phép biến đổi nào — cả hai cùng vào phả")
    void chiMERGEDMoiSinhKeHoach() {
        List<PersonRow> rows = List.of(
                nguoi(2, "AT-02-001", "Nguyễn Văn Hai", null, null),
                nguoi(3, "AT-02-900", "Nguyễn Văn Hai", null, null));

        for (DuplicateDecision quyet : List.of(DuplicateDecision.DISTINCT,
                DuplicateDecision.DEFERRED, DuplicateDecision.PENDING)) {
            DuplicateMergePlan plan = DuplicateMergePlan.cua(List.of(
                    cap("AT-02-900", DuplicatePair.Kind.FILE, null, "AT-02-001", quyet)), rows);
            assertThat(plan.rong()).as("quyet dinh %s", quyet).isTrue();
            assertThat(plan.apDung(rows, List.of()).personRows())
                    .as("ca hai dong deu phai vao pha khi quyet la %s", quyet)
                    .hasSize(2);
        }
    }

    @Test
    @DisplayName("Cặp gộp trỏ tới một dòng đã biến mất khỏi tệp: bỏ qua, không sinh mã chết")
    void dongKiaBienMatThiBoQua() {
        List<PersonRow> rows = List.of(nguoi(2, "AT-02-001", "Nguyễn Văn Hai", null, null));

        DuplicateMergePlan plan = DuplicateMergePlan.cua(
                List.of(gop("AT-02-001", DuplicatePair.Kind.FILE, null, "AT-02-900")), rows);

        assertThat(plan.rong()).isTrue();
        assertThat(plan.apDung(rows, List.of()).personRows()).hasSize(1);
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    /** Mã nào đang được trỏ tới mà không còn dòng nào mang mã ấy — định nghĩa của "mã chết". */
    private static List<String> maChet(DuplicateMergePlan.SauGop sau) {
        List<String> soTay = new ArrayList<>();
        for (PersonRow row : sau.personRows()) {
            soTay.add(row.externalCode());
        }
        List<String> chet = new ArrayList<>();
        for (PersonRow row : sau.personRows()) {
            for (String tro : List.of(String.valueOf(row.fatherCode()),
                    String.valueOf(row.motherCode()), String.valueOf(row.heirOfCode()))) {
                if (!"null".equals(tro) && !soTay.contains(tro)) {
                    chet.add(row.externalCode() + " -> " + tro);
                }
            }
        }
        for (MarriageRow m : sau.marriageRows()) {
            if (m.husbandCode() != null && !soTay.contains(m.husbandCode())) {
                chet.add("hôn phối -> " + m.husbandCode());
            }
            if (m.wifeCode() != null && !soTay.contains(m.wifeCode())) {
                chet.add("hôn phối -> " + m.wifeCode());
            }
        }
        return chet;
    }

    private static List<String> ma(List<PersonRow> rows) {
        return rows.stream().map(PersonRow::externalCode).toList();
    }

    private static Map<String, PersonRow> theoMa(DuplicateMergePlan.SauGop sau) {
        Map<String, PersonRow> byCode = new java.util.LinkedHashMap<>();
        for (PersonRow row : sau.personRows()) {
            byCode.put(row.externalCode(), row);
        }
        return byCode;
    }

    private static DuplicatePair gop(String incoming, DuplicatePair.Kind kind, UUID personId,
                                     String otherCode) {
        return cap(incoming, kind, personId, otherCode, DuplicateDecision.MERGED);
    }

    private static DuplicatePair cap(String incoming, DuplicatePair.Kind kind, UUID personId,
                                     String otherCode, DuplicateDecision quyet) {
        return new DuplicatePair(UUID.randomUUID(), LO,
                DuplicatePair.khoa(incoming, kind, personId, otherCode), 0, incoming, kind,
                personId, otherCode, 80, "trùng ngày giỗ", quyet,
                quyet == DuplicateDecision.PENDING ? null : UUID.randomUUID(),
                quyet == DuplicateDecision.PENDING ? null : Instant.now(), null);
    }

    private static PersonRow nguoi(int rowNo, String ma, String ten, String cha, String me) {
        return new PersonRow(rowNo, ma, null, null, ten, null, null, null, Gender.MALE, null,
                cha, me, null, Boolean.FALSE, 1900, null, null, null, null, null, null, null);
    }

    private static MarriageRow honPhoi(int rowNo, String chong, String vo, int bac) {
        return new MarriageRow(rowNo, chong, vo, bac, null, null, null, null);
    }
}
