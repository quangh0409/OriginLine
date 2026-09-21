package vn.giapha.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.media.domain.MediaProblemCodes;
import vn.giapha.media.domain.MediaRejectedException;
import vn.giapha.media.domain.MediaSignature;
import vn.giapha.media.domain.VideoHeaderProbe;

/**
 * Đọc thời lượng video từ header container — <b>không ffmpeg</b>.
 *
 * <h2>Lớp này canh một bất biến, không canh một hàm</h2>
 * Bất biến là: <b>một trần thời lượng chỉ có nghĩa nếu thời lượng đo được</b>. Quyết định của chủ
 * dự án là nhận tệp gốc không chuyển mã, nhưng vẫn phải có trần thời lượng — và hai lối sai đã bị
 * loại trước khi viết một dòng nào: tin con số client khai (bằng không có trần) và gọi ffmpeg (chủ
 * dự án đã nói không, và nó mở một bề mặt tấn công khác). Lối còn lại là đọc siêu dữ liệu mà chính
 * container đã ghi sẵn — và ca cuối cùng ở đây, "đọc không ra thì <i>từ chối</i>", là thứ giữ cho
 * lối ấy không lặng lẽ thành "không có trần".
 */
@DisplayName("Thời lượng video đọc từ header — và từ chối khi đọc không ra")
class VideoHeaderProbeTest {

    @Test
    @DisplayName("MP4 với moov ở ĐẦU tệp: đọc timescale/duration từ mvhd")
    void mp4MoovODau() {
        byte[] head = mp4WithMvhd(1_000, 5_000);              // 5.000 don vi / 1.000 mot giay
        assertThat(VideoHeaderProbe.durationMillis(MediaSignature.VIDEO_MP4, head, null))
                .isEqualTo(5_000L);
    }

    @Test
    @DisplayName("Timescale 600 (mặc định của QuickTime) được tôn trọng, không giả định 1000")
    void mp4Timescale600() {
        // 72.000 / 600 = 120 giay — dung bang tran, nen phep chia phai dung tung don vi.
        assertThat(VideoHeaderProbe.durationMillis(
                MediaSignature.VIDEO_MP4, mp4WithMvhd(600, 72_000), null))
                .isEqualTo(120_000L);
    }

    /**
     * Ca này là lý do {@code VideoHeaderProbe} nhận <b>hai</b> bộ đệm.
     *
     * <p>Hộp {@code moov} của một MP4 chưa "faststart" nằm ở <b>cuối</b> tệp — đó là mặc định của
     * nhiều công cụ dựng phim. Chỉ quét phần đầu thì mọi tệp như thế sẽ rơi vào nhánh "không đọc
     * được thời lượng" và bị từ chối oan, mà người dùng thì không có cách nào biết tệp của mình
     * khác tệp của người bên cạnh ở chỗ nào.</p>
     */
    @Test
    @DisplayName("MP4 với moov ở CUỐI tệp (chưa faststart): quét phần đuôi mới ra")
    void mp4MoovOCuoi() {
        byte[] head = new byte[1024];                          // mdat, khong co mvhd
        System.arraycopy("ftyp".getBytes(StandardCharsets.ISO_8859_1), 0, head, 4, 4);
        byte[] tail = mp4WithMvhd(1_000, 90_000);

        // Chi quet phan DAU: khong thay mvhd nen TU CHOI — day chinh la ca bi tu choi oan neu
        // VideoHeaderProbe khong nhan bo dem thu hai.
        assertThatThrownBy(() -> VideoHeaderProbe.durationMillis(
                MediaSignature.VIDEO_MP4, head, null))
                .isInstanceOf(MediaRejectedException.class)
                .extracting(ex -> ((MediaRejectedException) ex).getCode())
                .isEqualTo(MediaProblemCodes.MEDIA_DURATION_UNKNOWN);

        // Them phan DUOI thi doc ra.
        assertThat(VideoHeaderProbe.durationMillis(MediaSignature.VIDEO_MP4, head, tail))
                .isEqualTo(90_000L);
    }

    @Test
    @DisplayName("MP4 phiên bản 1 của mvhd (64-bit) đọc đúng")
    void mp4MvhdV1() {
        assertThat(VideoHeaderProbe.durationMillis(
                MediaSignature.VIDEO_MP4, mp4WithMvhdV1(1_000, 30_000), null))
                .isEqualTo(30_000L);
    }

    @Test
    @DisplayName("WebM: Duration (float) nhân TimecodeScale, không giả định 1 ms")
    void webmDuration() {
        // TimecodeScale 1.000.000 ns = 1 ms; Duration 4500.0 => 4500 ms.
        assertThat(VideoHeaderProbe.durationMillis(
                MediaSignature.VIDEO_WEBM, webm(1_000_000, 4_500f), null))
                .isEqualTo(4_500L);
        // TimecodeScale 100.000 ns = 0,1 ms; Duration 45000.0 => 4500 ms. Bo qua TimecodeScale se
        // ra 45.000 ms va mot clip 4,5 giay bi tu choi vi "dai qua".
        assertThat(VideoHeaderProbe.durationMillis(
                MediaSignature.VIDEO_WEBM, webm(100_000, 45_000f), null))
                .isEqualTo(4_500L);
    }

    /**
     * <b>Đóng khi nghi ngờ.</b> Không "cho qua vì chắc là ngắn": một trần im lặng bỏ qua tệp nó
     * không hiểu là một trần chỉ chặn được người trung thực.
     */
    @Test
    @DisplayName("Không đọc được thời lượng → TỪ CHỐI với mã riêng, không im lặng cho qua")
    void khongDocDuocThiTuChoi() {
        byte[] rac = new byte[2048];
        System.arraycopy("ftyp".getBytes(StandardCharsets.ISO_8859_1), 0, rac, 4, 4);

        assertThatThrownBy(() -> VideoHeaderProbe.durationMillis(
                MediaSignature.VIDEO_MP4, rac, null))
                .isInstanceOf(MediaRejectedException.class)
                .extracting(ex -> ((MediaRejectedException) ex).getCode())
                .isEqualTo(MediaProblemCodes.MEDIA_DURATION_UNKNOWN);
    }

    /**
     * Mã riêng cho "không đọc được", tách khỏi "sai định dạng" — vì hai mã dẫn người dùng đi hai
     * hướng khác nhau. Gộp chúng thì giao diện sẽ bảo "tệp không phải video" trong khi nó đúng là
     * video, và người dùng đi tìm nhầm chỗ.
     */
    @Test
    @DisplayName("Câu từ chối nói ra CON SỐ trần, không nói 'quá dài' trống rỗng")
    void cauTuChoiNoiRaConSo() {
        assertThatThrownBy(() -> VideoHeaderProbe.durationMillis(
                MediaSignature.VIDEO_MP4, new byte[512], null))
                .hasMessageContaining("120")
                .hasMessageContaining("MP4");
    }

    // =====================================================================================
    // Dựng header bằng tay
    // =====================================================================================

    /** Hộp {@code mvhd} phiên bản 0 nhúng giữa một ít byte đệm, đúng như trong tệp thật. */
    static byte[] mp4WithMvhd(int timescale, int duration) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] prefix = new byte[64];
        System.arraycopy("ftyp".getBytes(StandardCharsets.ISO_8859_1), 0, prefix, 4, 4);
        System.arraycopy("isom".getBytes(StandardCharsets.ISO_8859_1), 0, prefix, 8, 4);
        out.writeBytes(prefix);
        out.writeBytes("mvhd".getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(new byte[] {0, 0, 0, 0});               // version 0 + flags
        out.writeBytes(int32(0));                              // creation
        out.writeBytes(int32(0));                              // modification
        out.writeBytes(int32(timescale));
        out.writeBytes(int32(duration));
        out.writeBytes(new byte[128]);
        return out.toByteArray();
    }

    static byte[] mp4WithMvhdV1(int timescale, long duration) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] prefix = new byte[64];
        System.arraycopy("ftyp".getBytes(StandardCharsets.ISO_8859_1), 0, prefix, 4, 4);
        System.arraycopy("iso2".getBytes(StandardCharsets.ISO_8859_1), 0, prefix, 8, 4);
        out.writeBytes(prefix);
        out.writeBytes("mvhd".getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(new byte[] {1, 0, 0, 0});               // version 1 + flags
        out.writeBytes(new byte[16]);                          // creation(8) + modification(8)
        out.writeBytes(int32(timescale));
        out.writeBytes(ByteBuffer.allocate(8).putLong(duration).array());
        out.writeBytes(new byte[128]);
        return out.toByteArray();
    }

    static byte[] webm(long timecodeScale, float duration) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
        out.writeBytes(new byte[16]);
        out.writeBytes("webm".getBytes(StandardCharsets.ISO_8859_1));
        // TimecodeScale: id 2A D7 B1, vint size 0x84 (4 byte), gia tri big-endian
        out.writeBytes(new byte[] {0x2A, (byte) 0xD7, (byte) 0xB1, (byte) 0x84});
        out.writeBytes(int32((int) timecodeScale));
        // Duration: id 44 89, vint size 0x84 (4 byte), float32
        out.writeBytes(new byte[] {0x44, (byte) 0x89, (byte) 0x84});
        out.writeBytes(ByteBuffer.allocate(4).putFloat(duration).array());
        out.writeBytes(new byte[64]);
        return out.toByteArray();
    }

    private static byte[] int32(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }
}
