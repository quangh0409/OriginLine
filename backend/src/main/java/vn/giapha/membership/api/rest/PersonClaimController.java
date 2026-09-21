package vn.giapha.membership.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.membership.api.rest.dto.MyPersonClaimsDto;
import vn.giapha.membership.api.rest.dto.PersonClaimDto;
import vn.giapha.membership.api.rest.dto.ReviewPersonClaimRequest;
import vn.giapha.membership.api.rest.dto.SubmitNewPersonClaimRequest;
import vn.giapha.membership.api.rest.dto.SubmitPersonClaimRequest;
import vn.giapha.membership.application.PersonClaimService;
import vn.giapha.membership.application.command.ReviewPersonClaimCommand;
import vn.giapha.membership.application.command.SubmitNewPersonClaimCommand;
import vn.giapha.membership.application.command.SubmitPersonClaimCommand;

/**
 * Tự nhận mình trong phả — {@code /api/v1/person-claims}.
 *
 * <h2>Mọi đường ở đây đều đòi token</h2>
 * Khác hẳn {@code ClanInviteController}. Người gửi đơn <b>đã đăng ký</b> (bằng mã dòng họ hoặc qua
 * Google/Zalo) và đang ở trạng thái "có tài khoản, chưa gắn vào phả". Không có ca "người chưa đăng
 * nhập gửi đơn": một đơn ẩn danh thì không có gì để gắn vào khi được duyệt.
 *
 * <h2>Hai đường gửi, vì đó là hai việc khác nhau</h2>
 * <ul>
 *   <li>{@code POST /existing} — "tôi là người này trong phả". Duyệt thì chỉ <i>gắn</i> tài
 *       khoản.</li>
 *   <li>{@code POST /new-person} — "tôi chưa có trong phả". Duyệt thì <b>tạo nhân khẩu mới</b>.
 *       Đây là lối <i>ghi vào phả</i>, không phải một biểu mẫu liên hệ.</li>
 * </ul>
 * Gộp vào một endpoint với một trường phân loại sẽ làm hai thân yêu cầu gần như không giao nhau
 * phải chia chung một lược đồ, và mọi trường đều thành tuỳ chọn — lúc đó hợp đồng không nói được
 * điều gì bắt buộc với loại nào, và ràng buộc "phải chỉ ra người thân" chỉ còn là một câu văn.
 *
 * <h2>Hàng chờ duyệt lọc theo {@code ltree}</h2>
 * Trưởng chi Ất không thấy — và không duyệt được — đơn trỏ vào người chi Bính. Phép lọc chạy ngay
 * trong SQL, nên đơn của chi khác không bao giờ đi vào bộ nhớ tiến trình. Điều đó quan trọng hơn
 * một chi tiết hiệu năng: đơn chở số điện thoại và vài dòng tự giới thiệu của một người đang sống.
 */
@RestController
@RequestMapping("/api/v1/person-claims")
@Tag(name = "person-claims", description = "Tự nhận mình trong phả — gửi đơn, hàng chờ, duyệt")
public class PersonClaimController {

    private final PersonClaimService claims;

    public PersonClaimController(PersonClaimService claims) {
        this.claims = claims;
    }

    // -------------------------------------------------------------------------------------
    // Gửi đơn
    // -------------------------------------------------------------------------------------

    /**
     * "Tôi là người này trong phả".
     *
     * <p><b>Ba ca bị chặn ngay lúc gửi.</b> Nhận nhầm người đã khuất → {@code 422} với thông điệp
     * nói thẳng (hồ sơ người đã khuất vốn công khai nên nói thẳng không lộ thêm gì). Nhận nhầm
     * người <i>đã có tài khoản</i>, hoặc nhân khẩu đã xoá mềm → {@code 422} với <b>một thông điệp
     * chung chung</b>: nói thẳng "người này đã có tài khoản" sẽ biến màn này thành công cụ dò xem
     * ai đã vào hệ thống, và với 1.500 người thì đó là một danh sách có giá trị thật với người muốn
     * mạo danh.</p>
     */
    @PostMapping("/existing")
    @Operation(summary = "Gửi đơn nhận một nhân khẩu đã có trong phả")
    public ResponseEntity<PersonClaimDto> submitExisting(
            @Valid @RequestBody SubmitPersonClaimRequest body) {
        PersonClaimDto claim = PersonClaimDto.from(claims.submitExisting(
                new SubmitPersonClaimCommand(body.personId(), body.phone(), body.introduction())));
        return ResponseEntity.created(URI.create("/api/v1/person-claims/" + claim.id()))
                .body(claim);
    }

    /**
     * "Tôi chưa có trong phả" — con dâu mới về, cháu mới sinh, nhánh ở xa nhiều đời.
     *
     * <p><b>Đơn này không tạo nhân khẩu nào.</b> Nhân khẩu chỉ ra đời khi Trưởng chi duyệt; xoá mềm
     * là luật tuyệt đối của dự án nên tạo trước rồi xoá sau sẽ để lại một <i>node ma</i> trong phả
     * cho mỗi đơn bị từ chối.</p>
     *
     * <p>Phản hồi mang {@code duplicateSuspects}: đơn đã chạy qua bộ dò trùng ngay lúc gửi, vì
     * người khai rất có thể <i>đã</i> có trong phả dưới một tên khác. Giao diện nên hiện ngay cho
     * người gửi thấy — "có phải bạn là người này không?" ở bước gửi rẻ hơn nhiều so với một bản
     * trùng được tạo ra rồi phải gộp lại.</p>
     */
    @PostMapping("/new-person")
    @Operation(summary = "Gửi đơn xin được thêm vào phả (bắt buộc chỉ ra người thân đã có)")
    public ResponseEntity<PersonClaimDto> submitNew(
            @Valid @RequestBody SubmitNewPersonClaimRequest body) {
        PersonClaimDto claim = PersonClaimDto.from(claims.submitNew(
                new SubmitNewPersonClaimCommand(body.fullName(), body.birthYear(), body.gender(),
                        body.relativePersonId(), body.relativeKind(), body.phone(),
                        body.introduction())));
        return ResponseEntity.created(URI.create("/api/v1/person-claims/" + claim.id()))
                .body(claim);
    }

    // -------------------------------------------------------------------------------------
    // Đọc
    // -------------------------------------------------------------------------------------

    /**
     * Đơn của chính tôi — màn "đang chờ duyệt".
     *
     * <p>Không có màn này thì người dùng gửi đơn xong rơi vào im lặng và sẽ gửi lại lần hai, lần
     * ba — rồi chạm giới hạn gửi lại và không hiểu vì sao.</p>
     *
     * <p>Phản hồi <b>bọc</b> danh sách trong {@code { claims, quota }}, khác mọi danh sách khác của
     * context này. {@code quota} là hạn mức gửi lại, và nó không thuộc về bất kỳ đơn nào — nhét vào
     * từng đơn là lặp một giá trị toàn cục lên n dòng, tách ra một endpoint thứ hai là bắt màn hình
     * gọi hai lượt cho một câu trả lời.</p>
     *
     * <p><b>Thứ tự ánh xạ:</b> đường dẫn hằng {@code /mine} đứng trước đường dẫn có biến
     * {@code /&#123;id&#125;}. Spring chọn theo độ cụ thể chứ không theo thứ tự khai, nên
     * {@code /mine} thắng — nhưng giữ thứ tự khai đúng vẫn là kỷ luật rẻ, và một
     * {@code @PathVariable String} thay cho {@code UUID} ở dòng dưới sẽ biến điều này thành một cái
     * bẫy thật.</p>
     */
    @GetMapping("/mine")
    @Operation(summary = "Đơn của chính tôi, kèm hạn mức gửi lại")
    public MyPersonClaimsDto mine(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return MyPersonClaimsDto.from(claims.mine(page, size));
    }

    /**
     * Hàng chờ duyệt trong phạm vi chi của tôi, <b>cũ nhất trước</b>.
     *
     * <p>Hai đơn cùng trỏ một nhân khẩu <b>đều xuất hiện</b>, và cố ý không ưu tiên đơn gửi trước:
     * trùng tên trong dòng họ là chuyện thường, và người gửi trước chưa chắc là người đúng. Trưởng
     * chi thấy cả hai rồi chọn.</p>
     */
    @GetMapping("/pending")
    @Operation(summary = "Hàng chờ duyệt trong phạm vi chi/ngành của người gọi")
    public List<PersonClaimDto> pending(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return claims.pendingForReview(page, size).stream().map(PersonClaimDto::from).toList();
    }

    /**
     * Đếm đơn chờ duyệt trong phạm vi.
     *
     * <p><b>Trả {@code 0} cho người không có quyền duyệt, không trả 403.</b> Huy hiệu này nằm trên
     * thanh đầu trang của mọi màn hình: nếu nó 403 thì giao diện phải biết vai của người dùng
     * <i>trước khi</i> gọi, tức phải chép luật phân quyền sang client. Và {@code 0} không lộ gì —
     * con số ấy vốn là "có bao nhiêu đơn <i>bạn</i> duyệt được".</p>
     */
    @GetMapping("/pending/count")
    @Operation(summary = "Đếm đơn chờ duyệt trong phạm vi (không có quyền thì trả 0, không phải 403)")
    public Map<String, Long> pendingCount() {
        return Map.of("count", claims.countPendingForReview());
    }

    /** Chi tiết một đơn — chỉ người gửi hoặc người duyệt đúng phạm vi. */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một đơn")
    public PersonClaimDto byId(@PathVariable UUID id) {
        return PersonClaimDto.from(claims.byId(id));
    }

    // -------------------------------------------------------------------------------------
    // Duyệt / từ chối / rút
    // -------------------------------------------------------------------------------------

    /**
     * Duyệt hoặc từ chối.
     *
     * <p>Duyệt một đơn {@code NEW_PERSON} làm bốn việc trong <b>một transaction</b>: tạo nhân khẩu
     * cùng lúc với cạnh quan hệ vào người thân · gắn tài khoản · đóng đơn · ghi nhật ký. Hỏng ở bất
     * kỳ bước nào thì cả bốn cùng cuộn lại, và đặc biệt là không có nhân khẩu nào ở lại trong
     * phả.</p>
     *
     * <p>Duyệt xong, các đơn còn lại cùng trỏ một nhân khẩu được <b>đóng tường minh</b> kèm lý do.
     * Để chúng nằm lại {@code PENDING} là để lại một đơn vĩnh viễn không duyệt được làm nghẽn hàng
     * chờ, và người gửi thì không bao giờ nhận được câu trả lời.</p>
     */
    @PostMapping("/{id}/review")
    @Operation(summary = "Duyệt hoặc từ chối một đơn (Trưởng chi trong phạm vi; Hội đồng toàn họ)")
    public PersonClaimDto review(@PathVariable UUID id,
                                 @Valid @RequestBody ReviewPersonClaimRequest body) {
        return PersonClaimDto.from(claims.review(
                new ReviewPersonClaimCommand(id, Boolean.TRUE.equals(body.approve()), body.note())));
    }

    /**
     * Người gửi tự rút lại đơn của mình — và nhờ đó gửi được đơn khác ngay.
     *
     * <p>Rút <b>không</b> tính vào giới hạn gửi lại: giới hạn ấy đếm số lần <i>bị từ chối</i>, và
     * người tự sửa sai của mình không phải là người đang dò.</p>
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Rút lại đơn của chính mình")
    public PersonClaimDto cancel(@PathVariable UUID id) {
        return PersonClaimDto.from(claims.cancel(id));
    }
}
