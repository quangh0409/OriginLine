package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.domain.event.CorrectionPayload;

/**
 * Hợp đồng đóng của {@code change_request.payload}.
 *
 * <p>Đây là cửa kiểm <b>lúc gửi</b>. Mỗi ca đỏ ở đây tương đương một đề nghị nằm chờ Trưởng chi cả
 * tuần rồi mới lộ ra là không dùng được.</p>
 */
@DisplayName("Hợp đồng payload đề nghị đính chính")
class CorrectionPayloadTest {

    private static List<String> loi(Map<String, Object> payload) {
        return CorrectionPayload.violations("UPDATE_PERSON", payload, false);
    }

    private static Map<String, Object> ngayAm(int nam, int thang, int ngay, boolean nhuan) {
        return Map.of("death", Map.of(
                "lunar", Map.of("year", nam, "month", thang, "day", ngay, "leap", nhuan),
                "precision", "DAY"));
    }

    @Nested
    @DisplayName("Danh mục trường")
    class DanhMuc {

        @Test
        @DisplayName("Sáu trường khớp đúng correctable-fields.ts của frontend")
        void sauTruong() {
            // Quy uoc "khoa = ten truong cua UpdatePersonRequest" da duoc frontend chot truoc;
            // lech mot ten thi de nghi gui len khong ap dung duoc ma khong ai thay loi bien dich.
            assertThat(CorrectionPayload.UPDATE_PERSON_FIELDS).containsExactlyInAnyOrder(
                    "death", "birth", "nativePlace", "occupation", "currentPlaceProvince", "gender");
        }

        @Test
        @DisplayName("Khoá ngoài danh mục bị từ chối, kèm gợi ý dùng loại OTHER")
        void khoaLa() {
            assertThat(loi(Map.of("tenHuy", "Nguyễn Văn Cả")))
                    .anyMatch(msg -> msg.contains("tenHuy") && msg.contains("OTHER"));
        }

        @Test
        @DisplayName("Không nêu trường nào là không có gì để áp dụng")
        void payloadRong() {
            assertThat(loi(Map.of())).isNotEmpty();
        }

        @Test
        @DisplayName("Loại chưa có bộ áp dụng (OTHER) không bị siết")
        void loaiOtherKhongBiSiet() {
            // Payload cua OTHER la loi mo ta cho Truong chi doc, khong phai lenh ghi. Siet mot hop
            // dong chua ai hien thuc chi chan nguoi dung ma khong bao ve duoc gi.
            assertThat(CorrectionPayload.violations("OTHER", Map.of("moTa", "xin doi quan he cha con"),
                    true)).isEmpty();
            assertThat(CorrectionPayload.isApplicable("OTHER")).isFalse();
            assertThat(CorrectionPayload.isApplicable("UPDATE_PERSON")).isTrue();
        }
    }

    @Nested
    @DisplayName("Kiểu giá trị")
    class KieuGiaTri {

        @Test
        @DisplayName("null luôn hợp lệ — đó là đề nghị XOÁ trường")
        void nullLaXoaTruong() {
            // "Bo ngay mat ghi nham" la mot dinh chinh hoan toan binh thuong.
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("death", null);
            assertThat(loi(payload)).isEmpty();
        }

        @Test
        @DisplayName("Ngày phải là DateDual, không phải chuỗi '15/07'")
        void ngayPhaiLaDateDual() {
            assertThat(loi(Map.of("death", "15/07"))).isNotEmpty();
        }

        @Test
        @DisplayName("Ngày âm hợp lệ, giữ cờ tháng nhuận")
        void ngayAmHopLe() {
            assertThat(loi(ngayAm(1975, 7, 15, false))).isEmpty();
            assertThat(loi(ngayAm(1975, 7, 15, true))).isEmpty();
        }

        @Test
        @DisplayName("Tháng 13 / ngày 31 âm lịch bị chặn trước khi tới LunarDate")
        void ngayAmNgoaiKhoang() {
            assertThat(loi(ngayAm(1975, 13, 15, false))).isNotEmpty();
            assertThat(loi(ngayAm(1975, 7, 31, false))).isNotEmpty();
        }

        @Test
        @DisplayName("Ngày dương phải là ISO yyyy-MM-dd")
        void ngayDuongIso() {
            Map<String, Object> hopLe = Map.of("death", Map.of("solar", "1975-08-21"));
            Map<String, Object> sai = Map.of("death", Map.of("solar", "21/08/1975"));
            assertThat(loi(hopLe)).isEmpty();
            assertThat(loi(sai)).isNotEmpty();
        }

        @Test
        @DisplayName("Đối tượng ngày rỗng cả hai lịch là biểu mẫu điền dở, không phải 'xoá ngày'")
        void ngayRongCaHaiLich() {
            Map<String, Object> rong = new LinkedHashMap<>();
            rong.put("solar", null);
            rong.put("lunar", null);
            assertThat(loi(Map.of("death", rong))).isNotEmpty();
        }

        @Test
        @DisplayName("gender = OTHER bị chặn — ck_person_gender của V2 chỉ nhận MALE/FEMALE/UNKNOWN")
        void gioiTinhOtherBiChan() {
            // Enum Gender co OTHER nhung cot person.gender thi khong. Cho lot o day nghia la de
            // nghi di qua ca cua gui lan cua duyet roi moi chet o rang buoc CHECK - va Postgres huy
            // luon phan con lai cua transaction, nen Truong chi nhan mot loi 500 khong hieu noi.
            assertThat(loi(Map.of("gender", "OTHER"))).isNotEmpty();
            assertThat(loi(Map.of("gender", "FEMALE"))).isEmpty();
            assertThat(loi(Map.of("gender", "nu"))).isNotEmpty();
        }

        @Test
        @DisplayName("Trường chữ vượt 255 ký tự bị chặn — native_place là VARCHAR(255)")
        void chuQuaDai() {
            assertThat(loi(Map.of("nativePlace", "x".repeat(256)))).isNotEmpty();
            assertThat(loi(Map.of("nativePlace", "x".repeat(255)))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Mốc phiên bản _baseVersion")
    class MocPhienBan {

        @Test
        @DisplayName("Lúc gửi thì chưa bắt buộc — backend còn kịp tự đóng dấu")
        void lucGuiChuaBatBuoc() {
            assertThat(CorrectionPayload.violations("UPDATE_PERSON",
                    Map.of("nativePlace", "Bắc Ninh"), false)).isEmpty();
        }

        @Test
        @DisplayName("Bản đã lưu thì bắt buộc — thiếu là mở lại lỗ hổng ghi đè mù")
        void banDaLuuThiBatBuoc() {
            assertThat(CorrectionPayload.violations("UPDATE_PERSON",
                    Map.of("nativePlace", "Bắc Ninh"), true))
                    .anyMatch(msg -> msg.contains("_baseVersion"));
        }

        @Test
        @DisplayName("Phải là số nguyên không âm")
        void phaiLaSoNguyenKhongAm() {
            assertThat(loi(Map.of("nativePlace", "Bắc Ninh", "_baseVersion", "khong-phai-so")))
                    .isNotEmpty();
            assertThat(loi(Map.of("nativePlace", "Bắc Ninh", "_baseVersion", -1))).isNotEmpty();
            assertThat(loi(Map.of("nativePlace", "Bắc Ninh", "_baseVersion", 7))).isEmpty();
        }

        @Test
        @DisplayName("Đọc được cả số lẫn chuỗi số — JSON qua nhiều lớp client hay đổi kiểu")
        void docDuocCaSoLanChuoi() {
            assertThat(CorrectionPayload.baseVersion(Map.of("_baseVersion", 3))).isEqualTo(3L);
            assertThat(CorrectionPayload.baseVersion(Map.of("_baseVersion", "3"))).isEqualTo(3L);
            assertThat(CorrectionPayload.baseVersion(Map.of())).isNull();
        }

        @Test
        @DisplayName("Khoá điều khiển lạ bị từ chối")
        void khoaDieuKhienLa() {
            assertThat(loi(Map.of("nativePlace", "Bắc Ninh", "_force", true))).isNotEmpty();
        }

        @Test
        @DisplayName("fieldKeys bỏ khoá điều khiển, giữ nguyên tên trường hồ sơ")
        void fieldKeysBoKhoaDieuKhien() {
            // Giao dien dung bang so sanh "dang ghi / de nghi" tu danh sach nay; mot moc phien ban
            // lot vao do se hien len nhu mot truong ho so khong ai hieu la gi.
            assertThat(CorrectionPayload.fieldKeys(
                    Map.of("death", Map.of("solar", "1975-08-21"), "_baseVersion", 4)))
                    .containsExactly("death");
        }

        @Test
        @DisplayName("withBaseVersion giữ được payload mang giá trị null")
        void withBaseVersionGiuGiaTriNull() {
            // Map.copyOf nem NPE khi map chua null, ma "xoa truong nay" chinh la mot gia tri null.
            Map<String, Object> goc = new LinkedHashMap<>();
            goc.put("death", null);
            Map<String, Object> daDongDau = CorrectionPayload.withBaseVersion(goc, 5);

            assertThat(daDongDau).containsEntry("death", null);
            assertThat(CorrectionPayload.baseVersion(daDongDau)).isEqualTo(5L);
        }
    }
}
