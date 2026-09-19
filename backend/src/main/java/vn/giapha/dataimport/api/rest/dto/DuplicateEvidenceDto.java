package vn.giapha.dataimport.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Một dấu hiệu trong bảng đối chiếu nghi trùng.
 *
 * <h2>Bất biến riêng tư của mảng {@code evidence} — đọc trước khi thêm trường</h2>
 * <b>{@code existingValue = null} có đúng một nghĩa: "nguồn bên kia không ghi mục này".</b> Nó
 * <b>không bao giờ</b> được dùng để nói "đã bị cắt theo phân tầng riêng tư".
 *
 * <p>Lý do rất cụ thể: giao diện hiển thị {@code null} thành dòng chữ "không ghi", và người đối
 * chiếu sẽ đọc đúng như thế — rồi hợp nhất, tin rằng bên kia đang trống. Một ô rỗng vì bị giấu vừa
 * <i>rò rỉ sự tồn tại</i> của dữ liệu đang giấu, vừa dẫn thẳng tới một quyết định gộp sai. Gộp
 * nhầm hai người là hợp nhất hai nhánh con cháu vào một node sai, và sau ba mươi ngày thì không
 * hoàn tác được.</p>
 *
 * <p>Vì vậy: dấu hiệu nào mà bên kia không được phép trưng ra thì <b>bỏ hẳn khỏi mảng</b>, không
 * gửi sang một ô rỗng. Hệ quả là mảng {@code evidence} của một cặp đối chiếu với người đã có trong
 * phả hiện <b>luôn rỗng</b> — xem {@code ImportDuplicatePairDto}.</p>
 *
 * @param field {@code FULL_NAME} · {@code TABOO_NAME} · {@code GENERATION} · {@code BIRTH_YEAR} ·
 *        {@code DEATH_LUNAR} · {@code FATHER} · {@code ORIGIN_PLACE}
 * @param match {@code SAME} · {@code DIFFERENT} · {@code MISSING_IN_FILE} · {@code MISSING_IN_TREE}
 * @param points số điểm dấu hiệu này đóng góp, <b>do bộ chấm điểm cộng</b>. Vắng mặt khi bộ dò chưa
 *        công bố bản tách điểm theo từng tín hiệu — giao diện <b>không</b> được tự cộng bù: thang
 *        điểm là dữ liệu hiệu chỉnh của bộ dò, một bản sao ở client chắc chắn sẽ trôi
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DuplicateEvidenceDto(String field,
                                   String match,
                                   Integer points,
                                   String existingValue,
                                   String incomingValue) {

    public static final String SAME = "SAME";
    public static final String DIFFERENT = "DIFFERENT";
    public static final String MISSING_IN_FILE = "MISSING_IN_FILE";
    public static final String MISSING_IN_TREE = "MISSING_IN_TREE";
}
