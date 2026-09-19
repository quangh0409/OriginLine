package vn.giapha.dataimport.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Một bên của cặp nghi trùng.
 *
 * <h2>Hai nguồn, và chúng khác nhau về mọi thứ</h2>
 * <ul>
 *   <li>{@code source = FILE} — bên kia là <b>một dòng khác trong chính tệp vừa nộp</b>. Chuyện
 *       rất hay xảy ra khi một người đàn ông xuất hiện ở cả trang đời cha lẫn trang đời con trong
 *       sổ. Toàn bộ dữ liệu là thứ người nhập vừa gõ, nên trả đủ: không có gì để giấu với chính
 *       tác giả của nó.</li>
 *   <li>{@code source = TREE} — bên kia là một <b>nhân khẩu đã có trong phả</b>. Ở đây chỉ trả
 *       {@link #personId()}, không một trường nào khác. Xem javadoc của
 *       {@code ImportDuplicatePairDto} về lý do.</li>
 * </ul>
 *
 * @param source {@code FILE} hoặc {@code TREE}
 * @param personId khoá nhân khẩu; chỉ có với {@code TREE}
 * @param externalCode cột {@code Mã}; chỉ có với {@code FILE}
 * @param deathLunar ngày giỗ đã soạn sẵn để đọc, ví dụ {@code 15/8} hoặc {@code 15/8 nhuận/1945}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DuplicatePartyDto(String source,
                                UUID personId,
                                String externalCode,
                                Integer rowNo,
                                String displayName,
                                String tabooName,
                                Integer generation,
                                Integer birthYear,
                                String deathLunar,
                                String fatherCode,
                                String nativePlace) {

    public static final String SOURCE_FILE = "FILE";
    public static final String SOURCE_TREE = "TREE";

    /** Bên đã có trong phả: <b>chỉ khoá</b>, không một trường dữ liệu nào. */
    public static DuplicatePartyDto tree(UUID personId) {
        return new DuplicatePartyDto(SOURCE_TREE, personId, null, null, null, null, null, null,
                null, null, null);
    }
}
