package vn.giapha.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Lưới an toàn cuối cùng trước khi một ảnh chụp đi vào {@code audit_log}.
 *
 * <p>{@code audit_log} là bảng <b>chỉ ghi thêm</b>, có trigger chặn UPDATE. Một số điện thoại lọt
 * vào đó là lọt vĩnh viễn — chỉ còn cách xoá dòng, mà xoá dòng nhật ký thì còn tệ hơn. Vì vậy luật
 * ở đây là: <b>giữ tên trường, bỏ giá trị</b>.</p>
 */
class SensitiveFieldRedactorTest {

    @Nested
    @DisplayName("Trường Tầng 3 bị che")
    class Tang3BiChe {

        @Test
        @DisplayName("Số điện thoại, email, địa chỉ đầy đủ, ngày sinh đầy đủ, ảnh")
        void cheGiaTriTang3() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("phone", "0912345678");
            snapshot.put("email", "nguyenvana@example.test");
            snapshot.put("fullAddress", "Số 5, ngõ 12, phường X, Hà Nội");
            snapshot.put("birthDate", "1978-03-14");
            snapshot.put("avatarKey", "portraits/abc.jpg");
            snapshot.put("zaloId", "zalo-123");

            Map<String, Object> sach = SensitiveFieldRedactor.scrub(snapshot);

            assertThat(sach).allSatisfy((key, value) ->
                    assertThat(value).isEqualTo(SensitiveFieldRedactor.REDACTED));
            // Ten truong VAN CON: kiem toan vien biet truong nao da doi ma khong doc duoc noi dung.
            assertThat(sach).containsOnlyKeys("phone", "email", "fullAddress", "birthDate",
                    "avatarKey", "zaloId");
        }

        @Test
        @DisplayName("Bắt cả biến thể ghép tên: contactPhone, spousePhotoKey, avatar_url_thumb")
        void batBienTheGhep() {
            Map<String, Object> sach = SensitiveFieldRedactor.scrub(Map.of(
                    "contactPhone", "0912345678",
                    "spousePhotoKey", "media/xyz.jpg",
                    "avatar_url_thumb", "https://minio/x.jpg"));

            assertThat(sach.values()).containsOnly(SensitiveFieldRedactor.REDACTED);
        }

        @Test
        @DisplayName("Chuẩn hoá tên khoá: hoa/thường, gạch dưới, gạch ngang, dấu chấm")
        void chuanHoaTenKhoa() {
            Map<String, Object> sach = SensitiveFieldRedactor.scrub(Map.of(
                    "PHONE_NUMBER", "09",
                    "e-mail", "a@b.c",
                    "national.id", "0010012345"));

            assertThat(sach.values()).containsOnly(SensitiveFieldRedactor.REDACTED);
        }

        @Test
        @DisplayName("Bí mật kỹ thuật cũng bị che: mật khẩu, token")
        void cheBiMatKyThuat() {
            Map<String, Object> sach = SensitiveFieldRedactor.scrub(Map.of(
                    "password", "khong-nen-o-day",
                    "refreshToken", "eyJ..."));

            assertThat(sach.values()).containsOnly(SensitiveFieldRedactor.REDACTED);
        }
    }

    @Nested
    @DisplayName("Trường KHÔNG bị che")
    class KhongBiChe {

        @Test
        @DisplayName("Năm sinh là Tầng 2 — giữ nguyên, chỉ ngày sinh đầy đủ mới là Tầng 3")
        void namSinhLaTang2() {
            // BA v2 §10: T2 gom nam sinh, nghe nghiep, tinh/thanh.
            Map<String, Object> sach = SensitiveFieldRedactor.scrub(Map.of(
                    "birthYear", 1978,
                    "occupation", "Giáo viên",
                    "province", "Hà Nội"));

            assertThat(sach)
                    .containsEntry("birthYear", 1978)
                    .containsEntry("occupation", "Giáo viên")
                    .containsEntry("province", "Hà Nội");
        }

        @Test
        @DisplayName("Dữ liệu phả hệ lõi giữ nguyên: đời thứ, giới tính, chi, trạng thái sống/mất")
        void duLieuPhaHeGiuNguyen() {
            Map<String, Object> sach = SensitiveFieldRedactor.scrub(Map.of(
                    "generation", 7,
                    "gender", "MALE",
                    "primaryBranchPath", "goc.chi_giap",
                    "isAlive", false,
                    "deathLunar", "15/07"));

            assertThat(sach)
                    .containsEntry("generation", 7)
                    .containsEntry("gender", "MALE")
                    .containsEntry("primaryBranchPath", "goc.chi_giap")
                    .containsEntry("isAlive", false)
                    .containsEntry("deathLunar", "15/07");
        }
    }

    @Nested
    @DisplayName("Cấu trúc lồng nhau")
    class CauTrucLong {

        @Test
        @DisplayName("Map lồng trong map cũng bị soi")
        void mapLong() {
            Map<String, Object> sach = SensitiveFieldRedactor.scrub(Map.of(
                    "spouse", Map.of("displayName", "Trần Thị B", "phone", "0912345678")));

            @SuppressWarnings("unchecked")
            Map<String, Object> vo = (Map<String, Object>) sach.get("spouse");
            assertThat(vo).containsEntry("displayName", "Trần Thị B");
            assertThat(vo).containsEntry("phone", SensitiveFieldRedactor.REDACTED);
        }

        @Test
        @DisplayName("Danh sách map cũng bị soi")
        void danhSachMap() {
            Map<String, Object> sach = SensitiveFieldRedactor.scrub(Map.of(
                    "children", List.of(
                            Map.of("name", "A", "email", "a@example.test"),
                            Map.of("name", "B", "birthYear", 2001))));

            assertThat(String.valueOf(sach.get("children"))).doesNotContain("a@example.test");
            assertThat(String.valueOf(sach.get("children"))).contains("2001");
        }

        @Test
        @DisplayName("Map tự tham chiếu không làm treo luồng ghi audit")
        void mapTuThamChieu() {
            Map<String, Object> vong = new LinkedHashMap<>();
            vong.put("name", "vòng");
            vong.put("self", vong);

            Map<String, Object> sach = SensitiveFieldRedactor.scrub(vong);

            assertThat(sach).containsEntry("name", "vòng");
            assertThat(sach).containsKey("self");
        }
    }

    @Test
    @DisplayName("null vào thì null ra — before của CREATE, after của xoá")
    void nullVaoNullRa() {
        assertThat(SensitiveFieldRedactor.scrub(null)).isNull();
        assertThat(SensitiveFieldRedactor.scrub(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("Giá trị null trong ảnh chụp được giữ nguyên, không thành ***")
    void giaTriNullGiuNguyen() {
        Map<String, Object> co = new LinkedHashMap<>();
        co.put("occupation", null);

        assertThat(SensitiveFieldRedactor.scrub(co)).containsEntry("occupation", null);
    }
}
