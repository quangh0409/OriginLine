package vn.giapha.media.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.media.domain.MediaAsset;
import vn.giapha.media.domain.MediaLimits;
import vn.giapha.media.domain.MediaLink;
import vn.giapha.media.domain.port.MediaAssetRepository;
import vn.giapha.media.domain.port.MediaLinkRepository;
import vn.giapha.media.domain.port.ObjectStoragePort;

/**
 * Đổi một <b>khoá đối tượng</b> thành một URL đọc đã ký — sau khi kiểm quyền lại từ đầu.
 *
 * <h2>Khoá không phải giấy thông hành</h2>
 * Đây là điểm quan trọng nhất của lớp này. {@code PersonDto.avatarKey} đã đi qua bộ lọc nhóm
 * trường {@code birthDetailAndPhoto} trước khi rời máy chủ, nhưng một khoá <i>đã ra ngoài</i> thì
 * ra ngoài mãi — nó nằm trong lịch sử trình duyệt, trong một ảnh chụp màn hình, trong một
 * {@code localStorage} chưa dọn. Nên mỗi lần ký URL là một lần <b>phép lọc chạy lại</b>, với người
 * gọi <i>hiện tại</i> và cấu hình riêng tư <i>hiện tại</i>. Người đặt lại nhóm trường về
 * {@code PRIVATE} hôm nay thì từ hôm nay không ai ký được URL cho ảnh của họ nữa, kể cả người hôm
 * qua đã cầm khoá.
 *
 * <h2>Nhận một DANH SÁCH khoá, không phải một khoá</h2>
 * Phả đồ vẽ hàng trăm node, mỗi node một ảnh chân dung. Một điểm cuối "ký một khoá" nghĩa là hàng
 * trăm vòng đi-về HTTP trên một mạng di động — thứ sẽ làm hỏng NFR-1 (2000 ms tới thẻ người đầu
 * tiên) một cách chắc chắn. Ký hàng loạt là hình dạng duy nhất dùng được.
 *
 * <h2>Khoá không ký được thì VẮNG MẶT khỏi kết quả, không báo lỗi</h2>
 * Ba tình huống dẫn tới cùng một kết quả và <b>cố ý không phân biệt được</b> từ ngoài: khoá không
 * tồn tại, tệp đã bị gỡ, và người gọi không có quyền. Trả về ba câu trả lời khác nhau là biến
 * chính điểm cuối này thành công cụ dò xem một khoá có thật hay không — cùng lý do mà
 * {@code PostController} trả {@code 404} thay vì {@code 403} cho bài không được phép thấy.
 */
@Service
public class MediaViewService {

    private static final Logger log = LoggerFactory.getLogger(MediaViewService.class);

    /**
     * Số khoá tối đa một lượt. Đủ cho một màn phả đồ đã tải lười, và chặn một yêu cầu 10.000 khoá
     * bắt máy chủ chạy 10.000 phép kiểm quyền.
     */
    public static final int MAX_KEYS_PER_CALL = 200;

    private final MediaAssetRepository assets;
    private final MediaLinkRepository links;
    private final ObjectStoragePort storage;
    private final MediaAccessGuard access;

    public MediaViewService(MediaAssetRepository assets, MediaLinkRepository links,
                            ObjectStoragePort storage, MediaAccessGuard access) {
        this.assets = assets;
        this.links = links;
        this.storage = storage;
        this.access = access;
    }

    /**
     * @param objectKeys khoá lấy từ {@code PersonDto.avatarKey} (hoặc chỗ khác trong hợp đồng)
     * @return bản đồ khoá → URL đã ký, <b>chỉ chứa những khoá người gọi được xem</b>
     */
    @Transactional(readOnly = true)
    public Map<String, String> signUrls(List<String> objectKeys) {
        access.requireProvisioned();
        if (objectKeys == null || objectKeys.isEmpty()) {
            return Map.of();
        }
        List<String> capped = objectKeys.size() > MAX_KEYS_PER_CALL
                ? objectKeys.subList(0, MAX_KEYS_PER_CALL) : objectKeys;

        Map<String, String> signed = new LinkedHashMap<>();
        int refused = 0;
        for (MediaAsset asset : assets.findAllByObjectKeys(capped)) {
            if (!asset.isServable()) {
                refused++;
                continue;
            }
            Optional<MediaLink> link = links.findByMediaId(asset.id());
            // Tep mo coi: khong con ban ghi nao mang no, nen khong ai tra loi duoc cau "duoc xem
            // khong". Mac dinh DONG.
            if (link.isEmpty() || !access.canView(link.get().ownerType(), link.get().ownerId())) {
                refused++;
                continue;
            }
            signed.put(asset.objectKey(),
                    storage.presignGet(asset.objectKey(), MediaLimits.VIEW_URL_TTL));
        }
        if (refused > 0) {
            log.debug("Ky {} / {} khoa; {} khoa bi bo qua (khong ton tai, da go, hoac ngoai quyen)",
                    signed.size(), capped.size(), refused);
        }
        return signed;
    }
}
