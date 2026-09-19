package vn.giapha.dataimport.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.dataimport.domain.DuplicateDecision;
import vn.giapha.dataimport.domain.DuplicatePair;

/**
 * Kho <b>cặp nghi trùng và quyết định của người</b> về nó.
 *
 * <h2>Vì sao kho này KHÔNG có {@code replaceAll} như kho lỗi</h2>
 * {@code ImportIssueRepository} xoá sạch rồi ghi lại mỗi lần kiểm, và đó là hành vi đúng cho lỗi:
 * lỗi là kết quả tính toán, tính lại thì thay hết. Cặp nghi trùng thì <b>mang theo một quyết định
 * của con người</b>, và quyết định ấy không phải kết quả tính toán của ai cả. Xoá sạch rồi ghi lại
 * ở đây nghĩa là mỗi lần bấm "kiểm lại" sẽ xoá sạch công đối chiếu của cả buổi chiều — đúng loại
 * mất mát khiến người ta thôi dùng nút kiểm lại, rồi thôi dùng cả bộ dò.
 *
 * <p>Vì vậy lối ghi duy nhất là {@link #dongBo}: <b>hợp nhất</b> theo {@code pairKey}.</p>
 */
public interface DuplicatePairRepository {

    /**
     * Hợp nhất tập cặp vừa dò được vào tập cặp đang lưu của lô.
     *
     * <p>Ba nhánh, và cả ba đều bắt buộc:</p>
     * <ul>
     *   <li><b>Cặp đã có, {@code pairKey} y nguyên</b> → làm mới {@code score} / {@code signals} /
     *       {@code rowNo}, <b>giữ nguyên</b> quyết định. Đây là điều làm cho "kiểm lại vì có thêm
     *       cặp thứ 41" không bắt người ta quyết lại 40 cặp cũ — bắt quyết lại một danh sách không
     *       đổi là cách chắc chắn để lần thứ ba người ta bấm mà không đọc.</li>
     *   <li><b>Cặp mới</b> → chèn với {@code PENDING}. Lô lập tức bị cổng duyệt chặn lại, đúng như
     *       nó phải thế: đây là một câu hỏi chưa ai trả lời.</li>
     *   <li><b>Cặp không còn dò ra</b> → xoá. Câu hỏi không còn nữa thì câu trả lời cũng thôi có
     *       nghĩa, và giữ lại chỉ làm màn đối chiếu hiện những cặp không tồn tại.</li>
     * </ul>
     *
     * @param batchId lô
     * @param pairs tập cặp của <b>lần dò vừa rồi</b>, chưa mang quyết định
     * @return số cặp mới được chèn — dùng cho log, không cho nghiệp vụ
     */
    int dongBo(UUID batchId, List<DuplicatePair> pairs);

    /** Mọi cặp của lô, thứ tự tất định theo (số dòng, khoá cặp). */
    List<DuplicatePair> byBatch(UUID batchId);

    /** Một cặp theo khoá chính. {@code Optional.empty()} khi không có. */
    Optional<DuplicatePair> byId(UUID pairId);

    /**
     * Số cặp <b>chưa quyết</b> — đầu vào của cổng duyệt.
     *
     * <p>"Chưa quyết" gồm cả {@code PENDING} lẫn {@code DEFERRED}. Nếu "hoãn" không nằm trong con
     * số này thì nó thành nút "cho tôi qua" và cả cơ chế dò trùng thành trang trí.</p>
     */
    int demChuaQuyet(UUID batchId);

    /** Tổng số cặp của lô. */
    int demTatCa(UUID batchId);

    /**
     * Ghi quyết định của một người cho một cặp.
     *
     * <p>Ba thứ đi cùng nhau và thiếu thứ nào cũng làm bản ghi vô dụng đúng lúc cần nó: quyết
     * <b>gì</b>, <b>ai</b> quyết, <b>lúc nào</b>. Đây là thao tác có hệ quả vĩnh viễn lên phả —
     * "gộp" làm một dòng biến mất hoặc làm một hồ sơ đã có bị viết đè.</p>
     *
     * @param at thời điểm do tầng application truyền vào, không phải {@code now()} trong SQL, để
     *        nó kiểm được trong test thay vì phải tin
     * @return {@code true} nếu có dòng được cập nhật
     */
    boolean ghiQuyetDinh(UUID pairId, DuplicateDecision decision, UUID actorUserId, Instant at,
                         String note);
}
