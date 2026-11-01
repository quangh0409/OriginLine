package vn.giapha.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.content.api.rest.dto.CreatePostRequest;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.Post;

/**
 * Ghim quyết định 24/09/2026: <b>nháp được phép có thân rỗng, bài gửi duyệt thì không.</b>
 *
 * <p>Lỗi đo được bằng trình duyệt thật: gõ tiêu đề rồi rời ô ⇒ màn soạn tự lưu nháp
 * {@code POST /posts {"title":"…","body":""}} ⇒ {@code 400 VALIDATION_FAILED} trên {@code body}, và
 * bộ chọn ảnh — vốn chỉ mở khi đã có nháp — khoá tới khi người viết gõ xong một câu. Luật "phải có
 * thân" không bị bỏ mà <b>chuyển</b> sang {@link Post#submit()}; lớp này kiểm cả hai nửa.</p>
 *
 * <p>POJO thuần + {@code jakarta.validation} — không Spring, không Docker.</p>
 */
@DisplayName("Bài viết — nháp được thân rỗng, gửi duyệt thì phải có thân")
class PostDraftBodyTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Post nhapChiCoTieuDe() {
        return Post.draft(UUID.randomUUID(), "Họp họ cuối năm", "",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("CreatePostRequest chỉ có tiêu đề là hợp lệ — thân rỗng hay vắng mặt đều qua")
    void yeuCauTaoNhapChiCanTieuDe() {
        assertThat(VALIDATOR.validate(new CreatePostRequest("Họp họ cuối năm", ""))).isEmpty();
        assertThat(VALIDATOR.validate(new CreatePostRequest("Họp họ cuối năm", null))).isEmpty();
    }

    @Test
    @DisplayName("CreatePostRequest vẫn đòi tiêu đề — thứ duy nhất để nhận ra nháp ở 'Bài của tôi'")
    void yeuCauTaoNhapVanDoiTieuDe() {
        assertThat(VALIDATOR.validate(new CreatePostRequest("  ", "Có thân")))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("title");
    }

    @Test
    @DisplayName("Tạo nháp chỉ có tiêu đề được; thân null đọc thành chuỗi rỗng (cột NOT NULL)")
    void nhapThanRong() {
        assertThat(nhapChiCoTieuDe().body()).isEmpty();
        Post thanNull = Post.draft(UUID.randomUUID(), "Tiêu đề", null,
                UUID.randomUUID(), UUID.randomUUID(), null);
        assertThat(thanNull.body()).isEmpty();
    }

    @Test
    @DisplayName("Sửa nháp về thân rỗng được — người viết xoá hết để viết lại không bị chặn")
    void suaNhapVeThanRong() {
        Post post = Post.draft(UUID.randomUUID(), "Tiêu đề", "Có chữ",
                UUID.randomUUID(), UUID.randomUUID(), null);
        post.edit(null, "");
        assertThat(post.body()).isEmpty();
        assertThat(post.status()).isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    @DisplayName("Gửi duyệt nháp thân rỗng bị từ chối (IllegalArgumentException ⇒ 422) và bài ở lại DRAFT")
    void guiDuyetThanRongBiChan() {
        Post post = nhapChiCoTieuDe();
        assertThatThrownBy(post::submit).isInstanceOf(IllegalArgumentException.class);
        assertThat(post.status()).isEqualTo(ContentStatus.DRAFT);

        Post chiKhoangTrang = Post.draft(UUID.randomUUID(), "Tiêu đề", "   \n ",
                UUID.randomUUID(), UUID.randomUUID(), null);
        assertThatThrownBy(chiKhoangTrang::submit).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Viết thêm thân rồi gửi duyệt thì qua — DRAFT → PENDING")
    void vietThemRoiGui() {
        Post post = nhapChiCoTieuDe();
        post.edit(null, "Xin các chi báo số người về dự.");
        post.submit();
        assertThat(post.status()).isEqualTo(ContentStatus.PENDING);
    }

    @Test
    @DisplayName("Sai trạng thái vẫn là IllegalStateException (409), không lẫn với thân rỗng (422)")
    void saiTrangThaiVanLa409() {
        Post post = Post.draft(UUID.randomUUID(), "Tiêu đề", "Có chữ",
                UUID.randomUUID(), UUID.randomUUID(), null);
        post.submit();
        assertThatThrownBy(post::submit).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Lưới thứ hai: một hàng PENDING thân rỗng (dữ liệu cũ/lối ghi khác) không lên trang chủ được")
    void duyetThanRongBiChan() {
        UUID author = UUID.randomUUID();
        Post pendingRong = new Post(UUID.randomUUID(), "Tiêu đề", "", ContentStatus.PENDING,
                UUID.randomUUID(), author, null, null, null, null, null,
                Instant.now(), Instant.now(), 1L);
        assertThatThrownBy(() -> pendingRong.approve(UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(pendingRong.status()).isEqualTo(ContentStatus.PENDING);
    }

    @Test
    @DisplayName("Trần 200.000 ký tự vẫn áp cho nháp")
    void tranDoDaiVanApChoNhap() {
        String quaDai = "a".repeat(Post.MAX_BODY_LENGTH + 1);
        assertThatThrownBy(() -> Post.draft(UUID.randomUUID(), "Tiêu đề", quaDai,
                UUID.randomUUID(), UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
