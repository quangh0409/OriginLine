package vn.giapha.dataimport.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Một cặp nghi trùng trên màn đối chiếu, kèm <b>quyết định</b> của người (nếu đã có).
 *
 * <h2>Bất biến riêng tư: đường ống nhập liệu KHÔNG phát dữ liệu nhân khẩu</h2>
 * Khi bên bị nghi là người <b>đã có trong phả</b>, phản hồi chỉ mang {@code personId} —
 * không tên, không đời, không năm sinh, không ngày giỗ, và {@link #evidence()} rỗng.
 *
 * <p><b>Vì sao khắt khe tới mức đó.</b> Bộ dò trùng quét <i>toàn dòng họ</i>, nên hồ sơ bị nghi có
 * thể là một người <b>còn sống</b> ở một chi khác. Bộ lọc phân tầng riêng tư (Nghị định 13/2023,
 * BA v2 §10) sống ở context {@code genealogy} và là chỗ <b>duy nhất</b> được quyết định trường nào
 * của một người được trả cho ai. Nếu màn nhập liệu tự trưng tên và năm sinh lấy từ kết quả dò
 * trùng thì luật lọc có hai bản, và bản thứ hai — bản ở đây — sẽ không biết gì về đồng thuận của
 * chủ thể, về tuổi vị thành niên, hay về vai của người gọi.</p>
 *
 * <p>Cách đúng cho giao diện: cầm {@code personId} gọi {@code GET /api/v1/persons/{id}}. Lời gọi
 * ấy đi qua đúng bộ lọc, trả về đúng những trường người gọi được xem, và trả {@code 404} khi họ
 * không được biết bản ghi tồn tại. Thêm một vòng gọi, đổi lấy việc <b>chỉ có một luật lọc</b>.</p>
 *
 * <p>Khi bên bị nghi là một dòng khác <b>trong chính tệp vừa nộp</b> thì không có gì để giấu:
 * cả hai bên đều là thứ người nhập vừa gõ, nên {@link #evidence()} đầy đủ.</p>
 *
 * <h2>{@link #id()} nay là khoá chính thật, và nó ỔN ĐỊNH qua các lần kiểm lại</h2>
 * Trước V14 nó là một khoá <i>suy ra</i> từ (số dòng, bên bị nghi) và hợp đồng dặn "đừng lưu lại".
 * Nay nó là {@code import_duplicate_pair.id}, và giao diện <b>phải</b> dùng nó khi gửi quyết định
 * lên. Bấm "kiểm lại" không đổi khoá của những cặp không đổi — đó là cơ chế làm cho một lần kiểm
 * lại sinh cặp thứ 41 không bắt ai quyết lại 40 cặp cũ.
 *
 * @param status {@code PENDING} · {@code MERGED} · {@code DISTINCT} · {@code DEFERRED}.
 *        <b>{@code DEFERRED} vẫn chặn cổng duyệt</b> — nếu không thì nó thành nút "cho tôi qua"
 * @param preselectMerge giao diện nên tick sẵn "hợp nhất" — ngưỡng do máy chủ công bố ở
 *        {@code GET /api/v1/import/duplicate-policy}, giao diện không ghi cứng
 * @param hint câu giải thích <b>tự do</b>, chỉ để hiển thị, đừng phân tích chuỗi này. <b>Vắng mặt
 *        với cặp đối chiếu vào phả</b>: nó có thể nhắc tới giá trị trường của người bên kia
 *        ("trùng năm sinh 1975"), mà bên kia có thể là một người còn sống ở một chi khác
 * @param signals <b>nhãn tín hiệu</b> — "vì sao nghi" mà không tiết lộ "người ấy là ai"
 *        ("trùng ngày giỗ, cùng chi"). An toàn ở <b>cả hai</b> loại cặp, và là thứ duy nhất của vế
 *        trong phả được phép đi kèm khoá.
 *
 *        <p><b>Cố ý khác tên với {@code hint}</b>, theo đúng nguyên tắc đã dựng ở
 *        {@code SuspectDuplicateRule}: hai khoá mang hai mức dữ liệu khác nhau, nên một giao diện
 *        — hay một bài kiểm — lỡ đọc nhầm khoá thì nhận {@code null} chứ không nhận dữ liệu của
 *        mức kia. Gộp chúng làm một trường là mở lại đúng bề mặt rò rỉ vừa bịt.</p>
 * @param decidedBy ai đã quyết; vắng mặt khi {@code status = PENDING}
 * @param decidedAt lúc nào; vắng mặt khi {@code status = PENDING}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportDuplicatePairDto(String id,
                                     int rowNo,
                                     int score,
                                     boolean preselectMerge,
                                     String status,
                                     String hint,
                                     String signals,
                                     UUID decidedBy,
                                     Instant decidedAt,
                                     String note,
                                     DuplicatePartyDto incoming,
                                     DuplicatePartyDto existing,
                                     List<DuplicateEvidenceDto> evidence) {

    public ImportDuplicatePairDto {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    /** Chưa ai quyết. */
    public static final String PENDING = "PENDING";
}
