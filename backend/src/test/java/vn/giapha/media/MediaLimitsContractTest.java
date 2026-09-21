package vn.giapha.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.media.domain.MediaLimits;
import vn.giapha.media.domain.MediaSignature;

/**
 * Ghim rằng <b>các trần trong mã</b> và <b>hợp đồng mà frontend đọc</b> không trôi khỏi nhau.
 *
 * <h2>Đây là bản của {@code media} cho đúng khuôn mà {@code ImportMultipartLimitTest} đã lập</h2>
 * Bài học ở đó: hai con số nằm ở hai tệp khác loại (YAML và Java) thì <b>không có cách nào để
 * trình biên dịch bắt chúng lệch nhau</b>, và triệu chứng là một thông báo <i>sai</i> trông như
 * đúng — người dùng đi xoá ảnh trong một tệp không có ảnh nào.
 *
 * <p>Ở đợt này cái giá còn cao hơn, vì con số đi qua <b>ba</b> chỗ:</p>
 * <ol>
 *   <li>{@link MediaLimits} — nguồn chân lý;</li>
 *   <li>trường {@code maxBytes} / {@code maxDurationSeconds} mà phiếu tải lên trả xuống — giao
 *       diện dùng nó để chặn <i>trước khi</i> tốn băng thông;</li>
 *   <li>{@code contracts/openapi.yaml} — thứ người viết frontend thật sự đọc khi dựng màn hình.
 *       Lệch ở đây là tệ nhất: màn hình nhận một video 150 MiB, người dùng chờ hết 150 MiB tải
 *       lên, rồi bước xác nhận mới từ chối.</li>
 * </ol>
 *
 * <p>Ca cuối canh {@code acceptedContentTypes}: nó là thứ giao diện đặt vào thuộc tính
 * {@code accept} của ô chọn tệp, nên một kiểu MIME thừa ở đó nghĩa là hộp thoại chọn tệp mời người
 * dùng chọn một định dạng mà chữ ký byte sẽ từ chối ngay sau đó.</p>
 */
@DisplayName("Trần media phải khớp giữa mã và hợp đồng OpenAPI")
class MediaLimitsContractTest {

    /**
     * Đọc thẳng tệp hợp đồng, không đọc một bean đã bind.
     *
     * <p>Cùng lý do mà {@code ImportMultipartLimitTest} đọc thẳng {@code application.yml}: bind
     * được nghĩa là ứng dụng đã khởi động xong, mà cái cần canh là <b>nội dung của tệp hợp
     * đồng</b>.</p>
     */
    private static String contract() throws IOException {
        for (Path candidate : List.of(Path.of("../contracts/openapi.yaml"),
                Path.of("contracts/openapi.yaml"))) {
            if (Files.exists(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        // Chay tu mot thu muc lam viec khac (IDE, CI): khong co tep thi bao that, khong xanh gia.
        throw new AssertionError("Khong tim thay contracts/openapi.yaml de doi chieu tran media");
    }

    @Test
    @DisplayName("Trần ảnh 8 MiB và trần video 100 MiB xuất hiện đúng trong hợp đồng")
    void tranDungLuongKhopHopDong() throws IOException {
        assertThat(MediaLimits.MAX_IMAGE_BYTES / (1024 * 1024)).isEqualTo(8);
        assertThat(MediaLimits.MAX_VIDEO_BYTES / (1024 * 1024)).isEqualTo(100);

        String yaml = contract();
        assertThat(yaml)
                .as("openapi.yaml phai noi dung tran anh; lech mot ben la mot man hinh nhan tep"
                        + " roi tu choi sau khi da tai xong")
                .contains("**8 MiB** với ảnh");
        assertThat(yaml).contains("**100 MiB** với video");
    }

    @Test
    @DisplayName("Trần thời lượng 120 giây xuất hiện đúng trong hợp đồng")
    void tranThoiLuongKhopHopDong() throws IOException {
        assertThat(MediaLimits.MAX_VIDEO_SECONDS).isEqualTo(120);
        assertThat(contract()).contains("**120 giây** với video");
    }

    @Test
    @DisplayName("Trần số tệp trên một bài khớp maxItems của cả PostDto lẫn AttachMediaRequest")
    void tranSoTepKhopHopDong() throws IOException {
        assertThat(MediaLimits.MAX_MEDIA_PER_POST).isEqualTo(12);
        String yaml = contract();
        // Hai cho: mang `media` cua PostDto va `mediaIds` cua AttachMediaRequest. Lech mot trong
        // hai thi hoac giao dien cho gan 20 tep roi an 422, hoac no chan o 12 ma backend cho 20.
        assertThat(yaml.split("maxItems: " + MediaLimits.MAX_MEDIA_PER_POST, -1).length - 1)
                .as("ca PostDto.media lan AttachMediaRequest.mediaIds phai cung maxItems")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Độ dài chữ thay ảnh khớp cả hợp đồng lẫn ràng buộc cột alt_text của V19")
    void tranChuThayAnhKhopHopDongVaLuocDo() throws IOException {
        assertThat(MediaLimits.MAX_ALT_LENGTH).isEqualTo(300);
        assertThat(contract()).contains("maxLength: 300");

        String v19 = migration();
        assertThat(v19.replaceAll("\s+", " "))
                .as("cot alt_text phai du rong cho MediaLimits.MAX_ALT_LENGTH ky tu; lech thi mot"
                        + " chu thay anh hop le bi CSDL cat cut hoac tu choi")
                .contains("alt_text VARCHAR(" + MediaLimits.MAX_ALT_LENGTH + ")");
    }

    @Test
    @DisplayName("Danh sách kiểu MIME trả xuống khớp đúng những gì chữ ký byte nhận")
    void kieuMimeKhopChuKyByte() {
        // `acceptedContentTypes` la thu giao dien dat vao thuoc tinh `accept` cua o chon tep. Mot
        // kieu thua o do = hop thoai chon tep moi nguoi dung chon mot dinh dang se bi tu choi ngay
        // sau do; mot kieu thieu = mot dinh dang hop le khong chon duoc bang hop thoai.
        assertThat(List.of(MediaSignature.IMAGE_JPEG, MediaSignature.IMAGE_PNG,
                        MediaSignature.IMAGE_WEBP))
                .containsExactly("image/jpeg", "image/png", "image/webp");
        assertThat(List.of(MediaSignature.VIDEO_MP4, MediaSignature.VIDEO_WEBM))
                .containsExactly("video/mp4", "video/webm");
    }

    @Test
    @DisplayName("Ân hạn dọn tệp mồ côi rộng hơn hạn phiếu — nếu không, một lần tải chậm mất tệp")
    void anHanRongHonHanPhieu() {
        // Cuoc dua co that: nguoi dung tai xong o phut thu 14, yeu cau xac nhan dang tren duong
        // thi cong viec don chay va xoa mat doi tuong.
        assertThat(MediaLimits.PENDING_GRACE).isGreaterThan(MediaLimits.UPLOAD_TICKET_TTL);
        assertThat(MediaLimits.ORPHAN_GRACE).isGreaterThan(MediaLimits.PENDING_GRACE);
        assertThat(MediaLimits.VIEW_URL_TTL.toMinutes()).isEqualTo(10);
    }

    private static String migration() throws IOException {
        try (InputStream in = MediaLimitsContractTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V19__media_asset_link_report.sql")) {
            assertThat(in).as("V19 phai nam tren classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
