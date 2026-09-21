package vn.giapha.media.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.media.application.view.MediaAssetView;
import vn.giapha.media.domain.MediaAsset;
import vn.giapha.media.domain.MediaConflictException;
import vn.giapha.media.domain.MediaLimits;
import vn.giapha.media.domain.MediaLink;
import vn.giapha.media.domain.MediaOwnerType;
import vn.giapha.media.domain.MediaProblemCodes;
import vn.giapha.media.domain.port.MediaAssetRepository;
import vn.giapha.media.domain.port.MediaLinkRepository;
import vn.giapha.media.domain.port.ObjectStoragePort;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.exception.NotFoundException;

/**
 * <b>Mặt tiền công khai của context {@code media}</b> — {@code @NamedInterface("gan-tep")}.
 *
 * <h2>Chỉ MỘT lớp và MỘT kiểu trả về được mở ra ngoài</h2>
 * Đúng khuôn mà {@code genealogy} đã lập với ba mặt tiền {@code "do-trung"} / {@code "ghi-pha"} /
 * {@code "loc-rieng-tu"}: gói {@code media.application} <b>không</b> mang nhãn, nên
 * {@code MediaUploadService}, {@code MediaViewService}, {@code MediaReportService},
 * {@code MediaGcService} và ba repository ở lại bên trong. Ra ngoài đúng <b>hai</b> kiểu: lớp này
 * và {@link MediaAssetView}.
 *
 * <h2>Chữ ký nói bằng khái niệm của BÊN GỌI, không bằng enum của domain</h2>
 * Không có phương thức {@code attach(MediaOwnerType, UUID, …)}. Có {@link #attachToPost} và
 * {@link #setPersonAvatar}. Lý do là luật cứng của dự án — <b>không kiểu {@code domain} nào được
 * gắn nhãn</b> — và một tham số {@code MediaOwnerType} sẽ buộc chính enum ấy phải gắn nhãn. Lợi
 * ích kèm theo: mỗi phương thức <i>nói được</i> luật riêng của lối gọi ấy ngay trong javadoc, thay
 * vì một phương thức chung với bốn đoạn "nếu là bài viết thì…".
 *
 * <h2>Kiểm quyền chạy ở ĐÂY, không dựa vào bên gọi đã kiểm</h2>
 * Mọi lối ghi đều gọi {@code MediaAccessGuard.requireAttach}, tức hỏi lại chính module sở hữu bản
 * ghi. {@code content} đã kiểm trước khi gọi — nhưng "đã kiểm rồi" là một bất biến do trí nhớ giữ,
 * và đây là chỗ nó được giữ bằng mã. Đây cũng là chỗ chặn ca <i>người ngoài phạm vi gắn tệp vào
 * bài của chi khác</i>.
 */
@Service
@org.springframework.modulith.NamedInterface("gan-tep")
public class MediaLinkService {

    private static final Logger log = LoggerFactory.getLogger(MediaLinkService.class);

    private final MediaAssetRepository assets;
    private final MediaLinkRepository links;
    private final ObjectStoragePort storage;
    private final MediaAccessGuard access;

    public MediaLinkService(MediaAssetRepository assets, MediaLinkRepository links,
                            ObjectStoragePort storage, MediaAccessGuard access) {
        this.assets = assets;
        this.links = links;
        this.storage = storage;
        this.access = access;
    }

    // =====================================================================================
    // Bài viết
    // =====================================================================================

    /**
     * Đặt <b>toàn bộ</b> danh sách tệp của một bài, đúng thứ tự đưa vào.
     *
     * <h2>Thay cả tập, không thêm/bớt từng cái</h2>
     * Hợp đồng là {@code PUT}, không phải {@code PATCH}: client gửi xuống danh sách nó muốn thấy
     * và server làm cho khớp. Lý do rất cụ thể — <b>thứ tự là một phần của dữ liệu</b> ("ảnh
     * toàn cảnh trước, ảnh dâng hương sau"), và một API thêm/bớt từng tệp sẽ cần thêm một lệnh sắp
     * xếp lại, tức hai lệnh cho một thao tác mà người dùng nghĩ là một. Với 12 tệp là trần thì
     * viết lại cả tập rẻ hơn nhiều so với một API đúng-nhưng-khó-dùng.
     *
     * <p>Tệp bị bỏ ra khỏi danh sách <b>không bị xoá ngay</b>: liên kết bị cắt và nó thành mồ côi,
     * rồi {@code MediaGcService} mang đi sau {@code MediaLimits.ORPHAN_GRACE}. Xoá ngay sẽ làm
     * người viết mất tệp vĩnh viễn chỉ vì kéo nhầm thứ tự một lần.</p>
     *
     * @param mediaIds khoá của các tệp <b>đã xác nhận</b>; trùng lặp bị bỏ, giữ lần xuất hiện đầu
     */
    @Transactional
    public List<MediaAssetView> attachToPost(UUID postId, List<UUID> mediaIds) {
        return replaceLinks(MediaOwnerType.POST, postId, mediaIds);
    }

    /**
     * Các tệp của một bài, đã ký URL đọc, đúng thứ tự.
     *
     * <h2>Lối đọc này KHÔNG tự kiểm quyền — và đó là hợp đồng, không phải chỗ sót</h2>
     * Bên gọi ({@code content}) chỉ được gọi nó cho những bài mà người đọc <b>đã</b> được phép
     * thấy — phép lọc ấy chạy trong SQL của {@code PostJpaRepository}, một lần cho cả trang, và
     * hỏi lại ở đây sẽ là 20 lượt truy vấn nữa cho một trang 20 bài mà câu trả lời đã biết.
     *
     * <p>Lối đọc <i>có</i> kiểm quyền là {@code MediaViewService.signUrls} — dùng khi client đưa
     * xuống một khoá tuỳ ý (ảnh chân dung trên phả đồ chẳng hạn). Hai lối, hai hợp đồng, và sự
     * khác nhau nằm ở chỗ <b>ai đã xác lập quyền trước đó</b>.</p>
     */
    @Transactional(readOnly = true)
    public List<MediaAssetView> listForPost(UUID postId) {
        return viewsOf(MediaOwnerType.POST, postId);
    }

    /**
     * Tệp của <b>nhiều</b> bài một lượt — hai câu truy vấn cho cả trang chủ.
     *
     * <p>Không có nó thì trang chủ 10 bài chạy 20 câu (một liên kết + một tệp cho mỗi bài), và
     * NFR-1 đặt ngân sách 2000 ms tới thẻ người đầu tiên. Cùng hợp đồng với {@link #listForPost}:
     * <b>không tự kiểm quyền</b>, bên gọi phải đã lọc danh sách bài.</p>
     *
     * @return khoá bài → danh sách tệp đúng thứ tự; bài không có tệp thì <b>vắng mặt</b> khỏi bản đồ
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<MediaAssetView>> listForPosts(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<MediaLink> found = links.findByOwners(MediaOwnerType.POST, postIds);
        if (found.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MediaAsset> byId = assets.findAllByIds(found.stream().map(MediaLink::mediaId)
                        .toList()).stream()
                .filter(MediaAsset::isServable)
                .collect(Collectors.toMap(MediaAsset::id, a -> a));
        Map<UUID, List<MediaAssetView>> grouped = new LinkedHashMap<>();
        for (MediaLink link : found) {
            MediaAsset asset = byId.get(link.mediaId());
            if (asset != null) {
                grouped.computeIfAbsent(link.ownerId(), k -> new ArrayList<>())
                        .add(toView(asset, link.position()));
            }
        }
        return grouped;
    }

    /**
     * Cắt mọi liên kết của một bài — gọi khi bài bị <b>gỡ</b>.
     *
     * <p>Bài vẫn ở lại ({@code status = WITHDRAWN}, xoá mềm tuyệt đối); tệp thì thành mồ côi và
     * đường dọn mang đi sau ân hạn. Đó là ranh giới đã ghi ở {@code V19}: luật xoá mềm nói về
     * <i>node phả hệ</i>, còn Nghị định 13/2023 đòi dữ liệu bị gỡ phải thực sự biến mất.</p>
     */
    @Transactional
    public void detachFromPost(UUID postId) {
        int cut = links.deleteByOwner(MediaOwnerType.POST, postId);
        if (cut > 0) {
            log.info("Cat {} lien ket tep khoi bai {} — chung thanh mo coi va se duoc don sau {}h",
                    cut, postId, MediaLimits.ORPHAN_GRACE.toHours());
        }
    }

    // =====================================================================================
    // Ảnh chân dung
    // =====================================================================================

    /**
     * Gắn một tệp làm ảnh chân dung của một nhân khẩu.
     *
     * <p><b>Chỉ nhận ảnh.</b> Một video làm avatar không có nghĩa gì trên node phả đồ, và nó sẽ
     * kéo hàng trăm MiB xuống một canvas đang vẽ hàng nghìn node.</p>
     *
     * <p>Ảnh chân dung cũ (nếu có) bị cắt liên kết và thành mồ côi — nên đường dọn cũng là thứ
     * thu hồi ảnh cũ sau khi người ta đổi ảnh.</p>
     *
     * @return khoá đối tượng, để {@code genealogy} ghi vào {@code person.avatar_key} <b>trong cùng
     *         giao dịch</b>. Trả về khoá chứ không phải URL: cột ấy lưu khoá, và mọi bộ lọc riêng
     *         tư đang soi nó — xem khối ghi chú của {@code V19}
     */
    @Transactional
    public String setPersonAvatar(UUID personId, UUID mediaId) {
        // Kiem loai TRUOC khi cat lien ket cu: doi thu tu hai dong nay nghia la mot lan gan nham
        // video se xoa mat anh chan dung dang co, roi moi bao loi.
        MediaAsset asset = assets.findById(mediaId)
                .orElseThrow(() -> new NotFoundException(MediaProblemCodes.NOT_FOUND,
                        "Khong tim thay tep " + mediaId + "."));
        if (asset.kind() != vn.giapha.media.domain.MediaKind.IMAGE) {
            throw new DomainException(MediaProblemCodes.VALIDATION_FAILED,
                    "Anh chan dung phai la mot tam anh, khong phai video.");
        }
        List<MediaAssetView> result = replaceLinks(
                MediaOwnerType.PERSON_AVATAR, personId, List.of(mediaId));
        log.info("Dat anh chan dung {} cho nhan khau {}", result.get(0).id(), personId);
        return asset.objectKey();
    }

    /** Gỡ ảnh chân dung. Tệp thành mồ côi; {@code genealogy} tự xoá {@code avatar_key}. */
    @Transactional
    public void clearPersonAvatar(UUID personId) {
        access.requireAttach(MediaOwnerType.PERSON_AVATAR, personId);
        links.deleteByOwner(MediaOwnerType.PERSON_AVATAR, personId);
    }

    /** Ảnh chân dung hiện tại của một nhân khẩu, đã ký URL. Rỗng khi chưa có hoặc đã bị gỡ. */
    @Transactional(readOnly = true)
    public Optional<MediaAssetView> avatarOf(UUID personId) {
        List<MediaAssetView> found = viewsOf(MediaOwnerType.PERSON_AVATAR, personId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    // =====================================================================================
    // Chung
    // =====================================================================================

    private List<MediaAssetView> replaceLinks(MediaOwnerType ownerType, UUID ownerId,
                                              List<UUID> mediaIds) {
        access.requireAttach(ownerType, ownerId);
        MemberScopeView caller = access.caller();

        List<UUID> wanted = new ArrayList<>(new LinkedHashSet<>(
                mediaIds == null ? List.of() : mediaIds));
        if (wanted.size() > MediaLimits.MAX_MEDIA_PER_POST) {
            throw new DomainException(MediaProblemCodes.MEDIA_TOO_MANY,
                    "Mot bai gan toi da " + MediaLimits.MAX_MEDIA_PER_POST + " tep. Bai viet la"
                            + " mot bai viet, khong phai mot an-bum.");
        }

        List<MediaAsset> resolved = new ArrayList<>(wanted.size());
        for (UUID mediaId : wanted) {
            MediaAsset asset = assets.findById(mediaId)
                    .orElseThrow(() -> new NotFoundException(MediaProblemCodes.NOT_FOUND,
                            "Khong tim thay tep " + mediaId + "."));
            // BAT BIEN: chi tep DA XAC NHAN moi gan duoc. Mot hang PENDING la bang chung ai do da
            // XIN mot cho de tai len, khong phai bang chung tep ton tai — gan no vao bai la cach
            // mot bai viet tro vao hu khong.
            if (!asset.isServable()) {
                throw new MediaConflictException(MediaProblemCodes.MEDIA_NOT_UPLOADED,
                        "Tep " + mediaId + " chua duoc xac nhan (hoac da bi go), nen chua gan vao"
                                + " duoc. Hay goi buoc xac nhan sau khi tai len xong.");
            }
            // Chan loi "doan khoa cua mot tep nguoi khac vua tai len roi gan vao bai cua minh".
            if (!asset.isUploadedBy(caller.appUserId())) {
                throw new ForbiddenException(MediaProblemCodes.MEDIA_NOT_OWNED,
                        "Tep " + mediaId + " khong phai do ban tai len.");
            }
            resolved.add(asset);
        }

        links.deleteByOwner(ownerType, ownerId);
        for (int i = 0; i < resolved.size(); i++) {
            links.save(MediaLink.of(resolved.get(i).id(), ownerType, ownerId, i));
        }
        return toViews(resolved);
    }

    private List<MediaAssetView> viewsOf(MediaOwnerType ownerType, UUID ownerId) {
        List<MediaLink> found = links.findByOwner(ownerType, ownerId);
        List<MediaAssetView> views = new ArrayList<>(found.size());
        for (MediaLink link : found) {
            assets.findById(link.mediaId())
                    .filter(MediaAsset::isServable)
                    .ifPresent(asset -> views.add(toView(asset, link.position())));
        }
        return views;
    }

    private List<MediaAssetView> toViews(List<MediaAsset> resolved) {
        List<MediaAssetView> views = new ArrayList<>(resolved.size());
        for (int i = 0; i < resolved.size(); i++) {
            views.add(toView(resolved.get(i), i));
        }
        return views;
    }

    private MediaAssetView toView(MediaAsset asset, int position) {
        return new MediaAssetView(asset.id(), asset.kind().name(), asset.contentType(),
                asset.sizeBytes() == null ? 0L : asset.sizeBytes(), asset.durationMs(),
                asset.altText(), position,
                storage.presignGet(asset.objectKey(), MediaLimits.VIEW_URL_TTL));
    }
}
