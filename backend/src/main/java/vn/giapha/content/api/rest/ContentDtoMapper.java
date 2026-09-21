package vn.giapha.content.api.rest;

import java.util.List;
import vn.giapha.content.api.rest.dto.ContentPageDto;
import vn.giapha.content.api.rest.dto.HonourDto;
import vn.giapha.content.api.rest.dto.PostDto;
import vn.giapha.content.api.rest.dto.PostMediaDto;
import vn.giapha.content.application.ContentPage;
import vn.giapha.content.application.HonourView;
import vn.giapha.content.application.PostView;

/**
 * View của tầng application → DTO của hợp đồng REST.
 *
 * <p><b>Không một phép che nào ở đây.</b> Mọi trường đọc từ view đã đi qua bộ lọc riêng tư của
 * {@code genealogy} và luật "ai thấy gì" trong SQL; một câu {@code if} về quyền ở tầng ánh xạ là
 * bản luật thứ hai đội lốt một mapper — và nó sẽ lệch, vì không ai đi tìm luật bảo mật trong một
 * lớp tên là {@code DtoMapper}.</p>
 */
public final class ContentDtoMapper {

    private ContentDtoMapper() {
    }

    public static PostDto toDto(PostView view) {
        return new PostDto(view.id(), view.title(), view.body(),
                view.status(), view.authorPersonId(), view.authorDisplayName(), view.branchId(),
                view.branchName(), view.publishedAt(), view.reviewedBy(),
                view.reviewedByDisplayName(), view.reviewedAt(), view.rejectReason(),
                view.createdAt(), view.updatedAt(), view.version(),
                view.canReview(), view.canEdit(),
                view.media().stream().map(ContentDtoMapper::toMediaDto).toList());
    }

    /**
     * {@code media.MediaAssetView} → DTO của {@code content}.
     *
     * <p>Một phép chép trường-sang-trường, cố ý. Đưa thẳng kiểu của {@code media} vào
     * {@code PostDto} sẽ làm hợp đồng công khai của bài viết thay đổi mỗi khi {@code media} đổi
     * hình dạng nội bộ — xem javadoc {@link PostMediaDto}.</p>
     */
    private static PostMediaDto toMediaDto(vn.giapha.media.application.view.MediaAssetView m) {
        return new PostMediaDto(m.id(), m.kind(), m.contentType(), m.sizeBytes(), m.durationMs(),
                m.alt(), m.position(), m.url());
    }

    public static HonourDto toDto(HonourView view) {
        return new HonourDto(view.id(), view.personId(), view.personDisplayName(), view.kind(),
                view.title(), view.year(), view.issuer(), view.description(), view.status(),
                view.branchId(), view.branchName(), view.reviewedBy(),
                view.reviewedByDisplayName(), view.reviewedAt(), view.rejectReason(),
                view.createdAt(), view.updatedAt(), view.version(), view.canReview());
    }

    public static ContentPageDto<PostDto> toPostPage(ContentPage<PostView> page) {
        List<PostDto> items = page.items().stream().map(ContentDtoMapper::toDto).toList();
        return ContentPageDto.of(page, items);
    }

    public static ContentPageDto<HonourDto> toHonourPage(ContentPage<HonourView> page) {
        List<HonourDto> items = page.items().stream().map(ContentDtoMapper::toDto).toList();
        return ContentPageDto.of(page, items);
    }
}
