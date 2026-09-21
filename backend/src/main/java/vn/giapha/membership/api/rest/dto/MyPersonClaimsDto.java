package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.giapha.membership.application.MyPersonClaims;

/**
 * Màn "đang chờ duyệt" của chính người gửi: danh sách đơn <b>cộng hạn mức gửi lại</b>.
 *
 * <p>Endpoint duy nhất của context này <b>bọc</b> danh sách thay vì trả mảng phẳng, và có lý do:
 * {@code quota} không thuộc về bất kỳ đơn nào trong mảng. Nhét nó vào từng đơn là lặp một giá trị
 * toàn cục lên n dòng rồi để chúng có cơ hội lệch nhau; đặt nó ở một endpoint thứ hai là bắt màn
 * hình gọi hai lượt cho một câu trả lời.</p>
 */
@Schema(description = "Đơn của chính tôi, kèm hạn mức gửi lại")
public record MyPersonClaimsDto(List<PersonClaimDto> claims, ClaimQuotaDto quota) {

    /**
     * Còn được gửi lại mấy lần nữa.
     *
     * <p>Ngưỡng là một giá trị <b>cấu hình của máy chủ</b> và một dòng họ hoàn toàn có thể đặt
     * khác, nên client <b>không được đoán</b>: gán cứng con số 3 sẽ đúng hôm nay và âm thầm sai
     * ngày Hội đồng đổi cấu hình. Không có trường này thì màn hình im lặng cho tới khi người dùng
     * đâm vào {@code CLAIM_LIMIT_REACHED} ở lần gửi thứ tư và mới biết là có giới hạn.</p>
     */
    @Schema(description = "Hạn mức gửi lại — đọc từ cấu hình máy chủ, client không được đoán")
    public record ClaimQuotaDto(

            @Schema(description = "Số đơn đã bị **từ chối**. Đơn tự rút không tính: người tự sửa"
                    + " sai của mình không phải là người đang dò.",
                    example = "1")
            int rejected,

            @Schema(description = "Ngưỡng hiện hành của máy chủ", example = "3")
            int max,

            @Schema(description = "max - rejected, không bao giờ âm", example = "2")
            int remaining) {

        public static ClaimQuotaDto from(MyPersonClaims.ClaimQuota quota) {
            return new ClaimQuotaDto(quota.rejected(), quota.max(), quota.remaining());
        }
    }

    public static MyPersonClaimsDto from(MyPersonClaims mine) {
        return new MyPersonClaimsDto(mine.claims().stream().map(PersonClaimDto::from).toList(),
                ClaimQuotaDto.from(mine.quota()));
    }
}
