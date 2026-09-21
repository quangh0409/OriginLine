package vn.giapha.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.media.domain.MediaKind;
import vn.giapha.media.domain.MediaProblemCodes;
import vn.giapha.media.domain.MediaRejectedException;
import vn.giapha.media.domain.MediaSignature;

/**
 * Dò loại tệp bằng <b>chữ ký byte</b>.
 *
 * <h2>Vì sao lớp này chạy được mà không cần Docker, và đó là chủ ý</h2>
 * {@code MediaSignature} là POJO thuần, không Spring, không kho. Toàn bộ phần "tệp này có hợp lệ
 * không" nằm ở đó, nên nó kiểm được bằng một mảng byte dựng tay — và bộ ca dưới đây chạy trong vài
 * mili-giây, tức nó được chạy thật sự thường xuyên chứ không chỉ trong CI.
 *
 * <h2>Ca quan trọng nhất không phải ca "nhận đúng"</h2>
 * Mà là ba ca <b>từ chối kèm hướng dẫn</b>: HEIC của iPhone, GIF, và MKV. Một câu "tệp không hợp
 * lệ" trơn sẽ để bác dùng iPhone thử đi thử lại năm lần với năm tấm ảnh khác nhau, vì <i>mọi</i>
 * tấm ảnh trong máy bác đều là HEIC.
 */
@DisplayName("Chữ ký byte — nhận đúng thứ nhận được, từ chối kèm đường đi tiếp")
class MediaSignatureTest {

    @Nested
    @DisplayName("Nhận")
    class Nhan {

        @Test
        @DisplayName("JPEG nhận diện bằng FF D8 FF")
        void jpeg() {
            MediaSignature.Detected d = MediaSignature.detect(mauJpeg(), "a.jpg");
            assertThat(d.kind()).isEqualTo(MediaKind.IMAGE);
            assertThat(d.contentType()).isEqualTo(MediaSignature.IMAGE_JPEG);
        }

        @Test
        @DisplayName("PNG nhận diện bằng đủ tám byte, kể cả cặp CRLF")
        void png() {
            assertThat(MediaSignature.detect(mauPng(), "a.png").contentType())
                    .isEqualTo(MediaSignature.IMAGE_PNG);
        }

        @Test
        @DisplayName("WebP đòi CẢ 'RIFF' lẫn 'WEBP' — chỉ 'RIFF' thì một tệp .wav cũng lọt")
        void webp() {
            assertThat(MediaSignature.detect(mauWebp(), "a.webp").contentType())
                    .isEqualTo(MediaSignature.IMAGE_WEBP);

            byte[] wav = new byte[32];
            System.arraycopy("RIFF".getBytes(StandardCharsets.ISO_8859_1), 0, wav, 0, 4);
            System.arraycopy("WAVE".getBytes(StandardCharsets.ISO_8859_1), 0, wav, 8, 4);
            assertThatThrownBy(() -> MediaSignature.detect(wav, "am-thanh.webp"))
                    .isInstanceOf(MediaRejectedException.class);
        }

        @Test
        @DisplayName("MP4 nhận theo nhãn hiệu ở byte 8..11")
        void mp4() {
            MediaSignature.Detected d = MediaSignature.detect(mauMp4("isom"), "a.mp4");
            assertThat(d.kind()).isEqualTo(MediaKind.VIDEO);
            assertThat(d.contentType()).isEqualTo(MediaSignature.VIDEO_MP4);
        }

        @Test
        @DisplayName("WebM đòi DocType 'webm' trong header EBML")
        void webm() {
            assertThat(MediaSignature.detect(mauWebm(), "a.webm").contentType())
                    .isEqualTo(MediaSignature.VIDEO_WEBM);
        }
    }

    @Nested
    @DisplayName("Từ chối — và câu từ chối phải chỉ được đường đi tiếp")
    class TuChoi {

        /**
         * Ca đắt nhất nếu làm sai: ảnh mặc định của iPhone.
         *
         * <p>Nhận nó thì ảnh hiện ra ô vỡ trên máy của mọi người không dùng Safari, <b>không kèm
         * thông báo lỗi nào</b>. Từ chối nó mà không nói cách chữa thì bác dùng iPhone sẽ thử lại
         * với tấm thứ hai, thứ ba — vì mọi tấm trong máy bác đều là HEIC.</p>
         */
        @Test
        @DisplayName("HEIC của iPhone bị từ chối KÈM hướng dẫn đổi cài đặt máy ảnh")
        void heic() {
            assertThatThrownBy(() -> MediaSignature.detect(mauMp4("heic"), "IMG_0042.HEIC"))
                    .isInstanceOf(MediaRejectedException.class)
                    .hasMessageContaining("iPhone")
                    .hasMessageContaining("Tương thích")
                    .extracting(ex -> ((MediaRejectedException) ex).getCode())
                    .isEqualTo(MediaProblemCodes.MEDIA_BAD_SIGNATURE);
        }

        @Test
        @DisplayName("GIF bị từ chối và câu từ chối nêu đúng thứ được nhận")
        void gif() {
            byte[] gif = new byte[32];
            System.arraycopy("GIF89a".getBytes(StandardCharsets.ISO_8859_1), 0, gif, 0, 6);
            assertThatThrownBy(() -> MediaSignature.detect(gif, "vui.gif"))
                    .isInstanceOf(MediaRejectedException.class)
                    .hasMessageContaining("GIF")
                    .hasMessageContaining("MP4");
        }

        @Test
        @DisplayName("Matroska (.mkv) bị từ chối riêng, không lẫn vào 'không phải video'")
        void mkv() {
            byte[] mkv = new byte[64];
            mkv[0] = (byte) 0x1A;
            mkv[1] = 0x45;
            mkv[2] = (byte) 0xDF;
            mkv[3] = (byte) 0xA3;
            System.arraycopy("matroska".getBytes(StandardCharsets.ISO_8859_1), 0, mkv, 20, 8);
            assertThatThrownBy(() -> MediaSignature.detect(mkv, "phim.mkv"))
                    .isInstanceOf(MediaRejectedException.class)
                    .hasMessageContaining("Matroska");
        }

        /**
         * <b>Đuôi tệp không nói lên điều gì.</b> Đây là cả lý do lớp {@code MediaSignature} tồn
         * tại: ở đường này backend còn không hề thấy tên tệp lẫn {@code Content-Type} mà trình
         * duyệt gửi — tệp đi thẳng lên MinIO qua một URL đã ký.
         */
        @Test
        @DisplayName("Một tệp văn bản đặt tên .jpg vẫn bị từ chối")
        void vanBanDoiDuoi() {
            byte[] text = "Day la mot tep van ban, khong phai anh chup nao ca.\n"
                    .getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> MediaSignature.detect(text, "anh-cuoi.jpg"))
                    .isInstanceOf(MediaRejectedException.class)
                    .extracting(ex -> ((MediaRejectedException) ex).getCode())
                    .isEqualTo(MediaProblemCodes.MEDIA_BAD_SIGNATURE);
        }

        @Test
        @DisplayName("Tệp rỗng hoặc quá ngắn bị từ chối, không ném ngoại lệ chỉ số mảng")
        void quaNgan() {
            assertThatThrownBy(() -> MediaSignature.detect(new byte[0], "rong.jpg"))
                    .isInstanceOf(MediaRejectedException.class);
            assertThatThrownBy(() -> MediaSignature.detect(null, "khong-co.jpg"))
                    .isInstanceOf(MediaRejectedException.class);
            assertThatThrownBy(() -> MediaSignature.detect(new byte[] {(byte) 0xFF, (byte) 0xD8},
                    "cut.jpg"))
                    .isInstanceOf(MediaRejectedException.class);
        }
    }

    // =====================================================================================
    // Mẫu byte
    // =====================================================================================

    static byte[] mauJpeg() {
        byte[] b = new byte[64];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        b[3] = (byte) 0xE0;
        return b;
    }

    static byte[] mauPng() {
        byte[] b = new byte[64];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(sig, 0, b, 0, sig.length);
        return b;
    }

    static byte[] mauWebp() {
        byte[] b = new byte[64];
        System.arraycopy("RIFF".getBytes(StandardCharsets.ISO_8859_1), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.ISO_8859_1), 0, b, 8, 4);
        return b;
    }

    static byte[] mauMp4(String brand) {
        byte[] b = new byte[64];
        System.arraycopy("ftyp".getBytes(StandardCharsets.ISO_8859_1), 0, b, 4, 4);
        System.arraycopy(brand.getBytes(StandardCharsets.ISO_8859_1), 0, b, 8, 4);
        return b;
    }

    static byte[] mauWebm() {
        byte[] b = new byte[64];
        b[0] = (byte) 0x1A;
        b[1] = 0x45;
        b[2] = (byte) 0xDF;
        b[3] = (byte) 0xA3;
        System.arraycopy("webm".getBytes(StandardCharsets.ISO_8859_1), 0, b, 24, 4);
        return b;
    }
}
