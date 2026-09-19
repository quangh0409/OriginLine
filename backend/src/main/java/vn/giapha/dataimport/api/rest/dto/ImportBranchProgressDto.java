package vn.giapha.dataimport.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng của <b>màn tiến độ theo chi</b>.
 *
 * <h2>Đây không phải bảng xếp hạng</h2>
 * Màn này trả lời "còn thiếu gì", không trả lời "ai chậm". Vì vậy khung nhìn cố ý <b>không</b> có
 * thứ hạng, không có phần trăm so với chi khác, không có mốc "đáng lẽ phải xong". Cùng lý do bản
 * chốt phạm vi đã cắt xếp hạng đóng góp theo số tiền: một dòng họ không phải một bảng thi đua, và
 * cái giá của việc làm Trưởng chi mất mặt trước cả họ lớn hơn nhiều so với chút áp lực nó tạo ra.
 *
 * <h2>Trường vắng mặt ≠ trường bằng null — và đó là toàn bộ luật riêng tư ở đây</h2>
 * Với chi <b>ngoài phạm vi</b> người gọi, năm trường {@link #coordinatorName()},
 * {@link #openBatch()}, {@link #blockingCount()}, {@link #warningCount()},
 * {@link #undecidedDuplicateCount()} bị <b>bỏ hẳn khỏi JSON</b>, không null hoá. Một trường null
 * vẫn nói cho người đọc biết trường ấy tồn tại và có giá trị ở đâu đó — với
 * {@code coordinatorName}, vốn <b>là tên một người còn sống</b>, đó đã là một mẩu rò rỉ. Phép bỏ
 * hẳn do {@code @JsonInclude(NON_NULL)} cộng với việc tầng đọc truyền {@code null} vào đúng năm
 * trường ấy.
 *
 * <p>Hệ quả phải chấp nhận: chi <i>trong</i> phạm vi mà chưa có ai nhận phụ trách cũng vắng
 * {@code coordinatorName}. Giao diện đọc "vắng" thành "chưa có ai nhận" là đúng cho cả hai trường
 * hợp, nên sự nhập nhằng này không sinh ra hành vi sai.</p>
 *
 * @param stage vị trí trong quy trình: {@code NOT_STARTED} · {@code UPLOADED} ·
 *        {@code RECONCILING} · {@code COMMITTED}. <b>Chưa có</b> {@code TEMPLATE_DOWNLOADED} vì hệ
 *        thống chưa ghi lại lượt tải mẫu — bịa ra một bậc không đo được thì cả thang bậc mất giá
 * @param personsInTree số nhân khẩu chưa xoá mềm đang treo vào chi
 * @param expectedPersons số người Hội đồng đếm được trên bản phả <b>giấy</b>. Vắng mặt nghĩa là
 *        <b>chưa ai đếm</b> — khác hẳn 0, và giao diện không được coi nó là 0
 * @param missingGioCount số người đã mất mà trống ngày giỗ. Con số đáng giá nhất của cả màn: giỗ
 *        là thứ dòng họ dùng phả để tra, và nó <b>không</b> bị cắt với chi ngoài phạm vi vì nó là
 *        một phép đếm về người đã khuất, không phải một lời phán về ai đang làm chậm
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportBranchProgressDto(UUID branchId,
                                      String branchName,
                                      String branchPath,
                                      String coordinatorName,
                                      String stage,
                                      long personsInTree,
                                      Integer expectedPersons,
                                      LoDangMo openBatch,
                                      Integer blockingCount,
                                      Integer warningCount,
                                      Integer undecidedDuplicateCount,
                                      int missingGioCount) {

    /** Lô người nhập sẽ quay lại làm tiếp. Đủ để giao diện dựng một đường dẫn, không hơn. */
    public record LoDangMo(UUID id, String status, Instant uploadedAt) {
    }

    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String UPLOADED = "UPLOADED";
    public static final String RECONCILING = "RECONCILING";
    public static final String COMMITTED = "COMMITTED";
}
